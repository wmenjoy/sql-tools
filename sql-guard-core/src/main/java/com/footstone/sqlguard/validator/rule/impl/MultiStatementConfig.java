package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration class for MultiStatementChecker.
 *
 * <p>MultiStatementConfig extends CheckerConfig to provide configuration for the MultiStatement
 * validation rule that detects SQL injection via multi-statement execution.</p>
 *
 * <p><strong>Default Configuration:</strong></p>
 * <ul>
 *   <li>enabled: true (active by default for security)</li>
 *   <li>riskLevel: CRITICAL (highest severity for SQL injection)</li>
 * </ul>
 *
 * <p><strong>YAML Configuration Example:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     multi-statement:
 *       enabled: true
 *       risk-level: CRITICAL
 * }</pre>
 *
 * <p><strong>Security Rationale:</strong></p>
 * <p>Multi-statement injection is one of the most dangerous SQL injection techniques,
 * allowing attackers to execute arbitrary SQL commands (DROP TABLE, DELETE, etc.)
 * by appending statements after a semicolon. This checker is enabled by default
 * with CRITICAL risk level to provide maximum protection.</p>
 *
 * @see CheckerConfig
 * @see MultiStatementChecker
 * @since 1.0.0
 */
public class MultiStatementConfig extends CheckerConfig {

  /**
   * Creates a MultiStatementConfig with default settings:
   * enabled=true, riskLevel=CRITICAL.
   */
  public MultiStatementConfig() {
    super();
    setRiskLevel(RiskLevel.CRITICAL);
  }

  /**
   * Creates a MultiStatementConfig with specified enabled state
   * and CRITICAL risk level.
   *
   * @param enabled whether the checker should be enabled
   */
  public MultiStatementConfig(boolean enabled) {
    super(enabled);
    setRiskLevel(RiskLevel.CRITICAL);
  }
}
