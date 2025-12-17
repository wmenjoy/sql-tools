package com.footstone.sqlguard.validator;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.parser.SqlParseException;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import java.util.List;
import net.sf.jsqlparser.statement.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of SqlSafetyValidator coordinating all validation components.
 *
 * <p>DefaultSqlSafetyValidator assembles the complete validation pipeline:</p>
 * <ul>
 *   <li>SQL deduplication filter (avoids redundant validation within TTL window)</li>
 *   <li>Parse-once SQL parsing via JSqlParser facade (AST reused by all checkers)</li>
 *   <li>Rule checker orchestrator (coordinates all 10 rule checkers from Phase 2)</li>
 *   <li>Violation aggregation to highest risk level</li>
 * </ul>
 *
 * <p><strong>Architecture:</strong></p>
 * <pre>
 * SqlContext
 *     ↓
 * [Deduplication Filter] → (cached) → ValidationResult.pass()
 *     ↓ (not cached)
 * [JSqlParserFacade] → Statement AST → SqlContext.parsedSql
 *     ↓
 * [RuleCheckerOrchestrator] → [Checker1, Checker2, ..., Checker10]
 *     ↓
 * ValidationResult (violations aggregated)
 * </pre>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. All fields are final
 * and immutable. ThreadLocal deduplication cache ensures thread isolation.</p>
 *
 * <p><strong>Performance Optimization:</strong></p>
 * <ul>
 *   <li>Parse-once: SQL parsed only once per validation, AST shared across checkers</li>
 *   <li>Deduplication: Same SQL within 100ms TTL skips validation (default)</li>
 *   <li>LRU cache: JSqlParser facade caches parsed statements (1000 entries default)</li>
 *   <li>Target overhead: <5% compared to SQL execution time</li>
 * </ul>
 *
 * @see SqlSafetyValidator
 * @see JSqlParserFacade
 * @see RuleCheckerOrchestrator
 * @see SqlDeduplicationFilter
 */
public class DefaultSqlSafetyValidator implements SqlSafetyValidator {

  private static final Logger logger = LoggerFactory.getLogger(DefaultSqlSafetyValidator.class);

  /**
   * JSqlParser facade for SQL parsing with caching and error handling.
   */
  private final JSqlParserFacade facade;

  /**
   * List of all rule checkers (10 checkers from Phase 2 Tasks 2.2-2.12).
   */
  private final List<RuleChecker> checkers;

  /**
   * Orchestrator coordinating checker execution.
   */
  private final RuleCheckerOrchestrator orchestrator;

  /**
   * Deduplication filter preventing redundant validation.
   */
  private final SqlDeduplicationFilter deduplicationFilter;

  /**
   * Constructs a DefaultSqlSafetyValidator with all required components.
   *
   * <p><strong>Component Dependencies:</strong></p>
   * <ul>
   *   <li>facade: Parses SQL and provides AST for checkers</li>
   *   <li>checkers: All 10 rule checkers from Phase 2 (NoWhereClause, DummyCondition, etc.)</li>
   *   <li>orchestrator: Coordinates checker execution in Chain of Responsibility pattern</li>
   *   <li>deduplicationFilter: Prevents redundant validation using ThreadLocal LRU cache</li>
   * </ul>
   *
   * @param facade the JSqlParser facade for SQL parsing
   * @param checkers the list of all rule checkers to execute
   * @param orchestrator the rule checker orchestrator
   * @param deduplicationFilter the SQL deduplication filter
   * @throws IllegalArgumentException if any parameter is null
   */
  public DefaultSqlSafetyValidator(
      JSqlParserFacade facade,
      List<RuleChecker> checkers,
      RuleCheckerOrchestrator orchestrator,
      SqlDeduplicationFilter deduplicationFilter) {

    if (facade == null) {
      throw new IllegalArgumentException("facade cannot be null");
    }
    if (checkers == null) {
      throw new IllegalArgumentException("checkers cannot be null");
    }
    if (orchestrator == null) {
      throw new IllegalArgumentException("orchestrator cannot be null");
    }
    if (deduplicationFilter == null) {
      throw new IllegalArgumentException("deduplicationFilter cannot be null");
    }

    this.facade = facade;
    this.checkers = checkers;
    this.orchestrator = orchestrator;
    this.deduplicationFilter = deduplicationFilter;
  }

  /**
   * Validates SQL statement against all enabled safety rules.
   *
   * <p><strong>Validation Flow:</strong></p>
   * <ol>
   *   <li>Check deduplication filter - return pass if recently validated</li>
   *   <li>Parse SQL if not already parsed - handle parse failures based on mode</li>
   *   <li>Execute orchestrator with all enabled checkers</li>
   *   <li>Return aggregated validation result</li>
   * </ol>
   *
   * @param context the SQL execution context
   * @return ValidationResult with violations and risk level; never null
   * @throws IllegalArgumentException if context is null
   * @throws SqlParseException if parsing fails in fail-fast mode
   */
  @Override
  public ValidationResult validate(SqlContext context) {
    if (context == null) {
      throw new IllegalArgumentException("context cannot be null");
    }

    // Step 2: Deduplication check
    if (!deduplicationFilter.shouldCheck(context.getSql())) {
      // SQL recently validated - return pass to skip redundant validation
      return ValidationResult.pass();
    }

    // Step 3: Parse-once logic
    SqlContext contextWithParsedSql = context;
    if (context.getParsedSql() == null) {
      try {
        Statement stmt = facade.parse(context.getSql());
        // If parse returns null (lenient mode), return pass without validation
        if (stmt == null) {
          logger.warn("Parse returned null for SQL: {} - Skipping validation", 
              getSqlSnippet(context.getSql()));
          return ValidationResult.pass();
        }
        // Create new context with parsed SQL (SqlContext is immutable)
        contextWithParsedSql = SqlContext.builder()
            .sql(context.getSql())
            .parsedSql(stmt)
            .type(context.getType())
            .mapperId(context.getMapperId())
            .params(context.getParams())
            .datasource(context.getDatasource())
            .rowBounds(context.getRowBounds())
            .build();
      } catch (Exception e) {
        return handleParseFailure(context, e);
      }
    }

    // Step 4: Orchestrator integration
    ValidationResult result = ValidationResult.pass();
    orchestrator.orchestrate(contextWithParsedSql, result);
    return result;
  }

  /**
   * Handles SQL parse failures based on facade mode (fail-fast or lenient).
   *
   * @param context the SQL context that failed to parse
   * @param e the exception thrown during parsing
   * @return ValidationResult.pass() in lenient mode
   * @throws SqlParseException in fail-fast mode
   */
  private ValidationResult handleParseFailure(SqlContext context, Exception e) {
    String sqlSnippet = getSqlSnippet(context.getSql());
    
    if (facade.isLenientMode()) {
      // Lenient mode: log warning and return pass
      logger.warn("Failed to parse SQL in lenient mode: {} - Reason: {}. Skipping validation.",
          sqlSnippet, e.getMessage());
      return ValidationResult.pass();
    } else {
      // Fail-fast mode: throw exception
      if (e instanceof SqlParseException) {
        throw (SqlParseException) e;
      } else {
        throw new SqlParseException(
            "Failed to parse SQL: " + sqlSnippet + " - Reason: " + e.getMessage(),
            e
        );
      }
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






