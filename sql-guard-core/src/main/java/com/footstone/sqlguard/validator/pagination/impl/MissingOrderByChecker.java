package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import com.footstone.sqlguard.validator.rule.impl.MissingOrderByConfig;
import java.util.List;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

/**
 * Checker for detecting physical pagination queries missing ORDER BY clause.
 *
 * <p>This checker validates that physical pagination queries (using LIMIT/OFFSET) include an ORDER
 * BY clause to ensure stable and predictable result ordering across page requests.
 *
 * <h3>Problem Statement:</h3>
 *
 * <p>Without ORDER BY, database default ordering is not guaranteed stable:
 *
 * <ul>
 *   <li><b>Same Query, Different Results:</b> Executing the same pagination query multiple times
 *       may return rows in different orders
 *   <li><b>Page Overlap:</b> Page 2 may show rows that appeared on page 1 in a previous request
 *   <li><b>Missing Rows:</b> Some rows may never appear in any page due to ordering changes
 *   <li><b>User Confusion:</b> Inconsistent pagination creates poor user experience
 * </ul>
 *
 * <h3>Detection Logic:</h3>
 *
 * <ol>
 *   <li>Check if checker is enabled (skip if disabled)
 *   <li>Detect pagination type using {@link PaginationPluginDetector}
 *   <li>Skip if not PHYSICAL pagination (LOGICAL and NONE types ignored)
 *   <li>Extract ORDER BY elements from SELECT statement
 *   <li>Report LOW violation if ORDER BY is missing or empty
 * </ol>
 *
 * <h3>Scope:</h3>
 *
 * <p>This checker performs a simple presence check:
 *
 * <ul>
 *   <li><b>Validates:</b> ORDER BY clause exists (not null and not empty)
 *   <li><b>Does NOT validate:</b> ORDER BY quality (unique columns, proper indexing)
 *   <li><b>Does NOT validate:</b> ORDER BY column selection appropriateness
 *   <li><b>Does NOT validate:</b> Index support for ORDER BY columns
 * </ul>
 *
 * <h3>Risk Level:</h3>
 *
 * <p>Violations are reported at LOW risk level because:
 *
 * <ul>
 *   <li>Query executes successfully without errors
 *   <li>Results are returned (just unpredictable order)
 *   <li>Most critical for user-facing features
 *   <li>Less critical for batch processing or single-page results
 * </ul>
 *
 * <h3>Example Violations:</h3>
 *
 * <pre>{@code
 * // VIOLATION: Physical pagination without ORDER BY
 * SELECT * FROM user WHERE status = 'active' LIMIT 10
 *
 * // VIOLATION: OFFSET pagination without ORDER BY
 * SELECT * FROM product WHERE price > 100 LIMIT 10 OFFSET 20
 *
 * // PASS: ORDER BY ensures stable ordering
 * SELECT * FROM user WHERE status = 'active' ORDER BY id LIMIT 10
 *
 * // PASS: Multiple ORDER BY columns
 * SELECT * FROM product WHERE price > 100 ORDER BY category, id LIMIT 10
 * }</pre>
 *
 * <h3>Configuration:</h3>
 *
 * <pre>{@code
 * missingOrderBy:
 *   enabled: true  # Default: true
 * }</pre>
 *
 * @author SQL Guard Team
 * @see MissingOrderByConfig
 * @see PaginationPluginDetector
 * @see PaginationType
 * @since 1.0.0
 */
public class MissingOrderByChecker extends AbstractRuleChecker {

  private final PaginationPluginDetector detector;
  private final MissingOrderByConfig config;

  /**
   * Creates checker with pagination detector and configuration.
   *
   * @param detector pagination type detector for identifying PHYSICAL pagination
   * @param config configuration controlling checker enabled state
   */
  public MissingOrderByChecker(PaginationPluginDetector detector, MissingOrderByConfig config) {
    super(config);  // NEW: Pass config to AbstractRuleChecker
    this.detector = detector;
    this.config = config;
  }

  /**
   * Visit SELECT statement to check for missing ORDER BY in physical pagination.
   *
   * <p>Detection flow:
   *
   * <ol>
   *   <li>Skip if checker disabled
   *   <li>Detect pagination type (PHYSICAL, LOGICAL, or NONE)
   *   <li>Skip if not PHYSICAL pagination
   *   <li>Extract ORDER BY elements from SELECT statement
   *   <li>Add LOW violation if ORDER BY missing or empty
   * </ol>
   *
   * @param select the SELECT statement
   * @param context SQL context containing parsed statement and pagination metadata
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

    // Step 4: Extract ORDER BY elements from PlainSelect
    if (!(select.getSelectBody() instanceof PlainSelect)) {
      return;
    }
    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
    List<OrderByElement> orderByElements = plainSelect.getOrderByElements();

    // Step 5: Check if ORDER BY missing or empty
    if (orderByElements == null || orderByElements.isEmpty()) {
      addViolation(
          RiskLevel.LOW,
          "分页查询缺少ORDER BY,结果顺序不稳定",
          "添加ORDER BY子句确保分页结果顺序稳定");
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
