# SQL Safety Guard System - Architecture

## System Overview

SQL Safety Guard is a production-ready dual-layer SQL security framework preventing catastrophic database incidents in Java applications. The system provides comprehensive protection through **static code scanning** at build time and **runtime validation** at execution time.

### Core Capabilities

- **Static Analysis**: Scan SQL in XML mappers, Java annotations, and QueryWrapper usage before deployment
- **Runtime Validation**: Intercept and validate dynamically generated SQL at execution time
- **10 Validation Rules**: Detect missing WHERE clauses, dummy conditions, pagination abuse, blacklist/whitelist violations
- **Multi-Framework Support**: MyBatis, MyBatis-Plus, JDBC connection pools (Druid, HikariCP), universal P6Spy fallback
- **Build Tool Integration**: Maven plugin, Gradle plugin, standalone CLI tool
- **Spring Boot Starter**: Zero-configuration auto-configuration with type-safe YAML properties

### Design Principles

1. **Performance**: <5% runtime overhead through parse-once optimization, LRU caching, and ThreadLocal deduplication
2. **Zero False Negatives**: CRITICAL rules (missing WHERE, logical pagination) guarantee detection with no false negatives
3. **Extensibility**: SPI-based extension points for custom rules, JDBC interceptors, and config centers
4. **Fail-Safe**: Lenient parsing mode ensures validation failures don't break application execution
5. **Production-Ready**: 1000+ tests, comprehensive error handling, phased rollout support (LOG→WARN→BLOCK)

## Module Structure

The system consists of 9 Maven modules organized into three functional layers:

```
SQL Safety Guard System
├── Static Analysis Layer (Build-Time)
│   ├── sql-scanner-core        [Core scanning engine]
│   ├── sql-scanner-cli         [Standalone CLI tool]
│   ├── sql-scanner-maven       [Maven plugin]
│   └── sql-scanner-gradle      [Gradle plugin]
│
├── Runtime Validation Layer (Execution-Time)
│   ├── sql-guard-core          [Validation engine]
│   ├── sql-guard-mybatis       [MyBatis interceptor]
│   ├── sql-guard-mp            [MyBatis-Plus interceptor]
│   └── sql-guard-jdbc          [JDBC interceptors: Druid/HikariCP/P6Spy]
│
└── Integration Layer
    └── sql-guard-spring-boot-starter [Spring Boot auto-configuration]
```

### Module Dependency Graph

```
                    ┌─────────────────────┐
                    │  sql-scanner-cli    │
                    └──────────┬──────────┘
                               │
    ┌──────────────────────────┼──────────────────────────┐
    │                          │                          │
┌───▼──────────────┐  ┌────────▼────────┐  ┌─────────────▼────────┐
│ sql-scanner-maven│  │sql-scanner-gradle│  │  sql-scanner-core    │
└──────────────────┘  └─────────────────┘  └──────────┬───────────┘
                                                       │
                                                       │ (uses validator)
                                                       │
                    ┌──────────────────────────────────▼───────────┐
                    │          sql-guard-core                      │
                    │  (Validation Engine + Rule Checkers)         │
                    └──────────────────┬───────────────────────────┘
                                       │
            ┌──────────────────────────┼──────────────────────────┐
            │                          │                          │
    ┌───────▼────────┐      ┌──────────▼─────────┐      ┌────────▼──────────┐
    │sql-guard-mybatis│      │  sql-guard-mp      │      │  sql-guard-jdbc   │
    └────────────────┘      └────────────────────┘      └───────────────────┘
            │                          │                          │
            └──────────────────────────┼──────────────────────────┘
                                       │
                         ┌─────────────▼──────────────┐
                         │sql-guard-spring-boot-starter│
                         └────────────────────────────┘
```

### Module Descriptions

#### Static Analysis Modules

**sql-scanner-core**
- **Purpose**: Core SQL scanning and parsing engine
- **Key Classes**: 
  - `SqlScanner` - Orchestrates scanning across multiple parsers
  - `XmlMapperParser` - Parses MyBatis XML mappers with dynamic SQL variant generation
  - `AnnotationParser` - Extracts SQL from `@Select/@Update/@Delete/@Insert` annotations
  - `QueryWrapperScanner` - Detects MyBatis-Plus QueryWrapper usage
  - `ConsoleReportGenerator` / `HtmlReportGenerator` - Dual-format report output
- **Public APIs**: `SqlScanner`, `ScanReport`, `SqlEntry`, `ReportProcessor`
- **Dependencies**: sql-guard-core (validator integration), JSqlParser, JavaParser, DOM4J
- **Extension Points**: Custom `SqlParser` implementations

**sql-scanner-cli**
- **Purpose**: Standalone command-line tool for CI/CD integration
- **Key Classes**: `SqlScannerCli` - Picocli-based CLI with argument parsing
- **Public APIs**: CLI arguments (`--project-path`, `--config-file`, `--output-format`, `--fail-on-critical`)
- **Dependencies**: sql-scanner-core, picocli 4.7.5
- **Extension Points**: None (consumer module)

