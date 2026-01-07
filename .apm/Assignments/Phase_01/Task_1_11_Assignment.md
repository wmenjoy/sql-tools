---
task_ref: "Task 1.11 - ReadOnlyTableChecker Implementation"
agent_assignment: "Agent_Access_Control"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_11_ReadOnlyTableChecker_Implementation.md"
execution_type: "single-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: ReadOnlyTableChecker Implementation

## Task Reference
Implementation Plan: **Task 1.11 - ReadOnlyTableChecker Implementation** assigned to **Agent_Access_Control**

## Objective
Implement ReadOnlyTableChecker to protect read-only tables (audit logs, historical records) from write operations using wildcard pattern matching, detecting write attempts at INSERT/UPDATE/DELETE operation level.

## Detailed Instructions
Complete all items in one response:

- Write comprehensive tests first (≥18 total tests covering MySQL, Oracle, PostgreSQL):
  - PASS tests (≥5): SELECT from readonly tables (reading is allowed), write operations to non-readonly tables, UPDATE/DELETE with readonly table in WHERE clause only but not as target table (e.g., `UPDATE user SET status = 1 WHERE id IN (SELECT user_id FROM audit_log)` - audit_log in WHERE is OK)
  - FAIL tests (≥10): INSERT into readonly table, UPDATE readonly table, DELETE from readonly table, wildcard match (e.g., audit_log matches history_* pattern), write operations on multiple readonly tables
  - 边界 tests (≥3): schema-qualified names (e.g., db.audit_log), case-insensitive matching, empty readonlyTables allows all writes

- Implement ReadOnlyTableChecker.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/ReadOnlyTableChecker.java`
  - Extend AbstractRuleChecker using template method pattern
  - Override visitInsert/visitUpdate/visitDelete to extract target table name:
    - INSERT: Use Insert.getTable() to get target table
    - UPDATE: Use Update.getTable() to get target table
    - DELETE: Use Delete.getTable() to get target table
  - **IMPORTANT**: Only check the target table of write operations, NOT tables in WHERE clause or subqueries
  - Apply wildcard pattern matching logic against config.readonlyTables:
    - Reuse DeniedTableChecker pattern matching approach
    - Convert * wildcard to regex (sys_* → ^sys_[^_]*$)
    - Case-insensitive matching
  - Use HIGH violation level (not CRITICAL - data modification vs structure)
  - Include table name and operation type in violation message (e.g., "Write operation INSERT on read-only table: audit_log")
  - Test class location: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/ReadOnlyTableCheckerTest.java`

- Create ReadOnlyTableConfig.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/ReadOnlyTableConfig.java`
  - Extend CheckerConfig base class
  - Fields: enabled (default true), violationStrategy (default BLOCK), List<String> readonlyTables (default empty list)
  - Example default patterns in documentation: audit_log, history_*
  - Document wildcard usage in JavaDoc

- Write user documentation:
  - Location: `docs/user-guide/rules/readonly-table.md`
  - Follow no-where-clause.md template structure
  - Explain read-only table protection scenarios:
    - Audit logs (must be immutable)
    - Historical records (cannot be modified)
    - Reference data (read-only lookup tables)
  - Wildcard pattern examples:
    - history_* protects all historical tables (history_users, history_orders)
    - audit_* protects all audit tables
  - BAD examples: Writing to readonly tables (INSERT/UPDATE/DELETE)
  - GOOD examples: Reading readonly tables (SELECT), writing to normal tables
  - Configuration section: Show readonlyTables list with patterns
  - Note: Readonly protection is for application-level safety (database permissions should also enforce)

## Expected Output
- **Deliverables:**
  - ReadOnlyTableChecker.java (production-ready, ≥18 passing tests)
  - ReadOnlyTableConfig.java with readonlyTables list
  - docs/user-guide/rules/readonly-table.md explaining application-level safety rationale
  - Wildcard pattern matching support (reuse DeniedTableChecker logic)

- **Success Criteria:**
  - All tests passing (≥18 tests: PASS≥5, FAIL≥10, 边界≥3)
  - Write operations on readonly tables detected (INSERT/UPDATE/DELETE)
  - Read operations on readonly tables allowed (SELECT)
  - Tables in WHERE clause not incorrectly flagged (only target tables checked)
  - Wildcard matching works (history_* matches history_users)
  - Schema-qualified names supported
  - Case-insensitive matching
  - Default configuration: enabled=true, violationStrategy=BLOCK, readonlyTables=[]

- **File Locations:**
  - Implementation: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/ReadOnlyTableChecker.java`
  - Config: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/ReadOnlyTableConfig.java`
  - Tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/ReadOnlyTableCheckerTest.java`
  - Documentation: `docs/user-guide/rules/readonly-table.md`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_11_ReadOnlyTableChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
