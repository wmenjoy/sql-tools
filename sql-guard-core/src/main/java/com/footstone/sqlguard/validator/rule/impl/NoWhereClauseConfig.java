package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration class for NoWhereClauseChecker.
 *
 * <p>NoWhereClauseConfig extends CheckerConfig to provide configuration for the NoWhereClause
 * validation rule. Currently, it only uses the base enabled/disabled toggle from CheckerConfig
 * with no additional configuration fields.</p>
 *
 * <p><strong>Future Extensions:</strong></p>
 * <ul>
 *   <li>excludedTables - List of tables exempt from WHERE clause requirement</li>
 *   <li>excludedStatementTypes - Specific statement types to skip (e.g., only check DELETE)</li>
 * </ul>
 *
 * @see CheckerConfig
 * @see NoWhereClauseChecker
 */
public class NoWhereClauseConfig extends CheckerConfig {

  /**
   * Creates a NoWhereClauseConfig with enabled=true (default) and CRITICAL risk level.
   */
  public NoWhereClauseConfig() {
    super();
    setRiskLevel(RiskLevel.CRITICAL);
  }

  /**
   * Creates a NoWhereClauseConfig with specified enabled state and CRITICAL risk level.
   *
   * @param enabled whether the checker should be enabled
   */
  public NoWhereClauseConfig(boolean enabled) {
    super(enabled);
    setRiskLevel(RiskLevel.CRITICAL);
  }
}
