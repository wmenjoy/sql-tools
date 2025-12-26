#!/bin/bash

# ===================================================
# SQL Guard Demo - Logging Configuration Test
# ===================================================

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}SQL Guard Demo - 日志配置测试${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""

# Check log configuration files
echo -e "${YELLOW}1. 检查日志配置文件...${NC}"
echo ""

if [ -f "src/main/resources/logback-spring.xml" ]; then
    echo -e "  ${GREEN}✅${NC} logback-spring.xml 存在"
else
    echo -e "  ${RED}❌${NC} logback-spring.xml 不存在"
fi

echo ""

# Check log directories
echo -e "${YELLOW}2. 检查日志目录...${NC}"
echo ""

for dir in logs logs/sql logs/audit logs/sqlguard logs/archive; do
    if [ -d "$dir" ]; then
        echo -e "  ${GREEN}✅${NC} $dir 存在"
    else
        echo -e "  ${BLUE}ℹ️${NC}  $dir 不存在 (启动时自动创建)"
    fi
done

echo ""

# Check application.yml logging config
echo -e "${YELLOW}3. 检查 application.yml 日志配置...${NC}"
echo ""

if grep -q "com.footstone.sqlguard.demo.mapper: DEBUG" src/main/resources/application.yml; then
    echo -e "  ${GREEN}✅${NC} MyBatis Mapper 日志级别已配置为 DEBUG"
else
    echo -e "  ${RED}❌${NC} MyBatis Mapper 日志级别未配置"
fi

if grep -q "org.apache.ibatis: DEBUG" src/main/resources/application.yml; then
    echo -e "  ${GREEN}✅${NC} MyBatis 内部日志级别已配置为 DEBUG"
else
    echo -e "  ${RED}❌${NC} MyBatis 内部日志级别未配置"
fi

if grep -q "com.baomidou.mybatisplus: DEBUG" src/main/resources/application.yml; then
    echo -e "  ${GREEN}✅${NC} MyBatis-Plus 日志级别已配置为 DEBUG"
else
    echo -e "  ${YELLOW}⚠️${NC}  MyBatis-Plus 日志级别未配置"
fi

echo ""

# Display log configuration summary
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}日志配置总结${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${BLUE}日志文件位置:${NC}"
echo "  • 应用日志:         logs/application.log"
echo "  • SQL 语句日志:     logs/sql/sql.log         ✅"
echo "  • Audit 审计日志:   logs/audit/audit.log     ✅"
echo "  • SQL Guard 日志:   logs/sqlguard/validation.log"
echo ""
echo -e "${BLUE}日志级别:${NC}"
echo "  • SQL Guard:        DEBUG"
echo "  • MyBatis Mapper:   DEBUG (SQL 语句输出)     ✅"
echo "  • MyBatis 内部:     DEBUG                    ✅"
echo "  • MyBatis-Plus:     DEBUG                    ✅"
echo ""
echo -e "${BLUE}日志特性:${NC}"
echo "  • 异步日志输出 (高性能)"
echo "  • 自动滚动归档"
echo "  • 独立的日志通道"
echo "  • 彩色控制台输出"
echo ""
echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}配置完成! 🎉${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "${YELLOW}下一步:${NC}"
echo "  1. 启动应用: mvn spring-boot:run"
echo "  2. 访问 API: curl http://localhost:8081/api/users"
echo "  3. 查看 SQL 日志: tail -f logs/sql/sql.log"
echo "  4. 查看 Audit 日志: tail -f logs/audit/audit.log"
echo ""
echo -e "详细文档: ${BLUE}LOGGING-SETUP.md${NC}"
echo ""
