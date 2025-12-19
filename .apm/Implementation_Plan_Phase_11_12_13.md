## Phase 11: JDBC Module Separation - Agent_Core_Engine_Foundation, Agent_Testing_Validation

**Phase Overview:** Extract sql-guard-jdbc into independent connection pool modules (Druid, HikariCP, P6Spy) following minimal dependency principle, creating sql-guard-jdbc-common for shared abstractions, ensuring users only include needed pool modules without dependency pollution, maintaining 100% backward compatibility with existing tests.

### Task 11.1 – TDD Test Case Library Design - Agent_Testing_Validation

1. Read sql-guard-jdbc/src/test/ directory to analyze existing test structure and coverage patterns
2. Design module isolation tests for each module (Druid/HikariCP/P6Spy): independent compilation tests, dependency verification tests, ClassLoader isolation tests
3. Design backward compatibility tests: API compatibility tests, behavior consistency tests, configuration migration tests
4. Design performance baseline tests: module loading overhead tests, runtime performance comparison tests, memory usage comparison tests
5. Create `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md` test design document with complete test specifications

### Task 11.2 – JDBC Common Module Extraction - Agent_Core_Engine_Foundation

1. Create sql-guard-jdbc-common Maven module under sql-guard-jdbc/, configure POM to depend only on sql-guard-core and sql-guard-audit-api
2. Create ViolationStrategy unified enum (BLOCK/WARN/LOG) in sql-guard-jdbc-common, delete duplicate definitions from druid/hikari/p6spy modules
3. Create JdbcInterceptorBase abstract base class implementing template method pattern (interceptSql, buildSqlContext, handleViolation methods)
4. Create JdbcInterceptorConfig interface defining common configuration properties (enabled, strategy, auditEnabled, excludePatterns)
5. Create SqlContextBuilder utility class providing JDBC layer SqlContext construction helper methods
6. Write unit tests for each new class (ViolationStrategyTest, JdbcInterceptorBaseTest, SqlContextBuilderTest)

### Task 11.3 – Druid Module Separation - Agent_Core_Engine_Foundation

1. Create sql-guard-jdbc-druid Maven module, configure POM to depend on jdbc-common and druid:provided scope only
2. Migrate DruidSqlSafetyFilter to new module, refactor to compose JdbcInterceptorBase instead of duplicating logic
3. Create DruidInterceptorConfig implementing JdbcInterceptorConfig, add Druid-specific properties (filterPosition, connectionProxyEnabled)
4. Create DruidSqlSafetyFilterConfiguration with @ConditionalOnClass(DruidDataSource.class) for Spring Boot auto-configuration
5. Write integration tests: DruidModuleIsolationTest (no Hikari/P6Spy dependencies), DruidFilterRefactoringTest, DruidIntegrationTest with H2

### Task 11.4 – HikariCP Module Separation - Agent_Core_Engine_Foundation

1. Create sql-guard-jdbc-hikari Maven module, configure POM to depend on jdbc-common and HikariCP:provided scope only
2. Migrate HikariSqlSafetyProxyFactory, refactor DataSourceProxyHandler and ConnectionProxyHandler to use JdbcInterceptorBase
3. Create HikariInterceptorConfig implementing JdbcInterceptorConfig, add HikariCP-specific properties (proxyConnectionEnabled, leakDetectionThreshold)
4. Create HikariSqlSafetyConfiguration with @ConditionalOnClass(HikariDataSource.class) for Spring Boot auto-configuration
5. Write integration tests: HikariModuleIsolationTest, HikariProxyRefactoringTest, HikariIntegrationTest supporting HikariCP 4.x and 5.x

### Task 11.5 – P6Spy Module Separation - Agent_Core_Engine_Foundation

1. Create sql-guard-jdbc-p6spy Maven module, configure POM to depend on jdbc-common and p6spy:provided scope only
2. Migrate P6SpySqlSafetyListener, refactor onBeforeAnyExecute to use JdbcInterceptorBase
3. Create P6SpySqlSafetyModule implementing P6Factory, configure META-INF/services/com.p6spy.engine.spy.P6Factory for SPI registration
4. Create P6SpyInterceptorConfig reading configuration from system properties (sqlguard.p6spy.enabled, sqlguard.p6spy.strategy)
5. Write integration tests: P6SpyModuleIsolationTest, P6SpyListenerRefactoringTest, P6SpyIntegrationTest covering universal JDBC coverage

