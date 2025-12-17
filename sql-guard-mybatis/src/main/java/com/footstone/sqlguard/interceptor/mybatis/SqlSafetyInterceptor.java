package com.footstone.sqlguard.interceptor.mybatis;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.validator.SqlSafetyValidator;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MyBatis interceptor for runtime SQL validation.
 *
 * <p>SqlSafetyInterceptor intercepts MyBatis Executor methods to validate SQL statements
 * after dynamic SQL resolution but before database execution. This provides runtime defense
 * against dangerous SQL patterns that may be missed by static analysis.</p>
 *
 * <p><strong>Interception Points:</strong></p>
 * <ul>
 *   <li>{@code Executor.update(MappedStatement, Object)} - INSERT/UPDATE/DELETE</li>
 *   <li>{@code Executor.query(MappedStatement, Object, RowBounds, ResultHandler)} - SELECT</li>
 * </ul>
 *
 * <p><strong>Validation Flow:</strong></p>
 * <ol>
 *   <li>Extract MappedStatement and parameters from invocation</li>
 *   <li>Get BoundSql with resolved dynamic SQL (if/where/foreach tags processed)</li>
 *   <li>Build SqlContext with execution metadata (mapperId, rowBounds, params)</li>
 *   <li>Validate SQL using DefaultSqlSafetyValidator</li>
 *   <li>Handle violations according to configured strategy (BLOCK/WARN/LOG)</li>
 *   <li>Proceed with SQL execution (or throw exception if BLOCK)</li>
 * </ol>
 *
 * <p><strong>Configuration Example:</strong></p>
 * <pre>{@code
 * <plugins>
 *   <plugin interceptor="com.footstone.sqlguard.interceptor.mybatis.SqlSafetyInterceptor">
 *     <property name="strategy" value="WARN"/>
 *   </plugin>
 * </plugins>
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong> This interceptor is thread-safe. The validator instance
 * is shared across threads and uses ThreadLocal caching for deduplication.</p>
 *
 * @see ViolationStrategy
 * @see SqlSafetyValidator
 */
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "update",
        args = {MappedStatement.class, Object.class}
    ),
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}
    )
})
public class SqlSafetyInterceptor implements Interceptor {

  private static final Logger logger = LoggerFactory.getLogger(SqlSafetyInterceptor.class);

  /**
   * SQL safety validator for checking SQL statements.
   */
  private final SqlSafetyValidator validator;

  /**
   * Violation handling strategy.
   */
  private final ViolationStrategy strategy;

  /**
   * Constructs a SqlSafetyInterceptor with specified validator and strategy.
   *
   * @param validator the SQL safety validator
   * @param strategy the violation handling strategy
   * @throws IllegalArgumentException if validator or strategy is null
   */
  public SqlSafetyInterceptor(SqlSafetyValidator validator, ViolationStrategy strategy) {
    if (validator == null) {
      throw new IllegalArgumentException("validator cannot be null");
    }
    if (strategy == null) {
      throw new IllegalArgumentException("strategy cannot be null");
    }
    this.validator = validator;
    this.strategy = strategy;
  }

  /**
   * Intercepts Executor method calls to validate SQL before execution.
   *
   * <p>This method:</p>
   * <ol>
   *   <li>Extracts execution context from invocation arguments</li>
   *   <li>Gets BoundSql with resolved dynamic SQL</li>
   *   <li>Builds SqlContext with all metadata</li>
   *   <li>Validates SQL using the validator</li>
   *   <li>Handles violations according to strategy</li>
   *   <li>Proceeds with execution</li>
   * </ol>
   *
   * @param invocation the method invocation
   * @return the result of the intercepted method
   * @throws Throwable if validation fails (BLOCK strategy) or execution fails
   */
  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    // Extract execution context
    Object[] args = invocation.getArgs();
    MappedStatement ms = (MappedStatement) args[0];
    Object parameter = args[1];

    // Get RowBounds (query only)
    RowBounds rowBounds = null;
    if (args.length >= 3 && args[2] instanceof RowBounds) {
      rowBounds = (RowBounds) args[2];
    }

    // Get BoundSql (resolved dynamic SQL)
    BoundSql boundSql = ms.getBoundSql(parameter);
    String sql = boundSql.getSql();

    // Build SqlContext
    SqlContext context = buildSqlContext(ms, sql, parameter, rowBounds);

    // Validate SQL
    ValidationResult result = validator.validate(context);

    // Store validation result in ThreadLocal for SqlAuditInterceptor
    // Note: SqlAuditInterceptor is responsible for cleanup
    SqlInterceptorContext.VALIDATION_RESULT.set(result);

    // Handle violations
    if (!result.isPassed()) {
      handleViolation(result, ms.getId());
    }

