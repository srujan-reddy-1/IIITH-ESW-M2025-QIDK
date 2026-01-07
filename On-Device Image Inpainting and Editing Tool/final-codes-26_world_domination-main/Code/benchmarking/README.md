# Mass Benchmarking - Usage Guide

This guide explains how to run each Python file in this project to reproduce the benchmarking results for any given dataset.

---

## 📁 Project Structure Overview

### Root Directory Files
- **`test_model.py`** - Utility to inspect TFLite model specifications (input/output shapes, quantization)
- **`similarity.py`** - Batch comparison tool to calculate image quality metrics (PSNR, SSIM, LPIPS, FID)

### Model Directories
- **`aot_gan/`** - AOT-GAN inpainting model implementation
  - `batch_inpaint.py` - Batch inpainting script for AOT-GAN
  - `images/` - Input images directory
  - `masks/` - Input masks directory
  - `inpainted/` - Output inpainted images directory

- **`lama/`** - LaMa inpainting model implementation
  - `batch_inpaint.py` - Batch inpainting script for LaMa
  - `images/` - Input images directory
  - `masks/` - Input masks directory
  - `inpainted/` - Output inpainted images directory

### Dataset Directory
- **`esw_dataset/`** - Dataset preparation and classification tools
  - `router_classifier.py` - Image feature extraction and classification
  - `group_images.py` - Groups images by characteristics
  - `images/` - Original images
  - `masks/` - Mask files
  - `ideal/` - Ground truth/ideal images
  - `json_results/` - Classification results in JSON format
  - `image_classification.txt` - Grouped classification results

### Models Directory
**These models are not included in the github repo as they are too big, please get them from the Qualcomm AI hub, and put them in a folder called models to integrate with the code**
- **`models/`** - Pre-trained TFLite models
  - `aotgan.tflite` - AOT-GAN model
  - `lama_dilated.tflite` - LaMa model

### Results Directories
- **`aot_gan_sim_results/`** - AOT-GAN complete image comparison results
- **`aot_gan_sim_mask_results/`** - AOT-GAN masked region comparison results
- **`lama_sim_results/`** - LaMa complete image comparison results
- **`lama_sim_mask_results/`** - LaMa masked region comparison results

---

## 🚀 Complete Workflow Guide

### Prerequisites

Install required Python packages:
```bash
pip install tensorflow opencv-python pillow numpy torch torchvision lpips pytorch-fid scikit-image mediapipe
```

Optional packages for enhanced features:
```bash
pip install pytesseract clip  # For OCR and content classification
```

Alternative for TFLite (lighter than full TensorFlow):
```bash
pip install tflite-runtime opencv-python pillow numpy torch torchvision lpips pytorch-fid scikit-image mediapipe
```

---

## 📋 Step-by-Step Instructions

### Step 1: Inspect Model Specifications (Optional)

**File Location:** `test_model.py` (root directory)

**Purpose:** View input/output shapes and quantization details of TFLite models.

**How to Run:**
```bash
python test_model.py
```

**Configuration:**
- Edit line 51 to change the model path:
  ```python
  model_path = "./models/aotgan.tflite"  # or "./models/lama_dilated.tflite"
  ```

**Output:** Console output showing model specifications.

---

### Step 2: Classify and Analyze Dataset Images

**File Location:** `esw_dataset/router_classifier.py`

**Purpose:** Extract features from images (texture, color, faces, text detection, etc.) and create classification metadata.

**How to Run:**

**Single Image:**
```bash
cd esw_dataset
python router_classifier.py <image_path> [mask_path]
```

**Batch Processing:**
```bash
cd esw_dataset
python router_classifier.py --folder ./images --masks ./masks --output ./json_results
```

**Arguments:**
- `--folder` - Directory containing images to classify
- `--masks` - (Optional) Directory containing corresponding mask files
- `--output` - (Optional) Directory where JSON classification files will be saved

**Output:**
- Individual JSON files in the specified output folder
- Each JSON contains detailed feature analysis for one image

---

