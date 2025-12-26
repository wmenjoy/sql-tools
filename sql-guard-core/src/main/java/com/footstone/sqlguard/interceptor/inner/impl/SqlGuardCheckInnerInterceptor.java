package com.footstone.sqlguard.interceptor.inner.impl;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.config.ViolationStrategy;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.interceptor.inner.SqlGuardInnerInterceptor;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.StatementContext;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import net.sf.jsqlparser.statement.Statement;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * InnerInterceptor implementation bridging RuleChecker system with MyBatis-Plus InnerInterceptor pattern.
 *
 * <h2>Purpose</h2>
 * <p>Executes SQL safety checks from Phase 12's RuleChecker system within the InnerInterceptor chain.
 * Reuses parsed Statement from StatementContext (cache hit) or parses and caches (cache miss).
 *
 * <h2>Priority</h2>
 * <p>Priority: <b>10</b> (high priority) - Check interceptors run BEFORE fallback and rewrite interceptors.
 * If violations are detected with {@code ViolationStrategy.BLOCK}, throws SQLException preventing
 * downstream interceptors from executing.
 *
 * <h2>Workflow</h2>
 * <pre>
 * 1. Extract SQL from BoundSql
 * 2. Attempt StatementContext.get(sql) - reuse if cached
 * 3. If cache miss: parse using JSqlParserFacade and cache via StatementContext.cache()
 * 4. Build SqlContext with statement field
 * 5. Execute all enabled RuleCheckers: checker.check(context, result)
 * 6. Handle violations based on ViolationStrategy:
 *    - BLOCK: throw SQLException wrapping violations
 *    - WARN: log.warn(violations)
 *    - LOG: log.info(violations)
 * 7. Return true to continue interceptor chain (or throw if BLOCK)
 * </pre>
 *
 * <h2>RuleChecker Integration</h2>
 * <p>Bridges Phase 12 RuleChecker system:
 * <ul>
 *   <li>{@code NoWhereClauseChecker}</li>
 *   <li>{@code BlacklistFieldChecker}</li>
 *   <li>{@code WhitelistFieldChecker}</li>
 *   <li>{@code LogicalPaginationChecker}</li>
 *   <li>{@code NoConditionPaginationChecker}</li>
 *   <li>{@code DeepPaginationChecker}</li>
 *   <li>{@code NoPaginationChecker}</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>Uses ThreadLocal-based {@link StatementContext} ensuring thread isolation.
 * Multiple concurrent requests do not interfere with each other.
 *
 * @see SqlGuardInnerInterceptor
 * @see RuleChecker
 * @see StatementContext
 * @since 1.1.0
 */
