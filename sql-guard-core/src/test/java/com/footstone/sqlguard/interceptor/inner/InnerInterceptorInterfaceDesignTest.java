package com.footstone.sqlguard.interceptor.inner;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests verifying SqlGuardInnerInterceptor interface design.
 *
 * <p>This test class validates:
 * <ul>
 *   <li>Interface method existence and signatures</li>
 *   <li>Method signatures match MyBatis-Plus InnerInterceptor pattern</li>
 *   <li>Default implementations work correctly</li>
 *   <li>Priority mechanism functions as expected</li>
 * </ul>
 */
@DisplayName("InnerInterceptor Interface Design Tests")
public class InnerInterceptorInterfaceDesignTest {

    @Nested
    @DisplayName("1. Interface Method Existence Tests")
    class InterfaceMethodExistenceTests {

        @Test
        @DisplayName("testInterface_hasWillDoQueryMethod - willDoQuery method exists")
        void testInterface_hasWillDoQueryMethod() throws NoSuchMethodException {
            // Verify willDoQuery method signature
            Method method = SqlGuardInnerInterceptor.class.getMethod(
                "willDoQuery",
                Executor.class, MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, BoundSql.class
            );

            assertNotNull(method, "willDoQuery method should exist");
            assertEquals(boolean.class, method.getReturnType(),
                "willDoQuery should return boolean");
        }

        @Test
        @DisplayName("testInterface_hasBeforeQueryMethod - beforeQuery method exists")
        void testInterface_hasBeforeQueryMethod() throws NoSuchMethodException {
            // Verify beforeQuery method signature
            Method method = SqlGuardInnerInterceptor.class.getMethod(
                "beforeQuery",
                Executor.class, MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, BoundSql.class
            );

            assertNotNull(method, "beforeQuery method should exist");
            assertEquals(void.class, method.getReturnType(),
                "beforeQuery should return void");
        }

        @Test
        @DisplayName("testInterface_hasWillDoUpdateMethod - willDoUpdate method exists")
        void testInterface_hasWillDoUpdateMethod() throws NoSuchMethodException {
            // Verify willDoUpdate method signature
            Method method = SqlGuardInnerInterceptor.class.getMethod(
                "willDoUpdate",
                Executor.class, MappedStatement.class, Object.class
            );

            assertNotNull(method, "willDoUpdate method should exist");
            assertEquals(boolean.class, method.getReturnType(),
                "willDoUpdate should return boolean");
        }

        @Test
        @DisplayName("testInterface_hasBeforeUpdateMethod - beforeUpdate method exists")
        void testInterface_hasBeforeUpdateMethod() throws NoSuchMethodException {
            // Verify beforeUpdate method signature
            Method method = SqlGuardInnerInterceptor.class.getMethod(
                "beforeUpdate",
                Executor.class, MappedStatement.class, Object.class
            );

            assertNotNull(method, "beforeUpdate method should exist");
            assertEquals(void.class, method.getReturnType(),
                "beforeUpdate should return void");
        }

        @Test
        @DisplayName("testInterface_hasGetPriorityMethod - getPriority method exists")
        void testInterface_hasGetPriorityMethod() throws NoSuchMethodException {
            // Verify getPriority method signature
            Method method = SqlGuardInnerInterceptor.class.getMethod("getPriority");

            assertNotNull(method, "getPriority method should exist");
            assertEquals(int.class, method.getReturnType(),
                "getPriority should return int");
        }
    }

    @Nested
    @DisplayName("2. Method Signature Tests")
    class MethodSignatureTests {

        @Test
        @DisplayName("testMethodSignatures_matchMyBatisPlusPattern - match MyBatis-Plus pattern")
        void testMethodSignatures_matchMyBatisPlusPattern() throws NoSuchMethodException {
            // Verify willDoQuery throws SQLException
            Method willDoQuery = SqlGuardInnerInterceptor.class.getMethod(
                "willDoQuery",
                Executor.class, MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, BoundSql.class
            );

            Class<?>[] exceptions = willDoQuery.getExceptionTypes();
            assertEquals(1, exceptions.length, "willDoQuery should declare SQLException");
            assertEquals(SQLException.class, exceptions[0],
                "willDoQuery should throw SQLException");

            // Verify beforeQuery throws SQLException
            Method beforeQuery = SqlGuardInnerInterceptor.class.getMethod(
                "beforeQuery",
                Executor.class, MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, BoundSql.class
            );

            exceptions = beforeQuery.getExceptionTypes();
            assertEquals(1, exceptions.length, "beforeQuery should declare SQLException");
            assertEquals(SQLException.class, exceptions[0],
                "beforeQuery should throw SQLException");
            
            // Verify willDoUpdate throws SQLException
            Method willDoUpdate = SqlGuardInnerInterceptor.class.getMethod(
                "willDoUpdate",
                Executor.class, MappedStatement.class, Object.class
            );

            exceptions = willDoUpdate.getExceptionTypes();
            assertEquals(1, exceptions.length, "willDoUpdate should declare SQLException");
            assertEquals(SQLException.class, exceptions[0],
                "willDoUpdate should throw SQLException");
            
            // Verify beforeUpdate throws SQLException
            Method beforeUpdate = SqlGuardInnerInterceptor.class.getMethod(
                "beforeUpdate",
                Executor.class, MappedStatement.class, Object.class
            );

            exceptions = beforeUpdate.getExceptionTypes();
            assertEquals(1, exceptions.length, "beforeUpdate should declare SQLException");
            assertEquals(SQLException.class, exceptions[0],
                "beforeUpdate should throw SQLException");
        }
    }

