package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.config.ViolationStrategy;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration class for CallStatementChecker.
 *
 * <p>CallStatementConfig extends CheckerConfig to provide configuration for the CallStatement
 * validation rule that detects stored procedure calls (CALL/EXECUTE/EXEC) which introduce
 * opaque logic and potential permission escalation risks.</p>
 *
 * <p><strong>Default Configuration (Monitoring by Default):</strong></p>
 * <ul>
 *   <li>enabled: true (active by default for monitoring)</li>
 *   <li>riskLevel: HIGH (serious but not critical, procedures may be legitimate)</li>
 *   <li>violationStrategy: WARN (monitor without blocking legitimate use cases)</li>
 * </ul>
 *
 * <p><strong>Why Default Strategy is WARN (not BLOCK):</strong></p>
 * <p>Stored procedures are a valid architectural pattern in many systems. Unlike
 * file operations or SQL injection, procedure calls are often intentional and
 * serve legitimate business purposes. The default WARN strategy allows:</p>
 * <ul>
 *   <li>Monitoring procedure usage for security auditing</li>
 *   <li>Gradual migration from procedure-based to application-based logic</li>
 *   <li>Compatibility with legacy systems that rely on stored procedures</li>
 * </ul>
 *
 * <p><strong>Security Rationale:</strong></p>
 * <p>Stored procedure calls can introduce security concerns:</p>
 * <ul>
 *   <li>Opaque logic: Procedure implementation is not visible in application code</li>
 *   <li>Permission escalation: Procedures execute with definer privileges</li>
 *   <li>Hidden complexity: Business logic hidden in database layer</li>
 *   <li>Audit difficulty: Harder to track data flow and security implications</li>
 * </ul>
 *
 * <p><strong>When Procedures are Acceptable:</strong></p>
 * <ul>
 *   <li>Legacy systems with extensive procedure-based architecture</li>
 *   <li>Performance-critical batch operations</li>
 *   <li>Database-specific optimizations</li>
 * </ul>
 *
 * <p><strong>When Procedures are Problematic:</strong></p>
 * <ul>
 *   <li>New applications where logic should be in application layer</li>
 *   <li>Unexpected procedure calls that bypass application security</li>
 *   <li>Procedures with elevated privileges accessing sensitive data</li>
 * </ul>
 *
 * <p><strong>YAML Configuration Example:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     call-statement:
 *       enabled: true
 *       risk-level: HIGH
 *       violation-strategy: WARN  # Use BLOCK for strict enforcement
 * }</pre>
 *
 * @see CheckerConfig
 * @see CallStatementChecker
 * @since 1.0.0
 */
public class CallStatementConfig extends CheckerConfig {

  /**
   * Violation strategy for this checker.
   * Default is WARN to balance monitoring with legitimate use cases.
   */
  private ViolationStrategy violationStrategy;

  /**
   * Creates a CallStatementConfig with default settings:
   * enabled=true, riskLevel=HIGH, violationStrategy=WARN.
   */
  public CallStatementConfig() {
    super();
    setRiskLevel(RiskLevel.HIGH);
    this.violationStrategy = ViolationStrategy.WARN;
  }

  /**
   * Creates a CallStatementConfig with specified enabled state
   * and HIGH risk level with WARN strategy.
   *
   * @param enabled whether the checker should be enabled
   */
  public CallStatementConfig(boolean enabled) {
    super(enabled);
    setRiskLevel(RiskLevel.HIGH);
    this.violationStrategy = ViolationStrategy.WARN;
  }

  /**
   * Creates a CallStatementConfig with specified enabled state
   * and violation strategy.
   *
   * @param enabled           whether the checker should be enabled
   * @param violationStrategy the violation handling strategy
   */
  public CallStatementConfig(boolean enabled, ViolationStrategy violationStrategy) {
    super(enabled);
    setRiskLevel(RiskLevel.HIGH);
    this.violationStrategy = violationStrategy;
  }

  /**
   * Returns the violation strategy for this checker.
   *
   * @return the violation strategy (default: WARN)
   */
  public ViolationStrategy getViolationStrategy() {
    return violationStrategy;
  }

  /**
   * Sets the violation strategy for this checker.
   *
   * @param violationStrategy the violation strategy to set
   */
  public void setViolationStrategy(ViolationStrategy violationStrategy) {
    this.violationStrategy = violationStrategy;
  }
}
