package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.ExpressionVisitorAdapter;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

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
 * <p><strong>Migration from Phase 11:</strong></p>
 * <pre>{@code
 * // OLD (before Phase 12)
 * public void check(SqlContext context, ValidationResult result) {
 *     Statement stmt = context.getStatement();
 *     String tableName = extractTableName(stmt);  // Utility method
 *     Expression where = extractWhere(stmt);      // Utility method
 *     Set<String> whereFields = extractFields(where);  // Utility method
 *     ...
 * }
 *
 * // NEW (Phase 12 onwards)
 * protected void visitSelect(Select select, SqlContext context) {
 *     PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
 *     String tableName = extractTableNameFromSelect(plainSelect);  // Local method
 *     Expression where = plainSelect.getWhere();  // Direct API
 *     Set<String> whereFields = extractFieldsFromExpression(where);  // Local method
 *     ...
 * }
 * }</pre>
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
     * <p><strong>Direct API Usage:</strong></p>
     * <pre>{@code
     * PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
     * String tableName = extractTableNameFromSelect(plainSelect);  // Local method
     * Expression where = plainSelect.getWhere();  // JSqlParser API
     * }</pre>
     *
     * @param select the SELECT statement (type-safe, no casting needed)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    public void visitSelect(Select select, SqlContext context) {
        // Skip if disabled
        if (!isEnabled()) {
            return;
        }

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

        // Use common validation logic
        validateWhereFields(tableName, where);
    }

    /**
     * Validates UPDATE statements for required whitelist fields in WHERE clause.
     *
     * <p>Checks if the WHERE clause includes at least one required field from the
     * table-specific whitelist.</p>
     *
     * @param update the UPDATE statement (type-safe, no casting needed)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    public void visitUpdate(Update update, SqlContext context) {
        // Skip if disabled
        if (!isEnabled()) {
            return;
        }

        // Extract table name
        String tableName = update.getTable() != null ? update.getTable().getName() : null;
        if (tableName == null) {
            return;
        }

        // Extract WHERE clause
        Expression where = update.getWhere();
        if (where == null) {
            return;  // No WHERE clause means nothing to check
        }

        // Use common validation logic
        validateWhereFields(tableName, where);
    }

    /**
     * Validates DELETE statements for required whitelist fields in WHERE clause.
     *
     * <p>Checks if the WHERE clause includes at least one required field from the
     * table-specific whitelist.</p>
     *
     * @param delete the DELETE statement (type-safe, no casting needed)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    public void visitDelete(Delete delete, SqlContext context) {
        // Skip if disabled
        if (!isEnabled()) {
            return;
        }

        // Extract table name
        String tableName = delete.getTable() != null ? delete.getTable().getName() : null;
        if (tableName == null) {
            return;
        }

        // Extract WHERE clause
        Expression where = delete.getWhere();
        if (where == null) {
            return;  // No WHERE clause means nothing to check
        }

        // Use common validation logic
        validateWhereFields(tableName, where);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Common validation logic for checking if WHERE clause contains required whitelist fields.
     *
     * @param tableName the table name
     * @param where the WHERE expression
     */
    private void validateWhereFields(String tableName, Expression where) {
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

    // ==================== MIGRATION NOTES ====================
    // The following methods have been REMOVED in Phase 12 migration:
    // - public void check(SqlContext context, ValidationResult result)
    //   Replaced by: AbstractRuleChecker template method calls visitSelect()
    //
    // - Expression extractWhere(Statement stmt)
    //   Replaced by: Direct API call plainSelect.getWhere()
    //
    // - String extractTableName(Statement stmt)
    //   Replaced by: Local method extractTableNameFromSelect()
    //
    // - Set<String> extractFields(Expression expr)
    //   Replaced by: Local method extractFieldsFromExpression() with FieldCollectorVisitor
    //
    // Benefits of new architecture:
    // 1. Type safety: No instanceof checks for statement type
    // 2. Clarity: Direct API usage is self-documenting
    // 3. Performance: Single parse + type dispatch (vs. parse per Checker)
    // 4. Maintainability: Less code, fewer intermediate layers
}
