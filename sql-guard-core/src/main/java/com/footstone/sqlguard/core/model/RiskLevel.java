package com.footstone.sqlguard.core.model;

/**
 * Risk level enumeration for SQL validation violations.
 *
 * <p>RiskLevel represents the severity of a validation violation, with natural ordering
 * from SAFE (no risk) to CRITICAL (highest risk). The declaration order determines the
 * natural ordering used by {@link #compareTo(RiskLevel)}.</p>
 *
 * <p><strong>Severity Levels:</strong></p>
 * <ul>
 *   <li><strong>SAFE</strong> - No violations detected (severity 0)</li>
 *   <li><strong>LOW</strong> - Minor issues, informational (severity 1)</li>
 *   <li><strong>MEDIUM</strong> - Moderate issues, should be addressed (severity 2)</li>
 *   <li><strong>HIGH</strong> - Serious issues, requires attention (severity 3)</li>
 *   <li><strong>CRITICAL</strong> - Critical security or data integrity issues (severity 4)</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * if (riskLevel.compareTo(RiskLevel.HIGH) >= 0) {
 *   // Block execution for HIGH or CRITICAL risk
 * }
 * }</pre>
 *
 * @see ValidationResult
 * @see ViolationInfo
 */
public enum RiskLevel {
  /**
   * No risk detected - validation passed.
   */
  SAFE,

  /**
   * Low risk - minor issues, informational only.
   */
  LOW,

  /**
   * Medium risk - moderate issues that should be addressed.
   */
  MEDIUM,

  /**
   * High risk - serious issues requiring attention.
   */
  HIGH,

  /**
   * Critical risk - severe security or data integrity issues.
   */
  CRITICAL;

  /**
   * Returns the severity level as an integer.
   *
   * <p>The severity is equivalent to the ordinal value:
   * SAFE=0, LOW=1, MEDIUM=2, HIGH=3, CRITICAL=4</p>
   *
   * @return the severity level (0-4)
   */
  public int getSeverity() {
    return this.ordinal();
  }
}
