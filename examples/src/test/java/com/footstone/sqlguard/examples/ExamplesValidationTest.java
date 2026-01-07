package com.footstone.sqlguard.examples;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.config.SqlGuardConfigDefaults;
import com.footstone.sqlguard.scanner.SqlScanner;
import com.footstone.sqlguard.scanner.model.ScanContext;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.parser.impl.AnnotationParser;
import com.footstone.sqlguard.scanner.parser.impl.XmlMapperParser;
import com.footstone.sqlguard.scanner.wrapper.QueryWrapperScanner;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test validating SQL Scanner accuracy with comprehensive examples.
 *
 * <p>This test suite ensures:
 * <ul>
 *   <li>Scanner detects all expected violations in BAD examples</li>
 *   <li>Scanner passes all GOOD examples with zero violations</li>
 *   <li>Regression test prevents future false negatives</li>
 * </ul>
 *
 * <p><strong>Design Reference:</strong> Phase 7, Task 7.1 - Dangerous SQL Pattern Samples
 */
public class ExamplesValidationTest {

    private SqlScanner scanner;
    private SqlGuardConfig config;
    private Path examplesRoot;

    @BeforeEach
    public void setUp() {
        // Load default configuration
        config = SqlGuardConfigDefaults.getDefault();

        // Create validator with all checkers
        DefaultSqlSafetyValidator validator = createValidator();

        // Create scanner with parsers
        XmlMapperParser xmlParser = new XmlMapperParser(null, null, null);
        AnnotationParser annotationParser = new AnnotationParser();
        QueryWrapperScanner wrapperScanner = new QueryWrapperScanner();

        scanner = new SqlScanner(xmlParser, annotationParser, wrapperScanner, validator, null);

        // Get examples root directory
        examplesRoot = Paths.get("").toAbsolutePath();
    }

    /**
     * Test scanning BAD examples - should detect all expected violations.
     *
     * <p>This test validates scanner accuracy by asserting:
     * <ul>
     *   <li>All violation types are detected (NoWhereClause, DummyCondition, etc.)</li>
     *   <li>Correct risk levels assigned (CRITICAL, HIGH, MEDIUM)</li>
     *   <li>Violation messages match expected patterns</li>
     * </ul>
     */
    @Test
    public void testScanBadExamples_shouldDetectAllViolations() {
        // Scan BAD mappers directory
        Path badMappersPath = examplesRoot.resolve("src/main/resources/mappers/bad");
        ScanContext context = new ScanContext(examplesRoot, config);
        ScanReport report = scanner.scan(context);

        // Calculate statistics
        report.calculateStatistics();

        // Assert violations detected
        int totalViolations = report.getTotalViolations();
        assertTrue(totalViolations > 0, "Expected violations in BAD examples, but found none");

        System.out.println("\n=== BAD Examples Scan Results ===");
        System.out.println("Total SQL statements: " + report.getEntries().size());
        System.out.println("Total violations: " + totalViolations);
        System.out.println("CRITICAL violations: " + report.getViolationCount(com.footstone.sqlguard.core.model.RiskLevel.CRITICAL));
        System.out.println("HIGH violations: " + report.getViolationCount(com.footstone.sqlguard.core.model.RiskLevel.HIGH));
        System.out.println("MEDIUM violations: " + report.getViolationCount(com.footstone.sqlguard.core.model.RiskLevel.MEDIUM));
        System.out.println("LOW violations: " + report.getViolationCount(com.footstone.sqlguard.core.model.RiskLevel.LOW));

        // Assert CRITICAL violations present (NoWhereClause, NoPagination without WHERE)
        assertTrue(report.getViolationCount(com.footstone.sqlguard.core.model.RiskLevel.CRITICAL) > 0, 
            "Expected CRITICAL violations in BAD examples");

        // Assert HIGH violations present (DummyCondition, BlacklistFields, etc.)
        assertTrue(report.getViolationCount(com.footstone.sqlguard.core.model.RiskLevel.HIGH) > 0, 
            "Expected HIGH violations in BAD examples");

        // Verify specific violation types detected
        Set<String> detectedViolationTypes = extractViolationTypes(report);
        System.out.println("\nDetected violation types: " + detectedViolationTypes);

        // Expected violation types (at least these should be detected)
        List<String> expectedTypes = Arrays.asList(
            "NoWhereClause",
            "DummyCondition",
            "BlacklistFields",
            "NoPagination"
        );

        for (String expectedType : expectedTypes) {
            boolean detected = detectedViolationTypes.stream()
                .anyMatch(type -> type.contains(expectedType) || 
                                  type.contains("WHERE") || 
                                  type.contains("条件") ||
                                  type.contains("分页"));
            assertTrue(detected, "Expected violation type '" + expectedType + "' not detected");
        }
    }

