package com.footstone.sqlguard.validator.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for PaginationPluginDetector.
 * Tests plugin detection for MyBatis PageHelper and MyBatis-Plus PaginationInnerInterceptor.
 */
public class PaginationPluginDetectorTest {

  /**
   * Test that detector returns false when no pagination plugins are configured.
   */
  @Test
  public void testNoPaginationPlugin_shouldReturnFalse() {
    // No plugins configured
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    
    assertFalse(detector.hasPaginationPlugin(), 
        "Should return false when no pagination plugins are configured");
  }

  /**
   * Test that detector returns true when MyBatis PageHelper is configured.
   */
  @Test
  public void testPageHelper_shouldReturnTrue() {
    // Create a custom interceptor class that simulates PageInterceptor
    Interceptor mockPageInterceptor = new Interceptor() {
      @Override
      public Object intercept(org.apache.ibatis.plugin.Invocation invocation) {
        return null;
      }
      
      @Override
      public Object plugin(Object target) {
        return null;
      }
      
      @Override
      public void setProperties(java.util.Properties properties) {
      }
      
      @Override
      public String toString() {
        return "PageInterceptor";
      }
    };
    
    // Override getClass().getName() by creating anonymous class
    Interceptor pageInterceptor = new Interceptor() {
      @Override
      public Object intercept(org.apache.ibatis.plugin.Invocation invocation) {
        return null;
      }
      
      @Override
      public Object plugin(Object target) {
        return null;
      }
      
      @Override
      public void setProperties(java.util.Properties properties) {
      }
      
      // This class name will contain "PageInterceptor"
    };
    
    // Create class with PageInterceptor in package name
    Interceptor actualInterceptor = createPageInterceptor();
    
    List<Interceptor> interceptors = Arrays.asList(actualInterceptor);
    PaginationPluginDetector detector = new PaginationPluginDetector(interceptors, null);
    
    assertTrue(detector.hasPaginationPlugin(), 
        "Should return true when PageHelper PageInterceptor is configured");
  }
  
  /**
   * Helper method to create an interceptor with "PageInterceptor" in class name.
   */
  private Interceptor createPageInterceptor() {
    // Create anonymous class whose name will be checked
    return new com.github.pagehelper.PageInterceptor();
  }
  
  /**
   * Mock PageInterceptor class for testing.
   */
  static class com {
    static class github {
      static class pagehelper {
        static class PageInterceptor implements Interceptor {
          @Override
          public Object intercept(org.apache.ibatis.plugin.Invocation invocation) {
            return null;
          }
          
          @Override
          public Object plugin(Object target) {
            return null;
          }
          
          @Override
          public void setProperties(java.util.Properties properties) {
          }
        }
      }
    }
  }

  /**
   * Test that detector returns true when MyBatis-Plus PaginationInnerInterceptor is configured.
   * Uses a mock object that simulates MybatisPlusInterceptor with PaginationInnerInterceptor.
   */
  @Test
  public void testMpPaginationInnerInterceptor_shouldReturnTrue() throws Exception {
    // Create a mock object that simulates MybatisPlusInterceptor
    Object mybatisPlusInterceptor = createMockMybatisPlusInterceptor(true);
    
    PaginationPluginDetector detector = 
        new PaginationPluginDetector(null, mybatisPlusInterceptor);
    
    assertTrue(detector.hasPaginationPlugin(), 
        "Should return true when MyBatis-Plus PaginationInnerInterceptor is configured");
  }

  /**
   * Test that detector returns false when MybatisPlusInterceptor exists but has no 
   * PaginationInnerInterceptor.
   */
  @Test
  public void testMpInterceptorWithoutPagination_shouldReturnFalse() throws Exception {
    // Create a mock object that simulates MybatisPlusInterceptor without PaginationInnerInterceptor
    Object mybatisPlusInterceptor = createMockMybatisPlusInterceptor(false);
    
    PaginationPluginDetector detector = 
        new PaginationPluginDetector(null, mybatisPlusInterceptor);
    
    assertFalse(detector.hasPaginationPlugin(), 
        "Should return false when MybatisPlusInterceptor has no PaginationInnerInterceptor");
  }

