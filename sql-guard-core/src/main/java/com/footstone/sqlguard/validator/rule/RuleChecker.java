package com.footstone.sqlguard.validator.rule;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.visitor.StatementVisitor;

/**
 * Rule checker interface for SQL validation.
 * <p>
 * This interface extends {@link StatementVisitor} to leverage the visitor pattern
 * for type-safe Statement processing. The {@link #check(SqlContext, ValidationResult)}
 * method will be implemented as a template method in {@link AbstractRuleChecker},
 * dispatching to the appropriate visitXxx() methods based on Statement type.
 * </p>
 *
 * <h2>Migration from Old Architecture</h2>
 * <p>
 * <b>OLD Architecture (before Phase 12):</b>
 * </p>
 * <pre>{@code
 * public class NoWhereClauseChecker extends AbstractRuleChecker {
 *     @Override
 *     public void check(SqlContext context, ValidationResult result) {
 *         Statement stmt = context.getStatement();
 *         if (stmt instanceof Update) {
 *             Update update = (Update) stmt;
 *             if (update.getWhere() == null) {
 *                 result.addViolation(...);
 *             }
 *         } else if (stmt instanceof Delete) {
 *             Delete delete = (Delete) stmt;
 *             if (delete.getWhere() == null) {
 *                 result.addViolation(...);
 *             }
 *         }
 *     }
 * }
 * }</pre>
 * <p>
 * <b>NEW Architecture (Phase 12 onwards):</b>
 * </p>
 * <pre>{@code
 * public class NoWhereClauseChecker extends AbstractRuleChecker {
 *     // check() method becomes template method (implemented in AbstractRuleChecker)
 *     // Subclasses only override relevant visitXxx() methods
 *
 *     @Override
 *     protected void visitUpdate(Update update, SqlContext context) {
 *         if (update.getWhere() == null) {
 *             addViolation(context, RiskLevel.CRITICAL, "UPDATE without WHERE clause");
 *         }
 *     }
 *
 *     @Override
 *     protected void visitDelete(Delete delete, SqlContext context) {
 *         if (delete.getWhere() == null) {
 *             addViolation(context, RiskLevel.CRITICAL, "DELETE without WHERE clause");
 *         }
 *     }
 *     // No need to override visitSelect/visitInsert - default implementations are no-op
 * }
 * }</pre>
 *
 * <h2>Benefits of New Architecture</h2>
 * <ul>
 *   <li><b>Type Safety</b>: No casting needed - visitXxx() methods receive correctly typed Statement</li>
 *   <li><b>Clarity</b>: Each visitXxx() method has single responsibility (handle one Statement type)</li>
 *   <li><b>Selective Override</b>: Checkers only implement methods for Statement types they care about</li>
 *   <li><b>Centralized Dispatch</b>: AbstractRuleChecker handles instanceof logic once, not repeated in every Checker</li>
 * </ul>
 *
 * <h2>Usage Pattern</h2>
 * <ol>
 *   <li>Extend {@link AbstractRuleChecker} (do NOT implement RuleChecker directly)</li>
 *   <li>Override relevant visitXxx() methods for Statement types you need to validate</li>
 *   <li>Use {@link AbstractRuleChecker#addViolation} to report violations (available in AbstractRuleChecker)</li>
 *   <li>Override {@link #isEnabled()} to control whether the checker runs (defaults to true)</li>
 * </ol>
 *
 * <h2>Backward Compatibility</h2>
 * <p>
 * Existing RuleChecker implementations that override check() will continue to work
 * during the migration period. However, they should be migrated to use visitXxx()
 * methods for the following reasons:
 * </p>
 * <ul>
 *   <li>Avoid duplicate SQL parsing (check() parses SQL again, visitXxx() reuses parsed Statement)</li>
 *   <li>Cleaner code (no instanceof chains)</li>
 *   <li>Better performance (single parse + type dispatch vs. parse per Checker)</li>
 * </ul>
 *
 * @see StatementVisitor
 * @see AbstractRuleChecker
 * @since 1.0.0
 */
public interface RuleChecker extends StatementVisitor {

  /**
   * Check SQL statement for rule violations.
   * <p>
   * <b>IMPORTANT</b>: As of version 1.1.0, this method is implemented as a
   * <b>template method</b> in {@link AbstractRuleChecker}. Subclasses should
   * <b>NOT</b> override this method directly. Instead, override the specific
   * visitXxx() methods inherited from {@link StatementVisitor}.
   * </p>
   * <p>
   * The template method implementation in AbstractRuleChecker:
   * </p>
   * <pre>{@code
   * @Override
   * public final void check(SqlContext context, ValidationResult result) {
   *     Statement stmt = context.getStatement();
   *     if (stmt instanceof Select) {
   *         visitSelect((Select) stmt, context);
   *     } else if (stmt instanceof Update) {
   *         visitUpdate((Update) stmt, context);
   *     } else if (stmt instanceof Delete) {
   *         visitDelete((Delete) stmt, context);
   *     } else if (stmt instanceof Insert) {
   *         visitInsert((Insert) stmt, context);
   *     }
   * }
   * }</pre>
   *
   * <h3>Migration Guide</h3>
   * <p>
   * If you have an existing Checker that overrides check():
   * </p>
   * <ol>
   *   <li>Identify which Statement types your Checker handles (Select/Update/Delete/Insert)</li>
   *   <li>For each Statement type, move validation logic to the corresponding visitXxx() method</li>
   *   <li>Remove the check() method override (let AbstractRuleChecker provide it)</li>
   *   <li>Update tests to use statement field instead of parsedSql field</li>
   * </ol>
   *
   * @param context the SQL execution context containing Statement and metadata
   * @param result  the validation result accumulator for violations
   * @since 1.0.0
   * @see StatementVisitor#visitSelect(net.sf.jsqlparser.statement.select.Select, SqlContext)
   * @see StatementVisitor#visitUpdate(net.sf.jsqlparser.statement.update.Update, SqlContext)
   * @see StatementVisitor#visitDelete(net.sf.jsqlparser.statement.delete.Delete, SqlContext)
   * @see StatementVisitor#visitInsert(net.sf.jsqlparser.statement.insert.Insert, SqlContext)
   */
  void check(SqlContext context, ValidationResult result);

  /**
   * Check if this rule checker is enabled.
   * <p>
   * Disabled checkers are skipped by {@link RuleCheckerOrchestrator}.
   * </p>
   *
   * @return true if enabled, false if disabled
   * @since 1.0.0
   */
  boolean isEnabled();
}
