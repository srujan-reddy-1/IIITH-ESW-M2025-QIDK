#!/usr/bin/env python3
import json
import os
from pathlib import Path
from collections import defaultdict


def classify_complexity(features):
    """Classify image complexity based on texture features."""
    texture = features.get('texture', {})
    edge_density = texture.get('edge_density', 0)
    laplacian_var = texture.get('laplacian_variance', 0)
    
    if edge_density > 0.15 or laplacian_var > 500:
        return "High"
    elif edge_density > 0.08 or laplacian_var > 200:
        return "Medium"
    else:
        return "Low"


def classify_brightness(features):
    """Classify image brightness."""
    brightness = features.get('colors', {}).get('brightness_mean', 128)
    
    if brightness > 180:
        return "Very Bright"
    elif brightness > 130:
        return "Bright"
    elif brightness > 80:
        return "Medium"
    else:
        return "Dark"


def classify_saturation(features):
    """Classify image saturation."""
    saturation = features.get('colors', {}).get('saturation_mean', 0)
    
    if saturation > 100:
        return "High Saturation"
    elif saturation > 50:
        return "Medium Saturation"
    else:
        return "Low Saturation"


def classify_mask_coverage(mask_features):
    """Classify mask coverage area."""
    area_ratio = mask_features.get('mask_area_ratio')
    
    if area_ratio is None:
        return "No Mask"
    elif area_ratio > 0.5:
        return "Large Coverage"
    elif area_ratio > 0.2:
        return "Medium Coverage"
    elif area_ratio > 0:
        return "Small Coverage"
    else:
        return "No Coverage"


def classify_mask_location(mask_features):
    """Classify mask location."""
    location = mask_features.get('mask_location')
    
    if location == "center":
        return "Center"
    elif location == "edge_or_corner":
        return "Edge/Corner"
    else:
        return "No Mask"


def classify_mask_fragmentation(mask_features):
    """Classify mask fragmentation."""
    fragments = mask_features.get('mask_fragments')
    
    if fragments is None or fragments == 0:
        return "No Mask"
    elif fragments == 1:
        return "Single Region"
    elif fragments <= 3:
        return "Few Regions"
    else:
        return "Many Regions"


def classify_content_type(features):
    """Classify content type based on available features."""
    categories = []
    
    if features.get('faces_detected', 0) > 0:
        categories.append("Contains Faces")
    
    if features.get('text_present', False):
        categories.append("Contains Text")
    
    return categories if categories else ["General"]


