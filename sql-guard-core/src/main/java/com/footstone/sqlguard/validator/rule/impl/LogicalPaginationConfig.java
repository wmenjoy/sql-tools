package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration for LogicalPaginationChecker that detects dangerous logical pagination patterns.
 *
 * <p>Logical pagination occurs when MyBatis RowBounds or MyBatis-Plus IPage parameters are used
 * without pagination plugin support. This causes the entire result set to be loaded into memory,
 * followed by in-memory offset/limit operations, which can lead to OutOfMemoryError in production
 * environments.</p>
 *
 * <p><strong>What is Logical Pagination?</strong></p>
 * <ul>
 *   <li><strong>Scenario:</strong> Developer uses {@code RowBounds(offset, limit)}
 *       or {@code IPage<T>} parameter without configuring PageHelper or
 *       PaginationInnerInterceptor</li>
 *   <li><strong>Execution Flow:</strong>
 *       <ol>
 *         <li>MyBatis executes SQL without LIMIT clause: {@code SELECT * FROM users}</li>
 *         <li>Database returns ALL matching rows (e.g., 1 million rows)</li>
 *         <li>MyBatis loads entire result set into memory</li>
 *         <li>MyBatis performs in-memory skip: {@code resultSet.skip(offset)}</li>
 *         <li>MyBatis returns only {@code limit} rows to application</li>
 *       </ol>
 *   </li>
 *   <li><strong>Risk:</strong> For large tables, this loads millions of rows into memory,
 *       causing OutOfMemoryError and application crashes</li>
 * </ul>
 *
 * <p><strong>Physical Pagination (Safe):</strong></p>
 * <ul>
 *   <li>With PageHelper or PaginationInnerInterceptor configured, the plugin intercepts
 *       RowBounds/IPage and rewrites SQL to include LIMIT clause</li>
 *   <li>Database performs row filtering: {@code SELECT * FROM users LIMIT 10 OFFSET 100}</li>
 *   <li>Only requested page is returned from database - memory-safe</li>
 * </ul>
 *
 * <p><strong>Configuration Example:</strong></p>
 * <pre>{@code
 * // Enable checker (default)
 * LogicalPaginationConfig config = new LogicalPaginationConfig();
 * 
 * // Disable checker
 * LogicalPaginationConfig config = new LogicalPaginationConfig(false);
 * }</pre>
 *
 * <p><strong>Remediation:</strong></p>
 * <ul>
 *   <li><strong>MyBatis:</strong> Configure PageHelper plugin in mybatis-config.xml</li>
 *   <li><strong>MyBatis-Plus:</strong> Register PaginationInnerInterceptor in
 *       Spring configuration</li>
 * </ul>
 *
 * @see com.footstone.sqlguard.validator.pagination.impl.LogicalPaginationChecker
 * @see com.footstone.sqlguard.validator.pagination.PaginationType
 */
public class LogicalPaginationConfig extends CheckerConfig {

  /**
   * Creates a LogicalPaginationConfig with enabled=true (default).
   *
   * <p>By default, logical pagination checking is enabled because it represents a
   * CRITICAL production risk that can cause OutOfMemoryError crashes.</p>
   */
  public LogicalPaginationConfig() {
    super();
  }

  /**
   * Creates a LogicalPaginationConfig with specified enabled state.
   *
   * @param enabled whether the logical pagination checker should be enabled
   */
  public LogicalPaginationConfig(boolean enabled) {
    super(enabled);
  }
}

