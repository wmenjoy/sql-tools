package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.mybatis.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyzer for parameter-based security risks
 * 
 * Provides intelligent risk assessment based on:
 * - Parameter type (String, Integer, etc.)
 * - Parameter position (WHERE, ORDER BY, LIMIT, etc.)
 * - Parameter usage (#{} vs ${})
 */
public class ParameterRiskAnalyzer {
    
    /**
     * Analyze security risks in a combined analysis result
     * 
     * @param combined Combined analysis result
     * @return List of security risks
     */
    public List<SecurityRisk> analyze(CombinedAnalysisResult combined) {
        List<SecurityRisk> risks = new ArrayList<>();
        
        for (ParameterUsage usage : combined.getParameterUsages()) {
            // Skip safe parameters (#{})
            if (usage.isSafe()) {
                continue;
            }
            
            // Get parameter type from method info
            String paramType = getParameterType(combined.getMethodInfo(), usage.getParameterName());
            
            // Assess risk based on type and position
            RiskLevel level = assessRiskLevel(usage, paramType);
            
            if (level != null) {
                String message = buildMessage(usage, paramType, level);
                String recommendation = buildRecommendation(usage, paramType, level);
                
                SecurityRisk risk = new SecurityRisk(
                    usage.getParameterName(),
                    level,
                    message,
                    recommendation,
                    usage.getPosition(),
                    paramType
                );
                
                risks.add(risk);
            }
        }
        
        return risks;
    }
    
    /**
     * Get parameter type from method info
     */
    private String getParameterType(MethodInfo methodInfo, String paramName) {
        if (methodInfo == null) {
            return "String";  // Assume worst case
        }
        
        ParameterInfo paramInfo = methodInfo.getParameter(paramName);
        if (paramInfo == null) {
            return "String";  // Assume worst case
        }
        
        return paramInfo.getType();
    }
    
    /**
     * Assess risk level based on parameter usage and type
     * 
     * Risk Matrix:
     * 
     * | Position   | String    | Integer | Other |
     * |------------|-----------|---------|-------|
     * | WHERE      | CRITICAL  | HIGH    | HIGH  |
     * | TABLE_NAME | CRITICAL  | CRITICAL| CRITICAL |
     * | ORDER BY   | HIGH      | MEDIUM  | MEDIUM|
     * | LIMIT      | MEDIUM    | LOW     | LOW   |
     * | SET        | HIGH      | MEDIUM  | MEDIUM|
     * | SELECT     | HIGH      | MEDIUM  | MEDIUM|
     * | OTHER      | MEDIUM    | LOW     | LOW   |
     */
    private RiskLevel assessRiskLevel(ParameterUsage usage, String paramType) {
        SqlPosition position = usage.getPosition();
        boolean isString = isStringType(paramType);
        boolean isInteger = isIntegerType(paramType);
        
        switch (position) {
            case WHERE:
                return isString ? RiskLevel.CRITICAL : RiskLevel.HIGH;
                
            case TABLE_NAME:
                return RiskLevel.CRITICAL;
                
            case ORDER_BY:
                if (isString) {
                    return RiskLevel.HIGH;
                } else if (isInteger) {
                    return RiskLevel.MEDIUM;
                } else {
                    return RiskLevel.MEDIUM;
                }
                
            case LIMIT:
                if (isString) {
                    return RiskLevel.MEDIUM;
                } else if (isInteger) {
                    return RiskLevel.LOW;
                } else {
                    return RiskLevel.LOW;
                }
                
            case SET:
                return isString ? RiskLevel.HIGH : RiskLevel.MEDIUM;
                
            case SELECT:
                return isString ? RiskLevel.HIGH : RiskLevel.MEDIUM;
                
            case OTHER:
            default:
                return isString ? RiskLevel.MEDIUM : RiskLevel.LOW;
        }
    }
    
    /**
     * Build risk message
     */
    private String buildMessage(ParameterUsage usage, String paramType, RiskLevel level) {
        StringBuilder msg = new StringBuilder();
        
        msg.append("Parameter '").append(usage.getParameterName()).append("' ");
        msg.append("(type: ").append(paramType).append(") ");
        msg.append("is used with ${} in ").append(usage.getPosition()).append(" clause");
        
        if (level == RiskLevel.CRITICAL) {
            msg.append(" - HIGH RISK of SQL injection!");
        } else if (level == RiskLevel.HIGH) {
            msg.append(" - Potential SQL injection risk");
        } else if (level == RiskLevel.MEDIUM) {
            msg.append(" - Moderate security concern");
        } else {
            msg.append(" - Low security risk");
        }
        
        return msg.toString();
    }
    
    /**
     * Build recommendation
     */
    private String buildRecommendation(ParameterUsage usage, String paramType, RiskLevel level) {
        SqlPosition position = usage.getPosition();
        
        switch (position) {
            case WHERE:
            case TABLE_NAME:
                return "Use #{} instead of ${} to prevent SQL injection. " +
                       "If dynamic SQL is required, implement strict input validation and whitelist checking.";
                
            case ORDER_BY:
                if (isStringType(paramType)) {
                    return "Validate '${" + usage.getParameterName() + "}' against a whitelist of allowed column names. " +
                           "Consider using CASE WHEN or mapping to prevent SQL injection.";
                } else {
                    return "Validate that the value is a valid column index. " +
                           "Consider using a whitelist approach.";
                }
                
            case LIMIT:
                if (isIntegerType(paramType)) {
                    return "Validate that '${" + usage.getParameterName() + "}' is a positive integer " +
                           "and within acceptable range (e.g., <= 1000).";
                } else {
                    return "Use Integer type for LIMIT parameter and validate the value range.";
                }
                
            case SET:
            case SELECT:
                return "Avoid using ${} in " + position + " clause. " +
                       "If dynamic columns are required, use a whitelist approach.";
                
            default:
                return "Review the usage of ${} and consider using #{} if possible. " +
                       "Implement input validation if ${} is necessary.";
        }
    }
    
    /**
     * Check if type is String
     */
    private boolean isStringType(String type) {
        return type != null && 
               (type.equals("String") || type.equals("java.lang.String"));
    }
    
    /**
     * Check if type is Integer
     */
    private boolean isIntegerType(String type) {
        return type != null && 
               (type.equals("Integer") || type.equals("int") ||
                type.equals("Long") || type.equals("long") ||
                type.equals("java.lang.Integer") || type.equals("java.lang.Long"));
    }
}















