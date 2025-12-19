---
Task_ID: 11.1
Task_Name: TDD Test Case Library Design (Module Separation Only)
Assigned_Agent: Agent_Testing_Validation
Phase: Phase 11 - JDBC Module Separation
Priority: CRITICAL (Blocks all implementation tasks)
Estimated_Duration: 2 days
Dependencies: None
Output_Location: .apm/Assignments/Phase_11/Task_11_1_Test_Design.md
---

# Task 11.1 – TDD Test Case Library Design (Module Separation Only)

## Objective

Design comprehensive test case library covering JDBC module separation scenarios **before any implementation begins**, establishing test fixtures for module isolation verification, dependency checks, and backward compatibility validation, ensuring strict TDD methodology with tests designed first.

**CRITICAL**: This task explicitly **EXCLUDES** architecture refactoring tests. Phase 11 focuses solely on module structure separation without changing internal architecture (no RuleChecker refactoring, no SqlContext changes, no StatementVisitor introduction - those belong to Phase 12).

---

## Context

### Phase 11 Scope
**Goal**: Extract `sql-guard-jdbc` into 4 independent connection pool modules:
- `sql-guard-jdbc-common` - shared abstractions (ViolationStrategy, JdbcInterceptorBase)
- `sql-guard-jdbc-druid` - Druid-specific implementation
- `sql-guard-jdbc-hikari` - HikariCP-specific implementation
- `sql-guard-jdbc-p6spy` - P6Spy universal JDBC fallback

**Key Principle**: Users include only needed pool modules without dependency pollution. Example: user only needs Druid → only includes `sql-guard-jdbc-druid` → does NOT transitively pull HikariCP or P6Spy dependencies.

### Why TDD First?
1. **Design before implementation**: Tests define module boundaries and contracts
2. **Acceptance criteria clarity**: Measurable success metrics established upfront
3. **Risk mitigation**: Identify integration issues before coding begins
4. **Quality baseline**: 145+ tests (40+30+25+25+25+20) ensure comprehensive coverage

### Reference Architecture Context
Review `.apm/Memory/Phase_11_Reference_Architecture/Architecture_Review_Report.md` to understand:
- Why Phase 11 focuses ONLY on module separation (not architecture refactoring)
- Module dependency principles (minimal dependency, provided scope strategy)
- Phase 11/12/13 separation rationale (incremental delivery, risk reduction)

---

## Expected Outputs

