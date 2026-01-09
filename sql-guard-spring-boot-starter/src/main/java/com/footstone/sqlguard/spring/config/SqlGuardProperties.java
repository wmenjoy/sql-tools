package com.footstone.sqlguard.spring.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for SQL Guard.
 *
 * <p>This class binds all sql-guard.* properties from application.yml to type-safe Java objects
 * with JSR-303 validation and IDE autocomplete support via spring-configuration-metadata.json.</p>
 *
 * <p><strong>Example YAML Configuration:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   enabled: true
 *   active-strategy: BLOCK
 *   interceptors:
 *     mybatis:
 *       enabled: true
 *   deduplication:
 *     enabled: true
 *     cache-size: 1000
 *     ttl-ms: 100
 *   rules:
 *     no-where-clause:
 *       enabled: true
 *       risk-level: CRITICAL
 * }</pre>
 *
 * @see org.springframework.boot.context.properties.ConfigurationProperties
 * @see org.springframework.validation.annotation.Validated
 */
@ConfigurationProperties(prefix = "sql-guard")
@Validated
public class SqlGuardProperties {

    /**
     * Global switch to enable/disable SQL Safety Guard.
     *
     * <p>When set to {@code false}, ALL SQL Guard functionality is bypassed at the interceptor level.
     * This provides a single control point to completely disable SQL Guard without removing
     * the dependency or modifying individual checker configurations.</p>
     *
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li><strong>Emergency bypass:</strong> Quickly disable all checks if SQL Guard causes issues in production</li>
     *   <li><strong>Performance testing:</strong> Compare application performance with/without SQL Guard</li>
     *   <li><strong>Development mode:</strong> Disable checks during development for faster iteration</li>
     *   <li><strong>Gradual rollout:</strong> Enable SQL Guard in specific environments only</li>
     * </ul>
     *
     * <p><strong>Example:</strong></p>
     * <pre>{@code
     * # application.yml
     * sql-guard:
     *   enabled: false  # Completely disable SQL Guard
     * }</pre>
     *
     * <p>Default: {@code false} (SQL Guard is disabled by default for safety)</p>
     */
    private boolean enabled = false;

    /**
     * Active violation strategy: LOG, WARN, or BLOCK.
     */
    @NotNull
    private String activeStrategy = "LOG";

    /**
     * Interceptor configuration.
     */
    @NestedConfigurationProperty
    private InterceptorsConfig interceptors = new InterceptorsConfig();

    /**
     * Deduplication configuration.
     */
    @NestedConfigurationProperty
    private DeduplicationConfig deduplication = new DeduplicationConfig();

    /**
     * Rule configuration.
     */
    @NestedConfigurationProperty
    private RulesConfig rules = new RulesConfig();

    /**
     * Parser configuration.
     */
    @NestedConfigurationProperty
    private ParserConfig parser = new ParserConfig();

    // Getters and setters

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

    public ParserConfig getParser() {
        return parser;
    }

    public void setParser(ParserConfig parser) {
        this.parser = parser;
    }

    @Override
    public String toString() {
        return "SqlGuardProperties{" +
                "enabled=" + enabled +
                ", activeStrategy='" + activeStrategy + '\'' +
                ", interceptors=" + interceptors +
                ", deduplication=" + deduplication +
                ", rules=" + rules +
                ", parser=" + parser +
                '}';
    }

    /**
     * Interceptor configuration.
     */
    public static class InterceptorsConfig {
        /**
         * MyBatis interceptor configuration.
         */
        @NestedConfigurationProperty
        private MyBatisConfig mybatis = new MyBatisConfig();

        /**
         * MyBatis-Plus interceptor configuration.
         */
        @NestedConfigurationProperty
        private MyBatisPlusConfig mybatisPlus = new MyBatisPlusConfig();

        /**
         * JDBC interceptor configuration.
         */
        @NestedConfigurationProperty
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

        @Override
        public String toString() {
            return "InterceptorsConfig{" +
                    "mybatis=" + mybatis +
                    ", mybatisPlus=" + mybatisPlus +
                    ", jdbc=" + jdbc +
                    '}';
        }
    }

    /**
     * MyBatis interceptor configuration.
     */
    public static class MyBatisConfig {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return "MyBatisConfig{enabled=" + enabled + '}';
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

