# 光密度测量在JSON/GeoJSON中的参数名称

## 📊 GeoJSON结构

在QuPath/HPath导出的GeoJSON中，对象的结构如下：

```json
{
  "type": "Feature",
  "id": "PathCellObject",
  "geometry": {
    "type": "Polygon",
    "coordinates": [...]
  },
  "properties": {
    "objectType": "detection",
    "classification": {
      "name": "Positive",
      "color": [255, 0, 0]
    },
    "measurements": [
      {
        "name": "Detection: DAB OD mean",
        "value": 0.234
      },
      {
        "name": "Detection: DAB OD sum",
        "value": 1.567
      },
      {
        "name": "Detection: Hematoxylin OD mean",
        "value": 0.123
      }
    ]
  }
}
```

---

## 🔍 光密度测量参数名称

### 常见的光密度测量字段

#### 1. DAB染色光密度
```json
"measurements": [
  {"name": "Detection: DAB OD mean", "value": 0.234},
  {"name": "Detection: DAB OD sum", "value": 1.567},
  {"name": "Detection: DAB OD max", "value": 0.456},
  {"name": "Detection: DAB OD min", "value": 0.012},
  {"name": "Detection: DAB OD std dev", "value": 0.089}
]
```

**参数名称**:
- `Detection: DAB OD mean` - DAB光密度均值 ⭐ 最常用
- `Detection: DAB OD sum` - DAB光密度总和
- `Detection: DAB OD max` - DAB光密度最大值
- `Detection: DAB OD min` - DAB光密度最小值
- `Detection: DAB OD std dev` - DAB光密度标准差

#### 2. Hematoxylin染色光密度
```json
"measurements": [
  {"name": "Detection: Hematoxylin OD mean", "value": 0.123},
  {"name": "Detection: Hematoxylin OD sum", "value": 0.876},
  {"name": "Detection: Hematoxylin OD max", "value": 0.234},
  {"name": "Detection: Hematoxylin OD min", "value": 0.045}
]
```

#### 3. 细胞核和细胞质
如果是细胞对象，可能有：
```json
"measurements": [
  {"name": "Nucleus: DAB OD mean", "value": 0.234},
  {"name": "Nucleus: DAB OD sum", "value": 1.567},
  {"name": "Cytoplasm: DAB OD mean", "value": 0.123},
  {"name": "Cell: DAB OD mean", "value": 0.178}
]
```

---

## 📝 JSON格式说明

### 标准GeoJSON格式

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "id": "PathDetectionObject",
      "geometry": {
        "type": "Point",
        "coordinates": [100.0, 200.0]
      },
      "properties": {
        "objectType": "detection",
        "classification": {
          "name": "Positive",
          "color": [255, 0, 0]
        },
        "measurements": [
          {
            "name": "Detection: DAB OD mean",
            "value": 0.234
          },
          {
            "name": "Detection: Area µm^2",
            "value": 45.67
          }
        ]
      }
    }
  ]
}
```

### 关键字段

| 字段路径 | 说明 | 示例值 |
|---------|------|--------|
| `properties.measurements` | 测量值数组 | `[{...}]` |
| `properties.measurements[].name` | 测量值名称 | `"Detection: DAB OD mean"` |
| `properties.measurements[].value` | 测量值数值 | `0.234` |

---

## 🔎 如何查找具体的测量参数名

### 方法1: 导出示例查看

1. 在HPath中选择一个检测对象
2. `File → Export objects → Export as GeoJSON`
3. 打开导出的JSON文件
4. 查看 `properties.measurements` 中的 `name` 字段

### 方法2: 在界面查看

1. 选择一个对象
2. `Measure → Show measurements`
3. 表格中的列名就是参数名

### 方法3: 通过脚本查看

```groovy
// 获取第一个检测对象
def detection = getDetectionObjects()[0]

// 获取所有测量值
def measurements = detection.getMeasurementList()

