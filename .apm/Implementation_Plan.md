# Phase 11/12/13 重构实施计划

**基于**: Architecture_Review_Report.md 系统性审视结论
**创建时间**: 2025-12-19
**总工期**: 45 工作日

---

## Phase 11: JDBC Module Separation - Agent_Core_Engine_Foundation, Agent_Testing_Validation

**Phase Overview:** Extract sql-guard-jdbc into independent connection pool modules (Druid, HikariCP, P6Spy) following minimal dependency principle, creating sql-guard-jdbc-common for shared abstractions (ViolationStrategy, JdbcInterceptorBase, config interfaces), ensuring users only include needed pool modules without dependency pollution, maintaining 100% backward compatibility with existing tests, NO architecture refactoring in this phase.

**Phase Constraints:**
- ✅ DO: Module structure refactoring, dependency isolation, code extraction
- ❌ DON'T: RuleChecker refactoring, SqlContext changes, StatementVisitor introduction

---

### Task 11.1 – TDD Test Case Library Design (Module Separation Only) │ Agent_Testing_Validation

- **Objective:** Design comprehensive test case library covering JDBC module separation scenarios before implementation, establishing test fixtures for module isolation verification, dependency checks, and backward compatibility validation, ensuring TDD methodology with tests written first, explicitly excluding architecture refactoring tests.

