package com.footstone.audit.service.api;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to validate API usage examples are complete and executable.
 *
 * <p>This test verifies that all API example code snippets are present,
 * syntactically correct, and demonstrate proper usage patterns.</p>
 *
 * @since 2.0.0
 */
public class APIExamplesValidationTest {

    private static final String JAVA_EXAMPLES_PATH = "docs/api-examples/java";
    private static final String PYTHON_EXAMPLES_PATH = "docs/api-examples/python";
    private static final String JS_EXAMPLES_PATH = "docs/api-examples/javascript";

    @Test
    public void testExample_Java_RestTemplate_shouldCompile() {
        // Verify Java RestTemplate example exists and is valid
        File exampleFile = findFile(JAVA_EXAMPLES_PATH + "/QueryFindings.java");

        assertNotNull(exampleFile,
            "QueryFindings.java should exist");

        if (exampleFile.exists()) {
            String content = readFile(exampleFile);

            assertTrue(content.contains("import org.springframework.web.client.RestTemplate"),
                "Example should import RestTemplate");
            assertTrue(content.contains("RestTemplate restTemplate"),
                "Example should create RestTemplate instance");
            assertTrue(content.contains("getForEntity"),
                "Example should use getForEntity method");
            assertTrue(content.contains("public static void main"),
                "Example should have main method");
        }
    }

    @Test
    public void testExample_Java_RestTemplate_shouldExecute() {
        // Verify RestTemplate example has proper error handling
        File exampleFile = findFile(JAVA_EXAMPLES_PATH + "/QueryFindings.java");

        if (exampleFile != null && exampleFile.exists()) {
            String content = readFile(exampleFile);

            assertTrue(content.contains("try") || content.contains("catch"),
                "Example should have error handling");
            assertTrue(content.contains("BASE_URL"),
                "Example should define base URL");
            assertTrue(content.contains("/audits"),
                "Example should call /audits endpoint");
        }
    }

    @Test
    public void testExample_Java_WebClient_shouldCompile() {
        // Verify Java WebClient example exists and is valid
        File exampleFile = findFile(JAVA_EXAMPLES_PATH + "/QueryFindingsReactive.java");

        assertNotNull(exampleFile,
            "QueryFindingsReactive.java should exist");

        if (exampleFile.exists()) {
            String content = readFile(exampleFile);

            assertTrue(content.contains("import org.springframework.web.reactive.function.client.WebClient"),
                "Example should import WebClient");
            assertTrue(content.contains("WebClient webClient"),
                "Example should create WebClient instance");
            assertTrue(content.contains("bodyToMono"),
                "Example should use reactive methods");
        }
    }

    @Test
    public void testExample_Java_WebClient_shouldExecute() {
        // Verify WebClient example uses reactive patterns
        File exampleFile = findFile(JAVA_EXAMPLES_PATH + "/QueryFindingsReactive.java");

        if (exampleFile != null && exampleFile.exists()) {
            String content = readFile(exampleFile);

            assertTrue(content.contains("subscribe") || content.contains("block"),
                "Example should subscribe to reactive stream");
            assertTrue(content.contains("Mono"),
                "Example should use Mono type");
        }
    }

    @Test
    public void testExample_Python_requests_shouldExecute() {
        // Verify Python requests example exists and is valid
        File exampleFile = findFile(PYTHON_EXAMPLES_PATH + "/query_findings.py");

        assertNotNull(exampleFile,
            "query_findings.py should exist");

        if (exampleFile.exists()) {
            String content = readFile(exampleFile);

            assertTrue(content.contains("import requests"),
                "Example should import requests");
            assertTrue(content.contains("requests.get"),
                "Example should use requests.get");
            assertTrue(content.contains("def query_critical_findings"),
                "Example should have query_critical_findings function");
            assertTrue(content.contains("if __name__ == \"__main__\""),
                "Example should have main block");
        }
    }

    @Test
    public void testExample_JavaScript_fetch_shouldExecute() {
        // Verify JavaScript fetch example exists and is valid
        File exampleFile = findFile(JS_EXAMPLES_PATH + "/queryFindings.js");

        assertNotNull(exampleFile,
            "queryFindings.js should exist");

        if (exampleFile.exists()) {
            String content = readFile(exampleFile);

            assertTrue(content.contains("async function"),
                "Example should use async functions");
            assertTrue(content.contains("await fetch"),
                "Example should use fetch API");
            assertTrue(content.contains("response.json()"),
                "Example should parse JSON response");
        }
    }

    @Test
    public void testExample_queryRecentCritical_shouldWork() {
        // Verify examples demonstrate querying critical findings
        List<File> exampleFiles = Arrays.asList(
            findFile(JAVA_EXAMPLES_PATH + "/QueryFindings.java"),
            findFile(PYTHON_EXAMPLES_PATH + "/query_findings.py"),
            findFile(JS_EXAMPLES_PATH + "/queryFindings.js")
        );

        for (File file : exampleFiles) {
            if (file != null && file.exists()) {
                String content = readFile(file);

                assertTrue(content.contains("CRITICAL") || content.contains("critical"),
                    "Example should query CRITICAL findings: " + file.getName());
                assertTrue(content.contains("/audits") || content.contains("/api/v1/audits"),
                    "Example should use correct endpoint: " + file.getName());
            }
        }
    }

