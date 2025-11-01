/**
 * 批量设置当前Project中所有图像的对象为Training或Validation类别
 * 用于Cellpose训练前的数据准备
 */

import qupath.lib.objects.classes.PathClass

def project = getProject()
if (project == null) {
    println "❌ 请先打开一个Project"
    return
}

// 询问用户选择类别和比例
def result = javax.swing.JOptionPane.showInputDialog(
    null,
    "请输入Training/Validation的比例 (例如: 80 表示80%用于训练，20%用于验证)",
    "设置训练数据比例",
    javax.swing.JOptionPane.QUESTION_MESSAGE,
    null,
    null,
    "80"
)

if (result == null) {
    println "❌ 已取消"
    return
}

double trainingRatio = Double.parseDouble(result.toString()) / 100.0
println "训练比例: ${trainingRatio * 100}%"
println "验证比例: ${(1 - trainingRatio) * 100}%"

def trainingClass = PathClass.fromString("Training")
def validationClass = PathClass.fromString("Validation")

int totalImages = 0
int totalObjects = 0
int trainingObjects = 0
int validationObjects = 0

// 遍历所有图像
for (entry in project.getImageList()) {
    def imageData = entry.readImageData()
    def hierarchy = imageData.getHierarchy()
    def allObjects = hierarchy.getAnnotationObjects()
    
    if (allObjects.isEmpty()) {
        println "⚠️ ${entry.getImageName()} - 没有标注对象，跳过"
        continue
    }
    
    totalImages++
    
    // 随机分配Training/Validation
    allObjects.each { obj ->
        totalObjects++
        if (Math.random() < trainingRatio) {
            obj.setPathClass(trainingClass)
            trainingObjects++
        } else {
            obj.setPathClass(validationClass)
            validationObjects++
        }
    }
    
    // 保存更改
    entry.saveImageData(imageData)
    imageData.getServer().close()
    
    println "✅ ${entry.getImageName()} - 处理了 ${allObjects.size()} 个对象"
}

println "=" * 60
println "批量设置完成！"
println "  处理图像: ${totalImages}"
println "  总对象数: ${totalObjects}"
println "  Training对象: ${trainingObjects}"
println "  Validation对象: ${validationObjects}"
println "=" * 60
println "✅ 现在可以运行Cellpose训练脚本了"