**sql-scanner-maven**
- **Purpose**: Maven plugin for build-time scanning
- **Key Classes**: `SqlGuardScanMojo` - Maven goal `mvn sqlguard:scan`
- **Public APIs**: Maven configuration properties (projectPath, configFile, outputFormat, failOnCritical)
- **Dependencies**: sql-scanner-core, Maven Plugin API
- **Extension Points**: None (consumer module)

**sql-scanner-gradle**
- **Purpose**: Gradle plugin for build-time scanning
- **Key Classes**: 
  - `SqlGuardPlugin` - Gradle plugin registration
  - `SqlGuardExtension` - DSL configuration
  - `SqlGuardScanTask` - Gradle task `gradle sqlguardScan`
- **Public APIs**: Gradle DSL (`sqlGuard { ... }`)
- **Dependencies**: sql-scanner-core, Gradle API (provided)
- **Extension Points**: None (consumer module)

#### Runtime Validation Modules

**sql-guard-core**
- **Purpose**: Runtime SQL validation engine with rule checkers
- **Key Classes**:
  - `SqlSafetyValidator` / `DefaultSqlSafetyValidator` - Main validation interface
  - `RuleChecker` / `AbstractRuleChecker` - Rule checker contract
  - `RuleCheckerOrchestrator` - Chain of Responsibility coordinator
  - `SqlDeduplicationFilter` - ThreadLocal LRU cache for deduplication
  - `JSqlParserFacade` - SQL parsing with LRU cache
  - 10 Rule Checkers: `NoWhereClauseChecker`, `DummyConditionChecker`, `BlacklistFieldChecker`, `WhitelistFieldChecker`, `LogicalPaginationChecker`, `NoConditionPaginationChecker`, `DeepPaginationChecker`, `LargePageSizeChecker`, `MissingOrderByChecker`, `NoPaginationChecker`
- **Public APIs**: `SqlSafetyValidator`, `RuleChecker`, `SqlContext`, `ValidationResult`, `JSqlParserFacade`
- **Dependencies**: JSqlParser 4.6, SnakeYAML 1.33, SLF4J
- **Extension Points**: Custom `RuleChecker` implementations

**sql-guard-mybatis**
- **Purpose**: MyBatis Executor interceptor for SQL validation
- **Key Classes**: 
  - `SqlSafetyInterceptor` - `@Intercepts` annotation-based interceptor
  - `ViolationStrategy` - Violation handling (BLOCK/WARN/LOG)
- **Public APIs**: `SqlSafetyInterceptor` configuration
- **Dependencies**: sql-guard-core, MyBatis 3.4.6+ or 3.5.13+
- **Extension Points**: None (consumer module)

**sql-guard-mp**
- **Purpose**: MyBatis-Plus InnerInterceptor for SQL validation
- **Key Classes**:
  - `MpSqlSafetyInnerInterceptor` - Implements `InnerInterceptor` interface
  - `ViolationStrategy` - Violation handling (BLOCK/WARN/LOG)
- **Public APIs**: `MpSqlSafetyInnerInterceptor` configuration
- **Dependencies**: sql-guard-core, MyBatis-Plus 3.4.0+ or 3.5.3+
- **Extension Points**: None (consumer module)

**sql-guard-jdbc**
- **Purpose**: JDBC-layer interceptors for connection pools and universal fallback
- **Key Classes**:
  - `DruidSqlSafetyFilter` - Druid FilterAdapter implementation
  - `HikariSqlSafetyProxyFactory` - HikariCP DataSource wrapper with JDK proxies
  - `P6SpySqlSafetyListener` - P6Spy JdbcEventListener for universal JDBC interception
  - `ViolationStrategy` (3 enums) - Violation handling per interceptor
- **Public APIs**: `DruidSqlSafetyFilterConfiguration`, `HikariSqlSafetyConfiguration`, `P6SpySqlSafetyModule`
- **Dependencies**: sql-guard-core, Druid (optional), HikariCP (optional), P6Spy (optional)
- **Extension Points**: Custom JDBC pool interceptors following same pattern

#### Integration Module

**sql-guard-spring-boot-starter**
- **Purpose**: Spring Boot auto-configuration for zero-config integration
- **Key Classes**:
  - `SqlGuardAutoConfiguration` - Auto-configuration with conditional bean creation
  - `SqlGuardProperties` - Type-safe YAML configuration binding
  - `ConfigCenterAdapter` - SPI for custom config centers
  - `ApolloConfigCenterAdapter` - Apollo config center hot-reload support
- **Public APIs**: `SqlGuardProperties`, `ConfigCenterAdapter`, `ConfigChangeEvent`
- **Dependencies**: sql-guard-core, Spring Boot 2.7.18, Apollo Client (optional)
- **Extension Points**: Custom `ConfigCenterAdapter` implementations

