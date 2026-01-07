import pandas as pd
import sys

def parse_report_file(file_path):
    """
    Parses the table format of the summary report.
    Expected line format: "Image Name | PSNR | SSIM | LPIPS"
    """
    data = {}
    print(f"Reading file: {file_path}")
    
    try:
        with open(file_path, 'r') as f:
            lines = f.readlines()
            
        for line in lines:
            line = line.strip()
            
            # Skip empty lines, separators, and headers
            if not line or line.startswith('-') or line.startswith('=') or 'Image Name' in line:
                continue
            
            # Skip summary stats lines (e.g., "Average PSNR: ...")
            if 'Average' in line or 'FID Score' in line:
                continue

            # Parse data line: "016.jpg | 14.0465 | 0.6042 | 0.0511"
            if '|' in line:
                parts = [p.strip() for p in line.split('|')]
                
                # We expect 4 columns: Name, PSNR, SSIM, LPIPS
                if len(parts) >= 4:
                    filename = parts[0]
                    try:
                        psnr = float(parts[1])
                        ssim = float(parts[2])
                        lpips = float(parts[3])
                        
                        data[filename] = {'psnr': psnr, 'ssim': ssim, 'lpips': lpips}
                    except ValueError:
                        # Skip lines that look like data but don't have valid numbers
                        continue
                        
        print(f" -> Found {len(data)} valid entries.")
        return data

    except FileNotFoundError:
        print(f"Error: The file '{file_path}' was not found.")
        sys.exit(1)

def main():
    # --- CONFIGURATION ---
    # Hardcode your file paths here
    # Model A = Label 0
    file_path_model_a = 'AOT-GAN_complete_image_summary_report.txt' 
    # Model B = Label 1
    file_path_model_b = 'lama_complete_image_summary_report.txt'
    
    output_filename = 'comparison_labels.csv'
    # ---------------------

    # 1. Parse both files
    model_a_data = parse_report_file(file_path_model_a)
    model_b_data = parse_report_file(file_path_model_b)

    if not model_a_data or not model_b_data:
        print("Error: One or both data files were empty or could not be parsed.")
        sys.exit(1)

    # 2. Compare and Label
    labeled_data = []
    skipped_count = 0

    print("\nComparing models...")
    for filename, metrics_a in model_a_data.items():
        if filename not in model_b_data:
            print(f"Warning: '{filename}' found in Model A report but not in Model B. Skipping.")
            skipped_count += 1
            continue
            
        metrics_b = model_b_data[filename]
        
        # LOGIC: LPIPS (Lower is better) > SSIM (Higher is better) > PSNR (Higher is better)
        label = -1 
        
        # Thresholds for "tie-breaking"
        LPIPS_DIFF_THRESHOLD = 0.01
        SSIM_DIFF_THRESHOLD = 0.02
        
        # 1. Compare LPIPS (Priority #1)
        diff_lpips = metrics_a['lpips'] - metrics_b['lpips']
        
        if diff_lpips < -LPIPS_DIFF_THRESHOLD:
            label = 0  # Model A is significantly better (lower LPIPS)
        elif diff_lpips > LPIPS_DIFF_THRESHOLD:
            label = 1  # Model B is significantly better (lower LPIPS)
        
        # 2. Tie-break with SSIM (Priority #2)
        else:
            diff_ssim = metrics_a['ssim'] - metrics_b['ssim']
            
            if diff_ssim > SSIM_DIFF_THRESHOLD:
                label = 0  # Model A is significantly better (higher SSIM)
            elif diff_ssim < -SSIM_DIFF_THRESHOLD:
                label = 1  # Model B is significantly better (higher SSIM)
            
            # 3. Tie-break with PSNR (Priority #3)
            else:
                if metrics_a['psnr'] >= metrics_b['psnr']:
                    label = 0
                else:
                    label = 1
                
        labeled_data.append([filename, label])

    # 3. Save to CSV
    if labeled_data:
        df = pd.DataFrame(labeled_data, columns=['filename', 'label'])
        df.to_csv(output_filename, index=False)
        print(f"\nSuccess! Generated labels for {len(df)} images.")
        print(f"Output saved to: {output_filename}")
        
        # Show class balance
        count_0 = df[df['label'] == 0].shape[0]
        count_1 = df[df['label'] == 1].shape[0]
        print(f"Class Balance -> Model A wins: {count_0}, Model B wins: {count_1}")
    else:
        print("\nError: No common images found between the two reports.")

if __name__ == "__main__":
    main()
