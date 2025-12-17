package com.footstone.sqlguard.interceptor.druid;

import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.ConnectionProxy;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
import com.alibaba.druid.proxy.jdbc.PreparedStatementProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Druid connection pool filter intercepting SQL at JDBC layer.
 *
 * <p>DruidSqlSafetyFilter extends Druid's FilterAdapter to intercept SQL execution at the JDBC
 * layer, providing validation coverage for SQL not passing through MyBatis/MyBatis-Plus layers
 * (direct JDBC usage, JdbcTemplate, other ORM frameworks).</p>
 *
 * <p><strong>Interception Points:</strong></p>
 * <ul>
 *   <li>{@link #preparedStatement_before} - Intercepts PreparedStatement creation</li>
 *   <li>{@link #statement_executeQuery} - Intercepts Statement.executeQuery()</li>
 *   <li>{@link #statement_executeUpdate} - Intercepts Statement.executeUpdate()</li>
 * </ul>
 *
 * <p><strong>Multi-Datasource Support:</strong></p>
 * <p>Extracts datasource name from ConnectionProxy to enable per-datasource violation tracking
 * and configuration.</p>
 *
 * <p><strong>Filter Ordering:</strong></p>
 * <p>Should be registered with order=1 to execute before Druid's StatFilter (order=2), allowing
 * violations to be tracked in Druid statistics.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * DruidDataSource dataSource = new DruidDataSource();
 * DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(validator, ViolationStrategy.WARN);
 * filter.setOrder(1);
 * dataSource.getProxyFilters().add(filter);
 * }</pre>
 *
 * @see FilterAdapter
 * @see DefaultSqlSafetyValidator
 * @see ViolationStrategy
 */
public class DruidSqlSafetyFilter extends FilterAdapter {

  private static final Logger logger = LoggerFactory.getLogger(DruidSqlSafetyFilter.class);

  /**
   * SQL safety validator for rule checking.
   */
  private final DefaultSqlSafetyValidator validator;

  /**
   * Strategy for handling violations (BLOCK/WARN/LOG).
   */
  private final ViolationStrategy strategy;

  /**
   * Deduplication filter to prevent redundant validation.
   */
  private final SqlDeduplicationFilter deduplicationFilter;

  /**
   * Constructs a DruidSqlSafetyFilter with validator and violation strategy.
   *
   * @param validator the SQL safety validator
   * @param strategy the violation handling strategy
   * @throws IllegalArgumentException if validator or strategy is null
   */
  public DruidSqlSafetyFilter(
      DefaultSqlSafetyValidator validator,
      ViolationStrategy strategy) {
    if (validator == null) {
      throw new IllegalArgumentException("validator cannot be null");
    }
    if (strategy == null) {
      throw new IllegalArgumentException("strategy cannot be null");
    }

    this.validator = validator;
    this.strategy = strategy;
    this.deduplicationFilter = new SqlDeduplicationFilter(1000, 100L); // 1000 cache size, 100ms TTL
  }

  /**
   * Intercepts PreparedStatement creation to validate SQL at prepare time.
   *
   * <p>This method is called when {@code connection.prepareStatement(sql)} is invoked.
   * It validates the SQL before the PreparedStatement is created.</p>
   *
   * @param chain the filter chain
   * @param connection the connection proxy
   * @param sql the SQL to prepare
   * @return the prepared statement proxy
   * @throws SQLException if validation fails with BLOCK strategy
   */
  @Override
  public PreparedStatementProxy connection_prepareStatement(
      FilterChain chain,
      ConnectionProxy connection,
      String sql) throws SQLException {

    // Validate SQL at prepare time
    validateSql(sql, connection);

    return super.connection_prepareStatement(chain, connection, sql);
  }

  /**
   * Intercepts CallableStatement creation to validate SQL at prepare time.
   *
   * <p>This method is called when {@code connection.prepareCall(sql)} is invoked.
   * It validates the SQL before the CallableStatement is created.</p>
   *
   * @param chain the filter chain
   * @param connection the connection proxy
   * @param sql the SQL to prepare
   * @return the callable statement proxy
   * @throws SQLException if validation fails with BLOCK strategy
   */
  @Override
  public com.alibaba.druid.proxy.jdbc.CallableStatementProxy connection_prepareCall(
      FilterChain chain,
      ConnectionProxy connection,
      String sql) throws SQLException {

    // Validate SQL at prepare time
    validateSql(sql, connection);

    return super.connection_prepareCall(chain, connection, sql);
  }

  /**
   * Intercepts Statement.executeQuery() to validate SQL at execute time.
   *
   * <p>This method is called when {@code statement.executeQuery(sql)} is invoked.
   * It validates the SQL before execution.</p>
   *
   * @param chain the filter chain
   * @param statement the statement proxy
   * @param sql the SQL to execute
   * @return the result set proxy
   * @throws SQLException if validation fails with BLOCK strategy
   */
  @Override
  public com.alibaba.druid.proxy.jdbc.ResultSetProxy statement_executeQuery(
      FilterChain chain,
      StatementProxy statement,
      String sql) throws SQLException {
    // Validate SQL at execute time (Statement)
    validateSql(sql, statement.getConnectionProxy());

    return super.statement_executeQuery(chain, statement, sql);
  }

  /**
   * Intercepts Statement.executeUpdate() to validate SQL at execute time.
   *
   * <p>This method is called when {@code statement.executeUpdate(sql)} is invoked.
   * It validates the SQL before execution.</p>
   *
   * @param chain the filter chain
   * @param statement the statement proxy
   * @param sql the SQL to execute
   * @return the update count
   * @throws SQLException if validation fails with BLOCK strategy
   */
  @Override
  public int statement_executeUpdate(
      FilterChain chain,
      StatementProxy statement,
      String sql) throws SQLException {

    validateSql(sql, statement.getConnectionProxy());

    return super.statement_executeUpdate(chain, statement, sql);
  }

  /**
   * Validates SQL statement using the configured validator.
   *
   * <p><strong>Validation Flow:</strong></p>
   * <ol>
   *   <li>Check deduplication filter - skip if recently validated</li>
   *   <li>Detect SQL command type from SQL prefix</li>
   *   <li>Extract datasource name from ConnectionProxy</li>
   *   <li>Build SqlContext with all metadata</li>
   *   <li>Execute validator</li>
   *   <li>Handle violations according to strategy</li>
   * </ol>
   *
   * @param sql the SQL to validate
   * @param connection the connection proxy for datasource extraction
   * @throws SQLException if validation fails with BLOCK strategy
   */
  private void validateSql(String sql, ConnectionProxy connection) throws SQLException {
    // Deduplication check
    if (!shouldValidate(sql)) {
      return;
    }

    // Detect SqlCommandType from SQL prefix
    SqlCommandType type = detectSqlType(sql);

    // Extract datasource name
    String datasourceName = extractDatasourceName(connection);

    // Build SqlContext
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(type)
        .mapperId("jdbc.druid:" + datasourceName) // Use dot instead of colon for namespace.methodId format
        .datasource(datasourceName)
        .build();

    // Validate
    ValidationResult result = validator.validate(context);

    // Handle violations
    if (!result.isPassed()) {
      handleViolation(result, datasourceName);
    }
  }

  /**
   * Checks if SQL should be validated based on deduplication filter.
   *
   * @param sql the SQL to check
   * @return true if SQL should be validated, false if recently validated
   */
  private boolean shouldValidate(String sql) {
    return deduplicationFilter.shouldCheck(sql);
  }

  /**
   * Detects SQL command type from SQL prefix.
   *
   * <p>Performs case-insensitive prefix matching on trimmed SQL.</p>
   *
   * @param sql the SQL to analyze
   * @return the detected SqlCommandType, or UNKNOWN if not recognized
   */
  private SqlCommandType detectSqlType(String sql) {
    if (sql == null || sql.trim().isEmpty()) {
      return SqlCommandType.UNKNOWN;
    }

    String upperSql = sql.trim().toUpperCase();

    if (upperSql.startsWith("SELECT")) {
      return SqlCommandType.SELECT;
    } else if (upperSql.startsWith("UPDATE")) {
      return SqlCommandType.UPDATE;
    } else if (upperSql.startsWith("DELETE")) {
      return SqlCommandType.DELETE;
    } else if (upperSql.startsWith("INSERT")) {
      return SqlCommandType.INSERT;
    } else {
      return SqlCommandType.UNKNOWN;
    }
  }

  /**
   * Extracts datasource name from ConnectionProxy.
   *
   * <p>Attempts to get datasource name from DataSourceProxy. Returns "default" if name is null,
   * or "unknown" if extraction fails.</p>
   *
   * @param connection the connection proxy
   * @return the datasource name, "default", or "unknown"
   */
  private String extractDatasourceName(ConnectionProxy connection) {
    try {
      DataSourceProxy dataSource = connection.getDirectDataSource();
      String name = dataSource.getName();
      return name != null ? name : "default";
    } catch (Exception e) {
      logger.debug("Failed to extract datasource name", e);
      return "unknown";
    }
  }

  /**
   * Handles validation violations according to the configured strategy.
   *
   * <p><strong>Strategy Behaviors:</strong></p>
   * <ul>
   *   <li><strong>BLOCK</strong> - Log error and throw SQLException</li>
   *   <li><strong>WARN</strong> - Log error and continue execution</li>
   *   <li><strong>LOG</strong> - Log warning and continue execution</li>
   * </ul>
   *
   * @param result the validation result with violations
   * @param datasourceName the datasource name for logging context
   * @throws SQLException if strategy is BLOCK
   */
  private void handleViolation(ValidationResult result, String datasourceName)
      throws SQLException {

    String message = formatViolationMessage(result, datasourceName);

    switch (strategy) {
      case BLOCK:
        logger.error(message);
        throw new SQLException(message, "42000"); // SQL syntax/access rule violation
      case WARN:
        logger.error(message);
        break;
      case LOG:
        logger.warn(message);
        break;
    }
  }

  /**
   * Formats violation message for logging.
   *
   * @param result the validation result
   * @param datasourceName the datasource name
   * @return formatted violation message
   */
  private String formatViolationMessage(ValidationResult result, String datasourceName) {
    StringBuilder sb = new StringBuilder();
    sb.append("SQL Safety Violation [Druid Filter] [Datasource: ")
        .append(datasourceName)
        .append("] [Risk: ")
        .append(result.getRiskLevel())
        .append("] - ");

    result.getViolations().forEach(v -> {
      sb.append(v.getMessage()).append("; ");
    });

    return sb.toString();
  }
}
