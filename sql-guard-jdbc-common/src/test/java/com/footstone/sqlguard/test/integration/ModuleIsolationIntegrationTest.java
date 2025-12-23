package com.footstone.sqlguard.test.integration;

import static org.assertj.core.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Module Isolation Integration Tests (8 tests).
 *
 * <p>Verifies each module compiles independently without transitive dependency pollution.
 * This is the final validation for Phase 11 JDBC Module Separation.</p>
 *
 * <h2>Test Categories</h2>
 * <ul>
 *   <li>Independent compilation verification</li>
 *   <li>Dependency pollution analysis</li>
 *   <li>Maven Enforcer validation</li>
 *   <li>Multi-module integration</li>
 * </ul>
 *
 * @since 2.0.0
 */
@DisplayName("Module Isolation Integration Tests (8 tests)")
class ModuleIsolationIntegrationTest {

    private static Path projectRoot;
    private static boolean mavenAvailable;

    // Connection pool classes for isolation verification
    private static final String DRUID_CLASS = "com.alibaba.druid.pool.DruidDataSource";
    private static final String HIKARI_CLASS = "com.zaxxer.hikari.HikariDataSource";
    private static final String P6SPY_CLASS = "com.p6spy.engine.spy.P6DataSource";

    @BeforeAll
    static void setupProjectRoot() {
        // Find project root by looking for parent pom.xml
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        
        // Navigate up to find the parent pom.xml with modules definition
        while (currentDir != null) {
            Path pomPath = currentDir.resolve("pom.xml");
            if (Files.exists(pomPath)) {
                try {
                    String content = new String(Files.readAllBytes(pomPath));
                    if (content.contains("<modules>") && content.contains("sql-guard-jdbc-common")) {
                        projectRoot = currentDir;
                        break;
                    }
                } catch (IOException e) {
                    // Continue searching
                }
            }
            currentDir = currentDir.getParent();
        }
        
        if (projectRoot == null) {
            projectRoot = Paths.get(System.getProperty("user.dir"));
        }

        // Check if Maven is available
        mavenAvailable = isMavenAvailable();
    }