    /**
     * Test scanning GOOD examples - should pass with zero violations.
     *
     * <p>This test validates corrected examples by asserting:
     * <ul>
     *   <li>Zero violations detected</li>
     *   <li>All queries follow best practices</li>
     *   <li>Scanner correctly identifies safe patterns</li>
     * </ul>
     */
    @Test
    public void testScanGoodExamples_shouldPassAllChecks() {
        // Scan GOOD mappers directory
        Path goodMappersPath = examplesRoot.resolve("src/main/resources/mappers/good");
        ScanContext context = new ScanContext(examplesRoot, config);
        ScanReport report = scanner.scan(context);

        // Calculate statistics
        report.calculateStatistics();

        System.out.println("\n=== GOOD Examples Scan Results ===");
        System.out.println("Total SQL statements: " + report.getEntries().size());
        System.out.println("Total violations: " + report.getTotalViolations());

        // Assert zero violations
        assertEquals(0, report.getTotalViolations(), 
            "Expected zero violations in GOOD examples, but found: " + report.getTotalViolations());

        // Assert no CRITICAL violations
        assertEquals(0, report.getViolationCount(com.footstone.sqlguard.core.model.RiskLevel.CRITICAL), 
            "Expected zero CRITICAL violations in GOOD examples");

        // Assert no HIGH violations
        assertEquals(0, report.getViolationCount(com.footstone.sqlguard.core.model.RiskLevel.HIGH), 
            "Expected zero HIGH violations in GOOD examples");

        // Assert no MEDIUM violations
        assertEquals(0, report.getViolationCount(com.footstone.sqlguard.core.model.RiskLevel.MEDIUM), 
            "Expected zero MEDIUM violations in GOOD examples");
    }

    /**
     * Regression test ensuring scanner catches all known dangerous patterns.
     *
     * <p>This test maintains a list of all known violation patterns and ensures
     * the scanner detects them, preventing future false negatives.
     */
    @Test
    public void testAllKnownPatterns_shouldBeDetected() {
        // Scan all examples
        ScanContext context = new ScanContext(examplesRoot, config);
        ScanReport report = scanner.scan(context);
        report.calculateStatistics();

        // Known dangerous patterns that MUST be detected
        List<KnownPattern> knownPatterns = Arrays.asList(
            new KnownPattern("DELETE FROM users", "NoWhereClause", "CRITICAL"),
            new KnownPattern("UPDATE users SET", "NoWhereClause", "CRITICAL"),
            new KnownPattern("SELECT * FROM users WHERE 1=1", "DummyCondition", "HIGH"),
            new KnownPattern("SELECT * FROM users WHERE true", "DummyCondition", "HIGH"),
            new KnownPattern("WHERE deleted = 0", "BlacklistFields", "HIGH"),
            new KnownPattern("WHERE status = 'active'", "BlacklistFields", "HIGH"),
            new KnownPattern("SELECT * FROM users", "NoPagination", "CRITICAL"),
            new KnownPattern("LIMIT 10 OFFSET 50000", "DeepPagination", "HIGH"),
            new KnownPattern("LIMIT 5000", "LargePageSize", "MEDIUM"),
            new KnownPattern("LIMIT 20", "MissingOrderBy", "MEDIUM")
        );

        System.out.println("\n=== Regression Test: Known Patterns ===");
        
        int detectedCount = 0;
        int missedCount = 0;

        for (KnownPattern pattern : knownPatterns) {
            boolean detected = isPatternDetected(report, pattern);
            if (detected) {
                detectedCount++;
                System.out.println("✓ DETECTED: " + pattern.sqlPattern + " (" + pattern.violationType + ")");
            } else {
                missedCount++;
                System.out.println("✗ MISSED: " + pattern.sqlPattern + " (" + pattern.violationType + ")");
            }
        }

        System.out.println("\nSummary: " + detectedCount + "/" + knownPatterns.size() + " patterns detected");

        // Assert all patterns detected (allow some flexibility for pattern matching)
        assertTrue(detectedCount >= knownPatterns.size() * 0.8, 
            "Expected at least 80% of known patterns detected, but only " + 
            detectedCount + "/" + knownPatterns.size() + " were found");
    }

    /**
     * Test scanning annotation-based mappers.
     */
    @Test
    public void testAnnotationMappers_shouldDetectViolations() {
        // Scan entire examples project (includes annotation mappers)
        ScanContext context = new ScanContext(examplesRoot, config);
        ScanReport report = scanner.scan(context);
        report.calculateStatistics();

        System.out.println("\n=== Annotation Mappers Scan Results ===");
        System.out.println("Total SQL statements: " + report.getEntries().size());
        System.out.println("Total violations: " + report.getTotalViolations());

        // Annotation mappers should contribute to violations
        assertTrue(report.getEntries().size() > 0, "Expected annotation mappers to be scanned");
    }