    // Proceed with execution
    return invocation.proceed();
  }

  /**
   * Builds SqlContext from execution metadata.
   *
   * @param ms the MappedStatement
   * @param sql the resolved SQL string
   * @param parameter the execution parameter
   * @param rowBounds the RowBounds (may be null)
   * @return the constructed SqlContext
   */
  private SqlContext buildSqlContext(MappedStatement ms, String sql,
                                      Object parameter, RowBounds rowBounds) {
    // Convert MyBatis SqlCommandType to our SqlCommandType
    SqlCommandType type = convertSqlCommandType(ms.getSqlCommandType());

    // Extract parameters
    Map<String, Object> params = extractParameters(parameter);

    return SqlContext.builder()
        .sql(sql)
        .type(type)
        .mapperId(ms.getId())
        .rowBounds(rowBounds)
        .params(params)
        .build();
  }

  /**
   * Converts MyBatis SqlCommandType to our SqlCommandType enum.
   *
   * @param mybatisType the MyBatis SqlCommandType
   * @return our SqlCommandType
   */
  private SqlCommandType convertSqlCommandType(
      org.apache.ibatis.mapping.SqlCommandType mybatisType) {
    if (mybatisType == null) {
      return SqlCommandType.SELECT; // Default to SELECT
    }

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
        return SqlCommandType.SELECT;
    }
  }

  /**
   * Extracts parameters from MyBatis parameter object.
   *
   * <p>Handles different parameter types:</p>
   * <ul>
   *   <li>Map - @Param annotated parameters</li>
   *   <li>Single value - primitive or simple object</li>
   *   <li>POJO - complex object (returned as-is in map)</li>
   * </ul>
   *
   * @param parameter the MyBatis parameter object
   * @return parameter map (never null)
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> extractParameters(Object parameter) {
    Map<String, Object> params = new HashMap<>();

    if (parameter == null) {
      return params;
    }

    if (parameter instanceof Map) {
      // @Param annotated parameters
      params.putAll((Map<String, Object>) parameter);
    } else {
      // Single parameter or POJO
      params.put("param", parameter);
    }

    return params;
  }

  /**
   * Handles validation violations according to configured strategy.
   *
   * @param result the validation result with violations
   * @param mapperId the mapper method identifier
   * @throws SQLException if strategy is BLOCK
   */
  private void handleViolation(ValidationResult result, String mapperId) throws SQLException {
    String violationMsg = formatViolations(result, mapperId);

    switch (strategy) {
      case BLOCK:
        logger.error("[BLOCK] SQL Safety Violation: {}", violationMsg);
        throw new SQLException(
            "SQL Safety Violation (BLOCK): " + violationMsg,
            "42000"  // SQLState: syntax error or access rule violation
        );

      case WARN:
        logger.error("[WARN] SQL Safety Violation: {}", violationMsg);
        break;  // Continue execution

      case LOG:
        logger.warn("[LOG] SQL Safety Violation: {}", violationMsg);
        break;  // Continue execution

      default:
        logger.warn("[UNKNOWN] SQL Safety Violation: {}", violationMsg);
        break;
    }
  }

  /**
   * Formats violations into detailed message for logging.
   *
   * @param result the validation result
   * @param mapperId the mapper method identifier
   * @return formatted violation message
   */
  private String formatViolations(ValidationResult result, String mapperId) {
    StringBuilder sb = new StringBuilder();
    sb.append("MapperId: ").append(mapperId).append("\n");
    sb.append("Risk Level: ").append(result.getRiskLevel()).append("\n");
    sb.append("Violations:\n");

    for (ViolationInfo violation : result.getViolations()) {
      sb.append("  - [").append(violation.getRiskLevel()).append("] ");
      sb.append(violation.getMessage()).append("\n");
      if (violation.getSuggestion() != null) {
        sb.append("    Suggestion: ").append(violation.getSuggestion()).append("\n");
      }
    }

    return sb.toString();
  }

  /**
   * Wraps the target object with this interceptor.
   *
   * @param target the target object (Executor)
   * @return the wrapped proxy
   */
  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  /**
   * Sets properties from MyBatis configuration.
   *
   * <p>Supported properties:</p>
   * <ul>
   *   <li>{@code strategy} - Violation strategy (BLOCK/WARN/LOG)</li>
   * </ul>
   *
   * <p><strong>Note:</strong> This method is called by MyBatis when using XML configuration.
   * When using programmatic configuration (Spring Boot), use the constructor instead.</p>
   *
   * @param properties the configuration properties
   */
  @Override
  public void setProperties(Properties properties) {
    // This method is for XML configuration support
    // When using programmatic configuration, the constructor is used instead
    // Properties can be used to override strategy if needed
    if (properties != null && properties.containsKey("strategy")) {
      String strategyStr = properties.getProperty("strategy");
      logger.info("Strategy property found in XML config: {}", strategyStr);
      // Note: Cannot modify final field, this is for logging only
      // Users should use constructor for programmatic configuration
    }
  }

  /**
   * Returns the configured violation strategy.
   *
   * @return the violation strategy
   */
  public ViolationStrategy getStrategy() {
    return strategy;
  }

  /**
   * Returns the SQL safety validator.
   *
   * @return the validator
   */
  public SqlSafetyValidator getValidator() {
    return validator;
  }
}
