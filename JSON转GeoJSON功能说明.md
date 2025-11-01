# JSON 转 GeoJSON 拖放功能使用说明

## 功能概述

HPath 现在支持将普通 JSON 文件拖放到应用程序中，系统会自动将其转换为 GeoJSON 格式并渲染显示。这个功能让您可以方便地导入各种格式的坐标数据。

## 使用方法

1. **打开 HPath** 并加载一个图像
2. **准备 JSON 文件**（支持多种格式，见下文）
3. **拖放 JSON 文件**到 HPath 主窗口或查看器中
4. 系统会自动检测并转换 JSON 格式
5. 弹出对话框确认是否导入对象

## 支持的 JSON 格式

### 1. 标准 GeoJSON 格式
如果您的 JSON 已经是标准 GeoJSON 格式，系统会直接解析：

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "geometry": {
        "type": "Polygon",
        "coordinates": [
          [[0, 0], [100, 0], [100, 100], [0, 100], [0, 0]]
        ]
      },
      "properties": {
        "name": "My Annotation"
      }
    }
  ]
}
```

### 2. 点数组格式（多边形）
系统会自动将点数组转换为多边形：

```json
{
  "name": "Sample Polygon",
  "points": [
    {"x": 100, "y": 100},
    {"x": 200, "y": 100},
    {"x": 200, "y": 200},
    {"x": 100, "y": 200}
  ],
  "properties": {
    "label": "Test Region"
  }
}
```

支持的坐标字段名：
- `x`, `y`
- `lat`, `lon`
- `latitude`, `longitude`

### 3. 单点格式
单个点会被转换为 Point 几何：

```json
{
  "name": "Sample Point",
  "x": 150,
  "y": 250,
  "marker_type": "cell"
}
```

### 4. 对象数组格式
包含多个对象的数组会被转换为 FeatureCollection：

```json
[
  {
    "id": "region1",
    "points": [
      {"x": 50, "y": 50},
      {"x": 100, "y": 50},
      {"x": 100, "y": 100},
      {"x": 50, "y": 100}
    ],
    "classification": "positive"
  },
  {
    "id": "region2",
    "points": [
      {"x": 200, "y": 200},
      {"x": 300, "y": 200},
      {"x": 300, "y": 300},
      {"x": 200, "y": 300}
    ],
    "classification": "negative"
  }
]
```

### 5. 其他支持的字段名

系统会自动识别以下几何相关的字段：
- `coordinates` - 直接的坐标数组
- `geometry` - 嵌套的几何对象
- `points` / `polygon` - 点数组

## 转换规则

1. **几何字段识别**：
   - 包含 `coordinates` 的对象会被视为已包含几何数据
   - 包含 `points` 或 `polygon` 的对象会被转换为多边形
   - 包含 `x, y` 或 `lat, lon` 的对象会被转换为点

2. **属性保留**：
   - 非几何字段会被保存为 GeoJSON 的 `properties`
   - 原始 JSON 中的所有元数据都会保留

3. **数组处理**：
   - JSON 数组会被转换为 GeoJSON FeatureCollection
   - 每个数组元素成为一个 Feature

## 测试示例

在项目目录的 `example-json-files/` 文件夹中，提供了以下测试文件：

1. **sample-polygon.json** - 简单多边形示例
2. **sample-point.json** - 单点示例
3. **sample-multiple-objects.json** - 多对象数组示例
4. **sample-geojson.json** - 标准 GeoJSON 示例

您可以将这些文件拖放到 HPath 中测试功能。

## 注意事项

1. **必须打开图像**：拖放 JSON 文件前必须先在查看器中打开一个图像
2. **坐标系统**：确保 JSON 中的坐标与图像的坐标系统匹配
3. **ID 冲突**：导入时如果检测到 ID 冲突，系统会提示是否更新 ID
4. **文件大小**：对于大型 JSON 文件，转换可能需要一些时间

## 技术实现

- **处理器类**：`JsonToGeoJsonHandler.java`
- **注册位置**：`CommonActions.java`
- **转换逻辑**：自动检测 JSON 结构并转换为 GeoJSON FeatureCollection

## 日志记录

转换过程中的信息会记录在日志中：
- 检测到 GeoJSON 格式时会记录
- 转换成功时会显示导入的对象数量
- 转换失败时会记录错误信息

查看日志：`View → Show Log`

## 问题排查

### JSON 文件无法导入
1. 检查是否已打开图像
2. 确认 JSON 格式正确（可使用在线 JSON 验证工具）
3. 查看日志获取详细错误信息

### 对象位置不正确
1. 检查坐标值是否在图像范围内
2. 确认坐标系统是否匹配（像素坐标 vs 地理坐标）

### 部分对象未导入
1. 检查 JSON 中所有对象是否都包含有效的几何数据
2. 查看日志了解哪些对象被跳过

## 扩展开发

如果需要支持其他 JSON 格式，可以修改 `JsonToGeoJsonHandler.java` 中的转换逻辑：

1. 在 `hasCoordinateData()` 中添加新的字段检测
2. 在 `createGeometry()` 中添加新的转换规则
3. 重新编译项目

---

**版本**: HPath v1.0.0  
**最后更新**: 2025-10-24

