---
task_ref: "Task 10.3 - Audit Engine & Checker Orchestration"
agent_assignment: "Agent_Audit_Service"
memory_log_path: ".apm/Memory/Phase_10_Audit_Service/Task_10_3_Audit_Engine_Checker_Orchestration.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
parallel_with: "Task 10.2"
---

# APM Task Assignment: Audit Engine & Checker Orchestration

## Task Reference
Implementation Plan: **Task 10.3 - Audit Engine & Checker Orchestration** assigned to **Agent_Audit_Service**

## Parallel Execution Note
**此任务与 Task 10.2 并行执行。** 需要预定义共享接口以确保集成成功。

### 共享接口定义（Task 10.2 和 10.3 共用）

```java
// 放在 sql-audit-service-core 模块
package com.footstone.audit.service.core.processor;

/**
 * 审计事件处理器接口 - Task 10.2 调用, Task 10.3 实现
 */
public interface AuditEventProcessor {
    /**
     * 处理审计事件
     * @param event 审计事件
     * @return 处理结果
     */
    AuditProcessingResult process(AuditEvent event);
}
```

## Context from Dependencies

### Task 10.1 Output (Required)
- Maven multi-module project: `sql-audit-service/`
- Sub-modules: `sql-audit-service-core/`, `sql-audit-service-web/`, `sql-audit-service-consumer/`
- Virtual Thread executor configuration

### Phase 9 Output (External - Checkers)
- `AbstractAuditChecker` base class
- P0 Checkers: `SlowQueryChecker`, `ActualImpactNoWhereChecker`
- P1 Checkers: `LargeResultChecker`, `UnboundedReadChecker`
- P2 Checkers: `ErrorPatternChecker`
- Models: `ExecutionResult`, `RiskScore`, `AuditResult`

**Dependency Declaration:**
```xml
<dependency>
  <groupId>com.footstone.sqlguard</groupId>
  <artifactId>sql-guard-audit-checker</artifactId>
  <version>${project.version}</version>
</dependency>
```

## Objective
Implement audit engine orchestrating execution of all Phase 9 checkers against each consumed AuditEvent, coordinating parallel checker execution using `CompletableFuture.allOf()` (Critical Fix C1: Structured Concurrency is Preview feature in Java 21), aggregating RiskScore results into comprehensive AuditReport, applying checker configuration and whitelisting rules, and persisting final audit results to storage layer for query and reporting.

## Detailed Instructions

Complete in **5 exchanges**, one step per response. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Core Models & Interfaces Definition (TDD)
**先写测试，再实现：**

1. **Write test class AuditEngineModelTest** covering:
   - `testAuditReport_builder_shouldCreateImmutable()`
   - `testAuditReport_riskScore_shouldAggregate()`
   - `testAuditProcessingResult_shouldContainReport()`
   - `testAuditEventProcessor_interface_shouldDefine()`
   - `testAuditReportRepository_interface_shouldDefine()`
   - `testCheckerResult_shouldContainRiskScore()`
   - `testAggregatedRiskScore_shouldCalculateMax()`
   - `testAggregatedRiskScore_shouldCalculateWeightedSum()`

2. **Then implement models:**
   ```java
   // AuditReport - 审计结果聚合
   public record AuditReport(
       String reportId,
       String sqlId,
       AuditEvent originalEvent,
       List<CheckerResult> checkerResults,
       RiskScore aggregatedRiskScore,
       Instant createdAt
   ) {}

   // AuditEventProcessor - 与 Task 10.2 的集成接口
   public interface AuditEventProcessor {
       AuditProcessingResult process(AuditEvent event);
   }

   // AuditReportRepository - 与 Task 10.4 的集成接口
   public interface AuditReportRepository {
       void save(AuditReport report);
       Optional<AuditReport> findById(String reportId);
       List<AuditReport> findByTimeRange(Instant start, Instant end);
   }
   ```

3. **Verify:** Model tests pass, interfaces defined

### Step 2: Audit Engine Core Implementation (TDD)
**先写测试，再实现：**

