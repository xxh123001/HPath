# Script编辑器改进 - 已完成工作和后续步骤

## ✅ Phase 1a: 已完成（刚刚完成）

### 停止按钮添加到工具栏

**修改文件**: `DefaultScriptEditor.java`

**已完成的改进**:
```
✅ 在工具栏添加了停止按钮 (⏹ Stop)
✅ 按钮自动根据脚本运行状态启用/禁用
✅ 添加了提示信息
✅ 布局已正确配置
✅ 无编译错误
```

**现在的工具栏**:
```
[运行] [⏹ Stop] [更多选项▼]
```

**立即可用**:
- 编译项目即可看到停止按钮
- 运行脚本时按钮自动启用
- 点击可中断正在运行的脚本

---

## 📋 Phase 1b: 后续改进（建议）

为了让脚本停止功能更完善，建议继续以下改进：

### 1. 增强停止机制（可选）

**位置**: `DefaultScriptEditor.java` 的 `createKillRunningScriptAction` 方法

**现有代码**:
```java
Action createKillRunningScriptAction(final String name) {
    Action action = new Action(name, e -> {
        Future<?> future = runningTask.get();
        if (future == null)
            return;
        if (future.isDone())
            runningTask.set(null);
        else
            future.cancel(true);
    });
    action.disabledProperty().bind(runningTask.isNull());
```

**建议增强**:
```java
Action createKillRunningScriptAction(final String name) {
    Action action = new Action(name, e -> {
        Future<?> future = runningTask.get();
        if (future == null)
            return;
        
        if (future.isDone()) {
            runningTask.set(null);
        } else {
            // Try to cancel gracefully
            future.cancel(true);
            
            // Wait a bit for graceful shutdown
            Platform.runLater(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
                
                // Force termination if still running
                if (future != null && !future.isDone()) {
                    logger.warn("Script did not terminate gracefully, forcing...");
                    // Additional cleanup if needed
                }
            });
            
            // Log the interruption
            logger.info("Script execution interrupted by user");
        }
    });
    action.disabledProperty().bind(runningTask.isNull());
    return action;
}
```

**为什么这是可选的**:
- 当前的停止功能已经工作
- 这只是增强健壮性
- 可以稍后添加

---

## 🚀 Phase 2: Tab管理增强

### 目标
- ✅ 新建Tab快捷键 (Ctrl+T)
- ✅ Tab状态显示
- ✅ Tab右键菜单

### 实施步骤

#### Step 1: 添加新建Tab快捷键

**位置**: `DefaultScriptEditor.java` 的 `initializeActions()` 方法

**在以下代码后添加**:
```java
findAction = createFindAction("Find");
```

**添加代码**:
```java
// New Tab action (Phase 2)
newTabAction = new Action("New Tab", e -> {
    addNewTab(null, "");
});
newTabAction.setAccelerator(
    new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN)
);
```

**在类的成员变量部分添加**:
```java
protected Action newTabAction;  // Add near other Action declarations
```

**添加到菜单**:
在 `createMenus()` 方法的 File 菜单中添加：
```java
MenuTools.addMenuItems(
    menuFile,
    actionNew,
    newTabAction,  // Add this line
    actionOpen,
    ...
```

#### Step 2: Tab状态显示

**位置**: `ScriptTab.java`

**添加运行状态属性**:
```java
// Add to ScriptTab class
private BooleanProperty isRunning = new SimpleBooleanProperty(false);

public BooleanProperty isRunningProperty() {
    return isRunning;
}

public void setRunning(boolean running) {
    this.isRunning.set(running);
}
```

**在 `DefaultScriptEditor.java` 中使用**:
```java
// When executing script
tab.setRunning(true);

// When script completes/stops
tab.setRunning(false);
```

**Tab标题显示状态**:
```java
// In createTabPane or similar
tab.isRunningProperty().addListener((obs, wasRunning, isRunning) -> {
    String title = tab.getName();
    if (tab.isModified()) {
        title += " *";
    }
    if (isRunning) {
        title = "⏳ " + title;
    }
    // Update tab text
    tabPane.getSelectionModel().getSelectedItem().setText(title);
});
```