### Task 11.6 – Integration Testing & Performance Verification - Agent_Testing_Validation

1. Write ModuleIsolationTest (8 tests) verifying each module compiles independently, no excess dependencies, user project dependency isolation
2. Write BackwardCompatibilityTest (7 tests) verifying existing code requires no changes, API behavior unchanged, all tests pass
3. Run PerformanceRegressionTest (5 tests) comparing throughput, latency, memory usage before/after module separation
4. Generate acceptance test report aggregating all test results, marking pass/fail for acceptance criteria, performance comparison data

---

## Phase 12: Core Architecture Unification - Agent_Architecture_Refactoring, Agent_Testing_Validation

**Phase Overview:** Introduce StatementVisitor unified abstraction, refactor RuleChecker interface and AbstractRuleChecker to template method pattern, migrate all Checker implementations to visitor methods, achieve single SQL parse (N→1) performance optimization through SqlContext enhancement, ensuring gradual migration with backward compatibility.

### Task 12.1 – SqlContext Refactoring - Agent_Architecture_Refactoring

- Add `@NonNull Statement statement` field to SqlContext, retain `parsedSql` field marked @Deprecated, add compatibility method `getParsedSql()` returning statement
- Update SqlContext.Builder adding `statement()` method, maintain `parsedSql()` method for backward compatibility
- Write SqlContextTest verifying new/old field interoperability, Builder compatibility, @Deprecated annotation correctness

### Task 12.2 – StatementVisitor Interface Design - Agent_Architecture_Refactoring

- Create StatementVisitor interface in sql-guard-core/src/main/java/com/footstone/sqlguard/visitor/, define visitSelect/visitUpdate/visitDelete/visitInsert methods with default empty implementations
- Write comprehensive Javadoc explaining visitor pattern usage, relationship with RuleChecker, code examples demonstrating pattern

### Task 12.3 – RuleChecker Interface Refactoring - Agent_Architecture_Refactoring

- Modify RuleChecker interface to extend StatementVisitor, retain check() and isEnabled() methods
- Update Javadoc explaining new visitor pattern usage, mark check() method as recommended entry point (though subclasses can implement visitXxx directly)

### Task 12.4 – AbstractRuleChecker Template Method Refactoring - Agent_Architecture_Refactoring

- Refactor AbstractRuleChecker.check() to final method implementing Statement type judgment and dispatch logic (instanceof Select/Update/Delete/Insert)
- Delete old utility methods (extractWhere, extractTableName, extractFields) as subclasses should use JSqlParser API directly
- Add protected visitXxx() method default implementations (empty methods) for subclass override
- Write AbstractRuleCheckerTest verifying template method dispatch logic and subclass override mechanism

### Task 12.5 – NoWhereClauseChecker Migration - Agent_Architecture_Refactoring

- Delete check() method implementation, add visitUpdate() and visitDelete() methods directly using update.getWhere() and delete.getWhere() without self-parsing
- Update NoWhereClauseCheckerTest renaming parsedSql → statement, verify new method behavior matches old version
- Run tests ensuring all pass, compare new/old version violation detection results for consistency

### Task 12.6 – BlacklistFieldChecker Migration - Agent_Architecture_Refactoring

- Delete check() method implementation, add visitSelect() method directly using select.getSelectBody() to extract fields and check blacklist
- Update BlacklistFieldCheckerTest renaming parsedSql → statement, verify blacklist detection logic unchanged
- Run tests ensuring all pass

### Task 12.7 – WhitelistFieldChecker Migration - Agent_Architecture_Refactoring

- Delete check() method implementation, add visitSelect() method directly using select.getSelectBody() to extract fields and check whitelist
- Update WhitelistFieldCheckerTest renaming parsedSql → statement, verify whitelist detection logic unchanged
- Run tests ensuring all pass

### Task 12.8 – NoPaginationChecker Migration - Agent_Architecture_Refactoring

