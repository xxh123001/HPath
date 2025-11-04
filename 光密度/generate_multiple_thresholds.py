#!/usr/bin/env python3
"""
Generate multiple GeoJSON files with different thresholds
Load them into QuPath to see which threshold works best
"""

import json
import csv
from pathlib import Path

def load_intensity_cache(cache_file):
    """Load cached intensity data"""
    roi_data = {}
    with open(cache_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            roi_data[row['id']] = {
                'current_label': row['current_label'],
                'mean_intensity': float(row['mean_intensity']),
                'median_intensity': float(row['median_intensity']),
                'max_intensity': float(row['max_intensity'])
            }
    return roi_data

def generate_geojson_with_threshold(input_geojson, output_geojson, roi_data, threshold, target_label='AQP1'):
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
        
        # If intensity > threshold, set to target_label (AQP1)
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
    """
    Generate GeoJSON files with different thresholds
    """
    print("=" * 80)
    print("🎯 Generating Multiple Threshold GeoJSON Files")
    print("=" * 80)
    print()
    
    # File paths
    cache_file = Path('/Users/xinxiaohong/Desktop/match/roi_intensity_cache_K2023-0460_AQP1.csv')
    input_geojson = Path('/Users/xinxiaohong/Desktop/match/trans_geojsons/K2023-0460/K2023-0460-AQP1.geojson')
    output_dir = Path('/Users/xinxiaohong/Desktop/match/threshold_test')
    
    # Create output directory
    output_dir.mkdir(exist_ok=True)
    
    # Check if cache exists
    if not cache_file.exists():
        print(f"❌ Cache file not found: {cache_file}")
        print("   Please run the analysis first to generate the cache")
        return
    
    # Load intensity data
    print(f"📥 Loading intensity data from {cache_file.name}...")
    roi_data = load_intensity_cache(cache_file)
    print(f"   Loaded {len(roi_data)} ROIs")
    print()
    
    # Calculate statistics
    intensities = [roi['mean_intensity'] for roi in roi_data.values()]
    import numpy as np
    
    print("📊 Intensity Statistics:")
    print(f"   Mean: {np.mean(intensities):.2f}")
    print(f"   Median: {np.median(intensities):.2f}")
    print(f"   25th percentile: {np.percentile(intensities, 25):.2f}")
    print(f"   75th percentile: {np.percentile(intensities, 75):.2f}")
    print()
    
    # Define thresholds to test
    thresholds = [30, 40, 50, 60, 70, 80]
    
    print(f"🔄 Generating {len(thresholds)} GeoJSON files with different thresholds...")
    print()
    
    results = []
    
    for threshold in thresholds:
        output_file = output_dir / f"K2023-0460-AQP1_threshold{threshold}.geojson"
        
        updated_count = generate_geojson_with_threshold(
            input_geojson,
            output_file,
            roi_data,
            threshold
        )
        
        # Calculate statistics
        positive_count = sum(1 for roi in roi_data.values() if roi['mean_intensity'] > threshold)
        positive_pct = 100 * positive_count / len(roi_data)
        
        results.append({
            'threshold': threshold,
            'file': output_file.name,
            'updated': updated_count,
            'positive_count': positive_count,
            'positive_pct': positive_pct
        })
        
        print(f"✅ Threshold {threshold:3d} → {output_file.name}")
        print(f"   Updated {updated_count} labels | {positive_count} ROIs ({positive_pct:.1f}%) would be AQP1")
    
    print()
    print("=" * 80)
    print("📊 Summary Table")
    print("=" * 80)
    print(f"{'Threshold':<12} {'AQP1 ROIs':<15} {'Percentage':<12} {'File Name':<40}")
    print("-" * 80)
    for r in results:
        print(f"{r['threshold']:<12} {r['positive_count']:<15} {r['positive_pct']:>6.1f}%      {r['file']:<40}")
    
    print()
    print("=" * 80)
    print("✅ Done!")
    print("=" * 80)
    print(f"\n📁 All files saved to: {output_dir}")
    print()
    print("🎮 How to use in QuPath:")
    print("   1. Open your SVS file (qp1_620_jin.svs) in QuPath")
    print("   2. Go to: Objects → Import objects → From GeoJSON")
    print("   3. Select one of the generated files to test")
    print("   4. Compare the results to find the best threshold")
    print("   5. Repeat with different threshold files")
    print()

if __name__ == '__main__':
    main()
