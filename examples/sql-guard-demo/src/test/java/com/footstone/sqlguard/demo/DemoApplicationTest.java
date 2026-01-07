package com.footstone.sqlguard.demo;

import com.footstone.sqlguard.demo.controller.DemoController;
import com.footstone.sqlguard.demo.mapper.UserAnnotationMapper;
import com.footstone.sqlguard.demo.mapper.UserMapper;
import com.footstone.sqlguard.demo.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SQL Guard Demo Application.
 *
 * <p>These tests verify that the demo application starts correctly and that
 * violation endpoints behave as expected with different strategies.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "sql-guard.active-strategy=LOG"
})
class DemoApplicationTest {

    @Autowired
    private DemoController demoController;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserAnnotationMapper userAnnotationMapper;

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("Spring Boot context loads successfully")
    void contextLoads() {
        assertNotNull(demoController);
        assertNotNull(userMapper);
        assertNotNull(userAnnotationMapper);
        assertNotNull(orderService);
    }

    @Test
    @DisplayName("Home endpoint returns API documentation")
    void testHomeEndpoint() {
        Map<String, Object> response = demoController.home();
        
        assertNotNull(response);
        assertEquals("SQL Guard Demo", response.get("application"));
        assertTrue(response.containsKey("endpoints"));
        assertTrue(response.containsKey("documentation"));
    }

    @Test
    @DisplayName("NoWhereClauseChecker violation endpoint works with LOG strategy")
    void testNoWhereClauseViolation_LogStrategy() {
        Map<String, Object> response = demoController.triggerNoWhereClause();
        
        assertNotNull(response);
        // With LOG strategy, execution continues
        assertTrue(response.get("status").equals("success") || 
                   response.get("status").equals("blocked"));
        assertEquals("NoWhereClauseChecker", response.get("checker"));
        assertEquals("CRITICAL", response.get("riskLevel"));
    }

    @Test
    @DisplayName("DummyConditionChecker violation endpoint works")
    void testDummyConditionViolation() {
        Map<String, Object> response = demoController.triggerDummyCondition();
        
        assertNotNull(response);
        assertEquals("DummyConditionChecker", response.get("checker"));
        assertEquals("HIGH", response.get("riskLevel"));
    }

    @Test
    @DisplayName("BlacklistFieldChecker violation endpoint works")
    void testBlacklistOnlyViolation() {
        Map<String, Object> response = demoController.triggerBlacklistOnly();
        
        assertNotNull(response);
        assertEquals("BlacklistFieldChecker", response.get("checker"));
        assertEquals("HIGH", response.get("riskLevel"));
    }

    @Test
    @DisplayName("DeepPaginationChecker violation endpoint works")
    void testDeepPaginationViolation() {
        Map<String, Object> response = demoController.triggerDeepPagination();
        
        assertNotNull(response);
        assertEquals("DeepPaginationChecker", response.get("checker"));
        assertEquals("MEDIUM", response.get("riskLevel"));
    }

    @Test
    @DisplayName("LargePageSizeChecker violation endpoint works")
    void testLargePageSizeViolation() {
        Map<String, Object> response = demoController.triggerLargePageSize();
        
        assertNotNull(response);
        assertEquals("LargePageSizeChecker", response.get("checker"));
        assertEquals("MEDIUM", response.get("riskLevel"));
    }

    @Test
    @DisplayName("MissingOrderByChecker violation endpoint works")
    void testMissingOrderByViolation() {
        Map<String, Object> response = demoController.triggerMissingOrderBy();
        
        assertNotNull(response);
        assertEquals("MissingOrderByChecker", response.get("checker"));
        assertEquals("LOW", response.get("riskLevel"));
    }

    @Test
    @DisplayName("NoPaginationChecker violation endpoint works")
    void testNoPaginationViolation() {
        Map<String, Object> response = demoController.triggerNoPagination();
        
        assertNotNull(response);
        assertEquals("NoPaginationChecker", response.get("checker"));
        assertTrue(response.get("riskLevel").toString().contains("MEDIUM") || 
                   response.get("riskLevel").toString().contains("CRITICAL"));
    }

    @Test
    @DisplayName("NoConditionPaginationChecker violation endpoint works")
    void testNoConditionPaginationViolation() {
        Map<String, Object> response = demoController.triggerNoConditionPagination();
        
        assertNotNull(response);
        assertEquals("NoConditionPaginationChecker", response.get("checker"));
        assertEquals("CRITICAL", response.get("riskLevel"));
    }

    @Test
    @DisplayName("Violation logs endpoint returns log data")
    void testViolationLogsEndpoint() {
        // Trigger a violation first
        demoController.triggerNoWhereClause();
        
        // Get logs
        Map<String, Object> response = demoController.getViolationLogs();
        
        assertNotNull(response);
        assertTrue(response.containsKey("total"));
        assertTrue(response.containsKey("logs"));
    }

    @Test
    @DisplayName("Strategy change endpoint validates input")
    void testChangeStrategyEndpoint() {
        // Valid strategy
        Map<String, Object> response = demoController.changeStrategy("BLOCK");
        assertNotNull(response);
        assertTrue((Boolean) response.get("success"));
        
        // Invalid strategy
        response = demoController.changeStrategy("INVALID");
        assertNotNull(response);
        assertFalse((Boolean) response.get("success"));
    }
}















