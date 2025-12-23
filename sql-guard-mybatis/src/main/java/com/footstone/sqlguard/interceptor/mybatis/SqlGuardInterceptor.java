package com.footstone.sqlguard.interceptor.mybatis;

import com.footstone.sqlguard.compat.mybatis.SqlExtractor;
import com.footstone.sqlguard.compat.mybatis.SqlExtractorFactory;
import com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.StatementContext;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Main MyBatis Interceptor orchestrating InnerInterceptor chain with priority-based execution.
 *
 * <h2>Purpose</h2>
 * <p>Manages List of {@link SqlGuardInnerInterceptor} instances, sorting by priority and invoking
 * lifecycle methods (willDoXxx → beforeXxx) for each SQL operation. Parses SQL once and caches
 * Statement in ThreadLocal for reuse across all InnerInterceptors.
 *
 * <h2>Interceptor Chain Flow</h2>
 * <pre>
 * MyBatis Executor.query/update
 *     ↓
 * SqlGuardInterceptor.intercept()
 *     ├─ Parse SQL → Statement
 *     ├─ Cache: StatementContext.cache(sql, statement)
 *     ├─ Sort InnerInterceptors by priority (10 → 100 → 200)
 *     ├─ Phase 1: Invoke willDoXxx() chain (stop if any false)
 *     ├─ Phase 2: Invoke beforeXxx() chain (modify BoundSql)
 *     ├─ Execute: invocation.proceed()
 *     └─ Finally: StatementContext.clear() (CRITICAL)
 * </pre>
 *
 * <h2>Priority Mechanism</h2>
 * <ul>
 *   <li><b>1-99:</b> Check interceptors (e.g., SqlGuardCheckInnerInterceptor = 10)</li>
 *   <li><b>100-199:</b> Fallback interceptors (e.g., SelectLimitInnerInterceptor = 100)</li>
 *   <li><b>200+:</b> Rewrite interceptors (e.g., SqlGuardRewriteInnerInterceptor = 200)</li>
 * </ul>
 * Lower priority numbers execute first.
 *
 * <h2>Short-Circuit Mechanism</h2>
 * <p>If any InnerInterceptor's {@code willDoXxx()} returns {@code false}, the chain stops
 * immediately and {@code invocation.proceed()} is skipped. This allows check interceptors
 * to block unsafe SQL operations.
 *
 * <h2>ThreadLocal Memory Leak Prevention</h2>
 * <p><b>CRITICAL:</b> {@link StatementContext#clear()} MUST be called in finally block.
 * Failure to clear ThreadLocal causes memory leaks in thread pool environments.
 *
 * <h2>Spring Integration</h2>
 * <pre>{@code
 * @Bean
 * public SqlGuardInterceptor sqlGuardInterceptor(
 *         List<SqlGuardInnerInterceptor> innerInterceptors,
 *         JSqlParserFacade parserFacade) {
 *     return new SqlGuardInterceptor(innerInterceptors, parserFacade);
 * }
 * }</pre>
 *
 * @see SqlGuardInnerInterceptor
 * @see StatementContext
 * @since 1.1.0
 */
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
    ),
    @Signature(
        type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class}
    )
})
public class SqlGuardInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlGuardInterceptor.class);

    /**
     * InnerInterceptor chain sorted by priority (ascending).
     */
    private final List<SqlGuardInnerInterceptor> sortedInterceptors;

    /**
     * JSqlParser facade for parsing SQL into Statement.
     */
    private final JSqlParserFacade parserFacade;

    /**
     * SqlExtractor for version-compatible SQL extraction from BoundSql.
     *
     * <p>Automatically selects appropriate implementation based on MyBatis version:
     * <ul>
     *   <li>MyBatis 3.4.x: Uses LegacySqlExtractor</li>
     *   <li>MyBatis 3.5.x: Uses ModernSqlExtractor</li>
     * </ul>
     */
    private final SqlExtractor sqlExtractor;

    /**
     * Constructs SqlGuardInterceptor with InnerInterceptor list and parser facade.
     *
     * <p>InnerInterceptors are automatically sorted by priority (ascending order).
     * SqlExtractor is automatically selected based on detected MyBatis version.
     *
     * @param innerInterceptors List of InnerInterceptor instances (unsorted)
     * @param parserFacade      JSqlParser facade for SQL parsing
     */
    public SqlGuardInterceptor(List<SqlGuardInnerInterceptor> innerInterceptors,
                                JSqlParserFacade parserFacade) {
        this.parserFacade = parserFacade;
        this.sqlExtractor = SqlExtractorFactory.create();

        // Sort by priority (ascending: 10 → 100 → 200)
        this.sortedInterceptors = innerInterceptors.stream()
                .sorted(Comparator.comparingInt(SqlGuardInnerInterceptor::getPriority))
                .collect(Collectors.toList());

        log.info("SqlGuardInterceptor initialized with {} InnerInterceptors (MyBatis {})",
                sortedInterceptors.size(), SqlExtractorFactory.getDetectedVersion());
        for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
            log.info("  - {} (priority: {})",
                    interceptor.getClass().getSimpleName(),
                    interceptor.getPriority());
        }
    }

    /**
     * Intercepts Executor.query/update methods and orchestrates InnerInterceptor chain.
     *
     * <p>Workflow:
     * <ol>
     *   <li>Extract SQL from BoundSql</li>
     *   <li>Parse SQL into Statement using JSqlParserFacade</li>
     *   <li>Cache Statement in ThreadLocal: {@code StatementContext.cache(sql, statement)}</li>
     *   <li>Invoke InnerInterceptor chain:
     *     <ul>
     *       <li>Phase 1: willDoXxx() - pre-check (stop if any false)</li>
     *       <li>Phase 2: beforeXxx() - modification</li>
     *     </ul>
     *   </li>
     *   <li>Execute original method: {@code invocation.proceed()}</li>
     *   <li>Finally: {@code StatementContext.clear()} (CRITICAL for memory leak prevention)</li>
     * </ol>
     *
     * @param invocation MyBatis Invocation wrapping Executor method call
     * @return Result from original Executor method or short-circuit result
     * @throws Throwable If SQL parsing fails, InnerInterceptor throws exception, or execution fails
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            // 1. Extract method arguments
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args[1];

            // 2. Extract SQL from BoundSql using version-compatible SqlExtractor
            BoundSql boundSql = ms.getBoundSql(parameter);
            String sql = sqlExtractor.extractSql(ms, parameter, boundSql);

            log.debug("Intercepting SQL: {} (Statement ID: {})", sql, ms.getId());

            // 3. Parse SQL and cache to ThreadLocal
            Statement statement = parserFacade.parse(sql);
            StatementContext.cache(sql, statement);
            log.trace("Cached Statement in ThreadLocal for SQL: {}", sql);

            // 4. Determine operation type (query vs update)
            String methodName = invocation.getMethod().getName();
            boolean isQuery = "query".equals(methodName);

            // 5. Invoke InnerInterceptor chain
            if (isQuery) {
                RowBounds rowBounds = (RowBounds) args[2];
                ResultHandler<?> resultHandler = (ResultHandler<?>) args[3];

                if (!invokeWillDoQuery(ms, parameter, rowBounds, resultHandler, boundSql)) {
                    // Short-circuit: willDoQuery returned false
                    log.debug("InnerInterceptor chain short-circuited for query");
                    return null; // Skip execution
                }

                invokeBeforeQuery(ms, parameter, rowBounds, resultHandler, boundSql);
            } else {
                if (!invokeWillDoUpdate(ms, parameter)) {
                    // Short-circuit: willDoUpdate returned false
                    log.debug("InnerInterceptor chain short-circuited for update");
                    return 0; // Skip execution (return 0 rows affected)
                }

                invokeBeforeUpdate(ms, parameter);
            }

            // 6. Execute original method
            log.trace("Proceeding with original Executor method");
            return invocation.proceed();

        } finally {
            // 7. CRITICAL: Cleanup ThreadLocal to prevent memory leaks
            StatementContext.clear();
            log.trace("Cleared StatementContext ThreadLocal");
        }
    }

    /**
     * Invokes willDoQuery() lifecycle method on all InnerInterceptors.
     *
     * <p>Stops chain if any interceptor returns {@code false} (short-circuit).
     *
     * @return {@code true} if all interceptors returned true, {@code false} to stop chain
     */
    private boolean invokeWillDoQuery(MappedStatement ms, Object parameter,
                                       RowBounds rowBounds, ResultHandler<?> resultHandler,
                                       BoundSql boundSql) throws Throwable {
        Executor executor = null; // MyBatis Executor not needed for phase 13

        for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
            log.trace("Invoking willDoQuery on {}", interceptor.getClass().getSimpleName());
            boolean shouldContinue = interceptor.willDoQuery(executor, ms, parameter,
                                                             rowBounds, resultHandler, boundSql);
            if (!shouldContinue) {
                log.debug("InnerInterceptor {} returned false from willDoQuery, stopping chain",
                         interceptor.getClass().getSimpleName());
                return false; // Short-circuit
            }
        }

        return true; // Continue to beforeQuery phase
    }

    /**
     * Invokes beforeQuery() lifecycle method on all InnerInterceptors.
     *
     * <p>Allows interceptors to modify BoundSql before query execution.
     */
    private void invokeBeforeQuery(MappedStatement ms, Object parameter,
                                     RowBounds rowBounds, ResultHandler<?> resultHandler,
                                     BoundSql boundSql) throws Throwable {
        Executor executor = null;

        for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
            log.trace("Invoking beforeQuery on {}", interceptor.getClass().getSimpleName());
            interceptor.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        }
    }

    /**
     * Invokes willDoUpdate() lifecycle method on all InnerInterceptors.
     *
     * @return {@code true} if all interceptors returned true, {@code false} to stop chain
     */
    private boolean invokeWillDoUpdate(MappedStatement ms, Object parameter) throws Throwable {
        Executor executor = null;

        for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
            log.trace("Invoking willDoUpdate on {}", interceptor.getClass().getSimpleName());
            boolean shouldContinue = interceptor.willDoUpdate(executor, ms, parameter);
            if (!shouldContinue) {
                log.debug("InnerInterceptor {} returned false from willDoUpdate, stopping chain",
                         interceptor.getClass().getSimpleName());
                return false; // Short-circuit
            }
        }

        return true; // Continue to beforeUpdate phase
    }

    /**
     * Invokes beforeUpdate() lifecycle method on all InnerInterceptors.
     */
    private void invokeBeforeUpdate(MappedStatement ms, Object parameter) throws Throwable {
        Executor executor = null;

        for (SqlGuardInnerInterceptor interceptor : sortedInterceptors) {
            log.trace("Invoking beforeUpdate on {}", interceptor.getClass().getSimpleName());
            interceptor.beforeUpdate(executor, ms, parameter);
        }
    }

    /**
     * Wraps target Executor for plugin chain.
     *
     * @param target Target Executor instance
     * @return Wrapped proxy
     */
    @Override
    public Object plugin(Object target) {
        if (target instanceof Executor) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    /**
     * Sets properties (currently unused).
     *
     * @param properties Configuration properties
     */
    @Override
    public void setProperties(Properties properties) {
        // No properties needed
    }

    /**
     * Returns sorted InnerInterceptor list (for testing).
     *
     * @return Sorted InnerInterceptor list
     */
    List<SqlGuardInnerInterceptor> getSortedInterceptors() {
        return sortedInterceptors;
    }
}

