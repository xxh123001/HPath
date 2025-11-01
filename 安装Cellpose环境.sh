#!/bin/bash
# Cellpose Python环境安装脚本

echo "========================================="
echo "开始安装Cellpose Python环境"
echo "========================================="

# 检查conda是否已安装
if ! command -v conda &> /dev/null; then
    echo "❌ Conda未安装"
    echo "请先安装Anaconda或Miniconda:"
    echo "  https://docs.conda.io/en/latest/miniconda.html"
    exit 1
fi

echo "✅ 检测到Conda: $(conda --version)"

# 创建cellpose环境
echo ""
echo "创建cellpose环境..."
conda create -n cellpose python=3.10 -y

# 激活环境
echo ""
echo "激活cellpose环境..."
source $(conda info --base)/etc/profile.d/conda.sh
conda activate cellpose

# 安装cellpose
echo ""
echo "安装cellpose..."
pip install cellpose[gui]

# 验证安装
echo ""
echo "验证安装..."
python -m cellpose --version

# 获取Python路径
PYTHON_PATH=$(which python)

echo ""
echo "========================================="
echo "✅ Cellpose安装完成！"
echo "========================================="
echo ""
echo "Python路径："
echo "$PYTHON_PATH"
echo ""
echo "请复制上面的路径，然后："
echo "1. 打开HPath"
echo "2. Edit → Preferences"
echo "3. 搜索 'Cellpose'"
echo "4. 粘贴Python路径"
echo ""
echo "或者运行下一个脚本自动配置"









