package com.footstone.sqlguard.interceptor.inner.impl;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.footstone.sqlguard.dialect.SqlGuardDialect;
import com.footstone.sqlguard.dialect.impl.MySQLDialect;
import com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor;
import com.footstone.sqlguard.parser.StatementContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectBody;
import net.sf.jsqlparser.statement.select.Top;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

/**
 * InnerInterceptor for automatic LIMIT addition as fallback safety mechanism.
 *
 * <h2>Purpose</h2>
 * <p>Automatically adds LIMIT clause to SELECT statements without existing pagination,
 * preventing full-table scans and large result sets. Acts as a safety fallback after
 * {@code SqlGuardCheckInnerInterceptor} (priority 10) has completed checks.
 *
 * <h2>Priority</h2>
 * <p>Priority: <b>150</b> (fallback interceptor) - runs after check interceptors (10)
 * but before rewrite interceptors (200).
 *
 * <h2>Behavior Modes</h2>
 * <ul>
 *   <li><b>Default mode (enforceMaxLimit=false)</b>: Only adds LIMIT to queries without pagination</li>
 *   <li><b>Enforce mode (enforceMaxLimit=true)</b>: Also caps existing large LIMIT values to maxLimit</li>
 * </ul>
 *
 * <h2>Pagination Detection</h2>
 * <p>Detects existing pagination via:
 * <ul>
 *   <li>LIMIT clause</li>
 *   <li>OFFSET clause</li>
 *   <li>TOP clause (SQL Server)</li>
 *   <li>RowBounds (MyBatis)</li>
 * </ul>
 *
 * <h2>Multi-Database Support</h2>
 * <p>Uses {@link SqlGuardDialect} abstraction to support database-specific syntax:
 * <ul>
 *   <li>MySQL/PostgreSQL: {@code LIMIT n}</li>
 *   <li>Oracle: {@code WHERE ROWNUM <= n}</li>
 *   <li>SQL Server: {@code TOP n}</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <ul>
 *   <li><b>defaultLimit</b>: Default limit value (default: 1000)</li>
 *   <li><b>enforceMaxLimit</b>: Whether to cap large LIMIT values (default: false)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Mode 1: Only add LIMIT to queries without pagination
 * SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor();
 *
 * // Mode 2: Also cap large LIMIT values (e.g., LIMIT 1000000 → LIMIT 1000)
 * SelectLimitInnerInterceptor interceptor = new SelectLimitInnerInterceptor(1000, true);
 *
 * // Add to interceptor chain
 * sqlGuardInterceptor.addInnerInterceptor(interceptor);
 * }</pre>
 *
 * @see SqlGuardDialect
 * @see com.footstone.sqlguard.dialect.DialectFactory
 * @since 1.1.0
 */
