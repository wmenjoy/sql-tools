---
phase: 12
task_id: 12.6
task_name: BlacklistFieldChecker Migration
agent: Agent_Architecture_Refactoring
estimated_duration: 1 day
dependencies: [Task_12.1, Task_12.2, Task_12.3, Task_12.4]
parallel_with: [Task_12.5, Task_12.7, Task_12.8]
---

# Task 12.6 Assignment: BlacklistFieldChecker Migration

## 任务目标

将 BlacklistFieldChecker 从 check() 实现迁移到 visitSelect() 方法，使用 JSqlParser API 直接提取 SELECT 字段（selectBody.getSelectItems()），删除旧的 check() 方法实现，更新测试使用 statement 字段，验证黑名单字段检测逻辑一致性。

---

## 背景说明

### 当前架构（Task 12.4 之前）
```java
public class BlacklistFieldChecker extends AbstractRuleChecker {
    @Override
    public void check(SqlContext context, ValidationResult result) {
        if (!config.isEnabled()) {
            return;
        }

        Statement stmt = context.getParsedSql();

        // Extract WHERE using utility method
        Expression where = extractWhere(stmt);  // ❌ Utility method
        if (where == null) {
            return;
        }

        // Extract fields using utility method
        Set<String> whereFields = extractFields(where);  // ❌ Utility method

        // Check if ALL fields are blacklisted
        boolean allFieldsBlacklisted = whereFields.stream()
            .allMatch(field -> isBlacklisted(field, config.getFields()));

        if (allFieldsBlacklisted) {
            result.addViolation(...);
        }
    }
}
```

**问题**：
- 使用 extractWhere() 工具方法（Task 12.4 已删除）
- 使用 extractFields() 工具方法（Task 12.4 已删除）
- 使用 context.getParsedSql()（已弃用）
- 直接调用 result.addViolation()（应使用辅助方法）

### 目标架构（Task 12.6 之后）
```java
public class BlacklistFieldChecker extends AbstractRuleChecker {
    @Override
    protected void visitSelect(Select select, SqlContext context) {
        // 直接使用 JSqlParser API 提取 WHERE
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        Expression where = plainSelect.getWhere();

        if (where == null) {
            return;
        }

        // 直接提取字段（实现 extractFields 逻辑）
        Set<String> whereFields = extractFieldsFromExpression(where);

        // 检查黑名单
        boolean allFieldsBlacklisted = whereFields.stream()
            .allMatch(field -> isBlacklisted(field, config.getFields()));

        if (allFieldsBlacklisted) {
            String fieldList = String.join(", ", whereFields);
            addViolation(RiskLevel.HIGH,
                "WHERE条件只包含黑名单字段[" + fieldList + "],条件过于宽泛");
        }
    }

    // 保留私有方法
    private boolean isBlacklisted(String field, Set<String> blacklist) { ... }
    private Set<String> extractFieldsFromExpression(Expression expr) { ... }
}
```

**改进**：
- ✅ 删除 check() 方法（使用模板方法）
- ✅ 类型安全：visitSelect 接收 Select 参数
- ✅ 直接 API：plainSelect.getWhere() 而非 extractWhere(statement)
- ✅ 本地实现：extractFieldsFromExpression() 替代 extractFields()
- ✅ 保留逻辑：isBlacklisted() 仍为私有方法（非 extractXxx 工具方法）

---

## 实现要求

