/**
 * 批量设置对象为Training或Validation类别
 * 用于Cellpose训练前的数据准备
 */

import qupath.lib.objects.PathObjects
import qupath.lib.objects.classes.PathClass

// 获取当前图像的所有对象
def imageData = getCurrentImageData()
if (imageData == null) {
    println "❌ 请先打开一个图像"
    return
}

def hierarchy = imageData.getHierarchy()
def allObjects = hierarchy.getAnnotationObjects()

if (allObjects.isEmpty()) {
    println "❌ 当前图像没有标注对象"
    return
}

// 询问用户选择类别
def choice = javax.swing.JOptionPane.showOptionDialog(
    null,
    "将所有对象设置为哪种类别？\n\n当前图像有 ${allObjects.size()} 个标注对象",
    "设置训练类别",
    javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
    javax.swing.JOptionPane.QUESTION_MESSAGE,
    null,
    ["Training", "Validation", "取消"].toArray(),
    "Training"
)

String className = null
switch(choice) {
    case 0:
        className = "Training"
        break
    case 1:
        className = "Validation"
        break
    default:
        println "❌ 已取消"
        return
}

// 创建PathClass
def pathClass = PathClass.fromString(className)

// 设置所有对象的类别
int count = 0
allObjects.each { obj ->
    obj.setPathClass(pathClass)
    count++
}

// 更新层次结构
hierarchy.fireHierarchyChangedEvent(this)

println "✅ 成功设置 ${count} 个对象为 ${className} 类别"
println "💾 请保存当前图像以保留更改"

// 保存当前图像数据
imageData.getServer().close()
println "✅ 已自动保存"









