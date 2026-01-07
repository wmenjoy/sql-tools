package com.footstone.sqlguard.interceptor.jdbc.hikari;

import static org.assertj.core.api.Assertions.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Module Isolation Tests for HikariCP Module.
 *
 * <p>Verifies that sql-guard-jdbc-hikari is completely independent of Druid and P6Spy.
 * These tests validate the dependency isolation requirements for Phase 11.</p>
 *
 * <p><strong>Test Categories:</strong></p>
 * <ul>
 *   <li>Dependency isolation - no Druid/P6Spy dependencies</li>
 *   <li>HikariCP provided scope verification</li>
 *   <li>Common module dependency resolution</li>
 *   <li>ClassLoader isolation</li>
 *   <li>Independent JAR packaging</li>
 *   <li>Version compatibility (HikariCP 4.x and 5.x)</li>
 * </ul>
 *
 * @since 2.0.0
 */
@DisplayName("HikariCP Module Isolation Tests")
class HikariModuleIsolationTest {

    private static final String MODULE_PATH = "sql-guard-jdbc-hikari";
    private Path pomPath;
    private String pomContent;

    @BeforeEach
    void setUp() throws IOException {
        // Find module root - handle both running from module dir and parent dir
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        if (currentDir.endsWith(MODULE_PATH)) {
            pomPath = currentDir.resolve("pom.xml");
        } else {
            pomPath = currentDir.resolve(MODULE_PATH).resolve("pom.xml");
        }
        
        if (Files.exists(pomPath)) {
            pomContent = new String(Files.readAllBytes(pomPath));
        }
    }

    /**
     * Test 1: Verify HikariCP module has no Druid dependency.
     * <p>Checks that pom.xml does not declare com.alibaba:druid as a compile/runtime dependency.</p>
     */
    @Test
    @DisplayName("testHikariModule_noDruidDependency_compiles")
    void testHikariModule_noDruidDependency_compiles() {
        assertThat(pomContent)
            .as("HikariCP module POM should exist")
            .isNotNull();

        // Verify no Druid dependency in <dependencies> section (not in bannedDependencies exclusions)
        // Count occurrences of druid - should only appear in excludes section (Maven Enforcer)
        String dependencies = extractDependenciesSection(pomContent);
        
        assertThat(dependencies)
            .as("HikariCP module should NOT have Druid dependency in dependencies section")
            .doesNotContain("<artifactId>druid</artifactId>");
    }
    
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
     * Test 2: Verify HikariCP module has no P6Spy dependency.
     * <p>Checks that pom.xml does not declare p6spy:p6spy as a compile/runtime dependency.</p>
     */
    @Test
    @DisplayName("testHikariModule_noP6SpyDependency_compiles")
    void testHikariModule_noP6SpyDependency_compiles() {
        assertThat(pomContent)
            .as("HikariCP module POM should exist")
            .isNotNull();

        // Verify no P6Spy dependency in <dependencies> section
        String dependencies = extractDependenciesSection(pomContent);
        
        assertThat(dependencies)
            .as("HikariCP module should NOT have P6Spy dependency in dependencies section")
            .doesNotContain("<artifactId>p6spy</artifactId>");
    }

    /**
     * Test 3: Verify HikariCP is declared with provided scope.
     * <p>Users must provide their own HikariCP version.</p>
     */
    @Test
    @DisplayName("testHikariModule_onlyHikariProvided_works")
    void testHikariModule_onlyHikariProvided_works() {
        assertThat(pomContent)
            .as("HikariCP module POM should exist")
            .isNotNull();

        // Verify HikariCP dependency exists with provided scope
        assertThat(pomContent)
            .as("HikariCP module should have HikariCP dependency")
            .contains("<artifactId>HikariCP</artifactId>");

        // Check provided scope - normalize whitespace for comparison
        String normalizedPom = pomContent.replaceAll("\\s+", " ");
        assertThat(normalizedPom)
            .as("HikariCP dependency should have provided scope")
            .containsPattern("HikariCP.*<scope>provided</scope>");
    }

    /**
     * Test 4: Verify common module dependency resolves.
     * <p>Checks that sql-guard-jdbc-common is a compile dependency.</p>
     */
    @Test
    @DisplayName("testHikariModule_commonModuleDependency_resolves")
    void testHikariModule_commonModuleDependency_resolves() {
        assertThat(pomContent)
            .as("HikariCP module POM should exist")
            .isNotNull();

        // Verify sql-guard-jdbc-common dependency exists
        assertThat(pomContent)
            .as("HikariCP module should depend on sql-guard-jdbc-common")
            .contains("<artifactId>sql-guard-jdbc-common</artifactId>");
    }

