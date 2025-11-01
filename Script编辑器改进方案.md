# Script编辑器功能改进方案

## 📋 需求分析

根据你的反馈，当前Script编辑器存在以下问题：

### 当前问题

1. **❌ 运行后不能停止**
   - 长时间运行的脚本无法中断
   - 死循环或耗时任务无法终止
   - 缺少停止按钮

2. **❌ 无法新建Tab跑代码**
   - 同时处理多个脚本不便
   - 需要频繁切换文件

3. **❌ 不能引用外部代码文件**
   - 无法import其他脚本
   - 代码重用困难
   - 缺少模块化支持

4. **❌ 无历史记录**
   - 运行过的脚本无法查看
   - 无法快速重新运行
   - 调试困难

---

## ✨ 改进方案

### 1. 脚本停止功能 ⭐⭐⭐

#### 功能描述
添加脚本执行控制功能，允许用户中断正在运行的脚本。

#### 实现方案

**A. 添加停止按钮**
```
工具栏布局:
[运行] [运行选中] [停止] [新建Tab] ...
```

**B. 后台线程管理**
```java
// 使用Future跟踪脚本执行
private Future<?> currentScriptTask;

// 停止按钮动作
stopButton.setOnAction(e -> {
    if (currentScriptTask != null && !currentScriptTask.isDone()) {
        currentScriptTask.cancel(true);
    }
});
```

**C. 脚本状态指示**
```
状态栏显示:
[运行中...] ⏸ 或 [就绪] ✓
```

#### 技术细节

1. **线程中断机制**
   - 使用`Thread.interrupt()`
   - 检查`Thread.isInterrupted()`
   - 超时强制终止

2. **UI状态管理**
   - 运行时禁用运行按钮
   - 启用停止按钮
   - 显示运行状态

3. **资源清理**
   - 确保脚本停止后释放资源
   - 清理临时文件
   - 重置变量状态

---

### 2. 完善多Tab支持 ⭐⭐⭐

#### 功能描述
增强多标签页管理，允许同时打开和运行多个脚本。

#### 实现方案

**A. 新建Tab功能**
```
菜单: File → New Tab (Ctrl+T)
工具栏: [新建Tab] 按钮
右键: Tab上下文菜单
```

**B. Tab管理**
```
功能:
- 新建空白Tab
- 复制当前Tab
- 重命名Tab
- 关闭Tab (Ctrl+W)
- 关闭其他Tab
- 关闭所有Tab
- Tab间切换 (Ctrl+Tab)
```

**C. Tab状态**
```
每个Tab独立维护:
- 运行状态 (运行中/已停止)
- 修改状态 (已修改*)
- 文件关联
- 控制台输出
```

#### 技术细节

1. **Tab隔离**
   ```java
   class EnhancedScriptTab extends ScriptTab {
       private Future<?> runningTask;
       private boolean isRunning;
       private List<String> console输出;
   }
   ```

2. **并发执行**
   - 不同Tab可同时运行
   - 每个Tab独立线程池
   - 资源隔离

3. **UI增强**
   - Tab标题显示状态图标
   - 运行中显示动画
   - 未保存显示 *

---

### 3. 外部文件引用功能 ⭐⭐⭐

#### 功能描述
支持在脚本中引用外部脚本文件，实现代码模块化。

#### 实现方案

**A. Groovy引用语法**
```groovy
// 方法1: load - 执行外部脚本
load('/path/to/utils.groovy')

// 方法2: evaluate - 执行并返回值
def result = evaluate(new File('helper.groovy'))

// 方法3: 自定义函数
import '/path/to/mylib.groovy'
includeScript('common/functions.groovy')
```

**B. 搜索路径**
```
脚本查找顺序:
1. 当前脚本所在目录
2. 项目脚本目录
3. 用户脚本目录 (~/.qupath/scripts/)
4. 系统脚本目录 (内置)
```

**C. IDE支持**
```
功能:
- 自动补全外部文件路径
- Ctrl+Click 跳转到外部文件
- 外部文件语法高亮
- 依赖关系显示
```

#### 技术细节

1. **脚本加载器**
   ```java
   class ScriptLoader {
       private Map<String, String> loadedScripts;
       private List<File> searchPaths;
       
       public String loadScript(String path) {
           // 搜索路径
           // 缓存已加载脚本
           // 处理循环依赖
       }
   }
   ```

