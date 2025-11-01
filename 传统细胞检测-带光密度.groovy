/**
 * 传统细胞检测（不使用CellPose）- 包含光密度测量
 * 
 * 适合：
 * - 没有CellPose扩展
 * - 简单的细胞检测
 * - H&E或DAB染色图像
 */

import qupath.imagej.detect.cells.WatershedCellDetection
import qupath.lib.analysis.features.ObjectMeasurements

println "=== 传统细胞检测开始 ==="

// 1. 选择要检测的区域
selectAnnotations()
def annotations = getSelectedObjects()

if (annotations.isEmpty()) {
    println "⚠️ 请先选择要检测的区域"
    return
}

println "在 ${annotations.size()} 个区域内检测..."

// 2. 配置检测参数
def detectionChannel = 'Hematoxylin OD'  // 或 'DAB OD', 'DAPI' 等
def requestedPixelSize = 0.5             // 像素大小（微米）
def backgroundRadius = 8.0               // 背景半径
def medianRadius = 0.0                   // 中值滤波半径
def sigma = 1.5                          // 高斯平滑
def minArea = 10.0                       // 最小细胞面积（平方微米）
def maxArea = 400.0                      // 最大细胞面积
def threshold = 0.1                      // 检测阈值
def cellExpansion = 5.0                  // 细胞扩展（从核到边界）
def includeNuclei = true                 // 包括细胞核
def smoothBoundaries = true              // 平滑边界
def makeMeasurements = true              // 创建测量值 ⭐ 重要！

// 3. 运行检测
def detectionParams = [
    'detectionImageBrightfield': detectionChannel,
    'requestedPixelSizeMicrons': requestedPixelSize,
    'backgroundRadiusMicrons': backgroundRadius,
    'medianRadiusMicrons': medianRadius,
    'sigmaMicrons': sigma,
    'minAreaMicrons': minArea,
    'maxAreaMicrons': maxArea,
    'threshold': threshold,
    'watershedPostProcess': true,
    'cellExpansionMicrons': cellExpansion,
    'includeNuclei': includeNuclei,
    'smoothBoundaries': smoothBoundaries,
    'makeMeasurements': makeMeasurements
]

runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', 
    detectionParams.collect { k, v -> 
        "\"${k}\":${v instanceof String ? "\"${v}\"" : v}" 
    }.join(',')
)

println "✅ 细胞检测完成"

// 4. 添加额外的光密度测量（确保有OD测量）
def cells = getCellObjects()
println "检测到 ${cells.size()} 个细胞"

if (cells.size() > 0) {
    println "添加光密度测量..."
    
    cells.each { cell ->
        ObjectMeasurements.addIntensityMeasurements(
            getCurrentServer(),
            cell,
            1.0
        )
    }
    
    println "✅ 光密度测量添加完成"
}

// 5. 刷新界面
fireHierarchyUpdate()

// 6. 显示可用的测量值
if (cells.size() > 0) {
    def firstCell = cells[0]
    println "\n=== 可用的测量值（前20个）==="
    firstCell.getMeasurementList().getMeasurements().take(20).each { m ->
        println "  ${m.getName()}"
    }
    
    println "\n=== 光密度相关测量 ==="
    firstCell.getMeasurementList().getMeasurements()
        .findAll { it.getName().contains("OD") }
        .each { m ->
            println "  ${m.getName()}: ${m.getValue()}"
        }
}

println "\n✅ 全部完成！"
println "\n现在可以使用分类器:"
println "  Classify → Object classification → Single measurement classifier"
println "  Object filter: Cells"
println "  Measurement: Cell: DAB OD mean (或 Nucleus: DAB OD mean)"
println "  设置阈值并应用！"

