#!/usr/bin/env python3
"""
Analyze ROI intensity for any marker channel
"""

import json
import csv
import sys
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np
import openslide

def calculate_intensity(svs_path, polygon_coords, downsample=16):
    """
    Calculates mean intensity within a polygon ROI from an SVS image.
    """
    try:
        with openslide.OpenSlide(svs_path) as slide:
            # Get bounding box of the polygon
            min_x = min(p[0] for p in polygon_coords)
            max_x = max(p[0] for p in polygon_coords)
            min_y = min(p[1] for p in polygon_coords)
            max_y = max(p[1] for p in polygon_coords)

            # Calculate region to read from SVS
            read_x = int(min_x)
            read_y = int(min_y)
            read_width = int(max_x - min_x)
            read_height = int(max_y - min_y)

            if read_width <= 0 or read_height <= 0:
                return 0.0

            # Read region at a downsampled level
            level = slide.get_best_level_for_downsample(downsample)
            
            # Read the region
            region = slide.read_region((read_x, read_y), level, (read_width, read_height))
            
            # Resize for faster processing
            width, height = region.size
            if width == 0 or height == 0:
                return 0.0
 
            # Convert to grayscale
            region_gray = region.convert('L')
            region_array = np.array(region_gray)
            
            # Create mask for the polygon
            mask = Image.new('L', (width, height), 0)
            draw = ImageDraw.Draw(mask)
            
            # Scale polygon coordinates to downsampled size
            downsample_factor = slide.level_downsamples[level]
            scaled_coords = [
                ((x - min_x) / downsample_factor, (y - min_y) / downsample_factor)
                for x, y in polygon_coords
            ]
        
            # Draw polygon on mask
            draw.polygon([tuple(p) for p in scaled_coords], fill=255)
            mask_array = np.array(mask)

            # Apply mask and calculate mean intensity
            masked_region = region_array[mask_array == 255]
            if masked_region.size > 0:
                return float(np.mean(masked_region))
            else:
                return 0.0

    except Exception as e:
        print(f"  ⚠️  Error: {e}")
        return 0.0

def analyze_marker(case_name, marker_name, svs_filename):
    """
    Analyze a specific marker for a case
    """
    print("=" * 80)
    print(f"🔬 Analyzing {case_name} - {marker_name}")
    print("=" * 80)
    print()
    
    # File paths
    geojson_path = Path(f'/Users/xinxiaohong/Desktop/match/trans_geojsons/{case_name}/{case_name}-{marker_name}.geojson')
    svs_path = Path(f'/Users/xinxiaohong/Desktop/match/{case_name}/{svs_filename}')
    cache_file = Path(f'/Users/xinxiaohong/Desktop/match/roi_intensity_cache_{case_name}_{marker_name}.csv')

    # Check files exist
    if not geojson_path.exists():
        print(f"❌ GeoJSON file not found: {geojson_path}")
        return False
    
    if not svs_path.exists():
        print(f"❌ SVS file not found: {svs_path}")
        return False
    
    print(f"📁 GeoJSON: {geojson_path}")
    print(f"📁 SVS: {svs_path}")
    print(f"📁 Cache: {cache_file}")
    print()
    
    # Load GeoJSON
    print("📥 Loading GeoJSON...")
    with open(geojson_path, 'r', encoding='utf-8') as f:
        geojson = json.load(f)
    
    features = geojson.get('features', [])
    print(f"   Found {len(features)} ROIs")
    print()
    
    # Analyze intensities
    print("🔍 Analyzing ROI intensities...")
    results = []
    
    for i, feature in enumerate(features, 1):
        if i % 50 == 0:
            print(f"   Progress: {i}/{len(features)} ({100*i/len(features):.1f}%)")
        
        feature_id = feature.get('id', f'feature_{i}')
        current_label = feature['properties'].get('classification', 'Unknown')
        
        # Get polygon coordinates
        coords = feature['geometry']['coordinates'][0]
        
        # Calculate intensity
        mean_intensity = calculate_intensity(str(svs_path), coords, downsample=16)
        
        results.append({
            'id': feature_id,
            'current_label': current_label,
            'mean_intensity': mean_intensity,
            'median_intensity': mean_intensity,  # For compatibility
            'max_intensity': mean_intensity,     # For compatibility
        })
    
    print()
    print("💾 Saving results to cache file...")
    
    # Save to CSV cache
    with open(cache_file, 'w', newline='', encoding='utf-8') as f:
        fieldnames = ['id', 'current_label', 'mean_intensity', 'median_intensity', 'max_intensity']
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(results)
    
    # Calculate statistics
    intensities = [r['mean_intensity'] for r in results]
    
    print()
    print("=" * 80)
    print("📊 Analysis Results")
    print("=" * 80)
    print(f"Total ROIs analyzed: {len(results)}")
    print(f"Mean intensity: {np.mean(intensities):.2f}")
    print(f"Median intensity: {np.median(intensities):.2f}")
    print(f"Min intensity: {np.min(intensities):.2f}")
    print(f"Max intensity: {np.max(intensities):.2f}")
    print(f"Std deviation: {np.std(intensities):.2f}")
    print()
    print(f"✅ Cache saved to: {cache_file}")
    print()
    
    return True

def main():
    """
    Main analysis function
    """
    if len(sys.argv) >= 4:
        case_name = sys.argv[1]
        marker_name = sys.argv[2]
        svs_filename = sys.argv[3]
    else:
        # Default values
        case_name = 'K2023-0460'
        marker_name = 'AQP1'
        svs_filename = 'qp1_620_jin.svs'
    
    print("🎯 Channel Intensity Analyzer")
    print("=" * 80)
    print(f"Case: {case_name}")
    print(f"Marker: {marker_name}")
    print(f"SVS file: {svs_filename}")
    print()
    
    success = analyze_marker(case_name, marker_name, svs_filename)
    
    if success:
        print("✅ Analysis completed successfully!")
        print()
        print("🎮 Next steps:")
        print("   1. Use generate_multiple_thresholds.py to create threshold files")
        print("   2. Test different thresholds in QuPath")
    else:
        print("❌ Analysis failed!")

if __name__ == '__main__':
    main()
