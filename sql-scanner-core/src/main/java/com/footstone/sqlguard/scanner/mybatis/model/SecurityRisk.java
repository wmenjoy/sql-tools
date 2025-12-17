package com.footstone.sqlguard.scanner.mybatis.model;

/**
 * Represents a security risk found in SQL
 */
public class SecurityRisk {
    
    private final String parameterName;
    private final RiskLevel level;
    private final String message;
    private final String recommendation;
    private final SqlPosition position;
    private final String parameterType;
    
    public SecurityRisk(String parameterName, RiskLevel level, String message, 
                       String recommendation, SqlPosition position, String parameterType) {
        this.parameterName = parameterName;
        this.level = level;
        this.message = message;
        this.recommendation = recommendation;
        this.position = position;
        this.parameterType = parameterType;
    }
    
    public String getParameterName() {
        return parameterName;
    }
    
    public RiskLevel getLevel() {
        return level;
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getRecommendation() {
        return recommendation;
    }
    
    public SqlPosition getPosition() {
        return position;
    }
    
    public String getParameterType() {
        return parameterType;
    }
}

