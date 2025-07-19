#!/bin/bash

# 数据迁移脚本
# 将项目目录中的数据文件迁移到 ~/data/adam-monitor/ 目录

echo "=== Adam Monitor 数据迁移脚本 ==="
echo "此脚本将项目目录中的数据文件迁移到 ~/data/adam-monitor/ 目录"
echo ""

# 目标目录
TARGET_DIR="$HOME/data/adam-monitor"
PROJECT_DIR="$(pwd)"

# 确保目标目录存在
echo "1. 确保目标目录存在..."
mkdir -p "$TARGET_DIR"/{zk_data,zk_log,kafka_data,nacos/{logs,data,mysql_data},ch_data,ch_logs,redis_data,prometheus_data,grafana_data,es_data,flink_checkpoints,user}

# 数据文件夹列表
DATA_DIRS=(
    "zk_data"
    "zk_log" 
    "kafka_data"
    "nacos"
    "ch_data"
    "ch_logs"
    "redis_data"
    "prometheus_data"
    "grafana_data"
    "flink_checkpoints"
    "user"
    "data"
)

echo "2. 开始迁移数据文件..."

for dir in "${DATA_DIRS[@]}"; do
    if [ -d "$PROJECT_DIR/$dir" ] && [ "$(ls -A "$PROJECT_DIR/$dir" 2>/dev/null)" ]; then
        echo "   迁移 $dir/ ..."
        
        # 如果目标目录已存在且有内容，先备份
        if [ -d "$TARGET_DIR/$dir" ] && [ "$(ls -A "$TARGET_DIR/$dir" 2>/dev/null)" ]; then
            echo "     目标目录已存在数据，创建备份..."
            mv "$TARGET_DIR/$dir" "$TARGET_DIR/${dir}.backup.$(date +%Y%m%d_%H%M%S)"
        fi
        
        # 移动数据
        mv "$PROJECT_DIR/$dir" "$TARGET_DIR/"
        echo "     ✓ $dir/ 迁移完成"
    else
        echo "   跳过 $dir/ (不存在或为空)"
    fi
done

echo ""
echo "3. 清理项目目录中的残留数据文件..."

# 清理可能残留的空目录
for dir in "${DATA_DIRS[@]}"; do
    if [ -d "$PROJECT_DIR/$dir" ] && [ ! "$(ls -A "$PROJECT_DIR/$dir" 2>/dev/null)" ]; then
        rmdir "$PROJECT_DIR/$dir" 2>/dev/null
        echo "   ✓ 删除空目录 $dir/"
    fi
done

echo ""
echo "=== 迁移完成 ==="
echo "数据已迁移到: $TARGET_DIR"
echo ""
echo "现在可以安全地使用新的Docker Compose配置："
echo "  docker-compose -f docker-compose-test.yml up -d"
echo "  docker-compose -f docker-compose-skywalking.yaml up -d"
echo ""
echo "如果需要回滚，备份文件位于 $TARGET_DIR/*.backup.*" 