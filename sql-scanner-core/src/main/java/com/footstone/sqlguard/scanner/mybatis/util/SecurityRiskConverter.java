package com.footstone.sqlguard.scanner.mybatis.util;

import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.scanner.mybatis.model.RiskLevel;
import com.footstone.sqlguard.scanner.mybatis.model.SecurityRisk;

/**
 * Converts SecurityRisk from semantic analysis to ViolationInfo for reporting.
 * 
 * <p>This converter bridges the gap between the MyBatis semantic analysis module
 * and the core validation framework, allowing risks detected during semantic
 * analysis to be reported alongside other SQL violations.</p>
 * 
 * <p><strong>Conversion Rules:</strong></p>
 * <ul>
 *   <li>Rule ID: "MYBATIS_" + risk type (e.g., "MYBATIS_SQL_INJECTION")</li>
 *   <li>Risk Level: Direct mapping from semantic RiskLevel to core RiskLevel</li>
 *   <li>Location: Combines mapper ID with risk location</li>
 *   <li>Message and Recommendation: Pass-through from SecurityRisk</li>
 * </ul>
 * 
 * @see SecurityRisk
 * @see ViolationInfo
 */
public class SecurityRiskConverter {

    private static final String RULE_ID_PREFIX = "MYBATIS_";

    /**
     * Converts a SecurityRisk to a ViolationInfo.
     * 
     * @param risk the security risk from semantic analysis (must not be null)
     * @param mapperId the mapper ID (namespace.methodId) (must not be null or empty)
     * @return the corresponding ViolationInfo
     * @throws IllegalArgumentException if risk is null or mapperId is null/empty
     */
    public static ViolationInfo toViolationInfo(SecurityRisk risk, String mapperId) {
        if (risk == null) {
            throw new IllegalArgumentException("risk cannot be null");
        }
        if (mapperId == null || mapperId.trim().isEmpty()) {
            throw new IllegalArgumentException("mapperId cannot be null or empty");
        }

        // Convert risk level
        com.footstone.sqlguard.core.model.RiskLevel coreRiskLevel = convertRiskLevel(risk.getLevel());

        // Format message with location context
        String message = formatMessage(risk, mapperId);

        // Create ViolationInfo (riskLevel, message, suggestion)
        return new ViolationInfo(
            coreRiskLevel,
            message,
            risk.getRecommendation()
        );
    }

    /**
     * Converts semantic RiskLevel to core RiskLevel.
     * 
     * @param semanticLevel the semantic analysis risk level
     * @return the corresponding core risk level
     */
    private static com.footstone.sqlguard.core.model.RiskLevel convertRiskLevel(RiskLevel semanticLevel) {
        switch (semanticLevel) {
            case CRITICAL:
                return com.footstone.sqlguard.core.model.RiskLevel.CRITICAL;
            case HIGH:
                return com.footstone.sqlguard.core.model.RiskLevel.HIGH;
            case MEDIUM:
                return com.footstone.sqlguard.core.model.RiskLevel.MEDIUM;
            case LOW:
                return com.footstone.sqlguard.core.model.RiskLevel.LOW;
            case INFO:
                // Core doesn't have INFO, map to LOW
                return com.footstone.sqlguard.core.model.RiskLevel.LOW;
            default:
                // Should never happen, but default to MEDIUM for safety
                return com.footstone.sqlguard.core.model.RiskLevel.MEDIUM;
        }
    }

    /**
     * Formats the message by combining risk message with context.
     * 
     * @param risk the security risk
     * @param mapperId the mapper ID
     * @return formatted message string
     */
    private static String formatMessage(SecurityRisk risk, String mapperId) {
        StringBuilder message = new StringBuilder();
        
        // Add mapper context
        message.append("[").append(mapperId).append("] ");
        
        // Add parameter context if available
        if (risk.getParameterName() != null && !risk.getParameterName().isEmpty()) {
            message.append("Parameter '").append(risk.getParameterName()).append("' ");
            if (risk.getParameterType() != null) {
                message.append("(").append(risk.getParameterType()).append(") ");
            }
        }
        
        // Add position context
        if (risk.getPosition() != null) {
            message.append("in ").append(risk.getPosition()).append(": ");
        }
        
        // Add the actual message
        message.append(risk.getMessage());
        
        return message.toString();
    }
}