        @Override
        public String toString() {
            return "MyBatisPlusConfig{enabled=" + enabled + '}';
        }
    }

    /**
     * JDBC interceptor configuration.
     */
    public static class JdbcConfig {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public String toString() {
            return "JdbcConfig{enabled=" + enabled + '}';
        }
    }

    /**
     * Deduplication configuration.
     */
    public static class DeduplicationConfig {
        /**
         * Enable SQL deduplication.
         */
        private boolean enabled = false;

        /**
         * Cache size for deduplication (number of SQL statements).
         */
        @Min(1)
        @Max(100000)
        private int cacheSize = 1000;

        /**
         * Cache TTL in milliseconds.
         */
        @Min(1)
        @Max(60000)
        private long ttlMs = 100;

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

        @Override
        public String toString() {
            return "DeduplicationConfig{" +
                    "enabled=" + enabled +
                    ", cacheSize=" + cacheSize +
                    ", ttlMs=" + ttlMs +
                    '}';
        }
    }

    /**
     * Rule configuration.
     */
    public static class RulesConfig {
        @NestedConfigurationProperty
        private NoWhereClauseProperties noWhereClause = new NoWhereClauseProperties();

        @NestedConfigurationProperty
        private DummyConditionProperties dummyCondition = new DummyConditionProperties();

        @NestedConfigurationProperty
        private BlacklistFieldProperties blacklistFields = new BlacklistFieldProperties();

        @NestedConfigurationProperty
        private WhitelistFieldProperties whitelistFields = new WhitelistFieldProperties();

        @NestedConfigurationProperty
        private LogicalPaginationProperties logicalPagination = new LogicalPaginationProperties();

        @NestedConfigurationProperty
        private NoConditionPaginationProperties noConditionPagination = new NoConditionPaginationProperties();

        @NestedConfigurationProperty
        private DeepPaginationProperties deepPagination = new DeepPaginationProperties();

        @NestedConfigurationProperty
        private LargePageSizeProperties largePageSize = new LargePageSizeProperties();

        @NestedConfigurationProperty
        private MissingOrderByProperties missingOrderBy = new MissingOrderByProperties();

        @NestedConfigurationProperty
        private NoPaginationProperties noPagination = new NoPaginationProperties();

        // ==================== SQL Injection Checkers ====================

        @NestedConfigurationProperty
        private MultiStatementProperties multiStatement = new MultiStatementProperties();

        @NestedConfigurationProperty
        private SetOperationProperties setOperation = new SetOperationProperties();

        @NestedConfigurationProperty
        private SqlCommentProperties sqlComment = new SqlCommentProperties();

        @NestedConfigurationProperty
        private IntoOutfileProperties intoOutfile = new IntoOutfileProperties();

        // ==================== Dangerous Operations Checkers ====================

        @NestedConfigurationProperty
        private DdlOperationProperties ddlOperation = new DdlOperationProperties();

        @NestedConfigurationProperty
        private DangerousFunctionProperties dangerousFunction = new DangerousFunctionProperties();

        @NestedConfigurationProperty
        private CallStatementProperties callStatement = new CallStatementProperties();

        // ==================== Access Control Checkers ====================

        @NestedConfigurationProperty
        private MetadataStatementProperties metadataStatement = new MetadataStatementProperties();

        @NestedConfigurationProperty
        private SetStatementProperties setStatement = new SetStatementProperties();

        @NestedConfigurationProperty
        private DeniedTableProperties deniedTable = new DeniedTableProperties();

        @NestedConfigurationProperty
        private ReadOnlyTableProperties readOnlyTable = new ReadOnlyTableProperties();

        public NoWhereClauseProperties getNoWhereClause() {
            return noWhereClause;
        }

        public void setNoWhereClause(NoWhereClauseProperties noWhereClause) {
            this.noWhereClause = noWhereClause;
        }

        public DummyConditionProperties getDummyCondition() {
            return dummyCondition;
        }

        public void setDummyCondition(DummyConditionProperties dummyCondition) {
            this.dummyCondition = dummyCondition;
        }

        public BlacklistFieldProperties getBlacklistFields() {
            return blacklistFields;
        }