## Design Patterns

### 1. Chain of Responsibility (RuleChecker Validation)

**Pattern Usage**: `RuleCheckerOrchestrator` coordinates sequential execution of multiple `RuleChecker` implementations.

**Rationale**:
- Decouples validation logic into independent, testable checkers
- Enables dynamic enabling/disabling of rules via configuration
- Allows multiple violations to be collected without short-circuiting
- Simplifies addition of new validation rules

**Implementation**:

```java
// RuleChecker interface (handler contract)
public interface RuleChecker {
  void check(SqlContext context, ValidationResult result);
  boolean isEnabled();
}

// RuleCheckerOrchestrator (chain coordinator)
public class RuleCheckerOrchestrator {
  private final List<RuleChecker> checkers;
  
  public void executeAll(SqlContext context, ValidationResult result) {
    for (RuleChecker checker : checkers) {
      if (checker.isEnabled()) {
        checker.check(context, result);
      }
    }
  }
}

// Concrete handler example
public class NoWhereClauseChecker extends AbstractRuleChecker {
  @Override
  public void check(SqlContext context, ValidationResult result) {
    if (hasNoWhereClause(context)) {
      result.addViolation(RiskLevel.CRITICAL, "Missing WHERE clause", "Add WHERE condition");
    }
  }
}
```

**Benefits**:
- Single Responsibility: Each checker validates one rule
- Open/Closed Principle: Add new checkers without modifying orchestrator
- Testability: Each checker tested in isolation

### 2. Strategy Pattern (Violation Handling)

**Pattern Usage**: `ViolationStrategy` enum defines interchangeable violation handling strategies across all interceptors.

**Rationale**:
- Enables phased rollout: LOG (observe) → WARN (alert) → BLOCK (enforce)
- Decouples violation detection from violation handling
- Consistent behavior across MyBatis, MyBatis-Plus, and JDBC interceptors

**Implementation**:

```java
public enum ViolationStrategy {
  LOG,    // Log violation, continue execution
  WARN,   // Log warning, continue execution
  BLOCK;  // Throw SQLException, halt execution
  
  public void handle(ValidationResult result) {
    switch (this) {
      case BLOCK:
        if (!result.isPassed()) {
          throw new SQLException("SQL validation failed: " + result.getViolations());
        }
        break;
      case WARN:
        if (!result.isPassed()) {
          log.warn("SQL validation warnings: {}", result.getViolations());
        }
        break;
      case LOG:
        if (!result.isPassed()) {
          log.info("SQL validation info: {}", result.getViolations());
        }
        break;
    }
  }
}
```

**Benefits**:
- Supports gradual enforcement in production
- Reduces risk of breaking existing applications
- Enables A/B testing of validation rules

### 3. Builder Pattern (SqlContext Construction)

**Pattern Usage**: `SqlContext.Builder` provides fluent API for constructing immutable context objects.

**Rationale**:
- SqlContext has 7 fields (sql, parsedSql, type, mapperId, params, datasource, rowBounds)
- Different interceptors provide different subsets of fields
- Immutability prevents accidental modification during validation chain
- Validation at build time ensures required fields are present

**Implementation**:

```java
public final class SqlContext {
  private final String sql;
  private final Statement parsedSql;
  private final SqlCommandType type;
  private final String mapperId;
  // ... other fields
  
  private SqlContext(SqlContextBuilder builder) {
    this.sql = builder.sql;
    this.parsedSql = builder.parsedSql;
    // ... copy other fields
  }
  
  public static SqlContextBuilder builder() {
    return new SqlContextBuilder();
  }
  
  public static class SqlContextBuilder {
    public SqlContextBuilder sql(String sql) { this.sql = sql; return this; }
    public SqlContextBuilder type(SqlCommandType type) { this.type = type; return this; }
    // ... other setters
    
    public SqlContext build() {
      // Validation logic
      if (sql == null || sql.trim().isEmpty()) {
        throw new IllegalArgumentException("sql cannot be null or empty");
      }
      return new SqlContext(this);
    }
  }
}
```

**Benefits**:
- Immutability prevents bugs from shared mutable state
- Fluent API improves readability
- Build-time validation catches errors early

### 4. Visitor Pattern (JSqlParser AST Traversal)

**Pattern Usage**: JSqlParser's `Visitor` interface for traversing SQL Abstract Syntax Trees.

**Rationale**:
- SQL AST has 50+ node types (Select, Update, Delete, Where, Join, etc.)
- Visitor pattern separates traversal logic from node structure
- Enables extraction of specific information (fields, tables, conditions) without modifying JSqlParser

**Implementation**:

