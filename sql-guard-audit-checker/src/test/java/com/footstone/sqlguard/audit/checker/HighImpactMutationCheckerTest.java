package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class HighImpactMutationCheckerTest {

    // Thresholds: Medium > 1000, Critical > 10000
    private final HighImpactMutationChecker checker = new HighImpactMutationChecker(1000, 10000);

    @Test
    void testCheck_smallMutation_shouldPass() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(500)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("UPDATE users SET status = 1 WHERE id < 500", result);

        assertTrue(auditResult.getRisks().isEmpty());
    }

    @Test
    void testCheck_largeMutation_shouldReturnMediumRisk() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(5000)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("DELETE FROM logs WHERE date < '2023-01-01'", result);

        assertEquals(1, auditResult.getRisks().size());
        RiskScore risk = auditResult.getRisks().get(0);
        assertEquals(RiskLevel.MEDIUM, risk.getSeverity());
        assertEquals(100, risk.getConfidence());
        assertTrue(risk.getJustification().contains("High impact mutation: 5000 rows modified"));
    }

    @Test
    void testCheck_massiveMutation_shouldReturnCriticalRisk() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(20000)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("UPDATE products SET price = price * 1.1", result);

        assertEquals(1, auditResult.getRisks().size());
        RiskScore risk = auditResult.getRisks().get(0);
        assertEquals(RiskLevel.CRITICAL, risk.getSeverity()); // Using CRITICAL for massive
        assertTrue(risk.getJustification().contains("High impact mutation: 20000 rows modified"));
    }

    @Test
    void testCheck_select_shouldBeIgnored() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(-1) // Typically -1 for SELECT
                .resultSetSize(10000)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT * FROM large_table", result);

        assertTrue(auditResult.getRisks().isEmpty());
    }
    
    @Test
    void testGetCheckerId() {
        assertEquals("HighImpactMutationChecker", checker.getCheckerId());
    }
}
