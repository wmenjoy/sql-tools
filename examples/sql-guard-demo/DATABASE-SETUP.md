# SQL Guard Demo - Database Setup Guide

## 问题描述

启动 SQL Guard Demo 应用时遇到错误：

```
java.sql.SQLSyntaxErrorException: Unknown database 'sqlguard_demo'
```

## 解决方案

数据库 `sqlguard_demo` 尚未创建。使用以下方法之一设置数据库。

---

## 方法 1: 使用自动化脚本（推荐）

```bash
cd examples/sql-guard-demo
./setup-db.sh
```

脚本将自动：
1. ✅ 测试数据库连接
2. ✅ 创建数据库 `sqlguard_demo`
3. ✅ 创建表（user, order, product）
4. ✅ 插入测试数据
   - 100 个用户
   - 500 个订单
   - 50 个商品

---

## 方法 2: 手动执行 SQL 脚本

```bash
mysql -h192.168.220.202 -P3307 -uroot -proot123 < setup-database.sql
```

---

## 方法 3: 使用 MySQL 客户端

1. 连接到 MySQL：
   ```bash
   mysql -h192.168.220.202 -P3307 -uroot -proot123
   ```

2. 执行脚本：
   ```sql
   source setup-database.sql;
   ```

---

## 数据库配置

配置信息来自 `src/main/resources/application.yml`:

| 配置项 | 值 |
|--------|-----|
| **Host** | 192.168.220.202 |
| **Port** | 3307 |
| **Database** | sqlguard_demo |
| **Username** | root |
| **Password** | root123 |
| **JDBC URL** | jdbc:mysql://192.168.220.202:3307/sqlguard_demo?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true |

---

## 验证安装

```bash
mysql -h192.168.220.202 -P3307 -uroot -proot123 sqlguard_demo -e "
SHOW TABLES;
SELECT COUNT(*) FROM user;
SELECT COUNT(*) FROM \`order\`;
SELECT COUNT(*) FROM product;
"
```

**预期输出**:
```
Tables_in_sqlguard_demo
order
product
user

COUNT(*)
100

COUNT(*)
500

COUNT(*)
50
```

---

## 启动应用

数据库设置完成后，启动 SQL Guard Demo:

```bash
mvn spring-boot:run
```

应用将在 `http://localhost:8081` 启动。

---

## 数据库结构

### user 表
- 100 个测试用户
- 字段：id, username, email, status, deleted, create_time
- 索引：username, email, status, deleted

### order 表
- 500 个测试订单
- 字段：id, user_id, total_amount, status, order_time
- 索引：user_id, status, order_time

### product 表
- 50 个测试商品
- 字段：id, name, price, stock, category_id
- 索引：name, category_id

---

## 故障排查

### 连接失败

如果看到 "Database connection failed"：

1. **检查 MySQL 是否运行**：
   ```bash
   telnet 192.168.220.202 3307
   ```

2. **检查用户名/密码**：
   ```bash
   mysql -h192.168.220.202 -P3307 -uroot -proot123 -e "SELECT 1"
   ```

3. **检查防火墙**：
   确保端口 3307 可访问

### 脚本执行失败

如果 `setup-db.sh` 失败：

1. **检查脚本权限**：
   ```bash
   chmod +x setup-db.sh
   ```

2. **手动执行 SQL**：
   ```bash
   mysql -h192.168.220.202 -P3307 -uroot -proot123 < setup-database.sql
   ```

---

## 重置数据库

如果需要重置数据库：

```bash
./setup-db.sh  # 脚本会自动删除并重建所有表
```

或手动：

```sql
DROP DATABASE IF EXISTS sqlguard_demo;
CREATE DATABASE sqlguard_demo;
```

然后重新运行设置脚本。

---

## 文件说明

| 文件 | 说明 |
|------|------|
| `setup-database.sql` | 完整的数据库初始化 SQL 脚本 |
| `setup-db.sh` | 自动化数据库设置脚本 |
| `src/main/resources/db/init.sql` | 原始初始化脚本（不含 CREATE DATABASE） |
| `DATABASE-SETUP.md` | 本文档 |

---

## ✅ 完成

数据库设置完成后，你应该能够成功启动 SQL Guard Demo 应用并测试各种 SQL 安全规则。
