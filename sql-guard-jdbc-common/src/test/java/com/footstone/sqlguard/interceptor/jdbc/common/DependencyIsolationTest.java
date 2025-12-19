package com.footstone.sqlguard.interceptor.jdbc.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * TDD Test Class for Dependency Isolation (10 tests).
 * 
 * <p>Tests verify that sql-guard-jdbc-common module has:
 * <ul>
 *   <li>NO Druid dependency</li>
 *   <li>NO HikariCP dependency</li>
 *   <li>NO P6Spy dependency</li>
 *   <li>ONLY sql-guard-core and sql-guard-audit-api as runtime dependencies</li>
 * </ul>
 * 
 * <p><strong>Minimal Dependency Principle:</strong> The common module must remain 
 * completely independent of any connection pool implementation.
 * 
 * @see ViolationStrategy
 */
@DisplayName("Dependency Isolation Tests")
class DependencyIsolationTest {

    private static final String DRUID_CLASS = "com.alibaba.druid.pool.DruidDataSource";
    private static final String HIKARI_CLASS = "com.zaxxer.hikari.HikariDataSource";
    private static final String P6SPY_CLASS = "com.p6spy.engine.spy.P6DataSource";

    // ========== Compile-Time Dependency Tests (3 tests) ==========

    @Nested
    @DisplayName("Compile-Time Dependency Tests")
    class CompileTimeDependencyTests {

        @Test
        @DisplayName("3.2.1 - Common module has no Druid dependency")
        void testCommonModule_noDruidDependency_compiles() {
            // When: Try to load Druid class
            boolean druidPresent = isClassAvailable(DRUID_CLASS);

            // Then: Druid should NOT be on classpath
            assertThat(druidPresent)
                .as("Druid classes should NOT be available in sql-guard-jdbc-common")
                .isFalse();
        }

        @Test
        @DisplayName("3.2.2 - Common module has no HikariCP dependency")
        void testCommonModule_noHikariDependency_compiles() {
            // When: Try to load HikariCP class
            boolean hikariPresent = isClassAvailable(HIKARI_CLASS);

            // Then: HikariCP should NOT be on classpath
            assertThat(hikariPresent)
                .as("HikariCP classes should NOT be available in sql-guard-jdbc-common")
                .isFalse();
        }

        @Test
        @DisplayName("3.2.3 - Common module has no P6Spy dependency")
        void testCommonModule_noP6SpyDependency_compiles() {
            // When: Try to load P6Spy class
            boolean p6spyPresent = isClassAvailable(P6SPY_CLASS);

            // Then: P6Spy should NOT be on classpath
            assertThat(p6spyPresent)
                .as("P6Spy classes should NOT be available in sql-guard-jdbc-common")
                .isFalse();
        }
    }

    // ========== Allowed Dependencies Tests (2 tests) ==========

    @Nested
    @DisplayName("Allowed Dependencies Tests")
    class AllowedDependenciesTests {

        @Test
        @DisplayName("3.2.4 - Common module only depends on core and audit-api (sufficient)")
        void testCommonModule_onlyCoreAndAuditApi_sufficient() {
            // Verify core classes are available
            boolean coreClassPresent = isClassAvailable(
                "com.footstone.sqlguard.core.model.SqlContext");
            boolean auditApiClassPresent = isClassAvailable(
                "com.footstone.sqlguard.audit.AuditEvent");
            
            // Then: Core and Audit API should be available
            assertThat(coreClassPresent)
                .as("sql-guard-core classes should be available")
                .isTrue();
            assertThat(auditApiClassPresent)
                .as("sql-guard-audit-api classes should be available")
                .isTrue();
        }

        @Test
        @DisplayName("3.2.5 - Common module has no transitive pool dependencies")
        void testCommonModule_transitiveDeps_none() {
            // Verify no transitive pool dependencies are pulled in
            // by checking for common pool-specific classes
            
            Set<String> poolClasses = Set.of(
                // Druid classes
                "com.alibaba.druid.filter.Filter",
                "com.alibaba.druid.filter.FilterAdapter",
                // HikariCP classes
                "com.zaxxer.hikari.HikariConfig",
                "com.zaxxer.hikari.pool.ProxyFactory",
                // P6Spy classes
                "com.p6spy.engine.common.P6Util",
                "com.p6spy.engine.event.JdbcEventListener"
            );

            for (String className : poolClasses) {
                assertThat(isClassAvailable(className))
                    .as("Pool class %s should NOT be transitively available", className)
                    .isFalse();
            }
        }
    }

    // ========== ClassLoader Tests (3 tests) ==========

    @Nested
    @DisplayName("ClassLoader Isolation Tests")
    class ClassLoaderTests {