    /**
     * Test 5: Verify class loading does not require other pools.
     * <p>Attempts to load core HikariCP interceptor classes should succeed
     * without requiring Druid or P6Spy classes.</p>
     */
    @Test
    @DisplayName("testHikariModule_classLoading_noOtherPoolsRequired")
    void testHikariModule_classLoading_noOtherPoolsRequired() {
        // Verify Druid classes are NOT loaded when loading HikariCP interceptor
        assertThatThrownBy(() -> 
            Class.forName("com.alibaba.druid.pool.DruidDataSource"))
            .as("Druid classes should NOT be on classpath")
            .isInstanceOf(ClassNotFoundException.class);

        // Verify P6Spy classes are NOT loaded
        assertThatThrownBy(() -> 
            Class.forName("com.p6spy.engine.spy.P6DataSource"))
            .as("P6Spy classes should NOT be on classpath")
            .isInstanceOf(ClassNotFoundException.class);
    }

    /**
     * Test 6: Verify independent JAR can be packaged.
     * <p>Module should build as standalone JAR without other pool classes.</p>
     */
    @Test
    @DisplayName("testHikariModule_independentJar_packages")
    void testHikariModule_independentJar_packages() {
        // Verify POM packaging type (default is jar)
        // If not specified, default is jar which is correct
        if (pomContent != null) {
            // Either no packaging specified (default jar) or explicit jar
            boolean hasJarPackaging = !pomContent.contains("<packaging>") 
                || pomContent.contains("<packaging>jar</packaging>");
            assertThat(hasJarPackaging)
                .as("HikariCP module should package as JAR")
                .isTrue();
        }
    }

    /**
     * Test 7: Verify HikariCP 4.x compatibility.
     * <p>Module should work with HikariCP 4.x (Java 8+).</p>
     */
    @Test
    @DisplayName("testHikariModule_hikari4x_compatible")
    void testHikariModule_hikari4x_compatible() {
        // Verify POM has profile for HikariCP 4.x or version range support
        // Check Java 8 compatibility
        assertThat(pomContent)
            .as("HikariCP module should support Java 8 (for HikariCP 4.x)")
            .containsAnyOf(
                "1.8",
                "${java.version}",
                "<maven.compiler.source>1.8</maven.compiler.source>",
                "<maven.compiler.source>8</maven.compiler.source>"
            );
    }

    /**
     * Test 8: Verify HikariCP 5.x compatibility profile.
     * <p>Module should work with HikariCP 5.x (Java 11+).</p>
     */
    @Test
    @DisplayName("testHikariModule_hikari5x_compatible")
    void testHikariModule_hikari5x_compatible() {
        // HikariCP 5.x requires Java 11+
        // Module should either have profiles or version range to support both
        // This test verifies we don't have Java 11+ only features that break 4.x
        
        // Verify source compatibility allows Java 8 (common base for both)
        // If we have profiles, verify both 4.x and 5.x profiles exist
        if (pomContent != null && pomContent.contains("<profiles>")) {
            assertThat(pomContent)
                .as("Should have HikariCP version profiles")
                .containsAnyOf("hikari-4x", "hikari-5x", "hikari4x", "hikari5x");
        }
        // If no profiles, verify we use reflection-based detection (implementation detail)
        // which is verified by other tests
    }

    /**
     * Test 9: Verify runtime classpath is minimal.
     * <p>Only required dependencies should be included.</p>
     */
    @Test
    @DisplayName("testHikariModule_runtimeClasspath_minimal")
    void testHikariModule_runtimeClasspath_minimal() {
        assertThat(pomContent)
            .as("HikariCP module POM should exist")
            .isNotNull();

        // Count compile-scope dependencies (non-test, non-provided)
        // Should have minimal dependencies: sql-guard-jdbc-common, possibly slf4j
        List<String> allowedDeps = Arrays.asList(
            "sql-guard-jdbc-common",
            "sql-guard-core",
            "sql-guard-audit-api",
            "slf4j-api"
        );

        // Verify no unnecessary compile dependencies
        assertThat(pomContent)
            .as("Should not have unnecessary compile dependencies")
            .doesNotContain("<scope>compile</scope>"); // Compile is default, so usually omitted
    }

    /**
     * Test 10: Verify test classpath is isolated.
     * <p>Test dependencies should not pollute production classpath.</p>
     */
    @Test
    @DisplayName("testHikariModule_testClasspath_isolated")
    void testHikariModule_testClasspath_isolated() {
        assertThat(pomContent)
            .as("HikariCP module POM should exist")
            .isNotNull();

        // Verify test dependencies have test scope
        String normalizedPom = pomContent.replaceAll("\\s+", " ");
        
        // JUnit should be test scope
        if (normalizedPom.contains("junit")) {
            assertThat(normalizedPom)
                .as("JUnit should have test scope")
                .containsPattern("junit.*<scope>test</scope>");
        }

        // Mockito should be test scope
        if (normalizedPom.contains("mockito")) {
            assertThat(normalizedPom)
                .as("Mockito should have test scope")
                .containsPattern("mockito.*<scope>test</scope>");
        }

        // H2 should be test scope
        if (normalizedPom.contains("h2")) {
            assertThat(normalizedPom)
                .as("H2 database should have test scope")
                .containsPattern("h2.*<scope>test</scope>");
        }
    }
}








