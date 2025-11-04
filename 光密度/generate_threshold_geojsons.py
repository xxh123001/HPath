#!/usr/bin/env python3
"""
Generate multiple GeoJSON files with different thresholds for any marker
"""

import json
import csv
import sys
from pathlib import Path
import numpy as np

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

def generate_geojson_with_threshold(input_geojson, output_geojson, roi_data, threshold, target_label):
    """
    Generate a GeoJSON file with labels updated based on threshold
    """
    # Load original GeoJSON
    with open(input_geojson, 'r', encoding='utf-8') as f:
        geojson = json.load(f)
    
    updated_count = 0
    
    # Update labels based on threshold
    for feature in geojson['features']:
        feature_id = feature.get('id', '')
        
        if feature_id not in roi_data:
            continue
        
        roi = roi_data[feature_id]
        intensity = roi['mean_intensity']
        
        # If intensity > threshold, set to target_label
        if intensity > threshold:
            old_label = feature['properties'].get('classification', '')
            if old_label != target_label:
                feature['properties']['classification'] = target_label
                updated_count += 1
    
    # Save new GeoJSON
    with open(output_geojson, 'w', encoding='utf-8') as f:
        json.dump(geojson, f, ensure_ascii=False, indent=2)
    
    return updated_count

def main():
    """Generate threshold GeoJSON files"""
    
    if len(sys.argv) != 5:
        print("Usage: python3 generate_threshold_geojsons.py <case_name> <marker> <threshold> <target_label>")
        print("Example: python3 generate_threshold_geojsons.py K2023-0460 AQP1 60 AQP1")
        return
    
    case_name = sys.argv[1]
    marker = sys.argv[2]
    threshold = int(sys.argv[3])
    target_label = sys.argv[4]
    
    print("=" * 80)
    print(f"🎯 Generating Threshold GeoJSON for {case_name} - {marker}")
    print("=" * 80)
    print(f"Threshold: {threshold}")
    print(f"Target label: {target_label}")
    print()
    
    # File paths
    cache_file = Path(f'/Users/xinxiaohong/Desktop/match/roi_intensity_cache_{case_name}_{marker}.csv')
    input_geojson = Path(f'/Users/xinxiaohong/Desktop/match/trans_geojsons/{case_name}/{case_name}-{marker}.geojson')
    output_dir = Path(f'/Users/xinxiaohong/Desktop/match/threshold_batch/{case_name}/{marker}')
    output_file = output_dir / f"{case_name}-{marker}_threshold{threshold}.geojson"
    
    # Create output directory
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Check if files exist
    if not cache_file.exists():
        print(f"❌ Cache file not found: {cache_file}")
        print("   Please run analyze_channel_intensity.py first")
        return
    
    if not input_geojson.exists():
        print(f"❌ Input GeoJSON not found: {input_geojson}")
        return
    
    # Load intensity data
    print(f"📥 Loading intensity cache...")
    roi_data = load_intensity_cache(cache_file)
    print(f"   Loaded {len(roi_data)} ROIs")
    
    # Generate threshold GeoJSON
    print(f"🔄 Generating threshold GeoJSON...")
    updated_count = generate_geojson_with_threshold(
        input_geojson,
        output_file,
        roi_data,
        threshold,
        target_label
    )
    
    # Calculate statistics
    positive_count = sum(1 for roi in roi_data.values() if roi['mean_intensity'] > threshold)
    positive_pct = 100 * positive_count / len(roi_data)
    
    print()
    print("=" * 80)
    print("✅ Generation Complete!")
    print("=" * 80)
    print(f"Output file: {output_file}")
    print(f"Updated {updated_count} labels")
    print(f"ROIs above threshold: {positive_count} ({positive_pct:.1f}%)")
    print()
    print("🎮 Load into QuPath:")
    print("   1. Open your SVS file in QuPath")
    print("   2. Objects → Import objects → From GeoJSON")
    print(f"   3. Select: {output_file}")

if __name__ == '__main__':
    main()
