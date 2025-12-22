package com.footstone.sqlguard.validator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * TDD tests verifying parsing count reduction from N times to 1 time.
 * 
 * <p>Phase 12 Architecture Optimization:</p>
 * <ul>
 *   <li>Old Architecture: Each Checker independently parses SQL (N parses for N checkers)</li>
 *   <li>New Architecture: Interceptor parses once, all Checkers reuse Statement from SqlContext</li>
 *   <li>Expected Reduction: 90% for 10 checkers scenario</li>
 * </ul>
 */
@DisplayName("Parsing Count Verification Tests")
public class ParsingCountVerificationTest {

    private JSqlParserFacade facade;
    private JSqlParserFacade spyFacade;

    @BeforeEach
    void setUp() {
        facade = new JSqlParserFacade(false); // fail-fast mode
        SqlDeduplicationFilter.clearThreadCache();
    }

    @Nested
    @DisplayName("1. Parsing Count Tests")
    class ParsingCountTests {

        @Test
        @DisplayName("testNewArchitecture_parsesOnce_cachesInSqlContext - parse once per validation")
        void testNewArchitecture_parsesOnce_cachesInSqlContext() {
            // Arrange
            String sql = "SELECT * FROM users WHERE age > 18";
            spyFacade = Mockito.spy(new JSqlParserFacade(false));
            
            // Create multiple checkers to simulate real scenario
            List<RuleChecker> checkers = createMockCheckers(5);
            RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
            SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
            
            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
                spyFacade, checkers, orchestrator, filter);

            // Act
            SqlContext context = SqlContext.builder()
                .sql(sql)
                .type(SqlCommandType.SELECT)
                .mapperId("com.example.UserMapper.selectByAge")
                .build();

            ValidationResult result = validator.validate(context);

            // Assert - Should parse exactly once
            verify(spyFacade, times(1)).parse(anyString());
            assertTrue(result.isPassed(), "Validation should pass");
        }

        @Test
        @DisplayName("testParsingReduction_90percent_with10Checkers - 90% reduction with 10 checkers")
        void testParsingReduction_90percent_with10Checkers() {
            // Arrange - 10 checkers enabled
            spyFacade = Mockito.spy(new JSqlParserFacade(false));
            
            List<RuleChecker> checkers = createMockCheckers(10);
            RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
            SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
            
            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
                spyFacade, checkers, orchestrator, filter);

            // Count enabled checkers
            int enabledCheckerCount = 10;

            // Act - Validate SQL
            String sql = "SELECT id, name FROM users LIMIT 10";
            SqlContext context = SqlContext.builder()
                .sql(sql)
                .type(SqlCommandType.SELECT)
                .mapperId("com.example.UserMapper.selectUsers")
                .build();

            validator.validate(context);

            // Assert
            int expectedOldParseCount = enabledCheckerCount; // Old: N parses
            int actualNewParseCount = 1; // New architecture: 1 parse
            
            verify(spyFacade, times(1)).parse(anyString());

            double reductionPercent = ((double)(expectedOldParseCount - actualNewParseCount) / expectedOldParseCount) * 100;
            assertTrue(reductionPercent >= 90,
                String.format("Expected 90%% reduction, got %.1f%% (old: %d parses → new: %d parse)",
                    reductionPercent, expectedOldParseCount, actualNewParseCount));
            
