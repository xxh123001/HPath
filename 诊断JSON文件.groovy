/**
 * JSON文件诊断工具
 * 帮助查看JSON文件的格式和内容
 */

import com.google.gson.*
import qupath.lib.io.GsonTools
import java.nio.file.Files

println "=== JSON文件诊断工具 ==="
println ""

// 选择一个JSON文件
def chooser = new javax.swing.JFileChooser()
chooser.dialogTitle = "选择要诊断的JSON文件"
chooser.fileFilter = new javax.swing.filechooser.FileNameExtensionFilter("JSON Files", "json", "geojson")

if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
    def file = chooser.selectedFile
    
    println "文件: ${file.name}"
    println "路径: ${file.absolutePath}"
    println "大小: ${file.length()} bytes"
    println ""
    
    // 读取内容
    def jsonText = file.text
    
    println "=== 文件内容前500字符 ==="
    println jsonText.take(500)
    println "..."
    println ""
    
    // 解析JSON
    try {
        def element = new JsonParser().parse(jsonText)
        
        println "=== JSON结构分析 ==="
        println "根类型: ${element.class.simpleName}"
        
        if (element.isJsonObject()) {
            def obj = element.asJsonObject
            println "\n对象包含的keys:"
            obj.keySet().each { key ->
                def value = obj.get(key)
                println "  - ${key}: ${value.class.simpleName}"
                
                if (key == "type") {
                    println "    值: ${value.asString}"
                }
                if (key == "features" && value.isJsonArray()) {
                    println "    数组大小: ${value.asJsonArray.size()}"
                }
            }
        } else if (element.isJsonArray()) {
            def arr = element.asJsonArray
            println "是JSON数组"
            println "数组大小: ${arr.size()}"
            
            if (arr.size() > 0) {
                println "\n第一个元素:"
                def first = arr.get(0)
                println "  类型: ${first.class.simpleName}"
                
                if (first.isJsonObject()) {
                    println "  包含keys: ${first.asJsonObject.keySet()}"
                }
            }
        }
        
        println "\n=== 尝试QuPath解析 ==="
        
        try {
            def objects = GsonTools.parseObjectsFromGeoJSON(element)
            println "✅ QuPath解析成功！"
            println "解析出 ${objects.size()} 个对象"
            
            if (objects.size() > 0) {
                println "\n前3个对象:"
                objects.take(3).each { obj ->
                    println "  - ${obj.class.simpleName}"
                    if (obj.getROI()) {
                        println "    ROI: ${obj.getROI().getRoiName()}"
                        println "    位置: (${obj.getROI().getCentroidX()}, ${obj.getROI().getCentroidY()})"
                    }
                }
            } else {
                println "\n⚠️ 解析结果为0个对象"
                println "\n可能的原因:"
                println "1. JSON不是标准GeoJSON格式"
                println "2. 缺少必要的字段（type, geometry等）"
                println "3. 坐标系统不兼容"
            }
            
        } catch (Exception e) {
            println "❌ QuPath解析失败！"
            println "错误: ${e.message}"
            println "\n完整错误信息:"
            e.printStackTrace()
            
            println "\n=== 可能的解决方案 ==="
            println "1. 确保JSON是从QuPath导出的GeoJSON格式"
            println "2. 检查JSON是否有'type': 'FeatureCollection'"
            println "3. 检查是否有'features'数组"
            println "4. 运行修复脚本转换格式"
        }
        
    } catch (Exception e) {
        println "❌ JSON解析失败！"
        println "错误: ${e.message}"
        e.printStackTrace()
    }
    
} else {
    println "未选择文件"
}

println "\n=== 诊断完成 ==="

