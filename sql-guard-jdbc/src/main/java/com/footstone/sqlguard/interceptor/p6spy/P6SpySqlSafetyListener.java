package com.footstone.sqlguard.interceptor.p6spy;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.p6spy.engine.common.StatementInformation;
import com.p6spy.engine.event.JdbcEventListener;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P6Spy JDBC event listener providing universal SQL validation for any JDBC driver/connection
 * pool.
 *
 * <p>P6SpySqlSafetyListener implements P6Spy's JdbcEventListener interface to intercept SQL
 * execution at the JDBC driver level. This provides framework-agnostic SQL validation as a
 * fallback solution when native integrations (MyBatis, Druid, HikariCP) are unavailable.</p>
 *
 * <p><strong>Architecture:</strong></p>
 * <pre>
 * Application → Connection Pool (any) → P6Spy Proxy Driver → Real JDBC Driver
 *                                            ↓
 *                                  onBeforeAnyExecute()
 *                                            ↓
 *                                  SQL Validation Pipeline
 * </pre>
 *
 * <p><strong>Key Features:</strong></p>
 * <ul>
 *   <li>Universal JDBC interception - works with any driver (MySQL, PostgreSQL, H2, Oracle)</li>
 *   <li>Connection pool agnostic - supports C3P0, DBCP, Tomcat JDBC, bare JDBC</li>
 *   <li>Parameter-substituted SQL validation (P6Spy provides values)</li>
 *   <li>Deduplication support via SqlDeduplicationFilter</li>
 *   <li>Configurable violation strategies (BLOCK/WARN/LOG)</li>
 * </ul>
 *
 * <p><strong>Performance Trade-offs:</strong></p>
 * <ul>
 *   <li>Overhead: ~15% (higher than native solutions)</li>
 *   <li>Setup complexity: Low (just driver configuration change)</li>
 *   <li>Coverage: Universal (any JDBC-compliant system)</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. All fields are final and
 * immutable. SqlDeduplicationFilter uses ThreadLocal for thread isolation.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Configuration in spy.properties:
 * // modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlSafetyModule
 * //
 * // Driver configuration:
 * // spring.datasource.driver-class-name=com.p6spy.engine.spy.P6SpyDriver
 * // spring.datasource.url=jdbc:p6spy:mysql://localhost:3306/mydb
 * }</pre>
 *
 * @see JdbcEventListener
 * @see DefaultSqlSafetyValidator
 * @see ViolationStrategy
 */
public class P6SpySqlSafetyListener extends JdbcEventListener {

  private static final Logger logger = LoggerFactory.getLogger(P6SpySqlSafetyListener.class);

  /**
   * Pattern to extract SQL command type from SQL string.
   */
  private static final Pattern SQL_TYPE_PATTERN =
      Pattern.compile("^\\s*(SELECT|UPDATE|DELETE|INSERT)", Pattern.CASE_INSENSITIVE);

  /**
   * SQL safety validator for validation pipeline.
   */
  private final DefaultSqlSafetyValidator validator;

  /**
   * Violation handling strategy.
   */
  private final ViolationStrategy strategy;

  /**
   * Deduplication filter to prevent redundant validation.
   */
  private final SqlDeduplicationFilter deduplicationFilter;

  /**
   * Constructs a P6SpySqlSafetyListener with specified components.
   *
   * @param validator the SQL safety validator
   * @param strategy the violation handling strategy
   * @throws IllegalArgumentException if validator or strategy is null
   */
  public P6SpySqlSafetyListener(DefaultSqlSafetyValidator validator, ViolationStrategy strategy) {
    if (validator == null) {
      throw new IllegalArgumentException("validator cannot be null");
    }
    if (strategy == null) {
      throw new IllegalArgumentException("strategy cannot be null");
    }

    this.validator = validator;
    this.strategy = strategy;
    this.deduplicationFilter = new SqlDeduplicationFilter();
  }

  /**
   * Constructs a P6SpySqlSafetyListener with custom deduplication filter.
   *
   * @param validator the SQL safety validator
   * @param strategy the violation handling strategy
   * @param deduplicationFilter custom deduplication filter
   * @throws IllegalArgumentException if any parameter is null
   */
  public P6SpySqlSafetyListener(
      DefaultSqlSafetyValidator validator,
      ViolationStrategy strategy,
      SqlDeduplicationFilter deduplicationFilter) {
    if (validator == null) {
      throw new IllegalArgumentException("validator cannot be null");
    }
    if (strategy == null) {
      throw new IllegalArgumentException("strategy cannot be null");
    }
    if (deduplicationFilter == null) {
      throw new IllegalArgumentException("deduplicationFilter cannot be null");
    }

    this.validator = validator;
    this.strategy = strategy;
    this.deduplicationFilter = deduplicationFilter;
  }

  /**
   * Intercepts SQL execution before any JDBC statement execution.
   *
   * <p>This method is called by P6Spy before executing any SQL statement (PreparedStatement,
   * Statement, CallableStatement). It extracts the SQL with parameter values substituted and
   * validates it against all safety rules.</p>
   *
   * <p><strong>Execution Flow:</strong></p>
   * <ol>
   *   <li>Extract SQL with parameter values from StatementInformation</li>
   *   <li>Skip if SQL is null or empty</li>
   *   <li>Check deduplication filter</li>
   *   <li>Detect SQL command type</li>
   *   <li>Build SqlContext with execution metadata</li>
   *   <li>Validate SQL using DefaultSqlSafetyValidator</li>
   *   <li>Handle violations according to strategy</li>
   * </ol>
   *
   * @param statementInfo P6Spy statement information with SQL and connection context
   * @throws SQLException if validation fails and strategy is BLOCK
   */
  public void onBeforeAnyExecute(StatementInformation statementInfo) throws SQLException {
    // Extract SQL with parameter values substituted
    String sql = statementInfo.getSqlWithValues();

    // Skip if empty
    if (sql == null || sql.trim().isEmpty()) {
      return;
    }

    // Validate SQL
    validateSql(sql, statementInfo);
  }

