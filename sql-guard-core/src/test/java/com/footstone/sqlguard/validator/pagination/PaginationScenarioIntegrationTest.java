package com.footstone.sqlguard.validator.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for all pagination detection scenarios.
 * Tests all 7 scenarios specified in Task 2.6 Step 4.
 */
public class PaginationScenarioIntegrationTest {

  private JSqlParserFacade parser;

  @BeforeEach
  public void setUp() {
    parser = new JSqlParserFacade(false);
  }

  /**
   * Scenario 1: RowBounds(offset=0, limit=10) without plugin → expect LOGICAL.
   */
  @Test
  public void testScenario1_RowBoundsWithoutPlugin() {
    String sql = "SELECT * FROM user";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectAll")
        .rowBounds(new RowBounds(0, 10))
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType result = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.LOGICAL, result,
        "Scenario 1: RowBounds without plugin should be LOGICAL");
  }

  /**
   * Scenario 2: RowBounds with PageHelper interceptor → expect PHYSICAL.
   */
  @Test
  public void testScenario2_RowBoundsWithPageHelper() {
    String sql = "SELECT * FROM user";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectAll")
        .rowBounds(new RowBounds(0, 10))
        .build();
    
    Interceptor pageHelper = createPageInterceptor();
    PaginationPluginDetector detector = 
        new PaginationPluginDetector(Arrays.asList(pageHelper), null);
    PaginationType result = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.PHYSICAL, result,
        "Scenario 2: RowBounds with PageHelper should be PHYSICAL");
  }

  /**
   * Scenario 3: IPage parameter with MybatisPlusInterceptor + PaginationInnerInterceptor 
   * → expect PHYSICAL.
   */
  @Test
  public void testScenario3_IPageWithMpPlugin() {
    String sql = "SELECT * FROM user";
    Statement stmt = parser.parse(sql);
    
    Map<String, Object> params = new HashMap<>();
    params.put("page", new MockIPage());
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectPage")
        .params(params)
        .build();
    
    Object mpInterceptor = createMockMybatisPlusInterceptor();
    PaginationPluginDetector detector = new PaginationPluginDetector(null, mpInterceptor);
    PaginationType result = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.PHYSICAL, result,
        "Scenario 3: IPage with MybatisPlusInterceptor should be PHYSICAL");
  }

  /**
   * Scenario 4: SQL "SELECT * FROM user LIMIT 10" → expect PHYSICAL.
   */
  @Test
  public void testScenario4_SqlWithLimit() {
    String sql = "SELECT * FROM user LIMIT 10";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectUsers")
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType result = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.PHYSICAL, result,
        "Scenario 4: SQL with LIMIT should be PHYSICAL");
  }

  /**
   * Scenario 5: SQL "SELECT * FROM user LIMIT 10 OFFSET 5" → expect PHYSICAL.
   */
  @Test
  public void testScenario5_SqlWithLimitOffset() {
    String sql = "SELECT * FROM user LIMIT 10 OFFSET 5";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectUsers")
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType result = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.PHYSICAL, result,
        "Scenario 5: SQL with LIMIT and OFFSET should be PHYSICAL");
  }

  /**
   * Scenario 6: Plain SQL "SELECT * FROM user WHERE id=?" with no pagination params 
   * → expect NONE.
   */
  @Test
  public void testScenario6_PlainSqlNoPagination() {
    String sql = "SELECT * FROM user WHERE id=?";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectById")
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType result = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.NONE, result,
        "Scenario 6: Plain SQL with no pagination should be NONE");
  }

  /**
   * Scenario 7: RowBounds.DEFAULT (infinite bounds) → expect NONE (not pagination).
   */
  @Test
  public void testScenario7_RowBoundsDefault() {
    String sql = "SELECT * FROM user";
    Statement stmt = parser.parse(sql);
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectAll")
        .rowBounds(RowBounds.DEFAULT)
        .build();
    
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    PaginationType result = detector.detectPaginationType(context);
    
    assertEquals(PaginationType.NONE, result,
        "Scenario 7: RowBounds.DEFAULT should be NONE (not pagination)");
  }

  /**
   * Additional test: Verify 100% detection accuracy across all scenarios.
   */
  @Test
  public void testAllScenariosDetectionAccuracy() {
    // Run all 7 scenarios and verify correct detection
    testScenario1_RowBoundsWithoutPlugin();
    testScenario2_RowBoundsWithPageHelper();
    testScenario3_IPageWithMpPlugin();
    testScenario4_SqlWithLimit();
    testScenario5_SqlWithLimitOffset();
    testScenario6_PlainSqlNoPagination();
    testScenario7_RowBoundsDefault();
    
    // If we reach here, all scenarios passed - 100% accuracy
  }

  /**
   * Helper method to create PageInterceptor.
   */
  private Interceptor createPageInterceptor() {
    return new com.github.pagehelper.PageInterceptor();
  }

  /**
   * Helper method to create mock MybatisPlusInterceptor with PaginationInnerInterceptor.
   */
  private Object createMockMybatisPlusInterceptor() {
    return new Object() {
      @SuppressWarnings("unused")
      public java.util.List<Object> getInterceptors() {
        return Arrays.asList(new MockPaginationInnerInterceptor());
      }
    };
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
   * Mock IPage class.
   */
  static class MockIPage {
    // Class name contains "IPage"
  }

  /**
   * Mock PaginationInnerInterceptor class.
   */
  static class MockPaginationInnerInterceptor {
    // Class name contains "PaginationInnerInterceptor"
  }
}
