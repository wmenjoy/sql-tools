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
            DummyConditionConfig dummyCondition = rules.getDummyCondition();
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
     */
    public static class RulesConfig {
        private NoWhereClauseConfig noWhereClause = new NoWhereClauseConfig();
        private DummyConditionConfig dummyCondition = new DummyConditionConfig();
        private BlacklistFieldsConfig blacklistFields = new BlacklistFieldsConfig();
        private WhitelistFieldsConfig whitelistFields = new WhitelistFieldsConfig();
        private PaginationAbuseConfig paginationAbuse = new PaginationAbuseConfig();
        private NoPaginationConfig noPagination = new NoPaginationConfig();
        private EstimatedRowsConfig estimatedRows = new EstimatedRowsConfig();

        public NoWhereClauseConfig getNoWhereClause() {
            return noWhereClause;
        }

        public void setNoWhereClause(NoWhereClauseConfig noWhereClause) {
            this.noWhereClause = noWhereClause;
        }

        public DummyConditionConfig getDummyCondition() {
            return dummyCondition;
        }

        public void setDummyCondition(DummyConditionConfig dummyCondition) {
            this.dummyCondition = dummyCondition;
        }

        public BlacklistFieldsConfig getBlacklistFields() {
            return blacklistFields;
        }

        public void setBlacklistFields(BlacklistFieldsConfig blacklistFields) {
            this.blacklistFields = blacklistFields;
        }

        public WhitelistFieldsConfig getWhitelistFields() {
            return whitelistFields;
        }

        public void setWhitelistFields(WhitelistFieldsConfig whitelistFields) {
            this.whitelistFields = whitelistFields;
        }

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
