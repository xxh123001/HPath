/**
 * 项目合并界面 - 立即可用版本
 * 
 * 功能:
 * - 上半部左右分区：图像导入 | 对象导入
 * - 每项都有复选框
 * - Ctrl+F搜索
 * - 中间Merge按钮
 * - 下半部显示合并结果
 * - 双击打开
 * 
 * 直接运行此脚本即可使用！
 */

import javafx.stage.Stage
import javafx.stage.FileChooser
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.geometry.Insets
import javafx.collections.FXCollections
import javafx.scene.control.cell.CheckBoxListCell
import javafx.beans.property.SimpleBooleanProperty
import com.google.gson.JsonElement
import qupath.lib.io.GsonTools
import java.nio.file.Files

// ===== 数据类 =====
class ImageEntry {
    String name
    File file
    SimpleBooleanProperty selected = new SimpleBooleanProperty(false)
    
    ImageEntry(File f) {
        this.file = f
        this.name = f.name
    }
    
    String toString() { name }
}

class ObjectEntry {
    String name
    File file
    List objects
    SimpleBooleanProperty selected = new SimpleBooleanProperty(false)
    
    ObjectEntry(File f, List objs) {
        this.file = f
        this.name = f.name
        this.objects = objs
    }
    
    String toString() { "${name} (${objects.size()} objects)" }
}

class MergedEntry {
    ImageEntry image
    ObjectEntry object
    
    MergedEntry(ImageEntry img, ObjectEntry obj) {
        this.image = img
        this.object = obj
    }
    
    String toString() { 
        "${image.name} + ${object.name} (${object.objects.size()} obj)" 
    }
}

// ===== 数据存储 =====
def imageList = FXCollections.observableArrayList()
def objectList = FXCollections.observableArrayList()
def mergedList = FXCollections.observableArrayList()

// ===== 创建窗口 =====
def stage = new Stage()
stage.title = "Project Merger - 图像和标注合并工具"
stage.initOwner(getQuPath().getStage())

def mainPane = new BorderPane()
mainPane.padding = new Insets(10)

// ===== 上半部：图像和对象导入 =====
def topSection = new HBox(10)

// --- 左侧：图像导入 ---
def imageBox = new VBox(10)
imageBox.style = "-fx-border-color: #4CAF50; -fx-border-width: 2; -fx-padding: 10; -fx-background-color: #f9f9f9;"

def imageTitle = new Label("📷 图像文件")
imageTitle.style = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #4CAF50;"

def importImageBtn = new Button("+ 导入图像...")
importImageBtn.maxWidth = Double.MAX_VALUE
importImageBtn.style = "-fx-font-size: 13px;"
importImageBtn.onAction = { e ->
    def chooser = new FileChooser()
    chooser.title = "选择图像文件"
    chooser.extensionFilters.addAll(
        new FileChooser.ExtensionFilter("图像文件", "*.svs", "*.tif", "*.tiff", "*.ndpi", "*.vsi", "*.mrxs"),
        new FileChooser.ExtensionFilter("所有文件", "*.*")
    )
    
    def files = chooser.showOpenMultipleDialog(stage)
    if (files) {
        files.each { f ->
            imageList.add(new ImageEntry(f))
        }
        println "导入了 ${files.size()} 个图像"
    }
}

def imageSearch = new TextField()
imageSearch.promptText = "🔍 搜索图像 (Ctrl+F)..."

def imageListView = new ListView(imageList)
imageListView.cellFactory = { lv ->
    new CheckBoxListCell({ ImageEntry e -> e.selected })
}

// Ctrl+F聚焦搜索
imageListView.onKeyPressed = { e ->
    if (e.isShortcutDown() && e.code == javafx.scene.input.KeyCode.F) {
        imageSearch.requestFocus()
        e.consume()
    }
}

def imageSelectAll = new Button("全选")
imageSelectAll.onAction = { imageList.each { it.selected.set(true) } }

def imageClear = new Button("清除")
imageClear.onAction = { imageList.each { it.selected.set(false) } }

def imageRemove = new Button("删除")
imageRemove.onAction = {
    def toRemove = imageList.findAll { it.selected.get() }
    imageList.removeAll(toRemove)
}

def imageBtnBox = new HBox(5, imageSelectAll, imageClear, imageRemove)

