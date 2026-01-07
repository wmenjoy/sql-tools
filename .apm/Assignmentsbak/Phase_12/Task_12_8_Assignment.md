---
phase: 12
task_id: 12.8
task_name: NoPaginationChecker Migration
agent: Agent_Architecture_Refactoring
estimated_duration: 1 day
dependencies: [Task_12.1, Task_12.2, Task_12.3, Task_12.4]
parallel_with: [Task_12.5, Task_12.6, Task_12.7]
---

# Task 12.8 Assignment: NoPaginationChecker Migration

## 任务目标

将 NoPaginationChecker 从 check() 实现迁移到 visitSelect() 方法，使用 JSqlParser API 直接检测 LIMIT/OFFSET 子句，删除旧的 check() 方法实现，更新测试使用 statement 字段，验证分页检测逻辑一致性（包括 RowBounds、IPage、风险分层）。

---

## 背景说明

### 当前架构（Task 12.4 之前）
```java
public class NoPaginationChecker extends AbstractRuleChecker {
    @Override
    public void check(SqlContext context, ValidationResult result) {
        if (!isEnabled()) {
            return;
        }

        Statement stmt = context.getParsedSql();
        if (!(stmt instanceof Select)) {
            return;
        }

        Select select = (Select) stmt;

        // Check pagination
        if (hasPaginationLimit(select, context)) {
            return;
        }

        // Check whitelist
        if (isWhitelisted(select, context)) {
            return;
        }

        // Assess risk
        assessNoPaginationRisk(select, context, result);
    }

    private void assessNoPaginationRisk(...) {
        Expression where = extractWhere(select);  // ❌ Utility method
        if (where == null || isDummyCondition(where)) {  // ❌ Utility method
            result.addViolation(CRITICAL, ...);
            return;
        }

        Set<String> whereFields = extractFields(where);  // ❌ Utility method
        if (allFieldsBlacklisted(whereFields, blacklistConfig)) {
            result.addViolation(HIGH, ...);
        }
    }
}
```

**问题**：
- 使用 extractWhere() 工具方法（Task 12.4 已删除）
- 使用 isDummyCondition() 工具方法（Task 12.4 已删除）
- 使用 extractFields() 工具方法（Task 12.4 已删除）
- 使用 extractTableName() 工具方法（Task 12.4 已删除）

### 目标架构（Task 12.8 之后）
```java
public class NoPaginationChecker extends AbstractRuleChecker {
    @Override
    protected void visitSelect(Select select, SqlContext context) {
        // Check pagination
        if (hasPaginationLimit(select, context)) {
            return;
        }

        // Check whitelist
        if (isWhitelisted(select, context)) {
            return;
        }

        // Assess risk
        assessNoPaginationRisk(select, context);
    }

    private void assessNoPaginationRisk(Select select, SqlContext context) {
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        Expression where = plainSelect.getWhere();  // ✅ Direct API

        if (where == null || isDummyConditionExpression(where)) {  // Local method
            addViolation(CRITICAL, "SELECT查询无条件且无分页限制...");
            return;
        }

        Set<String> whereFields = extractFieldsFromExpression(where);  // Local method
        if (allFieldsBlacklisted(whereFields, blacklistConfig)) {
            addViolation(HIGH, "...);
        }
    }

    // Local methods
    private boolean isDummyConditionExpression(Expression where) { ... }
    private Set<String> extractFieldsFromExpression(Expression where) { ... }
    private String extractTableNameFromSelect(PlainSelect select) { ... }
}
```

---

## 实现要求

### 1. NoPaginationChecker 迁移