public class SelectLimitInnerInterceptor implements SqlGuardInnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SelectLimitInnerInterceptor.class);

    /**
     * Default limit value if no pagination detected.
     */
    private final long defaultLimit;

    /**
     * Database dialect for applying LIMIT clauses.
     */
    private final SqlGuardDialect dialect;

    /**
     * Whether to enforce max limit on existing LIMIT clauses.
     * When true, LIMIT values exceeding defaultLimit will be capped.
     */
    private final boolean enforceMaxLimit;

    /**
     * Constructs SelectLimitInnerInterceptor with default limit 1000 and MySQL dialect.
     * enforceMaxLimit is disabled by default.
     */
    public SelectLimitInnerInterceptor() {
        this(1000, new MySQLDialect(), false);
    }

    /**
     * Constructs SelectLimitInnerInterceptor with custom limit and default MySQL dialect.
     *
     * @param defaultLimit Default limit value
     */
    public SelectLimitInnerInterceptor(long defaultLimit) {
        this(defaultLimit, new MySQLDialect(), false);
    }

    /**
     * Constructs SelectLimitInnerInterceptor with custom limit and enforceMaxLimit flag.
     *
     * @param defaultLimit    Default limit value
     * @param enforceMaxLimit Whether to cap large LIMIT values
     */
    public SelectLimitInnerInterceptor(long defaultLimit, boolean enforceMaxLimit) {
        this(defaultLimit, new MySQLDialect(), enforceMaxLimit);
    }

    /**
     * Constructs SelectLimitInnerInterceptor with custom limit and dialect.
     *
     * @param defaultLimit Default limit value
     * @param dialect      Database dialect
     */
    public SelectLimitInnerInterceptor(long defaultLimit, SqlGuardDialect dialect) {
        this(defaultLimit, dialect, false);
    }

    /**
     * Constructs SelectLimitInnerInterceptor with all options.
     *
     * @param defaultLimit    Default limit value (also used as max limit when enforceMaxLimit=true)
     * @param dialect         Database dialect
     * @param enforceMaxLimit Whether to cap large LIMIT values
     */
    public SelectLimitInnerInterceptor(long defaultLimit, SqlGuardDialect dialect, boolean enforceMaxLimit) {
        if (defaultLimit <= 0) {
            throw new IllegalArgumentException("Default limit must be positive");
        }
        if (dialect == null) {
            throw new IllegalArgumentException("Dialect cannot be null");
        }
        this.defaultLimit = defaultLimit;
        this.dialect = dialect;
        this.enforceMaxLimit = enforceMaxLimit;
    }

    /**
     * Returns priority 150 (fallback interceptor).
     *
     * <p>Priority positioning:
     * <ul>
     *   <li>10: SqlGuardCheckInnerInterceptor (check)</li>
     *   <li><b>150: SelectLimitInnerInterceptor (fallback)</b></li>
     *   <li>200: SqlGuardRewriteInnerInterceptor (rewrite)</li>
     * </ul>
     *
     * @return Priority value 150
     */
    @Override
    public int getPriority() {
        return 150;
    }

    /**
     * Pre-check method for query execution (no-op for fallback interceptors).
     *
     * @return {@code true} to continue chain
     */
    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter,
                               RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        return true; // Continue chain
    }

    /**
     * Modifies SELECT statements to add or cap LIMIT.
     *
     * <p>Processing flow:
     * <ol>
     *   <li>Retrieve cached Statement from StatementContext</li>
     *   <li>Check if statement is SELECT</li>
     *   <li>If enforceMaxLimit=true, check and cap existing large LIMIT values</li>
     *   <li>If no pagination exists, add LIMIT using dialect</li>
     *   <li>Update BoundSql with modified SQL if changes were made</li>
     * </ol>
     *
     * @param executor      MyBatis Executor
     * @param ms            MappedStatement
     * @param parameter     SQL parameter
     * @param rowBounds     Pagination bounds
     * @param resultHandler Result handler
     * @param boundSql      BoundSql containing SQL
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        String sql = boundSql.getSql();

        // 1. Get Statement from ThreadLocal cache
        Statement stmt = StatementContext.get(sql);

        if (stmt == null) {
            log.trace("StatementContext cache miss, skipping LIMIT processing for SQL: {}", 
                    getSqlSnippet(sql));
            return; // No cached Statement, cannot process
        }

        if (!(stmt instanceof Select)) {
            return; // Not a SELECT, skip
        }

        Select select = (Select) stmt;
        boolean modified = false;

        // 2. Check if enforceMaxLimit is enabled and existing LIMIT exceeds max
        if (enforceMaxLimit) {
            modified = capExistingLimit(select);
        }

        // 3. Check if pagination already exists
        if (!hasPagination(select, rowBounds)) {
            // No pagination detected, add LIMIT
            log.info("Adding LIMIT {} to SELECT statement (no pagination detected): {}", 
                    defaultLimit, getSqlSnippet(sql));
            dialect.applyLimit(select, defaultLimit);
            modified = true;
        } else if (!modified) {
            log.debug("Pagination already exists, skipping LIMIT addition for SQL: {}", 
                    getSqlSnippet(sql));
        }

        // 4. Update BoundSql with modified SQL if changes were made
        if (modified) {
            String modifiedSql = select.toString();
            PluginUtils.MPBoundSql mpBoundSql = PluginUtils.mpBoundSql(boundSql);
            mpBoundSql.sql(modifiedSql);
            log.debug("Modified SQL: {}", getSqlSnippet(modifiedSql));
        }
    }

    /**
     * Caps existing LIMIT/TOP values that exceed defaultLimit.
     *
     * @param select SELECT statement to check
     * @return true if LIMIT was capped, false otherwise
     */
    private boolean capExistingLimit(Select select) {
        SelectBody selectBody = select.getSelectBody();
        if (!(selectBody instanceof PlainSelect)) {
            return false;
        }

        PlainSelect plainSelect = (PlainSelect) selectBody;
        boolean modified = false;

        // Check and cap LIMIT clause
        Limit limit = plainSelect.getLimit();
        if (limit != null) {
            Expression rowCount = limit.getRowCount();
            if (rowCount instanceof LongValue) {
                long value = ((LongValue) rowCount).getValue();
                if (value > defaultLimit) {
                    log.warn("Capping LIMIT {} to {} for safety: {}", 
                            value, defaultLimit, getSqlSnippet(select.toString()));
                    limit.setRowCount(new LongValue(defaultLimit));
                    modified = true;
                }
            }
        }

        // Check and cap TOP clause (SQL Server)
        Top top = plainSelect.getTop();
        if (top != null) {
            Expression expression = top.getExpression();
            if (expression instanceof LongValue) {
                long value = ((LongValue) expression).getValue();
                if (value > defaultLimit) {
                    log.warn("Capping TOP {} to {} for safety: {}", 
                            value, defaultLimit, getSqlSnippet(select.toString()));
                    top.setExpression(new LongValue(defaultLimit));
                    modified = true;
                }
            }
        }

        return modified;
    }

    /**
     * Pre-check method for update execution (no-op for SELECT-only interceptor).
     *
     * @return {@code true} to continue chain
     */
    @Override
    public boolean willDoUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        return true;
    }

    /**
     * beforeUpdate is no-op (SELECT-only interceptor).
     */
    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        // No-op for SELECT-only interceptor
    }

    /**
     * Checks if pagination already exists in SELECT statement or RowBounds.
     *
     * @param select    SELECT statement
     * @param rowBounds MyBatis RowBounds
     * @return {@code true} if pagination exists, {@code false} otherwise
     */
    private boolean hasPagination(Select select, RowBounds rowBounds) {
        // Check RowBounds
        if (rowBounds != null && rowBounds != RowBounds.DEFAULT) {
            log.trace("RowBounds pagination detected");
            return true;
        }

        // Check LIMIT/OFFSET/TOP in Statement
        SelectBody selectBody = select.getSelectBody();
        if (selectBody instanceof PlainSelect) {
            PlainSelect plainSelect = (PlainSelect) selectBody;

            if (plainSelect.getLimit() != null) {
                log.trace("LIMIT clause detected in SQL");
                return true;
            }

            if (plainSelect.getOffset() != null) {
                log.trace("OFFSET clause detected in SQL");
                return true;
            }

            if (plainSelect.getTop() != null) {
                log.trace("TOP clause detected in SQL");
                return true;
            }
        }

        return false; // No pagination detected
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

    /**
     * Returns the configured default limit value.
     *
     * @return Default limit value
     */
    public long getDefaultLimit() {
        return defaultLimit;
    }

    /**
     * Returns the configured dialect.
     *
     * @return Database dialect
     */
    public SqlGuardDialect getDialect() {
        return dialect;
    }

    /**
     * Returns whether enforceMaxLimit is enabled.
     *
     * @return true if large LIMIT values will be capped
     */
    public boolean isEnforceMaxLimit() {
        return enforceMaxLimit;
    }
}