- Delete check() method implementation, add visitSelect() method checking if select contains LIMIT/OFFSET or RowBounds
- Update NoPaginationCheckerTest renaming parsedSql → statement, verify pagination detection logic unchanged
- Run tests ensuring all pass

### Task 12.9 – Other Checker Migration - Agent_Architecture_Refactoring

1. Migrate LogicalPaginationChecker to visitSelect, update tests
2. Migrate DeepPaginationChecker to visitSelect, update tests
3. Migrate DummyConditionChecker to visitUpdate/visitDelete, update tests
4. Migrate EstimatedRowsChecker to visitSelect/visitUpdate/visitDelete, update tests
5. Migrate MissingOrderByChecker to visitSelect, update tests
6. Migrate LargePageSizeChecker to visitSelect, update tests
7. Run all Checker integration tests verifying DefaultSqlSafetyValidator works correctly

### Task 12.10 – Test Migration - Agent_Testing_Validation

1. Use Grep to find all test files using parsedSql (approximately 46 files)
2. Batch replace `.parsedSql(` → `.statement(`, verify syntax correctness
3. Update Checker unit test construction approach (pass ValidationResult instead of return)
4. Run all tests ensuring 100% pass, compare new/old test result consistency

### Task 12.11 – Performance Verification - Agent_Testing_Validation

- Write ParsingPerformanceTest using Mockito.spy to monitor JsqlParserGlobal.parse() call count
- Compare parsing count before/after refactoring (old: each Checker parses once = N times; new: interceptor parses once = 1 time)
- Generate performance report including parsing count comparison, total time comparison, memory usage comparison

---

## Phase 13: InnerInterceptor Architecture - Agent_Advanced_Interceptor, Agent_Testing_Validation

**Phase Overview:** Implement MyBatis-Plus style InnerInterceptor architecture with priority control mechanism, create SelectLimitInnerInterceptor automatic LIMIT fallback feature, implement ThreadLocal Statement sharing to avoid duplicate parsing across interceptor chain, support multi-version compatibility for MyBatis 3.4.x/3.5.x and MyBatis-Plus 3.4.x/3.5.x, establish comprehensive CI/CD test matrix.

### Task 13.1 – SqlGuardInnerInterceptor Interface Design - Agent_Advanced_Interceptor

- Create SqlGuardInnerInterceptor interface in sql-guard-mybatis referencing MyBatis-Plus InnerInterceptor design, define willDoQuery/beforeQuery/willDoUpdate/beforeUpdate/getPriority methods
- Write comprehensive Javadoc explaining priority mechanism (1-99 for check interceptors, 100-199 for fallback interceptors), lifecycle methods, integration with SqlGuardInterceptor

### Task 13.2 – SqlGuardInterceptor Main Interceptor Implementation - Agent_Advanced_Interceptor

1. Create SqlGuardInterceptor implementing MyBatis Interceptor, managing List<SqlGuardInnerInterceptor> with priority sorting
2. Implement intercept() method parsing SQL once and caching to ThreadLocal before invoking inner interceptor chain
3. Implement priority sorting mechanism sorting interceptors by getPriority() value before invocation
4. Implement lifecycle method invocation: willDoQuery → beforeQuery for SELECT, willDoUpdate → beforeUpdate for UPDATE/DELETE
5. Add ThreadLocal cleanup in finally block ensuring no memory leaks
6. Write SqlGuardInterceptorTest verifying priority sorting, lifecycle invocation order, ThreadLocal cleanup

### Task 13.3 – SqlGuardCheckInnerInterceptor Implementation - Agent_Advanced_Interceptor

1. Create SqlGuardCheckInnerInterceptor bridging RuleChecker and InnerInterceptor, getPriority() returns 10
2. In willDoQuery/willDoUpdate, attempt to get Statement from StatementContext.get(), if null parse and cache using StatementContext.cache()
3. Build SqlContext with cached Statement (not parsing again), execute all enabled RuleChecker.check()
4. Handle violations based on ViolationStrategy (BLOCK throws SQLException, WARN/LOG logs)
5. Write SqlGuardCheckInnerInterceptorTest verifying Statement caching, RuleChecker bridging, violation handling

### Task 13.4 – SqlGuardRewriteInnerInterceptor Implementation - Agent_Advanced_Interceptor

