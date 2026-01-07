package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;

import java.util.*;

/**
 * Provides default configuration values for SqlGuardConfig.
 * These defaults represent sensible out-of-box settings for common scenarios.
 */
public class SqlGuardConfigDefaults {

    // Alias for validator.rule.impl config classes to avoid conflicts with config package classes
    private static final String IMPL_PKG = "com.footstone.sqlguard.validator.rule.impl.";

    /**
     * Returns a SqlGuardConfig with all default values.
     *
     * @return Default configuration
     */
    public static SqlGuardConfig getDefault() {
        SqlGuardConfig config = new SqlGuardConfig();
        
        // Root level defaults
        config.setEnabled(true);
        config.setActiveStrategy("prod");
        
        // Interceptors defaults
        SqlGuardConfig.InterceptorsConfig interceptors = new SqlGuardConfig.InterceptorsConfig();
        interceptors.getMybatis().setEnabled(true);
        interceptors.getMybatisPlus().setEnabled(false);
        interceptors.getJdbc().setEnabled(true);
        interceptors.getJdbc().setType("auto");
        config.setInterceptors(interceptors);
        
        // Deduplication defaults
        SqlGuardConfig.DeduplicationConfig deduplication = new SqlGuardConfig.DeduplicationConfig();
        deduplication.setEnabled(true);
        deduplication.setCacheSize(1000);
        deduplication.setTtlMs(100L);
        config.setDeduplication(deduplication);
        
        // Rules defaults
        SqlGuardConfig.RulesConfig rules = new SqlGuardConfig.RulesConfig();
        
        // ==================== Basic Security Checkers (1-4) ====================
        
        // NoWhereClause rule
        com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig noWhereClause = 
            new com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig();
        noWhereClause.setEnabled(true);
        noWhereClause.setRiskLevel(RiskLevel.CRITICAL);
        rules.setNoWhereClause(noWhereClause);
        
        // DummyCondition rule
        com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig dummyCondition = 
            new com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig();
        dummyCondition.setEnabled(true);
        dummyCondition.setRiskLevel(RiskLevel.HIGH);
        rules.setDummyCondition(dummyCondition);
        
        // BlacklistFields rule
        com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig blacklistFields = 
            new com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig(true, 
            new HashSet<>(Arrays.asList("deleted", "del_flag", "status")));
        blacklistFields.setRiskLevel(RiskLevel.HIGH);
        rules.setBlacklistFields(blacklistFields);
        
        // WhitelistFields rule
        com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig whitelistFields = 
            new com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig();
        whitelistFields.setEnabled(true);
        whitelistFields.setRiskLevel(RiskLevel.MEDIUM);
        whitelistFields.setFields(new ArrayList<>());
        whitelistFields.setByTable(new HashMap<>());
        whitelistFields.setEnforceForUnknownTables(false);
        rules.setWhitelistFields(whitelistFields);
        
        // ==================== SQL Injection Checkers (5-8) ====================
        
        // MultiStatement rule
        com.footstone.sqlguard.validator.rule.impl.MultiStatementConfig multiStatement = 
            new com.footstone.sqlguard.validator.rule.impl.MultiStatementConfig();
        multiStatement.setEnabled(true);
        multiStatement.setRiskLevel(RiskLevel.CRITICAL);
        rules.setMultiStatement(multiStatement);
        
        // SetOperation rule
        com.footstone.sqlguard.validator.rule.impl.SetOperationConfig setOperation = 
            new com.footstone.sqlguard.validator.rule.impl.SetOperationConfig();
        setOperation.setEnabled(true);
        setOperation.setRiskLevel(RiskLevel.CRITICAL);
        rules.setSetOperation(setOperation);
        
        // SqlComment rule
        com.footstone.sqlguard.validator.rule.impl.SqlCommentConfig sqlComment = 
            new com.footstone.sqlguard.validator.rule.impl.SqlCommentConfig();
        sqlComment.setEnabled(true);
        sqlComment.setRiskLevel(RiskLevel.CRITICAL);
        rules.setSqlComment(sqlComment);
        
        // IntoOutfile rule
        com.footstone.sqlguard.validator.rule.impl.IntoOutfileConfig intoOutfile = 
            new com.footstone.sqlguard.validator.rule.impl.IntoOutfileConfig();
        intoOutfile.setEnabled(true);
        intoOutfile.setRiskLevel(RiskLevel.CRITICAL);
        rules.setIntoOutfile(intoOutfile);
        
        // ==================== Dangerous Operations Checkers (9-11) ====================
        
        // DdlOperation rule
        com.footstone.sqlguard.validator.rule.impl.DdlOperationConfig ddlOperation = 
            new com.footstone.sqlguard.validator.rule.impl.DdlOperationConfig();
        ddlOperation.setEnabled(true);
        ddlOperation.setRiskLevel(RiskLevel.CRITICAL);
        rules.setDdlOperation(ddlOperation);
        
        // DangerousFunction rule
        com.footstone.sqlguard.validator.rule.impl.DangerousFunctionConfig dangerousFunction = 
            new com.footstone.sqlguard.validator.rule.impl.DangerousFunctionConfig();
        dangerousFunction.setEnabled(true);
        dangerousFunction.setRiskLevel(RiskLevel.CRITICAL);
        rules.setDangerousFunction(dangerousFunction);
        
        // CallStatement rule
        com.footstone.sqlguard.validator.rule.impl.CallStatementConfig callStatement = 
            new com.footstone.sqlguard.validator.rule.impl.CallStatementConfig();
        callStatement.setEnabled(true);
        callStatement.setRiskLevel(RiskLevel.HIGH);
        rules.setCallStatement(callStatement);
        
        // ==================== Access Control Checkers (12-15) ====================
        
        // MetadataStatement rule
        com.footstone.sqlguard.validator.rule.impl.MetadataStatementConfig metadataStatement = 
            new com.footstone.sqlguard.validator.rule.impl.MetadataStatementConfig();
        metadataStatement.setEnabled(true);
        metadataStatement.setRiskLevel(RiskLevel.HIGH);
        rules.setMetadataStatement(metadataStatement);
        
        // SetStatement rule
        com.footstone.sqlguard.validator.rule.impl.SetStatementConfig setStatement = 
            new com.footstone.sqlguard.validator.rule.impl.SetStatementConfig();
        setStatement.setEnabled(true);
        setStatement.setRiskLevel(RiskLevel.HIGH);
        rules.setSetStatement(setStatement);
        
        // DeniedTable rule
        com.footstone.sqlguard.validator.rule.impl.DeniedTableConfig deniedTable = 
            new com.footstone.sqlguard.validator.rule.impl.DeniedTableConfig();
        deniedTable.setEnabled(true);
        deniedTable.setRiskLevel(RiskLevel.CRITICAL);
        deniedTable.setDeniedTables(Arrays.asList("sys_*", "admin_*", "audit_log", "sensitive_data"));
        rules.setDeniedTable(deniedTable);
        
        // ReadOnlyTable rule
        com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableConfig readOnlyTable = 
            new com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableConfig();
        readOnlyTable.setEnabled(true);
        readOnlyTable.setRiskLevel(RiskLevel.HIGH);
        readOnlyTable.setReadonlyTables(Arrays.asList("audit_log", "history_*", "compliance_records"));
        rules.setReadOnlyTable(readOnlyTable);
        
        // ==================== Pagination and Performance Checkers ====================
        // These use config package classes, not validator.rule.impl
        
        // PaginationAbuse rule
        PaginationAbuseConfig paginationAbuse = new PaginationAbuseConfig();
        paginationAbuse.setEnabled(true);
        paginationAbuse.setRiskLevel(RiskLevel.HIGH);
        rules.setPaginationAbuse(paginationAbuse);
        
        // NoPagination rule
        NoPaginationConfig noPagination = new NoPaginationConfig();
        noPagination.setEnabled(true);
        noPagination.setRiskLevel(RiskLevel.MEDIUM);
        noPagination.setEnforceForAllQueries(false);
        noPagination.setWhitelistMapperIds(new ArrayList<>());
        noPagination.setWhitelistTables(new ArrayList<>());
        noPagination.setUniqueKeyFields(new ArrayList<>());
        rules.setNoPagination(noPagination);
        
        // EstimatedRows rule
        EstimatedRowsConfig estimatedRows = new EstimatedRowsConfig();
        estimatedRows.setEnabled(true);
        estimatedRows.setRiskLevel(RiskLevel.HIGH);
        rules.setEstimatedRows(estimatedRows);
        
        config.setRules(rules);
        
        return config;
    }
}
