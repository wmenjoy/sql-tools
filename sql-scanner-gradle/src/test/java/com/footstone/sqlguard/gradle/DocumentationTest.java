package com.footstone.sqlguard.gradle;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify documentation completeness.
 * 
 * @author SQL Safety Guard Team
 */
class DocumentationTest {

    private static final Path PROJECT_ROOT = Paths.get("").toAbsolutePath();
    private static final Path README_PATH = PROJECT_ROOT.resolve("README.md");
    private static final Path USAGE_EXAMPLES_PATH = PROJECT_ROOT.resolve("docs/usage-examples.md");

    @Test
    void testReadme_shouldExist() {
        // Then
        assertTrue(Files.exists(README_PATH),
            "README.md should exist in project root");
        assertTrue(Files.isRegularFile(README_PATH),
            "README.md should be a regular file");
    }

    @Test
    void testReadme_shouldContainQuickStart() throws IOException {
        // Given
        String content = readFileAsString(README_PATH);

        // Then
        assertTrue(content.contains("Quick Start"),
            "README should contain Quick Start section");
        assertTrue(content.contains("Apply Plugin"),
            "README should contain plugin application instructions");
        assertTrue(content.contains("gradle sqlguardScan"),
            "README should contain task execution command");
    }

    @Test
    void testReadme_shouldContainDslMethods() throws IOException {
        // Given
        String content = readFileAsString(README_PATH);

        // Then
        assertTrue(content.contains("DSL Methods"),
            "README should contain DSL Methods section");
        assertTrue(content.contains("console()"),
            "README should document console() method");
        assertTrue(content.contains("html()"),
            "README should document html() method");
        assertTrue(content.contains("both()"),
            "README should document both() method");
        assertTrue(content.contains("failOnCritical()"),
            "README should document failOnCritical() method");
    }

    @Test
    void testReadme_shouldContainConfigurationTable() throws IOException {
        // Given
        String content = readFileAsString(README_PATH);

        // Then
        assertTrue(content.contains("Configuration DSL"),
            "README should contain Configuration DSL section");
        assertTrue(content.contains("projectPath"),
            "README should document projectPath property");
        assertTrue(content.contains("configFile"),
            "README should document configFile property");
        assertTrue(content.contains("outputFormat"),
            "README should document outputFormat property");
        assertTrue(content.contains("outputFile"),
            "README should document outputFile property");
        assertTrue(content.contains("failOnCritical"),
            "README should document failOnCritical property");
    }

    @Test
    void testReadme_shouldContainUsageExamples() throws IOException {
        // Given
        String content = readFileAsString(README_PATH);

        // Then
        assertTrue(content.contains("Usage Examples"),
            "README should contain Usage Examples section");
        assertTrue(content.contains("Example"),
            "README should contain example configurations");
    }

    @Test
    void testReadme_shouldContainTroubleshooting() throws IOException {
        // Given
        String content = readFileAsString(README_PATH);

        // Then
        assertTrue(content.contains("Troubleshooting"),
            "README should contain Troubleshooting section");
        assertTrue(content.contains("Issue:"),
            "README should contain troubleshooting issues");
        assertTrue(content.contains("Solution:"),
            "README should contain troubleshooting solutions");
    }

    @Test
    void testReadme_shouldContainRequirements() throws IOException {
        // Given
        String content = readFileAsString(README_PATH);

        // Then
        assertTrue(content.contains("Requirements"),
            "README should contain Requirements section");
        assertTrue(content.contains("Gradle"),
            "README should specify Gradle requirement");
        assertTrue(content.contains("Java"),
            "README should specify Java requirement");
    }

    @Test
    void testReadme_shouldHaveMinimumLength() throws IOException {
        // Given
        String content = readFileAsString(README_PATH);

        // Then
        assertTrue(content.length() > 5000,
            "README should be comprehensive (at least 5000 characters)");
    }

    @Test
    void testUsageExamples_shouldExist() {
        // Then
        assertTrue(Files.exists(USAGE_EXAMPLES_PATH),
            "usage-examples.md should exist in docs directory");
        assertTrue(Files.isRegularFile(USAGE_EXAMPLES_PATH),
            "usage-examples.md should be a regular file");
    }

    @Test
    void testUsageExamples_shouldContainMultipleExamples() throws IOException {
        // Given
        String content = readFileAsString(USAGE_EXAMPLES_PATH);

        // Then
        assertTrue(content.contains("Example 1:"),
            "Usage examples should contain Example 1");
        assertTrue(content.contains("Example 2:"),
            "Usage examples should contain Example 2");
        assertTrue(content.contains("Example 3:"),
            "Usage examples should contain Example 3");
        
        // Count examples
        long exampleCount = countMatchingLines(content, "###\\s+Example\\s+\\d+:.*");
        assertTrue(exampleCount >= 10,
            "Usage examples should contain at least 10 examples, found: " + exampleCount);
    }

    @Test
    void testUsageExamples_shouldContainCiCdExamples() throws IOException {
        // Given
        String content = readFileAsString(USAGE_EXAMPLES_PATH);

        // Then
        assertTrue(content.contains("CI/CD"),
            "Usage examples should contain CI/CD section");
        assertTrue(content.contains("Jenkins") || content.contains("GitLab") || content.contains("GitHub"),
            "Usage examples should contain CI/CD platform examples");
    }

    @Test
    void testUsageExamples_shouldContainCodeBlocks() throws IOException {
        // Given
        String content = readFileAsString(USAGE_EXAMPLES_PATH);

        // Then
        long codeBlockCount = countMatchingLines(content, "^```.*");
        assertTrue(codeBlockCount >= 20,
            "Usage examples should contain multiple code blocks (at least 20), found: " + codeBlockCount);
    }

    @Test
    void testUsageExamples_shouldHaveMinimumLength() throws IOException {
        // Given
        String content = readFileAsString(USAGE_EXAMPLES_PATH);

        // Then
        assertTrue(content.length() > 10000,
            "Usage examples should be comprehensive (at least 10000 characters)");
    }

    @Test
    void testDocumentation_shouldBeWellFormatted() throws IOException {
        // Given
        String readmeContent = readFileAsString(README_PATH);
        String examplesContent = readFileAsString(USAGE_EXAMPLES_PATH);

        // Then: Check for proper markdown formatting
        assertTrue(readmeContent.contains("##"),
            "README should use proper heading levels");
        assertTrue(examplesContent.contains("##"),
            "Usage examples should use proper heading levels");
        
        // Check for proper code block formatting
        assertTrue(readmeContent.contains("```groovy"),
            "README should contain Groovy code blocks");
        assertTrue(examplesContent.contains("```groovy"),
            "Usage examples should contain Groovy code blocks");
    }

    /**
     * Helper method to read file as string (JDK 8 compatible).
     */
    private String readFileAsString(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Helper method to count matching lines (JDK 8 compatible).
     */
    private long countMatchingLines(String content, String regex) {
        String[] lines = content.split("\n");
        long count = 0;
        for (String line : lines) {
            if (line.matches(regex)) {
                count++;
            }
        }
        return count;
    }
}