public class SqlGuardCheckInnerInterceptor implements SqlGuardInnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SqlGuardCheckInnerInterceptor.class);

    /**
     * Phase 12 RuleChecker instances (injected via constructor).
     */
    private final List<RuleChecker> ruleCheckers;

    /**
     * Configuration containing ViolationStrategy and checker enable/disable flags.
     */
    private final SqlGuardConfig config;

    /**
     * JSqlParser facade for parsing SQL into Statement.
     */
    private final JSqlParserFacade parserFacade;

    /**
     * Constructs SqlGuardCheckInnerInterceptor with RuleChecker list and configuration.
     *
     * @param ruleCheckers List of RuleChecker instances from Phase 12
     * @param config       SqlGuardConfig containing ViolationStrategy
     * @param parserFacade JSqlParser facade for SQL parsing
     */
    public SqlGuardCheckInnerInterceptor(List<RuleChecker> ruleCheckers,
                                          SqlGuardConfig config,
                                          JSqlParserFacade parserFacade) {
        this.ruleCheckers = ruleCheckers;
        this.config = config;
        this.parserFacade = parserFacade;
    }

    /**
     * Returns priority 10 (high priority for check interceptors).
     *
     * <p>Check interceptors run BEFORE fallback (100) and rewrite (200) interceptors.
     *
     * @return Priority value 10
     */
    @Override
    public int getPriority() {
        return 10;
    }

    /**
     * Pre-check method for query execution.
     *
     * <p>Workflow:
     * <ol>
     *   <li>Extract SQL from BoundSql</li>
     *   <li>Attempt cache retrieval: {@code StatementContext.get(sql)}</li>
     *   <li>If cache miss: parse and cache {@code JSqlParserFacade.parse(sql)}</li>
     *   <li>Build SqlContext with statement field</li>
     *   <li>Execute all enabled RuleCheckers</li>
     *   <li>Handle violations based on ViolationStrategy</li>
     * </ol>
     *
     * @param executor      MyBatis Executor
     * @param ms            MappedStatement containing SQL metadata
     * @param parameter     SQL parameter object
     * @param rowBounds     Pagination bounds
     * @param resultHandler Result handler
     * @param boundSql      BoundSql containing SQL string
     * @return {@code true} to continue chain (if no BLOCK violations)
     * @throws SQLException If ViolationStrategy.BLOCK and violations detected
     */
    @Override
    public boolean willDoQuery(Executor executor, MappedStatement ms, Object parameter,
                                RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        return executeChecks(boundSql, ms.getId());
    }

    /**
     * Pre-check method for update/insert/delete execution.
     *
     * <p>Similar workflow to {@link #willDoQuery} but for DML operations.
     *
     * @param executor  MyBatis Executor
     * @param ms        MappedStatement containing SQL metadata
     * @param parameter SQL parameter object
     * @return {@code true} to continue chain (if no BLOCK violations)
     * @throws SQLException If ViolationStrategy.BLOCK and violations detected
     */
    @Override
    public boolean willDoUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        BoundSql boundSql = ms.getBoundSql(parameter);
        return executeChecks(boundSql, ms.getId());
    }

    /**
     * beforeQuery is no-op for check interceptors (checks don't modify SQL).
     */
    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                             RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        // Check interceptors don't modify SQL
    }

    /**
     * beforeUpdate is no-op for check interceptors (checks don't modify SQL).
     */
    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
            throws SQLException {
        // Check interceptors don't modify SQL
    }

    /**
     * Executes all enabled RuleCheckers and handles violations.
     *
     * @param boundSql      BoundSql containing SQL string
     * @param statementId   MyBatis statement ID (e.g., "UserMapper.selectAll")
     * @return {@code true} to continue chain
     * @throws SQLException If ViolationStrategy.BLOCK and violations detected
     */
    private boolean executeChecks(BoundSql boundSql, String statementId) throws SQLException {
        String sql = boundSql.getSql();

        // 1. Attempt cache retrieval
        Statement stmt = StatementContext.get(sql);

        if (stmt == null) {
            // 2. Cache miss - parse and cache
            log.debug("StatementContext cache miss for SQL: {}", sql);
            stmt = parserFacade.parse(sql);
            StatementContext.cache(sql, stmt);
        } else {
            log.debug("StatementContext cache hit for SQL: {}", sql);
        }

        // 3. Build SqlContext with statement field
        SqlContext context = SqlContext.builder()
                .sql(sql)
                .statement(stmt)
                .statementId(statementId)
                .type(determineSqlCommandType(stmt))
                .executionLayer(ExecutionLayer.MYBATIS)
                .build();

        // 4. Prepare ValidationResult
        ValidationResult result = ValidationResult.pass();

        // 5. Execute all enabled RuleCheckers
        List<RuleChecker> enabledCheckers = ruleCheckers.stream()
                .filter(RuleChecker::isEnabled)
                .collect(Collectors.toList());

        for (RuleChecker checker : enabledCheckers) {
            log.trace("Executing RuleChecker: {}", checker.getClass().getSimpleName());
            checker.check(context, result);
        }

        // 6. Handle violations based on ViolationStrategy
        if (!result.isPassed()) {
            handleViolations(result);
        }

        // 7. Continue chain if no BLOCK violations thrown
        return true;
    }

    /**
     * Determines the SQL command type from the parsed Statement.
     *
     * @param stmt Parsed SQL statement
     * @return SqlCommandType corresponding to the statement type
     */
    private SqlCommandType determineSqlCommandType(Statement stmt) {
        if (stmt instanceof net.sf.jsqlparser.statement.select.Select) {
            return SqlCommandType.SELECT;
        } else if (stmt instanceof net.sf.jsqlparser.statement.update.Update) {
            return SqlCommandType.UPDATE;
        } else if (stmt instanceof net.sf.jsqlparser.statement.delete.Delete) {
            return SqlCommandType.DELETE;
        } else if (stmt instanceof net.sf.jsqlparser.statement.insert.Insert) {
            return SqlCommandType.INSERT;
        }
        return SqlCommandType.UNKNOWN;
    }

    /**
     * Handles violations based on configured ViolationStrategy.
     *
     * @param result ValidationResult containing violations
     * @throws SQLException If ViolationStrategy.BLOCK
     */
    private void handleViolations(ValidationResult result) throws SQLException {
        ViolationStrategy strategy = config.getViolationStrategy();

        switch (strategy) {
            case BLOCK:
                // Throw SQLException wrapping violations
                String errorMsg = buildViolationMessage(result);
                log.error("SQL safety violations detected (BLOCK): {}", errorMsg);
                throw new SQLException("SQL safety violations detected: " + errorMsg);

            case WARN:
                // Log as warning
                log.warn("SQL safety violations detected (WARN): {}", buildViolationMessage(result));
                break;

            case LOG:
                // Log as info
                log.info("SQL safety violations detected (LOG): {}", buildViolationMessage(result));
                break;

            default:
                // Unknown strategy - log as warning
                log.warn("Unknown ViolationStrategy: {}, treating as LOG", strategy);
                log.info("SQL safety violations detected: {}", buildViolationMessage(result));
        }
    }

    /**
     * Builds violation message from ValidationResult.
     *
     * @param result ValidationResult containing violations
     * @return Formatted violation message
     */
    private String buildViolationMessage(ValidationResult result) {
        return result.getViolations().stream()
                .map(v -> String.format("[%s] %s", v.getRiskLevel(), v.getMessage()))
                .collect(Collectors.joining("; "));
    }
}

