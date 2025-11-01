/**
 * 阳性细胞检测 - 完整版
 * 
 * 功能:
 * - 检测细胞
 * - 自动计算光密度
 * - 自动分类为阳性/阴性
 * - 生成统计报告
 */

println "=== 阳性细胞检测完整流程 ==="

// ===== Step 1: 选择区域 =====
selectAnnotations()
def annotations = getSelectedObjects()

if (annotations.isEmpty()) {
    println "⚠️ 请先创建并选择标注区域"
    return
}

println "Step 1: 选中 ${annotations.size()} 个区域 ✓"

// ===== Step 2: 设置染色类型 =====
setImageType('BRIGHTFIELD_H_DAB')
println "Step 2: 设置图像类型为 H-DAB ✓"

// ===== Step 3: 估计染色向量（如果还没做）=====
// setColorDeconvolutionStains('{"Name" : "H-DAB default", "Stain 1" : "Hematoxylin", "Values 1" : "0.65111 0.70119 0.29049", "Stain 2" : "DAB", "Values 2" : "0.26917 0.56824 0.77759", "Background" : " 255 255 255"}')
println "Step 3: 使用默认染色向量 ✓"

// ===== Step 4: 运行阳性细胞检测 =====
println "Step 4: 运行阳性细胞检测..."

runPlugin('qupath.imagej.detect.cells.PositiveCellDetection', 
  '{"detectionImageBrightfield":"Hematoxylin OD",' +
   '"requestedPixelSizeMicrons":0.5,' +
   '"backgroundRadiusMicrons":8.0,' +
   '"medianRadiusMicrons":0.0,' +
   '"sigmaMicrons":1.5,' +
   '"minAreaMicrons":10.0,' +
   '"maxAreaMicrons":400.0,' +
   '"threshold":0.1,' +
   '"maxBackground":2.0,' +
   '"watershedPostProcess":true,' +
   '"cellExpansionMicrons":5.0,' +
   '"includeNuclei":true,' +
   '"smoothBoundaries":true,' +
   '"makeMeasurements":true,' +
   '"thresholdCompartment":"Nucleus: DAB OD mean",' +  // 基于核的DAB光密度
   '"thresholdPositive1":0.2,' +                        // 1+阈值
   '"thresholdPositive2":0.4,' +                        // 2+阈值
   '"thresholdPositive3":0.6,' +                        // 3+阈值
   '"singleThreshold":true}')                           // 单阈值模式

println "检测完成 ✓"

// ===== Step 5: 获取结果 =====
def cells = getCellObjects()
println "\nStep 5: 检测到 ${cells.size()} 个细胞"

// 统计阳性细胞
def positive = cells.findAll { 
    it.getPathClass()?.toString()?.contains("Positive") 
}
def negative = cells.findAll { 
    it.getPathClass()?.toString() == "Negative" 
}

println "  - 阳性细胞: ${positive.size()}"
println "  - 阴性细胞: ${negative.size()}"
if (cells.size() > 0) {
    println "  - 阳性率: ${String.format('%.2f', positive.size() * 100.0 / cells.size())}%"
}

// ===== Step 6: 验证测量值 =====
if (cells.size() > 0) {
    def firstCell = cells[0]
    
    println "\n=== 第一个细胞的测量值示例 ==="
    println "光密度测量:"
    firstCell.getMeasurementList().getMeasurements()
        .findAll { it.getName().contains("OD") }
        .each { m ->
            println "  ${m.getName()}: ${m.getValue()}"
        }
}

// ===== Step 7: 生成报告 =====
println "\n=== 检测报告 ==="
println "图像: ${getCurrentImageData().getServer().getMetadata().getName()}"
println "检测区域数: ${annotations.size()}"
println "总细胞数: ${cells.size()}"
println "阳性细胞: ${positive.size()} (${positive.size() > 0 ? String.format('%.2f', positive.size() * 100.0 / cells.size()) : 0}%)"
println "阴性细胞: ${negative.size()} (${negative.size() > 0 ? String.format('%.2f', negative.size() * 100.0 / cells.size()) : 0}%)"

println "\n✅ 全部完成！"
println "\n现在可以:"
println "1. 使用 Measure → Show measurements 查看所有测量值"
println "2. 使用 Classify → Object classification → Single measurement classifier 进一步分类"
println "3. 导出结果: File → Export objects → Export as GeoJSON"

