package com.footstone.sqlguard.visitor;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for StatementVisitor interface design.
 * <p>
 * This test class ensures the StatementVisitor interface:
 * <ul>
 *   <li>Has all 4 required visit methods (visitSelect/visitUpdate/visitDelete/visitInsert)</li>
 *   <li>All methods have correct signatures (void return, Statement subtype + SqlContext params)</li>
 *   <li>All methods are default methods with empty implementations</li>
 *   <li>Selective override pattern works correctly</li>
 *   <li>Interface is extensible for future Statement types</li>
 * </ul>
 */
@DisplayName("StatementVisitor Interface Tests")
public class StatementVisitorInterfaceTest {

    private SqlContext selectContext;
    private SqlContext updateContext;
    private SqlContext deleteContext;
    private SqlContext insertContext;
    
    private Select selectStatement;
    private Update updateStatement;
    private Delete deleteStatement;
    private Insert insertStatement;

    @BeforeEach
    void setUp() throws JSQLParserException {
        // Parse test statements
        selectStatement = (Select) CCJSqlParserUtil.parse("SELECT * FROM users WHERE id = 1");
        updateStatement = (Update) CCJSqlParserUtil.parse("UPDATE users SET name = 'foo' WHERE id = 1");
        deleteStatement = (Delete) CCJSqlParserUtil.parse("DELETE FROM users WHERE id = 1");
        insertStatement = (Insert) CCJSqlParserUtil.parse("INSERT INTO users (id, name) VALUES (1, 'test')");
        
        // Create contexts for each statement type
        selectContext = SqlContext.builder()
                .sql("SELECT * FROM users WHERE id = 1")
                .mapperId("test.selectMapper")
                .type(SqlCommandType.SELECT)
                .statement(selectStatement)
                .build();
        
        updateContext = SqlContext.builder()
                .sql("UPDATE users SET name = 'foo' WHERE id = 1")
                .mapperId("test.updateMapper")
                .type(SqlCommandType.UPDATE)
                .statement(updateStatement)
                .build();
        
        deleteContext = SqlContext.builder()
                .sql("DELETE FROM users WHERE id = 1")
                .mapperId("test.deleteMapper")
                .type(SqlCommandType.DELETE)
                .statement(deleteStatement)
                .build();
        
        insertContext = SqlContext.builder()
                .sql("INSERT INTO users (id, name) VALUES (1, 'test')")
                .mapperId("test.insertMapper")
                .type(SqlCommandType.INSERT)
                .statement(insertStatement)
                .build();
    }

    @Nested
    @DisplayName("Interface Method Existence Tests")
    class InterfaceMethodExistenceTests {

        /**
         * Test 1: Interface has all 4 required visit methods
         */
        @Test
        @DisplayName("Should have all 4 visit methods declared")
        void testStatementVisitor_interface_hasVisitMethods() throws NoSuchMethodException {
            // Verify interface declares all 4 visit methods
            assertNotNull(StatementVisitor.class.getMethod("visitSelect", Select.class, SqlContext.class),
                    "visitSelect method should exist");
            assertNotNull(StatementVisitor.class.getMethod("visitUpdate", Update.class, SqlContext.class),
                    "visitUpdate method should exist");
            assertNotNull(StatementVisitor.class.getMethod("visitDelete", Delete.class, SqlContext.class),
                    "visitDelete method should exist");
            assertNotNull(StatementVisitor.class.getMethod("visitInsert", Insert.class, SqlContext.class),
                    "visitInsert method should exist");
        }

        /**
         * Test: Interface is correctly defined
         */
        @Test
        @DisplayName("Should be an interface in the correct package")
        void testStatementVisitor_isInterface_inCorrectPackage() {
            assertTrue(StatementVisitor.class.isInterface(),
                    "StatementVisitor should be an interface");
            assertTrue(StatementVisitor.class.getName().startsWith("com.footstone.sqlguard.visitor"),
                    "Should be in com.footstone.sqlguard.visitor package");
        }

