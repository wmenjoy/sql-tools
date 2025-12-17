package com.footstone.sqlguard.interceptor.hikari;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HikariCP-compatible SQL safety proxy factory using DataSource wrapping pattern.
 *
 * <p>Since HikariCP doesn't expose a ProxyFactory interface for custom proxies (it uses
 * Javassist bytecode generation internally), this implementation wraps the DataSource
 * to intercept Connection creation and then wraps Connection/Statement objects using
 * JDK dynamic proxies.</p>
 *
 * <p><strong>Architecture:</strong></p>
 * <pre>
 * HikariDataSource (wrapped)
 *     ↓
 * getConnection() → Connection Proxy (ConnectionInvocationHandler)
 *     ↓
 * prepareStatement(sql) → Validate SQL → PreparedStatement Proxy
 * createStatement() → Statement Proxy
 *     ↓
 * execute(sql) → Validate SQL → Delegate to real Statement
 * </pre>
 *
 * <p><strong>Performance Characteristics:</strong></p>
 * <ul>
 *   <li>Target overhead: &lt;1% for connection acquisition</li>
 *   <li>Target overhead: &lt;5% for SQL execution</li>
 *   <li>Deduplication filter prevents redundant validation</li>
 *   <li>Compatible with HikariCP's microsecond-level performance goals</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * HikariConfig config = new HikariConfig();
 * config.setJdbcUrl("jdbc:mysql://localhost:3306/db");
 * HikariDataSource hikariDs = new HikariDataSource(config);
 *
 * // Wrap with SQL safety proxy
 * DataSource safeDs = HikariSqlSafetyProxyFactory.wrap(
 *     hikariDs,
 *     validator,
 *     ViolationStrategy.BLOCK
 * );
 * }</pre>
 *
 * @see DefaultSqlSafetyValidator
 * @see ViolationStrategy
 */
public class HikariSqlSafetyProxyFactory {

  private static final Logger logger = LoggerFactory.getLogger(HikariSqlSafetyProxyFactory.class);

  /**
   * Wraps a DataSource (typically HikariDataSource) with SQL safety validation.
   *
   * @param dataSource the datasource to wrap (typically HikariDataSource)
   * @param validator the SQL safety validator
   * @param strategy the violation handling strategy
   * @return wrapped DataSource that validates SQL before execution
   * @throws IllegalArgumentException if any parameter is null
   */
  public static DataSource wrap(
      DataSource dataSource,
      DefaultSqlSafetyValidator validator,
      ViolationStrategy strategy) {
    if (dataSource == null) {
      throw new IllegalArgumentException("dataSource cannot be null");
    }
    if (validator == null) {
      throw new IllegalArgumentException("validator cannot be null");
    }
    if (strategy == null) {
      throw new IllegalArgumentException("strategy cannot be null");
    }

    return (DataSource) Proxy.newProxyInstance(
        dataSource.getClass().getClassLoader(),
        new Class<?>[]{DataSource.class},
        new DataSourceInvocationHandler(dataSource, validator, strategy)
    );
  }

  /**
   * InvocationHandler for DataSource proxy intercepting getConnection().
   */
  private static class DataSourceInvocationHandler implements InvocationHandler {

    private final DataSource delegate;
    private final DefaultSqlSafetyValidator validator;
    private final ViolationStrategy strategy;

    DataSourceInvocationHandler(
        DataSource delegate,
        DefaultSqlSafetyValidator validator,
        ViolationStrategy strategy) {
      this.delegate = delegate;
      this.validator = validator;
      this.strategy = strategy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String methodName = method.getName();

      // Intercept getConnection methods
      if ("getConnection".equals(methodName)) {
        Connection conn = (Connection) method.invoke(delegate, args);
        // Wrap connection in proxy
        return wrapConnection(conn);
      }

      // Delegate all other methods
      return method.invoke(delegate, args);
    }

    private Connection wrapConnection(Connection conn) {
      return (Connection) Proxy.newProxyInstance(
          conn.getClass().getClassLoader(),
          new Class<?>[]{Connection.class},
          new ConnectionInvocationHandler(conn, delegate.toString(), validator, strategy)
      );
    }
  }

