package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UnboundedReadCheckerTest {

    private final UnboundedReadChecker checker = new UnboundedReadChecker();

    @Test
    void testCheck_selectExceedingLimit_shouldReturnHighRisk() {
        ExecutionResult result = ExecutionResult.builder()
                .resultSetSize(10001)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT * FROM large_table", result);

        assertEquals(1, auditResult.getRisks().size());
        RiskScore risk = auditResult.getRisks().get(0);
        assertEquals(RiskLevel.HIGH, risk.getSeverity());
        assertTrue(risk.getJustification().contains("10001 rows"));
        
        Map<String, Number> metrics = risk.getImpactMetrics();
        assertEquals(10001, metrics.get("result_set_size"));
    }

    @Test
    void testCheck_selectWithinLimit_shouldBeSafe() {
        ExecutionResult result = ExecutionResult.builder()
                .resultSetSize(5000)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT * FROM medium_table", result);

        assertTrue(auditResult.getRisks().isEmpty());
    }

    @Test
    void testCheck_notSelect_shouldBeIgnored() {
        ExecutionResult result = ExecutionResult.builder()
                .resultSetSize(0)
                .rowsAffected(100000)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("UPDATE users SET status = 1", result);

        assertTrue(auditResult.getRisks().isEmpty());
    }

    @Test
    void testCheck_withCustomLimit() {
        UnboundedReadChecker customChecker = new UnboundedReadChecker(100);
        
        ExecutionResult result = ExecutionResult.builder()
                .resultSetSize(150)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = customChecker.check("SELECT * FROM users", result);

        assertEquals(1, auditResult.getRisks().size());
    }
}
