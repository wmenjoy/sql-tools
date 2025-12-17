package com.footstone.sqlguard.validator.pagination;

/**
 * Enum representing different types of pagination detected in SQL execution.
 *
 * <p>This enum is used by pagination detection infrastructure to distinguish between
 * safe and dangerous pagination patterns in MyBatis/MyBatis-Plus applications.</p>
 *
 * <p><strong>Pagination Type Definitions:</strong></p>
 * <ul>
 *   <li><strong>LOGICAL:</strong> In-memory pagination using RowBounds or IPage parameters
 *       without pagination plugin support. This loads the entire result set into memory,
 *       then performs offset/limit operations in-memory. <strong>Dangerous</strong> - can
 *       cause OutOfMemoryError with large result sets.</li>
 *   <li><strong>PHYSICAL:</strong> Database-level pagination using SQL LIMIT clause or
 *       pagination plugin (PageHelper/PaginationInnerInterceptor). The database performs
 *       row filtering, returning only the requested page. <strong>Safe</strong> - efficient
 *       and memory-safe.</li>
 *   <li><strong>NONE:</strong> No pagination detected. Query returns all matching rows
 *       without any pagination mechanism.</li>
 * </ul>
 *
 * <p><strong>Detection Logic:</strong></p>
 * <pre>
 * LOGICAL: RowBounds/IPage present + No LIMIT clause + No pagination plugin
 * PHYSICAL: LIMIT clause in SQL OR (RowBounds/IPage + Pagination plugin enabled)
 * NONE: No RowBounds/IPage + No LIMIT clause
 * </pre>
 *
 * @see PaginationPluginDetector
 */
public enum PaginationType {
  
  /**
   * Logical (in-memory) pagination.
   * 
   * <p>Occurs when RowBounds or IPage parameters are used without pagination plugin support.
   * MyBatis loads the entire result set into memory, then skips rows in-memory to implement
   * pagination. This is <strong>extremely dangerous</strong> for large result sets as it can
   * cause OutOfMemoryError.</p>
   *
   * <p><strong>Example Scenario:</strong></p>
   * <pre>{@code
   * // MyBatis Mapper method with RowBounds
   * List<User> selectUsers(RowBounds rowBounds);
   * 
   * // Without PageHelper plugin, this loads ALL users into memory
   * RowBounds bounds = new RowBounds(0, 10);
   * List<User> users = mapper.selectUsers(bounds); // LOGICAL pagination - DANGEROUS!
   * }</pre>
   */
  LOGICAL,
  
  /**
   * Physical (database-level) pagination.
   * 
   * <p>Occurs when SQL contains LIMIT clause or pagination plugin is enabled. The database
   * performs row filtering, returning only the requested page. This is <strong>safe and
   * efficient</strong> as it minimizes memory usage and network transfer.</p>
   *
   * <p><strong>Example Scenarios:</strong></p>
   * <pre>{@code
   * // Scenario 1: SQL with LIMIT clause
   * SELECT * FROM users LIMIT 10 OFFSET 0; // PHYSICAL pagination
   * 
   * // Scenario 2: RowBounds with PageHelper plugin
   * RowBounds bounds = new RowBounds(0, 10);
   * List<User> users = mapper.selectUsers(bounds); // PHYSICAL pagination (plugin intercepts)
   * 
   * // Scenario 3: MyBatis-Plus IPage with PaginationInnerInterceptor
   * IPage<User> page = new Page<>(1, 10);
   * IPage<User> result = mapper.selectPage(page, null); // PHYSICAL pagination
   * }</pre>
   */
  PHYSICAL,
  
  /**
   * No pagination detected.
   * 
   * <p>Query returns all matching rows without any pagination mechanism. This is acceptable
   * for queries with small result sets or when pagination is not required.</p>
   *
   * <p><strong>Example Scenario:</strong></p>
   * <pre>{@code
   * // Plain query without pagination
   * SELECT * FROM users WHERE id = ?; // NONE - returns single row
   * 
   * // Query with RowBounds.DEFAULT (infinite bounds)
   * List<User> users = mapper.selectUsers(RowBounds.DEFAULT); // NONE - not pagination
   * }</pre>
   */
  NONE
}







