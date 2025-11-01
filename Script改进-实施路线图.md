# Script编辑器完整改进 - 实施路线图

## 🎯 项目规模评估

经过代码分析，发现：

### 现状
- **DefaultScriptEditor.java**: 2259行，核心文件
- **已有基础**: killRunningScriptAction, runningTask管理
- **需要改进**: 界面、功能增强、新功能

### 实际工作量
```
预估工作量:
├── 阶段1: 脚本停止优化 (2-3天)
├── 阶段2: Tab管理增强 (2-3天)
├── 阶段3: 外部引用 (3-4天)
├── 阶段4: 历史记录 (3-4天)
└── 阶段5: 测试集成 (2-3天)

总计: 12-17天 (约2.5-3.5周)
```

---

## 📋 详细实施计划

### Phase 1: 脚本停止功能 [2-3天]

#### 目标
✅ 工具栏添加停止按钮  
✅ 改进停止机制  
✅ 添加状态指示  

#### 文件修改
```
DefaultScriptEditor.java:
- 添加停止按钮到工具栏 (约50行)
- 改进runningTask管理 (约30行)
- 添加状态指示器 (约40行)
```

#### 实现细节
```java
// 1. 工具栏添加停止按钮
var btnStop = ActionTools.createButton(killRunningScriptAction);
btnStop.setText("⏸ Stop");
// 添加到工具栏

// 2. 状态指示
Label statusLabel = new Label();
runningTask.addListener((obs, oldVal, newVal) -> {
    if (newVal != null && !newVal.isDone()) {
        statusLabel.setText("⏳ Running...");
    } else {
        statusLabel.setText("✓ Ready");
    }
});

// 3. 增强停止机制
- 添加超时强制终止
- 资源清理
- 错误处理
```

---

### Phase 2: Tab管理增强 [2-3天]

#### 目标
✅ 新建Tab快捷键 (Ctrl+T)  
✅ Tab状态显示 (运行中/已修改)  
✅ Tab右键菜单  

#### 文件修改
```
DefaultScriptEditor.java:
- 添加新建Tab快捷键 (约30行)
- Tab标题状态显示 (约50行)
- Tab右键菜单 (约80行)

ScriptTab.java:
- 添加运行状态属性 (约20行)
- 状态变化监听 (约30行)
```

#### 实现细节
```java
// 1. 新建Tab快捷键
Action newTabAction = new Action("New Tab", e -> {
    addNewTab("Untitled", "");
});
newTabAction.setAccelerator(
    new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN)
);

// 2. Tab状态显示
tab.setGraphic(createTabGraphic(tab));
// 显示: ⏳ running | * modified | ✓ saved

// 3. 右键菜单
ContextMenu tabMenu = new ContextMenu(
    new MenuItem("Close"),
    new MenuItem("Close Others"),
    new MenuItem("Close All"),
    new SeparatorMenuItem(),
    new MenuItem("Rename"),
    new MenuItem("Duplicate")
);
```

---

### Phase 3: 外部文件引用 [3-4天]

#### 目标
✅ 创建ScriptLoader类  
✅ 实现load()和includeScript()  
✅ 路径搜索机制  

#### 新建文件
```
1. ScriptLoader.java (约300行)
   - 文件搜索
   - 缓存管理
   - 路径解析

2. ScriptImportExtension.java (约150行)
   - Groovy扩展方法
   - load(), includeScript()
```

#### 文件修改
```
DefaultScriptEditor.java:
- 集成ScriptLoader (约50行)
- 添加搜索路径配置 (约30行)
```

#### 实现细节
```java
// ScriptLoader.java
public class ScriptLoader {
    private List<File> searchPaths;
    private Map<String, String> cache;
    
    public String loadScript(String path) {
        // 1. 解析路径
        // 2. 搜索文件
        // 3. 读取并缓存
        // 4. 返回内容
    }
    
    public File findScript(String name) {
        for (File searchPath : searchPaths) {
            File script = new File(searchPath, name);
            if (script.exists()) {
                return script;
            }
        }
        return null;
    }
}

// 使用示例
load('/path/to/utils.groovy')
includeScript('common/functions.groovy')
```

---

### Phase 4: 历史记录功能 [3-4天]

#### 目标
✅ 创建历史记录管理器  
✅ 持久化存储  
✅ UI面板  
✅ 搜索功能  

#### 新建文件
```
1. ScriptHistoryManager.java (约400行)
   - 历史记录CRUD
   - JSON序列化
   - 搜索过滤

2. ScriptHistoryEntry.java (约100行)
   - 历史条目数据结构

3. ScriptHistoryPanel.java (约500行)
   - UI面板
   - 列表显示
   - 搜索框
   - 右键菜单
```

#### 文件修改
```
DefaultScriptEditor.java:
- 集成历史管理器 (约80行)
- 添加历史面板 (约50行)
- executeScript调用后记录 (约30行)
```

#### 实现细节
```java
// ScriptHistoryEntry.java
public class ScriptHistoryEntry {
    private String id;
    private LocalDateTime timestamp;
    private String scriptName;
    private String scriptContent;
    private Duration duration;
    private boolean success;
    private String output;
    private String errorMessage;
}

// ScriptHistoryManager.java
public class ScriptHistoryManager {
    private static final String HISTORY_FILE = 
        System.getProperty("user.home") + "/.qupath/script-history.json";
    
    public void addEntry(ScriptHistoryEntry entry) {
        // 保存到历史
    }
    
    public List<ScriptHistoryEntry> getRecent(int count) {
        // 获取最近N条
    }
    
    public void save() {
        // 持久化到文件
    }
}

// ScriptHistoryPanel.java
public class ScriptHistoryPanel extends BorderPane {
    private ListView<ScriptHistoryEntry> historyList;
    private TextField searchField;
    
    public ScriptHistoryPanel() {
        // 构建UI
        setupHistoryList();
        setupSearchField();
        setupContextMenu();
    }
}
```

