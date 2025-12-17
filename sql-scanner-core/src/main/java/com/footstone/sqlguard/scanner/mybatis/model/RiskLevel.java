package com.footstone.sqlguard.scanner.mybatis.model;

/**
 * Risk level for security issues
 */
public enum RiskLevel {
    /** Informational - not a security risk, just a best practice suggestion */
    INFO,
    
    /** Low risk - unlikely to be exploited */
    LOW,
    
    /** Medium risk - could be exploited under certain conditions */
    MEDIUM,
    
    /** High risk - likely to be exploited */
    HIGH,
    
    /** Critical risk - immediate security threat */
    CRITICAL
}

