/**
 * 快速检查为什么JSON显示0 objects
 */

import com.google.gson.*
import qupath.lib.io.GsonTools
import qupath.lib.gui.commands.JsonToGeoJsonHandler

// 测试一个显示0 objects的文件
def testFile = new File("/Users/felix/Downloads/K2023-1409.json")  // 改成你的文件路径

if (!testFile.exists()) {
    println "❌ 文件不存在: ${testFile.absolutePath}"
    println "请修改脚本中的文件路径"
    return
}

println "=== 测试文件: ${testFile.name} ==="

// 读取JSON
def jsonText = testFile.text
println "文件大小: ${jsonText.length()} bytes"
println "\n前200字符:"
println jsonText.take(200)
println "...\n"

// 解析JSON
def element = GsonTools.getInstance().fromJson(jsonText, JsonElement.class)
println "JSON类型: ${element.class.simpleName}"

// 检查是否GeoJSON
if (element.isJsonObject()) {
    def obj = element.asJsonObject
    if (obj.has("type")) {
        println "type字段: ${obj.get('type')}"
    }
    println "keys: ${obj.keySet()}"
}

// 尝试QuPath直接解析
println "\n=== 尝试1: 直接解析 ==="
try {
    def objects1 = GsonTools.parseObjectsFromGeoJSON(element)
    println "结果: ${objects1.size()} objects"
} catch (Exception e) {
    println "失败: ${e.message}"
}

// 尝试使用转换器
println "\n=== 尝试2: 使用JsonToGeoJsonHandler转换 ==="
try {
    def converted = JsonToGeoJsonHandler.convertToGeoJSON(element)
    if (converted != null) {
        println "转换成功！"
        println "转换后类型: ${converted.class.simpleName}"
        
        if (converted.isJsonObject() && converted.asJsonObject.has("type")) {
            println "type: ${converted.asJsonObject.get('type')}"
        }
        if (converted.isJsonObject() && converted.asJsonObject.has("features")) {
            println "features数量: ${converted.asJsonObject.get('features').asJsonArray.size()}"
        }
        
        // 解析转换后的
        def objects2 = GsonTools.parseObjectsFromGeoJSON(converted)
        println "解析结果: ${objects2.size()} objects ✅"
        
        if (objects2.size() > 0) {
            println "\n前3个对象:"
            objects2.take(3).each { obj ->
                println "  - ${obj.class.simpleName}"
                if (obj.getROI()) {
                    println "    位置: (${obj.getROI().getCentroidX()}, ${obj.getROI().getCentroidY()})"
                }
            }
        }
    } else {
        println "❌ 转换失败！返回null"
    }
} catch (Exception e) {
    println "❌ 转换出错: ${e.message}"
    e.printStackTrace()
}

println "\n=== 诊断完成 ==="
println "\n如果转换成功但还是显示0，请查看日志:"
println "Help → Show log"
println "搜索: Loading object file"


