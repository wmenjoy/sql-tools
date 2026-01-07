package com.footstone.sqlguard.config;

/**
 * Root configuration class for SQL Guard system.
 * Supports YAML deserialization and provides comprehensive configuration
 * for all validation rules, interceptors, and runtime behavior.
 */
public class SqlGuardConfig {

    private boolean enabled = true;
    private String activeStrategy = "prod";
    private ViolationStrategy violationStrategy = ViolationStrategy.BLOCK;
    private InterceptorsConfig interceptors = new InterceptorsConfig();
    private DeduplicationConfig deduplication = new DeduplicationConfig();
    private RulesConfig rules = new RulesConfig();

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getActiveStrategy() {
        return activeStrategy;
    }

    public void setActiveStrategy(String activeStrategy) {
        this.activeStrategy = activeStrategy;
    }

    public ViolationStrategy getViolationStrategy() {
        return violationStrategy;
    }

    public void setViolationStrategy(ViolationStrategy violationStrategy) {
        this.violationStrategy = violationStrategy;
    }

    public InterceptorsConfig getInterceptors() {
        return interceptors;
    }

    public void setInterceptors(InterceptorsConfig interceptors) {
        this.interceptors = interceptors;
    }

    public DeduplicationConfig getDeduplication() {
        return deduplication;
    }

    public void setDeduplication(DeduplicationConfig deduplication) {
        this.deduplication = deduplication;
    }

    public RulesConfig getRules() {
        return rules;
    }

    public void setRules(RulesConfig rules) {
        this.rules = rules;
    }

    /**
     * Validates the configuration for correctness.
     * Performs fail-fast validation to catch misconfigurations early.
     *
     * @throws IllegalArgumentException if validation fails with descriptive message
     */
    public void validate() throws IllegalArgumentException {
        // Validate activeStrategy
        if (activeStrategy != null && 
            !activeStrategy.equals("dev") && 
            !activeStrategy.equals("test") && 
            !activeStrategy.equals("prod")) {
            throw new IllegalArgumentException(
                "activeStrategy must be one of [dev, test, prod], got: " + activeStrategy);
        }

        // Validate deduplication config
        if (deduplication != null) {
            if (deduplication.getCacheSize() <= 0) {
                throw new IllegalArgumentException(
                    "deduplication.cacheSize must be > 0, got: " + deduplication.getCacheSize());
            }
            if (deduplication.getTtlMs() <= 0) {
                throw new IllegalArgumentException(
                    "deduplication.ttlMs must be > 0, got: " + deduplication.getTtlMs());
            }
        }

        // Validate pagination abuse config
        if (rules != null && rules.getPaginationAbuse() != null) {
            PaginationAbuseConfig paginationAbuse = rules.getPaginationAbuse();
            
            if (paginationAbuse.getPhysicalDeepPagination() != null) {
                if (paginationAbuse.getPhysicalDeepPagination().getMaxOffset() <= 0) {
                    throw new IllegalArgumentException(
                        "paginationAbuse.physicalDeepPagination.maxOffset must be > 0, got: " + 
                        paginationAbuse.getPhysicalDeepPagination().getMaxOffset());
                }
                if (paginationAbuse.getPhysicalDeepPagination().getMaxPageNum() <= 0) {
                    throw new IllegalArgumentException(
                        "paginationAbuse.physicalDeepPagination.maxPageNum must be > 0, got: " + 
                        paginationAbuse.getPhysicalDeepPagination().getMaxPageNum());
                }
            }
            
            if (paginationAbuse.getLargePageSize() != null) {
                if (paginationAbuse.getLargePageSize().getMaxPageSize() <= 0) {
                    throw new IllegalArgumentException(
                        "paginationAbuse.largePageSize.maxPageSize must be > 0, got: " + 
                        paginationAbuse.getLargePageSize().getMaxPageSize());
                }
            }
        }

        // Validate pattern lists not empty if configured
        if (rules != null && rules.getDummyCondition() != null) {
            com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig dummyCondition = rules.getDummyCondition();
            if (dummyCondition.isEnabled()) {
                if ((dummyCondition.getPatterns() == null || dummyCondition.getPatterns().isEmpty()) &&
                    (dummyCondition.getCustomPatterns() == null || dummyCondition.getCustomPatterns().isEmpty())) {
                    throw new IllegalArgumentException(
                        "dummyCondition.patterns or customPatterns must not be empty when rule is enabled");
                }
            }
        }
    }

    /**
     * Nested configuration for interceptor frameworks.
     */
    public static class InterceptorsConfig {
        private MyBatisConfig mybatis = new MyBatisConfig();
        private MyBatisPlusConfig mybatisPlus = new MyBatisPlusConfig();
        private JdbcConfig jdbc = new JdbcConfig();

