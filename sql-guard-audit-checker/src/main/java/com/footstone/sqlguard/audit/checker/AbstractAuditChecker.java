package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;

import java.time.Instant;

/**
 * Base class for all audit checkers.
 * 
 * <p>Implements the Template Method pattern to ensure consistent audit lifecycle:
 * Validation -> Execution -> Result Building.</p>
 */
public abstract class AbstractAuditChecker {

    /**
     * Performs the audit check.
     * 
     * @param sql the SQL statement
     * @param result the execution metrics
     * @return the audit result
     * @throws IllegalArgumentException if inputs are invalid
     */
    public final AuditResult check(String sql, ExecutionResult result) {
        validateInput(sql, result);
        RiskScore score = performAudit(sql, result);
        return buildAuditResult(sql, result, score);
    }

    protected void validateInput(String sql, ExecutionResult result) {
        if (sql == null) {
            throw new IllegalArgumentException("sql cannot be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("executionResult cannot be null");
        }
    }

    protected abstract RiskScore performAudit(String sql, ExecutionResult result);

    public abstract String getCheckerId();

    protected AuditResult buildAuditResult(String sql, ExecutionResult result, RiskScore score) {
        return AuditResult.builder()
                .checkerId(getCheckerId())
                .sql(sql)
                .executionResult(result)
                .addRisk(score)
                .auditTimestamp(Instant.now())
                .build();
    }
    
    // Utility methods
    protected boolean isSlowQuery(long executionTimeMs, long thresholdMs) {
        return executionTimeMs > thresholdMs;
    }
    
    protected long calculateImpactScore(int rowsAffected) {
        if (rowsAffected < 0) return 0;
        return rowsAffected; // Simple pass-through for now, can be complex formula
    }
}
