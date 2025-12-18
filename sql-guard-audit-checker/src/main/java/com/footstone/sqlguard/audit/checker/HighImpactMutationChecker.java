package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;

/**
 * Checker that detects high impact mutations (UPDATE/DELETE affecting many rows).
 */
public class HighImpactMutationChecker extends AbstractAuditChecker {

    private final long riskThreshold;
    private final long criticalThreshold;
    
    private static final long DEFAULT_RISK_THRESHOLD = 1000;
    private static final long DEFAULT_CRITICAL_THRESHOLD = 10000;

    public HighImpactMutationChecker() {
        this(DEFAULT_RISK_THRESHOLD, DEFAULT_CRITICAL_THRESHOLD);
    }

    public HighImpactMutationChecker(long riskThreshold, long criticalThreshold) {
        this.riskThreshold = riskThreshold;
        this.criticalThreshold = criticalThreshold;
    }

    @Override
    protected RiskScore performAudit(String sql, ExecutionResult result) {
        int rowsAffected = result.getRowsAffected();
        
        // Ignore if rowsAffected is negative (usually SELECT) or 0 (no impact, safe)
        if (rowsAffected <= 0) {
            return null;
        }

        if (rowsAffected > criticalThreshold) {
            return RiskScore.builder()
                    .severity(RiskLevel.CRITICAL)
                    .confidence(100)
                    .justification(String.format("High impact mutation: %d rows modified. Ensure this batch size is intended.", rowsAffected))
                    .build();
        }

        if (rowsAffected > riskThreshold) {
            return RiskScore.builder()
                    .severity(RiskLevel.MEDIUM)
                    .confidence(100)
                    .justification(String.format("High impact mutation: %d rows modified. Ensure this batch size is intended.", rowsAffected))
                    .build();
        }

        return null;
    }

    @Override
    public String getCheckerId() {
        return "HighImpactMutationChecker";
    }
}
