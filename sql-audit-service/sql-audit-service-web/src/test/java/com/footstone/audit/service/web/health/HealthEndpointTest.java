package com.footstone.audit.service.web.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for health indicators.
 */
class HealthEndpointTest {
    
    @Test
    void testHealthEndpoint_shouldReturnUp() {
        // Test Kafka health indicator
        KafkaHealthIndicator kafkaHealth = new KafkaHealthIndicator();
        Health health = kafkaHealth.health();
        
        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails().get("status"));
    }
    
    @Test
    void testHealthEndpoint_kafkaConnectivity_shouldReport() {
        KafkaHealthIndicator kafkaHealth = new KafkaHealthIndicator();
        
        // Test connected state
        kafkaHealth.setConnected(true);
        Health health = kafkaHealth.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("connected", health.getDetails().get("status"));
        
        // Test disconnected state
        kafkaHealth.setConnected(false);
        kafkaHealth.setLastError("Connection refused");
        health = kafkaHealth.health();
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Connection refused", health.getDetails().get("error"));
    }
    
    @Test
    void testHealthEndpoint_databaseStatus_shouldReport() {
        DatabaseHealthIndicator dbHealth = new DatabaseHealthIndicator();
        
        // Test connected state
        dbHealth.setConnected(true);
        Health health = dbHealth.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("connected", health.getDetails().get("status"));
        assertNotNull(health.getDetails().get("activeConnections"));
        assertNotNull(health.getDetails().get("poolUtilization"));
        
        // Test disconnected state
        dbHealth.setConnected(false);
        dbHealth.setLastError("Connection timeout");
        health = dbHealth.health();
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("Connection timeout", health.getDetails().get("error"));
    }
    
    @Test
    void testHealthEndpoint_clickHouseStatus_shouldReport() {
        ClickHouseHealthIndicator chHealth = new ClickHouseHealthIndicator();
        
        // Test connected state
        chHealth.setConnected(true);
        Health health = chHealth.health();
        assertEquals(Status.UP, health.getStatus());
        assertEquals("connected", health.getDetails().get("status"));
        assertNotNull(health.getDetails().get("version"));
        
        // Test disconnected state
        chHealth.setConnected(false);
        chHealth.setLastError("ClickHouse server unavailable");
        health = chHealth.health();
        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("ClickHouse server unavailable", health.getDetails().get("error"));
    }
    
    @Test
    void testKafkaHealth_idleState_shouldReport() {
        KafkaHealthIndicator kafkaHealth = new KafkaHealthIndicator();
        
        // Simulate receiving a message
        kafkaHealth.recordMessageReceived();
        Health health = kafkaHealth.health();
        
        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails().get("lastMessageTime"));
    }
    
    @Test
    void testDatabaseHealth_poolUtilization_shouldWarn() {
        DatabaseHealthIndicator dbHealth = new DatabaseHealthIndicator();
        
        // Set high pool utilization
        dbHealth.setActiveConnections(18);
        dbHealth.setMaxConnections(20);
        
        Health health = dbHealth.health();
        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails().get("warning"));
        assertTrue(health.getDetails().get("warning").toString().contains("high"));
    }
    
    @Test
    void testClickHouseHealth_recordQuery_shouldUpdate() {
        ClickHouseHealthIndicator chHealth = new ClickHouseHealthIndicator();
        
        // Record a query
        chHealth.recordQuery();
        Health health = chHealth.health();
        
        assertEquals(Status.UP, health.getStatus());
        assertNotNull(health.getDetails().get("lastQueryTime"));
    }
}