---

### Phase 5: 集成测试 [2-3天]

#### 目标
✅ 功能测试  
✅ Bug修复  
✅ 性能优化  
✅ 文档更新  

#### 测试清单
```
停止功能:
- [ ] 停止长时间运行的脚本
- [ ] 停止死循环
- [ ] 多次快速停止/运行
- [ ] 资源是否正确清理

Tab管理:
- [ ] 新建Tab快捷键
- [ ] 同时运行多个Tab
- [ ] Tab关闭是否保存提示
- [ ] Tab状态正确显示

外部引用:
- [ ] 相对路径加载
- [ ] 绝对路径加载
- [ ] 循环依赖检测
- [ ] 错误处理

历史记录:
- [ ] 记录运行脚本
- [ ] 搜索功能
- [ ] 重新运行
- [ ] 持久化存储
```

---

## 📊 代码量统计

### 新增文件 (4个)
```
ScriptLoader.java              ~300行
ScriptImportExtension.java     ~150行
ScriptHistoryManager.java      ~400行
ScriptHistoryEntry.java        ~100行
ScriptHistoryPanel.java        ~500行
─────────────────────────────────────
总计:                         ~1450行
```

### 修改文件 (2个)
```
DefaultScriptEditor.java       ~400行 (新增/修改)
ScriptTab.java                 ~100行 (新增/修改)
─────────────────────────────────────
总计:                          ~500行
```

### 文档 (3个)
```
Script功能使用手册.md          ~300行
Script改进技术文档.md          ~400行
Script功能示例.md              ~200行
─────────────────────────────────────
总计:                          ~900行
```

**总代码量**: ~2850行

---

## 🚀 实施策略

### 方案A: 完整一次性实施

**优点**:
- 功能完整
- 一次性解决所有问题
- 测试更全面

**缺点**:
- 时间长 (2.5-3.5周)
- 一次性改动大
- 风险集中

### 方案B: 分阶段实施（推荐）⭐

**阶段1** (Week 1): 停止功能 + Tab管理
- 时间: 4-6天
- 风险: 低
- 产出: 立即可用

**阶段2** (Week 2): 外部引用
- 时间: 3-4天
- 风险: 中
- 产出: 代码复用

**阶段3** (Week 3): 历史记录
- 时间: 3-4天
- 风险: 低
- 产出: 调试增强

**优点**:
- 分步验证
- 风险分散
- 灵活调整

### 方案C: 核心功能优先

**只实施**:
- Phase 1: 停止功能
- Phase 2: Tab管理基础
- 时间: 4-6天

**其他功能**:
- 作为独立插件
- 或后续版本

---

## 💡 我的建议

### 推荐: 方案B（分阶段）

**理由**:
1. **快速见效**: 第1周就能看到改进
2. **风险控制**: 每阶段独立测试
3. **时间灵活**: 可根据实际调整
4. **质量保证**: 充分测试每个功能

### 实施流程

```
Week 1: 停止+Tab管理
  Day 1-2: 实现停止功能
  Day 3-4: 增强Tab管理
  Day 5: 测试和修复
  ✅ 交付第一版

Week 2: 外部引用
  Day 6-8: 实现ScriptLoader
  Day 9: 集成和测试
  ✅ 交付第二版

Week 3: 历史记录
  Day 10-12: 实现历史功能
  Day 13-14: 完整测试
  ✅ 交付完整版
```

---

## 🎯 立即开始

如果你同意方案B，我将：

### Week 1 (现在开始)

**Day 1-2: 停止功能**
```
1. 修改DefaultScriptEditor.java
   - 添加停止按钮到工具栏
   - 改进停止机制
   - 添加状态指示

2. 测试
   - 创建测试脚本
   - 验证停止功能
```

**Day 3-4: Tab管理**
```
1. 修改DefaultScriptEditor.java
   - 新建Tab快捷键
   - Tab状态显示
   - Tab右键菜单

2. 修改ScriptTab.java
   - 添加状态属性

3. 测试
   - 多Tab场景
   - 快捷键
```

**Day 5: 测试优化**
```
1. 集成测试
2. Bug修复
3. 性能优化
4. 文档更新
```

---

## 📝 需要你的确认

请确认以下事项：

1. **实施方案**: 
   - [ ] A. 完整一次性实施 (2.5-3.5周)
   - [ ] B. 分阶段实施 (推荐，Week by week)
   - [ ] C. 只做核心功能 (4-6天)

2. **开始时间**:
   - [ ] 立即开始
   - [ ] 等待进一步讨论

3. **优先级确认**:
   - [ ] 按建议顺序（停止→Tab→引用→历史）
   - [ ] 其他顺序（请说明）

4. **测试协助**:
   - [ ] 我会在每个阶段结束后测试
   - [ ] 完成后统一测试

---

## 🎉 预期成果

**完成后你将拥有**:

```
✅ 响应式脚本停止（工具栏一键停止）
✅ 强大的多Tab管理（快捷键、状态显示）
✅ 灵活的代码复用（外部文件引用）
✅ 完整的运行历史（搜索、重跑）

= 一个完善且高可用的Script编辑器！
```

---

**准备就绪，等待你的确认！** 🚀

---

**版本**: 实施路线图 v1.0  
**创建时间**: 2025-01  
**预计周期**: 2.5-3.5周（分阶段）