  /**
   * InvocationHandler for Connection proxy intercepting SQL-related methods.
   */
  private static class ConnectionInvocationHandler implements InvocationHandler {

    private final Connection delegate;
    private final String datasourceName;
    private final DefaultSqlSafetyValidator validator;
    private final ViolationStrategy strategy;

    ConnectionInvocationHandler(
        Connection delegate,
        String datasourceName,
        DefaultSqlSafetyValidator validator,
        ViolationStrategy strategy) {
      this.delegate = delegate;
      this.datasourceName = datasourceName;
      this.validator = validator;
      this.strategy = strategy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String methodName = method.getName();

      // Intercept prepareStatement - SQL known at prepare time
      if ("prepareStatement".equals(methodName) && args != null && args.length > 0
          && args[0] instanceof String) {
        String sql = (String) args[0];
        validateSql(sql);

        // Create actual PreparedStatement
        PreparedStatement ps = (PreparedStatement) method.invoke(delegate, args);

        // Wrap in proxy (PreparedStatement already validated, no need to re-validate on execute)
        return wrapPreparedStatement(ps, sql);
      }

      // Intercept createStatement - SQL not known yet, will validate at execute time
      if ("createStatement".equals(methodName)) {
        Statement stmt = (Statement) method.invoke(delegate, args);
        return wrapStatement(stmt);
      }

      // Intercept prepareCall - for CallableStatement
      if ("prepareCall".equals(methodName) && args != null && args.length > 0
          && args[0] instanceof String) {
        String sql = (String) args[0];
        validateSql(sql);

        // Create actual CallableStatement
        java.sql.CallableStatement cs =
            (java.sql.CallableStatement) method.invoke(delegate, args);

        // Wrap in proxy (already validated)
        return wrapCallableStatement(cs, sql);
      }

      // Delegate all other methods to real connection
      return method.invoke(delegate, args);
    }

    /**
     * Validates SQL using the configured validator and handles violations.
     *
     * @param sql the SQL statement to validate
     * @throws SQLException if validation fails and strategy is BLOCK
     */
    private void validateSql(String sql) throws SQLException {
      try {
        // Detect SQL type
        SqlCommandType type = detectSqlType(sql);

        // Build SqlContext
        // mapperId must be in format "namespace.methodId" (SqlContext validation requirement)
        SqlContext context = SqlContext.builder()
            .sql(sql)
            .type(type)
            .mapperId("jdbc.hikari:" + datasourceName)
            .datasource(datasourceName)
            .build();

        // Validate
        ValidationResult result = validator.validate(context);

        // Handle violations
        if (!result.isPassed()) {
          handleViolation(result, sql);
        }
      } catch (SQLException e) {
        // Re-throw SQLException (from BLOCK strategy)
        throw e;
      } catch (Exception e) {
        // Log unexpected errors but don't block execution
        logger.error("Unexpected error during SQL validation: {}", e.getMessage(), e);
      }
    }

    /**
     * Detects SQL command type from SQL string.
     *
     * @param sql the SQL statement
     * @return the detected SqlCommandType
     */
    private SqlCommandType detectSqlType(String sql) {
      if (sql == null) {
        return SqlCommandType.SELECT;
      }
      String trimmed = sql.trim().toUpperCase();
      if (trimmed.startsWith("SELECT")) {
        return SqlCommandType.SELECT;
      } else if (trimmed.startsWith("UPDATE")) {
        return SqlCommandType.UPDATE;
      } else if (trimmed.startsWith("DELETE")) {
        return SqlCommandType.DELETE;
      } else if (trimmed.startsWith("INSERT")) {
        return SqlCommandType.INSERT;
      }
      return SqlCommandType.SELECT;
    }

