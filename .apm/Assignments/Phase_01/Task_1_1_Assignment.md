---
task_ref: "Task 1.1 - MultiStatementChecker Implementation"
agent_assignment: "Agent_SQL_Injection"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_1_MultiStatementChecker_Implementation.md"
execution_type: "single-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: MultiStatementChecker Implementation

## Task Reference
Implementation Plan: **Task 1.1 - MultiStatementChecker Implementation** assigned to **Agent_SQL_Injection**

## Objective
Implement MultiStatementChecker to detect and block SQL injection via multi-statement execution (e.g., `SELECT * FROM user; DROP TABLE user--`), providing CRITICAL-level protection against statement separator attacks.

## Detailed Instructions
Complete all items in one response:

- Write comprehensive tests first following TDD approach (≥18 total tests covering MySQL, Oracle, PostgreSQL dialects):
  - PASS tests (≥5): normal SQL with ending semicolon, single statements without violations
  - FAIL tests (≥10): multi-statement injection variants (e.g., `SELECT * FROM user; DROP TABLE user--`), semicolons in different positions, various attack patterns
  - 边界 tests (≥3): empty SQL, only semicolons, string literals with semicolons (should pass - semicolons inside strings are safe)

- Implement MultiStatementChecker.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MultiStatementChecker.java`
  - Extend AbstractRuleChecker and follow the template method pattern
  - Use SQL string parsing for semicolon detection (JSqlParser Statement AST doesn't preserve statement separators)
  - Detection logic: Find semicolons in SQL string, excluding:
    - Trailing semicolons (end of SQL)
    - Semicolons within string literals (use proper string boundary detection with ', ", ``)
  - Add CRITICAL violation when multiple statements detected
  - Test class location: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/MultiStatementCheckerTest.java`

- Create MultiStatementConfig.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MultiStatementConfig.java`
  - Extend CheckerConfig base class following established config pattern
  - Fields: enabled (default true), violationStrategy (default BLOCK)
  - Follow existing config class structure from Phase 2 implementations

- Write user documentation:
  - Location: `docs/user-guide/rules/multi-statement.md`
  - Follow the template structure from `docs/user-guide/rules/no-where-clause.md`:
    - Overview section explaining the rule
    - What It Detects section with technical details
    - Why Dangerous section explaining attack vectors
    - Examples section with BAD (multi-statement injection) and GOOD (single statements with trailing semicolon)
    - Configuration section showing YAML examples
    - Edge Cases section explaining trailing semicolon and string literal handling

## Expected Output
- **Deliverables:**
  - MultiStatementChecker.java (production-ready, ≥18 passing tests)
  - MultiStatementConfig.java with YAML binding support
  - docs/user-guide/rules/multi-statement.md following template structure
  - Test coverage >80%, multi-dialect support (MySQL, Oracle, PostgreSQL)

- **Success Criteria:**
  - All tests passing (≥18 tests: PASS≥5, FAIL≥10, 边界≥3)
  - Proper handling of trailing semicolons (should pass validation)
  - Proper handling of semicolons within string literals (should pass validation)
  - Multi-statement attacks correctly detected and blocked
  - Default configuration: enabled=true, violationStrategy=BLOCK

- **File Locations:**
  - Implementation: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MultiStatementChecker.java`
  - Config: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MultiStatementConfig.java`
  - Tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/MultiStatementCheckerTest.java`
  - Documentation: `docs/user-guide/rules/multi-statement.md`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_1_MultiStatementChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
