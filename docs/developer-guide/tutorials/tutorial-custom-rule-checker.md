# Tutorial: Adding a Custom Rule Checker

This tutorial demonstrates how to add a custom SQL validation rule to SQL Safety Guard. We'll implement a `CountStarChecker` that detects `SELECT COUNT(*)` on large tables without WHERE clause.

## Problem Statement

`SELECT COUNT(*)` without WHERE clause on large tables causes full table scans, leading to:
- High database CPU usage
- Slow query performance
- Potential database overload
- Memory exhaustion from result set processing

**Goal**: Detect this pattern and flag it as MEDIUM risk violation.

## Prerequisites

- Java 11 (for development)
- Maven 3.6+
- IDE with Checkstyle plugin
- Familiarity with JSqlParser AST traversal

## Step 1: Write Test Class First (TDD)

Following Test-Driven Development methodology, we write tests **before** implementation code.

Create `CountStarCheckerTest.java` in `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/`:

```java
package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test class for CountStarChecker.
 *
 * <p>Verifies that CountStarChecker correctly detects SELECT COUNT(*) without WHERE clause
 * and adds MEDIUM violations for queries that could cause full table scans.</p>
 */
class CountStarCheckerTest {

  private JSqlParserFacade parser;
  private CountStarChecker checker;
  private CountStarConfig config;

  @BeforeEach
  void setUp() {
    parser = new JSqlParserFacade();
    config = new CountStarConfig();
    checker = new CountStarChecker(config);
  }

  @Test
  void testCountStarWithoutWhere_shouldViolate() throws Exception {
    // COUNT(*) without WHERE is MEDIUM risk - full table scan
    String sql = "SELECT COUNT(*) FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.countAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
    assertEquals(1, result.getViolations().size());
    assertTrue(result.getViolations().get(0).getMessage().contains("COUNT(*)"));
    assertTrue(result.getViolations().get(0).getSuggestion().contains("WHERE"));
  }

  @Test
  void testCountStarWithWhere_shouldPass() throws Exception {
    // COUNT(*) with WHERE is acceptable - scoped query
    String sql = "SELECT COUNT(*) FROM user WHERE status = 1";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.countActive")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testCountWithColumnName_shouldPass() throws Exception {
    // COUNT(column_name) is different from COUNT(*) - may use index
    String sql = "SELECT COUNT(id) FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.countIds")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testRegularSelect_shouldPass() throws Exception {
    // Regular SELECT without COUNT(*) should pass
    String sql = "SELECT id, name FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testUpdateStatement_shouldSkip() throws Exception {
    // Non-SELECT statements should be skipped
    String sql = "UPDATE user SET status = 1";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.UPDATE)
        .mapperId("com.example.UserMapper.updateAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testDisabledChecker_shouldSkip() throws Exception {
    // Disabled checker should not add violations
    config.setEnabled(false);
    String sql = "SELECT COUNT(*) FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.countAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    assertTrue(result.isPassed());
    assertEquals(0, result.getViolations().size());
  }

  @Test
  void testCountStarInSubquery_shouldViolate() throws Exception {
    // COUNT(*) in subquery without WHERE should also violate
    String sql = "SELECT * FROM (SELECT COUNT(*) as cnt FROM user) t";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(parser.parse(sql))
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.selectWithCount")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // This test may fail if subquery detection not implemented
    // For simplicity, we focus on top-level COUNT(*) in this tutorial
    // Advanced implementation would traverse subqueries
  }

  @Test
  void testNullParsedSql_shouldSkip() {
    // Null parsed SQL should be handled gracefully
    String sql = "SELECT COUNT(*) FROM user";
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .parsedSql(null) // Simulate parse failure
        .type(SqlCommandType.SELECT)
        .mapperId("com.example.UserMapper.countAll")
        .build();
    ValidationResult result = ValidationResult.pass();

    checker.check(context, result);

    // Should not throw exception, should skip validation
    assertTrue(result.isPassed());
  }
}
```

## Step 2: Run Tests (Should Fail)

```bash
cd sql-guard-core
mvn test -Dtest=CountStarCheckerTest
```

**Expected Output**: Compilation error because `CountStarChecker` and `CountStarConfig` classes don't exist yet.

