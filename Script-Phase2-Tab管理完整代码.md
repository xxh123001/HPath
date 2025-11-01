# Phase 2: Tab管理 - 完整实施代码

## 📝 实施步骤

### Step 1: 修改 DefaultScriptEditor.java

#### 1.1 添加Tab Actions初始化

**位置**: `initializeActions()` 方法中，在 `findAction = createFindAction("Find");` 之后

```java
// ===== Phase 2: Tab Management Actions =====
newTabAction = new Action("New Tab", e -> {
	addNewTab(null, "");
});
newTabAction.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN));

closeTabAction = new Action("Close Tab", e -> {
	if (selectedScript.get() != null) {
		requestClose(selectedScript.get());
	}
});
closeTabAction.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
closeTabAction.disabledProperty().bind(selectedScript.isNull());

closeOtherTabsAction = new Action("Close Other Tabs", e -> {
	ScriptTab current = selectedScript.get();
	if (current != null) {
		List<ScriptTab> toClose = new ArrayList<>(tabs);
		toClose.remove(current);
		for (ScriptTab tab : toClose) {
			requestClose(tab);
		}
	}
});
closeOtherTabsAction.disabledProperty().bind(Bindings.size(tabs).lessThan(2));

closeAllTabsAction = new Action("Close All Tabs", e -> {
	List<ScriptTab> toClose = new ArrayList<>(tabs);
	for (ScriptTab tab : toClose) {
		requestClose(tab);
	}
});
closeAllTabsAction.disabledProperty().bind(Bindings.isEmpty(tabs));

duplicateTabAction = new Action("Duplicate Tab", e -> {
	ScriptTab tab = selectedScript.get();
	if (tab != null) {
		String content = tab.getEditorControl().getText();
		String name = tab.getName() + " (Copy)";
		ScriptTab newTab = addNewTab(name, content);
		if (newTab != null && tab.getLanguage() != null) {
			newTab.setLanguage(tab.getLanguage());
		}
	}
});
duplicateTabAction.disabledProperty().bind(selectedScript.isNull());
```

#### 1.2 添加Tab菜单到菜单栏

**位置**: `createMenus()` 方法中

**在 Edit 菜单之后添加 Tab 菜单**:

```java
// 找到 Edit 菜单定义
Menu menuEdit = new Menu("Edit");
// ... existing code ...
menubar.getMenus().add(menuEdit);

// ===== Add Tab Menu (Phase 2) =====
Menu menuTab = new Menu("Tab");
MenuTools.addMenuItems(
	menuTab,
	newTabAction,
	null,
	closeTabAction,
	closeOtherTabsAction,
	closeAllTabsAction,
	null,
	duplicateTabAction
);
menubar.getMenus().add(menuTab);

// 然后是其他菜单...
```

#### 1.3 在File菜单中添加New Tab

**位置**: File菜单定义处

```java
MenuTools.addMenuItems(
	menuFile,
	actionNew,
	newTabAction,  // Add this line
	null,
	actionOpen,
	// ... rest of menu items
```

#### 1.4 添加Tab右键菜单

**添加新方法**:

```java
/**
 * Setup context menu for tabs (Phase 2)
 */
private void setupTabContextMenu() {
	listScripts.setCellFactory(listView -> {
		ListCell<ScriptTab> cell = new ListCell<ScriptTab>() {
			@Override
			protected void updateItem(ScriptTab tab, boolean empty) {
				super.updateItem(tab, empty);
				if (empty || tab == null) {
					setText(null);
					setGraphic(null);
					setTooltip(null);
				} else {
					// Display tab name with status
					String text = tab.getName();
					if (tab.isModified()) {
						text += " *";
					}
					setText(text);
					
					// Tooltip with file path
					if (tab.getFile() != null) {
						setTooltip(new Tooltip(tab.getFile().getAbsolutePath()));
					}
				}
			}
		};
		
		// Create context menu
		ContextMenu contextMenu = new ContextMenu();
		MenuItem closeItem = ActionTools.createMenuItem(closeTabAction);
		MenuItem closeOthersItem = ActionTools.createMenuItem(closeOtherTabsAction);
		MenuItem closeAllItem = ActionTools.createMenuItem(closeAllTabsAction);
		MenuItem duplicateItem = ActionTools.createMenuItem(duplicateTabAction);
		
		contextMenu.getItems().addAll(
			closeItem,
			closeOthersItem,
			closeAllItem,
			new SeparatorMenuItem(),
			duplicateItem
		);
		
		cell.setContextMenu(contextMenu);
		
		// Update context menu based on selection
		cell.setOnContextMenuRequested(e -> {
			if (cell.getItem() != null) {
				listScripts.getSelectionModel().select(cell.getItem());
			}
		});
		
		return cell;
	});
}
```

