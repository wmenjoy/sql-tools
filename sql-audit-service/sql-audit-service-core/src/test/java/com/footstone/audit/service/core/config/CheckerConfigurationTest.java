package com.footstone.audit.service.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class CheckerConfigurationTest {

    @EnableConfigurationProperties(AuditEngineConfig.class)
    static class TestConfig {}

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void testConfig_yamlBinding_shouldLoad() {
        runner.withPropertyValues(
                "audit.engine.checker-timeout-ms=500",
                "audit.engine.checkers.SlowQueryChecker.enabled=false"
        ).run(context -> {
            AuditEngineConfig config = context.getBean(AuditEngineConfig.class);
            assertEquals(500, config.getCheckerTimeoutMs());
            assertFalse(config.isCheckerEnabled("SlowQueryChecker"));
            assertTrue(config.isCheckerEnabled("OtherChecker")); // Default true
        });
    }

    @Test
    void testConfig_whitelist_shouldFilterSql() {
        runner.withPropertyValues(
                "audit.engine.whitelist-rules[0]=^SELECT 1$"
        ).run(context -> {
            AuditEngineConfig config = context.getBean(AuditEngineConfig.class);
            assertTrue(config.isWhitelisted("SELECT 1"));
            assertFalse(config.isWhitelisted("SELECT * FROM users"));
        });
    }
}
