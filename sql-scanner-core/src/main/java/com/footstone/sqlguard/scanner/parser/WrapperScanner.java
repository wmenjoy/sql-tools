package com.footstone.sqlguard.scanner.parser;

import com.footstone.sqlguard.scanner.model.WrapperUsage;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Interface for scanning MyBatis-Plus Wrapper API usage in Java source files.
 *
 * <p>WrapperScanner traverses a project directory tree to detect dynamic query
 * construction using QueryWrapper, LambdaQueryWrapper, UpdateWrapper, and
 * LambdaUpdateWrapper classes.</p>
 *
 * <p><strong>Scanning Strategy:</strong></p>
 * <ul>
 *   <li>Recursively traverse all Java files under project root</li>
 *   <li>Detect wrapper instantiation and method chaining patterns</li>
 *   <li>Record usage location (file, method, line number)</li>
 *   <li>Mark all usages as requiring runtime validation (static analysis insufficient)</li>
 * </ul>
 *
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li>Throw IOException for file system errors (directory not accessible)</li>
 *   <li>Log parse errors for individual files but continue processing</li>
 *   <li>Return partial results when some files fail to parse</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> Implementations should be thread-safe to support
 * concurrent scanning of multiple directories.</p>
 *
 * @see WrapperUsage
 * @see com.footstone.sqlguard.scanner.model.SourceType#WRAPPER
 */
public interface WrapperScanner {

  /**
   * Scans a project root directory for MyBatis-Plus Wrapper usage.
   *
   * <p>This method recursively traverses the project directory tree, analyzing
   * Java source files to detect dynamic query construction patterns.</p>
   *
   * @param projectRoot the root directory of the project to scan (must exist)
   * @return list of wrapper usages found (never null, may be empty)
   * @throws IOException if project root does not exist or cannot be accessed
   */
  List<WrapperUsage> scan(File projectRoot) throws IOException;
}




















