/**
 * 明场/H&E图像的Cellpose检测
 * 适用于你当前的组织切片图像
 */

import qupath.ext.biop.cellpose.Cellpose2D

def imageData = getCurrentImageData()
if (imageData == null) {
    println "❌ 请先打开一个图像"
    return
}

// 检查是否有选中的标注区域
def pathObjects = getSelectedObjects()
if (pathObjects.isEmpty()) {
    println "❌ 请先选中一个标注区域（黄色框）"
    println "💡 已经有黄色框了，请点击它选中"
    return
}

println "✅ 将在 ${pathObjects.size()} 个区域中检测细胞"

// 对于明场/H&E图像，使用Red, Green, Blue通道
// 或者使用光学密度(OD)通道
def cellpose = Cellpose2D.builder('cyto2')  // ⚠️ 使用cyto2避免网络下载问题
        .pixelSize(0.5)                      // 分辨率 (微米)
        .channels('Red')                     // ⭐ 明场图像用Red, Green或Blue
        .diameter(15)                        // 细胞直径（像素），根据实际调整
        .cellprobThreshold(0.0)              // 细胞概率阈值
        .flowThreshold(0.4)                  // Flow阈值
        .measureShape()                      // 测量形状
        .measureIntensity()                  // 测量强度
        .build()

println "开始Cellpose检测..."

// 运行检测
cellpose.detectObjects(imageData, pathObjects)

println '✅ Cellpose检测完成！'
println "检测到的对象会显示在标注列表中"

// 统计结果
def detections = getDetectionObjects()
println "共检测到 ${detections.size()} 个细胞"

