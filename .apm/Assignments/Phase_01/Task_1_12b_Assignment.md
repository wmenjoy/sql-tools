---
task_ref: "Task 1.12b - Dangerous Operations Examples"
agent_assignment: "Agent_Dangerous_Operations"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_12b_Dangerous_Operations_Examples.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Dangerous Operations Examples

## Task Reference
Implementation Plan: **Task 1.12b - Dangerous Operations Examples** assigned to **Agent_Dangerous_Operations**

## Context from Dependencies
Based on your completed Tasks 1.5-1.7 implementations, use the following Checkers you created:
- **DdlOperationChecker** (Task 1.5): Detects DDL operations (CREATE/ALTER/DROP/TRUNCATE)
- **DangerousFunctionChecker** (Task 1.6): Detects dangerous functions (load_file, sys_exec, sleep, benchmark, etc.)
- **CallStatementChecker** (Task 1.7): Detects stored procedure calls (CALL/EXECUTE/EXEC)

Create realistic example SQL patterns that demonstrate each Checker's detection capabilities for Scanner CLI validation.

## Objective
Create realistic bad/good example mapper files demonstrating Dangerous Operations Checker behavior (DdlOperation, DangerousFunction, CallStatement), enabling developers to understand prohibited operations and validate Scanner CLI detection.

## Detailed Instructions
Complete all items in one response:

- **Create BAD example mapper files** in `examples/src/main/resources/mappers/bad/`:
  - `DdlOperationMapper.xml`: 2-3 SQL examples with DDL operations from your Task 1.5 test cases
  - `DangerousFunctionMapper.xml`: 2-3 SQL examples with dangerous functions from your Task 1.6 test cases (demonstrate nested function detection)
  - `CallStatementMapper.xml`: 2-3 SQL examples with stored procedure calls from your Task 1.7 test cases

- **Create GOOD example mapper files** in `examples/src/main/resources/mappers/good/` with same naming:
  - Corrected versions of each BAD example showing proper SQL without violations
  - DdlOperation GOOD examples should show normal DML operations
  - DangerousFunction GOOD examples should use safe functions (MAX, SUM, COUNT, etc.)
  - CallStatement GOOD examples should use direct SQL instead of procedures
  - Include XML comments explaining why GOOD examples are safe

- **Ensure realistic SQL patterns**:
  - Match patterns from your Checker test cases for consistency
  - DangerousFunctionMapper.xml should demonstrate nested function detection (key feature of Task 1.6)
  - Use realistic table/column names (users, orders, products)
  - Include XML comments explaining why each BAD example is dangerous
  - All SQL should be valid MyBatis XML mapper syntax

- **Verify Scanner CLI detection**:
  - Run: `mvn -pl sql-scanner-cli exec:java -Dexec.args="scan --source-dirs=examples/src/main/resources"`
  - Confirm all 3 Dangerous Operations Checkers report violations in bad examples
  - Confirm no violations in good examples
  - Document verification results in Memory Log

## Expected Output
- **Deliverables:**
  - 6 mapper XML files total (3 bad + 3 good) in examples/src/main/resources/mappers/
  - Each file contains 2-3 realistic SQL examples
  - DangerousFunctionMapper.xml demonstrates nested function detection
  - All examples verified detectable by SQL Scanner CLI
  - Zero false positives/negatives

- **Success Criteria:**
  - BAD examples trigger corresponding Checker violations
  - GOOD examples pass validation without violations
  - XML comments explain prohibited operations and safe alternatives
  - Scanner CLI verification documented
  - Examples match test case patterns from Tasks 1.5-1.7

- **File Locations:**
  - BAD examples: `examples/src/main/resources/mappers/bad/DdlOperationMapper.xml`, etc.
  - GOOD examples: `examples/src/main/resources/mappers/good/DdlOperationMapper.xml`, etc.

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_12b_Dangerous_Operations_Examples.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
