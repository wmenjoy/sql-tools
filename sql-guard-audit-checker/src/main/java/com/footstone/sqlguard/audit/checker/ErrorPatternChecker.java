package com.footstone.sqlguard.audit.checker;

import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checker that analyzes execution error messages for known failure patterns.
 * 
 * <p>Detects:</p>
 * <ul>
 *   <li>Deadlocks (HIGH)</li>
 *   <li>Lock timeouts (HIGH)</li>
 *   <li>Connection timeouts (HIGH)</li>
 *   <li>Syntax errors (MEDIUM)</li>
 *   <li>Generic errors (LOW)</li>
 * </ul>
 */
public class ErrorPatternChecker extends AbstractAuditChecker {

    private static final Map<Pattern, RiskConfig> PATTERNS = new HashMap<>();

    static {
        PATTERNS.put(Pattern.compile("deadlock", Pattern.CASE_INSENSITIVE), 
            new RiskConfig(RiskLevel.HIGH, "Deadlock detected"));
        PATTERNS.put(Pattern.compile("lock wait timeout", Pattern.CASE_INSENSITIVE), 
            new RiskConfig(RiskLevel.HIGH, "Lock wait timeout detected"));
        PATTERNS.put(Pattern.compile("connection timeout", Pattern.CASE_INSENSITIVE), 
            new RiskConfig(RiskLevel.HIGH, "Connection timeout detected"));
        PATTERNS.put(Pattern.compile("syntax error|SQL syntax", Pattern.CASE_INSENSITIVE), 
            new RiskConfig(RiskLevel.MEDIUM, "SQL syntax error"));
    }

    private static class RiskConfig {
        final RiskLevel level;
        final String justification;

        RiskConfig(RiskLevel level, String justification) {
            this.level = level;
            this.justification = justification;
        }
    }

    @Override
    protected RiskScore performAudit(String sql, ExecutionResult result) {
        String error = result.getErrorMessage();
        if (error == null || error.trim().isEmpty()) {
            return null;
        }

        for (Map.Entry<Pattern, RiskConfig> entry : PATTERNS.entrySet()) {
            if (entry.getKey().matcher(error).find()) {
                RiskConfig config = entry.getValue();
                return RiskScore.builder()
                        .severity(config.level)
                        .confidence(100)
                        .justification(config.justification)
                        .build();
            }
        }

        // Generic error
        return RiskScore.builder()
                .severity(RiskLevel.LOW)
                .confidence(80)
                .justification("Generic execution error")
                .build();
    }

    @Override
    public String getCheckerId() {
        return "ErrorPatternChecker";
    }
}