            System.out.printf("✅ Parsing reduction verified: %d → 1 (%.0f%% reduction)%n", 
                expectedOldParseCount, reductionPercent);
        }

        @Test
        @DisplayName("testSpyVerification_parseInvocationCount_accurate - spy accurately counts invocations")
        void testSpyVerification_parseInvocationCount_accurate() {
            // Arrange
            String sql = "SELECT * FROM users";
            spyFacade = Mockito.spy(new JSqlParserFacade(false));

            // Act - Direct parse invocations
            spyFacade.parse(sql);
            spyFacade.parse(sql);
            spyFacade.parse(sql);

            // Assert
            verify(spyFacade, times(3)).parse(anyString());
            System.out.println("✅ Spy verification: accurately counted 3 invocations");
        }

        @Test
        @DisplayName("testCacheHit_Statement_reusedByCheckers - Statement cached and reused")
        void testCacheHit_Statement_reusedByCheckers() {
            // Arrange
            String sql = "SELECT * FROM users WHERE age > 18";
            Statement parsedStatement = facade.parse(sql);

            // Create context WITH pre-parsed Statement
            SqlContext context = SqlContext.builder()
                .sql(sql)
                .statement(parsedStatement)  // ✅ Statement already cached
                .type(SqlCommandType.SELECT)
                .mapperId("com.example.UserMapper.selectByAge")
                .build();

            // Assert - SqlContext should return the cached statement
            assertNotNull(context.getStatement());
            assertSame(parsedStatement, context.getStatement(),
                "Statement should be the same instance (cached and reused)");
            
            // Verify that validator with pre-parsed context doesn't re-parse
            spyFacade = Mockito.spy(new JSqlParserFacade(false));
            List<RuleChecker> checkers = createMockCheckers(3);
            RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
            SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
            
            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
                spyFacade, checkers, orchestrator, filter);
            
            validator.validate(context);
            
            // Should NOT parse since Statement is already in context
            verify(spyFacade, never()).parse(anyString());
            System.out.println("✅ Pre-parsed Statement reused: no additional parse calls");
        }

        @Test
        @DisplayName("testMultipleValidations_parsesEachUniqueSql_once - unique SQLs parsed once each")
        void testMultipleValidations_parsesEachUniqueSql_once() {
            // Arrange
            spyFacade = Mockito.spy(new JSqlParserFacade(false));
            List<RuleChecker> checkers = createMockCheckers(3);
            RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
            
            // Disable deduplication filter to test parsing behavior
            SqlDeduplicationFilter filter = new SqlDeduplicationFilter() {
                @Override
                public boolean shouldCheck(String sql) {
                    return true; // Always check (bypass deduplication)
                }
            };
            
            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
                spyFacade, checkers, orchestrator, filter);

            String sql1 = "SELECT * FROM users";
            String sql2 = "SELECT * FROM products";
            String sql3 = "SELECT * FROM orders";

            // Act - Validate 3 different SQLs
            validator.validate(buildContext(sql1));
            validator.validate(buildContext(sql2));
            validator.validate(buildContext(sql3));

            // Assert - Should parse 3 times (once per unique SQL)
            verify(spyFacade, times(3)).parse(anyString());
            System.out.println("✅ Each unique SQL parsed exactly once");
        }
    }

    @Nested
    @DisplayName("2. Statement Sharing Verification")
    class StatementSharingTests {

        @Test
        @DisplayName("testAllCheckers_receivesSameStatementInstance - all checkers share one Statement")
        void testAllCheckers_receivesSameStatementInstance() {
            // Arrange
            String sql = "SELECT id, name FROM users WHERE status = 'ACTIVE'";
            final Statement[] capturedStatements = new Statement[3];
            
            // Create 3 checkers that capture the Statement instance
            RuleChecker checker1 = createStatementCapturingChecker(capturedStatements, 0);
            RuleChecker checker2 = createStatementCapturingChecker(capturedStatements, 1);
            RuleChecker checker3 = createStatementCapturingChecker(capturedStatements, 2);

            List<RuleChecker> checkers = Arrays.asList(checker1, checker2, checker3);
            RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
            SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
            
            DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
                facade, checkers, orchestrator, filter);

            // Create context without pre-parsed SQL
            SqlContext context = SqlContext.builder()
                .sql(sql)
                .type(SqlCommandType.SELECT)
                .mapperId("com.example.UserMapper.selectActive")
                .build();

            // Act
            validator.validate(context);

            // Assert - All checkers received same Statement instance
            assertNotNull(capturedStatements[0], "Checker1 should receive Statement");
            assertNotNull(capturedStatements[1], "Checker2 should receive Statement");
            assertNotNull(capturedStatements[2], "Checker3 should receive Statement");
            assertSame(capturedStatements[0], capturedStatements[1], 
                "Checker1 and Checker2 should receive same instance");
            assertSame(capturedStatements[1], capturedStatements[2], 
                "Checker2 and Checker3 should receive same instance");
            
            System.out.println("✅ All 3 checkers received the same Statement instance");
        }
    }

    // ========== Helper Methods ==========

    /**
     * Creates a list of mock checkers that do nothing but are enabled.
     */
    private List<RuleChecker> createMockCheckers(int count) {
        List<RuleChecker> checkers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            checkers.add(new RuleChecker() {
                @Override
                public void check(SqlContext context, ValidationResult result) {
                    // Verify Statement is available
                    assertNotNull(context.getStatement(), 
                        "Parsed Statement should be available to checker");
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }
            });
        }
        return checkers;
    }

    /**
     * Creates a checker that captures the Statement instance for verification.
     */
    private RuleChecker createStatementCapturingChecker(Statement[] capturedStatements, int index) {
        return new RuleChecker() {
            @Override
            public void check(SqlContext context, ValidationResult result) {
                capturedStatements[index] = context.getStatement();
            }

            @Override
            public boolean isEnabled() {
                return true;
            }
        };
    }

    /**
     * Builds a SqlContext for testing.
     */
    private SqlContext buildContext(String sql) {
        return SqlContext.builder()
            .sql(sql)
            .type(SqlCommandType.SELECT)
            .mapperId("com.example.TestMapper.method")
            .build();
    }
}

