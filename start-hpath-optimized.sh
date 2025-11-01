#!/bin/bash

# HPath 性能优化启动脚本
# 使用优化的JVM参数启动HPath

cd "$(dirname "$0")/qupath-0.6.0"

# JVM优化参数
export JAVA_OPTS="-Xms8g -Xmx16g \
-XX:+UseG1GC \
-XX:G1HeapRegionSize=32M \
-XX:+ParallelRefProcEnabled \
-XX:MaxGCPauseMillis=200 \
-XX:+UnlockExperimentalVMOptions \
-XX:+DisableExplicitGC \
-XX:+AlwaysPreTouch \
-XX:InitiatingHeapOccupancyPercent=45 \
-XX:+UseStringDeduplication \
-XX:+OptimizeStringConcat \
-Djava.awt.headless=false \
-Dprism.order=sw \
-Dprism.maxvram=2G"

echo "🚀 Starting HPath with optimized JVM parameters..."
echo "   Initial Heap: 8GB"
echo "   Maximum Heap: 16GB"
echo "   GC: G1 (optimized for large memory)"
echo ""

./build/scripts/HPath "$@"