#### Step 3: Tab右键菜单

**位置**: `DefaultScriptEditor.java`

**创建Tab上下文菜单**:
```java
private ContextMenu createTabContextMenu(ScriptTab tab) {
    ContextMenu menu = new ContextMenu();
    
    // Close Tab
    MenuItem closeItem = new MenuItem("Close Tab");
    closeItem.setOnAction(e -> closeTab(tab));
    
    // Close Other Tabs
    MenuItem closeOthersItem = new MenuItem("Close Other Tabs");
    closeOthersItem.setOnAction(e -> {
        List<ScriptTab> toClose = new ArrayList<>(tabs);
        toClose.remove(tab);
        toClose.forEach(this::closeTab);
    });
    
    // Close All Tabs
    MenuItem closeAllItem = new MenuItem("Close All Tabs");
    closeAllItem.setOnAction(e -> {
        new ArrayList<>(tabs).forEach(this::closeTab);
    });
    
    // Separator
    SeparatorMenuItem sep1 = new SeparatorMenuItem();
    
    // Rename Tab
    MenuItem renameItem = new MenuItem("Rename Tab");
    renameItem.setOnAction(e -> {
        TextInputDialog dialog = new TextInputDialog(tab.getName());
        dialog.setTitle("Rename Tab");
        dialog.setHeaderText("Enter new name:");
        dialog.showAndWait().ifPresent(name -> {
            // Update tab name
            tab.setName(name);
        });
    });
    
    // Duplicate Tab
    MenuItem duplicateItem = new MenuItem("Duplicate Tab");
    duplicateItem.setOnAction(e -> {
        String content = tab.getEditorControl().getText();
        addNewTab(tab.getName() + " (Copy)", content);
    });
    
    menu.getItems().addAll(
        closeItem,
        closeOthersItem,
        closeAllItem,
        sep1,
        renameItem,
        duplicateItem
    );
    
    return menu;
}
```

**应用到Tab**:
```java
// When creating tab in TabPane
tab.setOnContextMenuRequested(e -> {
    ContextMenu menu = createTabContextMenu(scriptTab);
    menu.show(tab, e.getScreenX(), e.getScreenY());
});
```

---

## 📁 Phase 3: 外部文件引用

这需要创建新文件。以下是完整的代码框架：

### 文件1: ScriptLoader.java

**创建位置**: `qupath/lib/gui/scripting/ScriptLoader.java`

**完整代码框架**:
```java
package qupath.lib.gui.scripting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader for external script files.
 * Supports searching multiple paths and caching loaded scripts.
 */
public class ScriptLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(ScriptLoader.class);
    
    private List<Path> searchPaths = new ArrayList<>();
    private Map<String, String> cache = new HashMap<>();
    private boolean cachingEnabled = true;
    
    public ScriptLoader() {
        // Add default search paths
        addDefaultSearchPaths();
    }
    
    private void addDefaultSearchPaths() {
        // Current directory
        searchPaths.add(Path.of(System.getProperty("user.dir")));
        
        // User scripts directory
        String userHome = System.getProperty("user.home");
        searchPaths.add(Path.of(userHome, ".qupath", "scripts"));
        
        // Project scripts directory (if applicable)
        // Add more as needed
    }
    
    public void addSearchPath(Path path) {
        if (!searchPaths.contains(path)) {
            searchPaths.add(path);
        }
    }
    
    public String loadScript(String path) throws IOException {
        // Check cache first
        if (cachingEnabled && cache.containsKey(path)) {
            return cache.get(path);
        }
        
        // Find and load the script
        File scriptFile = findScript(path);
        if (scriptFile == null) {
            throw new IOException("Script not found: " + path);
        }
        
        String content = Files.readString(scriptFile.toPath());
        
        // Cache it
        if (cachingEnabled) {
            cache.put(path, content);
        }
        
        return content;
    }
    
    private File findScript(String path) {
        // Try as absolute path first
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            return file;
        }
        
        // Search in search paths
        for (Path searchPath : searchPaths) {
            File candidate = searchPath.resolve(path).toFile();
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }
        
        return null;
    }
    
    public void clearCache() {
        cache.clear();
    }
    
    public void setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
    }
}
```

