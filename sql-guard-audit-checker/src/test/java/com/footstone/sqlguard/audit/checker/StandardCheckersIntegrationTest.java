package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StandardCheckersIntegrationTest {

    @Test
    void testCompositeRisks_slowUnboundedUpdate_shouldTriggerMultipleRisks() {
        // Setup checkers
        List<AbstractAuditChecker> checkers = new ArrayList<>();
        checkers.add(new SlowQueryChecker(100, 500)); // Low threshold for test
        checkers.add(new ActualImpactNoWhereChecker());
        checkers.add(new UnboundedReadChecker());

        // Scenario: A slow UPDATE that modifies many rows without WHERE
        ExecutionResult result = ExecutionResult.builder()
                .executionTimeMs(600) // Critical (> 500)
                .rowsAffected(5000)
                .executionTimestamp(Instant.now())
                .build();

        String sql = "UPDATE users SET status = 1";

        // Execute all checkers
        List<AuditResult> results = new ArrayList<>();
        for (AbstractAuditChecker checker : checkers) {
            AuditResult r = checker.check(sql, result);
            if (!r.getRisks().isEmpty()) {
                results.add(r);
            }
        }

        // Verify
        assertEquals(2, results.size(), "Should trigger SlowQueryChecker and ActualImpactNoWhereChecker");
        
        // Check 1: SlowQueryChecker (Critical)
        boolean hasSlowCritical = results.stream()
                .anyMatch(r -> r.getCheckerId().equals("SlowQueryChecker") 
                        && r.getRisks().get(0).getSeverity() == RiskLevel.CRITICAL);
        assertTrue(hasSlowCritical, "Should detect critical slow query");

        // Check 2: ActualImpactNoWhereChecker (Critical)
        boolean hasUnboundedMutation = results.stream()
                .anyMatch(r -> r.getCheckerId().equals("ActualImpactNoWhereChecker") 
                        && r.getRisks().get(0).getSeverity() == RiskLevel.CRITICAL);
        assertTrue(hasUnboundedMutation, "Should detect unbounded mutation");
    }

    @Test
    void testCompositeRisks_slowLargeRead_shouldTriggerMultipleRisks() {
        List<AbstractAuditChecker> checkers = new ArrayList<>();
        checkers.add(new SlowQueryChecker(100, 500));
        checkers.add(new UnboundedReadChecker(1000)); // Low limit for test
        checkers.add(new LargeResultChecker(1000)); // Also checking new checker

        ExecutionResult result = ExecutionResult.builder()
                .executionTimeMs(200) // High (> 100)
                .resultSetSize(2000) // High (> 1000)
                .executionTimestamp(Instant.now())
                .build();

        String sql = "SELECT * FROM logs";

        List<AuditResult> results = new ArrayList<>();
        for (AbstractAuditChecker checker : checkers) {
            AuditResult r = checker.check(sql, result);
            if (!r.getRisks().isEmpty()) {
                results.add(r);
            }
        }

        // Expect 3: SlowQuery, UnboundedRead, LargeResult
        assertEquals(3, results.size());
        
        // Slow Query (High)
        assertTrue(results.stream().anyMatch(r -> r.getCheckerId().equals("SlowQueryChecker") 
                && r.getRisks().get(0).getSeverity() == RiskLevel.HIGH));
                
        // Unbounded Read (High)
        assertTrue(results.stream().anyMatch(r -> r.getCheckerId().equals("UnboundedReadChecker") 
                && r.getRisks().get(0).getSeverity() == RiskLevel.HIGH));
                
        // Large Result (High)
        assertTrue(results.stream().anyMatch(r -> r.getCheckerId().equals("LargeResultChecker") 
                && r.getRisks().get(0).getSeverity() == RiskLevel.HIGH));
    }

    @Test
    void testPerformanceCheckers_integration() {
        List<AbstractAuditChecker> checkers = new ArrayList<>();
        checkers.add(new LargeResultChecker(5000));
        checkers.add(new HighImpactMutationChecker(1000, 10000));

        // 1. Large Result
        ExecutionResult resultLarge = ExecutionResult.builder()
                .resultSetSize(6000)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditLarge = checkers.get(0).check("SELECT * FROM big", resultLarge);
        assertEquals(1, auditLarge.getRisks().size());
        assertEquals("LargeResultChecker", auditLarge.getCheckerId());
        
        // 2. High Impact Mutation (Critical)
        ExecutionResult resultMutation = ExecutionResult.builder()
                .rowsAffected(15000)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditMutation = checkers.get(1).check("UPDATE all", resultMutation);
        assertEquals(1, auditMutation.getRisks().size());
        assertEquals(RiskLevel.CRITICAL, auditMutation.getRisks().get(0).getSeverity());

        // 3. High Impact Mutation (Medium)
        ExecutionResult resultMutationMed = ExecutionResult.builder()
                .rowsAffected(5000)
                .executionTimestamp(Instant.now())
                .build();
        
        AuditResult auditMutationMed = checkers.get(1).check("UPDATE some", resultMutationMed);
        assertEquals(1, auditMutationMed.getRisks().size());
        assertEquals(RiskLevel.MEDIUM, auditMutationMed.getRisks().get(0).getSeverity());
    }
}
