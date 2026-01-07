package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Configuration class for SetOperationChecker.
 *
 * <p>SetOperationConfig extends CheckerConfig to provide configuration for the SetOperation
 * validation rule that detects SQL set operations (UNION, MINUS, EXCEPT, INTERSECT) which
 * can be used for data exfiltration and SQL injection attacks.</p>
 *
 * <p><strong>Default Configuration (Secure by Default):</strong></p>
 * <ul>
 *   <li>enabled: true (active by default for security)</li>
 *   <li>riskLevel: CRITICAL (highest severity for SQL injection)</li>
 *   <li>allowedOperations: [] (empty list = block ALL set operations)</li>
 * </ul>
 *
 * <p><strong>Allowlist Behavior:</strong></p>
 * <ul>
 *   <li>Empty allowedOperations list: blocks ALL set operations (most secure)</li>
 *   <li>Populated list: allows only specified operation types</li>
 *   <li>Operation names are case-insensitive (UNION, union, Union all match)</li>
 * </ul>
 *
 * <p><strong>Valid Operation Names:</strong></p>
 * <ul>
 *   <li>UNION - Standard SQL union (removes duplicates)</li>
 *   <li>UNION_ALL - Union keeping all rows (JSqlParser uses UNION_ALL internally)</li>
 *   <li>MINUS - Oracle-specific set difference</li>
 *   <li>EXCEPT - PostgreSQL/SQL Server set difference</li>
 *   <li>INTERSECT - Set intersection</li>
 *   <li>INTERSECT_ALL - Intersection keeping duplicates</li>
 *   <li>EXCEPT_ALL - Except keeping duplicates</li>
 *   <li>MINUS_ALL - Minus keeping duplicates</li>
 * </ul>
 *
 * <p><strong>YAML Configuration Example:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     set-operation:
 *       enabled: true
 *       risk-level: CRITICAL
 *       allowed-operations:
 *         - UNION       # Allow UNION for reporting queries
 *         - INTERSECT   # Allow INTERSECT for data validation
 * }</pre>
 *
 * <p><strong>Security Rationale:</strong></p>
 * <p>Set operations like UNION are commonly used in SQL injection attacks to exfiltrate
 * data from other tables. For example:</p>
 * <pre>{@code
 * SELECT name FROM users WHERE id = '1' UNION SELECT password FROM admin_users--
 * }</pre>
 * <p>By default, all set operations are blocked. Applications with legitimate use cases
 * can selectively enable specific operations via the allowedOperations list.</p>
 *
 * @see CheckerConfig
 * @see SetOperationChecker
 * @since 1.0.0
 */
public class SetOperationConfig extends CheckerConfig {

  /**
   * Valid set operation names that can be allowed.
   * Case-insensitive matching is used.
   */
  private static final Set<String> VALID_OPERATIONS = new HashSet<>(Arrays.asList(
      "UNION",
      "UNION_ALL",
      "MINUS",
      "MINUS_ALL",
      "EXCEPT",
      "EXCEPT_ALL",
      "INTERSECT",
      "INTERSECT_ALL"
  ));

  /**
   * List of allowed set operations (empty = block all).
   * Stored in uppercase for case-insensitive matching.
   */
  private final List<String> allowedOperations;

  /**
   * Creates a SetOperationConfig with default settings:
   * enabled=true, riskLevel=CRITICAL, allowedOperations=[].
   */
  public SetOperationConfig() {
    super();
    setRiskLevel(RiskLevel.CRITICAL);
    this.allowedOperations = new ArrayList<>();
  }

  /**
   * Creates a SetOperationConfig with specified enabled state
   * and CRITICAL risk level, empty allowedOperations.
   *
   * @param enabled whether the checker should be enabled
   */
  public SetOperationConfig(boolean enabled) {
    super(enabled);
    setRiskLevel(RiskLevel.CRITICAL);
    this.allowedOperations = new ArrayList<>();
  }

  /**
   * Creates a SetOperationConfig with specified enabled state
   * and allowed operations list.
   *
   * @param enabled whether the checker should be enabled
   * @param allowedOperations list of allowed operation names (case-insensitive)
   * @throws IllegalArgumentException if any operation name is invalid
   */
  public SetOperationConfig(boolean enabled, List<String> allowedOperations) {
    super(enabled);
    setRiskLevel(RiskLevel.CRITICAL);
    this.allowedOperations = new ArrayList<>();
    if (allowedOperations != null) {
      setAllowedOperations(allowedOperations);
    }
  }

  /**
   * Returns the list of allowed set operations.
   *
   * @return list of allowed operation names (uppercase), empty list means block all
   */
  public List<String> getAllowedOperations() {
    return new ArrayList<>(allowedOperations);
  }

  /**
   * Sets the list of allowed set operations.
   *
   * <p>Operation names are validated against the known valid operations
   * and stored in uppercase for case-insensitive matching.</p>
   *
   * @param operations list of operation names to allow (case-insensitive)
   * @throws IllegalArgumentException if any operation name is invalid
   */
  public void setAllowedOperations(List<String> operations) {
    this.allowedOperations.clear();
    if (operations != null) {
      for (String op : operations) {
        addAllowedOperation(op);
      }
    }
  }

  /**
   * Adds a single operation to the allowed list.
   *
   * @param operation the operation name to allow (case-insensitive)
   * @throws IllegalArgumentException if the operation name is invalid
   */
  public void addAllowedOperation(String operation) {
    if (operation == null || operation.trim().isEmpty()) {
      throw new IllegalArgumentException("Operation name cannot be null or empty");
    }

    String normalizedOp = normalizeOperationName(operation);
    if (!VALID_OPERATIONS.contains(normalizedOp)) {
      throw new IllegalArgumentException(
          "Invalid operation name: '" + operation + "'. Valid values are: " + VALID_OPERATIONS);
    }

    if (!allowedOperations.contains(normalizedOp)) {
      allowedOperations.add(normalizedOp);
    }
  }

  /**
   * Checks if a specific operation is allowed.
   *
   * @param operation the operation name to check (case-insensitive)
   * @return true if the operation is in the allowed list, false otherwise
   */
  public boolean isOperationAllowed(String operation) {
    if (operation == null) {
      return false;
    }
    String normalizedOp = normalizeOperationName(operation);
    return allowedOperations.contains(normalizedOp);
  }

  /**
   * Normalizes an operation name for consistent comparison.
   * Converts to uppercase and replaces spaces with underscores.
   *
   * @param operation the operation name to normalize
   * @return normalized operation name
   */
  private String normalizeOperationName(String operation) {
    return operation.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
  }

  /**
   * Returns the set of valid operation names.
   *
   * @return unmodifiable set of valid operation names
   */
  public static Set<String> getValidOperations() {
    return new HashSet<>(VALID_OPERATIONS);
  }
}
