#!/usr/bin/env python3
"""
Interactive Threshold Adjuster for ROI Classification
Allows real-time threshold adjustment and preview of changes
"""

import json
import numpy as np
import csv
from pathlib import Path
from PIL import Image, ImageDraw
import openslide
from collections import defaultdict, Counter

def load_geojson(geojson_file):
    """Load GeoJSON file"""
    with open(geojson_file, 'r', encoding='utf-8') as f:
        return json.load(f)

def save_geojson(geojson_data, output_file):
    """Save GeoJSON file"""
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(geojson_data, f, ensure_ascii=False, indent=2)

def get_roi_intensity(slide, polygon_coords, downsample=16):
    """Calculate average intensity within a ROI polygon"""
    xs = [coord[0] for coord in polygon_coords]
    ys = [coord[1] for coord in polygon_coords]
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    
    width = int((max_x - min_x) / downsample)
    height = int((max_y - min_y) / downsample)
    
    if width <= 0 or height <= 0:
        return None
    
    try:
        region = slide.read_region(
            (int(min_x), int(min_y)),
            0,
            (int(max_x - min_x), int(max_y - min_y))
        )
        
        region = region.resize((width, height), Image.LANCZOS)
        region_gray = region.convert('L')
        region_array = np.array(region_gray)
        
        mask = Image.new('L', (width, height), 0)
        draw = ImageDraw.Draw(mask)
        
        scaled_coords = [
            ((x - min_x) / downsample, (y - min_y) / downsample)
            for x, y in polygon_coords
        ]
        draw.polygon(scaled_coords, fill=255)
        mask_array = np.array(mask)
        
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
        print(f"  ⚠️  Error calculating intensity: {e}")
        return None

