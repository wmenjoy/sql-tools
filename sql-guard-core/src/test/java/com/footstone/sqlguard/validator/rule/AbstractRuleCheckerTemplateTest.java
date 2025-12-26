package com.footstone.sqlguard.validator.rule;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.ValidationResult;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for AbstractRuleChecker template method implementation.
 * <p>
 * These tests validate the Phase 12.4 refactoring requirements:
 * <ul>
 *   <li>check() method is final (template method pattern)</li>
 *   <li>Statement type dispatch to visitXxx() methods</li>
 *   <li>Default visitXxx() implementations are empty</li>
 *   <li>Old utility methods have been removed</li>
 *   <li>Exception handling (degradation pattern)</li>
 *   <li>addViolation() helper method</li>
 *   <li>ThreadLocal cleanup for memory safety</li>
 * </ul>
 * </p>
 */
@DisplayName("AbstractRuleChecker Template Method Tests")
public class AbstractRuleCheckerTemplateTest {

    @Nested
    @DisplayName("1. Template Method Tests")
    class TemplateMethodTests {

        /**
         * Test 1: check() method is final (cannot be overridden)
         */
        @Test
        @DisplayName("check() should be final to enforce template method pattern")
        public void testCheck_final_cannotOverride() throws NoSuchMethodException {
            Method checkMethod = AbstractRuleChecker.class.getMethod("check", SqlContext.class, ValidationResult.class);

            // Verify method is final
            assertTrue(Modifier.isFinal(checkMethod.getModifiers()),
                    "check() must be final to prevent subclasses from overriding the template method");
        }

        /**
         * Test 2: SELECT statement dispatches to visitSelect()
         */
        @Test
        @DisplayName("SELECT statement should dispatch to visitSelect()")
        public void testCheck_selectStatement_dispatchesToVisitSelect() throws JSQLParserException {
            final boolean[] visitSelectCalled = {false};

            // Create checker that tracks visitSelect() calls
            AbstractRuleChecker checker = new TestChecker(null) {
                @Override
                public void visitSelect(Select select, SqlContext context) {
                    visitSelectCalled[0] = true;
                    assertNotNull(select);
                    assertNotNull(context);
                }
            };

            // Parse SELECT statement
            Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM users");
            SqlContext context = SqlContext.builder()
                    .sql("SELECT * FROM users")
                    .statementId("test.select")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();

            // Execute check() - should dispatch to visitSelect()
            checker.check(context, result);

            assertTrue(visitSelectCalled[0], "visitSelect() should have been called");
        }

        /**
         * Test 3: UPDATE statement dispatches to visitUpdate()
         */
        @Test
        @DisplayName("UPDATE statement should dispatch to visitUpdate()")
        public void testCheck_updateStatement_dispatchesToVisitUpdate() throws JSQLParserException {
            final boolean[] visitUpdateCalled = {false};

            AbstractRuleChecker checker = new TestChecker(null) {
                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    visitUpdateCalled[0] = true;
                    assertNotNull(update);
                    assertNotNull(context);
                }
            };

            Statement stmt = CCJSqlParserUtil.parse("UPDATE users SET name = 'foo'");
            SqlContext context = SqlContext.builder()
                    .sql("UPDATE users SET name = 'foo'")
                    .statementId("test.update")
                    .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(visitUpdateCalled[0], "visitUpdate() should have been called");
        }

        /**
         * Test 4: DELETE statement dispatches to visitDelete()
         */
        @Test
        @DisplayName("DELETE statement should dispatch to visitDelete()")
        public void testCheck_deleteStatement_dispatchesToVisitDelete() throws JSQLParserException {
            final boolean[] visitDeleteCalled = {false};

            AbstractRuleChecker checker = new TestChecker(null) {
                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    visitDeleteCalled[0] = true;
                    assertNotNull(delete);
                    assertNotNull(context);
                }
            };

