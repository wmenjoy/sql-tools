package com.footstone.sqlguard.validator.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import java.util.HashMap;
import java.util.Map;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Test class for PaginationType detection logic.
 * Tests comprehensive pagination scenarios including RowBounds, IPage, LIMIT, and plugins.
 */
public class PaginationTypeDetectionTest {

  private JSqlParserFacade parser;

  @BeforeEach
  public void setUp() {
    parser = new JSqlParserFacade(false);
  }

  /**
   * Test: RowBounds without plugin should be LOGICAL (dangerous).
   */
  @Test
  public void testRowBoundsWithoutPlugin_shouldBeLogical() {
    String sql = "SELECT * FROM user WHERE id > 100";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectUsers")
        .rowBounds(new RowBounds(0, 10))  // RowBounds with offset=0, limit=10
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType type = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.LOGICAL, type,
        "RowBounds without plugin should be LOGICAL (loads entire result set into memory)");
  }

  /**
   * Test: RowBounds with PageHelper plugin should be PHYSICAL (safe).
   */
  @Test
  public void testRowBoundsWithPageHelper_shouldBePhysical() {
    String sql = "SELECT * FROM user WHERE id > 100";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectUsers")
        .rowBounds(new RowBounds(0, 10))
        .build();
    
    // Create PageHelper interceptor
    Interceptor pageInterceptor = createPageInterceptor();
    PaginationPluginDetector detector = 
        new PaginationPluginDetector(Arrays.asList(pageInterceptor), null);
    
    PaginationType type = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.PHYSICAL, type,
        "RowBounds with PageHelper should be PHYSICAL (plugin intercepts and adds LIMIT)");
  }

  /**
   * Test: IPage parameter with MyBatis-Plus plugin should be PHYSICAL (safe).
   */
  @Test
  public void testIPageWithMpPlugin_shouldBePhysical() {
    String sql = "SELECT * FROM user WHERE id > 100";
    Statement stmt = parser.parse(sql);
    
    // Create IPage-like parameter
    Map<String, Object> params = new HashMap<>();
    params.put("page", new MockIPage());
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectPage")
        .params(params)
        .build();
    
    // Create MyBatis-Plus interceptor with PaginationInnerInterceptor
    Object mpInterceptor = createMockMybatisPlusInterceptor(true);
    PaginationPluginDetector detector = new PaginationPluginDetector(null, mpInterceptor);
    
    PaginationType type = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.PHYSICAL, type,
        "IPage with PaginationInnerInterceptor should be PHYSICAL");
  }

  /**
   * Test: SQL with LIMIT clause should be PHYSICAL (safe).
   */
  @Test
  public void testLimitInSql_shouldBePhysical() {
    String sql = "SELECT * FROM user WHERE id > 100 LIMIT 10";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectUsers")
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType type = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.PHYSICAL, type,
        "SQL with LIMIT clause should be PHYSICAL (database-level pagination)");
  }

  /**
   * Test: Plain query without pagination should be NONE.
   */
  @Test
  public void testPlainQuery_shouldBeNone() {
    String sql = "SELECT * FROM user WHERE id = ?";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectById")
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType type = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.NONE, type,
        "Plain query without pagination should be NONE");
  }

  /**
   * Test: RowBounds.DEFAULT (infinite bounds) should be NONE (not pagination).
   */
  @Test
  public void testRowBoundsDefault_shouldBeNone() {
    String sql = "SELECT * FROM user WHERE id > 100";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectUsers")
        .rowBounds(RowBounds.DEFAULT)  // DEFAULT = infinite bounds, not pagination
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType type = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.NONE, type,
        "RowBounds.DEFAULT should be NONE (infinite bounds, not pagination)");
  }

  /**
   * Test: SQL with LIMIT and OFFSET should be PHYSICAL.
   */
  @Test
  public void testLimitWithOffset_shouldBePhysical() {
    String sql = "SELECT * FROM user WHERE id > 100 LIMIT 10 OFFSET 5";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectUsers")
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType type = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.PHYSICAL, type,
        "SQL with LIMIT and OFFSET should be PHYSICAL");
  }

  /**
   * Test: IPage without plugin should be LOGICAL (dangerous).
   */
  @Test
  public void testIPageWithoutPlugin_shouldBeLogical() {
    String sql = "SELECT * FROM user WHERE id > 100";
    Statement stmt = parser.parse(sql);
    
    // Create IPage-like parameter
    Map<String, Object> params = new HashMap<>();
    params.put("page", new MockIPage());
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId("com.example.UserMapper.selectPage")
        .params(params)
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType type = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.LOGICAL, type,
        "IPage without plugin should be LOGICAL (dangerous in-memory pagination)");
  }

  /**
   * Helper method to create PageInterceptor for testing.
   */
  private Interceptor createPageInterceptor() {
    return new com.github.pagehelper.PageInterceptor();
  }

  /**
   * Helper method to create mock MybatisPlusInterceptor.
   */
  private Object createMockMybatisPlusInterceptor(boolean hasPaginationInterceptor) {
    if (hasPaginationInterceptor) {
      return new Object() {
        @SuppressWarnings("unused")
        public java.util.List<Object> getInterceptors() {
          return Arrays.asList(new MockPaginationInnerInterceptor());
        }
      };
    } else {
      return new Object() {
        @SuppressWarnings("unused")
        public java.util.List<Object> getInterceptors() {
          return Arrays.asList(new MockOtherInterceptor());
        }
      };
    }
  }

  /**
   * Mock PageInterceptor class.
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
   * Mock IPage class for testing.
   */
  static class MockIPage {
    // Class name contains "IPage" for detection
  }

  /**
   * Mock PaginationInnerInterceptor class.
   */
  static class MockPaginationInnerInterceptor {
    // Class name contains "PaginationInnerInterceptor"
  }

  /**
   * Mock other interceptor class.
   */
  static class MockOtherInterceptor {
    // Class name doesn't contain "PaginationInnerInterceptor"
  }
}




















