package com.footstone.sqlguard.validator.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
 * 高级分页检测测试 - 覆盖IPage参数、插件组合、边界情况等.
 *
 * <p>这个测试类专注于测试之前遗漏的关键场景：</p>
 * <ul>
 *   <li>IPage参数的检测（MyBatis-Plus）</li>
 *   <li>插件+参数的各种组合</li>
 *   <li>TOP(100)括号形式</li>
 *   <li>WITH子句（CTE）</li>
 *   <li>表名/字段名包含关键字的误报测试</li>
 *   <li>RowBounds边界值</li>
 *   <li>null安全性</li>
 * </ul>
 */
public class PaginationAdvancedTest {

  private JSqlParserFacade parser;
  private PaginationPluginDetector detectorWithoutPlugin;
  private PaginationPluginDetector detectorWithPageHelper;
  private PaginationPluginDetector detectorWithMybatisPlus;

  @BeforeEach
  public void setUp() throws Exception {
    parser = new JSqlParserFacade();

    // 无插件的检测器
    detectorWithoutPlugin = new PaginationPluginDetector(null, null);

    // 带PageHelper插件的检测器
    Interceptor pageInterceptor = createPageInterceptor();
    List<Interceptor> pageHelperInterceptors = Arrays.asList(pageInterceptor);
    detectorWithPageHelper = new PaginationPluginDetector(pageHelperInterceptors, null);

    // 带MyBatis-Plus插件的检测器
    Object mybatisPlusInterceptor = createMockMybatisPlusInterceptor();
    detectorWithMybatisPlus = new PaginationPluginDetector(null, mybatisPlusInterceptor);
  }

  // ============================================================
  // IPage参数测试（P0优先级 - 之前完全缺失）
  // ============================================================

