package com.footstone.sqlguard.interceptor.inner.impl;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor;
import com.footstone.sqlguard.parser.StatementContext;
import com.footstone.sqlguard.rewriter.StatementRewriter;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * InnerInterceptor for executing StatementRewriter chain with chain-rewrite support.
 *
 * <h2>Purpose</h2>
 * <p>Bridges {@link StatementRewriter} implementations with the InnerInterceptor chain,
 * allowing custom SQL modifications such as tenant isolation, soft-delete filtering,
 * and column masking.
 *
 * <h2>Priority</h2>
 * <p>Priority: <b>200</b> (rewrite interceptor) - runs AFTER check (10) and fallback (150)
 * interceptors. This ensures SQL safety checks and LIMIT fallbacks are applied before
 * custom rewrites.
 *
 * <h2>Chain Rewrite Support</h2>
 * <p>Multiple rewriters are executed sequentially, with each rewriter receiving the
 * Statement modified by previous rewriters:
 * <pre>
 * Original SQL
 *     |
 *     v
 * Rewriter 1 (add tenant filter)
 *     |
 *     v
 * Rewriter 2 (add soft-delete filter)
 *     |
 *     v
 * Rewriter 3 (force ORDER BY)
 *     |
 *     v
 * Final SQL
 * </pre>
 *
 * <p>After each rewrite, updates:
 * <ul>
 *   <li>{@code SqlContext.statement} for next rewriter</li>
 *   <li>{@code StatementContext.cache()} ThreadLocal cache</li>
 *   <li>{@code BoundSql.sql} field via reflection</li>
 * </ul>
 *
 * <h2>BoundSql Modification via Reflection</h2>
 * <p>MyBatis {@code BoundSql.sql} field is final, requiring reflection:
 * <pre>{@code
 * Field sqlField = BoundSql.class.getDeclaredField("sql");
 * sqlField.setAccessible(true);
 * sqlField.set(boundSql, newSql);
 * }</pre>
 *
 * @see StatementRewriter
 * @see StatementContext
 * @since 1.1.0
 */
