import cv2
import json
import numpy as np
from PIL import Image
import torch
import torchvision.transforms as T
import mediapipe as mp

# Optional modules:
try:
    import pytesseract
    OCR_AVAILABLE = True
except:
    OCR_AVAILABLE = False

# Load CLIP (content tags, embeddings)
try:
    import clip
    CLIP_AVAILABLE = True
    device = "cuda" if torch.cuda.is_available() else "cpu"
    clip_model, clip_preprocess = clip.load("ViT-B/32", device=device)
except:
    CLIP_AVAILABLE = False


# ---------------------------------------------------------
# Utility Functions
# ---------------------------------------------------------

def compute_laplacian_variance(img_gray):
    return cv2.Laplacian(img_gray, cv2.CV_64F).var()


def compute_edge_density(img_gray):
    edges = cv2.Canny(img_gray, 100, 200)
    return float(np.sum(edges > 0) / edges.size)


def dominant_colors(img, k=3):
    data = img.reshape((-1, 3)).astype(np.float32)
    _, labels, centers = cv2.kmeans(
        data, k, None,
        (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 10, 1.0),
        10, cv2.KMEANS_RANDOM_CENTERS
    )
    centers = centers.astype(int).tolist()
    return centers


def detect_faces(img):
    mp_face = mp.solutions.face_detection.FaceDetection(model_selection=1, min_detection_confidence=0.5)
    results = mp_face.process(cv2.cvtColor(img, cv2.COLOR_BGR2RGB))
    if not results.detections:
        return 0
    return len(results.detections)


def run_clip_classification(img_pil):
    if not CLIP_AVAILABLE:
        return {}
    image_input = clip_preprocess(img_pil).unsqueeze(0).to(device)
    with torch.no_grad():
        features = clip_model.encode_image(image_input).cpu().numpy()[0]
    return features.tolist()


def detect_text(img):
    if not OCR_AVAILABLE:
        return False
    text = pytesseract.image_to_string(img)
    return len(text.strip()) > 0


# ---------------------------------------------------------
# Mask classification helper
# ---------------------------------------------------------
def analyze_mask(mask):
    mask_binary = (mask > 128).astype(np.uint8)
    mask_size = float(np.sum(mask_binary) / mask_binary.size)

    # Bounding box of mask
    ys, xs = np.where(mask_binary == 1)
    if len(xs) == 0:
        bbox = None
        location = "none"
    else:
        bbox = {
            "x_min": int(xs.min()),
            "y_min": int(ys.min()),
            "x_max": int(xs.max()),
            "y_max": int(ys.max()),
        }

        # Location heuristic
        h, w = mask.shape
        cx = (xs.min() + xs.max()) / 2
        cy = (ys.min() + ys.max()) / 2
        if 0.33*w < cx < 0.66*w and 0.33*h < cy < 0.66*h:
            location = "center"
        else:
            location = "edge_or_corner"

    # Fragmentation
    num_components, _, _, _ = cv2.connectedComponentsWithStats(mask_binary, connectivity=8)
    fragments = num_components - 1  # exclude background

    return {
        "mask_area_ratio": mask_size,
        "mask_bbox": bbox,
        "mask_location": location,
        "mask_fragments": fragments,
    }


# ---------------------------------------------------------
# Main function
# ---------------------------------------------------------
def classify_image(image_path, mask_path=None):
    img = cv2.imread(image_path)
    img_rgb = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    img_gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    pil_img = Image.open(image_path).convert("RGB")

    h, w = img.shape[:2]

    # Mask
    mask_data = {}
    if mask_path:
        mask = cv2.imread(mask_path, 0)
        mask_data = analyze_mask(mask)
    else:
        mask_data = {
            "mask_area_ratio": None,
            "mask_bbox": None,
            "mask_location": None,
            "mask_fragments": None,
        }

    # Compute features
    features = {
        "resolution": {"width": w, "height": h, "aspect_ratio": round(w/h, 3)},
        "faces_detected": detect_faces(img),
        "texture": {
            "laplacian_variance": compute_laplacian_variance(img_gray),
            "edge_density": compute_edge_density(img_gray),
        },
        "colors": {
            "brightness_mean": float(np.mean(img_gray)),
            "saturation_mean": float(np.mean(cv2.cvtColor(img, cv2.COLOR_BGR2HSV)[:,:,1])),
            "dominant_colors": dominant_colors(img, k=3),
        },
        "text_present": detect_text(img),
        "clip_embedding": run_clip_classification(pil_img) if CLIP_AVAILABLE else None,
    }

    # Combine everything
    return {
        "image_path": image_path,
        "mask_path": mask_path,
        "mask_features": mask_data,
        "image_features": features,
    }