        public void setBlacklistFields(BlacklistFieldProperties blacklistFields) {
            this.blacklistFields = blacklistFields;
        }

        public WhitelistFieldProperties getWhitelistFields() {
            return whitelistFields;
        }

        public void setWhitelistFields(WhitelistFieldProperties whitelistFields) {
            this.whitelistFields = whitelistFields;
        }

        public LogicalPaginationProperties getLogicalPagination() {
            return logicalPagination;
        }

        public void setLogicalPagination(LogicalPaginationProperties logicalPagination) {
            this.logicalPagination = logicalPagination;
        }

        public NoConditionPaginationProperties getNoConditionPagination() {
            return noConditionPagination;
        }

        public void setNoConditionPagination(NoConditionPaginationProperties noConditionPagination) {
            this.noConditionPagination = noConditionPagination;
        }

        public DeepPaginationProperties getDeepPagination() {
            return deepPagination;
        }

        public void setDeepPagination(DeepPaginationProperties deepPagination) {
            this.deepPagination = deepPagination;
        }

        public LargePageSizeProperties getLargePageSize() {
            return largePageSize;
        }

        public void setLargePageSize(LargePageSizeProperties largePageSize) {
            this.largePageSize = largePageSize;
        }

        public MissingOrderByProperties getMissingOrderBy() {
            return missingOrderBy;
        }

        public void setMissingOrderBy(MissingOrderByProperties missingOrderBy) {
            this.missingOrderBy = missingOrderBy;
        }

        public NoPaginationProperties getNoPagination() {
            return noPagination;
        }

        public void setNoPagination(NoPaginationProperties noPagination) {
            this.noPagination = noPagination;
        }

        // ==================== SQL Injection Checker Getters/Setters ====================

        public MultiStatementProperties getMultiStatement() {
            return multiStatement;
        }

        public void setMultiStatement(MultiStatementProperties multiStatement) {
            this.multiStatement = multiStatement;
        }

        public SetOperationProperties getSetOperation() {
            return setOperation;
        }

        public void setSetOperation(SetOperationProperties setOperation) {
            this.setOperation = setOperation;
        }

        public SqlCommentProperties getSqlComment() {
            return sqlComment;
        }

        public void setSqlComment(SqlCommentProperties sqlComment) {
            this.sqlComment = sqlComment;
        }

        public IntoOutfileProperties getIntoOutfile() {
            return intoOutfile;
        }

        public void setIntoOutfile(IntoOutfileProperties intoOutfile) {
            this.intoOutfile = intoOutfile;
        }

        // ==================== Dangerous Operations Checker Getters/Setters ====================

        public DdlOperationProperties getDdlOperation() {
            return ddlOperation;
        }

        public void setDdlOperation(DdlOperationProperties ddlOperation) {
            this.ddlOperation = ddlOperation;
        }

        public DangerousFunctionProperties getDangerousFunction() {
            return dangerousFunction;
        }

        public void setDangerousFunction(DangerousFunctionProperties dangerousFunction) {
            this.dangerousFunction = dangerousFunction;
        }

        public CallStatementProperties getCallStatement() {
            return callStatement;
        }

        public void setCallStatement(CallStatementProperties callStatement) {
            this.callStatement = callStatement;
        }

        // ==================== Access Control Checker Getters/Setters ====================

        public MetadataStatementProperties getMetadataStatement() {
            return metadataStatement;
        }

        public void setMetadataStatement(MetadataStatementProperties metadataStatement) {
            this.metadataStatement = metadataStatement;
        }

        public SetStatementProperties getSetStatement() {
            return setStatement;
        }

        public void setSetStatement(SetStatementProperties setStatement) {
            this.setStatement = setStatement;
        }

        public DeniedTableProperties getDeniedTable() {
            return deniedTable;
        }

        public void setDeniedTable(DeniedTableProperties deniedTable) {
            this.deniedTable = deniedTable;
        }

        public ReadOnlyTableProperties getReadOnlyTable() {
            return readOnlyTable;
        }

        public void setReadOnlyTable(ReadOnlyTableProperties readOnlyTable) {
            this.readOnlyTable = readOnlyTable;
        }

