---
task_ref: "Task 1.4 - IntoOutfileChecker Implementation"
agent_assignment: "Agent_SQL_Injection"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_4_IntoOutfileChecker_Implementation.md"
execution_type: "single-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: IntoOutfileChecker Implementation

## Task Reference
Implementation Plan: **Task 1.4 - IntoOutfileChecker Implementation** assigned to **Agent_SQL_Injection**

## Objective
Implement IntoOutfileChecker to detect and block MySQL file write operations (SELECT INTO OUTFILE/DUMPFILE) that enable arbitrary file writes and data exfiltration, while permitting Oracle SELECT INTO variable syntax.

## Detailed Instructions
Complete all items in one response:

- Write comprehensive tests first (≥18 total tests covering MySQL INTO OUTFILE/DUMPFILE and Oracle INTO variable syntax):
  - PASS tests (≥5): normal SELECT statements, Oracle SELECT INTO variable (e.g., `SELECT id INTO v_id FROM user`), queries without file operations
  - FAIL tests (≥10): SELECT INTO OUTFILE with various paths (e.g., `/tmp/file.txt`, `C:\\data\\export.csv`), SELECT INTO DUMPFILE, different path formats (Unix paths, Windows paths), file operations in subqueries
  - 边界 tests (≥3): path injection attempts with special characters, encoded paths, INTO keyword without OUTFILE/DUMPFILE (should pass)

- Implement IntoOutfileChecker.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/IntoOutfileChecker.java`
  - Extend AbstractRuleChecker using template method pattern
  - Override visitSelect() method to detect file operations
  - Detection approach: Check Select.getIntoTables() for INTO clause, OR parse SQL string for INTO OUTFILE/INTO DUMPFILE keywords
  - **CRITICAL**: Differentiate MySQL file operations from Oracle INTO variable syntax:
    - MySQL: `SELECT * INTO OUTFILE '/tmp/file'` → BLOCK
    - Oracle: `SELECT id INTO v_id FROM user` → ALLOW (variable assignment, not file operation)
  - Detection heuristic: INTO OUTFILE/INTO DUMPFILE = file operation (block), INTO <identifier> without OUTFILE/DUMPFILE = variable (allow)
  - Add CRITICAL violation when file operations detected, include specific file path in message
  - Test various path formats: Unix paths (`/tmp/file`), Windows paths (`C:\\file`), relative paths
  - Test class location: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/IntoOutfileCheckerTest.java`

- Create IntoOutfileConfig.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/IntoOutfileConfig.java`
  - Extend CheckerConfig base class
  - Standard fields: enabled (default true), violationStrategy (default BLOCK)
  - No additional fields needed for this checker

- Write user documentation:
  - Location: `docs/user-guide/rules/into-outfile.md`
  - Follow no-where-clause.md template structure
  - Explain file write attack vectors:
    - Arbitrary file writes to system directories
    - Data exfiltration to attacker-controlled paths
    - Web shell creation via SELECT INTO OUTFILE
  - Why INTO OUTFILE/DUMPFILE are dangerous: Enable file system access from SQL layer, bypass application security
  - BAD examples: File write attacks with different paths (Unix, Windows), data exfiltration scenarios
  - GOOD examples: Normal SELECT statements, Oracle INTO variable usage (legitimate use case)
  - Note MySQL-specific risk and Oracle syntax difference in documentation

## Expected Output
- **Deliverables:**
  - IntoOutfileChecker.java (production-ready, ≥18 passing tests)
  - IntoOutfileConfig.java with standard enabled/violationStrategy fields
  - docs/user-guide/rules/into-outfile.md explaining attack vectors
  - Proper differentiation between MySQL file operations and Oracle variable syntax

- **Success Criteria:**
  - All tests passing (≥18 tests: PASS≥5, FAIL≥10, 边界≥3)
  - MySQL INTO OUTFILE/DUMPFILE correctly detected and blocked
  - Oracle INTO variable syntax correctly allowed (no false positives)
  - File path included in violation message
  - Multi-dialect test coverage (MySQL, Oracle)
  - Default configuration: enabled=true, violationStrategy=BLOCK

- **File Locations:**
  - Implementation: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/IntoOutfileChecker.java`
  - Config: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/IntoOutfileConfig.java`
  - Tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/IntoOutfileCheckerTest.java`
  - Documentation: `docs/user-guide/rules/into-outfile.md`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_4_IntoOutfileChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
