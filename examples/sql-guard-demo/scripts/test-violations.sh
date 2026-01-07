#!/bin/bash

# SQL Guard Demo - 违规测试脚本
# 使用方法: ./scripts/test-violations.sh [base_url]
# 示例:
#   ./scripts/test-violations.sh                    # 默认 http://localhost:8080
#   ./scripts/test-violations.sh http://localhost:9090

set -e

BASE_URL=${1:-"http://localhost:8081"}

echo "=========================================="
echo "  SQL Guard 违规测试脚本"
echo "=========================================="
echo ""
echo "目标服务: $BASE_URL"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 测试函数
test_endpoint() {
    local name=$1
    local endpoint=$2
    local method=${3:-GET}
    
    echo -e "${BLUE}测试: $name${NC}"
    echo "  端点: $method $endpoint"
    
    if [ "$method" = "GET" ]; then
        response=$(curl -s -w "\n%{http_code}" "$BASE_URL$endpoint")
    else
        response=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE_URL$endpoint")
    fi
    
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        echo -e "  状态: ${GREEN}$http_code OK${NC}"
    else
        echo -e "  状态: ${RED}$http_code${NC}"
    fi
    
    # 格式化 JSON 输出
    if command -v jq &> /dev/null; then
        echo "  响应: $(echo "$body" | jq -c '.')"
    else
        echo "  响应: $body"
    fi
    echo ""
}

# 检查服务是否运行
echo "检查服务状态..."
health_response=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null || echo "000")

if [ "$health_response" != "200" ]; then
    echo -e "${RED}错误: 服务未运行或不可达${NC}"
    echo "请先启动服务: ./scripts/start-demo.sh"
    exit 1
fi

echo -e "${GREEN}服务运行中${NC}"
echo ""

echo "=========================================="
echo "  1. 基础检查"
echo "=========================================="
echo ""

test_endpoint "健康检查" "/actuator/health"
test_endpoint "API 文档" "/"

echo "=========================================="
echo "  2. SQL 违规测试"
echo "=========================================="
echo ""

echo -e "${YELLOW}以下测试会触发 SQL Guard 拦截器检测违规${NC}"
echo -e "${YELLOW}请观察服务端日志查看违规详情${NC}"
echo ""

test_endpoint "NoWhereClause (CRITICAL) - DELETE 无 WHERE" "/violations/no-where-clause"
test_endpoint "DummyCondition (HIGH) - WHERE 1=1" "/violations/dummy-condition"
test_endpoint "BlacklistOnly (HIGH) - 只有黑名单字段" "/violations/blacklist-only"
test_endpoint "WhitelistMissing (HIGH) - 缺少白名单字段" "/violations/whitelist-missing"
test_endpoint "DeepPagination (MEDIUM) - 深度分页" "/violations/deep-pagination"
test_endpoint "LargePageSize (MEDIUM) - 大页面" "/violations/large-page-size"
test_endpoint "MissingOrderBy (LOW) - 分页无排序" "/violations/missing-orderby"
test_endpoint "NoPagination - 无分页" "/violations/no-pagination"
test_endpoint "NoConditionPagination (CRITICAL) - 无条件分页" "/violations/no-condition-pagination"

echo "=========================================="
echo "  3. 违规日志查看"
echo "=========================================="
echo ""

test_endpoint "查看违规日志" "/violations/logs"

echo "=========================================="
echo "  4. 审计场景测试"
echo "=========================================="
echo ""

test_endpoint "慢查询场景" "/api/audit-scenarios/slow-query"
test_endpoint "深度分页场景" "/api/audit-scenarios/deep-pagination"
test_endpoint "大页面场景" "/api/audit-scenarios/large-page-size"
test_endpoint "无分页场景" "/api/audit-scenarios/no-pagination"

echo "=========================================="
echo "  测试完成"
echo "=========================================="
echo ""
echo -e "${GREEN}所有测试已执行完成${NC}"
echo "请查看服务端日志了解 SQL Guard 拦截器的详细检测结果"
