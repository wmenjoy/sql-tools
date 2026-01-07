#!/bin/bash

# SQL Guard Demo - Curl 示例命令
# 这个文件包含所有可用的 curl 命令示例
# 可以直接复制粘贴执行，或者 source 这个文件后调用函数

BASE_URL=${BASE_URL:-"http://localhost:8080"}

echo "=========================================="
echo "  SQL Guard Demo - Curl 示例命令"
echo "=========================================="
echo ""
echo "目标服务: $BASE_URL"
echo ""

# ==========================================
# 基础命令
# ==========================================

echo "# 1. 健康检查"
echo "curl -s $BASE_URL/actuator/health | jq"
echo ""

echo "# 2. API 文档"
echo "curl -s $BASE_URL/ | jq"
echo ""

# ==========================================
# 违规测试命令
# ==========================================

echo "# 3. NoWhereClause 违规 (CRITICAL)"
echo "# DELETE 语句没有 WHERE 条件"
echo "curl -s $BASE_URL/violations/no-where-clause | jq"
echo ""

echo "# 4. DummyCondition 违规 (HIGH)"
echo "# WHERE 1=1 无效条件"
echo "curl -s $BASE_URL/violations/dummy-condition | jq"
echo ""

echo "# 5. BlacklistOnly 违规 (HIGH)"
echo "# 只使用黑名单字段 (status) 作为条件"
echo "curl -s $BASE_URL/violations/blacklist-only | jq"
echo ""

echo "# 6. WhitelistMissing 违规 (HIGH)"
echo "# 缺少白名单字段"
echo "curl -s $BASE_URL/violations/whitelist-missing | jq"
echo ""

echo "# 7. DeepPagination 违规 (MEDIUM)"
echo "# OFFSET 值过大 (深度分页)"
echo "curl -s $BASE_URL/violations/deep-pagination | jq"
echo ""

echo "# 8. LargePageSize 违规 (MEDIUM)"
echo "# LIMIT 值过大 (大页面)"
echo "curl -s $BASE_URL/violations/large-page-size | jq"
echo ""

echo "# 9. MissingOrderBy 违规 (LOW)"
echo "# 分页查询没有 ORDER BY"
echo "curl -s $BASE_URL/violations/missing-orderby | jq"
echo ""

echo "# 10. NoPagination 违规"
echo "# SELECT 没有分页限制"
echo "curl -s $BASE_URL/violations/no-pagination | jq"
echo ""

echo "# 11. NoConditionPagination 违规 (CRITICAL)"
echo "# LIMIT 但没有 WHERE 条件"
echo "curl -s $BASE_URL/violations/no-condition-pagination | jq"
echo ""

# ==========================================
# 管理命令
# ==========================================

echo "# 12. 查看违规日志"
echo "curl -s $BASE_URL/violations/logs | jq"
echo ""

echo "# 13. 切换策略 (LOG/WARN/BLOCK)"
echo "curl -s -X POST $BASE_URL/config/strategy/LOG | jq"
echo "curl -s -X POST $BASE_URL/config/strategy/WARN | jq"
echo "curl -s -X POST $BASE_URL/config/strategy/BLOCK | jq"
echo ""

# ==========================================
# 审计场景命令
# ==========================================

echo "# 14. 慢查询场景"
echo "curl -s $BASE_URL/api/audit-scenarios/slow-query | jq"
echo ""

echo "# 15. 深度分页场景"
echo "curl -s $BASE_URL/api/audit-scenarios/deep-pagination | jq"
echo ""

echo "# 16. 大页面场景"
echo "curl -s $BASE_URL/api/audit-scenarios/large-page-size | jq"
echo ""

echo "# 17. 无分页场景"
echo "curl -s $BASE_URL/api/audit-scenarios/no-pagination | jq"
echo ""

echo "# 18. 缺少 ORDER BY 场景"
echo "curl -s $BASE_URL/api/audit-scenarios/missing-orderby | jq"
echo ""

echo "# 19. 无条件分页场景"
echo "curl -s $BASE_URL/api/audit-scenarios/no-condition-pagination | jq"
echo ""

echo "# 20. 黑名单字段场景"
echo "curl -s $BASE_URL/api/audit-scenarios/blacklist-field | jq"
echo ""

echo "# 21. Dummy 条件场景"
echo "curl -s $BASE_URL/api/audit-scenarios/dummy-condition | jq"
echo ""

echo "# 22. 无 WHERE 场景"
echo "curl -s $BASE_URL/api/audit-scenarios/no-where | jq"
echo ""

# ==========================================
# 负载测试命令
# ==========================================

echo "# 23. 启动负载生成器 (5分钟)"
echo "curl -s -X POST $BASE_URL/api/load-generator/start | jq"
echo ""

echo "# 24. 启动负载生成器 (自定义时长)"
echo "curl -s -X POST $BASE_URL/api/load-generator/start/10 | jq"
echo ""

echo "# 25. 停止负载生成器"
echo "curl -s -X POST $BASE_URL/api/load-generator/stop | jq"
echo ""

echo "# 26. 查看负载生成器状态"
echo "curl -s $BASE_URL/api/load-generator/status | jq"
echo ""

echo "=========================================="
echo "  使用说明"
echo "=========================================="
echo ""
echo "1. 启动服务:"
echo "   ./scripts/start-demo.sh"
echo ""
echo "2. 执行测试:"
echo "   ./scripts/test-violations.sh"
echo ""
echo "3. 或者直接复制上面的 curl 命令执行"
echo ""
echo "4. 观察服务端日志查看 SQL Guard 拦截器的检测结果"
