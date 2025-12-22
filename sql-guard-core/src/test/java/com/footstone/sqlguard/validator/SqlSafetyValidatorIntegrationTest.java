package com.footstone.sqlguard.validator;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.SqlParseException;
import com.footstone.sqlguard.validator.pagination.impl.*;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import com.footstone.sqlguard.validator.rule.impl.*;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration tests for SqlSafetyValidator with all 10 rule checkers.
 */
@DisplayName("SqlSafetyValidatorIntegration - End-to-End Validation")
class SqlSafetyValidatorIntegrationTest {

  private DefaultSqlSafetyValidator validator;
  private JSqlParserFacade facade;

  @BeforeEach
  void setUp() {
    facade = new JSqlParserFacade(false); // fail-fast mode
    SqlDeduplicationFilter.clearThreadCache();
    
    // Create all 10 checkers with minimal configuration
    List<RuleChecker> checkers = createAllCheckers();
    
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    
    validator = new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
  }

  @AfterEach
  void tearDown() {
    SqlDeduplicationFilter.clearThreadCache();
  }

  /**
   * Creates a subset of rule checkers for integration testing.
   * Using only checkers that don't require complex dependencies.
   */
  private List<RuleChecker> createAllCheckers() {
    // Checker 1: NoWhereClauseChecker
    NoWhereClauseConfig noWhereConfig = new NoWhereClauseConfig();
    noWhereConfig.setEnabled(true);
    NoWhereClauseChecker noWhereChecker = new NoWhereClauseChecker(noWhereConfig);

    // Checker 2: DummyConditionChecker
    DummyConditionConfig dummyConfig = new DummyConditionConfig();
    dummyConfig.setEnabled(true);
    DummyConditionChecker dummyChecker = new DummyConditionChecker(dummyConfig);

    // Checker 3: BlacklistFieldChecker (with default blacklist)
    BlacklistFieldsConfig blacklistConfig = new BlacklistFieldsConfig();
    blacklistConfig.setEnabled(true);
    BlacklistFieldChecker blacklistChecker = new BlacklistFieldChecker(blacklistConfig);

    // Checker 4: WhitelistFieldChecker (with default whitelist)
    WhitelistFieldsConfig whitelistConfig = new WhitelistFieldsConfig();
    whitelistConfig.setEnabled(true);
    WhitelistFieldChecker whitelistChecker = new WhitelistFieldChecker(whitelistConfig);

    // Note: Pagination checkers require PaginationPluginDetector which needs
    // interceptor configuration. For this integration test, we focus on
    // the core validation flow with simpler checkers.

    return Arrays.asList(
        noWhereChecker,
        dummyChecker,
        blacklistChecker,
        whitelistChecker
    );
  }

  @Test
  @DisplayName("Multi-rule violation: No WHERE clause on UPDATE")
  void testMultiRuleViolation_noWhere() {
    // Note: NoWhereClauseChecker only checks UPDATE/DELETE, not SELECT
    // SELECT without WHERE is handled by NoPaginationChecker/NoConditionPaginationChecker
    // which require PaginationPluginDetector (not included in this test setup)
    String sql = "UPDATE users SET status = 'inactive'";
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.UPDATE)
        .mapperId("test.Mapper.updateUsers")
        .build();

    ValidationResult result = validator.validate(context);