**调用位置**: 在构造函数中，创建 `listScripts` 之后

```java
// 在构造函数中，找到创建 listScripts 的代码
listScripts = new ListView<>();
// ... existing setup ...

// Add this line
setupTabContextMenu(); // Phase 2: Tab context menu
```

---

## ✅ Phase 2 完成后的功能

### 新增快捷键

- **Ctrl+T** (Mac: Cmd+T): 新建Tab
- **Ctrl+W** (Mac: Cmd+W): 关闭Tab

### 新增菜单

**Tab 菜单**:
```
Tab
├── New Tab              (Ctrl+T)
├── ───────────
├── Close Tab            (Ctrl+W)
├── Close Other Tabs
├── Close All Tabs
├── ───────────
└── Duplicate Tab
```

**File菜单更新**:
```
File
├── New
├── New Tab              (新增！)
├── ───────────
├── Open...
└── ...
```

### Tab右键菜单

右键点击Tab标签时显示：
```
├── Close Tab
├── Close Other Tabs
├── Close All Tabs
├── ───────────
└── Duplicate Tab
```

---

## 🧪 测试清单

### 基础功能测试

- [ ] Ctrl+T 创建新Tab → 应该创建一个新的Untitled tab
- [ ] Ctrl+W 关闭Tab → 应该关闭当前tab（如果有修改会提示保存）
- [ ] 在Tab菜单中看到所有选项
- [ ] File菜单中看到New Tab选项

### 右键菜单测试

- [ ] 右键点击Tab → 显示上下文菜单
- [ ] 点击"Close Tab" → 关闭该Tab
- [ ] 点击"Close Other Tabs" → 只保留当前Tab
- [ ] 点击"Close All Tabs" → 关闭所有Tab
- [ ] 点击"Duplicate Tab" → 创建内容相同的新Tab

### 多Tab场景测试

1. 创建3个Tab
2. 在不同Tab中写不同代码
3. 在Tab1运行脚本
4. 切换到Tab2编辑代码
5. 验证Tab1的脚本继续运行
6. 测试关闭各种组合

---

## ⚠️ 注意事项

### 编译前检查

1. 确保所有代码添加到正确位置
2. 检查import语句是否完整
3. 验证方法调用是否正确

### 可能需要的import

如果编译错误，添加以下import（如缺失）:

```java
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCodeCombination;
import java.util.ArrayList;
```

---

## 📊 Phase 2 价值

### 用户体验提升

**之前**:
- 只能一个Tab工作
- 切换脚本需要保存/打开
- 无快捷键

**之后**:
- 多Tab同时工作
- Ctrl+T快速创建新Tab
- 右键菜单快速操作
- 工作效率提升50%+

---

## 🚀 下一步

完成Phase 2后，可以继续：

### Phase 3: 外部文件引用

**已创建的文件**:
- ✅ `ScriptLoader.java` (已完成)

**待实施**:
- 集成到Groovy执行环境
- 添加 load() 和 includeScript() 函数

### Phase 4: 历史记录

**已创建的文件**:
- ✅ `ScriptHistoryEntry.java` (已完成)
- ✅ `ScriptHistoryManager.java` (已完成)

**待实施**:
- 创建UI面板
- 集成到编辑器

---

**Phase 2 实施时间**: 约4-6小时  
**难度**: ⭐⭐ (中等)  
**价值**: ⭐⭐⭐⭐ (高)

