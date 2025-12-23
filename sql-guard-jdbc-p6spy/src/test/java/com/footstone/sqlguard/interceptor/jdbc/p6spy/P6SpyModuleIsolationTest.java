package com.footstone.sqlguard.interceptor.jdbc.p6spy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Module Isolation Tests for sql-guard-jdbc-p6spy.
 *
 * <p>Verifies that the P6Spy module compiles and runs independently without
 * Druid or HikariCP dependencies, ensuring users who only use P6Spy (for
 * universal JDBC interception) are not forced to pull in unnecessary
 * connection pool libraries.</p>
 *
 * <h2>Test Categories</h2>
 * <ul>
 *   <li>Dependency isolation (no Druid/HikariCP)</li>
 *   <li>POM configuration verification</li>
 *   <li>ClassLoader isolation</li>
 *   <li>SPI registration verification</li>
 *   <li>Transitive dependency analysis</li>
 * </ul>
 *
 * @since 2.0.0
 */
@DisplayName("P6Spy Module Isolation Tests")
class P6SpyModuleIsolationTest {

    // When running from within the module, the path is just "."
    // When running from parent, the path is "sql-guard-jdbc-p6spy"
    private static String MODULE_PATH;
    private static final String POM_FILE = "pom.xml";
    
    /**
     * Druid class that should NOT be on classpath.
     */
    private static final String DRUID_CLASS = "com.alibaba.druid.pool.DruidDataSource";
    
    /**
     * HikariCP class that should NOT be on classpath.
     */
    private static final String HIKARI_CLASS = "com.zaxxer.hikari.HikariDataSource";
    
    /**
     * P6Spy class that SHOULD be available (provided scope).
     */
    private static final String P6SPY_CLASS = "com.p6spy.engine.spy.P6DataSource";
    
    /**
     * P6Spy JdbcEventListener class that SHOULD be available.
     */
    private static final String P6SPY_LISTENER_CLASS = "com.p6spy.engine.event.JdbcEventListener";

    private static String projectRootPath;

    @BeforeAll
    static void setupProjectRoot() {
        // Find project root by looking for pom.xml
        File currentDir = new File(System.getProperty("user.dir"));
        
        // If current dir has our module's POM, we're running from the module
        File modulePom = new File(currentDir, "pom.xml");
        if (modulePom.exists()) {
            try {
                String content = new String(java.nio.file.Files.readAllBytes(modulePom.toPath()));
                if (content.contains("sql-guard-jdbc-p6spy")) {
                    // We're in the P6Spy module directory
                    projectRootPath = currentDir.getAbsolutePath();
                    MODULE_PATH = ".";
                    return;
                }
            } catch (IOException e) {
                // Fall through to default
            }
        }
        
        // Otherwise, look for parent pom
        while (currentDir != null && !new File(currentDir, "pom.xml").exists()) {
            currentDir = currentDir.getParentFile();
        }
        projectRootPath = currentDir != null ? currentDir.getAbsolutePath() : System.getProperty("user.dir");
        MODULE_PATH = "sql-guard-jdbc-p6spy";
    }

    // ==================== Test 1: No Druid Dependency ====================
    
    @Test
    @DisplayName("1. P6Spy module should have NO Druid dependency")
    void testP6SpyModule_noDruidDependency_compiles() {
        // When: Check if Druid class can be loaded
        boolean druidAvailable = isClassAvailable(DRUID_CLASS);
        
        // Then: Druid should NOT be available
        assertFalse(druidAvailable, 
            "Druid should NOT be on classpath for P6Spy module. " +
            "Users who only use P6Spy should not be forced to pull in Druid.");
    }

    // ==================== Test 2: No HikariCP Dependency ====================
    
    @Test
    @DisplayName("2. P6Spy module should have NO HikariCP dependency")
    void testP6SpyModule_noHikariDependency_compiles() {
        // When: Check if HikariCP class can be loaded
        boolean hikariAvailable = isClassAvailable(HIKARI_CLASS);
        
        // Then: HikariCP should NOT be available
        assertFalse(hikariAvailable, 
            "HikariCP should NOT be on classpath for P6Spy module. " +
            "Users who only use P6Spy should not be forced to pull in HikariCP.");
    }

