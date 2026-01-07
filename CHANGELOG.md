# Changelog

All notable changes to SQL Safety Guard will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Future enhancements and features will be listed here

## [2.0.0] - 2026-01-05

### Added - Security Checkers (Phase 1 Complete)

#### SQL Injection Prevention (4 Checkers)
- **MultiStatementChecker** (CRITICAL, BLOCK): Detects SQL injection via multi-statement execution (semicolon separators)
  - Character-by-character SQL string parsing with string literal boundary tracking
  - Excludes trailing semicolons and semicolons within string literals
  - 31 comprehensive tests covering MySQL, Oracle, PostgreSQL dialects
- **SetOperationChecker** (CRITICAL, BLOCK): Detects UNION/MINUS/EXCEPT/INTERSECT set operations for data exfiltration
  - AST-based detection via SetOperationList visitor
  - Configurable allowedOperations list for legitimate use cases
  - 33 tests including nested set operations
- **SqlCommentChecker** (CRITICAL, BLOCK): Detects SQL comments (--, /* */, #) used to bypass security
  - Raw SQL string parsing (JSqlParser strips comments during parsing)
  - Special handling for Oracle optimizer hints (/*+ INDEX */)
  - MyBatis parameter syntax (#{param}) correctly differentiated from MySQL # comments
  - 35 tests with multi-line and nested comment detection
- **IntoOutfileChecker** (CRITICAL, BLOCK): Detects MySQL file write operations (INTO OUTFILE/DUMPFILE)
  - Direct RuleChecker implementation (JSqlParser cannot parse this syntax)
  - Differentiates from Oracle SELECT INTO variable syntax
  - 36 tests with various path formats and file write attack vectors

#### Dangerous Operations Control (3 Checkers)
- **DdlOperationChecker** (CRITICAL, BLOCK): Detects DDL operations (CREATE/ALTER/DROP/TRUNCATE)
  - AST-based instanceof checks for DDL statement types
  - Configurable allowedOperations exemptions for migration scripts
  - 37 tests covering CREATE TABLE, ALTER TABLE, DROP TABLE, TRUNCATE, CREATE INDEX
- **DangerousFunctionChecker** (CRITICAL, BLOCK): Detects dangerous database functions
  - Recursive ExpressionVisitor with IdentityHashMap-based visitedSet for cycle prevention
  - Default denied functions: load_file, into_outfile, into_dumpfile, sys_exec, sys_eval, sleep, benchmark
  - Nested function detection (e.g., CONCAT(UPPER(LOWER(load_file(...)))))
  - 41 tests with deep nesting and multi-dialect dangerous functions
- **CallStatementChecker** (HIGH, WARN): Detects stored procedure calls (CALL/EXECUTE/EXEC)
  - Pattern matching for MySQL CALL, Oracle EXECUTE, SQL Server EXEC
  - HIGH severity (not CRITICAL) as procedures may be legitimate architecture
  - Default WARN strategy to balance monitoring with legitimate use cases
  - 45 tests covering multi-dialect procedure syntax

#### Access Control (4 Checkers)
- **MetadataStatementChecker** (HIGH, WARN): Detects metadata disclosure statements (SHOW/DESCRIBE/USE)
  - Statement-start keyword detection (trim and case-insensitive)
  - Differentiates from INFORMATION_SCHEMA queries via SELECT
  - Configurable allowedStatements list for admin tools
  - 40 tests covering MySQL SHOW/DESCRIBE/USE, PostgreSQL \d, Oracle DESC
- **SetStatementChecker** (MEDIUM, WARN): Detects session variable modification (SET statements)
  - Critical differentiation from UPDATE...SET column assignments
  - Detects SET autocommit, SET sql_mode, SET @variable, SET NAMES
  - 42 tests ensuring UPDATE vs SET disambiguation
- **DeniedTableChecker** (CRITICAL, BLOCK): Table-level access control with wildcard pattern support
  - Uses JSqlParser TablesNamesFinder for comprehensive table extraction
  - Wildcard regex conversion: sys_* → ^sys_[^_]+$ (matches sys_user, NOT system)
  - Schema prefix stripping and delimiter removal (MySQL backticks, Oracle quotes)
  - Extracts tables from FROM, JOIN, subqueries, CTEs
  - 30 tests with wildcard boundary cases and schema-qualified names
- **ReadOnlyTableChecker** (HIGH, BLOCK): Protects readonly tables from write operations
  - Overrides visitInsert/visitUpdate/visitDelete for target table detection
  - Wildcard pattern matching (history_*, audit_*)
  - READ operations on readonly tables are permitted
  - 22 tests ensuring only write operations are blocked

### Enhanced - Scanner CLI Integration
- Added all 11 security checkers to Scanner CLI (SqlScannerCli.createAllCheckers())
- Implemented lenient parsing mode to handle MySQL-specific syntax
- Added AbstractRuleChecker.visitRawSql() method for raw SQL validation when parsing fails
- Updated DefaultSqlSafetyValidator to execute checkers even when SQL parsing fails

### Enhanced - Testing & Quality
- Added 409 new tests (357 unit + 52 integration)
- Integration test suites:
  - MultiCheckerIntegrationTest (10 tests): Multi-checker interaction validation
  - ViolationStrategyIntegrationTest (10 tests): WARN/BLOCK strategy behavior
  - AcceptanceChecklistIntegrationTest (12 tests): Programmatic 7-item checklist verification
  - ScannerCliIntegrationTest (20+ tests): Scanner CLI registration and example file detection
- Code coverage: 84.60% (9,164/10,832 instructions)
- Performance: <50ms p99 validation latency maintained

### Enhanced - Documentation
- Added 11 user documentation files in docs/user-guide/rules/:
  - multi-statement.md, set-operation.md, sql-comment.md, into-outfile.md
  - ddl-operation.md, dangerous-function.md, call-statement.md
  - metadata-statement.md, set-statement.md, denied-table.md, readonly-table.md
- Created 22 example mapper XML files (11 bad + 11 good) demonstrating all checkers
- Added 15 Memory Log files documenting task completion
- Created Phase_01_Completion_Report.md (comprehensive project summary)

### Technical Architecture
- **Dual Implementation Pattern**: String-Based (5 checkers) vs AST-Based (6 checkers)
  - String-Based: Required for JSqlParser limitations (comments stripped, MySQL-specific syntax unparseable)
  - AST-Based: Type-safe visitor pattern for parseable SQL
- **Risk Level Distribution**: CRITICAL (7), HIGH (3), MEDIUM (1)
- **Default Strategy Distribution**: BLOCK (9), WARN (2)

### Performance
- Runtime overhead maintained: <5% for ORM layers
- String parsing overhead: ~1-2ms per SQL (acceptable for complete detection)

## [1.0.0] - 2024-XX-XX

### Added

#### Static Analysis (Scanner)
- **sql-scanner-core**: Core SQL scanning engine with three parsers
  - XmlMapperParser: Parses MyBatis XML mappers with dynamic SQL variant generation (if/foreach/choose/where/trim/set/bind tags)
  - AnnotationParser: Extracts SQL from `@Select/@Update/@Delete/@Insert` annotations
  - QueryWrapperScanner: Detects MyBatis-Plus QueryWrapper usage with package verification
  - Dynamic SQL variant generation with 10-variant limit to prevent combinatorial explosion
  - Performance: <2s for complex dynamic SQL, 70%+ generated variants are syntactically valid
- **sql-scanner-cli**: Standalone command-line tool for CI/CD integration
  - Picocli 4.7.5-based CLI with comprehensive argument parsing
  - Dual-format report output: Console (ANSI-colored) and HTML (sortable tables with collapsible SQL)
  - Fail-fast input validation with clear, actionable error messages
  - CI/CD integration with exit codes (0=success, 1=critical violations, 2=invalid arguments)
  - Quiet mode for headless CI/CD environments
  - Complete documentation: README (1000+ lines), Quick Reference (200+ lines), Config Example (150+ lines)
- **sql-scanner-maven**: Maven plugin for build-time scanning
  - Maven goal `mvn sqlguard:scan` in VERIFY phase
  - Configuration: projectPath, configFile, outputFormat, outputFile, failOnCritical, skip
  - Maven Invoker Plugin integration tests (3 scenarios: simple-scan, with-violations, fail-on-critical)
  - Documentation: README (650+ lines), usage-examples (650+ lines)
- **sql-scanner-gradle**: Gradle plugin for build-time scanning
  - Gradle task `gradle sqlguardScan` in verification group
  - DSL configuration with fluent API: `console()`, `html()`, `both()`, `failOnCritical()`
  - Property-based configuration with lazy evaluation and convention-based defaults
  - Hybrid build approach: Maven build with stub Gradle API classes for testing
  - Documentation: README (800+ lines), usage-examples (500+ lines)

#### Runtime Validation (Guard)
- **sql-guard-core**: Runtime SQL validation engine with 10 specialized rule checkers
  - **NoWhereClauseChecker** (CRITICAL): Detects DELETE/UPDATE/SELECT without WHERE clause
  - **DummyConditionChecker** (HIGH): Detects dummy conditions like "1=1" or "true"
  - **BlacklistFieldChecker** (HIGH): Detects blacklist-only WHERE clauses
  - **WhitelistFieldChecker** (HIGH): Enforces whitelist field requirements
  - **LogicalPaginationChecker** (CRITICAL): Detects RowBounds/IPage without pagination plugin
  - **NoConditionPaginationChecker** (CRITICAL): Detects pagination without WHERE clause
  - **DeepPaginationChecker** (MEDIUM): Detects high OFFSET values (default: >1000)
  - **LargePageSizeChecker** (MEDIUM): Detects excessive page sizes (default: >500)
  - **MissingOrderByChecker** (LOW): Detects missing ORDER BY in pagination queries
  - **NoPaginationChecker** (Variable): Variable risk based on WHERE clause presence (CRITICAL/HIGH/MEDIUM)
  - Chain of Responsibility pattern for rule checker orchestration
  - Parse-once optimization: SQL parsed once, AST shared across all checkers (90% parsing overhead reduction)
  - SqlDeduplicationFilter with ThreadLocal LRU cache (1000 entries, 100ms TTL, ~50% cache hit rate)
  - JSqlParser 4.6 facade with LRU cache (1000 parsed statements, ~80% cache hit rate)
  - Dual-Config Pattern: YAML deserialization layer (config package) + runtime behavior layer (validator/rule/impl package)
  - Performance target: <5% overhead compared to SQL execution time
  - 468 comprehensive tests with 100% pass rate
- **sql-guard-mybatis**: MyBatis Executor interceptor for SQL validation
  - `@Intercepts` annotation-based interceptor for Executor.query() and Executor.update()
  - BoundSql extraction captures resolved dynamic SQL (after `<if>/<where>/<foreach>`)
  - RowBounds detection for logical pagination validation
  - ViolationStrategy enum (BLOCK/WARN/LOG) for phased rollout support
  - Multi-version compatibility: MyBatis 3.4.6 and 3.5.13 tested
  - Thread-safety verified: 100 concurrent operations
  - MapperId linking violations to source code
  - 59 tests with 100% pass rate
- **sql-guard-mp**: MyBatis-Plus InnerInterceptor for SQL validation
  - MpSqlSafetyInnerInterceptor implements InnerInterceptor interface
  - beforeQuery() and beforeUpdate() method interception
  - IPage pagination detection (direct parameter and in Map)
  - QueryWrapper/LambdaQueryWrapper/UpdateWrapper detection and validation
  - Empty QueryWrapper detection (generates SQL without WHERE clause)
  - PaginationInnerInterceptor coordination (correct execution order)
  - Deduplication filter prevents double validation (with MyBatis interceptor)
  - BaseMapper integration verified (selectById, selectList, insert, update, delete)
  - MyBatis-Plus plugin ecosystem compatibility:
    - OptimisticLockerInnerInterceptor (version control)
    - TenantLineInnerInterceptor simulation
    - BlockAttackInnerInterceptor compatibility
    - Logic delete (@TableLogic) integration
  - Thread-safety verified with ThreadLocal cache isolation
  - 79 tests with 100% pass rate
- **sql-guard-jdbc**: JDBC-layer interceptors for connection pools and universal fallback
  - **Druid Filter**: DruidSqlSafetyFilter extending FilterAdapter
    - Three interception points: connection_prepareStatement(), statement_executeQuery(), statement_executeUpdate()
    - SQL command type detection from SQL prefix (SELECT/UPDATE/DELETE/INSERT/UNKNOWN)
    - Datasource name extraction from ConnectionProxy and DataSourceProxy
    - Filter registration at position 0 (executes before StatFilter)
    - CallableStatement support via connection_prepareCall()
    - Performance: 7.84% overhead (398.98ms vs 369.96ms for 1000 queries)
    - 67 tests with 100% pass rate
  - **HikariCP Proxy**: HikariSqlSafetyProxyFactory with DataSource wrapper pattern
    - Two-layer proxy architecture: DataSource proxy → Connection proxy → Statement/PreparedStatement proxies
    - SQL validation points: PreparedStatement at prepareStatement() time, Statement at execute() time
    - HikariSqlSafetyConfiguration helper class for configuration
    - Spring Framework integration via BeanPostProcessor pattern
    - HikariCP version-agnostic (works with 4.x and 5.x)
    - Connection pool leak detection preserved and functional
    - Performance: ~3% overhead (<1% connection acquisition, <5% execution)
    - 68 tests with 100% pass rate
  - **P6Spy Listener**: P6SpySqlSafetyListener implementing JdbcEventListener
    - onBeforeAnyExecute() method intercepts all SQL executions
    - Parameter-substituted SQL extraction via P6Spy's getSqlWithValues()
    - P6SpySqlSafetyModule for SPI registration via spy.properties
    - ServiceLoader integration with fallback to default validator creation
    - System property configuration: `-Dsqlguard.p6spy.strategy=BLOCK|WARN|LOG`
    - Universal coverage: C3P0, DBCP, Tomcat JDBC, bare JDBC, any JDBC driver
    - Performance: ~15% overhead (documented trade-off for universal coverage)
    - Comprehensive documentation: setup (400+ lines), troubleshooting (250+ lines), performance analysis (300+ lines)
    - 81 tests with 100% pass rate

#### Spring Boot Integration
- **sql-guard-spring-boot-starter**: Zero-configuration Spring Boot auto-configuration
  - SqlGuardAutoConfiguration with @Configuration, @ConditionalOnClass, @EnableConfigurationProperties
  - Automatic bean creation: JSqlParserFacade, 10 RuleCheckers, PaginationPluginDetector, RuleCheckerOrchestrator, SqlDeduplicationFilter, DefaultSqlSafetyValidator
  - @ConditionalOnMissingBean user override support
  - META-INF/spring.factories auto-discovery registration
  - @AutoConfigureAfter(DataSourceAutoConfiguration) ordering
  - SqlGuardProperties (850+ lines) with nested configuration classes
  - JSR-303 validation (@Pattern, @Min, @Max, @Valid cascade) with fail-fast startup validation
  - spring-configuration-metadata.json for IDE autocomplete
  - Profile-specific configs (dev/prod) with property overrides
  - Kebab-case/snake_case/camelCase property name mapping
  - ConfigCenterAdapter SPI interface for custom config centers
  - ApolloConfigCenterAdapter with @ConditionalOnClass + @ConditionalOnProperty
  - Reflection-based Apollo event handling (no compile-time dependency)
  - Thread-safe synchronized reload operations with exception isolation
  - Extension documentation (200+ lines)
  - 95 tests with 100% pass rate (30 + 47 + 18)

#### Core Infrastructure
- **Maven Multi-Module Structure**: 9 Maven modules with proper dependency management
- **Parent POM**: Multi-version Java profiles (8/11/17/21) with baseline Java 8 compatibility
- **Core Domain Models**: SqlContext (Builder pattern), ValidationResult, RiskLevel, SqlCommandType, ViolationInfo
- **Configuration System**: 8 configuration classes with YAML loader and defaults (SnakeYAML 1.33)
- **JSqlParser Integration**: JSqlParser 4.6 facade with parsing, extraction utilities, and LRU cache
- **Logging Infrastructure**: SLF4J/Logback logging across core modules
- **Google Java Style**: Enforcement via Checkstyle (120-char line length, 2-space indentation)
- **Test Infrastructure**: JUnit 5, Mockito 4.11.0, 1000+ comprehensive tests with 100% pass rate

### Performance Characteristics
- **Parse-Once Optimization**: 90% reduction in parsing overhead (1 parse vs. 10 parses for 10 checkers)
- **LRU Caching**: JSqlParser cache (~80% hit rate), Deduplication cache (~50% hit rate)
- **ThreadLocal Deduplication**: ~50% reduction in overhead for double interception scenarios
- **Runtime Overhead**:
  - MyBatis Interceptor: ~3%
  - MyBatis-Plus InnerInterceptor: ~4%
  - Druid Filter: 7.84%
  - HikariCP Proxy: ~3%
  - P6Spy Listener: ~15% (acceptable for universal coverage)

### Documentation
- **Architecture Documentation**: ARCHITECTURE.md with system overview, module structure, design patterns, data flows, threading model, extension points
- **Contributing Guide**: CONTRIBUTING.md with development setup, code style guidelines, TDD requirements, PR process, extension tutorials
- **CLI Documentation**: README (1000+ lines), Quick Reference (200+ lines), Config Example (150+ lines)
- **Maven Plugin Documentation**: README (650+ lines), usage-examples (650+ lines)
- **Gradle Plugin Documentation**: README (800+ lines), usage-examples (500+ lines)
- **P6Spy Documentation**: setup (400+ lines), troubleshooting (250+ lines), performance analysis (300+ lines)
- **Spring Boot Documentation**: config-center-extension (200+ lines)
- **Total Documentation**: 7000+ lines across 20+ files

### Known Limitations
- **JSqlParser 4.6**: Limited SQL Server TOP/bracket syntax support (version changed from 4.9.0 due to Maven repository availability)
- **Nacos Support**: Removed due to Spring Boot 2.7.18 dependency conflicts (SnakeYAML version, ErrorCoded ClassNotFound)
- **Interceptor Registration**: Deferred from Spring Boot auto-configuration (future enhancement)

### Migration Guide
This is the initial 1.0.0 release. No migration needed.

## Version Comparison Links

[Unreleased]: https://github.com/footstone/sql-safety-guard/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/footstone/sql-safety-guard/releases/tag/v1.0.0















