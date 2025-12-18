package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ErrorPatternChecker.
 * Verifies that the checker correctly identifies risk patterns in realistic execution results.
 */
class ErrorPatternCheckerIntegrationTest {

    private final ErrorPatternChecker checker = new ErrorPatternChecker();

    @Test
    void testChecker_shouldDetectAllConfiguredPatterns() {
        // 1. Deadlock
        verifyRisk("Deadlock found", RiskLevel.HIGH, "Deadlock detected");
        
        // 2. Lock wait timeout
        verifyRisk("Lock wait timeout exceeded", RiskLevel.HIGH, "Lock wait timeout detected");
        
        // 3. Connection timeout
        verifyRisk("Connection timeout occurred", RiskLevel.HIGH, "Connection timeout detected");
        
        // 4. Syntax error
        verifyRisk("You have an error in your SQL syntax", RiskLevel.MEDIUM, "SQL syntax error");
        verifyRisk("Syntax error near 'WHERE'", RiskLevel.MEDIUM, "SQL syntax error");
        
        // 5. Generic error
        verifyRisk("Unknown system failure", RiskLevel.LOW, "Generic execution error");
    }

    @Test
    void testChecker_shouldIgnoreSuccess() {
        ExecutionResult result = ExecutionResult.builder()
                .executionTimestamp(Instant.now())
                // No errorMessage
                .build();
        
        AuditResult auditResult = checker.check("SELECT 1", result);
        assertNotNull(auditResult);
        assertTrue(auditResult.getRisks().isEmpty());
    }

    private void verifyRisk(String errorMessage, RiskLevel expectedLevel, String expectedJustification) {
        ExecutionResult result = ExecutionResult.builder()
                .errorMessage(errorMessage)
                .executionTimestamp(Instant.now())
                .build();

        AuditResult auditResult = checker.check("SELECT * FROM t", result);

        assertNotNull(auditResult);
        assertEquals(1, auditResult.getRisks().size(), "Expected risk for error: " + errorMessage);
        assertEquals(expectedLevel, auditResult.getRisks().get(0).getSeverity());
        assertEquals(expectedJustification, auditResult.getRisks().get(0).getJustification());
    }
}