### 1. Test Design Document
**Location**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md`

**Required Sections**:
- **Introduction**: Test design philosophy, TDD approach, coverage goals
- **Test Fixture Design**: AbstractJdbcModuleTest base class specification
- **Module Isolation Tests** (15 tests): Compilation independence, dependency verification, ClassLoader isolation
- **Backward Compatibility Tests** (12 tests): API consistency, behavior preservation, configuration migration
- **Performance Baseline Tests** (13 tests): Module loading overhead, runtime performance, memory usage
- **Test Execution Strategy**: Maven profiles, H2 in-memory database setup, JMH benchmark configuration
- **Acceptance Criteria Mapping**: How each test validates specific acceptance criteria

### 2. AbstractJdbcModuleTest Specification
**Purpose**: Reusable test fixture base class for all JDBC module tests

**Required Capabilities**:
- H2 in-memory database setup/teardown (lightweight, no external dependencies)
- Mock ConnectionPool creation (Druid, HikariCP, P6Spy stubs for testing)
- SQL execution verification utilities (execute SQL, assert results, verify intercepted)
- Module isolation helpers (ClassLoader verification, POM dependency parsing)
- Performance measurement utilities (timing, memory profiling, benchmark runners)

**Do NOT implement** - only specify the interface and capabilities needed.

### 3. Test Specifications by Category

#### Category 1: Module Isolation Tests (15 tests)
**Goal**: Verify each module compiles and runs independently without transitive dependency pollution.

**Test Names** (document EXACTLY these):
1. `testCommonModule_compilesWithoutPoolDependencies()`
2. `testCommonModule_onlyDependsOnCoreAndAuditApi()`
3. `testDruidModule_compilesWithoutHikariP6Spy()`
4. `testDruidModule_druidDependencyIsProvided()`
5. `testHikariModule_compilesWithoutDruidP6Spy()`
6. `testHikariModule_hikariDependencyIsProvided()`
7. `testP6SpyModule_compilesWithoutDruidHikari()`
8. `testP6SpyModule_p6spyDependencyIsProvided()`
9. `testUserProject_onlyDruidDependency_noTransitivePollution()`
10. `testUserProject_onlyHikariDependency_noTransitivePollution()`
11. `testUserProject_onlyP6SpyDependency_noTransitivePollution()`
12. `testMavenEnforcer_rejectsWrongDependencies()`
13. `testClassLoader_poolClassesNotLoaded_whenModuleNotUsed()`
14. `testIndependentJar_druidModulePackagesCorrectly()`
15. `testIndependentJar_hikariModulePackagesCorrectly()`

**For each test, specify**:
- Test objective (what it validates)
- Test methodology (how to implement - Maven compilation test, POM parsing, ClassLoader inspection)
- Success criteria (concrete pass/fail conditions)
- Expected implementation class location (which test class will contain it)

**Acceptance Criteria**:
- All modules compile independently
- No transitive dependency pollution
- Maven Enforcer validates dependency constraints

#### Category 2: Backward Compatibility Tests (12 tests)
**Goal**: Ensure 100% backward compatibility - existing code works without changes.

**Test Names** (document EXACTLY these):
1. `testViolationStrategy_oldDruidEnum_stillWorks()`
2. `testViolationStrategy_oldHikariEnum_stillWorks()`
3. `testViolationStrategy_oldP6SpyEnum_stillWorks()`
4. `testDruidFilter_existingCode_noChangesNeeded()`
5. `testHikariProxy_existingCode_noChangesNeeded()`
6. `testP6SpyListener_existingCode_noChangesNeeded()`
7. `testConfiguration_oldYaml_parsesCorrectly()`
8. `testConfiguration_oldProperties_parsesCorrectly()`
9. `testDeprecatedApi_compiles_withWarning()`
10. `testDeprecatedApi_behavior_unchanged()`
11. `testAllExistingTests_pass100Percent()`
12. `testMigrationGuide_documentsChanges()`

**For each test, specify**:
- What "old behavior" is being preserved (be specific - reference current implementation patterns)
- Migration path design (how deprecated APIs delegate to new implementations)
- Test data requirements (old configuration files, legacy API usage examples)
- Success criteria (zero breaking changes definition)

**Acceptance Criteria**:
- 100% backward compatibility
- Zero breaking changes
- All existing tests pass
- Deprecated APIs still functional with clear migration path

#### Category 3: Performance Baseline Tests (13 tests)
**Goal**: Establish performance baselines and ensure no regression from module separation.

**Test Names** (document EXACTLY these):
1. `testModuleLoading_commonModule_under10ms()`
2. `testModuleLoading_druidModule_under10ms()`
3. `testModuleLoading_hikariModule_under10ms()`
4. `testModuleLoading_p6spyModule_under10ms()`
5. `testRuntimePerformance_druid_noRegression()`
6. `testRuntimePerformance_hikari_noRegression()`
7. `testRuntimePerformance_p6spy_noRegression()`
8. `testMemoryUsage_staticFootprint_noIncrease()`
9. `testMemoryUsage_runtime_noIncrease()`
10. `testConnectionAcquisition_speed_unchanged()`
11. `testSqlValidation_throughput_noRegression()`
12. `testSqlValidation_latency_noRegression()`
13. `testConcurrentAccess_scalability_maintained()`

**For each test, specify**:
- Performance metric being measured (throughput, latency, memory, startup time)
- Measurement methodology (JMH benchmark configuration, profiler usage)
- Baseline establishment (how to capture "before" metrics from current implementation)
- Regression threshold (< 10ms module load, < 110% runtime, no memory increase)
- Test environment requirements (warmup iterations, measurement iterations, JVM flags)

**Performance Targets**:
- Module loading: < 10ms overhead per module
- Runtime validation throughput: < 5% degradation (< 110% baseline)
- P99 latency: no increase
- Memory footprint: no increase
- Startup time: no increase

**Acceptance Criteria**:
- All performance baselines documented
- JMH benchmarks configured correctly
- Regression thresholds defined
- Performance measurement reproducible

---

## Guidance

### TDD First Principle
**CRITICAL**: Design ALL tests BEFORE any implementation begins. This task produces test specifications - NOT test implementations. Implementation agents will write actual test code based on these specifications.

### Module Separation Focus
Reference `.apm/Memory/Phase_11_Reference_Architecture/Architecture_Review_Report.md` to understand Phase 11 scope:

**DO Design Tests For** ✅:
- Module structure refactoring (extracting modules)
- Dependency isolation (POM configuration, provided scope, transitive deps)
- Code extraction to specialized modules (moving classes between modules)
- Backward compatibility (old APIs still work)
- Performance baselines (no regression from modularization)

**DON'T Design Tests For** ❌:
- RuleChecker refactoring (belongs to Phase 12)
- SqlContext changes (belongs to Phase 12)
- StatementVisitor introduction (belongs to Phase 12)
- InnerInterceptor implementation (belongs to Phase 13)
- Any architecture pattern changes (Phase 12)

### Test Design Quality Criteria

#### 1. Measurability
Every test must have concrete pass/fail criteria:
- ❌ BAD: "Test module isolation"
- ✅ GOOD: "Verify `sql-guard-jdbc-druid` compiles successfully when HikariCP is NOT on classpath, using Maven profile `<excludeHikari>`"

#### 2. Implementability
Test specifications must be detailed enough for implementation agents to write actual test code:
- What test framework? (JUnit 5, Mockito, AssertJ, JMH)
- What test data? (H2 schema, SQL samples, configuration files)
- What assertions? (specific expected values, error messages, performance thresholds)

#### 3. Traceability
Each test must map to specific acceptance criteria from Implementation Plan.

### AbstractJdbcModuleTest Design Considerations

**H2 In-Memory Database Setup**:
```java
// Specify interface only - DO NOT implement
public abstract class AbstractJdbcModuleTest {
    // Setup/teardown lifecycle
    // Connection pool mock creation
    // SQL execution utilities
    // Module isolation helpers
    // Performance measurement utilities
}
```

**Key Capabilities Needed**:
- Lightweight H2 setup (no external database required)
- Support for all 3 pool types (Druid, HikariCP, P6Spy)
- SQL execution verification (did interception happen?)
- Performance timing utilities (for baseline tests)
- ClassLoader verification (for isolation tests)

### Test Execution Strategy

**Maven Profiles for Isolation Testing**:
```xml
<!-- Example specification - actual implementation in Task 11.2-11.5 -->
<profile>
    <id>test-druid-only</id>
    <!-- Excludes HikariCP and P6Spy from classpath -->
