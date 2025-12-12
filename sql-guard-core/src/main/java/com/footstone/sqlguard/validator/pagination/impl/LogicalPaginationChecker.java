package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import com.footstone.sqlguard.validator.rule.impl.LogicalPaginationConfig;
import org.apache.ibatis.session.RowBounds;

/**
 * Checker that detects dangerous logical pagination patterns in MyBatis/MyBatis-Plus applications.
 *
 * <p>This checker identifies CRITICAL-level violations when RowBounds or IPage parameters are used
 * without pagination plugin support, which causes entire result sets to load into memory before
 * in-memory row skipping. This is the most dangerous pagination pattern and frequently causes
 * production OutOfMemoryError crashes.</p>
 *
 * <p><strong>Detection Logic:</strong></p>
 * <ol>
 *   <li>Skip if checker is disabled</li>
 *   <li>Use {@link PaginationPluginDetector} to detect pagination type</li>
 *   <li>If type is {@link PaginationType#LOGICAL}, add CRITICAL violation</li>
 *   <li>Extract offset/limit from RowBounds and add to violation details</li>
 * </ol>
 *
 * <p><strong>Why is Logical Pagination Dangerous?</strong></p>
 * <pre>
 * Without Plugin:
 * 1. SQL executed: SELECT * FROM users (no LIMIT clause)
 * 2. Database returns: 1,000,000 rows
 * 3. MyBatis loads: All 1,000,000 rows into memory
 * 4. MyBatis skips: First 100 rows in-memory
 * 5. MyBatis returns: 20 rows to application
 * Result: 999,980 rows wasted in memory → OutOfMemoryError
 *
 * With Plugin (PageHelper/PaginationInnerInterceptor):
 * 1. SQL rewritten: SELECT * FROM users LIMIT 20 OFFSET 100
 * 2. Database returns: Only 20 rows
 * 3. MyBatis loads: Only 20 rows into memory
 * 4. MyBatis returns: 20 rows to application
 * Result: Memory-safe, efficient
 * </pre>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * PaginationPluginDetector detector = new PaginationPluginDetector(
 *     mybatisInterceptors, mybatisPlusInterceptor);
 * LogicalPaginationConfig config = new LogicalPaginationConfig();
 * LogicalPaginationChecker checker = new LogicalPaginationChecker(detector, config);
 * 
 * ValidationResult result = ValidationResult.pass();
 * checker.check(context, result);
 * 
 * if (!result.isPassed()) {
 *   // CRITICAL violation detected - logical pagination without plugin
 *   System.out.println("Risk: " + result.getRiskLevel()); // CRITICAL
 *   System.out.println("Offset: " + result.getDetails().get("offset"));
 *   System.out.println("Limit: " + result.getDetails().get("limit"));
 * }
 * }</pre>
 *
 * @see PaginationPluginDetector
 * @see PaginationType
 * @see LogicalPaginationConfig
 */
public class LogicalPaginationChecker extends AbstractRuleChecker {

  /**
   * Pagination plugin detector for determining pagination type.
   */
  private final PaginationPluginDetector detector;

  /**
   * Configuration for this checker.
   */
  private final LogicalPaginationConfig config;

  /**
   * Constructs a LogicalPaginationChecker with required dependencies.
   *
   * @param detector pagination plugin detector for type detection
   * @param config configuration controlling checker behavior
   * @throws IllegalArgumentException if detector or config is null
   */
  public LogicalPaginationChecker(PaginationPluginDetector detector, 
      LogicalPaginationConfig config) {
    if (detector == null) {
      throw new IllegalArgumentException("PaginationPluginDetector cannot be null");
    }
    if (config == null) {
      throw new IllegalArgumentException("LogicalPaginationConfig cannot be null");
    }
    this.detector = detector;
    this.config = config;
  }

  /**
   * Checks for logical pagination violations in the SQL execution context.
   *
   * <p><strong>Validation Flow:</strong></p>
   * <ol>
   *   <li>Skip if checker is disabled via configuration</li>
   *   <li>Detect pagination type using {@link PaginationPluginDetector}</li>
   *   <li>Skip if pagination type is not {@link PaginationType#LOGICAL}</li>
   *   <li>Extract pagination parameters (offset/limit) from RowBounds</li>
   *   <li>Add CRITICAL violation with urgent message and actionable suggestion</li>
   *   <li>Add pagination details to result for debugging context</li>
   * </ol>
   *
   * <p><strong>Violation Details:</strong></p>
   * <ul>
   *   <li>{@code offset}: Starting row offset from RowBounds</li>
   *   <li>{@code limit}: Maximum rows to return from RowBounds</li>
   *   <li>{@code paginationType}: Always "LOGICAL" for this violation</li>
   * </ul>
   *
   * @param context SQL execution context containing parsed SQL and parameters
   * @param result validation result to accumulate violations
   */
  @Override
  public void check(SqlContext context, ValidationResult result) {
    // Step 1: Skip if checker disabled
    if (!isEnabled()) {
      return;
    }

    // Step 2: Detect pagination type
    PaginationType type = detector.detectPaginationType(context);

    // Step 3: Skip if not LOGICAL pagination
    if (type != PaginationType.LOGICAL) {
      return;
    }

    // Step 4: Extract pagination parameters
    Object rowBoundsObj = context.getRowBounds();
    int offset = 0;
    int limit = 0;

    if (rowBoundsObj instanceof RowBounds) {
      RowBounds rowBounds = (RowBounds) rowBoundsObj;
      offset = rowBounds.getOffset();
      limit = rowBounds.getLimit();
    }

    // Step 5: Add CRITICAL violation
    String message = "检测到逻辑分页!将加载全表数据到内存,可能导致OOM";
    String suggestion = "立即配置分页插件:MyBatis-Plus PaginationInnerInterceptor或PageHelper";
    result.addViolation(RiskLevel.CRITICAL, message, suggestion);

    // Step 6: Add pagination parameters to violation details
    result.getDetails().put("offset", offset);
    result.getDetails().put("limit", limit);
    result.getDetails().put("paginationType", "LOGICAL");
  }

  /**
   * Returns whether this checker is enabled.
   *
   * @return true if enabled, false otherwise
   */
  @Override
  public boolean isEnabled() {
    return config.isEnabled();
  }
}