  /**
   * Test that detector returns false when MyBatis interceptor list contains non-pagination 
   * interceptors.
   */
  @Test
  public void testNonPaginationInterceptors_shouldReturnFalse() {
    // Create a custom interceptor that doesn't have PageInterceptor in name
    Interceptor customInterceptor = new Interceptor() {
      @Override
      public Object intercept(org.apache.ibatis.plugin.Invocation invocation) {
        return null;
      }
      
      @Override
      public Object plugin(Object target) {
        return null;
      }
      
      @Override
      public void setProperties(java.util.Properties properties) {
      }
    };
    
    List<Interceptor> interceptors = Arrays.asList(customInterceptor);
    PaginationPluginDetector detector = new PaginationPluginDetector(interceptors, null);
    
    assertFalse(detector.hasPaginationPlugin(), 
        "Should return false when interceptor list contains no PageInterceptor");
  }

  /**
   * Test that detector returns true when both MyBatis and MyBatis-Plus pagination plugins 
   * are configured.
   */
  @Test
  public void testBothPlugins_shouldReturnTrue() throws Exception {
    // MyBatis PageHelper
    Interceptor pageInterceptor = createPageInterceptor();
    List<Interceptor> interceptors = Arrays.asList(pageInterceptor);
    
    // MyBatis-Plus PaginationInnerInterceptor
    Object mybatisPlusInterceptor = createMockMybatisPlusInterceptor(true);
    
    PaginationPluginDetector detector = 
        new PaginationPluginDetector(interceptors, mybatisPlusInterceptor);
    
    assertTrue(detector.hasPaginationPlugin(), 
        "Should return true when both pagination plugins are configured");
  }

  /**
   * Test that detector handles empty interceptor list correctly.
   */
  @Test
  public void testEmptyInterceptorList_shouldReturnFalse() {
    List<Interceptor> emptyList = new ArrayList<>();
    PaginationPluginDetector detector = new PaginationPluginDetector(emptyList, null);
    
    assertFalse(detector.hasPaginationPlugin(), 
        "Should return false when interceptor list is empty");
  }

  /**
   * Helper method to create a mock object that simulates MybatisPlusInterceptor.
   * Uses dynamic proxy to simulate getInterceptors() method.
   *
   * @param hasPaginationInterceptor whether to include PaginationInnerInterceptor
   * @return mock object simulating MybatisPlusInterceptor
   */
  private Object createMockMybatisPlusInterceptor(boolean hasPaginationInterceptor) throws Exception {
    // Create a list of inner interceptors
    final List<Object> innerInterceptors = new ArrayList<>();
    
    if (hasPaginationInterceptor) {
      // Add an object with PaginationInnerInterceptor class name
      innerInterceptors.add(new MockPaginationInnerInterceptor());
    } else {
      // Add an object with different class name
      innerInterceptors.add(new MockOtherInterceptor());
    }
    
    // Create an object with getInterceptors() method
    return new Object() {
      @SuppressWarnings("unused")
      public List<Object> getInterceptors() {
        return innerInterceptors;
      }
    };
  }
  
  /**
   * Mock class simulating PaginationInnerInterceptor.
   */
  static class MockPaginationInnerInterceptor {
    // Class name contains "PaginationInnerInterceptor"
  }
  
  /**
   * Mock class simulating other interceptor.
   */
  static class MockOtherInterceptor {
    // Class name doesn't contain "PaginationInnerInterceptor"
  }

  // ============================================================
  // Multi-Dialect Physical Pagination Detection Tests
  // ============================================================

  private JSqlParserFacade parser;
  private PaginationPluginDetector detectorWithoutPlugin;

  @BeforeEach
  public void setUp() {
    parser = new JSqlParserFacade();
    // Create detector without any pagination plugins
    detectorWithoutPlugin = new PaginationPluginDetector(null, null);
  }

