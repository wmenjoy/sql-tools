package com.footstone.sqlguard.core.model;

import java.util.Objects;

/**
 * Immutable value object representing a single validation violation.
 *
 * <p>ViolationInfo encapsulates the details of a validation rule violation, including
 * the risk level, descriptive message, and optional suggestion for remediation.</p>
 *
 * <p><strong>Immutability:</strong></p>
 * <ul>
 *   <li>All fields are final and cannot be modified after construction</li>
 *   <li>No setters are provided</li>
 *   <li>Safe to share across threads and store in collections</li>
 * </ul>
 *
 * <p><strong>Value Object Pattern:</strong></p>
 * <ul>
 *   <li>{@link #equals(Object)} compares riskLevel and message (not suggestion)</li>
 *   <li>{@link #hashCode()} is consistent with equals()</li>
 *   <li>Two violations with same risk level and message are considered equal</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * ViolationInfo violation = new ViolationInfo(
 *     RiskLevel.HIGH,
 *     "SQL injection risk detected in WHERE clause",
 *     "Use parameterized queries instead of string concatenation"
 * );
 * }</pre>
 *
 * @see RiskLevel
 * @see ValidationResult
 */
public final class ViolationInfo {

  /**
   * Risk level of this violation (required).
   */
  private final RiskLevel riskLevel;

  /**
   * Descriptive message explaining the violation (required).
   */
  private final String message;

  /**
   * Suggested fix or remediation action (optional).
   */
  private final String suggestion;

  /**
   * Constructs a new ViolationInfo with validation.
   *
   * @param riskLevel the risk level (must not be null)
   * @param message the violation message (must not be null or empty)
   * @param suggestion the suggested fix (may be null)
   * @throws IllegalArgumentException if riskLevel or message is null/empty
   */
  public ViolationInfo(RiskLevel riskLevel, String message, String suggestion) {
    // Validate required fields
    if (riskLevel == null) {
      throw new IllegalArgumentException("riskLevel cannot be null");
    }
    if (message == null || message.trim().isEmpty()) {
      throw new IllegalArgumentException("message cannot be null or empty");
    }

    this.riskLevel = riskLevel;
    this.message = message;
    this.suggestion = suggestion;
  }

  /**
   * Returns the risk level of this violation.
   *
   * @return the risk level (never null)
   */
  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  /**
   * Returns the violation message.
   *
   * @return the message (never null or empty)
   */
  public String getMessage() {
    return message;
  }

  /**
   * Returns the suggested fix.
   *
   * @return the suggestion (may be null)
   */
  public String getSuggestion() {
    return suggestion;
  }

  /**
   * Compares this violation to another object for equality.
   *
   * <p>Two ViolationInfo objects are equal if they have the same riskLevel and message.
   * The suggestion field is NOT included in equality comparison.</p>
   *
   * @param o the object to compare
   * @return true if equal, false otherwise
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ViolationInfo that = (ViolationInfo) o;
    return riskLevel == that.riskLevel
        && Objects.equals(message, that.message);
  }

  /**
   * Returns the hash code for this violation.
   *
   * <p>Hash code is computed from riskLevel and message only (consistent with equals).</p>
   *
   * @return the hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(riskLevel, message);
  }

  /**
   * Returns a string representation of this violation.
   *
   * @return formatted string including all fields
   */
  @Override
  public String toString() {
    return "ViolationInfo{"
        + "riskLevel=" + riskLevel
        + ", message='" + message + '\''
        + ", suggestion='" + suggestion + '\''
        + '}';
  }
}
