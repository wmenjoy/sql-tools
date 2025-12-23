package com.footstone.sqlguard.scanner.model;

/**
 * Enumeration of SQL source types for static analysis.
 *
 * <p>SourceType categorizes where SQL statements are defined in the codebase,
 * enabling different parsing strategies for each source type.</p>
 *
 * <p><strong>Source Types:</strong></p>
 * <ul>
 *   <li><strong>XML</strong> - SQL defined in MyBatis XML mapper files</li>
 *   <li><strong>ANNOTATION</strong> - SQL defined in Java annotations (@Select, @Insert, etc.)</li>
 *   <li><strong>WRAPPER</strong> - SQL constructed using MyBatis-Plus Wrapper API</li>
 * </ul>
 *
 * @see SqlEntry
 */
public enum SourceType {
  /**
   * SQL defined in MyBatis XML mapper files.
   * Typically found in src/main/resources with .xml extension.
   */
  XML,

  /**
   * SQL defined in Java method annotations.
   * Examples: @Select, @Insert, @Update, @Delete from MyBatis.
   */
  ANNOTATION,

  /**
   * SQL constructed dynamically using MyBatis-Plus Wrapper API.
   * Examples: QueryWrapper, LambdaQueryWrapper, UpdateWrapper, LambdaUpdateWrapper.
   */
  WRAPPER
}


