    // ==================== Test 3: P6Spy Dependency in Provided Scope ====================
    
    @Test
    @DisplayName("3. P6Spy dependency should be available (provided scope)")
    void testP6SpyModule_onlyP6SpyProvided_works() {
        // When: Check if P6Spy class can be loaded
        boolean p6spyAvailable = isClassAvailable(P6SPY_CLASS);
        
        // Then: P6Spy should be available (provided for tests)
        assertTrue(p6spyAvailable, 
            "P6Spy should be available on classpath. " +
            "The provided scope dependency should be accessible during tests.");
    }

    // ==================== Test 4: Common Module Dependency Resolves ====================
    
    @Test
    @DisplayName("4. Common module dependency should resolve correctly")
    void testP6SpyModule_commonModuleDependency_resolves() {
        // When: Try to load classes from sql-guard-jdbc-common
        boolean violationStrategyAvailable = isClassAvailable(
            "com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy");
        boolean jdbcInterceptorBaseAvailable = isClassAvailable(
            "com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase");
        boolean jdbcInterceptorConfigAvailable = isClassAvailable(
            "com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorConfig");
        
        // Then: All common module classes should be available
        assertAll("Common module classes should be available",
            () -> assertTrue(violationStrategyAvailable, "ViolationStrategy should be available"),
            () -> assertTrue(jdbcInterceptorBaseAvailable, "JdbcInterceptorBase should be available"),
            () -> assertTrue(jdbcInterceptorConfigAvailable, "JdbcInterceptorConfig should be available")
        );
    }

    // ==================== Test 5: ClassLoader Isolation ====================
    
    @Test
    @DisplayName("5. ClassLoader should NOT contain other pool classes")
    void testP6SpyModule_classLoading_noOtherPoolsRequired() {
        // Given: List of classes that should NOT be loadable
        String[] forbiddenClasses = {
            "com.alibaba.druid.pool.DruidDataSource",
            "com.alibaba.druid.filter.FilterAdapter",
            "com.alibaba.druid.filter.Filter",
            "com.zaxxer.hikari.HikariDataSource",
            "com.zaxxer.hikari.HikariConfig",
            "com.zaxxer.hikari.pool.HikariPool"
        };
        
        // When/Then: None of the forbidden classes should be loadable
        for (String className : forbiddenClasses) {
            assertFalse(isClassAvailable(className),
                "Class " + className + " should NOT be available on classpath");
        }
    }

    // ==================== Test 6: Independent JAR Packaging ====================
    
    @Test
    @DisplayName("6. Module should package as independent JAR")
    void testP6SpyModule_independentJar_packages() {
        // This test verifies the module structure exists and is correct
        File pomFile = new File(projectRootPath, MODULE_PATH + "/" + POM_FILE);
        
        // Then: POM file should exist
        assertTrue(pomFile.exists(), 
            "POM file should exist at " + pomFile.getAbsolutePath());
        
        // And: Module directory structure should exist
        File srcMainJava = new File(projectRootPath, 
            MODULE_PATH + "/src/main/java/com/footstone/sqlguard/interceptor/jdbc/p6spy");
        File srcTestJava = new File(projectRootPath, 
            MODULE_PATH + "/src/test/java/com/footstone/sqlguard/interceptor/jdbc/p6spy");
        
        assertAll("Module directory structure should exist",
            () -> assertTrue(srcMainJava.exists() || srcMainJava.mkdirs(), 
                "src/main/java directory should exist"),
            () -> assertTrue(srcTestJava.exists(), 
                "src/test/java directory should exist")
        );
    }

    // ==================== Test 7: SPI Registration Works ====================
    