        public MyBatisConfig getMybatis() {
            return mybatis;
        }

        public void setMybatis(MyBatisConfig mybatis) {
            this.mybatis = mybatis;
        }

        public MyBatisPlusConfig getMybatisPlus() {
            return mybatisPlus;
        }

        public void setMybatisPlus(MyBatisPlusConfig mybatisPlus) {
            this.mybatisPlus = mybatisPlus;
        }

        public JdbcConfig getJdbc() {
            return jdbc;
        }

        public void setJdbc(JdbcConfig jdbc) {
            this.jdbc = jdbc;
        }
    }

    /**
     * MyBatis interceptor configuration.
     */
    public static class MyBatisConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * MyBatis-Plus interceptor configuration.
     */
    public static class MyBatisPlusConfig {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * JDBC interceptor configuration.
     */
    public static class JdbcConfig {
        private boolean enabled = true;
        private String type = "auto";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /**
     * Deduplication cache configuration.
     */
    public static class DeduplicationConfig {
        private boolean enabled = true;
        private int cacheSize = 1000;
        private long ttlMs = 100L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCacheSize() {
            return cacheSize;
        }

        public void setCacheSize(int cacheSize) {
            this.cacheSize = cacheSize;
        }

        public long getTtlMs() {
            return ttlMs;
        }

        public void setTtlMs(long ttlMs) {
            this.ttlMs = ttlMs;
        }
    }

    /**
     * Container for all validation rule configurations.
     * All configurations use validator.rule.impl package classes for consistency.
     */
    public static class RulesConfig {
        // Basic security checkers (1-4)
        private com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig noWhereClause = 
            new com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig();
        private com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig dummyCondition = 
            new com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig();
        private com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig blacklistFields = 
            new com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig();
        private com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig whitelistFields = 
            new com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig();
        
        // SQL injection checkers (5-8)
        private com.footstone.sqlguard.validator.rule.impl.MultiStatementConfig multiStatement = 
            new com.footstone.sqlguard.validator.rule.impl.MultiStatementConfig();
        private com.footstone.sqlguard.validator.rule.impl.SetOperationConfig setOperation = 
            new com.footstone.sqlguard.validator.rule.impl.SetOperationConfig();
        private com.footstone.sqlguard.validator.rule.impl.SqlCommentConfig sqlComment = 
            new com.footstone.sqlguard.validator.rule.impl.SqlCommentConfig();
        private com.footstone.sqlguard.validator.rule.impl.IntoOutfileConfig intoOutfile = 
            new com.footstone.sqlguard.validator.rule.impl.IntoOutfileConfig();
        
        // Dangerous operation checkers (9-11)
        private com.footstone.sqlguard.validator.rule.impl.DdlOperationConfig ddlOperation = 
            new com.footstone.sqlguard.validator.rule.impl.DdlOperationConfig();
        private com.footstone.sqlguard.validator.rule.impl.DangerousFunctionConfig dangerousFunction = 
            new com.footstone.sqlguard.validator.rule.impl.DangerousFunctionConfig();
        private com.footstone.sqlguard.validator.rule.impl.CallStatementConfig callStatement = 
            new com.footstone.sqlguard.validator.rule.impl.CallStatementConfig();
        
        // Access control checkers (12-15)
        private com.footstone.sqlguard.validator.rule.impl.MetadataStatementConfig metadataStatement = 
            new com.footstone.sqlguard.validator.rule.impl.MetadataStatementConfig();
        private com.footstone.sqlguard.validator.rule.impl.SetStatementConfig setStatement = 
            new com.footstone.sqlguard.validator.rule.impl.SetStatementConfig();
        private com.footstone.sqlguard.validator.rule.impl.DeniedTableConfig deniedTable = 
            new com.footstone.sqlguard.validator.rule.impl.DeniedTableConfig();
        private com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableConfig readOnlyTable = 
            new com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableConfig();
        
        // Pagination and performance checkers
        private PaginationAbuseConfig paginationAbuse = new PaginationAbuseConfig();
        private NoPaginationConfig noPagination = new NoPaginationConfig();
        private EstimatedRowsConfig estimatedRows = new EstimatedRowsConfig();

        // Basic security checkers getters/setters
        public com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig getNoWhereClause() {
            return noWhereClause;
        }

        public void setNoWhereClause(com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig noWhereClause) {
            this.noWhereClause = noWhereClause;
        }

        public com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig getDummyCondition() {
            return dummyCondition;
        }

