# 顺序配对和Label识别功能说明

## 功能概述

新增了两个重要功能：
1. **顺序复选框配对功能**：在Project界面支持按点击顺序配对合并images和annotations
2. **JSON Label自动识别为Classification**：导入JSON标注时自动识别label字段并设置为对象的classification

---

## 功能一：顺序配对合并 (Ordered Pairing Merge)

### 使用方法

#### 1. 选择Images（按顺序）
- 在左侧Images列表中，**按住 Ctrl (Windows/Linux) 或 Cmd (Mac)**
- 依次点击要选择的图像（例如：第1、3、6张图像）
- 选中的图像会显示 **`[1]`、`[2]`、`[3]`** 等序号标记
- 背景会变成**淡蓝色**以示选中

#### 2. 选择Objects（按顺序）
- 在右侧Objects列表中，**按住 Ctrl/Cmd**
- 依次点击要选择的标注文件（例如：第3、8、9个文件）
- 选中的文件会显示 **`[1]`、`[2]`、`[3]`** 等序号标记
- 背景也会变成**淡蓝色**

#### 3. 执行配对合并
- 点击 **"⬇ Merge Selected Image + Objects"** 按钮
- 系统会按照选择顺序自动配对：
  - 第1个选中的图像 → 第1个选中的标注
  - 第2个选中的图像 → 第2个选中的标注
  - 第3个选中的图像 → 第3个选中的标注
  - ...以此类推

#### 4. 查看配对结果
- 合并后会在下方 **"📊 Merged Results"** 列表中显示所有配对
- 显示格式：`图像名 + 标注文件名 (对象数量)`
- 系统会弹出确认信息，显示所有配对详情

#### 5. 打开合并结果
- 双击 Merged Results 列表中的任意项
- 或者选中后点击 **"Open"** 按钮
- 图像会自动打开，标注对象会自动导入

### 示例场景

**场景：** 你有6张图像和9个标注文件，想要配对：
- Images: 选择第 1, 3, 6 张
- Objects: 选择第 3, 8, 9 个

**结果：** 会生成3对合并：
```
1. 图像1 + 标注3
2. 图像3 + 标注8
3. 图像6 + 标注9
```

### 清除选择

- **清除Image选择**：点击Images区域的 **"Clear Selection"** 按钮
- **清除Object选择**：点击Objects区域的 **"Clear Selection"** 按钮
- 合并后会**自动清除**所有选择

### 提示信息

界面上会实时显示选择状态：
- **Images区域**：`Selected: 3 (order: img1.svs, img3.svs, img6.svs)`
- **Objects区域**：`Selected: 3 (order: anno3.json, anno8.json, anno9.json)`

---

## 功能二：JSON Label自动识别为Classification

### 功能说明

当导入JSON格式的标注文件时，系统会自动识别以下字段：
- **`label`** 字段（在JSON根对象中）
- **`label`** 字段（在 `properties` 对象中）

并自动将其映射为QuPath的 **`classification`** 属性。

### JSON格式示例

#### 格式1：label在根对象中
```json
{
  "type": "Feature",
  "geometry": {
    "type": "Polygon",
    "coordinates": [[[100, 200], [150, 200], [150, 250], [100, 250], [100, 200]]]
  },
  "label": "tumor"
}
```

#### 格式2：label在properties中
```json
{
  "type": "Feature",
  "geometry": {
    "type": "Polygon",
    "coordinates": [[[100, 200], [150, 200], [150, 250], [100, 250], [100, 200]]]
  },
  "properties": {
    "label": "tumor",
    "area": 2500
  }
}
```

#### 格式3：标准JSON数组（会自动转换为GeoJSON）
```json
[
  {
    "points": [[100, 200], [150, 200], [150, 250], [100, 250]],
    "label": "tumor"
  },
  {
    "points": [[200, 300], [250, 300], [250, 350], [200, 350]],
    "label": "stroma"
  }
]
```

### 转换结果

导入后，每个对象的 **classification** 会被自动设置为对应的 **label** 值：
- `label: "tumor"` → `classification: "tumor"`
- `label: "stroma"` → `classification: "stroma"`
- `label: "normal"` → `classification: "normal"`

### 日志输出

系统会在日志中记录转换信息：
```
Mapped 'label' field ('tumor') to 'classification'
```

### 兼容性

- ✅ 支持标准GeoJSON格式
- ✅ 支持普通JSON自动转换为GeoJSON
- ✅ 支持嵌套的properties对象
- ✅ 向后兼容（没有label字段的JSON仍正常工作）

---

## 技术实现

### 修改的文件

1. **JsonToGeoJsonHandler.java**
   - 修改 `convertToFeature()` 方法
   - 添加label字段识别和映射逻辑

2. **ProjectBrowser.java**
   - 添加 `orderedImageSelection` 和 `orderedObjectSelection` 列表
   - 修改UI添加选择顺序显示和清除按钮
   - 更新 `mergeImageWithObjects()` 方法支持按序配对
   - 更新TreeCell和ListCell显示选择序号
   - 添加辅助方法 `getImageSelectionOrderDisplay()` 和 `getObjectSelectionOrderDisplay()`

### 快捷键

- **Ctrl+Click** (Windows/Linux) 或 **Cmd+Click** (Mac)：添加到顺序选择
- 再次 **Ctrl+Click** 已选中的项：从选择中移除

---

## 使用建议

1. **大批量处理**：如果有很多图像和标注需要配对，先规划好顺序
2. **验证配对**：合并后检查Merged Results列表确认配对正确
3. **保存项目**：配对合并后记得保存项目
4. **Label命名**：建议使用标准的classification名称（如：Tumor, Stroma, Normal等）

---

## 常见问题

### Q1: 选择数量不匹配怎么办？
**A:** 系统会配对到较小的数量，剩余的不会配对。例如：
- 选择3个images和5个objects → 配对3对，2个objects不会被使用
- 选择5个images和3个objects → 配对3对，2个images不会被使用

### Q2: 如何更改配对顺序？
**A:** 点击 "Clear Selection" 清除所有选择，然后按新的顺序重新选择。

### Q3: Label字段不叫"label"怎么办？
**A:** 目前只支持字段名为 "label" 的自动识别。如需支持其他字段名，需要手动修改代码。

### Q4: 导入后classification没有设置？
**A:** 检查：
- JSON中是否有 "label" 字段
- label的值是否为字符串类型
- 查看日志是否有 "Mapped 'label' field" 的信息

---

## 更新日期

**2025-10-26**

功能已完成开发和测试，可以立即使用！🎉

