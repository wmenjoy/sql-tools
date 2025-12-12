package com.footstone.sqlguard.validator.pagination.impl;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.PaginationType;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

/**
 * Checker detecting unconditioned physical pagination (LIMIT without WHERE clause).
 *
 * <p>This is the highest-priority physical pagination checker with CRITICAL risk level.
 * It detects LIMIT queries without WHERE clauses, which still perform full table scans
 * despite pagination. Even though LIMIT restricts the number of returned rows, the database
 * must scan the entire table to determine which rows to return.</p>
 *
 * <p><strong>Detection Logic:</strong></p>
 * <ol>
 *   <li>Skip if checker is disabled</li>
 *   <li>Detect pagination type using PaginationPluginDetector</li>
 *   <li>Skip if not PHYSICAL pagination (LOGICAL/NONE handled by other checkers)</li>
 *   <li>Extract WHERE clause from SELECT statement</li>
 *   <li>Check for no-condition or dummy condition (WHERE 1=1, WHERE true)</li>
 *   <li>If violated, extract LIMIT details and add CRITICAL violation</li>
 *   <li>Set early-return flag to prevent misleading subsequent checker violations</li>
 * </ol>
 *
 * <p><strong>Early-Return Mechanism:</strong></p>
 * <p>When this checker detects a violation, it sets
 * {@code result.getDetails().put("earlyReturn", true)} to signal subsequent physical
 * pagination checkers (deep offset, missing ORDER BY) to skip their checks. This prevents
 * misleading violations - if there's no WHERE clause, concerns about deep offset or missing
 * ORDER BY are irrelevant since the query is already catastrophic.</p>
 *
 * <p><strong>Example Violations:</strong></p>
 * <pre>{@code
 * // CRITICAL: Full table scan despite LIMIT
 * SELECT * FROM user LIMIT 100;
 * 
 * // CRITICAL: Dummy condition equivalent to no condition
 * SELECT * FROM user WHERE 1=1 LIMIT 100;
 * SELECT * FROM user WHERE true LIMIT 50;
 * 
 * // PASS: Proper WHERE condition limits scan range
 * SELECT * FROM user WHERE status='active' LIMIT 100;
 * }</pre>
 *
 * <p><strong>Violation Message:</strong></p>
 * <ul>
 *   <li>Message: "无条件物理分页,仍会全表扫描,仅限制返回行数"</li>
 *   <li>Suggestion: "添加业务WHERE条件限制查询范围"</li>
 *   <li>Risk Level: CRITICAL</li>
 * </ul>
 *
 * @see PaginationPluginDetector
 * @see PaginationType
 * @see AbstractRuleChecker
 */
public class NoConditionPaginationChecker extends AbstractRuleChecker {

  /**
   * Configuration for this checker.
   */
  private final NoConditionPaginationConfig config;

  /**
   * Pagination plugin detector for determining pagination type.
   */
  private final PaginationPluginDetector detector;

  /**
   * Constructs a NoConditionPaginationChecker with configuration and detector.
   *
   * @param config the checker configuration
   * @param detector the pagination plugin detector
   */
  public NoConditionPaginationChecker(NoConditionPaginationConfig config,
      PaginationPluginDetector detector) {
    this.config = config;
    this.detector = detector;
  }

  /**
   * Checks for unconditioned physical pagination violations.
   *
   * <p>This method implements the detection logic described in the class documentation.
   * It detects PHYSICAL pagination without WHERE clauses and adds CRITICAL violations
   * with early-return flag to prevent misleading subsequent checker violations.</p>
   *
   * @param context the SQL execution context
   * @param result the validation result to populate
   */
  @Override
  public void check(SqlContext context, ValidationResult result) {
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

    // Step 4: Extract WHERE clause
    Statement stmt = context.getParsedSql();
    Expression where = extractWhere(stmt);

    // Step 5: Check for no-condition or dummy condition
    if (where == null || isDummyCondition(where)) {
      // Step 6: Extract LIMIT details for violation report
      extractLimitDetails(stmt, result);

      // Step 7: Add CRITICAL violation
      result.addViolation(
          config.getRiskLevel(),
          "无条件物理分页,仍会全表扫描,仅限制返回行数",
          "添加业务WHERE条件限制查询范围"
      );

      // Step 8: Set early-return flag
      result.getDetails().put("earlyReturn", true);
    }
  }

  /**
   * Extracts LIMIT details from SELECT statement and adds them to result details.
   *
   * <p>This method extracts the following information:</p>
   * <ul>
   *   <li>limit: The row count limit (LIMIT value)</li>
   *   <li>offset: The offset value (OFFSET value or 0 if not present)</li>
   * </ul>
   *
   * <p>Supports both standard SQL syntax (LIMIT n OFFSET m) and MySQL comma syntax
   * (LIMIT m, n).</p>
   *
   * @param stmt the parsed SQL statement
   * @param result the validation result to populate with details
   */
  private void extractLimitDetails(Statement stmt, ValidationResult result) {
    if (!(stmt instanceof Select)) {
      return;
    }

    Select select = (Select) stmt;
    if (!(select.getSelectBody() instanceof PlainSelect)) {
      return;
    }

    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
    Limit limit = plainSelect.getLimit();

    if (limit == null) {
      return;
    }

    // Extract row count (LIMIT value)
    if (limit.getRowCount() != null) {
      result.getDetails().put("limit", limit.getRowCount().toString());
    }

    // Extract offset (OFFSET value or 0 if not present)
    if (limit.getOffset() != null) {
      result.getDetails().put("offset", limit.getOffset().toString());
    } else {
      result.getDetails().put("offset", "0");
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