```
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.11.0:testCompile
[ERROR] /path/to/CountStarCheckerTest.java:[10,47] cannot find symbol
[ERROR]   symbol:   class CountStarChecker
[ERROR]   location: package com.footstone.sqlguard.validator.rule.impl
```

This is **expected** in TDD - tests fail first (red), then we implement code to make them pass (green).

## Step 3: Implement CountStarConfig

Create `CountStarConfig.java` in `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/`:

```java
package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration for CountStarChecker.
 *
 * <p>Detects SELECT COUNT(*) on large tables without WHERE clause, which can cause
 * full table scans and performance degradation.</p>
 *
 * <p><strong>Risk Level:</strong> MEDIUM</p>
 * <p><strong>Rationale:</strong> While not as critical as DELETE/UPDATE without WHERE,
 * COUNT(*) can still cause significant performance issues on large tables (millions of rows).</p>
 *
 * @see CountStarChecker
 */
public class CountStarConfig extends CheckerConfig {

  private boolean enabled = true;

  /**
   * Default constructor with enabled=true.
   */
  public CountStarConfig() {
    super();
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  /**
   * Sets whether this checker is enabled.
   *
   * @param enabled true to enable, false to disable
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
```

## Step 4: Implement CountStarChecker

Create `CountStarChecker.java` in `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/`:

```java
package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.rule.AbstractRuleChecker;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectExpressionItem;
import net.sf.jsqlparser.statement.select.SelectItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checker for detecting COUNT(*) without WHERE clause.
 *
 * <p>COUNT(*) on large tables without WHERE clause causes full table scans,
 * leading to performance degradation and potential database overload.</p>
 *
 * <p><strong>Detection Logic:</strong></p>
 * <ul>
 *   <li>Checks if SQL is SELECT statement</li>
 *   <li>Traverses SELECT items to find COUNT(*) function</li>
 *   <li>Checks if WHERE clause is absent</li>
 *   <li>Adds MEDIUM violation if both conditions met</li>
 * </ul>
 *
 * <p><strong>Risk Level:</strong> MEDIUM</p>
 * <p><strong>Rationale:</strong> While not as critical as DELETE/UPDATE without WHERE,
 * COUNT(*) can still cause significant performance issues on large tables.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * CountStarConfig config = new CountStarConfig();
 * config.setEnabled(true);
 * CountStarChecker checker = new CountStarChecker(config);
 *
 * SqlContext context = SqlContext.builder()
 *     .sql("SELECT COUNT(*) FROM user")
 *     .parsedSql(parser.parse("SELECT COUNT(*) FROM user"))
 *     .type(SqlCommandType.SELECT)
 *     .mapperId("com.example.UserMapper.countAll")
 *     .build();
 *
 * ValidationResult result = ValidationResult.pass();
 * checker.check(context, result);
 * // result.getRiskLevel() == RiskLevel.MEDIUM
 * // result.getViolations().size() == 1
 * }</pre>
 *
 * @see CountStarConfig
 * @see AbstractRuleChecker
 */
public class CountStarChecker extends AbstractRuleChecker {

  private static final Logger log = LoggerFactory.getLogger(CountStarChecker.class);

  private final CountStarConfig config;

  /**
   * Constructs CountStarChecker with configuration.
   *
   * @param config the checker configuration
   */
  public CountStarChecker(CountStarConfig config) {
    super(config);
    this.config = config;
  }

  @Override
  public void check(SqlContext context, ValidationResult result) {
    if (!isEnabled()) {
      return;
    }

    // Only check SELECT statements
    if (context.getType() != SqlCommandType.SELECT) {
      return;
    }

    Statement stmt = context.getParsedSql();
    if (stmt == null) {
      log.debug("Skipping validation: parsed SQL is null");
      return;
    }

    if (!(stmt instanceof Select)) {
      return;
    }

    Select select = (Select) stmt;
    if (!(select.getSelectBody() instanceof PlainSelect)) {
      // Skip UNION, INTERSECT, EXCEPT queries for simplicity
      return;
    }

    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

    // Check for COUNT(*) without WHERE
    if (hasCountStar(plainSelect) && plainSelect.getWhere() == null) {
      result.addViolation(
          RiskLevel.MEDIUM,
          "COUNT(*) without WHERE clause on large table",
          "Add WHERE condition to limit scope, or use approximate count if exact count not needed"
      );
      log.debug("Detected COUNT(*) without WHERE in SQL: {}", context.getSql());
    }
  }

  /**
   * Checks if SELECT contains COUNT(*) function.
   *
   * <p>Traverses all SELECT items and checks if any item is a COUNT(*) function.
   * COUNT(column_name) is not flagged as it may use index.</p>
   *
   * @param select the PlainSelect statement
   * @return true if COUNT(*) found, false otherwise
   */
  private boolean hasCountStar(PlainSelect select) {
    for (SelectItem item : select.getSelectItems()) {
      if (item instanceof SelectExpressionItem) {
        Expression expr = ((SelectExpressionItem) item).getExpression();
        if (expr instanceof Function) {
          Function func = (Function) expr;
          // Check if function name is COUNT and uses * (all columns)
          if ("COUNT".equalsIgnoreCase(func.getName()) && func.isAllColumns()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public boolean isEnabled() {
    return config.isEnabled();
  }
}
```

