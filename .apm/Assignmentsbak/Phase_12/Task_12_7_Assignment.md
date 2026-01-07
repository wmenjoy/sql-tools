---
phase: 12
task_id: 12.7
task_name: WhitelistFieldChecker Migration
agent: Agent_Architecture_Refactoring
estimated_duration: 1 day
dependencies: [Task_12.1, Task_12.2, Task_12.3, Task_12.4]
parallel_with: [Task_12.5, Task_12.6, Task_12.8]
---

# Task 12.7 Assignment: WhitelistFieldChecker Migration

## 任务目标

将 WhitelistFieldChecker 从 check() 实现迁移到 visitSelect() 方法，使用 JSqlParser API 直接提取表名和WHERE字段，删除旧的 check() 方法实现，更新测试使用 statement 字段，验证白名单字段验证逻辑一致性。

---

## 背景说明

### 当前架构（Task 12.4 之前）
```java
public class WhitelistFieldChecker extends AbstractRuleChecker {
    @Override
    public void check(SqlContext context, ValidationResult result) {
        if (!config.isEnabled()) {
            return;
        }

        Statement stmt = context.getParsedSql();

        // Extract table name using utility method
        String tableName = extractTableName(stmt);  // ❌ Utility method

        // Extract WHERE using utility method
        Expression where = extractWhere(stmt);  // ❌ Utility method
        if (where == null) {
            return;
        }

        // Lookup required fields
        List<String> requiredFields = config.getByTable().get(tableName);

        // Extract fields using utility method
        Set<String> whereFields = extractFields(where);  // ❌ Utility method

        // Check if ANY required field is present
        boolean hasRequiredField = whereFields.stream()
            .anyMatch(requiredFields::contains);

        if (!hasRequiredField) {
            result.addViolation(...);
        }
    }
}
```

**问题**：
- 使用 extractTableName() 工具方法（Task 12.4 已删除）
- 使用 extractWhere() 工具方法（Task 12.4 已删除）
- 使用 extractFields() 工具方法（Task 12.4 已删除）
- 使用 context.getParsedSql()（已弃用）

### 目标架构（Task 12.7 之后）
```java
public class WhitelistFieldChecker extends AbstractRuleChecker {
    @Override
    protected void visitSelect(Select select, SqlContext context) {
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        // 直接提取表名
        String tableName = extractTableNameFromSelect(plainSelect);

        // 直接提取 WHERE
        Expression where = plainSelect.getWhere();
        if (where == null) {
            return;
        }

        // Lookup required fields
        List<String> requiredFields = config.getByTable().get(tableName);

        // 直接提取字段
        Set<String> whereFields = extractFieldsFromExpression(where);

        // Check if ANY required field is present
        boolean hasRequiredField = whereFields.stream()
            .anyMatch(requiredFields::contains);

        if (!hasRequiredField) {
            String message = "表" + tableName + "的WHERE条件必须包含以下字段之一:" + requiredFields;
            addViolation(RiskLevel.MEDIUM, message);
        }
    }

    // 本地方法
    private String extractTableNameFromSelect(PlainSelect plainSelect) { ... }
    private Set<String> extractFieldsFromExpression(Expression expr) { ... }
}
```

---

## 实现要求

### 1. WhitelistFieldChecker 迁移

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/WhitelistFieldChecker.java`

**完整迁移代码**:

```java
package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rule checker that enforces table-specific mandatory WHERE fields (whitelist).
 *
 * <p>WhitelistFieldChecker ensures queries include primary keys, tenant IDs, or other
 * high-selectivity fields for critical tables, providing an additional safety layer.</p>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Table-specific whitelist enforcement (any one required field must be present)</li>
 *   <li>Optional global whitelist for unknown tables</li>
 *   <li>MEDIUM risk level (less severe than blacklist-only, opt-in enforcement)</li>
 *   <li>Useful for multi-tenant systems, GDPR compliance, critical business tables</li>
 * </ul>
 *
 * <p><strong>Configuration Example:</strong></p>
 * <pre>{@code
 * WhitelistFieldsConfig config = new WhitelistFieldsConfig();
 *
 * // Setup table-specific whitelist
 * Map<String, List<String>> byTable = new HashMap<>();
 * byTable.put("user", Arrays.asList("id", "user_id"));
 * byTable.put("tenant_data", Arrays.asList("tenant_id"));
 * config.setByTable(byTable);
 * }</pre>
 *
 * <p><strong>Validation Logic:</strong></p>
 * <ol>
 *   <li>Extract table name from SELECT statement</li>
 *   <li>Lookup required fields from byTable map</li>
 *   <li>If table not in map:
 *     <ul>
 *       <li>If enforceForUnknownTables=false, skip validation</li>
 *       <li>If enforceForUnknownTables=true, use global fields</li>
 *     </ul>
 *   </li>
 *   <li>Extract WHERE fields and check if ANY required field is present</li>
 *   <li>If no required field found, add MEDIUM violation</li>
 * </ol>
 *
 * @see WhitelistFieldsConfig
 * @see AbstractRuleChecker
 * @since 1.0.0 (refactored in 1.1.0 for visitor pattern)
 */
