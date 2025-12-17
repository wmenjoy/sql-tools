package com.footstone.sqlguard.scanner.mybatis.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of combined XML + Java analysis
 */
public class CombinedAnalysisResult {
    
    private final String statementId;
    private final MethodInfo methodInfo;
    private final List<ParameterUsage> parameterUsages = new ArrayList<>();
    private String rawSql;
    
    public CombinedAnalysisResult(String statementId, MethodInfo methodInfo) {
        this.statementId = statementId;
        this.methodInfo = methodInfo;
    }
    
    public String getStatementId() {
        return statementId;
    }
    
    public MethodInfo getMethodInfo() {
        return methodInfo;
    }
    
    public void addParameterUsage(ParameterUsage usage) {
        parameterUsages.add(usage);
    }
    
    public List<ParameterUsage> getParameterUsages() {
        return new ArrayList<>(parameterUsages);
    }
    
    public boolean hasPagination() {
        return methodInfo != null && methodInfo.hasPagination();
    }
    
    public String getRawSql() {
        return rawSql;
    }
    
    public void setRawSql(String rawSql) {
        this.rawSql = rawSql;
    }
}