### 1. BlacklistFieldChecker 迁移

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/BlacklistFieldChecker.java`

**完整迁移代码**:

```java
package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Rule checker that detects WHERE conditions using only blacklisted fields.
 *
 * <p>Blacklisted fields are typically low-cardinality state flags (deleted, status, enabled, etc.)
 * that cause excessive row matches and near-full-table scans when used as the only WHERE
 * condition. Such queries appear to have WHERE clauses but still match most rows in the table,
 * resulting in poor performance.</p>
 *
 * <p><strong>Detection Logic:</strong></p>
 * <ol>
 *   <li>Extract WHERE clause from SELECT statement</li>
 *   <li>Extract all field names from WHERE clause using visitor pattern</li>
 *   <li>Check if ALL fields are blacklisted</li>
 *   <li>If true, add HIGH risk violation</li>
 * </ol>
 *
 * <p><strong>Risk Level:</strong> HIGH</p>
 * <p>Queries with blacklist-only conditions appear to filter data but still scan most rows,
 * causing performance issues similar to missing WHERE clauses.</p>
 *
 * <p><strong>Examples:</strong></p>
 * <pre>{@code
 * // VIOLATES - only blacklisted field
 * SELECT * FROM users WHERE deleted=0
 * SELECT * FROM orders WHERE status='active'
 * SELECT * FROM products WHERE enabled=1 AND type='normal'
 *
 * // PASSES - includes non-blacklisted field
 * SELECT * FROM users WHERE id=1 AND deleted=0
 * SELECT * FROM orders WHERE user_id=? AND status='active'
 * }</pre>
 *
 * <p><strong>Wildcard Support:</strong></p>
 * <p>Blacklist entries ending with '*' are treated as prefix patterns:</p>
 * <pre>{@code
 * // If blacklist includes "create_*"
 * SELECT * FROM users WHERE create_time > ?  // VIOLATES
 * SELECT * FROM users WHERE create_by = ?    // VIOLATES
 * }</pre>
 *
 * <p><strong>Implementation Details:</strong></p>
 * <ul>
 *   <li>Uses visitor pattern: overrides visitSelect()</li>
 *   <li>Direct API usage: plainSelect.getWhere() instead of utility methods</li>
 *   <li>Local field extraction: extractFieldsFromExpression() using ExpressionVisitorAdapter</li>
 *   <li>Retained logic: isBlacklisted() private method for pattern matching</li>
 * </ul>
 *
 * @see BlacklistFieldsConfig
 * @see AbstractRuleChecker
 * @since 1.0.0 (refactored in 1.1.0 for visitor pattern)
 */
public class BlacklistFieldChecker extends AbstractRuleChecker {

    private final BlacklistFieldsConfig config;

