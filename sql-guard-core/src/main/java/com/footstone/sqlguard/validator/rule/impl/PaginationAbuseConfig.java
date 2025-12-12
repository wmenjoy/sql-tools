package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration class for pagination abuse detection checkers.
 *
 * <p>PaginationAbuseConfig provides configuration for detecting pagination-related performance
 * issues including deep pagination (high OFFSET values) and excessive page sizes. These patterns
 * cause significant database performance degradation as they require scanning and skipping large
 * numbers of rows before returning results.</p>
 *
 * <p><strong>Deep Pagination Performance Impact:</strong></p>
 * <ul>
 *   <li><strong>Problem:</strong> LIMIT with high OFFSET requires database to scan offset+limit
 *       rows, discard first offset rows, then return limit rows</li>
 *   <li><strong>Example:</strong> "LIMIT 20 OFFSET 100000" scans 100020 rows but returns only 20
 *       </li>
 *   <li><strong>Performance:</strong> Query time increases linearly with offset value</li>
 *   <li><strong>Solution:</strong> Use cursor-based pagination (WHERE id > lastId) to avoid
 *       scanning skipped rows</li>
 * </ul>
 *
 * <p><strong>Configuration Parameters:</strong></p>
 * <ul>
 *   <li><strong>maxOffset:</strong> Maximum allowed OFFSET value before triggering MEDIUM
 *       violation (default: 10000). Teams can adjust based on data volume and performance
 *       requirements.</li>
 *   <li><strong>maxPageSize:</strong> Maximum allowed page size (LIMIT value) before triggering
 *       violation (default: 1000). Used by excessive page size checker (Task 2.10).</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Default configuration (enabled, maxOffset=10000, maxPageSize=1000)
 * PaginationAbuseConfig config = new PaginationAbuseConfig();
 *
 * // Custom thresholds for high-volume system
 * PaginationAbuseConfig customConfig = new PaginationAbuseConfig(true, 5000, 500);
 *
 * // Disable pagination abuse checking
 * PaginationAbuseConfig disabledConfig = new PaginationAbuseConfig(false, 10000, 1000);
 * }</pre>
 *
 * @see com.footstone.sqlguard.validator.pagination.impl.DeepPaginationChecker
 */
public class PaginationAbuseConfig extends CheckerConfig {

  /**
   * Maximum allowed OFFSET value before triggering deep pagination violation.
   * Default: 10000 rows.
   */
  private int maxOffset;

  /**
   * Maximum allowed page size (LIMIT value) before triggering excessive page size violation.
   * Default: 1000 rows.
   */
  private int maxPageSize;

  /**
   * Creates a PaginationAbuseConfig with default values.
   * 
   * <p>Defaults:</p>
   * <ul>
   *   <li>enabled = true</li>
   *   <li>maxOffset = 10000</li>
   *   <li>maxPageSize = 1000</li>
   * </ul>
   */
  public PaginationAbuseConfig() {
    super(); // enabled = true by default
    this.maxOffset = 10000;
    this.maxPageSize = 1000;
  }

  /**
   * Creates a PaginationAbuseConfig with specified values.
   *
   * @param enabled whether pagination abuse checking is enabled
   * @param maxOffset maximum allowed OFFSET value
   * @param maxPageSize maximum allowed page size (LIMIT value)
   */
  public PaginationAbuseConfig(boolean enabled, int maxOffset, int maxPageSize) {
    super(enabled);
    this.maxOffset = maxOffset;
    this.maxPageSize = maxPageSize;
  }

  /**
   * Returns the maximum allowed OFFSET value.
   *
   * @return maximum offset threshold
   */
  public int getMaxOffset() {
    return maxOffset;
  }

  /**
   * Sets the maximum allowed OFFSET value.
   *
   * @param maxOffset maximum offset threshold
   */
  public void setMaxOffset(int maxOffset) {
    this.maxOffset = maxOffset;
  }

  /**
   * Returns the maximum allowed page size.
   *
   * @return maximum page size threshold
   */
  public int getMaxPageSize() {
    return maxPageSize;
  }

  /**
   * Sets the maximum allowed page size.
   *
   * @param maxPageSize maximum page size threshold
   */
  public void setMaxPageSize(int maxPageSize) {
    this.maxPageSize = maxPageSize;
  }
}

