/**
 * 查看JSON文件的实际结构
 * 找出为什么转换返回null
 */

import com.google.gson.*

// 你的JSON文件路径
def file = new File("/Users/felix/Desktop/image_data_json/K2023-3068.json")

println "=== 分析JSON文件 ==="
println "文件: ${file.name}"

// 读取内容
def jsonText = file.text

println "\n=== 前500字符 ==="
println jsonText.take(500)
println "...\n"

// 解析
def element = new JsonParser().parse(jsonText)

println "=== JSON根类型 ==="
println "类型: ${element.class.simpleName}"

if (element.isJsonObject()) {
    def obj = element.asJsonObject
    
    println "\n=== 对象的所有keys ==="
    obj.keySet().each { key ->
        def value = obj.get(key)
        println "  ${key}: ${value.class.simpleName}"
        
        // 显示值的类型和大小
        if (value.isJsonArray()) {
            println "    → 数组大小: ${value.asJsonArray.size()}"
            if (value.asJsonArray.size() > 0) {
                println "    → 第一个元素: ${value.asJsonArray.get(0).class.simpleName}"
            }
        } else if (value.isJsonObject()) {
            println "    → 对象keys: ${value.asJsonObject.keySet()}"
        } else if (value.isJsonPrimitive()) {
            def prim = value.asJsonPrimitive
            if (prim.isString()) {
                println "    → 字符串: ${prim.asString.take(50)}"
            } else {
                println "    → 值: ${prim}"
            }
        }
    }
    
} else if (element.isJsonArray()) {
    def arr = element.asJsonArray
    
    println "\n=== JSON数组 ==="
    println "数组大小: ${arr.size()}"
    
    if (arr.size() > 0) {
        println "\n第一个元素:"
        def first = arr.get(0)
        println "  类型: ${first.class.simpleName}"
        
        if (first.isJsonObject()) {
            println "  keys: ${first.asJsonObject.keySet()}"
            
            println "\n  详细内容:"
            first.asJsonObject.keySet().each { key ->
                def val = first.asJsonObject.get(key)
                println "    ${key}: ${val.class.simpleName}"
                if (val.isJsonPrimitive()) {
                    println "      = ${val}"
                }
            }
        }
    }
}

println "\n=== JsonToGeoJsonHandler可以处理的格式 ==="
println "1. 包含'coordinates'字段的对象"
println "2. 包含'geometry'字段的对象"
println "3. 包含'points'或'polygon'字段的对象"
println "4. 包含'x'和'y'字段的对象"
println "5. 数组（会尝试转换每个元素）"

println "\n=== 你的JSON是否包含这些字段？ ==="
if (element.isJsonObject()) {
    def obj = element.asJsonObject
    println "coordinates: ${obj.has('coordinates')}"
    println "geometry: ${obj.has('geometry')}"
    println "points: ${obj.has('points')}"
    println "polygon: ${obj.has('polygon')}"
    println "x和y: ${obj.has('x') && obj.has('y')}"
} else if (element.isJsonArray() && element.asJsonArray.size() > 0) {
    def first = element.asJsonArray.get(0)
    if (first.isJsonObject()) {
        def obj = first.asJsonObject
        println "第一个元素:"
        println "  coordinates: ${obj.has('coordinates')}"
        println "  geometry: ${obj.has('geometry')}"
        println "  points: ${obj.has('points')}"
        println "  polygon: ${obj.has('polygon')}"
        println "  x和y: ${obj.has('x') && obj.has('y')}"
    }
}

println "\n=== 建议 ==="
println "如果上面都是false，说明JSON格式不被支持"
println "需要根据实际JSON结构编写专门的转换逻辑"


