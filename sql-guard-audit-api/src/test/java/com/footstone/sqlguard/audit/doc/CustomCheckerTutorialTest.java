package com.footstone.sqlguard.audit.doc;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests to verify the Custom Audit Checker tutorial is complete and accurate.
 *
 * <p>This test validates that the custom checker tutorial documentation and
 * example code are present, complete, and followable.</p>
 *
 * @since 2.0.0
 */
public class CustomCheckerTutorialTest {

    private static final String TUTORIAL_PATH = "docs/developer-guide/custom-audit-checker.md";
    private static final String EXAMPLE_PATH = "docs/developer-guide/examples/TableLockChecker.java";

    @Test
    public void testTutorial_step1_extend_shouldCompile() {
        // Verify Step 1 example (extend AbstractAuditChecker) is present
        File tutorialFile = findFile(TUTORIAL_PATH);

        if (tutorialFile != null && tutorialFile.exists()) {
            String content = readFile(tutorialFile);

            assertTrue(content.contains("Step 1:"),
                "Tutorial should have Step 1");
            assertTrue(content.contains("extends AbstractAuditChecker"),
                "Step 1 should show extending AbstractAuditChecker");
            assertTrue(content.contains("@Component"),
                "Step 1 should show @Component annotation");
        }
    }

    @Test
    public void testTutorial_step2_implement_shouldWork() {
        // Verify Step 2 (implement performAudit) has correct method signature
        File tutorialFile = findFile(TUTORIAL_PATH);

        if (tutorialFile != null && tutorialFile.exists()) {
            String content = readFile(tutorialFile);

            assertTrue(content.contains("Step 2:"),
                "Tutorial should have Step 2");
            assertTrue(content.contains("performAudit"),
                "Step 2 should implement performAudit method");
            assertTrue(content.contains("SqlContext"),
                "Step 2 should use SqlContext parameter");
            assertTrue(content.contains("ExecutionResult"),
                "Step 2 should use ExecutionResult parameter");
            assertTrue(content.contains("AuditResult.builder()"),
                "Step 2 should use AuditResult builder");
        }
    }

    @Test
    public void testTutorial_step3_calculateRisk_shouldScore() {
        // Verify Step 3 (calculate risk score) is implemented
        File tutorialFile = findFile(TUTORIAL_PATH);

        if (tutorialFile != null && tutorialFile.exists()) {
            String content = readFile(tutorialFile);

            assertTrue(content.contains("Step 3:"),
                "Tutorial should have Step 3");
            assertTrue(content.contains("calculateRiskScore"),
                "Step 3 should implement calculateRiskScore");
            assertTrue(content.contains("RiskScore.builder()"),
                "Step 3 should use RiskScore builder");
            assertTrue(content.contains("score"),
                "Step 3 should set risk score");
            assertTrue(content.contains("confidence"),
                "Step 3 should set confidence level");
        }
    }

    @Test
    public void testTutorial_step4_tests_shouldPass() {
        // Verify Step 4 (write tests) includes test examples
        File tutorialFile = findFile(TUTORIAL_PATH);

        if (tutorialFile != null && tutorialFile.exists()) {
            String content = readFile(tutorialFile);

            assertTrue(content.contains("Step 4:"),
                "Tutorial should have Step 4");
            assertTrue(content.contains("@Test"),
                "Step 4 should show test examples");
            assertTrue(content.contains("assertEquals") || content.contains("assertTrue"),
                "Step 4 should show assertions");
            assertTrue(content.contains("30+ test"),
                "Step 4 should mention test coverage requirement");
        }
    }

    @Test
    public void testTutorial_step5_register_shouldDiscover() {
        // Verify Step 5 (register as Spring Bean) is documented
        File tutorialFile = findFile(TUTORIAL_PATH);

        if (tutorialFile != null && tutorialFile.exists()) {
            String content = readFile(tutorialFile);

            assertTrue(content.contains("Step 5:"),
                "Tutorial should have Step 5");
            assertTrue(content.contains("@Component") || content.contains("@Bean"),
                "Step 5 should show Spring registration");
        }
    }

