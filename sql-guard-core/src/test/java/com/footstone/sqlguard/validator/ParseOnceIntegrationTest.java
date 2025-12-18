package com.footstone.sqlguard.validator;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.SqlParseException;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for parse-once optimization in DefaultSqlSafetyValidator.
 */
@DisplayName("ParseOnceIntegration - Parse-Once Logic")
class ParseOnceIntegrationTest {

  private DefaultSqlSafetyValidator validator;
  private JSqlParserFacade facade;
  private AtomicInteger parseCallCount;

  @BeforeEach
  void setUp() {
    parseCallCount = new AtomicInteger(0);
    SqlDeduplicationFilter.clearThreadCache();
  }

  @Test
  @DisplayName("parsedSql null in context should trigger facade.parse() call")
  void testParsedSqlNull_shouldTriggerParse() {
    facade = new JSqlParserFacade(false); // fail-fast mode
    
    // Create mock checker to verify parsed SQL is available
    RuleChecker mockChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        assertNotNull(context.getParsedSql(), "Parsed SQL should be available to checker");
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(Arrays.asList(mockChecker));
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    validator = new DefaultSqlSafetyValidator(facade, Arrays.asList(mockChecker), orchestrator, filter);

    // Create context WITHOUT parsed SQL
    SqlContext context = SqlContext.builder()
        .sql("SELECT * FROM users WHERE id = ?")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    assertNull(context.getParsedSql(), "Initial context should have null parsedSql");

    // Execute validation
    ValidationResult result = validator.validate(context);

    // Verify result
    assertNotNull(result);
    assertTrue(result.isPassed());
  }

  @Test
  @DisplayName("parsedSql set in context should not trigger re-parsing")
  void testParsedSqlSet_shouldNotReparse() {
    facade = new JSqlParserFacade(false);
    
    // Track parse calls
    final AtomicInteger checkerCallCount = new AtomicInteger(0);
    
    RuleChecker mockChecker = new RuleChecker() {
      private Statement firstParsedSql = null;

      @Override
      public void check(SqlContext context, ValidationResult result) {
        checkerCallCount.incrementAndGet();
        Statement currentParsedSql = context.getParsedSql();
        assertNotNull(currentParsedSql, "Parsed SQL should be available");
        
        if (firstParsedSql == null) {
          firstParsedSql = currentParsedSql;
        } else {
          assertSame(firstParsedSql, currentParsedSql, "All checkers should receive same Statement instance");
        }
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(Arrays.asList(mockChecker));
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    validator = new DefaultSqlSafetyValidator(facade, Arrays.asList(mockChecker), orchestrator, filter);

    // Pre-parse SQL
    String sql = "SELECT * FROM users WHERE id = ?";
    Statement preParsedStmt = facade.parse(sql);
    
    // Create context WITH parsed SQL
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(preParsedStmt)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    assertNotNull(context.getParsedSql(), "Context should have pre-parsed SQL");

    // Execute validation
    validator.validate(context);

    assertEquals(1, checkerCallCount.get(), "Checker should be called once");
  }

  @Test
  @DisplayName("All checkers should receive same parsedSql instance")
  void testAllCheckersReceiveSameParsedSql() {
    facade = new JSqlParserFacade(false);
    
    final Statement[] capturedStatements = new Statement[3];
    
    // Create 3 checkers that capture the Statement instance
    RuleChecker checker1 = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        capturedStatements[0] = context.getParsedSql();
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    RuleChecker checker2 = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        capturedStatements[1] = context.getParsedSql();
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    RuleChecker checker3 = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        capturedStatements[2] = context.getParsedSql();
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(
        Arrays.asList(checker1, checker2, checker3)
    );
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    validator = new DefaultSqlSafetyValidator(
        facade, 
        Arrays.asList(checker1, checker2, checker3), 
        orchestrator, 
        filter
    );

    // Create context without parsed SQL
    SqlContext context = SqlContext.builder()
        .sql("SELECT * FROM users WHERE id = ?")
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    // Execute validation
    validator.validate(context);

    // Verify all checkers received same Statement instance
    assertNotNull(capturedStatements[0], "Checker1 should receive Statement");
    assertNotNull(capturedStatements[1], "Checker2 should receive Statement");
    assertNotNull(capturedStatements[2], "Checker3 should receive Statement");
    assertSame(capturedStatements[0], capturedStatements[1], "Checker1 and Checker2 should receive same instance");
    assertSame(capturedStatements[1], capturedStatements[2], "Checker2 and Checker3 should receive same instance");
  }

  @Test
  @DisplayName("Parse failure with fail-fast config should throw SqlParseException")
  void testParseFailureFailFast_shouldThrowException() {
    facade = new JSqlParserFacade(false); // fail-fast mode
    
    RuleChecker mockChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        fail("Checker should not be called when parse fails");
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(Arrays.asList(mockChecker));
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    validator = new DefaultSqlSafetyValidator(facade, Arrays.asList(mockChecker), orchestrator, filter);

    // Create context with invalid SQL
    SqlContext context = SqlContext.builder()
        .sql("SELECT * FORM users") // Typo: FORM instead of FROM
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    // Execute validation - should throw SqlParseException
    assertThrows(SqlParseException.class, () -> {
      validator.validate(context);
    }, "Fail-fast mode should throw SqlParseException on parse failure");
  }

  @Test
  @DisplayName("Parse failure with lenient config should log warning and return pass")
  void testParseFailureLenient_shouldReturnPass() {
    facade = new JSqlParserFacade(true); // lenient mode
    
    RuleChecker mockChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        fail("Checker should not be called when parse fails in lenient mode");
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(Arrays.asList(mockChecker));
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    validator = new DefaultSqlSafetyValidator(facade, Arrays.asList(mockChecker), orchestrator, filter);

    // Create context with invalid SQL
    SqlContext context = SqlContext.builder()
        .sql("SELECT * FORM users") // Typo: FORM instead of FROM
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    // Execute validation - should return pass (not throw)
    ValidationResult result = validator.validate(context);

    assertNotNull(result, "Result should not be null");
    assertTrue(result.isPassed(), "Lenient mode should return pass on parse failure");
    assertEquals(RiskLevel.SAFE, result.getRiskLevel(), "Risk level should be SAFE");
    assertTrue(result.getViolations().isEmpty(), "Should have no violations");
  }

  @Test
  @DisplayName("Deduplication should skip parsing for cached SQL")
  void testDeduplication_shouldSkipParsing() {
    facade = new JSqlParserFacade(false);
    
    final AtomicInteger checkerCallCount = new AtomicInteger(0);
    
    RuleChecker mockChecker = new RuleChecker() {
      @Override
      public void check(SqlContext context, ValidationResult result) {
        checkerCallCount.incrementAndGet();
      }

      @Override
      public boolean isEnabled() {
        return true;
      }
    };

    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(Arrays.asList(mockChecker));
    SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
    validator = new DefaultSqlSafetyValidator(facade, Arrays.asList(mockChecker), orchestrator, filter);

    String sql = "SELECT * FROM users WHERE id = ?";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(SqlCommandType.SELECT)
        .mapperId("test.Mapper.selectById")
        .build();

    // First validation
    ValidationResult result1 = validator.validate(context);
    assertTrue(result1.isPassed());
    assertEquals(1, checkerCallCount.get(), "First validation should call checker");

    // Second validation (same SQL, within TTL)
    ValidationResult result2 = validator.validate(context);
    assertTrue(result2.isPassed());
    assertEquals(1, checkerCallCount.get(), "Second validation should skip checker (cached)");
  }
}







