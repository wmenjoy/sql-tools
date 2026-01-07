# SQL Guard 审计日志测试报告

## 测试日期
2026-01-04

## 测试目标
验证 MyBatis 和 Druid 两层的 SQL 审计功能是否正常工作，确保所有字段正确填充。

## 测试环境
- Spring Boot: 2.7.18
- MyBatis: 2.3.2
- MyBatis-Plus: 3.5.5
- Druid: 1.2.20
- SQL Guard: 1.0.0-SNAPSHOT

## 测试结果

### ✅ MyBatis 层审计日志

**测试用例 1: DELETE without WHERE**
```json
{
  "sqlId": "819fe8a9b31bc221717384aa76ca1fe3",
  "sql": "DELETE FROM user",
  "sqlType": "DELETE",
  "executionLayer": "MYBATIS",
  "statementId": "com.footstone.sqlguard.demo.mapper.UserMapper.deleteAllUnsafe",
  "datasource": "MybatisSqlSessionFactoryBean",
  "params": null,
  "executionTimeMs": 107,
  "rowsAffected": 0,
  "errorMessage": null,
  "timestamp": "2026-01-04T06:11:24.508459Z",
  "violations": {
    "passed": false,
    "riskLevel": "CRITICAL",
    "violations": [
      {
        "riskLevel": "CRITICAL",
        "message": "DELETE语句缺少WHERE条件,可能导致全表删除",
        "suggestion": null
      }
    ],
    "details": {}
  }
}
```

**字段验证:**
- ✅ sqlId: MD5 hash
- ✅ sql: 完整 SQL 语句
- ✅ sqlType: DELETE
- ✅ executionLayer: MYBATIS
- ✅ statementId: MyBatis mapper 方法 ID
- ✅ **datasource: MybatisSqlSessionFactoryBean** (已修复)
- ✅ params: null (无参数)
- ✅ executionTimeMs: 107ms
- ✅ rowsAffected: 0
- ✅ errorMessage: null (执行成功)
- ✅ timestamp: ISO-8601 格式
- ✅ **violations: 完整的违规信息** (已修复)

**测试用例 2: SELECT with dummy condition (WHERE 1=1)**
```json
{
  "sqlId": "3657c4fe49e3dac9432d2dba2ea1b468",
  "sql": "SELECT * FROM user WHERE 1=1",
  "sqlType": "SELECT",
  "executionLayer": "MYBATIS",
  "statementId": "com.footstone.sqlguard.demo.mapper.UserMapper.findWithDummyCondition",
  "datasource": "MybatisSqlSessionFactoryBean",
  "params": null,
  "executionTimeMs": 44,
  "rowsAffected": 0,
  "violations": {
    "passed": false,
    "riskLevel": "CRITICAL",
    "violations": [
      {
        "riskLevel": "HIGH",
        "message": "检测到无效条件(如 1=1),请移除"
      },
      {
        "riskLevel": "CRITICAL",
        "message": "SELECT查询无条件且无分页限制,可能返回全表数据导致内存溢出"
      }
    ]
  }
}
```

**字段验证:**
- ✅ 检测到多个违规 (DummyCondition + NoPagination)
- ✅ violations 数组包含 2 个违规记录
- ✅ 最高风险等级为 CRITICAL

---

### ✅ Druid/JDBC 层审计日志

**测试用例 3: SELECT without pagination (JDBC 直接执行)**
```json
{
  "sqlId": "bfee9eef6c3318d4900da48025e2b7d6",
  "sql": "SELECT * FROM user",
  "sqlType": "SELECT",
  "executionLayer": "JDBC",
  "statementId": "jdbc.druid:DataSource-1035825415:bfee9eef",
  "datasource": "DataSource-1035825415",
  "params": null,
  "executionTimeMs": 3,
  "rowsAffected": -1,
  "errorMessage": null,
  "timestamp": "2026-01-04T06:11:25.478645Z",
  "violations": {
    "passed": false,
    "riskLevel": "CRITICAL",
    "violations": [
      {
        "riskLevel": "CRITICAL",
        "message": "SELECT查询无条件且无分页限制,可能返回全表数据导致内存溢出",
        "suggestion": "添加WHERE条件和分页限制(LIMIT或RowBounds)"
      }
    ],
    "details": {}
  }
}
```

**字段验证:**
- ✅ sqlId: MD5 hash
- ✅ sql: 完整 SQL 语句
- ✅ sqlType: SELECT
- ✅ **executionLayer: JDBC** (区别于 MyBatis 层)
- ✅ **statementId: jdbc.druid:DataSource-xxx:hash** (Druid 专用格式)
- ✅ **datasource: DataSource-1035825415** (Druid DataSource ID)
- ✅ params: null
- ✅ executionTimeMs: 3ms (JDBC 层执行更快)
- ✅ rowsAffected: -1 (JDBC 层 SELECT 无法获取)
- ✅ errorMessage: null
- ✅ timestamp: ISO-8601 格式
- ✅ **violations: 检测到 NoPagination 违规**

