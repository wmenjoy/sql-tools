package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration for NoConditionPaginationChecker.
 *
 * <p>This configuration extends CheckerConfig to provide enabled/disabled toggle for the
 * no-condition physical pagination checker. The checker detects LIMIT queries without WHERE
 * clauses, which still perform full table scans despite pagination.</p>
 *
 * <p><strong>Default Configuration:</strong></p>
 * <ul>
 *   <li>enabled: true (checker is active by default)</li>
 *   <li>riskLevel: CRITICAL (unconditioned LIMIT queries are catastrophic)</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * NoConditionPaginationConfig config = new NoConditionPaginationConfig();
 * config.setEnabled(true);
 * config.setRiskLevel(RiskLevel.CRITICAL);
 * 
 * NoConditionPaginationChecker checker = new NoConditionPaginationChecker(config, detector);
 * }</pre>
 *
 * @see NoConditionPaginationChecker
 * @see CheckerConfig
 */
public class NoConditionPaginationConfig extends CheckerConfig {

  /**
   * Creates a NoConditionPaginationConfig with default settings.
   * 
   * <p>Default: enabled=true, riskLevel=CRITICAL</p>
   */
  public NoConditionPaginationConfig() {
    super(true);
    setRiskLevel(RiskLevel.CRITICAL);
  }

  /**
   * Creates a NoConditionPaginationConfig with specified enabled state.
   *
   * @param enabled whether the checker should be enabled
   */
  public NoConditionPaginationConfig(boolean enabled) {
    super(enabled);
    setRiskLevel(RiskLevel.CRITICAL);
  }

  /**
   * Creates a NoConditionPaginationConfig with specified enabled state and risk level.
   *
   * @param enabled whether the checker should be enabled
   * @param riskLevel the risk level for violations
   */
  public NoConditionPaginationConfig(boolean enabled, RiskLevel riskLevel) {
    super(enabled);
    setRiskLevel(riskLevel);
  }
}









