#!/usr/bin/env python3
"""
基于阳性面积占比的ROI分类
根据ROI内高亮度区域占总面积的比例来判定是否为阳性
"""

import json
import csv
from pathlib import Path
from PIL import Image, ImageDraw
import numpy as np
import openslide

# Markers to process
MARKERS = ['AQP1', 'AQP2', 'Cd31', 'LRP2', 'PanCK', 'PNA', 'SLC8A1', 'UMOD']

# 强度阈值列表
INTENSITY_THRESHOLDS = [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]

# 面积占比阈值列表 (阳性像素占比需要超过这个百分比才算阳性)
AREA_RATIO_THRESHOLDS = [10, 20, 30, 40, 50, 60, 70, 80, 90]

def convert_json_to_geojson(json_data, marker_label):
    """Convert JSON to GeoJSON format"""
    features = []
    
    for item in json_data:
        label = item.get('label', '')
        points = item.get('points', [])
        if not points:
            continue
        
        coordinates = [[float(p[0]), float(p[1])] for p in points]
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

def calculate_area_ratio(svs_path, polygon_coords, intensity_threshold, downsample=16):
    """
    计算ROI内强度超过阈值的像素面积占比
    
    返回：
    - mean_intensity: 平均强度
    - positive_ratio: 阳性像素占比 (0-100)
    - total_pixels: 总像素数
    - positive_pixels: 阳性像素数
    """
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
                return 0.0, 0.0, 0, 0

            level = slide.get_best_level_for_downsample(downsample)
            region = slide.read_region((read_x, read_y), level, (read_width, read_height))
            
            width, height = region.size
            if width == 0 or height == 0:
                return 0.0, 0.0, 0, 0
            
            region_gray = region.convert('L')
            region_array = np.array(region_gray)
            
            # Create mask
            mask = Image.new('L', (width, height), 0)
            draw = ImageDraw.Draw(mask)
            
            downsample_factor = slide.level_downsamples[level]
            scaled_coords = [
                ((x - min_x) / downsample_factor, (y - min_y) / downsample_factor)
                for x, y in polygon_coords
            ]
            
            draw.polygon([tuple(p) for p in scaled_coords], fill=255)
            mask_array = np.array(mask)

            # Get pixels within ROI
            masked_region = region_array[mask_array == 255]
            
            if masked_region.size > 0:
                mean_intensity = float(np.mean(masked_region))
                
                # Calculate positive pixels (intensity > threshold)
                positive_pixels = np.sum(masked_region > intensity_threshold)
                total_pixels = masked_region.size
                positive_ratio = 100.0 * positive_pixels / total_pixels
                
                return mean_intensity, positive_ratio, total_pixels, positive_pixels
            else:
                return 0.0, 0.0, 0, 0

    except Exception as e:
        print(f"      Warning: Error calculating area ratio: {e}")
        return 0.0, 0.0, 0, 0