```java
// FieldExtractorVisitor extracts all field references from SQL
public class FieldExtractorVisitor implements SelectVisitor, FromItemVisitor, ExpressionVisitor {
  private final Set<String> fields = new HashSet<>();
  
  @Override
  public void visit(Column column) {
    fields.add(column.getColumnName());
  }
  
  @Override
  public void visit(PlainSelect plainSelect) {
    // Visit WHERE clause
    if (plainSelect.getWhere() != null) {
      plainSelect.getWhere().accept(this);
    }
  }
  
  public Set<String> getFields() {
    return Collections.unmodifiableSet(fields);
  }
}

// Usage in RuleChecker
FieldExtractorVisitor visitor = new FieldExtractorVisitor();
statement.accept(visitor);
Set<String> fields = visitor.getFields();
```

**Benefits**:
- Separation of concerns: traversal vs. business logic
- Reusability: Same visitor for multiple checkers
- Extensibility: Add new visitors without modifying AST classes

### 5. Factory Pattern (Interceptor Creation)

**Pattern Usage**: Spring Boot auto-configuration creates interceptors based on classpath detection.

**Rationale**:
- Different applications use different frameworks (MyBatis, MyBatis-Plus, JDBC pools)
- Conditional bean creation prevents classpath conflicts
- Centralizes interceptor instantiation logic

**Implementation**:

```java
@Configuration
@ConditionalOnClass(Executor.class) // MyBatis Executor on classpath
public class SqlGuardAutoConfiguration {
  
  @Bean
  @ConditionalOnMissingBean
  public SqlSafetyInterceptor sqlSafetyInterceptor(SqlSafetyValidator validator) {
    return new SqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);
  }
  
  @Bean
  @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.MybatisConfiguration")
  public MpSqlSafetyInnerInterceptor mpSqlSafetyInnerInterceptor(SqlSafetyValidator validator) {
    return new MpSqlSafetyInnerInterceptor(validator, ViolationStrategy.BLOCK);
  }
}
```

**Benefits**:
- Automatic detection of available frameworks
- Prevents runtime errors from missing dependencies
- User override support via `@ConditionalOnMissingBean`

## Data Flow Diagrams

### Static Scanning Flow

```
┌─────────────┐
│ Build Tool  │ (Maven/Gradle/CLI)
└──────┬──────┘
       │
       ▼
┌──────────────────────────────────────────────────┐
│          SqlScanner Orchestration                │
│  1. Load configuration (YAML + defaults)         │
│  2. Initialize parsers (XML/Annotation/Wrapper)  │
│  3. Scan project directory                       │
└──────┬───────────────────────────────────────────┘
       │
       ├──────────────────┬──────────────────┬──────────────────┐
       ▼                  ▼                  ▼                  ▼
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│XmlMapper    │  │ Annotation  │  │QueryWrapper │  │  Validator  │
│Parser       │  │Parser       │  │Scanner      │  │ Integration │
│             │  │             │  │             │  │             │
│ DOM4J+SAX   │  │ JavaParser  │  │ JavaParser  │  │DefaultSql   │
│ Dynamic SQL │  │ @Select/    │  │ Package     │  │SafetyValid  │
│ Variants    │  │ @Update/etc │  │ Verification│  │ator         │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │                │
       └────────────────┴────────────────┴────────────────┘
                        │
                        ▼
              ┌──────────────────┐
              │   SqlEntry List  │
              │  (with violations)│
              └─────────┬─────────┘
                        │
                        ▼
              ┌──────────────────┐
              │ ReportProcessor  │
              │  - Aggregate     │
              │  - Statistics    │
              └─────────┬─────────┘
                        │
                ┌───────┴───────┐
                ▼               ▼
        ┌──────────────┐ ┌──────────────┐
        │   Console    │ │     HTML     │
        │   Report     │ │    Report    │
        │ (ANSI colors)│ │ (sortable)   │
        └──────────────┘ └──────────────┘
```

### Runtime Interception Flow

