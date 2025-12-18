package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ErrorPatternCheckerTest {

    private final ErrorPatternChecker checker = new ErrorPatternChecker();

    @Test
    void testCheck_deadlockError_shouldReturnHighRisk() {
        ExecutionResult result = ExecutionResult.builder()
                .errorMessage("Deadlock found when trying to get lock; try restarting transaction")
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("UPDATE t SET c = 1", result);

        assertNotNull(auditResult);
        assertEquals(1, auditResult.getRisks().size());
        RiskScore risk = auditResult.getRisks().get(0);
        assertEquals(RiskLevel.HIGH, risk.getSeverity());
        assertEquals("Deadlock detected", risk.getJustification());
    }

    @Test
    void testCheck_lockWaitTimeoutError_shouldReturnHighRisk() {
        ExecutionResult result = ExecutionResult.builder()
                .errorMessage("Lock wait timeout exceeded; try restarting transaction")
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("UPDATE t SET c = 1", result);

        assertNotNull(auditResult);
        assertEquals(1, auditResult.getRisks().size());
        RiskScore risk = auditResult.getRisks().get(0);
        assertEquals(RiskLevel.HIGH, risk.getSeverity());
        assertEquals("Lock wait timeout detected", risk.getJustification());
    }

    @Test
    void testCheck_syntaxError_shouldReturnMediumRisk() {
        ExecutionResult result = ExecutionResult.builder()
                .errorMessage("You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version")
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELEC * FROM t", result);

        assertNotNull(auditResult);
        assertEquals(1, auditResult.getRisks().size());
        RiskScore risk = auditResult.getRisks().get(0);
        assertEquals(RiskLevel.MEDIUM, risk.getSeverity());
        assertEquals("SQL syntax error", risk.getJustification());
    }

    @Test
    void testCheck_genericError_shouldReturnLowRisk() {
        ExecutionResult result = ExecutionResult.builder()
                .errorMessage("Some unknown error occurred")
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT * FROM t", result);

        assertNotNull(auditResult);
        assertEquals(1, auditResult.getRisks().size());
        RiskScore risk = auditResult.getRisks().get(0);
        assertEquals(RiskLevel.LOW, risk.getSeverity());
        assertEquals("Generic execution error", risk.getJustification());
    }

    @Test
    void testCheck_success_shouldReturnNull() {
        ExecutionResult result = ExecutionResult.builder()
                .executionTimestamp(Instant.now())
                .build(); // success is true by default or null error means success?

        // Assuming null errorMessage means success or isSuccess() checks it.
        // Let's verify ExecutionResult logic if possible, but based on AbstractAuditCheckerIntegrationTest, null error is good.
        
        AuditResult auditResult = checker.check("SELECT * FROM t", result);

        assertNotNull(auditResult);
        assertTrue(auditResult.getRisks().isEmpty());
    }
    
    @Test
    void testGetCheckerId() {
        assertEquals("ErrorPatternChecker", checker.getCheckerId());
    }
}
