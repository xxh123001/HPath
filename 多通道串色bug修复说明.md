# 多通道串色Bug修复说明

## 问题描述

在多通道情况下出现串色问题：当用户选择通道1时，通道3的蓝色也会被渲染上去，导致显示不正确。

## 问题原因

在 `BrightnessContrastChannelPane.java` 中，RGB通道被分组显示为一个代表通道（Red通道），但是：

1. **问题1**：当用户选中一个RGB组的代表通道时，系统只选中了Red通道，而没有同时选中Green和Blue通道
2. **问题2**：要正确显示RGB图像，需要同时选中该RGB组的所有三个通道（Red、Green、Blue）
3. **结果**：只有Red通道被选中，导致只显示红色分量，或者其他通道错误地被渲染

## 修复方案

### 1. 添加RGB通道组映射

在 `BrightnessContrastChannelPane` 类中添加了一个Map来追踪RGB通道组：

```java
// Map to track RGB channel groups: representative channel -> list of all channels in the group
private final Map<ChannelDisplayInfo, List<ChannelDisplayInfo>> rgbChannelGroups = new HashMap<>();
```

### 2. 在分组时记录映射关系

修改 `groupRGBChannels` 方法，在创建RGB组时记录代表通道与所有通道的映射：

```java
if (group.size() == 3 && hasRGBChannels(group)) {
    // RGB group - only add the Red channel (will display with custom name)
    ChannelDisplayInfo redChannel = group.stream()
            .filter(ch -> ch.getName().contains(" (Red)"))
            .findFirst()
            .orElse(group.get(0));
    result.add(redChannel);
    // Store the mapping: red channel -> all RGB channels in the group
    rgbChannelGroups.put(redChannel, new ArrayList<>(group));
    logger.debug("Grouped RGB channels for '{}', showing as single entry", entry.getKey());
}
```

### 3. 修改通道选择逻辑

修改了以下方法，使其在操作RGB组代表通道时，同时操作该组的所有通道：

#### `setShowChannels` 方法
```java
for (var channel : channels) {
    // Check if this channel is a representative of an RGB group
    if (rgbChannelGroups.containsKey(channel)) {
        // Select all channels in the RGB group
        for (var groupChannel : rgbChannelGroups.get(channel)) {
            imageDisplay.setChannelSelected(groupChannel, true);
        }
    } else {
        // Regular channel - select it directly
        imageDisplay.setChannelSelected(channel, true);
    }
}
```

#### `setHideChannels` 方法
同样的逻辑，但是设置为false（隐藏）

#### `toggleShowHideChannels` 方法
在切换显示状态时，同时切换RGB组的所有通道

#### `isChannelShowing` 方法
检查RGB组的所有通道是否都被选中：
```java
if (rgbChannelGroups.containsKey(channel)) {
    // For RGB groups, check if all channels in the group are selected
    var groupChannels = rgbChannelGroups.get(channel);
    return groupChannels.stream().allMatch(ch -> imageDisplay.selectedChannels().contains(ch));
}
```

### 4. 批量操作支持

也修改了 `setTableSelectedChannels` 和 `toggleTableSelectedChannels` 方法，以支持右键菜单的批量操作。

## 修改文件

- `/Users/felix/Downloads/HPath-main/qupath-0.6.0/qupath-gui-fx/src/main/java/qupath/lib/gui/commands/display/BrightnessContrastChannelPane.java`

## 测试方法

### 步骤1：启动HPath
```bash
cd /Users/felix/Downloads/HPath-main/qupath-0.6.0
./gradlew run
```

### 步骤2：创建多通道叠加
1. 打开一个项目
2. 菜单：View → Multi-channel overlay...
3. 选择多个RGB图像
4. 点击 "Create Overlay"

### 步骤3：测试通道选择
1. 在Brightness & Contrast面板中，查看通道列表
2. 选中一个RGB图像的通道（显示为图像名，不带"(Red)"后缀）
3. 点击"Show"复选框
4. **预期结果**：该RGB图像应该显示为完整的彩色图像，包含Red、Green、Blue三个通道
5. 切换其他通道，验证没有串色问题

### 步骤4：验证没有串色
1. 只选中通道1
2. **预期结果**：只显示通道1的图像，其他通道（如通道3的蓝色）不应该被渲染
3. 依次测试每个通道，确保没有串色

## 技术细节

### RGB通道分组机制

当检测到三个通道（Red、Green、Blue）具有相同的基础名称时：
- **表格显示**：只显示一个代表通道（Red），名称简化为基础名称
- **内部映射**：维护代表通道到完整通道列表的映射
- **选择逻辑**：当选中代表通道时，自动选中所有三个通道
- **状态检查**：只有当所有三个通道都被选中时，才显示为"showing"

### 优势

1. **界面简洁**：用户只看到一个RGB通道条目，而不是三个
2. **正确渲染**：选中RGB通道时，自动选中所有三个颜色分量
3. **一致性**：显示状态与实际选中状态保持一致
4. **无串色**：每个通道组独立管理，不会相互影响

## 日期
2025-11-02