    /**
     * Handles validation violations based on configured strategy.
     *
     * @param result the validation result containing violations
     * @param sql the SQL statement that failed validation
     * @throws SQLException if strategy is BLOCK
     */
    private void handleViolation(ValidationResult result, String sql) throws SQLException {
      String message = formatViolationMessage(result, sql);

      switch (strategy) {
        case BLOCK:
          logger.error(message);
          throw new SQLException(message, "42000");

        case WARN:
          logger.error(message);
          break;

        case LOG:
          logger.warn(message);
          break;
      }
    }

    /**
     * Formats violation message from ValidationResult.
     *
     * @param result the validation result
     * @param sql the SQL statement
     * @return formatted message
     */
    private String formatViolationMessage(ValidationResult result, String sql) {
      StringBuilder sb = new StringBuilder();
      sb.append("SQL safety violation detected [datasource=").append(datasourceName)
          .append(", riskLevel=").append(result.getRiskLevel()).append("]: ");

      // Format violations
      for (ViolationInfo violation : result.getViolations()) {
        sb.append(violation.getMessage()).append("; ");
      }

      sb.append("SQL: ").append(truncateSql(sql));
      return sb.toString();
    }

    /**
     * Truncates SQL for logging (first 200 characters).
     *
     * @param sql the SQL statement
     * @return truncated SQL
     */
    private String truncateSql(String sql) {
      if (sql == null) {
        return "null";
      }
      if (sql.length() <= 200) {
        return sql;
      }
      return sql.substring(0, 200) + "...";
    }

    /**
     * Wraps PreparedStatement in a proxy (no-op proxy since SQL already validated).
     *
     * @param ps the PreparedStatement to wrap
     * @param sql the SQL statement (already validated)
     * @return proxy PreparedStatement
     */
    private PreparedStatement wrapPreparedStatement(PreparedStatement ps, String sql) {
      return (PreparedStatement) Proxy.newProxyInstance(
          ps.getClass().getClassLoader(),
          new Class<?>[]{PreparedStatement.class},
          new PreparedStatementInvocationHandler(ps, sql)
      );
    }

    /**
     * Wraps Statement in a proxy for execute-time validation.
     *
     * @param stmt the Statement to wrap
     * @return proxy Statement
     */
    private Statement wrapStatement(Statement stmt) {
      return (Statement) Proxy.newProxyInstance(
          stmt.getClass().getClassLoader(),
          new Class<?>[]{Statement.class},
          new StatementInvocationHandler(stmt, validator, strategy, datasourceName)
      );
    }

    /**
     * Wraps CallableStatement in a proxy (no-op proxy since SQL already validated).
     *
     * @param cs the CallableStatement to wrap
     * @param sql the SQL statement (already validated)
     * @return proxy CallableStatement
     */
    private java.sql.CallableStatement wrapCallableStatement(
        java.sql.CallableStatement cs, String sql) {
      return (java.sql.CallableStatement) Proxy.newProxyInstance(
          cs.getClass().getClassLoader(),
          new Class<?>[]{java.sql.CallableStatement.class},
          new CallableStatementInvocationHandler(cs, sql)
      );
    }
  }

  /**
   * InvocationHandler for PreparedStatement proxy (no-op since SQL already validated).
   */
  private static class PreparedStatementInvocationHandler implements InvocationHandler {

    private final PreparedStatement delegate;
    private final String sql;

    PreparedStatementInvocationHandler(PreparedStatement delegate, String sql) {
      this.delegate = delegate;
      this.sql = sql;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      // All methods delegate to real PreparedStatement (SQL already validated)
      return method.invoke(delegate, args);
    }
  }

  /**
   * InvocationHandler for Statement proxy validating SQL at execute time.
   */
  private static class StatementInvocationHandler implements InvocationHandler {

    private final Statement delegate;
    private final DefaultSqlSafetyValidator validator;
    private final ViolationStrategy strategy;
    private final String datasourceName;

