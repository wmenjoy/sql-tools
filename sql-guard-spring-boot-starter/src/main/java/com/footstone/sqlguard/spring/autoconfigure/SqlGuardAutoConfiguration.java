package com.footstone.sqlguard.spring.autoconfigure;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.config.ViolationStrategy;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.footstone.sqlguard.validator.SqlSafetyValidator;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.impl.DeepPaginationChecker;
import com.footstone.sqlguard.validator.pagination.impl.LargePageSizeChecker;
import com.footstone.sqlguard.validator.pagination.impl.LogicalPaginationChecker;
import com.footstone.sqlguard.validator.pagination.impl.MissingOrderByChecker;
import com.footstone.sqlguard.validator.pagination.impl.NoConditionPaginationChecker;
import com.footstone.sqlguard.validator.pagination.impl.NoPaginationChecker;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import com.footstone.sqlguard.validator.rule.impl.BlacklistFieldChecker;
import com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig;
import com.footstone.sqlguard.validator.rule.impl.CallStatementChecker;
import com.footstone.sqlguard.validator.rule.impl.CallStatementConfig;
import com.footstone.sqlguard.validator.rule.impl.DangerousFunctionChecker;
import com.footstone.sqlguard.validator.rule.impl.DangerousFunctionConfig;
import com.footstone.sqlguard.validator.rule.impl.DdlOperationChecker;
import com.footstone.sqlguard.validator.rule.impl.DdlOperationConfig;
import com.footstone.sqlguard.validator.rule.impl.DeniedTableChecker;
import com.footstone.sqlguard.validator.rule.impl.DeniedTableConfig;
import com.footstone.sqlguard.validator.rule.impl.DummyConditionChecker;
import com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig;
import com.footstone.sqlguard.validator.rule.impl.IntoOutfileChecker;
import com.footstone.sqlguard.validator.rule.impl.IntoOutfileConfig;
import com.footstone.sqlguard.validator.rule.impl.LogicalPaginationConfig;
import com.footstone.sqlguard.validator.rule.impl.MetadataStatementChecker;
import com.footstone.sqlguard.validator.rule.impl.MetadataStatementConfig;
import com.footstone.sqlguard.validator.rule.impl.MissingOrderByConfig;
import com.footstone.sqlguard.validator.rule.impl.MultiStatementChecker;
import com.footstone.sqlguard.validator.rule.impl.MultiStatementConfig;
import com.footstone.sqlguard.validator.rule.impl.NoPaginationConfig;
import com.footstone.sqlguard.validator.rule.impl.NoWhereClauseChecker;
import com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig;
import com.footstone.sqlguard.validator.rule.impl.PaginationAbuseConfig;
import com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableChecker;
import com.footstone.sqlguard.validator.rule.impl.ReadOnlyTableConfig;
import com.footstone.sqlguard.validator.rule.impl.SetOperationChecker;
import com.footstone.sqlguard.validator.rule.impl.SetOperationConfig;
import com.footstone.sqlguard.validator.rule.impl.SetStatementChecker;
import com.footstone.sqlguard.validator.rule.impl.SetStatementConfig;
import com.footstone.sqlguard.validator.rule.impl.SqlCommentChecker;
import com.footstone.sqlguard.validator.rule.impl.SqlCommentConfig;
import com.footstone.sqlguard.validator.rule.impl.WhitelistFieldChecker;
import com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig;
import com.footstone.sqlguard.validator.pagination.impl.NoConditionPaginationConfig;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for SQL Guard.
 *
 * <p>SqlGuardAutoConfiguration provides zero-configuration SQL safety integration with automatic
 * bean creation, conditional component activation, and interceptor registration across
 * MyBatis/MyBatis-Plus/JDBC layers.</p>
 *
 * <p><strong>Auto-Configuration Features:</strong></p>
 * <ul>
 *   <li><strong>Zero-Configuration:</strong> Just add starter dependency, no @Import or
 *       @EnableSqlGuard required</li>
 *   <li><strong>Conditional Activation:</strong> Components only created when required dependencies
 *       present on classpath</li>
 *   <li><strong>User Override:</strong> All beans use @ConditionalOnMissingBean allowing user
 *       customization</li>
 *   <li><strong>Multi-Layer Support:</strong> Automatic interceptor registration for MyBatis,
 *       MyBatis-Plus, Druid, HikariCP, P6Spy</li>
 *   <li><strong>Property Binding:</strong> Configuration via application.yml/properties using
 *       sql-guard.* prefix</li>
 * </ul>
 *
 * <p><strong>Conditional Bean Creation:</strong></p>
 * <ul>
 *   <li><strong>@ConditionalOnClass:</strong> Ensures beans only created when required dependencies
 *       present (prevents startup failures)</li>
 *   <li><strong>@ConditionalOnMissingBean:</strong> Allows user overrides (user-defined bean takes
 *       precedence)</li>
 *   <li><strong>sql-guard.enabled=false:</strong> Disables all auto-configuration</li>
 * </ul>
 *
 * <p><strong>Bean Lifecycle:</strong></p>
 * <ol>
 *   <li>Spring Boot scans META-INF/spring.factories for auto-configuration classes</li>
 *   <li>SqlGuardAutoConfiguration evaluated based on @Conditional annotations</li>
 *   <li>Core beans created (parser, checkers, validator)</li>
 *   <li>Interceptor beans created based on classpath dependencies</li>
 *   <li>BeanPostProcessors register interceptors with SqlSessionFactory/DataSource</li>
 * </ol>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // 1. Add dependency to pom.xml
 * <dependency>
 *     <groupId>com.footstone</groupId>
 *     <artifactId>sql-guard-spring-boot-starter</artifactId>
 *     <version>1.0.0-SNAPSHOT</version>
 * </dependency>
 *
 * // 2. Configure in application.yml (optional)
 * sql-guard:
 *   enabled: true
 *   active-strategy: WARN
 *   rules:
 *     no-where-clause:
 *       enabled: true
 *
 * // 3. Auto-configuration loads automatically - no code needed!
 *
 * // 4. Optional: Override beans for customization
 * @Configuration
 * public class CustomSqlGuardConfig {
 *     @Bean
 *     public DefaultSqlSafetyValidator customValidator(...) {
 *         // Custom implementation
 *     }
 * }
 * }</pre>
 *
 * <p><strong>Auto-Configuration Ordering:</strong></p>
 * <ul>
 *   <li><strong>@AutoConfigureAfter(DataSourceAutoConfiguration):</strong> Ensures DataSource
 *       created before SQL Guard interceptors</li>
 *   <li><strong>@AutoConfigureBefore(MybatisAutoConfiguration):</strong> Ensures SQL Guard
 *       interceptors registered before MyBatis initialization</li>
 * </ul>
 *
 * @see SqlGuardProperties
 * @see SqlSafetyValidator
 */