public class WhitelistFieldChecker extends AbstractRuleChecker {

    private final WhitelistFieldsConfig config;

    /**
     * Creates a WhitelistFieldChecker with the specified configuration.
     *
     * @param config the whitelist configuration
     */
    public WhitelistFieldChecker(WhitelistFieldsConfig config) {
        super(config);  // Pass config to AbstractRuleChecker
        this.config = config;
    }

    /**
     * Returns whether this checker is enabled.
     *
     * @return true if enabled, false otherwise
     */
    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Validates SELECT statements for required whitelist fields in WHERE clause.
     *
     * <p>Checks if the WHERE clause includes at least one required field from the
     * table-specific whitelist.</p>
     *
     * @param select the SELECT statement (type-safe, no casting needed)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    protected void visitSelect(Select select, SqlContext context) {
        // Extract PlainSelect
        if (!(select.getSelectBody() instanceof PlainSelect)) {
            return;
        }

        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        // Extract table name
        String tableName = extractTableNameFromSelect(plainSelect);
        if (tableName == null) {
            return;
        }

        // Extract WHERE clause
        Expression where = plainSelect.getWhere();
        if (where == null) {
            return;  // No WHERE clause means nothing to check
        }

        // Lookup required fields for this table
        List<String> requiredFields = config.getByTable().get(tableName);

        // If table not in whitelist map
        if (requiredFields == null) {
            if (!config.isEnforceForUnknownTables()) {
                return;  // Skip validation for unknown tables
            }
            // Use global fields for unknown tables
            requiredFields = config.getFields();
        }

        // If no required fields (empty list), skip validation
        if (requiredFields == null || requiredFields.isEmpty()) {
            return;
        }

        // Extract fields from WHERE clause
        Set<String> whereFields = extractFieldsFromExpression(where);

        // Check if ANY required field is present
        boolean hasRequiredField = whereFields.stream()
                .anyMatch(requiredFields::contains);

        if (!hasRequiredField) {
            // Add MEDIUM violation
            String message = "表" + tableName + "的WHERE条件必须包含以下字段之一:" + requiredFields;
            addViolation(RiskLevel.MEDIUM, message);
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts table name from PlainSelect.
     *
     * @param plainSelect the PlainSelect statement
     * @return table name or null if not found
     */
    private String extractTableNameFromSelect(PlainSelect plainSelect) {
        if (plainSelect.getFromItem() == null) {
            return null;
        }

        if (plainSelect.getFromItem() instanceof Table) {
            Table table = (Table) plainSelect.getFromItem();
            return table.getName();
        }

        return null;
    }

    /**
     * Extracts all field names from a WHERE expression.
     *
     * @param expression the WHERE expression
     * @return set of field names (lowercase)
     */
    private Set<String> extractFieldsFromExpression(Expression expression) {
        if (expression == null) {
            return new HashSet<>();
        }

        FieldCollectorVisitor visitor = new FieldCollectorVisitor();
        expression.accept(visitor);
        return visitor.getFields();
    }

    /**
     * Visitor that collects all Column references from an Expression.
     */
    private static class FieldCollectorVisitor extends ExpressionVisitorAdapter {

        private final Set<String> fields = new HashSet<>();

        public Set<String> getFields() {
            return fields;
        }

        @Override
        public void visit(Column column) {
            String columnName = column.getColumnName();
            if (columnName != null) {
                fields.add(columnName.toLowerCase());
            }
        }
    }
}
```

---

### 2. TDD 测试用例设计

**位置**: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/WhitelistFieldCheckerMigrationTest.java`

**完整测试代码**:

```java
package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for WhitelistFieldChecker migration to visitor pattern
 */
@DisplayName("WhitelistFieldChecker Migration Tests")
public class WhitelistFieldCheckerMigrationTest {

    private WhitelistFieldsConfig config;
    private WhitelistFieldChecker checker;

    @BeforeEach
    public void setUp() {
        config = new WhitelistFieldsConfig();
        config.setEnabled(true);

        // Setup table-specific whitelist
        Map<String, List<String>> byTable = new HashMap<>();
        byTable.put("users", Arrays.asList("id", "user_id"));
        byTable.put("orders", Arrays.asList("order_id", "user_id"));
        config.setByTable(byTable);

        checker = new WhitelistFieldChecker(config);
    }

    @Nested
    @DisplayName("1. Whitelist Violation Tests")
    class WhitelistViolationTests {

        /**
         * Test 1: WHERE without required field should violate
         */
        @Test
        @DisplayName("visitSelect() - WHERE without required field - should add MEDIUM violation")
        public void testVisitSelect_nonWhitelistField_violates() throws JSQLParserException {
            // Parse SELECT with WHERE name='foo' (not in whitelist)
            String sql = "SELECT * FROM users WHERE name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.nonWhitelistField")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.MEDIUM, result.getViolations().get(0).getLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("users"));
            assertTrue(result.getViolations().get(0).getMessage().contains("id"));
        }

        /**
         * Test 2: WHERE with multiple non-whitelist fields should violate
         */
        @Test
        @DisplayName("visitSelect() - WHERE multiple non-whitelist fields - should add MEDIUM violation")
        public void testVisitSelect_multipleNonWhitelistFields_violates() throws JSQLParserException {
            // Parse SELECT with WHERE name='foo' AND status='active'
            String sql = "SELECT * FROM users WHERE name = 'foo' AND status = 'active'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.multipleNonWhitelist")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("2. Whitelist Compliance Tests")
    class WhitelistComplianceTests {

        /**
         * Test 3: WHERE with required field should pass
         */
        @Test
        @DisplayName("visitSelect() - WHERE with required field - should pass")
        public void testVisitSelect_whitelistField_passes() throws JSQLParserException {
            // Parse SELECT with WHERE id=1
            String sql = "SELECT * FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.whitelistField")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation
            assertTrue(result.isPassed(), "Should pass validation");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 4: WHERE with mixed fields (whitelist + non-whitelist) should pass
         */
        @Test
        @DisplayName("visitSelect() - WHERE mixed fields - should pass")
        public void testVisitSelect_mixedFields_passes() throws JSQLParserException {
            // Parse SELECT with WHERE id=1 AND name='foo'
            String sql = "SELECT * FROM users WHERE id = 1 AND name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.mixedFields")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (includes required field 'id')
            assertTrue(result.isPassed(), "Should pass - includes required field");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 5: WHERE with alternative required field should pass
         */
        @Test
        @DisplayName("visitSelect() - WHERE with alternative required field - should pass")
        public void testVisitSelect_alternativeWhitelistField_passes() throws JSQLParserException {
            // Parse SELECT with WHERE user_id=1 (alternative required field)
            String sql = "SELECT * FROM users WHERE user_id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.alternativeWhitelist")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (user_id is in whitelist)
            assertTrue(result.isPassed(), "Should pass - user_id in whitelist");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("3. Configuration Tests")
    class ConfigurationTests {

        /**
         * Test 6: Unknown table without enforceForUnknownTables should skip
         */
        @Test
        @DisplayName("visitSelect() - unknown table, enforceForUnknownTables=false - should skip")
        public void testVisitSelect_unknownTable_skipped() throws JSQLParserException {
            config.setEnforceForUnknownTables(false);
            WhitelistFieldChecker configuredChecker = new WhitelistFieldChecker(config);

            // Parse SELECT from unknown table
            String sql = "SELECT * FROM products WHERE name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.unknownTable")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            configuredChecker.check(context, result);

            // Verify no violation (unknown table skipped)
            assertTrue(result.isPassed(), "Unknown table should be skipped");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 7: Unknown table with enforceForUnknownTables should use global fields
         */
        @Test
        @DisplayName("visitSelect() - unknown table, enforceForUnknownTables=true - should use global fields")
        public void testVisitSelect_unknownTable_usesGlobalFields() throws JSQLParserException {
            config.setEnforceForUnknownTables(true);
            List<String> globalFields = new ArrayList<>();
            globalFields.add("id");
            config.setFields(globalFields);

            WhitelistFieldChecker configuredChecker = new WhitelistFieldChecker(config);

            // Parse SELECT from unknown table without 'id'
            String sql = "SELECT * FROM products WHERE name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.unknownTableGlobal")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            configuredChecker.check(context, result);

            // Verify violation (global whitelist enforced)
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("4. Edge Cases Tests")
    class EdgeCasesTests {

        /**
         * Test 8: No WHERE clause should be skipped
         */
        @Test
        @DisplayName("visitSelect() - no WHERE clause - should be skipped")
        public void testVisitSelect_noWhere_skipped() throws JSQLParserException {
            // Parse SELECT without WHERE
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.noWhere")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (no WHERE clause to check)
            assertTrue(result.isPassed(), "Should skip - no WHERE clause");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 9: UPDATE should be skipped (default visitUpdate is no-op)
         */
        @Test
        @DisplayName("visitUpdate() - UPDATE statement - should be skipped")
        public void testVisitUpdate_skipped() throws JSQLParserException {
            // Parse UPDATE without whitelist field
            String sql = "UPDATE users SET name = 'foo' WHERE status = 'active'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.update")
                    .type(SqlCommandType.UPDATE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (UPDATE skipped)
            assertTrue(result.isPassed(), "UPDATE should be skipped");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("5. Statement Field Migration Tests")
    class StatementFieldMigrationTests {

        /**
         * Test 10: Verify statement field is used (not parsedSql)
         */
        @Test
        @DisplayName("statement field - should work correctly")
        public void testStatementField_works() throws JSQLParserException {
            // Parse SELECT without required field
            String sql = "SELECT * FROM users WHERE name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            // Build context using statement field (NOT parsedSql)
            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.statementField")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)  // NEW field
                    .build();

            // Verify statement field is accessible
            assertNotNull(context.getStatement(), "statement field should be accessible");
            assertEquals(stmt, context.getStatement(), "statement should match");

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added (no required field)
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
        }
    }
}
```

---

## 验收标准

### 功能验收
- [ ] WhitelistFieldChecker 迁移完成
- [ ] `check()` 方法已删除
- [ ] `visitSelect()` 方法实现（检查 SELECT 语句）
- [ ] 本地实现 `extractTableNameFromSelect()` 方法
- [ ] 本地实现 `extractFieldsFromExpression()` 方法
- [ ] 添加 `FieldCollectorVisitor` 内部类
- [ ] 使用 `addViolation()` 辅助方法

### 测试验收
- [ ] `WhitelistFieldCheckerMigrationTest` 创建，包含 10 个测试用例（5个测试类组）
- [ ] Test 1-2: 白名单违规检测
- [ ] Test 3-5: 白名单符合性测试
- [ ] Test 6-7: 配置测试（未知表处理）
- [ ] Test 8-9: 边界情况（无 WHERE、UPDATE 跳过）
- [ ] Test 10: statement 字段测试
- [ ] 所有 10 个新测试通过

### 行为一致性验收
- [ ] WHERE 不包含必需字段 → MEDIUM 违规（行为不变）
- [ ] WHERE 包含至少一个必需字段 → 通过（行为不变）
- [ ] 未知表处理逻辑正常（行为不变）

---

## 输出文件

### 修改文件 (1个)
- `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/WhitelistFieldChecker.java`

### 新增文件 (1个)
- `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/WhitelistFieldCheckerMigrationTest.java`

---

## 依赖与限制

### 依赖
- ✅ Task 12.1-12.4 已完成

### 限制
- ⚠️ **并行执行**：可与 Task 12.5, 12.6, 12.8 并行执行

---

## 执行时间线

- **预计时间**: 1 工作日
- **上午**: 迁移代码 + Test 1-5
- **下午**: Test 6-10 + 验证

---

**Created**: 2025-12-22
**Phase**: 12 - Core Architecture Unification
**Task ID**: 12.7
**Parallel**: Task 12.5, 12.6, 12.8
