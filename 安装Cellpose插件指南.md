# QuPath Cellpose插件安装指南

## 方法1：通过扩展管理器安装（推荐）

### 步骤1: 打开扩展管理器

1. 启动HPath/QuPath
2. 菜单栏：**Extensions → Manage extensions**

### 步骤2: 安装Cellpose扩展

在扩展管理器中：
1. 找到 **"Cellpose"** 扩展（作者：BIOP Team）
2. 点击 **"Install"** 按钮
3. 等待下载完成
4. 点击 **"Close"**

### 步骤3: 重启HPath

关闭并重新启动HPath以加载新安装的扩展。

### 步骤4: 验证安装

重启后，检查：
- 菜单栏应该出现 **Extensions → Cellpose** 菜单
- 或者在脚本中可以 `import qupath.ext.biop.cellpose.Cellpose2D`

---

## 方法2: 手动下载安装

如果扩展管理器无法使用，可以手动安装：

### 步骤1: 下载扩展

访问: https://github.com/BIOP/qupath-extension-cellpose/releases

下载最新版本的 `.jar` 文件，例如：
```
qupath-extension-cellpose-0.9.4.jar
```

### 步骤2: 安装到QuPath

将下载的 `.jar` 文件复制到QuPath扩展目录：

**macOS:**
```bash
~/Library/Application Support/QuPath-0.6/extensions/
```

或者在HPath中：
```bash
~/Library/Application Support/HPath/extensions/
```

**Windows:**
```
C:\Users\你的用户名\AppData\Roaming\QuPath-0.6\extensions\
```

**Linux:**
```
~/.config/QuPath-0.6/extensions/
```

### 步骤3: 创建extensions文件夹（如果不存在）

```bash
# macOS/Linux
mkdir -p ~/Library/Application\ Support/QuPath-0.6/extensions/

# 或创建HPath专用目录
mkdir -p ~/Library/Application\ Support/HPath/extensions/
```

### 步骤4: 复制文件

```bash
# 示例：将下载的jar文件复制到扩展目录
cp ~/Downloads/qupath-extension-cellpose-0.9.4.jar ~/Library/Application\ Support/QuPath-0.6/extensions/
```

### 步骤5: 重启HPath

完全关闭并重新启动HPath。

---

## 方法3: 使用命令行安装（最快）

我可以帮你用命令行直接下载和安装：

```bash
# 创建扩展目录
mkdir -p ~/Library/Application\ Support/QuPath-0.6/extensions/

# 下载最新版Cellpose扩展
cd ~/Library/Application\ Support/QuPath-0.6/extensions/
curl -L -O https://github.com/BIOP/qupath-extension-cellpose/releases/download/v0.9.4/qupath-extension-cellpose-0.9.4.jar

# 验证文件
ls -lh qupath-extension-cellpose*.jar
```

---

## 验证安装成功

重启HPath后，在Script Editor中运行：

```groovy
import qupath.ext.biop.cellpose.Cellpose2D

println "✅ Cellpose扩展已成功安装！"
println "版本: " + Cellpose2D.class.getPackage().getImplementationVersion()
```

如果没有错误，说明安装成功！

---

## 安装Cellpose Python环境

插件安装后，还需要安装Cellpose Python环境：

### 使用Conda（推荐）

```bash
# 创建新环境
conda create -n cellpose python=3.8

# 激活环境
conda activate cellpose

# 安装cellpose
pip install cellpose

# 验证安装
python -m cellpose --version
```

### 在QuPath中配置Cellpose环境

1. **Edit → Preferences → Cellpose**
2. **设置Python环境路径：**
   - macOS/Linux: `/path/to/anaconda3/envs/cellpose/bin/python`
   - Windows: `C:\Users\你的用户名\anaconda3\envs\cellpose\python.exe`

---

## 常见问题

### Q1: 扩展管理器中找不到Cellpose？

**A:** 
- 检查网络连接
- 或使用手动安装方法

### Q2: 插件安装后菜单没有Cellpose？

**A:**
- 确保重启了QuPath/HPath
- 检查extensions文件夹是否正确
- 查看日志是否有加载错误

### Q3: 运行脚本时提示找不到Cellpose2D？

**A:**
- 插件可能没有正确加载
- 重启HPath
- 检查jar文件是否在extensions目录

---

## 我来帮你自动安装

你想让我帮你运行命令自动下载和安装吗？我可以执行上面的命令行安装方法。









