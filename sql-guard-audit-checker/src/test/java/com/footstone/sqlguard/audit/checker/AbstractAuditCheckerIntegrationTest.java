package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbstractAuditCheckerIntegrationTest {

    // Concrete test checker 1: Detects large row counts
    private static class SimpleAuditChecker extends AbstractAuditChecker {
        @Override
        protected RiskScore performAudit(String sql, ExecutionResult result) {
            if (result.getRowsAffected() > 1000) {
                return RiskScore.builder()
                        .severity(RiskLevel.HIGH)
                        .confidence(100)
                        .justification("Rows affected > 1000")
                        .build();
            }
            return null; // No risk
        }

        @Override
        public String getCheckerId() {
            return "SimpleAuditChecker";
        }
    }

    // Concrete test checker 2: Detects slow queries
    private static class SlowQueryTestChecker extends AbstractAuditChecker {
        @Override
        protected RiskScore performAudit(String sql, ExecutionResult result) {
            if (isSlowQuery(result.getExecutionTimeMs(), 1000)) {
                return RiskScore.builder()
                        .severity(RiskLevel.CRITICAL)
                        .confidence(100)
                        .justification("Execution time > 1000ms")
                        .build();
            }
            return null;
        }

        @Override
        public String getCheckerId() {
            return "SlowQueryTestChecker";
        }
    }

    // Concrete test checker 3: Analyzes errors
    private static class ErrorAuditChecker extends AbstractAuditChecker {
        @Override
        protected RiskScore performAudit(String sql, ExecutionResult result) {
            if (!result.isSuccess() && result.getErrorMessage() != null && result.getErrorMessage().contains("deadlock")) {
                return RiskScore.builder()
                        .severity(RiskLevel.HIGH)
                        .confidence(100)
                        .justification("Deadlock detected")
                        .build();
            }
            return null;
        }

        @Override
        public String getCheckerId() {
            return "ErrorAuditChecker";
        }
    }

    @Test
    void testSimpleChecker_withHighImpact_shouldDetectRisk() {
        SimpleAuditChecker checker = new SimpleAuditChecker();
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(5000)
                .executionTimeMs(100)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("UPDATE users", result);

        assertEquals(1, auditResult.getRisks().size());
        assertEquals(RiskLevel.HIGH, auditResult.getRisks().get(0).getSeverity());
        assertEquals("SimpleAuditChecker", auditResult.getCheckerId());
    }

    @Test
    void testSimpleChecker_withLowImpact_shouldNotDetectRisk() {
        SimpleAuditChecker checker = new SimpleAuditChecker();
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(5)
                .executionTimeMs(100)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("UPDATE users", result);

        assertEquals(0, auditResult.getRisks().size());
    }

    @Test
    void testSlowQueryChecker_withSlowQuery_shouldDetectCriticalRisk() {
        SlowQueryTestChecker checker = new SlowQueryTestChecker();
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(1)
                .executionTimeMs(2500)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT * FROM big_table", result);

        assertEquals(1, auditResult.getRisks().size());
        assertEquals(RiskLevel.CRITICAL, auditResult.getRisks().get(0).getSeverity());
    }

    @Test
    void testErrorChecker_withDeadlock_shouldDetectRisk() {
        ErrorAuditChecker checker = new ErrorAuditChecker();
        ExecutionResult result = ExecutionResult.builder()
                .errorMessage("Transaction deadlock detected")
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("UPDATE account", result);

        assertEquals(1, auditResult.getRisks().size());
        assertEquals("Deadlock detected", auditResult.getRisks().get(0).getJustification());
    }

    @Test
    void testErrorChecker_withOtherError_shouldIgnore() {
        ErrorAuditChecker checker = new ErrorAuditChecker();
        ExecutionResult result = ExecutionResult.builder()
                .errorMessage("Syntax error")
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT", result);

        assertEquals(0, auditResult.getRisks().size());
    }

    @Test
    void testTemplateMethod_lifecycleValidation() {
        // Verify validateInput is called (implicit via exception test)
        SimpleAuditChecker checker = new SimpleAuditChecker();
        assertThrows(IllegalArgumentException.class, () -> checker.check("SQL", null));
        
        // Verify buildAuditResult is called (implicit via result check)
        ExecutionResult result = ExecutionResult.builder().executionTimestamp(Instant.now()).build();
        AuditResult auditResult = checker.check("SQL", result);
        assertNotNull(auditResult.getAuditTimestamp());
    }

    @Test
    void testPerformance_benchmark() {
        List<ExecutionResult> dataset = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < 1000; i++) {
            dataset.add(ExecutionResult.builder()
                    .rowsAffected(i * 10)
                    .executionTimeMs(i * 2)
                    .executionTimestamp(now)
                    .errorMessage(i % 100 == 0 ? "deadlock" : null)
                    .build());
        }

        SimpleAuditChecker simpleChecker = new SimpleAuditChecker();
        SlowQueryTestChecker slowChecker = new SlowQueryTestChecker();
        ErrorAuditChecker errorChecker = new ErrorAuditChecker();

        long start = System.nanoTime();
        
        for (ExecutionResult result : dataset) {
            simpleChecker.check("SQL", result);
            slowChecker.check("SQL", result);
            errorChecker.check("SQL", result);
        }
        
        long totalTimeNs = System.nanoTime() - start;
        double avgTimeMs = (totalTimeNs / 1_000_000.0) / (dataset.size() * 3);
        
        System.out.println("Average check time: " + avgTimeMs + "ms");
        
        // Requirement is < 50ms p99. Here avg is likely < 0.01ms
        assertTrue(avgTimeMs < 1.0, "Average check time should be very low");
        
        // Calculate P99 (simplified)
        // Since we ran sequential, we can just assert total time is reasonable
        // 1000 iterations * 3 checkers = 3000 checks. 
        // 3000 * 0.05ms = 150ms. Allow up to 500ms total for 3000 checks.
        assertTrue(totalTimeNs / 1_000_000.0 < 500, "Total benchmark time too high");
    }
}