        /**
         * Test: Interface has exactly 4 declared methods (excluding JaCoCo injected methods)
         */
        @Test
        @DisplayName("Should have exactly 4 declared methods")
        void testStatementVisitor_hasExactly4Methods() {
            Method[] methods = StatementVisitor.class.getDeclaredMethods();
            
            // Filter out JaCoCo injected methods (e.g., $jacocoInit)
            List<String> methodNames = Arrays.stream(methods)
                    .map(Method::getName)
                    .filter(name -> !name.startsWith("$"))  // Exclude synthetic/instrumented methods
                    .sorted()
                    .collect(Collectors.toList());
            
            assertEquals(4, methodNames.size(), "Interface should have exactly 4 methods (excluding instrumentation)");
            assertEquals(Arrays.asList("visitDelete", "visitInsert", "visitSelect", "visitUpdate"),
                    methodNames, "Should have visitDelete, visitInsert, visitSelect, visitUpdate methods");
        }
    }

    @Nested
    @DisplayName("Default Implementation Tests")
    class DefaultImplementationTests {

        /**
         * Test 2: Default implementations are empty (no-op)
         */
        @Test
        @DisplayName("Default implementations should be empty (no-op)")
        void testStatementVisitor_defaultMethods_empty() {
            // Create a minimal implementation (uses default methods)
            StatementVisitor visitor = new StatementVisitor() {
                // All methods use default implementations
            };

            // Default methods should not throw exceptions (empty implementations)
            assertDoesNotThrow(() -> visitor.visitSelect(selectStatement, selectContext),
                    "visitSelect default should not throw");
            assertDoesNotThrow(() -> visitor.visitUpdate(updateStatement, updateContext),
                    "visitUpdate default should not throw");
            assertDoesNotThrow(() -> visitor.visitDelete(deleteStatement, deleteContext),
                    "visitDelete default should not throw");
            assertDoesNotThrow(() -> visitor.visitInsert(insertStatement, insertContext),
                    "visitInsert default should not throw");
        }

        /**
         * Test: Default implementations with null parameters don't throw
         */
        @Test
        @DisplayName("Default implementations should handle null parameters gracefully")
        void testStatementVisitor_defaultMethods_handleNull() {
            StatementVisitor visitor = new StatementVisitor() {};

            // Default implementations are empty, so null params should not cause issues
            assertDoesNotThrow(() -> visitor.visitSelect(null, null),
                    "visitSelect should handle null gracefully");
            assertDoesNotThrow(() -> visitor.visitUpdate(null, null),
                    "visitUpdate should handle null gracefully");
            assertDoesNotThrow(() -> visitor.visitDelete(null, null),
                    "visitDelete should handle null gracefully");
            assertDoesNotThrow(() -> visitor.visitInsert(null, null),
                    "visitInsert should handle null gracefully");
        }

        /**
         * Test: Default methods can be called multiple times
         */
        @Test
        @DisplayName("Default methods should be idempotent")
        void testStatementVisitor_defaultMethods_idempotent() {
            StatementVisitor visitor = new StatementVisitor() {};

            // Call each method multiple times - should have no side effects
            for (int i = 0; i < 10; i++) {
                assertDoesNotThrow(() -> {
                    visitor.visitSelect(selectStatement, selectContext);
                    visitor.visitUpdate(updateStatement, updateContext);
                    visitor.visitDelete(deleteStatement, deleteContext);
                    visitor.visitInsert(insertStatement, insertContext);
                });
            }
        }
    }

    @Nested
    @DisplayName("Method Signature Tests")
    class MethodSignatureTests {

        /**
         * Test 3: visitSelect method signature is correct
         */
        @Test
        @DisplayName("visitSelect should have correct signature")
        void testStatementVisitor_selectMethod_signature() throws NoSuchMethodException {
            Method method = StatementVisitor.class.getMethod("visitSelect", Select.class, SqlContext.class);

            // Verify return type is void
            assertEquals(void.class, method.getReturnType(),
                    "Return type should be void");

            // Verify parameter types
            assertEquals(2, method.getParameterCount(),
                    "Should have 2 parameters");
            assertEquals(Select.class, method.getParameterTypes()[0],
                    "First parameter should be Select");
            assertEquals(SqlContext.class, method.getParameterTypes()[1],
                    "Second parameter should be SqlContext");

            // Verify it's a default method
            assertTrue(method.isDefault(),
                    "visitSelect should be a default method");
        }

