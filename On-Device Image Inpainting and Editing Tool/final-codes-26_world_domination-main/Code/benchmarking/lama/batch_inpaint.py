import os
import numpy as np
from PIL import Image

# --- UNIVERSAL IMPORT BLOCK ---
# This handles the "ModuleNotFoundError" automatically by trying both options.
try:
    import tflite_runtime.interpreter as tflite
except ImportError:
    try:
        import tensorflow.lite as tflite
    except ImportError:
        print("CRITICAL ERROR: neither 'tflite_runtime' nor 'tensorflow' is installed.")
        print("Please run: pip install tflite-runtime   OR   pip install tensorflow")
        exit(1)

# --- CONFIGURATION ---
MODEL_FILE = "../models/lama_dilated.tflite"  # Updated to your AOTGAN model
IMAGE_DIR = "images"
MASK_DIR = "masks"
OUTPUT_DIR = "inpainted"

# Model specific input size (Matches your Spec: [1, 512, 512, 3])
MODEL_INPUT_SIZE = (512, 512)


def find_corresponding_mask(base_name, mask_directory):
    """
    Finds a mask file with the same base_name, checking for .png, .jpg, .jpeg.
    Returns the full path if found, otherwise None.
    """
    for ext in ['.png', '.jpg', '.jpeg']:
        mask_path = os.path.join(mask_directory, base_name + ext)
        if os.path.exists(mask_path):
            return mask_path
    return None

def run_inpainting(interpreter, image_path, mask_path):
    """
    Runs inference on a single image/mask pair using the pre-loaded interpreter.
    Returns a PIL Image object of the result.
    """
    
    # Get input and output tensor details from the interpreter
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # 1. Preprocess the Input Image
    # Target: [1, 512, 512, 3], float32
    img = Image.open(image_path).convert("RGB")
    img = img.resize(MODEL_INPUT_SIZE)
    input_img_data = np.array(img, dtype=np.float32) / 255.0
    input_img_data = np.expand_dims(input_img_data, axis=0)

    # 2. Preprocess the Mask
    # Target: [1, 512, 512, 1], float32
    mask = Image.open(mask_path).convert("L") # Grayscale
    mask = mask.resize(MODEL_INPUT_SIZE)
    mask_data = np.array(mask, dtype=np.float32) / 255.0
    mask_data = np.expand_dims(mask_data, axis=-1) # Add channel dim
    mask_data = np.expand_dims(mask_data, axis=0)  # Add batch dim

    # 3. Set Inputs
    # Your specs: Input 0 is image, Input 1 is mask.
    interpreter.set_tensor(input_details[0]['index'], input_img_data)
    interpreter.set_tensor(input_details[1]['index'], mask_data)

    # 4. Run Inference
    interpreter.invoke()

    # 5. Get Output and Post-process
    # Your specs: Output 0 is painted_image
    output_data = interpreter.get_tensor(output_details[0]['index'])
    result_array = output_data[0] # Remove batch dim
    
    # Denormalize from [0, 1] to [0, 255]
    result_array = np.clip(result_array * 255.0, 0, 255).astype(np.uint8)
    
    return Image.fromarray(result_array)


def main():
    print(f"Starting batch inpainting with AOTGAN...")
    
    # 1. Load the TFLite model ONCE
    try:
        interpreter = tflite.Interpreter(model_path=MODEL_FILE)
        interpreter.allocate_tensors()
    except Exception as e:
        print(f"CRITICAL ERROR: Could not load model '{MODEL_FILE}'.")
        print(f"Details: {e}")
        print("Make sure the path is correct relative to where you run this script.")
        return

    # 2. Create output directory if it doesn't exist
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print(f"Output will be saved to: {OUTPUT_DIR}")

    # 3. Scan the images directory
    processed_count = 0
    skipped_count = 0
    
    if not os.path.exists(IMAGE_DIR):
        print(f"Error: Image directory '{IMAGE_DIR}' does not exist.")
        return

    print(f"Scanning {IMAGE_DIR} for images...")
    
    all_files = os.listdir(IMAGE_DIR)
    image_files = [f for f in all_files if f.lower().endswith(('.png', '.jpg', '.jpeg'))]

    if not image_files:
        print("No images found in the 'images' directory.")
        return

    for img_filename in image_files:
        # Get 'my_photo' from 'my_photo.jpg'
        base_name, _ = os.path.splitext(img_filename)
        
        # Find the matching mask in the MASK_DIR
        mask_path = find_corresponding_mask(base_name, MASK_DIR)
        
        if mask_path:
            image_path = os.path.join(IMAGE_DIR, img_filename)
            print(f"Processing: {img_filename}  (Mask: {os.path.basename(mask_path)})")
            
            try:
                # Run the model
                result_image = run_inpainting(interpreter, image_path, mask_path)
                
                # Save the result (always as PNG for consistent quality)
                output_filename = base_name + '.png'
                output_path = os.path.join(OUTPUT_DIR, output_filename)
                result_image.save(output_path)
                
                processed_count += 1
                
            except Exception as e:
                print(f"  -> ERROR processing {img_filename}: {e}")
                skipped_count += 1
        else:
            # No mask was found for this image
            print(f"Skipping:   {img_filename} (No corresponding mask found in {MASK_DIR})")
            skipped_count += 1

    print("\n--- Batch Complete ---")
    print(f"Successfully processed: {processed_count}")
    print(f"Skipped (errors or no mask): {skipped_count}")


if __name__ == "__main__":
    main()
