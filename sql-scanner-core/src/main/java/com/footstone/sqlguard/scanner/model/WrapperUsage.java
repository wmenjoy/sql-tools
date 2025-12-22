package com.footstone.sqlguard.scanner.model;

import java.util.Objects;

/**
 * Represents a MyBatis-Plus Wrapper usage detected during static analysis.
 *
 * <p>WrapperUsage captures metadata about dynamic query construction using
 * MyBatis-Plus Wrapper API (QueryWrapper, UpdateWrapper, etc.).</p>
 *
 * <p><strong>Runtime Check Requirement:</strong> Static analysis cannot determine
 * the actual SQL conditions built dynamically at runtime, so needsRuntimeCheck
 * is always true for wrapper usages.</p>
 *
 * @see SourceType#WRAPPER
 */
public class WrapperUsage {
  private final String filePath;
  private final String methodName;
  private final int lineNumber;
  private final String wrapperType;
  private final boolean needsRuntimeCheck;

  /**
   * Constructs a new WrapperUsage with all required fields.
   *
   * @param filePath the absolute path to the Java file
   * @param methodName the method containing the wrapper usage
   * @param lineNumber the line number where wrapper is created
   * @param wrapperType the wrapper type (QueryWrapper, LambdaQueryWrapper, etc.)
   * @param needsRuntimeCheck whether runtime validation is needed (typically true)
   */
  public WrapperUsage(String filePath, String methodName, int lineNumber,
                      String wrapperType, boolean needsRuntimeCheck) {
    this.filePath = filePath;
    this.methodName = methodName;
    this.lineNumber = lineNumber;
    this.wrapperType = wrapperType;
    this.needsRuntimeCheck = needsRuntimeCheck;
  }

  /**
   * Gets the absolute path to the Java file.
   *
   * @return the file path
   */
  public String getFilePath() {
    return filePath;
  }

  /**
   * Gets the method name containing the wrapper usage.
   *
   * @return the method name
   */
  public String getMethodName() {
    return methodName;
  }

  /**
   * Gets the line number where wrapper is created.
   *
   * @return the line number (1-based)
   */
  public int getLineNumber() {
    return lineNumber;
  }

  /**
   * Gets the wrapper type.
   *
   * @return the wrapper type (QueryWrapper, LambdaQueryWrapper, UpdateWrapper, LambdaUpdateWrapper)
   */
  public String getWrapperType() {
    return wrapperType;
  }

  /**
   * Checks if runtime validation is needed for this wrapper usage.
   *
   * <p>Static analysis cannot determine dynamic query conditions,
   * so this is typically always true for wrappers.</p>
   *
   * @return true if runtime check needed (default: true)
   */
  public boolean isNeedsRuntimeCheck() {
    return needsRuntimeCheck;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WrapperUsage that = (WrapperUsage) o;
    return lineNumber == that.lineNumber &&
        Objects.equals(filePath, that.filePath) &&
        Objects.equals(methodName, that.methodName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(filePath, methodName, lineNumber);
  }

  @Override
  public String toString() {
    return "WrapperUsage{" +
        "filePath='" + filePath + '\'' +
        ", methodName='" + methodName + '\'' +
        ", lineNumber=" + lineNumber +
        ", wrapperType='" + wrapperType + '\'' +
        ", needsRuntimeCheck=" + needsRuntimeCheck +
        '}';
  }
}