```
┌─────────────────────────────────────────────────┐
│         Application SQL Execution               │
│  (MyBatis Mapper / QueryWrapper / JDBC)         │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│            Interceptor Layer                    │
│  ┌─────────────────────────────────────┐        │
│  │ MyBatis Interceptor                 │        │
│  │  @Intercepts(Executor.class)        │        │
│  └─────────────────────────────────────┘        │
│  ┌─────────────────────────────────────┐        │
│  │ MyBatis-Plus InnerInterceptor       │        │
│  │  beforeQuery() / beforeUpdate()     │        │
│  └─────────────────────────────────────┘        │
│  ┌─────────────────────────────────────┐        │
│  │ JDBC Interceptor                    │        │
│  │  Druid Filter / HikariCP Proxy /    │        │
│  │  P6Spy Listener                     │        │
│  └─────────────────────────────────────┘        │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│      SqlDeduplicationFilter (ThreadLocal)       │
│  - Check LRU cache (1000 entries, 100ms TTL)    │
│  - Return cached result if hit                  │
└──────────────────┬──────────────────────────────┘
                   │ (cache miss)
                   ▼
┌─────────────────────────────────────────────────┐
│          DefaultSqlSafetyValidator              │
│  1. Parse SQL (JSqlParserFacade with LRU cache) │
│  2. Build SqlContext with parsed AST            │
│  3. Execute RuleCheckerOrchestrator             │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│       RuleCheckerOrchestrator                   │
│  Execute enabled checkers in sequence:          │
│  1. NoWhereClauseChecker                        │
│  2. DummyConditionChecker                       │
│  3. BlacklistFieldChecker                       │
│  4. WhitelistFieldChecker                       │
│  5. LogicalPaginationChecker                    │
│  6. NoConditionPaginationChecker                │
│  7. DeepPaginationChecker                       │
│  8. LargePageSizeChecker                        │
│  9. MissingOrderByChecker                       │
│  10. NoPaginationChecker                        │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│           ValidationResult                      │
│  - Aggregated violations                        │
│  - Highest risk level                           │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│        ViolationStrategy Handling               │
│  - LOG: Log and continue                        │
│  - WARN: Warn and continue                      │
│  - BLOCK: Throw SQLException                    │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│         SQL Execution Proceeds or Halts         │
└─────────────────────────────────────────────────┘
```

## Threading Model

### Validator Thread-Safety

**Design**: `DefaultSqlSafetyValidator` is **immutable after construction** and fully thread-safe.

**Guarantees**:
- All dependencies (JSqlParserFacade, RuleCheckerOrchestrator, checkers) are immutable
- No shared mutable state across validation calls
- Multiple threads can call `validate()` concurrently without synchronization

**Implementation**:

```java
public class DefaultSqlSafetyValidator implements SqlSafetyValidator {
  private final JSqlParserFacade parser;              // immutable
  private final RuleCheckerOrchestrator orchestrator; // immutable
  private final SqlDeduplicationFilter filter;        // ThreadLocal (see below)
  
  // Constructor initializes all dependencies (no setters)
  public DefaultSqlSafetyValidator(JSqlParserFacade parser, 
                                   RuleCheckerOrchestrator orchestrator,
                                   SqlDeduplicationFilter filter) {
    this.parser = parser;
    this.orchestrator = orchestrator;
    this.filter = filter;
  }
  
  @Override
  public ValidationResult validate(SqlContext context) {
    // Thread-safe: no shared mutable state
    if (filter.shouldSkip(context)) {
      return ValidationResult.pass();
    }
    
    Statement parsed = parser.parse(context.getSql());
    SqlContext enriched = SqlContext.builder()
        .from(context)
        .parsedSql(parsed)
        .build();
    
    ValidationResult result = ValidationResult.pass();
    orchestrator.executeAll(enriched, result);
    return result;
  }
}
```

### Deduplication Filter ThreadLocal Design

**Design**: `SqlDeduplicationFilter` uses **ThreadLocal LRU cache** for per-thread deduplication without synchronization.

**Rationale**:
- Each thread has isolated cache (no cross-thread pollution)
- No synchronization overhead (ThreadLocal provides thread isolation)
- LRU eviction prevents unbounded memory growth
- 100ms TTL prevents stale cache entries

**Implementation**:

```java
public class SqlDeduplicationFilter {
  private static final int CACHE_SIZE = 1000;
  private static final long TTL_MS = 100;
  
  private final ThreadLocal<LRUCache<String, CacheEntry>> cache = 
      ThreadLocal.withInitial(() -> new LRUCache<>(CACHE_SIZE));
  
  public boolean shouldSkip(SqlContext context) {
    LRUCache<String, CacheEntry> threadCache = cache.get();
    String cacheKey = context.getSql();
    
    CacheEntry entry = threadCache.get(cacheKey);
    if (entry != null && !entry.isExpired()) {
      return true; // Skip validation (cache hit)
    }
    
    // Cache miss: will validate and cache result
    threadCache.put(cacheKey, new CacheEntry(System.currentTimeMillis()));
    return false;
  }
  
  private static class CacheEntry {
    private final long timestamp;
    
    boolean isExpired() {
      return System.currentTimeMillis() - timestamp > TTL_MS;
    }
  }
}
```

**Thread Safety Characteristics**:
- **Read Operations**: No synchronization needed (ThreadLocal isolation)
- **Write Operations**: No synchronization needed (ThreadLocal isolation)
- **Memory Model**: Each thread has independent cache instance
- **Cleanup**: ThreadLocal cleaned up when thread terminates

### Interceptor Statelessness

**Design**: All interceptors (MyBatis, MyBatis-Plus, JDBC) are **stateless** and thread-safe.

**Guarantees**:
- No instance variables modified during interception
- All dependencies injected via constructor (immutable)
- Multiple threads can execute interception concurrently

**Example**:

