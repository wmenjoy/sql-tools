package com.footstone.sqlguard.core.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of SQL validation with violations and risk assessment.
 *
 * <p>ValidationResult aggregates violations from multiple validation checkers and determines
 * the overall risk level. The risk level is always the highest severity among all violations.</p>
 *
 * <p><strong>Violation Aggregation Logic:</strong></p>
 * <ul>
 *   <li>Initial state: passed=true, riskLevel=SAFE, violations=empty</li>
 *   <li>Adding any violation sets passed=false</li>
 *   <li>Risk level is updated to max(currentRiskLevel, newViolationRiskLevel)</li>
 *   <li>Multiple violations are accumulated in the violations list</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * ValidationResult result = ValidationResult.pass();
 * result.addViolation(RiskLevel.MEDIUM, "SQL injection risk", "Use parameterized queries");
 * result.addViolation(RiskLevel.HIGH, "Missing WHERE clause", "Add WHERE condition");
 * // result.isPassed() == false
 * // result.getRiskLevel() == RiskLevel.HIGH (highest)
 * }</pre>
 *
 * @see RiskLevel
 * @see ViolationInfo
 */
public class ValidationResult {

  /**
   * Whether validation passed (no violations).
   */
  private boolean passed;

  /**
   * Overall risk level (highest among all violations).
   */
  private RiskLevel riskLevel;

  /**
   * List of all violations detected.
   */
  private final List<ViolationInfo> violations;

  /**
   * Additional details map for custom metadata.
   */
  private final Map<String, Object> details;

  /**
   * Private constructor. Use {@link #pass()} factory method.
   */
  private ValidationResult() {
    this.passed = true;
    this.riskLevel = RiskLevel.SAFE;
    this.violations = new ArrayList<>();
    this.details = new LinkedHashMap<>();
  }

  /**
   * Creates a new ValidationResult in passed state.
   *
   * @return a new ValidationResult with passed=true and SAFE risk level
   */
  public static ValidationResult pass() {
    return new ValidationResult();
  }

  /**
   * Adds a violation to this result.
   *
   * <p>This method:</p>
   * <ul>
   *   <li>Creates a ViolationInfo with the provided details</li>
   *   <li>Adds it to the violations list</li>
   *   <li>Sets passed=false</li>
   *   <li>Updates riskLevel to max(current, new) using compareTo</li>
   * </ul>
   *
   * @param riskLevel the risk level of this violation
   * @param message the violation message
   * @param suggestion the suggested fix
   */
  public void addViolation(RiskLevel riskLevel, String message, String suggestion) {
    ViolationInfo violation = new ViolationInfo(riskLevel, message, suggestion);
    this.violations.add(violation);
    this.passed = false;

    // Update risk level to highest severity
    if (riskLevel.compareTo(this.riskLevel) > 0) {
      this.riskLevel = riskLevel;
    }
  }

  /**
   * Returns whether validation passed (no violations).
   *
   * @return true if no violations, false otherwise
   */
  public boolean isPassed() {
    return passed;
  }

  /**
   * Returns the overall risk level.
   *
   * @return SAFE if passed, otherwise the highest risk level among violations
   */
  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  /**
   * Returns the list of all violations.
   *
   * @return unmodifiable view of violations list
   */
  public List<ViolationInfo> getViolations() {
    return violations;
  }

  /**
   * Returns the details map for additional metadata.
   *
   * @return mutable details map
   */
  public Map<String, Object> getDetails() {
    return details;
  }

  @Override
  public String toString() {
    return "ValidationResult{"
        + "passed=" + passed
        + ", riskLevel=" + riskLevel
        + ", violations=" + violations.size()
        + ", details=" + details
        + '}';
  }
}







