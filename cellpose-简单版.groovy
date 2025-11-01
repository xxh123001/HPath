/**
 * CellPose 细胞检测 - 简化版
 * 适合快速使用
 */

import qupath.ext.biop.cellpose.Cellpose2D

// 选择要检测的区域
selectAnnotations()

// 配置CellPose
def cellpose = Cellpose2D.builder("cyto2")  // 细胞+核模型
    .pixelSize(0.5)              // 像素大小
    .diameter(30)                // 细胞直径
    .cellExpansion(2.0)          // 细胞扩展
    .measureIntensity()          // 测量强度（包括光密度）
    .build()

// 运行检测
cellpose.detectObjects(getCurrentImageData(), getSelectedObjects())

println "✅ 检测完成！现在有 ${getDetectionObjects().size()} 个细胞"
println "可以使用分类器了：Object filter选Cells，Measurement选DAB OD mean"