```java
@Intercepts({
  @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
  @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlSafetyInterceptor implements Interceptor {
  private final SqlSafetyValidator validator;      // immutable
  private final ViolationStrategy strategy;        // immutable
  
  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    // Stateless: all data from method parameters
    SqlContext context = buildContext(invocation);
    ValidationResult result = validator.validate(context);
    strategy.handle(result);
    return invocation.proceed();
  }
}
```

## Extension Points

### 1. Custom RuleChecker

**Purpose**: Add custom SQL validation rules beyond the 10 built-in checkers.

**Interface**: `com.footstone.sqlguard.validator.rule.RuleChecker`

**Example Use Cases**:
- Detect `SELECT COUNT(*)` on large tables without WHERE
- Enforce table name prefixes (e.g., all tables must start with `t_`)
- Detect subqueries in SELECT list (performance anti-pattern)
- Enforce index hints on specific tables

**Implementation Steps**:

1. Implement `RuleChecker` interface:

```java
public class CountStarChecker extends AbstractRuleChecker {
  private final CountStarConfig config;
  
  public CountStarChecker(CountStarConfig config) {
    super(config);
    this.config = config;
  }
  
  @Override
  public void check(SqlContext context, ValidationResult result) {
    if (context.getType() != SqlCommandType.SELECT) {
      return;
    }
    
    Statement stmt = context.getParsedSql();
    if (!(stmt instanceof Select)) {
      return;
    }
    
    Select select = (Select) stmt;
    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
    
    // Check for COUNT(*) without WHERE
    if (hasCountStar(plainSelect) && plainSelect.getWhere() == null) {
      result.addViolation(
        RiskLevel.MEDIUM,
        "COUNT(*) without WHERE clause on large table",
        "Add WHERE condition or use approximate count"
      );
    }
  }
  
  private boolean hasCountStar(PlainSelect select) {
    for (SelectItem item : select.getSelectItems()) {
      if (item instanceof SelectExpressionItem) {
        Expression expr = ((SelectExpressionItem) item).getExpression();
        if (expr instanceof Function) {
          Function func = (Function) expr;
          if ("COUNT".equalsIgnoreCase(func.getName()) && 
              func.isAllColumns()) {
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

2. Register in `DefaultSqlSafetyValidator`:

```java
List<RuleChecker> checkers = Arrays.asList(
  new NoWhereClauseChecker(config.getNoWhereClause()),
  new DummyConditionChecker(config.getDummyCondition()),
  // ... existing checkers
  new CountStarChecker(config.getCountStar()) // Add custom checker
);
```

3. Add configuration class:

```java
public class CountStarConfig extends CheckerConfig {
  private boolean enabled = true;
  
  // Getters/setters
}
```

**Registration in Spring Boot**:

```java
@Bean
@ConditionalOnMissingBean
public CountStarChecker countStarChecker(SqlGuardProperties properties) {
  return new CountStarChecker(properties.getRules().getCountStar());
}
```

### 2. Custom JDBC Pool Interceptor

**Purpose**: Add SQL validation support for JDBC connection pools not covered by built-in interceptors.

**Supported Pools**: Druid, HikariCP, P6Spy (universal fallback)

**Example Use Cases**:
- Tomcat JDBC Pool
- Apache DBCP2
- C3P0
- Custom connection pool implementations

**Implementation Pattern**: Follow Druid or HikariCP pattern depending on pool extension mechanism.

**Example: Tomcat JDBC Pool Interceptor**

1. Implement `JdbcInterceptor` (Tomcat JDBC extension point):

```java
public class TomcatSqlSafetyInterceptor extends JdbcInterceptor {
  private SqlSafetyValidator validator;
  private ViolationStrategy strategy;
  
  @Override
  public void reset(ConnectionPool parent, PooledConnection con) {
    // Initialize validator from pool properties
    if (validator == null) {
      validator = createValidator();
      strategy = ViolationStrategy.valueOf(
        parent.getPoolProperties().getProperty("sqlguard.strategy", "BLOCK")
      );
    }
  }
  
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // Intercept prepareStatement() and createStatement()
    if ("prepareStatement".equals(method.getName()) && args.length > 0) {
      String sql = (String) args[0];
      validateSql(sql);
    }
    return super.invoke(proxy, method, args);
  }
  
  private void validateSql(String sql) {
    SqlContext context = SqlContext.builder()
        .sql(sql)
        .type(detectCommandType(sql))
        .mapperId("jdbc.tomcat:datasource")
        .build();
    
    ValidationResult result = validator.validate(context);
    strategy.handle(result);
  }
}
```

2. Register interceptor in DataSource configuration:

```java
@Bean
public DataSource dataSource() {
  PoolProperties props = new PoolProperties();
  props.setJdbcInterceptors(
    "com.footstone.sqlguard.interceptor.tomcat.TomcatSqlSafetyInterceptor"
  );
  props.setProperty("sqlguard.strategy", "BLOCK");
  
  return new org.apache.tomcat.jdbc.pool.DataSource(props);
}
```

**Spring Boot Auto-Configuration**:

```java
@Configuration
@ConditionalOnClass(name = "org.apache.tomcat.jdbc.pool.DataSource")
public class TomcatJdbcAutoConfiguration {
  