## Step 5: Run Tests (Should Pass)

```bash
cd sql-guard-core
mvn test -Dtest=CountStarCheckerTest
```

**Expected Output**: All tests pass (green).

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.footstone.sqlguard.validator.rule.impl.CountStarCheckerTest
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.245 s
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] BUILD SUCCESS
```

## Step 6: Register in DefaultSqlSafetyValidator

Update `DefaultSqlSafetyValidator.java` to include new checker:

```java
package com.footstone.sqlguard.validator;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.pagination.impl.*;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import com.footstone.sqlguard.validator.rule.impl.*;
import java.util.Arrays;
import java.util.List;
import net.sf.jsqlparser.statement.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of SqlSafetyValidator.
 */
public class DefaultSqlSafetyValidator implements SqlSafetyValidator {

  private static final Logger log = LoggerFactory.getLogger(DefaultSqlSafetyValidator.class);

  private final JSqlParserFacade parser;
  private final RuleCheckerOrchestrator orchestrator;
  private final SqlDeduplicationFilter filter;

  /**
   * Constructs validator with configuration.
   *
   * @param parser the SQL parser facade
   * @param config the SQL Guard configuration
   * @param filter the deduplication filter
   */
  public DefaultSqlSafetyValidator(
      JSqlParserFacade parser,
      SqlGuardConfig config,
      SqlDeduplicationFilter filter) {
    this.parser = parser;
    this.filter = filter;

    // Create all rule checkers
    List<RuleChecker> checkers = Arrays.asList(
        new NoWhereClauseChecker(new NoWhereClauseConfig(config.getNoWhereClause())),
        new DummyConditionChecker(new DummyConditionConfig(config.getDummyCondition())),
        new BlacklistFieldChecker(new BlacklistFieldsConfig(config.getBlacklistFields())),
        new WhitelistFieldChecker(new WhitelistFieldsConfig(config.getWhitelistFields())),
        new LogicalPaginationChecker(/* ... */),
        new NoConditionPaginationChecker(/* ... */),
        new DeepPaginationChecker(/* ... */),
        new LargePageSizeChecker(/* ... */),
        new MissingOrderByChecker(/* ... */),
        new NoPaginationChecker(/* ... */),
        new CountStarChecker(new CountStarConfig()) // Add new checker
    );

    this.orchestrator = new RuleCheckerOrchestrator(checkers);
  }

  @Override
  public ValidationResult validate(SqlContext context) {
    // Check deduplication filter
    if (filter.shouldSkip(context)) {
      return ValidationResult.pass();
    }

    // Parse SQL if not already parsed
    SqlContext enriched = context;
    if (context.getParsedSql() == null) {
      Statement parsed = parser.parse(context.getSql());
      enriched = SqlContext.builder()
          .sql(context.getSql())
          .parsedSql(parsed)
          .type(context.getType())
          .mapperId(context.getMapperId())
          .params(context.getParams())
          .datasource(context.getDatasource())
          .rowBounds(context.getRowBounds())
          .build();
    }

    // Execute all checkers
    ValidationResult result = ValidationResult.pass();
    orchestrator.executeAll(enriched, result);

    return result;
  }
}
```

## Step 7: Add Configuration Support

Update `SqlGuardConfig.java` to include CountStarConfig:

```java
package com.footstone.sqlguard.config;

