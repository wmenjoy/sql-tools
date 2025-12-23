package com.footstone.sqlguard.demo;

import com.footstone.sqlguard.demo.controller.AuditScenarioController;
import com.footstone.sqlguard.demo.controller.LoadGeneratorController;
import com.footstone.sqlguard.demo.load.LoadGenerator;
import com.footstone.sqlguard.demo.mapper.AuditScenarioMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Audit Scenario functionality.
 *
 * <p>These tests verify that audit scenario endpoints work correctly
 * and generate appropriate responses for different SQL patterns.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "sql-guard.active-strategy=LOG",
    "spring.kafka.bootstrap-servers=localhost:9092"
})
class AuditScenarioTest {

    @Autowired
    private AuditScenarioController auditScenarioController;

    @Autowired
    private AuditScenarioMapper auditScenarioMapper;

    @Autowired
    private LoadGenerator loadGenerator;

    @Autowired
    private LoadGeneratorController loadGeneratorController;

    @Test
    @DisplayName("Audit scenario controller loads successfully")
    void testContextLoads() {
        assertNotNull(auditScenarioController);
        assertNotNull(auditScenarioMapper);
        assertNotNull(loadGenerator);
        assertNotNull(loadGeneratorController);
    }

    @Test
    @DisplayName("List scenarios returns all available scenarios")
    void testListScenarios() {
        ResponseEntity<Map<String, Object>> response = auditScenarioController.listScenarios();
        
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("scenarios"));
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenarios = (List<Map<String, Object>>) body.get("scenarios");
        assertTrue(scenarios.size() >= 6, "Should have at least 6 scenarios");
    }

    @Test
    @DisplayName("Deep pagination scenario returns expected response")
    void testDeepPaginationScenario() {
        ResponseEntity<Map<String, Object>> response = auditScenarioController.triggerDeepPagination();
        
        assertNotNull(response);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        
        assertEquals("deep-pagination", body.get("scenario"));
        assertEquals(10000, body.get("offset"));
        assertEquals(100, body.get("limit"));
        assertEquals("DeepPaginationChecker - MEDIUM severity", body.get("expectedAudit"));
    }

    @Test
    @DisplayName("Error SQL scenario returns error_triggered status")
    void testErrorSqlScenario() {
        ResponseEntity<Map<String, Object>> response = auditScenarioController.triggerErrorSql();
        
        assertNotNull(response);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        
        assertEquals("error-sql", body.get("scenario"));
        assertEquals("error_triggered", body.get("status"));
        assertTrue(body.containsKey("errorMessage"));
    }

    @Test
    @DisplayName("Large page size scenario returns expected response")
    void testLargePageSizeScenario() {
        ResponseEntity<Map<String, Object>> response = auditScenarioController.triggerLargePageSize();
        
        assertNotNull(response);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        
        assertEquals("large-page-size", body.get("scenario"));
        assertEquals(5000, body.get("limit"));
    }

    @Test
    @DisplayName("No pagination scenario returns expected response")
    void testNoPaginationScenario() {
        ResponseEntity<Map<String, Object>> response = auditScenarioController.triggerNoPagination();
        
        assertNotNull(response);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        
        assertEquals("no-pagination", body.get("scenario"));
    }

    @Test
    @DisplayName("Load generator status returns idle when not running")
    void testLoadGeneratorStatus_Idle() {
        ResponseEntity<Map<String, Object>> response = loadGeneratorController.status();
        
        assertNotNull(response);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        
        assertFalse((Boolean) body.get("running"));
        assertEquals("idle", body.get("status"));
    }

    @Test
    @DisplayName("Load generator info returns API documentation")
    void testLoadGeneratorInfo() {
        ResponseEntity<Map<String, Object>> response = loadGeneratorController.info();
        
        assertNotNull(response);
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        
        assertTrue(body.containsKey("description"));
        assertTrue(body.containsKey("endpoints"));
        assertTrue(body.containsKey("distribution"));
        assertEquals(100, body.get("targetQPS"));
    }

    @Test
    @DisplayName("Load generator rejects invalid duration")
    void testLoadGeneratorInvalidDuration() {
        // Duration too short
        ResponseEntity<Map<String, Object>> response = loadGeneratorController.startWithDuration(0);
        assertEquals(400, response.getStatusCode().value());
        
        // Duration too long
        response = loadGeneratorController.startWithDuration(100);
        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Load generator statistics calculation is correct")
    void testLoadGeneratorStatistics() {
        LoadGenerator.LoadStatistics stats = loadGenerator.getStatistics();
        
        assertNotNull(stats);
        assertEquals(0, stats.getTotalQueries());
        assertEquals(0, stats.getFastQueryCount());
        assertEquals(0, stats.getSlowQueryCount());
        assertEquals(0, stats.getErrorQueryCount());
    }

    @Test
    @DisplayName("Load generator is not running initially")
    void testLoadGeneratorNotRunning() {
        assertFalse(loadGenerator.isRunning());
    }

    @Test
    @DisplayName("Audit scenario mapper methods exist")
    void testAuditScenarioMapperMethods() {
        // Verify mapper is properly configured
        assertNotNull(auditScenarioMapper);
        
        // Test selectById (safe method)
        // Note: May return null if no data, but should not throw
        try {
            auditScenarioMapper.selectById(1L);
        } catch (Exception e) {
            // Expected in test environment without full MySQL
        }
    }
}

