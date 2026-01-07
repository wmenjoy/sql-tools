package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration class for SqlCommentChecker.
 *
 * <p>SqlCommentConfig extends CheckerConfig to provide configuration for the SQL Comment
 * validation rule that detects SQL comments used for injection attacks.</p>
 *
 * <p><strong>Default Configuration:</strong></p>
 * <ul>
 *   <li>enabled: true (active by default for security)</li>
 *   <li>riskLevel: CRITICAL (highest severity for SQL injection)</li>
 *   <li>allowHintComments: false (blocks all comments including hints)</li>
 * </ul>
 *
 * <p><strong>YAML Configuration Example:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     sql-comment:
 *       enabled: true
 *       risk-level: CRITICAL
 *       allow-hint-comments: false  # Set to true to allow Oracle optimizer hints
 * }</pre>
 *
 * <p><strong>Security Rationale:</strong></p>
 * <p>SQL comments can be used by attackers to:</p>
 * <ul>
 *   <li>Bypass security detection mechanisms</li>
 *   <li>Hide malicious code within seemingly harmless queries</li>
 *   <li>Comment out WHERE clauses to affect all records</li>
 *   <li>Inject payloads that evade input validation</li>
 * </ul>
 *
 * <p><strong>Hint Comments:</strong></p>
 * <p>Oracle optimizer hints ({@code /*+ INDEX(table idx) * /}) are legitimate performance
 * optimization tools. When {@code allowHintComments=true}, these are permitted while still
 * blocking regular comments. Hints are identified by the {@code /*+} pattern.</p>
 *
 * @see CheckerConfig
 * @see SqlCommentChecker
 * @since 1.0.0
 */
public class SqlCommentConfig extends CheckerConfig {

  /**
   * Whether to allow Oracle optimizer hint comments (/*+ ... * /).
   * Default: false (blocks all comments including hints for maximum security).
   */
  private boolean allowHintComments;

  /**
   * Creates a SqlCommentConfig with default settings:
   * enabled=true, riskLevel=CRITICAL, allowHintComments=false.
   */
  public SqlCommentConfig() {
    super();
    setRiskLevel(RiskLevel.CRITICAL);
    this.allowHintComments = false;
  }

  /**
   * Creates a SqlCommentConfig with specified enabled state
   * and CRITICAL risk level.
   *
   * @param enabled whether the checker should be enabled
   */
  public SqlCommentConfig(boolean enabled) {
    super(enabled);
    setRiskLevel(RiskLevel.CRITICAL);
    this.allowHintComments = false;
  }

  /**
   * Creates a SqlCommentConfig with specified enabled state and hint allowance.
   *
   * @param enabled whether the checker should be enabled
   * @param allowHintComments whether to allow Oracle optimizer hints
   */
  public SqlCommentConfig(boolean enabled, boolean allowHintComments) {
    super(enabled);
    setRiskLevel(RiskLevel.CRITICAL);
    this.allowHintComments = allowHintComments;
  }

  /**
   * Returns whether Oracle optimizer hint comments are allowed.
   *
   * @return true if hint comments are allowed, false otherwise
   */
  public boolean isAllowHintComments() {
    return allowHintComments;
  }

  /**
   * Sets whether Oracle optimizer hint comments are allowed.
   *
   * @param allowHintComments whether to allow hint comments
   */
  public void setAllowHintComments(boolean allowHintComments) {
    this.allowHintComments = allowHintComments;
  }
}