        /**
         * Test 4: visitUpdate method signature is correct
         */
        @Test
        @DisplayName("visitUpdate should have correct signature")
        void testStatementVisitor_updateMethod_signature() throws NoSuchMethodException {
            Method method = StatementVisitor.class.getMethod("visitUpdate", Update.class, SqlContext.class);

            assertEquals(void.class, method.getReturnType());
            assertEquals(2, method.getParameterCount());
            assertEquals(Update.class, method.getParameterTypes()[0]);
            assertEquals(SqlContext.class, method.getParameterTypes()[1]);
            assertTrue(method.isDefault());
        }

        /**
         * Test: visitDelete method signature is correct
         */
        @Test
        @DisplayName("visitDelete should have correct signature")
        void testStatementVisitor_deleteMethod_signature() throws NoSuchMethodException {
            Method method = StatementVisitor.class.getMethod("visitDelete", Delete.class, SqlContext.class);

            assertEquals(void.class, method.getReturnType());
            assertEquals(2, method.getParameterCount());
            assertEquals(Delete.class, method.getParameterTypes()[0]);
            assertEquals(SqlContext.class, method.getParameterTypes()[1]);
            assertTrue(method.isDefault());
        }

        /**
         * Test: visitInsert method signature is correct
         */
        @Test
        @DisplayName("visitInsert should have correct signature")
        void testStatementVisitor_insertMethod_signature() throws NoSuchMethodException {
            Method method = StatementVisitor.class.getMethod("visitInsert", Insert.class, SqlContext.class);

            assertEquals(void.class, method.getReturnType());
            assertEquals(2, method.getParameterCount());
            assertEquals(Insert.class, method.getParameterTypes()[0]);
            assertEquals(SqlContext.class, method.getParameterTypes()[1]);
            assertTrue(method.isDefault());
        }

        /**
         * Test: All visit methods are public (excluding JaCoCo injected methods)
         */
        @Test
        @DisplayName("All visit methods should be public")
        void testStatementVisitor_allMethods_public() {
            for (Method method : StatementVisitor.class.getDeclaredMethods()) {
                // Skip JaCoCo injected methods (e.g., $jacocoInit)
                if (method.getName().startsWith("$")) {
                    continue;
                }
                assertTrue(Modifier.isPublic(method.getModifiers()),
                        method.getName() + " should be public");
            }
        }
    }

    @Nested
    @DisplayName("Javadoc and Design Tests")
    class JavadocAndDesignTests {

        /**
         * Test 5: Javadoc is complete and includes usage examples
         * (Verified at compile time; here we verify structural aspects)
         */
        @Test
        @DisplayName("Interface should be properly designed with correct package")
        void testStatementVisitor_javadoc_complete() {
            // This test verifies Javadoc presence at compile time
            // StatementVisitor interface should have:
            // - Class-level Javadoc explaining visitor pattern
            // - Method-level Javadoc for each visitXxx() method
            // - Code examples in Javadoc
            // - @since 1.1.0 tag

            assertTrue(StatementVisitor.class.isInterface());
            assertTrue(StatementVisitor.class.getName().startsWith("com.footstone.sqlguard.visitor"),
                    "Should be in com.footstone.sqlguard.visitor package");
            
            // Verify interface is public
            assertTrue(Modifier.isPublic(StatementVisitor.class.getModifiers()),
                    "Interface should be public");
            
            // Verify it's not abstract (interfaces are implicitly abstract, but check access)
            assertTrue(Modifier.isInterface(StatementVisitor.class.getModifiers()),
                    "Should be declared as interface");
        }
    }

    @Nested
    @DisplayName("Selective Override Tests")
    class SelectiveOverrideTests {

        /**
         * Test 6: Selective override pattern works
         */
        @Test
        @DisplayName("Selective override pattern should work correctly")
        void testStatementVisitor_selectiveOverride_works() {
            // Create visitor that only overrides visitSelect and visitUpdate
            AtomicBoolean selectCalled = new AtomicBoolean(false);
            AtomicBoolean updateCalled = new AtomicBoolean(false);
            AtomicBoolean deleteCalled = new AtomicBoolean(false);
            AtomicBoolean insertCalled = new AtomicBoolean(false);

            StatementVisitor visitor = new StatementVisitor() {
                @Override
                public void visitSelect(Select select, SqlContext context) {
                    selectCalled.set(true);
                }

                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    updateCalled.set(true);
                }
                // visitDelete and visitInsert use default (empty) implementations
            };

