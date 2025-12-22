package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import com.footstone.sqlguard.validator.rule.impl.PaginationAbuseConfig;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.Offset;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

/**
 * Checker that detects deep pagination (high OFFSET values) causing performance degradation.
 *
 * <p>DeepPaginationChecker identifies LIMIT queries with excessive OFFSET values that force the
 * database to scan and discard large numbers of rows before returning results. This pattern causes
 * query performance to degrade linearly with offset value.</p>
 *
 * <p><strong>Performance Impact:</strong></p>
 * <ul>
 *   <li><strong>Problem:</strong> "LIMIT 20 OFFSET 100000" requires database to scan 100020 rows,
 *       discard first 100000 rows, then return 20 rows</li>
 *   <li><strong>Performance:</strong> Query time increases linearly with offset (O(n) where n is
 *       offset value)</li>
 *   <li><strong>Resource Usage:</strong> High CPU, disk I/O, and buffer pool pressure</li>
 *   <li><strong>User Impact:</strong> Slow page loads for users navigating to high page numbers</li>
 * </ul>
 *
 * <p><strong>Detection Logic:</strong></p>
 * <ol>
 *   <li>Skip if checker disabled via configuration</li>
 *   <li>Detect pagination type using PaginationPluginDetector</li>
 *   <li>Skip if not PHYSICAL pagination (only LIMIT-based queries are checked)</li>
 *   <li>Check for early-return flag from NoConditionPaginationChecker (Task 2.8)</li>
 *   <li>Extract OFFSET value from LIMIT clause (supports both syntaxes)</li>
 *   <li>Compare against maxOffset threshold</li>
 *   <li>Add MEDIUM violation if offset > maxOffset</li>
 * </ol>
 *
 * <p><strong>Supported LIMIT Syntaxes:</strong></p>
 * <ul>
 *   <li><strong>Standard:</strong> "LIMIT n OFFSET m" (offset extracted from Limit.getOffset())</li>
 *   <li><strong>MySQL:</strong> "LIMIT m,n" (JSqlParser 4.6 parses both syntaxes into
 *       Limit.getOffset())</li>
 * </ul>
 *
 * <p><strong>Early-Return Integration:</strong></p>
 * <p>This checker checks for early-return flag set by NoConditionPaginationChecker (Task 2.8).
 * If a query has no WHERE clause (CRITICAL violation), there's no point checking for deep offset
 * since the query already performs a full table scan. The early-return flag prevents misleading
 * duplicate violations.</p>
 *
 * <p><strong>Recommended Solution:</strong></p>
 * <p>Replace offset-based pagination with cursor-based pagination:</p>
 * <pre>{@code
 * // Instead of: SELECT * FROM user ORDER BY id LIMIT 20 OFFSET 100000
 * // Use: SELECT * FROM user WHERE id > ? ORDER BY id LIMIT 20
 * }</pre>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * PaginationAbuseConfig config = new PaginationAbuseConfig(true, 10000, 1000);
 * PaginationPluginDetector detector = new PaginationPluginDetector(interceptors, mpInterceptor);
 * DeepPaginationChecker checker = new DeepPaginationChecker(config, detector);
 *
 * SqlContext context = SqlContext.builder()
 *     .sql("SELECT * FROM user WHERE id > 0 LIMIT 20 OFFSET 50000")
 *     .statement(parser.parse(sql))
 *     .build();
 * ValidationResult result = ValidationResult.pass();
 *
 * checker.check(context, result);
 * // result.isPassed() == false
 * // result.getRiskLevel() == RiskLevel.MEDIUM
 * // violation message: "深分页offset=50000,需扫描并跳过50000行数据,性能较差"
 * }</pre>
 *
 * @see PaginationAbuseConfig
 * @see PaginationPluginDetector
 * @see PaginationType
 */
public class DeepPaginationChecker extends AbstractRuleChecker {

  /**
   * Configuration for pagination abuse detection.
   */
  private final PaginationAbuseConfig config;

  /**
   * Detector for pagination type (LOGICAL, PHYSICAL, NONE).
   */
  private final PaginationPluginDetector detector;

  /**
   * Constructs a DeepPaginationChecker with specified configuration and detector.
   *
   * @param config configuration containing maxOffset threshold
   * @param detector pagination type detector
   */
  public DeepPaginationChecker(PaginationAbuseConfig config, PaginationPluginDetector detector) {
    super(config);  // NEW: Pass config to AbstractRuleChecker
    this.config = config;
    this.detector = detector;
  }

  /**
   * Visit SELECT statement to check for deep pagination violations.
   *
   * <p>Detection steps:</p>
   * <ol>
   *   <li>Skip if checker disabled</li>
   *   <li>Detect pagination type (must be PHYSICAL)</li>
   *   <li>Check early-return flag (skip if Task 2.8 already violated)</li>
   *   <li>Extract OFFSET from LIMIT clause</li>
   *   <li>Compare against maxOffset threshold</li>
   *   <li>Add MEDIUM violation if offset > maxOffset</li>
   * </ol>
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

    // Step 3: Skip if not PHYSICAL pagination (only check LIMIT-based queries)
    if (type != PaginationType.PHYSICAL) {
      return;
    }

    // Step 4: Check for early-return flag from Task 2.8 (NoConditionPaginationChecker)
    if (getCurrentResult().getDetails().containsKey("earlyReturn")
        && getCurrentResult().getDetails().get("earlyReturn") == Boolean.TRUE) {
      // Skip: NoConditionPaginationChecker already violated (no WHERE clause)
      // Deep offset is irrelevant if query performs full table scan anyway
      return;
    }

    // Step 5: Extract Limit from SELECT statement
    if (!(select.getSelectBody() instanceof PlainSelect)) {
      return; // Only PlainSelect has LIMIT clause (not UNION, etc.)
    }

    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
    Limit limit = plainSelect.getLimit();
    if (limit == null) {
      return; // No LIMIT clause
    }

    // Step 6: Calculate offset supporting multiple LIMIT syntaxes
    long offset = 0;

    // In JSqlParser 4.6, OFFSET handling differs by syntax:
    // Syntax 1: "LIMIT n OFFSET m" - offset stored in PlainSelect.getOffset()
    // Syntax 2: "LIMIT m,n" (MySQL) - offset stored in Limit.getOffset()
    
    // Try PlainSelect.getOffset() first (standard OFFSET keyword syntax)
    if (plainSelect.getOffset() != null) {
      try {
        Offset offsetObj = plainSelect.getOffset();
        if (offsetObj.getOffset() != null) {
          String offsetStr = offsetObj.getOffset().toString();
          offset = Long.parseLong(offsetStr.trim());
        }
      } catch (NumberFormatException e) {
        // If offset is a parameter placeholder (e.g., "?"), skip check
        return;
      }
    }
    // Try Limit.getOffset() for MySQL comma syntax
    else if (limit.getOffset() != null) {
      try {
        String offsetStr = limit.getOffset().toString();
        offset = Long.parseLong(offsetStr.trim());
      } catch (NumberFormatException e) {
        // If offset is a parameter placeholder (e.g., "?"), skip check
        return;
      }
    }

    // Step 7: Compare against threshold
    if (offset > config.getMaxOffset()) {
      // Add MEDIUM violation
      String message = String.format(
          "深分页offset=%d,需扫描并跳过%d行数据,性能较差",
          offset, offset
      );
      String suggestion = "建议使用游标分页(WHERE id > lastId)避免深度offset";

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
