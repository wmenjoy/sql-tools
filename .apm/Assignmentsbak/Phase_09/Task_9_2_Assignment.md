
---
task_ref: "Task 9.2 - P0 High-Value Audit Checkers (SlowQuery & ActualImpact)"
agent_assignment: "Agent_Audit_Analysis"
memory_log_path: ".apm/Memory/Phase_09_Audit_Checker_Layer/Task_9_2_P0_High_Value_Checkers.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: P0 High-Value Audit Checkers

## Task Reference
Implementation Plan: **Task 9.2 - P0 High-Value Audit Checkers** assigned to **Agent_Audit_Analysis**

## Context from Dependencies

This task builds directly upon **Task 9.1 - AbstractAuditChecker Base Class**, which provided the `AbstractAuditChecker` template, `ExecutionResult` input model, and `RiskScore` output model.

**Previous Work Summary (Task 9.1):**
- **Base Class**: `AbstractAuditChecker` enforces `validateInput -> performAudit -> buildAuditResult` lifecycle.
- **Input**: `ExecutionResult` contains `executionTimeMs`, `rowsAffected`, `errorMessage`, `resultSetSize`.
- **Output**: `RiskScore` supports `severity` (LOW/MEDIUM/HIGH/CRITICAL), `confidence` (0-100), and `impactMetrics`.
- **Infrastructure**: `sql-guard-audit-checker` module depends on `sql-guard-core`, giving access to JSqlParser utilities.

**Integration Requirements:**
- **Extensibility**: All checkers must extend `AbstractAuditChecker` and implement `performAudit(String sql, ExecutionResult result)`.
- **Configuration**: Checkers should be configurable (thresholds) via constructor or setter methods (dependency injection ready).
- **Statelessness**: Checkers must be thread-safe (stateless or immutable config).

## Objective

Implement the two highest-priority (P0) audit checkers that provide immediate value by detecting performance bottlenecks and dangerous unbounded operations based on actual execution metrics.

## Detailed Instructions

Complete this task in **4 exchanges**, one step per response. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: SlowQueryChecker TDD

**What to do**: Implement checker detecting queries exceeding execution time thresholds.

**How to approach**:
- Write test class `SlowQueryCheckerTest` in `com.footstone.sqlguard.audit.checker`.
- Test cases:
  - `testCheck_belowThreshold_shouldReturnNullOrLowRisk()`
  - `testCheck_exceedingHighThreshold_shouldReturnHighRisk()`
  - `testCheck_exceedingCriticalThreshold_shouldReturnCriticalRisk()`
  - `testConfiguration_shouldAllowCustomThresholds()`

**Implementation specifications**:
- Class: `SlowQueryChecker extends AbstractAuditChecker`
- Configuration Fields:
  - `long slowThresholdMs` (default: 1000ms)
  - `long criticalThresholdMs` (default: 5000ms)
- Logic:
  - If `executionTimeMs` > `criticalThresholdMs` → Severity **CRITICAL**, Confidence **100**.
  - If `executionTimeMs` > `slowThresholdMs` → Severity **HIGH**, Confidence **100**.
  - Else → Return null (no risk) or LOW severity info (optional, prefer noise reduction).
- Metrics: Include `execution_time_ms` and `threshold_exceeded_ms` in `impactMetrics`.

**Success criteria**: 5+ tests passing, correct severity escalation based on thresholds.

### Step 2: ActualImpactNoWhereChecker TDD (UPDATE/DELETE focus)

**What to do**: Implement checker detecting destructive operations (UPDATE/DELETE) that modified data without a WHERE clause (or with broad impact).

**How to approach**:
- Write test class `ActualImpactNoWhereCheckerTest`.
- Test cases:
  - `testCheck_updateWithoutWhere_shouldBeCritical()`
  - `testCheck_deleteWithoutWhere_shouldBeCritical()`
  - `testCheck_updateWithWhere_shouldBeSafe()` (unless rows affected is huge - see below)
  - `testCheck_selectWithoutWhere_shouldBeIgnoredByThisChecker()` (scope limitation: focus on mutation first, or handle separately)

**Implementation specifications**:
- Class: `ActualImpactNoWhereChecker extends AbstractAuditChecker`
- Logic:
  - Parse SQL using JSqlParser (leverage `sql-guard-core` utilities if available, or `CCJSqlParserUtil`).
  - Check statement type: only target `Update` and `Delete`.
  - Check for WHERE clause presence.
  - **Critical Logic**:
    - If (UPDATE/DELETE) AND (No WHERE clause) AND (`rowsAffected` > 0) → Severity **CRITICAL**.
    - Justification: "Unbounded data mutation detected: [Statement Type] without WHERE clause modified [X] rows."
  - **Safety Net**:
    - If parsing fails, fall back to regex check or return MEDIUM risk "Analysis Failed" (don't crash).

**Success criteria**: 5+ tests passing, accurately identifies unbounded mutations.

### Step 3: LargeResultChecker TDD (SELECT focus - Optional/Bonus or Combined)

*Note: The handover suggested "ActualImpactNoWhereChecker" as P0. Often "Large Result" (SELECT without LIMIT/WHERE) is P1. If you treat Step 2 as strictly mutation, this step covers the "Read" side.*
*Refinement: Let's combine "Unbounded Read" into Step 2 or keep it simple. Let's stick to the Handover: "ActualImpactNoWhereChecker". Let's assume it covers both or explicitly just mutation. Given "Actual Impact", let's make Step 2 cover Mutation (Critical) and Step 3 cover Read (High).*

**Revised Step 3: UnboundedReadChecker (or extension of Step 2)**
**What to do**: Extend `ActualImpactNoWhereChecker` or create `UnboundedReadChecker` to detect SELECTs returning excessive rows without filters.
**Decision**: Create separate `UnboundedReadChecker` (cleaner SRP).

**How to approach**:
- Write `UnboundedReadCheckerTest`.
- Logic:
  - If Statement is SELECT.
  - If `rowsAffected` (or `resultSetSize`) > `maxRowLimit` (default 10000).
  - Severity **HIGH**.
  - Justification: "Large result set fetching [X] rows may cause OOM or network saturation."

**Success criteria**: 3+ tests passing.

### Step 4: Integration & Registry

**What to do**: Verify all checkers working together.

**How to approach**:
- Update `AbstractAuditCheckerIntegrationTest` (or create `StandardCheckersIntegrationTest`).
- Register `SlowQueryChecker`, `ActualImpactNoWhereChecker`, `UnboundedReadChecker`.
- Run against a set of simulated `ExecutionResult`s.
- Verify that a single bad query (e.g., "Slow Update without Where") triggers multiple risks (Slow + Unbounded Mutation).

**Success criteria**: Integration test passing, confirming composability.

## Output

**Deliverables**:
- `SlowQueryChecker.java` + Tests
- `ActualImpactNoWhereChecker.java` + Tests
- `UnboundedReadChecker.java` + Tests (optional, but recommended for completeness of "No Where" concept)
- Integration verification.

**Memory Logging**:
- Log to `.apm/Memory/Phase_09_Audit_Checker_Layer/Task_9_2_P0_High_Value_Checkers.md`
