package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration class for MetadataStatementChecker.
 *
 * <p>MetadataStatementConfig extends CheckerConfig to provide configuration for the
 * MetadataStatement validation rule that detects metadata disclosure statements
 * (SHOW/DESCRIBE/USE) which can leak schema information to attackers.</p>
 *
 * <p><strong>Default Configuration (Secure by Default):</strong></p>
 * <ul>
 *   <li>enabled: true (active by default for security)</li>
 *   <li>riskLevel: HIGH (metadata queries are less severe than data modification)</li>
 *   <li>allowedStatements: [] (empty list - block all metadata commands)</li>
 * </ul>
 *
 * <p><strong>Security Rationale:</strong></p>
 * <p>Metadata statements like SHOW, DESCRIBE, and USE can reveal database schema
 * information to attackers, enabling:</p>
 * <ul>
 *   <li>Table structure discovery for targeted SQL injection attacks</li>
 *   <li>Database enumeration for reconnaissance</li>
 *   <li>Schema information disclosure for exploitation planning</li>
 * </ul>
 *
 * <p><strong>Supported Statement Types:</strong></p>
 * <ul>
 *   <li>SHOW - MySQL SHOW commands (SHOW TABLES, SHOW DATABASES, etc.)</li>
 *   <li>DESCRIBE - Table structure disclosure (DESCRIBE table, DESC table)</li>
 *   <li>USE - Database switching command</li>
 * </ul>
 *
 * <p><strong>YAML Configuration Example:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     metadata-statement:
 *       enabled: true
 *       risk-level: HIGH
 *       allowed-statements:
 *         - SHOW  # Allow SHOW commands for admin tools
 * }</pre>
 *
 * @see CheckerConfig
 * @see MetadataStatementChecker
 * @since 1.0.0
 */
public class MetadataStatementConfig extends CheckerConfig {

  /**
   * Valid statement type names that can be allowed.
   */
  private static final Set<String> VALID_STATEMENT_TYPES = new HashSet<>(
      Arrays.asList("SHOW", "DESCRIBE", "USE")
  );

  /**
   * List of allowed metadata statement types.
   * Empty list means all metadata commands are blocked (default secure behavior).
   */
  private List<String> allowedStatements;

  /**
   * Creates a MetadataStatementConfig with default settings:
   * enabled=true, riskLevel=HIGH, allowedStatements=[].
   */
  public MetadataStatementConfig() {
    super();
    setRiskLevel(RiskLevel.HIGH);
    this.allowedStatements = new ArrayList<>();
  }

  /**
   * Creates a MetadataStatementConfig with specified enabled state,
   * HIGH risk level, and empty allowedStatements list.
   *
   * @param enabled whether the checker should be enabled
   */
  public MetadataStatementConfig(boolean enabled) {
    super(enabled);
    setRiskLevel(RiskLevel.HIGH);
    this.allowedStatements = new ArrayList<>();
  }

  /**
   * Creates a MetadataStatementConfig with specified enabled state
   * and allowed statements list.
   *
   * @param enabled           whether the checker should be enabled
   * @param allowedStatements list of allowed statement types (SHOW, DESCRIBE, USE)
   * @throws IllegalArgumentException if any statement type is invalid
   */
  public MetadataStatementConfig(boolean enabled, List<String> allowedStatements) {
    super(enabled);
    setRiskLevel(RiskLevel.HIGH);
    setAllowedStatements(allowedStatements);
  }

  /**
   * Returns the list of allowed metadata statement types.
   *
   * @return list of allowed statement types (may be empty)
   */
  public List<String> getAllowedStatements() {
    return new ArrayList<>(allowedStatements);
  }

  /**
   * Sets the list of allowed metadata statement types.
   * Each statement type is validated and converted to uppercase.
   *
   * @param allowedStatements list of statement types to allow (SHOW, DESCRIBE, USE)
   * @throws IllegalArgumentException if any statement type is invalid
   */
  public void setAllowedStatements(List<String> allowedStatements) {
    if (allowedStatements == null) {
      this.allowedStatements = new ArrayList<>();
      return;
    }

    List<String> normalized = new ArrayList<>();
    for (String stmt : allowedStatements) {
      if (stmt == null || stmt.trim().isEmpty()) {
        continue;
      }
      String upper = stmt.trim().toUpperCase();
      if (!VALID_STATEMENT_TYPES.contains(upper)) {
        throw new IllegalArgumentException(
            "Invalid statement type: '" + stmt + "'. Valid types are: " + VALID_STATEMENT_TYPES
        );
      }
      normalized.add(upper);
    }
    this.allowedStatements = normalized;
  }

  /**
   * Checks if a specific statement type is allowed.
   *
   * @param statementType the statement type to check (case-insensitive)
   * @return true if the statement type is in the allowed list
   */
  public boolean isStatementAllowed(String statementType) {
    if (statementType == null) {
      return false;
    }
    return allowedStatements.contains(statementType.toUpperCase());
  }

  /**
   * Returns the set of valid statement type names.
   *
   * @return unmodifiable set of valid statement types
   */
  public static Set<String> getValidStatementTypes() {
    return new HashSet<>(VALID_STATEMENT_TYPES);
  }
}
