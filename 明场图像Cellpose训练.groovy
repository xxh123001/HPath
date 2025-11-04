/**
 * 明场/H&E图像的Cellpose训练
 * 
 * 使用前提：
 * 1. 已经运行"批量设置对象为训练类别.groovy"设置了Training和Validation对象
 * 2. 多个图像中都有Training和Validation标注
 */

import qupath.ext.biop.cellpose.Cellpose2D

// 检查Project
def project = getProject()
if (project == null) {
    println "❌ 请先打开一个Project"
    return
}

// 统计Training和Validation对象
int trainingCount = 0
int validationCount = 0

for (entry in project.getImageList()) {
    try {
        def imageData = entry.readImageData()
        def objects = imageData.getHierarchy().getAnnotationObjects()
        
        objects.each { obj ->
            def className = obj.getPathClass()?.getName()
            if (className == "Training") trainingCount++
            if (className == "Validation") validationCount++
        }
        
        imageData.getServer().close()
    } catch (Exception e) {
        println "⚠️ 无法读取 ${entry.getImageName()}: ${e.message}"
    }
}

println "=" * 60
println "训练数据统计："
println "  Training对象: ${trainingCount}"
println "  Validation对象: ${validationCount}"
println "=" * 60

if (trainingCount == 0) {
    println "❌ 没有Training对象！"
    println "💡 请先运行 '批量设置对象为训练类别.groovy'"
    return
}

if (validationCount == 0) {
    println "⚠️ 没有Validation对象，建议添加一些用于验证"
}

// 构建Cellpose训练器 - 明场图像版本
def cellpose = Cellpose2D.builder("cyto3")    // 从预训练模型开始
        .channels("Red")                       // ⭐ 明场图像用Red通道
        .epochs(100)                          // 训练轮数，可以减少以加快速度
        .learningRate(0.2)                    // 学习率
        .batchSize(8)                         // 批次大小
        .minTrainMasks(5)                     // 最小训练mask数量
        .build()

println "开始训练Cellpose模型..."
println "这可能需要几分钟到几小时，取决于数据量和电脑性能"

try {
    // 训练模型
    def resultModel = cellpose.train()
    
    println "=" * 60
    println "✅ 训练完成！"
    println "模型保存位置:"
    println resultModel.getAbsolutePath().replace('\\', '/')
    println "=" * 60
    
    // 显示训练结果
    def results = cellpose.getTrainingResults()
    results.show("Training Results")
    
    // 显示QC结果
    def qcResults = cellpose.getQCResults()
    qcResults.show("QC Results")
    
    // 显示训练图表
    cellpose.showTrainingGraph()
    
    println "✅ 训练脚本完成"
    println "💡 现在可以在检测脚本中使用这个模型"
    
} catch (Exception e) {
    println "❌ 训练失败: ${e.message}"
    e.printStackTrace()
}