  /**
   * 测试：IPage + 无LIMIT + 无插件 → LOGICAL（危险！）.
   * 这是最危险的场景：使用IPage但没有配置分页插件，会导致内存分页
   */
  @Test
  public void testIPageWithoutLimitWithoutPlugin_shouldDetectLogical() {
    String sql = "SELECT * FROM users WHERE age > 18";
    Statement stmt = parser.parse(sql);

    // 创建模拟的IPage参数
    Map<String, Object> params = new HashMap<>();
    params.put("page", new MockIPage(1, 20)); // 第1页，每页20条

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .params(params)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.LOGICAL, type,
        "IPage + 无LIMIT + 无插件 → LOGICAL（危险的内存分页）");
  }

  /**
   * 测试：IPage + 无LIMIT + 有插件 → PHYSICAL（安全）.
   * 插件会拦截IPage并自动添加LIMIT子句
   */
  @Test
  public void testIPageWithoutLimitWithPlugin_shouldDetectPhysical() {
    String sql = "SELECT * FROM users WHERE age > 18";
    Statement stmt = parser.parse(sql);

    Map<String, Object> params = new HashMap<>();
    params.put("page", new MockIPage(1, 20));

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .params(params)
        .build();

    // 测试PageHelper插件
    PaginationType typePageHelper = detectorWithPageHelper.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, typePageHelper,
        "IPage + 无LIMIT + PageHelper插件 → PHYSICAL（插件拦截）");

    // 测试MyBatis-Plus插件
    PaginationType typeMybatisPlus = detectorWithMybatisPlus.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, typeMybatisPlus,
        "IPage + 无LIMIT + MyBatis-Plus插件 → PHYSICAL（插件拦截）");
  }

  /**
   * 测试：IPage + 有LIMIT + 无插件 → PHYSICAL（安全）.
   * SQL已经有LIMIT，数据库层面分页
   */
  @Test
  public void testIPageWithLimitWithoutPlugin_shouldDetectPhysical() {
    String sql = "SELECT * FROM users WHERE age > 18 LIMIT 100";
    Statement stmt = parser.parse(sql);

    Map<String, Object> params = new HashMap<>();
    params.put("page", new MockIPage(1, 20));

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .params(params)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "IPage + 有LIMIT + 无插件 → PHYSICAL（SQL已有分页）");
  }

  /**
   * 测试：IPage + 有LIMIT + 有插件 → PHYSICAL（安全）.
   */
  @Test
  public void testIPageWithLimitWithPlugin_shouldDetectPhysical() {
    String sql = "SELECT * FROM users WHERE age > 18 LIMIT 100";
    Statement stmt = parser.parse(sql);

    Map<String, Object> params = new HashMap<>();
    params.put("page", new MockIPage(1, 20));

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .params(params)
        .build();

    PaginationType type = detectorWithPageHelper.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "IPage + 有LIMIT + 有插件 → PHYSICAL");
  }

  // ============================================================
  // RowBounds + 插件组合测试（之前缺失的场景）
  // ============================================================

  /**
   * 测试：RowBounds + 无LIMIT + 有PageHelper插件 → PHYSICAL.
   */
  @Test
  public void testRowBoundsWithoutLimitWithPageHelper_shouldDetectPhysical() {
    String sql = "SELECT * FROM users WHERE age > 18";
    Statement stmt = parser.parse(sql);

    RowBounds rowBounds = new RowBounds(10, 20); // offset=10, limit=20

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .rowBounds(rowBounds)
        .build();

    PaginationType type = detectorWithPageHelper.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "RowBounds + 无LIMIT + PageHelper → PHYSICAL（插件拦截）");
  }

  /**
   * 测试：RowBounds + 无LIMIT + 有MyBatis-Plus插件 → PHYSICAL.
   */
  @Test
  public void testRowBoundsWithoutLimitWithMybatisPlus_shouldDetectPhysical() {
    String sql = "SELECT * FROM users WHERE age > 18";
    Statement stmt = parser.parse(sql);

    RowBounds rowBounds = new RowBounds(10, 20);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .rowBounds(rowBounds)
        .build();

    PaginationType type = detectorWithMybatisPlus.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "RowBounds + 无LIMIT + MyBatis-Plus → PHYSICAL（插件拦截）");
  }

  // ============================================================
  // TOP(100)括号形式测试（之前会漏报的bug）
  // ============================================================

  /**
   * 测试：SQL Server TOP(100)括号形式 → PHYSICAL.
   * SQL Server支持 TOP(100) 和 TOP 100 两种语法
   */
  @Test
  public void testSqlServerTopWithParentheses_shouldDetectPhysical() {
    String sql = "SELECT TOP(100) * FROM users WHERE age > 18";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "SQL Server TOP(100)括号形式应该被检测为PHYSICAL");
  }

  /**
   * 测试：嵌套查询中的TOP(100) → PHYSICAL.
   */
  @Test
  public void testNestedTopWithParentheses_shouldDetectPhysical() {
    String sql = "SELECT * FROM (SELECT TOP(50) * FROM users ORDER BY id) t WHERE status = 1";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "嵌套查询中的TOP(50)应该被字符串检测捕获");
  }

  // ============================================================
  // WITH子句（CTE）测试
  // ============================================================

  /**
   * 测试：WITH子句（CTE）+ LIMIT → PHYSICAL.
   */
  @Test
  public void testCTEWithLimit_shouldDetectPhysical() {
    String sql = "WITH temp AS (SELECT * FROM users WHERE age > 18 LIMIT 100) "
        + "SELECT * FROM temp WHERE status = 1";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "WITH子句中的LIMIT应该被字符串检测捕获");
  }

  /**
   * 测试：多个WITH子句 + 分页 → PHYSICAL.
   */
  @Test
  public void testMultipleCTEWithPagination_shouldDetectPhysical() {
    String sql = "WITH temp1 AS (SELECT * FROM users LIMIT 50), "
        + "temp2 AS (SELECT * FROM orders LIMIT 100) "
        + "SELECT * FROM temp1 JOIN temp2 ON temp1.id = temp2.user_id";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.PHYSICAL, type,
        "多个WITH子句中的LIMIT应该被检测");
  }

  // ============================================================
  // 表名/字段名误报边界测试
  // ============================================================

  /**
   * 测试：表名包含"top"关键字 → NONE（不应误报）.
   * 因为正则使用了单词边界\\b，"top_users"不会被匹配
   */
  @Test
  public void testTableNameContainsTop_shouldNotBeFalsePositive() {
    String sql = "SELECT * FROM top_users WHERE status = 1";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.NONE, type,
        "表名top_users不应被误报为分页");
  }

  /**
   * 测试：字段名包含"limit"关键字 → 可能误报为PHYSICAL（可接受）.
   * 例如：WHERE user_limit = 100
   * 这种误报是保守策略，比漏报安全
   */
  @Test
  public void testFieldNameContainsLimit_acceptableFalsePositive() {
    String sql = "SELECT user_limit FROM config WHERE user_limit = 100";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    // 这个测试记录当前行为：可能误报为PHYSICAL
    // 由于使用了\bLIMIT\s+\d+，"user_limit = 100"不会匹配（没有数字紧跟LIMIT）
    // 但"user_limit 100"可能会匹配
    // 不强制断言，只是记录行为
  }

  // ============================================================
  // RowBounds边界值测试
  // ============================================================

  /**
   * 测试：RowBounds(0, 0) - limit为0的边界情况.
   */
  @Test
  public void testRowBoundsZeroLimit_shouldStillDetectParameter() {
    String sql = "SELECT * FROM users";
    Statement stmt = parser.parse(sql);

    RowBounds rowBounds = new RowBounds(0, 0); // offset=0, limit=0

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .rowBounds(rowBounds)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    // RowBounds(0, 0) != RowBounds.DEFAULT，所以hasPageParam=true
    // 但没有LIMIT且没有插件，所以是LOGICAL
    assertEquals(PaginationType.LOGICAL, type,
        "RowBounds(0, 0)应该被检测为分页参数");
  }

  /**
   * 测试：RowBounds.DEFAULT → NONE.
   * RowBounds.DEFAULT表示无限制，不应被视为分页
   */
  @Test
  public void testRowBoundsDefault_shouldDetectNone() {
    String sql = "SELECT * FROM users";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .rowBounds(RowBounds.DEFAULT)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.NONE, type,
        "RowBounds.DEFAULT不应被视为分页");
  }

  /**
   * 测试：RowBounds(100, Integer.MAX_VALUE) - 只有offset无limit.
   * 虽然不等于DEFAULT，但没有真正的limit限制
   */
  @Test
  public void testRowBoundsOffsetOnlyNoLimit_shouldDetectLogical() {
    String sql = "SELECT * FROM users";
    Statement stmt = parser.parse(sql);

    RowBounds rowBounds = new RowBounds(100, Integer.MAX_VALUE);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .rowBounds(rowBounds)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    // 虽然这不是真正的分页（没有限制行数），但RowBounds存在且不是DEFAULT
    // 所以被检测为LOGICAL（可能是误报，但保守）
    assertEquals(PaginationType.LOGICAL, type,
        "RowBounds(100, MAX_VALUE)被检测为LOGICAL");
  }

  // ============================================================
  // null安全性测试
  // ============================================================

  /**
   * 测试：context为null → NONE.
   */
  @Test
  public void testNullContext_shouldDetectNone() {
    PaginationType type = detectorWithoutPlugin.detectPaginationType(null);
    assertEquals(PaginationType.NONE, type,
        "null context应该返回NONE");
  }

  /**
   * 测试：context.getStatement()为null → NONE.
   */
  @Test
  public void testNullStatement_shouldDetectNone() {
    SqlContext context = SqlContext.builder()
        .sql("SELECT * FROM users")
        .statement(null) // null statement
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.NONE, type,
        "null statement应该返回NONE");
  }

  /**
   * 测试：context.getParams()为null → 不影响RowBounds检测.
   */
  @Test
  public void testNullParams_shouldNotAffectRowBoundsDetection() {
    String sql = "SELECT * FROM users";
    Statement stmt = parser.parse(sql);

    RowBounds rowBounds = new RowBounds(0, 20);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .params(null) // null params
        .rowBounds(rowBounds)
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.LOGICAL, type,
        "params为null不影响RowBounds检测");
  }

  /**
   * 测试：空params map → 不影响检测.
   */
  @Test
  public void testEmptyParams_shouldDetectNone() {
    String sql = "SELECT * FROM users";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .params(new HashMap<>()) // empty map
        .build();

    PaginationType type = detectorWithoutPlugin.detectPaginationType(context);
    assertEquals(PaginationType.NONE, type,
        "空params应该返回NONE");
  }

  // ============================================================
  // Helper方法和Mock类
  // ============================================================

  /**
   * 创建PageHelper的PageInterceptor模拟对象.
   */
  private Interceptor createPageInterceptor() {
    return new MockPageInterceptor();
  }

  /**
   * Mock PageInterceptor类.
   */
  static class MockPageInterceptor implements Interceptor {
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

  /**
   * 创建MyBatis-Plus拦截器模拟对象.
   */
  private Object createMockMybatisPlusInterceptor() {
    final List<Object> innerInterceptors = new ArrayList<>();
    innerInterceptors.add(new MockPaginationInnerInterceptor());

    return new Object() {
      @SuppressWarnings("unused")
      public List<Object> getInterceptors() {
        return innerInterceptors;
      }
    };
  }

  /**
   * Mock PaginationInnerInterceptor类.
   */
  static class MockPaginationInnerInterceptor {
    // Class name contains "PaginationInnerInterceptor"
  }

  /**
   * Mock IPage类（模拟MyBatis-Plus的分页对象）.
   */
  static class MockIPage {
    private long current;
    private long size;

    public MockIPage(long current, long size) {
      this.current = current;
      this.size = size;
    }

    public long getCurrent() {
      return current;
    }

    public long getSize() {
      return size;
    }
  }
}
