---
task_ref: "Task 1.12c - Access Control Examples"
agent_assignment: "Agent_Access_Control"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_12c_Access_Control_Examples.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Access Control Examples

## Task Reference
Implementation Plan: **Task 1.12c - Access Control Examples** assigned to **Agent_Access_Control**

## Context from Dependencies
Based on your completed Tasks 1.8-1.11 implementations, use the following Checkers you created:
- **MetadataStatementChecker** (Task 1.8): Detects metadata disclosure (SHOW/DESCRIBE/USE)
- **SetStatementChecker** (Task 1.9): Detects session variable modification (SET statements)
- **DeniedTableChecker** (Task 1.10): Enforces table-level access control blacklist with wildcards
- **ReadOnlyTableChecker** (Task 1.11): Protects read-only tables from write operations

Create realistic example SQL patterns that demonstrate each Checker's detection capabilities for Scanner CLI validation.

## Objective
Create realistic bad/good example mapper files demonstrating Access Control Checker behavior (MetadataStatement, SetStatement, DeniedTable, ReadOnlyTable), enabling developers to understand access control violations and validate Scanner CLI detection.

## Detailed Instructions
Complete all items in one response:

- **Create BAD example mapper files** in `examples/src/main/resources/mappers/bad/`:
  - `MetadataStatementMapper.xml`: 2-3 SQL examples with metadata commands from your Task 1.8 test cases
  - `SetStatementMapper.xml`: 2-3 SQL examples with SET statements from your Task 1.9 test cases (demonstrate UPDATE...SET differentiation)
  - `DeniedTableMapper.xml`: 2-3 SQL examples with denied table access from your Task 1.10 test cases (demonstrate wildcard matching like sys_*)
  - `ReadOnlyTableMapper.xml`: 2-3 SQL examples with write operations on readonly tables from your Task 1.11 test cases (demonstrate wildcard patterns like history_*)

- **Create GOOD example mapper files** in `examples/src/main/resources/mappers/good/` with same naming:
  - Corrected versions of each BAD example showing proper SQL without violations
  - MetadataStatement GOOD examples should use INFORMATION_SCHEMA queries via SELECT
  - SetStatement GOOD examples should show UPDATE...SET column assignments (not SET statements)
  - DeniedTable GOOD examples should access allowed tables
  - ReadOnlyTable GOOD examples should show SELECT from readonly tables (reads are OK) or writes to normal tables
  - Include XML comments explaining why GOOD examples are safe

- **Ensure realistic SQL patterns**:
  - Match patterns from your Checker test cases for consistency
  - **DeniedTableMapper.xml MUST demonstrate wildcard matching** (sys_* patterns from Task 1.10)
  - **ReadOnlyTableMapper.xml MUST demonstrate wildcard patterns** (history_*, audit_* from Task 1.11)
  - **SetStatementMapper.xml MUST show UPDATE...SET differentiation** (critical feature from Task 1.9)
  - Use realistic table/column names (users, orders, products, sys_user, audit_log, history_orders)
  - Include XML comments explaining why each BAD example is dangerous
  - All SQL should be valid MyBatis XML mapper syntax

- **Verify Scanner CLI detection**:
  - Run: `mvn -pl sql-scanner-cli exec:java -Dexec.args="scan --source-dirs=examples/src/main/resources"`
  - Confirm all 4 Access Control Checkers report violations in bad examples
  - Confirm no violations in good examples
  - Document verification results in Memory Log

## Expected Output
- **Deliverables:**
  - 8 mapper XML files total (4 bad + 4 good) in examples/src/main/resources/mappers/
  - Each file contains 2-3 realistic SQL examples
  - DeniedTable/ReadOnlyTable examples demonstrate wildcard pattern matching
  - SetStatement examples show UPDATE...SET differentiation
  - All examples verified detectable by SQL Scanner CLI
  - Zero false positives/negatives

- **Success Criteria:**
  - BAD examples trigger corresponding Checker violations
  - GOOD examples pass validation without violations
  - Wildcard pattern demonstrations (sys_*, history_*) in DeniedTable/ReadOnlyTable examples
  - UPDATE...SET differentiation demonstrated in SetStatement examples
  - XML comments explain access control violations and safe alternatives
  - Scanner CLI verification documented
  - Examples match test case patterns from Tasks 1.8-1.11

- **File Locations:**
  - BAD examples: `examples/src/main/resources/mappers/bad/MetadataStatementMapper.xml`, etc.
  - GOOD examples: `examples/src/main/resources/mappers/good/MetadataStatementMapper.xml`, etc.

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_12c_Access_Control_Examples.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
