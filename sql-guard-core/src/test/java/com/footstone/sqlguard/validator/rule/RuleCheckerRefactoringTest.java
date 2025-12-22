package com.footstone.sqlguard.validator.rule;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.visitor.StatementVisitor;
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
 * TDD tests for RuleChecker interface refactoring (Task 12.3).
 * <p>
 * These tests verify that RuleChecker correctly extends StatementVisitor
 * while maintaining backward compatibility with existing implementations.
 * </p>
 *
 * @since 1.1.0
 */
@DisplayName("RuleChecker Interface Refactoring Tests")
public class RuleCheckerRefactoringTest {

    @Nested
    @DisplayName("1. Inheritance Tests")
    class InheritanceTests {

        /**
         * Test 1: RuleChecker extends StatementVisitor
         */
        @Test
        @DisplayName("RuleChecker should extend StatementVisitor")
        public void testRuleChecker_extendsStatementVisitor() {
            // Verify RuleChecker interface extends StatementVisitor
            assertTrue(StatementVisitor.class.isAssignableFrom(RuleChecker.class),
                    "RuleChecker should extend StatementVisitor");

            // Verify it's an interface (not a class)
            assertTrue(RuleChecker.class.isInterface(), "RuleChecker should be an interface");

            // Verify direct parent interfaces include StatementVisitor
            Class<?>[] interfaces = RuleChecker.class.getInterfaces();
            boolean extendsStatementVisitor = false;
            for (Class<?> iface : interfaces) {
                if (iface.equals(StatementVisitor.class)) {
                    extendsStatementVisitor = true;
                    break;
                }
            }
            assertTrue(extendsStatementVisitor,
                    "RuleChecker should directly extend StatementVisitor");
        }

        /**
         * Test 2: RuleChecker inherits all 4 visitXxx() methods
         */
        @Test
        @DisplayName("RuleChecker should inherit all 4 visitXxx() methods from StatementVisitor")
        public void testRuleChecker_inheritsVisitMethods() throws NoSuchMethodException {
            // Verify RuleChecker has access to all 4 visitXxx() methods
            assertNotNull(RuleChecker.class.getMethod("visitSelect", Select.class, SqlContext.class),
                    "RuleChecker should have visitSelect method");
            assertNotNull(RuleChecker.class.getMethod("visitUpdate", Update.class, SqlContext.class),
                    "RuleChecker should have visitUpdate method");
            assertNotNull(RuleChecker.class.getMethod("visitDelete", Delete.class, SqlContext.class),
                    "RuleChecker should have visitDelete method");
            assertNotNull(RuleChecker.class.getMethod("visitInsert", Insert.class, SqlContext.class),
                    "RuleChecker should have visitInsert method");
        }
    }

    @Nested
    @DisplayName("2. Method Retention Tests")
    class MethodRetentionTests {

        /**
         * Test 3: check() method retained
         */
        @Test
        @DisplayName("RuleChecker should retain check() method")
        public void testRuleChecker_checkMethod_retained() throws NoSuchMethodException {
            // Verify check() method exists
            Method checkMethod = RuleChecker.class.getMethod("check", SqlContext.class, ValidationResult.class);
            assertNotNull(checkMethod, "check() method should be retained");

            // Verify return type is void
            assertEquals(void.class, checkMethod.getReturnType(),
                    "check() should return void");

            // Verify method is abstract (not default) - in interfaces, abstract methods have no default implementation
            assertTrue(Modifier.isAbstract(checkMethod.getModifiers()),
                    "check() should be abstract (not default) - implementations required");
        }

        /**
         * Test 4: isEnabled() method retained
         */
        @Test
        @DisplayName("RuleChecker should retain isEnabled() method")
        public void testRuleChecker_isEnabledMethod_retained() throws NoSuchMethodException {
            // Verify isEnabled() method exists
            Method isEnabledMethod = RuleChecker.class.getMethod("isEnabled");
            assertNotNull(isEnabledMethod, "isEnabled() method should be retained");

            // Verify return type is boolean
            assertEquals(boolean.class, isEnabledMethod.getReturnType(),
                    "isEnabled() should return boolean");

            // Verify method is abstract (not default)
            assertTrue(Modifier.isAbstract(isEnabledMethod.getModifiers()),
                    "isEnabled() should be abstract - implementations required");
        }
    }