2. **路径解析**
   ```java
   // 相对路径
   "utils.groovy" → 当前目录
   "./common/helper.groovy" → 当前目录
   
   // 绝对路径
   "/path/to/script.groovy"
   
   // 项目相对路径
   "project://scripts/init.groovy"
   ```

3. **缓存机制**
   - 避免重复加载
   - 检测文件变化
   - 热重载支持

---

### 4. 历史记录功能 ⭐⭐⭐

#### 功能描述
保存和管理脚本运行历史，支持快速重新运行。

#### 实现方案

**A. 历史面板**
```
界面布局:
┌─────────────────────────┐
│ 脚本历史                │
├─────────────────────────┤
│ [搜索框]                │
├─────────────────────────┤
│ ✓ script1.groovy        │ ← 成功
│   2025-01-25 10:30      │
│   运行时间: 2.3s        │
├─────────────────────────┤
│ ✗ test.groovy           │ ← 失败
│   2025-01-25 10:28      │
│   错误: NPE             │
├─────────────────────────┤
│ ✓ import_data.groovy    │
│   2025-01-25 10:25      │
└─────────────────────────┘

右键菜单:
- 重新运行
- 在编辑器中打开
- 查看输出
- 复制到剪贴板
- 删除记录
- 清空历史
```

**B. 历史记录结构**
```java
class ScriptHistoryEntry {
    String scriptName;
    String scriptContent;
    LocalDateTime runTime;
    Duration duration;
    boolean success;
    String output;
    String errorMessage;
    Map<String, Object> parameters;
}
```

**C. 持久化**
```
存储位置:
~/.qupath/script-history/
  ├── history.json          (索引)
  ├── 2025-01-25/
  │   ├── run-001.groovy
  │   ├── run-001.output
  │   ├── run-002.groovy
  │   └── run-002.output
```

#### 技术细节

1. **数据存储**
   ```json
   {
     "id": "uuid",
     "timestamp": "2025-01-25T10:30:00",
     "scriptName": "test.groovy",
     "duration": 2300,
     "success": true,
     "hash": "sha256..."
   }
   ```

2. **快速访问**
   - 最近10条快捷访问
   - 收藏功能
   - 标签分类

3. **搜索功能**
   - 按文件名搜索
   - 按日期范围搜索
   - 按运行结果搜索
   - 按内容搜索

---

## 🎯 实现优先级

### Phase 1: 核心功能（必需）⭐⭐⭐

1. **脚本停止功能**（最紧急）
   - 影响: 高
   - 难度: 中
   - 时间: 1-2天

2. **新建Tab功能**
   - 影响: 高
   - 难度: 低
   - 时间: 半天

### Phase 2: 增强功能（重要）⭐⭐

3. **外部文件引用**
   - 影响: 中
   - 难度: 中
   - 时间: 2-3天

4. **历史记录基础**
   - 影响: 中
   - 难度: 中
   - 时间: 2天

### Phase 3: 高级功能（可选）⭐

5. **历史记录高级特性**
   - 搜索、过滤
   - 可视化
   - 时间: 1-2天

---

## 🔧 技术架构

### 整体架构

```
ScriptEditor (主窗口)
├── MenuBar (菜单栏)
│   ├── File (新建Tab, 打开, 保存...)
│   ├── Edit (撤销, 重做...)
│   ├── Run (运行, 停止, 运行项目)
│   └── Tools (历史记录, 外部引用...)
├── ToolBar (工具栏)
│   ├── [运行] [停止] [新建Tab]
│   └── [保存] [打开] [历史]
├── TabPane (标签页)
│   ├── ScriptTab 1
│   │   ├── Editor (编辑器)
│   │   ├── Console (控制台)
│   │   ├── Status (状态)
│   │   └── ScriptRunner (执行器)
│   ├── ScriptTab 2
│   └── ...
└── SidePanel (侧边栏)
    ├── 历史记录
    ├── 文件浏览器
    └── 外部引用
```

### 关键组件

1. **ScriptRunner**
   ```java
   class ScriptRunner {
       private ExecutorService executor;
       private Future<?> currentTask;
       
       public void runScript(String script);
       public void stopScript();
       public boolean isRunning();
   }
   ```

2. **ScriptHistoryManager**
   ```java
   class ScriptHistoryManager {
       private List<ScriptHistoryEntry> history;
       
       public void addEntry(ScriptHistoryEntry entry);
       public List<ScriptHistoryEntry> getRecent(int count);
       public List<ScriptHistoryEntry> search(String query);
       public void save();
       public void load();
   }
   ```

