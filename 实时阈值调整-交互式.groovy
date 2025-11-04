/**
 * 实时阈值调整 - 交互式光密度分类
 * 
 * 功能：
 * 1. 实时调整阈值并预览效果
 * 2. 根据光密度测量值自动分类对象
 * 3. 实时刷新显示，立即看到分类结果
 * 4. 支持多种光密度测量参数（DAB OD mean, Nucleus OD mean等）
 * 
 * 使用方法：
 * 1. 在QuPath中打开已检测的图像（需要有光密度测量）
 * 2. 运行此脚本
 * 3. 在对话框中输入阈值参数
 * 4. 实时查看分类效果
 */

import qupath.lib.objects.PathObject
import qupath.lib.classification.PathClassFactory
import qupath.lib.gui.dialogs.Dialogs
import javafx.application.Platform
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.geometry.Insets
import javafx.stage.Stage

println "=".repeat(80)
println "🎛️  实时阈值调整工具"
println "=".repeat(80)
println ""

// ===== 1. 获取当前图像中的对象 =====
def imageData = getCurrentImageData()
if (!imageData) {
    Dialogs.showErrorMessage("错误", "请先打开一个图像！")
    return
}

// 获取所有检测对象
def allObjects = []
def detections = getDetectionObjects()
def cells = getCellObjects()

if (detections.isEmpty() && cells.isEmpty()) {
    Dialogs.showErrorMessage("错误", "当前图像中没有检测对象或细胞！\n请先运行细胞检测。")
    return
}

allObjects.addAll(detections)
allObjects.addAll(cells)

println "找到 ${allObjects.size()} 个对象"
println "  - 检测对象: ${detections.size()}"
println "  - 细胞: ${cells.size()}"

// ===== 2. 分析可用的光密度测量参数 =====
def availableODMeasurements = []
if (allObjects.size() > 0) {
    def firstObj = allObjects[0]
    firstObj.getMeasurementList().getMeasurementNames().each { name ->
        if (name.contains("OD") && name.contains("mean")) {
            availableODMeasurements.add(name)
        }
    }
}

if (availableODMeasurements.isEmpty()) {
    Dialogs.showErrorMessage("错误", "对象中没有光密度测量值！\n请确保运行检测时启用了光密度计算。")
    return
}

println "\n可用的光密度测量参数："
availableODMeasurements.each { println "  - ${it}" }

// ===== 3. 分析光密度分布 =====
def measurementName = availableODMeasurements[0]  // 默认使用第一个

// 收集所有对象的光密度值
def intensityValues = []
def objectsWithIntensity = [:]

allObjects.each { obj ->
    def intensity = obj.getMeasurementList().getMeasurementValue(measurementName)
    if (intensity != null && !Double.isNaN(intensity)) {
        intensityValues.add(intensity as Double)
        objectsWithIntensity[obj] = intensity as Double
    }
}

if (intensityValues.isEmpty()) {
    Dialogs.showErrorMessage("错误", "没有找到有效的光密度测量值！")
    return
}

// 计算统计信息
intensityValues.sort()
def meanValue = intensityValues.sum() / intensityValues.size()
def medianValue = intensityValues[intensityValues.size() / 2]
def minValue = intensityValues.min()
def maxValue = intensityValues.max()
def p25 = intensityValues[(int)(intensityValues.size() * 0.25)]
def p75 = intensityValues[(int)(intensityValues.size() * 0.75)]
def p90 = intensityValues[(int)(intensityValues.size() * 0.90)]

println "\n📊 光密度分布统计"
println "=".repeat(80)
println "测量参数: ${measurementName}"
println "对象数量: ${intensityValues.size()}"
println "平均值: ${String.format('%.2f', meanValue)}"
println "中位数: ${String.format('%.2f', medianValue)}"
println "范围: ${String.format('%.2f', minValue)} - ${String.format('%.2f', maxValue)}"
println "25百分位: ${String.format('%.2f', p25)}"
println "75百分位: ${String.format('%.2f', p75)}"
println "90百分位: ${String.format('%.2f', p90)}"

// ===== 4. 创建交互式GUI =====
def dialog = new Dialog<Map>()
dialog.setTitle("🎛️ 实时阈值调整")
dialog.setHeaderText("根据光密度阈值实时分类对象")

// 创建对话框内容
def grid = new GridPane()
grid.setHgap(10)
grid.setVgap(10)
grid.setPadding(new Insets(20, 20, 10, 20))

// 测量参数选择
def measurementCombo = new ComboBox<String>()
measurementCombo.getItems().addAll(availableODMeasurements)
measurementCombo.setValue(measurementName)
measurementCombo.setMinWidth(300)

grid.add(new Label("光密度测量参数:"), 0, 0)
grid.add(measurementCombo, 1, 0)