public class SqlGuardRewriteInnerInterceptor implements SqlGuardInnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlGuardRewriteInnerInterceptor.class);

    /**
     * StatementRewriter instances (injected via constructor).
     */
    private final List<StatementRewriter> rewriters;

    /**
     * Cached reflection Field for BoundSql.sql (performance optimization).
     */
    private static volatile Field boundSqlSqlField;

    /**
     * Lock for thread-safe initialization of boundSqlSqlField.
     */
    private static final Object FIELD_INIT_LOCK = new Object();

    /**
     * Constructs SqlGuardRewriteInnerInterceptor with StatementRewriter list.
     *
     * @param rewriters List of StatementRewriter instances
     */
    public SqlGuardRewriteInnerInterceptor(List<StatementRewriter> rewriters) {
        this.rewriters = rewriters;
        // Initialize reflection field lazily on first use
        initBoundSqlSqlField();
    }

    /**
     * Initializes the BoundSql.sql field via reflection (thread-safe lazy initialization).
     */
    private static void initBoundSqlSqlField() {
        if (boundSqlSqlField == null) {
            synchronized (FIELD_INIT_LOCK) {
                if (boundSqlSqlField == null) {
                    try {
                        Field field = BoundSql.class.getDeclaredField("sql");
                        field.setAccessible(true);
                        boundSqlSqlField = field;
                        log.debug("BoundSql.sql field reflection initialized successfully");
                    } catch (NoSuchFieldException e) {
                        log.error("Failed to access BoundSql.sql field via reflection", e);
                    }
                }
            }
        }
    }

    /**
     * Returns priority 200 (rewrite interceptor runs after checks and fallbacks).
     *
     * @return Priority value 200
     */
    @Override
    public int getPriority() {
        return 200;
    }

    /**
     * willDoQuery is no-op for rewrite interceptors (no pre-filtering).
     *
     * @return {@code true} to continue chain
     */
    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter,
                               RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        return true; // Rewriters don't pre-filter
    }

    /**
     * willDoUpdate is no-op for rewrite interceptors.
     *
     * @return {@code true} to continue chain
     */
    @Override
    public boolean willDoUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        return true;
    }

    /**
     * Executes StatementRewriter chain and updates BoundSql if Statement is modified.
     *
     * <p>Workflow:
     * <ol>
     *   <li>Get Statement from {@code StatementContext.get(sql)} (reuse CheckInnerInterceptor's parse)</li>
     *   <li>Build SqlContext with statement</li>
     *   <li>Iterate all enabled StatementRewriters:
     *     <ul>
     *       <li>Call {@code rewriter.rewrite(statement, context)}</li>
     *       <li>If Statement changed: update SqlContext, StatementContext, BoundSql</li>
     *     </ul>
     *   </li>
     *   <li>Replace SQL in BoundSql via reflection</li>
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
        executeRewrites(ms, boundSql);
    }

    /**
     * Executes StatementRewriter chain for update operations.
     *
     * @param executor  MyBatis Executor
     * @param ms        MappedStatement
     * @param parameter SQL parameter
     */
    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);
        executeRewrites(ms, boundSql);
    }

    /**
     * Executes all enabled StatementRewriters and updates BoundSql.
     *
     * @param ms       MappedStatement
     * @param boundSql BoundSql to modify
     */
    private void executeRewrites(MappedStatement ms, BoundSql boundSql) throws SQLException {
        String originalSql = boundSql.getSql();

        // 1. Get Statement from ThreadLocal cache
        Statement currentStatement = StatementContext.get(originalSql);

        if (currentStatement == null) {
            log.trace("StatementContext cache miss for SQL: {}, skipping rewrites", originalSql);
            return; // No cached Statement, cannot rewrite
        }

        // 2. Build initial SqlContext
        SqlContext context = SqlContext.builder()
                .sql(originalSql)
                .statement(currentStatement)
                .mapperId(ms.getId())
                .type(determineSqlCommandType(currentStatement))
                .build();

        // 3. Execute rewrite chain
        List<StatementRewriter> enabledRewriters = rewriters.stream()
                .filter(StatementRewriter::isEnabled)
                .collect(Collectors.toList());

        if (enabledRewriters.isEmpty()) {
            log.trace("No enabled StatementRewriters, skipping rewrites");
            return;
        }

        boolean modified = false;
        String currentSql = originalSql;

        for (StatementRewriter rewriter : enabledRewriters) {
            log.trace("Executing StatementRewriter: {}", rewriter.getClass().getSimpleName());

            // Capture SQL before rewrite for comparison
            String sqlBeforeRewrite = currentStatement.toString();

            Statement newStatement = rewriter.rewrite(currentStatement, context);

            // Compare SQL strings to detect modification (handles in-place mutations)
            String sqlAfterRewrite = newStatement.toString();
            boolean statementModified = !sqlBeforeRewrite.equals(sqlAfterRewrite);

            if (statementModified) {
                // Statement was modified
                log.debug("Statement modified by {}", rewriter.getClass().getSimpleName());

                currentStatement = newStatement;
                String newSql = sqlAfterRewrite;

                // Update SqlContext for next rewriter
                context = SqlContext.builder()
                        .sql(newSql)
                        .statement(currentStatement)
                        .mapperId(ms.getId())
                        .type(determineSqlCommandType(currentStatement))
                        .build();

                // Update ThreadLocal cache for next rewriter
                StatementContext.cache(newSql, currentStatement);

                currentSql = newSql;
                modified = true;
            }
        }

        // 4. Replace SQL in BoundSql if modified
        if (modified) {
            replaceBoundSqlSql(boundSql, currentSql);
            log.info("SQL rewritten: {} -> {}", originalSql, currentSql);
        }
    }

    /**
     * Determines the SQL command type from the parsed Statement.
     *
     * @param stmt Parsed SQL statement
     * @return SqlCommandType corresponding to the statement type
     */
    private SqlCommandType determineSqlCommandType(Statement stmt) {
        if (stmt instanceof Select) {
            return SqlCommandType.SELECT;
        } else if (stmt instanceof Update) {
            return SqlCommandType.UPDATE;
        } else if (stmt instanceof Delete) {
            return SqlCommandType.DELETE;
        } else if (stmt instanceof Insert) {
            return SqlCommandType.INSERT;
        }
        return SqlCommandType.UNKNOWN;
    }

    /**
     * Replaces SQL in BoundSql using reflection.
     *
     * <p>MyBatis {@code BoundSql.sql} field is final, requiring reflection to modify.
     *
     * @param boundSql BoundSql instance
     * @param newSql   New SQL string
     * @throws SQLException If reflection fails
     */
    private void replaceBoundSqlSql(BoundSql boundSql, String newSql) throws SQLException {
        try {
            if (boundSqlSqlField == null) {
                throw new SQLException("BoundSql.sql field reflection not initialized");
            }

            boundSqlSqlField.set(boundSql, newSql);
        } catch (IllegalAccessException e) {
            log.error("Failed to replace SQL in BoundSql via reflection", e);
            throw new SQLException("Failed to replace SQL in BoundSql", e);
        }
    }
}

