#!/usr/bin/env python3
"""
Batch generate threshold GeoJSON files for all cases and all markers
"""

import json
import csv
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np
import openslide
import sys

# Marker name mapping
MARKERS = ['PanCK', 'PNA', 'AQP1', 'UMOD', 'Cd31', 'AQP2', 'SLC8A1', 'LRP2']

# Thresholds to generate (10-100, step 10)
THRESHOLDS = list(range(10, 101, 10))

def calculate_intensity(svs_path, polygon_coords, downsample=16):
    """Calculate mean intensity within a polygon ROI from an SVS image."""
    try:
        with openslide.OpenSlide(svs_path) as slide:
            min_x = min(p[0] for p in polygon_coords)
            max_x = max(p[0] for p in polygon_coords)
            min_y = min(p[1] for p in polygon_coords)
            max_y = max(p[1] for p in polygon_coords)

            read_x = int(min_x)
            read_y = int(min_y)
            read_width = int(max_x - min_x)
            read_height = int(max_y - min_y)

            if read_width <= 0 or read_height <= 0:
                return 0.0

            level = slide.get_best_level_for_downsample(downsample)
            region = slide.read_region((read_x, read_y), level, (read_width, read_height))
            
            width, height = region.size
            if width == 0 or height == 0:
                return 0.0
            
            region_gray = region.convert('L')
            region_array = np.array(region_gray)
            
            mask = Image.new('L', (width, height), 0)
            draw = ImageDraw.Draw(mask)
            
            downsample_factor = slide.level_downsamples[level]
            scaled_coords = [
                ((x - min_x) / downsample_factor, (y - min_y) / downsample_factor)
                for x, y in polygon_coords
            ]
            
            draw.polygon([tuple(p) for p in scaled_coords], fill=255)
            mask_array = np.array(mask)

            masked_region = region_array[mask_array == 255]
            if masked_region.size > 0:
                return float(np.mean(masked_region))
            else:
                return 0.0

    except Exception as e:
        return 0.0

def analyze_intensity(case_name, marker_name, geojson_path, svs_path, cache_file):
    """Analyze intensity for a marker and save to cache."""
    
    # Check if cache already exists
    if cache_file.exists():
        print(f"      ✓ Cache exists, skipping analysis")
        return True
    
    # Check if files exist
    if not geojson_path.exists():
        print(f"      ✗ GeoJSON not found: {geojson_path.name}")
        return False
    
    if not svs_path.exists():
        print(f"      ✗ SVS not found: {svs_path.name}")
        return False
    
    # Load GeoJSON
    with open(geojson_path, 'r', encoding='utf-8') as f:
        geojson = json.load(f)

    features = geojson.get('features', [])
    total = len(features)
    
    # Analyze each ROI
    results = []
    
    for idx, feature in enumerate(features, 1):
        if idx % 1000 == 0:
            print(f"      Progress: {idx}/{total} ({100*idx/total:.1f}%)")
        
        feature_id = feature.get('id', '')
        current_label = feature['properties'].get('classification', '')
        
        coords = feature['geometry']['coordinates'][0]
        intensity = calculate_intensity(str(svs_path), coords, downsample=16)
        
        results.append({
            'id': feature_id,
            'current_label': current_label,
            'mean_intensity': intensity,
            'median_intensity': intensity,
            'max_intensity': intensity,
        })
    
    # Save to cache
    with open(cache_file, 'w', newline='', encoding='utf-8') as f:
        fieldnames = ['id', 'current_label', 'mean_intensity', 'median_intensity', 'max_intensity']
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(results)
    
    print(f"      ✓ Analyzed {len(results)} ROIs, saved to cache")
    return True

