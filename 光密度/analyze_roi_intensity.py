#!/usr/bin/env python3
"""
Analyze ROI intensity in SVS image and update GeoJSON labels based on optical density
"""

import json
import numpy as np
import csv
from pathlib import Path
from PIL import Image, ImageDraw
import openslide
from collections import defaultdict

def load_geojson(geojson_file):
    """Load GeoJSON file"""
    with open(geojson_file, 'r', encoding='utf-8') as f:
        return json.load(f)

def save_geojson(geojson_data, output_file):
    """Save GeoJSON file"""
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(geojson_data, f, ensure_ascii=False, indent=2)

def get_roi_intensity(slide, polygon_coords, downsample=16):
    """
    Calculate average intensity within a ROI polygon
    
    Args:
        slide: OpenSlide object
        polygon_coords: List of [x, y] coordinates
        downsample: Downsampling factor for faster processing
    
    Returns:
        dict with intensity statistics
    """
    # Get polygon bounds
    xs = [coord[0] for coord in polygon_coords]
    ys = [coord[1] for coord in polygon_coords]
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    
    # Calculate region size at downsampled level
    width = int((max_x - min_x) / downsample)
    height = int((max_y - min_y) / downsample)
    
    if width <= 0 or height <= 0:
        return None
    
    # Read region from slide
    try:
        region = slide.read_region(
            (int(min_x), int(min_y)),
            0,  # level
            (int(max_x - min_x), int(max_y - min_y))
        )
        
        # Resize for faster processing
        region = region.resize((width, height), Image.LANCZOS)
        
        # Convert to grayscale
        region_gray = region.convert('L')
        region_array = np.array(region_gray)
        
        # Create mask for the polygon
        mask = Image.new('L', (width, height), 0)
        draw = ImageDraw.Draw(mask)
        
        # Scale polygon coordinates to downsampled size
        scaled_coords = [
            ((x - min_x) / downsample, (y - min_y) / downsample)
            for x, y in polygon_coords
        ]
        draw.polygon(scaled_coords, fill=255)
        mask_array = np.array(mask)
        
        # Calculate intensity statistics within the polygon
        roi_pixels = region_array[mask_array > 0]
        
        if len(roi_pixels) == 0:
            return None
        
        stats = {
            'mean': float(np.mean(roi_pixels)),
            'median': float(np.median(roi_pixels)),
            'max': float(np.max(roi_pixels)),
            'min': float(np.min(roi_pixels)),
            'std': float(np.std(roi_pixels)),
            'pixel_count': len(roi_pixels)
        }
        
        return stats
        
    except Exception as e:
        print(f"  ⚠️  Error reading region: {e}")
        return None