**位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/NoPaginationChecker.java`

**核心变更说明**:
1. ✅ 删除 `check()` 方法
2. ✅ 添加 `visitSelect()` 方法
3. ✅ 直接使用 `plainSelect.getWhere()` 替代 `extractWhere()`
4. ✅ 实现本地方法：`isDummyConditionExpression()`, `extractFieldsFromExpression()`, `extractTableNameFromSelect()`
5. ✅ 保留现有逻辑：`hasPaginationLimit()`, `isWhitelisted()`, `assessNoPaginationRisk()`, `allFieldsBlacklisted()`, `matchesWildcardPattern()`, `UniqueKeyVisitor`
6. ✅ 使用 `addViolation()` 辅助方法

**完整迁移代码**（关键部分）:

```java
package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig;
import com.footstone.sqlguard.validator.rule.impl.NoPaginationConfig;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.session.RowBounds;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checker for detecting SELECT queries completely lacking pagination limits.
 *
 * <p>This checker identifies queries without any form of pagination (no LIMIT clause, no
 * RowBounds, no IPage parameter) and applies variable risk stratification based on WHERE
 * clause characteristics.</p>
 *
 * <p><strong>Risk Stratification:</strong></p>
 * <ul>
 *   <li><strong>CRITICAL:</strong> No WHERE clause or dummy WHERE (e.g., "1=1")</li>
 *   <li><strong>HIGH:</strong> WHERE clause uses ONLY blacklist fields</li>
 *   <li><strong>MEDIUM:</strong> Normal WHERE clause (only if enforceForAllQueries=true)</li>
 * </ul>
 *
 * @see NoPaginationConfig
 * @see PaginationPluginDetector
 * @since 1.0.0 (refactored in 1.1.0 for visitor pattern)
 */
public class NoPaginationChecker extends AbstractRuleChecker {

    private final PaginationPluginDetector pluginDetector;
    private final BlacklistFieldsConfig blacklistConfig;
    private final NoPaginationConfig config;

    public NoPaginationChecker(PaginationPluginDetector pluginDetector,
                               BlacklistFieldsConfig blacklistConfig,
                               NoPaginationConfig config) {
        super(config);  // Pass config to AbstractRuleChecker
        this.pluginDetector = pluginDetector;
        this.blacklistConfig = blacklistConfig;
        this.config = config;
    }

    @Override
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Validates SELECT statements for missing pagination.
     *
     * @param select the SELECT statement (type-safe)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    protected void visitSelect(Select select, SqlContext context) {
        // Check for pagination
        if (hasPaginationLimit(select, context)) {
            return;
        }

        // Check whitelist exemptions
        if (isWhitelisted(select, context)) {
            return;
        }

        // Assess risk and add violation
        assessNoPaginationRisk(select, context);
    }

    // ==================== Pagination Detection ====================

    private boolean hasPaginationLimit(Select select, SqlContext context) {
        // Check SQL LIMIT clause
        String sql = select.toString().toUpperCase();
        if (sql.contains("LIMIT")) {
            return true;
        }

        // Check RowBounds
        Object rowBounds = context.getRowBounds();
        if (rowBounds != null && rowBounds instanceof RowBounds) {
            RowBounds rb = (RowBounds) rowBounds;
            if (rb != RowBounds.DEFAULT) {
                return true;
            }
        }

        // Check IPage parameter
        if (hasPageParameter(context.getParams())) {
            return true;
        }

        return false;
    }

    private boolean hasPageParameter(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return false;
        }

        for (Object value : params.values()) {
            if (value != null) {
                String className = value.getClass().getName();
                if (className.contains("IPage") ||
                        className.contains("com.baomidou.mybatisplus.core.metadata.IPage")) {
                    return true;
                }
            }
        }