// 打印所有测量名称
measurements.getMeasurementNames().each { name ->
    println "测量名称: ${name}"
    println "测量值: ${detection.getMeasurementList().get(name)}"
}
```

---

## 💡 常见光密度参数对照表

### Detection对象

| UI显示名称 | JSON参数名称 | 说明 |
|-----------|-------------|------|
| DAB OD mean | `Detection: DAB OD mean` | DAB平均光密度 ⭐ |
| DAB OD sum | `Detection: DAB OD sum` | DAB总光密度 |
| DAB OD max | `Detection: DAB OD max` | DAB最大光密度 |
| DAB OD min | `Detection: DAB OD min` | DAB最小光密度 |
| Hematoxylin OD mean | `Detection: Hematoxylin OD mean` | H平均光密度 |

### Cell对象

| UI显示名称 | JSON参数名称 | 说明 |
|-----------|-------------|------|
| Nucleus: DAB OD mean | `Nucleus: DAB OD mean` | 细胞核DAB均值 |
| Cytoplasm: DAB OD mean | `Cytoplasm: DAB OD mean` | 细胞质DAB均值 |
| Cell: DAB OD mean | `Cell: DAB OD mean` | 整个细胞DAB均值 |

---

## 🎯 实际JSON示例

### 示例1: Detection对象的光密度

```json
{
  "type": "Feature",
  "geometry": {
    "type": "Point",
    "coordinates": [1234.5, 5678.9]
  },
  "properties": {
    "objectType": "detection",
    "classification": {"name": "Positive"},
    "measurements": [
      {"name": "Detection: DAB OD mean", "value": 0.25},
      {"name": "Detection: DAB OD sum", "value": 1.5},
      {"name": "Detection: Hematoxylin OD mean", "value": 0.15},
      {"name": "Detection: Area µm^2", "value": 45.6}
    ]
  }
}
```

### 示例2: Cell对象的光密度

```json
{
  "type": "Feature",
  "geometry": {
    "type": "Polygon",
    "coordinates": [[...]]
  },
  "properties": {
    "objectType": "cell",
    "measurements": [
      {"name": "Cell: DAB OD mean", "value": 0.234},
      {"name": "Nucleus: DAB OD mean", "value": 0.345},
      {"name": "Cytoplasm: DAB OD mean", "value": 0.123},
      {"name": "Nucleus: Area µm^2", "value": 25.6},
      {"name": "Cell: Area µm^2", "value": 45.6}
    ]
  }
}
```

---

## 🔧 在代码中访问

### Python示例

```python
import json

# 读取GeoJSON
with open('detections.geojson', 'r') as f:
    data = json.load(f)

# 遍历所有对象
for feature in data['features']:
    measurements = feature['properties']['measurements']
    
    # 查找DAB OD mean
    for m in measurements:
        if m['name'] == 'Detection: DAB OD mean':
            od_value = m['value']
            print(f"光密度: {od_value}")
```

### JavaScript示例

```javascript
// 读取GeoJSON
fetch('detections.geojson')
  .then(response => response.json())
  .then(data => {
    data.features.forEach(feature => {
      const measurements = feature.properties.measurements;
      
      // 查找DAB OD mean
      const odMeasurement = measurements.find(
        m => m.name === 'Detection: DAB OD mean'
      );
      
      if (odMeasurement) {
        console.log('光密度:', odMeasurement.value);
      }
    });
  });
```

### Groovy/Java示例

```groovy
import com.google.gson.JsonParser
import com.google.gson.JsonObject

// 读取JSON
def json = new File('detections.geojson').text
def root = JsonParser.parseString(json).asJsonObject

// 获取features
def features = root.getAsJsonArray('features')

features.each { feature ->
    def props = feature.asJsonObject.getAsJsonObject('properties')
    def measurements = props.getAsJsonArray('measurements')
    
    measurements.each { m ->
        def mObj = m.asJsonObject
        def name = mObj.get('name').asString
        def value = mObj.get('value').asDouble
        
        if (name == 'Detection: DAB OD mean') {
            println "光密度: ${value}"
        }
    }
}
```

---

## 📋 快速参考表

### 最常用的光密度参数

**在JSON中查找这些名称**:

1. **DAB染色** (棕色，常用于免疫组化)
   ```
   "Detection: DAB OD mean"      ← 最常用
   "Nucleus: DAB OD mean"
   "Cytoplasm: DAB OD mean"
   ```

2. **Hematoxylin染色** (蓝紫色，细胞核)
   ```
   "Detection: Hematoxylin OD mean"
   "Nucleus: Hematoxylin OD mean"
   ```

3. **其他染色**
   ```
   "Detection: [染色名称] OD mean"
   ```

---

## 💡 提示

### 参数名称规则

QuPath的测量参数名称格式：
```
[对象类型]: [区域]: [染色]: OD [统计量]
```

例如：
- `Detection: DAB OD mean`
- `Nucleus: Hematoxylin OD sum`
- `Cell: DAB OD max`

### 如果找不到OD测量

**可能原因**:
1. 图像未进行染色分离（Stain deconvolution）
2. 对象创建时未计算光密度

**解决**:
1. `Analyze → Preprocessing → Estimate stain vectors`
2. 重新运行细胞检测，确保包含光密度计算

---

## 🎯 总结

**光密度在JSON中的位置**:
```
feature.properties.measurements[i].name == "Detection: DAB OD mean"
feature.properties.measurements[i].value == 0.234  (光密度值)
```

**最常用的参数名**: `Detection: DAB OD mean`

**如何找到其他参数**: 导出一个示例GeoJSON查看即可

---

需要帮助提取或处理光密度数据吗？ 🎯

