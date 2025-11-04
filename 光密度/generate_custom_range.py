#!/usr/bin/env python3
"""
生成自定义范围的阈值组合文件
可以指定marker、中心值和浮动范围
"""

import json
import csv
from pathlib import Path
import sys

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
            for key, value in row.items():
                if key.startswith('ratio_i') or key.startswith('pixels_i'):
                    roi_data[roi_id][key] = float(value)
    
    return roi_data

def convert_json_to_geojson(json_data):
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
    
    return {
        "type": "FeatureCollection",
        "features": features
    }

def interpolate_ratio(roi_data, roi_id, target_intensity):
    """插值计算特定强度阈值下的阳性像素占比"""
    known_thresholds = [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
    
    if target_intensity in known_thresholds:
        ratio_key = f'ratio_i{target_intensity}'
        return roi_data[roi_id].get(ratio_key, 0.0)
    
    lower = max([t for t in known_thresholds if t < target_intensity], default=10)
    upper = min([t for t in known_thresholds if t > target_intensity], default=100)
    
    if lower == upper:
        ratio_key = f'ratio_i{lower}'
        return roi_data[roi_id].get(ratio_key, 0.0)
    
    lower_ratio = roi_data[roi_id].get(f'ratio_i{lower}', 0.0)
    upper_ratio = roi_data[roi_id].get(f'ratio_i{upper}', 0.0)
    
    weight = (target_intensity - lower) / (upper - lower)
    interpolated_ratio = lower_ratio + weight * (upper_ratio - lower_ratio)
    
    return interpolated_ratio

def generate_geojson_fine_grained(geojson_data, output_geojson, roi_data, 
                                   intensity_threshold, area_ratio_threshold, target_label):
    """生成细粒度GeoJSON文件"""
    output_data = json.loads(json.dumps(geojson_data))
    
    updated_count = 0
    
    for feature in output_data['features']:
        feature_id = feature.get('id', '')
        
        if feature_id not in roi_data:
            continue
        
        positive_ratio = interpolate_ratio(roi_data, feature_id, intensity_threshold)
        
        if positive_ratio > area_ratio_threshold:
            old_label = feature['properties'].get('classification', '')
            if old_label != target_label:
                feature['properties']['classification'] = target_label
                feature['properties']['positive_ratio'] = round(positive_ratio, 2)
                feature['properties']['intensity_threshold'] = intensity_threshold
                feature['properties']['area_threshold'] = area_ratio_threshold
                updated_count += 1
    
    with open(output_geojson, 'w', encoding='utf-8') as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    return updated_count

def main():
    """Main function"""
    
    # 配置参数
    marker = 'UMOD'
    intensity_center = 40
    intensity_range = 5
    area_center = 30
    area_range = 5
    
    print("=" * 80)
    print(f"🚀 生成 {marker} 自定义范围阈值组合文件")
    print("=" * 80)
    print()
    print(f"📊 参数设置:")
    print(f"   Intensity中心值: {intensity_center} (范围: {intensity_center-intensity_range} - {intensity_center+intensity_range})")
    print(f"   Area中心值: {area_center}% (范围: {area_center-area_range}% - {area_center+area_range}%)")
    print()
    
    case_name = 'K2023-3068'
    
    # Paths
    json_file = Path(f'/Users/felix/Desktop/光密度/input/{case_name}_json/{case_name}-{marker}.json')
    cache_file = Path(f'/Users/felix/Desktop/光密度/output/area_ratio/cache_area_ratio_{case_name}_{marker}.csv')
    output_dir = Path(f'/Users/felix/Desktop/光密度/output/area_ratio/{marker}_final')
    
    output_dir.mkdir(parents=True, exist_ok=True)
    
    print(f"📁 输出目录: {output_dir}")
    print()
    
    # Check files
    if not json_file.exists():
        print(f"❌ JSON文件不存在: {json_file}")
        return
    
    if not cache_file.exists():
        print(f"❌ Cache文件不存在: {cache_file}")
        print(f"   正在等待主脚本生成cache...")
        print(f"   请确保 process_with_area_ratio.py 已完成对 {marker} 的处理")
        return
    
    # Load data
    print(f"📥 加载JSON数据...")
    with open(json_file, 'r', encoding='utf-8') as f:
        input_json_data = json.load(f)
    
    geojson_data = convert_json_to_geojson(input_json_data)
    print(f"   加载 {len(geojson_data['features'])} 个ROIs")
    
    print(f"📥 加载cache数据...")
    roi_data = load_area_ratio_cache(cache_file)
    print(f"   加载 {len(roi_data)} 个ROIs的强度数据")
    print()
    
    # Generate files
    intensity_min = intensity_center - intensity_range
    intensity_max = intensity_center + intensity_range
    area_min = area_center - area_range
    area_max = area_center + area_range
    
    intensity_values = range(intensity_min, intensity_max + 1)
    area_values = range(area_min, area_max + 1)
    
    total_files = len(list(intensity_values)) * len(list(area_values))
    
    print(f"🎯 生成文件...")
    print(f"   Intensity范围: {intensity_min}-{intensity_max} (共{len(list(intensity_values))}个值)")
    print(f"   Area范围: {area_min}-{area_max} (共{len(list(area_values))}个值)")
    print(f"   预计生成: {len(list(intensity_values))} × {len(list(area_values))} = {total_files} 个文件")
    print()
    
    generated = 0
    for intensity_thresh in range(intensity_min, intensity_max + 1):
        for area_ratio_thresh in range(area_min, area_max + 1):
            output_file = output_dir / f"{case_name}-{marker}_intensity{intensity_thresh}_area{area_ratio_thresh}.geojson"
            
            updated_count = generate_geojson_fine_grained(
                geojson_data,
                output_file,
                roi_data,
                intensity_thresh,
                area_ratio_thresh,
                marker
            )
            
            # Count positive ROIs
            positive_count = sum(1 for roi_id in roi_data.keys() 
                               if interpolate_ratio(roi_data, roi_id, intensity_thresh) > area_ratio_thresh)
            positive_pct = 100 * positive_count / len(roi_data) if roi_data else 0
            
            generated += 1
            
            # 显示关键组合的统计
            if intensity_thresh in [intensity_min, intensity_center, intensity_max] and \
               area_ratio_thresh in [area_min, area_center, area_max]:
                print(f"   intensity{intensity_thresh}_area{area_ratio_thresh}: {positive_count} ROIs ({positive_pct:.1f}%)")
    
    print()
    print(f"✅ 完成！生成了 {generated} 个文件")
    print()
    print("=" * 80)
    print(f"📁 所有文件保存在: {output_dir}")
    print("=" * 80)
    print()
    print("📖 文件命名规则:")
    print(f"   {case_name}-{marker}_intensity{{N}}_area{{M}}.geojson")
    print(f"   - N: {intensity_min}, {intensity_min+1}, ..., {intensity_max}")
    print(f"   - M: {area_min}, {area_min+1}, ..., {area_max}")
    print()
    print("💡 推荐测试组合:")
    print(f"   - 中心值: intensity{intensity_center}_area{area_center}")
    print(f"   - 严格: intensity{intensity_max}_area{area_max}")
    print(f"   - 宽松: intensity{intensity_min}_area{area_min}")

if __name__ == '__main__':
    main()