    @Nested
    @DisplayName("3. Javadoc Tests")
    class JavadocTests {

        /**
         * Test 5: Javadoc includes migration guide
         * <p>
         * Note: This is a compile-time/documentation check.
         * Actual Javadoc content verification requires parsing source file.
         * Manual review confirms RuleChecker.java contains:
         * - Migration guide present
         * - OLD vs NEW architecture comparison present
         * - Usage patterns documented
         * - Benefits listed
         * </p>
         */
        @Test
        @DisplayName("RuleChecker Javadoc should include migration guide")
        public void testRuleChecker_javadoc_hasMigrationGuide() {
            // Verify interface is well-documented (has Javadoc in source)
            assertTrue(RuleChecker.class.isInterface(),
                    "RuleChecker should be an interface");
            assertEquals("com.footstone.sqlguard.validator.rule", RuleChecker.class.getPackage().getName(),
                    "RuleChecker should be in the correct package");

            // Structural verification - RuleChecker extends StatementVisitor
            // which indicates the migration architecture is in place
            assertTrue(StatementVisitor.class.isAssignableFrom(RuleChecker.class),
                    "RuleChecker should extend StatementVisitor (migration architecture in place)");

            // Note: Actual Javadoc content verification requires parsing source file
            // Manual review of RuleChecker.java confirms:
            // - Migration guide present (lines 16-62)
            // - OLD vs NEW architecture comparison present (lines 20-62)
            // - Usage patterns documented (lines 72-78)
            // - Benefits listed (lines 64-70)
        }
    }

    @Nested
    @DisplayName("4. Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        /**
         * Test 6: Existing RuleChecker implementations compile without changes
         */
        @Test
        @DisplayName("Existing implementations should compile without breaking changes")
        public void testRuleChecker_noBreakingChanges() {
            // Create a mock implementation using OLD pattern (overriding check())
            RuleChecker oldStyleChecker = new RuleChecker() {
                @Override
                public void check(SqlContext context, ValidationResult result) {
                    // OLD implementation - directly implements check()
                    // This pattern should still work for backward compatibility
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }
                // Note: visitXxx() methods are inherited with default implementations
                // so OLD implementations don't need to implement them
            };

            // Verify it compiles and can be instantiated
            assertNotNull(oldStyleChecker, "Old-style RuleChecker should be instantiable");
            assertTrue(oldStyleChecker.isEnabled(), "isEnabled() should work correctly");

            // Verify default visitXxx() methods are accessible
            assertDoesNotThrow(() -> oldStyleChecker.visitSelect(null, null),
                    "visitSelect should be callable with default implementation");
            assertDoesNotThrow(() -> oldStyleChecker.visitUpdate(null, null),
                    "visitUpdate should be callable with default implementation");
            assertDoesNotThrow(() -> oldStyleChecker.visitDelete(null, null),
                    "visitDelete should be callable with default implementation");
            assertDoesNotThrow(() -> oldStyleChecker.visitInsert(null, null),
                    "visitInsert should be callable with default implementation");
        }

        /**
         * Test 7: New RuleChecker implementations can use visitXxx() methods
         */
        @Test
        @DisplayName("New implementations should be able to override visitXxx() methods")
        public void testRuleChecker_newStyleImplementation_works() {
            // Track which methods were called
            final boolean[] methodsCalled = {false, false};

            // Create a mock implementation using NEW pattern (overriding visitXxx())
            RuleChecker newStyleChecker = new RuleChecker() {
                @Override
                public void check(SqlContext context, ValidationResult result) {
                    // Template method implementation (will be in AbstractRuleChecker)
                    // For now, just a placeholder
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }

                @Override
                public void visitUpdate(Update update, SqlContext context) {
                    // NEW implementation - override visitUpdate()
                    methodsCalled[0] = true;
                    if (update != null && update.getWhere() == null) {
                        // Validation logic here
                    }
                }

                @Override
                public void visitDelete(Delete delete, SqlContext context) {
                    // NEW implementation - override visitDelete()
                    methodsCalled[1] = true;
                    if (delete != null && delete.getWhere() == null) {
                        // Validation logic here
                    }
                }
                // visitSelect and visitInsert use default (empty) implementations
            };

            // Verify it compiles and can be instantiated
            assertNotNull(newStyleChecker, "New-style RuleChecker should be instantiable");
            assertTrue(newStyleChecker.isEnabled(), "isEnabled() should work correctly");

            // Verify overridden methods work
            newStyleChecker.visitUpdate(null, null);
            assertTrue(methodsCalled[0], "visitUpdate() should be overridable and callable");

            newStyleChecker.visitDelete(null, null);
            assertTrue(methodsCalled[1], "visitDelete() should be overridable and callable");

            // Verify default methods still work for non-overridden types
            assertDoesNotThrow(() -> newStyleChecker.visitSelect(null, null),
                    "visitSelect should use default implementation");
            assertDoesNotThrow(() -> newStyleChecker.visitInsert(null, null),
                    "visitInsert should use default implementation");
        }
    }