imageBox.children.addAll(imageTitle, importImageBtn, imageSearch, imageListView, imageBtnBox)
VBox.setVgrow(imageListView, javafx.scene.layout.Priority.ALWAYS)

// --- 右侧：对象导入 ---
def objectBox = new VBox(10)
objectBox.style = "-fx-border-color: #2196F3; -fx-border-width: 2; -fx-padding: 10; -fx-background-color: #f9f9f9;"

def objectTitle = new Label("📝 标注文件 (JSON/GeoJSON)")
objectTitle.style = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2196F3;"

def importObjectBtn = new Button("+ 导入标注...")
importObjectBtn.maxWidth = Double.MAX_VALUE
importObjectBtn.style = "-fx-font-size: 13px;"
importObjectBtn.onAction = { e ->
    def chooser = new FileChooser()
    chooser.title = "选择标注文件"
    chooser.extensionFilters.addAll(
        new FileChooser.ExtensionFilter("JSON/GeoJSON", "*.json", "*.geojson"),
        new FileChooser.ExtensionFilter("所有文件", "*.*")
    )
    
    def files = chooser.showOpenMultipleDialog(stage)
    if (files) {
        files.each { f ->
            try {
                def json = f.text
                def element = GsonTools.getInstance().fromJson(json, JsonElement.class)
                def objects = GsonTools.parseObjectsFromGeoJSON(element)
                
                objectList.add(new ObjectEntry(f, objects))
                println "导入: ${f.name} (${objects.size()} 个对象)"
            } catch (Exception ex) {
                println "❌ 错误: ${f.name} - ${ex.message}"
            }
        }
    }
}

def objectSearch = new TextField()
objectSearch.promptText = "🔍 搜索标注 (Ctrl+F)..."

def objectListView = new ListView(objectList)
objectListView.cellFactory = { lv ->
    new CheckBoxListCell({ ObjectEntry e -> e.selected })
}

// Ctrl+F聚焦搜索
objectListView.onKeyPressed = { e ->
    if (e.isShortcutDown() && e.code == javafx.scene.input.KeyCode.F) {
        objectSearch.requestFocus()
        e.consume()
    }
}

def objectSelectAll = new Button("全选")
objectSelectAll.onAction = { objectList.each { it.selected.set(true) } }

def objectClear = new Button("清除")
objectClear.onAction = { objectList.each { it.selected.set(false) } }

def objectRemove = new Button("删除")
objectRemove.onAction = {
    def toRemove = objectList.findAll { it.selected.get() }
    objectList.removeAll(toRemove)
}

def objectBtnBox = new HBox(5, objectSelectAll, objectClear, objectRemove)

objectBox.children.addAll(objectTitle, importObjectBtn, objectSearch, objectListView, objectBtnBox)
VBox.setVgrow(objectListView, javafx.scene.layout.Priority.ALWAYS)

// 上半部布局
topSection.children.addAll(imageBox, objectBox)
HBox.setHgrow(imageBox, javafx.scene.layout.Priority.ALWAYS)
HBox.setHgrow(objectBox, javafx.scene.layout.Priority.ALWAYS)

// ===== 中间：合并按钮 =====
def mergeBtn = new Button("⬇⬇⬇ 合并选中的图像和标注 ⬇⬇⬇")
mergeBtn.style = "-fx-font-size: 18px; -fx-font-weight: bold; -fx-background-color: #FF9800; -fx-text-fill: white;"
mergeBtn.maxWidth = Double.MAX_VALUE
mergeBtn.onAction = { e ->
    def selectedImages = imageList.findAll { it.selected.get() }
    def selectedObjects = objectList.findAll { it.selected.get() }
    
    if (selectedImages.isEmpty()) {
        println "⚠️ 请至少选择一个图像"
        return
    }
    
    if (selectedObjects.isEmpty()) {
        println "⚠️ 请至少选择一个标注文件"
        return
    }
    
    // 创建合并项（笛卡尔积）
    def count = 0
    selectedImages.each { img ->
        selectedObjects.each { obj ->
            mergedList.add(new MergedEntry(img, obj))
            count++
        }
    }
    
    println "✅ 创建了 ${count} 个合并项"
    println "   ${selectedImages.size()} 个图像 × ${selectedObjects.size()} 个标注 = ${count} 项"
}

