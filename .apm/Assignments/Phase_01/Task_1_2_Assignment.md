---
task_ref: "Task 1.2 - SetOperationChecker Implementation"
agent_assignment: "Agent_SQL_Injection"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_2_SetOperationChecker_Implementation.md"
execution_type: "single-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: SetOperationChecker Implementation

## Task Reference
Implementation Plan: **Task 1.2 - SetOperationChecker Implementation** assigned to **Agent_SQL_Injection**

## Objective
Implement SetOperationChecker to detect and control SQL set operations (UNION, MINUS, EXCEPT, INTERSECT) that enable data exfiltration and injection attacks, with configurable allowlist for legitimate use cases.

## Detailed Instructions
Complete all items in one response:

- Write comprehensive tests first (≥18 total tests covering MySQL UNION, Oracle MINUS/INTERSECT, PostgreSQL EXCEPT, SQL Server dialects):
  - PASS tests (≥5): normal SELECT/JOIN/subqueries without set operations, queries with allowed operations when configured
  - FAIL tests (≥10): UNION, UNION ALL, MINUS, EXCEPT, INTERSECT injection attacks, nested set operations, various combinations
  - 边界 tests (≥3): empty allowedOperations list blocks all set operations, populated allowedOperations allows only specific types, case-insensitive matching

- Implement SetOperationChecker.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SetOperationChecker.java`
  - Extend AbstractRuleChecker using template method pattern
  - Override visitSelect() method to detect SetOperationList in Select statement using JSqlParser AST
  - Detection logic: Check Select.getSetOperationList() for set operations (UNION, MINUS, EXCEPT, INTERSECT)
  - Validation: Compare detected operation type against config.allowedOperations list
  - Empty allowedOperations list = block all set operations (default secure behavior)
  - Add CRITICAL violation with specific operation type in message (e.g., "Detected UNION set operation")
  - Test class location: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/SetOperationCheckerTest.java`

- Create SetOperationConfig.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SetOperationConfig.java`
  - Extend CheckerConfig base class
  - Fields: enabled (default true), violationStrategy (default BLOCK), List<String> allowedOperations (default empty list)
  - Validate operation names in setter to prevent typos (valid values: UNION, MINUS, EXCEPT, INTERSECT)
  - Store operation names case-insensitively for matching

- Write user documentation:
  - Location: `docs/user-guide/rules/set-operation.md`
  - Follow no-where-clause.md template structure
  - Include separate BAD examples for each operation type demonstrating injection scenarios:
    - UNION injection (data exfiltration from other tables)
    - MINUS injection (Oracle-specific attacks)
    - EXCEPT injection (PostgreSQL-specific attacks)
    - INTERSECT injection (data correlation attacks)
  - GOOD examples: normal queries without set operations, allowed operations when configured in allowedOperations
  - Configuration section: Show YAML example with allowedOperations: [UNION, INTERSECT] for selective permission

## Expected Output
- **Deliverables:**
  - SetOperationChecker.java (production-ready, ≥18 passing tests)
  - SetOperationConfig.java with allowedOperations list support
  - docs/user-guide/rules/set-operation.md with per-operation injection examples
  - Multi-dialect test coverage (MySQL, Oracle, PostgreSQL, SQL Server)

- **Success Criteria:**
  - All tests passing (≥18 tests: PASS≥5, FAIL≥10, 边界≥3)
  - Empty allowedOperations blocks all set operations
  - Populated allowedOperations allows specific operation types only
  - Per-dialect operation support (UNION for MySQL, MINUS for Oracle, EXCEPT for PostgreSQL)
  - Default configuration: enabled=true, violationStrategy=BLOCK, allowedOperations=[]

- **File Locations:**
  - Implementation: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SetOperationChecker.java`
  - Config: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SetOperationConfig.java`
  - Tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/SetOperationCheckerTest.java`
  - Documentation: `docs/user-guide/rules/set-operation.md`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_2_SetOperationChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
