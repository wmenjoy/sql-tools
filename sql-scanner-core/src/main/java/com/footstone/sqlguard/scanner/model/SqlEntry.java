package com.footstone.sqlguard.scanner.model;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.ViolationInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single SQL entry discovered during static analysis.
 *
 * <p>SqlEntry captures all metadata about a SQL statement found in the codebase,
 * including its source location, type, content, and dynamic variants.</p>
 *
 * <p><strong>Equality Contract:</strong> Two SqlEntry instances are equal if they
 * have the same filePath and lineNumber, regardless of other fields. This allows
 * deduplication of SQL entries from the same location.</p>
 *
 * @see SourceType
 * @see SqlCommandType
 */
public class SqlEntry {
  private final SourceType source;
  private final String filePath;
  private final String statementId;
  private final SqlCommandType sqlType;
  private final String rawSql;
  private final int lineNumber;
  private boolean dynamic;
  private final List<String> sqlVariants;
  private final List<ViolationInfo> violations;
  
  // Additional context for better reporting
  private String xmlSnippet;  // Complete XML element text
  private String javaMethodSignature;  // Java interface method signature

  /**
   * Constructs a new SqlEntry with all required fields.
   *
   * @param source the source type (XML, ANNOTATION, or WRAPPER)
   * @param filePath the absolute path to the source file
   * @param mapperId the mapper identifier (format: namespace.methodId)
   * @param sqlType the SQL command type (SELECT, INSERT, UPDATE, DELETE)
   * @param rawSql the actual SQL text
   * @param lineNumber the line number in the source file (must be > 0)
   * @param dynamic whether this is dynamic SQL
   * @param violations list of violations detected for this SQL entry
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public SqlEntry(SourceType source, String filePath, String statementId,
                  SqlCommandType sqlType, String rawSql, int lineNumber,
                  boolean dynamic, List<ViolationInfo> violations) {
    // Validation
    if (source == null) {
      throw new IllegalArgumentException("source cannot be null");
    }
    if (filePath == null || filePath.trim().isEmpty()) {
      throw new IllegalArgumentException("filePath cannot be null or empty");
    }
    if (statementId == null || statementId.trim().isEmpty()) {
      throw new IllegalArgumentException("statementId cannot be null or empty");
    }
    if (sqlType == null) {
      throw new IllegalArgumentException("sqlType cannot be null");
    }
    if (rawSql == null || rawSql.trim().isEmpty()) {
      throw new IllegalArgumentException("rawSql cannot be null or empty");
    }
    if (lineNumber <= 0) {
      throw new IllegalArgumentException("lineNumber must be greater than 0");
    }

    this.source = source;
    this.filePath = filePath;
    this.statementId = statementId;
    this.sqlType = sqlType;
    this.rawSql = rawSql;
    this.lineNumber = lineNumber;
    this.dynamic = dynamic;
    this.sqlVariants = new ArrayList<>();
    this.violations = violations != null ? new ArrayList<>(violations) : new ArrayList<>();
  }

  /**
   * Constructs a new SqlEntry with all required fields (backward compatibility).
   *
   * @param source the source type (XML, ANNOTATION, or WRAPPER)
   * @param filePath the absolute path to the source file
   * @param mapperId the mapper identifier (format: namespace.methodId)
   * @param sqlType the SQL command type (SELECT, INSERT, UPDATE, DELETE)
   * @param rawSql the actual SQL text
   * @param lineNumber the line number in the source file (must be > 0)
   * @throws IllegalArgumentException if any parameter is invalid
   */
  public SqlEntry(SourceType source, String filePath, String statementId,
                  SqlCommandType sqlType, String rawSql, int lineNumber) {
    this(source, filePath, statementId, sqlType, rawSql, lineNumber, false, new ArrayList<>());
  }

  /**
   * Gets the source type of this SQL entry.
   *
   * @return the source type (XML, ANNOTATION, or WRAPPER)
   */
  public SourceType getSource() {
    return source;
  }

  /**
   * Gets the absolute path to the source file.
   *
   * @return the file path
   */
  public String getFilePath() {
    return filePath;
  }

  /**
   * Gets the mapper identifier.
   *
   * @return the mapper ID in format namespace.methodId
   */
  public String getStatementId() {
    return statementId;
  }

  /**
   * Gets the SQL command type.
   *
   * @return the SQL type (SELECT, INSERT, UPDATE, DELETE)
   */
  public SqlCommandType getSqlType() {
    return sqlType;
  }

  /**
   * Gets the raw SQL text.
   *
   * @return the actual SQL statement
   */
  public String getRawSql() {
    return rawSql;
  }

