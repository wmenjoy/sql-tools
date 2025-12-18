package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * Audit checker that detects queries exceeding execution time thresholds.
 */
public class SlowQueryChecker extends AbstractAuditChecker {

    private final long slowThresholdMs;
    private final long criticalThresholdMs;

    private static final long DEFAULT_SLOW_THRESHOLD_MS = 1000;
    private static final long DEFAULT_CRITICAL_THRESHOLD_MS = 5000;

    public SlowQueryChecker() {
        this(DEFAULT_SLOW_THRESHOLD_MS, DEFAULT_CRITICAL_THRESHOLD_MS);
    }

    public SlowQueryChecker(long slowThresholdMs, long criticalThresholdMs) {
        this.slowThresholdMs = slowThresholdMs;
        this.criticalThresholdMs = criticalThresholdMs;
    }

    @Override
    protected RiskScore performAudit(String sql, ExecutionResult result) {
        long executionTimeMs = result.getExecutionTimeMs();

        if (executionTimeMs > criticalThresholdMs) {
            return buildRiskScore(RiskLevel.CRITICAL, executionTimeMs, criticalThresholdMs);
        } else if (executionTimeMs > slowThresholdMs) {
            return buildRiskScore(RiskLevel.HIGH, executionTimeMs, slowThresholdMs);
        }

        return null;
    }

    private RiskScore buildRiskScore(RiskLevel severity, long executionTimeMs, long thresholdMs) {
        Map<String, Number> metrics = new HashMap<>();
        metrics.put("execution_time_ms", executionTimeMs);
        metrics.put("threshold_exceeded_ms", executionTimeMs - thresholdMs);
        metrics.put("threshold_limit_ms", thresholdMs);

        return RiskScore.builder()
                .severity(severity)
                .confidence(100)
                .justification(String.format("Query execution time (%dms) exceeded %s threshold (%dms)", 
                        executionTimeMs, severity.name(), thresholdMs))
                .impactMetrics(metrics)
                .build();
    }

    @Override
    public String getCheckerId() {
        return "SlowQueryChecker";
    }
}
