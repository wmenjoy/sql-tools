package com.footstone.sqlguard.scanner.mybatis.model;

/**
 * Position where a parameter is used in SQL
 */
public enum SqlPosition {
    /** Used in WHERE clause */
    WHERE,
    
    /** Used in ORDER BY clause */
    ORDER_BY,
    
    /** Used in LIMIT clause */
    LIMIT,
    
    /** Used in SELECT columns */
    SELECT,
    
    /** Used in FROM/JOIN table names */
    TABLE_NAME,
    
    /** Used in SET clause (UPDATE) */
    SET,
    
    /** Used in other positions */
    OTHER
}

