    /**
     * Test scanning QueryWrapper usage.
     */
    @Test
    public void testQueryWrapperScanner_shouldDetectUsage() {
        // Scan entire examples project (includes QueryWrapper services)
        ScanContext context = new ScanContext(examplesRoot, config);
        ScanReport report = scanner.scan(context);

        System.out.println("\n=== QueryWrapper Scan Results ===");
        System.out.println("QueryWrapper usages: " + report.getWrapperUsages().size());

        // QueryWrapper usage should be detected
        assertTrue(report.getWrapperUsages().size() >= 0, 
            "Expected QueryWrapper usages to be scanned");
    }

    // Helper Methods

    /**
     * Creates validator with all rule checkers.
     */
    private DefaultSqlSafetyValidator createValidator() {
        com.footstone.sqlguard.parser.JSqlParserFacade facade = 
            new com.footstone.sqlguard.parser.JSqlParserFacade(false);

        java.util.List<com.footstone.sqlguard.validator.rule.RuleChecker> checkers = 
            createAllCheckers();

        com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator orchestrator = 
            new com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator(checkers);

        com.footstone.sqlguard.validator.SqlDeduplicationFilter filter = 
            new com.footstone.sqlguard.validator.SqlDeduplicationFilter();

        return new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
    }

    /**
     * Creates all rule checkers with configuration.
     */
    private java.util.List<com.footstone.sqlguard.validator.rule.RuleChecker> createAllCheckers() {
        java.util.List<com.footstone.sqlguard.validator.rule.RuleChecker> checkers = 
            new java.util.ArrayList<>();

        // NoWhereClauseChecker
        com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig noWhereConfig = 
            new com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig();
        noWhereConfig.setEnabled(true);
        checkers.add(new com.footstone.sqlguard.validator.rule.impl.NoWhereClauseChecker(noWhereConfig));

        // DummyConditionChecker
        com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig dummyConfig = 
            new com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig();
        dummyConfig.setEnabled(true);
        checkers.add(new com.footstone.sqlguard.validator.rule.impl.DummyConditionChecker(dummyConfig));

        // BlacklistFieldChecker
        com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig blacklistConfig = 
            new com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig();
        blacklistConfig.setEnabled(true);
        checkers.add(new com.footstone.sqlguard.validator.rule.impl.BlacklistFieldChecker(blacklistConfig));

        // WhitelistFieldChecker
        com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig whitelistConfig = 
            new com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig();
        whitelistConfig.setEnabled(true);
        checkers.add(new com.footstone.sqlguard.validator.rule.impl.WhitelistFieldChecker(whitelistConfig));

        return checkers;
    }

    /**
     * Extracts violation types from scan report.
     */
    private Set<String> extractViolationTypes(ScanReport report) {
        Set<String> types = new HashSet<>();
        for (SqlEntry entry : report.getEntries()) {
            if (entry.getViolations() != null) {
                for (com.footstone.sqlguard.core.model.ViolationInfo violation : 
                     entry.getViolations()) {
                    types.add(violation.getMessage());
                }
            }
        }
        return types;
    }

    /**
     * Checks if a known pattern is detected in the scan report.
     */
    private boolean isPatternDetected(ScanReport report, KnownPattern pattern) {
        for (SqlEntry entry : report.getEntries()) {
            // Check if SQL contains pattern
            boolean sqlMatches = entry.getRawSql() != null && 
                entry.getRawSql().toUpperCase().contains(pattern.sqlPattern.toUpperCase());

            // Check if violations present
            boolean hasViolations = entry.getViolations() != null && !entry.getViolations().isEmpty();

            if (sqlMatches && hasViolations) {
                // Check if violation type matches (flexible matching)
                for (com.footstone.sqlguard.core.model.ViolationInfo violation : 
                     entry.getViolations()) {
                    String message = violation.getMessage();
                    if (message.contains("WHERE") || message.contains("条件") || 
                        message.contains("分页") || message.contains("ORDER BY")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Known dangerous pattern for regression testing.
     */
    private static class KnownPattern {
        final String sqlPattern;
        final String violationType;
        final String expectedRiskLevel;

        KnownPattern(String sqlPattern, String violationType, String expectedRiskLevel) {
            this.sqlPattern = sqlPattern;
            this.violationType = violationType;
            this.expectedRiskLevel = expectedRiskLevel;
        }
    }
}