def analyze_area_ratios(marker_name, geojson_data, svs_path, cache_file, intensity_thresholds):
    """分析每个ROI在不同强度阈值下的阳性面积占比"""
    
    if cache_file.exists():
        print(f"      ✓ Cache exists, skipping analysis")
        return True
    
    if not svs_path.exists():
        print(f"      ✗ SVS not found: {svs_path}")
        return False
    
    features = geojson_data.get('features', [])
    total = len(features)
    
    if total == 0:
        print(f"      ✗ No features found")
        return False
    
    print(f"      Analyzing {total} ROIs with {len(intensity_thresholds)} intensity thresholds...")
    results = []
    
    for idx, feature in enumerate(features, 1):
        if idx % 100 == 0 or idx == total:
            print(f"      Progress: {idx}/{total} ({100*idx/total:.1f}%)")
        
        feature_id = feature.get('id', '')
        current_label = feature['properties'].get('classification', '')
        coords = feature['geometry']['coordinates'][0]
        
        # Calculate for each intensity threshold
        row = {
            'id': feature_id,
            'current_label': current_label,
        }
        
        # Calculate mean intensity first (using threshold 0)
        mean_intensity, _, total_pixels, _ = calculate_area_ratio(
            str(svs_path), coords, 0, downsample=16
        )
        row['mean_intensity'] = mean_intensity
        row['total_pixels'] = total_pixels
        
        # Calculate positive ratio for each intensity threshold
        for intensity_thresh in intensity_thresholds:
            _, positive_ratio, _, positive_pixels = calculate_area_ratio(
                str(svs_path), coords, intensity_thresh, downsample=16
            )
            row[f'ratio_i{intensity_thresh}'] = positive_ratio
            row[f'pixels_i{intensity_thresh}'] = positive_pixels
        
        results.append(row)
    
    # Save to cache
    cache_file.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = ['id', 'current_label', 'mean_intensity', 'total_pixels']
    for thresh in intensity_thresholds:
        fieldnames.append(f'ratio_i{thresh}')
        fieldnames.append(f'pixels_i{thresh}')
    
    with open(cache_file, 'w', newline='', encoding='utf-8') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(results)
    
    print(f"      ✓ Analyzed {len(results)} ROIs, saved to cache")
    return True