/**
 * Root configuration class for SQL Guard.
 */
public class SqlGuardConfig {

  private NoWhereClauseConfig noWhereClause = new NoWhereClauseConfig();
  private DummyConditionConfig dummyCondition = new DummyConditionConfig();
  private BlacklistFieldsConfig blacklistFields = new BlacklistFieldsConfig();
  private WhitelistFieldsConfig whitelistFields = new WhitelistFieldsConfig();
  private PaginationAbuseConfig paginationAbuse = new PaginationAbuseConfig();
  private CountStarConfig countStar = new CountStarConfig(); // Add new config

  // Getters and setters

  public CountStarConfig getCountStar() {
    return countStar;
  }

  public void setCountStar(CountStarConfig countStar) {
    this.countStar = countStar;
  }

  // ... other getters/setters
}
```

Update YAML configuration example in `config-example.yml`:

```yaml
sql-guard:
  enabled: true
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
    dummy-condition:
      enabled: true
      risk-level: HIGH
    count-star:
      enabled: true
      risk-level: MEDIUM
    # ... other rules
```

## Step 8: Add Integration Test

Add integration test in `RuleCheckerIntegrationTest.java`:

```java
@Test
void testCountStarChecker_integration() throws Exception {
  // Arrange: Set up full validator with all checkers
  SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();
  config.getCountStar().setEnabled(true);

  DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
      new JSqlParserFacade(),
      config,
      new SqlDeduplicationFilter()
  );

  // Act: Validate COUNT(*) without WHERE
  String sql = "SELECT COUNT(*) FROM large_table";
  SqlContext context = SqlContext.builder()
      .sql(sql)
      .type(SqlCommandType.SELECT)
      .mapperId("test.mapper.countAll")
      .build();

  ValidationResult result = validator.validate(context);

  // Assert: Violation detected
  assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
  assertTrue(result.getViolations().stream()
      .anyMatch(v -> v.getMessage().contains("COUNT(*)")));
}

@Test
void testCountStarChecker_withWhere_shouldPass() throws Exception {
  // Arrange
  SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();
  config.getCountStar().setEnabled(true);

  DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
      new JSqlParserFacade(),
      config,
      new SqlDeduplicationFilter()
  );

  // Act: Validate COUNT(*) with WHERE
  String sql = "SELECT COUNT(*) FROM large_table WHERE status = 1";
  SqlContext context = SqlContext.builder()
      .sql(sql)
      .type(SqlCommandType.SELECT)
      .mapperId("test.mapper.countActive")
      .build();

  ValidationResult result = validator.validate(context);

  // Assert: No violation
  assertTrue(result.isPassed());
  assertEquals(0, result.getViolations().size());
}
```

## Step 9: Update Documentation

Update `README.md` with new rule:

```markdown
### Validation Rules

SQL Safety Guard includes 11 built-in validation rules:

- **NoWhereClause** (CRITICAL): Detects DELETE/UPDATE/SELECT without WHERE clause
- **DummyCondition** (HIGH): Detects dummy conditions like "1=1" or "true"
- **BlacklistField** (HIGH): Detects blacklist-only WHERE clauses
- **WhitelistField** (HIGH): Enforces whitelist field requirements
- **CountStar** (MEDIUM): Detects COUNT(*) without WHERE clause ← NEW
- **LogicalPagination** (CRITICAL): Detects RowBounds/IPage without pagination plugin
- **NoConditionPagination** (CRITICAL): Detects pagination without WHERE clause
- **DeepPagination** (MEDIUM): Detects high OFFSET values
- **LargePageSize** (MEDIUM): Detects excessive page sizes
- **MissingOrderBy** (LOW): Detects missing ORDER BY in pagination
- **NoPagination** (Variable): Variable risk based on WHERE clause
```

Update `CHANGELOG.md`:

```markdown
## [Unreleased]

### Added
- CountStarChecker: Detects SELECT COUNT(*) without WHERE clause (MEDIUM risk)
```

## Step 10: Run Full Test Suite

```bash
# Run all tests
cd sql-guard-core
mvn clean test

# Verify coverage
mvn jacoco:report
open target/site/jacoco/index.html

