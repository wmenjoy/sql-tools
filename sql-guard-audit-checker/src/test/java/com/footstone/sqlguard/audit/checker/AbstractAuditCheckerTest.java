package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AbstractAuditCheckerTest {

    private static class TestAuditChecker extends AbstractAuditChecker {
        @Override
        protected RiskScore performAudit(String sql, ExecutionResult result) {
            return RiskScore.builder()
                    .severity(RiskLevel.LOW)
                    .confidence(100)
                    .justification("Test")
                    .build();
        }

        @Override
        public String getCheckerId() {
            return "TestChecker";
        }
    }

    @Test
    void testCheck_withValidInput_shouldInvokeTemplate() {
        AbstractAuditChecker checker = new TestAuditChecker();
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(1)
                .executionTimeMs(10)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditResult = checker.check("SELECT 1", result);
        
        assertNotNull(auditResult);
        assertEquals("TestChecker", auditResult.getCheckerId());
        assertEquals("SELECT 1", auditResult.getSql());
        assertEquals(result, auditResult.getExecutionResult());
        assertEquals(1, auditResult.getRisks().size());
        assertEquals(RiskLevel.LOW, auditResult.getRisks().get(0).getSeverity());
    }

    @Test
    void testCheck_withNullExecutionResult_shouldThrowException() {
        AbstractAuditChecker checker = new TestAuditChecker();
        assertThrows(IllegalArgumentException.class, () -> 
            checker.check("SELECT 1", null)
        );
    }
    
    @Test
    void testCheck_withNullSql_shouldThrowException() {
        AbstractAuditChecker checker = new TestAuditChecker();
        ExecutionResult result = ExecutionResult.builder()
                .executionTimestamp(Instant.now())
                .build();
        assertThrows(IllegalArgumentException.class, () -> 
            checker.check(null, result)
        );
    }
    
    @Test
    void testCheck_withIncompleteResult_shouldHandleGracefully() {
        AbstractAuditChecker checker = new TestAuditChecker();
        // Result without resultSetSize or error message
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(-1)
                .executionTimestamp(Instant.now())
                .build();
        
        assertDoesNotThrow(() -> checker.check("SELECT 1", result));
    }

    @Test
    void testPerformance_shouldMeetRelaxedRequirements() {
        AbstractAuditChecker checker = new TestAuditChecker();
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(1)
                .executionTimeMs(10)
                .executionTimestamp(Instant.now())
                .build();
        
        long start = System.nanoTime();
        checker.check("SELECT 1", result);
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        
        assertTrue(durationMs < 50, "Check took too long: " + durationMs + "ms");
    }
}