1. Create SqlGuardRewriteInnerInterceptor bridging StatementRewriter and InnerInterceptor, getPriority() returns 100
2. In beforeQuery, get Statement from StatementContext.get() (reusing CheckInnerInterceptor's parse), execute all StatementRewriter.rewrite()
3. If rewrite returns new Statement, replace SQL in BoundSql using PluginUtils.MPBoundSql
4. Support chain rewrites: update SqlContext with new Statement after each rewrite for next rewriter
5. Write SqlGuardRewriteInnerInterceptorTest verifying Statement reuse, rewrite chaining, SQL replacement

### Task 13.5 – SelectLimitInnerInterceptor Implementation - Agent_Advanced_Interceptor

1. Create SelectLimitInnerInterceptor extending JsqlParserSupport, getPriority() returns 150
2. Implement processSelect() checking if Statement already has LIMIT/OFFSET or RowBounds indicates pagination
3. If no pagination detected, add LIMIT clause using database dialect (MySQL/Oracle/SQL Server/PostgreSQL)
4. Create SqlGuardDialect interface and implementations (MySQLDialect, OracleDialect, SQLServerDialect, PostgreSQLDialect)
5. Implement DialectFactory auto-detecting database type from Connection metadata
6. Write SelectLimitInnerInterceptorTest verifying pagination detection, LIMIT addition, multi-dialect support

### Task 13.6 – StatementContext ThreadLocal Sharing - Agent_Advanced_Interceptor

- Create StatementContext class in sql-guard-core with ThreadLocal<Map<String, Statement>> cache
- Implement cache(String sql, Statement statement), get(String sql), clear() methods
- Write StatementContextTest verifying ThreadLocal isolation, cache/get correctness, memory leak prevention through clear()

### Task 13.7 – MyBatis Version Compatibility Layer - Agent_Advanced_Interceptor

1. Create MyBatisVersionDetector checking for 3.5.0+ marker class (e.g., ProviderMethodResolver), cache result in static initializer
2. Create SqlExtractor interface defining version-agnostic SQL extraction abstraction
3. Implement LegacySqlExtractor (3.4.x) using reflection-based 3.4.x API access
4. Implement ModernSqlExtractor (3.5.x) using reflection-based 3.5.x API access
5. Create SqlExtractorFactory selecting implementation based on detected version
6. Write MyBatisVersionDetectionTest verifying version detection accuracy across 3.4.6/3.5.6/3.5.13/3.5.16
7. Write MyBatisCompatibilityIntegrationTest verifying interceptor works across all versions

### Task 13.8 – MyBatis-Plus Version Compatibility Layer - Agent_Advanced_Interceptor

1. Create MyBatisPlusVersionDetector checking for 3.5.x marker classes, cache result
2. Create IPageDetector using reflection-based IPage detection from parameter types and Map contents
3. Create QueryWrapperInspector extracting conditions from QueryWrapper/LambdaQueryWrapper using reflection
4. Create WrapperTypeDetector identifying wrapper types (Query/Update/Lambda variants)
5. Implement empty wrapper detection critical for safety validation
6. Write MyBatisPlusVersionDetectionTest verifying version detection across 3.4.0/3.4.3/3.5.3/3.5.5
7. Write MyBatisPlusCompatibilityIntegrationTest verifying wrapper inspection and IPage detection

### Task 13.9 – CI/CD Multi-Version Test Matrix - Agent_Testing_Validation

1. Create .github/workflows/multi-version-test.yml with matrix strategy covering Java 8/11/17/21 × MyBatis versions × MyBatis-Plus versions
2. Create Maven profiles for each version combination (mybatis-3.4.6, mybatis-3.5.6, mp-3.4.0, mp-3.5.3, etc.)
3. Configure GitHub Actions to run test matrix on PR (subset: latest + oldest) and nightly (full matrix)
4. Implement test result aggregation uploading artifacts and generating compatibility report
5. Create compatibility badge generation updating README with shields.io badges showing version support status
6. Write CIConfigurationValidationTest and VersionProfileValidationTest verifying workflow syntax and profile activation

---

## Phase 14: Audit Platform Examples & Documentation - Agent_Documentation

(Original Phase 12 content continues here...)