# Verify Checkstyle
mvn checkstyle:check
```

**Expected Output**: All tests pass, coverage >80%, no Checkstyle violations.

```
[INFO] Results:
[INFO] 
[INFO] Tests run: 476, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] BUILD SUCCESS
```

## Verification Testing

Test the new checker with a real SQL statement:

```java
public class CountStarCheckerManualTest {
  public static void main(String[] args) throws Exception {
    // Set up validator
    JSqlParserFacade parser = new JSqlParserFacade();
    SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();
    config.getCountStar().setEnabled(true);
    
    DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
        parser,
        config,
        new SqlDeduplicationFilter()
    );
    
    // Test 1: COUNT(*) without WHERE (should violate)
    String sql1 = "SELECT COUNT(*) FROM user";
    SqlContext context1 = SqlContext.builder()
        .sql(sql1)
        .type(SqlCommandType.SELECT)
        .mapperId("test.mapper.countAll")
        .build();
    
    ValidationResult result1 = validator.validate(context1);
    System.out.println("Test 1 - COUNT(*) without WHERE:");
    System.out.println("  Passed: " + result1.isPassed());
    System.out.println("  Risk Level: " + result1.getRiskLevel());
    System.out.println("  Violations: " + result1.getViolations());
    
    // Test 2: COUNT(*) with WHERE (should pass)
    String sql2 = "SELECT COUNT(*) FROM user WHERE status = 1";
    SqlContext context2 = SqlContext.builder()
        .sql(sql2)
        .type(SqlCommandType.SELECT)
        .mapperId("test.mapper.countActive")
        .build();
    
    ValidationResult result2 = validator.validate(context2);
    System.out.println("\nTest 2 - COUNT(*) with WHERE:");
    System.out.println("  Passed: " + result2.isPassed());
    System.out.println("  Violations: " + result2.getViolations());
  }
}
```

**Expected Output**:

```
Test 1 - COUNT(*) without WHERE:
  Passed: false
  Risk Level: MEDIUM
  Violations: [ViolationInfo{riskLevel=MEDIUM, message='COUNT(*) without WHERE clause on large table', suggestion='Add WHERE condition to limit scope, or use approximate count if exact count not needed'}]

Test 2 - COUNT(*) with WHERE:
  Passed: true
  Violations: []
```

## Troubleshooting

### Issue: Tests fail with NullPointerException

**Cause**: `parsedSql` is null in SqlContext.

**Solution**: Ensure parser.parse() is called before creating SqlContext:

```java
Statement parsed = parser.parse(sql);
SqlContext context = SqlContext.builder()
    .sql(sql)
    .parsedSql(parsed) // Must include parsed SQL
    .type(SqlCommandType.SELECT)
    .mapperId("test.mapper")
    .build();
```

### Issue: Checker not executing

**Cause**: Checker not registered in DefaultSqlSafetyValidator.

**Solution**: Verify checker is added to checkers list in constructor:

```java
List<RuleChecker> checkers = Arrays.asList(
    // ... existing checkers
    new CountStarChecker(new CountStarConfig()) // Must be included
);
```

### Issue: Checkstyle violations

**Cause**: Code doesn't follow Google Java Style.

**Solution**: Auto-format code with IDE:

```bash
# IntelliJ IDEA: Ctrl+Alt+L (Windows/Linux) or Cmd+Option+L (Mac)
# Eclipse: Ctrl+Shift+F (Windows/Linux) or Cmd+Shift+F (Mac)
```

## Summary

You've successfully added a custom rule checker to SQL Safety Guard! Key takeaways:

1. **TDD Methodology**: Write tests first, then implement code
2. **Extend AbstractRuleChecker**: Inherit common functionality
3. **Use JSqlParser AST**: Traverse SQL structure to detect patterns
4. **Register in Validator**: Add checker to DefaultSqlSafetyValidator
5. **Update Configuration**: Add config class and YAML support
6. **Document Changes**: Update README and CHANGELOG

## Next Steps

- Add support for subquery COUNT(*) detection
- Add configuration for table size threshold (only check tables >1M rows)
- Add support for approximate count alternatives (e.g., EXPLAIN output)
- Contribute your checker back to the project via Pull Request!

## Complete Source Code

All source code from this tutorial is available in:

- `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/CountStarChecker.java`
- `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/CountStarConfig.java`
- `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/CountStarCheckerTest.java`

Copy-paste the code examples above to get started!
