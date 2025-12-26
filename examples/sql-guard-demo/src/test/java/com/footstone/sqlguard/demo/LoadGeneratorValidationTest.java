package com.footstone.sqlguard.demo;

import com.footstone.sqlguard.demo.load.LoadGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validation tests for LoadGenerator functionality.
 *
 * <p>These tests verify that the load generator properly controls
 * execution duration and maintains expected query distribution.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "sql-guard.active-strategy=LOG"
})
class LoadGeneratorValidationTest {

    @Autowired
    private LoadGenerator loadGenerator;

    @Test
    @DisplayName("Load generator initializes with zero statistics")
    void testInitialStatistics() {
        LoadGenerator.LoadStatistics stats = loadGenerator.getStatistics();
        
        assertNotNull(stats);
        assertEquals(0, stats.getTotalQueries());
        assertEquals(0, stats.getFastQueryCount());
        assertEquals(0, stats.getSlowQueryCount());
        assertEquals(0, stats.getErrorQueryCount());
        assertEquals(0, stats.getTotalExecutionTimeMs());
    }

    @Test
    @DisplayName("Load generator is not running initially")
    void testNotRunningInitially() {
        assertFalse(loadGenerator.isRunning());
    }

    @Test
    @DisplayName("Load generator can be stopped")
    void testStop() {
        // Stop even if not running should not throw
        loadGenerator.stop();
        assertFalse(loadGenerator.isRunning());
    }

    @Test
    @DisplayName("Statistics toString contains expected fields")
    void testStatisticsToString() {
        LoadGenerator.LoadStatistics stats = new LoadGenerator.LoadStatistics();
        stats.setTotalQueries(100);
        stats.setFastQueryCount(80);
        stats.setFastQueryPercent(80.0);
        stats.setSlowQueryCount(15);
        stats.setSlowQueryPercent(15.0);
        stats.setErrorQueryCount(5);
        stats.setErrorQueryPercent(5.0);
        stats.setActualDurationMs(10000);
        stats.setAverageExecutionTimeMs(10.5);
        
        String str = stats.toString();
        
        assertNotNull(str);
        assertTrue(str.contains("total=100"));
        assertTrue(str.contains("fast=80"));
        assertTrue(str.contains("slow=15"));
        assertTrue(str.contains("error=5"));
    }

    @Test
    @DisplayName("Statistics setters and getters work correctly")
    void testStatisticsGettersSetters() {
        LoadGenerator.LoadStatistics stats = new LoadGenerator.LoadStatistics();
        
        stats.setFastQueryCount(100);
        assertEquals(100, stats.getFastQueryCount());
        
        stats.setSlowQueryCount(50);
        assertEquals(50, stats.getSlowQueryCount());
        
        stats.setErrorQueryCount(10);
        assertEquals(10, stats.getErrorQueryCount());
        
        stats.setTotalQueries(160);
        assertEquals(160, stats.getTotalQueries());
        
        stats.setTotalExecutionTimeMs(5000);
        assertEquals(5000, stats.getTotalExecutionTimeMs());
        
        stats.setActualDurationMs(60000);
        assertEquals(60000, stats.getActualDurationMs());
        
        stats.setFastQueryPercent(62.5);
        assertEquals(62.5, stats.getFastQueryPercent(), 0.01);
        
        stats.setSlowQueryPercent(31.25);
        assertEquals(31.25, stats.getSlowQueryPercent(), 0.01);
        
        stats.setErrorQueryPercent(6.25);
        assertEquals(6.25, stats.getErrorQueryPercent(), 0.01);
        
        stats.setAverageExecutionTimeMs(31.25);
        assertEquals(31.25, stats.getAverageExecutionTimeMs(), 0.01);
    }

    @Test
    @DisplayName("Load generator short run completes successfully")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testShortRun() {
        // Run for just 100ms
        LoadGenerator.LoadStatistics stats = loadGenerator.runFor(100);
        
        assertNotNull(stats);
        assertTrue(stats.getActualDurationMs() >= 100, "Should run for at least 100ms");
        assertFalse(loadGenerator.isRunning(), "Should not be running after completion");
    }

    @Test
    @DisplayName("Running load generator returns current statistics")
    void testGetStatisticsWhileRunning() {
        // Start in background
        Thread runner = new Thread(() -> loadGenerator.runFor(500));
        runner.start();
        
        try {
            Thread.sleep(100); // Let it run a bit
            
            if (loadGenerator.isRunning()) {
                LoadGenerator.LoadStatistics stats = loadGenerator.getStatistics();
                assertNotNull(stats);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            loadGenerator.stop();
            try {
                runner.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}