def analyze_and_update_geojson(svs_file, geojson_file, output_file, 
                               threshold_mean=50, 
                               target_label='AQP1',
                               analyze_only=True):
    """
    Analyze ROI intensity and update GeoJSON labels
    
    Args:
        svs_file: Path to SVS image file
        geojson_file: Path to input GeoJSON file
        output_file: Path to output GeoJSON file
        threshold_mean: Mean intensity threshold for positive detection
        target_label: Label to assign to positive ROIs
        analyze_only: If True, only analyze without modifying (for testing)
    """
    print("=" * 80)
    print("🔬 Analyzing ROI Intensity and Updating Labels")
    print("=" * 80)
    print(f"SVS file: {svs_file}")
    print(f"GeoJSON file: {geojson_file}")
    print(f"Threshold (mean): {threshold_mean}")
    print(f"Target label: {target_label}")
    print(f"Mode: {'Analysis Only' if analyze_only else 'Update Labels'}")
    print()
    
    # Load SVS image
    print("📥 Loading SVS image...")
    try:
        slide = openslide.OpenSlide(str(svs_file))
        print(f"   Image dimensions: {slide.dimensions}")
        print(f"   Level count: {slide.level_count}")
    except Exception as e:
        print(f"❌ Error loading SVS file: {e}")
        print("⚠️  Note: This script requires openslide-python package")
        print("   Install with: pip install openslide-python")
        return
    
    # Load GeoJSON
    print("📥 Loading GeoJSON...")
    geojson_data = load_geojson(geojson_file)
    features = geojson_data['features']
    print(f"   Total features: {len(features)}")
    print()
    
    # Analyze each ROI
    print("🔍 Analyzing ROI intensities...")
    intensity_stats = []
    updated_count = 0
    
    for i, feature in enumerate(features, 1):
        if i % 100 == 0:
            print(f"   Processing feature {i}/{len(features)}...")
        
        feature_id = feature.get('id', f'feature_{i}')
        current_label = feature['properties'].get('classification', 'Unknown')
        
        # Get polygon coordinates
        coords = feature['geometry']['coordinates'][0]
        
        # Calculate intensity
        stats = get_roi_intensity(slide, coords, downsample=16)
        
        if stats is None:
            continue
        
        # Determine if ROI is positive based on threshold
        is_positive = stats['mean'] > threshold_mean
        
        # Store statistics
        intensity_stats.append({
            'id': feature_id,
            'current_label': current_label,
            'mean_intensity': stats['mean'],
            'median_intensity': stats['median'],
            'max_intensity': stats['max'],
            'is_positive': is_positive,
            'suggested_label': target_label if is_positive else current_label
        })
        
        # Update label if not in analysis-only mode
        if not analyze_only and is_positive and current_label != target_label:
            feature['properties']['classification'] = target_label
            updated_count += 1
    
    # Close slide
    slide.close()
    
    print()
    print("=" * 80)
    print("📊 Analysis Results")
    print("=" * 80)
    
    if len(intensity_stats) == 0:
        print("❌ No ROIs could be analyzed (possibly due to errors)")
        return
    
    # Calculate overall statistics
    mean_intensities = [s['mean_intensity'] for s in intensity_stats]
    positive_count = sum(1 for s in intensity_stats if s['is_positive'])
    
    print(f"Total analyzed ROIs: {len(intensity_stats)}")
    print(f"Positive ROIs (> {threshold_mean}): {positive_count} ({100*positive_count/len(intensity_stats):.1f}%)")
    print(f"Mean intensity overall: {np.mean(mean_intensities):.2f}")
    print(f"Median intensity overall: {np.median(mean_intensities):.2f}")
    print(f"Intensity range: {np.min(mean_intensities):.1f} - {np.max(mean_intensities):.1f}")
    
    # Save results to CSV cache for later use
    cache_file = Path(str(geojson_file).replace('.geojson', f'_intensity_cache.csv'))
    print(f"\n💾 Saving intensity cache to: {cache_file}")
    
    with open(cache_file, 'w', newline='', encoding='utf-8') as f:
        fieldnames = ['id', 'current_label', 'mean_intensity', 'median_intensity', 
                     'max_intensity', 'is_positive', 'suggested_label']
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(intensity_stats)
    
    # Save updated GeoJSON if not analyze-only mode
    if not analyze_only:
        print(f"💾 Saving updated GeoJSON to: {output_file}")
        save_geojson(geojson_data, output_file)
        print(f"   Updated {updated_count} labels to {target_label}")
    
    print()
    print("✅ Analysis complete!")
    
    return intensity_stats

def main():
    """
    Main function for command line usage
    """
    # Default file paths - modify these as needed
    svs_file = Path('/Users/xinxiaohong/Desktop/match/K2023-0460/qp1_620_jin.svs')
    geojson_file = Path('/Users/xinxiaohong/Desktop/match/trans_geojsons/K2023-0460/K2023-0460-AQP1.geojson')
    output_file = Path('/Users/xinxiaohong/Desktop/match/K2023-0460-AQP1_analyzed.geojson')
    
    # Check if files exist
    if not svs_file.exists():
        print(f"❌ SVS file not found: {svs_file}")
        return
    
    if not geojson_file.exists():
        print(f"❌ GeoJSON file not found: {geojson_file}")
        return
    
    # Run analysis (analysis-only mode by default for safety)
    analyze_and_update_geojson(
        svs_file=svs_file,
        geojson_file=geojson_file,
        output_file=output_file,
        threshold_mean=50,
        target_label='AQP1',
        analyze_only=True  # Set to False to actually update labels
    )

if __name__ == '__main__':
    main()