        return false;
    }

    // ==================== Whitelist Exemption ====================

    private boolean isWhitelisted(Select select, SqlContext context) {
        // Check mapperId whitelist
        String mapperId = context.getMapperId();
        if (mapperId != null && matchesWildcardPattern(mapperId, config.getWhitelistMapperIds())) {
            return true;
        }

        // Check table whitelist
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        String tableName = extractTableNameFromSelect(plainSelect);
        if (tableName != null && config.getWhitelistTables().contains(tableName)) {
            return true;
        }

        // Check unique key condition
        if (hasUniqueKeyCondition(plainSelect, context)) {
            return true;
        }

        return false;
    }

    private boolean hasUniqueKeyCondition(PlainSelect plainSelect, SqlContext context) {
        // Extract WHERE clause
        Expression where = plainSelect.getWhere();
        if (where == null) {
            return false;
        }

        // Build unique key set
        Set<String> uniqueKeys = new HashSet<>();
        uniqueKeys.add("id");
        for (String key : config.getUniqueKeyFields()) {
            uniqueKeys.add(key.toLowerCase());
        }

        // Use visitor to find unique key equality
        UniqueKeyVisitor visitor = new UniqueKeyVisitor(uniqueKeys);
        where.accept(visitor);
        return visitor.isFoundUniqueKeyEquals();
    }

    // ==================== Risk Assessment ====================

    private void assessNoPaginationRisk(Select select, SqlContext context) {
        PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
        Expression where = plainSelect.getWhere();

        // CRITICAL: No WHERE or dummy WHERE
        if (where == null || isDummyConditionExpression(where)) {
            addViolation(
                    RiskLevel.CRITICAL,
                    "SELECT查询无条件且无分页限制,可能返回全表数据导致内存溢出");
            return;
        }

        // Extract fields from WHERE clause
        Set<String> whereFields = extractFieldsFromExpression(where);

        // HIGH: WHERE uses ONLY blacklist fields
        if (!whereFields.isEmpty() && allFieldsBlacklisted(whereFields, blacklistConfig)) {
            String fieldsStr = String.join(", ", whereFields);
            addViolation(
                    RiskLevel.HIGH,
                    String.format("SELECT查询条件只有黑名单字段[%s]且无分页,可能返回大量数据", fieldsStr));
            return;
        }

        // MEDIUM: Normal WHERE (only if enforceForAllQueries=true)
        if (config.isEnforceForAllQueries()) {
            addViolation(
                    RiskLevel.MEDIUM,
                    "SELECT查询缺少分页限制,建议添加LIMIT或使用分页");
        }
    }

    private boolean allFieldsBlacklisted(Set<String> whereFields,
                                         BlacklistFieldsConfig blacklistConfig) {
        if (whereFields.isEmpty()) {
            return false;
        }

        Set<String> blacklist = blacklistConfig.getFields();

        for (String field : whereFields) {
            String fieldLower = field.toLowerCase();
            boolean isBlacklisted = false;

            // Check exact match or wildcard pattern
            for (String blacklistPattern : blacklist) {
                String patternLower = blacklistPattern.toLowerCase();

                // Exact match
                if (fieldLower.equals(patternLower)) {
                    isBlacklisted = true;
                    break;
                }

                // Wildcard pattern
                if (patternLower.endsWith("*")) {
                    String prefix = patternLower.substring(0, patternLower.length() - 1);
                    if (fieldLower.startsWith(prefix)) {
                        isBlacklisted = true;
                        break;
                    }
                }
            }

            // If any field is NOT blacklisted, return false
            if (!isBlacklisted) {
                return false;
            }
        }

        // All fields are blacklisted
        return true;
    }

    private boolean matchesWildcardPattern(String text, List<String> patterns) {
        if (text == null || patterns == null || patterns.isEmpty()) {
            return false;
        }

        for (String pattern : patterns) {
            if (pattern == null) {
                continue;
            }

            // Exact match
            if (text.equals(pattern)) {
                return true;
            }

            // Wildcard pattern
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            if (text.matches(regex)) {
                return true;
            }
        }

        return false;
    }

    // ==================== Local Helper Methods (NEW) ====================

    /**
     * Checks if the expression is a dummy condition (e.g., WHERE 1=1, WHERE true).
     *
     * @param where the WHERE expression
     * @return true if dummy condition, false otherwise
     */
    private boolean isDummyConditionExpression(Expression where) {
        if (where == null) {
            return false;
        }

        String whereStr = where.toString().trim().toUpperCase();

        // Common dummy conditions
        if (whereStr.equals("1=1") ||
                whereStr.equals("1 = 1") ||
                whereStr.equals("TRUE") ||
                whereStr.equals("'1'='1'")) {
            return true;
        }

        return false;
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

    // ==================== Inner Classes ====================

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

    /**
     * Visitor that detects unique key equality conditions in WHERE clause.
     */
    private static class UniqueKeyVisitor extends ExpressionVisitorAdapter {

        private boolean foundUniqueKeyEquals = false;
        private final Set<String> uniqueKeys;

        public UniqueKeyVisitor(Set<String> uniqueKeys) {
            this.uniqueKeys = uniqueKeys;
        }

        public boolean isFoundUniqueKeyEquals() {
            return foundUniqueKeyEquals;
        }

        @Override
        public void visit(EqualsTo equalsTo) {
            Expression left = equalsTo.getLeftExpression();
            Expression right = equalsTo.getRightExpression();

            // Check if left is a Column and in uniqueKeys
            if (left instanceof Column) {
                String columnName = ((Column) left).getColumnName().toLowerCase();
                if (uniqueKeys.contains(columnName)) {
                    // Check if right is constant or parameter
                    if (right instanceof JdbcParameter ||
                            right instanceof LongValue ||
                            right instanceof StringValue) {
                        foundUniqueKeyEquals = true;
                    }
                }
            }
        }
    }
}
```

---

### 2. TDD 测试用例设计

**位置**: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/NoPaginationCheckerMigrationTest.java`

