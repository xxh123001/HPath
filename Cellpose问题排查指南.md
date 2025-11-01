# Cellpose错误排查指南

## 常见错误及解决方案

---

## 错误1: No channel found with name DAPI

### 错误信息
```
ERROR: No channel found with name DAPI
```

### 原因
你的图像中没有名为"DAPI"的通道。这通常发生在：
- RGB图像（只有Red, Green, Blue通道）
- 明场图像（没有荧光通道）
- 通道名称不同（如C0, C1, C2等）

### 解决方案

#### 步骤1: 检查你的图像通道

运行脚本 **`检查图像通道.groovy`**，会显示：
```
可用的通道列表：
通道 1: Red
  - 颜色: java.awt.Color[r=255,g=0,b=0]
通道 2: Green
  - 颜色: java.awt.Color[r=0,g=255,b=0]
通道 3: Blue
  - 颜色: java.awt.Color[r=0,g=0,b=255]
```

#### 步骤2: 修改Cellpose脚本

根据检查结果，修改脚本中的 `.channels()` 参数：

**如果是RGB图像：**
```groovy
.channels('Red')  // 只用红色通道
// 或
.channels('Red', 'Green', 'Blue')  // 使用多个通道
```

**如果是荧光图像：**
```groovy
.channels('C0')  // 使用第一个通道
// 或
.channels('C0', 'C1')  // 使用两个通道
```

**如果通道有名字：**
```groovy
.channels('Cy3')  // 使用实际的通道名称
```

---

## 错误2: Found 0 Training objects and 0 Validation objects

### 错误信息
```
INFO: Found 0 Training objects and 0 Validation objects in image xxx
ERROR: no files in --dir folder
```

### 原因
项目中没有标记为"Training"或"Validation"类别的对象。

### 解决方案

#### 方法1: 批量设置现有对象为训练类别

运行脚本 **`批量设置对象为训练类别.groovy`**

这会：
1. 自动将项目中所有对象按比例分配为Training/Validation
2. 默认80%用于训练，20%用于验证
3. 自动保存所有图像

#### 方法2: 手动创建训练标注

1. **打开一个图像**
2. **使用工具创建标注**（Rectangle, Polygon, Brush等）
3. **右键标注 → Set class → 创建"Training"类别**
4. **创建更多标注**
5. **部分标注设置为"Validation"类别**
6. **保存图像** (Ctrl+S)
7. **对多个图像重复以上步骤**

#### 方法3: 导入已有的标注并设置类别

如果你有JSON标注文件：
1. **导入JSON到图像**（使用你的批量导入功能）
2. **运行脚本"设置训练验证类别.groovy"**设置当前图像的对象
3. **切换到下一个图像，重复操作**

---

## 完整的Cellpose使用流程

### 🎯 训练流程

1. **准备数据**
   ```
   运行: 批量设置对象为训练类别.groovy
   ```

2. **检查数据**
   - 确保每个图像都有Training和Validation对象
   - 建议至少5-10个图像用于训练

3. **运行训练**
   ```groovy
   修改训练脚本的通道后运行
   ```

### 🎯 检测流程

1. **检查图像通道**
   ```
   运行: 检查图像通道.groovy
   ```

2. **修改检测脚本**
   ```groovy
   .channels('实际的通道名称')  // 替换为检查到的通道
   ```

3. **创建检测区域**
   - 使用Rectangle工具画一个检测区域
   - 选中这个区域

4. **运行检测**
   ```
   运行修改后的检测脚本
   ```

---

## 快速修复脚本

我已经为你创建了三个脚本：

1. **`检查图像通道.groovy`**
   - 查看当前图像有哪些通道
   - 告诉你应该用什么通道名称

2. **`批量设置对象为训练类别.groovy`**
   - 自动将所有对象分配为Training/Validation
   - 适合已经有标注的情况

3. **`Cellpose训练检测-修复版.groovy`**
   - 自动检测图像类型和通道
   - 自动选择合适的通道

---

## 常见通道名称对照表

| 图像类型 | 常见通道名称 | Cellpose参数示例 |
|---------|------------|----------------|
| RGB图像 | Red, Green, Blue | `.channels('Red')` |
| 明场图像 | Red, Green, Blue | `.channels('Red')` |
| 荧光图像 | C0, C1, C2, C3 | `.channels('C0', 'C1')` |
| 多通道命名 | DAPI, Cy3, Cy5 | `.channels('DAPI', 'Cy3')` |
| 灰度图像 | Channel 1 | `.channels('Channel 1')` |

---

## 建议的完整工作流程

### 如果你有JSON标注（如kt.json）：

1. **批量导入文件夹**
   ```
   使用: 📁 Batch Import Folder
   ```

2. **批量配对**
   ```
   使用: 🔗 Batch Merge by Name
   正则: (.+)\.(svs|tif) 和 (.+)\.json
   ```

3. **打开一个配对结果**
   ```
   双击 Merged Results 中的任意项
   ```

4. **检查通道**
   ```
   运行: 检查图像通道.groovy
   ```

5. **设置为训练类别**
   ```
   运行: 设置训练验证类别.groovy
   选择: Training 或 Validation
   ```

6. **重复步骤3-5**，为多个图像设置类别

7. **修改并运行训练脚本**
   ```
   修改 .channels() 为实际通道名
   运行训练脚本
   ```

---

## 推荐的修改

### 对于你的检测脚本，改为：

```groovy
// 自动检测通道
def imageData = getCurrentImageData()
def channels = imageData.getServer().getMetadata().getChannels()
def channelName = channels[0].getName()  // 使用第一个通道

println "使用通道: ${channelName}"

def cellpose = Cellpose2D.builder('cyto3')
        .pixelSize(0.5)
        .channels(channelName)  // 使用检测到的通道
        .diameter(30)
        .build()

// ... 其余代码不变
```

---

## 需要帮助？

1. **先运行 `检查图像通道.groovy`** - 了解你的图像结构
2. **根据输出修改Cellpose脚本** - 替换通道名称
3. **如有疑问** - 查看HPath日志 (Help → Show Log)

🎉 祝你训练顺利！