3. **ScriptImporter**
   ```java
   class ScriptImporter {
       private List<File> searchPaths;
       
       public String loadScript(String path);
       public File findScript(String name);
       public List<String> getAvailableScripts();
   }
   ```

---

## 📝 UI设计

### 主界面

```
┌────────────────────────────────────────────────────────┐
│ File  Edit  Run  Tools  Help                          │
├────────────────────────────────────────────────────────┤
│ [▶运行] [⏸停止] [+新Tab] [💾保存] [📂打开] [🕐历史] │
├────────────────────────────────────────────────────────┤
│ Tab1* │ Tab2 │ Tab3 │ + │                              │
├────────────────────────────────────────────────────────┤
│                                                         │
│  import '/common/utils.groovy'              ┌────────┐ │
│                                              │ 历史   │ │
│  def processImage() {                       ├────────┤ │
│      // 你的代码                            │ test.  │ │
│  }                                          │ groovy │ │
│                                              │        │ │
│                                              │ import │ │
│                                              │ .groov │ │
│─────────────────────────────────────────────┤        │ │
│ Console:                                    │        │ │
│ > Running script...                         │        │ │
│ > Processing complete                       └────────┘ │
│                                                         │
├────────────────────────────────────────────────────────┤
│ 状态: ✓ 就绪 | 行: 10, 列: 5 | Groovy      运行: 2.3s │
└────────────────────────────────────────────────────────┘
```

---

## 🚀 实施计划

### Week 1: 核心功能

- [ ] Day 1-2: 脚本停止功能
  - 实现线程中断
  - 添加停止按钮
  - 状态指示

- [ ] Day 3: Tab管理增强
  - 新建Tab快捷方式
  - Tab关闭优化
  - 状态显示

### Week 2: 高级功能

- [ ] Day 4-5: 外部引用
  - 实现load函数
  - 路径解析
  - 错误处理

- [ ] Day 6-7: 历史记录
  - 基础存储
  - UI面板
  - 搜索功能

### Week 3: 测试和优化

- [ ] Day 8-9: 集成测试
- [ ] Day 10: 文档和示例

---

## 💡 使用示例

### 1. 停止长时间运行的脚本

```groovy
// 长时间循环
for (int i = 0; i < 1000000; i++) {
    processImage(i)
    // 现在可以随时点击[停止]按钮中断
}
```

### 2. 使用多Tab

```
Tab 1: 主处理脚本 → 运行中
Tab 2: 测试脚本 → 编辑中
Tab 3: 工具函数 → 参考
```

### 3. 引用外部脚本

```groovy
// common_utils.groovy
def processROI(roi) {
    // 通用函数
}

// main.groovy
load('common_utils.groovy')
processROI(myROI)
```

### 4. 使用历史记录

```
历史面板:
1. 双击 → 在编辑器打开
2. 右键 → 重新运行
3. 拖拽 → 复制到当前Tab
```

---

## ⚠️ 注意事项

### 兼容性

- 保持向后兼容
- 不破坏现有脚本
- 提供迁移指南

### 性能

- 大文件加载优化
- 历史记录数据库索引
- 内存管理

### 安全性

- 脚本执行沙箱
- 文件访问权限
- 恶意代码检测

---

## 📚 参考文档

### 类似产品

- **VS Code**: 多Tab, 停止按钮
- **IntelliJ IDEA**: 历史记录, 外部引用
- **Jupyter Notebook**: 执行控制

### QuPath现有功能

- ScriptTab: 已有Tab基础
- ScriptLanguage: 语言支持
- ScriptEditorControl: 编辑器接口

---

## 🎉 预期效果

### 改进前

```
❌ 脚本卡死，只能关闭整个程序
❌ 想测试新代码，需要保存当前文件
❌ 重复代码到处复制
❌ 找不到之前运行的脚本
```

### 改进后

```
✅ 一键停止脚本，立即响应
✅ 多Tab同时工作，互不干扰
✅ 代码模块化，一次编写，到处使用
✅ 历史记录，一键重跑，方便调试
```

---

**这将是一个完善且高可用的Script编辑器！** 🚀

---

**版本**: v2.0 改进方案  
**创建日期**: 2025-01  
**状态**: 📋 规划中  
**预计工作量**: 2-3周