# ---------------------------------------------------------
# Batch processing function
# ---------------------------------------------------------
def classify_folder(image_folder, mask_folder=None, output_folder=None):
    """
    Classify all images in a folder.
    
    Args:
        image_folder: Path to folder containing images
        mask_folder: Optional path to folder containing corresponding masks
        output_folder: Optional path to folder where individual JSON files will be saved
    
    Returns:
        List of classification results
    """
    import os
    from pathlib import Path
    
    results = []
    image_folder = Path(image_folder)
    mask_folder = Path(mask_folder) if mask_folder else None
    output_folder = Path(output_folder) if output_folder else None
    
    # Create output folder if specified
    if output_folder:
        output_folder.mkdir(parents=True, exist_ok=True)
    
    # Supported image extensions
    extensions = {'.jpg', '.jpeg', '.png', '.bmp', '.tiff', '.webp'}
    
    # Get all image files
    image_files = sorted([f for f in image_folder.iterdir() 
                         if f.suffix.lower() in extensions and f.is_file()])
    
    print(f"Found {len(image_files)} images to process...")
    
    for idx, img_path in enumerate(image_files, 1):
        print(f"Processing {idx}/{len(image_files)}: {img_path.name}")
        
        # Try to find corresponding mask
        mask_path = None
        if mask_folder:
            mask_path = mask_folder / img_path.name
            if not mask_path.exists():
                # Try different extensions
                for ext in extensions:
                    alt_mask = mask_folder / (img_path.stem + ext)
                    if alt_mask.exists():
                        mask_path = alt_mask
                        break
                else:
                    mask_path = None
        
        try:
            result = classify_image(str(img_path), str(mask_path) if mask_path else None)
            results.append(result)
            
            # Save individual JSON file if output folder specified
            if output_folder:
                json_filename = output_folder / f"{img_path.stem}.json"
                with open(json_filename, 'w') as f:
                    json.dump(result, f, indent=4)
                print(f"  Saved to {json_filename}")
                
        except Exception as e:
            print(f"Error processing {img_path.name}: {e}")
            results.append({
                "image_path": str(img_path),
                "error": str(e)
            })
    
    print(f"\n{'='*50}")
    print(f"Processed {len(results)} images")
    if output_folder:
        print(f"JSON files saved to {output_folder}")
    
    return results


# ---------------------------------------------------------
# Run sample
# ---------------------------------------------------------
if __name__ == "__main__":
    import sys
    import os
    
    if len(sys.argv) < 2:
        print("Usage:")
        print("  Single image: python router_classifier.py <image_path> [mask_path]")
        print("  Batch folder: python router_classifier.py --folder <image_folder> [--masks <mask_folder>] [--output <output_folder>]")
        sys.exit(1)
    
    # Batch processing mode
    if sys.argv[1] == "--folder":
        image_folder = sys.argv[2]
        mask_folder = None
        output_folder = None
        
        # Parse optional arguments
        for i in range(3, len(sys.argv), 2):
            if sys.argv[i] == "--masks" and i+1 < len(sys.argv):
                mask_folder = sys.argv[i+1]
            elif sys.argv[i] == "--output" and i+1 < len(sys.argv):
                output_folder = sys.argv[i+1]
        
        results = classify_folder(image_folder, mask_folder, output_folder)
    
    # Single image mode (backward compatible)
    else:
        img_path = sys.argv[1]
        mask_path = sys.argv[2] if len(sys.argv) > 2 else None
        
        output = classify_image(img_path, mask_path)
        print(json.dumps(output, indent=4))
