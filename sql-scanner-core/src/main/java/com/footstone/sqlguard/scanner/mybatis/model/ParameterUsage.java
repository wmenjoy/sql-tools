package com.footstone.sqlguard.scanner.mybatis.model;

/**
 * Represents how a parameter is used in SQL
 */
public class ParameterUsage {
    
    private final String parameterName;
    private final SqlPosition position;
    private final boolean isDynamic;  // true if used with ${}, false if used with #{}
    
    public ParameterUsage(String parameterName, SqlPosition position, boolean isDynamic) {
        this.parameterName = parameterName;
        this.position = position;
        this.isDynamic = isDynamic;
    }
    
    public String getParameterName() {
        return parameterName;
    }
    
    public SqlPosition getPosition() {
        return position;
    }
    
    public boolean isDynamic() {
        return isDynamic;
    }
    
    public boolean isSafe() {
        return !isDynamic;  // #{} is safe, ${} is potentially unsafe
    }
}

















