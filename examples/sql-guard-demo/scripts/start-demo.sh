#!/bin/bash

# SQL Guard Demo - 启动脚本
# 使用方法: ./scripts/start-demo.sh [profile]
# 示例:
#   ./scripts/start-demo.sh           # 默认使用 LOG 策略
#   ./scripts/start-demo.sh mybatis   # 使用 MyBatis 拦截器
#   ./scripts/start-demo.sh druid     # 使用 Druid 拦截器
#   ./scripts/start-demo.sh block     # 使用 BLOCK 策略

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

PROFILE=${1:-""}

echo "=========================================="
echo "  SQL Guard Demo 服务启动"
echo "=========================================="
echo ""
echo "项目目录: $PROJECT_DIR"
echo "Spring Profile: ${PROFILE:-默认}"
echo ""

# 检查是否已编译
if [ ! -f "target/sql-guard-demo-1.0.0-SNAPSHOT.jar" ]; then
    echo "正在编译项目..."
    mvn package -DskipTests -q
fi

echo "启动服务..."
echo ""

if [ -n "$PROFILE" ]; then
    echo "使用配置: application-${PROFILE}.yml"
    java -jar target/sql-guard-demo-1.0.0-SNAPSHOT.jar --spring.profiles.active=$PROFILE
else
    echo "使用默认配置"
    java -jar target/sql-guard-demo-1.0.0-SNAPSHOT.jar
fi