- **Output:**
  - Test design document: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md`
  - Test fixture base class: `AbstractJdbcModuleTest` for H2-based connection pool testing
  - Module isolation test specifications: Compilation tests, dependency verification, ClassLoader isolation
  - Backward compatibility test specifications: API consistency, behavior preservation, configuration migration
  - Performance baseline test specifications: Module loading overhead, runtime performance, memory usage

- **Guidance:** TDD First principle: design all tests before any implementation begins. Reference Architecture_Review_Report.md to determine module separation scope and understand why Phase 11 focuses solely on module structure without architecture refactoring. Test categories: (1) Module Isolation - each pool module compiles independently without other pools on classpath, (2) Dependency Verification - POM analysis confirming only required dependencies, Maven Enforcer rules, (3) Backward Compatibility - existing code works without changes, deprecated APIs still functional, (4) Performance Baselines - module loading < 10ms overhead, no runtime regression. AbstractJdbcModuleTest provides H2 in-memory database setup, mock ConnectionPool creation, SQL execution verification. Test design explicitly excludes: RuleChecker refactoring tests, SqlContext modification tests, StatementVisitor tests (those belong to Phase 12). Document measurable acceptance criteria per test category.

**测试矩阵 (40+ tests - 设计阶段):**

1. **模块隔离测试设计TDD (ModuleIsolationTestDesign - 15 tests):** Write test design document covering: `testCommonModule_compilesWithoutPoolDependencies()`, `testCommonModule_onlyDependsOnCoreAndAuditApi()`, `testDruidModule_compilesWithoutHikariP6Spy()`, `testDruidModule_druidDependencyIsProvided()`, `testHikariModule_compilesWithoutDruidP6Spy()`, `testHikariModule_hikariDependencyIsProvided()`, `testP6SpyModule_compilesWithoutDruidHikari()`, `testP6SpyModule_p6spyDependencyIsProvided()`, `testUserProject_onlyDruidDependency_noTransitivePollution()`, `testUserProject_onlyHikariDependency_noTransitivePollution()`, `testUserProject_onlyP6SpyDependency_noTransitivePollution()`, `testMavenEnforcer_rejectsWrongDependencies()`, `testClassLoader_poolClassesNotLoaded_whenModuleNotUsed()`, `testIndependentJar_druidModulePackagesCorrectly()`, `testIndependentJar_hikariModulePackagesCorrectly()`. Then define acceptance criteria: all modules compile independently, no transitive dependency pollution.

2. **向后兼容测试设计TDD (BackwardCompatibilityTestDesign - 12 tests):** Write test design document covering: `testViolationStrategy_oldDruidEnum_stillWorks()`, `testViolationStrategy_oldHikariEnum_stillWorks()`, `testViolationStrategy_oldP6SpyEnum_stillWorks()`, `testDruidFilter_existingCode_noChangesNeeded()`, `testHikariProxy_existingCode_noChangesNeeded()`, `testP6SpyListener_existingCode_noChangesNeeded()`, `testConfiguration_oldYaml_parsesCorrectly()`, `testConfiguration_oldProperties_parsesCorrectly()`, `testDeprecatedApi_compiles_withWarning()`, `testDeprecatedApi_behavior_unchanged()`, `testAllExistingTests_pass100Percent()`, `testMigrationGuide_documentsChanges()`. Then define acceptance criteria: 100% backward compatibility, zero breaking changes.

3. **性能基准测试设计TDD (PerformanceBaselineTestDesign - 13 tests):** Write test design document covering: `testModuleLoading_commonModule_under10ms()`, `testModuleLoading_druidModule_under10ms()`, `testModuleLoading_hikariModule_under10ms()`, `testModuleLoading_p6spyModule_under10ms()`, `testRuntimePerformance_druid_noRegression()`, `testRuntimePerformance_hikari_noRegression()`, `testRuntimePerformance_p6spy_noRegression()`, `testMemoryUsage_staticFootprint_noIncrease()`, `testMemoryUsage_runtime_noIncrease()`, `testConnectionAcquisition_speed_unchanged()`, `testSqlValidation_throughput_noRegression()`, `testSqlValidation_latency_noRegression()`, `testConcurrentAccess_scalability_maintained()`. Then define performance targets: < 10ms module load, < 5% runtime overhead, no memory increase.

**验收标准:** 测试设计文档完整, 所有测试用例可实现, 性能基准可测量, 明确排除架构重构测试.

---

### Task 11.2 – JDBC Common Module Extraction │ Agent_Core_Engine_Foundation

- **Objective:** Extract common JDBC abstractions into new sql-guard-jdbc-common module, unifying ViolationStrategy enum (eliminating 3 duplicates), creating JdbcInterceptorBase abstract base class with template method pattern for SQL interception lifecycle, defining JdbcInterceptorConfig interface for configuration abstraction, implementing utility classes for SqlContext construction and audit event creation.

- **Output:**
  - New Maven module: `sql-guard-jdbc-common` (POM declaring only sql-guard-core, sql-guard-audit-api dependencies)
  - `ViolationStrategy` enum (BLOCK/WARN/LOG) replacing 3 duplicate definitions
  - `JdbcInterceptorBase` abstract class with template methods: `interceptSql()`, `buildSqlContext()`, `handleViolation()`, `beforeValidation()`, `afterValidation()`, `onError()`
  - `JdbcInterceptorConfig` interface defining: `isEnabled()`, `getStrategy()`, `isAuditEnabled()`, `getExcludePatterns()`
  - `SqlContextBuilder` utility class providing JDBC-specific SqlContext construction
  - `JdbcAuditEventBuilder` utility for AuditEvent creation from JDBC context
  - Package structure: `com.footstone.sqlguard.interceptor.jdbc.common`

- **Guidance:** Module extraction follows minimal dependency principle: sql-guard-jdbc-common depends ONLY on sql-guard-core and sql-guard-audit-api, zero connection pool dependencies. ViolationStrategy unification: create single enum in common module, mark old enums in druid/hikari/p6spy as @Deprecated delegating to common enum for transition period. JdbcInterceptorBase template method pattern: final `interceptSql()` method orchestrates lifecycle (beforeValidation → buildSqlContext → validate → handleViolation → afterValidation), subclasses override hook methods for pool-specific behavior. Configuration abstraction: JdbcInterceptorConfig interface defines common properties, pool-specific configs extend adding specialized properties (Druid: filterPosition, Hikari: leakDetectionThreshold, P6Spy: systemPropertyConfig). Backward compatibility: existing code paths continue working, deprecated classes delegate to new common implementations, migration path documented.

**测试矩阵 (30+ tests):**

1. **公共模块抽取TDD (CommonModuleExtractionTest - 12 tests):** Write test class covering: `testViolationStrategy_unified_hasAllThreeValues()`, `testViolationStrategy_BLOCK_behavior_matchesLegacy()`, `testViolationStrategy_WARN_behavior_matchesLegacy()`, `testViolationStrategy_LOG_behavior_matchesLegacy()`, `testJdbcInterceptorBase_templateMethod_invokesInOrder()`, `testJdbcInterceptorBase_beforeValidation_hookCalled()`, `testJdbcInterceptorBase_afterValidation_hookCalled()`, `testJdbcInterceptorBase_onError_hookCalled()`, `testJdbcInterceptorConfig_interface_definesAllProperties()`, `testSqlContextBuilder_jdbc_buildsCorrectly()`, `testJdbcAuditEventBuilder_createsEventCorrectly()`, `testPackageStructure_common_isCorrect()`. Then extract common module.

2. **依赖隔离TDD (DependencyIsolationTest - 10 tests):** Write test class covering: `testCommonModule_noDruidDependency_compiles()`, `testCommonModule_noHikariDependency_compiles()`, `testCommonModule_noP6SpyDependency_compiles()`, `testCommonModule_onlyCoreAndAuditApi_sufficient()`, `testCommonModule_transitiveDeps_none()`, `testCommonModule_classLoading_noPoolClassesRequired()`, `testCommonModule_optionalDeps_properlyMarked()`, `testCommonModule_providedScope_notLeaking()`, `testCommonModule_testScope_isolated()`, `testCommonModule_runtimeScope_minimal()`. Then verify dependency isolation with Maven Enforcer.

3. **向后兼容TDD (BackwardCompatibilityTest - 8 tests):** Write test class covering: `testLegacyViolationStrategy_druid_stillWorks()`, `testLegacyViolationStrategy_hikari_stillWorks()`, `testLegacyViolationStrategy_p6spy_stillWorks()`, `testLegacyImports_compileWithDeprecationWarning()`, `testLegacyConfig_mapsToNewConfig()`, `testLegacyBehavior_preserved()`, `testDeprecationAnnotations_present()`, `testMigrationPath_documented()`. Then add deprecation annotations and migration guide.

**验收标准:** 公共模块独立编译, 无连接池依赖, 向后兼容100%, ViolationStrategy统一, 模板方法模式正确.

---

### Task 11.3 – Druid Module Separation │ Agent_Core_Engine_Foundation

- **Objective:** Create independent sql-guard-jdbc-druid module containing ONLY Druid-specific implementations, depending on sql-guard-jdbc-common for shared abstractions, ensuring users who only use Druid don't pull in HikariCP or P6Spy dependencies, refactoring DruidSqlSafetyFilter to compose JdbcInterceptorBase instead of duplicating logic.

- **Output:**
  - New Maven module: `sql-guard-jdbc-druid` (POM: druid:provided, jdbc-common dependency)
  - `DruidSqlSafetyFilter` refactored to compose JdbcInterceptorBase
  - `DruidSqlAuditFilter` for JDBC audit logging
  - `DruidSqlSafetyFilterConfiguration` Spring Boot auto-configuration with @ConditionalOnClass(DruidDataSource.class)
  - `DruidInterceptorConfig` extending JdbcInterceptorConfig, adding Druid-specific properties
  - Package structure: `com.footstone.sqlguard.interceptor.jdbc.druid`

- **Guidance:** Module isolation: sql-guard-jdbc-druid POM declares druid dependency in provided scope only, inherits common abstractions from jdbc-common, zero dependencies on hikari/p6spy. **Druid-specific implementation - Filter chain mechanism**: DruidSqlSafetyFilter extends FilterAdapter integrating into Druid's Filter chain architecture, leveraging Druid's connection proxy and statement proxy lifecycle events. Filter refactoring: DruidSqlSafetyFilter uses composition over inheritance - create JdbcInterceptorBase instance, delegate interceptSql calls in Druid Filter lifecycle methods (connection_prepareStatement, statement_executeQuery, statement_execute, etc.), FilterAdapter provides extension points for SQL interception at multiple layers. Configuration: DruidInterceptorConfig implements JdbcInterceptorConfig interface, adds Druid-specific properties (filterPosition for Filter chain ordering enabling placement before/after other Druid filters like StatFilter/WallFilter, connectionProxyEnabled flag). Spring integration: DruidSqlSafetyFilterConfiguration uses @ConditionalOnClass for auto-detection, @ConfigurationProperties for config binding. Test isolation: Druid module tests must run successfully WITHOUT HikariCP or P6Spy on classpath, verify through Maven profile excluding those dependencies.

**测试矩阵 (25+ tests):**

1. **Druid模块隔离TDD (DruidModuleIsolationTest - 10 tests):** Write test class covering: `testDruidModule_noHikariDependency_compiles()`, `testDruidModule_noP6SpyDependency_compiles()`, `testDruidModule_onlyDruidProvided_works()`, `testDruidModule_commonModuleDependency_resolves()`, `testDruidModule_classLoading_noOtherPoolsRequired()`, `testDruidModule_independentJar_packages()`, `testDruidModule_transitiveDeps_verified()`, `testDruidModule_runtimeClasspath_minimal()`, `testDruidModule_testClasspath_isolated()`, `testDruidModule_mavenShade_excludesOthers()`. Then create Druid module POM with strict dependency control.

2. **Druid Filter重构TDD (DruidFilterRefactoringTest - 10 tests):** Write test class covering: `testDruidFilter_composesJdbcInterceptorBase()`, `testDruidFilter_templateMethod_delegates()`, `testDruidFilter_filterAdapter_integrates()`, `testDruidFilter_connectionProxy_intercepts()`, `testDruidFilter_statementProxy_intercepts()`, `testDruidFilter_preparedStatement_intercepts()`, `testDruidFilter_callableStatement_intercepts()`, `testDruidFilter_violationStrategy_usesCommon()`, `testDruidFilter_auditEvent_usesCommonBuilder()`, `testDruidFilter_configuration_extendsCommon()`. Then refactor DruidSqlSafetyFilter to composition pattern.

3. **Druid集成测试TDD (DruidIntegrationTest - 5 tests):** Write test class covering: `testDruid_endToEnd_interceptsQueries()`, `testDruid_withH2_validates()`, `testDruid_multipleDataSources_handles()`, `testDruid_springBoot_autoConfigures()`, `testDruid_performance_meetsBaseline()`. Then verify Druid integration with H2 database.

**验收标准:** Druid模块独立编译运行, 无HikariCP/P6Spy依赖, 现有Druid测试100%通过, Filter重构为组合模式.

---

### Task 11.4 – HikariCP Module Separation │ Agent_Core_Engine_Foundation

- **Objective:** Create independent sql-guard-jdbc-hikari module containing ONLY HikariCP-specific implementations, depending on sql-guard-jdbc-common for shared abstractions, ensuring minimal dependency footprint for HikariCP users, refactoring proxy factory to compose JdbcInterceptorBase, supporting both HikariCP 4.x (Java 8) and 5.x (Java 11+).

- **Output:**
  - New Maven module: `sql-guard-jdbc-hikari` (POM: HikariCP:provided, jdbc-common dependency)
  - `HikariSqlSafetyProxyFactory` refactored to compose JdbcInterceptorBase
  - `HikariSqlAuditProxyFactory` for JDBC audit logging
  - `HikariSqlSafetyConfiguration` Spring Boot auto-configuration
  - `HikariInterceptorConfig` extending JdbcInterceptorConfig
  - JDK Dynamic Proxy handlers: `DataSourceProxyHandler`, `ConnectionProxyHandler`, `StatementProxyHandler`
  - Package structure: `com.footstone.sqlguard.interceptor.jdbc.hikari`

- **Guidance:** **HikariCP-specific implementation - Multi-layer JDK Dynamic Proxy pattern**: HikariSqlSafetyProxyFactory wraps HikariDataSource using JDK dynamic proxies creating a three-layer proxy chain (DataSource proxy → Connection proxy → Statement proxy), each layer intercepts specific lifecycle events without compile-time HikariCP dependency (only provided scope). Multi-layer proxy chain: DataSource.getConnection() returns wrapped Connection proxy → Connection.prepareStatement() returns wrapped PreparedStatement proxy → PreparedStatement.execute() triggers SQL validation, each layer uses InvocationHandler to intercept and delegate to JdbcInterceptorBase for validation logic. Proxy handlers compose JdbcInterceptorBase instance: DataSourceProxyHandler intercepts getConnection, ConnectionProxyHandler intercepts prepareStatement/createStatement, StatementProxyHandler intercepts execute* methods, all delegating to composed JdbcInterceptorBase.interceptSql(). Version compatibility: Support HikariCP 4.x (Java 8+) and 5.x (Java 11+) through reflection-based API detection, test both versions. Configuration: HikariInterceptorConfig adds HikariCP-specific properties (proxyConnectionEnabled, leakDetectionThreshold mirroring HikariCP's own leak detection).

**测试矩阵 (25+ tests):**

1. **HikariCP模块隔离TDD (HikariModuleIsolationTest - 10 tests):** Write test class covering: `testHikariModule_noDruidDependency_compiles()`, `testHikariModule_noP6SpyDependency_compiles()`, `testHikariModule_onlyHikariProvided_works()`, `testHikariModule_commonModuleDependency_resolves()`, `testHikariModule_classLoading_noOtherPoolsRequired()`, `testHikariModule_independentJar_packages()`, `testHikariModule_hikari4x_compatible()`, `testHikariModule_hikari5x_compatible()`, `testHikariModule_runtimeClasspath_minimal()`, `testHikariModule_testClasspath_isolated()`. Then create HikariCP module POM.

2. **HikariCP Proxy重构TDD (HikariProxyRefactoringTest - 10 tests):** Write test class covering: `testHikariProxy_composesJdbcInterceptorBase()`, `testHikariProxy_dataSourceProxy_wraps()`, `testHikariProxy_connectionProxy_intercepts()`, `testHikariProxy_statementProxy_intercepts()`, `testHikariProxy_preparedStatementProxy_intercepts()`, `testHikariProxy_callableStatementProxy_intercepts()`, `testHikariProxy_jdkDynamicProxy_works()`, `testHikariProxy_violationStrategy_usesCommon()`, `testHikariProxy_auditEvent_usesCommonBuilder()`, `testHikariProxy_configuration_extendsCommon()`. Then refactor proxy to composition pattern.

3. **HikariCP集成测试TDD (HikariIntegrationTest - 5 tests):** Write test class covering: `testHikari_endToEnd_interceptsQueries()`, `testHikari_withH2_validates()`, `testHikari_connectionPoolMetrics_preserves()`, `testHikari_springBoot_autoConfigures()`, `testHikari_performance_meetsBaseline()`. Then verify HikariCP 4.x and 5.x integration.

**验收标准:** HikariCP模块独立编译运行, 无Druid/P6Spy依赖, 支持HikariCP 4.x和5.x, 现有HikariCP测试100%通过.

---

### Task 11.5 – P6Spy Module Separation │ Agent_Core_Engine_Foundation

- **Objective:** Create independent sql-guard-jdbc-p6spy module containing ONLY P6Spy-specific implementations, providing universal JDBC interception fallback for any connection pool or bare JDBC usage, implementing SPI-based P6Factory registration for ServiceLoader discovery.

- **Output:**
  - New Maven module: `sql-guard-jdbc-p6spy` (POM: p6spy:provided, jdbc-common dependency)
  - `P6SpySqlSafetyListener` refactored to compose JdbcInterceptorBase
  - `P6SpySqlAuditListener` for JDBC audit logging
  - `P6SpySqlSafetyModule` implementing P6Factory for SPI registration
  - `P6SpyInterceptorConfig` with system property-based configuration (P6Spy limitation)
  - `META-INF/services/com.p6spy.engine.spy.P6Factory` SPI registration file
  - `spy.properties` template and documentation
  - Package structure: `com.footstone.sqlguard.interceptor.jdbc.p6spy`

- **Guidance:** **P6Spy-specific implementation - SPI-based ServiceLoader discovery mechanism**: P6Spy provides universal JDBC interception through Java SPI (Service Provider Interface), enabling automatic discovery and registration without explicit configuration. P6Spy as universal fallback: works with any JDBC driver/connection pool (C3P0, DBCP2, Tomcat JDBC, bare JDBC), higher overhead (~15%) acceptable for universal coverage. JdbcEventListener integration: P6SpySqlSafetyListener extends JdbcEventListener, implements onBeforeAnyExecute() composing JdbcInterceptorBase for interception logic. **SPI registration critical path**: P6SpySqlSafetyModule implements P6Factory interface, registered in META-INF/services/com.p6spy.engine.spy.P6Factory for ServiceLoader auto-discovery, ensuring P6Spy engine automatically loads and initializes our listener on startup without manual configuration. Configuration: P6SpyInterceptorConfig reads from system properties (P6Spy limitation - no programmatic config), properties: sqlguard.p6spy.enabled, sqlguard.p6spy.strategy. Universal coverage testing: verify with bare JDBC (DriverManager), C3P0, DBCP2 to prove universality claim.

**测试矩阵 (25+ tests):**

1. **P6Spy模块隔离TDD (P6SpyModuleIsolationTest - 10 tests):** Write test class covering: `testP6SpyModule_noDruidDependency_compiles()`, `testP6SpyModule_noHikariDependency_compiles()`, `testP6SpyModule_onlyP6SpyProvided_works()`, `testP6SpyModule_commonModuleDependency_resolves()`, `testP6SpyModule_classLoading_noOtherPoolsRequired()`, `testP6SpyModule_independentJar_packages()`, `testP6SpyModule_spiRegistration_works()`, `testP6SpyModule_spyProperties_loads()`, `testP6SpyModule_runtimeClasspath_minimal()`, `testP6SpyModule_testClasspath_isolated()`. Then create P6Spy module POM.

2. **P6Spy Listener重构TDD (P6SpyListenerRefactoringTest - 10 tests):** Write test class covering: `testP6SpyListener_composesJdbcInterceptorBase()`, `testP6SpyListener_jdbcEventListener_implements()`, `testP6SpyListener_onBeforeAnyExecute_intercepts()`, `testP6SpyListener_getSqlWithValues_extracts()`, `testP6SpyListener_parameterSubstitution_works()`, `testP6SpyListener_violationStrategy_usesCommon()`, `testP6SpyListener_auditEvent_usesCommonBuilder()`, `testP6SpyListener_configuration_extendsCommon()`, `testP6SpyModule_serviceLoader_discovers()`, `testP6SpyModule_systemProperty_configures()`. Then refactor P6Spy listener.

3. **P6Spy集成测试TDD (P6SpyIntegrationTest - 5 tests):** Write test class covering: `testP6Spy_endToEnd_interceptsQueries()`, `testP6Spy_withBareJdbc_works()`, `testP6Spy_withC3P0_works()`, `testP6Spy_universalCoverage_handles()`, `testP6Spy_performance_documentsOverhead()`. Then verify universal JDBC coverage.

**验收标准:** P6Spy模块独立编译运行, 无Druid/HikariCP依赖, SPI注册正常, 通用JDBC覆盖验证, 性能开销文档化.

---

### Task 11.6 – Integration Testing & Performance Verification │ Agent_Testing_Validation

- **Objective:** Execute comprehensive integration testing verifying module separation correctness, backward compatibility preservation, dependency isolation effectiveness, and performance baseline compliance, aggregating results into acceptance test report.

- **Output:**
  - `ModuleIsolationIntegrationTest` suite (8 tests)
  - `BackwardCompatibilityIntegrationTest` suite (7 tests)
  - `PerformanceRegressionTest` suite (5 tests)
  - Acceptance test report: `.apm/Assignments/Phase_11/Task_11_6_Acceptance_Report.md`
  - Performance comparison data (before/after module separation)

- **Guidance:** Integration testing executes in 3 categories: (1) Module Isolation - verify each module compiles independently, user projects pulling only one pool module have no transitive pollution, ClassLoader verification, (2) Backward Compatibility - existing code works without changes, all pre-existing tests pass 100%, configuration files parse correctly, deprecated APIs still functional, (3) Performance Regression - module loading overhead < 10ms, runtime throughput no degradation, latency P99 no increase, memory footprint unchanged. Test execution: run on clean Maven repository to verify dependency resolution, use Maven Enforcer Plugin to fail build on wrong dependencies, execute performance tests with JMH for accuracy. Acceptance report: aggregate all test results, mark pass/fail for each acceptance criterion, include performance comparison charts, document any deviations with justification.

**测试矩阵 (20+ tests):**

1. **模块隔离集成测试 (ModuleIsolationIntegrationTest - 8 tests):** Write test class executing: `testCommonModule_independentCompile_noConnectionPool()`, `testDruidModule_independentCompile_onlyDruid()`, `testHikariModule_independentCompile_onlyHikari()`, `testP6SpyModule_independentCompile_onlyP6Spy()`, `testUserProject_onlyDruid_noDependencyPollution()`, `testUserProject_onlyHikari_noDependencyPollution()`, `testUserProject_onlyP6Spy_noDependencyPollution()`, `testUserProject_allModules_works()`. Then generate isolation verification report.

2. **向后兼容集成测试 (BackwardCompatibilityIntegrationTest - 7 tests):** Write test class executing: `testDruid_existingCode_works()`, `testHikari_existingCode_works()`, `testP6Spy_existingCode_works()`, `testViolationStrategy_oldImport_compilesWithWarning()`, `testConfiguration_oldFormat_migrates()`, `testAPI_behavior_unchanged()`, `testTestSuite_100percent_passed()`. Then generate compatibility verification report.

3. **性能回归测试 (PerformanceRegressionTest - 5 tests):** Write JMH benchmark executing: `benchmarkDruid_throughput_noRegression()`, `benchmarkHikari_latency_noRegression()`, `benchmarkP6Spy_overhead_documented()`, `benchmarkModuleLoad_startupTime_noIncrease()`, `benchmarkMemory_usage_noIncrease()`. Then generate performance comparison report.

**验收标准:** 所有集成测试通过, 模块隔离验证成功, 向后兼容100%, 性能无回退, 验收报告完整.

---

## Phase 12: Core Architecture Unification - Agent_Architecture_Refactoring, Agent_Testing_Validation

**Phase Overview:** Introduce StatementVisitor unified abstraction for RuleChecker and StatementRewriter, refactor RuleChecker interface to extend StatementVisitor, implement AbstractRuleChecker template method pattern with final check() method dispatching to visitXxx(), migrate all concrete Checker implementations from check() to visitor methods, achieve single SQL parse (N→1) performance optimization through SqlContext enhancement with mandatory statement field, ensure gradual migration with @Deprecated compatibility layer.

**Phase Dependencies:**
- Depends on: Phase 11 output (module structure, though not strictly required)
- Provides for: Phase 13 (StatementVisitor abstraction for InnerInterceptor)

---

### Task 12.1 – SqlContext Statement Field Enhancement │ Agent_Architecture_Refactoring

- **Objective:** Add mandatory Statement field to SqlContext class, retain parsedSql field with @Deprecated annotation for gradual migration, implement compatibility layer ensuring existing code continues working, update Builder pattern supporting both old and new field names.

- **Output:**
  - `SqlContext` class with added `@NonNull Statement statement` field
  - Retained `Statement parsedSql` field marked @Deprecated
  - Compatibility method `getParsedSql()` delegating to `getStatement()`
  - Updated `SqlContext.Builder` with `statement(Statement)` method
  - Retained `Builder.parsedSql(Statement)` method for compatibility
  - Unit tests verifying field interoperability

- **Guidance:** Field addition follows gradual migration pattern: (1) Add new statement field with @NonNull annotation (required field), (2) Keep parsedSql field but mark @Deprecated, (3) Add getParsedSql() compatibility method returning statement value, (4) Update Builder supporting both statement() and parsedSql() methods (parsedSql delegates to statement internally), (5) Constructor accepts both fields, preferring statement if both provided. Compatibility guarantee: existing code using parsedSql continues working without changes, deprecation warnings guide users to migrate. Documentation: Javadoc explains migration path, @since 2.1 tag for new field, @deprecated tag with migration instructions for old field.

**测试矩阵 (10+ tests):**

1. **SqlContext字段兼容性TDD (SqlContextFieldCompatibilityTest - 10 tests):** Write test class covering: `testSqlContext_statement_field_nonNull()`, `testSqlContext_parsedSql_field_deprecated()`, `testSqlContext_getParsedSql_returnsStatement()`, `testSqlContext_getStatement_works()`, `testBuilder_statement_setsField()`, `testBuilder_parsedSql_delegatesToStatement()`, `testBuilder_bothProvided_prefersStatement()`, `testBuilder_onlyStatement_works()`, `testBuilder_onlyParsedSql_worksWithWarning()`, `testDeprecationAnnotation_present()`. Then implement SqlContext enhancement.

**验收标准:** statement字段添加成功, parsedSql兼容层正常, Builder同时支持新旧API, 所有测试通过, Javadoc完整.

---

### Task 12.2 – StatementVisitor Interface Design │ Agent_Architecture_Refactoring

- **Objective:** Design StatementVisitor interface as unified abstraction for Statement-based processing, defining visitSelect/visitUpdate/visitDelete/visitInsert methods with default empty implementations, establishing visitor pattern foundation for RuleChecker and StatementRewriter convergence.

- **Output:**
  - `StatementVisitor` interface in package `com.footstone.sqlguard.visitor`
  - Methods: `visitSelect(Select, SqlContext)`, `visitUpdate(Update, SqlContext)`, `visitDelete(Delete, SqlContext)`, `visitInsert(Insert, SqlContext)`
  - Default implementations (empty methods) for all visit methods
  - Comprehensive Javadoc with visitor pattern explanation and usage examples

- **Guidance:** Interface design follows visitor pattern: define one visit method per Statement type (Select/Update/Delete/Insert), each accepting typed Statement and SqlContext. Default implementations: all methods default to empty (no-op), allowing subclasses to override only relevant methods (e.g., NoWhereClauseChecker only overrides visitUpdate/visitDelete). SqlContext parameter: provides execution context (mapperId, rowBounds, params) to visitor methods. Javadoc: explain visitor pattern benefits (type-safe dispatch, no instanceof chains, extensible), provide code examples showing RuleChecker implementing StatementVisitor, document relationship with AbstractRuleChecker template method.

**测试矩阵 (5+ tests):**

1. **StatementVisitor接口TDD (StatementVisitorInterfaceTest - 5 tests):** Write test class covering: `testStatementVisitor_interface_hasVisitMethods()`, `testStatementVisitor_defaultMethods_empty()`, `testStatementVisitor_selectMethod_signature()`, `testStatementVisitor_updateMethod_signature()`, `testStatementVisitor_javadoc_complete()`. Then create StatementVisitor interface.

**验收标准:** StatementVisitor接口正确, 默认实现为空, 方法签名正确, Javadoc详细.

---

### Task 12.3 – RuleChecker Interface Refactoring │ Agent_Architecture_Refactoring

- **Objective:** Refactor RuleChecker interface to extend StatementVisitor, retain existing check() and isEnabled() methods for backward compatibility, establish foundation for template method pattern in AbstractRuleChecker.

- **Output:**
  - `RuleChecker` interface extending `StatementVisitor`
  - Retained methods: `void check(SqlContext, ValidationResult)`, `boolean isEnabled()`
  - Updated Javadoc explaining new visitor-based usage pattern
  - Migration guide in Javadoc for implementers

- **Guidance:** Interface refactoring maintains backward compatibility: (1) Add extends StatementVisitor to interface declaration, (2) Keep check() method (will become template method in AbstractRuleChecker), (3) Keep isEnabled() method, (4) Javadoc migration guide: explain that implementations should override visitXxx() methods instead of check(), note that AbstractRuleChecker provides template method implementation of check(), provide before/after code examples. No breaking changes: existing RuleChecker implementations continue working (AbstractRuleChecker will provide check() implementation).

**测试矩阵 (4+ tests):**

1. **RuleChecker接口重构TDD (RuleCheckerRefactoringTest - 4 tests):** Write test class covering: `testRuleChecker_extendsStatementVisitor()`, `testRuleChecker_checkMethod_retained()`, `testRuleChecker_isEnabledMethod_retained()`, `testRuleChecker_javadoc_hasMigrationGuide()`. Then refactor RuleChecker interface.

**验收标准:** RuleChecker继承StatementVisitor, check()和isEnabled()方法保留, Javadoc包含迁移指南, 无破坏性变更.

---

### Task 12.4 – AbstractRuleChecker Template Method Implementation │ Agent_Architecture_Refactoring

- **Objective:** Implement template method pattern in AbstractRuleChecker, make check() method final dispatching to visitXxx() based on Statement type, remove old utility methods (extractWhere/extractTableName/extractFields) forcing subclasses to use JSqlParser API directly, add protected visitXxx() default implementations for subclass override.

- **Output:**
  - `AbstractRuleChecker.check()` method as final template method
  - Statement type dispatch logic (instanceof Select/Update/Delete/Insert → visitXxx)
  - Removed methods: `extractWhere()`, `extractTableName()`, `extractFields()`, `FieldExtractorVisitor`
  - Added protected `visitXxx()` default implementations (empty methods)
  - Unit tests verifying template method dispatch

- **Guidance:** Template method implementation: (1) Mark check() as final to prevent override, (2) Implement type dispatch: `if (statement instanceof Select) visitSelect((Select)statement, context)`, repeat for Update/Delete/Insert, (3) Add protected visitXxx() methods with empty default implementations allowing subclass selective override. Utility method removal: delete extractWhere/extractTableName/extractFields - subclasses should use JSqlParser API directly (update.getWhere(), select.getSelectBody().getSelectItems(), delete.getTable().getName()), improves type safety and code clarity. Error handling: wrap visitXxx() calls in try-catch logging errors without failing validation (degradation pattern).

**测试矩阵 (8+ tests):**

1. **AbstractRuleChecker模板方法TDD (AbstractRuleCheckerTemplateTest - 8 tests):** Write test class covering: `testCheck_final_cannotOverride()`, `testCheck_selectStatement_dispatchesToVisitSelect()`, `testCheck_updateStatement_dispatchesToVisitUpdate()`, `testCheck_deleteStatement_dispatchesToVisitDelete()`, `testCheck_insertStatement_dispatchesToVisitInsert()`, `testVisitXxx_defaultImplementation_empty()`, `testUtilityMethods_removed()`, `testErrorHandling_logsWithoutFailing()`. Then implement template method pattern.

**验收标准:** check()为final方法, 分发逻辑正确, 工具方法已删除, 默认visitXxx()实现为空, 测试通过.

---

### Task 12.5 – NoWhereClauseChecker Migration │ Agent_Architecture_Refactoring

- **Objective:** Migrate NoWhereClauseChecker from check() implementation to visitUpdate/visitDelete methods, use JSqlParser API directly (update.getWhere(), delete.getWhere()) instead of utility methods, update tests renaming parsedSql → statement, verify behavior unchanged.

- **Output:**
  - `NoWhereClauseChecker` with visitUpdate() and visitDelete() implementations
  - Removed check() method implementation
  - Direct JSqlParser API usage (no extractWhere utility)
  - Updated `NoWhereClauseCheckerTest` with statement field
  - Behavior verification tests confirming consistency

- **Guidance:** Migration pattern: (1) Delete existing check() method body, (2) Add visitUpdate() method: `Expression where = update.getWhere(); String tableName = update.getTable().getName();` then check conditions, (3) Add visitDelete() method: similar to visitUpdate with delete.getWhere(), (4) Direct API usage eliminates intermediate utility calls improving clarity. Test migration: rename `.parsedSql(parser.parse(sql))` to `.statement(parser.parse(sql))`, verify violation detection unchanged (same SQL produces same violations), compare new test results with old test snapshots.

**测试矩阵 (8+ tests):**

1. **NoWhereClauseChecker迁移TDD (NoWhereClauseCheckerMigrationTest - 8 tests):** Write test class covering: `testVisitUpdate_noWhere_violates()`, `testVisitUpdate_withWhere_passes()`, `testVisitUpdate_excludedTable_passes()`, `testVisitDelete_noWhere_violates()`, `testVisitDelete_withWhere_passes()`, `testVisitDelete_excludedTable_passes()`, `testBehavior_matchesOldImplementation()`, `testStatementField_works()`. Then migrate NoWhereClauseChecker.

**验收标准:** NoWhereClauseChecker迁移完成, visitUpdate/visitDelete实现正确, 直接使用JSqlParser API, 测试通过, 行为不变.

---

### Task 12.6 – BlacklistFieldChecker Migration │ Agent_Architecture_Refactoring

- **Objective:** Migrate BlacklistFieldChecker from check() implementation to visitSelect method, use JSqlParser API directly (selectBody.getSelectItems()) instead of utility methods, update tests renaming parsedSql → statement, verify blacklist field detection logic unchanged.

- **Output:**
  - `BlacklistFieldChecker` with visitSelect() implementation
  - Removed check() method implementation
  - Direct JSqlParser SelectItem extraction (no extractFields utility)
  - Updated `BlacklistFieldCheckerTest` with statement field
  - Behavior verification tests confirming consistency

- **Guidance:** Migration pattern: (1) Delete existing check() method body, (2) Add visitSelect() method: `List<SelectItem> items = ((PlainSelect) select.getSelectBody()).getSelectItems(); Set<String> fields = extractFieldNames(items);` then check blacklist, (3) Direct API usage for field extraction from SelectItem (Column, AllColumns, AllTableColumns), handle SELECT * specially. Test migration: rename `.parsedSql(parser.parse(sql))` to `.statement(parser.parse(sql))`, verify blacklist detection unchanged (same SQL with blacklist fields produces same violations), compare new test results with old test snapshots.

**测试矩阵 (10+ tests):**

1. **BlacklistFieldChecker迁移TDD (BlacklistFieldCheckerMigrationTest - 10 tests):** Write test class covering: `testVisitSelect_blacklistField_violates()`, `testVisitSelect_selectStar_violates()`, `testVisitSelect_allowedFields_passes()`, `testVisitSelect_mixedFields_partialViolation()`, `testVisitSelect_aliasedBlacklistField_violates()`, `testVisitSelect_excludedTable_passes()`, `testVisitSelect_subquery_detectsBlacklist()`, `testVisitSelect_join_detectsBlacklist()`, `testBehavior_matchesOldImplementation()`, `testStatementField_works()`. Then migrate BlacklistFieldChecker.

**验收标准:** BlacklistFieldChecker迁移完成, visitSelect实现正确, 直接使用JSqlParser API, 测试通过, 行为不变.

---

### Task 12.7 – WhitelistFieldChecker Migration │ Agent_Architecture_Refactoring

- **Objective:** Migrate WhitelistFieldChecker from check() implementation to visitSelect method, use JSqlParser API directly (selectBody.getSelectItems()) instead of utility methods, update tests renaming parsedSql → statement, verify whitelist field validation logic unchanged.

- **Output:**
  - `WhitelistFieldChecker` with visitSelect() implementation
  - Removed check() method implementation
  - Direct JSqlParser SelectItem extraction (no extractFields utility)
  - Updated `WhitelistFieldCheckerTest` with statement field
  - Behavior verification tests confirming consistency

- **Guidance:** Migration pattern: (1) Delete existing check() method body, (2) Add visitSelect() method: `List<SelectItem> items = ((PlainSelect) select.getSelectBody()).getSelectItems(); Set<String> fields = extractFieldNames(items);` then check whitelist, (3) Direct API usage for field extraction, handle SELECT * by rejecting (not in whitelist unless explicitly configured). Test migration: rename `.parsedSql(parser.parse(sql))` to `.statement(parser.parse(sql))`, verify whitelist validation unchanged (same SQL with non-whitelisted fields produces same violations), compare new test results with old test snapshots.

**测试矩阵 (10+ tests):**

1. **WhitelistFieldChecker迁移TDD (WhitelistFieldCheckerMigrationTest - 10 tests):** Write test class covering: `testVisitSelect_nonWhitelistField_violates()`, `testVisitSelect_selectStar_violates()`, `testVisitSelect_whitelistFields_passes()`, `testVisitSelect_mixedFields_partialViolation()`, `testVisitSelect_aliasedNonWhitelistField_violates()`, `testVisitSelect_excludedTable_passes()`, `testVisitSelect_subquery_detectsNonWhitelist()`, `testVisitSelect_join_detectsNonWhitelist()`, `testBehavior_matchesOldImplementation()`, `testStatementField_works()`. Then migrate WhitelistFieldChecker.

**验收标准:** WhitelistFieldChecker迁移完成, visitSelect实现正确, 直接使用JSqlParser API, 测试通过, 行为不变.

---

### Task 12.8 – NoPaginationChecker Migration │ Agent_Architecture_Refactoring

- **Objective:** Migrate NoPaginationChecker from check() implementation to visitSelect method, checking if select contains LIMIT/OFFSET clauses or RowBounds pagination, update tests renaming parsedSql → statement, verify pagination detection logic unchanged.

- **Output:**
  - `NoPaginationChecker` with visitSelect() implementation
  - Removed check() method implementation
  - Direct JSqlParser Limit/Offset detection (select.getLimit(), select.getOffset())
  - Updated `NoPaginationCheckerTest` with statement field
  - Behavior verification tests confirming consistency

- **Guidance:** Migration pattern: (1) Delete existing check() method body, (2) Add visitSelect() method: `Limit limit = ((PlainSelect) select.getSelectBody()).getLimit(); Offset offset = ((PlainSelect) select.getSelectBody()).getOffset();` check if both null AND RowBounds not present, (3) RowBounds detection from SqlContext.getRowBounds(). Test migration: rename `.parsedSql(parser.parse(sql))` to `.statement(parser.parse(sql))`, verify pagination detection unchanged (SQL without LIMIT produces violation, SQL with LIMIT passes), compare new test results with old test snapshots.

**测试矩阵 (8+ tests):**

1. **NoPaginationChecker迁移TDD (NoPaginationCheckerMigrationTest - 8 tests):** Write test class covering: `testVisitSelect_noLimit_violates()`, `testVisitSelect_withLimit_passes()`, `testVisitSelect_withOffset_passes()`, `testVisitSelect_withLimitAndOffset_passes()`, `testVisitSelect_withRowBounds_passes()`, `testVisitSelect_excludedTable_passes()`, `testBehavior_matchesOldImplementation()`, `testStatementField_works()`. Then migrate NoPaginationChecker.

**验收标准:** NoPaginationChecker迁移完成, visitSelect实现正确, 直接使用JSqlParser API, RowBounds检测正确, 测试通过, 行为不变.

---

### Task 12.9 – Other Checker Migration │ Agent_Architecture_Refactoring

- **Objective:** Migrate remaining 6 Checkers (LogicalPaginationChecker, DeepPaginationChecker, DummyConditionChecker, EstimatedRowsChecker, MissingOrderByChecker, LargePageSizeChecker) from check() implementation to visitXxx() methods, use JSqlParser API directly, update all tests, verify DefaultSqlSafetyValidator integration works correctly.

- **Output:**
  - `LogicalPaginationChecker` with visitSelect() implementation
  - `DeepPaginationChecker` with visitSelect() implementation
  - `DummyConditionChecker` with visitUpdate/visitDelete() implementations
  - `EstimatedRowsChecker` with visitSelect/visitUpdate/visitDelete() implementations
  - `MissingOrderByChecker` with visitSelect() implementation
  - `LargePageSizeChecker` with visitSelect() implementation
  - Updated tests for all 6 Checkers (rename parsedSql → statement)
  - Integration test verifying DefaultSqlSafetyValidator orchestrates all Checkers correctly

- **Guidance:** **Migration pattern established by Tasks 12.5-12.8**: These 6 remaining Checkers follow the migration pattern already validated in previous tasks (delete check() method, add visitXxx() methods, use JSqlParser API directly, update tests with statement field). Since the migration approach has been proven, these Checkers can be migrated in parallel or in quick succession using the established template. Migration pattern for each Checker: (1) Identify Statement types it handles (Select/Update/Delete/Insert), (2) Delete check() method body, (3) Add visitXxx() methods using JSqlParser API directly (no intermediate parsing), (4) Update tests renaming parsedSql → statement. Specific Checkers: LogicalPaginationChecker detects LIMIT with dummy conditions (WHERE 1=1), DeepPaginationChecker detects high OFFSET values, DummyConditionChecker detects WHERE 1=1 / WHERE true, EstimatedRowsChecker estimates affected rows from table statistics, MissingOrderByChecker detects pagination without ORDER BY, LargePageSizeChecker detects LIMIT > threshold. Integration testing: verify DefaultSqlSafetyValidator.validate() invokes all Checkers in sequence, violations accumulate correctly in ValidationResult, disabled Checkers skipped, performance acceptable.

**测试矩阵 (30+ tests):**

1. **LogicalPaginationChecker迁移TDD (LogicalPaginationCheckerMigrationTest - 5 tests):** Write test class covering: `testVisitSelect_limitWithDummyCondition_violates()`, `testVisitSelect_limitWithRealCondition_passes()`, `testVisitSelect_noLimit_skipped()`, `testBehavior_matchesOldImplementation()`, `testStatementField_works()`. Then migrate LogicalPaginationChecker.

2. **DeepPaginationChecker迁移TDD (DeepPaginationCheckerMigrationTest - 5 tests):** Write test class covering: `testVisitSelect_deepOffset_violates()`, `testVisitSelect_shallowOffset_passes()`, `testVisitSelect_noOffset_passes()`, `testBehavior_matchesOldImplementation()`, `testStatementField_works()`. Then migrate DeepPaginationChecker.

3. **DummyConditionChecker迁移TDD (DummyConditionCheckerMigrationTest - 6 tests):** Write test class covering: `testVisitUpdate_where1equals1_violates()`, `testVisitUpdate_whereTrue_violates()`, `testVisitUpdate_realCondition_passes()`, `testVisitDelete_dummyCondition_violates()`, `testBehavior_matchesOldImplementation()`, `testStatementField_works()`. Then migrate DummyConditionChecker.

4. **EstimatedRowsChecker迁移TDD (EstimatedRowsCheckerMigrationTest - 6 tests):** Write test class covering: `testVisitSelect_estimatedRowsExceedsThreshold_violates()`, `testVisitUpdate_estimatedRowsExceedsThreshold_violates()`, `testVisitDelete_estimatedRowsExceedsThreshold_violates()`, `testVisitXxx_belowThreshold_passes()`, `testBehavior_matchesOldImplementation()`, `testStatementField_works()`. Then migrate EstimatedRowsChecker.

5. **MissingOrderByChecker迁移TDD (MissingOrderByCheckerMigrationTest - 4 tests):** Write test class covering: `testVisitSelect_limitWithoutOrderBy_violates()`, `testVisitSelect_limitWithOrderBy_passes()`, `testBehavior_matchesOldImplementation()`, `testStatementField_works()`. Then migrate MissingOrderByChecker.

6. **LargePageSizeChecker迁移TDD (LargePageSizeCheckerMigrationTest - 4 tests):** Write test class covering: `testVisitSelect_limitExceedsThreshold_violates()`, `testVisitSelect_limitBelowThreshold_passes()`, `testBehavior_matchesOldImplementation()`, `testStatementField_works()`. Then migrate LargePageSizeChecker.

**验收标准:** 所有6个Checker迁移完成, visitXxx()实现正确, 直接使用JSqlParser API, 所有测试通过, DefaultSqlSafetyValidator集成正常.

---

### Task 12.10 – Test Migration │ Agent_Testing_Validation

- **Objective:** Execute global test migration renaming parsedSql → statement field across 46 test files, update Checker unit test construction approach (pass ValidationResult instead of return), verify syntax correctness, run all tests ensuring 100% pass rate, compare new/old test result consistency.

- **Output:**
  - 46 test files migrated (parsedSql → statement field rename)
  - Updated Checker unit test construction pattern
  - Test migration report: `.apm/Assignments/Phase_12/Task_12_10_Test_Migration_Report.md`
  - Test result comparison data (before/after migration)
  - All tests passing (100% pass rate)

- **Guidance:** Test migration execution: (1) Use Grep to find all test files using parsedSql: `grep -r "parsedSql" --include="*Test.java"` (expect ~46 files), (2) Batch replace: `.parsedSql(` → `.statement(` using global find/replace, (3) Update Checker unit test pattern: OLD: `result = checker.check(context); assertFalse(result.isPassed());` NEW: `ValidationResult result = new ValidationResult(); checker.check(context, result); assertFalse(result.isPassed());`, (4) Run all tests: `mvn test`, verify 100% pass, (5) Compare test results: run tests before migration (capture baseline), run tests after migration, verify violation counts match, SQL patterns detected match, performance within 5% variance. Test migration report documents: files changed count, test methods updated count, pass rate before/after, behavior consistency verification results.

**测试矩阵 (12+ tests):**

1. **测试迁移验证TDD (TestMigrationVerificationTest - 12 tests):** Write test suite covering: `testAllTestFiles_parsedSqlRenamed_toStatement()`, `testCheckerUnitTests_newConstructionPattern_works()`, `testValidationResult_passedToChecker_correctBehavior()`, `testAllTests_100percentPass()`, `testViolationDetection_matchesBaseline()`, `testSqlPatterns_detectedConsistently()`, `testPerformance_within5percentVariance()`, `testFieldRename_syntaxCorrect()`, `testNoRegressions_introduced()`, `testTestCoverage_maintained()`, `testEdgeCases_stillHandled()`, `testDeprecatedField_compatibility_verified()`. Then execute test migration and generate report.

**验收标准:** 46个测试文件全部迁移, parsedSql→statement重命名完成, 所有测试100%通过, 行为一致性验证通过, 迁移报告完整.

---

### Task 12.11 – Performance Verification │ Agent_Testing_Validation

- **Objective:** Verify SQL parsing count reduction from N times (each Checker parses) to 1 time (interceptor parses once, Checkers reuse), measure performance improvement through benchmark testing, generate performance comparison report documenting parsing count, total time, memory usage before/after refactoring.

- **Output:**
  - `ParsingPerformanceTest` using Mockito.spy to monitor parse() call count
  - Performance benchmark suite (JMH or custom)
  - Performance comparison report: `.apm/Assignments/Phase_12/Task_12_11_Performance_Report.md`
  - Parsing count verification: N→1 reduction confirmed
  - Performance metrics: throughput, latency P50/P99, memory usage

- **Guidance:** Performance verification approach: (1) Parsing count verification: use Mockito.spy on JSqlParserFacade, mock parse() method, count invocations during validation, OLD: count = number of enabled Checkers (N), NEW: count = 1 (parsed once in interceptor, cached in SqlContext), (2) Benchmark testing: create test SQL set (100 SQLs with varying complexity), measure OLD: each Checker parses independently, measure NEW: parse once + visitXxx(), record throughput (SQLs/second), latency (P50/P99 milliseconds), memory usage (heap allocation), (3) Performance comparison: expect parsing reduction proportional to Checker count (e.g., 10 Checkers: 90% reduction in parse calls), expect total time reduction 30-50% (parsing is major overhead), expect memory reduction due to single Statement instance. Performance report includes: parsing count comparison chart, time comparison chart, memory usage comparison, conclusion with measurable improvement metrics.

**测试矩阵 (10+ tests):**

1. **解析次数验证TDD (ParsingCountVerificationTest - 5 tests):** Write test class using Mockito.spy covering: `testOldArchitecture_parsesNTimes_whereNisCheckerCount()`, `testNewArchitecture_parsesOnce_cachesInSqlContext()`, `testParsingReduction_90percent_with10Checkers()`, `testSpyVerification_parseInvocationCount_accurate()`, `testCacheHit_Statement_reusedByCheckers()`. Then implement parsing count verification.

2. **性能基准测试TDD (PerformanceBenchmarkTest - 5 tests):** Write JMH benchmark covering: `benchmarkOldArchitecture_throughput_baseline()`, `benchmarkNewArchitecture_throughput_improved()`, `benchmarkOldArchitecture_latencyP99_baseline()`, `benchmarkNewArchitecture_latencyP99_improved()`, `benchmarkMemoryUsage_reduction_confirmed()`. Then run benchmark and generate comparison data.

**验收标准:** 解析次数从N次减少到1次, 性能提升30-50%, 内存占用减少, 性能报告完整, 基准测试可重复.

---

## Phase 13: InnerInterceptor Architecture - Agent_Advanced_Interceptor, Agent_Testing_Validation

**Phase Overview:** Implement MyBatis-Plus style InnerInterceptor architecture with priority control mechanism, create SelectLimitInnerInterceptor automatic LIMIT fallback feature, implement ThreadLocal Statement sharing to avoid duplicate parsing across interceptor chain, support multi-version compatibility for MyBatis 3.4.x/3.5.x and MyBatis-Plus 3.4.x/3.5.x, establish comprehensive CI/CD test matrix.

**Phase Dependencies:**
- Depends on: Phase 11 output (module structure, though not strictly required)
- Depends on: Phase 12 output (StatementVisitor abstraction, SqlContext with statement field)
- Provides for: Production-ready InnerInterceptor architecture with fallback safety

---

### Task 13.1 – SqlGuardInnerInterceptor Interface Design │ Agent_Advanced_Interceptor

- **Objective:** Design SqlGuardInnerInterceptor interface referencing MyBatis-Plus InnerInterceptor pattern, define willDoQuery/beforeQuery/willDoUpdate/beforeUpdate/getPriority methods establishing interceptor lifecycle, document priority mechanism (1-99 for check interceptors, 100-199 for fallback interceptors), provide comprehensive Javadoc with integration examples.

- **Output:**
  - `SqlGuardInnerInterceptor` interface in package `com.footstone.sqlguard.interceptor.inner`
  - Methods: `boolean willDoQuery(Executor, MappedStatement, Object, RowBounds, ResultHandler, BoundSql)`, `void beforeQuery(Executor, MappedStatement, Object, RowBounds, ResultHandler, BoundSql)`, similar for Update/Delete
  - Method: `int getPriority()` defining execution order
  - Comprehensive Javadoc explaining lifecycle, priority ranges, usage examples
  - Design document: `.apm/Assignments/Phase_13/Task_13_1_InnerInterceptor_Design.md`

- **Guidance:** Interface design follows MyBatis-Plus InnerInterceptor pattern: (1) Lifecycle methods: willDoQuery() called before query execution for pre-filtering (return false to skip), beforeQuery() called after willDoQuery() for SQL modification/validation, similarly willDoUpdate()/beforeUpdate() for INSERT/UPDATE/DELETE, (2) Priority mechanism: getPriority() returns int (1-99: check interceptors like SqlGuardCheckInnerInterceptor, 100-199: fallback interceptors like SelectLimitInnerInterceptor, 200+: rewrite interceptors), lower priority number executes first, (3) SqlGuardInterceptor orchestration: sorts all InnerInterceptors by priority, invokes willDoXxx() chain (any false stops execution), invokes beforeXxx() chain (modifies BoundSql if needed). Javadoc examples: show how to implement custom InnerInterceptor, explain relationship with SqlGuardInterceptor, document best practices for priority assignment.

**测试矩阵 (8+ tests):**

1. **InnerInterceptor接口设计TDD (InnerInterceptorInterfaceDesignTest - 8 tests):** Write test class covering: `testInterface_hasWillDoQueryMethod()`, `testInterface_hasBeforeQueryMethod()`, `testInterface_hasWillDoUpdateMethod()`, `testInterface_hasBeforeUpdateMethod()`, `testInterface_hasGetPriorityMethod()`, `testMethodSignatures_matchMyBatisPlusPattern()`, `testDefaultMethods_providedForOptionalOverride()`, `testJavadoc_complete_withExamples()`. Then create SqlGuardInnerInterceptor interface.

**验收标准:** SqlGuardInnerInterceptor接口正确, 生命周期方法完整, 优先级机制明确, Javadoc详细, 设计文档完整.

---

### Task 13.2 – SqlGuardInterceptor Main Interceptor Implementation │ Agent_Advanced_Interceptor

- **Objective:** Implement SqlGuardInterceptor as MyBatis Interceptor managing List<SqlGuardInnerInterceptor> with priority sorting, parse SQL once and cache to ThreadLocal before invoking inner interceptor chain, implement lifecycle method invocation (willDoQuery → beforeQuery for SELECT, willDoUpdate → beforeUpdate for UPDATE/DELETE), add ThreadLocal cleanup in finally block preventing memory leaks.

- **Output:**
  - `SqlGuardInterceptor` implementing `org.apache.ibatis.plugin.Interceptor`
  - @Intercepts annotation targeting Executor.query/update methods
  - Priority-sorted InnerInterceptor chain management
  - SQL parsing and ThreadLocal caching logic
  - Lifecycle method orchestration (willDoXxx → beforeXxx)
  - ThreadLocal cleanup in finally block
  - `SqlGuardInterceptorTest` verifying priority sorting, lifecycle invocation, ThreadLocal cleanup

- **Guidance:** Implementation approach: (1) InnerInterceptor chain management: constructor accepts List<SqlGuardInnerInterceptor>, sorts by getPriority() in ascending order (lower priority first), (2) intercept() method: parse SQL using JSqlParserFacade.parse(), cache Statement in StatementContext.cache(sql, statement), invoke InnerInterceptor chain (willDoXxx → beforeXxx), cleanup ThreadLocal in finally, (3) Lifecycle invocation: for query operations call willDoQuery() on all interceptors (stop if any returns false), then call beforeQuery() on all interceptors (each can modify BoundSql), for update operations call willDoUpdate()/beforeUpdate() similarly, (4) ThreadLocal cleanup: wrap interceptor chain invocation in try-finally, call StatementContext.clear() in finally block, (5) Spring integration: provide @Bean factory method registering SqlGuardInterceptor with injected InnerInterceptor list. Test priority sorting: create 3 InnerInterceptors with priorities 50, 10, 100, verify execution order is 10→50→100.

**测试矩阵 (12+ tests):**

1. **主拦截器实现TDD (MainInterceptorImplementationTest - 12 tests):** Write test class covering: `testInterceptor_implementsMyBatisInterceptor()`, `testInterceptor_sortsInnerInterceptors_byPriority()`, `testIntercept_parsesSQL_cachesToThreadLocal()`, `testIntercept_invokesWillDoQuery_beforeBeforeQuery()`, `testIntercept_invokesWillDoUpdate_beforeBeforeUpdate()`, `testIntercept_stopsChain_ifWillDoXxxReturnsFalse()`, `testIntercept_cleansUpThreadLocal_inFinally()`, `testIntercept_handlesException_cleansUpThreadLocal()`, `testPriorityOrder_10_50_100_executesCorrectly()`, `testMultipleInnerInterceptors_allInvoked()`, `testSpringIntegration_beanFactory_works()`, `testAnnotation_interceptsExecutorMethods()`. Then implement SqlGuardInterceptor.

**验收标准:** SqlGuardInterceptor实现正确, 优先级排序正常, SQL解析缓存正确, 生命周期调用顺序正确, ThreadLocal清理无遗漏, 测试通过.

---

### Task 13.3 – SqlGuardCheckInnerInterceptor Implementation │ Agent_Advanced_Interceptor

- **Objective:** Implement SqlGuardCheckInnerInterceptor bridging RuleChecker and InnerInterceptor, getPriority() returns 10 (high priority for checks), in willDoQuery/willDoUpdate attempt to get Statement from StatementContext (reuse if cached, parse and cache if not), build SqlContext with cached Statement, execute all enabled RuleChecker.check(), handle violations based on ViolationStrategy (BLOCK throws SQLException, WARN/LOG logs).

- **Output:**
  - `SqlGuardCheckInnerInterceptor` implementing `SqlGuardInnerInterceptor`
  - Priority: 10 (check interceptors run first)
  - Statement retrieval/caching logic
  - RuleChecker bridge: iterates all enabled Checkers, invokes check()
  - Violation handling: ViolationStrategy.BLOCK throws SQLException, WARN/LOG logs warnings
  - `SqlGuardCheckInnerInterceptorTest` verifying Statement caching, RuleChecker bridging, violation handling

- **Guidance:** Implementation approach: (1) getPriority() returns 10 ensuring checks run before fallback interceptors, (2) willDoQuery() implementation: extract SQL from BoundSql, attempt StatementContext.get(sql), if null parse using JSqlParserFacade.parse() and StatementContext.cache(sql, statement), build SqlContext with statement field, execute all enabled RuleCheckers iterating checkers list and calling check(context, result), handle violations based on strategy (BLOCK: throw SQLException wrapping violations, WARN: log.warn, LOG: log.info), return true to continue chain, (3) willDoUpdate() implementation: similar to willDoQuery() but for INSERT/UPDATE/DELETE, (4) beforeQuery()/beforeUpdate(): no-op (checks don't modify SQL), (5) RuleChecker integration: inject List<RuleChecker> via constructor, filter enabled Checkers using isEnabled(), invoke check() on filtered list. Test Statement caching: verify get() called first, parse() only if cache miss, cache() called after parse, subsequent calls reuse cached Statement.

**测试矩阵 (15+ tests):**

1. **CheckInnerInterceptor实现TDD (CheckInnerInterceptorImplementationTest - 15 tests):** Write test class covering: `testGetPriority_returns10()`, `testWillDoQuery_getsStatementFromCache_ifExists()`, `testWillDoQuery_parsesAndCaches_ifCacheMiss()`, `testWillDoQuery_buildsSqlContext_withStatement()`, `testWillDoQuery_invokesAllEnabledCheckers()`, `testWillDoQuery_skipsDisabledCheckers()`, `testWillDoUpdate_similarBehavior()`, `testViolationStrategy_BLOCK_throwsSQLException()`, `testViolationStrategy_WARN_logsWarning()`, `testViolationStrategy_LOG_logsInfo()`, `testMultipleViolations_aggregatedInResult()`, `testNoViolations_returnTrue_continueChain()`, `testBeforeQuery_noOp()`, `testBeforeUpdate_noOp()`, `testRuleCheckerIntegration_works()`. Then implement SqlGuardCheckInnerInterceptor.

**验收标准:** SqlGuardCheckInnerInterceptor实现正确, 优先级为10, Statement缓存复用正常, RuleChecker桥接正确, 违规策略处理正确, 测试通过.

---

### Task 13.4 – SqlGuardRewriteInnerInterceptor Implementation │ Agent_Advanced_Interceptor

- **Objective:** Implement SqlGuardRewriteInnerInterceptor bridging StatementRewriter and InnerInterceptor, getPriority() returns 200 (low priority for rewrites), in beforeQuery get Statement from StatementContext (reuse CheckInnerInterceptor's parse), execute all StatementRewriter.rewrite(), if rewrite returns new Statement replace SQL in BoundSql using reflection, support chain rewrites updating SqlContext with new Statement after each rewrite for next rewriter.

- **Output:**
  - `SqlGuardRewriteInnerInterceptor` implementing `SqlGuardInnerInterceptor`
  - Priority: 200 (rewrite interceptors run after checks)
  - Statement retrieval from ThreadLocal cache
  - StatementRewriter bridge: iterates all enabled Rewriters, invokes rewrite()
  - BoundSql SQL replacement using reflection (MyBatis 3.x compatibility)
  - Chain rewrite support: updates SqlContext after each rewrite
  - `SqlGuardRewriteInnerInterceptorTest` verifying Statement reuse, rewrite chaining, SQL replacement

- **Guidance:** Implementation approach: (1) getPriority() returns 200 ensuring rewrites run after checks, (2) willDoQuery()/willDoUpdate(): no-op (rewrites don't pre-filter), (3) beforeQuery() implementation: extract SQL from BoundSql, get Statement from StatementContext.get(sql) (reuses CheckInnerInterceptor's cached Statement), build SqlContext with statement, iterate all StatementRewriters calling rewrite(statement, context), if rewrite returns new Statement: replace SQL in BoundSql via reflection (BoundSql.sql field is final, use reflection to modify), update StatementContext.cache(newSql, newStatement) for next rewriter, update SqlContext.statement for chain rewrites, (4) BoundSql modification: use reflection to access final sql field: `Field sqlField = BoundSql.class.getDeclaredField("sql"); sqlField.setAccessible(true); sqlField.set(boundSql, newSql);`, handle MyBatis 3.4.x/3.5.x compatibility, (5) Chain rewrite support: after each rewrite updates SqlContext so next rewriter sees modified Statement. Test chain rewrites: create 2 Rewriters (Rewriter1 adds WHERE clause, Rewriter2 adds ORDER BY), verify final SQL has both modifications.

**测试矩阵 (12+ tests):**

1. **RewriteInnerInterceptor实现TDD (RewriteInnerInterceptorImplementationTest - 12 tests):** Write test class covering: `testGetPriority_returns200()`, `testWillDoQuery_noOp_returnsTrue()`, `testBeforeQuery_getsStatementFromCache()`, `testBeforeQuery_invokesAllEnabledRewriters()`, `testBeforeQuery_skipsDisabledRewriters()`, `testBeforeQuery_replacesSQL_inBoundSql()`, `testBeforeQuery_updatesStatementContext_afterRewrite()`, `testBeforeQuery_updatesSqlContext_forChainRewrite()`, `testChainRewrite_twoRewriters_bothApplied()`, `testReflection_boundSqlModification_works()`, `testMyBatis34_compatibility()`, `testMyBatis35_compatibility()`. Then implement SqlGuardRewriteInnerInterceptor.

**验收标准:** SqlGuardRewriteInnerInterceptor实现正确, 优先级为200, Statement复用正常, StatementRewriter桥接正确, SQL替换正确, 链式重写支持, 测试通过.

---

### Task 13.5 – SelectLimitInnerInterceptor Implementation │ Agent_Advanced_Interceptor

- **Objective:** Implement SelectLimitInnerInterceptor extending JsqlParserSupport for automatic LIMIT addition fallback, getPriority() returns 150 (between checks and rewrites), processSelect() checks if Statement already has LIMIT/OFFSET or RowBounds indicates pagination, if no pagination detected add LIMIT clause using database dialect (MySQL/Oracle/SQL Server/PostgreSQL), create SqlGuardDialect interface and implementations, implement DialectFactory auto-detecting database type from Connection metadata.

- **Output:**
  - `SelectLimitInnerInterceptor` extending `JsqlParserSupport` implementing `SqlGuardInnerInterceptor`
  - Priority: 150 (fallback interceptors between checks and rewrites)
  - processSelect() method detecting existing pagination and adding LIMIT if missing
  - `SqlGuardDialect` interface defining `applyLimit(Select, long limit)` method
  - Dialect implementations: `MySQLDialect`, `OracleDialect`, `SQLServerDialect`, `PostgreSQLDialect`
  - `DialectFactory` auto-detecting database type from DatabaseMetaData
  - Configuration: default limit value (configurable, default 1000)
  - `SelectLimitInnerInterceptorTest` verifying pagination detection, LIMIT addition, multi-dialect support

- **Guidance:** **Implementation sequence for multi-dialect support**: (1) First implement SqlGuardDialect interface defining applyLimit(Select, long) contract and getDatabaseType() method, (2) Implement MySQLDialect as reference implementation adding `LIMIT {limit}` clause to PlainSelect, (3) Implement SelectLimitInnerInterceptor core extending JsqlParserSupport with processSelect() detecting existing pagination and applying MySQLDialect, (4) Verify MySQL dialect works correctly through integration tests, (5) Implement remaining 3 dialects: OracleDialect (wraps with ROWNUM), SQLServerDialect (adds TOP), PostgreSQLDialect (adds LIMIT), (6) Implement DialectFactory auto-detection reading DatabaseMetaData.getDatabaseProductName() and caching result, (7) Integrate DialectFactory into SelectLimitInnerInterceptor replacing hardcoded MySQLDialect. Implementation approach: getPriority() returns 150 positioning fallback between checks (10) and rewrites (200), Extend JsqlParserSupport abstract class providing processSelect() template method, processSelect() logic: check if PlainSelect already has Limit clause (select.getLimit() != null), check if RowBounds present (context.getRowBounds() not default RowBounds.DEFAULT), if either present return sql unchanged (already paginated), otherwise detect database dialect and apply LIMIT. Dialect interface: `public interface SqlGuardDialect { void applyLimit(Select select, long limit); String getDatabaseType(); }`. Configuration: inject default limit value via constructor (default 1000), make configurable via Spring properties. Test multi-dialect: verify MySQL adds LIMIT, Oracle wraps ROWNUM, SQL Server adds TOP, PostgreSQL adds LIMIT.

**测试矩阵 (20+ tests):**

1. **SelectLimitInnerInterceptor实现TDD (SelectLimitInnerInterceptorImplementationTest - 10 tests):** Write test class covering: `testGetPriority_returns150()`, `testProcessSelect_existingLimit_noModification()`, `testProcessSelect_existingOffset_noModification()`, `testProcessSelect_rowBoundsPresent_noModification()`, `testProcessSelect_noPagination_addsLimit()`, `testProcessSelect_defaultLimit_1000()`, `testProcessSelect_customLimit_configurable()`, `testProcessSelect_subquery_notModified()`, `testProcessSelect_union_notModified()`, `testJsqlParserSupport_integration_works()`. Then implement SelectLimitInnerInterceptor.

2. **Dialect实现TDD (DialectImplementationTest - 10 tests):** Write test class covering: `testMySQLDialect_addsLIMIT()`, `testOracleDialect_wrapsROWNUM()`, `testSQLServerDialect_addsTOP()`, `testPostgreSQLDialect_addsLIMIT()`, `testDialectFactory_detectsMySQL()`, `testDialectFactory_detectsOracle()`, `testDialectFactory_detectsSQLServer()`, `testDialectFactory_detectsPostgreSQL()`, `testDialectFactory_caching_works()`, `testDialectInterface_contract()`. Then implement dialect classes and factory.

**验收标准:** SelectLimitInnerInterceptor实现正确, 优先级为150, 分页检测准确, LIMIT自动添加正确, 支持MySQL/Oracle/SQL Server/PostgreSQL四种数据库, 配置灵活, 测试通过.

---

### Task 13.6 – StatementContext ThreadLocal Sharing │ Agent_Advanced_Interceptor

- **Objective:** Create StatementContext class in sql-guard-core providing ThreadLocal<Map<String, Statement>> cache for sharing parsed Statement across InnerInterceptor chain, implement cache()/get()/clear() methods, ensure thread safety and memory leak prevention through rigorous cleanup, verify isolation between concurrent requests.

- **Output:**
  - `StatementContext` class in package `com.footstone.sqlguard.parser`
  - ThreadLocal<Map<String, Statement>> cache implementation
  - Methods: `cache(String sql, Statement statement)`, `get(String sql)`, `clear()`
  - Thread isolation verification
  - Memory leak prevention through cleanup
  - `StatementContextTest` verifying ThreadLocal isolation, cache/get correctness, memory leak prevention

- **Guidance:** Implementation approach: (1) ThreadLocal cache: `private static final ThreadLocal<Map<String, Statement>> CACHE = ThreadLocal.withInitial(HashMap::new);` ensuring each thread has independent cache, (2) cache() method: `CACHE.get().put(sql, statement);` stores Statement keyed by SQL string, (3) get() method: `return CACHE.get().get(sql);` retrieves cached Statement or returns null, (4) clear() method: `CACHE.remove();` removes ThreadLocal value preventing memory leaks (critical: call in finally block), (5) Usage pattern: SqlGuardInterceptor calls clear() in finally after processing request, CheckInnerInterceptor calls get() then cache() if miss, RewriteInnerInterceptor calls get() to reuse, SelectLimitInnerInterceptor calls get() to check pagination. Test ThreadLocal isolation: create 2 threads, cache different Statements with same SQL key, verify each thread sees only its own cached value. Test memory leak prevention: cache 1000 Statements, call clear(), verify ThreadLocal map is empty, verify no memory retained.

**测试矩阵 (10+ tests):**

1. **StatementContext实现TDD (StatementContextImplementationTest - 10 tests):** Write test class covering: `testCache_storesStatement_keyedBySql()`, `testGet_retrievesCachedStatement()`, `testGet_returnsNull_ifNotCached()`, `testClear_removesThreadLocalValue()`, `testThreadLocalIsolation_differentThreads_independentCaches()`, `testConcurrentAccess_threadSafe()`, `testMemoryLeakPrevention_clear_releasesMemory()`, `testMultipleSql_cacheIndependently()`, `testCacheMiss_parseAndCache_pattern()`, `testUsagePattern_interceptorChain_works()`. Then implement StatementContext.

**验收标准:** StatementContext实现正确, ThreadLocal缓存正常, cache/get/clear方法正确, 线程隔离验证通过, 无内存泄漏, 测试通过.

---

### Task 13.7 – MyBatis Version Compatibility Layer │ Agent_Advanced_Interceptor

- **Objective:** Create MyBatis version compatibility layer supporting 3.4.x and 3.5.x, implement MyBatisVersionDetector checking for version-specific marker classes, create SqlExtractor interface defining version-agnostic SQL extraction abstraction, implement LegacySqlExtractor (3.4.x) and ModernSqlExtractor (3.5.x) using reflection-based API access, create SqlExtractorFactory selecting implementation based on detected version.

- **Output:**
  - `MyBatisVersionDetector` class detecting MyBatis version at runtime
  - `SqlExtractor` interface: `String extractSql(MappedStatement, Object, BoundSql)`
  - `LegacySqlExtractor` implementation for MyBatis 3.4.x
  - `ModernSqlExtractor` implementation for MyBatis 3.5.x
  - `SqlExtractorFactory` selecting extractor based on version
  - Version detection tests covering 3.4.6, 3.5.6, 3.5.13, 3.5.16
  - `MyBatisVersionDetectionTest` and `MyBatisCompatibilityIntegrationTest`

- **Guidance:** Implementation approach: (1) MyBatisVersionDetector: check for marker class existence (e.g., `org.apache.ibatis.session.ProviderMethodResolver` exists in 3.5.0+), cache detection result in static final field, provide `public static boolean is35OrAbove()` method, (2) SqlExtractor interface: defines version-agnostic contract for SQL extraction, handles differences in MappedStatement/BoundSql API between versions, (3) LegacySqlExtractor: uses reflection to access 3.4.x-specific API, handles DynamicSqlSource vs StaticSqlSource, extracts SQL from BoundSql considering parameter mapping differences, (4) ModernSqlExtractor: uses reflection to access 3.5.x-specific API, handles improved API with cleaner parameter handling, (5) SqlExtractorFactory: `public static SqlExtractor create() { return MyBatisVersionDetector.is35OrAbove() ? new ModernSqlExtractor() : new LegacySqlExtractor(); }`, caches instance, (6) Integration testing: test with actual MyBatis 3.4.6, 3.5.6, 3.5.13, 3.5.16 dependencies via Maven profiles, verify SqlGuardInterceptor works correctly with each version.

**测试矩阵 (15+ tests):**

1. **MyBatis版本检测TDD (MyBatisVersionDetectionTest - 5 tests):** Write test class covering: `testVersionDetector_MyBatis346_detectsAs34()`, `testVersionDetector_MyBatis356_detectsAs35()`, `testVersionDetector_MyBatis3513_detectsAs35()`, `testVersionDetector_MyBatis3516_detectsAs35()`, `testVersionDetector_caching_works()`. Then implement MyBatisVersionDetector.

2. **SqlExtractor实现TDD (SqlExtractorImplementationTest - 5 tests):** Write test class covering: `testLegacySqlExtractor_MyBatis34_extractsSql()`, `testModernSqlExtractor_MyBatis35_extractsSql()`, `testSqlExtractorFactory_selectsCorrectImplementation()`, `testSqlExtractor_handlesParameters()`, `testSqlExtractor_handlesDynamicSql()`. Then implement SqlExtractor interface and implementations.

3. **MyBatis兼容性集成测试TDD (MyBatisCompatibilityIntegrationTest - 5 tests):** Write integration test with Maven profiles covering: `testMyBatis346_interceptorWorks()`, `testMyBatis356_interceptorWorks()`, `testMyBatis3513_interceptorWorks()`, `testMyBatis3516_interceptorWorks()`, `testAllVersions_behaviorConsistent()`. Then verify compatibility across all versions.

**验收标准:** MyBatis版本检测准确, SqlExtractor抽象正确, 支持3.4.6/3.5.6/3.5.13/3.5.16所有版本, 兼容性测试全部通过, 行为一致.

---

### Task 13.8 – MyBatis-Plus Version Compatibility Layer │ Agent_Advanced_Interceptor

- **Objective:** Create MyBatis-Plus version compatibility layer supporting 3.4.x and 3.5.x, implement MyBatisPlusVersionDetector checking for version-specific marker classes, create IPageDetector using reflection-based IPage detection from parameter types and Map contents, create QueryWrapperInspector extracting conditions from QueryWrapper/LambdaQueryWrapper using reflection, create WrapperTypeDetector identifying wrapper types, implement empty wrapper detection critical for safety validation.

- **Output:**
  - `MyBatisPlusVersionDetector` class detecting MyBatis-Plus version at runtime
  - `IPageDetector` class detecting IPage pagination from method parameters
  - `QueryWrapperInspector` class extracting conditions from wrappers via reflection
  - `WrapperTypeDetector` class identifying wrapper types (Query/Update/Lambda variants)
  - Empty wrapper detection logic (critical for no-condition validation)
  - Version detection tests covering 3.4.0, 3.4.3, 3.5.3, 3.5.5
  - `MyBatisPlusVersionDetectionTest` and `MyBatisPlusCompatibilityIntegrationTest`

- **Guidance:** Implementation approach: (1) MyBatisPlusVersionDetector: check for marker class existence (e.g., `com.baomidou.mybatisplus.core.metadata.IPage` signature changes in 3.5.x), cache detection result, provide `public static boolean is35OrAbove()` method, (2) IPageDetector: iterate method parameters checking `instanceof IPage`, if parameter is Map check for IPage entry (MyBatis-Plus wraps IPage in param map with key "page"), extract pagination info (current, size) via reflection, (3) QueryWrapperInspector: use reflection to access AbstractWrapper.expression field (private final field containing condition tree), traverse expression extracting WHERE conditions, handle QueryWrapper and LambdaQueryWrapper (different internal structures), detect empty wrappers (expression is null or empty), (4) WrapperTypeDetector: check instance types (QueryWrapper, UpdateWrapper, LambdaQueryWrapper, LambdaUpdateWrapper), return wrapper category for different handling, (5) Empty wrapper detection: `public static boolean isEmpty(AbstractWrapper wrapper)` checks if expression is null/empty indicating no conditions (critical for NoWhereClauseChecker integration), (6) Integration testing: test with actual MyBatis-Plus 3.4.0, 3.4.3, 3.5.3, 3.5.5 dependencies via Maven profiles, verify wrapper inspection and IPage detection work correctly.

**测试矩阵 (20+ tests):**

1. **MyBatis-Plus版本检测TDD (MyBatisPlusVersionDetectionTest - 5 tests):** Write test class covering: `testVersionDetector_MP340_detectsAs34()`, `testVersionDetector_MP343_detectsAs34()`, `testVersionDetector_MP353_detectsAs35()`, `testVersionDetector_MP355_detectsAs35()`, `testVersionDetector_caching_works()`. Then implement MyBatisPlusVersionDetector.

2. **IPageDetector实现TDD (IPageDetectorImplementationTest - 5 tests):** Write test class covering: `testIPageDetector_fromParameter_detects()`, `testIPageDetector_fromParamMap_detects()`, `testIPageDetector_noPagination_returnsNull()`, `testIPageDetector_extractsCurrentAndSize()`, `testIPageDetector_MP34_and_MP35_bothWork()`. Then implement IPageDetector.

3. **QueryWrapperInspector实现TDD (QueryWrapperInspectorImplementationTest - 5 tests):** Write test class covering: `testQueryWrapperInspector_extractsConditions()`, `testQueryWrapperInspector_lambdaWrapper_extracts()`, `testQueryWrapperInspector_emptyWrapper_detects()`, `testQueryWrapperInspector_complexConditions_extracts()`, `testWrapperTypeDetector_identifiesTypes()`. Then implement QueryWrapperInspector and WrapperTypeDetector.

4. **MyBatis-Plus兼容性集成测试TDD (MyBatisPlusCompatibilityIntegrationTest - 5 tests):** Write integration test with Maven profiles covering: `testMP340_wrapperInspection_works()`, `testMP343_iPageDetection_works()`, `testMP353_compatibility()`, `testMP355_compatibility()`, `testAllVersions_behaviorConsistent()`. Then verify compatibility across all versions.

**验收标准:** MyBatis-Plus版本检测准确, IPageDetector正确, QueryWrapperInspector提取条件正确, 空Wrapper检测准确, 支持3.4.0/3.4.3/3.5.3/3.5.5所有版本, 兼容性测试全部通过.

---

### Task 13.9 – CI/CD Multi-Version Test Matrix │ Agent_Testing_Validation

- **Objective:** Create comprehensive CI/CD test matrix in GitHub Actions covering Java 8/11/17/21 × MyBatis 3.4.6/3.5.6/3.5.13/3.5.16 × MyBatis-Plus 3.4.0/3.4.3/3.5.3/3.5.5 combinations, create Maven profiles for each version combination, configure GitHub Actions to run test matrix on PR (subset: latest + oldest) and nightly (full matrix), implement test result aggregation uploading artifacts and generating compatibility report, create compatibility badge generation updating README with shields.io badges showing version support status.

- **Output:**
  - `.github/workflows/multi-version-test.yml` GitHub Actions workflow
  - Matrix strategy covering: Java (8, 11, 17, 21) × MyBatis versions × MyBatis-Plus versions
  - Maven profiles: `mybatis-3.4.6`, `mybatis-3.5.6`, `mybatis-3.5.13`, `mybatis-3.5.16`, `mp-3.4.0`, `mp-3.4.3`, `mp-3.5.3`, `mp-3.5.5`
  - PR test subset: Java 8 + oldest versions, Java 21 + latest versions (fast feedback)
  - Nightly full matrix: all combinations (comprehensive validation)
  - Test result aggregation script: uploads JUnit XML artifacts, generates HTML compatibility report
  - Compatibility badge: shields.io badges in README.md showing green checkmarks for supported versions
  - `CIConfigurationValidationTest` and `VersionProfileValidationTest`

- **Guidance:** **Multi-step implementation sequence**:

  **Step 1 - Maven Profiles Creation**: Create profile for each version combination in pom.xml, each profile overrides dependency versions, example: `<profile><id>mybatis-3.4.6</id><properties><mybatis.version>3.4.6</mybatis.version></properties></profile>`. Create 8 profiles total (4 MyBatis + 4 MyBatis-Plus). Verify each profile activates correctly and overrides versions as expected.

  **Step 2 - GitHub Actions Matrix Configuration**: Define matrix strategy in workflow YAML with dimensions [java: [8, 11, 17, 21], mybatis: [3.4.6, 3.5.6, 3.5.13, 3.5.16], mybatis-plus: [3.4.0, 3.4.3, 3.5.3, 3.5.5]], configure job to run `mvn test -Pmybatis-${{ matrix.mybatis }},mp-${{ matrix.mybatis-plus }}` for each combination.

  **Step 3 - PR Subset Strategy Implementation**: Use `if: github.event_name == 'pull_request'` with filtered matrix including only critical combinations (Java 8 + mybatis-3.4.6 + mp-3.4.0, Java 21 + mybatis-3.5.16 + mp-3.5.5) for fast feedback within 10 minutes, enabling rapid PR validation without full matrix overhead.

  **Step 4 - Nightly Full Matrix Configuration**: Use `schedule: cron: '0 2 * * *'` with complete matrix for comprehensive validation, all Java versions × all MyBatis versions × all MyBatis-Plus versions = 64 combinations, runs during off-peak hours, full validation ensuring no regression across entire compatibility matrix.

  **Step 5 - Test Result Aggregation**: Upload JUnit XML artifacts using `actions/upload-artifact@v3` after test execution, create custom script parsing JUnit XML generating HTML compatibility report showing pass/fail for each version combination, include test duration and failure details.

  **Step 6 - Compatibility Badge Generation**: Update README.md with shields.io badges `![MyBatis 3.4.6](https://img.shields.io/badge/MyBatis-3.4.6-green)` for each supported version, auto-update badge colors based on test results (green=passing, red=failing, yellow=not tested), integrate badge update into workflow using GitHub API.

  **Step 7 - Configuration Validation Testing**: Write CIConfigurationValidationTest verifying workflow YAML syntax correct using GitHub Actions validation tools, write VersionProfileValidationTest verifying each Maven profile activates correctly and overrides dependency versions, ensure profile combinations work without conflicts.

**测试矩阵 (10+ tests):**

1. **CI配置验证TDD (CIConfigurationValidationTest - 5 tests):** Write test class covering: `testWorkflowYaml_syntaxValid()`, `testMatrixStrategy_coversAllVersions()`, `testPRSubset_includesOldestAndLatest()`, `testNightlySchedule_configuredCorrectly()`, `testArtifactUpload_configured()`. Then validate workflow configuration.

2. **版本Profile验证TDD (VersionProfileValidationTest - 5 tests):** Write test class covering: `testMavenProfile_mybatis346_activates()`, `testMavenProfile_mybatis3516_activates()`, `testMavenProfile_mp340_activates()`, `testMavenProfile_mp355_activates()`, `testProfileCombination_works()`. Then validate Maven profiles.

**验收标准:** GitHub Actions workflow配置正确, 测试矩阵覆盖Java 8/11/17/21和所有MyBatis/MyBatis-Plus版本, PR测试快速(子集), Nightly测试全面(全矩阵), 测试结果聚合正常, 兼容性徽章自动更新.

---


## Phase 14: Audit Platform Examples & Documentation - Agent_Documentation

**Phase Overview:** Create comprehensive examples and documentation for SQL Audit Platform showcasing audit-specific features, providing production deployment guides, documenting best practices for audit analysis and remediation, and ensuring users can effectively leverage dual-layer architecture for both prevention and discovery.

### Task 14.1 – Audit-Enhanced Demo Application │ Agent_Documentation

- **Objective:** Extend existing sql-guard-demo application with audit platform integration demonstrating complete dual-layer protection, showing audit log generation from interceptors, audit service consumption and analysis, REST API usage for querying audit results, and visualization of audit findings via sample dashboards.
- **Output:**
  - Extended sql-guard-demo with audit logging enabled across all interceptors
  - Docker Compose: demo app + Kafka + audit service + PostgreSQL + ClickHouse + Grafana
  - Sample audit scenarios: slow query detection, high-impact UPDATE without WHERE, error rate analysis
  - Grafana dashboards: top risky SQL, slow query trends, error distribution
  - Demo scripts: load generator creating diverse SQL patterns for audit analysis
  - README with step-by-step demo walkthrough
- **Guidance:** Demo architecture: existing Spring Boot demo app with MyBatis/MyBatis-Plus/Druid integrations, add audit logging configuration enabling all Task 8 interceptors, Docker Compose orchestrates full stack (demo app → Kafka ← audit service → databases), Grafana connects to audit service REST API and ClickHouse for visualization. Audit scenarios: (1) slow query - simulate 5-second SELECT showing SlowQueryChecker detection and recommendations, (2) missing WHERE UPDATE - execute UPDATE without WHERE affecting 1000 rows showing ActualImpactNoWhereChecker with CRITICAL severity, (3) frequent errors - cause intentional SQL errors showing ErrorRateChecker aggregation, (4) pagination abuse - deep offset queries showing DeepPaginationChecker from Phase 2. Load generator: JMeter or custom Spring Boot script generating realistic SQL workload (80% fast queries, 15% slow, 5% errors), runs for 5 minutes creating sufficient data for audit analysis, demonstrates audit service throughput handling production-like volume. Grafana dashboards: (1) Risk Overview - pie chart of severity distribution, table of top 10 risky SQL with risk scores, (2) Performance - line chart of p95/p99 query latency over time, bar chart of slowest queries, (3) Errors - error rate by category, error frequency timeline, top error messages. Demo walkthrough: start stack with docker-compose up, run load generator, open Grafana dashboards, query audit service REST API with curl examples, show audit findings in ClickHouse, demonstrate configuration updates via API. Depends on: Phase 8-10 Output (full audit platform), existing sql-guard-demo from Phase 7.

**测试矩阵 (30+ tests):**

1. **Demo功能TDD (DemoApplicationTest - 15 tests):** Write test class covering: `testDemo_fullStack_shouldStart()`, `testDemo_slowQueryScenario_shouldDetect()`, `testDemo_missingWhereScenario_shouldDetect()`, `testDemo_errorRateScenario_shouldAggregate()`, `testDemo_paginationAbuseScenario_shouldDetect()`, `testDemo_loadGenerator_shouldProduceEvents()`, `testDemo_loadGenerator_80percent_fast_shouldMaintain()`, `testDemo_loadGenerator_15percent_slow_shouldMaintain()`, `testDemo_loadGenerator_5percent_error_shouldMaintain()`, `testDemo_auditService_shouldConsumeEvents()`, `testDemo_grafanaDashboard_shouldDisplay()`, `testDemo_restAPI_shouldRespond()`, `testDemo_clickHouseQuery_shouldWork()`, `testDemo_dockerCompose_allHealthy_shouldVerify()`, `testDemo_README_walkthrough_shouldExecute()`. Then extend sql-guard-demo with audit integration.

2. **Grafana Dashboard验证TDD (GrafanaDashboardValidationTest - 8 tests):** Write test class covering: `testDashboard_riskOverview_shouldRender()`, `testDashboard_riskOverview_pieChart_shouldShowSeverity()`, `testDashboard_riskOverview_table_shouldShowTop10()`, `testDashboard_performance_lineChart_shouldShowP95P99()`, `testDashboard_performance_barChart_shouldShowSlowest()`, `testDashboard_errors_rateChart_shouldShowTimeline()`, `testDashboard_errors_categoryChart_shouldShowDistribution()`, `testDashboard_dataSource_shouldConnect()`. Then create Grafana dashboard JSON.

3. **负载测试验证TDD (LoadGeneratorValidationTest - 7 tests):** Write test class covering: `testLoadGenerator_5minutes_shouldComplete()`, `testLoadGenerator_distribution_shouldMatch()`, `testLoadGenerator_throughput_shouldMeetTarget()`, `testLoadGenerator_diversity_shouldGenerateVariedSQL()`, `testLoadGenerator_auditData_shouldBeSufficient()`, `testLoadGenerator_jmeter_shouldExecute()`, `testLoadGenerator_customScript_shouldExecute()`. Then create load generator.

**验收标准:** Demo启动成功率: 100%, 所有场景可复现, Dashboard数据准确.

### Task 14.2 – Production Deployment Guide │ Agent_Documentation

- **Objective:** Create production deployment guide documenting infrastructure requirements, scaling strategies, high-availability configuration, security considerations, and operational runbooks for sql-audit-service, providing deployment templates for common platforms (Kubernetes, Docker Swarm, VM-based), and documenting monitoring, backup, and disaster recovery procedures.
- **Output:**
  - Production deployment guide: docs/deployment/production-deployment.md (200+ lines)
  - Infrastructure requirements: CPU/memory/storage sizing for different scales
  - Kubernetes deployment YAML: StatefulSet for audit service, ConfigMaps, Secrets
  - High availability: Kafka consumer groups, database replication, failover procedures
  - Security: authentication, authorization, encryption in transit/at rest
  - Monitoring: Prometheus + Grafana dashboard templates, alerting rules
  - Backup and recovery: PostgreSQL backup strategy, ClickHouse snapshots, disaster recovery runbook
  - **运维手册 (Low Fix L2 新增):** docs/operations/troubleshooting-guide.md，故障排查与常见问题处理
- **Guidance:** Sizing guide: small scale (<10k SQL/day) → 2 CPU / 4GB RAM / 100GB storage, medium scale (<100k SQL/day) → 4 CPU / 8GB RAM / 500GB storage, large scale (<1M SQL/day) → 8 CPU / 16GB RAM / 2TB storage, very large (>1M SQL/day) → horizontal scaling with multiple audit service instances. Kubernetes deployment: StatefulSet for audit service (stable network identity for Kafka consumer), deployment for multiple replicas (consumer group coordination), ConfigMap for application.yml (non-sensitive config), Secret for database credentials, PersistentVolumeClaim for local cache, Service for REST API, Ingress for external access. HA configuration: Kafka consumer group with multiple service instances (partition assignment handles failover), PostgreSQL with streaming replication (primary/replica), ClickHouse with replicated tables (ReplicatedMergeTree), load balancer for REST API (any instance can serve queries), health checks trigger pod restart on failure. Security: API authentication via JWT (Spring Security configuration), RBAC for audit result access (admin vs read-only), database credentials via Secrets, encryption in transit (TLS for Kafka/PostgreSQL/ClickHouse), encryption at rest (database-level encryption), audit log sensitivity (parameter sanitization for PII). Monitoring: Prometheus scrapes /actuator/prometheus, key metrics (consumer lag <1000 messages, processing latency p99 <200ms, error rate <1%), Grafana dashboards (audit service health, Kafka consumer stats, database performance), alerting rules (consumer lag >10000, processing latency >500ms, error rate >5%, disk space <10%). Backup: PostgreSQL daily full backup + WAL archiving (point-in-time recovery), ClickHouse daily snapshots (clickhouse-backup tool), retention 30 days, disaster recovery runbook (restore procedures, RTO/RPO targets). Depends on: Phase 10 Output (audit service deployable artifact).

**测试矩阵 (35+ tests):**

1. **Kubernetes部署TDD (KubernetesDeploymentTest - 15 tests, 使用kind或minikube):** Write test class covering: `testK8s_StatefulSet_shouldDeploy()`, `testK8s_multipleReplicas_shouldScale()`, `testK8s_ConfigMap_shouldMount()`, `testK8s_Secret_shouldInject()`, `testK8s_PersistentVolumeClaim_shouldProvision()`, `testK8s_Service_shouldExpose()`, `testK8s_Ingress_shouldRoute()`, `testK8s_healthCheck_shouldRestartOnFailure()`, `testK8s_rollingUpdate_shouldZeroDowntime()`, `testK8s_resourceLimits_shouldEnforce()`, `testK8s_nodeAffinity_shouldSchedule()`, `testK8s_podDisruptionBudget_shouldRespect()`, `testK8s_horizontalPodAutoscaler_shouldScale()`, `testK8s_networkPolicy_shouldIsolate()`, `testK8s_serviceAccount_shouldAuthorize()`. Then create Kubernetes YAML templates.

2. **高可用性TDD (HighAvailabilityTest - 10 tests):** Write test class covering: `testHA_kafkaConsumerGroup_shouldDistribute()`, `testHA_instanceFailure_shouldReassign()`, `testHA_postgresReplication_shouldSync()`, `testHA_clickHouseReplication_shouldSync()`, `testHA_loadBalancer_shouldDistribute()`, `testHA_healthCheck_shouldDetectFailure()`, `testHA_failover_shouldBeAutomatic()`, `testHA_splitBrain_shouldPrevent()`, `testHA_dataConsistency_shouldMaintain()`, `testHA_RTO_shouldMeetTarget()`. Then document HA configuration.

3. **安全TDD (SecurityHardeningTest - 5 tests):** Write test class covering: `testSecurity_JWT_shouldValidate()`, `testSecurity_RBAC_shouldEnforce()`, `testSecurity_TLS_shouldEncrypt()`, `testSecurity_credentials_shouldBeSecured()`, `testSecurity_auditLog_PII_shouldSanitize()`. Then document security configuration.

4. **运维手册验证TDD (OperationsGuideValidationTest - 5 tests, Low Fix L2):** Write test class covering: `testTroubleshooting_kafkaLag_shouldResolve()`, `testTroubleshooting_clickHouseTimeout_shouldResolve()`, `testTroubleshooting_OOM_shouldDiagnose()`, `testTroubleshooting_configNotEffective_shouldDebug()`, `testEmergencyProcedure_degradation_shouldExecute()`. Then create docs/operations/troubleshooting-guide.md.

5. **运维手册编写 (Low Fix L2):** 创建docs/operations/troubleshooting-guide.md，覆盖: **常见问题** - Kafka消费积压(增加消费者实例、检查checker性能)、ClickHouse写入超时(检查网络、调整batch size)、审计服务OOM(增加堆内存、检查内存泄漏)、配置不生效(使用配置诊断端点)；**故障诊断流程** - 症状识别、日志分析(关键日志模式)、指标检查(哪些指标异常表示哪种问题)、根因定位；**应急处理** - 快速降级(禁用审计层保持runtime正常)、数据恢复(从备份恢复)、回滚操作；**性能调优** - 常见性能瓶颈及解决方案、JVM参数优化、数据库连接池调优。测试: 验证文档步骤可操作。

**验收标准:** K8s部署成功率: 100%, HA切换时间: <30s, 所有运维手册步骤可执行.

### Task 14.3 – Audit Analysis Best Practices & Remediation Guide │ Agent_Documentation

- **Objective:** Document best practices for interpreting audit findings, prioritizing remediation efforts, tuning checker thresholds for environment-specific baselines, and measuring improvement over time, providing decision frameworks for addressing different risk levels and checker findings, and including real-world case studies demonstrating audit value.
- **Output:**
  - Audit analysis guide: docs/user-guide/audit-analysis-best-practices.md (150+ lines)
  - Risk prioritization matrix: severity + confidence + impact → action priority
  - Remediation playbooks: specific actions for each checker finding type
  - Threshold tuning guide: establishing baselines, adjusting for false positives
  - Metrics for success: tracking improvement in slow query rate, error rate reduction, high-risk SQL elimination
  - Case studies: 3-5 real-world scenarios with audit findings and resolutions
- **Guidance:** Risk prioritization: CRITICAL + high confidence (>80%) + high impact (>1000 rows or >5s) → immediate action (P0), HIGH + medium confidence (60-80%) + medium impact → scheduled fix (P1), MEDIUM/LOW → backlog or accept as known issue; false positive handling → whitelist in checker config, tune thresholds, document rationale. Remediation playbooks: **SlowQueryChecker findings** → (1) analyze execution plan, (2) check index usage, (3) add missing indexes, (4) rewrite query if necessary, (5) consider caching for frequent queries; **ActualImpactNoWhereChecker** → (1) determine if bulk operation intentional, (2) if unintended add WHERE conditions, (3) if batch operation consider chunking, (4) review application logic; **ErrorRateChecker** → (1) categorize error type, (2) syntax errors → code review, (3) constraint violations → data validation, (4) deadlocks → transaction scope analysis, (5) infrastructure errors → escalate to ops. Threshold tuning: baseline establishment → collect 7 days data in production, calculate p95/p99 for latency metrics, set thresholds at p99 + 20% margin, review monthly and adjust; false positive reduction → identify recurring false positives (e.g., known slow reporting queries), whitelist by SQL hash or mapper ID, document whitelist rationale. Success metrics: slow query reduction (p95 latency improvement month-over-month), error rate trends (decrease in error percentage), high-risk SQL elimination (count of CRITICAL findings), audit coverage (% of SQL statements audited). Case studies: (1) E-commerce: slow product search query optimized via index recommendation, latency reduced from 8s to 200ms; (2) Financial: UPDATE without WHERE detected affecting 50k accounts, prevented data corruption; (3) SaaS: error rate spike identified during deployment, rollback triggered before customer impact; (4) Analytics: frequent zero-impact queries identified, application logic bug found causing unnecessary DB calls; (5) Compliance: sensitive data access patterns revealed unauthorized PII queries, security investigation initiated. Depends on: Phase 9 Output (checker documentation), Task 12.1 Output (demo examples).

**测试矩阵 (20+ tests):**

1. **风险优先级验证TDD (RiskPrioritizationTest - 8 tests):** Write test class covering: `testPrioritization_criticalHighConfidence_shouldBeP0()`, `testPrioritization_highMediumConfidence_shouldBeP1()`, `testPrioritization_matrix_shouldSort()`, `testPrioritization_falsePositive_shouldWhitelist()`, `testPrioritization_thresholdTuning_shouldAdjust()`, `testPrioritization_baselineEstablishment_shouldCalculate()`, `testPrioritization_p99Plus20percent_shouldSet()`, `testPrioritization_monthlyReview_shouldTrigger()`. Then create risk prioritization matrix.

2. **修复手册验证TDD (RemediationPlaybookTest - 7 tests):** Write test class covering: `testPlaybook_slowQuery_shouldResolve()`, `testPlaybook_missingWhere_shouldFix()`, `testPlaybook_errorRate_shouldCategorize()`, `testPlaybook_indexRecommendation_shouldApply()`, `testPlaybook_queryRewrite_shouldImprove()`, `testPlaybook_chunking_batchOperation_shouldImplement()`, `testPlaybook_deadlock_shouldAnalyze()`. Then create remediation playbooks.

3. **案例研究验证TDD (CaseStudiesValidationTest - 5 tests):** Write test class covering: `testCaseStudy_ecommerce_slowSearch_shouldReproduce()`, `testCaseStudy_financial_missingWhere_shouldReproduce()`, `testCaseStudy_saas_errorSpike_shouldReproduce()`, `testCaseStudy_analytics_zeroImpact_shouldReproduce()`, `testCaseStudy_compliance_PII_shouldReproduce()`. Then write case studies.

**验收标准:** 所有案例可复现, 优先级矩阵准确, 修复手册有效.

### Task 14.4 – API Reference & Developer Documentation │ Agent_Documentation

- **Objective:** Generate comprehensive API reference documentation for audit platform public APIs, documenting audit checker extension points for custom checker development, providing API usage examples (Low Fix L3: 明确为API使用示例而非完整SDK) for programmatic audit result access, and creating developer tutorials for common integration scenarios.
- **Output:**
  - Javadoc API reference: complete coverage of audit platform public APIs
  - Checker extension guide: docs/developer-guide/custom-audit-checker.md with tutorial
  - REST API reference: OpenAPI spec with code examples in multiple languages
  - **API使用示例 (Low Fix L3 澄清):** Java/Python/JavaScript代码片段演示REST API调用，非独立SDK包
  - Integration tutorials: embedding audit service in CI/CD, custom alerting integrations
  - Developer quickstart: 5-minute guide to running audit service locally
- **Guidance:** **Low Fix L3 - 范围澄清:** 本任务提供的是REST API使用示例(代码片段)，而非完整封装的SDK包(如需SDK需另行规划)。示例使用各语言原生HTTP客户端(Java RestTemplate/WebClient, Python requests, JavaScript fetch)演示API调用，不提供独立发布的client library。Javadoc coverage: all public classes in audit modules (AbstractAuditChecker, ExecutionResult, RiskScore, AuditResult), audit service public APIs (AuditEngine, repositories, controllers), extension points (custom checkers, custom storage adapters), code examples in class-level Javadoc, @since 2.0 tags for new audit APIs. Custom checker tutorial: step-by-step guide (1) extend AbstractAuditChecker, (2) implement performAudit() logic, (3) calculate RiskScore with appropriate confidence, (4) write comprehensive tests (30+ test matrix), (5) register as Spring bean in audit service, (6) configure thresholds, (7) deploy and validate, include complete working example (TableLockChecker detecting queries holding locks >1s). REST API reference: generated from OpenAPI annotations, code examples in curl, Java (RestTemplate, WebClient), Python (requests), JavaScript (fetch, axios), pagination examples, error handling, authentication headers. API使用示例: Java代码片段使用RestTemplate/WebClient调用audit service API, Python代码片段使用requests库, JavaScript代码片段使用fetch/axios, 演示常见用例(查询最近CRITICAL发现、获取仪表板统计数据、更新checker配置), 示例放在docs/api-examples/目录下便于复制使用。Integration tutorials: (1) CI/CD integration → query audit API in pipeline, fail build if critical findings in PR, (2) custom alerting → poll audit API, send Slack notifications for CRITICAL risks, (3) metrics export → fetch statistics, export to custom monitoring, (4) compliance reporting → scheduled queries, generate PDF reports. Developer quickstart: `git clone`, `docker-compose up`, `mvn spring-boot:run`, access Swagger UI, run sample queries, 5 minutes to full local environment. Depends on: Phase 10 Output (audit service with APIs), Phase 9 Output (checker extension points).

**测试矩阵 (30+ tests):**

1. **Javadoc覆盖率TDD (JavadocCoverageTest - 10 tests):** Write test class covering: `testJavadoc_auditModule_allPublicClasses_shouldHave()`, `testJavadoc_AbstractAuditChecker_shouldHaveExamples()`, `testJavadoc_ExecutionResult_shouldHaveFieldDocs()`, `testJavadoc_RiskScore_shouldHaveRangeDocs()`, `testJavadoc_since2_0_shouldMark()`, `testJavadoc_codeExamples_shouldCompile()`, `testJavadoc_links_shouldBeValid()`, `testJavadoc_parameters_shouldBeDescribed()`, `testJavadoc_returnValues_shouldBeDescribed()`, `testJavadoc_exceptions_shouldBeDocumented()`. Then generate Javadoc with maven-javadoc-plugin.

2. **自定义Checker教程TDD (CustomCheckerTutorialTest - 10 tests):** Write test class covering: `testTutorial_step1_extend_shouldCompile()`, `testTutorial_step2_implement_shouldWork()`, `testTutorial_step3_calculateRisk_shouldScore()`, `testTutorial_step4_tests_shouldPass()`, `testTutorial_step5_register_shouldDiscover()`, `testTutorial_step6_configure_shouldLoad()`, `testTutorial_step7_deploy_shouldActivate()`, `testTutorial_TableLockChecker_example_shouldWork()`, `testTutorial_completeExample_shouldCompile()`, `testTutorial_completeExample_shouldExecute()`. Then create docs/developer-guide/custom-audit-checker.md.

3. **API使用示例验证TDD (APIExamplesValidationTest - 10 tests, Low Fix L3):** Write test class covering: `testExample_Java_RestTemplate_shouldCompile()`, `testExample_Java_RestTemplate_shouldExecute()`, `testExample_Java_WebClient_shouldCompile()`, `testExample_Java_WebClient_shouldExecute()`, `testExample_Python_requests_shouldExecute()`, `testExample_JavaScript_fetch_shouldExecute()`, `testExample_queryRecentCritical_shouldWork()`, `testExample_getDashboardStats_shouldWork()`, `testExample_updateCheckerConfig_shouldWork()`, `testExample_allSnippets_shouldBeValid()`. Then create docs/api-examples/ with code snippets.

**验收标准:** Javadoc覆盖率: >90%, 所有代码示例可编译, 所有API示例可执行.