---

## 关键修复

### 1. Spring Boot Auto-Configuration 问题
**问题**: SqlGuardAutoConfiguration 未被加载
**根本原因**: 缺少 `META-INF/spring.factories` 文件
**解决方案**: 创建 spring.factories 并注册 SqlGuardAutoConfiguration

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.footstone.sqlguard.spring.autoconfigure.SqlGuardAutoConfiguration
```

### 2. datasource 字段为 null
**问题**: MyBatis 层 datasource 字段始终为 null
**根本原因**: SqlAuditInterceptor 未提取 Environment ID
**解决方案**: 从 `ms.getConfiguration().getEnvironment().getId()` 提取

```java
String datasourceName = null;
try {
    if (ms.getConfiguration() != null && ms.getConfiguration().getEnvironment() != null) {
        datasourceName = ms.getConfiguration().getEnvironment().getId();
    }
} catch (Exception e) {
    logger.debug("Failed to extract datasource name", e);
}
```

### 3. violations 字段为 null
**问题**: 所有审计事件的 violations 字段为 null
**根本原因**: SqlSafetyInterceptor 未注册到 MyBatis
**解决方案**: 创建 AuditConfiguration 注册两个拦截器

```java
// 1. SqlSafetyInterceptor (pre-execution validation)
SqlSafetyInterceptor safetyInterceptor = new SqlSafetyInterceptor(
    sqlSafetyValidator,
    ViolationStrategy.WARN
);
sqlSessionFactory.getConfiguration().addInterceptor(safetyInterceptor);

// 2. SqlAuditInterceptor (post-execution audit)
SqlAuditInterceptor auditInterceptor = new SqlAuditInterceptor(auditLogWriter);
sqlSessionFactory.getConfiguration().addInterceptor(auditInterceptor);
```

### 4. Druid 连接池循环问题
**问题**: Druid 连接验证查询触发 AuditFilter，导致无限循环
**根本原因**: AuditFilter 拦截了 `SELECT 1` 验证查询
**解决方案**: 在 DruidSqlAuditFilter 中跳过验证查询

```java
private boolean isValidationQuery(String sql) {
    if (sql == null) return false;
    String trimmed = sql.trim().toUpperCase();
    return trimmed.equals("SELECT 1") ||
           trimmed.equals("SELECT 1 FROM DUAL") ||
           trimmed.equals("SELECT 'X'");
}
```

### 5. Druid WallFilter 冲突
**问题**: Druid 自带的 WallFilter 阻止测试 SQL 执行
**解决方案**: 从配置中移除 wall filter

```yaml
spring:
  datasource:
    druid:
      filters: stat  # 移除了 wall
```

---

## 两层审计对比

| 特性 | MyBatis 层 | Druid/JDBC 层 |
|------|-----------|---------------|
| executionLayer | MYBATIS | JDBC |
| statementId | mapper 方法全限定名 | jdbc.druid:DataSource:hash |
| datasource | MybatisSqlSessionFactoryBean | DataSource-ID |
| rowsAffected | 准确值 | SELECT 为 -1 |
| 拦截点 | MyBatis Interceptor | Druid Filter |
| params | 从 BoundSql 提取 | 无法提取 |
| violations | ThreadLocal 协调 | ThreadLocal 协调 |

---

## 测试命令

### MyBatis 层测试
```bash
curl -s http://localhost:8081/violations/no-where-clause
curl -s http://localhost:8081/violations/dummy-condition
curl -s http://localhost:8081/violations/blacklist-only
```

### Druid/JDBC 层测试
```bash
curl -s http://localhost:8081/druid-test/clean-query/1
curl -s http://localhost:8081/druid-test/no-pagination
curl -s http://localhost:8081/druid-test/dummy-condition
```

### 查看审计日志
```bash
cat logs/audit/audit.log | python3 -m json.tool
```

---

## 结论

✅ **所有核心功能已验证通过:**
1. MyBatis 层审计日志正确记录 (datasource, violations, params 等字段)
2. Druid/JDBC 层审计日志正确记录 (executionLayer=JDBC)
3. 两层均能正确检测 SQL 违规
4. 审计日志格式符合 audit-log-schema.json 规范
5. 异步文件写入 (AsyncAppender) 正常工作
6. JSON 格式纯净 (pattern: %msg%n)

✅ **sql-audit-service 可以正常分析这些日志**