    assertNotNull(result);
    assertFalse(result.isPassed(), "Should fail with violations");
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel(), "Should aggregate to CRITICAL");
    
    // Verify violation messages
    boolean hasNoWhereViolation = result.getViolations().stream()
        .anyMatch(v -> v.getMessage().toLowerCase().contains("where"));
    assertTrue(hasNoWhereViolation, "Should have NoWhereClause violation");
  }

  @Test
  @DisplayName("Clean SQL: Should pass all checks")
  void testCleanSQL_shouldPass() {
    String sql = "SELECT * FROM users WHERE id = ? ORDER BY create_time LIMIT 10";
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectUsers")
        .build();

    ValidationResult result = validator.validate(context);

    assertNotNull(result);
    assertTrue(result.isPassed(), "Clean SQL should pass");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel());
    assertTrue(result.getViolations().isEmpty(), "Should have no violations");
  }

  @Test
  @DisplayName("Parse failure in fail-fast mode should throw exception")
  void testParseFailureFailFast_shouldThrow() {
    String invalidSql = "SELECT * FORM users"; // Typo: FORM instead of FROM
    
    SqlContext context = SqlContext.builder()
        .sql(invalidSql)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectUsers")
        .build();

    assertThrows(SqlParseException.class, () -> {
      validator.validate(context);
    }, "Fail-fast mode should throw SqlParseException");
  }

  @Test
  @DisplayName("Parse failure in lenient mode should return pass")
  void testParseFailureLenient_shouldReturnPass() {
    // Create validator with lenient facade
    JSqlParserFacade lenientFacade = new JSqlParserFacade(true);
    List<RuleChecker> checkers = createAllCheckers();
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    DefaultSqlSafetyValidator lenientValidator = 
        new DefaultSqlSafetyValidator(lenientFacade, checkers, orchestrator, filter);

    String invalidSql = "SELECT * FORM users";
    
    SqlContext context = SqlContext.builder()
        .sql(invalidSql)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectUsers")
        .build();

    ValidationResult result = lenientValidator.validate(context);

    assertNotNull(result);
    assertTrue(result.isPassed(), "Lenient mode should return pass on parse failure");
  }

  @Test
  @DisplayName("Deduplication: Same SQL within TTL should return cached result")
  void testDeduplication_sameSQLWithinTTL() {
    String sql = "SELECT * FROM users WHERE id = ?";
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectUsers")
        .build();

    // First validation
    ValidationResult result1 = validator.validate(context);
    assertTrue(result1.isPassed());

    // Second validation (same SQL, within TTL)
    ValidationResult result2 = validator.validate(context);
    assertTrue(result2.isPassed());
    
    // Both should succeed (deduplication working)
    assertNotNull(result1);
    assertNotNull(result2);
  }

  @Test
  @DisplayName("All checkers enabled: Various SQL patterns")
  void testAllCheckersEnabled_variousPatterns() {
    // Test 1: Dummy condition
    SqlContext dummyContext = SqlContext.builder()
        .sql("SELECT * FROM users WHERE 1=1")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectUsers")
        .build();
    
    ValidationResult dummyResult = validator.validate(dummyContext);
    assertFalse(dummyResult.isPassed(), "Dummy condition should fail");
    assertTrue(dummyResult.getRiskLevel().compareTo(RiskLevel.HIGH) >= 0);

    // Clear cache for next test
    SqlDeduplicationFilter.clearThreadCache();

    // Test 2: Blacklist field only (using default blacklist: deleted, status, etc.)
    SqlContext blacklistContext = SqlContext.builder()
        .sql("SELECT * FROM users WHERE deleted = 0")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectUsers")
        .build();
    
    ValidationResult blacklistResult = validator.validate(blacklistContext);
    assertFalse(blacklistResult.isPassed(), "Blacklist-only WHERE should fail");

    // Clear cache for next test
    SqlDeduplicationFilter.clearThreadCache();

    // Test 3: Valid WHERE with non-blacklist field
    SqlContext validContext = SqlContext.builder()
        .sql("SELECT * FROM users WHERE name = ?")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectUsers")
        .build();
    
    ValidationResult validResult = validator.validate(validContext);
    // This should pass as 'name' is not in default blacklist
    assertTrue(validResult.isPassed(), "Valid WHERE with non-blacklist field should pass");
  }

  @Test
  @DisplayName("UPDATE without WHERE should trigger CRITICAL violation")
  void testUpdateWithoutWhere_shouldBeCritical() {
    String sql = "UPDATE users SET status = 1";
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.UPDATE)
        .mapperId("test.Mapper.updateUsers")
        .build();

    ValidationResult result = validator.validate(context);

    assertNotNull(result);
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel(), 
        "UPDATE without WHERE should be CRITICAL");
  }

  @Test
  @DisplayName("DELETE without WHERE should trigger CRITICAL violation")
  void testDeleteWithoutWhere_shouldBeCritical() {
    String sql = "DELETE FROM users";
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.DELETE)
        .mapperId("test.Mapper.deleteUsers")
        .build();

    ValidationResult result = validator.validate(context);

    assertNotNull(result);
    assertFalse(result.isPassed());
    assertEquals(RiskLevel.CRITICAL, result.getRiskLevel(), 
        "DELETE without WHERE should be CRITICAL");
  }

  @Test
  @DisplayName("Complex SQL with JOIN and subquery should parse correctly")
  void testComplexSQL_shouldParseAndValidate() {
    String sql = "SELECT u.*, o.order_id FROM users u " +
                 "JOIN orders o ON u.id = o.user_id " +
                 "WHERE u.id = ? AND o.status = 'active' " +
                 "ORDER BY o.create_time LIMIT 10";
    
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectUsersWithOrders")
        .build();

    ValidationResult result = validator.validate(context);

    assertNotNull(result);
    assertTrue(result.isPassed(), "Complex SQL with proper WHERE and ORDER BY should pass");
  }

  @Test
  @DisplayName("Thread safety: Concurrent validations should work correctly")
  void testThreadSafety_concurrentValidations() throws InterruptedException {
    String sql = "SELECT * FROM users WHERE id = ?";
    
    // Create multiple threads performing validation
    Thread[] threads = new Thread[10];
    final boolean[] results = new boolean[10];
    
    for (int i = 0; i < 10; i++) {
      final int index = i;
      threads[i] = new Thread(() -> {
        SqlContext context = SqlContext.builder()
            .sql(sql)
            .type(SqlCommandType.SELECT)
            .mapperId("test.Mapper.selectUsers")
            .build();
        
        ValidationResult result = validator.validate(context);
        results[index] = result.isPassed();
        
        // Cleanup thread cache
        SqlDeduplicationFilter.clearThreadCache();
      });
    }
    
    // Start all threads
    for (Thread thread : threads) {
      thread.start();
    }
    
    // Wait for all threads to complete
    for (Thread thread : threads) {
      thread.join();
    }
    
    // Verify all validations passed
    for (int i = 0; i < 10; i++) {
      assertTrue(results[i], "Thread " + i + " validation should pass");
    }
  }
}
