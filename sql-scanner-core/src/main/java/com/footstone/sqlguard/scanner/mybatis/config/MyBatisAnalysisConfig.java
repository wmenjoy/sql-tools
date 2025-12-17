package com.footstone.sqlguard.scanner.mybatis.config;

/**
 * Configuration for MyBatis semantic analysis
 */
public class MyBatisAnalysisConfig {
    
    // Pagination settings
    private int maxPageSize = 1000;
    private boolean warnMissingPagination = true;
    
    // Risk assessment settings
    private boolean enableParameterRiskAnalysis = true;
    private boolean enablePaginationDetection = true;
    private boolean enableDynamicConditionAnalysis = true;
    
    // Dynamic condition settings
    private boolean warnWhereClauseMightDisappear = true;
    private boolean warnAlwaysTrueCondition = true;
    private boolean warnNoWhereClause = true;
    
    public int getMaxPageSize() {
        return maxPageSize;
    }
    
    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }
    
    public boolean isWarnMissingPagination() {
        return warnMissingPagination;
    }
    
    public void setWarnMissingPagination(boolean warnMissingPagination) {
        this.warnMissingPagination = warnMissingPagination;
    }
    
    public boolean isEnableParameterRiskAnalysis() {
        return enableParameterRiskAnalysis;
    }
    
    public void setEnableParameterRiskAnalysis(boolean enableParameterRiskAnalysis) {
        this.enableParameterRiskAnalysis = enableParameterRiskAnalysis;
    }
    
    public boolean isEnablePaginationDetection() {
        return enablePaginationDetection;
    }
    
    public void setEnablePaginationDetection(boolean enablePaginationDetection) {
        this.enablePaginationDetection = enablePaginationDetection;
    }
    
    public boolean isEnableDynamicConditionAnalysis() {
        return enableDynamicConditionAnalysis;
    }
    
    public void setEnableDynamicConditionAnalysis(boolean enableDynamicConditionAnalysis) {
        this.enableDynamicConditionAnalysis = enableDynamicConditionAnalysis;
    }
    
    public boolean isWarnWhereClauseMightDisappear() {
        return warnWhereClauseMightDisappear;
    }
    
    public void setWarnWhereClauseMightDisappear(boolean warnWhereClauseMightDisappear) {
        this.warnWhereClauseMightDisappear = warnWhereClauseMightDisappear;
    }
    
    public boolean isWarnAlwaysTrueCondition() {
        return warnAlwaysTrueCondition;
    }
    
    public void setWarnAlwaysTrueCondition(boolean warnAlwaysTrueCondition) {
        this.warnAlwaysTrueCondition = warnAlwaysTrueCondition;
    }
    
    public boolean isWarnNoWhereClause() {
        return warnNoWhereClause;
    }
    
    public void setWarnNoWhereClause(boolean warnNoWhereClause) {
        this.warnNoWhereClause = warnNoWhereClause;
    }
    
    /**
     * Create default configuration
     */
    public static MyBatisAnalysisConfig createDefault() {
        return new MyBatisAnalysisConfig();
    }
    
    /**
     * Create strict configuration (all checks enabled)
     */
    public static MyBatisAnalysisConfig createStrict() {
        MyBatisAnalysisConfig config = new MyBatisAnalysisConfig();
        config.setMaxPageSize(500);  // Stricter page size limit
        return config;
    }
    
    /**
     * Create lenient configuration (some checks disabled)
     */
    public static MyBatisAnalysisConfig createLenient() {
        MyBatisAnalysisConfig config = new MyBatisAnalysisConfig();
        config.setWarnMissingPagination(false);
        config.setWarnNoWhereClause(false);
        config.setMaxPageSize(5000);
        return config;
    }
}

