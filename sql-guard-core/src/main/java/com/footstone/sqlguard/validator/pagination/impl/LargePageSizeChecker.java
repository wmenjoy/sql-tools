package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import com.footstone.sqlguard.validator.rule.impl.PaginationAbuseConfig;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

/**
 * Checker detecting excessively large pageSize values in LIMIT queries.
 *
 * <p>This MEDIUM-level checker detects when a single query returns massive datasets via large
 * LIMIT values (e.g., LIMIT 10000), potentially overwhelming application memory or network
 * bandwidth. Unlike deep pagination (high OFFSET), which causes database performance issues,
 * large pageSize causes memory and bandwidth problems in the application layer.</p>
 *
 * <p><strong>Detection Logic:</strong></p>
 * <ol>
 *   <li>Skip if checker is disabled</li>
 *   <li>Detect pagination type using PaginationPluginDetector</li>
 *   <li>Skip if not PHYSICAL pagination (only check LIMIT queries)</li>
 *   <li>Extract LIMIT clause from SELECT statement</li>
 *   <li>Calculate pageSize from limit.getRowCount() (works for both LIMIT syntaxes)</li>
 *   <li>If pageSize > maxPageSize, add MEDIUM violation</li>
 * </ol>
 *
 * <p><strong>PageSize vs Offset:</strong></p>
 * <ul>
 *   <li><strong>PageSize:</strong> Number of rows to return (limit.getRowCount())</li>
 *   <li><strong>Offset:</strong> Number of rows to skip before returning</li>
 *   <li>LIMIT 100,500: offset=100, pageSize=500</li>
 *   <li>LIMIT 500 OFFSET 100: offset=100, pageSize=500</li>
 *   <li>LIMIT 500: offset=0, pageSize=500</li>
 * </ul>
 *
 * <p><strong>Memory and Bandwidth Impact:</strong></p>
 * <pre>
 * Example: LIMIT 5000
 * - Database returns: 5000 rows
 * - Network transfer: 5000 rows × row size (e.g., 5000 × 1KB = 5MB)
 * - JVM memory: 5000 row objects in heap
 * - Application processing: 5000 rows to serialize/process
 * 
 * Problem: Single query consuming excessive resources
 * Solution: Reduce pageSize to reasonable limit (default 1000)
 * </pre>
 *
 * <p><strong>Example Violations:</strong></p>
 * <pre>{@code
 * // MEDIUM: PageSize too large
 * SELECT * FROM user WHERE status='active' LIMIT 5000;
 * 
 * // MEDIUM: PageSize too large (comma syntax)
 * SELECT * FROM user WHERE status='active' LIMIT 100, 5000;
 * 
 * // PASS: PageSize within threshold
 * SELECT * FROM user WHERE status='active' LIMIT 500;
 * }</pre>
 *
 * <p><strong>Configuration:</strong></p>
 * <ul>
 *   <li>Uses {@link PaginationAbuseConfig#getMaxPageSize()} (default 1000)</li>
 *   <li>Threshold configurable per team's requirements</li>
 *   <li>Can be disabled via {@link PaginationAbuseConfig#isEnabled()}</li>
 * </ul>
 *
 * <p><strong>Independent Check:</strong></p>
 * <p>This checker is independent from DeepPaginationChecker. Both can trigger on the same SQL
 * if it has both high OFFSET and large LIMIT values. Example:</p>
 * <pre>{@code
 * SELECT * FROM user LIMIT 5000 OFFSET 50000;
 * // Triggers both:
 * // - DeepPaginationChecker: offset=50000 > maxOffset=10000
 * // - LargePageSizeChecker: pageSize=5000 > maxPageSize=1000
 * }</pre>
 *
 * @see PaginationAbuseConfig
 * @see PaginationPluginDetector
 * @see PaginationType
 */
public class LargePageSizeChecker extends AbstractRuleChecker {

  /**
   * Pagination plugin detector for determining pagination type.
   */
  private final PaginationPluginDetector detector;

  /**
   * Configuration for pagination abuse checking (shared with DeepPaginationChecker).
   */
  private final PaginationAbuseConfig config;

  /**
   * Constructs a LargePageSizeChecker with required dependencies.
   *
   * @param detector pagination plugin detector for type detection
   * @param config configuration controlling checker behavior and thresholds
   * @throws IllegalArgumentException if detector or config is null
   */
  public LargePageSizeChecker(PaginationPluginDetector detector, PaginationAbuseConfig config) {
    super(config);  // NEW: Pass config to AbstractRuleChecker
    if (detector == null) {
      throw new IllegalArgumentException("PaginationPluginDetector cannot be null");
    }
    if (config == null) {
      throw new IllegalArgumentException("PaginationAbuseConfig cannot be null");
    }
    this.detector = detector;
    this.config = config;
  }

  /**
   * Visit SELECT statement to check for large pageSize violations.
   *
   * <p><strong>Validation Flow:</strong></p>
   * <ol>
   *   <li>Skip if checker is disabled via configuration</li>
   *   <li>Detect pagination type using {@link PaginationPluginDetector}</li>
   *   <li>Skip if pagination type is not {@link PaginationType#PHYSICAL}</li>
   *   <li>Extract LIMIT clause from SELECT statement</li>
   *   <li>Calculate pageSize from limit.getRowCount() (handles both LIMIT syntaxes)</li>
   *   <li>Compare pageSize against config.getMaxPageSize() threshold</li>
   *   <li>If pageSize > maxPageSize, add MEDIUM violation with details</li>
   * </ol>
   *
   * <p><strong>LIMIT Syntax Support:</strong></p>
   * <ul>
   *   <li>LIMIT n: getRowCount() returns n</li>
   *   <li>LIMIT m,n: getRowCount() returns n (not m, which is offset)</li>
   *   <li>LIMIT n OFFSET m: getRowCount() returns n</li>
   * </ul>
   *
   * @param select the SELECT statement
   * @param context SQL execution context containing parsed SQL and parameters
   */
  @Override
  public void visitSelect(Select select, SqlContext context) {
    // Step 1: Skip if checker disabled
    if (!isEnabled()) {
      return;
    }

    // Step 2: Detect pagination type
    PaginationType type = detector.detectPaginationType(context);

    // Step 3: Skip if not PHYSICAL pagination
    if (type != PaginationType.PHYSICAL) {
      return;
    }

    // Step 4: Extract Limit from PlainSelect
    if (!(select.getSelectBody() instanceof PlainSelect)) {
      return;
    }

    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
    Limit limit = plainSelect.getLimit();

    // No LIMIT clause
    if (limit == null) {
      return;
    }

    // Step 5: Calculate pageSize from LIMIT clause
    long pageSize = 0;
    if (limit.getRowCount() != null) {
      // getRowCount() returns Expression, parse string to get numeric value
      // Works for both "LIMIT n" and "LIMIT m,n" syntaxes
      try {
        String pageSizeStr = limit.getRowCount().toString();
        pageSize = Long.parseLong(pageSizeStr);
      } catch (NumberFormatException e) {
        // If pageSize is a parameter placeholder (e.g., "?"), we can't determine the value
        // Skip this check as we can't evaluate dynamic values at static analysis time
        return;
      }
    }

    // Step 6: Compare against threshold
    if (pageSize > config.getMaxPageSize()) {
      // Add MEDIUM violation
      String message = "pageSize=" + pageSize + "过大,单次查询数据量过多";
      String suggestion = "建议降低pageSize到" + config.getMaxPageSize() + "以内,避免单次返回过多数据";
      addViolation(RiskLevel.MEDIUM, message, suggestion);
    }
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