    StatementInvocationHandler(
        Statement delegate,
        DefaultSqlSafetyValidator validator,
        ViolationStrategy strategy,
        String datasourceName) {
      this.delegate = delegate;
      this.validator = validator;
      this.strategy = strategy;
      this.datasourceName = datasourceName;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String methodName = method.getName();

      // Intercept execute(String sql)
      if ("execute".equals(methodName) && args != null && args.length > 0
          && args[0] instanceof String) {
        String sql = (String) args[0];
        validateSql(sql);
      }

      // Intercept executeQuery(String sql)
      if ("executeQuery".equals(methodName) && args != null && args.length > 0
          && args[0] instanceof String) {
        String sql = (String) args[0];
        validateSql(sql);
      }

      // Intercept executeUpdate(String sql)
      if ("executeUpdate".equals(methodName) && args != null && args.length > 0
          && args[0] instanceof String) {
        String sql = (String) args[0];
        validateSql(sql);
      }

      // Intercept addBatch(String sql) for Statement
      if ("addBatch".equals(methodName) && args != null && args.length > 0
          && args[0] instanceof String) {
        String sql = (String) args[0];
        validateSql(sql);
      }

      // Delegate to real Statement
      return method.invoke(delegate, args);
    }

    /**
     * Validates SQL using the configured validator.
     *
     * @param sql the SQL statement to validate
     * @throws SQLException if validation fails and strategy is BLOCK
     */
    private void validateSql(String sql) throws SQLException {
      try {
        // Detect SQL type
        SqlCommandType type = detectSqlType(sql);

        // Build SqlContext
        // mapperId must be in format "namespace.methodId" (SqlContext validation requirement)
        SqlContext context = SqlContext.builder()
            .sql(sql)
            .type(type)
            .mapperId("jdbc.hikari:" + datasourceName)
            .datasource(datasourceName)
            .build();

        // Validate
        ValidationResult result = validator.validate(context);

        // Handle violations
        if (!result.isPassed()) {
          handleViolation(result, sql);
        }
      } catch (SQLException e) {
        throw e;
      } catch (Exception e) {
        logger.error("Unexpected error during SQL validation: {}", e.getMessage(), e);
      }
    }

    private SqlCommandType detectSqlType(String sql) {
      if (sql == null) {
        return SqlCommandType.SELECT;
      }
      String trimmed = sql.trim().toUpperCase();
      if (trimmed.startsWith("SELECT")) {
        return SqlCommandType.SELECT;
      } else if (trimmed.startsWith("UPDATE")) {
        return SqlCommandType.UPDATE;
      } else if (trimmed.startsWith("DELETE")) {
        return SqlCommandType.DELETE;
      } else if (trimmed.startsWith("INSERT")) {
        return SqlCommandType.INSERT;
      }
      return SqlCommandType.SELECT;
    }

    private void handleViolation(ValidationResult result, String sql) throws SQLException {
      String message = formatViolationMessage(result, sql);

      switch (strategy) {
        case BLOCK:
          logger.error(message);
          throw new SQLException(message, "42000");

        case WARN:
          logger.error(message);
          break;

        case LOG:
          logger.warn(message);
          break;
      }
    }

    private String formatViolationMessage(ValidationResult result, String sql) {
      StringBuilder sb = new StringBuilder();
      sb.append("SQL safety violation detected [datasource=").append(datasourceName)
          .append(", riskLevel=").append(result.getRiskLevel()).append("]: ");

      for (ViolationInfo violation : result.getViolations()) {
        sb.append(violation.getMessage()).append("; ");
      }

      sb.append("SQL: ").append(truncateSql(sql));
      return sb.toString();
    }

    private String truncateSql(String sql) {
      if (sql == null) {
        return "null";
      }
      if (sql.length() <= 200) {
        return sql;
      }
      return sql.substring(0, 200) + "...";
    }
  }

  /**
   * InvocationHandler for CallableStatement proxy (no-op since SQL already validated).
   */
  private static class CallableStatementInvocationHandler implements InvocationHandler {

    private final java.sql.CallableStatement delegate;
    private final String sql;

    CallableStatementInvocationHandler(java.sql.CallableStatement delegate, String sql) {
      this.delegate = delegate;
      this.sql = sql;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      // All methods delegate to real CallableStatement (SQL already validated)
      return method.invoke(delegate, args);
    }
  }
}