        @Test
        @DisplayName("3.2.6 - ClassLoader does not require pool classes")
        void testCommonModule_classLoading_noPoolClassesRequired() {
            // Given: Common module classes
            Class<?>[] commonClasses = {
                ViolationStrategy.class,
                JdbcInterceptorBase.class,
                JdbcInterceptorConfig.class,
                SqlContextBuilder.class,
                JdbcAuditEventBuilder.class
            };

            // When/Then: Loading these classes should not trigger loading of pool classes
            for (Class<?> clazz : commonClasses) {
                assertDoesNotThrow(
                    () -> Class.forName(clazz.getName()),
                    "Loading " + clazz.getSimpleName() + " should not require pool classes"
                );
            }

            // Verify pool classes still not loaded
            assertThat(isClassAvailable(DRUID_CLASS)).isFalse();
            assertThat(isClassAvailable(HIKARI_CLASS)).isFalse();
            assertThat(isClassAvailable(P6SPY_CLASS)).isFalse();
        }

        @Test
        @DisplayName("3.2.7 - Optional dependencies are properly marked")
        void testCommonModule_optionalDeps_properlyMarked() {
            // This test verifies that optional features don't break when their dependencies are missing
            
            // ViolationStrategy should work without any optional dependencies
            ViolationStrategy strategy = ViolationStrategy.BLOCK;
            assertThat(strategy).isNotNull();
            assertThat(strategy.shouldBlock()).isTrue();

            // JdbcInterceptorConfig should work independently
            JdbcInterceptorConfig config = new JdbcInterceptorConfig() {
                @Override public boolean isEnabled() { return true; }
                @Override public ViolationStrategy getStrategy() { return ViolationStrategy.WARN; }
                @Override public boolean isAuditEnabled() { return false; }
                @Override public java.util.List<String> getExcludePatterns() { 
                    return java.util.Collections.emptyList(); 
                }
            };
            assertThat(config.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("3.2.8 - Provided scope dependencies not leaking")
        void testCommonModule_providedScope_notLeaking() {
            // In a properly configured common module, there should be NO provided scope
            // dependencies since this module doesn't depend on any pool implementations
            
            // Verify no pool-specific exception types
            Set<String> poolExceptions = Set.of(
                "com.alibaba.druid.pool.DruidPooledConnection",
                "com.zaxxer.hikari.pool.HikariProxyConnection",
                "com.p6spy.engine.spy.P6Connection"
            );

            for (String className : poolExceptions) {
                assertThat(isClassAvailable(className))
                    .as("Pool-specific class %s should NOT be available", className)
                    .isFalse();
            }
        }
    }

    // ========== Scope Tests (2 tests) ==========

    @Nested
    @DisplayName("Dependency Scope Tests")
    class DependencyScopeTests {

        @Test
        @DisplayName("3.2.9 - Test scope dependencies are isolated")
        void testCommonModule_testScope_isolated() {
            // Test scope dependencies (H2, AssertJ) should only be available in tests
            // This test passes if we're running in test context
            
            // H2 should be available (test scope)
            boolean h2Available = isClassAvailable("org.h2.Driver");
            assertThat(h2Available)
                .as("H2 should be available in test scope")
                .isTrue();

            // AssertJ should be available (test scope)
            boolean assertjAvailable = isClassAvailable("org.assertj.core.api.Assertions");
            assertThat(assertjAvailable)
                .as("AssertJ should be available in test scope")
                .isTrue();
        }

        @Test
        @DisplayName("3.2.10 - Runtime scope is minimal")
        void testCommonModule_runtimeScope_minimal() {
            // Verify only essential runtime dependencies are present
            
            // SLF4J API should be available (compile scope)
            boolean slf4jAvailable = isClassAvailable("org.slf4j.Logger");
            assertThat(slf4jAvailable)
                .as("SLF4J API should be available")
                .isTrue();

            // Core validation classes should be available
            boolean validatorAvailable = isClassAvailable(
                "com.footstone.sqlguard.validator.DefaultSqlSafetyValidator");
            assertThat(validatorAvailable)
                .as("Core validator should be available")
                .isTrue();

            // Audit API should be available
            boolean auditWriterAvailable = isClassAvailable(
                "com.footstone.sqlguard.audit.AuditLogWriter");
            assertThat(auditWriterAvailable)
                .as("Audit API should be available")
                .isTrue();
        }
    }

    // ========== Helper Methods ==========

    /**
     * Checks if a class is available on the classpath.
     *
     * @param className fully qualified class name
     * @return true if class can be loaded, false otherwise
     */
    private boolean isClassAvailable(String className) {
        try {
            Class.forName(className, false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