</profile>
```

**JMH Benchmark Configuration**:
```java
// Specify benchmark parameters - actual implementation in Task 11.6
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
```

Document these execution strategies in your test design.

---

## Test Matrix Summary

| Category | Test Count | Purpose | Acceptance Criteria |
|----------|-----------|---------|---------------------|
| Module Isolation | 15 tests | Verify independent compilation, dependency isolation | All modules compile independently, no transitive pollution |
| Backward Compatibility | 12 tests | Ensure existing code works unchanged | 100% compatibility, zero breaking changes |
| Performance Baselines | 13 tests | Establish performance targets, detect regression | < 10ms load, < 110% runtime, no memory increase |
| **TOTAL** | **40 tests** | **Comprehensive TDD coverage** | **Test design complete, implementable, measurable** |

---

## Acceptance Criteria

Your test design document will be accepted when:

- [ ] **Completeness**: All 40 test cases documented with full specifications
- [ ] **Implementability**: Each test has sufficient detail for implementation (framework, assertions, test data)
- [ ] **Measurability**: All tests have concrete pass/fail criteria (no ambiguous assertions)
- [ ] **AbstractJdbcModuleTest Spec**: Base class interface and capabilities clearly defined
- [ ] **Execution Strategy**: Maven profiles, JMH configuration, H2 setup documented
- [ ] **Scope Compliance**: Architecture refactoring tests explicitly excluded
- [ ] **Traceability**: Each test maps to Implementation Plan acceptance criteria
- [ ] **Performance Targets**: All baseline thresholds defined (< 10ms, < 110%, no memory increase)
- [ ] **Review Ready**: Document structured for review by implementation agents

---

## Reference Documents

### Required Reading (Before Starting)
1. **Architecture Review Report**: `.apm/Memory/Phase_11_Reference_Architecture/Architecture_Review_Report.md`
   - Understand Phase 11 scope boundaries
   - Learn why module separation is isolated from architecture refactoring
   - Review dependency principles

2. **Implementation Plan Phase 11**: `.apm/Implementation_Plan.md` (lines 9-176)
   - Full task context
   - Acceptance criteria for all 6 tasks
   - Success metrics

3. **Module Separation Strategy**: `.apm/Memory/Phase_11_Reference_Architecture/Module_Separation_And_Version_Compatibility.md`
   - Module dependency patterns
   - Provided scope strategy
   - Version compatibility approach

### Reference for Context (Optional)
- **Existing JDBC Tests**: `sql-guard-jdbc/src/test/java/**/*Test.java` (understand current test patterns)
- **Current Implementation**: `sql-guard-jdbc/src/main/java/**/*.java` (understand code being refactored)

---

## Deliverable Format

**File**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md`

