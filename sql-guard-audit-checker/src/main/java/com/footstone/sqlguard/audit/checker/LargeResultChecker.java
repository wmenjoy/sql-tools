package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;

/**
 * Checker that detects excessive result set sizes.
 */
public class LargeResultChecker extends AbstractAuditChecker {

    private final long maxResultSize;
    private static final long DEFAULT_MAX_RESULT_SIZE = 5000;

    public LargeResultChecker() {
        this(DEFAULT_MAX_RESULT_SIZE);
    }

    public LargeResultChecker(long maxResultSize) {
        this.maxResultSize = maxResultSize;
    }

    @Override
    protected RiskScore performAudit(String sql, ExecutionResult result) {
        Integer resultSetSize = result.getResultSetSize();
        
        // Ignore if metrics are missing or if it's likely a mutation (size < 0)
        if (resultSetSize == null || resultSetSize < 0) {
            return null;
        }

        if (resultSetSize > maxResultSize) {
            return RiskScore.builder()
                    .severity(RiskLevel.HIGH)
                    .confidence(100)
                    .justification(String.format("Large result set: %d rows returned, exceeding threshold %d. May cause OOM or network saturation.", resultSetSize, maxResultSize))
                    .build();
        }

        return null;
    }

    @Override
    public String getCheckerId() {
        return "LargeResultChecker";
    }
}