def analyze_json_files(json_folder, output_file):
    """Analyze all JSON files and create grouped output."""
    json_folder = Path(json_folder)
    
    # Storage for groupings
    groups = {
        'complexity': defaultdict(list),
        'brightness': defaultdict(list),
        'saturation': defaultdict(list),
        'mask_coverage': defaultdict(list),
        'mask_location': defaultdict(list),
        'mask_fragmentation': defaultdict(list),
        'content_type': defaultdict(list),
    }
    
    # Read all JSON files
    json_files = sorted(list(json_folder.glob('*.json')))
    
    if not json_files:
        print(f"No JSON files found in {json_folder}")
        return
    
    print(f"Found {len(json_files)} JSON files to analyze...")
    
    for json_file in json_files:
        try:
            with open(json_file, 'r') as f:
                data = json.load(f)
            
            image_name = Path(data.get('image_path', json_file.stem)).stem
            features = data.get('image_features', {})
            mask_features = data.get('mask_features', {})
            
            # Classify and group
            groups['complexity'][classify_complexity(features)].append(image_name)
            groups['brightness'][classify_brightness(features)].append(image_name)
            groups['saturation'][classify_saturation(features)].append(image_name)
            groups['mask_coverage'][classify_mask_coverage(mask_features)].append(image_name)
            groups['mask_location'][classify_mask_location(mask_features)].append(image_name)
            groups['mask_fragmentation'][classify_mask_fragmentation(mask_features)].append(image_name)
            
            content_categories = classify_content_type(features)
            for cat in content_categories:
                groups['content_type'][cat].append(image_name)
                
        except Exception as e:
            print(f"Error processing {json_file.name}: {e}")
    
    # Write output
    with open(output_file, 'w') as f:
        f.write("="*80 + "\n")
        f.write("IMAGE CLASSIFICATION GROUPS\n")
        f.write("="*80 + "\n\n")
        
        # Complexity groups
        f.write("COMPLEXITY (Based on Edge Density & Texture)\n")
        f.write("-"*80 + "\n")
        for level in ["High", "Medium", "Low"]:
            images = groups['complexity'][level]
            f.write(f"\n{level} Complexity ({len(images)} images):\n")
            for img in sorted(images):
                f.write(f"  - {img}\n")
        
        # Brightness groups
        f.write("\n\n" + "="*80 + "\n")
        f.write("BRIGHTNESS\n")
        f.write("-"*80 + "\n")
        for level in ["Very Bright", "Bright", "Medium", "Dark"]:
            images = groups['brightness'][level]
            f.write(f"\n{level} ({len(images)} images):\n")
            for img in sorted(images):
                f.write(f"  - {img}\n")
        
        # Saturation groups
        f.write("\n\n" + "="*80 + "\n")
        f.write("SATURATION\n")
        f.write("-"*80 + "\n")
        for level in ["High Saturation", "Medium Saturation", "Low Saturation"]:
            images = groups['saturation'][level]
            f.write(f"\n{level} ({len(images)} images):\n")
            for img in sorted(images):
                f.write(f"  - {img}\n")
        
        # Mask coverage groups
        f.write("\n\n" + "="*80 + "\n")
        f.write("MASK COVERAGE\n")
        f.write("-"*80 + "\n")
        for level in ["Large Coverage", "Medium Coverage", "Small Coverage", "No Coverage", "No Mask"]:
            images = groups['mask_coverage'][level]
            if images:
                f.write(f"\n{level} ({len(images)} images):\n")
                for img in sorted(images):
                    f.write(f"  - {img}\n")
        
        # Mask location groups
        f.write("\n\n" + "="*80 + "\n")
        f.write("MASK LOCATION\n")
        f.write("-"*80 + "\n")
        for level in ["Center", "Edge/Corner", "No Mask"]:
            images = groups['mask_location'][level]
            if images:
                f.write(f"\n{level} ({len(images)} images):\n")
                for img in sorted(images):
                    f.write(f"  - {img}\n")
        
        # Mask fragmentation groups
        f.write("\n\n" + "="*80 + "\n")
        f.write("MASK FRAGMENTATION\n")
        f.write("-"*80 + "\n")
        for level in ["Single Region", "Few Regions", "Many Regions", "No Mask"]:
            images = groups['mask_fragmentation'][level]
            if images:
                f.write(f"\n{level} ({len(images)} images):\n")
                for img in sorted(images):
                    f.write(f"  - {img}\n")
        
        # Content type groups
        f.write("\n\n" + "="*80 + "\n")
        f.write("CONTENT TYPE\n")
        f.write("-"*80 + "\n")
        for category in sorted(groups['content_type'].keys()):
            images = groups['content_type'][category]
            f.write(f"\n{category} ({len(images)} images):\n")
            for img in sorted(images):
                f.write(f"  - {img}\n")
        
        f.write("\n" + "="*80 + "\n")
    
    print(f"\nGroups saved to {output_file}")
    print(f"Total images analyzed: {len(json_files)}")


if __name__ == "__main__":
    import sys
    
    if len(sys.argv) < 3:
        print("Usage: python group_images.py <json_folder> <output_txt_file>")
        print("Example: python group_images.py ./json_results ./image_groups.txt")
        sys.exit(1)
    
    json_folder = sys.argv[1]
    output_file = sys.argv[2]
    
    analyze_json_files(json_folder, output_file)
