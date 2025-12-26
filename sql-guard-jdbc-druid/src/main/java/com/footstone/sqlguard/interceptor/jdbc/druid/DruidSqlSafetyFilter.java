package com.footstone.sqlguard.interceptor.jdbc.druid;

import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.proxy.jdbc.CallableStatementProxy;
import com.alibaba.druid.proxy.jdbc.ConnectionProxy;
import com.alibaba.druid.proxy.jdbc.DataSourceProxy;
import com.alibaba.druid.proxy.jdbc.PreparedStatementProxy;
import com.alibaba.druid.proxy.jdbc.ResultSetProxy;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * Druid connection pool filter that intercepts SQL at the JDBC layer.
 *
 * <p>DruidSqlSafetyFilter extends Druid's {@link FilterAdapter} to intercept SQL execution,
 * providing validation coverage for SQL not passing through MyBatis/MyBatis-Plus layers
 * (direct JDBC usage, JdbcTemplate, other ORM frameworks).</p>
 *
 * <h2>Composition Pattern</h2>
 * <p>This filter uses composition to delegate validation logic to {@link DruidJdbcInterceptor},
 * which extends {@link com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase}:</p>
 * <pre>
 * FilterAdapter (Druid)
 *     ↑
 *     | extends
 *     |
 * DruidSqlSafetyFilter
 *     |
 *     | composes (has-a)
 *     ↓
 * DruidJdbcInterceptor
 *     ↑
 *     | extends
 *     |
 * JdbcInterceptorBase (sql-guard-jdbc-common)
 * </pre>
 *
 * <h2>Benefits of Composition</h2>
 * <ul>
 *   <li>Clear separation of concerns: Filter handles Druid integration, Interceptor handles validation</li>
 *   <li>Testability: Interceptor can be tested independently</li>
 *   <li>Reusability: Same interceptor logic can be used with different integration mechanisms</li>
 *   <li>Consistency: Validation behavior consistent across all JDBC modules via template method</li>
 * </ul>
 *
 * <h2>Interception Points</h2>
 * <ul>
 *   <li>{@link #connection_prepareStatement} - Intercepts PreparedStatement creation</li>
 *   <li>{@link #connection_prepareCall} - Intercepts CallableStatement creation</li>
 *   <li>{@link #statement_executeQuery} - Intercepts Statement.executeQuery()</li>
 *   <li>{@link #statement_executeUpdate} - Intercepts Statement.executeUpdate()</li>
 *   <li>{@link #statement_execute} - Intercepts Statement.execute()</li>
 * </ul>
 *
 * <h2>Filter Ordering</h2>
 * <p>Should be registered at the beginning of the filter list to execute before Druid's
 * StatFilter, allowing violations to be tracked in Druid statistics.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * DruidInterceptorConfig config = new MyDruidConfig();
 * DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(rulesConfig);
 * DruidJdbcInterceptor interceptor = new DruidJdbcInterceptor(config, validator);
 * DruidSqlSafetyFilter filter = new DruidSqlSafetyFilter(interceptor);
 *
 * DruidDataSource dataSource = new DruidDataSource();
 * dataSource.getProxyFilters().add(0, filter);
 * }</pre>
 *
 * @since 2.0.0
 * @see FilterAdapter
 * @see DruidJdbcInterceptor
 * @see com.footstone.sqlguard.interceptor.jdbc.common.JdbcInterceptorBase
 */
public class DruidSqlSafetyFilter extends FilterAdapter {

    private static final Logger logger = LoggerFactory.getLogger(DruidSqlSafetyFilter.class);

    /**
     * The interceptor that handles SQL validation via composition.
     */
    private final DruidJdbcInterceptor interceptor;

    /**
     * Constructs a DruidSqlSafetyFilter with the specified interceptor.
     *
     * @param interceptor the DruidJdbcInterceptor for SQL validation
     * @throws IllegalArgumentException if interceptor is null
     */
    public DruidSqlSafetyFilter(DruidJdbcInterceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor cannot be null");
        }
        this.interceptor = interceptor;
    }

    // ========== Connection-Level Interception ==========

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
        
        // Intercept SQL validation
        interceptSql(sql, connection);
        
        return super.connection_prepareStatement(chain, connection, sql);
    }

    /**
     * Intercepts PreparedStatement creation with result set type and concurrency.
     */
    @Override
    public PreparedStatementProxy connection_prepareStatement(
            FilterChain chain,
            ConnectionProxy connection,
            String sql,
            int resultSetType,
            int resultSetConcurrency) throws SQLException {
        
        interceptSql(sql, connection);
        
        return super.connection_prepareStatement(chain, connection, sql, resultSetType, resultSetConcurrency);
    }

    /**
     * Intercepts PreparedStatement creation with result set type, concurrency, and holdability.
     */
    @Override
    public PreparedStatementProxy connection_prepareStatement(
            FilterChain chain,
            ConnectionProxy connection,
            String sql,
            int resultSetType,
            int resultSetConcurrency,
            int resultSetHoldability) throws SQLException {
        
        interceptSql(sql, connection);
        
        return super.connection_prepareStatement(chain, connection, sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    }

    /**
     * Intercepts PreparedStatement creation with auto-generated keys flag.
     */
    @Override
    public PreparedStatementProxy connection_prepareStatement(
            FilterChain chain,
            ConnectionProxy connection,
            String sql,
            int autoGeneratedKeys) throws SQLException {
        
        interceptSql(sql, connection);
        
        return super.connection_prepareStatement(chain, connection, sql, autoGeneratedKeys);
    }

    /**
     * Intercepts PreparedStatement creation with column indexes.
     */
    @Override
    public PreparedStatementProxy connection_prepareStatement(
            FilterChain chain,
            ConnectionProxy connection,
            String sql,
            int[] columnIndexes) throws SQLException {
        
        interceptSql(sql, connection);
        
        return super.connection_prepareStatement(chain, connection, sql, columnIndexes);
    }

    /**
     * Intercepts PreparedStatement creation with column names.
     */
    @Override
    public PreparedStatementProxy connection_prepareStatement(
            FilterChain chain,
            ConnectionProxy connection,
            String sql,
            String[] columnNames) throws SQLException {
        
        interceptSql(sql, connection);
        
        return super.connection_prepareStatement(chain, connection, sql, columnNames);
    }

    /**
     * Intercepts CallableStatement creation to validate SQL at prepare time.
     *
     * @param chain the filter chain
     * @param connection the connection proxy
     * @param sql the SQL to prepare
     * @return the callable statement proxy
     * @throws SQLException if validation fails with BLOCK strategy
     */
    @Override
    public CallableStatementProxy connection_prepareCall(
            FilterChain chain,
            ConnectionProxy connection,
            String sql) throws SQLException {
        
        interceptSql(sql, connection);
        
        return super.connection_prepareCall(chain, connection, sql);
    }

    // ========== Statement-Level Interception ==========

    /**
     * Intercepts Statement.executeQuery() to validate SQL at execute time.
     *
     * @param chain the filter chain
     * @param statement the statement proxy
     * @param sql the SQL to execute
     * @return the result set proxy
     * @throws SQLException if validation fails with BLOCK strategy
     */
    @Override
    public ResultSetProxy statement_executeQuery(
            FilterChain chain,
            StatementProxy statement,
            String sql) throws SQLException {
        
        try {
            interceptSql(sql, statement.getConnectionProxy());
            return super.statement_executeQuery(chain, statement, sql);
        } finally {
            // Cleanup ThreadLocal after execution
            DruidJdbcInterceptor.clearValidationResult();
            DruidJdbcInterceptor.clearDatasourceName();
        }
    }

    /**
     * Intercepts Statement.executeUpdate() to validate SQL at execute time.
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
        
        try {
            interceptSql(sql, statement.getConnectionProxy());
            return super.statement_executeUpdate(chain, statement, sql);
        } finally {
            DruidJdbcInterceptor.clearValidationResult();
            DruidJdbcInterceptor.clearDatasourceName();
        }
    }

    /**
     * Intercepts Statement.execute() to validate SQL at execute time.
     *
     * @param chain the filter chain
     * @param statement the statement proxy
     * @param sql the SQL to execute
     * @return true if the first result is a ResultSet
     * @throws SQLException if validation fails with BLOCK strategy
     */
    @Override
    public boolean statement_execute(
            FilterChain chain,
            StatementProxy statement,
            String sql) throws SQLException {
        
        try {
            interceptSql(sql, statement.getConnectionProxy());
            return super.statement_execute(chain, statement, sql);
        } finally {
            DruidJdbcInterceptor.clearValidationResult();
            DruidJdbcInterceptor.clearDatasourceName();
        }
    }

    // ========== Helper Methods ==========

    /**
     * Intercepts SQL by delegating to the DruidJdbcInterceptor.
     *
     * <p>Sets up datasource context and handles exceptions from the interceptor,
     * wrapping SqlSafetyViolationException in SQLException for JDBC compliance.</p>
     *
     * @param sql the SQL to intercept
     * @param connection the connection proxy for datasource context
     * @throws SQLException if validation fails with BLOCK strategy
     */
    private void interceptSql(String sql, ConnectionProxy connection) throws SQLException {
        // Set datasource context for interceptor
        String datasourceName = extractDatasourceName(connection);
        DruidJdbcInterceptor.setDatasourceName(datasourceName);
        
        try {
            // Delegate to interceptor (template method pattern)
            interceptor.interceptSql(sql);
        } catch (SqlSafetyViolationException e) {
            // Wrap in SQLException for JDBC compliance
            throw new SQLException(e.getMessage(), e.getSqlState(), e);
        } catch (Exception e) {
            // Log and continue for non-violation exceptions
            logger.debug("Unexpected error during SQL interception", e);
        }
    }

    /**
     * Extracts datasource name from ConnectionProxy.
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
     * Returns the interceptor used by this filter.
     *
     * @return the DruidJdbcInterceptor
     */
    public DruidJdbcInterceptor getInterceptor() {
        return interceptor;
    }
}







