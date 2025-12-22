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
    public void visitSelect(Select select, SqlContext context) {
        // Skip if checker is disabled
        if (!isEnabled()) {
            return;
        }

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

