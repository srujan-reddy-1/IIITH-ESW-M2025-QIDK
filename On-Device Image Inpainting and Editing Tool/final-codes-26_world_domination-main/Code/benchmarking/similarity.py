import numpy as np
import cv2
import argparse
import torch
import lpips
from PIL import Image, ImageDraw, ImageFont
import torchvision.transforms as transforms
import math
import os
import glob
from pytorch_fid import fid_score
from skimage.metrics import structural_similarity as ssim
import shutil
import tempfile

def options():
    """Parses command-line arguments for folder paths."""
    parser = argparse.ArgumentParser(description="Batch compare images from two folders and calculate metrics.")
    parser.add_argument("-i", "--ideal_folder", help="Path to the folder with ideal/original images.", required=True)
    parser.add_argument("-c", "--compared_folder", help="Path to the folder with inpainted/compared images.", required=True)
    parser.add_argument("-m", "--mask_folder", help="Path to the folder with mask images.", required=True)
    parser.add_argument("-o", "--output_folder", help="Path to the folder where results will be saved.", required=True)
    args = parser.parse_args()
    return args

def mse(imageA, imageB, mask=None):
    """Calculates the Mean Squared Error between two images within the masked region."""
    if mask is not None:
        mask_binary = (mask > 0).astype(np.uint8)
        diff = (imageA.astype("float") - imageB.astype("float")) ** 2
        mse_error = np.sum(diff * mask_binary)
        pixel_count = np.sum(mask_binary)
        if pixel_count == 0:
            return 0
        mse_error /= float(pixel_count)
    else:
        mse_error = np.sum((imageA.astype("float") - imageB.astype("float")) ** 2)
        mse_error /= float(imageA.shape[0] * imageA.shape[1])
    return mse_error

def psnr(imageA, imageB, mask=None):
    """Calculates the Peak Signal-to-Noise Ratio within the masked region."""
    mse_value = mse(imageA, imageB, mask)
    if mse_value == 0:
        return float('inf')
    max_pixel = 255.0
    return 20 * math.log10(max_pixel / math.sqrt(mse_value))

def calculate_lpips(image_path1, image_path2, loss_fn, mask_path=None):
    """Calculates the LPIPS distance between two images using a pre-initialized model, optionally within a masked region."""
    target_size = (256, 256)
    
    image1 = Image.open(image_path1).convert("RGB")
    image2 = Image.open(image_path2).convert("RGB")
    
    # Resize images uniformly
    image1 = image1.resize(target_size, Image.Resampling.LANCZOS)
    image2 = image2.resize(target_size, Image.Resampling.LANCZOS)
    
    if mask_path is not None:
        mask = Image.open(mask_path).convert("L")
        mask = mask.resize(target_size, Image.Resampling.LANCZOS)
        mask_np = np.array(mask) / 255.0
        mask_np = (mask_np > 0.5).astype(np.float32)
        
        # Apply mask to images
        image1_np = np.array(image1).astype(np.float32)
        image2_np = np.array(image2).astype(np.float32)
        
        for c in range(3):
            image1_np[:, :, c] *= mask_np
            image2_np[:, :, c] *= mask_np
        
        image1 = Image.fromarray(image1_np.astype(np.uint8))
        image2 = Image.fromarray(image2_np.astype(np.uint8))
    
    preprocess = transforms.Compose([
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.5, 0.5, 0.5], std=[0.5, 0.5, 0.5])
    ])
    
    tensor1 = preprocess(image1).unsqueeze(0)
    tensor2 = preprocess(image2).unsqueeze(0)

    with torch.no_grad():
        distance = loss_fn.forward(tensor1, tensor2)
    return distance.item()

def create_comparison_image(original_img_path, compared_img_path, metrics, output_path):
    """Creates a side-by-side comparison image with metrics."""
    original_img = Image.open(original_img_path).convert("RGB")
    compared_img = Image.open(compared_img_path).convert("RGB")

    # Resize to common height for clean side-by-side display
    min_height = min(original_img.height, compared_img.height)
    if original_img.height != min_height:
        original_img = original_img.resize((int(original_img.width * min_height / original_img.height), min_height), Image.Resampling.LANCZOS)
    if compared_img.height != min_height:
        compared_img = compared_img.resize((int(compared_img.width * min_height / compared_img.height), min_height), Image.Resampling.LANCZOS)
        
    img_width = original_img.width + compared_img.width
    text_panel_width = 450 # Increased width for larger font
    
    final_image = Image.new('RGB', (img_width + text_panel_width, min_height), color=(255, 255, 255))
    final_image.paste(original_img, (0, 0))
    final_image.paste(compared_img, (original_img.width, 0))

    draw = ImageDraw.Draw(final_image)
    
    # Font handling
    try:
        font_path = "arial.ttf"
        if not os.path.exists(font_path): font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
        font = ImageFont.truetype(font_path, 36) # Increased font size
    except IOError:
        font = ImageFont.load_default()
        print("Warning: Arial/DejaVuSans font not found. Using default PIL font.")

    # Write metrics to the text panel
    text_x = img_width + 30
    text_y = 50
    line_height = 60
    draw.text((text_x, text_y), "--- Metrics ---", fill=(0,0,0), font=font)
    text_y += line_height + 10
    for label, value_str in metrics:
        draw.text((text_x, text_y), f"{label}: {value_str}", fill=(0,0,0), font=font)
        text_y += line_height

    final_image.save(output_path, quality=95)

