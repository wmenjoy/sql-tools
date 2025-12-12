package com.footstone.sqlguard.validator.rule;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;

/**
 * Interface defining the contract for SQL validation rule checkers.
 *
 * <p>RuleChecker provides a uniform contract for polymorphic checker execution in the
 * Chain of Responsibility pattern. Each checker examines a SQL statement and may add
 * violations to the ValidationResult.</p>
 *
 * <p><strong>Design Principles:</strong></p>
 * <ul>
 *   <li>Validation happens via side-effect on ValidationResult (no return value)</li>
 *   <li>Checkers can be enabled/disabled via isEnabled() method</li>
 *   <li>Multiple checkers can execute sequentially without short-circuiting</li>
 *   <li>Each checker is independent and stateless</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * public class NoWhereClauseChecker implements RuleChecker {
 *   private final NoWhereClauseConfig config;
 *
 *   public NoWhereClauseChecker(NoWhereClauseConfig config) {
 *     this.config = config;
 *   }
 *
 *   @Override
 *   public void check(SqlContext context, ValidationResult result) {
 *     if (hasNoWhereClause(context)) {
 *       result.addViolation(
 *         RiskLevel.HIGH,
 *         "Missing WHERE clause",
 *         "Add WHERE condition to limit scope"
 *       );
 *     }
 *   }
 *
 *   @Override
 *   public boolean isEnabled() {
 *     return config.isEnabled();
 *   }
 * }
 * }</pre>
 *
 * @see RuleCheckerOrchestrator
 * @see AbstractRuleChecker
 * @see CheckerConfig
 */
public interface RuleChecker {

  /**
   * Performs validation on the SQL context and adds violations to the result if issues found.
   *
   * <p>This method examines the SQL statement in the context and may add one or more
   * violations to the result. The method does not return a value; all validation outcomes
   * are communicated via side-effects on the ValidationResult parameter.</p>
   *
   * <p><strong>Implementation Guidelines:</strong></p>
   * <ul>
   *   <li>Extract necessary information from SqlContext (parsed AST, SQL string, etc.)</li>
   *   <li>Apply validation logic specific to this checker</li>
   *   <li>Add violations to result using result.addViolation() when issues detected</li>
   *   <li>Do not modify the SqlContext parameter</li>
   *   <li>Handle null or invalid AST gracefully (skip validation if parsing failed)</li>
   * </ul>
   *
   * @param context the SQL execution context containing statement and metadata
   * @param result the validation result to populate with violations
   */
  void check(SqlContext context, ValidationResult result);

  /**
   * Returns whether this checker is enabled and should execute.
   *
   * <p>Disabled checkers are skipped by the RuleCheckerOrchestrator. This allows
   * runtime configuration of which validation rules are active.</p>
   *
   * <p>Typical implementation delegates to a CheckerConfig:</p>
   * <pre>{@code
   * @Override
   * public boolean isEnabled() {
   *   return config.isEnabled();
   * }
   * }</pre>
   *
   * @return true if checker should execute, false to skip
   */
  boolean isEnabled();
}