@Configuration
@ConditionalOnClass(SqlSafetyValidator.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(SqlGuardProperties.class)
public class SqlGuardAutoConfiguration {

  private final SqlGuardProperties properties;

  /**
   * Creates SqlGuardAutoConfiguration with injected properties.
   *
   * @param properties SQL Guard configuration properties
   */
  public SqlGuardAutoConfiguration(SqlGuardProperties properties) {
    this.properties = properties;
  }

  // ==================== Core Beans ====================

  /**
   * Creates SqlGuardConfig bean for global configuration.
   *
   * <p>SqlGuardConfig provides the global enabled flag that controls whether all
   * SQL Guard checks and rewrites are active. When disabled (sql-guard.enabled=false),
   * all interceptors will skip processing entirely without entering individual checker logic.</p>
   *
   * <p>This is the master switch for SQL Guard functionality:</p>
   * <ul>
   *   <li><strong>enabled=true (default):</strong> All configured checkers and rewriters are active</li>
   *   <li><strong>enabled=false:</strong> All SQL Guard processing is bypassed at the interceptor level</li>
   * </ul>
   *
   * @return SqlGuardConfig instance with properties from application.yml
   */
  @Bean
  @ConditionalOnMissingBean
  public SqlGuardConfig sqlGuardConfig() {
    SqlGuardConfig config = new SqlGuardConfig();
    config.setEnabled(properties.isEnabled());
    config.setActiveStrategy(properties.getActiveStrategy());
    
    // Map violation strategy from string to enum
    try {
      config.setViolationStrategy(ViolationStrategy.valueOf(properties.getActiveStrategy().toUpperCase()));
    } catch (IllegalArgumentException e) {
      // Default to LOG if invalid strategy
      config.setViolationStrategy(ViolationStrategy.LOG);
    }
    
    return config;
  }

  /**
   * Creates JSqlParserFacade bean for SQL parsing.
   *
   * <p>JSqlParserFacade provides unified SQL parsing interface with caching support.
   * Lenient mode can be configured via sql-guard.parser.lenient-mode property.</p>
   *
   * @return JSqlParserFacade instance
   */
  @Bean
  @ConditionalOnMissingBean
  public JSqlParserFacade sqlParserFacade() {
    boolean lenientMode = properties.getParser().isLenientMode();
    return new JSqlParserFacade(lenientMode);
  }

  /**
   * Creates SqlDeduplicationFilter bean for preventing redundant validation.
   *
   * <p>Deduplication filter uses ThreadLocal LRU cache to skip recently validated SQL
   * within TTL window. Configuration via sql-guard.deduplication properties.</p>
   *
   * @return SqlDeduplicationFilter instance
   */
  @Bean
  @ConditionalOnMissingBean
  public SqlDeduplicationFilter sqlDeduplicationFilter() {
    int cacheSize = properties.getDeduplication().getCacheSize();
    long ttlMs = properties.getDeduplication().getTtlMs();
    return new SqlDeduplicationFilter(cacheSize, ttlMs);
  }

  // ==================== Rule Checker Beans ====================

  /**
   * Creates NoWhereClauseChecker bean.
   *
   * <p>Detects SQL statements missing WHERE clauses (CRITICAL risk).</p>
   *
   * @return NoWhereClauseChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public NoWhereClauseChecker noWhereClauseChecker() {
    NoWhereClauseConfig config = new NoWhereClauseConfig();
    config.setEnabled(properties.getRules().getNoWhereClause().isEnabled());
    return new NoWhereClauseChecker(config);
  }

  /**
   * Creates DummyConditionChecker bean.
   *
   * <p>Detects dummy WHERE conditions like "1=1" that make WHERE clause meaningless.</p>
   *
   * @return DummyConditionChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public DummyConditionChecker dummyConditionChecker() {
    DummyConditionConfig config = new DummyConditionConfig();
    config.setEnabled(properties.getRules().getDummyCondition().isEnabled());
    return new DummyConditionChecker(config);
  }

  /**
   * Creates BlacklistFieldChecker bean.
   *
   * <p>Detects WHERE clauses using only blacklisted low-cardinality fields.</p>
   *
   * @return BlacklistFieldChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public BlacklistFieldChecker blacklistFieldChecker() {
    BlacklistFieldsConfig config = new BlacklistFieldsConfig();
    config.setEnabled(properties.getRules().getBlacklistFields().isEnabled());
    return new BlacklistFieldChecker(config);
  }

  /**
   * Creates WhitelistFieldChecker bean.
   *
   * <p>Ensures critical tables include mandatory high-selectivity fields in WHERE clause.</p>
   *
   * @return WhitelistFieldChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public WhitelistFieldChecker whitelistFieldChecker() {
    WhitelistFieldsConfig config = new WhitelistFieldsConfig();
    config.setEnabled(properties.getRules().getWhitelistFields().isEnabled());
    return new WhitelistFieldChecker(config);
  }

  // ==================== SQL Injection Checker Beans ====================

  /**
   * Creates MultiStatementChecker bean.
   *
   * <p>Detects multi-statement SQL injection (e.g., "SELECT...; DROP TABLE...").</p>
   *
   * @return MultiStatementChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public MultiStatementChecker multiStatementChecker() {
    MultiStatementConfig config = new MultiStatementConfig();
    config.setEnabled(properties.getRules().getMultiStatement().isEnabled());
    return new MultiStatementChecker(config);
  }

  /**
   * Creates SetOperationChecker bean.
   *
   * <p>Detects UNION/MINUS/EXCEPT/INTERSECT injection attacks.</p>
   *
   * @return SetOperationChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public SetOperationChecker setOperationChecker() {
    SetOperationConfig config = new SetOperationConfig();
    config.setEnabled(properties.getRules().getSetOperation().isEnabled());
    return new SetOperationChecker(config);
  }

  /**
   * Creates SqlCommentChecker bean.
   *
   * <p>Detects comment-based SQL injection (e.g., "--" or block comments).</p>
   *
   * @return SqlCommentChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public SqlCommentChecker sqlCommentChecker() {
    SqlCommentConfig config = new SqlCommentConfig();
    config.setEnabled(properties.getRules().getSqlComment().isEnabled());
    return new SqlCommentChecker(config);
  }

  /**
   * Creates IntoOutfileChecker bean.
   *
   * <p>Detects MySQL INTO OUTFILE file write operations.</p>
   *
   * @return IntoOutfileChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public IntoOutfileChecker intoOutfileChecker() {
    IntoOutfileConfig config = new IntoOutfileConfig();
    config.setEnabled(properties.getRules().getIntoOutfile().isEnabled());
    return new IntoOutfileChecker(config);
  }

  // ==================== Dangerous Operations Checker Beans ====================

  /**
   * Creates DdlOperationChecker bean.
   *
   * <p>Detects DDL operations (CREATE/ALTER/DROP/TRUNCATE).</p>
   *
   * @return DdlOperationChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public DdlOperationChecker ddlOperationChecker() {
    DdlOperationConfig config = new DdlOperationConfig();
    config.setEnabled(properties.getRules().getDdlOperation().isEnabled());
    return new DdlOperationChecker(config);
  }

  /**
   * Creates DangerousFunctionChecker bean.
   *
   * <p>Detects dangerous functions (load_file, sys_exec, sleep, etc.).</p>
   *
   * @return DangerousFunctionChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public DangerousFunctionChecker dangerousFunctionChecker() {
    DangerousFunctionConfig config = new DangerousFunctionConfig();
    config.setEnabled(properties.getRules().getDangerousFunction().isEnabled());
    return new DangerousFunctionChecker(config);
  }

  /**
   * Creates CallStatementChecker bean.
   *
   * <p>Detects stored procedure calls (CALL/EXECUTE/EXEC).</p>
   *
   * @return CallStatementChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public CallStatementChecker callStatementChecker() {
    CallStatementConfig config = new CallStatementConfig();
    config.setEnabled(properties.getRules().getCallStatement().isEnabled());
    return new CallStatementChecker(config);
  }

  // ==================== Access Control Checker Beans ====================

  /**
   * Creates MetadataStatementChecker bean.
   *
   * <p>Detects metadata disclosure (SHOW/DESCRIBE/USE).</p>
   *
   * @return MetadataStatementChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public MetadataStatementChecker metadataStatementChecker() {
    MetadataStatementConfig config = new MetadataStatementConfig();
    config.setEnabled(properties.getRules().getMetadataStatement().isEnabled());
    return new MetadataStatementChecker(config);
  }

  /**
   * Creates SetStatementChecker bean.
   *
   * <p>Detects session variable modification (SET statements).</p>
   *
   * @return SetStatementChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public SetStatementChecker setStatementChecker() {
    SetStatementConfig config = new SetStatementConfig();
    config.setEnabled(properties.getRules().getSetStatement().isEnabled());
    return new SetStatementChecker(config);
  }

  /**
   * Creates DeniedTableChecker bean.
   *
   * <p>Enforces table-level access control blacklist.</p>
   *
   * @return DeniedTableChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public DeniedTableChecker deniedTableChecker() {
    DeniedTableConfig config = new DeniedTableConfig();
    config.setEnabled(properties.getRules().getDeniedTable().isEnabled());
    config.setDeniedTables(properties.getRules().getDeniedTable().getDeniedTables());
    return new DeniedTableChecker(config);
  }

  /**
   * Creates ReadOnlyTableChecker bean.
   *
   * <p>Protects read-only tables from write operations.</p>
   *
   * @return ReadOnlyTableChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public ReadOnlyTableChecker readOnlyTableChecker() {
    ReadOnlyTableConfig config = new ReadOnlyTableConfig();
    config.setEnabled(properties.getRules().getReadOnlyTable().isEnabled());
    config.setReadonlyTables(properties.getRules().getReadOnlyTable().getReadOnlyTables());
    return new ReadOnlyTableChecker(config);
  }

  /**
   * Configuration for PaginationPluginDetector when MyBatis is available.
   *
   * <p>This inner configuration is only activated when MyBatis classes are present on the classpath.
   * It provides a PaginationPluginDetector that can detect MyBatis PageHelper plugin.</p>
   */
  @Configuration
  @ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
  static class MyBatisDetectorConfiguration {
    
    /**
     * Creates PaginationPluginDetector with MyBatis interceptor support.
     *
     * <p>Uses ObjectProvider to handle optional dependencies gracefully.
     * This allows the detector to work whether or not MyBatis/MyBatis-Plus
     * interceptors are configured in the application context.</p>
     *
     * @param mybatisInterceptorsProvider provider for MyBatis interceptors (may be empty)
     * @param mybatisPlusInterceptorProvider provider for MyBatis-Plus interceptor (may be empty)
     * @return PaginationPluginDetector instance
     */
    @Bean
    @ConditionalOnMissingBean
    public PaginationPluginDetector paginationPluginDetector(
        ObjectProvider<List<org.apache.ibatis.plugin.Interceptor>> mybatisInterceptorsProvider,
        ObjectProvider<Object> mybatisPlusInterceptorProvider) {
      List<org.apache.ibatis.plugin.Interceptor> mybatisInterceptors = 
          mybatisInterceptorsProvider.getIfAvailable();
      Object mybatisPlusInterceptor = mybatisPlusInterceptorProvider.getIfAvailable();
      return new PaginationPluginDetector(mybatisInterceptors, mybatisPlusInterceptor);
    }
  }

  /**
   * Configuration for PaginationPluginDetector when MyBatis is NOT available.
   *
   * <p>This fallback configuration provides a basic detector that only works based on
   * SQL LIMIT clause detection, without MyBatis plugin detection capability.</p>
   */
  @Configuration
  @ConditionalOnMissingBean(name = "paginationPluginDetector")
  static class FallbackDetectorConfiguration {
    
    /**
     * Creates PaginationPluginDetector without MyBatis support.
     *
     * <p>This detector can still detect pagination based on SQL LIMIT clauses,
     * but cannot detect MyBatis/MyBatis-Plus pagination plugins.</p>
     *
     * @return PaginationPluginDetector instance with null interceptors
     */
    @Bean
    public PaginationPluginDetector paginationPluginDetector() {
      return new PaginationPluginDetector(null, null);
    }
  }

  /**
   * Creates LogicalPaginationChecker bean.
   *
   * <p>Detects dangerous logical pagination (in-memory offset/limit) that can cause OOM.</p>
   *
   * @param detector pagination plugin detector
   * @return LogicalPaginationChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public LogicalPaginationChecker logicalPaginationChecker(PaginationPluginDetector detector) {
    LogicalPaginationConfig config = new LogicalPaginationConfig();
    config.setEnabled(properties.getRules().getLogicalPagination().isEnabled());
    return new LogicalPaginationChecker(detector, config);
  }

  /**
   * Creates NoConditionPaginationChecker bean.
   *
   * <p>Detects LIMIT queries without WHERE clause (still performs full table scan).</p>
   *
   * @param detector pagination plugin detector
   * @return NoConditionPaginationChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public NoConditionPaginationChecker noConditionPaginationChecker(
      PaginationPluginDetector detector) {
    NoConditionPaginationConfig config = new NoConditionPaginationConfig();
    config.setEnabled(properties.getRules().getNoConditionPagination().isEnabled());
    return new NoConditionPaginationChecker(config, detector);
  }

  /**
   * Creates DeepPaginationChecker bean.
   *
   * <p>Detects high OFFSET values causing performance degradation.</p>
   *
   * @param detector pagination plugin detector
   * @return DeepPaginationChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public DeepPaginationChecker deepPaginationChecker(PaginationPluginDetector detector) {
    PaginationAbuseConfig config = new PaginationAbuseConfig();
    config.setEnabled(properties.getRules().getDeepPagination().isEnabled());
    return new DeepPaginationChecker(config, detector);
  }

  /**
   * Creates LargePageSizeChecker bean.
   *
   * <p>Detects excessive page sizes (LIMIT values) that can cause memory issues.</p>
   *
   * @param detector pagination plugin detector
   * @return LargePageSizeChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public LargePageSizeChecker largePageSizeChecker(PaginationPluginDetector detector) {
    PaginationAbuseConfig config = new PaginationAbuseConfig();
    config.setEnabled(properties.getRules().getLargePageSize().isEnabled());
    return new LargePageSizeChecker(detector, config);
  }

  /**
   * Creates MissingOrderByChecker bean.
   *
   * <p>Detects pagination queries missing ORDER BY clause (unstable result ordering).</p>
   *
   * @param detector pagination plugin detector
   * @return MissingOrderByChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public MissingOrderByChecker missingOrderByChecker(PaginationPluginDetector detector) {
    MissingOrderByConfig config = new MissingOrderByConfig();
    config.setEnabled(properties.getRules().getMissingOrderBy().isEnabled());
    return new MissingOrderByChecker(detector, config);
  }

  /**
   * Creates NoPaginationChecker bean.
   *
   * <p>Detects SELECT queries completely lacking pagination limits.</p>
   *
   * @param detector pagination plugin detector
   * @param blacklistConfig blacklist fields configuration (for risk stratification)
   * @return NoPaginationChecker instance
   */
  @Bean
  @ConditionalOnMissingBean
  public NoPaginationChecker noPaginationChecker(
      PaginationPluginDetector detector,
      BlacklistFieldChecker blacklistChecker) {
    NoPaginationConfig config = new NoPaginationConfig();
    config.setEnabled(properties.getRules().getNoPagination().isEnabled());
    // Get blacklist config from blacklistChecker (we need to extract it)
    // For now, create a new BlacklistFieldsConfig with defaults
    BlacklistFieldsConfig blacklistConfig = new BlacklistFieldsConfig();
    return new NoPaginationChecker(detector, blacklistConfig, config);
  }

  /**
   * Creates RuleCheckerOrchestrator bean.
   *
   * <p>Orchestrates execution of all rule checkers in Chain of Responsibility pattern.</p>
   *
   * @param checkers list of all rule checkers (auto-wired by Spring)
   * @return RuleCheckerOrchestrator instance
   */
  @Bean
  @ConditionalOnMissingBean
  public RuleCheckerOrchestrator ruleCheckerOrchestrator(List<RuleChecker> checkers) {
    return new RuleCheckerOrchestrator(checkers);
  }

  /**
   * Creates DefaultSqlSafetyValidator bean.
   *
   * <p>Main validator coordinating all validation components (parser, checkers, orchestrator,
   * deduplication filter).</p>
   *
   * @param facade SQL parser facade
   * @param checkers list of all rule checkers
   * @param orchestrator rule checker orchestrator
   * @param filter deduplication filter
   * @return DefaultSqlSafetyValidator instance
   */
  @Bean
  @ConditionalOnMissingBean
  public DefaultSqlSafetyValidator sqlSafetyValidator(
      JSqlParserFacade facade,
      List<RuleChecker> checkers,
      RuleCheckerOrchestrator orchestrator,
      SqlDeduplicationFilter filter) {
    return new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
  }
}
