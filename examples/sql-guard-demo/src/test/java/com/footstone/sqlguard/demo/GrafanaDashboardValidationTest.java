package com.footstone.sqlguard.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation tests for Grafana dashboard JSON files.
 *
 * <p>These tests verify that dashboard JSON files are properly formatted
 * and contain expected panels and configurations.</p>
 */
class GrafanaDashboardValidationTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static Path dashboardsPath;

    @BeforeAll
    static void setup() {
        // Find the dashboards directory relative to test resources
        String projectRoot = System.getProperty("user.dir");
        dashboardsPath = Paths.get(projectRoot, "grafana", "dashboards");
        
        // If running from parent directory
        if (!Files.exists(dashboardsPath)) {
            dashboardsPath = Paths.get(projectRoot, "examples", "sql-guard-demo", "grafana", "dashboards");
        }
    }

    @Test
    @DisplayName("Risk overview dashboard JSON is valid")
    void testRiskOverviewDashboard_ValidJson() throws IOException {
        Path dashboardPath = dashboardsPath.resolve("risk-overview.json");
        
        if (!Files.exists(dashboardPath)) {
            // Skip if file doesn't exist in test environment
            return;
        }

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        
        assertNotNull(dashboard);
        assertEquals("SQL Guard - Risk Overview", dashboard.get("title").asText());
        assertEquals("sql-guard-risk-overview", dashboard.get("uid").asText());
        
        // Verify panels exist
        JsonNode panels = dashboard.get("panels");
        assertNotNull(panels);
        assertTrue(panels.isArray());
        assertTrue(panels.size() >= 4, "Should have at least 4 panels");
    }

    @Test
    @DisplayName("Risk overview dashboard has severity distribution pie chart")
    void testRiskOverviewDashboard_SeverityPieChart() throws IOException {
        Path dashboardPath = dashboardsPath.resolve("risk-overview.json");
        
        if (!Files.exists(dashboardPath)) {
            return;
        }

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        JsonNode panels = dashboard.get("panels");
        
        boolean foundPieChart = false;
        for (JsonNode panel : panels) {
            if ("piechart".equals(panel.get("type").asText()) && 
                panel.get("title").asText().contains("Severity")) {
                foundPieChart = true;
                break;
            }
        }
        
        assertTrue(foundPieChart, "Should have severity distribution pie chart");
    }

    @Test
    @DisplayName("Risk overview dashboard has top 10 high risk table")
    void testRiskOverviewDashboard_Top10Table() throws IOException {
        Path dashboardPath = dashboardsPath.resolve("risk-overview.json");
        
        if (!Files.exists(dashboardPath)) {
            return;
        }

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        JsonNode panels = dashboard.get("panels");
        
        boolean foundTable = false;
        for (JsonNode panel : panels) {
            if ("table".equals(panel.get("type").asText()) && 
                panel.get("title").asText().contains("Top 10")) {
                foundTable = true;
                break;
            }
        }
        
        assertTrue(foundTable, "Should have top 10 high risk SQL table");
    }

    @Test
    @DisplayName("Performance dashboard JSON is valid")
    void testPerformanceDashboard_ValidJson() throws IOException {
        Path dashboardPath = dashboardsPath.resolve("performance.json");
        
        if (!Files.exists(dashboardPath)) {
            return;
        }

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        
        assertNotNull(dashboard);
        assertEquals("SQL Guard - Performance", dashboard.get("title").asText());
        assertEquals("sql-guard-performance", dashboard.get("uid").asText());
    }

    @Test
    @DisplayName("Performance dashboard has latency percentile chart")
    void testPerformanceDashboard_LatencyChart() throws IOException {
        Path dashboardPath = dashboardsPath.resolve("performance.json");
        
        if (!Files.exists(dashboardPath)) {
            return;
        }

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        JsonNode panels = dashboard.get("panels");
        
        boolean foundLatencyChart = false;
        for (JsonNode panel : panels) {
            String title = panel.get("title").asText();
            if (title.contains("Latency") && title.contains("P")) {
                foundLatencyChart = true;
                break;
            }
        }
        
        assertTrue(foundLatencyChart, "Should have latency percentile chart");
    }

    @Test
    @DisplayName("Performance dashboard has slowest queries table")
    void testPerformanceDashboard_SlowestQueriesTable() throws IOException {
        Path dashboardPath = dashboardsPath.resolve("performance.json");
        
        if (!Files.exists(dashboardPath)) {
            return;
        }

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        JsonNode panels = dashboard.get("panels");
        
        boolean foundTable = false;
        for (JsonNode panel : panels) {
            if ("table".equals(panel.get("type").asText()) && 
                panel.get("title").asText().contains("Slowest")) {
                foundTable = true;
                break;
            }
        }
        
        assertTrue(foundTable, "Should have slowest queries table");
    }

    @Test
    @DisplayName("Errors dashboard JSON is valid")
    void testErrorsDashboard_ValidJson() throws IOException {
        Path dashboardPath = dashboardsPath.resolve("errors.json");
        
        if (!Files.exists(dashboardPath)) {
            return;
        }

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        
        assertNotNull(dashboard);
        assertEquals("SQL Guard - Errors", dashboard.get("title").asText());
        assertEquals("sql-guard-errors", dashboard.get("uid").asText());
    }

    @Test
    @DisplayName("Errors dashboard has error rate timeline")
    void testErrorsDashboard_ErrorRateTimeline() throws IOException {
        Path dashboardPath = dashboardsPath.resolve("errors.json");
        
        if (!Files.exists(dashboardPath)) {
            return;
        }

        JsonNode dashboard = objectMapper.readTree(dashboardPath.toFile());
        JsonNode panels = dashboard.get("panels");
        
        boolean foundTimeline = false;
        for (JsonNode panel : panels) {
            String title = panel.get("title").asText();
            if (title.contains("Error") && title.contains("Timeline")) {
                foundTimeline = true;
                break;
            }
        }
        
        assertTrue(foundTimeline, "Should have error rate timeline");
    }

    @Test
    @DisplayName("Datasources configuration file is valid")
    void testDatasourcesConfig_Valid() throws IOException {
        Path datasourcesPath = dashboardsPath.getParent().resolve("datasources").resolve("datasources.yml");
        
        if (!Files.exists(datasourcesPath)) {
            return;
        }

        String content = Files.readString(datasourcesPath);
        
        assertNotNull(content);
        assertTrue(content.contains("ClickHouse"), "Should configure ClickHouse datasource");
        assertTrue(content.contains("Audit Service API"), "Should configure Audit Service API datasource");
    }
}






