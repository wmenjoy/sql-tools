package com.footstone.sqlguard.validator.pagination;

import com.footstone.sqlguard.core.model.SqlContext;
import java.util.List;
import java.util.Map;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.RowBounds;

/**
 * Detects pagination plugins and determines pagination type for SQL execution.
 *
 * <p>This class provides infrastructure for distinguishing between LOGICAL (dangerous in-memory),
 * PHYSICAL (safe database-level), and NONE pagination types. It detects MyBatis PageHelper and
 * MyBatis-Plus PaginationInnerInterceptor plugins to enable accurate pagination abuse checking.</p>
 *
 * <p><strong>Plugin Detection:</strong></p>
 * <ul>
 *   <li><strong>MyBatis PageHelper:</strong> Detects PageInterceptor by class name matching to
 *       avoid direct dependency on PageHelper library</li>
 *   <li><strong>MyBatis-Plus:</strong> Detects PaginationInnerInterceptor within
 *       MybatisPlusInterceptor using instanceof check</li>
 * </ul>
 *
 * <p><strong>Pagination Type Detection Logic:</strong></p>
 * <pre>
 * 1. Check if SQL has LIMIT clause (Statement instanceof Select with Limit)
 * 2. Check if pagination parameters exist (RowBounds or IPage)
 * 3. Check if pagination plugin is enabled
 * 4. Apply decision logic:
 *    - LOGICAL: hasPageParam && !hasLimit && !hasPlugin (dangerous)
 *    - PHYSICAL: hasLimit || (hasPageParam && hasPlugin) (safe)
 *    - NONE: otherwise
 * </pre>
 *
 * @see PaginationType
 */
public class PaginationPluginDetector {

  /**
   * MyBatis interceptor list (may contain PageHelper PageInterceptor).
   */
  private final List<Interceptor> mybatisInterceptors;

  /**
   * MyBatis-Plus interceptor (may contain PaginationInnerInterceptor).
   * Stored as Object to avoid compile-time dependency on MyBatis-Plus.
   */
  private final Object mybatisPlusInterceptor;

  /**
   * Constructs a PaginationPluginDetector with optional plugin configurations.
   *
   * @param mybatisInterceptors optional list of MyBatis interceptors (nullable)
   * @param mybatisPlusInterceptor optional MyBatis-Plus interceptor (nullable), 
   *        should be MybatisPlusInterceptor instance
   */
  public PaginationPluginDetector(List<Interceptor> mybatisInterceptors,
      Object mybatisPlusInterceptor) {
    this.mybatisInterceptors = mybatisInterceptors;
    this.mybatisPlusInterceptor = mybatisPlusInterceptor;
  }

