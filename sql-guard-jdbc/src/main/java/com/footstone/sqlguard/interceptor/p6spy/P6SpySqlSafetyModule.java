package com.footstone.sqlguard.interceptor.p6spy;

import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.footstone.sqlguard.validator.SqlSafetyValidator;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import com.footstone.sqlguard.validator.rule.impl.BlacklistFieldChecker;
import com.footstone.sqlguard.validator.rule.impl.DummyConditionChecker;
import com.footstone.sqlguard.validator.rule.impl.NoWhereClauseChecker;
import com.footstone.sqlguard.validator.rule.impl.WhitelistFieldChecker;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P6Spy module for SQL safety validation via SPI registration.
 *
 * <p>P6SpySqlSafetyModule serves as the entry point for P6Spy's module system, implementing
 * JdbcEventListener and delegating to P6SpySqlSafetyListener for actual SQL validation. This
 * module is automatically discovered and loaded by P6Spy through Java's ServiceLoader mechanism
 * when registered in META-INF/services.</p>
 *
 * <p><strong>Module Initialization:</strong></p>
 * <ul>
 *   <li>Static initialization block loads validator and strategy configuration</li>
 *   <li>ServiceLoader attempts to discover SqlSafetyValidator implementation</li>
 *   <li>Falls back to creating DefaultSqlSafetyValidator if no service found</li>
 *   <li>ViolationStrategy loaded from system property or defaults to LOG</li>
 * </ul>
 *
 * <p><strong>Configuration:</strong></p>
 * <pre>
 * # spy.properties
 * modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlSafetyModule
 * appender=com.p6spy.engine.spy.appender.Slf4JLogger
 * driverlist=com.mysql.cj.jdbc.Driver,org.postgresql.Driver,org.h2.Driver
 *
 * # System property for violation strategy (optional)
 * -Dsqlguard.p6spy.strategy=BLOCK
 * </pre>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. The static listener instance is
 * initialized once and shared across all threads.</p>
 *
 * @see P6SpySqlSafetyListener
 * @see JdbcEventListener
 */
public class P6SpySqlSafetyModule extends JdbcEventListener {

  private static final Logger logger = LoggerFactory.getLogger(P6SpySqlSafetyModule.class);

  /**
   * System property key for violation strategy configuration.
   */
  private static final String STRATEGY_PROPERTY = "sqlguard.p6spy.strategy";

  /**
   * Shared listener instance for SQL validation.
   */
  private static P6SpySqlSafetyListener listener;

  /**
   * Static initialization block to load validator and strategy.
   */
  static {
    try {
      logger.info("Initializing P6Spy SQL Safety Module...");

      // Load validator
      DefaultSqlSafetyValidator validator = loadValidator();

      // Load strategy
      ViolationStrategy strategy = loadStrategy();

      // Create listener
      listener = new P6SpySqlSafetyListener(validator, strategy);

      logger.info(
          "P6Spy SQL Safety Module initialized successfully with strategy: {}", strategy);
    } catch (Exception e) {
      logger.error("Failed to initialize P6Spy SQL Safety Module", e);
      throw new IllegalStateException("P6Spy SQL Safety Module initialization failed", e);
    }
  }

  /**
   * Delegates to P6SpySqlSafetyListener for SQL validation.
   *
   * @param statementInfo P6Spy statement information
   * @throws SQLException if validation fails and strategy is BLOCK
   */
  public void onBeforeAnyExecute(StatementInformation statementInfo) throws SQLException {
    if (listener != null) {
      listener.onBeforeAnyExecute(statementInfo);
    }
  }

  /**
   * Loads SqlSafetyValidator using ServiceLoader or creates default instance.
   *
   * <p><strong>Loading Strategy:</strong></p>
   * <ol>
   *   <li>Attempt ServiceLoader discovery of SqlSafetyValidator</li>
   *   <li>If found, cast to DefaultSqlSafetyValidator and return</li>
   *   <li>If not found, create DefaultSqlSafetyValidator with default configuration</li>
   * </ol>
   *
   * @return DefaultSqlSafetyValidator instance
   * @throws IllegalStateException if validator cannot be created
   */
  static DefaultSqlSafetyValidator loadValidator() {
    try {
      // Try ServiceLoader first
      ServiceLoader<SqlSafetyValidator> loader = ServiceLoader.load(SqlSafetyValidator.class);
      
      // Java 8 compatible iteration (findFirst() is Java 9+)
      SqlSafetyValidator validator = null;
      for (SqlSafetyValidator v : loader) {
        validator = v;
        break; // Get first available
      }

      if (validator != null) {
        logger.info("Loaded SqlSafetyValidator from ServiceLoader: {}", validator.getClass());
        return (DefaultSqlSafetyValidator) validator;
      }

      // Fallback: create default validator
      logger.info("No SqlSafetyValidator found via ServiceLoader, creating default instance");
      return createDefaultValidator();

    } catch (Exception e) {
      logger.error("Failed to load SqlSafetyValidator", e);
      throw new IllegalStateException("Failed to load SqlSafetyValidator", e);
    }
  }

  /**
   * Creates DefaultSqlSafetyValidator with default configuration.
   *
   * @return DefaultSqlSafetyValidator instance
   */
  private static DefaultSqlSafetyValidator createDefaultValidator() {
    // Create JSqlParser facade
    JSqlParserFacade facade = new JSqlParserFacade(true); // lenient mode

    // Create all available rule checkers with default configs
    List<RuleChecker> checkers = new ArrayList<>();
    checkers.add(
        new NoWhereClauseChecker(
            new com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig(true)));
    checkers.add(
        new DummyConditionChecker(
            new com.footstone.sqlguard.validator.rule.impl.DummyConditionConfig(true)));
    checkers.add(
        new BlacklistFieldChecker(
            new com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig()));
    checkers.add(
        new WhitelistFieldChecker(
            new com.footstone.sqlguard.validator.rule.impl.WhitelistFieldsConfig()));

    // Create orchestrator
    RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);

    // Create deduplication filter
    SqlDeduplicationFilter deduplicationFilter = new SqlDeduplicationFilter();

    // Create validator
    return new DefaultSqlSafetyValidator(facade, checkers, orchestrator, deduplicationFilter);
  }

  /**
   * Loads ViolationStrategy from system property or returns default.
   *
   * <p><strong>Configuration:</strong></p>
   * <ul>
   *   <li>System property: -Dsqlguard.p6spy.strategy=BLOCK|WARN|LOG</li>
   *   <li>Default: LOG (observation mode)</li>
   * </ul>
   *
   * @return ViolationStrategy instance
   */
  static ViolationStrategy loadStrategy() {
    String strategyValue = System.getProperty(STRATEGY_PROPERTY, "LOG");
    try {
      ViolationStrategy strategy = ViolationStrategy.valueOf(strategyValue.toUpperCase());
      logger.info("Loaded ViolationStrategy from system property: {}", strategy);
      return strategy;
    } catch (IllegalArgumentException e) {
      logger.warn(
          "Invalid violation strategy '{}', defaulting to LOG. Valid values: BLOCK, WARN, LOG",
          strategyValue);
      return ViolationStrategy.LOG;
    }
  }

  /**
   * Gets the shared listener instance for testing purposes.
   *
   * @return P6SpySqlSafetyListener instance or null if initialization failed
   */
  static P6SpySqlSafetyListener getListener() {
    return listener;
  }
}