  @Bean
  @ConditionalOnMissingBean
  public TomcatSqlSafetyInterceptor tomcatSqlSafetyInterceptor(
      SqlSafetyValidator validator) {
    return new TomcatSqlSafetyInterceptor(validator, ViolationStrategy.BLOCK);
  }
}
```

### 3. Custom ConfigCenterAdapter

**Purpose**: Integrate custom configuration centers for hot-reload of SQL Guard configuration.

**Interface**: `com.footstone.sqlguard.spring.config.center.ConfigCenterAdapter`

**Built-in Implementations**: Apollo Config Center

**Example Use Cases**:
- Nacos (when dependency conflicts resolved)
- Consul
- etcd
- Spring Cloud Config
- Custom enterprise config centers

**Implementation Steps**:

1. Implement `ConfigCenterAdapter` interface:

```java
@Configuration
@ConditionalOnClass(name = "io.etcd.jetcd.Client")
@ConditionalOnProperty(prefix = "sql-guard.config-center.etcd", name = "enabled", havingValue = "true")
public class EtcdConfigCenterAdapter implements ConfigCenterAdapter {
  
  private final Client etcdClient;
  private final String configKey;
  private final ConfigReloadListener reloadListener;
  
  public EtcdConfigCenterAdapter(
      @Value("${sql-guard.config-center.etcd.endpoints}") String endpoints,
      @Value("${sql-guard.config-center.etcd.config-key}") String configKey,
      ConfigReloadListener reloadListener) {
    this.etcdClient = Client.builder()
        .endpoints(endpoints.split(","))
        .build();
    this.configKey = configKey;
    this.reloadListener = reloadListener;
    
    // Watch for configuration changes
    watchConfigChanges();
  }
  
  @Override
  public void onConfigChange(ConfigChangeEvent event) {
    if (event.getChangedKeys().contains(configKey)) {
      reloadConfig();
    }
  }
  
  @Override
  public void reloadConfig() {
    try {
      // Fetch latest configuration from etcd
      ByteSequence key = ByteSequence.from(configKey, StandardCharsets.UTF_8);
      GetResponse response = etcdClient.getKVClient().get(key).get();
      
      if (response.getKvs().isEmpty()) {
        return;
      }
      
      String configYaml = response.getKvs().get(0).getValue().toString(StandardCharsets.UTF_8);
      
      // Notify reload listener
      reloadListener.onReload(configYaml);
      
    } catch (Exception e) {
      log.error("Failed to reload config from etcd", e);
    }
  }
  
  private void watchConfigChanges() {
    ByteSequence key = ByteSequence.from(configKey, StandardCharsets.UTF_8);
    Watch.Watcher watcher = etcdClient.getWatchClient().watch(key);
    
    // Listen for watch events in background thread
    CompletableFuture.runAsync(() -> {
      try {
        for (WatchResponse response : watcher) {
          for (WatchEvent event : response.getEvents()) {
            if (event.getEventType() == WatchEvent.EventType.PUT) {
              onConfigChange(new ConfigChangeEvent(Collections.singleton(configKey)));
            }
          }
        }
      } catch (Exception e) {
        log.error("Error watching etcd config changes", e);
      }
    });
  }
  
  @Override
  public String getName() {
    return "EtcdConfigCenterAdapter";
  }
}
```

2. Add configuration properties:

```java
@ConfigurationProperties(prefix = "sql-guard.config-center.etcd")
public class EtcdConfigCenterProperties {
  private boolean enabled = false;
  private String endpoints;
  private String configKey = "/sqlguard/config";
  
  // Getters/setters
}
```

3. Configure in `application.yml`:

```yaml
sql-guard:
  config-center:
    etcd:
      enabled: true
      endpoints: http://localhost:2379
      config-key: /sqlguard/config
