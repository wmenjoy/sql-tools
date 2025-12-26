package com.footstone.sqlguard.validator.rule;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import java.util.List;

/**
 * Orchestrator managing the execution of multiple rule checkers in Chain of Responsibility pattern.
 *
 * <p>RuleCheckerOrchestrator coordinates validation across all enabled checkers, collecting
 * violations from each checker without short-circuiting. The final risk level is automatically
 * aggregated to the highest severity among all violations.</p>
 *
 * <p><strong>Design Principles:</strong></p>
 * <ul>
 *   <li>Implements Chain of Responsibility pattern</li>
 *   <li>Does not short-circuit on first violation (collects all issues)</li>
 *   <li>Respects checker order (allows priority-based execution)</li>
 *   <li>Skips disabled checkers via isEnabled() check</li>
 *   <li>Final risk level automatically aggregates to highest via ValidationResult</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * List<RuleChecker> checkers = Arrays.asList(
 *   new NoWhereClauseChecker(config.getNoWhereClause()),
 *   new DummyConditionChecker(config.getDummyCondition()),
 *   new BlacklistFieldsChecker(config.getBlacklistFields())
 * );
 *
 * RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
 * ValidationResult result = ValidationResult.pass();
 * orchestrator.orchestrate(context, result);
 *
 * if (!result.isPassed()) {
 *   // Handle violations
 * }
 * }</pre>
 *
 * @see RuleChecker
 * @see ValidationResult
 */
public class RuleCheckerOrchestrator {

  /**
   * List of rule checkers to execute in order.
   */
  private final List<RuleChecker> checkers;

  /**
   * Constructs a RuleCheckerOrchestrator with the specified list of checkers.
   *
   * <p>The checkers will be executed in the order they appear in the list.
   * Disabled checkers (isEnabled() returns false) will be skipped.</p>
   *
   * @param checkers the list of rule checkers to orchestrate
   */
  public RuleCheckerOrchestrator(List<RuleChecker> checkers) {
    this.checkers = checkers;
  }

  /**
   * Orchestrates validation by executing all enabled checkers in sequence.
   *
   * <p>This method:</p>
   * <ul>
   *   <li>Iterates through all checkers in order</li>
   *   <li>Checks if each checker is enabled via isEnabled()</li>
   *   <li>Executes enabled checkers by calling check(context, result)</li>
   *   <li>Continues through all checkers without short-circuiting</li>
   *   <li>Allows ValidationResult to aggregate violations and risk level</li>
   * </ul>
   *
   * <p><strong>No Short-Circuit:</strong> All enabled checkers execute regardless of
   * whether previous checkers found violations. This ensures comprehensive validation
   * and provides developers with complete feedback about all issues.</p>
   *
   * <p><strong>Risk Level Aggregation:</strong> The final risk level is automatically
   * determined by ValidationResult.addViolation() which updates to the highest severity
   * among all violations.</p>
   *
   * @param context the SQL execution context to validate
   * @param result the validation result to populate with violations
   */
  public void orchestrate(SqlContext context, ValidationResult result) {
    for (RuleChecker checker : checkers) {
      if (checker.isEnabled()) {
        checker.check(context, result);
      }
    }
  }
}



