        public void setDummyCondition(com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig dummyCondition) {
            this.dummyCondition = dummyCondition;
        }

        public com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig getBlacklistFields() {
            return blacklistFields;
        }

        public void setBlacklistFields(com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig blacklistFields) {
            this.blacklistFields = blacklistFields;
        }

        public com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig getWhitelistFields() {
            return whitelistFields;
        }

        public void setWhitelistFields(com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig whitelistFields) {
            this.whitelistFields = whitelistFields;
        }

        // SQL injection checkers getters/setters
        public com.footstone.sqlguard.validator.rule.impl.MultiStatementConfig getMultiStatement() {
            return multiStatement;
        }

        public void setMultiStatement(com.footstone.sqlguard.validator.rule.impl.MultiStatementConfig multiStatement) {
            this.multiStatement = multiStatement;
        }

        public com.footstone.sqlguard.validator.rule.impl.SetOperationConfig getSetOperation() {
            return setOperation;
        }

        public void setSetOperation(com.footstone.sqlguard.validator.rule.impl.SetOperationConfig setOperation) {
            this.setOperation = setOperation;
        }

        public com.footstone.sqlguard.validator.rule.impl.SqlCommentConfig getSqlComment() {
            return sqlComment;
        }

        public void setSqlComment(com.footstone.sqlguard.validator.rule.impl.SqlCommentConfig sqlComment) {
            this.sqlComment = sqlComment;
        }

        public com.footstone.sqlguard.validator.rule.impl.IntoOutfileConfig getIntoOutfile() {
            return intoOutfile;
        }

        public void setIntoOutfile(com.footstone.sqlguard.validator.rule.impl.IntoOutfileConfig intoOutfile) {
            this.intoOutfile = intoOutfile;
        }

        // Dangerous operation checkers getters/setters
        public com.footstone.sqlguard.validator.rule.impl.DdlOperationConfig getDdlOperation() {
            return ddlOperation;
        }

        public void setDdlOperation(com.footstone.sqlguard.validator.rule.impl.DdlOperationConfig ddlOperation) {
            this.ddlOperation = ddlOperation;
        }

        public com.footstone.sqlguard.validator.rule.impl.DangerousFunctionConfig getDangerousFunction() {
            return dangerousFunction;
        }

        public void setDangerousFunction(com.footstone.sqlguard.validator.rule.impl.DangerousFunctionConfig dangerousFunction) {
            this.dangerousFunction = dangerousFunction;
        }

        public com.footstone.sqlguard.validator.rule.impl.CallStatementConfig getCallStatement() {
            return callStatement;
        }

        public void setCallStatement(com.footstone.sqlguard.validator.rule.impl.CallStatementConfig callStatement) {
            this.callStatement = callStatement;
        }

        // Access control checkers getters/setters
        public com.footstone.sqlguard.validator.rule.impl.MetadataStatementConfig getMetadataStatement() {
            return metadataStatement;
        }

        public void setMetadataStatement(com.footstone.sqlguard.validator.rule.impl.MetadataStatementConfig metadataStatement) {
            this.metadataStatement = metadataStatement;
        }

        public com.footstone.sqlguard.validator.rule.impl.SetStatementConfig getSetStatement() {
            return setStatement;
        }

        public void setSetStatement(com.footstone.sqlguard.validator.rule.impl.SetStatementConfig setStatement) {
            this.setStatement = setStatement;
        }

        public com.footstone.sqlguard.validator.rule.impl.DeniedTableConfig getDeniedTable() {
            return deniedTable;
        }

        public void setDeniedTable(com.footstone.sqlguard.validator.rule.impl.DeniedTableConfig deniedTable) {
            this.deniedTable = deniedTable;
        }

        public com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableConfig getReadOnlyTable() {
            return readOnlyTable;
        }

        public void setReadOnlyTable(com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableConfig readOnlyTable) {
            this.readOnlyTable = readOnlyTable;
        }

        // Pagination and performance checkers getters/setters
        public PaginationAbuseConfig getPaginationAbuse() {
            return paginationAbuse;
        }

        public void setPaginationAbuse(PaginationAbuseConfig paginationAbuse) {
            this.paginationAbuse = paginationAbuse;
        }

        public NoPaginationConfig getNoPagination() {
            return noPagination;
        }

        public void setNoPagination(NoPaginationConfig noPagination) {
            this.noPagination = noPagination;
        }

        public EstimatedRowsConfig getEstimatedRows() {
            return estimatedRows;
        }

        public void setEstimatedRows(EstimatedRowsConfig estimatedRows) {
            this.estimatedRows = estimatedRows;
        }
    }
}
