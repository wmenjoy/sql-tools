# SQL Guard Demo - 日志配置说明

## ✅ 配置完成

SQL Guard Demo 已完成 Logback 日志配置，确保 **SQL 语句**和 **Audit 审计日志**输出到专门文件。

---

## 📁 日志文件说明

| 日志文件 | 路径 | 内容 |
|---------|------|------|
| **SQL 语句日志** ✅ | `logs/sql/sql.log` | 所有执行的 SQL 语句、参数、执行结果 |
| **Audit 审计日志** ✅ | `logs/audit/audit.log` | SQL 安全审计事件（JSON 格式） |
| SQL Guard 验证日志 | `logs/sqlguard/validation.log` | 规则检查详情、违规检测 |
| 应用主日志 | `logs/application.log` | 应用启动、业务逻辑、异常信息 |

---

## 🎯 核心功能

### 1. SQL 语句日志（logs/sql/sql.log）

**输出示例**:
```
2025-12-26 16:30:15.123 | ==>  Preparing: SELECT * FROM user WHERE id = ?
2025-12-26 16:30:15.124 | ==> Parameters: 1(Long)
2025-12-26 16:30:15.135 | <==      Total: 1
```

**配置的日志器**:
- `com.footstone.sqlguard.demo.mapper` - MyBatis Mapper SQL
- `org.apache.ibatis` - MyBatis 内部 SQL
- `com.baomidou.mybatisplus` - MyBatis-Plus SQL

**日志级别**: DEBUG
**滚动策略**: 100MB/文件，保留 30 天

### 2. Audit 审计日志（logs/audit/audit.log）

**输出示例**:
```json
{"sql":"SELECT * FROM user","sqlType":"SELECT","executionTimeMs":150,"rowsAffected":100,"riskLevel":"MEDIUM","violations":[{"rule":"NO_PAGINATION","message":"Missing LIMIT clause"}]}
```

**日志级别**: INFO
**滚动策略**: 每小时滚动，100MB/文件，保留 30 天

---

## ⚙️ 配置文件

### logback-spring.xml

位置: `src/main/resources/logback-spring.xml`

**关键配置**:

```xml
<!-- SQL 语句日志 -->
<logger name="com.footstone.sqlguard.demo.mapper" level="DEBUG" additivity="false">
    <appender-ref ref="ASYNC_SQL_FILE"/>
    <appender-ref ref="CONSOLE"/>
</logger>

<logger name="org.apache.ibatis" level="DEBUG" additivity="false">
    <appender-ref ref="ASYNC_SQL_FILE"/>
</logger>

<!-- Audit 审计日志 -->
<logger name="com.footstone.sqlguard.audit.AUDIT" level="INFO" additivity="false">
    <appender-ref ref="ASYNC_AUDIT_FILE"/>
</logger>
```

### application.yml

位置: `src/main/resources/application.yml`

```yaml
logging:
  file:
    path: logs
  level:
    com.footstone.sqlguard.demo.mapper: DEBUG  # MyBatis SQL
    org.apache.ibatis: DEBUG                   # MyBatis 内部
    com.baomidou.mybatisplus: DEBUG            # MyBatis-Plus
    com.footstone.sqlguard: DEBUG              # SQL Guard
```

---

## 🚀 快速验证

### 1. 启动应用

```bash
cd examples/sql-guard-demo
mvn spring-boot:run
```

### 2. 触发 SQL 执行

```bash
# 查询用户
curl http://localhost:8081/api/users

# 查询订单
curl http://localhost:8081/api/orders
```

### 3. 查看日志

**实时查看 SQL 日志**:
```bash
tail -f logs/sql/sql.log
```

**实时查看 Audit 日志**:
```bash
# 原始输出
tail -f logs/audit/audit.log

# JSON 格式化输出（需要 jq）
tail -f logs/audit/audit.log | jq '.'

# 筛选高风险 SQL
tail -f logs/audit/audit.log | jq 'select(.riskLevel == "HIGH" or .riskLevel == "CRITICAL")'
```

**查看 SQL Guard 验证日志**:
```bash
tail -f logs/sqlguard/validation.log
```

---

## 📊 日志级别说明

| Logger | 级别 | 说明 |
|--------|------|------|
| `com.footstone.sqlguard.demo.mapper` | **DEBUG** | ✅ MyBatis Mapper SQL 输出 |
| `org.apache.ibatis` | **DEBUG** | ✅ MyBatis 内部 SQL |
| `com.baomidou.mybatisplus` | **DEBUG** | ✅ MyBatis-Plus SQL |
| `com.footstone.sqlguard` | **DEBUG** | SQL Guard 验证详情 |
| `com.footstone.sqlguard.audit.AUDIT` | **INFO** | ✅ Audit 审计事件 |

---

## 🔧 常用命令

### 查看最近的 SQL

```bash
tail -50 logs/sql/sql.log
```

### 统计 SQL 类型

```bash
grep "Preparing:" logs/sql/sql.log | awk '{print $5}' | sort | uniq -c
```

### 查找慢查询（> 1000ms）

```bash
jq 'select(.executionTimeMs > 1000)' logs/audit/audit.log
```

### 统计违规类型

```bash
jq -r '.violations[].rule' logs/audit/audit.log | sort | uniq -c
```

### 清理旧日志

```bash
# 删除 7 天前的归档日志
find logs/archive -name "*.log.gz" -mtime +7 -delete
```

---

## 🎯 特性

- ✅ **异步日志输出** - 使用 AsyncAppender 提高性能
- ✅ **独立日志通道** - SQL、Audit、应用日志互不干扰
- ✅ **自动滚动归档** - 按日期和大小自动滚动，压缩存储
- ✅ **彩色控制台输出** - 开发环境下更易读
- ✅ **JSON 格式审计** - Audit 日志为纯 JSON，便于解析和分析

---

## 📝 文件清单

```
examples/sql-guard-demo/
├── src/main/resources/
│   ├── logback-spring.xml         # Logback 配置 ✅
│   └── application.yml             # 日志级别配置 ✅
├── logs/                           # 日志目录
│   ├── sql/sql.log                # SQL 语句日志 ✅
│   ├── audit/audit.log            # Audit 审计日志 ✅
│   ├── sqlguard/validation.log    # SQL Guard 验证日志
│   └── application.log            # 应用主日志
├── test-logging.sh                # 日志配置测试脚本
└── LOGGING-SETUP.md              # 本文档
```

---

## ✅ 验证清单

运行测试脚本验证配置：

```bash
./test-logging.sh
```

**预期输出**:
```
✅ logback-spring.xml 存在
✅ MyBatis Mapper 日志级别已配置为 DEBUG
✅ MyBatis 内部日志级别已配置为 DEBUG
✅ MyBatis-Plus 日志级别已配置为 DEBUG
✅ 配置完成! 🎉
```

---

**配置完成！** 🎉

现在所有 SQL 语句和 Audit 日志都会输出到专门的文件中。
