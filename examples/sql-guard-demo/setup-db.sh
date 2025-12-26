#!/bin/bash

# ===================================================
# SQL Guard Demo - Database Setup Helper Script
# ===================================================

set -e

# Database configuration (from application.yml)
DB_HOST="192.168.220.202"
DB_PORT="3307"
DB_NAME="sqlguard_demo"
DB_USER="root"
DB_PASSWORD="root123"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}SQL Guard Demo - Database Setup${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "Database Host: ${YELLOW}${DB_HOST}:${DB_PORT}${NC}"
echo -e "Database Name: ${YELLOW}${DB_NAME}${NC}"
echo -e "Database User: ${YELLOW}${DB_USER}${NC}"
echo ""

# Check if mysql client is available
if ! command -v mysql &> /dev/null; then
    echo -e "${RED}❌ Error: mysql client not found${NC}"
    echo "Please install MySQL client first"
    exit 1
fi

# Test database connection
echo -e "${YELLOW}Testing database connection...${NC}"
if mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASSWORD}" -e "SELECT 1" &> /dev/null; then
    echo -e "${GREEN}✅ Database connection successful${NC}"
else
    echo -e "${RED}❌ Database connection failed${NC}"
    echo "Please check your database configuration:"
    echo "  - Host: ${DB_HOST}:${DB_PORT}"
    echo "  - User: ${DB_USER}"
    echo "  - Password: ${DB_PASSWORD}"
    exit 1
fi

# Execute setup script
echo ""
echo -e "${YELLOW}Executing database setup script...${NC}"
if mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASSWORD}" < setup-database.sql; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}✅ Database setup completed!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "You can now start the SQL Guard Demo application:"
    echo -e "  ${YELLOW}mvn spring-boot:run${NC}"
    echo ""
else
    echo ""
    echo -e "${RED}❌ Database setup failed${NC}"
    exit 1
fi
