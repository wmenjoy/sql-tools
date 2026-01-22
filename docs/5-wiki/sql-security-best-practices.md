---
type: Knowledge Base
component: Wiki
version: 1.0.0
created: 2026-01-22
updated: 2026-01-22
status: Active
maintainer: SQL Safety Guard Team
language: zh-CN
---

# SQL 安全最佳实践

## 概述

本文档汇总了 SQL 安全领域的最佳实践，涵盖 SQL 注入防护、权限控制、审计策略等关键安全知识，帮助开发者和运维人员构建更安全的数据库应用系统。

## 目录

- [SQL 注入防护](#sql-注入防护)
- [权限控制策略](#权限控制策略)
- [审计与监控](#审计与监控)
- [敏感数据保护](#敏感数据保护)
- [安全配置建议](#安全配置建议)

---

## SQL 注入防护

### 什么是 SQL 注入

SQL 注入是一种常见的 Web 安全漏洞，攻击者通过在应用程序的输入字段中插入恶意 SQL 代码，从而操纵数据库执行非预期的操作。

**典型攻击示例**：
```sql
-- 原始查询
SELECT * FROM users WHERE username = '$input' AND password = '$password'

-- 攻击输入: username = "admin' --"
SELECT * FROM users WHERE username = 'admin' --' AND password = ''
-- 注释掉了密码验证，直接以 admin 身份登录
```

### 防护措施

#### 1. 使用参数化查询（推荐）

**Java (JDBC)**：
```java
// ❌ 错误：字符串拼接
String sql = "SELECT * FROM users WHERE username = '" + username + "'";
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery(sql);

// ✅ 正确：使用 PreparedStatement
String sql = "SELECT * FROM users WHERE username = ?";
PreparedStatement pstmt = conn.prepareStatement(sql);
pstmt.setString(1, username);
ResultSet rs = pstmt.executeQuery();
```

**MyBatis**：
```xml
<!-- ❌ 错误：使用 ${} 直接拼接 -->
<select id="getUser" resultType="User">
    SELECT * FROM users WHERE username = '${username}'
</select>

<!-- ✅ 正确：使用 #{} 参数化 -->
<select id="getUser" resultType="User">
    SELECT * FROM users WHERE username = #{username}
</select>
```

**Python (SQLAlchemy)**：
```python
# ❌ 错误：字符串拼接
query = f"SELECT * FROM users WHERE username = '{username}'"
result = session.execute(query)

# ✅ 正确：使用参数绑定
query = "SELECT * FROM users WHERE username = :username"
result = session.execute(query, {"username": username})
```

#### 2. 输入验证与过滤

**白名单验证**：
```java
// 验证表名（只允许特定表）
private static final Set<String> ALLOWED_TABLES = Set.of("users", "orders", "products");

public void queryTable(String tableName) {
    if (!ALLOWED_TABLES.contains(tableName)) {
        throw new IllegalArgumentException("Invalid table name");
    }
    // 安全地使用 tableName
}
```

**类型验证**：
```java
// 验证数字类型
public User getUserById(String idStr) {
    try {
        int id = Integer.parseInt(idStr);
        return userRepository.findById(id);
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid user ID");
    }
}
```

#### 3. 最小权限原则

数据库用户应该只拥有完成任务所需的最小权限：

```sql
-- ❌ 错误：应用使用 root 或 DBA 账号
GRANT ALL PRIVILEGES ON *.* TO 'app_user'@'%';

-- ✅ 正确：只授予必要权限
GRANT SELECT, INSERT, UPDATE ON mydb.users TO 'app_user'@'%';
GRANT SELECT, INSERT ON mydb.orders TO 'app_user'@'%';
-- 不授予 DELETE、DROP、CREATE 等危险权限
```

#### 4. 使用 ORM 框架的安全特性

现代 ORM 框架通常内置了 SQL 注入防护：

**JPA/Hibernate**：
```java
// ✅ 使用 JPQL 参数绑定
String jpql = "SELECT u FROM User u WHERE u.username = :username";
TypedQuery<User> query = em.createQuery(jpql, User.class);
query.setParameter("username", username);
List<User> users = query.getResultList();
```

**MyBatis-Plus**：
```java
// ✅ 使用 QueryWrapper
QueryWrapper<User> wrapper = new QueryWrapper<>();
wrapper.eq("username", username);
List<User> users = userMapper.selectList(wrapper);
```

---

## 权限控制策略

### 数据库层面权限控制

#### 1. 用户权限分离

为不同的应用场景创建不同的数据库用户：

```sql
-- 只读用户（用于报表、查询）
CREATE USER 'readonly_user'@'%' IDENTIFIED BY 'strong_password';
GRANT SELECT ON mydb.* TO 'readonly_user'@'%';

-- 应用用户（用于业务操作）
CREATE USER 'app_user'@'%' IDENTIFIED BY 'strong_password';
GRANT SELECT, INSERT, UPDATE ON mydb.* TO 'app_user'@'%';

-- 管理用户（用于运维）
CREATE USER 'admin_user'@'localhost' IDENTIFIED BY 'strong_password';
GRANT ALL PRIVILEGES ON mydb.* TO 'admin_user'@'localhost';
```

#### 2. 表级和列级权限

```sql
-- 限制敏感列的访问
GRANT SELECT (id, username, email) ON mydb.users TO 'app_user'@'%';
-- 不授予 password_hash 列的访问权限

-- 限制敏感表的访问
REVOKE ALL PRIVILEGES ON mydb.audit_logs FROM 'app_user'@'%';
```

#### 3. 行级安全（Row-Level Security）

**PostgreSQL 示例**：
```sql
-- 启用行级安全
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;

-- 创建策略：用户只能看到自己的订单
CREATE POLICY user_orders_policy ON orders
    FOR SELECT
    USING (user_id = current_user_id());
```

### 应用层面权限控制

#### 1. 基于角色的访问控制（RBAC）

```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(Long userId) {
    userRepository.deleteById(userId);
}

@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public List<Order> getAllOrders() {
    return orderRepository.findAll();
}

@PreAuthorize("hasRole('USER') and #userId == authentication.principal.id")
public User getUser(Long userId) {
    return userRepository.findById(userId);
}
```

#### 2. 数据访问审计

记录所有敏感操作：

```java
@Aspect
@Component
public class DataAccessAuditAspect {

    @Around("@annotation(Audited)")
    public Object auditDataAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        String operation = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        // 记录操作前状态
        auditLogger.info("User {} is performing {} with args {}", username, operation, args);

        try {
            Object result = joinPoint.proceed();
            auditLogger.info("Operation {} completed successfully", operation);
            return result;
        } catch (Exception e) {
            auditLogger.error("Operation {} failed: {}", operation, e.getMessage());
            throw e;
        }
    }
}
```

---

## 审计与监控

### 审计日志策略

#### 1. 记录关键操作

应该记录的操作类型：
- 用户登录/登出
- 权限变更
- 敏感数据访问（用户信息、财务数据）
- 数据修改操作（INSERT、UPDATE、DELETE）
- 配置变更
- 异常的 SQL 查询（如全表扫描、无 WHERE 条件的 DELETE）

#### 2. 审计日志内容

完整的审计日志应包含：
```json
{
  "timestamp": "2026-01-22T10:30:45.123Z",
  "user": "john.doe",
  "ip_address": "192.168.1.100",
  "operation": "SELECT",
  "table": "users",
  "sql": "SELECT * FROM users WHERE id = ?",
  "parameters": ["12345"],
  "affected_rows": 1,
  "execution_time_ms": 15,
  "status": "success",
  "application": "user-service",
  "session_id": "abc123xyz"
}
```

#### 3. 使用 SQL Safety Guard 进行审计

**静态扫描**：
```bash
# 扫描 MyBatis XML 文件
sql-scanner scan \
  --source mybatis \
  --path src/main/resources/mapper \
  --config audit-config.yml \
  --output audit-report.html
```

**运行时拦截**：
```java
// MyBatis 拦截器配置
@Configuration
public class AuditConfig {

    @Bean
    public SqlAuditInterceptor sqlAuditInterceptor() {
        SqlAuditInterceptor interceptor = new SqlAuditInterceptor();
        interceptor.setAuditRules(Arrays.asList(
            new NoWhereClauseRule(),
            new DangerousFunctionRule(),
            new SensitiveTableAccessRule()
        ));
        return interceptor;
    }
}
```

### 监控告警

#### 1. 异常 SQL 检测

监控以下异常模式：
- 无 WHERE 条件的 UPDATE/DELETE
- 全表扫描（EXPLAIN 显示 type=ALL）
- 慢查询（执行时间超过阈值）
- 深度分页（OFFSET 过大）
- 使用危险函数（LOAD_FILE、INTO OUTFILE）

#### 2. 告警规则示例

```yaml
# audit-config.yml
rules:
  - name: no-where-delete
    severity: CRITICAL
    alert: true
    alert_channels: [email, slack]

  - name: full-table-scan
    severity: WARNING
    alert: true
    threshold: 1000  # 扫描行数超过 1000 时告警

  - name: slow-query
    severity: WARNING
    alert: true
    threshold_ms: 5000  # 执行时间超过 5 秒
```

---

## 敏感数据保护

### 1. 数据加密

#### 传输加密
```properties
# 使用 SSL/TLS 连接数据库
spring.datasource.url=jdbc:mysql://localhost:3306/mydb?useSSL=true&requireSSL=true
spring.datasource.hikari.connection-test-query=SELECT 1
```

#### 存储加密
```sql
-- 使用数据库内置加密函数
INSERT INTO users (username, password_hash)
VALUES ('john', SHA2('password123', 256));

-- 应用层加密（推荐）
```

```java
// 使用 Spring Security 的 BCrypt
@Service
public class UserService {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public void createUser(String username, String password) {
        String hashedPassword = passwordEncoder.encode(password);
        userRepository.save(new User(username, hashedPassword));
    }
}
```

### 2. 敏感字段脱敏

#### 查询结果脱敏
```java
public class UserDTO {
    private Long id;
    private String username;

    @JsonSerialize(using = PhoneMaskSerializer.class)
    private String phone;  // 显示为 138****5678

    @JsonSerialize(using = EmailMaskSerializer.class)
    private String email;  // 显示为 j***@example.com
}
```

#### 日志脱敏
```java
@Slf4j
public class AuditLogger {

    public void logQuery(String sql, Object[] params) {
        // 脱敏参数中的敏感信息
        Object[] maskedParams = Arrays.stream(params)
            .map(this::maskSensitiveData)
            .toArray();
        log.info("SQL: {}, Params: {}", sql, maskedParams);
    }

    private Object maskSensitiveData(Object param) {
        if (param instanceof String) {
            String str = (String) param;
            // 检测并脱敏手机号、身份证号等
            if (str.matches("\\d{11}")) {
                return str.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
            }
        }
        return param;
    }
}
```

### 3. 访问控制

#### 字段级访问控制
```java
@Entity
public class User {
    private Long id;
    private String username;

    @JsonView(Views.Admin.class)  // 只有管理员可见
    private String email;

    @JsonIgnore  // 永不序列化
    private String passwordHash;
}
```

---

## 安全配置建议

### 数据库配置

#### MySQL 安全配置
```ini
[mysqld]
# 禁用本地文件加载
local_infile=0

# 禁用符号链接
symbolic-links=0

# 启用慢查询日志
slow_query_log=1
long_query_time=2

# 限制最大连接数
max_connections=500

# 启用查询缓存
query_cache_type=1
query_cache_size=64M

# 绑定到特定 IP（不对外暴露）
bind-address=127.0.0.1
```

#### PostgreSQL 安全配置
```ini
# postgresql.conf
ssl = on
ssl_cert_file = 'server.crt'
ssl_key_file = 'server.key'

# 限制连接
max_connections = 200

# 启用日志
logging_collector = on
log_statement = 'mod'  # 记录所有修改操作
log_duration = on
```

### 应用配置

#### 连接池配置
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

      # 连接测试
      connection-test-query: SELECT 1
      validation-timeout: 3000

      # 泄漏检测
      leak-detection-threshold: 60000
```

#### SQL Safety Guard 配置
```yaml
# sql-guard-config.yml
scanner:
  enabled: true
  mode: strict

rules:
  # 禁止无 WHERE 条件的 DELETE/UPDATE
  no-where-clause:
    enabled: true
    severity: CRITICAL

  # 检测 SQL 注入风险
  sql-injection:
    enabled: true
    check-mybatis-dollar: true

  # 限制分页大小
  large-page-size:
    enabled: true
    max-size: 1000

  # 禁止访问敏感表
  denied-tables:
    enabled: true
    tables: [sys_user_password, payment_details]

interceptor:
  enabled: true
  mode: audit  # audit | block
  log-level: INFO
```

---

## 安全检查清单

### 开发阶段
- [ ] 所有 SQL 查询使用参数化查询
- [ ] 输入验证和类型检查
- [ ] 敏感数据加密存储
- [ ] 实现基于角色的访问控制
- [ ] 添加审计日志记录
- [ ] 使用 SQL Safety Guard 进行静态扫描

### 测试阶段
- [ ] SQL 注入测试（使用 SQLMap 等工具）
- [ ] 权限测试（越权访问测试）
- [ ] 性能测试（防止慢查询）
- [ ] 审计日志完整性测试

### 部署阶段
- [ ] 数据库用户权限最小化
- [ ] 启用 SSL/TLS 连接
- [ ] 配置防火墙规则
- [ ] 启用数据库审计日志
- [ ] 配置监控告警
- [ ] 定期备份数据

### 运维阶段
- [ ] 定期审查审计日志
- [ ] 监控异常 SQL 模式
- [ ] 定期更新安全补丁
- [ ] 定期进行安全审计
- [ ] 定期备份验证

---

## 参考资源

### 标准与规范
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [CWE-89: SQL Injection](https://cwe.mitre.org/data/definitions/89.html)
- [PCI DSS 数据安全标准](https://www.pcisecuritystandards.org/)

### 工具
- [SQL Safety Guard](../../README_CN.md) - 本项目
- [SQLMap](https://sqlmap.org/) - SQL 注入检测工具
- [OWASP ZAP](https://www.zaproxy.org/) - Web 应用安全扫描

### 相关文档
- [规则设计指南](rule-design-guide.md)
- [审计分析最佳实践](../3-guides/user/audit-analysis-best-practices.md)
- [自定义审计规则开发](../3-guides/developer/custom-audit-checker.md)

---

**版本**: 1.0.0
**最后更新**: 2026-01-22
**维护者**: SQL Safety Guard Team
