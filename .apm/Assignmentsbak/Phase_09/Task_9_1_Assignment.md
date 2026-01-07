
---
  task_ref: "Task 9.1 - AbstractAuditChecker Base Class & ExecutionResult Context"
  agent_assignment: "Agent_Audit_Analysis"
  memory_log_path: ".apm/Memory/Phase_09_Audit_Checker_Layer/Task_9_1_AbstractAuditChecker_Base_Class.md"
  execution_type: "multi-step"
  dependency_context: true
  ad_hoc_delegation: false
  ---

  # APM Task Assignment: AbstractAuditChecker Base Class & ExecutionResult Context

  ## Task Reference
  Implementation Plan: **Task 9.1 - AbstractAuditChecker Base Class & ExecutionResult Context** assigned to **Agent_Audit_Analysis**

  ## Context from Dependencies

  This task builds upon **Task 8.1 - AuditLogWriter Interface & JSON Schema** implemented by Agent_Audit_Infrastructure, establishing the audit infrastructure
  foundation upon which this checker framework will operate.

  **Integration Steps (complete in one response before main task):**

  1. **Read AuditEvent Model**: Examine `sql-guard-audit-api/src/main/java/com/footstone/sqlguard/audit/AuditEvent.java` to understand the complete audit event data
  structure that Phase 8 interceptors produce and that audit checkers will consume
  2. **Review JSON Schema**: Study `sql-guard-audit-api/docs/audit-log-schema.json` to understand the standardized audit log format and available execution context
  fields
  3. **Examine Integration Tests**: Review `sql-guard-audit-api/src/test/java/com/footstone/sqlguard/audit/AuditLogWriterIntegrationTest.java` to see realistic
  AuditEvent construction scenarios with execution metrics
  4. **Check Task 8.1 Memory Log**: Read `.apm/Memory/Phase_08_Audit_Output/Task_8_1_AuditLogWriter_Interface_JSON_Schema.md` to understand design decisions,
  important findings (ViolationInfo Jackson support, MD5 sqlId, immutability pattern), and downstream integration guidance

  **Producer Output Summary:**

  - **AuditEvent Model**: Immutable data class containing complete SQL execution context
    - **Required Fields**: sql, sqlType (SELECT/UPDATE/DELETE/INSERT), mapperId, timestamp
    - **Execution Metrics**: executionTimeMs (default: 0), rowsAffected (default: -1), errorMessage (nullable)
    - **Additional Context**: datasource, params (Map), violations (List<ViolationInfo> from pre-execution validation)
    - **Auto-Generated**: sqlId (MD5 hash for deduplication)

  - **JSON Serialization**: Jackson ObjectMapper configured with JavaTimeModule, ISO-8601 timestamps, explicit null serialization (ALWAYS inclusion)

  - **Builder Pattern**: AuditEvent.builder() with validation enforcing required fields, constraints (executionTimeMs >= 0, rowsAffected >= -1, timestamp not future
  with 5s clock skew tolerance)

  - **Thread Safety**: Fully immutable design enabling safe sharing across threads without synchronization

  - **Integration Points**: ViolationInfo enhanced with Jackson annotations (@JsonCreator, @JsonProperty) for seamless deserialization of pre-execution violations in
   audit events

  **Integration Requirements:**

  - **ExecutionResult Design**: Must extract execution metrics from AuditEvent structure (executionTimeMs, rowsAffected, errorMessage, timestamp) - these fields
  provide post-execution context unavailable during pre-execution validation

  - **Risk Correlation**: AbstractAuditChecker should support correlating pre-execution violations (AuditEvent.violations) with post-execution audit findings (actual
   impact assessment)

  - **SQL Context Reuse**: Leverage sql and sqlType fields from AuditEvent for analysis, avoiding re-parsing where possible

  - **Immutability Pattern**: Follow AuditEvent's immutability design for ExecutionResult and RiskScore models to ensure thread-safe checker execution in audit
  service

  **User Clarification Protocol:**

  If the relationship between AuditEvent execution metrics (executionTimeMs, rowsAffected, errorMessage) and ExecutionResult model design is ambiguous after
  completing integration steps, ask User about preferred abstraction level - whether ExecutionResult should directly wrap AuditEvent or extract specific fields.

  ## Objective

  Design AbstractAuditChecker base class providing execution result access and multi-dimensional risk scoring framework for audit checkers, establishing
  architectural foundation enabling context-aware analysis with execution metrics (rows affected, duration, errors) unavailable during pre-execution validation.

  ## Detailed Instructions

  Complete this task in **5 exchanges**, one step per response. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

  ### Step 1: ExecutionResult Model TDD

  **What to do**: Implement ExecutionResult data model containing post-execution metrics using TDD methodology.

  **How to approach**:
  - Write test class `ExecutionResultTest` in new package `com.footstone.sqlguard.audit.model` (create in sql-guard-core or new sql-guard-audit-checker module -
  recommend new module for Phase 9 separation)
  - Test methods covering:
    - `testBuilder_withAllMetrics_shouldConstruct()` - complete execution result with all metrics
    - `testBuilder_withErrorOnly_shouldConstructFailureResult()` - error execution without success metrics
    - `testRowsAffected_forDifferentOperations_shouldReflectActual()` - SELECT returns result set size, UPDATE returns affected count, verify semantics
    - `testExecutionTime_shouldSupportMicrosecondPrecision()` - duration captured accurately (use long for milliseconds)
    - `testEquals_withSameMetrics_shouldBeEqual()` - value object semantics for comparison

  **Implementation specifications**:
  - Create `ExecutionResult` class with immutable fields:
    - `int rowsAffected` - actual rows from SQL execution, -1 if not applicable
    - `long executionTimeMs` - actual execution duration from interceptor timing
    - `String errorMessage` - SQLException message if execution failed (nullable)
    - `int resultSetSize` - for SELECT queries (nullable, may differ from rowsAffected)
    - `Instant executionTimestamp` - when execution completed
    - `Map<String, Object> additionalMetrics` - extensibility for custom metrics (cache hit rate, connection pool wait time, etc.)
  - Implement builder pattern: `ExecutionResult.builder().rowsAffected(count).executionTimeMs(duration).build()`
  - Add validation: rowsAffected >= -1, executionTimeMs >= 0, timestamp not null
  - Follow immutability pattern from AuditEvent for thread-safety

  **Success criteria**: 5+ tests passing, ExecutionResult builds correctly with validation

  ### Step 2: RiskScore Model TDD

  **What to do**: Implement multi-dimensional risk scoring model enabling precise audit findings with confidence levels.

  **How to approach**:
  - Write test class `RiskScoreTest` with comprehensive coverage:
    - `testBuilder_withAllDimensions_shouldConstruct()` - complete risk score construction
    - `testSeverity_shouldEnforceValidLevels()` - only LOW/MEDIUM/HIGH/CRITICAL allowed (reuse existing RiskLevel enum)
    - `testConfidence_shouldEnforceRange()` - 0-100 range validation with clear semantics
    - `testImpactMetrics_shouldBeImmutable()` - defensive copy of metrics map preventing external modification
    - `testCompareTo_shouldSortBySeverityThenConfidence()` - CRITICAL > HIGH regardless of confidence, within same severity sort by confidence descending for
  prioritization

  **Implementation specifications**:
  - Create `RiskScore` class in audit.model package with fields:
    - `RiskLevel severity` - enum LOW/MEDIUM/HIGH/CRITICAL (reuse existing from ValidationResult for consistency)
    - `int confidence` - 0-100 assessment certainty:
      - 100 = concrete metrics (row count, timing)
      - 80-90 = statistical analysis with solid data
      - 50-70 = heuristics or incomplete data
    - `Map<String, Number> impactMetrics` - quantified impact:
      - data_volume_affected (rows modified/read)
      - query_cost_estimate (execution cost units)
      - risk_score_points (composite score for dashboards)
    - `String justification` - human-readable explanation of assessment (e.g., "Query executed in 5230ms exceeding 5000ms HIGH threshold, affecting 1523 rows")
    - `List<String> recommendations` - actionable remediation steps (e.g., "Add index on user.email field", "Reduce transaction scope", "Implement pagination")
  - Implement `Comparable<RiskScore>` for risk prioritization in audit reports
  - Builder pattern with validation ensuring confidence 0-100, severity not null, justification not empty

  **Success criteria**: 5+ tests passing, RiskScore comparable sorting works correctly

  ### Step 3: AbstractAuditChecker Template TDD

  **What to do**: Implement AbstractAuditChecker base class with template method pattern ensuring consistent checker lifecycle.

  **How to approach**:
  - Write test class `AbstractAuditCheckerTest` with behavioral verification:
    - `testCheck_withValidInput_shouldInvokeTemplate()` - verify template method calls validateInput → performAudit → buildAuditResult sequence
    - `testCheck_withNullExecutionResult_shouldThrowException()` - input validation catches null/incomplete data
    - `testCheck_withIncompleteResult_shouldHandleGracefully()` - partial metrics (missing resultSetSize) don't crash checker
    - `testPerformance_shouldMeetRelaxedRequirements()` - check() completes in <50ms for typical input (relaxed vs <5ms runtime requirement)

  **Implementation specifications**:
  - Create `AbstractAuditChecker` abstract class in `com.footstone.sqlguard.audit.checker` package
  - Template method (final, cannot be overridden):
    ```java
    public final AuditResult check(String sql, ExecutionResult result) {
        validateInput(sql, result);  // throws IllegalArgumentException if required fields null
        RiskScore score = performAudit(sql, result);  // abstract method for subclass implementation
        return buildAuditResult(sql, result, score);  // creates AuditResult with checker metadata
    }
  - Abstract method for subclasses: protected abstract RiskScore performAudit(String sql, ExecutionResult result)
  - Utility methods for subclasses (protected):
    - extractTable(sql) - JSqlParser extraction of table name
    - extractWhereClause(sql) - WHERE condition analysis
    - calculateImpactScore(rowsAffected) - standard impact calculation formula
    - isSlowQuery(executionTimeMs, threshold) - performance assessment helper
  - Add String getCheckerId() abstract method for checker identification in results

  Success criteria: Template method enforces lifecycle, utility methods functional, performance <50ms baseline

  Step 4: AuditResult Model TDD

  What to do: Implement AuditResult aggregating checker findings for storage and reporting.

  How to approach:
  - Write test class AuditResultTest covering:
    - testBuilder_withCompleteData_shouldConstruct() - full result with all fields
    - testBuilder_withMultipleRisks_shouldAggregate() - single checker may identify multiple risks (e.g., slow query + missing index)
    - testToJson_shouldSerializeForStorage() - audit results will be stored in ClickHouse, verify Jackson serialization
    - testEquals_shouldCompareAllFields() - value object semantics for deduplication

  Implementation specifications:
  - Create AuditResult class in audit.model package with fields:
    - String checkerId - checker identifier for tracking (e.g., "SlowQueryChecker", "ActualImpactNoWhereChecker")
    - String sql - audited SQL statement (for reference and grouping)
    - ExecutionResult executionResult - original execution context (includes timestamp)
    - List<RiskScore> risks - multiple risks possible from single check (e.g., slow + missing index)
    - Instant auditTimestamp - when audit performed (may differ from execution timestamp)
    - Map<String, Object> checkerMetadata - checker-specific context (thresholds used, baselines compared, whitelist status)
  - Implement Jackson serialization support for database storage:
    - Add @JsonCreator and @JsonProperty annotations
    - Ensure all nested objects (ExecutionResult, RiskScore) are Jackson-compatible
    - Test round-trip JSON serialization/deserialization
  - Builder pattern with validation: checkerId and sql required, risks list not empty for meaningful results

  Success criteria: 4+ tests passing, JSON serialization working, nested objects serialize correctly

  Step 5: Integration Testing & Base Behavior

  What to do: Validate AbstractAuditChecker foundation with concrete test implementations and performance benchmarks.

  How to approach:
  - Write integration test AbstractAuditCheckerIntegrationTest creating 3 concrete test checkers:
    - SimpleAuditChecker: detects queries returning >1000 rows with HIGH severity (tests rowsAffected-based analysis)
    - SlowQueryTestChecker: detects >1000ms execution with CRITICAL severity (tests timing-based analysis)
    - ErrorAuditChecker: analyzes error messages for specific failure patterns (tests error handling analysis)
  - For each test checker:
    - Implement performAudit() with specific detection logic
    - Test with various ExecutionResult scenarios:
        - Success with high impact (e.g., 5000 rows affected, 2500ms execution)
      - Success with low impact (e.g., 5 rows, 50ms)
      - Failure with error message (e.g., SQLException: deadlock detected)
      - Edge cases (0 rows affected, null errorMessage, missing optional fields)
    - Verify RiskScore severity matches expected level
    - Verify confidence calculation appropriate (100% for concrete metrics, lower for heuristics)
    - Verify justification provides actionable information
    - Verify recommendations relevant to detected risk
  - Template method enforcement tests:
    - Attempt to override final check() method (should be compilation error - document in test comments)
    - Verify validateInput() called before performAudit() (use mock or spy to verify call order)
    - Verify error handling propagates correctly to AuditResult
  - Performance benchmark:
    - Create dataset of 1000 different ExecutionResults (varied row counts, durations, error scenarios)
    - Run all 3 test checkers on each result
    - Measure p95/p99 latencies using JMH or manual timing
    - Verify <50ms p99 threshold met (Phase 9 relaxed requirement vs Phase 4's <5ms)
    - Compare to runtime checker performance documented in Phase 2/4 to confirm audit checkers can afford more complex analysis

  Success criteria:
  - 3 concrete test checkers functional
  - 20+ integration tests covering edge cases
  - Performance benchmark showing <50ms p99
  - Template method lifecycle verified
  - All tests passing with mvn test

  Expected Output

  Deliverables:
  - 4 model classes: ExecutionResult, RiskScore, AuditResult, AbstractAuditChecker (in sql-guard-audit-checker or sql-guard-core module)
  - 5 test classes: ExecutionResultTest, RiskScoreTest, AbstractAuditCheckerTest, AuditResultTest, AbstractAuditCheckerIntegrationTest
  - 3 concrete test checker implementations for integration validation
  - 40+ total tests (ExecutionResult 5+, RiskScore 5+, AbstractAuditChecker 4+, AuditResult 4+, Integration 20+)
  - Performance benchmark results demonstrating <50ms p99 latency
  - Maven module structure (if creating new sql-guard-audit-checker module, update parent POM)

  File locations:
  - Main source: sql-guard-audit-checker/src/main/java/com/footstone/sqlguard/audit/{model,checker}/
  - Test source: sql-guard-audit-checker/src/test/java/com/footstone/sqlguard/audit/{model,checker}/
  - POM: sql-guard-audit-checker/pom.xml (if new module)

  Success criteria:
  - All 40+ tests passing with 100% pass rate
  - ExecutionResult provides complete execution context from AuditEvent integration
  - RiskScore enables multi-dimensional risk assessment (severity + confidence + impact metrics)
  - AbstractAuditChecker template method enforces consistent lifecycle
  - AuditResult aggregates findings for storage and reporting
  - Performance baseline met: <50ms p99 for audit checkers (vs <5ms runtime requirement)
  - Foundation ready for Tasks 9.2, 9.3, 9.4 P0/P1/P2 checker implementations

  Memory Logging

  Upon completion, you MUST log work in: .apm/Memory/Phase_09_Audit_Checker_Layer/Task_9_1_AbstractAuditChecker_Base_Class.md

  Follow .apm/guides/Memory_Log_Guide.md instructions including:
  - Summary of implementation approach and design decisions
  - Details of each step completion with test counts
  - Output listing all created files with paths
  - Any design decisions or tradeoffs (module structure choice, abstraction level, performance optimization strategies)
  - Important findings (integration patterns, performance characteristics, extensibility considerations)
  - Next steps for downstream tasks (Tasks 9.2, 9.3 will extend AbstractAuditChecker)
