package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;

import java.util.*;

/**
 * Provides default configuration values for SqlGuardConfig.
 * These defaults represent sensible out-of-box settings for common scenarios.
 */
public class SqlGuardConfigDefaults {

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
        
        // NoWhereClause rule
        NoWhereClauseConfig noWhereClause = new NoWhereClauseConfig();
        noWhereClause.setEnabled(true);
        noWhereClause.setRiskLevel(RiskLevel.CRITICAL);
        rules.setNoWhereClause(noWhereClause);
        
        // DummyCondition rule
        DummyConditionConfig dummyCondition = new DummyConditionConfig();
        dummyCondition.setEnabled(true);
        dummyCondition.setRiskLevel(RiskLevel.HIGH);
        rules.setDummyCondition(dummyCondition);
        
        // BlacklistFields rule
        BlacklistFieldsConfig blacklistFields = new BlacklistFieldsConfig(true, 
            new HashSet<>(Arrays.asList("deleted", "del_flag", "status")));
        blacklistFields.setRiskLevel(RiskLevel.HIGH);
        rules.setBlacklistFields(blacklistFields);
        
        // WhitelistFields rule
        WhitelistFieldsConfig whitelistFields = new WhitelistFieldsConfig();
        whitelistFields.setEnabled(true);
        whitelistFields.setRiskLevel(RiskLevel.MEDIUM);
        whitelistFields.setFields(new ArrayList<>());
        whitelistFields.setByTable(new HashMap<>());
        whitelistFields.setEnforceForUnknownTables(false);
        rules.setWhitelistFields(whitelistFields);
        
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
