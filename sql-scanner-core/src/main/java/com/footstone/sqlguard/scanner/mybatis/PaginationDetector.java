package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.mybatis.model.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detector for pagination mechanisms
 * 
 * Detects:
 * - SQL LIMIT clause
 * - MyBatis RowBounds
 * - PageHelper (ThreadLocal-based)
 * - MyBatis-Plus IPage
 * - MyBatis-Plus Page
 */
public class PaginationDetector {
    
    private static final Pattern LIMIT_PATTERN = Pattern.compile(
        "LIMIT\\s+(\\d+|[#$]\\{\\w+\\})", 
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "SELECT\\s+.*?\\s+FROM\\s+\\w+",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    
    private static final Pattern WHERE_PATTERN = Pattern.compile(
        "WHERE",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Detect pagination information
     * 
     * @param combined Combined analysis result
     * @return Pagination information
     */
    public PaginationInfo detect(CombinedAnalysisResult combined) {
        PaginationInfo info = new PaginationInfo();
        
        // Check Java interface for pagination parameters
        if (combined.getMethodInfo() != null) {
            detectFromJavaInterface(combined.getMethodInfo(), info);
        }
        
        // Check SQL for LIMIT clause
        detectFromSql(combined, info);
        
        // Check if should warn about missing pagination
        checkMissingPagination(combined, info);
        
        return info;
    }
    
    /**
     * Detect pagination from Java interface
     */
    private void detectFromJavaInterface(MethodInfo methodInfo, PaginationInfo info) {
        PaginationType paginationType = methodInfo.getPaginationType();
        
        if (paginationType != PaginationType.NONE) {
            info.addType(paginationType);
        }
    }
    
    /**
     * Detect pagination from SQL
     */
    private void detectFromSql(CombinedAnalysisResult combined, PaginationInfo info) {
        // Get raw SQL from parameter usages
        String sql = extractSqlFromUsages(combined);
        
        if (sql == null || sql.isEmpty()) {
            return;
        }
        
        // Check for LIMIT clause
        Matcher limitMatcher = LIMIT_PATTERN.matcher(sql);
        if (limitMatcher.find()) {
            info.addType(PaginationType.SQL_LIMIT);
            
            String limitValue = limitMatcher.group(1);
            
            // Check if it's a static number or parameter
            if (limitValue.matches("\\d+")) {
                // Static limit
                int pageSize = Integer.parseInt(limitValue);
                info.setPageSize(pageSize);
                info.setDynamic(false);
            } else {
                // Dynamic limit (parameter)
                info.setDynamic(true);
            }
        }
    }
    
    /**
     * Extract SQL from combined result
     */
    private String extractSqlFromUsages(CombinedAnalysisResult combined) {
        return combined.getRawSql() != null ? combined.getRawSql() : "";
    }
    
    /**
     * Check if should warn about missing pagination
     */
    private void checkMissingPagination(CombinedAnalysisResult combined, PaginationInfo info) {
        // If already has pagination, no warning needed
        if (info.hasPagination()) {
            return;
        }
        
        // Check if this is a SELECT query without WHERE clause
        // This is a simplified heuristic - in practice, we'd need more context
        String statementId = combined.getStatementId();
        
        // If it's a select query and no pagination, should warn
        if (statementId.toLowerCase().contains("select")) {
            // Check if there are WHERE conditions
            boolean hasWhereCondition = combined.getParameterUsages().stream()
                .anyMatch(usage -> usage.getPosition() == SqlPosition.WHERE);
            
            if (!hasWhereCondition) {
                info.setShouldWarnMissingPagination(true);
            }
        }
    }
}

