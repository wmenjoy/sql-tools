package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LargeResultCheckerTest {

    private final LargeResultChecker checker = new LargeResultChecker(5000);

    @Test
    void testCheck_smallResult_shouldPass() {
        ExecutionResult result = ExecutionResult.builder()
                .resultSetSize(100)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT * FROM users", result);

        assertTrue(auditResult.getRisks().isEmpty());
    }

    @Test
    void testCheck_exceedingThreshold_shouldReturnHighRisk() {
        ExecutionResult result = ExecutionResult.builder()
                .resultSetSize(5001)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT * FROM logs", result);

        assertEquals(1, auditResult.getRisks().size());
        RiskScore risk = auditResult.getRisks().get(0);
        assertEquals(RiskLevel.HIGH, risk.getSeverity());
        assertEquals(100, risk.getConfidence());
        assertTrue(risk.getJustification().contains("Large result set: 5001 rows returned"));
    }

    @Test
    void testCheck_mutation_shouldBeIgnored() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(100)
                .resultSetSize(-1) // Typically -1 for updates
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("UPDATE users SET status = 1", result);

        assertTrue(auditResult.getRisks().isEmpty());
    }

    @Test
    void testCheck_nullResultSetSize_shouldBeIgnored() {
        // Assuming default builder values, resultSetSize might be 0 if not set, or we need to check how it's handled.
        // If we treat 0 as valid size, it passes.
        // If the metrics are missing, usually it defaults to 0 or -1.
        ExecutionResult result = ExecutionResult.builder()
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT 1", result);

        assertTrue(auditResult.getRisks().isEmpty());
    }
    
    @Test
    void testGetCheckerId() {
        assertEquals("LargeResultChecker", checker.getCheckerId());
    }
}
