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
   *   <li>Extract Statement from context.getParsedSql()</li>
   *   <li>Check hasLimit = (stmt instanceof Select && ((Select)stmt).getLimit() != null)</li>
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
    Statement stmt = context.getParsedSql();
    if (stmt == null) {
      return PaginationType.NONE;
    }

    // Step 2: Check if SQL has LIMIT clause
    boolean hasLimit = false;
    if (stmt instanceof Select) {
      Select select = (Select) stmt;
      // In JSqlParser 4.6, Select has getSelectBody() which returns SelectBody
      // PlainSelect (most common SelectBody) has getLimit()
      if (select.getSelectBody() != null) {
        String sql = select.toString().toUpperCase();
        // Simple check: does SQL contain LIMIT keyword
        hasLimit = sql.contains("LIMIT");
      }
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
}