1. **Write test class DefaultAuditEngineTest** covering:
   - `testAuditEngine_singleChecker_shouldExecute()`
   - `testAuditEngine_multipleCheckers_shouldExecuteAll()`
   - `testAuditEngine_disabledChecker_shouldSkip()`
   - `testAuditEngine_checkerTimeout_shouldHandle()`
   - `testAuditEngine_checkerException_shouldContinue()`
   - `testAuditEngine_emptyCheckers_shouldReturnEmptyReport()`
   - `testAuditEngine_shouldImplementAuditEventProcessor()`
   - `testAuditEngine_resultAggregation_shouldCombineRiskScores()`

2. **Then implement DefaultAuditEngine:**
   ```java
   @Service
   public class DefaultAuditEngine implements AuditEventProcessor {

       private final List<AbstractAuditChecker> checkers;
       private final AuditEngineConfig config;

       @Override
       public AuditProcessingResult process(AuditEvent event) {
           // 转换为 ExecutionResult
           ExecutionResult executionResult = toExecutionResult(event);

           // 执行所有 checkers
           List<CheckerResult> results = executeCheckers(executionResult);

           // 聚合结果
           return aggregateResults(event, results);
       }
   }
   ```

3. **Verify:** Engine tests pass, implements AuditEventProcessor interface

### Step 3: Parallel Checker Execution with CompletableFuture (TDD)
**先写测试，再实现：**

1. **Write test class ParallelCheckerExecutionTest** covering:
   - `testParallelExecution_allCheckers_shouldRunConcurrently()`
   - `testParallelExecution_shouldUseVirtualThreads()`
   - `testParallelExecution_shouldRespectTimeout200ms()`
   - `testParallelExecution_oneSlowChecker_shouldNotBlockOthers()`
   - `testParallelExecution_12Checkers_shouldCompleteLessThan50ms()`
   - `testCompletableFutureAllOf_shouldReplaceStructuredConcurrency()` [C1验证]
   - `testParallelExecution_exceptionIsolation_shouldContinue()`
   - `testParallelExecution_shouldCollectAllResults()`

2. **Then implement parallel execution:**
   ```java
   private List<CheckerResult> executeCheckersInParallel(ExecutionResult event) {
       List<CompletableFuture<CheckerResult>> futures = checkers.stream()
           .filter(AbstractAuditChecker::isEnabled)
           .map(checker -> CompletableFuture.supplyAsync(
               () -> executeChecker(checker, event),
               virtualThreadExecutor
           ).orTimeout(config.getCheckerTimeoutMs(), TimeUnit.MILLISECONDS)
            .exceptionally(ex -> CheckerResult.failed(checker.getName(), ex)))
           .toList();

       CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
           .join();

       return futures.stream()
           .map(CompletableFuture::join)
           .toList();
   }
   ```

3. **Verify:** Parallel execution tests pass, <50ms for 12 checkers

### Step 4: Checker Registry & Configuration (TDD)
**先写测试，再实现：**

1. **Write test class CheckerRegistryTest** covering:
   - `testRegistry_autoDiscovery_shouldFindAllCheckers()`
   - `testRegistry_springComponentScan_shouldWork()`
   - `testRegistry_shouldContainPhase9Checkers()`
   - `testRegistry_disableChecker_shouldExclude()`
   - `testRegistry_enableChecker_shouldInclude()`
   - `testRegistry_getCheckerByName_shouldFind()`

2. **Write test class CheckerConfigurationTest** covering:
   - `testConfig_yamlBinding_shouldLoad()`
   - `testConfig_perCheckerThreshold_shouldApply()`
   - `testConfig_whitelist_shouldFilterSql()`
   - `testConfig_blacklist_shouldMarkCritical()`
   - `testConfig_databaseOverride_shouldTakePrecedence()`
   - `testConfig_hotReload_shouldApplyWithoutRestart()`

3. **Then implement:**
   - `CheckerRegistry` with Spring `@Component` auto-discovery
   - `AuditEngineConfig` with YAML binding
   - Whitelist/blacklist rules support

4. **Verify:** Registry and configuration tests pass

### Step 5: Result Aggregation & Integration Tests
**验证所有性能目标：**

1. **Write test class ResultAggregationTest** covering:
   - `testAggregation_multipleRiskScores_shouldCombine()`
   - `testAggregation_maxRiskLevel_shouldBubbleUp()`
   - `testAggregation_weightedScore_shouldCalculate()`
   - `testAggregation_emptyResults_shouldReturnNoRisk()`
   - `testAggregation_shouldCreateAuditReport()`

