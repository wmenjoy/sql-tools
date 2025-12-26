package com.footstone.sqlguard.interceptor.mp;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import java.sql.SQLException;
import java.util.Map;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MyBatis-Plus InnerInterceptor for runtime SQL safety validation.
 *
 * <p>MpSqlSafetyInnerInterceptor integrates with MyBatis-Plus interceptor chain to validate
 * SQL statements at runtime. It detects IPage pagination parameters, validates QueryWrapper
 * generated SQL, and coordinates with PaginationInnerInterceptor without conflicts.</p>
 *
 * <p><strong>Key Responsibilities:</strong></p>
 * <ul>
 *   <li>Detect IPage pagination (MyBatis-Plus pagination framework)</li>
 *   <li>Validate QueryWrapper/LambdaQueryWrapper generated SQL</li>
 *   <li>Coordinate with PaginationInnerInterceptor (both must work together)</li>
 *   <li>Use deduplication filter to prevent double-checking with MyBatis interceptor</li>
 * </ul>
 *
 * <p><strong>Interceptor Chain Ordering:</strong></p>
 * <pre>{@code
 * MybatisPlusInterceptor mpInterceptor = new MybatisPlusInterceptor();
 * // 1. PaginationInnerInterceptor adds LIMIT clause
 * mpInterceptor.addInnerInterceptor(new PaginationInnerInterceptor());
 * // 2. MpSqlSafetyInnerInterceptor validates final SQL (with LIMIT)
 * mpInterceptor.addInnerInterceptor(new MpSqlSafetyInnerInterceptor(validator));
 * }</pre>
 *
 * <p><strong>QueryWrapper Runtime Validation:</strong></p>
 * <p>Static scanner (Task 3.4) only marks QueryWrapper usage locations. Runtime validation
 * catches actual generated SQL from fluent API:</p>
 * <pre>{@code
 * // Empty wrapper (dangerous - no WHERE)
 * QueryWrapper<User> wrapper = new QueryWrapper<>();
 * // Generates: SELECT * FROM user → VIOLATION
 *
 * // Wrapper with conditions (safe)
 * QueryWrapper<User> wrapper = new QueryWrapper<>();
 * wrapper.eq("id", 123);
 * // Generates: SELECT * FROM user WHERE id = 123 → PASS
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. All fields are final
 * and the deduplication filter uses ThreadLocal caching.</p>
 *
 * @see InnerInterceptor
 * @see DefaultSqlSafetyValidator
 * @see SqlDeduplicationFilter
 * @see ViolationStrategy
 */
public class MpSqlSafetyInnerInterceptor implements InnerInterceptor {

  private static final Logger logger = LoggerFactory.getLogger(MpSqlSafetyInnerInterceptor.class);

  /**
   * SQL safety validator from sql-guard-core.
   */
  private final DefaultSqlSafetyValidator validator;

  /**
   * Strategy for handling violations (BLOCK, WARN, or LOG).
   */
  private final ViolationStrategy strategy;

  /**
   * Deduplication filter to prevent double validation.
   */
  private final SqlDeduplicationFilter deduplicationFilter;

  /**
   * ThreadLocal storage for ValidationResult to enable coordination with MpSqlAuditInnerInterceptor.
   * This allows the audit interceptor to access pre-execution validation results.
   */
  private static final ThreadLocal<ValidationResult> VALIDATION_RESULT = new ThreadLocal<>();

  /**
   * Constructs MpSqlSafetyInnerInterceptor with validator and default BLOCK strategy.
   *
   * @param validator the SQL safety validator
   * @throws IllegalArgumentException if validator is null
   */
  public MpSqlSafetyInnerInterceptor(DefaultSqlSafetyValidator validator) {
    this(validator, ViolationStrategy.BLOCK);
  }

  /**
   * Constructs MpSqlSafetyInnerInterceptor with validator and custom strategy.
   *
   * @param validator the SQL safety validator
   * @param strategy the violation handling strategy
   * @throws IllegalArgumentException if validator or strategy is null
   */
  public MpSqlSafetyInnerInterceptor(
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
    this.deduplicationFilter = new SqlDeduplicationFilter();
  }

  /**
   * Intercepts query execution to validate SQL before execution.
   *
   * <p>This method is called by MyBatis-Plus before executing SELECT queries.
   * It extracts SQL context, detects IPage pagination, identifies QueryWrapper usage,
   * and validates the SQL against all enabled rules.</p>
   *
   * @param executor the MyBatis executor
   * @param ms the mapped statement
   * @param parameter the query parameters (may contain IPage or QueryWrapper)
   * @param rowBounds the row bounds for pagination
   * @param resultHandler the result handler
   * @param boundSql the bound SQL with parameters
   * @throws SQLException if validation fails and strategy is BLOCK
   */
  @Override
  public void beforeQuery(
      Executor executor,
      MappedStatement ms,
      Object parameter,
      RowBounds rowBounds,
      ResultHandler resultHandler,
      BoundSql boundSql) throws SQLException {
    validateSql(ms, parameter, boundSql, rowBounds);
  }

  /**
   * Intercepts update execution to validate SQL before execution.
   *
   * <p>This method is called by MyBatis-Plus before executing UPDATE/DELETE/INSERT statements.
   * It extracts SQL context and validates the SQL against all enabled rules.</p>
   *
   * @param executor the MyBatis executor
   * @param ms the mapped statement
   * @param parameter the update parameters (may contain UpdateWrapper)
   * @throws SQLException if validation fails and strategy is BLOCK
   */
  @Override
  public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter)
      throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    validateSql(ms, parameter, boundSql, null);
  }

  /**
   * Validates SQL statement using DefaultSqlSafetyValidator.
   *
   * <p><strong>Validation Flow:</strong></p>
   * <ol>
   *   <li>Check deduplication filter - skip if recently validated</li>
   *   <li>Build SqlContext with SQL, type, mapperId, and parameters</li>
   *   <li>Detect IPage parameter and extract pagination info</li>
   *   <li>Detect QueryWrapper/LambdaQueryWrapper usage</li>
   *   <li>Validate using DefaultSqlSafetyValidator</li>
   *   <li>Handle violations according to strategy</li>
   * </ol>
   *
   * @param ms the mapped statement
   * @param parameter the execution parameters
   * @param boundSql the bound SQL
   * @param rowBounds the row bounds (may be null)
   * @throws SQLException if validation fails and strategy is BLOCK
   */
  private void validateSql(
      MappedStatement ms,
      Object parameter,
      BoundSql boundSql,
      RowBounds rowBounds) throws SQLException {

    String sql = boundSql.getSql();

    // Step 1: Deduplication check
    if (!deduplicationFilter.shouldCheck(sql)) {
      logger.debug("Skipping validation for recently checked SQL: {}", getSqlSnippet(sql));
      return;
    }

    // Step 2: Build SqlContext
    SqlContext.SqlContextBuilder contextBuilder = SqlContext.builder()
        .sql(sql)
        .type(convertSqlCommandType(ms.getSqlCommandType()))
        .executionLayer(ExecutionLayer.MYBATIS)
        .statementId(ms.getId())
        .params(extractParameters(parameter))
        .rowBounds(rowBounds);

    // Step 3: Detect IPage
    if (hasIPageParameter(parameter)) {
      IPage<?> page = extractIPage(parameter);
      if (page != null) {
        contextBuilder.getClass(); // IPage detection will be stored in context details
        logger.debug("Detected IPage pagination: current={}, size={}", 
            page.getCurrent(), page.getSize());
      }
    }

    // Step 4: Detect QueryWrapper
    boolean hasWrapper = hasQueryWrapper(parameter);
    
    SqlContext context = contextBuilder.build();

    // Step 5: Validate
    ValidationResult result = validator.validate(context);

    // Step 6: Store validation result in ThreadLocal for audit interceptor coordination
    VALIDATION_RESULT.set(result);

    // Step 7: Handle violations
    if (!result.isPassed()) {
      handleViolation(result, ms.getId(), sql, hasWrapper);
    }
  }

  /**
   * Retrieves the validation result from the current thread's context.
   * This method is called by MpSqlAuditInnerInterceptor to correlate pre-execution
   * validation violations with post-execution audit events.
   *
   * @return the validation result, or null if no validation was performed
   */
  public static ValidationResult getValidationResult() {
    return VALIDATION_RESULT.get();
  }

  /**
   * Clears the validation result from the current thread's context.
   * This should be called after audit logging to prevent memory leaks.
   */
  public static void clearValidationResult() {
    VALIDATION_RESULT.remove();
  }

  /**
   * Handles validation violations according to configured strategy.
   *
   * @param result the validation result with violations
   * @param mapperId the mapper identifier
   * @param sql the SQL statement
   * @param hasWrapper whether QueryWrapper was used
   * @throws SQLException if strategy is BLOCK
   */
  private void handleViolation(
      ValidationResult result,
      String mapperId,
      String sql,
      boolean hasWrapper) throws SQLException {

    String violationMessage = buildViolationMessage(result, mapperId, sql, hasWrapper);

    switch (strategy) {
      case BLOCK:
        logger.error("BLOCKED SQL execution: {}", violationMessage);
        throw new SQLException("SQL validation failed: " + violationMessage);

      case WARN:
        logger.warn("SQL validation warning: {}", violationMessage);
        break;

      case LOG:
        logger.info("SQL validation info: {}", violationMessage);
        break;

      default:
        throw new IllegalStateException("Unknown violation strategy: " + strategy);
    }
  }

  /**
   * Builds detailed violation message for logging/exception.
   *
   * @param result the validation result
   * @param mapperId the mapper identifier
   * @param sql the SQL statement
   * @param hasWrapper whether QueryWrapper was used
   * @return formatted violation message
   */
  private String buildViolationMessage(
      ValidationResult result,
      String mapperId,
      String sql,
      boolean hasWrapper) {

    StringBuilder sb = new StringBuilder();
    sb.append("Mapper: ").append(mapperId);
    sb.append(", Risk: ").append(result.getRiskLevel());
    sb.append(", Violations: ").append(result.getViolations().size());
    
    if (hasWrapper) {
      sb.append(" [QueryWrapper]");
    }
    
    sb.append("\n  SQL: ").append(getSqlSnippet(sql));
    
    for (ViolationInfo violation : result.getViolations()) {
      sb.append("\n  - ").append(violation.getRiskLevel())
          .append(": ").append(violation.getMessage());
      if (violation.getSuggestion() != null) {
        sb.append(" (").append(violation.getSuggestion()).append(")");
      }
    }
    
    return sb.toString();
  }

  /**
   * Checks if parameter contains IPage pagination object.
   *
   * @param parameter the execution parameter
   * @return true if IPage is present
   */
  private boolean hasIPageParameter(Object parameter) {
    if (parameter instanceof IPage) {
      return true;
    }
    if (parameter instanceof Map) {
      Map<?, ?> paramMap = (Map<?, ?>) parameter;
      return paramMap.values().stream()
          .anyMatch(v -> v instanceof IPage);
    }
    return false;
  }

  /**
   * Extracts IPage object from parameter.
   *
   * @param parameter the execution parameter
   * @return IPage object or null if not found
   */
  private IPage<?> extractIPage(Object parameter) {
    if (parameter instanceof IPage) {
      return (IPage<?>) parameter;
    }
    if (parameter instanceof Map) {
      Map<?, ?> paramMap = (Map<?, ?>) parameter;
      return (IPage<?>) paramMap.values().stream()
          .filter(v -> v instanceof IPage)
          .findFirst()
          .orElse(null);
    }
    return null;
  }

  /**
   * Checks if parameter contains QueryWrapper or LambdaQueryWrapper.
   *
   * @param parameter the execution parameter
   * @return true if wrapper is present
   */
  private boolean hasQueryWrapper(Object parameter) {
    if (parameter instanceof AbstractWrapper) {
      return true;
    }
    if (parameter instanceof Map) {
      Map<?, ?> paramMap = (Map<?, ?>) parameter;
      return paramMap.values().stream()
          .anyMatch(v -> v instanceof AbstractWrapper);
    }
    return false;
  }

  /**
   * Extracts parameters from execution parameter object.
   *
   * @param parameter the execution parameter
   * @return parameter map or null
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> extractParameters(Object parameter) {
    if (parameter instanceof Map) {
      return (Map<String, Object>) parameter;
    }
    return null;
  }

  /**
   * Converts MyBatis SqlCommandType to sql-guard-core SqlCommandType.
   *
   * @param mybatisType the MyBatis SQL command type
   * @return sql-guard-core SqlCommandType
   */
  private SqlCommandType convertSqlCommandType(
      org.apache.ibatis.mapping.SqlCommandType mybatisType) {
    switch (mybatisType) {
      case SELECT:
        return SqlCommandType.SELECT;
      case UPDATE:
        return SqlCommandType.UPDATE;
      case DELETE:
        return SqlCommandType.DELETE;
      case INSERT:
        return SqlCommandType.INSERT;
      default:
        return SqlCommandType.SELECT; // Default to SELECT for unknown types
    }
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













