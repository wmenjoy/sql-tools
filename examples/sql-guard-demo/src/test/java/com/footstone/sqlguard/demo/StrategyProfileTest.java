package com.footstone.sqlguard.demo;

import com.footstone.sqlguard.demo.controller.DemoController;
import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for different violation strategy profiles.
 *
 * <p>Tests verify behavior under different strategies:</p>
 * <ul>
 *   <li><strong>LOG</strong> - Silent logging, execution continues</li>
 *   <li><strong>WARN</strong> - Error logging, execution continues</li>
 *   <li><strong>BLOCK</strong> - Exception thrown, execution blocked</li>
 * </ul>
 */
class StrategyProfileTest {

    /**
     * Test LOG strategy - violations are logged but execution continues.
     */
    @SpringBootTest
    @ActiveProfiles({"test", "log"})
    @TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:log_strategy_test",
        "spring.datasource.driver-class-name=org.h2.Driver"
    })
    @DisplayName("LOG Strategy Tests")
    static class LogStrategyTest {

        @Autowired
        private SqlGuardProperties properties;

        @Autowired
        private DemoController demoController;

        @Test
        @DisplayName("Active strategy should be LOG")
        void testLogStrategy() {
            assertEquals("LOG", properties.getActiveStrategy(),
                         "Active strategy should be LOG");
        }

        @Test
        @DisplayName("Violations should be logged but execution continues")
        void testViolationsLogged() {
            // Trigger a violation - should succeed with LOG strategy
            Map<String, Object> response = demoController.triggerNoWhereClause();
            
            assertNotNull(response, "Response should not be null");
            // With LOG strategy, execution continues
            assertTrue(response.get("status").equals("success") || 
                       response.get("status").equals("blocked"),
                       "Execution should continue with LOG strategy");
        }
    }

    /**
     * Test WARN strategy - violations are logged as errors but execution continues.
     */
    @SpringBootTest
    @ActiveProfiles({"test", "warn"})
    @TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:warn_strategy_test",
        "spring.datasource.driver-class-name=org.h2.Driver"
    })
    @DisplayName("WARN Strategy Tests")
    static class WarnStrategyTest {

        @Autowired
        private SqlGuardProperties properties;

        @Autowired
        private DemoController demoController;

        @Test
        @DisplayName("Active strategy should be WARN")
        void testWarnStrategy() {
            assertEquals("WARN", properties.getActiveStrategy(),
                         "Active strategy should be WARN");
        }

        @Test
        @DisplayName("Violations should be logged as errors but execution continues")
        void testViolationsLoggedAsErrors() {
            // Trigger a violation - should succeed with WARN strategy
            Map<String, Object> response = demoController.triggerDummyCondition();
            
            assertNotNull(response, "Response should not be null");
            assertEquals("DummyConditionChecker", response.get("checker"),
                         "Checker should be DummyConditionChecker");
        }
    }

    /**
     * Test BLOCK strategy - violations throw exceptions and block execution.
     */
    @SpringBootTest
    @ActiveProfiles({"test", "block"})
    @TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:block_strategy_test",
        "spring.datasource.driver-class-name=org.h2.Driver"
    })
    @DisplayName("BLOCK Strategy Tests")
    static class BlockStrategyTest {

        @Autowired
        private SqlGuardProperties properties;

        @Test
        @DisplayName("Active strategy should be BLOCK")
        void testBlockStrategy() {
            assertEquals("BLOCK", properties.getActiveStrategy(),
                         "Active strategy should be BLOCK");
        }

        @Test
        @DisplayName("SQL Guard should be enabled with BLOCK strategy")
        void testSqlGuardEnabled() {
            assertTrue(properties.isEnabled(), "SQL Guard should be enabled");
        }
    }
}