        @Override
        public String toString() {
            return "RulesConfig{" +
                    "noWhereClause=" + noWhereClause +
                    ", dummyCondition=" + dummyCondition +
                    ", blacklistFields=" + blacklistFields +
                    ", whitelistFields=" + whitelistFields +
                    ", logicalPagination=" + logicalPagination +
                    ", noConditionPagination=" + noConditionPagination +
                    ", deepPagination=" + deepPagination +
                    ", largePageSize=" + largePageSize +
                    ", missingOrderBy=" + missingOrderBy +
                    ", noPagination=" + noPagination +
                    ", multiStatement=" + multiStatement +
                    ", setOperation=" + setOperation +
                    ", sqlComment=" + sqlComment +
                    ", intoOutfile=" + intoOutfile +
                    ", ddlOperation=" + ddlOperation +
                    ", dangerousFunction=" + dangerousFunction +
                    ", callStatement=" + callStatement +
                    ", metadataStatement=" + metadataStatement +
                    ", setStatement=" + setStatement +
                    ", deniedTable=" + deniedTable +
                    ", readOnlyTable=" + readOnlyTable +
                    '}';
        }
    }

    /**
     * No WHERE clause rule properties.
     */
    public static class NoWhereClauseProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.CRITICAL;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "NoWhereClauseProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    /**
     * Dummy condition rule properties.
     */
    public static class DummyConditionProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.HIGH;
        private List<String> patterns = Arrays.asList("1=1", "true", "'a'='a'");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        public List<String> getPatterns() {
            return patterns;
        }

        public void setPatterns(List<String> patterns) {
            this.patterns = patterns;
        }