def load_intensity_cache(cache_file):
    """Load cached intensity data"""
    roi_data = {}
    with open(cache_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            roi_data[row['id']] = {
                'current_label': row['current_label'],
                'mean_intensity': float(row['mean_intensity'])
            }
    return roi_data

def generate_threshold_geojson(input_geojson, output_geojson, roi_data, threshold, target_label):
    """Generate a GeoJSON file with labels updated based on threshold"""
    
    with open(input_geojson, 'r', encoding='utf-8') as f:
        geojson = json.load(f)
    
    updated_count = 0
    
    for feature in geojson['features']:
        feature_id = feature.get('id', '')
        
        if feature_id not in roi_data:
            continue
        
        roi = roi_data[feature_id]
        intensity = roi['mean_intensity']
        
        if intensity > threshold:
            old_label = feature['properties'].get('classification', '')
            if old_label != target_label:
                feature['properties']['classification'] = target_label
                updated_count += 1
    
    with open(output_geojson, 'w', encoding='utf-8') as f:
        json.dump(geojson, f, ensure_ascii=False, indent=2)
    
    return updated_count

def process_case(case_name, case_dir):
    """Process all markers for a case"""
    
    print(f"📁 Processing case: {case_name}")
    print(f"   Directory: {case_dir}")
    
    geojson_dir = Path(f'/Users/xinxiaohong/Desktop/match/trans_geojsons/{case_name}')
    if not geojson_dir.exists():
        print(f"   ❌ GeoJSON directory not found: {geojson_dir}")
        return
    
    # Find SVS file
    svs_files = list(case_dir.glob('*.svs'))
    if not svs_files:
        print(f"   ❌ No SVS files found in {case_dir}")
        return
    
    svs_file = svs_files[0]  # Use first SVS file found
    print(f"   📄 SVS file: {svs_file.name}")
    
    for marker in MARKERS:
        print(f"   🔬 Processing marker: {marker}")
        
        # File paths
        geojson_file = geojson_dir / f"{case_name}-{marker}.geojson"
        cache_file = Path(f'/Users/xinxiaohong/Desktop/match/roi_intensity_cache_{case_name}_{marker}.csv')
        
        if not geojson_file.exists():
            print(f"      ⏭️  No GeoJSON for {marker}, skipping")
            continue
        
        # Step 1: Analyze intensity (if cache doesn't exist)
        print(f"      📊 Analyzing intensity...")
        if not analyze_intensity(case_name, marker, geojson_file, svs_file, cache_file):
            continue
        
        # Step 2: Load cache
        roi_data = load_intensity_cache(cache_file)
        
        # Step 3: Generate threshold GeoJSONs
        print(f"      🎯 Generating threshold GeoJSONs...")
        output_dir = Path(f'/Users/xinxiaohong/Desktop/match/threshold_batch/{case_name}/{marker}')
        output_dir.mkdir(parents=True, exist_ok=True)
        
        for threshold in THRESHOLDS:
            output_file = output_dir / f"{case_name}-{marker}_threshold{threshold}.geojson"
            
            updated_count = generate_threshold_geojson(
                geojson_file,
                output_file,
                roi_data,
                threshold,
                marker
            )
            
            positive_count = sum(1 for roi in roi_data.values() if roi['mean_intensity'] > threshold)
            positive_pct = 100 * positive_count / len(roi_data) if roi_data else 0
            
            print(f"         Threshold {threshold:3d} → {positive_count:4d} ROIs ({positive_pct:5.1f}%)")
        
        print(f"      ✅ Generated {len(THRESHOLDS)} threshold files for {marker}")
    
    print(f"   ✅ Case {case_name} completed!")
    print()

def main():
    """Main batch processing function"""
    
    print("=" * 80)
    print("🚀 Batch Generate All Threshold GeoJSON Files")
    print("=" * 80)
    print(f"Markers: {', '.join(MARKERS)}")
    print(f"Thresholds: {min(THRESHOLDS)}-{max(THRESHOLDS)} (step {THRESHOLDS[1]-THRESHOLDS[0]})")
    print()
    
    # Find all case directories
    base_dir = Path('/Users/xinxiaohong/Desktop/match')
    case_dirs = []
    
    for item in base_dir.iterdir():
        if item.is_dir() and item.name.startswith('K2023-'):
            case_dirs.append(item)
    
    case_dirs.sort()
    
    if not case_dirs:
        print("❌ No case directories found (looking for K2023-* folders)")
        return
    
    print(f"📂 Found {len(case_dirs)} cases to process:")
    for case_dir in case_dirs:
        print(f"   - {case_dir.name}")
    print()
    
    # Process each case
    for case_dir in case_dirs:
        try:
            process_case(case_dir.name, case_dir)
        except Exception as e:
            print(f"❌ Error processing {case_dir.name}: {e}")
            continue
    
    print("=" * 80)
    print("🎉 Batch processing completed!")
    print("=" * 80)
    print()
    print("📁 Generated files are in: /Users/xinxiaohong/Desktop/match/threshold_batch/")
    print()
    print("🎮 How to use in QuPath:")
    print("   1. Open a case SVS file in QuPath")
    print("   2. Objects → Import objects → From GeoJSON")
    print("   3. Navigate to the corresponding threshold_batch folder")
    print("   4. Select a threshold file to test")
    print("   5. Compare different thresholds to find the best one")

if __name__ == '__main__':
    main()
