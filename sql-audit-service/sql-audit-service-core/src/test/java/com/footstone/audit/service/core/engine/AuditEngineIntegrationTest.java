package com.footstone.audit.service.core.engine;

import com.footstone.audit.service.core.config.AuditEngineConfig;
import com.footstone.audit.service.core.model.AuditProcessingResult;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.checker.AbstractAuditChecker;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = AuditEngineIntegrationTest.TestConfig.class)
class AuditEngineIntegrationTest {

    @Autowired
    private DefaultAuditEngine engine;

    @Test
    void testIntegration_fullPipeline_shouldComplete() {
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM users")
                .sqlType(com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
                .statementId("test")
                .timestamp(Instant.now())
                .executionTimeMs(1500) // Trigger SlowChecker
                .build();

        AuditProcessingResult result = engine.process(event);

        assertTrue(result.success());
        assertEquals(2, result.report().checkerResults().size()); // 2 checkers
        assertEquals(RiskLevel.HIGH, result.report().aggregatedRiskScore().getSeverity());
    }

    @Configuration
    @Import({DefaultAuditEngine.class, AuditEngineConfig.class, ResultAggregator.class})
    static class TestConfig {
        
        @Bean
        public ExecutorService executorService() {
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        @Bean
        public AbstractAuditChecker slowChecker() {
            return new AbstractAuditChecker() {
                @Override
                protected RiskScore performAudit(String sql, ExecutionResult result) {
                    if (result.getExecutionTimeMs() > 1000) {
                        return RiskScore.builder().severity(RiskLevel.HIGH).justification("Slow").build();
                    }
                    return null;
                }

                @Override
                public String getCheckerId() {
                    return "SlowChecker";
                }
            };
        }

        @Bean
        public AbstractAuditChecker normalChecker() {
            return new AbstractAuditChecker() {
                @Override
                protected RiskScore performAudit(String sql, ExecutionResult result) {
                    return null;
                }

                @Override
                public String getCheckerId() {
                    return "NormalChecker";
                }
            };
        }
    }
}