  /**
   * Gets the line number in the source file.
   *
   * @return the line number (1-based)
   */
  public int getLineNumber() {
    return lineNumber;
  }

  /**
   * Checks if this SQL entry represents dynamic SQL.
   *
   * @return true if dynamic, false otherwise (default: false)
   */
  public boolean isDynamic() {
    return dynamic;
  }

  /**
   * Sets the dynamic flag for this SQL entry.
   *
   * @param dynamic true if this is dynamic SQL, false otherwise
   */
  public void setDynamic(boolean dynamic) {
    this.dynamic = dynamic;
  }

  /**
   * Gets the list of SQL variants for dynamic SQL scenarios.
   *
   * <p>For dynamic SQL (e.g., MyBatis &lt;if&gt; tags), this list contains
   * possible SQL variations that could be generated at runtime.</p>
   *
   * @return mutable list of SQL variant strings
   */
  public List<String> getSqlVariants() {
    return sqlVariants;
  }

  /**
   * Adds a single violation to this SQL entry.
   *
   * @param violation the violation to add (must not be null)
   * @throws IllegalArgumentException if violation is null
   */
  public void addViolation(ViolationInfo violation) {
    if (violation == null) {
      throw new IllegalArgumentException("violation cannot be null");
    }
    this.violations.add(violation);
  }

  /**
   * Adds multiple violations to this SQL entry.
   *
   * @param violations the list of violations to add (may be null or empty)
   */
  public void addViolations(List<ViolationInfo> violations) {
    if (violations != null && !violations.isEmpty()) {
      this.violations.addAll(violations);
    }
  }

  /**
   * Gets the list of violations detected for this SQL entry.
   *
   * <p>Returns an unmodifiable view of the violations list to ensure immutability.</p>
   *
   * @return unmodifiable list of violation information
   */
  public List<ViolationInfo> getViolations() {
    return new ArrayList<>(violations);
  }

  /**
   * Checks if this SQL entry has any violations.
   *
   * @return true if violations exist, false otherwise
   */
  public boolean hasViolations() {
    return violations != null && !violations.isEmpty();
  }

  /**
   * Gets the highest risk level from all violations.
   *
   * @return the highest risk level, or SAFE if no violations
   */
  public RiskLevel getHighestRiskLevel() {
    if (violations == null || violations.isEmpty()) {
      return RiskLevel.SAFE;
    }
    return violations.stream()
        .map(ViolationInfo::getRiskLevel)
        .max(Enum::compareTo)
        .orElse(RiskLevel.SAFE);
  }

  /**
   * Gets the complete XML snippet for this SQL entry.
   *
   * @return the XML snippet, or null if not available
   */
  public String getXmlSnippet() {
    return xmlSnippet;
  }

  /**
   * Sets the complete XML snippet for this SQL entry.
   *
   * @param xmlSnippet the XML snippet to set
   */
  public void setXmlSnippet(String xmlSnippet) {
    this.xmlSnippet = xmlSnippet;
  }

  /**
   * Gets the Java method signature for this SQL entry.
   *
   * @return the Java method signature, or null if not available
   */
  public String getJavaMethodSignature() {
    return javaMethodSignature;
  }

  /**
   * Sets the Java method signature for this SQL entry.
   *
   * @param javaMethodSignature the Java method signature to set
   */
  public void setJavaMethodSignature(String javaMethodSignature) {
    this.javaMethodSignature = javaMethodSignature;
  }

  /**
   * Compares this SqlEntry with another object for equality.
   *
   * <p>Two SqlEntry instances are equal if they have the same filePath
   * and lineNumber, regardless of other fields.</p>
   *
   * @param o the object to compare with
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
    SqlEntry sqlEntry = (SqlEntry) o;
    return lineNumber == sqlEntry.lineNumber &&
        Objects.equals(filePath, sqlEntry.filePath);
  }

  /**
   * Returns a hash code value for this SqlEntry.
   *
   * <p>Hash code is based on filePath and lineNumber only,
   * consistent with the equals() contract.</p>
   *
   * @return the hash code value
   */
  @Override
  public int hashCode() {
    return Objects.hash(filePath, lineNumber);
  }

  /**
   * Returns a string representation of this SqlEntry.
   *
   * @return a string describing this entry
   */
  @Override
  public String toString() {
    return "SqlEntry{" +
        "source=" + source +
        ", filePath='" + filePath + '\'' +
        ", statementId='" + statementId + '\'' +
        ", sqlType=" + sqlType +
        ", lineNumber=" + lineNumber +
        ", dynamic=" + dynamic +
        '}';
  }
}