### Step 3: Group Images by Characteristics

**File Location:** `esw_dataset/group_images.py`

**Purpose:** Analyze classification JSONs and group images by complexity, brightness, saturation, mask properties, etc.

**How to Run:**
```bash
cd esw_dataset
python group_images.py ./json_results ./image_classification.txt
```

**Arguments:**
- First argument: Path to folder containing JSON files from Step 2
- Second argument: Output text file path for grouped results

**Output:**
- Text file with images grouped by:
  - Complexity (High/Medium/Low)
  - Brightness (Very Bright/Bright/Medium/Dark)
  - Saturation (High/Medium/Low)
  - Mask Coverage (Large/Medium/Small/None)
  - Mask Location (Center/Edge/Corner)
  - Mask Fragmentation (Single/Few/Many Regions)
  - Content Type (Faces/Text/General)

---

### Step 4: Run Inpainting with AOT-GAN

**File Location:** `aot_gan/batch_inpaint.py`

**Purpose:** Apply AOT-GAN model to inpaint images using corresponding masks.

**Setup:**
1. Navigate to the AOT-GAN directory:
   ```bash
   cd aot_gan
   ```

2. Prepare your data:
   - Place input images in `images/` directory
   - Place corresponding masks in `masks/` directory
   - Ensure image and mask filenames match (excluding extension)

**How to Run:**
```bash
python batch_inpaint.py
```

**Configuration (edit in script if needed):**
```python
MODEL_FILE = "../models/aotgan.tflite"  # Line 18
IMAGE_DIR = "images"                     # Line 19
MASK_DIR = "masks"                       # Line 20
OUTPUT_DIR = "inpainted"                 # Line 21
MODEL_INPUT_SIZE = (512, 512)            # Line 24
```

**Output:**
- Inpainted images saved in `inpainted/` directory
- Console shows processing progress

**Requirements:**
- Images and masks must have matching base filenames
- Model automatically resizes inputs to 512x512
- Supports .png, .jpg, .jpeg formats

---

### Step 5: Run Inpainting with LaMa

**File Location:** `lama/batch_inpaint.py`

**Purpose:** Apply LaMa model to inpaint images using corresponding masks.

**Setup:**
1. Navigate to the LaMa directory:
   ```bash
   cd lama
   ```

2. Prepare your data:
   - Place input images in `images/` directory
   - Place corresponding masks in `masks/` directory
   - Ensure image and mask filenames match (excluding extension)

**How to Run:**
```bash
python batch_inpaint.py
```

**Configuration (edit in script if needed):**
```python
MODEL_FILE = "../models/lama_dilated.tflite"  # Line 18
IMAGE_DIR = "images"                           # Line 19
MASK_DIR = "masks"                             # Line 20
OUTPUT_DIR = "inpainted"                       # Line 21
MODEL_INPUT_SIZE = (512, 512)                  # Line 24
```

**Output:**
- Inpainted images saved in `inpainted/` directory
- Console shows processing progress

---

### Step 6: Calculate Similarity Metrics

**File Location:** `similarity.py` (root directory)

**Purpose:** Compare inpainted images with ideal/ground truth images and calculate quality metrics.

**How to Run:**

**For Complete Image Comparison:**
```bash
python similarity.py \
  -i ./esw_dataset/ideal \
  -c ./aot_gan/inpainted \
  -m ./esw_dataset/masks \
  -o ./aot_gan_sim_results
```

**For Masked Region Only Comparison:**
```bash
python similarity.py \
  -i ./esw_dataset/ideal \
  -c ./aot_gan/inpainted \
  -m ./esw_dataset/masks \
  -o ./aot_gan_sim_mask_results
```

**Arguments:**
- `-i, --ideal_folder` - Path to ground truth/ideal images
- `-c, --compared_folder` - Path to inpainted images to evaluate
- `-m, --mask_folder` - Path to mask images
- `-o, --output_folder` - Path where results will be saved

