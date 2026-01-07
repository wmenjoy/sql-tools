#!/bin/bash

# ==========================================
# SQL Guard Demo - 综合测试脚本
# ==========================================
# 
# 测试计划:
# 1. 验证服务启动和健康检查
# 2. 测试所有违规端点，验证响应是否符合预期
# 3. 验证 audit.log 输出
# 4. 验证 validation.log 输出
# 5. 测试不同策略 (LOG/WARN/BLOCK)
# ==========================================

set -e

BASE_URL=${1:-"http://localhost:8081"}
LOG_DIR="logs"
AUDIT_LOG="$LOG_DIR/audit/audit.log"
VALIDATION_LOG="$LOG_DIR/sqlguard/validation.log"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# 测试计数器
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# 测试结果记录
declare -a TEST_RESULTS

# 记录测试结果
record_result() {
    local test_name=$1
    local status=$2
    local details=$3
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    if [ "$status" = "PASS" ]; then
        PASSED_TESTS=$((PASSED_TESTS + 1))
        TEST_RESULTS+=("${GREEN}✅ PASS${NC}: $test_name - $details")
    else
        FAILED_TESTS=$((FAILED_TESTS + 1))
        TEST_RESULTS+=("${RED}❌ FAIL${NC}: $test_name - $details")
    fi
}

# 打印分隔线
print_section() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
}

# 验证 JSON 响应字段
verify_json_field() {
    local json=$1
    local field=$2
    local expected=$3
    local actual=$(echo "$json" | python3 -c "import sys, json; print(json.load(sys.stdin).get('$field', ''))" 2>/dev/null)
    
    if [ "$actual" = "$expected" ]; then
        return 0
    else
        return 1
    fi
}

# ==========================================
# 测试 1: 服务健康检查
# ==========================================
test_health_check() {
    print_section "测试 1: 服务健康检查"
    
    echo "检查服务是否运行..."
    local response=$(curl -s "$BASE_URL/actuator/health" 2>/dev/null)
    local http_code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null)
    
    if [ "$http_code" = "200" ]; then
        local status=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('status', ''))" 2>/dev/null)
        if [ "$status" = "UP" ]; then
            record_result "服务健康检查" "PASS" "服务状态: UP"
            echo -e "${GREEN}✅ 服务运行正常${NC}"
        else
            record_result "服务健康检查" "FAIL" "服务状态: $status"
            echo -e "${RED}❌ 服务状态异常: $status${NC}"
        fi
    else
        record_result "服务健康检查" "FAIL" "HTTP 状态码: $http_code"
        echo -e "${RED}❌ 服务不可达, HTTP: $http_code${NC}"
        exit 1
    fi
}

# ==========================================
# 测试 2: 违规端点测试
# ==========================================
test_violation_endpoint() {
    local name=$1
    local endpoint=$2
    local expected_checker=$3
    local expected_risk=$4
    
    echo -e "${BLUE}测试: $name${NC}"
    echo "  端点: $endpoint"
    echo "  预期检查器: $expected_checker"
    echo "  预期风险级别: $expected_risk"
    
    local response=$(curl -s "$BASE_URL$endpoint")
    local http_code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$endpoint")
    
    if [ "$http_code" != "200" ]; then
        record_result "$name" "FAIL" "HTTP 状态码: $http_code"
        echo -e "  ${RED}❌ HTTP 失败: $http_code${NC}"
        return
    fi
    
    # 验证响应字段
    local actual_checker=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('checker', ''))" 2>/dev/null)
    local actual_risk=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('riskLevel', ''))" 2>/dev/null)
    local actual_status=$(echo "$response" | python3 -c "import sys, json; print(json.load(sys.stdin).get('status', ''))" 2>/dev/null)
    
    echo "  实际检查器: $actual_checker"
    echo "  实际风险级别: $actual_risk"
    echo "  执行状态: $actual_status"
    
    local test_passed=true
    local details=""
    
    if [ "$actual_checker" != "$expected_checker" ]; then
        test_passed=false
        details="检查器不匹配: 预期=$expected_checker, 实际=$actual_checker"
    elif [ "$actual_risk" != "$expected_risk" ]; then
        test_passed=false
        details="风险级别不匹配: 预期=$expected_risk, 实际=$actual_risk"
    else
        details="检查器=$actual_checker, 风险=$actual_risk"
    fi
    
    if [ "$test_passed" = true ]; then
        record_result "$name" "PASS" "$details"
        echo -e "  ${GREEN}✅ 验证通过${NC}"
    else
        record_result "$name" "FAIL" "$details"
        echo -e "  ${RED}❌ 验证失败: $details${NC}"
    fi
    echo ""
}

