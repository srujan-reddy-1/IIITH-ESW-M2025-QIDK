import os
import pandas as pd
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
from torchvision import models, transforms
from PIL import Image, ImageOps
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, confusion_matrix
import copy

# ==========================================
# 1. THE DATASET CLASS (ROUTER - FULL VISIBILITY)
# ==========================================
class InpaintingRouterDataset(Dataset):
    def __init__(self, csv_file, dir_input_images, mask_dir, transform=None):
        """
        Args:
            csv_file: Path to labels.
            dir_input_images: Path to the images BEFORE inpainting.
                              (e.g., The photo containing the object to remove)
            mask_dir: Path to the masks.
        """
        self.data = pd.read_csv(csv_file)
        self.dir_input = dir_input_images
        self.mask_dir = mask_dir
        self.transform = transform

    def _find_image_path(self, directory, filename):
        """Helper to find file with different extensions."""
        exact_path = os.path.join(directory, filename)
        if os.path.exists(exact_path): return exact_path
        
        base_name = os.path.splitext(filename)[0]
        for ext in ['.png', '.jpg', '.jpeg', '.PNG', '.JPG', '.JPEG']:
            path = os.path.join(directory, base_name + ext)
            if os.path.exists(path): return path
        
        raise FileNotFoundError(f"Could not find image '{base_name}' in {directory}")

    def __len__(self):
        return len(self.data)

    def __getitem__(self, idx):
        row = self.data.iloc[idx]
        fname = row['filename']
        label = int(row['label'])

        try:
            path_img = self._find_image_path(self.dir_input, fname)
            path_mask = self._find_image_path(self.mask_dir, fname)
        except FileNotFoundError as e:
            print(f"Error loading {fname}: {e}")
            return self.__getitem__(0) 

        # Load Image and Mask
        img = Image.open(path_img).convert('RGB')
        mask = Image.open(path_mask).convert('L') # 0=Black, 255=White

        # 2. Apply Transforms (Augmentation)
        if self.transform:
            seed = torch.randint(0, 2**32, (1,)).item()
            torch.manual_seed(seed)
            img = self.transform(img)
            torch.manual_seed(seed)
            mask = self.transform(mask)

        # 3. Create the Input
        # PREVIOUSLY: We masked the hole (img * (1-mask))
        # NEW LOGIC: We pass the full image content. 
        # This allows the AI to see "What is currently inside the mask?"
        # Examples: "Is it a face?" "Is it a car?" "Is it just noise?"
        # The AI uses this context to decide which model handles that object better.
        
        # Stack inputs: [Full_Image (3) + Mask (1)] = 4 Channels
        input_tensor = torch.cat((img, mask), dim=0)

        return input_tensor, torch.tensor(label, dtype=torch.float32)

# ==========================================
# 2. MODEL DEFINITION (4 CHANNELS)
# ==========================================
def get_router_model(input_channels=4):
    model = models.resnet18(pretrained=True)
    
    # --- SURGERY: Input Layer ---
    # Change from 3 channels (RGB) to 4 channels (RGB + Mask)
    original_weights = model.conv1.weight.data # [64, 3, 7, 7]
    new_conv1 = nn.Conv2d(input_channels, 64, kernel_size=7, stride=2, padding=3, bias=False)
    
    # Initialize: Average the RGB weights to create the base
    avg_weights = torch.mean(original_weights, dim=1, keepdim=True) # [64, 1, 7, 7]
    
    # We copy the average weight to all 4 channels
    new_weights = avg_weights.repeat(1, input_channels, 1, 1) # [64, 4, 7, 7]
    
    new_conv1.weight.data = new_weights
    model.conv1 = new_conv1

    # --- SURGERY: Output Layer ---
    num_ftrs = model.fc.in_features
    model.fc = nn.Sequential(
        nn.Dropout(0.5), 
        nn.Linear(num_ftrs, 1) # Output: Probability of Model B being better
    )
    
    return model