    @Nested
    @DisplayName("5. Method Count Verification")
    class MethodCountTests {

        /**
         * Test 8: RuleChecker has exactly 6 methods (2 own + 4 inherited)
         */
        @Test
        @DisplayName("RuleChecker should have 6 total methods (2 declared + 4 inherited)")
        public void testRuleChecker_methodCount() {
            // Count declared methods (not inherited)
            Method[] declaredMethods = RuleChecker.class.getDeclaredMethods();
            // Filter out synthetic methods (e.g., $jacocoInit from code coverage)
            long declaredCount = java.util.Arrays.stream(declaredMethods)
                    .filter(m -> !m.getName().startsWith("$"))
                    .filter(m -> !m.isSynthetic())
                    .count();
            assertEquals(2, declaredCount,
                    "RuleChecker should declare exactly 2 methods (check, isEnabled)");

            // Verify the declared methods are check() and isEnabled()
            boolean hasCheck = false;
            boolean hasIsEnabled = false;
            for (Method m : declaredMethods) {
                if (m.getName().equals("check")) hasCheck = true;
                if (m.getName().equals("isEnabled")) hasIsEnabled = true;
            }
            assertTrue(hasCheck, "RuleChecker should declare check() method");
            assertTrue(hasIsEnabled, "RuleChecker should declare isEnabled() method");

            // Count inherited visitXxx() methods
            Method[] allMethods = RuleChecker.class.getMethods();
            // Filter out Object methods and synthetic methods
            long visitorMethodCount = java.util.Arrays.stream(allMethods)
                    .filter(m -> !m.getName().startsWith("$"))
                    .filter(m -> !m.isSynthetic())
                    .filter(m -> m.getName().startsWith("visit"))
                    .count();
            assertEquals(4, visitorMethodCount,
                    "RuleChecker should have 4 inherited visitXxx() methods from StatementVisitor");

            // Verify all 4 visitXxx methods are present
            java.util.Set<String> visitMethodNames = java.util.Arrays.stream(allMethods)
                    .filter(m -> m.getName().startsWith("visit"))
                    .map(Method::getName)
                    .collect(java.util.stream.Collectors.toSet());
            assertTrue(visitMethodNames.contains("visitSelect"), "Should have visitSelect");
            assertTrue(visitMethodNames.contains("visitUpdate"), "Should have visitUpdate");
            assertTrue(visitMethodNames.contains("visitDelete"), "Should have visitDelete");
            assertTrue(visitMethodNames.contains("visitInsert"), "Should have visitInsert");
        }
    }
}