**完整测试代码**（8个核心测试）:

```java
package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig;
import com.footstone.sqlguard.validator.rule.impl.NoPaginationConfig;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for NoPaginationChecker migration to visitor pattern
 */
@DisplayName("NoPaginationChecker Migration Tests")
public class NoPaginationCheckerMigrationTest {

    private PaginationPluginDetector pluginDetector;
    private BlacklistFieldsConfig blacklistConfig;
    private NoPaginationConfig config;
    private NoPaginationChecker checker;

    @BeforeEach
    public void setUp() {
        pluginDetector = Mockito.mock(PaginationPluginDetector.class);

        blacklistConfig = new BlacklistFieldsConfig();
        blacklistConfig.setEnabled(true);
        Set<String> blacklist = new HashSet<>();
        blacklist.add("deleted");
        blacklist.add("status");
        blacklistConfig.setFields(blacklist);

        config = new NoPaginationConfig();
        config.setEnabled(true);

        checker = new NoPaginationChecker(pluginDetector, blacklistConfig, config);
    }

    @Nested
    @DisplayName("1. No Pagination Tests")
    class NoPaginationTests {

        /**
         * Test 1: SELECT without LIMIT should violate
         */
        @Test
        @DisplayName("visitSelect() - no LIMIT - should add CRITICAL violation")
        public void testVisitSelect_noLimit_violates() throws JSQLParserException {
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.noLimit")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getLevel());
        }

        /**
         * Test 2: SELECT with LIMIT should pass
         */
        @Test
        @DisplayName("visitSelect() - with LIMIT - should pass")
        public void testVisitSelect_withLimit_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users LIMIT 10";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.withLimit")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed());
        }

        /**
         * Test 3: SELECT with RowBounds should pass
         */
        @Test
        @DisplayName("visitSelect() - with RowBounds - should pass")
        public void testVisitSelect_withRowBounds_passes() throws JSQLParserException {
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.withRowBounds")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .rowBounds(new RowBounds(0, 10))  // Pagination present
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed());
        }
    }

    @Nested
    @DisplayName("2. Risk Stratification Tests")
    class RiskStratificationTests {

        /**
         * Test 4: Blacklist-only WHERE should be HIGH risk
         */
        @Test
        @DisplayName("visitSelect() - blacklist-only WHERE - should add HIGH violation")
        public void testVisitSelect_blacklistOnlyWhere_highRisk() throws JSQLParserException {
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

            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.HIGH, result.getViolations().get(0).getLevel());
        }

        /**
         * Test 5: Normal WHERE with enforceForAllQueries should be MEDIUM risk
         */
        @Test
        @DisplayName("visitSelect() - normal WHERE + enforceForAllQueries - should add MEDIUM violation")
        public void testVisitSelect_normalWhere_mediumRisk() throws JSQLParserException {
            config.setEnforceForAllQueries(true);
            NoPaginationChecker strictChecker = new NoPaginationChecker(
                    pluginDetector, blacklistConfig, config);

            String sql = "SELECT * FROM users WHERE name = 'foo'";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.normalWhere")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            strictChecker.check(context, result);

            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.MEDIUM, result.getViolations().get(0).getLevel());
        }
    }

    @Nested
    @DisplayName("3. Whitelist Exemption Tests")
    class WhitelistExemptionTests {

        /**
         * Test 6: Whitelisted table should be skipped
         */
        @Test
        @DisplayName("visitSelect() - whitelisted table - should skip")
        public void testVisitSelect_whitelistedTable_skipped() throws JSQLParserException {
            List<String> whitelistTables = new ArrayList<>();
            whitelistTables.add("users");
            config.setWhitelistTables(whitelistTables);

            NoPaginationChecker whitelistChecker = new NoPaginationChecker(
                    pluginDetector, blacklistConfig, config);

            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.whitelistedTable")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            whitelistChecker.check(context, result);

            assertTrue(result.isPassed());
        }

        /**
         * Test 7: Unique key condition should be skipped
         */
        @Test
        @DisplayName("visitSelect() - unique key condition - should skip")
        public void testVisitSelect_uniqueKeyCondition_skipped() throws JSQLParserException {
            String sql = "SELECT * FROM users WHERE id = 1";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.uniqueKey")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(result.isPassed());
        }
    }

    @Nested
    @DisplayName("4. Statement Field Migration Tests")
    class StatementFieldMigrationTests {

        /**
         * Test 8: Verify statement field is used
         */
        @Test
        @DisplayName("statement field - should work correctly")
        public void testStatementField_works() throws JSQLParserException {
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            SqlContext context = SqlContext.builder()
                    .sql(sql)
                    .mapperId("test.statementField")
                    .type(SqlCommandType.SELECT)
                    .statement(stmt)  // NEW field
                    .build();

            assertNotNull(context.getStatement());
            assertEquals(stmt, context.getStatement());

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertFalse(result.isPassed());
        }
    }
}
```