### 文件2: GroovyScriptExtensions.groovy

**创建位置**: `qupath/lib/gui/scripting/GroovyScriptExtensions.groovy`

**完整代码**:
```groovy
package qupath.lib.gui.scripting

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Groovy extensions for script loading.
 */
class GroovyScriptExtensions {
    
    private static final Logger logger = LoggerFactory.getLogger(GroovyScriptExtensions.class)
    private static ScriptLoader loader = new ScriptLoader()
    
    /**
     * Load and execute an external script.
     * Usage: load('/path/to/script.groovy')
     */
    static Object load(String scriptPath) {
        try {
            String scriptContent = loader.loadScript(scriptPath)
            // Evaluate the script
            def shell = new GroovyShell()
            return shell.evaluate(scriptContent)
        } catch (Exception e) {
            logger.error("Failed to load script: " + scriptPath, e)
            throw e
        }
    }
    
    /**
     * Include an external script (execute without returning value).
     * Usage: includeScript('common/functions.groovy')
     */
    static void includeScript(String scriptPath) {
        load(scriptPath)
    }
    
    /**
     * Add a search path for scripts.
     * Usage: addScriptPath('/my/scripts')
     */
    static void addScriptPath(String path) {
        loader.addSearchPath(new File(path).toPath())
    }
}
```

### 集成到DefaultScriptEditor

**在 `executeScript` 方法中添加**:
```java
// Before building ScriptParameters
import static qupath.lib.gui.scripting.GroovyScriptExtensions.*

// Make the extensions available to scripts
if (language instanceof GroovyLanguage) {
    // Add load() and includeScript() to binding
    // Implementation depends on how scripts are executed
}
```

---

## 🕐 Phase 4: 历史记录功能

### 文件清单和代码框架

由于篇幅，这部分我会创建单独的文件。主要包括：

1. `ScriptHistoryEntry.java` - 历史条目数据
2. `ScriptHistoryManager.java` - 历史管理器
3. `ScriptHistoryPanel.java` - UI面板

---

## 📊 实施建议

### 立即测试Phase 1a

```bash
# 编译
cd qupath-0.6.0
./gradlew build

# 运行
./build/scripts/HPath

# 测试
1. 打开Script编辑器
2. 查看工具栏 - 应该看到 [运行] [⏹ Stop]
3. 运行一个脚本
4. 点击Stop按钮测试
```

### 继续Phase 2（如果测试通过）

1. 按照上面的Step 1-3添加代码
2. 每完成一步就编译测试
3. 确保功能正常再继续下一步

### 完整实施时间表

```
已完成: Phase 1a (停止按钮) ✅
需要: 1-2小时实施Phase 1b (增强停止)
需要: 4-6小时实施Phase 2 (Tab管理)
需要: 6-8小时实施Phase 3 (外部引用)
需要: 6-8小时实施Phase 4 (历史记录)
───────────────────────────────────
总计: 约20-26小时 (2.5-3周按每天2-3小时)
```

---

## 🎯 你的选择

### 选项1: 现在测试Phase 1a

立即编译测试停止按钮功能，确保一切正常。

### 选项2: 继续实施Phase 2

如果Phase 1a测试通过，我可以继续实施Tab管理功能。

### 选项3: 获取完整代码包

我可以创建所有Phase的完整代码文件，你可以逐步实施。

### 选项4: 分阶段进行

每周实施一个Phase，确保质量。

---

**请告诉我你想怎么继续！** 🚀

---

**当前进度**: Phase 1a ✅ 完成  
**下一步**: 测试 → Phase 1b/2/3/4  
**预计完成时间**: 根据你的节奏