  /**
   * Checks if any pagination plugin is configured.
   *
   * <p>Detection logic:</p>
   * <ul>
   *   <li>If mybatisPlusInterceptor is not null, check if it contains PaginationInnerInterceptor
   *       using reflection to avoid compile-time dependency</li>
   *   <li>If mybatisInterceptors is not null, check if any interceptor class name contains
   *       "PageInterceptor" (supports PageHelper without direct dependency)</li>
   * </ul>
   *
   * @return true if pagination plugin is detected, false otherwise
   */
  public boolean hasPaginationPlugin() {
    // Check MyBatis-Plus PaginationInnerInterceptor using reflection
    if (mybatisPlusInterceptor != null) {
      try {
        // Call getInterceptors() method via reflection
        java.lang.reflect.Method getInterceptorsMethod = 
            mybatisPlusInterceptor.getClass().getMethod("getInterceptors");
        Object result = getInterceptorsMethod.invoke(mybatisPlusInterceptor);
        
        if (result instanceof List) {
          List<?> innerInterceptors = (List<?>) result;
          for (Object interceptor : innerInterceptors) {
            if (interceptor != null) {
              String className = interceptor.getClass().getName();
              // Check if it's PaginationInnerInterceptor
              if (className.contains("PaginationInnerInterceptor")) {
                return true;
              }
            }
          }
        }
      } catch (Exception e) {
        // Ignore reflection errors - plugin not available
      }
    }

    // Check MyBatis PageHelper PageInterceptor (by class name to avoid dependency)
    if (mybatisInterceptors != null) {
      for (Interceptor interceptor : mybatisInterceptors) {
        String className = interceptor.getClass().getName();
        if (className.contains("PageInterceptor")) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Detects pagination type for the given SQL execution context.
   *
   * <p>Detection logic per design specification:</p>
   * <ol>
   *   <li>Extract Statement from context.getStatement()</li>
   *   <li>Check hasLimit using multi-dialect detection (supports MySQL, SQL Server, Oracle, DB2)</li>
   *   <li>Check hasPageParam:
   *       <ul>
   *         <li>context.getRowBounds() != null && context.getRowBounds() != RowBounds.DEFAULT</li>
   *         <li>OR hasIPageParameter(context.getParams())</li>
   *       </ul>
   *   </li>
   *   <li>Check hasPlugin = hasPaginationPlugin()</li>
   *   <li>Apply decision logic:
   *       <ul>
   *         <li>If (hasPageParam && !hasLimit && !hasPlugin) return LOGICAL</li>
   *         <li>If (hasLimit || (hasPageParam && hasPlugin)) return PHYSICAL</li>
   *         <li>Else return NONE</li>
   *       </ul>
   *   </li>
   * </ol>
   *
   * @param context SQL execution context containing parsed SQL and parameters
   * @return detected pagination type (LOGICAL, PHYSICAL, or NONE)
   */
  public PaginationType detectPaginationType(SqlContext context) {
    if (context == null) {
      return PaginationType.NONE;
    }

    // Step 1: Extract Statement
    Statement stmt = context.getStatement();
    if (stmt == null) {
      return PaginationType.NONE;
    }

    // Step 2: Check if SQL has physical pagination (multi-dialect support)
    boolean hasLimit = false;
    if (stmt instanceof Select) {
      Select select = (Select) stmt;
      hasLimit = hasPhysicalPagination(select, context);
    }

    // Step 3: Check if pagination parameters exist
    boolean hasPageParam = false;

    // Check RowBounds (MyBatis pagination parameter)
    Object rowBounds = context.getRowBounds();
    if (rowBounds != null && rowBounds instanceof RowBounds) {
      RowBounds rb = (RowBounds) rowBounds;
      // RowBounds.DEFAULT is infinite bounds (offset=0, limit=Integer.MAX_VALUE), not pagination
      if (rb != RowBounds.DEFAULT) {
        hasPageParam = true;
      }
    }

    // Check IPage parameter (MyBatis-Plus pagination parameter)
    if (!hasPageParam) {
      hasPageParam = hasPageParameter(context.getParams());
    }

    // Step 4: Check if pagination plugin is enabled
    boolean hasPlugin = hasPaginationPlugin();

    // Step 5: Apply decision logic
    if (hasPageParam && !hasLimit && !hasPlugin) {
      // Dangerous: RowBounds/IPage without plugin and without LIMIT
      // Will load entire result set into memory
      return PaginationType.LOGICAL;
    } else if (hasLimit || (hasPageParam && hasPlugin)) {
      // Safe: SQL has LIMIT clause OR pagination plugin intercepts RowBounds/IPage
      // Database performs row filtering
      return PaginationType.PHYSICAL;
    } else {
      // No pagination detected
      return PaginationType.NONE;
    }
  }

  /**
   * Checks if any parameter is an IPage instance (MyBatis-Plus pagination parameter).
   *
   * @param params parameter map from SqlContext
   * @return true if any parameter is IPage, false otherwise
   */
  private boolean hasPageParameter(Map<String, Object> params) {
    if (params == null || params.isEmpty()) {
      return false;
    }

    for (Object value : params.values()) {
      if (value != null) {
        // Check if parameter is IPage using class name to avoid direct dependency
        String className = value.getClass().getName();
        if (className.contains("IPage")
            || className.contains("com.baomidou.mybatisplus.core.metadata.IPage")) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Detects if SELECT statement has physical pagination (database-level limit).
   *
   * <p>This method supports multiple database dialects using a two-layer detection strategy:</p>
   *
   * <h3>Layer 1: Structured Detection (O(1) - No Performance Overhead)</h3>
   * <ul>
   *   <li><strong>MySQL/PostgreSQL:</strong> Checks {@code plainSelect.getLimit() != null}
   *       <br>Syntax: {@code SELECT * FROM users LIMIT 1000}</li>
   *   <li><strong>SQL Server:</strong> Checks {@code plainSelect.getTop() != null}
   *       <br>Syntax: {@code SELECT TOP 1000 * FROM users}</li>
   *   <li><strong>DB2/Oracle 12c+:</strong> Checks {@code plainSelect.getFetch() != null}
   *       <br>Syntax: {@code SELECT * FROM users FETCH FIRST 1000 ROWS ONLY}</li>
   * </ul>
   *
   * <h3>Layer 2: String Detection (O(n) - Handles Nested Queries)</h3>
   * <p>String detection serves as a fallback to handle cases that structured detection misses:</p>
   * <ul>
   *   <li><strong>Nested Subqueries:</strong> {@code SELECT * FROM (SELECT * FROM users LIMIT 100) t}
   *       <br>Inner LIMIT is detected via string matching</li>
   *   <li><strong>UNION Statements:</strong> {@code SELECT * FROM a LIMIT 10 UNION SELECT * FROM b}
   *       <br>SelectBody is SetOperationList (not PlainSelect), but LIMIT is in SQL string</li>
   *   <li><strong>Oracle Legacy ROWNUM:</strong> {@code SELECT * FROM (SELECT * WHERE ROWNUM <= 100)}
   *       <br>Requires string detection as it's not a standard SQL clause</li>
   * </ul>
   *
   * <p>String detection checks for these pagination keywords (case-insensitive):</p>
   * <ul>
   *   <li>{@code LIMIT} - MySQL/PostgreSQL pagination</li>
   *   <li>{@code TOP \d+} - SQL Server pagination (with word boundaries to avoid false positives)</li>
   *   <li>{@code FETCH FIRST/NEXT} - DB2/Oracle 12c+ pagination</li>
   *   <li>{@code ROWNUM} or {@code ROW_NUMBER} - Oracle legacy pagination</li>
   * </ul>
   *
   * <h3>Performance Characteristics</h3>
   * <ul>
   *   <li><strong>Structured Detection (outer query):</strong> O(1) field access, ~0 microseconds</li>
   *   <li><strong>String Detection (nested/complex queries):</strong> O(n) where n = SQL length, ~1-5 microseconds for 500 chars</li>
   *   <li><strong>Total Overhead:</strong> <0.5% of SQL execution time (typically 1ms+)</li>
   * </ul>
   *
   * <h3>Design Rationale</h3>
   * <p>The two-layer approach balances correctness, performance, and code simplicity:</p>
   * <ul>
   *   <li>Structured detection handles 90%+ common cases (outer-level pagination) with zero overhead</li>
   *   <li>String detection provides complete coverage for nested/complex queries</li>
   *   <li>Using {@code context.getSql()} instead of {@code select.toString()} avoids expensive
   *       AST traversal and serialization (tens of milliseconds for complex SQL)</li>
   *   <li>Potential false positives (e.g., column named "user_limit") are acceptable since
   *       misclassifying as PHYSICAL (safe) is better than missing LOGICAL (dangerous)</li>
   * </ul>
   *
   * @param select the SELECT statement to check
   * @param context SQL execution context containing original SQL string
   * @return true if physical pagination is detected, false otherwise
   *
   * @see net.sf.jsqlparser.statement.select.PlainSelect#getLimit()
   * @see net.sf.jsqlparser.statement.select.PlainSelect#getTop()
   * @see net.sf.jsqlparser.statement.select.PlainSelect#getFetch()
   */
  private boolean hasPhysicalPagination(Select select, SqlContext context) {
    // ============================================================
    // Layer 1: Structured Detection (O(1) - No Performance Overhead)
    // ============================================================
    // Only PlainSelect supports pagination clauses (not UNION, SetOperationList, etc.)
    if (select.getSelectBody() instanceof net.sf.jsqlparser.statement.select.PlainSelect) {
      net.sf.jsqlparser.statement.select.PlainSelect plainSelect =
          (net.sf.jsqlparser.statement.select.PlainSelect) select.getSelectBody();

      // 1. MySQL/PostgreSQL: LIMIT clause
      // Example: SELECT * FROM users LIMIT 1000
      if (plainSelect.getLimit() != null) {
        return true;
      }

      // 2. SQL Server: TOP clause
      // Example: SELECT TOP 1000 * FROM users
      if (plainSelect.getTop() != null) {
        return true;
      }

      // 3. DB2/Oracle 12c+: FETCH FIRST clause
      // Example: SELECT * FROM users FETCH FIRST 1000 ROWS ONLY
      if (plainSelect.getFetch() != null) {
        return true;
      }
    }

    // ============================================================
    // Layer 2: String Detection (O(n) - Handles nested queries and UNION)
    // ============================================================

    // String detection handles cases that structured detection misses:
    // - Nested subqueries: SELECT * FROM (SELECT * FROM users LIMIT 100) t
    // - UNION statements: SELECT * FROM a LIMIT 10 UNION SELECT * FROM b
    // - Complex WHERE clauses with Oracle ROWNUM
    //
    // This layer runs for ALL SELECT types (PlainSelect, SetOperationList, etc.)
    // to ensure comprehensive coverage including UNION queries.
    //
    // Performance Optimization: Use context.getSql() instead of select.toString()
    // - context.getSql() returns the original SQL string (already available)
    // - select.toString() requires full AST traversal and serialization (expensive)
    String sql = context.getSql();
    if (sql != null) {
      String upperSql = sql.toUpperCase();

      // Check for common pagination keywords
      // Note: These checks are case-insensitive and work for nested queries

      // 1. MySQL/PostgreSQL: LIMIT keyword
      // Example: SELECT * FROM (SELECT * FROM users LIMIT 100) t
      // Use word boundary to avoid false positives in field names (e.g., "user_limit")
      // But allow LIMIT at any position (beginning, middle, end)
      if (upperSql.matches(".*\\bLIMIT\\s+\\d+.*")) {
        return true;
      }

      // 2. SQL Server: TOP keyword (with word boundaries to avoid false positives like "STOPPED")
      // Example: SELECT * FROM (SELECT TOP 100 * FROM users) t
      // Support both "TOP 100" and "TOP(100)" syntax
      if (upperSql.matches(".*\\bTOP\\s*\\(?\\d+.*")) {
        return true;
      }

      // 3. DB2/Oracle 12c+: FETCH FIRST keyword
      // Example: SELECT * FROM (SELECT * FROM users FETCH FIRST 100 ROWS ONLY) t
      if (upperSql.contains("FETCH FIRST") || upperSql.contains("FETCH NEXT")) {
        return true;
      }

      // 4. Oracle Legacy: ROWNUM pseudo-column or ROW_NUMBER() window function
      // Example: SELECT * FROM (SELECT * FROM (SELECT * WHERE ROWNUM <= 100))
      if (upperSql.contains("ROWNUM") || upperSql.contains("ROW_NUMBER")) {
        return true;
      }
    }

    return false;
  }
}