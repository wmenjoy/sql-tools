package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration class for SetStatementChecker.
 *
 * <p>SetStatementConfig extends CheckerConfig to provide configuration for the SetStatement
 * validation rule that detects session variable modification statements (SET) which can be
 * used for transaction isolation bypass and SQL mode manipulation attacks.</p>
 *
 * <p><strong>Default Configuration (WARN by Default):</strong></p>
 * <ul>
 *   <li>enabled: true (active by default for security)</li>
 *   <li>riskLevel: MEDIUM (less severe than structural changes)</li>
 * </ul>
 *
 * <p><strong>Note:</strong> Default is WARN (not BLOCK) because SET statements may be
 * necessary in some frameworks for initialization (e.g., connection pool setup).</p>
 *
 * <p><strong>Security Rationale:</strong></p>
 * <p>SET statements can be used to modify session variables that affect database behavior:</p>
 * <ul>
 *   <li>Unexpected behavior changes (e.g., SET autocommit = 0 disables auto-commit)</li>
 *   <li>Transaction isolation bypass (e.g., SET TRANSACTION ISOLATION LEVEL)</li>
 *   <li>SQL mode manipulation enabling attacks (e.g., SET sql_mode = '' removes safety checks)</li>
 *   <li>Character set manipulation (e.g., SET NAMES, SET CHARSET)</li>
 * </ul>
 *
 * <p><strong>Attack Examples:</strong></p>
 * <pre>{@code
 * -- Disable auto-commit (transaction manipulation)
 * SET autocommit = 0
 *
 * -- Remove SQL safety checks
 * SET sql_mode = ''
 *
 * -- Bypass transaction isolation
 * SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED
 *
 * -- User variable injection
 * SET @admin_id = 1
 * }</pre>
 *
 * <p><strong>UPDATE vs SET Differentiation:</strong></p>
 * <p>This checker correctly differentiates between:</p>
 * <ul>
 *   <li>SET statement (DETECT): {@code SET autocommit = 0}</li>
 *   <li>UPDATE statement (ALLOW): {@code UPDATE user SET name = 'test'} (column assignment)</li>
 * </ul>
 *
 * <p><strong>YAML Configuration Example:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     set-statement:
 *       enabled: true
 *       risk-level: MEDIUM
 * }</pre>
 *
 * @see CheckerConfig
 * @see SetStatementChecker
 * @since 1.0.0
 */
public class SetStatementConfig extends CheckerConfig {

  /**
   * Creates a SetStatementConfig with default settings:
   * enabled=true, riskLevel=MEDIUM.
   */
  public SetStatementConfig() {
    super();
    setRiskLevel(RiskLevel.MEDIUM);
  }

  /**
   * Creates a SetStatementConfig with specified enabled state
   * and MEDIUM risk level.
   *
   * @param enabled whether the checker should be enabled
   */
  public SetStatementConfig(boolean enabled) {
    super(enabled);
    setRiskLevel(RiskLevel.MEDIUM);
  }
}
