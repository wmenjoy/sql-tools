---
task_ref: "Task 1.7 - CallStatementChecker Implementation"
agent_assignment: "Agent_Dangerous_Operations"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_7_CallStatementChecker_Implementation.md"
execution_type: "single-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: CallStatementChecker Implementation

## Task Reference
Implementation Plan: **Task 1.7 - CallStatementChecker Implementation** assigned to **Agent_Dangerous_Operations**

## Objective
Implement CallStatementChecker to detect stored procedure calls (CALL/EXECUTE/EXEC) that introduce opaque logic and potential permission escalation, using HIGH severity with WARN default to balance monitoring with legitimate use cases.

## Detailed Instructions
Complete all items in one response:

- Write comprehensive tests first (≥18 total tests covering MySQL CALL, Oracle EXECUTE, SQL Server EXEC syntaxes):
  - PASS tests (≥5): SELECT/INSERT/UPDATE/DELETE statements, function calls in expressions (e.g., `SELECT MAX(id)` - functions are different from procedures)
  - FAIL tests (≥10): MySQL CALL procedure(), Oracle EXECUTE procedure, SQL Server EXEC procedure, procedures with parameters (e.g., `CALL sp_user_create(1, 'test')`), nested procedure calls
  - 边界 tests (≥3): case variations (CALL/call/Call), procedure names with special characters, empty parameter lists

- Implement CallStatementChecker.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/CallStatementChecker.java`
  - Extend AbstractRuleChecker using template method pattern
  - Override check() method to detect stored procedure calls
  - Detection approach: Use instanceof check for Execute Statement type, OR parse SQL string for CALL/EXECUTE/EXEC keywords at statement start (case-insensitive)
  - **IMPORTANT**: Differentiate stored procedure calls from function calls:
    - Procedure: `CALL sp_user_create(1)` → DETECT
    - Function: `SELECT MAX(id)` → ALLOW (functions in expressions are not procedure calls)
  - Use HIGH violation level (not CRITICAL) since procedures may be legitimate architecture in some systems
  - Include procedure name in violation message (extract from Execute statement or SQL string)
  - Test class location: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/CallStatementCheckerTest.java`

- Create CallStatementConfig.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/CallStatementConfig.java`
  - Extend CheckerConfig base class
  - Standard fields: enabled (default true), violationStrategy (default WARN, not BLOCK)
  - **IMPORTANT**: Default strategy is WARN (not BLOCK) because stored procedures may be valid business logic in some architectures

- Write user documentation:
  - Location: `docs/user-guide/rules/call-statement.md`
  - Follow no-where-clause.md template structure
  - Explain stored procedure security concerns:
    - Opaque logic (difficult to analyze and audit)
    - Potential permission escalation (procedures execute with elevated privileges)
    - Hidden complexity (logic not visible in application code)
  - When procedures are acceptable vs problematic:
    - Acceptable: Legacy systems with extensive procedure-based architecture
    - Problematic: New applications where all logic should be in application layer
  - BAD examples: Unexpected procedure calls that should be refactored to SQL
  - GOOD examples: Direct SQL statements with logic in application layer
  - Note that default strategy is WARN to balance monitoring with legitimate use cases

## Expected Output
- **Deliverables:**
  - CallStatementChecker.java (production-ready, ≥18 passing tests)
  - CallStatementConfig.java with default WARN strategy
  - docs/user-guide/rules/call-statement.md explaining when procedures are acceptable
  - Multi-dialect test coverage (MySQL CALL, Oracle EXECUTE, SQL Server EXEC)

- **Success Criteria:**
  - All tests passing (≥18 tests: PASS≥5, FAIL≥10, 边界≥3)
  - Stored procedure calls correctly detected across all dialects
  - Function calls in expressions correctly allowed (no false positives)
  - HIGH violation level (not CRITICAL)
  - Procedure name included in violation message
  - Default configuration: enabled=true, violationStrategy=WARN

- **File Locations:**
  - Implementation: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/CallStatementChecker.java`
  - Config: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/CallStatementConfig.java`
  - Tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/CallStatementCheckerTest.java`
  - Documentation: `docs/user-guide/rules/call-statement.md`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_7_CallStatementChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
