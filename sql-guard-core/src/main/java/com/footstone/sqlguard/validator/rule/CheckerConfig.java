package com.footstone.sqlguard.validator.rule;

import com.footstone.sqlguard.core.model.RiskLevel;

/**
 * Base configuration class for rule checkers providing common enabled/disabled toggle.
 *
 * <p>CheckerConfig provides a common mechanism for enabling or disabling validation rules
 * at runtime. All specific rule configuration classes should extend this base class to
 * inherit the enabled flag functionality.</p>
 *
 * <p><strong>Design Rationale:</strong></p>
 * <ul>
 *   <li>Centralizes enabled/disabled logic across all checkers</li>
 *   <li>Provides consistent default behavior (enabled=true)</li>
 *   <li>Allows runtime configuration via YAML or programmatic setup</li>
 *   <li>Supports selective rule activation in different environments</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * public class NoWhereClauseConfig extends CheckerConfig {
 *   private List<String> excludedTables;
 *
 *   public NoWhereClauseConfig() {
 *     super(); // enabled=true by default
 *     this.excludedTables = new ArrayList<>();
 *   }
 *
 *   public NoWhereClauseConfig(boolean enabled, List<String> excludedTables) {
 *     super(enabled);
 *     this.excludedTables = excludedTables;
 *   }
 *
 *   // Additional config methods...
 * }
 * }</pre>
 *
 * @see RuleChecker
 */
public class CheckerConfig {

  /**
   * Whether this checker is enabled (default: true).
   */
  private boolean enabled;

  /**
   * Risk level for violations detected by this checker.
   */
  private RiskLevel riskLevel;

  /**
   * Creates a CheckerConfig with enabled=true (default).
   */
  public CheckerConfig() {
    this.enabled = true;
    this.riskLevel = RiskLevel.MEDIUM;
  }

  /**
   * Creates a CheckerConfig with specified enabled state.
   *
   * @param enabled whether the checker should be enabled
   */
  public CheckerConfig(boolean enabled) {
    this.enabled = enabled;
    this.riskLevel = RiskLevel.MEDIUM;
  }

  /**
   * Returns whether this checker is enabled.
   *
   * @return true if enabled, false otherwise
   */
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether this checker is enabled.
   *
   * @param enabled whether the checker should be enabled
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Returns the risk level for violations detected by this checker.
   *
   * @return the risk level
   */
  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  /**
   * Sets the risk level for violations detected by this checker.
   *
   * @param riskLevel the risk level to set
   */
  public void setRiskLevel(RiskLevel riskLevel) {
    this.riskLevel = riskLevel;
  }
}