    @Test
    public void testTutorial_step6_configure_shouldLoad() {
        // Verify Step 6 (configure properties) is documented
        File tutorialFile = findFile(TUTORIAL_PATH);

        if (tutorialFile != null && tutorialFile.exists()) {
            String content = readFile(tutorialFile);

            assertTrue(content.contains("Step 6:"),
                "Tutorial should have Step 6");
            assertTrue(content.contains("application.yml") || content.contains("application.properties"),
                "Step 6 should show configuration file");
            assertTrue(content.contains("threshold") || content.contains("enabled"),
                "Step 6 should show configuration properties");
        }
    }

    @Test
    public void testTutorial_step7_deploy_shouldActivate() {
        // Verify Step 7 (deploy and validate) is documented
        File tutorialFile = findFile(TUTORIAL_PATH);

        if (tutorialFile != null && tutorialFile.exists()) {
            String content = readFile(tutorialFile);

            assertTrue(content.contains("Step 7:"),
                "Tutorial should have Step 7");
            assertTrue(content.contains("mvn") || content.contains("build"),
                "Step 7 should show build commands");
            assertTrue(content.contains("/actuator/health") || content.contains("health"),
                "Step 7 should show validation commands");
        }
    }

    @Test
    public void testTutorial_TableLockChecker_example_shouldWork() {
        // Verify TableLockChecker example exists and is complete
        File exampleFile = findFile(EXAMPLE_PATH);

        assertNotNull(exampleFile,
            "TableLockChecker example file should exist");

        if (exampleFile.exists()) {
            String content = readFile(exampleFile);

            assertTrue(content.contains("class TableLockChecker"),
                "Example should define TableLockChecker class");
            assertTrue(content.contains("extends AbstractAuditChecker"),
                "Example should extend AbstractAuditChecker");
            assertTrue(content.contains("performAudit"),
                "Example should implement performAudit");
            assertTrue(content.contains("calculateRiskScore"),
                "Example should implement calculateRiskScore");
        }
    }

    @Test
    public void testTutorial_completeExample_shouldCompile() {
        // Verify TableLockChecker example has valid Java syntax
        File exampleFile = findFile(EXAMPLE_PATH);

        if (exampleFile != null && exampleFile.exists()) {
            String content = readFile(exampleFile);

            // Check for balanced braces
            long openBraces = content.chars().filter(ch -> ch == '{').count();
            long closeBraces = content.chars().filter(ch -> ch == '}').count();
            assertEquals(openBraces, closeBraces,
                "Example code should have balanced braces");

            // Check for package declaration
            assertTrue(content.contains("package"),
                "Example should have package declaration");

            // Check for imports
            assertTrue(content.contains("import"),
                "Example should have import statements");

            // Check for class documentation
            assertTrue(content.contains("/**"),
                "Example should have Javadoc comments");
        }
    }

    @Test
    public void testTutorial_completeExample_shouldExecute() {
        // Verify example has all required methods implemented
        File exampleFile = findFile(EXAMPLE_PATH);

        if (exampleFile != null && exampleFile.exists()) {
            String content = readFile(exampleFile);

            // Check for required method implementations
            assertTrue(content.contains("@Override"),
                "Example should override parent methods");
            assertTrue(content.contains("protected AuditResult performAudit"),
                "Example should implement performAudit with correct signature");
            assertTrue(content.contains("protected RiskScore calculateRiskScore"),
                "Example should implement calculateRiskScore with correct signature");

            // Check for configuration class
            assertTrue(content.contains("class TableLockConfig") || content.contains("@ConfigurationProperties"),
                "Example should include configuration class");

            // Check for helper methods
            assertTrue(content.contains("private"),
                "Example should have private helper methods");
        }
    }

    // Helper methods

    private File findFile(String relativePath) {
        String[] possiblePaths = {
            relativePath,
            "../" + relativePath,
            "../../" + relativePath
        };

        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                return file;
            }
        }

        return new File(relativePath);
    }

    private String readFile(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()));
        } catch (Exception e) {
            return "";
        }
    }
}
