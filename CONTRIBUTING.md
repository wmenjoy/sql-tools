# Contributing to SQL Safety Guard

Thank you for your interest in contributing to SQL Safety Guard! This guide will help you set up your development environment, understand our code standards, and submit high-quality contributions.

## Table of Contents

- [Development Setup](#development-setup)
- [Code Style Guidelines](#code-style-guidelines)
- [Test-Driven Development Requirements](#test-driven-development-requirements)
- [Pull Request Process](#pull-request-process)
- [How to Add New Rule Checker](#how-to-add-new-rule-checker)
- [How to Support New JDBC Pool](#how-to-support-new-jdbc-pool)
- [Documentation Standards](#documentation-standards)
- [Community Guidelines](#community-guidelines)

## Development Setup

### Prerequisites

- **Java 11** (for development; project targets Java 8 baseline)
- **Maven 3.6+**
- **Git**
- **IDE**: IntelliJ IDEA or Eclipse with Checkstyle plugin

### Clone and Build

```bash
# Clone repository
git clone https://github.com/footstone/sql-safety-guard.git
cd sql-safety-guard

# Build all modules
mvn clean install

# Run tests
mvn test

# Generate Javadoc
mvn javadoc:aggregate

# Run Checkstyle
mvn checkstyle:check
```

### IDE Setup

#### IntelliJ IDEA

1. **Import Project**:
   - File → Open → Select `pom.xml` → Open as Project
   - IntelliJ will auto-detect Maven modules

2. **Install Checkstyle Plugin**:
   - File → Settings → Plugins → Search "Checkstyle-IDEA" → Install
   - File → Settings → Tools → Checkstyle → Add Configuration File
   - Select `google_checks.xml` (built-in)
   - Set as active configuration

3. **Install google-java-format Plugin**:
   - File → Settings → Plugins → Search "google-java-format" → Install
   - File → Settings → Other Settings → google-java-format Settings
   - Enable "Enable google-java-format"
   - Set code style to "AOSP" (Android Open Source Project, 2-space indentation)

4. **Configure Code Style**:
   - File → Settings → Editor → Code Style → Java
   - Scheme → Import Scheme → IntelliJ IDEA code style XML
   - Import `google-java-format` style (from plugin)
   - Set line length to 120 characters

5. **Enable Auto-Format on Save**:
   - File → Settings → Tools → Actions on Save
   - Enable "Reformat code"
   - Enable "Optimize imports"

#### Eclipse

1. **Import Project**:
   - File → Import → Maven → Existing Maven Projects
   - Select repository root directory
   - Eclipse will import all modules

2. **Install Checkstyle Plugin**:
   - Help → Eclipse Marketplace → Search "Checkstyle" → Install
   - Window → Preferences → Checkstyle → New → External Configuration File
   - Select `google_checks.xml` from Maven repository
   - Set as default configuration

3. **Install google-java-format Plugin**:
   - Help → Eclipse Marketplace → Search "google-java-format" → Install
   - Window → Preferences → Java → Code Style → Formatter
   - Import `google-java-format` formatter XML
   - Set line length to 120 characters

4. **Configure Save Actions**:
   - Window → Preferences → Java → Editor → Save Actions
   - Enable "Perform the selected actions on save"
   - Enable "Format source code"
   - Enable "Organize imports"

### Verify Setup

Run the following commands to verify your setup:

```bash
# Compile all modules
mvn clean compile

# Run all tests (should pass 100%)
mvn test

# Run Checkstyle (should have 0 violations)
mvn checkstyle:check

# Build Javadoc (should have 0 errors/warnings)
mvn javadoc:aggregate
```

Expected output:
```
[INFO] BUILD SUCCESS
[INFO] Total time: 2.345 s
```

## Code Style Guidelines

SQL Safety Guard enforces **Google Java Style** with minor modifications. All code must pass Checkstyle validation before merging.

### Google Java Style Essentials

#### 1. Indentation

- **2 spaces** for indentation (not tabs)
- **4 spaces** for continuation lines
- **No tabs** anywhere in source files

```java
// Correct
public class Example {
  public void method() {
    if (condition) {
      doSomething();
    }
  }
}

// Incorrect (4-space indentation)
public class Example {
    public void method() {
        if (condition) {
            doSomething();
        }
    }
}
```

#### 2. Line Length

- **Maximum 120 characters** per line
- Break long lines at logical points (after commas, before operators)

```java
// Correct
public ValidationResult validate(
    SqlContext context, 
    RuleCheckerOrchestrator orchestrator) {
  // ...
}

// Incorrect (exceeds 120 characters)
public ValidationResult validate(SqlContext context, RuleCheckerOrchestrator orchestrator, SqlDeduplicationFilter filter) {
  // ...
}
```

#### 3. Braces

- **K&R style**: Opening brace on same line, closing brace on new line
- **Always use braces** for if/else/for/while, even single-line blocks

```java
// Correct
if (condition) {
  doSomething();
}

// Incorrect (no braces)
if (condition)
  doSomething();

// Incorrect (opening brace on new line)
if (condition)
{
  doSomething();
}
```

#### 4. Whitespace

- **One space** after keywords (if, for, while, catch)
- **No space** after method names before parentheses
- **One space** around binary operators (+, -, *, /, ==, !=)

```java
// Correct
if (condition) {
  result = a + b;
  method(arg);
}

// Incorrect
if(condition){
  result=a+b;
  method (arg);
}
```

#### 5. Import Statements

- **No wildcard imports** (e.g., `import java.util.*`)
- **Group imports**: Java standard library → Third-party → Project
- **Alphabetical order** within each group

```java
// Correct
import java.util.List;
import java.util.Map;

import com.github.jsqlparser.statement.Statement;
import org.slf4j.Logger;

import com.footstone.sqlguard.core.model.SqlContext;

// Incorrect (wildcard import)
import java.util.*;
```

#### 6. Naming Conventions

- **Classes**: PascalCase (e.g., `SqlSafetyValidator`)
- **Methods**: camelCase (e.g., `validateSql()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_CACHE_SIZE`)
- **Variables**: camelCase (e.g., `sqlContext`)
- **Packages**: lowercase (e.g., `com.footstone.sqlguard`)

```java
// Correct
public class NoWhereClauseChecker {
  private static final int MAX_VIOLATIONS = 100;
  
  public void check(SqlContext context, ValidationResult result) {
    String sqlString = context.getSql();
    // ...
  }
}
```

#### 7. Javadoc

- **All public classes and methods** must have Javadoc
- **Use `@param`, `@return`, `@throws`** for method documentation
- **First sentence** is summary (ends with period)

```java
/**
 * Validates SQL statements against configured safety rules.
 *
 * <p>This validator performs comprehensive validation by executing all enabled
 * rule checkers and aggregating violations to the highest risk level.</p>
 *
 * @param context the SQL execution context containing statement and metadata
 * @return ValidationResult containing violations and overall risk level; never null
 * @throws IllegalArgumentException if context is null or invalid
 */
public ValidationResult validate(SqlContext context) {
  // ...
}
```

### Checkstyle Configuration

The project uses `google_checks.xml` with the following modifications:

- **Line length**: 120 characters (default: 100)
- **Indentation**: 2 spaces (default: 2)
- **Javadoc requirement**: All public members (default: public only)

Run Checkstyle locally:

```bash
# Check all modules
mvn checkstyle:check

# Check specific module
mvn checkstyle:check -pl sql-guard-core

# Generate Checkstyle report
mvn checkstyle:checkstyle
# Report: target/site/checkstyle.html
```

### Auto-Formatting

**Before committing**, always auto-format your code:

```bash
# IntelliJ IDEA
Ctrl+Alt+L (Windows/Linux) or Cmd+Option+L (Mac)

# Eclipse
Ctrl+Shift+F (Windows/Linux) or Cmd+Shift+F (Mac)

# Command line (using google-java-format)
java -jar google-java-format-1.17.0-all-deps.jar --replace src/**/*.java
```

## Test-Driven Development Requirements

SQL Safety Guard follows **strict Test-Driven Development (TDD)** methodology. All features must have tests written **BEFORE** implementation code.

### TDD Workflow

1. **Write Test First**: Write failing test that describes desired behavior
2. **Run Test**: Verify test fails (red)
3. **Implement Code**: Write minimal code to make test pass
4. **Run Test**: Verify test passes (green)
5. **Refactor**: Improve code quality without changing behavior
6. **Repeat**: Continue with next test case

### Test Class Naming Conventions

- **Unit Test**: `{ClassName}Test.java`
  - Example: `NoWhereClauseChecker.java` → `NoWhereClauseCheckerTest.java`
  
- **Integration Test**: `{Feature}IntegrationTest.java`
  - Example: `RuleCheckerIntegrationTest.java`
  
- **Performance Test**: `{Feature}PerformanceTest.java`
  - Example: `ReportGeneratorPerformanceTest.java`

### Test Method Naming Convention

Use descriptive names following pattern: `test{MethodName}_should{ExpectedBehavior}`

```java
@Test
void testDeleteWithoutWhere_shouldViolate() {
  // Arrange
  String sql = "DELETE FROM user";
  SqlContext context = SqlContext.builder()
      .sql(sql)
      .parsedSql(parser.parse(sql))
      .type(SqlCommandType.DELETE)
      .mapperId("com.example.UserMapper.deleteAll")
      .build();
  ValidationResult result = ValidationResult.pass();
  
  // Act
  checker.check(context, result);
  
  // Assert
  assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
  assertEquals(1, result.getViolations().size());
  assertTrue(result.getViolations().get(0).getMessage().contains("缺少WHERE条件"));
}
```

### Test Coverage Requirements

- **Minimum 80% code coverage** enforced by Jacoco
- **All public methods** must have at least one test
- **Edge cases** must be tested (null inputs, empty strings, boundary values)
- **Error paths** must be tested (exceptions, validation failures)

Run coverage report:

```bash
# Generate Jacoco coverage report
mvn clean test jacoco:report

# View report
open target/site/jacoco/index.html
```

### Test Organization

**Arrange-Act-Assert Pattern**:

```java
@Test
void testMethodName_shouldExpectedBehavior() {
  // Arrange: Set up test data and dependencies
  String sql = "SELECT * FROM user";
  SqlContext context = buildContext(sql);
  
  // Act: Execute the method under test
  ValidationResult result = validator.validate(context);
  
  // Assert: Verify expected outcomes
  assertTrue(result.isPassed());
  assertEquals(0, result.getViolations().size());
}
```

### Test Data Builders

Use builder pattern for complex test objects:

```java
// Test data builder
public class SqlContextTestBuilder {
  private String sql = "SELECT * FROM user";
  private SqlCommandType type = SqlCommandType.SELECT;
  private String mapperId = "com.example.UserMapper.selectAll";
  
  public SqlContextTestBuilder sql(String sql) {
    this.sql = sql;
    return this;
  }
  
  public SqlContextTestBuilder type(SqlCommandType type) {
    this.type = type;
    return this;
  }
  
  public SqlContext build() {
    return SqlContext.builder()
        .sql(sql)
        .type(type)
        .mapperId(mapperId)
        .build();
  }
}

// Usage in tests
@Test
void testExample() {
  SqlContext context = new SqlContextTestBuilder()
      .sql("DELETE FROM user")
      .type(SqlCommandType.DELETE)
      .build();
  // ...
}
```

### Integration Tests

Integration tests verify interactions between multiple components:

```java
@Test
void testValidatorIntegration_shouldExecuteAllCheckers() {
  // Arrange: Set up real dependencies (not mocks)
  JSqlParserFacade parser = new JSqlParserFacade();
  List<RuleChecker> checkers = Arrays.asList(
      new NoWhereClauseChecker(new NoWhereClauseConfig()),
      new DummyConditionChecker(new DummyConditionConfig())
  );
  RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
  DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(
      parser, orchestrator, new SqlDeduplicationFilter()
  );
  
  // Act: Execute validation
  String sql = "DELETE FROM user WHERE 1=1";
  SqlContext context = SqlContext.builder()
      .sql(sql)
      .type(SqlCommandType.DELETE)
      .mapperId("test.mapper")
      .build();
  ValidationResult result = validator.validate(context);
  
  // Assert: Verify both checkers detected violations
  assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
  assertEquals(2, result.getViolations().size()); // NoWhereClause + DummyCondition
}
```

### Performance Tests

Performance tests verify latency and throughput requirements:

```java
@Test
void testValidationPerformance_shouldCompleteUnder10ms() {
  // Arrange
  String sql = "SELECT * FROM user WHERE id = ?";
  SqlContext context = buildContext(sql);
  
  // Warm up JVM
  for (int i = 0; i < 1000; i++) {
    validator.validate(context);
  }
  
  // Act: Measure validation time
  long startTime = System.nanoTime();
  for (int i = 0; i < 10000; i++) {
    validator.validate(context);
  }
  long endTime = System.nanoTime();
  
  // Assert: Average time < 10ms
  long avgTimeMs = (endTime - startTime) / 10000 / 1_000_000;
  assertTrue(avgTimeMs < 10, "Average validation time: " + avgTimeMs + "ms");
}
```

## Pull Request Process

### Before Submitting PR

1. **Ensure all tests pass**:
   ```bash
   mvn clean test
   ```

2. **Ensure Checkstyle passes**:
   ```bash
   mvn checkstyle:check
   ```

3. **Ensure test coverage meets minimum 80%**:
   ```bash
   mvn jacoco:report
   ```

4. **Update documentation** if adding new features:
   - Update README.md with usage examples
   - Update CHANGELOG.md with changes
   - Add Javadoc to all public APIs

5. **Commit with descriptive message**:
   ```bash
   git commit -m "feat: add CountStarChecker for detecting COUNT(*) without WHERE"
   ```

### PR Description Template

```markdown
## Summary
Brief description of changes (1-2 sentences).

## Motivation
Why is this change needed? What problem does it solve?

## Changes
- Added `CountStarChecker` to detect COUNT(*) without WHERE clause
- Updated `DefaultSqlSafetyValidator` to register new checker
- Added 15 unit tests with 100% coverage
- Updated CHANGELOG.md with new feature

## Testing
- All existing tests pass (1000+ tests)
- Added 15 new tests for CountStarChecker
- Coverage: 95% (target: 80%)
- Manual testing: Verified with sample project

## Breaking Changes
None / List breaking changes if any

## Checklist
- [x] Tests written before implementation (TDD)
- [x] All tests pass (`mvn test`)
- [x] Checkstyle passes (`mvn checkstyle:check`)
- [x] Coverage meets minimum 80% (`mvn jacoco:report`)
- [x] Javadoc added to all public APIs
- [x] CHANGELOG.md updated
- [x] README.md updated (if applicable)
```

### Review Criteria

Pull requests will be reviewed for:

1. **Code Quality**:
   - Follows Google Java Style
   - No Checkstyle violations
   - Clear variable/method names
   - Appropriate use of design patterns

2. **Test Coverage**:
   - Tests written before implementation (TDD)
   - Minimum 80% coverage
   - Edge cases tested
   - Integration tests for multi-component features

3. **Javadoc**:
   - All public classes/methods documented
   - `@param`, `@return`, `@throws` tags present
   - Code examples for complex APIs

4. **Design Alignment**:
   - Follows existing architecture patterns
   - Maintains backward compatibility
   - Uses appropriate extension points

5. **Documentation**:
   - CHANGELOG.md updated
   - README.md updated (if applicable)
   - Migration guide for breaking changes

### Addressing Feedback

1. **Respond to all comments**: Acknowledge feedback and explain changes
2. **Make requested changes**: Update code based on reviewer feedback
3. **Re-request review**: Notify reviewers after addressing feedback
4. **Squash commits**: Combine multiple commits into logical units before merge

### Merging

Once approved:

1. **Squash commits** into single commit with descriptive message
2. **Delete feature branch** after merge
3. **Verify CI/CD pipeline** passes after merge

## How to Add New Rule Checker

This tutorial walks through adding a custom rule checker to detect `SELECT COUNT(*)` on large tables without WHERE clause.

### Step 1: Write Test Class First (TDD)

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
  }

  @Test
  void testCountStarWithWhere_shouldPass() throws Exception {
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
  void testRegularSelect_shouldPass() throws Exception {
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
  }

  @Test
  void testDisabledChecker_shouldSkip() throws Exception {
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

    assertTrue(result.isPassed()); // Disabled checker doesn't add violations
  }
}
```

### Step 2: Run Tests (Should Fail)

```bash
mvn test -Dtest=CountStarCheckerTest
```

Expected output: **Compilation error** (classes don't exist yet).

### Step 3: Implement CountStarConfig

Create `CountStarConfig.java` in `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/`:

```java
package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration for CountStarChecker.
 *
 * <p>Detects SELECT COUNT(*) on large tables without WHERE clause, which can cause
 * full table scans and performance degradation.</p>
 */
public class CountStarConfig extends CheckerConfig {
  private boolean enabled = true;

  public CountStarConfig() {
    super();
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
```

### Step 4: Implement CountStarChecker

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

/**
 * Checker for detecting COUNT(*) without WHERE clause.
 *
 * <p>COUNT(*) on large tables without WHERE clause causes full table scans,
 * leading to performance degradation and potential database overload.</p>
 *
 * <p><strong>Risk Level:</strong> MEDIUM</p>
 * <p><strong>Rationale:</strong> While not as critical as DELETE/UPDATE without WHERE,
 * COUNT(*) can still cause significant performance issues on large tables.</p>
 */
public class CountStarChecker extends AbstractRuleChecker {

  private final CountStarConfig config;

  public CountStarChecker(CountStarConfig config) {
    super(config);
    this.config = config;
  }

  @Override
  public void check(SqlContext context, ValidationResult result) {
    if (!isEnabled()) {
      return;
    }

    if (context.getType() != SqlCommandType.SELECT) {
      return;
    }

    Statement stmt = context.getParsedSql();
    if (stmt == null || !(stmt instanceof Select)) {
      return;
    }

    Select select = (Select) stmt;
    if (!(select.getSelectBody() instanceof PlainSelect)) {
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
    }
  }

  /**
   * Checks if SELECT contains COUNT(*) function.
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

### Step 5: Run Tests (Should Pass)

```bash
mvn test -Dtest=CountStarCheckerTest
```

Expected output: **All tests pass** (green).

### Step 6: Register in DefaultSqlSafetyValidator

Update `DefaultSqlSafetyValidator.java` to include new checker:

```java
public class DefaultSqlSafetyValidator implements SqlSafetyValidator {
  
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
        // ... other checkers
        new CountStarChecker(new CountStarConfig()) // Add new checker
    );
    
    this.orchestrator = new RuleCheckerOrchestrator(checkers);
  }
}
```

### Step 7: Add Configuration Support

Update `SqlGuardConfig.java` to include CountStarConfig:

```java
public class SqlGuardConfig {
  private CountStarConfig countStar = new CountStarConfig();
  
  public CountStarConfig getCountStar() {
    return countStar;
  }
  
  public void setCountStar(CountStarConfig countStar) {
    this.countStar = countStar;
  }
}
```

### Step 8: Add Integration Test

Create integration test in `RuleCheckerIntegrationTest.java`:

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
      .mapperId("test.mapper")
      .build();
  
  ValidationResult result = validator.validate(context);
  
  // Assert: Violation detected
  assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
  assertTrue(result.getViolations().stream()
      .anyMatch(v -> v.getMessage().contains("COUNT(*)")));
}
```

### Step 9: Update Documentation

Update `README.md` with new rule:

```markdown
### Validation Rules

- **NoWhereClause** (CRITICAL): Detects DELETE/UPDATE/SELECT without WHERE clause
- **DummyCondition** (HIGH): Detects dummy conditions like "1=1" or "true"
- **CountStar** (MEDIUM): Detects COUNT(*) without WHERE clause ← NEW
- ... (other rules)
```

Update `CHANGELOG.md`:

```markdown
## [Unreleased]

### Added
- CountStarChecker: Detects SELECT COUNT(*) without WHERE clause (MEDIUM risk)
```

### Step 10: Run Full Test Suite

```bash
# Run all tests
mvn clean test

# Verify coverage
mvn jacoco:report
open target/site/jacoco/index.html

# Verify Checkstyle
mvn checkstyle:check
```

Expected output: **All tests pass, coverage >80%, no Checkstyle violations**.

## How to Support New JDBC Pool

This tutorial walks through adding SQL validation support for Tomcat JDBC Pool.

### Step 1: Identify Interception Mechanism

Research Tomcat JDBC Pool documentation to find extension point:

- **Extension Point**: `JdbcInterceptor` interface
- **Interception Method**: `invoke(Object proxy, Method method, Object[] args)`
- **Registration**: Set via `PoolProperties.setJdbcInterceptors()`

### Step 2: Write Test Class First (TDD)

Create `TomcatSqlSafetyInterceptorTest.java` in `sql-guard-jdbc/src/test/java/com/footstone/sqlguard/interceptor/tomcat/`:

```java
package com.footstone.sqlguard.interceptor.tomcat;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TomcatSqlSafetyInterceptorTest {

  private DataSource dataSource;

  @BeforeEach
  void setUp() {
    PoolProperties props = new PoolProperties();
    props.setUrl("jdbc:h2:mem:test");
    props.setDriverClassName("org.h2.Driver");
    props.setJdbcInterceptors(
        "com.footstone.sqlguard.interceptor.tomcat.TomcatSqlSafetyInterceptor"
    );
    props.setProperty("sqlguard.strategy", "BLOCK");
    
    dataSource = new DataSource(props);
  }

  @AfterEach
  void tearDown() {
    dataSource.close();
  }

  @Test
  void testPrepareStatement_withViolation_shouldThrowException() {
    String sql = "DELETE FROM user"; // Missing WHERE clause
    
    SQLException exception = assertThrows(SQLException.class, () -> {
      try (Connection conn = dataSource.getConnection()) {
        conn.prepareStatement(sql);
      }
    });
    
    assertTrue(exception.getMessage().contains("SQL validation failed"));
  }

  @Test
  void testPrepareStatement_withoutViolation_shouldSucceed() throws SQLException {
    String sql = "SELECT * FROM user WHERE id = ?";
    
    try (Connection conn = dataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
      assertNotNull(stmt);
    }
  }

  @Test
  void testWarnStrategy_shouldLogButNotThrow() throws SQLException {
    PoolProperties props = new PoolProperties();
    props.setUrl("jdbc:h2:mem:test");
    props.setDriverClassName("org.h2.Driver");
    props.setJdbcInterceptors(
        "com.footstone.sqlguard.interceptor.tomcat.TomcatSqlSafetyInterceptor"
    );
    props.setProperty("sqlguard.strategy", "WARN");
    
    DataSource warnDataSource = new DataSource(props);
    
    String sql = "DELETE FROM user"; // Violation, but WARN mode
    
    try (Connection conn = warnDataSource.getConnection();
         PreparedStatement stmt = conn.prepareStatement(sql)) {
      assertNotNull(stmt); // Should succeed despite violation
    } finally {
      warnDataSource.close();
    }
  }
}
```

### Step 3: Implement TomcatSqlSafetyInterceptor

Create `TomcatSqlSafetyInterceptor.java` in `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/tomcat/`:

```java
package com.footstone.sqlguard.interceptor.tomcat;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.config.SqlGuardConfigDefaults;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import java.lang.reflect.Method;
import java.sql.SQLException;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.JdbcInterceptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tomcat JDBC Pool interceptor for SQL validation.
 *
 * <p>This interceptor validates SQL statements at JDBC level before execution,
 * providing protection for applications using Tomcat JDBC Pool.</p>
 *
 * <p><strong>Configuration:</strong></p>
 * <pre>{@code
 * PoolProperties props = new PoolProperties();
 * props.setJdbcInterceptors(
 *     "com.footstone.sqlguard.interceptor.tomcat.TomcatSqlSafetyInterceptor"
 * );
 * props.setProperty("sqlguard.strategy", "BLOCK"); // BLOCK/WARN/LOG
 * }</pre>
 */
public class TomcatSqlSafetyInterceptor extends JdbcInterceptor {

  private static final Logger log = LoggerFactory.getLogger(TomcatSqlSafetyInterceptor.class);

  private DefaultSqlSafetyValidator validator;
  private ViolationStrategy strategy;

  @Override
  public void reset(ConnectionPool parent, PooledConnection con) {
    // Initialize validator from pool properties
    if (validator == null) {
      SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();
      validator = new DefaultSqlSafetyValidator(
          new JSqlParserFacade(),
          config,
          new SqlDeduplicationFilter()
      );
      
      String strategyStr = parent.getPoolProperties()
          .getProperty("sqlguard.strategy", "BLOCK");
      strategy = ViolationStrategy.valueOf(strategyStr.toUpperCase());
      
      log.info("TomcatSqlSafetyInterceptor initialized with strategy: {}", strategy);
    }
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // Intercept prepareStatement() and createStatement()
    if ("prepareStatement".equals(method.getName()) && args.length > 0) {
      String sql = (String) args[0];
      validateSql(sql);
    } else if ("prepareCall".equals(method.getName()) && args.length > 0) {
      String sql = (String) args[0];
      validateSql(sql);
    }
    
    return super.invoke(proxy, method, args);
  }

  private void validateSql(String sql) throws SQLException {
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(detectCommandType(sql))
        .mapperId("jdbc.tomcat:datasource")
        .build();
    
    ValidationResult result = validator.validate(context);
    strategy.handle(result);
  }

  private SqlCommandType detectCommandType(String sql) {
    String trimmed = sql.trim().toUpperCase();
    if (trimmed.startsWith("SELECT")) {
      return SqlCommandType.SELECT;
    } else if (trimmed.startsWith("UPDATE")) {
      return SqlCommandType.UPDATE;
    } else if (trimmed.startsWith("DELETE")) {
      return SqlCommandType.DELETE;
    } else if (trimmed.startsWith("INSERT")) {
      return SqlCommandType.INSERT;
    } else {
      return SqlCommandType.UNKNOWN;
    }
  }
}
```

### Step 4: Add ViolationStrategy Enum

Create `ViolationStrategy.java` in same package:

```java
package com.footstone.sqlguard.interceptor.tomcat;

import com.footstone.sqlguard.core.model.ValidationResult;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for handling SQL validation violations in Tomcat JDBC interceptor.
 */
public enum ViolationStrategy {
  
  LOG,
  WARN,
  BLOCK;

  private static final Logger log = LoggerFactory.getLogger(ViolationStrategy.class);

  public void handle(ValidationResult result) throws SQLException {
    if (result.isPassed()) {
      return;
    }

    switch (this) {
      case BLOCK:
        throw new SQLException(
            "SQL validation failed: " + result.getViolations(),
            "42000"
        );
      case WARN:
        log.warn("SQL validation warnings: {}", result.getViolations());
        break;
      case LOG:
        log.info("SQL validation info: {}", result.getViolations());
        break;
    }
  }
}
```

### Step 5: Run Tests

```bash
mvn test -Dtest=TomcatSqlSafetyInterceptorTest
```

Expected output: **All tests pass**.

### Step 6: Add Spring Boot Auto-Configuration

Create `TomcatJdbcAutoConfiguration.java` in `sql-guard-spring-boot-starter`:

```java
@Configuration
@ConditionalOnClass(name = "org.apache.tomcat.jdbc.pool.DataSource")
@ConditionalOnProperty(prefix = "sql-guard.interceptors.tomcat", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TomcatJdbcAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public TomcatSqlSafetyInterceptor tomcatSqlSafetyInterceptor(
      SqlSafetyValidator validator) {
    return new TomcatSqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);
  }
}
```

### Step 7: Document Usage

Create `tomcat-jdbc-setup.md` in `sql-guard-jdbc/docs/`:

```markdown
# Tomcat JDBC Pool Integration

## Maven Dependency

\`\`\`xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-jdbc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.apache.tomcat</groupId>
    <artifactId>tomcat-jdbc</artifactId>
    <version>10.1.0</version>
</dependency>
\`\`\`

## Configuration

\`\`\`java
PoolProperties props = new PoolProperties();
props.setUrl("jdbc:mysql://localhost:3306/mydb");
props.setDriverClassName("com.mysql.cj.jdbc.Driver");
props.setJdbcInterceptors(
    "com.footstone.sqlguard.interceptor.tomcat.TomcatSqlSafetyInterceptor"
);
props.setProperty("sqlguard.strategy", "BLOCK"); // BLOCK/WARN/LOG

DataSource dataSource = new DataSource(props);
\`\`\`

## Spring Boot Auto-Configuration

\`\`\`yaml
sql-guard:
  interceptors:
    tomcat:
      enabled: true
      strategy: BLOCK
\`\`\`
```

### Step 8: Update CHANGELOG.md

```markdown
## [Unreleased]

### Added
- Tomcat JDBC Pool interceptor support
```

## Documentation Standards

### Javadoc Requirements

All public classes and methods must have comprehensive Javadoc:

```java
/**
 * Brief one-sentence summary ending with period.
 *
 * <p>Detailed description with multiple paragraphs if needed. Use HTML tags
 * for formatting: {@code code}, {@link ClassName}, <strong>bold</strong>.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SqlContext context = SqlContext.builder()
 *     .sql("SELECT * FROM user")
 *     .type(SqlCommandType.SELECT)
 *     .build();
 * }</pre>
 *
 * @param context the SQL execution context containing statement and metadata
 * @return ValidationResult containing violations and overall risk level; never null
 * @throws IllegalArgumentException if context is null or invalid
 * @see RelatedClass
 * @since 1.0.0
 */
public ValidationResult validate(SqlContext context) {
  // ...
}
```

### README Updates

When adding new features, update README.md with:

- Feature description in "Features" section
- Usage example in "Quick Start" section
- Configuration example if applicable

### CHANGELOG Updates

Every PR must update CHANGELOG.md in the `[Unreleased]` section:

```markdown
## [Unreleased]

### Added
- New feature description

### Changed
- Modified behavior description

### Fixed
- Bug fix description
```

## Community Guidelines

### Code of Conduct

- Be respectful and inclusive
- Provide constructive feedback
- Focus on code quality, not personal criticism
- Welcome newcomers and help them learn

### Getting Help

- **GitHub Issues**: Bug reports and feature requests
- **GitHub Discussions**: Questions and general discussion
- **Stack Overflow**: Tag questions with `sql-safety-guard`

### Reporting Bugs

Use GitHub Issues with template:

```markdown
## Bug Description
Brief description of the bug.

## Steps to Reproduce
1. Step 1
2. Step 2
3. Step 3

## Expected Behavior
What should happen.

## Actual Behavior
What actually happens.

## Environment
- Java version: 11
- Maven version: 3.8.1
- OS: macOS 12.0
- sql-guard version: 1.0.0-SNAPSHOT

## Logs
Paste relevant logs here.
```

### Feature Requests

Use GitHub Issues with template:

```markdown
## Feature Description
Brief description of the feature.

## Use Case
Why is this feature needed? What problem does it solve?

## Proposed Solution
How should this feature work?

## Alternatives Considered
What other approaches were considered?
```

## License

By contributing to SQL Safety Guard, you agree that your contributions will be licensed under the same license as the project.

---

Thank you for contributing to SQL Safety Guard! Your contributions help make SQL safer for everyone.