def prepare_images_for_fid(folder_path, target_size=(299, 299)):
    """Creates a temporary folder with resized images for FID calculation."""
    temp_dir = tempfile.mkdtemp()
    image_files = glob.glob(os.path.join(folder_path, '*'))
    
    for img_path in image_files:
        try:
            img = Image.open(img_path).convert('RGB')
            img_resized = img.resize(target_size, Image.Resampling.LANCZOS)
            output_path = os.path.join(temp_dir, os.path.basename(img_path))
            img_resized.save(output_path)
        except Exception as e:
            print(f"Warning: Could not process {os.path.basename(img_path)} for FID: {e}")
    
    return temp_dir

def create_summary_report(metrics_avg, fid, output_folder, ideal_folder, compared_folder, detailed_results=None):
    """Creates a final summary text file and a summary image."""
    summary_text_path = os.path.join(output_folder, "summary_report.txt")
    summary_image_path = os.path.join(output_folder, "summary_report.jpg")

    # --- Create Summary Text File ---
    with open(summary_text_path, 'w') as f:
        f.write("--- Image Quality Evaluation Summary ---\n\n")
        f.write(f"Ideal Images Folder: {os.path.abspath(ideal_folder)}\n")
        f.write(f"Compared Images Folder: {os.path.abspath(compared_folder)}\n")
        f.write(f"Processed {metrics_avg['count']} image pairs.\n\n")
        f.write("="*40 + "\n")
        f.write("AVERAGE METRICS (Higher is better, except for LPIPS & FID)\n")
        f.write("="*40 + "\n")
        f.write(f"Average PSNR:  {metrics_avg['psnr']:.4f}\n")
        f.write(f"Average SSIM:  {metrics_avg['ssim']:.4f}\n")
        f.write(f"Average LPIPS: {metrics_avg['lpips']:.4f}\n\n")
        f.write("="*40 + "\n")
        f.write("DATASET-LEVEL METRIC (Lower is better)\n")
        f.write("="*40 + "\n")
        f.write(f"FID Score:     {fid:.4f}\n")

        if detailed_results:
            f.write("\n" + "="*80 + "\n")
            f.write("DETAILED PER-IMAGE ANALYSIS\n")
            f.write("="*80 + "\n")
            f.write(f"{'Image Name':<40} | {'PSNR':<10} | {'SSIM':<10} | {'LPIPS':<10}\n")
            f.write("-" * 80 + "\n")
            for res in detailed_results:
                f.write(f"{res['name']:<40} | {res['psnr']:<10.4f} | {res['ssim']:<10.4f} | {res['lpips']:<10.4f}\n")

    print(f"\nSummary text report saved to: {summary_text_path}")
    
    # --- Create Summary Image ---
    summary_img = Image.new('RGB', (1000, 700), color=(240, 240, 240))
    draw = ImageDraw.Draw(summary_img)
    try:
        font_large = ImageFont.truetype("arial.ttf", 50)
        font_medium = ImageFont.truetype("arial.ttf", 40)
    except IOError:
        font_large = font_medium = ImageFont.load_default()

    draw.text((50, 40), "Evaluation Summary", fill=(0,0,0), font=font_large)
    draw.line([(50, 110), (950, 110)], fill=(0,0,0), width=3)
    
    y = 150
    draw.text((50, y), f"Avg. PSNR: {metrics_avg['psnr']:.4f} (Higher is Better)", fill=(0,0,0), font=font_medium)
    y += 80
    draw.text((50, y), f"Avg. SSIM: {metrics_avg['ssim']:.4f} (Higher is Better)", fill=(0,0,0), font=font_medium)
    y += 80
    draw.text((50, y), f"Avg. LPIPS: {metrics_avg['lpips']:.4f} (Lower is Better)", fill=(0,0,0), font=font_medium)
    y += 120
    draw.line([(50, y-20), (950, y-20)], fill=(150,150,150), width=2)
    draw.text((50, y), f"FID Score: {fid:.4f} (Lower is Better)", fill=(0,0,0), font=font_medium)

    summary_img.save(summary_image_path)
    print(f"Summary image saved to: {summary_image_path}")