# ==========================================
# 3. TRAINING LOOP
# ==========================================
def train_model():
    # --- CONFIGURATION ---
    CSV_FILE = 'comparison_labels.csv'
    # IMPORTANT: Point this to the folder containing images BEFORE processing
    # (The images that still have the object/damage visible)
    DIR_INPUT_IMAGES = './images' 
    DIR_MASK = './masks'       
    
    BATCH_SIZE = 8
    EPOCHS = 20
    LR = 0.0001
    # ---------------------

    train_transforms = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomRotation(10),
        transforms.ToTensor(),
    ])
    
    val_transforms = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
    ])

    full_dataset = InpaintingRouterDataset(CSV_FILE, DIR_INPUT_IMAGES, DIR_MASK, transform=None)
    
    train_idx, val_idx = train_test_split(list(range(len(full_dataset))), test_size=0.2, random_state=42)
    train_data = torch.utils.data.Subset(full_dataset, train_idx)
    val_data = torch.utils.data.Subset(full_dataset, val_idx)
    
    # Assign transforms
    full_dataset.transform = train_transforms

    train_loader = DataLoader(train_data, batch_size=BATCH_SIZE, shuffle=True)
    val_loader = DataLoader(val_data, batch_size=BATCH_SIZE, shuffle=False)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Training on: {device}")
    
    model = get_router_model(input_channels=4)
    model = model.to(device)
    criterion = nn.BCEWithLogitsLoss() 
    optimizer = optim.Adam(model.parameters(), lr=LR)

    for epoch in range(EPOCHS):
        model.train()
        running_loss = 0.0
        
        for inputs, labels in train_loader:
            inputs, labels = inputs.to(device), labels.to(device)
            labels = labels.unsqueeze(1)

            optimizer.zero_grad()
            outputs = model(inputs)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()
            running_loss += loss.item()
            
        print(f"Epoch {epoch+1}/{EPOCHS} | Loss: {running_loss/len(train_loader):.4f}")

    torch.save(model.state_dict(), 'inpainting_router.pth')
    print("Model saved to 'inpainting_router.pth'")
    
    # --- EVALUATION ---
    print("\n--- Final Evaluation ---")
    model.eval()
    all_preds = []
    all_labels = []
    
    with torch.no_grad():
        for inputs, labels in val_loader:
            inputs = inputs.to(device)
            outputs = model(inputs)
            preds = torch.sigmoid(outputs) > 0.5
            all_preds.extend(preds.cpu().numpy())
            all_labels.extend(labels.numpy())
            
    print(classification_report(all_labels, all_preds, target_names=['Model A Wins', 'Model B Wins']))
    
    return model

# ==========================================
# 4. PREDICTION (For New Images)
# ==========================================
def predict_best_model(model, img_path, mask_path):
    """
    Takes an input image and a mask.
    Predicts which model (A or B) will likely do a better job.
    """
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model.eval()
    model.to(device)
    
    transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
    ])
    
    img = Image.open(img_path).convert('RGB')
    mask = Image.open(mask_path).convert('L')
    
    t_img = transform(img)
    t_mask = transform(mask)
    
    # Stack [4, 224, 224] -> Batch [1, 4, 224, 224]
    input_tensor = torch.cat((t_img, t_mask), dim=0).unsqueeze(0).to(device)
    
    with torch.no_grad():
        output = model(input_tensor)
        prob = torch.sigmoid(output).item()
        
    if prob < 0.5:
        return "Model A (Label 0)", 1 - prob
    else:
        return "Model B (Label 1)", prob

if __name__ == "__main__":
    # model = train_model()
    # To predict on a new image:
    # winner, conf = predict_best_model(model, "my_photo.jpg", "my_mask.png")
    # print(f"Recommendation: Use {winner} (Confidence: {conf:.2f})")

    model = get_router_model(input_channels=4)
    
    # 2. Load the trained memory (The Weights)
    # This puts the "knowledge" into the empty brain
    model.load_state_dict(torch.load('inpainting_router.pth'))
    
    # 3. Pass this object to the function
    winner, confidence = predict_best_model(model, "094_img.png", "094.png")

    print("winner: ",winner)
    print("confidence: ", confidence)

