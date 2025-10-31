#!/bin/bash
# QuPath 快速重启脚本

echo "🔨 正在编译..."
./gradlew compileJava --quiet

if [ $? -eq 0 ]; then
    echo "✅ 编译成功"
    echo "🚀 启动 QuPath..."
    ./gradlew run
else
    echo "❌ 编译失败，请检查错误"
    exit 1
fi

