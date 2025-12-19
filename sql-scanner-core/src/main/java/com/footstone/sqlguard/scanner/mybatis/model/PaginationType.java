package com.footstone.sqlguard.scanner.mybatis.model;

/**
 * Types of pagination mechanisms
 */
public enum PaginationType {
    /** No pagination */
    NONE,
    
    /** MyBatis RowBounds */
    MYBATIS_ROWBOUNDS,
    
    /** PageHelper (ThreadLocal-based, no parameter) */
    PAGEHELPER,
    
    /** MyBatis-Plus IPage */
    MYBATIS_PLUS_IPAGE,
    
    /** MyBatis-Plus Page */
    MYBATIS_PLUS_PAGE,
    
    /** SQL LIMIT clause */
    SQL_LIMIT
}