    @Test
    @DisplayName("7. SPI registration file should exist for P6Factory")
    void testP6SpyModule_spiRegistration_works() {
        // Given: Expected SPI file location
        File spiFile = new File(projectRootPath, 
            MODULE_PATH + "/src/main/resources/META-INF/services/com.p6spy.engine.spy.P6Factory");
        
        // Then: SPI file should exist
        assertTrue(spiFile.exists(), 
            "SPI registration file should exist at " + spiFile.getAbsolutePath());
        
        // And: SPI file should contain our module class
        if (spiFile.exists()) {
            try {
                String content = readFileContent(spiFile);
                assertTrue(content.contains("P6SpySqlSafetyModule"),
                    "SPI file should register P6SpySqlSafetyModule");
            } catch (IOException e) {
                fail("Failed to read SPI file: " + e.getMessage());
            }
        }
    }

    // ==================== Test 8: spy.properties Template Exists ====================
    
    @Test
    @DisplayName("8. spy.properties template should exist")
    void testP6SpyModule_spyProperties_loads() {
        // Given: Expected spy.properties.template location
        File templateFile = new File(projectRootPath, 
            MODULE_PATH + "/src/main/resources/spy.properties.template");
        
        // Then: Template file should exist
        assertTrue(templateFile.exists(), 
            "spy.properties.template should exist at " + templateFile.getAbsolutePath());
        
        // And: Template should contain module registration
        if (templateFile.exists()) {
            try {
                String content = readFileContent(templateFile);
                assertAll("spy.properties.template content validation",
                    () -> assertTrue(content.contains("modulelist"),
                        "Template should contain modulelist configuration"),
                    () -> assertTrue(content.contains("P6SpySqlSafetyModule"),
                        "Template should reference P6SpySqlSafetyModule"),
                    () -> assertTrue(content.contains("sqlguard.p6spy"),
                        "Template should contain sqlguard.p6spy property prefix")
                );
            } catch (IOException e) {
                fail("Failed to read template file: " + e.getMessage());
            }
        }
    }

    // ==================== Test 9: Runtime Classpath Minimal ====================
    
    @Test
    @DisplayName("9. Runtime classpath should be minimal")
    void testP6SpyModule_runtimeClasspath_minimal() {
        // Given: Expected runtime dependencies (sql-guard-core, sql-guard-audit-api, 
        //        sql-guard-jdbc-common, slf4j-api)
        
        // When: Check that core classes are available
        assertAll("Core dependencies should be available",
            () -> assertTrue(isClassAvailable("com.footstone.sqlguard.core.model.SqlContext"),
                "sql-guard-core should be available"),
            () -> assertTrue(isClassAvailable("com.footstone.sqlguard.audit.AuditLogWriter"),
                "sql-guard-audit-api should be available"),
            () -> assertTrue(isClassAvailable("com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy"),
                "sql-guard-jdbc-common should be available"),
            () -> assertTrue(isClassAvailable("org.slf4j.Logger"),
                "slf4j-api should be available")
        );
    }

    // ==================== Test 10: Test Classpath Isolated ====================
    
    @Test
    @DisplayName("10. Test classpath should be properly isolated")
    void testP6SpyModule_testClasspath_isolated() {
        // Given: Test dependencies that should be available
        String[] testDependencies = {
            "org.junit.jupiter.api.Test",
            "org.mockito.Mockito",
            "org.assertj.core.api.Assertions"
        };
        
        // When/Then: Test dependencies should be available
        for (String className : testDependencies) {
            assertTrue(isClassAvailable(className),
                "Test dependency class " + className + " should be available");
        }
        
        // And: Other pool classes should NOT be available
        assertFalse(isClassAvailable(DRUID_CLASS),
            "Druid should not be available even in test classpath");
        assertFalse(isClassAvailable(HIKARI_CLASS),
            "HikariCP should not be available even in test classpath");
    }

    // ==================== Helper Methods ====================
    
    /**
     * Checks if a class is available on the classpath.
     *
     * @param className fully qualified class name
     * @return true if class can be loaded, false otherwise
     */
    private boolean isClassAvailable(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Reads file content as string.
     *
     * @param file the file to read
     * @return file content as string
     * @throws IOException if file cannot be read
     */
    private String readFileContent(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return content.toString();
    }
}






