---
task_ref: "Task 1.12a - SQL Injection Examples"
agent_assignment: "Agent_SQL_Injection"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_12a_SQL_Injection_Examples.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: SQL Injection Examples

## Task Reference
Implementation Plan: **Task 1.12a - SQL Injection Examples** assigned to **Agent_SQL_Injection**

## Context from Dependencies
Based on your completed Tasks 1.1-1.4 implementations, use the following Checkers you created:
- **MultiStatementChecker** (Task 1.1): Detects multi-statement injection via semicolons
- **SetOperationChecker** (Task 1.2): Detects UNION/MINUS/EXCEPT/INTERSECT set operations
- **SqlCommentChecker** (Task 1.3): Detects SQL comments (--, /* */, #)
- **IntoOutfileChecker** (Task 1.4): Detects MySQL file write operations (INTO OUTFILE/DUMPFILE)

Create realistic example SQL patterns that demonstrate each Checker's detection capabilities for Scanner CLI validation.

## Objective
Create realistic bad/good example mapper files demonstrating SQL Injection Checker behavior (MultiStatement, SetOperation, SqlComment, IntoOutfile), enabling developers to understand attack patterns and validate Scanner CLI detection.

## Detailed Instructions
Complete all items in one response:

- **Create BAD example mapper files** in `examples/src/main/resources/mappers/bad/`:
  - `MultiStatementMapper.xml`: 2-3 SQL examples with multi-statement injection patterns from your Task 1.1 test cases
  - `SetOperationMapper.xml`: 2-3 SQL examples with UNION/set operation attacks from your Task 1.2 test cases
  - `SqlCommentMapper.xml`: 2-3 SQL examples with comment-based injection from your Task 1.3 test cases
  - `IntoOutfileMapper.xml`: 2-3 SQL examples with file write operations from your Task 1.4 test cases

- **Create GOOD example mapper files** in `examples/src/main/resources/mappers/good/` with same naming:
  - Corrected versions of each BAD example showing proper SQL without violations
  - Include XML comments explaining why GOOD examples are safe

- **Ensure realistic SQL patterns**:
  - Match patterns from your Checker test cases for consistency
  - Use realistic table/column names (users, orders, products)
  - Include XML comments explaining why each BAD example is dangerous
  - All SQL should be valid MyBatis XML mapper syntax

- **Verify Scanner CLI detection**:
  - Run: `mvn -pl sql-scanner-cli exec:java -Dexec.args="scan --source-dirs=examples/src/main/resources"`
  - Confirm all 4 SQL Injection Checkers report violations in bad examples
  - Confirm no violations in good examples
  - Document verification results in Memory Log

## Expected Output
- **Deliverables:**
  - 8 mapper XML files total (4 bad + 4 good) in examples/src/main/resources/mappers/
  - Each file contains 2-3 realistic SQL examples
  - All examples verified detectable by SQL Scanner CLI
  - Zero false positives (good examples don't trigger violations)
  - Zero false negatives (bad examples trigger expected violations)

- **Success Criteria:**
  - BAD examples trigger corresponding Checker violations
  - GOOD examples pass validation without violations
  - XML comments explain attack patterns and safe alternatives
  - Scanner CLI verification documented
  - Examples match test case patterns from Tasks 1.1-1.4

- **File Locations:**
  - BAD examples: `examples/src/main/resources/mappers/bad/MultiStatementMapper.xml`, etc.
  - GOOD examples: `examples/src/main/resources/mappers/good/MultiStatementMapper.xml`, etc.

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_12a_SQL_Injection_Examples.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