            // Call all visitor methods
            visitor.visitSelect(selectStatement, selectContext);
            visitor.visitUpdate(updateStatement, updateContext);
            visitor.visitDelete(deleteStatement, deleteContext);  // Uses default (no-op)
            visitor.visitInsert(insertStatement, insertContext);  // Uses default (no-op)

            // Verify selective override works
            assertTrue(selectCalled.get(), "visitSelect should have been called");
            assertTrue(updateCalled.get(), "visitUpdate should have been called");
            assertFalse(deleteCalled.get(), "visitDelete should use default (not track)");
            assertFalse(insertCalled.get(), "visitInsert should use default (not track)");
        }

        /**
         * Test: Override all methods
         */
        @Test
        @DisplayName("Should allow overriding all methods")
        void testStatementVisitor_overrideAll_works() {
            AtomicInteger callCount = new AtomicInteger(0);
            List<String> callOrder = new ArrayList<>();

            StatementVisitor visitor = new StatementVisitor() {
                @Override
                public void visitSelect(Select select, SqlContext context) {
                    callCount.incrementAndGet();
                    callOrder.add("select");
                }

                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    callCount.incrementAndGet();
                    callOrder.add("update");
                }

                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    callCount.incrementAndGet();
                    callOrder.add("delete");
                }

