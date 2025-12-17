package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;

import java.util.Objects;

/**
 * Represents a formatted violation entry for report generation.
 *
 * <p>ViolationEntry is an immutable data class that encapsulates all information
 * needed to display a violation in console or HTML reports.</p>
 *
 * <p><strong>Immutability:</strong> All fields are final and cannot be modified
 * after construction, making instances thread-safe and suitable for concurrent
 * report generation.</p>
 */
public class ViolationEntry {
  private final String filePath;
  private final int lineNumber;
  private final String mapperId;
  private final String sqlSnippet;
  private final RiskLevel riskLevel;
  private final String message;
  private final String suggestion;
  private final String xmlSnippet;
  private final String javaMethodSignature;

  /**
   * Constructs a new ViolationEntry with all required fields.
   *
   * @param filePath the absolute path to the source file
   * @param lineNumber the line number where violation occurs
   * @param mapperId the mapper identifier
   * @param sqlSnippet the SQL snippet (may be truncated)
   * @param riskLevel the risk level of the violation
   * @param message the violation message
   * @param suggestion the suggested fix (may be null)
   */
  public ViolationEntry(String filePath, int lineNumber, String mapperId,
                        String sqlSnippet, RiskLevel riskLevel,
                        String message, String suggestion) {
    this(filePath, lineNumber, mapperId, sqlSnippet, riskLevel, message, suggestion, null, null);
  }

  /**
   * Constructs a new ViolationEntry with all fields including XML and Java context.
   *
   * @param filePath the absolute path to the source file
   * @param lineNumber the line number where violation occurs
   * @param mapperId the mapper identifier
   * @param sqlSnippet the SQL snippet (may be truncated)
   * @param riskLevel the risk level of the violation
   * @param message the violation message
   * @param suggestion the suggested fix (may be null)
   * @param xmlSnippet the complete XML snippet (may be null)
   * @param javaMethodSignature the Java method signature (may be null)
   */
  public ViolationEntry(String filePath, int lineNumber, String mapperId,
                        String sqlSnippet, RiskLevel riskLevel,
                        String message, String suggestion,
                        String xmlSnippet, String javaMethodSignature) {
    this.filePath = filePath;
    this.lineNumber = lineNumber;
    this.mapperId = mapperId;
    this.sqlSnippet = sqlSnippet;
    this.riskLevel = riskLevel;
    this.message = message;
    this.suggestion = suggestion;
    this.xmlSnippet = xmlSnippet;
    this.javaMethodSignature = javaMethodSignature;
  }

  /**
   * Gets the file path.
   *
   * @return the file path
   */
  public String getFilePath() {
    return filePath;
  }

  /**
   * Gets the line number.
   *
   * @return the line number
   */
  public int getLineNumber() {
    return lineNumber;
  }

  /**
   * Gets the mapper ID.
   *
   * @return the mapper ID
   */
  public String getMapperId() {
    return mapperId;
  }

  /**
   * Gets the SQL snippet.
   *
   * @return the SQL snippet
   */
  public String getSqlSnippet() {
    return sqlSnippet;
  }

  /**
   * Gets the risk level.
   *
   * @return the risk level
   */
  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  /**
   * Gets the violation message.
   *
   * @return the message
   */
  public String getMessage() {
    return message;
  }

  /**
   * Gets the suggestion.
   *
   * @return the suggestion (may be null)
   */
  public String getSuggestion() {
    return suggestion;
  }

  /**
   * Gets the XML snippet.
   *
   * @return the XML snippet (may be null)
   */
  public String getXmlSnippet() {
    return xmlSnippet;
  }

  /**
   * Gets the Java method signature.
   *
   * @return the Java method signature (may be null)
   */
  public String getJavaMethodSignature() {
    return javaMethodSignature;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ViolationEntry that = (ViolationEntry) o;
    return lineNumber == that.lineNumber &&
        Objects.equals(filePath, that.filePath) &&
        Objects.equals(mapperId, that.mapperId) &&
        Objects.equals(sqlSnippet, that.sqlSnippet) &&
        riskLevel == that.riskLevel &&
        Objects.equals(message, that.message) &&
        Objects.equals(suggestion, that.suggestion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(filePath, lineNumber, mapperId, sqlSnippet,
        riskLevel, message, suggestion);
  }

  @Override
  public String toString() {
    return "ViolationEntry{" +
        "filePath='" + filePath + '\'' +
        ", lineNumber=" + lineNumber +
        ", mapperId='" + mapperId + '\'' +
        ", riskLevel=" + riskLevel +
        ", message='" + message + '\'' +
        '}';
  }
}