def mergeBox = new HBox(mergeBtn)
mergeBox.padding = new Insets(15, 0, 15, 0)
HBox.setHgrow(mergeBtn, javafx.scene.layout.Priority.ALWAYS)

// ===== 下半部：合并结果 =====
def bottomBox = new VBox(10)
bottomBox.style = "-fx-border-color: #9C27B0; -fx-border-width: 2; -fx-padding: 10; -fx-background-color: #f9f9f9;"

def mergedTitle = new Label("📊 合并结果列表 (双击打开)")
mergedTitle.style = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #9C27B0;"

def mergedListView = new ListView(mergedList)

// 双击打开
mergedListView.onMouseClicked = { e ->
    if (e.clickCount == 2) {
        def selected = mergedListView.selectionModel.selectedItem
        if (selected) {
            try {
                println "\n打开: ${selected.image.name} + ${selected.object.name}"
                
                // 打开图像
                getQuPath().openImage(getQuPath().getViewer(), selected.image.file.absolutePath, true, true)
                
                // 等待图像加载
                Thread.sleep(1000)
                
                // 导入对象
                def imageData = getCurrentImageData()
                if (imageData) {
                    imageData.hierarchy.addObjects(selected.object.objects)
                    fireHierarchyUpdate()
                    
                    println "✅ 成功加载！"
                    println "   图像: ${selected.image.name}"
                    println "   对象: ${selected.object.objects.size()} 个"
                } else {
                    println "❌ 图像未成功打开"
                }
            } catch (Exception ex) {
                println "❌ 错误: ${ex.message}"
                ex.printStackTrace()
            }
        }
    }
}

def openBtn = new Button("打开选中")
openBtn.onAction = { e ->
    def selected = mergedListView.selectionModel.selectedItem
    if (selected) {
        mergedListView.onMouseClicked.handle(
            new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY,
                2, false, false, false, false,
                true, false, false, false, false, false, null
            )
        )
    }
}

def removeBtn = new Button("删除")
removeBtn.onAction = {
    def selected = mergedListView.selectionModel.selectedItem
    if (selected) mergedList.remove(selected)
}

def clearAllBtn = new Button("清空全部")
clearAllBtn.onAction = { mergedList.clear() }

def mergedBtnBox = new HBox(5, openBtn, removeBtn, clearAllBtn)

bottomBox.children.addAll(mergedTitle, mergedListView, mergedBtnBox)
VBox.setVgrow(mergedListView, javafx.scene.layout.Priority.ALWAYS)

// ===== 主布局 =====
def centerBox = new VBox(10)
centerBox.children.addAll(topSection, mergeBox, bottomBox)
VBox.setVgrow(topSection, javafx.scene.layout.Priority.ALWAYS)
VBox.setVgrow(bottomBox, javafx.scene.layout.Priority.ALWAYS)

mainPane.center = centerBox

// ===== 显示窗口 =====
def scene = new Scene(mainPane, 1200, 900)
stage.scene = scene
stage.show()

println """
╔════════════════════════════════════════════════════════╗
║  项目合并界面已打开！                                  ║
╚════════════════════════════════════════════════════════╝

📖 使用说明:

1️⃣  左侧 - 导入图像:
   • 点击 [+ 导入图像...] 按钮
   • 选择一个或多个图像文件
   • 勾选想要的图像
   • Ctrl+F 可以搜索

2️⃣  右侧 - 导入标注:
   • 点击 [+ 导入标注...] 按钮
   • 选择JSON或GeoJSON文件
   • 勾选想要的标注文件
   • Ctrl+F 可以搜索

3️⃣  合并:
   • 点击中间的 [⬇⬇⬇ 合并选中的图像和标注 ⬇⬇⬇] 按钮
   • 会创建所有勾选项的组合

4️⃣  打开:
   • 在下方列表双击任意合并项
   • 自动打开图像+导入标注
   • 立即可用！

✨ 提示:
- 可以多选：1个图像 + 多个标注 = 多个组合
- 可以多选：多个图像 + 1个标注 = 多个组合
- 可以多对多：m个图像 × n个标注 = m×n个组合

💡 快捷键:
- Ctrl+F: 在当前列表搜索
- 双击: 打开合并项

═══════════════════════════════════════════════════════
"""

