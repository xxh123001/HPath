#!/bin/bash

# 批量重命名脚本 v2.0 - 将kt_merged_split目录下的文件重命名为标准格式
# 作者: AI Assistant
# 日期: 2025年10月29日

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 源目录
SOURCE_BASE="/Users/felix/Desktop/光密度/2025年10月29日/kt_merged_split"

# 统计变量
TOTAL_PROJECTS=0
TOTAL_FILES=0
RENAMED_FILES=0
SKIPPED_FILES=0
ERROR_FILES=0

# 日志文件
LOG_FILE="/Users/felix/Downloads/HPath-main/重命名日志_$(date +%Y%m%d_%H%M%S).log"

# 打印日志函数
log() {
    echo -e "$1" | tee -a "$LOG_FILE"
}

# 打印标题
print_header() {
    log "${BLUE}================================${NC}"
    log "${BLUE}$1${NC}"
    log "${BLUE}================================${NC}"
}

# 重命名函数
rename_file() {
    local project_dir=$1
    local project_id=$2
    local source_file=$3
    local target_file=$4
    
    local source_path="$project_dir/$source_file"
    local target_path="$project_dir/$target_file"
    
    # 检查源文件是否存在
    if [ ! -f "$source_path" ]; then
        log "  ${YELLOW}⊗ 文件不存在: $source_file${NC}"
        SKIPPED_FILES=$((SKIPPED_FILES + 1))
        return 1
    fi
    
    # 检查目标文件是否已存在
    if [ -f "$target_path" ]; then
        log "  ${YELLOW}⊗ 目标文件已存在，跳过: $target_file${NC}"
        SKIPPED_FILES=$((SKIPPED_FILES + 1))
        return 1
    fi
    
    TOTAL_FILES=$((TOTAL_FILES + 1))
    
    # 显示重命名操作
    log "  ${GREEN}✓${NC} $source_file"
    log "    → $target_file"
    
    # 执行重命名
    if [ "$DRY_RUN" = false ]; then
        if mv "$source_path" "$target_path" 2>/dev/null; then
            RENAMED_FILES=$((RENAMED_FILES + 1))
            return 0
        else
            log "    ${RED}✗ 重命名失败!${NC}"
            ERROR_FILES=$((ERROR_FILES + 1))
            return 1
        fi
    else
        RENAMED_FILES=$((RENAMED_FILES + 1))
        return 0
    fi
}

# 检查目录是否存在
if [ ! -d "$SOURCE_BASE" ]; then
    log "${RED}错误: 源目录不存在: $SOURCE_BASE${NC}"
    exit 1
fi

print_header "批量重命名脚本 v2.0 - 开始执行"
log "源目录: $SOURCE_BASE"
log "日志文件: $LOG_FILE"
log "执行模式: ${1:-DRY_RUN}"
log ""

# 判断是预览模式还是实际执行模式
DRY_RUN=true
if [ "$1" = "--execute" ]; then
    DRY_RUN=false
    log "${YELLOW}⚠️  实际执行模式 - 将修改文件名${NC}"
    log ""
    read -p "确认要执行重命名吗？(输入 YES 继续): " confirmation
    if [ "$confirmation" != "YES" ]; then
        log "${RED}用户取消操作${NC}"
        exit 0
    fi
    log ""
else
    log "${GREEN}✓ 预览模式 - 不会实际修改文件${NC}"
    log "${YELLOW}提示: 使用 --execute 参数执行实际重命名${NC}"
fi
log ""

# 遍历所有K2024开头的目录
for PROJECT_DIR in "$SOURCE_BASE"/K2024-*; do
    # 检查是否是目录
    if [ ! -d "$PROJECT_DIR" ]; then
        continue
    fi
    
    # 提取项目编号
    PROJECT_ID=$(basename "$PROJECT_DIR")
    
    # 跳过非标准命名的目录
    if [[ ! "$PROJECT_ID" =~ ^K2024-[0-9]{4}$ ]]; then
        log "${YELLOW}⊗ 跳过非标准命名目录: $PROJECT_ID${NC}"
        continue
    fi
    
    TOTAL_PROJECTS=$((TOTAL_PROJECTS + 1))
    
    print_header "处理项目: $PROJECT_ID"
    
    PROJECT_START_FILES=$TOTAL_FILES
    PROJECT_START_RENAMED=$RENAMED_FILES
    
    # 逐个处理文件映射
    rename_file "$PROJECT_DIR" "$PROJECT_ID" "kt.json" "${PROJECT_ID}_kt.json"
    rename_file "$PROJECT_DIR" "$PROJECT_ID" "light.svs" "${PROJECT_ID}_PAS.svs"
    rename_file "$PROJECT_DIR" "$PROJECT_ID" "qp1_480_all.svs" "${PROJECT_ID}_UMOD.svs"
    rename_file "$PROJECT_DIR" "$PROJECT_ID" "qp1_520_yuan.svs" "${PROJECT_ID}_LRP2.svs"
    rename_file "$PROJECT_DIR" "$PROJECT_ID" "qp1_620_jin.svs" "${PROJECT_ID}_AQP1.svs"
    rename_file "$PROJECT_DIR" "$PROJECT_ID" "qp1_690_shengzhi.svs" "${PROJECT_ID}_PanCK.svs"
    rename_file "$PROJECT_DIR" "$PROJECT_ID" "qp2_480_xueguan.svs" "${PROJECT_ID}_CD31.svs"
    rename_file "$PROJECT_DIR" "$PROJECT_ID" "qp2_570_jihe.svs" "${PROJECT_ID}_AQP2.svs"
    rename_file "$PROJECT_DIR" "$PROJECT_ID" "qp2_620_shengzhi.svs" "${PROJECT_ID}_SLC8A1.svs"
    rename_file "$PROJECT_DIR" "$PROJECT_ID" "qp2_690_jin.svs" "${PROJECT_ID}_PNA.svs"
    
    # 计算本项目的统计
    PROJECT_FILES=$((TOTAL_FILES - PROJECT_START_FILES))
    PROJECT_RENAMED=$((RENAMED_FILES - PROJECT_START_RENAMED))
    
    # 打印项目统计
    log ""
    log "  项目统计: 发现 $PROJECT_FILES 个文件, 重命名 $PROJECT_RENAMED 个"
    log ""
done

# 打印总体统计
print_header "执行完成 - 总体统计"
log "处理项目数: ${BLUE}$TOTAL_PROJECTS${NC}"
log "发现文件数: ${BLUE}$TOTAL_FILES${NC}"
log "重命名文件: ${GREEN}$RENAMED_FILES${NC}"
log "跳过文件数: ${YELLOW}$SKIPPED_FILES${NC}"
log "错误文件数: ${RED}$ERROR_FILES${NC}"
log ""

if [ "$DRY_RUN" = true ]; then
    log "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    log "${YELLOW}这是预览模式，未实际修改任何文件${NC}"
    log "${YELLOW}要执行实际重命名，请运行:${NC}"
    log "${GREEN}bash \"$0\" --execute${NC}"
    log "${YELLOW}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
else
    log "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    log "${GREEN}✓ 重命名操作已完成！${NC}"
    log "${GREEN}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
fi

log ""
log "详细日志已保存到: $LOG_FILE"

exit 0