        @Override
        public String toString() {
            return "DummyConditionProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + ", patterns=" + patterns + '}';
        }
    }

    /**
     * Blacklist field rule properties.
     */
    public static class BlacklistFieldProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.HIGH;
        private List<String> blacklistFields = Arrays.asList("deleted", "status", "enabled");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        public List<String> getBlacklistFields() {
            return blacklistFields;
        }

        public void setBlacklistFields(List<String> blacklistFields) {
            this.blacklistFields = blacklistFields;
        }

        @Override
        public String toString() {
            return "BlacklistFieldProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + ", blacklistFields=" + blacklistFields + '}';
        }
    }

    /**
     * Whitelist field rule properties.
     */
    public static class WhitelistFieldProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.HIGH;
        private Map<String, List<String>> whitelistFields = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        public Map<String, List<String>> getWhitelistFields() {
            return whitelistFields;
        }

        public void setWhitelistFields(Map<String, List<String>> whitelistFields) {
            this.whitelistFields = whitelistFields;
        }

        @Override
        public String toString() {
            return "WhitelistFieldProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + ", whitelistFields=" + whitelistFields + '}';
        }
    }

    /**
     * Logical pagination rule properties.
     */
    public static class LogicalPaginationProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.CRITICAL;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "LogicalPaginationProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    /**
     * No condition pagination rule properties.
     */
    public static class NoConditionPaginationProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.CRITICAL;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "NoConditionPaginationProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    /**
     * Deep pagination rule properties.
     */
    public static class DeepPaginationProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.MEDIUM;
        @Min(1)
        private int maxOffset = 10000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        public int getMaxOffset() {
            return maxOffset;
        }

        public void setMaxOffset(int maxOffset) {
            this.maxOffset = maxOffset;
        }

        @Override
        public String toString() {
            return "DeepPaginationProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + ", maxOffset=" + maxOffset + '}';
        }
    }

    /**
     * Large page size rule properties.
     */
    public static class LargePageSizeProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.MEDIUM;
        @Min(1)
        private int maxPageSize = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        public int getMaxPageSize() {
            return maxPageSize;
        }

        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
        }

        @Override
        public String toString() {
            return "LargePageSizeProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + ", maxPageSize=" + maxPageSize + '}';
        }
    }

    /**
     * Missing ORDER BY rule properties.
     */
    public static class MissingOrderByProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.LOW;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "MissingOrderByProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    /**
     * No pagination rule properties.
     */
    public static class NoPaginationProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.MEDIUM;
        @Min(1)
        private long estimatedRowsThreshold = 10000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        public long getEstimatedRowsThreshold() {
            return estimatedRowsThreshold;
        }

        public void setEstimatedRowsThreshold(long estimatedRowsThreshold) {
            this.estimatedRowsThreshold = estimatedRowsThreshold;
        }

        @Override
        public String toString() {
            return "NoPaginationProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + ", estimatedRowsThreshold=" + estimatedRowsThreshold + '}';
        }
    }

    /**
     * Parser configuration.
     */
    public static class ParserConfig {
        /**
         * Enable lenient parsing mode for SQL with syntax extensions.
         */
        private boolean lenientMode = false;

        public boolean isLenientMode() {
            return lenientMode;
        }

        public void setLenientMode(boolean lenientMode) {
            this.lenientMode = lenientMode;
        }

        @Override
        public String toString() {
            return "ParserConfig{lenientMode=" + lenientMode + '}';
        }
    }

    // ==================== SQL Injection Checker Properties ====================

    /**
     * Multi-statement SQL injection detection properties.
     */
    public static class MultiStatementProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.CRITICAL;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "MultiStatementProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    /**
     * Set operation (UNION/MINUS/EXCEPT/INTERSECT) injection detection properties.
     */
    public static class SetOperationProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.HIGH;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "SetOperationProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    /**
     * SQL comment-based injection detection properties.
     */
    public static class SqlCommentProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.HIGH;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "SqlCommentProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    /**
     * MySQL INTO OUTFILE file write detection properties.
     */
    public static class IntoOutfileProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.CRITICAL;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "IntoOutfileProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    // ==================== Dangerous Operations Checker Properties ====================

    /**
     * DDL operation (CREATE/ALTER/DROP/TRUNCATE) detection properties.
     */
    public static class DdlOperationProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.CRITICAL;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "DdlOperationProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    /**
     * Dangerous function (load_file, sys_exec, sleep, etc.) detection properties.
     */
    public static class DangerousFunctionProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.CRITICAL;
        private List<String> additionalFunctions = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        public List<String> getAdditionalFunctions() {
            return additionalFunctions;
        }

        public void setAdditionalFunctions(List<String> additionalFunctions) {
            this.additionalFunctions = additionalFunctions;
        }

        @Override
        public String toString() {
            return "DangerousFunctionProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + 
                   ", additionalFunctions=" + additionalFunctions + '}';
        }
    }

    /**
     * Stored procedure call (CALL/EXECUTE/EXEC) detection properties.
     */
    public static class CallStatementProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.HIGH;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "CallStatementProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    // ==================== Access Control Checker Properties ====================

    /**
     * Metadata disclosure (SHOW/DESCRIBE/USE) detection properties.
     */
    public static class MetadataStatementProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.MEDIUM;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "MetadataStatementProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    /**
     * Session variable modification (SET statements) detection properties.
     */
    public static class SetStatementProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.MEDIUM;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        @Override
        public String toString() {
            return "SetStatementProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + '}';
        }
    }

    /**
     * Table-level access control blacklist properties.
     */
    public static class DeniedTableProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.CRITICAL;
        private List<String> deniedTables = Arrays.asList("sys_*", "admin_*", "audit_log", "sensitive_data");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        public List<String> getDeniedTables() {
            return deniedTables;
        }

        public void setDeniedTables(List<String> deniedTables) {
            this.deniedTables = deniedTables;
        }

        @Override
        public String toString() {
            return "DeniedTableProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + 
                   ", deniedTables=" + deniedTables + '}';
        }
    }

    /**
     * Read-only table protection properties.
     */
    public static class ReadOnlyTableProperties {
        private boolean enabled = false;
        private RiskLevel riskLevel = RiskLevel.CRITICAL;
        private List<String> readOnlyTables = Arrays.asList("audit_log", "history_*", "compliance_records");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public void setRiskLevel(RiskLevel riskLevel) {
            this.riskLevel = riskLevel;
        }

        public List<String> getReadOnlyTables() {
            return readOnlyTables;
        }

        public void setReadOnlyTables(List<String> readOnlyTables) {
            this.readOnlyTables = readOnlyTables;
        }

        @Override
        public String toString() {
            return "ReadOnlyTableProperties{enabled=" + enabled + ", riskLevel=" + riskLevel + 
                   ", readOnlyTables=" + readOnlyTables + '}';
        }
    }
}
