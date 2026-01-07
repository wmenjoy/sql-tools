#!/bin/bash

# ==========================================
# SQL Guard Demo - 拦截器测试脚本
# ==========================================
# 
# 测试不同拦截器配置:
# 1. MyBatis 拦截器 (application-mybatis.yml)
# 2. Druid 拦截器 (application-druid.yml)
# 3. 默认配置
# ==========================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || echo $JAVA_HOME)
JAVA="$JAVA_HOME/bin/java"

# 停止所有运行中的实例
stop_service() {
    pkill -f "sql-guard-demo" 2>/dev/null || true
    sleep 2
}

# 启动服务
start_service() {
    local profile=$1
    local port=${2:-8081}
    
    echo -e "${BLUE}启动服务 (profile=$profile, port=$port)...${NC}"
    
    if [ -n "$profile" ]; then
        $JAVA -jar target/sql-guard-demo-1.0.0-SNAPSHOT.jar \
            --spring.profiles.active=$profile \
            --server.port=$port \
            > /tmp/sqlguard-$profile.log 2>&1 &
    else
        $JAVA -jar target/sql-guard-demo-1.0.0-SNAPSHOT.jar \
            --server.port=$port \
            > /tmp/sqlguard-default.log 2>&1 &
    fi
    
    # 等待服务启动
    local max_wait=30
    local waited=0
    while [ $waited -lt $max_wait ]; do
        if curl -s "http://localhost:$port/actuator/health" > /dev/null 2>&1; then
            echo -e "${GREEN}服务已启动${NC}"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done
    
    echo -e "${RED}服务启动超时${NC}"
    return 1
}

# 测试违规端点
test_violation() {
    local port=$1
    local endpoint=$2
    local expected_checker=$3
    
    local response=$(curl -s "http://localhost:$port$endpoint")
    local actual_checker=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('checker', ''))" 2>/dev/null)
    
    if [ "$actual_checker" = "$expected_checker" ]; then
        echo -e "  ${GREEN}✅ $endpoint -> $actual_checker${NC}"
        return 0
    else
        echo -e "  ${RED}❌ $endpoint -> 预期 $expected_checker, 实际 $actual_checker${NC}"
        return 1
    fi
}

# 检查日志中的拦截器
check_interceptor_in_log() {
    local log_file=$1
    local interceptor_name=$2
    
    if grep -q "$interceptor_name" "$log_file" 2>/dev/null; then
        echo -e "  ${GREEN}✅ 日志中发现 $interceptor_name${NC}"
        return 0
    else
        echo -e "  ${RED}❌ 日志中未发现 $interceptor_name${NC}"
        return 1
    fi
}

# ==========================================
# 测试 MyBatis 拦截器
# ==========================================
test_mybatis_interceptor() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  测试 1: MyBatis 拦截器${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
    
    stop_service
    start_service "mybatis" 8081
    
    echo ""
    echo "测试违规检测:"
    test_violation 8081 "/violations/no-where-clause" "NoWhereClauseChecker"
    test_violation 8081 "/violations/dummy-condition" "DummyConditionChecker"
    test_violation 8081 "/violations/blacklist-only" "BlacklistFieldChecker"
    
    echo ""
    echo "检查日志:"
    sleep 2  # 等待日志写入
    check_interceptor_in_log "/tmp/sqlguard-mybatis.log" "SqlSafetyInterceptor"
    check_interceptor_in_log "/tmp/sqlguard-mybatis.log" "SQL Safety Violation"
    
    echo ""
    echo -e "${GREEN}MyBatis 拦截器测试完成${NC}"
}

# ==========================================
# 测试 Druid 拦截器
# ==========================================
test_druid_interceptor() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  测试 2: Druid 拦截器${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
    
    stop_service
    start_service "druid" 8082
    
    echo ""
    echo "测试违规检测:"
    test_violation 8082 "/violations/no-where-clause" "NoWhereClauseChecker"
    test_violation 8082 "/violations/dummy-condition" "DummyConditionChecker"
    test_violation 8082 "/violations/blacklist-only" "BlacklistFieldChecker"
    
    echo ""
    echo "检查日志:"
    sleep 2
    # Druid 使用不同的拦截器类
    if grep -q "druid\|Druid\|SqlGuardFilter" "/tmp/sqlguard-druid.log" 2>/dev/null; then
        echo -e "  ${GREEN}✅ Druid 拦截器已激活${NC}"
    else
        echo -e "  ${YELLOW}⚠️ 未检测到 Druid 特定日志 (可能使用了默认 MyBatis 拦截器)${NC}"
    fi
    
    echo ""
    echo -e "${GREEN}Druid 拦截器测试完成${NC}"
}

# ==========================================
# 测试默认配置
# ==========================================
test_default_config() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  测试 3: 默认配置${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
    
    stop_service
    start_service "" 8083
    
    echo ""
    echo "测试违规检测:"
    test_violation 8083 "/violations/no-where-clause" "NoWhereClauseChecker"
    test_violation 8083 "/violations/dummy-condition" "DummyConditionChecker"
    test_violation 8083 "/violations/blacklist-only" "BlacklistFieldChecker"
    
    echo ""
    echo "检查日志:"
    sleep 2
    check_interceptor_in_log "/tmp/sqlguard-default.log" "SQL Safety Violation"
    
    echo ""
    echo -e "${GREEN}默认配置测试完成${NC}"
}

# ==========================================
# 主函数
# ==========================================
main() {
    echo ""
    echo -e "${CYAN}===========================================${NC}"
    echo -e "${CYAN}  SQL Guard - 拦截器测试${NC}"
    echo -e "${CYAN}===========================================${NC}"
    echo ""
    echo "Java: $JAVA"
    echo "项目目录: $PROJECT_DIR"
    echo ""
    
    # 确保已编译
    if [ ! -f "target/sql-guard-demo-1.0.0-SNAPSHOT.jar" ]; then
        echo "编译项目..."
        mvn package -DskipTests -q
    fi
    
    # 执行测试
    test_mybatis_interceptor
    test_druid_interceptor
    test_default_config
    
    # 清理
    stop_service
    
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  测试完成${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
    echo "日志文件:"
    echo "  - /tmp/sqlguard-mybatis.log"
    echo "  - /tmp/sqlguard-druid.log"
    echo "  - /tmp/sqlguard-default.log"
}

# 执行主函数
main
