/**
 * 自动设置Cellpose Python路径
 * 在HPath的Script Editor中运行这个脚本
 */

// Python路径
def pythonPath = '/usr/local/bin/python3'

// 验证Python是否存在
def pythonFile = new File(pythonPath)
if (!pythonFile.exists()) {
    println "❌ Python文件不存在: ${pythonPath}"
    println "请检查路径是否正确"
    return
}

println "✅ 找到Python: ${pythonPath}"

// 尝试设置路径（方法1：通过系统属性）
System.setProperty("qupath.cellpose.python", pythonPath)
println "✅ 已设置系统属性"

// 尝试设置路径（方法2：通过环境变量）
def env = System.getenv()
println "当前环境变量数量: ${env.size()}"

println ""
println "=" * 60
println "⚠️ 重要提示："
println "由于脚本的限制，最可靠的方法是："
println "1. Edit → Preferences"
println "2. 搜索 'cellpose' 或 'python'"
println "3. 找到 'Cellpose Python executable'"
println "4. 填入路径: ${pythonPath}"
println "5. 点击 Apply"
println "=" * 60
println ""
println "💡 或者尝试重启HPath后再运行检测脚本"