```

**Thread Safety**: Implementations must synchronize reload operations to prevent concurrent modification:

```java
@Override
public synchronized void reloadConfig() {
  // Synchronized to prevent concurrent reloads
  // ...
}
```

## Performance Characteristics

### Parse-Once Optimization

**Technique**: SQL parsed once by `JSqlParserFacade`, AST reused by all checkers.

**Impact**: Eliminates 10x parsing overhead (one parse vs. 10 parses for 10 checkers).

**Measurement**:
- Without optimization: 10 checkers × 5ms parse time = 50ms overhead
- With optimization: 1 parse × 5ms = 5ms overhead
- **Savings**: 90% reduction in parsing overhead

### LRU Caching

**JSqlParser LRU Cache**:
- Size: 1000 parsed statements
- Eviction: Least Recently Used
- Hit Rate: ~80% in typical applications (same SQL executed repeatedly)

**Deduplication LRU Cache**:
- Size: 1000 SQL strings per thread
- TTL: 100ms
- Scope: ThreadLocal (per-thread isolation)
- Hit Rate: ~50% for duplicate SQL within TTL window

### ThreadLocal Deduplication

**Scenario**: MyBatis + MyBatis-Plus both enabled (double interception).

**Without Deduplication**:
- First interceptor validates SQL (5ms)
- Second interceptor validates same SQL again (5ms)
- **Total**: 10ms overhead

**With Deduplication**:
- First interceptor validates SQL (5ms), caches result
- Second interceptor cache hit, skips validation (0.1ms)
- **Total**: 5.1ms overhead
- **Savings**: ~50% reduction

### Performance Targets

| Component | Target Overhead | Actual Measurement |
|-----------|----------------|-------------------|
| MyBatis Interceptor | <5% | ~3% (measured) |
| MyBatis-Plus InnerInterceptor | <5% | ~4% (measured) |
| Druid Filter | <5% | 7.84% (measured, 1000 queries) |
| HikariCP Proxy | <5% | ~3% (measured) |
| P6Spy Listener | <15% | ~15% (measured, acceptable for universal coverage) |

**Note**: P6Spy has higher overhead due to driver-level proxy + parameter substitution, but provides universal JDBC coverage as fallback.

### Scalability

**Concurrent Validation**:
- Validator is thread-safe and stateless
- No synchronization bottlenecks
- Linear scalability with thread count

**Large SQL Handling**:
- JSqlParser handles SQL up to 100KB efficiently
- LRU cache prevents memory exhaustion from large SQL
- Lenient mode skips unparseable SQL (fail-safe)

## Security Considerations

### SQL Injection Prevention

**Scope**: SQL Safety Guard focuses on **structural safety** (missing WHERE, pagination abuse), not SQL injection.

**Recommendation**: Use prepared statements and parameterized queries to prevent SQL injection. SQL Safety Guard complements but does not replace SQL injection prevention.

### Configuration Security

**Sensitive Data**: Configuration files may contain table names, field names, and validation thresholds.

**Recommendations**:
- Store configuration in secure config centers (Apollo, Nacos, Consul)
- Use environment-specific configurations (dev/staging/prod)
- Restrict access to configuration files in production

### Violation Logging

**Risk**: Violation logs may contain sensitive SQL and parameter values.

**Mitigation**:
- Mask sensitive parameters in logs (e.g., passwords, credit cards)
- Use structured logging with log level filtering
- Rotate logs frequently and restrict access

## Deployment Considerations

### Phased Rollout Strategy

**Phase 1: Observation (LOG mode)**
- Deploy with `ViolationStrategy.LOG`
- Collect violation metrics for 1-2 weeks
- Identify false positives and tune configuration

**Phase 2: Alerting (WARN mode)**
- Switch to `ViolationStrategy.WARN`
- Monitor alert volume and response time
- Refine rules based on production patterns

**Phase 3: Enforcement (BLOCK mode)**
- Switch to `ViolationStrategy.BLOCK`
- Violations halt SQL execution
- Monitor application errors and rollback if needed

### Configuration Management

**Centralized Configuration**:
- Use Apollo/Nacos for hot-reload without redeployment
- Version control configuration changes
- A/B test rule changes with traffic splitting

**Environment-Specific Configuration**:
- **Dev**: Lenient rules, LOG mode, verbose logging
- **Staging**: Production-like rules, WARN mode, test enforcement
- **Production**: Strict rules, BLOCK mode, minimal logging

### Monitoring and Alerting

**Key Metrics**:
- Violation count by rule type
- Violation rate (violations per 1000 SQL executions)
- Validation latency (p50, p95, p99)
- Cache hit rate (JSqlParser cache, deduplication cache)

**Alerting Thresholds**:
- CRITICAL violations > 10/hour → Page on-call engineer
- Validation latency p99 > 50ms → Investigate performance
- Cache hit rate < 50% → Review SQL patterns

### Troubleshooting

**Common Issues**:

1. **High Validation Overhead**
   - Symptom: Validation latency > 10ms
   - Diagnosis: Check JSqlParser cache hit rate
   - Solution: Increase cache size or enable deduplication

2. **False Positives**
   - Symptom: Valid SQL flagged as violation
   - Diagnosis: Review violation message and SQL pattern
   - Solution: Add exclusion pattern or adjust rule threshold

3. **Parse Errors**
   - Symptom: `SqlParseException` in logs
   - Diagnosis: Check SQL syntax and JSqlParser version
   - Solution: Enable lenient mode or upgrade JSqlParser

**Debug Logging**:

```yaml
logging:
  level:
    com.footstone.sqlguard: DEBUG
```

## Version History

See [CHANGELOG.md](CHANGELOG.md) for detailed version history and migration guides.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, code standards, and contribution guidelines.

## License

[Add license information]
