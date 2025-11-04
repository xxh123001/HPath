#!/usr/bin/env python3
"""
Process K2023-3068 case data and generate threshold GeoJSON files
Adapted from existing scripts for the 光密度 project structure
"""

import json
import csv
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np
import openslide

# Markers to process - note: Cd31 not CD31
MARKERS = ['AQP1', 'AQP2', 'Cd31', 'LRP2', 'PanCK', 'PNA', 'SLC8A1', 'UMOD']

# Thresholds to generate (1-100, every single threshold)
THRESHOLDS = list(range(1, 101))

def convert_json_to_geojson(json_data, marker_label):
    """
    Convert the JSON format to GeoJSON format
    Filter by marker label (e.g., 'KV1' for one marker)
    """
    features = []
    
    for item in json_data:
        # Get the label - assuming different markers have different labels
        label = item.get('label', '')
        
        # Convert points to GeoJSON coordinate format
        points = item.get('points', [])
        if not points:
            continue
        
        # Points are already in [x, y] format
        coordinates = [[float(p[0]), float(p[1])] for p in points]
        # Close the polygon if not already closed
        if coordinates[0] != coordinates[-1]:
            coordinates.append(coordinates[0])
        
        feature = {
            "type": "Feature",
            "id": item.get('id', ''),
            "geometry": {
                "type": "Polygon",
                "coordinates": [coordinates]
            },
            "properties": {
                "classification": label,
                "object_type": "annotation"
            }
        }
        features.append(feature)
    
    geojson = {
        "type": "FeatureCollection",
        "features": features
    }
    
    return geojson

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
        print(f"      Warning: Error calculating intensity: {e}")
        return 0.0

def analyze_intensity(marker_name, geojson_data, svs_path, cache_file):
    """Analyze intensity for a marker and save to cache."""
    
    # Check if cache already exists
    if cache_file.exists():
        print(f"      ✓ Cache exists, skipping analysis")
        return True
    
    # Check if SVS file exists
    if not svs_path.exists():
        print(f"      ✗ SVS not found: {svs_path}")
        return False
    
    features = geojson_data.get('features', [])
    total = len(features)
    
    if total == 0:
        print(f"      ✗ No features found")
        return False
    
    # Analyze each ROI
    results = []
    
    print(f"      Analyzing {total} ROIs...")
    for idx, feature in enumerate(features, 1):
        if idx % 100 == 0 or idx == total:
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
    cache_file.parent.mkdir(parents=True, exist_ok=True)
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

def generate_threshold_geojson(geojson_data, output_geojson, roi_data, threshold, target_label):
    """Generate a GeoJSON file with labels updated based on threshold"""
    
    # Make a copy of the geojson data
    output_data = json.loads(json.dumps(geojson_data))
    
    updated_count = 0
    
    for feature in output_data['features']:
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
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    return updated_count

def process_marker(case_name, marker, json_dir, svs_dir, output_dir):
    """Process a single marker"""
    
    print(f"\n   🔬 Processing marker: {marker}")
    
    # Find corresponding JSON file
    json_file = json_dir / f"{case_name}-{marker}.json"
    if not json_file.exists():
        print(f"      ⏭️  No JSON file found for {marker}, skipping")
        return
    
    # Find corresponding SVS file
    svs_files = list(svs_dir.glob(f'*{marker}.svs'))
    if not svs_files:
        print(f"      ⏭️  No SVS file found for {marker}, skipping")
        return
    
    svs_path = svs_files[0]
    print(f"      📄 SVS file: {svs_path.name}")
    print(f"      📄 JSON file: {json_file.name}")
    
    # Load JSON data
    with open(json_file, 'r', encoding='utf-8') as f:
        input_json_data = json.load(f)
    
    # Convert JSON to GeoJSON (use all features for now)
    print(f"      🔄 Converting to GeoJSON format...")
    geojson_data = convert_json_to_geojson(input_json_data, marker)
    
    if not geojson_data['features']:
        print(f"      ⏭️  No features for {marker}, skipping")
        return
    
    print(f"      Found {len(geojson_data['features'])} ROIs")
    
    # Cache file
    cache_file = output_dir / f'cache_{case_name}_{marker}.csv'
    
    # Step 1: Analyze intensity
    print(f"      📊 Analyzing intensity...")
    if not analyze_intensity(marker, geojson_data, svs_path, cache_file):
        return
    
    # Step 2: Load cache
    roi_data = load_intensity_cache(cache_file)
    
    # Step 3: Generate threshold GeoJSONs
    print(f"      🎯 Generating threshold GeoJSONs...")
    marker_output_dir = output_dir / marker
    marker_output_dir.mkdir(parents=True, exist_ok=True)
    
    for threshold in THRESHOLDS:
        output_file = marker_output_dir / f"{case_name}-{marker}_threshold{threshold}.geojson"
        
        updated_count = generate_threshold_geojson(
            geojson_data,
            output_file,
            roi_data,
            threshold,
            marker
        )
        
        positive_count = sum(1 for roi in roi_data.values() if roi['mean_intensity'] > threshold)
        positive_pct = 100 * positive_count / len(roi_data) if roi_data else 0
        
        print(f"         Threshold {threshold:3d} → {positive_count:4d} ROIs ({positive_pct:5.1f}%)")
    
    print(f"      ✅ Generated {len(THRESHOLDS)} threshold files for {marker}")

def main():
    """Main processing function"""
    
    print("=" * 80)
    print("🚀 Process K2023-3068 Case Data")
    print("=" * 80)
    print()
    
    case_name = 'K2023-3068'
    json_dir = Path(f'/Users/felix/Desktop/光密度/input/{case_name}_json')
    svs_dir = Path(f'/Users/felix/Desktop/光密度/input/{case_name}_svs')
    output_dir = Path('/Users/felix/Desktop/光密度/output/test')
    
    # Create output directory
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Check directories exist
    if not json_dir.exists():
        print(f"❌ JSON directory not found: {json_dir}")
        return
    
    if not svs_dir.exists():
        print(f"❌ SVS directory not found: {svs_dir}")
        return
    
    print(f"📁 JSON directory: {json_dir}")
    print(f"📁 SVS directory: {svs_dir}")
    print(f"📁 Output directory: {output_dir}")
    print()
    
    # Process each marker
    for marker in MARKERS:
        try:
            process_marker(case_name, marker, json_dir, svs_dir, output_dir)
        except Exception as e:
            print(f"❌ Error processing {marker}: {e}")
            import traceback
            traceback.print_exc()
            continue
    
    print()
    print("=" * 80)
    print("✅ Processing Complete!")
    print("=" * 80)
    print(f"\n📁 Generated files are in: {output_dir}")
    print()
    print("🎮 How to use in QuPath:")
    print("   1. Open a case SVS file in QuPath")
    print("   2. Objects → Import objects → From GeoJSON")
    print("   3. Navigate to the test output folder")
    print("   4. Select a threshold file to test")
    print("   5. Compare different thresholds to find the best one")
    print()

if __name__ == '__main__':
    main()