**Structure**:
```markdown
# Task 11.1 Test Design Document

## 1. Introduction
- TDD philosophy for Phase 11
- Test coverage goals (40+ tests)
- Design-first approach rationale

## 2. Test Fixture Design
### 2.1 AbstractJdbcModuleTest Specification
- Interface definition
- Required capabilities
- Usage examples

## 3. Module Isolation Tests (15 tests)
### Test 3.1: testCommonModule_compilesWithoutPoolDependencies()
- Objective: ...
- Methodology: ...
- Success Criteria: ...
- Implementation Class: ...

[... repeat for all 15 tests ...]

## 4. Backward Compatibility Tests (12 tests)
[... detailed specifications ...]

## 5. Performance Baseline Tests (13 tests)
[... detailed specifications ...]

## 6. Test Execution Strategy
- Maven profiles for isolation testing
- JMH benchmark configuration
- H2 database setup
- Test data preparation

## 7. Acceptance Criteria Mapping
- Map each test to Implementation Plan criteria
- Traceability matrix

## 8. Appendix
- Test naming conventions
- Framework versions (JUnit 5, Mockito, JMH)
- Performance measurement methodology
```

---

## Success Metrics

- [ ] **40+ test cases** fully specified
- [ ] **AbstractJdbcModuleTest** interface defined
- [ ] **Test execution strategy** documented
- [ ] **Performance baselines** established with thresholds
- [ ] **Backward compatibility** preservation verified
- [ ] **Module isolation** validation comprehensive
- [ ] **Implementation ready** - agents can write code from specs
- [ ] **Review approved** - Manager Agent confirms completeness

---

## Notes for Implementation Agent

**Agent_Testing_Validation**: You are designing tests, NOT implementing them yet. Focus on:

1. **Specification Quality**: Each test must be detailed enough for Agent_Core_Engine_Foundation to implement without ambiguity
2. **TDD Rigor**: Tests define the contract - implementation will conform to tests, not vice versa
3. **Scope Discipline**: Resist scope creep - this is module separation ONLY, not architecture refactoring
4. **Measurability**: Every acceptance criterion must be testable with concrete assertions

**Timeline**: 2 days for complete test design. Output will block Tasks 11.2-11.6 until approved.

---

**Task Assignment Complete. Agent_Testing_Validation may begin execution.**
