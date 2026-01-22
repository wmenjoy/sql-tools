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

# 审计规则设计指南

## 概述

本文档提供了设计和实现自定义 SQL 审计规则的完整指南，帮助开发者根据业务需求创建高质量的审计规则。

## 目录

- [规则设计原则](#规则设计原则)
- [规则类型](#规则类型)
- [规则实现步骤](#规则实现步骤)
- [规则配置](#规则配置)
- [测试与验证](#测试与验证)
- [最佳实践](#最佳实践)

---

## 规则设计原则

### 1. 单一职责原则

每个规则应该只检查一种特定的 SQL 问题。

**✅ 好的设计**：
```java
// NoWhereClauseChecker - 只检查缺少 WHERE 条件
public class NoWhereClauseChecker implements AuditChecker {
    @Override
    public ValidationResult check(SqlStatement statement) {
        if (isUpdateOrDelete(statement) && !hasWhereClause(statement)) {
            return ValidationResult.fail("UPDATE/DELETE 语句缺少 WHERE 条件");
        }
        return ValidationResult.pass();
    }
}
```

**❌ 不好的设计**：
```java
// 一个规则检查多个不相关的问题
public class MultiPurposeChecker implements AuditChecker {
    @Override
    public ValidationResult check(SqlStatement statement) {
        // 检查 WHERE 条件
        if (!hasWhereClause(statement)) { ... }
        // 检查分页
        if (hasLargePageSize(statement)) { ... }
        // 检查危险函数
        if (hasDangerousFunction(statement)) { ... }
        // 违反单一职责原则
    }
}
```

### 2. 明确的错误信息

错误信息应该清晰地说明问题和建议的解决方案。

**✅ 好的错误信息**：
```java
return ValidationResult.fail(
    "检测到深度分页：OFFSET 值为 " + offset + "，超过限制 " + maxOffset + "。" +
    "建议：使用游标分页或限制 OFFSET 最大值。"
);
```

**❌ 不好的错误信息**：
```java
return ValidationResult.fail("分页错误");  // 太模糊
```

### 3. 可配置性

规则应该支持配置，以适应不同的业务场景。

```java
public class LargePageSizeChecker implements AuditChecker {

    private int maxPageSize = 1000;  // 默认值

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    @Override
    public ValidationResult check(SqlStatement statement) {
        int limit = extractLimit(statement);
        if (limit > maxPageSize) {
            return ValidationResult.fail(
                "分页大小 " + limit + " 超过限制 " + maxPageSize
            );
        }
        return ValidationResult.pass();
    }
}
```

### 4. 性能考虑

规则检查应该高效，避免复杂的计算或外部调用。

**✅ 高效的实现**：
```java
// 使用简单的 AST 遍历
public boolean hasWhereClause(Statement statement) {
    if (statement instanceof Select) {
        Select select = (Select) statement;
        return select.getWhere() != null;
    }
    return false;
}
```

**❌ 低效的实现**：
```java
// 避免：重新解析 SQL 字符串
public boolean hasWhereClause(Statement statement) {
    String sql = statement.toString();
    // 正则表达式匹配，性能差且不可靠
    return sql.matches(".*WHERE.*");
}
```

---

## 规则类型

### 1. 语法规则

检查 SQL 语法结构是否符合规范。

**示例：禁止 SELECT ***
```java
public class SelectStarChecker implements AuditChecker {

    @Override
    public ValidationResult check(SqlStatement statement) {
        if (statement instanceof Select) {
            Select select = (Select) statement;
            for (SelectItem item : select.getSelectItems()) {
                if (item instanceof AllColumns) {
                    return ValidationResult.fail(
                        "禁止使用 SELECT *，请明确指定列名"
                    );
                }
            }
        }
        return ValidationResult.pass();
    }
}
```

### 2. 语义规则

检查 SQL 的业务语义是否合理。

**示例：检查敏感表访问**
```java
public class SensitiveTableChecker implements AuditChecker {

    private Set<String> sensitiveTablesSet<String> = Set.of(
        "user_password", "payment_info", "credit_card"
    );

    @Override
    public ValidationResult check(SqlStatement statement) {
        Set<String> tables = extractTableNames(statement);
        for (String table : tables) {
            if (sensitiveTables.contains(table.toLowerCase())) {
                return ValidationResult.fail(
                    "禁止访问敏感表：" + table
                );
            }
        }
        return ValidationResult.pass();
    }
}
```

### 3. 性能规则

检查可能导致性能问题的 SQL 模式。

**示例：检查深度分页**
```java
public class DeepPaginationChecker implements AuditChecker {

    private int maxOffset = 10000;

    @Override
    public ValidationResult check(SqlStatement statement) {
        if (statement instanceof Select) {
            Select select = (Select) statement;
            Limit limit = select.getLimit();
            if (limit != null && limit.getOffset() != null) {
                long offset = limit.getOffset().getValue();
                if (offset > maxOffset) {
                    return ValidationResult.fail(
                        "深度分页：OFFSET " + offset + " 超过限制 " + maxOffset +
                        "。建议使用游标分页。"
                    );
                }
            }
        }
        return ValidationResult.pass();
    }
}
```

### 4. 安全规则

检查潜在的安全风险。

**示例：检查 SQL 注入风险**
```java
public class SqlInjectionChecker implements AuditChecker {

    @Override
    public ValidationResult check(SqlStatement statement) {
        // 检查 MyBatis ${} 语法
        String sql = statement.getOriginalSql();
        if (sql.contains("${")) {
            return ValidationResult.fail(
                "检测到 SQL 注入风险：使用了 MyBatis ${} 语法。" +
                "建议使用 #{} 参数化查询。"
            );
        }
        return ValidationResult.pass();
    }
}
```

---

## 规则实现步骤

### 步骤 1：定义规则需求

明确规则要检查什么问题，以及为什么需要这个规则。

**示例需求文档**：
```markdown
## 规则：禁止无条件删除

### 背景
生产环境曾发生过误删除整表数据的事故。

### 目标
防止 DELETE 语句在没有 WHERE 条件时执行。

### 范围
- 适用于所有 DELETE 语句
- 不适用于 TRUNCATE（由其他规则处理）

### 严重级别
CRITICAL

### 示例
❌ DELETE FROM users;
✅ DELETE FROM users WHERE id = 123;
```

### 步骤 2：实现 AuditChecker 接口

```java
package com.example.sqlguard.checker;

import com.example.sqlguard.core.AuditChecker;
import com.example.sqlguard.core.ValidationResult;
import com.example.sqlguard.model.SqlStatement;
import net.sf.jsqlparser.statement.delete.Delete;

public class NoWhereDeleteChecker implements AuditChecker {

    @Override
    public String getRuleName() {
        return "no-where-delete";
    }

    @Override
    public String getDescription() {
        return "禁止无 WHERE 条件的 DELETE 语句";
    }

    @Override
    public ValidationResult check(SqlStatement statement) {
        if (!(statement.getParsedStatement() instanceof Delete)) {
            return ValidationResult.pass();
        }

        Delete delete = (Delete) statement.getParsedStatement();

        if (delete.getWhere() == null) {
            return ValidationResult.builder()
                .passed(false)
                .ruleName(getRuleName())
                .message("DELETE 语句缺少 WHERE 条件，可能导致误删除所有数据")
                .suggestion("添加 WHERE 条件限制删除范围")
                .severity(Severity.CRITICAL)
                .build();
        }

        return ValidationResult.pass();
    }
}
```

### 步骤 3：添加配置支持

```java
public class NoWhereDeleteChecker implements AuditChecker {

    private boolean enabled = true;
    private Severity severity = Severity.CRITICAL;
    private List<String> excludedTables = new ArrayList<>();

    // Getter 和 Setter
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setExcludedTables(List<String> excludedTables) {
        this.excludedTables = excludedTables;
    }

    @Override
    public ValidationResult check(SqlStatement statement) {
        if (!enabled) {
            return ValidationResult.pass();
        }

        Delete delete = (Delete) statement.getParsedStatement();
        String tableName = delete.getTable().getName();

        // 检查是否在排除列表中
        if (excludedTables.contains(tableName.toLowerCase())) {
            return ValidationResult.pass();
        }

        if (delete.getWhere() == null) {
            return ValidationResult.builder()
                .passed(false)
                .ruleName(getRuleName())
                .message("DELETE 语句缺少 WHERE 条件")
                .severity(severity)
                .build();
        }

        return ValidationResult.pass();
    }
}
```

### 步骤 4：编写单元测试

```java
package com.example.sqlguard.checker;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoWhereDeleteCheckerTest {

    private NoWhereDeleteChecker checker = new NoWhereDeleteChecker();

    @Test
    void testDeleteWithoutWhere_shouldFail() {
        String sql = "DELETE FROM users";
        SqlStatement statement = SqlParser.parse(sql);

        ValidationResult result = checker.check(statement);

        assertFalse(result.isPassed());
        assertEquals("no-where-delete", result.getRuleName());
        assertEquals(Severity.CRITICAL, result.getSeverity());
    }

    @Test
    void testDeleteWithWhere_shouldPass() {
        String sql = "DELETE FROM users WHERE id = 123";
        SqlStatement statement = SqlParser.parse(sql);

        ValidationResult result = checker.check(statement);

        assertTrue(result.isPassed());
    }

    @Test
    void testExcludedTable_shouldPass() {
        checker.setExcludedTables(Arrays.asList("temp_table"));

        String sql = "DELETE FROM temp_table";
        SqlStatement statement = SqlParser.parse(sql);

        ValidationResult result = checker.check(statement);

        assertTrue(result.isPassed());
    }

    @Test
    void testDisabledChecker_shouldPass() {
        checker.setEnabled(false);

        String sql = "DELETE FROM users";
        SqlStatement statement = SqlParser.parse(sql);

        ValidationResult result = checker.check(statement);

        assertTrue(result.isPassed());
    }
}
```

### 步骤 5：注册规则

```java
@Configuration
public class AuditRuleConfig {

    @Bean
    public NoWhereDeleteChecker noWhereDeleteChecker() {
        NoWhereDeleteChecker checker = new NoWhereDeleteChecker();
        checker.setEnabled(true);
        checker.setSeverity(Severity.CRITICAL);
        return checker;
    }

    @Bean
    public AuditEngine auditEngine(List<AuditChecker> checkers) {
        return new AuditEngine(checkers);
    }
}
```

---

## 规则配置

### YAML 配置格式

```yaml
# audit-rules.yml
rules:
  no-where-delete:
    enabled: true
    severity: CRITICAL
    excluded_tables:
      - temp_table
      - staging_table

  large-page-size:
    enabled: true
    severity: WARNING
    max_page_size: 1000

  sensitive-table-access:
    enabled: true
    severity: CRITICAL
    sensitive_tables:
      - user_password
      - payment_info
      - credit_card
    allowed_users:
      - admin
      - audit_service
```

### 配置加载

```java
@Configuration
@ConfigurationProperties(prefix = "audit")
public class AuditRuleProperties {

    private Map<String, RuleConfig> rules = new HashMap<>();

    public static class RuleConfig {
        private boolean enabled = true;
        private String severity = "WARNING";
        private Map<String, Object> parameters = new HashMap<>();

        // Getters and Setters
    }

    // Getters and Setters
}
```

---

## 测试与验证

### 1. 单元测试

测试规则的核心逻辑：

```java
@Test
void testRuleLogic() {
    // 准备测试数据
    String sql = "...";
    SqlStatement statement = SqlParser.parse(sql);

    // 执行规则检查
    ValidationResult result = checker.check(statement);

    // 验证结果
    assertEquals(expected, result.isPassed());
    assertEquals(expectedMessage, result.getMessage());
}
```

### 2. 集成测试

测试规则在完整系统中的行为：

```java
@SpringBootTest
class AuditEngineIntegrationTest {

    @Autowired
    private AuditEngine auditEngine;

    @Test
    void testAuditWithMultipleRules() {
        String sql = "DELETE FROM users";

        AuditResult result = auditEngine.audit(sql);

        assertFalse(result.isPassed());
        assertTrue(result.getViolations().stream()
            .anyMatch(v -> v.getRuleName().equals("no-where-delete")));
    }
}
```

### 3. 性能测试

确保规则检查不会成为性能瓶颈：

```java
@Test
void testPerformance() {
    String sql = "SELECT * FROM users WHERE id = 1";
    SqlStatement statement = SqlParser.parse(sql);

    long startTime = System.nanoTime();
    for (int i = 0; i < 10000; i++) {
        checker.check(statement);
    }
    long endTime = System.nanoTime();

    long avgTimeNs = (endTime - startTime) / 10000;
    assertTrue(avgTimeNs < 100000, "规则检查平均耗时应小于 0.1ms");
}
```

---

## 最佳实践

### 1. 规则命名规范

- 使用 kebab-case：`no-where-delete`、`large-page-size`
- 名称应该描述检查的问题，而不是解决方案
- 保持简洁但有意义

### 2. 错误信息模板

```java
public class ErrorMessageBuilder {

    public static String buildMessage(String problem, String impact, String suggestion) {
        return String.format(
            "问题：%s\n影响：%s\n建议：%s",
            problem, impact, suggestion
        );
    }
}

// 使用示例
String message = ErrorMessageBuilder.buildMessage(
    "DELETE 语句缺少 WHERE 条件",
    "可能导致误删除所有数据",
    "添加 WHERE 条件限制删除范围，或使用 TRUNCATE 清空表"
);
```

### 3. 规则优先级

按严重程度组织规则：

```java
public enum Severity {
    CRITICAL(1),    // 必须修复
    ERROR(2),       // 应该修复
    WARNING(3),     // 建议修复
    INFO(4);        // 仅提示

    private final int level;

    Severity(int level) {
        this.level = level;
    }
}
```

### 4. 规则文档

每个规则应该有完整的文档：

```markdown
## no-where-delete

### 描述
检测 DELETE 语句是否缺少 WHERE 条件。

### 原因
无 WHERE 条件的 DELETE 会删除表中所有数据，通常是误操作。

### 示例

❌ 违规：
DELETE FROM users;

✅ 合规：
DELETE FROM users WHERE id = 123;
DELETE FROM users WHERE created_at < '2020-01-01';

### 配置

enabled: true
severity: CRITICAL
excluded_tables: []

### 相关规则
- no-where-update
- truncate-table
```

### 5. 规则版本管理

```java
public interface AuditChecker {

    String getRuleName();

    String getVersion();  // 规则版本

    default String getFullName() {
        return getRuleName() + "@" + getVersion();
    }
}
```

---

## 高级主题

### 1. 复合规则

组合多个简单规则：

```java
public class CompositeChecker implements AuditChecker {

    private List<AuditChecker> checkers;

    @Override
    public ValidationResult check(SqlStatement statement) {
        List<ValidationResult> results = new ArrayList<>();

        for (AuditChecker checker : checkers) {
            ValidationResult result = checker.check(statement);
            if (!result.isPassed()) {
                results.add(result);
            }
        }

        if (results.isEmpty()) {
            return ValidationResult.pass();
        }

        return ValidationResult.composite(results);
    }
}
```

### 2. 上下文感知规则

根据执行上下文调整规则行为：

```java
public class ContextAwareChecker implements AuditChecker {

    @Override
    public ValidationResult check(SqlStatement statement) {
        ExecutionContext context = statement.getContext();

        // 根据用户角色调整规则
        if (context.getUser().hasRole("ADMIN")) {
            return ValidationResult.pass();
        }

        // 根据环境调整规则
        if (context.getEnvironment().equals("test")) {
            // 测试环境放宽限制
        }

        return doCheck(statement);
    }
}
```

### 3. 规则依赖

某些规则依赖其他规则的结果：

```java
public class DependentChecker implements AuditChecker {

    @Override
    public List<String> getDependencies() {
        return Arrays.asList("no-where-clause", "table-exists");
    }

    @Override
    public ValidationResult check(SqlStatement statement, Map<String, ValidationResult> dependencyResults) {
        // 只有依赖规则都通过时才执行
        if (dependencyResults.values().stream().allMatch(ValidationResult::isPassed)) {
            return doCheck(statement);
        }
        return ValidationResult.skip("依赖规则未通过");
    }
}
```

---

## 参考资源

### 内部文档
- [自定义审计规则开发](../3-guides/developer/custom-audit-checker.md)
- [SQL 安全最佳实践](sql-security-best-practices.md)
- [架构文档](../1-specs/architecture/ARCHITECTURE.md)

### 外部资源
- [JSqlParser 文档](https://github.com/JSQLParser/JSqlParser)
- [OWASP SQL 注入防护](https://owasp.org/www-community/attacks/SQL_Injection)

---

**版本**: 1.0.0
**最后更新**: 2026-01-22
**维护者**: SQL Safety Guard Team
