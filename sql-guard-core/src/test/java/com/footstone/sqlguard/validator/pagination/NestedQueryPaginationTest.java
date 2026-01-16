package com.footstone.sqlguard.validator.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 测试嵌套子查询和复杂语句的分页检测.
 *
 * <p>验证当前实现对以下场景的处理：</p>
 * <ul>
 *   <li>嵌套子查询（内层有 LIMIT，外层无）</li>
 *   <li>UNION 语句</li>
 *   <li>深层嵌套的 Oracle ROWNUM</li>
 * </ul>
 */
public class NestedQueryPaginationTest {

  private JSqlParserFacade parser;
  private PaginationPluginDetector detector;

  @BeforeEach
  public void setUp() {
    parser = new JSqlParserFacade();
    detector = new PaginationPluginDetector(null, null);
  }

  /**
   * 测试：内层子查询有 LIMIT，外层无 LIMIT.
   * 当前实现：只检查外层，会返回 NONE（❌ 问题）
   * 期望：应该返回 PHYSICAL（内层已限制结果集）
   */
  @Test
  public void testNestedSubqueryWithInnerLimit_currentBehavior() {
    String sql = "SELECT * FROM (SELECT * FROM users WHERE age > 18 LIMIT 100) t WHERE id > 0";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .build();

    PaginationType type = detector.detectPaginationType(context);

    // 当前实现的实际行为：由于字符串检测包含 "LIMIT"，会返回 PHYSICAL
    // 所以这个场景实际上已经被字符串检测覆盖了！
    assertEquals(PaginationType.PHYSICAL, type,
        "内层子查询有 LIMIT，应该被字符串检测捕获");
  }

  /**
   * 测试：UNION 语句，各分支都有 LIMIT.
   * 当前实现：SelectBody 是 SetOperationList，结构化检测返回 false
   * 但字符串检测应该能找到 LIMIT
   */
  @Test
  public void testUnionWithLimit_shouldDetectPhysical() {
    String sql = "SELECT * FROM users LIMIT 10 UNION SELECT * FROM admins LIMIT 20";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .build();

    PaginationType type = detector.detectPaginationType(context);

    assertEquals(PaginationType.PHYSICAL, type,
        "UNION 语句的 LIMIT 应该被字符串检测捕获");
  }

  /**
   * 测试：SQL Server TOP 在子查询中.
   * 当前实现：只检查外层 PlainSelect.getTop()
   */
  @Test
  public void testNestedTopClause_currentBehavior() {
    String sql = "SELECT * FROM (SELECT TOP 100 * FROM users) t WHERE id > 0";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detector.detectPaginationType(context);

    assertEquals(PaginationType.PHYSICAL, type,
        "嵌套查询中的 TOP 应该被字符串检测捕获");
  }

  /**
   * 测试：深层嵌套的 Oracle ROWNUM.
   * 字符串检测应该能够处理
   */
  @Test
  public void testDeeplyNestedRownum_shouldDetectPhysical() {
    String sql = "SELECT * FROM (SELECT * FROM (SELECT * FROM users WHERE ROWNUM <= 100) t1) t2";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detector.detectPaginationType(context);

    assertEquals(PaginationType.PHYSICAL, type,
        "深层嵌套的 ROWNUM 应该被字符串检测捕获");
  }

  /**
   * 测试：DB2 FETCH FIRST 在子查询中.
   */
  @Test
  public void testNestedFetchFirst_currentBehavior() {
    String sql = "SELECT * FROM (SELECT * FROM users FETCH FIRST 100 ROWS ONLY) t";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.JDBC)
        .build();

    PaginationType type = detector.detectPaginationType(context);

    assertEquals(PaginationType.PHYSICAL, type,
        "嵌套 FETCH FIRST 应该被字符串检测捕获");
  }

  /**
   * 测试：字段名包含 "limit" 的误报情况.
   * 这是字符串检测的潜在问题
   */
  @Test
  public void testColumnNamedLimit_potentialFalsePositive() {
    String sql = "SELECT user_limit, credit_limit FROM users WHERE age > 18";
    Statement stmt = parser.parse(sql);

    SqlContext context = SqlContext.builder()
        .sql(sql)
        .statement(stmt)
        .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
        .build();

    PaginationType type = detector.detectPaginationType(context);

    // 字符串包含 "LIMIT"，但实际没有分页
    // 由于使用了单词边界 \bLIMIT\b，"user_limit" 不会匹配
    assertEquals(PaginationType.NONE, type,
        "字段名包含 limit 不应被误判为分页");
  }
}