// 阈值滑动条
def thresholdSlider = new Slider(minValue, maxValue, medianValue)
thresholdSlider.setShowTickLabels(true)
thresholdSlider.setShowTickMarks(true)
thresholdSlider.setMajorTickUnit((maxValue - minValue) / 10)
thresholdSlider.setMinorTickCount(5)
thresholdSlider.setSnapToTicks(false)

// 阈值输入框
def thresholdField = new TextField()
thresholdField.setText(String.format('%.2f', medianValue))

// 同步滑动条和输入框
thresholdSlider.valueProperty().addListener { _, _, newVal ->
    thresholdField.setText(String.format('%.2f', newVal))
}

thresholdField.textProperty().addListener { _, _, newText ->
    try {
        def value = Double.parseDouble(newText)
        if (value >= minValue && value <= maxValue) {
            thresholdSlider.setValue(value)
        }
    } catch (Exception e) {
        // 忽略无效输入
    }
}

def thresholdBox = new VBox(5)
thresholdBox.getChildren().addAll(thresholdSlider, thresholdField)

grid.add(new Label("阈值:"), 0, 1)
grid.add(thresholdBox, 1, 1)

// 阳性类别名称
def positiveClassField = new TextField()
positiveClassField.setText("Positive")
grid.add(new Label("阳性类别:"), 0, 2)
grid.add(positiveClassField, 1, 2)

// 阴性类别名称
def negativeClassField = new TextField()
negativeClassField.setText("Negative")
grid.add(new Label("阴性类别:"), 0, 3)
grid.add(negativeClassField, 1, 3)

// 统计信息显示
def statsText = new TextArea()
statsText.setEditable(false)
statsText.setPrefRowCount(6)
statsText.setWrapText(true)
statsText.text = """
统计信息:
- 总对象数: ${intensityValues.size()}
- 平均值: ${String.format('%.2f', meanValue)}
- 中位数: ${String.format('%.2f', medianValue)}
- 25/75/90百分位: ${String.format('%.2f', p25)} / ${String.format('%.2f', p75)} / ${String.format('%.2f', p90)}

建议阈值:
- 保守: ${String.format('%.2f', p75)}
- 中等: ${String.format('%.2f', medianValue)}
- 宽松: ${String.format('%.2f', p25)}
"""

grid.add(new Label("统计信息:"), 0, 4)
grid.add(statsText, 1, 4)

// 实时预览统计
def previewStats = new Label("等待阈值输入...")
previewStats.setWrapText(true)
previewStats.setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32;")

// 实时预览按钮（更新视图）
def previewButton = new Button("🔍 实时预览")
previewButton.setStyle("-fx-font-size: 12px;")
previewButton.setOnAction { event ->
    try {
        def threshold = Double.parseDouble(thresholdField.getText())
        def positiveClassName = positiveClassField.getText().trim() ?: "Positive"
        def negativeClassName = negativeClassField.getText().trim() ?: "Negative"
        def currentMeasurement = measurementCombo.getValue()
        
        // 创建或获取PathClass
        def positiveClass = PathClassFactory.getPathClass(positiveClassName)
        def negativeClass = PathClassFactory.getPathClass(negativeClassName)
        
        // 临时应用分类（用于预览）
        def changedCount = 0
        allObjects.each { obj ->
            def intensity = obj.getMeasurementList().getMeasurementValue(currentMeasurement)
            if (intensity != null && !Double.isNaN(intensity)) {
                def newClass = (intensity > threshold) ? positiveClass : negativeClass
                obj.setPathClass(newClass)
                changedCount++
            }
        }
        
        // 刷新显示
        fireHierarchyUpdate()
        
        // 计算统计
        def positiveCount = allObjects.count { 
            def intensity = it.getMeasurementList().getMeasurementValue(currentMeasurement)
            intensity != null && !Double.isNaN(intensity) && intensity > threshold
        }
        
        previewStats.text = """
✅ 预览已更新（视图已刷新）
当前阈值: ${String.format('%.2f', threshold)}
- 阳性对象: ${positiveCount} (${String.format('%.1f', positiveCount*100.0/allObjects.size())}%)
- 阴性对象: ${allObjects.size() - positiveCount} (${String.format('%.1f', (allObjects.size()-positiveCount)*100.0/allObjects.size())}%)
- 已更新: ${changedCount} 个对象
        """.trim()
        previewStats.setStyle("-fx-font-weight: bold; -fx-text-fill: #1976d2;")
        
    } catch (Exception e) {
        previewStats.text = "❌ 预览时出错: ${e.message}"
        previewStats.setStyle("-fx-text-fill: #c62828;")
    }
}

def previewBox = new VBox(5)
previewBox.getChildren().addAll(previewButton, previewStats)

grid.add(new Label("实时预览:"), 0, 5)
grid.add(previewBox, 1, 5)