  /**
   * Test MySQL LIMIT clause detection - should return PHYSICAL.
   */
  @Test
  public void testMySqlLimit_shouldDetectPhysical() {
    String sql = "SELECT * FROM users WHERE age > 18 LIMIT 100";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "MySQL LIMIT should be detected as PHYSICAL pagination");
  }

  /**
   * Test MySQL LIMIT with OFFSET - should return PHYSICAL.
   */
  @Test
  public void testMySqlLimitWithOffset_shouldDetectPhysical() {
    String sql = "SELECT * FROM users ORDER BY id LIMIT 10 OFFSET 100";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "MySQL LIMIT with OFFSET should be detected as PHYSICAL pagination");
  }

  /**
   * Test SQL Server TOP clause detection - should return PHYSICAL.
   */
  @Test
  public void testSqlServerTop_shouldDetectPhysical() {
    String sql = "SELECT TOP 100 * FROM users WHERE age > 18";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "SQL Server TOP should be detected as PHYSICAL pagination");
  }

  /**
   * Test Oracle ROWNUM detection - should return PHYSICAL.
   */
  @Test
  public void testOracleRownum_shouldDetectPhysical() {
    String sql = "SELECT * FROM (SELECT * FROM users WHERE age > 18) WHERE ROWNUM <= 100";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "Oracle ROWNUM should be detected as PHYSICAL pagination");
  }

  /**
   * Test Oracle ROW_NUMBER() function detection - should return PHYSICAL.
   */
  @Test
  public void testOracleRowNumber_shouldDetectPhysical() {
    String sql = "SELECT * FROM (SELECT t.*, ROW_NUMBER() OVER (ORDER BY id) rn FROM users t) WHERE rn <= 100";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "Oracle ROW_NUMBER() should be detected as PHYSICAL pagination");
  }

  /**
   * Test DB2/Oracle 12c+ FETCH FIRST clause detection - should return PHYSICAL.
   */
  @Test
  public void testDb2FetchFirst_shouldDetectPhysical() {
    String sql = "SELECT * FROM users WHERE age > 18 FETCH FIRST 100 ROWS ONLY";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "DB2/Oracle 12c+ FETCH FIRST should be detected as PHYSICAL pagination");
  }

  /**
   * Test SQL Server TOP with RowBounds parameter but no plugin.
   * Previously would be INCORRECTLY detected as LOGICAL.
   * Now should be correctly detected as PHYSICAL (SQL has TOP, so pagination is at DB level).
   */
  @Test
  public void testSqlServerTopWithRowBounds_shouldDetectPhysical() {
    String sql = "SELECT TOP 100 * FROM users WHERE age > 18";
    Statement stmt = parser.parse(sql);

    // Add RowBounds parameter (pagination parameter)
    RowBounds rowBounds = new RowBounds(0, 20);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .rowBounds(rowBounds)
        .build();

    // Detector without plugin - previously would return LOGICAL (WRONG!)
    // Now should return PHYSICAL (CORRECT - SQL has TOP clause)
    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "SQL Server TOP with RowBounds should be PHYSICAL (SQL has physical pagination)");
  }

  /**
   * Test Oracle ROWNUM with RowBounds parameter but no plugin.
   * Previously would be INCORRECTLY detected as LOGICAL.
   * Now should be correctly detected as PHYSICAL.
   */
  @Test
  public void testOracleRownumWithRowBounds_shouldDetectPhysical() {
    String sql = "SELECT * FROM (SELECT * FROM users) WHERE ROWNUM <= 100";
    Statement stmt = parser.parse(sql);

    RowBounds rowBounds = new RowBounds(0, 20);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .rowBounds(rowBounds)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "Oracle ROWNUM with RowBounds should be PHYSICAL (SQL has physical pagination)");
  }

  /**
   * Test SELECT without any pagination - should return NONE.
   */
  @Test
  public void testSelectWithoutPagination_shouldDetectNone() {
    String sql = "SELECT * FROM users WHERE age > 18";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.NONE, type,
        "SELECT without pagination should be NONE");
  }

  /**
   * Test SELECT with RowBounds but no plugin and no LIMIT clause - should return LOGICAL.
   * This is the dangerous case: pagination parameter exists but no plugin to intercept,
   * and SQL has no physical pagination, so MyBatis loads all rows into memory.
   */
  @Test
  public void testSelectWithRowBoundsNoPluginNoLimit_shouldDetectLogical() {
    String sql = "SELECT * FROM users WHERE age > 18";
    Statement stmt = parser.parse(sql);

    RowBounds rowBounds = new RowBounds(0, 20);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .rowBounds(rowBounds)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.LOGICAL, type,
        "SELECT with RowBounds but no plugin and no LIMIT should be LOGICAL (dangerous)");
  }

  /**
   * Test PostgreSQL LIMIT - should return PHYSICAL.
   */
  @Test
  public void testPostgreSqlLimit_shouldDetectPhysical() {
    String sql = "SELECT * FROM users WHERE age > 18 LIMIT 100 OFFSET 50";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "PostgreSQL LIMIT should be detected as PHYSICAL pagination");
  }

  /**
   * Test case-insensitive ROWNUM detection.
   */
  @Test
  public void testOracleRownumLowerCase_shouldDetectPhysical() {
    String sql = "SELECT * FROM (SELECT * FROM users) WHERE rownum <= 100";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "Oracle ROWNUM (lowercase) should be detected as PHYSICAL pagination");
  }
}