def load_intensity_cache(cache_file):
    """Load cached intensity data"""
    roi_data = {}
    with open(cache_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            roi_data[row['id']] = {
                'current_label': row['current_label'],
                'mean_intensity': float(row['mean_intensity']),
                'median_intensity': float(row.get('median_intensity', row['mean_intensity'])),
                'max_intensity': float(row.get('max_intensity', row['mean_intensity']))
            }
    return roi_data

def preview_threshold_changes(roi_data, threshold, target_label):
    """Preview what changes would be made with a given threshold"""
    
    changes = []
    stats = {'positive': 0, 'negative': 0, 'changed': 0}
    
    for roi_id, roi in roi_data.items():
        intensity = roi['mean_intensity']
        current_label = roi['current_label']
        
        if intensity > threshold:
            new_label = target_label
            stats['positive'] += 1
        else:
            new_label = current_label
            stats['negative'] += 1
        
        if current_label != new_label:
            changes.append({
                'id': roi_id,
                'intensity': intensity,
                'old_label': current_label,
                'new_label': new_label
            })
            stats['changed'] += 1
    
    return changes, stats

def analyze_intensity_distribution(roi_data):
    """Analyze the distribution of intensities"""
    
    intensities = [roi['mean_intensity'] for roi in roi_data.values()]
    
    stats = {
        'count': len(intensities),
        'mean': np.mean(intensities),
        'median': np.median(intensities),
        'min': np.min(intensities),
        'max': np.max(intensities),
        'std': np.std(intensities),
        'percentiles': {
            '10th': np.percentile(intensities, 10),
            '25th': np.percentile(intensities, 25),
            '75th': np.percentile(intensities, 75),
            '90th': np.percentile(intensities, 90),
        }
    }
    
    return stats, intensities

def interactive_threshold_adjustment(case_name, marker_name, target_label):
    """
    Interactive threshold adjustment session
    """
    
    # File paths
    cache_file = Path(f'/Users/xinxiaohong/Desktop/match/roi_intensity_cache_{case_name}_{marker_name}.csv')
    input_geojson = Path(f'/Users/xinxiaohong/Desktop/match/trans_geojsons/{case_name}/{case_name}-{marker_name}.geojson')
    
    if not cache_file.exists():
        print(f"❌ Cache file not found: {cache_file}")
        print("   Please run analyze_channel_intensity.py first")
        return
    
    if not input_geojson.exists():
        print(f"❌ Input GeoJSON not found: {input_geojson}")
        return
    
    print("=" * 80)
    print(f"🎛️  Interactive Threshold Adjuster")
    print("=" * 80)
    print(f"Case: {case_name}")
    print(f"Marker: {marker_name}")
    print(f"Target label: {target_label}")
    print()
    
    # Load data
    print("📥 Loading intensity data...")
    roi_data = load_intensity_cache(cache_file)
    print(f"   Loaded {len(roi_data)} ROIs")
    
    # Analyze distribution
    print("📊 Analyzing intensity distribution...")
    dist_stats, intensities = analyze_intensity_distribution(roi_data)
    
    print()
    print("=" * 80)
    print("📊 Intensity Distribution")
    print("=" * 80)
    print(f"Total ROIs: {dist_stats['count']}")
    print(f"Mean: {dist_stats['mean']:.2f}")
    print(f"Median: {dist_stats['median']:.2f}")
    print(f"Range: {dist_stats['min']:.1f} - {dist_stats['max']:.1f}")
    print(f"Std deviation: {dist_stats['std']:.2f}")
    print()
    print("Percentiles:")
    print(f"  10th: {dist_stats['percentiles']['10th']:.1f}")
    print(f"  25th: {dist_stats['percentiles']['25th']:.1f}")
    print(f"  75th: {dist_stats['percentiles']['75th']:.1f}")
    print(f"  90th: {dist_stats['percentiles']['90th']:.1f}")
    print()
    
    # Interactive loop
    while True:
        print("=" * 80)
        print("🎛️  Threshold Testing")
        print("=" * 80)
        print("Enter a threshold value to test (or 'q' to quit):")
        print("Suggested starting points:")
        print(f"  - Conservative: {dist_stats['percentiles']['75th']:.0f}")
        print(f"  - Moderate: {dist_stats['median']:.0f}")
        print(f"  - Liberal: {dist_stats['percentiles']['25th']:.0f}")
        print()
        
        user_input = input("Threshold value: ").strip()
        
        if user_input.lower() in ['q', 'quit', 'exit']:
            break
        
        try:
            threshold = float(user_input)
        except ValueError:
            print("❌ Please enter a valid number")
            continue
        
        # Preview changes
        changes, stats = preview_threshold_changes(roi_data, threshold, target_label)
        
        print()
        print(f"🔍 Preview for threshold = {threshold}")
        print("-" * 40)
        print(f"ROIs above threshold: {stats['positive']} ({100*stats['positive']/len(roi_data):.1f}%)")
        print(f"ROIs below threshold: {stats['negative']} ({100*stats['negative']/len(roi_data):.1f}%)")
        print(f"Labels to be changed: {stats['changed']}")
        
        if changes:
            print()
            print("First 10 changes:")
            for i, change in enumerate(changes[:10]):
                print(f"  {i+1:2d}. ID:{change['id'][:8]}... "
                     f"Intensity:{change['intensity']:6.1f} "
                     f"{change['old_label']} → {change['new_label']}")
            
            if len(changes) > 10:
                print(f"  ... and {len(changes)-10} more changes")
        
        print()
        save_choice = input("Save this threshold? (y/n): ").strip().lower()
        
        if save_choice in ['y', 'yes']:
            # Generate output filename
            output_dir = Path(f'/Users/xinxiaohong/Desktop/match/threshold_interactive/{case_name}')
            output_dir.mkdir(parents=True, exist_ok=True)
            output_file = output_dir / f"{case_name}-{marker_name}_threshold{threshold:.0f}.geojson"
            
            # Apply threshold to GeoJSON
            geojson_data = load_geojson(input_geojson)
            
            applied_changes = 0
            for feature in geojson_data['features']:
                feature_id = feature.get('id', '')
                
                if feature_id in roi_data:
                    roi = roi_data[feature_id]
                    if roi['mean_intensity'] > threshold:
                        old_label = feature['properties'].get('classification', '')
                        if old_label != target_label:
                            feature['properties']['classification'] = target_label
                            applied_changes += 1
            
            # Save the modified GeoJSON
            save_geojson(geojson_data, output_file)
            
            print(f"✅ Saved to: {output_file}")
            print(f"   Applied {applied_changes} label changes")
            print()
            print("🎮 Load into QuPath:")
            print("   1. Open your SVS file in QuPath")
            print("   2. Objects → Import objects → From GeoJSON")
            print(f"   3. Select: {output_file}")
            print()
        
        print()

def main():
    """Main interactive function"""
    
    print("🎛️  Interactive Threshold Adjuster")
    print("=" * 80)
    print()
    
    # Get case information
    case_name = input("Enter case name (e.g., K2023-0460): ").strip()
    if not case_name:
        case_name = "K2023-0460"
    
    marker_name = input("Enter marker name (e.g., AQP1): ").strip()
    if not marker_name:
        marker_name = "AQP1"
    
    target_label = input(f"Enter target label (default: {marker_name}): ").strip()
    if not target_label:
        target_label = marker_name
    
    print()
    
    interactive_threshold_adjustment(case_name, marker_name, target_label)
    
    print()
    print("👋 Thanks for using the Interactive Threshold Adjuster!")

if __name__ == '__main__':
    main()
