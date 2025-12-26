package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration for MissingOrderByChecker.
 *
 * <p>Controls validation of ORDER BY clause presence in physical pagination queries. Without ORDER
 * BY, database default ordering is not guaranteed to be stable across executions, leading to
 * unpredictable pagination results.
 *
 * <h3>Why ORDER BY Matters for Pagination:</h3>
 *
 * <ul>
 *   <li><b>Result Instability:</b> Without ORDER BY, the same query may return different row orders
 *       on different executions
 *   <li><b>Pagination Inconsistency:</b> Page 2 may show rows that appeared on page 1 in a
 *       previous request
 *   <li><b>User Experience:</b> Confusing and inconsistent pagination in user-facing features
 *       (product listings, search results)
 *   <li><b>Data Integrity:</b> Critical for features requiring deterministic result ordering
 * </ul>
 *
 * <h3>Database Behavior Without ORDER BY:</h3>
 *
 * <p>When no ORDER BY clause is specified, databases may return rows in any order based on:
 *
 * <ul>
 *   <li>Physical storage order (heap order)
 *   <li>Index scan order (if optimizer chooses index)
 *   <li>Parallel query execution order
 *   <li>Buffer cache state
 * </ul>
 *
 * <p>None of these orderings are guaranteed to be stable or repeatable across queries.
 *
 * <h3>Configuration:</h3>
 *
 * <pre>{@code
 * # Enable ORDER BY validation for pagination queries
 * missingOrderBy:
 *   enabled: true  # Default: true
 * }</pre>
 *
 * <h3>Risk Level:</h3>
 *
 * <p>This checker reports violations at LOW risk level because:
 *
 * <ul>
 *   <li>Query executes successfully (no errors)
 *   <li>Results are returned (just unpredictable order)
 *   <li>Most critical for user-facing features
 *   <li>Less critical for batch processing or single-page results
 * </ul>
 *
 * @author SQL Guard Team
 * @since 1.0.0
 */
public class MissingOrderByConfig extends CheckerConfig {

  /**
   * Creates configuration with ORDER BY validation enabled by default.
   *
   * <p>Default behavior is to validate ORDER BY presence for all physical pagination queries.
   */
  public MissingOrderByConfig() {
    super(true);
  }

  /**
   * Creates configuration with specified enabled state.
   *
   * @param enabled whether to validate ORDER BY presence in pagination queries
   */
  public MissingOrderByConfig(boolean enabled) {
    super(enabled);
  }
}




