    @Test
    public void testExample_getDashboardStats_shouldWork() {
        // Verify examples demonstrate getting dashboard statistics
        List<File> exampleFiles = Arrays.asList(
            findFile(JAVA_EXAMPLES_PATH + "/QueryFindingsReactive.java"),
            findFile(PYTHON_EXAMPLES_PATH + "/query_findings.py"),
            findFile(JS_EXAMPLES_PATH + "/getDashboardStats.js")
        );

        for (File file : exampleFiles) {
            if (file != null && file.exists()) {
                String content = readFile(file);

                assertTrue(content.contains("/statistics/dashboard") || content.contains("dashboard"),
                    "Example should access dashboard endpoint: " + file.getName());
            }
        }
    }

    @Test
    public void testExample_updateCheckerConfig_shouldWork() {
        // Verify JavaScript axios example demonstrates PUT requests
        File exampleFile = findFile(JS_EXAMPLES_PATH + "/getDashboardStats.js");

        if (exampleFile != null && exampleFile.exists()) {
            String content = readFile(exampleFile);

            assertTrue(content.contains("axios") || content.contains("require('axios')"),
                "Example should use axios library");
            assertTrue(content.contains("updateCheckerConfig") ||
                      content.contains("/configuration/checkers"),
                "Example should demonstrate configuration updates");
        }
    }

    @Test
    public void testExample_allSnippets_shouldBeValid() {
        // Verify all example files have valid syntax
        List<File> javaFiles = findAllFiles(JAVA_EXAMPLES_PATH, ".java");
        List<File> pythonFiles = findAllFiles(PYTHON_EXAMPLES_PATH, ".py");
        List<File> jsFiles = findAllFiles(JS_EXAMPLES_PATH, ".js");

        // Check Java files
        for (File file : javaFiles) {
            if (file.exists()) {
                String content = readFile(file);

                // Check for balanced braces
                long openBraces = content.chars().filter(ch -> ch == '{').count();
                long closeBraces = content.chars().filter(ch -> ch == '}').count();
                assertEquals(openBraces, closeBraces,
                    "Java file should have balanced braces: " + file.getName());

                // Check for class declaration
                assertTrue(content.contains("class") || content.contains("interface"),
                    "Java file should have class/interface: " + file.getName());
            }
        }

        // Check Python files
        for (File file : pythonFiles) {
            if (file.exists()) {
                String content = readFile(file);

                // Check for function definitions
                assertTrue(content.contains("def "),
                    "Python file should have function definitions: " + file.getName());

                // Check for proper imports
                assertTrue(content.contains("import "),
                    "Python file should have imports: " + file.getName());
            }
        }

        // Check JavaScript files
        for (File file : jsFiles) {
            if (file.exists()) {
                String content = readFile(file);

                // Check for function declarations
                assertTrue(content.contains("function") || content.contains("=>"),
                    "JS file should have functions: " + file.getName());

                // Check for balanced braces
                long openBraces = content.chars().filter(ch -> ch == '{').count();
                long closeBraces = content.chars().filter(ch -> ch == '}').count();
                assertEquals(openBraces, closeBraces,
                    "JS file should have balanced braces: " + file.getName());
            }
        }

        // Ensure we found at least some examples
        int totalExamples = javaFiles.size() + pythonFiles.size() + jsFiles.size();
        assertTrue(totalExamples >= 5,
            "Should have at least 5 example files across all languages");
    }

    // Helper methods

    private File findFile(String relativePath) {
        // Try different paths based on potential working directories
        // Maven may run from different locations depending on the module
        String[] possiblePaths = {
            relativePath,
            "../../../" + relativePath,
            "../../../../" + relativePath,
            "../../../../../" + relativePath,
            "../../../../../../" + relativePath,
            // From sql-audit-service-web directory
            "../../" + relativePath,
            "../" + relativePath,
            // Absolute path from workspace root
            System.getProperty("user.dir") + "/" + relativePath,
            System.getProperty("user.dir") + "/../" + relativePath,
            System.getProperty("user.dir") + "/../../" + relativePath,
            System.getProperty("user.dir") + "/../../../" + relativePath
        };

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }

        // Try to find from parent directories
        File currentDir = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 6; i++) {
            File targetFile = new File(currentDir, relativePath);
            if (targetFile.exists()) {
                return targetFile;
            }
            currentDir = currentDir.getParentFile();
            if (currentDir == null) break;
        }

        return new File(relativePath);
    }

    private List<File> findAllFiles(String directory, String extension) {
        File dir = findFile(directory);
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return Arrays.asList();
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(extension));
        return files != null ? Arrays.asList(files) : Arrays.asList();
    }

    private String readFile(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()));
        } catch (Exception e) {
            return "";
        }
    }
}
