package com.footstone.sqlguard.validator.rule;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for rule checkers implementing the Template Method pattern.
 * <p>
 * This class provides a final {@link #check(SqlContext, ValidationResult)} template method
 * that automatically dispatches to the appropriate {@code visitXxx()} method based on
 * the Statement type. Subclasses only need to override the specific {@code visitXxx()}
 * methods for the Statement types they need to validate.
 * </p>
 *
 * <h2>Template Method Pattern</h2>
 * <p>
 * The {@link #check(SqlContext, ValidationResult)} method implements the template:
 * </p>
 * <ol>
 *   <li>Store context and result in ThreadLocal for convenience</li>
 *   <li>Extract Statement from SqlContext</li>
 *   <li>Dispatch to appropriate visitXxx() method based on Statement type</li>
 *   <li>Handle errors gracefully (log but don't fail validation)</li>
 *   <li>Clean up ThreadLocal to prevent memory leaks</li>
 * </ol>
 *
 * <h2>Migration from Old Architecture</h2>
 * <p>
 * <b>OLD (before Phase 12):</b> Subclasses override check() and use utility methods
 * </p>
 * <pre>{@code
 * public class NoWhereClauseChecker extends AbstractRuleChecker {
 *     @Override
 *     public void check(SqlContext context, ValidationResult result) {
 *         Statement stmt = context.getStatement();
 *         if (stmt instanceof Update) {
 *             Expression where = extractWhere(stmt);  // ❌ Utility method
 *             if (where == null) {
 *                 result.addViolation(...);
 *             }
 *         }
 *     }
 * }
 * }</pre>
 * <p>
 * <b>NEW (Phase 12 onwards):</b> Subclasses override visitXxx() and use JSqlParser API directly
 * </p>
 * <pre>{@code
 * public class NoWhereClauseChecker extends AbstractRuleChecker {
 *     @Override
 *     protected void visitUpdate(Update update, SqlContext context) {
 *         Expression where = update.getWhere();  // ✅ Direct API
 *         if (where == null) {
 *             addViolation(RiskLevel.CRITICAL, "UPDATE without WHERE clause");
 *         }
 *     }
 *
 *     @Override
 *     protected void visitDelete(Delete delete, SqlContext context) {
 *         Expression where = delete.getWhere();  // ✅ Direct API
 *         if (where == null) {
 *             addViolation(RiskLevel.CRITICAL, "DELETE without WHERE clause");
 *         }
 *     }
 *     // No need to override visitSelect/visitInsert
 * }
 * }</pre>
 *
 * <h2>Benefits</h2>
 * <ul>
 *   <li><b>Single Dispatch Point</b>: instanceof logic centralized, not repeated in every Checker</li>
 *   <li><b>Type Safety</b>: visitXxx() methods receive correctly typed Statement parameters</li>
 *   <li><b>Selective Override</b>: Override only the visitXxx() methods you need</li>
 *   <li><b>Error Handling</b>: Automatic exception handling with degradation</li>
 *   <li><b>Clean API</b>: No intermediate utility methods, use JSqlParser directly</li>
 * </ul>
 *
 * @since 1.0.0 (refactored in 1.1.0 for visitor pattern)
 */
public abstract class AbstractRuleChecker implements RuleChecker {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final CheckerConfig config;

    /**
     * ThreadLocal storage for current validation context.
     * Used by {@link #addViolation(RiskLevel, String)} helper method.
     */
    private final ThreadLocal<SqlContext> currentContext = new ThreadLocal<>();
    private final ThreadLocal<ValidationResult> currentResult = new ThreadLocal<>();

    protected AbstractRuleChecker(CheckerConfig config) {
        this.config = config;
    }

    /**
     * Template method that dispatches to visitXxx() methods.
     * <p>
     * <b>DO NOT OVERRIDE THIS METHOD</b> in subclasses. Override the specific
     * visitXxx() methods instead.
     * </p>
     *
     * @param context the SQL execution context
     * @param result  the validation result accumulator
     * @since 1.0.0
     */
    @Override
    public final void check(SqlContext context, ValidationResult result) {
        // Store context for addViolation() helper
        currentContext.set(context);
        currentResult.set(result);

        try {
            Statement stmt = context.getStatement();

            // Dispatch to appropriate visitXxx() method
            if (stmt instanceof Select) {
                visitSelect((Select) stmt, context);
            } else if (stmt instanceof Update) {
                visitUpdate((Update) stmt, context);
            } else if (stmt instanceof Delete) {
                visitDelete((Delete) stmt, context);
            } else if (stmt instanceof Insert) {
                visitInsert((Insert) stmt, context);
            } else if (stmt != null) {
                logger.warn("Unknown Statement type: {}", stmt.getClass().getName());
            }
        } catch (Exception e) {
            // Degradation: log error but don't fail validation
            logger.warn("Checker {} encountered error while processing {}: {}",
                    getClass().getSimpleName(),
                    context.getMapperId(),
                    e.getMessage(),
                    e);
        } finally {
            // Clean up ThreadLocal to prevent memory leaks
            currentContext.remove();
            currentResult.remove();
        }
    }

    /**
     * Check if this rule checker is enabled.
     * <p>
     * Default implementation checks the config's enabled flag.
     * Subclasses can override for custom logic.
     * </p>
     *
     * @return true if enabled, false otherwise
     * @since 1.0.0
     */
    @Override
    public boolean isEnabled() {
        return config != null && config.isEnabled();
    }

    // ==================== Visitor Methods (default empty implementations) ====================

    /**
     * Visit a SELECT statement.
     * <p>
     * Override this method to validate SELECT statements.
     * Default implementation does nothing.
     * </p>
     *
     * @param select  the SELECT statement (already cast, type-safe)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    public void visitSelect(Select select, SqlContext context) {
        // Default: no-op (subclasses override if needed)
    }

    /**
     * Visit an UPDATE statement.
     * <p>
     * Override this method to validate UPDATE statements.
     * Default implementation does nothing.
     * </p>
     *
     * @param update  the UPDATE statement (already cast, type-safe)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    public void visitUpdate(Update update, SqlContext context) {
        // Default: no-op (subclasses override if needed)
    }

    /**
     * Visit a DELETE statement.
     * <p>
     * Override this method to validate DELETE statements.
     * Default implementation does nothing.
     * </p>
     *
     * @param delete  the DELETE statement (already cast, type-safe)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    public void visitDelete(Delete delete, SqlContext context) {
        // Default: no-op (subclasses override if needed)
    }

    /**
     * Visit an INSERT statement.
     * <p>
     * Override this method to validate INSERT statements.
     * Default implementation does nothing.
     * </p>
     *
     * @param insert  the INSERT statement (already cast, type-safe)
     * @param context the SQL execution context
     * @since 1.1.0
     */
    @Override
    public void visitInsert(Insert insert, SqlContext context) {
        // Default: no-op (subclasses override if needed)
    }

    // ==================== Helper Methods ====================

    /**
     * Add a violation to the current validation result.
     * <p>
     * This is a convenience method that uses the ThreadLocal context and result
     * stored by the {@link #check(SqlContext, ValidationResult)} template method.
     * </p>
     *
     * @param level   the risk level of the violation
     * @param message the violation message
     * @since 1.0.0
     */
    protected void addViolation(RiskLevel level, String message) {
        ValidationResult res = currentResult.get();
        if (res != null) {
            res.addViolation(level, message, null);
        } else {
            logger.warn("addViolation called outside check() context");
        }
    }

    /**
     * Add a violation to the current validation result with a suggestion.
     * <p>
     * This is a convenience method that uses the ThreadLocal context and result
     * stored by the {@link #check(SqlContext, ValidationResult)} template method.
     * </p>
     *
     * @param level      the risk level of the violation
     * @param message    the violation message
     * @param suggestion the suggested fix
     * @since 1.1.0
     */
    protected void addViolation(RiskLevel level, String message, String suggestion) {
        ValidationResult res = currentResult.get();
        if (res != null) {
            res.addViolation(level, message, suggestion);
        } else {
            logger.warn("addViolation called outside check() context");
        }
    }

    /**
     * Get the current SQL context.
     * <p>
     * This method provides access to the current SqlContext during visitXxx() execution.
     * Returns null if called outside the check() context.
     * </p>
     *
     * @return the current SQL context, or null if not in check() context
     * @since 1.1.0
     */
    protected SqlContext getCurrentContext() {
        return currentContext.get();
    }

    /**
     * Get the current validation result.
     * <p>
     * This method provides access to the current ValidationResult during visitXxx() execution.
     * Returns null if called outside the check() context.
     * </p>
     *
     * @return the current validation result, or null if not in check() context
     * @since 1.1.0
     */
    protected ValidationResult getCurrentResult() {
        return currentResult.get();
    }

    /**
     * Get the checker configuration.
     *
     * @return the checker configuration
     * @since 1.0.0
     */
    protected CheckerConfig getConfig() {
        return config;
    }

    // ==================== DELETED: Old Utility Methods ====================
    // The following methods have been REMOVED in Phase 12 refactoring:
    // - protected Expression extractWhere(Statement stmt)
    // - protected String extractTableName(Statement stmt)
    // - protected Set<String> extractFields(Expression expr)
    // - protected boolean isDummyCondition(Expression expr)
    // - protected boolean isConstant(Expression expr)
    // - private static class FieldExtractorVisitor
    //
    // Rationale: These utility methods added unnecessary abstraction layers.
    // Subclasses should use JSqlParser API directly:
    // - update.getWhere() instead of extractWhere(stmt)
    // - delete.getTable().getName() instead of extractTableName(stmt)
    // - select.getSelectBody().getSelectItems() instead of extractFields(stmt)
    //
    // Benefits of direct API usage:
    // 1. Better type safety (no casting needed)
    // 2. Clearer code (explicit API calls)
    // 3. Easier debugging (direct JSqlParser stack traces)
    // 4. Less maintenance (no intermediate layer to maintain)
}