2. **Write test class AuditEngineIntegrationTest** covering:
   - `testIntegration_withAllPhase9Checkers_shouldWork()`
   - `testIntegration_slowQueryDetection_shouldTrigger()`
   - `testIntegration_noWhereActualImpact_shouldTrigger()`
   - `testIntegration_largeResultSet_shouldTrigger()`
   - `testIntegration_errorPattern_shouldTrigger()`
   - `testIntegration_fullPipeline_shouldComplete()`

3. **Performance targets verification:**
   - 12 checkers 并发执行: <50ms (p95)
   - 单个 checker 超时: 200ms
   - 整体超时: 500ms
   - Checker 失败率: <1%
   - 结果聚合: <5ms

4. **Final verification:** `mvn clean test -pl sql-audit-service-core` - all 50+ tests pass

## Expected Output
- **Deliverables:**
  - `AuditReport` record (immutable audit result)
  - `AuditEventProcessor` interface (与 Task 10.2 集成点)
  - `AuditReportRepository` interface (与 Task 10.4 集成点)
  - `DefaultAuditEngine` implementation
  - `CheckerRegistry` with auto-discovery
  - `AuditEngineConfig` with YAML binding
  - `ResultAggregator` for risk score combination

- **Success criteria:**
  - 50+ tests passing (8+8+8+12+14)
  - `mvn clean test` succeeds
  - 12 checkers parallel execution <50ms
  - CompletableFuture.allOf() 替代 Structured Concurrency
  - 所有 Phase 9 checkers 集成成功

- **File locations:**
  - `sql-audit-service-core/src/main/java/com/footstone/audit/service/core/`
    - `model/AuditReport.java`
    - `model/CheckerResult.java`
    - `model/AuditProcessingResult.java`
    - `processor/AuditEventProcessor.java`
    - `engine/DefaultAuditEngine.java`
    - `engine/CheckerRegistry.java`
    - `engine/ResultAggregator.java`
    - `config/AuditEngineConfig.java`
    - `repository/AuditReportRepository.java`
  - `sql-audit-service-core/src/test/java/...` (all test classes)

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_10_Audit_Service/Task_10_3_Audit_Engine_Checker_Orchestration.md`
Follow .apm/guides/Memory_Log_Guide.md instructions.

## Technical Notes
1. **Critical Fix C1 - Structured Concurrency 替代方案:**
   ```java
   // ❌ 不使用 (Preview 特性)
   try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
       scope.fork(() -> checker1.check(event));
       scope.fork(() -> checker2.check(event));
       scope.join();
   }

   // ✅ 使用 CompletableFuture.allOf()
   CompletableFuture.allOf(
       CompletableFuture.supplyAsync(() -> checker1.check(event), executor),
       CompletableFuture.supplyAsync(() -> checker2.check(event), executor)
   ).join();
   ```

2. **AuditEvent → ExecutionResult 转换:**
   ```java
   private ExecutionResult toExecutionResult(AuditEvent event) {
       return ExecutionResult.builder()
           .sql(event.getSql())
           .sqlType(event.getSqlType())
           .executionTimeMs(event.getExecutionTimeMs())
           .rowsAffected(event.getRowsAffected())
           .errorMessage(event.getErrorMessage())
           .build();
   }
   ```

3. **性能目标:**
   | 指标 | 目标值 |
   |------|--------|
   | 12 checkers 并发 | <50ms (p95) |
   | 单个 checker 超时 | 200ms |
   | 整体超时 | 500ms |
   | Checker 失败率 | <1% |
   | 结果聚合 | <5ms |

4. **与其他任务的接口约定:**
   - `AuditEventProcessor`: Task 10.2 (Kafka Consumer) 调用此接口
   - `AuditReportRepository`: Task 10.4 (Storage) 实现此接口

---

**Assignment Created:** 2025-12-18
**Manager Agent:** Manager_Agent_3
**Status:** Ready for Parallel Assignment with Task 10.2
**Prerequisite:** Task 10.1 Completed
**Parallel With:** Task 10.2