// 更新预览函数
def updatePreview = { ->
    try {
        def threshold = Double.parseDouble(thresholdField.getText())
        def currentMeasurement = measurementCombo.getValue()
        
        // 重新收集当前测量参数的值
        def currentValues = [:]
        allObjects.each { obj ->
            def intensity = obj.getMeasurementList().getMeasurementValue(currentMeasurement)
            if (intensity != null && !Double.isNaN(intensity)) {
                currentValues[obj] = intensity as Double
            }
        }
        
        // 计算统计
        def positiveCount = currentValues.values().count { it > threshold }
        def negativeCount = currentValues.size() - positiveCount
        def positivePercent = currentValues.size() > 0 ? 
            (positiveCount * 100.0 / currentValues.size()) : 0
        
        previewStats.text = """
当前阈值: ${String.format('%.2f', threshold)}
- 阳性对象: ${positiveCount} (${String.format('%.1f', positivePercent)}%)
- 阴性对象: ${negativeCount} (${String.format('%.1f', 100-positivePercent)}%)
        """.trim()
    } catch (Exception e) {
        previewStats.text = "❌ 无效的阈值值"
        previewStats.setStyle("-fx-text-fill: #c62828;")
    }
}

// 绑定实时更新（仅更新统计，不更新视图）
thresholdSlider.valueProperty().addListener { _, _, _ -> updatePreview() }
thresholdField.textProperty().addListener { _, _, _ -> updatePreview() }
measurementCombo.valueProperty().addListener { _, _, _ -> updatePreview() }

// 初始更新
Platform.runLater { updatePreview() }

// 应用按钮（确认分类）
def applyButton = new Button("✅ 确认应用")
applyButton.setDefaultButton(true)
applyButton.setOnAction { event ->
    try {
        def threshold = Double.parseDouble(thresholdField.getText())
        def positiveClassName = positiveClassField.getText().trim() ?: "Positive"
        def negativeClassName = negativeClassField.getText().trim() ?: "Negative"
        def currentMeasurement = measurementCombo.getValue()
        
        // 创建或获取PathClass
        def positiveClass = PathClassFactory.getPathClass(positiveClassName)
        def negativeClass = PathClassFactory.getPathClass(negativeClassName)
        
        // 应用分类
        def changedCount = 0
        allObjects.each { obj ->
            def intensity = obj.getMeasurementList().getMeasurementValue(currentMeasurement)
            if (intensity != null && !Double.isNaN(intensity)) {
                def newClass = (intensity > threshold) ? positiveClass : negativeClass
                if (obj.getPathClass() != newClass) {
                    obj.setPathClass(newClass)
                    changedCount++
                }
            }
        }
        
        // 刷新显示（如果之前没有预览过，这里也会刷新）
        fireHierarchyUpdate()
        
        // 显示结果
        def positiveCount = allObjects.count { 
            def intensity = it.getMeasurementList().getMeasurementValue(currentMeasurement)
            intensity != null && !Double.isNaN(intensity) && intensity > threshold
        }
        
        Dialogs.showInfoNotification(
            "分类已确认",
            "已确认 ${changedCount} 个对象的分类\n" +
            "阳性: ${positiveCount} | 阴性: ${allObjects.size() - positiveCount}\n\n" +
            "提示：可以继续调整阈值并预览效果"
        )
        
        // 更新预览统计
        previewStats.text = """
✅ 分类已确认应用
当前阈值: ${String.format('%.2f', threshold)}
- 阳性对象: ${positiveCount} (${String.format('%.1f', positiveCount*100.0/allObjects.size())}%)
- 阴性对象: ${allObjects.size() - positiveCount} (${String.format('%.1f', (allObjects.size()-positiveCount)*100.0/allObjects.size())}%)
        """.trim()
        previewStats.setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32;")
        
        println "\n✅ 分类已确认应用"
        println "  - 更新了 ${changedCount} 个对象"
        println "  - 阳性: ${positiveCount} (${String.format('%.1f', positiveCount*100.0/allObjects.size())}%)"
        println "  - 阴性: ${allObjects.size() - positiveCount} (${String.format('%.1f', (allObjects.size()-positiveCount)*100.0/allObjects.size())}%)"
        println "  - 使用的阈值: ${threshold}"
        println "  - 使用的测量参数: ${currentMeasurement}"
        
    } catch (Exception e) {
        Dialogs.showErrorNotification("错误", "应用分类时出错: ${e.message}")
        println "❌ 错误: ${e.message}"
        e.printStackTrace()
    }
}

// 取消按钮
def cancelButton = new Button("取消")
cancelButton.setCancelButton(true)

// 按钮布局
def buttonBox = new HBox(10)
buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT)
buttonBox.getChildren().addAll(cancelButton, previewButton, applyButton)

grid.add(buttonBox, 0, 6, 2, 1)

// 设置对话框
dialog.getDialogPane().setContent(grid)
dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL)

// 设置对话框大小
dialog.getDialogPane().setMinWidth(650)
dialog.getDialogPane().setMinHeight(550)

// 显示对话框
def result = dialog.showAndWait()

println "\n" + "=".repeat(80)
println "👋 阈值调整工具已关闭"