**Metrics Calculated:**
- **PSNR** (Peak Signal-to-Noise Ratio) - Higher is better
- **SSIM** (Structural Similarity Index) - Higher is better (0-1)
- **LPIPS** (Learned Perceptual Image Patch Similarity) - Lower is better
- **FID** (Fréchet Inception Distance) - Lower is better (dataset-level)

**Output Files:**
1. Individual comparison images: `{filename}_comparison.jpg`
   - Side-by-side view with metrics overlay
2. `summary_report.txt` - Detailed text report with averages and per-image metrics
3. `summary_report.jpg` - Visual summary of average metrics

---

## 🔄 Complete Reproduction Workflow

To reproduce the entire benchmarking process for a new dataset:

### 1. Prepare Dataset
```bash
# Organize your data
esw_dataset/
├── images/      # Original images
├── masks/       # Inpainting masks
└── ideal/       # Ground truth images
```

### 2. Classify Dataset
```bash
cd esw_dataset
python router_classifier.py --folder ./images --masks ./masks --output ./json_results
python group_images.py ./json_results ./image_classification.txt
cd ..
```

### 3. Copy Images to Model Directories
```bash
# For AOT-GAN
cp esw_dataset/images/* aot_gan/images/
cp esw_dataset/masks/* aot_gan/masks/

# For LaMa
cp esw_dataset/images/* lama/images/
cp esw_dataset/masks/* lama/masks/
```

### 4. Run Inpainting
```bash
# AOT-GAN
cd aot_gan
python batch_inpaint.py
cd ..

# LaMa
cd lama
python batch_inpaint.py
cd ..
```

### 5. Calculate Metrics
```bash
# AOT-GAN results
python similarity.py \
  -i ./esw_dataset/ideal \
  -c ./aot_gan/inpainted \
  -m ./esw_dataset/masks \
  -o ./aot_gan_sim_results

# LaMa results
python similarity.py \
  -i ./esw_dataset/ideal \
  -c ./lama/inpainted \
  -m ./esw_dataset/masks \
  -o ./lama_sim_results
```

### 6. Review Results
Check the output directories for:
- Summary reports (TXT and JPG)
- Individual comparison images
- Detailed per-image metrics


## 🐛 Troubleshooting

### Issue: Module Not Found Errors
**Solution:** Install missing packages:
```bash
pip install tensorflow opencv-python pillow numpy torch torchvision lpips pytorch-fid scikit-image mediapipe
```

### Issue: CUDA/GPU Errors
**Solution:** The scripts will automatically fall back to CPU. For GPU support, install CUDA-enabled PyTorch.

### Issue: Model File Not Found
**Solution:** Verify model files exist in `models/` directory and paths in scripts are correct.

### Issue: Image/Mask Mismatch
**Solution:** Ensure images and masks have the same base filename (different extensions are OK).

### Issue: Memory Errors
**Solution:** Process fewer images at once or reduce batch size in FID calculation (line 303 in similarity.py).

---

## 📊 Understanding Results

### Good Quality Indicators:
- **PSNR:** > 30 dB (excellent), 25-30 dB (good), < 25 dB (poor)
- **SSIM:** > 0.9 (excellent), 0.8-0.9 (good), < 0.8 (poor)
- **LPIPS:** < 0.1 (excellent), 0.1-0.3 (good), > 0.3 (poor)
- **FID:** < 10 (excellent), 10-50 (good), > 50 (poor)

### Comparing Models:
- Higher PSNR/SSIM = better structural preservation
- Lower LPIPS = better perceptual quality
- Lower FID = better overall dataset similarity

---

## 📝 Notes

1. **Image Formats:** All scripts support PNG, JPG, and JPEG formats
2. **Automatic Resizing:** Models automatically resize inputs to 512x512
3. **Filename Matching:** Image and mask pairs must have matching base filenames
4. **GPU Acceleration:** Automatically used if available (PyTorch/TensorFlow)
5. **Batch Processing:** All scripts support multiple images automatically

---

## 📧 Additional Information

For questions or issues, refer to individual script docstrings or comments for more detailed parameter descriptions.