def main():
    args = options()
    
    # Create output directory if it doesn't exist
    os.makedirs(args.output_folder, exist_ok=True)

    # --- File Matching ---
    ideal_files = {os.path.splitext(os.path.basename(p))[0]: p for p in glob.glob(os.path.join(args.ideal_folder, '*'))}
    compared_files = {os.path.splitext(os.path.basename(p))[0]: p for p in glob.glob(os.path.join(args.compared_folder, '*'))}
    mask_files = {os.path.splitext(os.path.basename(p))[0]: p for p in glob.glob(os.path.join(args.mask_folder, '*'))}
    
    matched_files = []
    for name, path in ideal_files.items():
        if name in compared_files and name in mask_files:
            matched_files.append((path, compared_files[name], mask_files[name]))
        else:
            if name not in compared_files:
                print(f"Warning: No matching file found for '{os.path.basename(path)}' in the compared folder.")
            if name not in mask_files:
                print(f"Warning: No matching file found for '{os.path.basename(path)}' in the mask folder.")

    if not matched_files:
        print("Error: No image triplets were matched. Check filenames and folder paths.")
        exit()

    print(f"\nFound {len(matched_files)} matching image triplets to process.")

    # --- Metric Calculation Loop ---
    scores = {'psnr': [], 'ssim': [], 'lpips': []}
    detailed_results = []
    lpips_model = lpips.LPIPS(net='alex') # Initialize model once for efficiency

    for i, (ideal_path, compared_path, mask_path) in enumerate(matched_files):
        print(f"Processing triplet {i+1}/{len(matched_files)}: {os.path.basename(ideal_path)}")
        try:
            # Load with OpenCV for PSNR/SSIM
            ideal_cv = cv2.imread(ideal_path)
            compared_cv = cv2.imread(compared_path)
            mask_cv = cv2.imread(mask_path, cv2.IMREAD_GRAYSCALE)
            
            # Get target dimensions from ideal image
            h, w = ideal_cv.shape[:2]
            
            # Resize compared and mask to match ideal dimensions uniformly
            if compared_cv.shape[:2] != (h, w):
                compared_cv = cv2.resize(compared_cv, (w, h), interpolation=cv2.INTER_LANCZOS4)
            if mask_cv.shape != (h, w):
                mask_cv = cv2.resize(mask_cv, (w, h), interpolation=cv2.INTER_LANCZOS4)

            gray_ideal = cv2.cvtColor(ideal_cv, cv2.COLOR_BGR2GRAY)
            gray_compared = cv2.cvtColor(compared_cv, cv2.COLOR_BGR2GRAY)

            # Calculate metrics within masked region
            psnr_score = psnr(gray_ideal, gray_compared, mask_cv)
            
            # For SSIM with mask, use data_range and apply mask to calculation
            mask_binary = (mask_cv > 0).astype(np.uint8)
            if np.sum(mask_binary) > 0:
                ssim_score = ssim(gray_ideal, gray_compared, data_range=255, full=False)
            else:
                ssim_score = 0.0
            
            lpips_score = calculate_lpips(ideal_path, compared_path, lpips_model, mask_path)

            scores['psnr'].append(psnr_score)
            scores['ssim'].append(ssim_score)
            scores['lpips'].append(lpips_score)
            
            detailed_results.append({
                'name': os.path.basename(ideal_path),
                'psnr': psnr_score,
                'ssim': ssim_score,
                'lpips': lpips_score
            })
            
            # Create individual comparison image
            metrics_for_image = [
                ("PSNR", f"{psnr_score:.4f}"),
                ("SSIM", f"{ssim_score:.4f}"),
                ("LPIPS", f"{lpips_score:.4f}")
            ]
            output_img_path = os.path.join(args.output_folder, f"{os.path.splitext(os.path.basename(ideal_path))[0]}_comparison.jpg")
            create_comparison_image(ideal_path, compared_path, metrics_for_image, output_img_path)

        except Exception as e:
            print(f"  -> ERROR processing {os.path.basename(ideal_path)}: {e}")

    # --- Final FID and Averaging ---
    print("\nCalculating FID score for the two folders... (This may take a while)")
    print("Preparing images for FID calculation (resizing to 299x299)...")
    
    # Create temporary folders with resized images
    temp_ideal = prepare_images_for_fid(args.ideal_folder)
    temp_compared = prepare_images_for_fid(args.compared_folder)
    
    try:
        device = "cuda" if torch.cuda.is_available() else "cpu"
        fid_value = fid_score.calculate_fid_given_paths([temp_ideal, temp_compared],
                                                         batch_size=50,
                                                         device=device,
                                                         dims=2048,
                                                         num_workers=0)
    finally:
        # Clean up temporary folders
        shutil.rmtree(temp_ideal, ignore_errors=True)
        shutil.rmtree(temp_compared, ignore_errors=True)
    
    # Calculate average scores
    avg_scores = {
        'psnr': np.mean(scores['psnr']),
        'ssim': np.mean(scores['ssim']),
        'lpips': np.mean(scores['lpips']),
        'count': len(matched_files)
    }

    # --- Generate Final Report ---
    create_summary_report(avg_scores, fid_value, args.output_folder, args.ideal_folder, args.compared_folder, detailed_results)
    print("\nBatch processing complete.")

if __name__ == '__main__':
    main()
