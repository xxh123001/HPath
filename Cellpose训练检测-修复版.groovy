/**
 * Cellpose Detection - 修复版
 * 
 * 使用说明：
 * 1. 先运行"检查图像通道.groovy"查看你的图像有哪些通道
 * 2. 修改下面的 .channels() 参数为实际的通道名称
 * 3. 如果是RGB图像，使用 .channels('Red') 或 .channels('Red', 'Green', 'Blue')
 */

import qupath.ext.biop.cellpose.Cellpose2D

def imageData = getCurrentImageData()
if (imageData == null) {
    println "❌ 请先打开一个图像"
    return
}

// 检查图像类型
def imageType = imageData.getImageType().toString()
println "图像类型: ${imageType}"

// 获取通道信息
def server = imageData.getServer()
def channels = server.getMetadata().getChannels()
println "可用通道: ${channels.collect{it.getName()}.join(', ')}"

// 根据图像类型选择通道
def channelToUse = null

if (imageType.contains("RGB") || imageType.contains("BRIGHTFIELD")) {
    // RGB或明场图像
    channelToUse = ['Red']  // 或者用其他通道
    println "✅ 检测到RGB/明场图像，使用通道: ${channelToUse}"
} else if (channels.size() > 0) {
    // 使用第一个通道
    channelToUse = [channels[0].getName()]
    println "✅ 使用通道: ${channelToUse}"
} else {
    println "❌ 无法确定通道，请手动指定"
    return
}

// 构建Cellpose检测器
def pathModel = 'cyto3'
def cellpose = Cellpose2D.builder(pathModel)
        .pixelSize(0.5)                    // 分辨率 (um)
        .channels(channelToUse as String[]) // 使用检测到的通道
        .diameter(30)                       // 细胞直径（像素）
        .cellprobThreshold(0.0)            // 细胞概率阈值
        .flowThreshold(0.4)                // Flow阈值
        .measureShape()                     // 添加形状测量
        .measureIntensity()                 // 添加强度测量
        .build()

// 获取要处理的对象
def pathObjects = getSelectedObjects()
if (pathObjects.isEmpty()) {
    println "⚠️ 没有选中的对象，将处理所有标注"
    pathObjects = getAnnotationObjects()
}

if (pathObjects.isEmpty()) {
    println "❌ 没有可处理的对象"
    println "💡 请先创建一个矩形标注框来圈定检测区域"
    return
}

println "开始检测，处理 ${pathObjects.size()} 个区域..."

// 运行检测
cellpose.detectObjects(imageData, pathObjects)

println '✅ Cellpose检测完成'
println '💡 提示：如果结果不理想，可以调整以下参数：'
println '   - diameter: 细胞直径（像素）'
println '   - cellprobThreshold: 降低此值检测更多对象'
println '   - flowThreshold: 调整对象边界质量'









