package com.footstone.sqlguard.scanner.mybatis.model;

/**
 * Information about a method parameter
 */
public class ParameterInfo {
    
    private final String name;
    private final String type;
    private final int index;
    
    public ParameterInfo(String name, String type, int index) {
        this.name = name;
        this.type = type;
        this.index = index;
    }
    
    public String getName() {
        return name;
    }
    
    public String getType() {
        return type;
    }
    
    public int getIndex() {
        return index;
    }
    
    public boolean isString() {
        return "String".equals(type) || "java.lang.String".equals(type);
    }
    
    public boolean isInteger() {
        return "Integer".equals(type) || "int".equals(type) || 
               "Long".equals(type) || "long".equals(type) ||
               "java.lang.Integer".equals(type) || "java.lang.Long".equals(type);
    }
}