    /**
     * Creates a BlacklistFieldChecker with the specified configuration.
     *
     * @param config the configuration defining blacklisted fields
     */
    public BlacklistFieldChecker(BlacklistFieldsConfig config) {
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
     * Validates SELECT statements for blacklist-only WHERE conditions.
     *
     * <p>Checks if the WHERE clause uses only blacklisted fields. If all WHERE fields
     * are blacklisted, adds a HIGH violation.</p>
     *
     * <p><strong>Direct API Usage:</strong></p>
     * <pre>{@code
     * PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
     * Expression where = plainSelect.getWhere();  // JSqlParser API
     * }</pre>
     *
     * @param select the SELECT statement (type-safe, no casting needed)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    protected void visitSelect(Select select, SqlContext context) {
        // Extract PlainSelect (most common SELECT body type)
        if (!(select.getSelectBody() instanceof PlainSelect)) {
            // Skip non-PlainSelect (SetOperations like UNION)
            return;
        }

        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

        // Direct API call to get WHERE clause
        Expression where = plainSelect.getWhere();

        // Skip if no WHERE clause (handled by NoWhereClauseChecker)
        if (where == null) {
            return;
        }

        // Extract all field names from WHERE clause
        Set<String> whereFields = extractFieldsFromExpression(where);

        // Skip if no fields found (e.g., WHERE 1=1)
        if (whereFields.isEmpty()) {
            return;
        }

        // Check if ALL fields are blacklisted
        boolean allFieldsBlacklisted = whereFields.stream()
                .allMatch(field -> isBlacklisted(field, config.getFields()));

        // Add violation if all fields are blacklisted
        if (allFieldsBlacklisted) {
            String fieldList = whereFields.stream()
                    .collect(Collectors.joining(", "));

            String message = String.format(
                    "WHERE条件只包含黑名单字段[%s],条件过于宽泛",
                    fieldList
            );

            addViolation(RiskLevel.HIGH, message);
        }
    }

    // ==================== Private Helper Methods ====================

    /**
     * Extracts all field names from a WHERE expression.
     *
     * <p>Uses a visitor pattern to traverse the expression tree and collect
     * all Column references.</p>
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
     * Checks if a field name matches any entry in the blacklist.
     *
     * <p>Matching rules:</p>
     * <ul>
     *   <li>Case-insensitive comparison</li>
     *   <li>Exact match: "deleted" matches "deleted"</li>
     *   <li>Wildcard match: "create_*" matches "create_time", "create_by", etc.</li>
     * </ul>
     *
     * @param field the field name to check
     * @param blacklist the set of blacklisted patterns
     * @return true if the field is blacklisted, false otherwise
     */
    private boolean isBlacklisted(String field, Set<String> blacklist) {
        if (field == null || blacklist == null || blacklist.isEmpty()) {
            return false;
        }

        String fieldLower = field.toLowerCase();

        for (String blacklistEntry : blacklist) {
            String entryLower = blacklistEntry.toLowerCase();

            // Check for exact match
            if (fieldLower.equals(entryLower)) {
                return true;
            }

            // Check for wildcard pattern (e.g., "create_*")
            if (entryLower.endsWith("*")) {
                String prefix = entryLower.substring(0, entryLower.length() - 1);
                if (fieldLower.startsWith(prefix)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Visitor that collects all Column references from an Expression.
     *
     * <p>Traverses the expression tree and extracts column names.</p>
     */
    private static class FieldCollectorVisitor extends ExpressionVisitorAdapter {

        private final Set<String> fields = new HashSet<>();

        /**
         * Returns the collected field names.
         *
         * @return set of field names (lowercase)
         */
        public Set<String> getFields() {
            return fields;
        }

        @Override
        public void visit(Column column) {
            // Extract column name and add to set (lowercase)
            String columnName = column.getColumnName();
            if (columnName != null) {
                fields.add(columnName.toLowerCase());
            }
        }
    }

    // ==================== MIGRATION NOTES ====================
    // The following methods have been REMOVED in Phase 12 migration:
    // - public void check(SqlContext context, ValidationResult result)
    //   Replaced by: AbstractRuleChecker template method calls visitSelect
    //
    // - Expression extractWhere(Statement stmt)
    //   Replaced by: Direct API call plainSelect.getWhere()
    //
    // - Set<String> extractFields(Expression where)
    //   Replaced by: Local method extractFieldsFromExpression() using visitor pattern
    //
    // Retained methods:
    // - isBlacklisted(String, Set<String>) - NOT a utility method, specific to blacklist logic
    // - FieldCollectorVisitor - Local visitor for field extraction
    //
    // Benefits of new architecture:
    // 1. Type safety: visitSelect receives Select parameter
    // 2. Direct API: plainSelect.getWhere() is clear and explicit
    // 3. Local implementation: Field extraction logic is encapsulated in this class
    // 4. Maintainability: No cross-class utility dependencies
}
```

**关键变更**：
1. ✅ 删除 `check()` 方法（AbstractRuleChecker 模板方法提供）
2. ✅ 添加 `visitSelect()` 方法（处理 SELECT 语句）
3. ✅ 直接使用 `plainSelect.getWhere()`
4. ✅ 本地实现 `extractFieldsFromExpression()` 替代 `extractFields()`
5. ✅ 添加 `FieldCollectorVisitor` 内部类
6. ✅ 保留 `isBlacklisted()` 私有方法（非工具方法）
7. ✅ 使用 `addViolation(RiskLevel, String)` 辅助方法
8. ✅ 构造函数传递 config 给父类：`super(config)`

---

### 2. TDD 测试用例设计

**位置**: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/BlacklistFieldCheckerMigrationTest.java`

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

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for BlacklistFieldChecker migration to visitor pattern
 */
@DisplayName("BlacklistFieldChecker Migration Tests")
public class BlacklistFieldCheckerMigrationTest {

    private BlacklistFieldsConfig config;
    private BlacklistFieldChecker checker;

    @BeforeEach
    public void setUp() {
        config = new BlacklistFieldsConfig();
        config.setEnabled(true);

        // Setup default blacklist: deleted, status
        Set<String> blacklist = new HashSet<>();
        blacklist.add("deleted");
        blacklist.add("status");
        config.setFields(blacklist);

        checker = new BlacklistFieldChecker(config);
    }

    @Nested
    @DisplayName("1. Blacklist-Only WHERE Tests")
    class BlacklistOnlyTests {

        /**
         * Test 1: WHERE with only blacklist field should violate
         */
        @Test
        @DisplayName("visitSelect() - WHERE only blacklist field - should add HIGH violation")
        public void testVisitSelect_blacklistField_violates() throws JSQLParserException {
            // Parse SELECT with WHERE deleted=0
            String sql = "SELECT * FROM users WHERE deleted = 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.blacklistOnly")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("deleted"));
            assertTrue(result.getViolations().get(0).getMessage().contains("黑名单"));
        }

        /**
         * Test 2: WHERE with multiple blacklist fields should violate
         */
        @Test
        @DisplayName("visitSelect() - WHERE multiple blacklist fields - should add HIGH violation")
        public void testVisitSelect_multipleBlacklistFields_violates() throws JSQLParserException {
            // Parse SELECT with WHERE deleted=0 AND status='active'
            String sql = "SELECT * FROM users WHERE deleted = 0 AND status = 'active'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.multipleBlacklist")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify violation added
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("deleted"));
            assertTrue(result.getViolations().get(0).getMessage().contains("status"));
        }
    }

    @Nested
    @DisplayName("2. Mixed Fields Tests")
    class MixedFieldsTests {

        /**
         * Test 3: WHERE with non-blacklist field should pass
         */
        @Test
        @DisplayName("visitSelect() - WHERE with non-blacklist field - should pass")
        public void testVisitSelect_allowedFields_passes() throws JSQLParserException {
            // Parse SELECT with WHERE id=1
            String sql = "SELECT * FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.allowedField")
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
         * Test 4: WHERE with mixed fields (blacklist + non-blacklist) should pass
         */
        @Test
        @DisplayName("visitSelect() - WHERE mixed fields - should pass")
        public void testVisitSelect_mixedFields_passes() throws JSQLParserException {
            // Parse SELECT with WHERE id=1 AND deleted=0
            String sql = "SELECT * FROM users WHERE id = 1 AND deleted = 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.mixedFields")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (includes non-blacklist field 'id')
            assertTrue(result.isPassed(), "Should pass - includes non-blacklist field");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("3. Wildcard Pattern Tests")
    class WildcardPatternTests {

        /**
         * Test 5: Wildcard pattern should match prefix
         */
        @Test
        @DisplayName("visitSelect() - wildcard pattern match - should violate")
        public void testVisitSelect_wildcardPattern_violates() throws JSQLParserException {
            // Setup wildcard pattern
            Set<String> blacklist = new HashSet<>();
            blacklist.add("create_*");  // Matches create_time, create_by, etc.
            config.setFields(blacklist);

            BlacklistFieldChecker wildcardChecker = new BlacklistFieldChecker(config);

            // Parse SELECT with WHERE create_time > ?
            String sql = "SELECT * FROM users WHERE create_time > '2020-01-01'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.wildcardPattern")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            wildcardChecker.check(context, result);

            // Verify violation added (create_time matches create_*)
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
            assertTrue(result.getViolations().get(0).getMessage().contains("create_time"));
        }
    }

    @Nested
    @DisplayName("4. Edge Cases Tests")
    class EdgeCasesTests {

        /**
         * Test 6: No WHERE clause should be skipped
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
         * Test 7: WHERE 1=1 (no fields) should be skipped
         */
        @Test
        @DisplayName("visitSelect() - WHERE 1=1 (dummy) - should be skipped")
        public void testVisitSelect_dummyWhere_skipped() throws JSQLParserException {
            // Parse SELECT with WHERE 1=1
            String sql = "SELECT * FROM users WHERE 1 = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.dummyWhere")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (no fields extracted from WHERE 1=1)
            assertTrue(result.isPassed(), "Should skip - no fields in WHERE");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("5. Other Statement Tests")
    class OtherStatementTests {

        /**
         * Test 8: UPDATE should be skipped (default visitUpdate is no-op)
         */
        @Test
        @DisplayName("visitUpdate() - UPDATE statement - should be skipped")
        public void testVisitUpdate_skipped() throws JSQLParserException {
            // Parse UPDATE with blacklist field
            String sql = "UPDATE users SET name = 'foo' WHERE deleted = 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.update")
                    .type(SqlCommandType.UPDATE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (UPDATE skipped by BlacklistFieldChecker)
            assertTrue(result.isPassed(), "UPDATE should be skipped");
            assertEquals(0, result.getViolations().size());
        }

        /**
         * Test 9: DELETE should be skipped (default visitDelete is no-op)
         */
        @Test
        @DisplayName("visitDelete() - DELETE statement - should be skipped")
        public void testVisitDelete_skipped() throws JSQLParserException {
            // Parse DELETE with blacklist field
            String sql = "DELETE FROM users WHERE deleted = 0";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.delete")
                    .type(SqlCommandType.DELETE)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify no violation (DELETE skipped by BlacklistFieldChecker)
            assertTrue(result.isPassed(), "DELETE should be skipped");
            assertEquals(0, result.getViolations().size());
        }
    }

    @Nested
    @DisplayName("6. Statement Field Migration Tests")
    class StatementFieldMigrationTests {

        /**
         * Test 10: Verify statement field is used (not parsedSql)
         */
        @Test
        @DisplayName("statement field - should work correctly")
        public void testStatementField_works() throws JSQLParserException {
            // Parse SELECT with blacklist field
            String sql = "SELECT * FROM users WHERE deleted = 0";
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

            // Verify violation added (blacklist-only WHERE)
            assertFalse(result.isPassed(), "Should have violation");
            assertEquals(1, result.getViolations().size());
        }
    }
}
```

---

## 验收标准

### 功能验收
- [ ] BlacklistFieldChecker 迁移完成
- [ ] `check()` 方法已删除
- [ ] `visitSelect()` 方法实现（检查 SELECT 语句）
- [ ] 直接使用 `plainSelect.getWhere()`
- [ ] 本地实现 `extractFieldsFromExpression()` 方法
- [ ] 保留 `isBlacklisted()` 私有方法
- [ ] 添加 `FieldCollectorVisitor` 内部类
- [ ] 使用 `addViolation()` 辅助方法

### 测试验收
- [ ] `BlacklistFieldCheckerMigrationTest` 创建，包含 10 个测试用例（6个测试类组）
- [ ] Test 1-2: 黑名单字段检测（单个字段、多个字段）
- [ ] Test 3-4: 混合字段测试（非黑名单字段、混合字段）
- [ ] Test 5: 通配符模式测试
- [ ] Test 6-7: 边界情况（无 WHERE、WHERE 1=1）
- [ ] Test 8-9: 其他语句跳过（UPDATE、DELETE）
- [ ] Test 10: statement 字段测试
- [ ] 所有 10 个新测试通过

### 行为一致性验收
- [ ] WHERE 只有黑名单字段 → HIGH 违规（行为不变）
- [ ] WHERE 包含非黑名单字段 → 通过（行为不变）
- [ ] 通配符模式匹配正常（行为不变）
- [ ] 无 WHERE 子句跳过检查（行为不变）

---

## 输出文件

### 修改文件 (1个)
- `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/BlacklistFieldChecker.java`

### 新增文件 (1个)
- `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/BlacklistFieldCheckerMigrationTest.java`

---

## 依赖与限制

### 依赖
- ✅ Task 12.1 已完成（SqlContext.statement 字段）
- ✅ Task 12.2 已完成（StatementVisitor 接口）
- ✅ Task 12.3 已完成（RuleChecker 继承 StatementVisitor）
- ✅ Task 12.4 已完成（AbstractRuleChecker 模板方法，extractWhere/extractFields 已删除）

### 限制
- ⚠️ **并行执行**：可与 Task 12.5, 12.7, 12.8 并行执行（独立 Checker，无依赖）
- ⚠️ **不修改其他 Checker**：只迁移 BlacklistFieldChecker
- ⚠️ **保持行为一致**：迁移后黑名单检测逻辑必须与旧实现完全一致

---

## 参考文档

### Implementation Plan
- `.apm/Implementation_Plan.md` - Task 12.6 详细说明（line 291-309）

### 相关代码
- BlacklistFieldChecker (当前): `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/BlacklistFieldChecker.java`
- AbstractRuleChecker: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/AbstractRuleChecker.java`

---

## 注意事项

### 1. PlainSelect 类型转换
```java
// SELECT 有多种 SelectBody 类型
if (!(select.getSelectBody() instanceof PlainSelect)) {
    return;  // Skip SetOperations (UNION, INTERSECT, etc.)
}

PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
Expression where = plainSelect.getWhere();
```

### 2. 字段提取实现
```java
// 使用 ExpressionVisitorAdapter 遍历表达式树
private static class FieldCollectorVisitor extends ExpressionVisitorAdapter {
    private final Set<String> fields = new HashSet<>();

    @Override
    public void visit(Column column) {
        fields.add(column.getColumnName().toLowerCase());
    }
}
```

### 3. 保留 vs 删除方法
```java
// ✅ RETAINED - 黑名单匹配逻辑（非工具方法）
private boolean isBlacklisted(String field, Set<String> blacklist) { ... }

// ❌ DELETED - 工具方法（已从 AbstractRuleChecker 删除）
// protected Expression extractWhere(Statement stmt) { ... }
// protected Set<String> extractFields(Expression where) { ... }
```

### 4. 通配符模式
```java
// 支持后缀通配符
if (blacklistEntry.endsWith("*")) {
    String prefix = blacklistEntry.substring(0, blacklistEntry.length() - 1);
    if (field.startsWith(prefix)) {
        return true;
    }
}
```

---

## 执行时间线

- **预计时间**: 1 工作日
- **上午**:
  - 迁移 BlacklistFieldChecker.java（删除 check，添加 visitSelect）
  - 实现 extractFieldsFromExpression() 和 FieldCollectorVisitor
  - 编写 Test 1-5（黑名单检测、混合字段、通配符）
- **下午**:
  - 编写 Test 6-10（边界情况、其他语句、字段迁移）
  - 运行所有测试，验证通过
  - 验证行为一致性（与旧实现对比）

---

**Created**: 2025-12-22
**Phase**: 12 - Core Architecture Unification
**Agent**: Agent_Architecture_Refactoring
**Task ID**: 12.6
**Dependencies**: Task 12.1, 12.2, 12.3, 12.4 (All Completed)
**Parallel**: Task 12.5, 12.7, 12.8 (Can execute concurrently)
