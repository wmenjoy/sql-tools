package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Rule checker that detects SQL statements missing WHERE clauses.
 *
 * <p>NoWhereClauseChecker validates UPDATE and DELETE statements to ensure they include
 * WHERE clauses. Missing WHERE clauses can cause catastrophic consequences:</p>
 *
 * <ul>
 *   <li><strong>DELETE without WHERE:</strong> Irreversible deletion of all table data</li>
 *   <li><strong>UPDATE without WHERE:</strong> Irreversible modification of all table rows</li>
 * </ul>
 *
 * <p><strong>Risk Level:</strong> CRITICAL - This is the most severe validation check</p>
 *
 * <p><strong>Implementation Details:</strong></p>
 * <ul>
 *   <li>Uses visitor pattern: overrides visitUpdate() and visitDelete()</li>
 *   <li>Direct API usage: update.getWhere() / delete.getWhere() instead of utility methods</li>
 *   <li>Does NOT check SELECT (handled by NoPaginationChecker with risk stratification)</li>
 *   <li>Does NOT check INSERT (INSERT has no WHERE clause by design)</li>
 * </ul>
 *
 * <p><strong>Edge Cases:</strong></p>
 * <ul>
 *   <li>INSERT statements are skipped (default visitInsert() is no-op)</li>
 *   <li>SELECT statements are skipped (default visitSelect() is no-op)</li>
 *   <li>Statements with dummy conditions like "WHERE 1=1" pass this check
 *       (handled by DummyConditionChecker)</li>
 *   <li>Complex WHERE clauses always pass regardless of effectiveness</li>
 * </ul>
 *
 * <p><strong>Migration from Phase 11:</strong></p>
 * <pre>{@code
 *
 * // NEW (Phase 12 onwards)
 * protected void visitUpdate(Update update, SqlContext context) {
 *     Expression where = update.getWhere();  // Direct API
 *     if (where == null) {
 *         addViolation(RiskLevel.CRITICAL, "UPDATE without WHERE");
 *     }
 * }
 * }</pre>
 *
 * @see AbstractRuleChecker
 * @see NoWhereClauseConfig
 * @since 1.0.0 (refactored in 1.1.0 for visitor pattern)
 */
public class NoWhereClauseChecker extends AbstractRuleChecker {

    private final NoWhereClauseConfig config;

    /**
     * Creates a NoWhereClauseChecker with the specified configuration.
     *
     * @param config the configuration for this checker
     */
    public NoWhereClauseChecker(NoWhereClauseConfig config) {
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
     * Validates UPDATE statements for missing WHERE clauses.
     *
     * <p>Checks if the UPDATE statement has a WHERE clause. If WHERE is null,
     * adds a CRITICAL violation.</p>
     *
     * <p><strong>Direct API Usage:</strong></p>
     * <pre>{@code
     * Expression where = update.getWhere();  // JSqlParser API
     * String tableName = update.getTable().getName();  // JSqlParser API
     * }</pre>
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

        // Direct API call to get WHERE clause
        Expression where = update.getWhere();

        // If WHERE clause is missing, add CRITICAL violation
        if (where == null) {
            addViolation(
                RiskLevel.CRITICAL,
                "UPDATE语句缺少WHERE条件,可能导致全表更新");
        }
    }

    /**
     * Validates DELETE statements for missing WHERE clauses.
     *
     * <p>Checks if the DELETE statement has a WHERE clause. If WHERE is null,
     * adds a CRITICAL violation.</p>
     *
     * <p><strong>Direct API Usage:</strong></p>
     * <pre>{@code
     * Expression where = delete.getWhere();  // JSqlParser API
     * String tableName = delete.getTable().getName();  // JSqlParser API
     * }</pre>
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

        // Direct API call to get WHERE clause
        Expression where = delete.getWhere();

        // If WHERE clause is missing, add CRITICAL violation
        if (where == null) {
            addViolation(
                RiskLevel.CRITICAL,
                "DELETE语句缺少WHERE条件,可能导致全表删除");
        }
    }

    // ==================== MIGRATION NOTES ====================
    // The following methods have been REMOVED in Phase 12 migration:
    // - public void check(SqlContext context, ValidationResult result)
    //   Replaced by: AbstractRuleChecker template method calls visitUpdate/visitDelete
    //
    // - Expression extractWhere(Statement stmt)
    //   Replaced by: Direct API calls update.getWhere() / delete.getWhere()
    //
    // Benefits of new architecture:
    // 1. Type safety: No instanceof checks or casting
    // 2. Clarity: Direct API usage is self-documenting
    // 3. Performance: Single parse + type dispatch (vs. parse per Checker)
    // 4. Maintainability: Less code, fewer intermediate layers
}
