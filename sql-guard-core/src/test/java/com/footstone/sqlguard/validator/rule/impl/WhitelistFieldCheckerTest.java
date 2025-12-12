package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for WhitelistFieldChecker.
 *
 * <p>Verifies enforcement of table-specific mandatory WHERE fields (whitelist) to ensure
 * queries include primary keys, tenant IDs, or other high-selectivity fields for critical tables.</p>
 */
class WhitelistFieldCheckerTest {

  private JSqlParserFacade parser;
  private WhitelistFieldChecker checker;
  private WhitelistFieldsConfig config;

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    
    // Setup default config with table-specific whitelist
    Map<String, List<String>> byTable = new HashMap<>();
    byTable.put("user", Arrays.asList("id", "user_id"));
    byTable.put("order", Collections.emptyList()); // Empty list means no enforcement
    byTable.put("tenant_data", Arrays.asList("tenant_id"));
    
    config = new WhitelistFieldsConfig();
    config.setByTable(byTable);
    config.setEnforceForUnknownTables(false);
    
    checker = new WhitelistFieldChecker(config);
  }

  // ==================== Table-Specific Whitelist Tests ====================

  @Test
  void testUserTableWithId_shouldPass() {
    String sql = "SELECT * FROM user WHERE id=?";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testUserTableWithoutRequiredField_shouldViolate() {
    String sql = "SELECT * FROM user WHERE status=?";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("user"));
    assertTrue(result.getViolations().get(0).getMessage().contains("id"));
    assertTrue(result.getViolations().get(0).getMessage().contains("user_id"));
  }

  @Test
  void testOrderTableNoWhitelist_shouldPass() {
    // order table has empty whitelist, so any WHERE passes
    String sql = "SELECT * FROM order WHERE status='pending'";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testMultipleRequiredFieldsAny_shouldPass() {
    // user requires [id, user_id], WHERE user_id=? should pass
    String sql = "SELECT * FROM user WHERE user_id=?";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Edge Cases ====================

  @Test
  void testJoinMultipleTables_shouldUsePrimaryTable() {
    // JOIN query should check primary table (user) whitelist
    String sql = "SELECT * FROM user u JOIN order o ON u.id=o.user_id WHERE u.id=?";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testTableWithMultipleRequiredFields_anyOneSatisfies() {
    // user requires [id, user_id], WHERE with any one should pass
    String sql = "SELECT * FROM user WHERE user_id=? AND status='active'";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testTableNotInWhitelist_shouldPass() {
    // unknown_table not in byTable map, should pass
    String sql = "SELECT * FROM unknown_table WHERE status='active'";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testEmptyRequiredFields_shouldPass() {
    // order table has empty list in byTable map, should pass
    String sql = "SELECT * FROM order WHERE status='pending'";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testEnforceForUnknownTables_shouldViolate() {
    // Setup config with enforceForUnknownTables=true and global fields
    Map<String, List<String>> byTable = new HashMap<>();
    byTable.put("user", Arrays.asList("id", "user_id"));
    
    WhitelistFieldsConfig strictConfig = new WhitelistFieldsConfig();
    strictConfig.setByTable(byTable);
    strictConfig.setFields(Arrays.asList("id", "tenant_id"));
    strictConfig.setEnforceForUnknownTables(true);
    
    WhitelistFieldChecker strictChecker = new WhitelistFieldChecker(strictConfig);

    // unknown_table not in byTable, but enforceForUnknownTables=true
    String sql = "SELECT * FROM unknown_table WHERE status='active'";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    strictChecker.check(context, result);

    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("unknown_table"));
  }

  @Test
  void testDisabledChecker_shouldSkip() {
    Map<String, List<String>> byTable = new HashMap<>();
    byTable.put("user", Arrays.asList("id", "user_id"));
    
    WhitelistFieldsConfig disabledConfig = new WhitelistFieldsConfig(false);
    disabledConfig.setByTable(byTable);
    
    WhitelistFieldChecker disabledChecker = new WhitelistFieldChecker(disabledConfig);

    String sql = "SELECT * FROM user WHERE status='active'";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    disabledChecker.check(context, result);

    // Should not add violations when disabled
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  // ==================== Additional Edge Cases ====================

  @Test
  void testTenantDataWithTenantId_shouldPass() {
    String sql = "SELECT * FROM tenant_data WHERE tenant_id=?";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testTenantDataWithoutTenantId_shouldViolate() {
    String sql = "SELECT * FROM tenant_data WHERE status='active'";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("tenant_data"));
    assertTrue(result.getViolations().get(0).getMessage().contains("tenant_id"));
  }

  @Test
  void testUpdateWithRequiredField_shouldPass() {
    String sql = "UPDATE user SET status='inactive' WHERE id=?";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.UPDATE)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testUpdateWithoutRequiredField_shouldViolate() {
    String sql = "UPDATE user SET status='inactive' WHERE email=?";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.UPDATE)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testDeleteWithRequiredField_shouldPass() {
    String sql = "DELETE FROM user WHERE user_id=?";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.DELETE)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testDeleteWithoutRequiredField_shouldViolate() {
    String sql = "DELETE FROM user WHERE status='inactive'";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.DELETE)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }

  @Test
  void testNoWhereClause_shouldSkip() {
    // No WHERE clause means nothing to check
    String sql = "SELECT * FROM user";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testComplexWhereWithRequiredField_shouldPass() {
    String sql = "SELECT * FROM user WHERE (status='active' OR status='pending') AND id=?";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testComplexWhereWithoutRequiredField_shouldViolate() {
    String sql = "SELECT * FROM user WHERE (status='active' OR status='pending') AND email=?";
    Statement stmt = parser.parse(sql);
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(stmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.testMethod")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
  }
}