test_all_violations() {
    print_section "测试 2: 违规端点测试"
    
    echo -e "${YELLOW}测试所有违规端点，验证响应是否符合预期...${NC}"
    echo ""
    
    # 测试所有违规端点
    test_violation_endpoint "NoWhereClause" "/violations/no-where-clause" "NoWhereClauseChecker" "CRITICAL"
    test_violation_endpoint "DummyCondition" "/violations/dummy-condition" "DummyConditionChecker" "HIGH"
    test_violation_endpoint "BlacklistOnly" "/violations/blacklist-only" "BlacklistFieldChecker" "HIGH"
    test_violation_endpoint "WhitelistMissing" "/violations/whitelist-missing" "WhitelistFieldChecker" "HIGH"
    test_violation_endpoint "DeepPagination" "/violations/deep-pagination" "DeepPaginationChecker" "MEDIUM"
    test_violation_endpoint "LargePageSize" "/violations/large-page-size" "LargePageSizeChecker" "MEDIUM"
    test_violation_endpoint "MissingOrderBy" "/violations/missing-orderby" "MissingOrderByChecker" "LOW"
    test_violation_endpoint "NoPagination" "/violations/no-pagination" "NoPaginationChecker" "MEDIUM/CRITICAL"
    test_violation_endpoint "NoConditionPagination" "/violations/no-condition-pagination" "NoConditionPaginationChecker" "CRITICAL"
}

# ==========================================
# 测试 3: Audit Log 验证
# ==========================================
test_audit_log() {
    print_section "测试 3: Audit Log 验证"
    
    echo "检查 audit.log 文件..."
    echo "路径: $AUDIT_LOG"
    
    if [ -f "$AUDIT_LOG" ]; then
        local line_count=$(wc -l < "$AUDIT_LOG")
        local file_size=$(ls -lh "$AUDIT_LOG" | awk '{print $5}')
        
        echo "  文件存在: 是"
        echo "  行数: $line_count"
        echo "  大小: $file_size"
        
        if [ "$line_count" -gt 0 ]; then
            echo ""
            echo "最近 5 条审计记录:"
            echo "---"
            tail -5 "$AUDIT_LOG" | while read line; do
                # 尝试格式化 JSON
                echo "$line" | python3 -c "import sys, json; d=json.load(sys.stdin); print(f\"  SQL: {d.get('sql', 'N/A')[:50]}...\")" 2>/dev/null || echo "  $line"
            done
            echo "---"
            
            record_result "Audit Log 存在" "PASS" "行数=$line_count, 大小=$file_size"
            echo -e "${GREEN}✅ Audit Log 正常输出${NC}"
        else
            record_result "Audit Log 存在" "FAIL" "文件为空"
            echo -e "${RED}❌ Audit Log 为空${NC}"
        fi
    else
        record_result "Audit Log 存在" "FAIL" "文件不存在"
        echo -e "${RED}❌ Audit Log 文件不存在${NC}"
        echo "  请确保服务已启动并执行过 SQL 查询"
    fi
}