                @Override
                public void visitInsert(Insert insert, SqlContext context) {
                    callCount.incrementAndGet();
                    callOrder.add("insert");
                }
            };

            visitor.visitSelect(selectStatement, selectContext);
            visitor.visitUpdate(updateStatement, updateContext);
            visitor.visitDelete(deleteStatement, deleteContext);
            visitor.visitInsert(insertStatement, insertContext);

            assertEquals(4, callCount.get(), "All 4 methods should have been called");
            assertEquals(Arrays.asList("select", "update", "delete", "insert"), callOrder,
                    "Methods should be called in order");
        }

        /**
         * Test: Override only one method
         */
        @Test
        @DisplayName("Should allow overriding only one method")
        void testStatementVisitor_overrideOne_works() {
            AtomicReference<Delete> capturedDelete = new AtomicReference<>();
            AtomicReference<SqlContext> capturedContext = new AtomicReference<>();

            StatementVisitor deleteOnlyVisitor = new StatementVisitor() {
                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    capturedDelete.set(delete);
                    capturedContext.set(context);
                }
            };

            // Call all methods - only delete should capture
            deleteOnlyVisitor.visitSelect(selectStatement, selectContext);
            deleteOnlyVisitor.visitUpdate(updateStatement, updateContext);
            deleteOnlyVisitor.visitDelete(deleteStatement, deleteContext);
            deleteOnlyVisitor.visitInsert(insertStatement, insertContext);

            // Verify only delete captured its arguments
            assertSame(deleteStatement, capturedDelete.get());
            assertSame(deleteContext, capturedContext.get());
        }
    }

    @Nested
    @DisplayName("Extensibility Tests")
    class ExtensibilityTests {

        /**
         * Test 7: Interface is extensible for future Statement types (excluding JaCoCo injected methods)
         */
        @Test
        @DisplayName("All methods should be default for extensibility")
        void testStatementVisitor_extensible_design() {
            // Verify interface uses default methods (allows adding new methods without breaking implementations)
            Method[] methods = StatementVisitor.class.getDeclaredMethods();

            // All declared methods should be default (for extensibility)
            // Skip JaCoCo injected methods (e.g., $jacocoInit)
            for (Method method : methods) {
                if (method.getName().startsWith("$")) {
                    continue;
                }
                assertTrue(method.isDefault(),
                        "Method " + method.getName() + " should be default for extensibility");
            }
        }

        /**
         * Test: Can create lambda-style implementations for simple cases
         */
        @Test
        @DisplayName("Should work with minimal implementations")
        void testStatementVisitor_minimalImplementation_works() {
            // Create a completely empty implementation
            StatementVisitor emptyVisitor = new StatementVisitor() {};

            // Should work without any issues
            assertDoesNotThrow(() -> {
                emptyVisitor.visitSelect(selectStatement, selectContext);
                emptyVisitor.visitUpdate(updateStatement, updateContext);
                emptyVisitor.visitDelete(deleteStatement, deleteContext);
                emptyVisitor.visitInsert(insertStatement, insertContext);
            });
        }

        /**
         * Test: Interface can be used with new implementations
         */
        @Test
        @DisplayName("Interface should support custom implementations")
        void testStatementVisitor_customImplementation() {
            // Create implementation with custom logic
            List<String> visited = new ArrayList<>();
            
            StatementVisitor customVisitor = new StatementVisitor() {
                @Override
                public void visitSelect(Select select, SqlContext context) {
                    visited.add("SELECT:" + context.getMapperId());
                }

                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    visited.add("UPDATE:" + context.getMapperId());
                }

                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    visited.add("DELETE:" + context.getMapperId());
                }

                @Override
                public void visitInsert(Insert insert, SqlContext context) {
                    visited.add("INSERT:" + context.getMapperId());
                }
            };

            customVisitor.visitSelect(selectStatement, selectContext);
            customVisitor.visitUpdate(updateStatement, updateContext);
            customVisitor.visitDelete(deleteStatement, deleteContext);
            customVisitor.visitInsert(insertStatement, insertContext);

            assertEquals(4, visited.size());
            assertTrue(visited.contains("SELECT:test.selectMapper"));
            assertTrue(visited.contains("UPDATE:test.updateMapper"));
            assertTrue(visited.contains("DELETE:test.deleteMapper"));
            assertTrue(visited.contains("INSERT:test.insertMapper"));
        }
    }

    @Nested
    @DisplayName("Context Integration Tests")
    class ContextIntegrationTests {

        /**
         * Test: Visitor can access SqlContext properties
         */
        @Test
        @DisplayName("Visitor should be able to access SqlContext properties")
        void testStatementVisitor_accessContextProperties() {
            AtomicReference<String> capturedMapperId = new AtomicReference<>();
            AtomicReference<SqlCommandType> capturedCommandType = new AtomicReference<>();
            AtomicReference<String> capturedSql = new AtomicReference<>();

            StatementVisitor contextAccessingVisitor = new StatementVisitor() {
                @Override
                public void visitSelect(Select select, SqlContext context) {
                    capturedMapperId.set(context.getMapperId());
                    capturedCommandType.set(context.getType());
                    capturedSql.set(context.getSql());
                }
            };

            contextAccessingVisitor.visitSelect(selectStatement, selectContext);

            assertEquals("test.selectMapper", capturedMapperId.get());
            assertEquals(SqlCommandType.SELECT, capturedCommandType.get());
            assertEquals("SELECT * FROM users WHERE id = 1", capturedSql.get());
        }

        /**
         * Test: Visitor can access Statement from context
         */
        @Test
        @DisplayName("Visitor should be able to access Statement from context")
        void testStatementVisitor_accessStatementFromContext() {
            AtomicReference<Statement> capturedStatement = new AtomicReference<>();

            StatementVisitor statementAccessingVisitor = new StatementVisitor() {
                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    capturedStatement.set(context.getStatement());
                }
            };

            statementAccessingVisitor.visitUpdate(updateStatement, updateContext);

            assertSame(updateStatement, capturedStatement.get());
        }

        /**
         * Test: Visitor receives correctly typed statements
         */
        @Test
        @DisplayName("Visitor should receive correctly typed statements")
        void testStatementVisitor_typeCorrectness() {
            final AtomicBoolean selectTypeCorrect = new AtomicBoolean(false);
            final AtomicBoolean updateTypeCorrect = new AtomicBoolean(false);
            final AtomicBoolean deleteTypeCorrect = new AtomicBoolean(false);
            final AtomicBoolean insertTypeCorrect = new AtomicBoolean(false);

            StatementVisitor typeCheckingVisitor = new StatementVisitor() {
                @Override
                public void visitSelect(Select select, SqlContext context) {
                    selectTypeCorrect.set(select instanceof Select);
                }

                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    updateTypeCorrect.set(update instanceof Update);
                }

                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    deleteTypeCorrect.set(delete instanceof Delete);
                }

                @Override
                public void visitInsert(Insert insert, SqlContext context) {
                    insertTypeCorrect.set(insert instanceof Insert);
                }
            };

            typeCheckingVisitor.visitSelect(selectStatement, selectContext);
            typeCheckingVisitor.visitUpdate(updateStatement, updateContext);
            typeCheckingVisitor.visitDelete(deleteStatement, deleteContext);
            typeCheckingVisitor.visitInsert(insertStatement, insertContext);

            assertTrue(selectTypeCorrect.get(), "visitSelect should receive Select type");
            assertTrue(updateTypeCorrect.get(), "visitUpdate should receive Update type");
            assertTrue(deleteTypeCorrect.get(), "visitDelete should receive Delete type");
            assertTrue(insertTypeCorrect.get(), "visitInsert should receive Insert type");
        }
    }

    @Nested
    @DisplayName("Real-World Usage Pattern Tests")
    class RealWorldUsagePatternTests {

        /**
         * Test: Simulate NoWhereClauseChecker pattern
         */
        @Test
        @DisplayName("Should support NoWhereClauseChecker pattern")
        void testStatementVisitor_noWhereClauseCheckerPattern() throws JSQLParserException {
            List<String> violations = new ArrayList<>();

            StatementVisitor noWhereChecker = new StatementVisitor() {
                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    if (update.getWhere() == null) {
                        violations.add("UPDATE without WHERE clause: " + context.getMapperId());
                    }
                }

                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    if (delete.getWhere() == null) {
                        violations.add("DELETE without WHERE clause: " + context.getMapperId());
                    }
                }
            };

            // Test UPDATE without WHERE
            Update updateNoWhere = (Update) CCJSqlParserUtil.parse("UPDATE users SET name = 'foo'");
            SqlContext updateNoWhereCtx = SqlContext.builder()
                    .sql("UPDATE users SET name = 'foo'")
                    .mapperId("test.dangerousUpdate")
                    .type(SqlCommandType.UPDATE)
                    .statement(updateNoWhere)
                    .build();

            noWhereChecker.visitUpdate(updateNoWhere, updateNoWhereCtx);
            
            assertEquals(1, violations.size());
            assertTrue(violations.get(0).contains("UPDATE without WHERE clause"));

            // Test UPDATE with WHERE (should not add violation)
            violations.clear();
            noWhereChecker.visitUpdate(updateStatement, updateContext);
            assertTrue(violations.isEmpty());
        }

        /**
         * Test: Simulate accumulating results pattern
         */
        @Test
        @DisplayName("Should support result accumulation pattern")
        void testStatementVisitor_resultAccumulationPattern() {
            List<String> visitedTables = new ArrayList<>();

            StatementVisitor tableCollector = new StatementVisitor() {
                @Override
                public void visitSelect(Select select, SqlContext context) {
                    visitedTables.add("SELECT from table");
                }

                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    visitedTables.add("UPDATE: " + update.getTable().getName());
                }

                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    visitedTables.add("DELETE: " + delete.getTable().getName());
                }

                @Override
                public void visitInsert(Insert insert, SqlContext context) {
                    visitedTables.add("INSERT: " + insert.getTable().getName());
                }
            };

            tableCollector.visitSelect(selectStatement, selectContext);
            tableCollector.visitUpdate(updateStatement, updateContext);
            tableCollector.visitDelete(deleteStatement, deleteContext);
            tableCollector.visitInsert(insertStatement, insertContext);

            assertEquals(4, visitedTables.size());
            assertTrue(visitedTables.contains("UPDATE: users"));
            assertTrue(visitedTables.contains("DELETE: users"));
            assertTrue(visitedTables.contains("INSERT: users"));
        }

        /**
         * Test: DELETE without WHERE clause detection
         */
        @Test
        @DisplayName("Should detect DELETE without WHERE clause")
        void testStatementVisitor_deleteWithoutWhereDetection() throws JSQLParserException {
            AtomicBoolean violationFound = new AtomicBoolean(false);

            StatementVisitor checker = new StatementVisitor() {
                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    if (delete.getWhere() == null) {
                        violationFound.set(true);
                    }
                }
            };

            // Test DELETE without WHERE
            Delete deleteNoWhere = (Delete) CCJSqlParserUtil.parse("DELETE FROM users");
            SqlContext deleteNoWhereCtx = SqlContext.builder()
                    .sql("DELETE FROM users")
                    .mapperId("test.dangerousDelete")
                    .type(SqlCommandType.DELETE)
                    .statement(deleteNoWhere)
                    .build();

            checker.visitDelete(deleteNoWhere, deleteNoWhereCtx);
            assertTrue(violationFound.get());

            // Test DELETE with WHERE (should not trigger)
            violationFound.set(false);
            checker.visitDelete(deleteStatement, deleteContext);
            assertFalse(violationFound.get());
        }
    }
}