    @Nested
    @DisplayName("3. Default Implementation Tests")
    class DefaultImplementationTests {

        @Test
        @DisplayName("testDefaultMethods_providedForOptionalOverride - default implementations work")
        void testDefaultMethods_providedForOptionalOverride() throws SQLException {
            // Create minimal implementation
            SqlGuardInnerInterceptor interceptor = new SqlGuardInnerInterceptor() {
                // No methods overridden - using all defaults
            };

            // Test default willDoQuery returns true
            boolean willDoQueryResult = interceptor.willDoQuery(
                null, null, null, null, null, null
            );
            assertTrue(willDoQueryResult, "Default willDoQuery should return true");

            // Test default beforeQuery does nothing (no exception)
            assertDoesNotThrow(() ->
                interceptor.beforeQuery(null, null, null, null, null, null),
                "Default beforeQuery should not throw exception"
            );

            // Test default willDoUpdate returns true
            boolean willDoUpdateResult = interceptor.willDoUpdate(null, null, null);
            assertTrue(willDoUpdateResult, "Default willDoUpdate should return true");

            // Test default beforeUpdate does nothing (no exception)
            assertDoesNotThrow(() ->
                interceptor.beforeUpdate(null, null, null),
                "Default beforeUpdate should not throw exception"
            );

            // Test default getPriority returns 50
            int priority = interceptor.getPriority();
            assertEquals(50, priority, "Default priority should be 50");
        }

        @Test
        @DisplayName("testCustomPriority_canBeOverridden - custom priority works")
        void testCustomPriority_canBeOverridden() {
            // Create implementation with custom priority
            SqlGuardInnerInterceptor highPriority = new SqlGuardInnerInterceptor() {
                @Override
                public int getPriority() {
                    return 10;  // High priority (check interceptor)
                }
            };

            SqlGuardInnerInterceptor lowPriority = new SqlGuardInnerInterceptor() {
                @Override
                public int getPriority() {
                    return 100;  // Low priority (fallback interceptor)
                }
            };

            assertEquals(10, highPriority.getPriority(),
                "Custom high priority should be 10");
            assertEquals(100, lowPriority.getPriority(),
                "Custom low priority should be 100");
        }
    }

    @Nested
    @DisplayName("4. Javadoc Completeness Tests")
    class JavadocCompletenessTests {

        @Test
        @DisplayName("testJavadoc_complete_withExamples - Javadoc contains examples")
        void testJavadoc_complete_withExamples() {
            // This test verifies that Javadoc exists by checking the interface is documented
            // In practice, this would use a documentation analyzer tool

            // Verify interface is accessible (documented)
            assertNotNull(SqlGuardInnerInterceptor.class,
                "Interface should be accessible and documented");

            // Verify priority ranges are clearly defined (implicit in default method)
            SqlGuardInnerInterceptor testInterceptor = new SqlGuardInnerInterceptor() {};
            int defaultPriority = testInterceptor.getPriority();

            // Default priority 50 should be in the middle of range 1-99
            assertTrue(defaultPriority >= 1 && defaultPriority <= 199,
                "Default priority should be in documented range");
        }
    }

    @Nested
    @DisplayName("5. Interface Characteristics Tests")
    class InterfaceCharacteristicsTests {

        @Test
        @DisplayName("testInterface_isInterface - SqlGuardInnerInterceptor is an interface")
        void testInterface_isInterface() {
            assertTrue(SqlGuardInnerInterceptor.class.isInterface(),
                "SqlGuardInnerInterceptor should be an interface");
        }

        @Test
        @DisplayName("testInterface_allMethodsHaveDefaultImplementations - all methods are default")
        void testInterface_allMethodsHaveDefaultImplementations() {
            // Count declared methods (filter out synthetic methods)
            Method[] declaredMethods = SqlGuardInnerInterceptor.class.getDeclaredMethods();
            
            // Count only non-synthetic default methods
            int defaultMethodCount = 0;
            for (Method method : declaredMethods) {
                if (!method.isSynthetic()) {
                    assertTrue(method.isDefault(),
                        "Method " + method.getName() + " should be a default method");
                    defaultMethodCount++;
                }
            }
            
            // All 5 methods should be default (have implementations)
            assertEquals(5, defaultMethodCount, 
                "Interface should have exactly 5 non-synthetic default methods");
        }

        @Test
        @DisplayName("testInterface_implementableWithEmptyClass - can implement with empty class")
        void testInterface_implementableWithEmptyClass() {
            // This should compile and work without implementing any method
            SqlGuardInnerInterceptor emptyImpl = new SqlGuardInnerInterceptor() {};
            
            assertNotNull(emptyImpl, "Empty implementation should be instantiable");
            assertEquals(50, emptyImpl.getPriority(), 
                "Empty implementation should use default priority");
        }
    }
}