# ==========================================
# 测试 4: Validation Log 验证
# ==========================================
test_validation_log() {
    print_section "测试 4: Validation Log 验证"
    
    echo "检查 validation.log 文件..."
    echo "路径: $VALIDATION_LOG"
    
    if [ -f "$VALIDATION_LOG" ]; then
        local line_count=$(wc -l < "$VALIDATION_LOG")
        local file_size=$(ls -lh "$VALIDATION_LOG" | awk '{print $5}')
        
        echo "  文件存在: 是"
        echo "  行数: $line_count"
        echo "  大小: $file_size"
        
        if [ "$line_count" -gt 0 ]; then
            echo ""
            echo "最近的违规检测记录:"
            echo "---"
            grep -i "violation\|CRITICAL\|HIGH\|MEDIUM\|LOW" "$VALIDATION_LOG" 2>/dev/null | tail -10 || echo "  无违规记录"
            echo "---"
            
            # 检查是否有违规记录
            local violation_count=$(grep -c "SQL Safety Violation" "$VALIDATION_LOG" 2>/dev/null || echo "0")
            echo ""
            echo "违规检测总数: $violation_count"
            
            record_result "Validation Log 存在" "PASS" "行数=$line_count, 违规数=$violation_count"
            echo -e "${GREEN}✅ Validation Log 正常输出${NC}"
        else
            record_result "Validation Log 存在" "FAIL" "文件为空"
            echo -e "${RED}❌ Validation Log 为空${NC}"
        fi
    else
        record_result "Validation Log 存在" "FAIL" "文件不存在"
        echo -e "${RED}❌ Validation Log 文件不存在${NC}"
    fi
}

# ==========================================
# 测试 5: 审计场景测试
# ==========================================
test_audit_scenarios() {
    print_section "测试 5: 审计场景测试"
    
    echo -e "${YELLOW}测试审计场景端点...${NC}"
    echo ""
    
    local scenarios=(
        "/api/audit-scenarios/deep-pagination:深度分页"
        "/api/audit-scenarios/large-page-size:大页面"
        "/api/audit-scenarios/no-pagination:无分页"
        "/api/audit-scenarios/missing-orderby:缺少OrderBy"
        "/api/audit-scenarios/no-condition-pagination:无条件分页"
        "/api/audit-scenarios/blacklist-field:黑名单字段"
        "/api/audit-scenarios/dummy-condition:Dummy条件"
        "/api/audit-scenarios/no-where:无WHERE"
    )
    
    for scenario in "${scenarios[@]}"; do
        local endpoint="${scenario%%:*}"
        local name="${scenario##*:}"
        
        echo -e "${BLUE}测试: $name${NC}"
        echo "  端点: $endpoint"
        
        local http_code=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL$endpoint" 2>/dev/null)
        
        if [ "$http_code" = "200" ]; then
            record_result "审计场景: $name" "PASS" "HTTP 200"
            echo -e "  ${GREEN}✅ 成功${NC}"
        else
            record_result "审计场景: $name" "FAIL" "HTTP $http_code"
            echo -e "  ${RED}❌ 失败: HTTP $http_code${NC}"
        fi
    done
}

# ==========================================
# 打印测试报告
# ==========================================
print_report() {
    print_section "测试报告"
    
    echo "测试结果汇总:"
    echo "  总测试数: $TOTAL_TESTS"
    echo -e "  ${GREEN}通过: $PASSED_TESTS${NC}"
    echo -e "  ${RED}失败: $FAILED_TESTS${NC}"
    echo ""
    
    echo "详细结果:"
    for result in "${TEST_RESULTS[@]}"; do
        echo -e "  $result"
    done
    echo ""
    
    if [ $FAILED_TESTS -eq 0 ]; then
        echo -e "${GREEN}========================================${NC}"
        echo -e "${GREEN}  所有测试通过！${NC}"
        echo -e "${GREEN}========================================${NC}"
    else
        echo -e "${RED}========================================${NC}"
        echo -e "${RED}  有 $FAILED_TESTS 个测试失败${NC}"
        echo -e "${RED}========================================${NC}"
    fi
}

# ==========================================
# 主函数
# ==========================================
main() {
    echo ""
    echo -e "${CYAN}===========================================${NC}"
    echo -e "${CYAN}  SQL Guard Demo - 综合测试${NC}"
    echo -e "${CYAN}===========================================${NC}"
    echo ""
    echo "目标服务: $BASE_URL"
    echo "日志目录: $LOG_DIR"
    echo ""
    
    # 切换到项目目录
    cd "$(dirname "$0")/.."
    
    # 执行测试
    test_health_check
    test_all_violations
    test_audit_log
    test_validation_log
    test_audit_scenarios
    
    # 打印报告
    print_report
}

# 执行主函数
main
