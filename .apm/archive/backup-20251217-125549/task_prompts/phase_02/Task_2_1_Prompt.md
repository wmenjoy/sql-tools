---
task_ref: "Task 2.1 - Rule Checker Framework & Interfaces"
agent_assignment: "Agent_Core_Engine_Validation"
memory_log_path: ".apm/Memory/Phase_02_Validation_Engine/Task_2_1_Rule_Checker_Framework_Interfaces.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Rule Checker Framework & Interfaces

## Task Reference
Implementation Plan: **Task 2.1 - Rule Checker Framework & Interfaces** assigned to **Agent_Core_Engine_Validation**

## Context from Dependencies
Based on Phase 1 completed work:
- Use the `sql-guard-core` module from Task 1.1
- Leverage JSqlParser facade from Task 1.4 for SQL parsing and AST extraction
- Use core domain models from Task 1.2 (SqlContext, ValidationResult, RiskLevel)
- Follow TDD methodology with JUnit 5 and Mockito
- Apply Google Java Style enforced by Checkstyle

## Objective
Establish foundational framework for Chain of Responsibility pattern rule checking system with RuleChecker interface, AbstractRuleChecker base class providing shared utilities, and RuleCheckerOrchestrator coordinating validation across all enabled checkers.

## Detailed Instructions
Complete this task in **4 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: RuleChecker Interface TDD
**Test First:**
Write test class `RuleCheckerTest` in `com.footstone.sqlguard.validator.rule` package covering:
- Checker with no violations leaves ValidationResult passed
- Checker adding violation changes passed to false
- Disabled checker returns false from isEnabled()
- Verify check method signature accepts SqlContext and ValidationResult

**Then Implement:**
Create `RuleChecker` interface in `com.footstone.sqlguard.validator.rule` package with:
- Method: `void check(SqlContext context, ValidationResult result)` - performs validation, may add violations to result
- Method: `boolean isEnabled()` - returns true if checker should execute

Create `CheckerConfig` base class in same package with:
- Field: `boolean enabled` (default true)
- Getter: `isEnabled()` returning enabled field value
- All rule config classes will extend this base

**Constraints:**
- Interface provides uniform contract for polymorphic checker execution
- CheckerConfig provides common enabled/disabled toggle mechanism
- All validation happens via side-effect on ValidationResult (no return value from check)

### Step 2: AbstractRuleChecker Utilities TDD
**Test First:**
Write test class `AbstractRuleCheckerTest` covering:
- `extractWhere()` from SELECT/UPDATE/DELETE returns Expression, returns null for INSERT
- `extractTableName()` from simple SELECT returns table name
- `extractTableName()` from JOIN returns primary table
- `FieldExtractorVisitor` extracts all field names from complex WHERE (AND/OR/nested)
- `isDummyCondition()` detects "1=1", "true", constant comparisons
- `isConstant()` identifies literals vs column references

**Then Implement:**
Create `AbstractRuleChecker` abstract class in `com.footstone.sqlguard.validator.rule` package implementing RuleChecker with:

**Protected utility methods:**
- `protected Expression extractWhere(Statement stmt)` - using instanceof checks for Select/Update/Delete, casting and calling getWhere()
- `protected String extractTableName(Statement stmt)` - traversing FromItem to get table name
- `protected Set<String> extractFields(Expression expr)` - using FieldExtractorVisitor
- `protected boolean isDummyCondition(Expression expr)` - checking for dummy patterns ("1=1", constant equality)
- `protected boolean isConstant(Expression expr)` - detecting Value nodes vs Column nodes

**Inner class FieldExtractorVisitor:**
- Extends `ExpressionVisitorAdapter` from JSqlParser
- Overrides `visit(Column column)` to collect column names into `Set<String>`
- Provides `getFields()` method returning collected set
- Handles table prefixes by removing them (e.g., "user.id" → "id")

**Constraints:**
- Use JSqlParser 4.6 AST types (Statement, Expression, Column, etc.) from Phase 1 Task 1.4
- Utility methods reduce code duplication across all rule checkers
- null-safe handling: return null/empty set for null inputs
- isDummyCondition() should detect common patterns: "1=1", "1 = 1", "'1'='1'", "true", constant numeric equality

### Step 3: RuleCheckerOrchestrator TDD
**Test First:**
Write test class `RuleCheckerOrchestratorTest` covering:
- Orchestrator with no checkers returns passed ValidationResult
- Single enabled checker adds violation correctly
- Multiple checkers add violations independently
- Disabled checker is skipped (isEnabled returns false)
- Final risk level is max of all violations (MEDIUM + CRITICAL = CRITICAL)
- Orchestrator preserves checker execution order

**Then Implement:**
Create `RuleCheckerOrchestrator` class in `com.footstone.sqlguard.validator.rule` package with:

**Constructor:**
- Accepts `List<RuleChecker> checkers` parameter
- Stores as final field

**Method: `void orchestrate(SqlContext context, ValidationResult result)`**
- Iterate through checkers list
- For each checker:
  - Call `checker.isEnabled()`
  - If enabled, call `checker.check(context, result)`
  - Continue through all enabled checkers (no short-circuit)
- Violation aggregation handled by ValidationResult.addViolation() from Task 1.2

**Constraints:**
- Implements Chain of Responsibility pattern
- Does not short-circuit on first violation (collects all issues)
- Respects checker order (allows priority-based execution in future)
- Final risk level automatically aggregates to highest via ValidationResult

### Step 4: Integration Testing
**Write integration test `RuleCheckerIntegrationTest`** with:

**Mock checker implementations:**
- `MockChecker1` - adds LOW violation when enabled
- `MockChecker2` - adds HIGH violation when enabled
- `MockChecker3` - disabled (isEnabled returns false)

**Test scenarios:**
1. **Full orchestration:** Create orchestrator with all three checkers, call orchestrate with test SqlContext, verify result contains violations from checker1 and checker2 only (checker3 skipped), verify final risk level is HIGH (max of LOW and HIGH)

2. **Enabled/disabled toggling:** Disable checker1, re-run orchestration, verify only checker2 violation present, verify risk level is HIGH

3. **Empty checker list:** Create orchestrator with empty list, verify returns passed result

4. **Execution order preservation:** Create 5 mock checkers adding violations in sequence, verify violations appear in same order in result.violations list

5. **Multiple violations per checker:** Create checker that adds 3 violations (LOW, MEDIUM, HIGH), verify all appear in result, verify risk level aggregates to CRITICAL if any checker adds CRITICAL

**Verification:**
- Run `mvn test` ensuring all rule framework tests pass
- Verify Google Java Style compliance via `mvn checkstyle:check`
- Confirm test coverage includes all framework components

**Constraints:**
- This framework is used by ALL subsequent rule checkers (Tasks 2.2-2.12)
- Integration tests validate orchestration, enabled/disabled toggling, violation aggregation
- Mock implementations demonstrate how actual checkers will integrate

## Expected Output
- **RuleChecker interface:** Defining check contract for all validation rules
- **CheckerConfig base class:** Common configuration for enabled flag
- **AbstractRuleChecker:** Base class with utility methods (extractWhere, extractTableName, FieldExtractorVisitor, isDummyCondition, isConstant)
- **RuleCheckerOrchestrator:** Managing checker execution and violation aggregation
- **Integration tests:** Verifying orchestration, toggling, aggregation
- **Success Criteria:** All tests pass, framework ready for checker implementations

**File Locations:**
- Interface/Classes: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/`
- Tests: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_02_Validation_Engine/Task_2_1_Rule_Checker_Framework_Interfaces.md`

Follow .apm/guides/Memory_Log_Guide.md instructions.