def load_area_ratio_cache(cache_file):
    """Load cached area ratio data"""
    roi_data = {}
    with open(cache_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            roi_id = row['id']
            roi_data[roi_id] = {
                'current_label': row['current_label'],
                'mean_intensity': float(row['mean_intensity']),
                'total_pixels': int(row['total_pixels'])
            }
            # Load all ratio data
            for key, value in row.items():
                if key.startswith('ratio_i') or key.startswith('pixels_i'):
                    roi_data[roi_id][key] = float(value)
    
    return roi_data

def generate_geojson_by_area_ratio(geojson_data, output_geojson, roi_data, 
                                   intensity_threshold, area_ratio_threshold, target_label):
    """
    根据强度阈值和面积占比阈值生成GeoJSON
    
    规则：ROI内强度>intensity_threshold的像素占比 > area_ratio_threshold 才标记为阳性
    """
    output_data = json.loads(json.dumps(geojson_data))
    
    updated_count = 0
    ratio_key = f'ratio_i{intensity_threshold}'
    
    for feature in output_data['features']:
        feature_id = feature.get('id', '')
        
        if feature_id not in roi_data:
            continue
        
        roi = roi_data[feature_id]
        
        if ratio_key not in roi:
            continue
        
        positive_ratio = roi[ratio_key]
        
        # 如果阳性像素占比 > 面积占比阈值，则标记为目标label
        if positive_ratio > area_ratio_threshold:
            old_label = feature['properties'].get('classification', '')
            if old_label != target_label:
                feature['properties']['classification'] = target_label
                # 添加额外属性记录占比
                feature['properties']['positive_ratio'] = round(positive_ratio, 2)
                feature['properties']['intensity_threshold'] = intensity_threshold
                feature['properties']['area_threshold'] = area_ratio_threshold
                updated_count += 1
    
    with open(output_geojson, 'w', encoding='utf-8') as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    return updated_count

def process_marker(case_name, marker, json_dir, svs_dir, output_dir):
    """Process a single marker"""
    
    print(f"\n   🔬 Processing marker: {marker}")
    
    json_file = json_dir / f"{case_name}-{marker}.json"
    if not json_file.exists():
        print(f"      ⏭️  No JSON file found for {marker}, skipping")
        return
    
    svs_files = list(svs_dir.glob(f'*{marker}.svs'))
    if not svs_files:
        svs_files = list(svs_dir.glob(f'*{marker.upper()}.svs'))
    if not svs_files:
        print(f"      ⏭️  No SVS file found for {marker}, skipping")
        return
    
    svs_path = svs_files[0]
    print(f"      📄 SVS file: {svs_path.name}")
    print(f"      📄 JSON file: {json_file.name}")
    
    with open(json_file, 'r', encoding='utf-8') as f:
        input_json_data = json.load(f)
    
    print(f"      🔄 Converting to GeoJSON format...")
    geojson_data = convert_json_to_geojson(input_json_data, marker)
    
    if not geojson_data['features']:
        print(f"      ⏭️  No features for {marker}, skipping")
        return
    
    print(f"      Found {len(geojson_data['features'])} ROIs")
    
    cache_file = output_dir / f'cache_area_ratio_{case_name}_{marker}.csv'
    
    # Step 1: Analyze area ratios
    print(f"      📊 Analyzing area ratios...")
    if not analyze_area_ratios(marker, geojson_data, svs_path, cache_file, INTENSITY_THRESHOLDS):
        return
    
    # Step 2: Load cache
    roi_data = load_area_ratio_cache(cache_file)
    
    # Step 3: Generate GeoJSON files for different combinations
    print(f"      🎯 Generating GeoJSON files...")
    marker_output_dir = output_dir / marker
    marker_output_dir.mkdir(parents=True, exist_ok=True)
    
    total_combinations = 0
    for intensity_thresh in INTENSITY_THRESHOLDS:
        for area_ratio_thresh in AREA_RATIO_THRESHOLDS:
            output_file = marker_output_dir / f"{case_name}-{marker}_intensity{intensity_thresh}_area{area_ratio_thresh}.geojson"
            
            updated_count = generate_geojson_by_area_ratio(
                geojson_data,
                output_file,
                roi_data,
                intensity_thresh,
                area_ratio_thresh,
                marker
            )
            
            # Count positive ROIs
            ratio_key = f'ratio_i{intensity_thresh}'
            positive_count = sum(1 for roi in roi_data.values() 
                               if ratio_key in roi and roi[ratio_key] > area_ratio_thresh)
            positive_pct = 100 * positive_count / len(roi_data) if roi_data else 0
            
            if intensity_thresh == 50 and area_ratio_thresh in [30, 50, 70]:
                print(f"         强度>{intensity_thresh}, 面积>{area_ratio_thresh}% → {positive_count:4d} ROIs ({positive_pct:5.1f}%)")
            
            total_combinations += 1
    
    print(f"      ✅ Generated {total_combinations} files for {marker}")
    print(f"         ({len(INTENSITY_THRESHOLDS)} intensity × {len(AREA_RATIO_THRESHOLDS)} area thresholds)")

def main():
    """Main processing function"""
    
    print("=" * 80)
    print("🚀 基于阳性面积占比的ROI分类")
    print("=" * 80)
    print()
    print(f"强度阈值: {INTENSITY_THRESHOLDS}")
    print(f"面积占比阈值: {AREA_RATIO_THRESHOLDS}")
    print(f"预计生成: {len(MARKERS)} markers × {len(INTENSITY_THRESHOLDS)} × {len(AREA_RATIO_THRESHOLDS)} = {len(MARKERS)*len(INTENSITY_THRESHOLDS)*len(AREA_RATIO_THRESHOLDS)} 个文件")
    print()
    
    case_name = 'K2023-3068'
    json_dir = Path(f'/Users/felix/Desktop/光密度/input/{case_name}_json')
    svs_dir = Path(f'/Users/felix/Desktop/光密度/input/{case_name}_svs')
    output_dir = Path('/Users/felix/Desktop/光密度/output/area_ratio')
    
    output_dir.mkdir(parents=True, exist_ok=True)
    
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
    print("📖 文件命名规则:")
    print("   {case}-{marker}_intensity{N}_area{M}.geojson")
    print("   - intensityN: ROI内像素强度需要 > N")
    print("   - areaM: 阳性像素占比需要 > M%")
    print()
    print("🎮 How to use in QuPath:")
    print("   1. Open a case SVS file in QuPath")
    print("   2. Objects → Import objects → From GeoJSON")
    print("   3. 选择不同的intensity和area组合进行测试")
    print("   4. 找到最佳的强度阈值和面积占比组合")

if __name__ == '__main__':
    main()

