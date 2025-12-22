package com.footstone.sqlguard.visitor;

import com.footstone.sqlguard.core.model.SqlContext;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;

/**
 * Unified visitor interface for processing JSqlParser Statement types.
 * <p>
 * This interface follows the Visitor pattern, providing type-safe dispatch
 * for different SQL statement types (Select/Update/Delete/Insert) without
 * requiring instanceof chains.
 * </p>
 *
 * <h2>Design Motivation</h2>
 * <p>
 * Prior to this abstraction, RuleChecker implementations used repetitive code:
 * </p>
 * <pre>{@code
 * // OLD: instanceof chain (repeated in every Checker)
 * public void check(SqlContext context, ValidationResult result) {
 *     Statement stmt = context.getStatement();
 *     if (stmt instanceof Select) {
 *         // handle Select
 *     } else if (stmt instanceof Update) {
 *         // handle Update
 *     } else if (stmt instanceof Delete) {
 *         // handle Delete
 *     }
 * }
 * }</pre>
 * <p>
 * StatementVisitor eliminates this repetition through centralized dispatch:
 * </p>
 * <pre>{@code
 * // NEW: visitor pattern (dispatch once in AbstractRuleChecker)
 * public abstract class AbstractRuleChecker implements RuleChecker {
 *     @Override
 *     public final void check(SqlContext context, ValidationResult result) {
 *         Statement stmt = context.getStatement();
 *         if (stmt instanceof Select) {
 *             visitSelect((Select) stmt, context);
 *         } else if (stmt instanceof Update) {
 *             visitUpdate((Update) stmt, context);
 *         }
 *         // ... dispatch logic centralized
 *     }
 * }
 *
 * // Checker implementations only override relevant methods
 * public class NoWhereClauseChecker extends AbstractRuleChecker {
 *     @Override
 *     protected void visitUpdate(Update update, SqlContext context) {
 *         if (update.getWhere() == null) {
 *             addViolation("Missing WHERE clause");
 *         }
 *     }
 *
 *     @Override
 *     protected void visitDelete(Delete delete, SqlContext context) {
 *         if (delete.getWhere() == null) {
 *             addViolation("Missing WHERE clause");
 *         }
 *     }
 *     // SELECT/INSERT not relevant, no need to override
 * }
 * }</pre>
 *
 * <h2>Usage Pattern</h2>
 * <ol>
 *   <li><b>RuleChecker</b>: Extend this interface to implement validation logic for specific Statement types</li>
 *   <li><b>StatementRewriter</b>: Extend this interface to implement SQL rewriting logic (future use)</li>
 *   <li><b>AbstractRuleChecker</b>: Implements template method pattern, dispatches to visitXxx() methods</li>
 * </ol>
 *
 * <h2>Default Implementations</h2>
 * <p>
 * All visit methods have default empty implementations, allowing subclasses to
 * override only the methods they need. For example:
 * </p>
 * <ul>
 *   <li>NoWhereClauseChecker only overrides visitUpdate() and visitDelete()</li>
 *   <li>BlacklistFieldChecker only overrides visitSelect()</li>
 *   <li>DummyConditionChecker overrides visitUpdate() and visitDelete()</li>
 * </ul>
 *
 * <h2>Benefits</h2>
 * <ul>
 *   <li><b>Type Safety</b>: Each visit method receives correctly typed Statement (no casting needed)</li>
 *   <li><b>Extensibility</b>: Adding new Statement types only requires updating the interface</li>
 *   <li><b>Clarity</b>: Clear separation of concerns - dispatch logic vs. validation logic</li>
 *   <li><b>Reusability</b>: Same abstraction works for RuleChecker, StatementRewriter, and future extensions</li>
 * </ul>
 *
 * @since 1.1.0
 * @see com.footstone.sqlguard.validator.rule.RuleChecker
 * @see com.footstone.sqlguard.validator.rule.AbstractRuleChecker
 */
public interface StatementVisitor {

    /**
     * Visit a SELECT statement.
     * <p>
     * Override this method to process SELECT statements. For example:
     * </p>
     * <pre>{@code
     * @Override
     * protected void visitSelect(Select select, SqlContext context) {
     *     PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
     *     List<SelectItem> items = plainSelect.getSelectItems();
     *     // Process SELECT items...
     * }
     * }</pre>
     *
     * @param select  the parsed SELECT statement (type-safe, no casting needed)
     * @param context the execution context containing SQL metadata (mapperId, rowBounds, etc.)
     */
    default void visitSelect(Select select, SqlContext context) {
        // Default: empty implementation (no-op)
        // Subclasses override this method to add SELECT-specific logic
    }

    /**
     * Visit an UPDATE statement.
     * <p>
     * Override this method to process UPDATE statements. For example:
     * </p>
     * <pre>{@code
     * @Override
     * protected void visitUpdate(Update update, SqlContext context) {
     *     Expression where = update.getWhere();
     *     if (where == null) {
     *         addViolation("UPDATE without WHERE clause");
     *     }
     * }
     * }</pre>
     *
     * @param update  the parsed UPDATE statement (type-safe, no casting needed)
     * @param context the execution context containing SQL metadata
     */
    default void visitUpdate(Update update, SqlContext context) {
        // Default: empty implementation (no-op)
    }

    /**
     * Visit a DELETE statement.
     * <p>
     * Override this method to process DELETE statements. For example:
     * </p>
     * <pre>{@code
     * @Override
     * protected void visitDelete(Delete delete, SqlContext context) {
     *     Expression where = delete.getWhere();
     *     if (where == null) {
     *         addViolation("DELETE without WHERE clause");
     *     }
     * }
     * }</pre>
     *
     * @param delete  the parsed DELETE statement (type-safe, no casting needed)
     * @param context the execution context containing SQL metadata
     */
    default void visitDelete(Delete delete, SqlContext context) {
        // Default: empty implementation (no-op)
    }

    /**
     * Visit an INSERT statement.
     * <p>
     * Override this method to process INSERT statements. For example:
     * </p>
     * <pre>{@code
     * @Override
     * protected void visitInsert(Insert insert, SqlContext context) {
     *     Table table = insert.getTable();
     *     List<Column> columns = insert.getColumns();
     *     // Process INSERT columns...
     * }
     * }</pre>
     *
     * @param insert  the parsed INSERT statement (type-safe, no casting needed)
     * @param context the execution context containing SQL metadata
     */
    default void visitInsert(Insert insert, SqlContext context) {
        // Default: empty implementation (no-op)
    }
}