  /**
   * Validates SQL statement and handles violations.
   *
   * @param sql the SQL string with parameter values substituted
   * @param statementInfo P6Spy statement information
   * @throws SQLException if validation fails and strategy is BLOCK
   */
  private void validateSql(String sql, StatementInformation statementInfo) throws SQLException {
    // Deduplication check
    if (!deduplicationFilter.shouldCheck(sql)) {
      return;
    }

    // Detect SQL command type
    SqlCommandType type = detectSqlType(sql);

    // Extract datasource from connection URL if available
    String datasource = extractDatasourceFromUrl(statementInfo);

    // Build SqlContext
    // mapperId must be in format "namespace.methodId" - use "jdbc.p6spy" as namespace
    SqlContext context =
        SqlContext.builder()
            .sql(sql)
            .type(type)
            .mapperId("jdbc.p6spy:" + datasource)
            .datasource(datasource)
            .build();

    // Validate
    ValidationResult result = validator.validate(context);

    // Handle violations
    if (!result.isPassed()) {
      handleViolation(result, sql);
    }
  }

  /**
   * Detects SQL command type from SQL string.
   *
   * @param sql the SQL string
   * @return detected SqlCommandType or UNKNOWN
   */
  SqlCommandType detectSqlType(String sql) {
    if (sql == null || sql.trim().isEmpty()) {
      return SqlCommandType.UNKNOWN;
    }

    Matcher matcher = SQL_TYPE_PATTERN.matcher(sql);
    if (matcher.find()) {
      String commandType = matcher.group(1).toUpperCase();
      return SqlCommandType.fromString(commandType);
    }

    return SqlCommandType.UNKNOWN;
  }

  /**
   * Extracts datasource identifier from connection URL.
   *
   * @param statementInfo P6Spy statement information
   * @return datasource identifier or "unknown"
   */
  String extractDatasourceFromUrl(StatementInformation statementInfo) {
    try {
      if (statementInfo.getConnectionInformation() != null) {
        String url = statementInfo.getConnectionInformation().getUrl();
        if (url != null) {
          // Extract database name from JDBC URL
          // Example: jdbc:p6spy:mysql://localhost:3306/mydb → mydb
          // Example: jdbc:p6spy:h2:mem:test → test
          if (url.contains("/")) {
            String[] parts = url.split("/");
            String lastPart = parts[parts.length - 1];
            // Remove query parameters if present
            if (lastPart.contains("?")) {
              lastPart = lastPart.substring(0, lastPart.indexOf("?"));
            }
            return lastPart.isEmpty() ? "unknown" : lastPart;
          } else if (url.contains(":")) {
            // Handle H2 in-memory URLs like jdbc:p6spy:h2:mem:test
            String[] parts = url.split(":");
            if (parts.length > 0) {
              return parts[parts.length - 1];
            }
          }
        }
      }
    } catch (Exception e) {
      logger.debug("Failed to extract datasource from URL: {}", e.getMessage());
    }
    return "unknown";
  }

  /**
   * Handles validation violations according to configured strategy.
   *
   * @param result the validation result with violations
   * @param sql the SQL string that failed validation
   * @throws SQLException if strategy is BLOCK
   */
  private void handleViolation(ValidationResult result, String sql) throws SQLException {
    String violationMsg = formatViolations(result, sql);

    switch (strategy) {
      case BLOCK:
        logger.error("[BLOCK] SQL Safety Violation: {}", violationMsg);
        throw new SQLException(
            "SQL Safety Violation (BLOCK): " + violationMsg, "42000" // SQLState for syntax/access
            // rule violation
            );

      case WARN:
        logger.error("[WARN] SQL Safety Violation: {}", violationMsg);
        break;

      case LOG:
        logger.warn("[LOG] SQL Safety Violation: {}", violationMsg);
        break;

      default:
        logger.warn("[UNKNOWN] SQL Safety Violation: {}", violationMsg);
        break;
    }
  }

  /**
   * Formats violation information for logging.
   *
   * @param result the validation result
   * @param sql the SQL string
   * @return formatted violation message
   */
  private String formatViolations(ValidationResult result, String sql) {
    StringBuilder sb = new StringBuilder();
    sb.append("SQL: ").append(getSqlSnippet(sql));
    sb.append(" | Risk: ").append(result.getRiskLevel());
    sb.append(" | Violations: [");

    boolean first = true;
    for (ViolationInfo violation : result.getViolations()) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(violation.getMessage());
      if (violation.getSuggestion() != null && !violation.getSuggestion().isEmpty()) {
        sb.append(" (Suggestion: ").append(violation.getSuggestion()).append(")");
      }
      first = false;
    }

    sb.append("]");
    return sb.toString();
  }

  /**
   * Extracts SQL snippet for logging (first 100 characters).
   *
   * @param sql the full SQL string
   * @return truncated SQL snippet
   */
  private String getSqlSnippet(String sql) {
    if (sql == null) {
      return "null";
    }
    if (sql.length() <= 100) {
      return sql;
    }
    return sql.substring(0, 100) + "...";
  }
}

