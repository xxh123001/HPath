/**
 * 检查当前图像的通道信息
 * 用于确定Cellpose脚本中应该使用什么通道名称
 */

def imageData = getCurrentImageData()
if (imageData == null) {
    println "❌ 请先打开一个图像"
    return
}

def server = imageData.getServer()
def channels = server.getMetadata().getChannels()

println "=" * 60
println "图像信息："
println "  名称: ${server.getMetadata().getName()}"
println "  类型: ${imageData.getImageType()}"
println "  宽度: ${server.getWidth()}"
println "  高度: ${server.getHeight()}"
println "  通道数量: ${channels.size()}"
println "=" * 60

println "\n可用的通道列表："
println "-" * 60

channels.eachWithIndex { channel, index ->
    println "通道 ${index + 1}: ${channel.getName()}"
    println "  - 颜色: ${channel.getColor()}"
    println "  - 类型: ${channel.getClass().getSimpleName()}"
}

println "=" * 60
println "\n✅ 检查完成！"
println "\n📝 在Cellpose脚本中使用通道名称："
println "   .channels('${channels[0].getName()}')"
if (channels.size() > 1) {
    println "   或"
    println "   .channels('${channels[0].getName()}', '${channels[1].getName()}')"
}

// 如果是RGB图像
if (imageData.getImageType().toString().contains("RGB")) {
    println "\n⚠️ 这是RGB图像，建议使用："
    println "   .channels('Red', 'Green', 'Blue')"
    println "   或"
    println "   .channels('Red')  // 只用红色通道"
}

println "\n💡 提示：复制上面的通道名称，替换Cellpose脚本中的 .channels() 参数"














