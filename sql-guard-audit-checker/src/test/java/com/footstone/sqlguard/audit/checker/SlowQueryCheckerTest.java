package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SlowQueryCheckerTest {

    @Test
    void testCheck_belowThreshold_shouldReturnNullOrLowRisk() {
        SlowQueryChecker checker = new SlowQueryChecker();
        ExecutionResult result = ExecutionResult.builder()
                .executionTimeMs(500)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT 1", result);

        assertTrue(auditResult.getRisks().isEmpty(), "Should have no risks for fast query");
    }

    @Test
    void testCheck_exceedingHighThreshold_shouldReturnHighRisk() {
        SlowQueryChecker checker = new SlowQueryChecker(); // Default slow=1000
        ExecutionResult result = ExecutionResult.builder()
                .executionTimeMs(1500)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT * FROM heavy_table", result);

        assertEquals(1, auditResult.getRisks().size());
        RiskScore risk = auditResult.getRisks().get(0);
        assertEquals(RiskLevel.HIGH, risk.getSeverity());
        assertEquals(100, risk.getConfidence());
        assertTrue(risk.getJustification().contains("1500ms"), "Justification should contain time");
        
        Map<String, Number> metrics = risk.getImpactMetrics();
        assertNotNull(metrics);
        assertEquals(1500L, metrics.get("execution_time_ms"));
        assertTrue(metrics.get("threshold_exceeded_ms").longValue() > 0);
    }

    @Test
    void testCheck_exceedingCriticalThreshold_shouldReturnCriticalRisk() {
        SlowQueryChecker checker = new SlowQueryChecker(); // Default critical=5000
        ExecutionResult result = ExecutionResult.builder()
                .executionTimeMs(6000)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT * FROM huge_table", result);

        assertEquals(1, auditResult.getRisks().size());
        assertEquals(RiskLevel.CRITICAL, auditResult.getRisks().get(0).getSeverity());
    }

    @Test
    void testConfiguration_shouldAllowCustomThresholds() {
        SlowQueryChecker checker = new SlowQueryChecker(200, 500);
        
        // 300ms > 200ms (High) but < 500ms
        ExecutionResult resultHigh = ExecutionResult.builder()
                .executionTimeMs(300)
                .executionTimestamp(Instant.now())
                .build();
        AuditResult auditHigh = checker.check("SQL", resultHigh);
        assertEquals(RiskLevel.HIGH, auditHigh.getRisks().get(0).getSeverity());

        // 600ms > 500ms (Critical)
        ExecutionResult resultCritical = ExecutionResult.builder()
                .executionTimeMs(600)
                .executionTimestamp(Instant.now())
                .build();
        AuditResult auditCritical = checker.check("SQL", resultCritical);
        assertEquals(RiskLevel.CRITICAL, auditCritical.getRisks().get(0).getSeverity());
    }
}