---

## 验收标准

### 功能验收
- [ ] NoPaginationChecker 迁移完成
- [ ] `check()` 方法已删除
- [ ] `visitSelect()` 方法实现
- [ ] 本地实现：`isDummyConditionExpression()`, `extractFieldsFromExpression()`, `extractTableNameFromSelect()`
- [ ] 保留逻辑：`hasPaginationLimit()`, `isWhitelisted()`, `assessNoPaginationRisk()`, `UniqueKeyVisitor`
- [ ] 使用 `addViolation()` 辅助方法

### 测试验收
- [ ] 8 个测试用例通过

### 行为一致性验收
- [ ] 无分页 → CRITICAL 违规（行为不变）
- [ ] RowBounds/IPage 检测正常（行为不变）
- [ ] 风险分层正常（CRITICAL/HIGH/MEDIUM）（行为不变）
- [ ] 白名单豁免正常（行为不变）

---

## 输出文件

### 修改文件 (1个)
- `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/pagination/impl/NoPaginationChecker.java`

### 新增文件 (1个)
- `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/pagination/impl/NoPaginationCheckerMigrationTest.java`

---

## 依赖与限制

### 依赖
- ✅ Task 12.1-12.4 已完成

### 限制
- ⚠️ **并行执行**：可与 Task 12.5-12.7 并行执行

---

## 注意事项

### 1. Dummy Condition 检测
```java
// 本地实现（不再使用 AbstractRuleChecker.isDummyCondition()）
private boolean isDummyConditionExpression(Expression where) {
    String whereStr = where.toString().toUpperCase();
    return whereStr.equals("1=1") || whereStr.equals("TRUE");
}
```

### 2. RowBounds 检测
```java
// RowBounds.DEFAULT 不算分页
RowBounds rb = (RowBounds) rowBounds;
if (rb != RowBounds.DEFAULT) {
    return true;  // Has pagination
}
```

---

## 执行时间线

- **预计时间**: 1 工作日

---

**Created**: 2025-12-22
**Phase**: 12 - Core Architecture Unification
**Task ID**: 12.8
**Parallel**: Task 12.5, 12.6, 12.7