            Statement stmt = CCJSqlParserUtil.parse("DELETE FROM users");
            SqlContext context = SqlContext.builder()
                    .sql("DELETE FROM users")
                    .statementId("test.delete")
                    .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(visitDeleteCalled[0], "visitDelete() should have been called");
        }

        /**
         * Test 5: INSERT statement dispatches to visitInsert()
         */
        @Test
        @DisplayName("INSERT statement should dispatch to visitInsert()")
        public void testCheck_insertStatement_dispatchesToVisitInsert() throws JSQLParserException {
            final boolean[] visitInsertCalled = {false};

            AbstractRuleChecker checker = new TestChecker(null) {
                @Override
                public void visitInsert(Insert insert, SqlContext context) {
                    visitInsertCalled[0] = true;
                    assertNotNull(insert);
                    assertNotNull(context);
                }
            };

            Statement stmt = CCJSqlParserUtil.parse("INSERT INTO users VALUES (1, 'foo')");
            SqlContext context = SqlContext.builder()
                    .sql("INSERT INTO users VALUES (1, 'foo')")
                    .statementId("test.insert")
                    .type(SqlCommandType.INSERT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            assertTrue(visitInsertCalled[0], "visitInsert() should have been called");
        }
    }

    @Nested
    @DisplayName("2. Default Implementation Tests")
    class DefaultImplementationTests {

        /**
         * Test 6: Default visitXxx() implementations are empty (no-op)
         */
        @Test
        @DisplayName("Default visitXxx() implementations should be no-op")
        public void testVisitXxx_defaultImplementation_empty() throws JSQLParserException {
            // Create checker without overriding any visitXxx() methods
            AbstractRuleChecker checker = new TestChecker(null);

            Statement[] statements = {
                    CCJSqlParserUtil.parse("SELECT * FROM users"),
                    CCJSqlParserUtil.parse("UPDATE users SET name = 'foo'"),
                    CCJSqlParserUtil.parse("DELETE FROM users"),
                    CCJSqlParserUtil.parse("INSERT INTO users VALUES (1)")
            };

            SqlCommandType[] types = {
                    SqlCommandType.SELECT,
                    SqlCommandType.UPDATE,
                    SqlCommandType.DELETE,
                    SqlCommandType.INSERT
            };

            String[] mapperIds = {
                    "test.select",
                    "test.update",
                    "test.delete",
                    "test.insert"
            };

            ValidationResult result = ValidationResult.pass();

            // All statements should be processed without errors (default implementations are no-op)
            for (int i = 0; i < statements.length; i++) {
                Statement stmt = statements[i];
                SqlContext context = SqlContext.builder()
                        .sql(stmt.toString())
                        .statementId(mapperIds[i])
                        .type(types[i])
                        .executionLayer(ExecutionLayer.MYBATIS)
                        .statement(stmt)
                        .build();

                assertDoesNotThrow(() -> checker.check(context, result));
            }

            // No violations should be added (default implementations do nothing)
            assertTrue(result.isPassed(), "Default implementations should not add violations");
        }
    }

    @Nested
    @DisplayName("3. Utility Method Tests")
    class UtilityMethodTests {

        /**
         * Test 7: Old utility methods have been removed
         */
        @Test
        @DisplayName("Old utility methods (extractWhere, extractTableName, extractFields) should be removed")
        public void testUtilityMethods_removed() {
            Method[] methods = AbstractRuleChecker.class.getDeclaredMethods();

            // Verify old utility methods don't exist
            for (Method method : methods) {
                String methodName = method.getName();
                assertFalse(methodName.equals("extractWhere"),
                        "extractWhere() should be removed");
                assertFalse(methodName.equals("extractTableName"),
                        "extractTableName() should be removed");
                assertFalse(methodName.equals("extractFields"),
                        "extractFields() should be removed");
                assertFalse(methodName.equals("isDummyCondition"),
                        "isDummyCondition() should be removed");
                assertFalse(methodName.equals("isConstant"),
                        "isConstant() should be removed");
            }

            // Verify FieldExtractorVisitor inner class doesn't exist
            Class<?>[] innerClasses = AbstractRuleChecker.class.getDeclaredClasses();
            for (Class<?> innerClass : innerClasses) {
                assertFalse(innerClass.getSimpleName().equals("FieldExtractorVisitor"),
                        "FieldExtractorVisitor should be removed");
            }
        }
    }

    @Nested
    @DisplayName("4. Error Handling Tests")
    class ErrorHandlingTests {

        /**
         * Test 8: Exceptions in visitXxx() are logged but don't fail validation
         */
        @Test
        @DisplayName("Exceptions in visitXxx() should be caught and logged without failing validation")
        public void testErrorHandling_logsWithoutFailing() throws JSQLParserException {
            // Create checker that throws exception in visitSelect()
            AbstractRuleChecker checker = new TestChecker(null) {
                @Override
                public void visitSelect(Select select, SqlContext context) {
                    throw new RuntimeException("Simulated error");
                }
            };

            Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM users");
            SqlContext context = SqlContext.builder()
                    .sql("SELECT * FROM users")
                    .statementId("test.select")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();

            // check() should not throw exception (degradation pattern)
            assertDoesNotThrow(() -> checker.check(context, result));

            // Validation should pass (no violations added)
            assertTrue(result.isPassed(), "Validation should pass despite checker error");
        }
    }

    @Nested
    @DisplayName("5. Helper Method Tests")
    class HelperMethodTests {

        /**
         * Test 9: addViolation() helper method works correctly
         */
        @Test
        @DisplayName("addViolation() should add violation to current result")
        public void testAddViolation_worksCorrectly() throws JSQLParserException {
            AbstractRuleChecker checker = new TestChecker(null) {
                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    if (update.getWhere() == null) {
                        addViolation(RiskLevel.CRITICAL, "UPDATE without WHERE clause");
                    }
                }
            };

            Statement stmt = CCJSqlParserUtil.parse("UPDATE users SET name = 'foo'");
            SqlContext context = SqlContext.builder()
                    .sql("UPDATE users SET name = 'foo'")
                    .statementId("test.update")
                    .type(SqlCommandType.UPDATE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Violation should be added
            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("UPDATE without WHERE clause"));
        }

        /**
         * Test 9b: addViolation() with suggestion works correctly
         */
        @Test
        @DisplayName("addViolation() with suggestion should add violation with suggestion")
        public void testAddViolation_withSuggestion_worksCorrectly() throws JSQLParserException {
            AbstractRuleChecker checker = new TestChecker(null) {
                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    if (delete.getWhere() == null) {
                        addViolation(RiskLevel.CRITICAL, "DELETE without WHERE clause", "Add WHERE clause to prevent full table deletion");
                    }
                }
            };

            Statement stmt = CCJSqlParserUtil.parse("DELETE FROM users");
            SqlContext context = SqlContext.builder()
                    .sql("DELETE FROM users")
                    .statementId("test.delete")
                    .type(SqlCommandType.DELETE)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Violation should be added with suggestion
            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());
            assertEquals(RiskLevel.CRITICAL, result.getViolations().get(0).getRiskLevel());
            assertEquals("DELETE without WHERE clause", result.getViolations().get(0).getMessage());
            assertEquals("Add WHERE clause to prevent full table deletion", result.getViolations().get(0).getSuggestion());
        }
    }

    @Nested
    @DisplayName("6. ThreadLocal Cleanup Tests")
    class ThreadLocalCleanupTests {

        /**
         * Test 10: ThreadLocal is cleaned up after check()
         */
        @Test
        @DisplayName("ThreadLocal should be cleaned up to prevent memory leaks")
        public void testThreadLocal_cleanedUp() throws JSQLParserException {
            // Track that addViolation works during check() and doesn't work after
            final boolean[] violationAddedDuringCheck = {false};
            final boolean[] getCurrentContextWorkedDuringCheck = {false};
            
            AbstractRuleChecker checker = new TestChecker(null) {
                @Override
                public void visitSelect(Select select, SqlContext context) {
                    // Verify getCurrentContext() works during check()
                    if (getCurrentContext() != null) {
                        getCurrentContextWorkedDuringCheck[0] = true;
                    }
                    // Add a violation to verify addViolation works during check()
                    addViolation(RiskLevel.LOW, "Test violation");
                    violationAddedDuringCheck[0] = true;
                }
            };

            Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM users");
            SqlContext context = SqlContext.builder()
                    .sql("SELECT * FROM users")
                    .statementId("test.select")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            checker.check(context, result);

            // Verify addViolation worked during check()
            assertTrue(violationAddedDuringCheck[0], "Violation should be added during check()");
            assertTrue(getCurrentContextWorkedDuringCheck[0], "getCurrentContext() should work during check()");
            assertFalse(result.isPassed());
            assertEquals(1, result.getViolations().size());

            // After check() completes, ThreadLocal should be cleaned up
            // This test verifies the pattern is implemented correctly
            // (actual memory leak testing requires profiling tools)
            
            // Verify getCurrentContext() returns null after check() completes
            // by calling it from outside the check() context
            assertNull(checker.getCurrentContext(), "getCurrentContext() should return null after check() completes");
            assertNull(checker.getCurrentResult(), "getCurrentResult() should return null after check() completes");
        }

        /**
         * Test 10b: ThreadLocal cleanup happens even when exception occurs
         */
        @Test
        @DisplayName("ThreadLocal should be cleaned up even when exception occurs in visitXxx()")
        public void testThreadLocal_cleanedUpOnException() throws JSQLParserException {
            AbstractRuleChecker checker = new TestChecker(null) {
                @Override
                public void visitSelect(Select select, SqlContext context) {
                    throw new RuntimeException("Simulated error");
                }
            };

            Statement stmt = CCJSqlParserUtil.parse("SELECT * FROM users");
            SqlContext context = SqlContext.builder()
                    .sql("SELECT * FROM users")
                    .statementId("test.select")
                    .type(SqlCommandType.SELECT)
        .executionLayer(ExecutionLayer.MYBATIS)
                    .statement(stmt)
                    .build();

            ValidationResult result = ValidationResult.pass();
            
            // check() should not throw (exception caught internally)
            assertDoesNotThrow(() -> checker.check(context, result));

            // ThreadLocal should still be cleaned up (in finally block)
            assertNull(checker.getCurrentContext(), "getCurrentContext() should return null after exception");
            assertNull(checker.getCurrentResult(), "getCurrentResult() should return null after exception");
        }
    }

    @Nested
    @DisplayName("7. isEnabled Tests")
    class IsEnabledTests {

        /**
         * Test 11: isEnabled() returns config's enabled state
         */
        @Test
        @DisplayName("isEnabled() should return config's enabled state")
        public void testIsEnabled_returnsConfigState() {
            // Enabled config - using a minimal checker that doesn't override isEnabled()
            CheckerConfig enabledConfig = new CheckerConfig(true);
            AbstractRuleChecker enabledChecker = new MinimalTestChecker(enabledConfig);
            assertTrue(enabledChecker.isEnabled(), "isEnabled() should return true when config is enabled");

            // Disabled config
            CheckerConfig disabledConfig = new CheckerConfig(false);
            AbstractRuleChecker disabledChecker = new MinimalTestChecker(disabledConfig);
            assertFalse(disabledChecker.isEnabled(), "isEnabled() should return false when config is disabled");

            // Null config
            AbstractRuleChecker nullConfigChecker = new MinimalTestChecker(null);
            assertFalse(nullConfigChecker.isEnabled(), "isEnabled() should return false when config is null");
        }
    }

    /**
     * Minimal test implementation that does NOT override isEnabled() to test the default behavior.
     */
    private static class MinimalTestChecker extends AbstractRuleChecker {
        public MinimalTestChecker(CheckerConfig config) {
            super(config);
        }
        // Does NOT override isEnabled() - uses AbstractRuleChecker's default implementation
    }

    // ==================== Test Helper Class ====================

    /**
     * Test implementation of AbstractRuleChecker for testing.
     * <p>
     * Made public static to allow access to getCurrentContext() and getCurrentResult()
     * for testing ThreadLocal cleanup.
     * </p>
     */
    private static class TestChecker extends AbstractRuleChecker {
        public TestChecker(CheckerConfig config) {
            super(config);
        }

        @Override
        public boolean isEnabled() {
            return getConfig() == null || getConfig().isEnabled();
        }

        // Expose getCurrentContext for testing
        @Override
        public SqlContext getCurrentContext() {
            return super.getCurrentContext();
        }

        // Expose getCurrentResult for testing
        @Override
        public ValidationResult getCurrentResult() {
            return super.getCurrentResult();
        }
    }
}

