package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ActualImpactNoWhereCheckerTest {

    private final ActualImpactNoWhereChecker checker = new ActualImpactNoWhereChecker();

    @Test
    void testCheck_updateWithoutWhere_shouldBeCritical() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(10)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditResult = checker.check("UPDATE users SET status = 1", result);
        
        assertEquals(1, auditResult.getRisks().size());
        RiskScore risk = auditResult.getRisks().get(0);
        assertEquals(RiskLevel.CRITICAL, risk.getSeverity());
        assertEquals(100, risk.getConfidence());
        assertTrue(risk.getJustification().contains("without WHERE clause modified 10 rows"));
    }

    @Test
    void testCheck_deleteWithoutWhere_shouldBeCritical() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(50)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditResult = checker.check("DELETE FROM logs", result);
        
        assertEquals(1, auditResult.getRisks().size());
        assertEquals(RiskLevel.CRITICAL, auditResult.getRisks().get(0).getSeverity());
    }

    @Test
    void testCheck_updateWithWhere_shouldBeSafe() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(1)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditResult = checker.check("UPDATE users SET status = 1 WHERE id = 1", result);
        
        assertTrue(auditResult.getRisks().isEmpty());
    }

    @Test
    void testCheck_deleteWithWhere_shouldBeSafe() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(1)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditResult = checker.check("DELETE FROM logs WHERE created_at < '2020-01-01'", result);
        
        assertTrue(auditResult.getRisks().isEmpty());
    }

    @Test
    void testCheck_selectWithoutWhere_shouldBeIgnoredByThisChecker() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(0)
                .resultSetSize(100)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditResult = checker.check("SELECT * FROM users", result);
        
        assertTrue(auditResult.getRisks().isEmpty());
    }

    @Test
    void testCheck_updateWithoutWhereButZeroRows_shouldMaybeBeLessCriticalOrIgnored() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(0)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditResult = checker.check("UPDATE users SET status = 1", result);
        
        assertTrue(auditResult.getRisks().isEmpty(), "Should ignore if 0 rows affected (no actual impact)");
    }
    
    @Test
    void testCheck_malformedSql_shouldHandleGracefully() {
        ExecutionResult result = ExecutionResult.builder()
                .rowsAffected(10)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditResult = checker.check("UPDATE users SET status = ? WHERE", result);
        
        assertNotNull(auditResult);
    }
}
