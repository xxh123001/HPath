/**
 * CellPose细胞检测脚本 - 包含光密度测量
 * 
 * 功能:
 * - 使用CellPose进行细胞分割
 * - 自动添加光密度测量
 * - 可用于Single measurement classifier
 */

import qupath.ext.biop.cellpose.Cellpose2D
import qupath.lib.analysis.features.ObjectMeasurements

println "=== CellPose 细胞检测开始 ==="

// 检查是否有选中的区域
def annotations = getSelectedObjects().findAll { it.isAnnotation() }
if (annotations.isEmpty()) {
    println "⚠️ 请先选择要检测的区域（Annotation）"
    println "或者使用 selectAnnotations() 选择所有标注"
    return
}

println "在 ${annotations.size()} 个区域内进行检测..."

// ===== CellPose 参数配置 =====
def cellpose = Cellpose2D.builder("cyto2")  // 使用cyto2模型（细胞+细胞核）
    // 或使用其他模型:
    // .builder("nuclei")  // 只检测细胞核
    // .builder("cyto")    // 细胞质模型
    
    .pixelSize(0.5)              // 像素大小（微米）
    .diameter(30)                // 预期细胞直径（像素）
    .cellExpansion(2.0)          // 细胞扩展（从核到细胞边界）
    .cellConstrainScale(1.5)     // 细胞约束比例
    .channels('DAPI', 'Membrane') // 使用的通道
    // 或简单使用:
    // .channels(0, 2)  // 通道索引
    
    .preprocess(                 // 预处理（可选）
        qupath.ext.biop.cellpose.Cellpose2D.PreprocessMethod.MEDIAN
    )
    
    .tileSize(1024)              // 分块大小
    .maskThreshold(0.0)          // mask阈值（-4到6，默认0）
    .flowThreshold(0.4)          // flow阈值（0到1，默认0.4）
    
    .measureShape()              // 测量形状特征
    .measureIntensity()          // 测量强度（包括光密度！）
    
    .createAnnotations()         // 或使用 .createDetections()
    .build()

// 运行CellPose检测
cellpose.detectObjects(getCurrentImageData(), annotations)

println "✅ CellPose检测完成"

// ===== 添加额外的光密度测量 =====
println "添加光密度测量..."

def detections = getDetectionObjects()
println "找到 ${detections.size()} 个检测对象"

// 为所有检测添加光密度测量
detections.each { detection ->
    ObjectMeasurements.addIntensityMeasurements(
        getCurrentServer(),
        detection,
        1.0
    )
}

println "✅ 光密度测量添加完成"

// 刷新界面
fireHierarchyUpdate()

// ===== 显示结果 =====
println "\n=== 检测结果 ==="
println "检测对象数量: ${detections.size()}"

if (detections.size() > 0) {
    def first = detections[0]
    println "\n可用的测量值（示例）:"
    first.getMeasurementList().getMeasurements().take(15).each { m ->
        println "  ${m.getName()}: ${m.getValue()}"
    }
    
    println "\n光密度测量值:"
    first.getMeasurementList().getMeasurements()
        .findAll { it.getName().contains("OD") }
        .each { m ->
            println "  ${m.getName()}: ${m.getValue()}"
        }
}

println "\n✅ 全部完成！现在可以使用Single measurement classifier了"
println "   Object filter: Cells"
println "   Measurement: Detection: DAB OD mean (或其他光密度测量)"

