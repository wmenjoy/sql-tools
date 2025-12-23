package com.footstone.audit.service.deployment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Operations Guide Validation Tests
 * Validates troubleshooting guide and operations documentation
 */
@DisplayName("Operations Guide Validation Tests")
@Tag("deployment")
@Tag("operations")
public class OperationsGuideValidationTest {

    private static final String DOCS_DIR = "docs";
    private static final String PROJECT_ROOT = System.getProperty("user.dir", ".").replace("/sql-audit-service/sql-audit-service-web", "");

    @Test
    @DisplayName("Test Troubleshooting kafka lag should resolve")
    public void testTroubleshooting_kafkaLag_shouldResolve() throws Exception {
        Path troubleshootingPath = Path.of(PROJECT_ROOT, DOCS_DIR, "operations/troubleshooting-guide.md");
        assertTrue(Files.exists(troubleshootingPath), "Troubleshooting guide should exist");

        String content = Files.readString(troubleshootingPath);

        // Validate Kafka lag troubleshooting
        assertTrue(content.contains("Kafka Consumer Lag") || content.contains("kafka_consumer_lag"),
            "Should document Kafka lag issue");
        assertTrue(content.contains("Solution") || content.contains("Fix"),
            "Should provide solutions");
        assertTrue(content.contains("scale") || content.contains("replicas"),
            "Should suggest scaling");
    }

    @Test
    @DisplayName("Test Troubleshooting clickhouse timeout should resolve")
    public void testTroubleshooting_clickHouseTimeout_shouldResolve() throws Exception {
        Path troubleshootingPath = Path.of(PROJECT_ROOT, DOCS_DIR, "operations/troubleshooting-guide.md");
        String content = Files.readString(troubleshootingPath);

        // Validate ClickHouse timeout troubleshooting
        assertTrue(content.contains("ClickHouse") && content.contains("timeout"),
            "Should document ClickHouse timeout issue");
        assertTrue(content.contains("batch.size") || content.contains("flush.interval"),
            "Should provide batch tuning solutions");
    }

    @Test
    @DisplayName("Test Troubleshooting OOM should diagnose")
    public void testTroubleshooting_OOM_shouldDiagnose() throws Exception {
        Path troubleshootingPath = Path.of(PROJECT_ROOT, DOCS_DIR, "operations/troubleshooting-guide.md");
        String content = Files.readString(troubleshootingPath);

        // Validate OOM troubleshooting
        assertTrue(content.contains("OOM") || content.contains("OutOfMemoryError"),
            "Should document OOM issue");
        assertTrue(content.contains("heap dump") || content.contains("jmap"),
            "Should suggest heap dump analysis");
        assertTrue(content.contains("-Xmx") || content.contains("heap size"),
            "Should document heap size tuning");
    }

    @Test
    @DisplayName("Test Troubleshooting config not effective should debug")
    public void testTroubleshooting_configNotEffective_shouldDebug() throws Exception {
        Path troubleshootingPath = Path.of(PROJECT_ROOT, DOCS_DIR, "operations/troubleshooting-guide.md");
        String content = Files.readString(troubleshootingPath);

        // Validate configuration troubleshooting
        assertTrue(content.contains("Configuration Not Taking Effect") ||
                   content.contains("config") && content.contains("not") && content.contains("effect"),
            "Should document configuration issue");
        assertTrue(content.contains("restart") || content.contains("rollout"),
            "Should suggest pod restart");
    }

    @Test
    @DisplayName("Test Emergency procedure degradation should execute")
    public void testEmergencyProcedure_degradation_shouldExecute() throws Exception {
        Path troubleshootingPath = Path.of(PROJECT_ROOT, DOCS_DIR, "operations/troubleshooting-guide.md");
        String content = Files.readString(troubleshootingPath);

        // Validate emergency procedures
        assertTrue(content.contains("Emergency") || content.contains("Degradation"),
            "Should document emergency procedures");
        assertTrue(content.contains("sqlguard.audit.enabled") || content.contains("disable"),
            "Should document how to disable audit");
    }
}