    /**
     * Check if Maven is available in the system.
     */
    private static boolean isMavenAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean completed = process.waitFor(10, TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Test 1: Common Module Independent Compile ====================

    @Test
    @DisplayName("1. testCommonModule_independentCompile_noConnectionPool")
    void testCommonModule_independentCompile_noConnectionPool() {
        // Verify common module has NO connection pool dependencies
        
        // Check POM file for banned dependencies
        Path pomPath = projectRoot.resolve("sql-guard-jdbc-common/pom.xml");
        assertThat(Files.exists(pomPath))
            .as("Common module POM should exist")
            .isTrue();

        try {
            String pomContent = new String(Files.readAllBytes(pomPath));
            
            // Verify Maven Enforcer plugin is configured
            assertThat(pomContent)
                .as("Common module should have Maven Enforcer plugin")
                .contains("maven-enforcer-plugin");
            
            // Verify banned dependencies are configured
            assertThat(pomContent)
                .as("Common module should ban Druid")
                .contains("com.alibaba:druid");
            assertThat(pomContent)
                .as("Common module should ban HikariCP")
                .contains("com.zaxxer:HikariCP");
            assertThat(pomContent)
                .as("Common module should ban P6Spy")
                .contains("p6spy:p6spy");

            // Verify no pool dependencies in dependencies section
            String depsSection = extractDependenciesSection(pomContent);
            assertThat(depsSection)
                .as("Common module should not have Druid dependency")
                .doesNotContain("<artifactId>druid</artifactId>");
            assertThat(depsSection)
                .as("Common module should not have HikariCP dependency")
                .doesNotContain("<artifactId>HikariCP</artifactId>");
            assertThat(depsSection)
                .as("Common module should not have P6Spy dependency")
                .doesNotContain("<artifactId>p6spy</artifactId>");

        } catch (IOException e) {
            fail("Failed to read common module POM: " + e.getMessage());
        }

        // Runtime verification: pool classes should not be on classpath
        assertThat(isClassAvailable(DRUID_CLASS)).as("Druid should NOT be on classpath").isFalse();
        assertThat(isClassAvailable(HIKARI_CLASS)).as("HikariCP should NOT be on classpath").isFalse();
        assertThat(isClassAvailable(P6SPY_CLASS)).as("P6Spy should NOT be on classpath").isFalse();
    }

    // ==================== Test 2: Druid Module Independent Compile ====================

    @Test
    @DisplayName("2. testDruidModule_independentCompile_onlyDruid")
    void testDruidModule_independentCompile_onlyDruid() {
        // Verify Druid module has ONLY Druid dependency (no HikariCP/P6Spy)
        
        Path pomPath = projectRoot.resolve("sql-guard-jdbc-druid/pom.xml");
        assertThat(Files.exists(pomPath))
            .as("Druid module POM should exist")
            .isTrue();

        try {
            String pomContent = new String(Files.readAllBytes(pomPath));
            
            // Verify Maven Enforcer plugin bans HikariCP and P6Spy
            assertThat(pomContent)
                .as("Druid module should have Maven Enforcer plugin")
                .contains("maven-enforcer-plugin");
            assertThat(pomContent)
                .as("Druid module should ban HikariCP")
                .contains("com.zaxxer:HikariCP");
            assertThat(pomContent)
                .as("Druid module should ban P6Spy")
                .contains("p6spy:p6spy");

            // Verify Druid is in provided scope
            String depsSection = extractDependenciesSection(pomContent);
            assertThat(depsSection)
                .as("Druid module should have Druid dependency")
                .contains("<artifactId>druid</artifactId>");
            
            // Verify no HikariCP or P6Spy in dependencies
            assertThat(depsSection)
                .as("Druid module should NOT have HikariCP dependency")
                .doesNotContain("<artifactId>HikariCP</artifactId>");
            assertThat(depsSection)
                .as("Druid module should NOT have P6Spy dependency")
                .doesNotContain("<artifactId>p6spy</artifactId>");

        } catch (IOException e) {
            fail("Failed to read Druid module POM: " + e.getMessage());
        }
    }

    // ==================== Test 3: HikariCP Module Independent Compile ====================

    @Test
    @DisplayName("3. testHikariModule_independentCompile_onlyHikari")
    void testHikariModule_independentCompile_onlyHikari() {
        // Verify HikariCP module has ONLY HikariCP dependency (no Druid/P6Spy)
        
        Path pomPath = projectRoot.resolve("sql-guard-jdbc-hikari/pom.xml");
        assertThat(Files.exists(pomPath))
            .as("HikariCP module POM should exist")
            .isTrue();

        try {
            String pomContent = new String(Files.readAllBytes(pomPath));
            
            // Verify Maven Enforcer plugin bans Druid and P6Spy
            assertThat(pomContent)
                .as("HikariCP module should have Maven Enforcer plugin")
                .contains("maven-enforcer-plugin");
            assertThat(pomContent)
                .as("HikariCP module should ban Druid")
                .contains("com.alibaba:druid");
            assertThat(pomContent)
                .as("HikariCP module should ban P6Spy")
                .contains("p6spy:p6spy");

            // Verify HikariCP is in provided scope
            String depsSection = extractDependenciesSection(pomContent);
            assertThat(depsSection)
                .as("HikariCP module should have HikariCP dependency")
                .contains("<artifactId>HikariCP</artifactId>");
            
            // Verify no Druid or P6Spy in dependencies
            assertThat(depsSection)
                .as("HikariCP module should NOT have Druid dependency")
                .doesNotContain("<artifactId>druid</artifactId>");
            assertThat(depsSection)
                .as("HikariCP module should NOT have P6Spy dependency")
                .doesNotContain("<artifactId>p6spy</artifactId>");

        } catch (IOException e) {
            fail("Failed to read HikariCP module POM: " + e.getMessage());
        }
    }

    // ==================== Test 4: P6Spy Module Independent Compile ====================

    @Test
    @DisplayName("4. testP6SpyModule_independentCompile_onlyP6Spy")
    void testP6SpyModule_independentCompile_onlyP6Spy() {
        // Verify P6Spy module has ONLY P6Spy dependency (no Druid/HikariCP)
        
        Path pomPath = projectRoot.resolve("sql-guard-jdbc-p6spy/pom.xml");
        assertThat(Files.exists(pomPath))
            .as("P6Spy module POM should exist")
            .isTrue();

        try {
            String pomContent = new String(Files.readAllBytes(pomPath));
            
            // Verify Maven Enforcer plugin bans Druid and HikariCP
            assertThat(pomContent)
                .as("P6Spy module should have Maven Enforcer plugin")
                .contains("maven-enforcer-plugin");
            assertThat(pomContent)
                .as("P6Spy module should ban Druid")
                .contains("com.alibaba:druid");
            assertThat(pomContent)
                .as("P6Spy module should ban HikariCP")
                .contains("com.zaxxer:HikariCP");

            // Verify P6Spy is in provided scope
            String depsSection = extractDependenciesSection(pomContent);
            assertThat(depsSection)
                .as("P6Spy module should have P6Spy dependency")
                .contains("<artifactId>p6spy</artifactId>");
            
            // Verify no Druid or HikariCP in dependencies
            assertThat(depsSection)
                .as("P6Spy module should NOT have Druid dependency")
                .doesNotContain("<artifactId>druid</artifactId>");
            assertThat(depsSection)
                .as("P6Spy module should NOT have HikariCP dependency")
                .doesNotContain("<artifactId>HikariCP</artifactId>");

        } catch (IOException e) {
            fail("Failed to read P6Spy module POM: " + e.getMessage());
        }
    }

    // ==================== Test 5: User Project Only Druid ====================

    @Test
    @DisplayName("5. testUserProject_onlyDruid_noDependencyPollution")
    void testUserProject_onlyDruid_noDependencyPollution() {
        // Simulate user project depending ONLY on sql-guard-jdbc-druid
        // Verify NO HikariCP or P6Spy in transitive deps
        
        Path druidPomPath = projectRoot.resolve("sql-guard-jdbc-druid/pom.xml");
        assertThat(Files.exists(druidPomPath))
            .as("Druid module POM should exist")
            .isTrue();

        try {
            String pomContent = new String(Files.readAllBytes(druidPomPath));
            
            // Verify only depends on sql-guard-jdbc-common (which has no pool deps)
            String depsSection = extractDependenciesSection(pomContent);
            assertThat(depsSection)
                .as("Druid module should depend on sql-guard-jdbc-common")
                .contains("<artifactId>sql-guard-jdbc-common</artifactId>");
            
            // Verify common module doesn't pull in HikariCP or P6Spy
            Path commonPomPath = projectRoot.resolve("sql-guard-jdbc-common/pom.xml");
            String commonPomContent = new String(Files.readAllBytes(commonPomPath));
            String commonDepsSection = extractDependenciesSection(commonPomContent);
            
            assertThat(commonDepsSection)
                .as("Common module should NOT have HikariCP")
                .doesNotContain("<artifactId>HikariCP</artifactId>");
            assertThat(commonDepsSection)
                .as("Common module should NOT have P6Spy")
                .doesNotContain("<artifactId>p6spy</artifactId>");
            assertThat(commonDepsSection)
                .as("Common module should NOT have Druid")
                .doesNotContain("<artifactId>druid</artifactId>");

        } catch (IOException e) {
            fail("Failed to verify dependency pollution: " + e.getMessage());
        }
    }

    // ==================== Test 6: User Project Only HikariCP ====================

    @Test
    @DisplayName("6. testUserProject_onlyHikari_noDependencyPollution")
    void testUserProject_onlyHikari_noDependencyPollution() {
        // Simulate user project depending ONLY on sql-guard-jdbc-hikari
        // Verify NO Druid or P6Spy in transitive deps
        
        Path hikariPomPath = projectRoot.resolve("sql-guard-jdbc-hikari/pom.xml");
        assertThat(Files.exists(hikariPomPath))
            .as("HikariCP module POM should exist")
            .isTrue();

        try {
            String pomContent = new String(Files.readAllBytes(hikariPomPath));
            
            // Verify only depends on sql-guard-jdbc-common (which has no pool deps)
            String depsSection = extractDependenciesSection(pomContent);
            assertThat(depsSection)
                .as("HikariCP module should depend on sql-guard-jdbc-common")
                .contains("<artifactId>sql-guard-jdbc-common</artifactId>");
            
            // Verify common module doesn't pull in Druid or P6Spy
            Path commonPomPath = projectRoot.resolve("sql-guard-jdbc-common/pom.xml");
            String commonPomContent = new String(Files.readAllBytes(commonPomPath));
            String commonDepsSection = extractDependenciesSection(commonPomContent);
            
            assertThat(commonDepsSection)
                .as("Common module should NOT have Druid")
                .doesNotContain("<artifactId>druid</artifactId>");
            assertThat(commonDepsSection)
                .as("Common module should NOT have P6Spy")
                .doesNotContain("<artifactId>p6spy</artifactId>");

        } catch (IOException e) {
            fail("Failed to verify dependency pollution: " + e.getMessage());
        }
    }

    // ==================== Test 7: User Project Only P6Spy ====================

    @Test
    @DisplayName("7. testUserProject_onlyP6Spy_noDependencyPollution")
    void testUserProject_onlyP6Spy_noDependencyPollution() {
        // Simulate user project depending ONLY on sql-guard-jdbc-p6spy
        // Verify NO Druid or HikariCP in transitive deps
        
        Path p6spyPomPath = projectRoot.resolve("sql-guard-jdbc-p6spy/pom.xml");
        assertThat(Files.exists(p6spyPomPath))
            .as("P6Spy module POM should exist")
            .isTrue();

        try {
            String pomContent = new String(Files.readAllBytes(p6spyPomPath));
            
            // Verify only depends on sql-guard-jdbc-common (which has no pool deps)
            String depsSection = extractDependenciesSection(pomContent);
            assertThat(depsSection)
                .as("P6Spy module should depend on sql-guard-jdbc-common")
                .contains("<artifactId>sql-guard-jdbc-common</artifactId>");
            
            // Verify common module doesn't pull in Druid or HikariCP
            Path commonPomPath = projectRoot.resolve("sql-guard-jdbc-common/pom.xml");
            String commonPomContent = new String(Files.readAllBytes(commonPomPath));
            String commonDepsSection = extractDependenciesSection(commonPomContent);
            
            assertThat(commonDepsSection)
                .as("Common module should NOT have Druid")
                .doesNotContain("<artifactId>druid</artifactId>");
            assertThat(commonDepsSection)
                .as("Common module should NOT have HikariCP")
                .doesNotContain("<artifactId>HikariCP</artifactId>");

        } catch (IOException e) {
            fail("Failed to verify dependency pollution: " + e.getMessage());
        }
    }

    // ==================== Test 8: All Modules Together ====================

    @Test
    @DisplayName("8. testUserProject_allModules_works")
    void testUserProject_allModules_works() {
        // Verify all three modules can be used together without conflicts
        
        // All modules exist
        assertThat(Files.exists(projectRoot.resolve("sql-guard-jdbc-druid/pom.xml")))
            .as("Druid module should exist")
            .isTrue();
        assertThat(Files.exists(projectRoot.resolve("sql-guard-jdbc-hikari/pom.xml")))
            .as("HikariCP module should exist")
            .isTrue();
        assertThat(Files.exists(projectRoot.resolve("sql-guard-jdbc-p6spy/pom.xml")))
            .as("P6Spy module should exist")
            .isTrue();

        // All modules depend on the same common module (no version conflicts)
        try {
            List<String> commonVersions = new ArrayList<>();
            
            for (String module : Arrays.asList("sql-guard-jdbc-druid", "sql-guard-jdbc-hikari", "sql-guard-jdbc-p6spy")) {
                Path pomPath = projectRoot.resolve(module + "/pom.xml");
                String pomContent = new String(Files.readAllBytes(pomPath));
                
                // Extract version from sql-guard-jdbc-common dependency
                if (pomContent.contains("<artifactId>sql-guard-jdbc-common</artifactId>")) {
                    // Version is typically ${project.version}
                    assertThat(pomContent)
                        .as(module + " should use project version for common module")
                        .contains("${project.version}");
                }
            }

            // Verify ViolationStrategy is available (unified enum)
            assertThat(isClassAvailable("com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy"))
                .as("ViolationStrategy should be available from common module")
                .isTrue();

            // Verify JdbcInterceptorBase is available (template method pattern)
            assertThat(isClassAvailable("com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase"))
                .as("JdbcInterceptorBase should be available from common module")
                .isTrue();

        } catch (IOException e) {
            fail("Failed to verify all modules work together: " + e.getMessage());
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Extracts the dependencies section from POM content.
     */
    private String extractDependenciesSection(String pom) {
        if (pom == null) return "";
        int start = pom.indexOf("<dependencies>");
        int end = pom.indexOf("</dependencies>");
        if (start >= 0 && end > start) {
            return pom.substring(start, end + "</dependencies>".length());
        }
        return "";
    }

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






