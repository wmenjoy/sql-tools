---
task_ref: "Task 1.8 - MetadataStatementChecker Implementation"
agent_assignment: "Agent_Access_Control"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_8_MetadataStatementChecker_Implementation.md"
execution_type: "single-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: MetadataStatementChecker Implementation

## Task Reference
Implementation Plan: **Task 1.8 - MetadataStatementChecker Implementation** assigned to **Agent_Access_Control**

## Objective
Implement MetadataStatementChecker to detect metadata disclosure statements (SHOW/DESCRIBE/USE) that leak schema information to attackers, with configurable exemptions for legitimate admin tools.

## Detailed Instructions
Complete all items in one response:

- Write comprehensive tests first (≥18 total tests covering MySQL SHOW/DESCRIBE/USE, PostgreSQL \d commands, Oracle DESC variants):
  - PASS tests (≥5): SELECT/INSERT/UPDATE/DELETE (DML operations), allowed metadata types when configured in allowedStatements, INFORMATION_SCHEMA queries via SELECT (e.g., `SELECT * FROM INFORMATION_SCHEMA.TABLES` - proper metadata access method)
  - FAIL tests (≥10): SHOW TABLES, SHOW DATABASES, DESCRIBE table, DESC table, USE database, case variations, metadata commands with options (e.g., `SHOW TABLES LIKE 'user%'`)
  - 边界 tests (≥3): empty allowedStatements blocks all metadata commands, populated allowedStatements allows specific types, INFORMATION_SCHEMA queries should pass (not metadata commands)

- Implement MetadataStatementChecker.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MetadataStatementChecker.java`
  - Extend AbstractRuleChecker using template method pattern
  - Override check() method to parse SQL string for metadata keywords at statement start
  - Detection logic: Check for SHOW/DESCRIBE/DESC/USE keywords at statement start (trim whitespace and use case-insensitive check)
  - **IMPORTANT**: INFORMATION_SCHEMA queries via SELECT should pass validation (e.g., `SELECT * FROM INFORMATION_SCHEMA.TABLES` is proper way to query metadata)
  - Validation: Compare detected statement type against config.allowedStatements list
  - Empty allowedStatements list = block all metadata commands (default secure behavior)
  - Use HIGH violation level (not CRITICAL - metadata queries are less severe than data modification)
  - Include specific statement type in violation message (e.g., "Detected metadata statement: SHOW TABLES")
  - Test class location: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/MetadataStatementCheckerTest.java`

- Create MetadataStatementConfig.java:
  - Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MetadataStatementConfig.java`
  - Extend CheckerConfig base class
  - Fields: enabled (default true), violationStrategy (default WARN), List<String> allowedStatements (default empty list)
  - Support statement names: SHOW, DESCRIBE, USE
  - Validate statement names in setter

- Write user documentation:
  - Location: `docs/user-guide/rules/metadata-statement.md`
  - Follow no-where-clause.md template structure
  - Explain metadata leakage risks:
    - Attackers learn table structures for targeted attacks
    - Database names revealed for enumeration attacks
    - Schema information enables SQL injection exploitation
  - BAD examples: Each metadata type (SHOW TABLES, SHOW DATABASES, DESCRIBE, DESC, USE) showing information disclosure scenarios
  - GOOD examples: Querying INFORMATION_SCHEMA via SELECT (proper metadata access), normal DML operations
  - Configuration section: Show allowedStatements for legitimate admin tools (e.g., database management consoles)

## Expected Output
- **Deliverables:**
  - MetadataStatementChecker.java (production-ready, ≥18 passing tests)
  - MetadataStatementConfig.java with allowedStatements list
  - docs/user-guide/rules/metadata-statement.md explaining information disclosure risks
  - Proper differentiation between metadata commands and INFORMATION_SCHEMA queries

- **Success Criteria:**
  - All tests passing (≥18 tests: PASS≥5, FAIL≥10, 边界≥3)
  - All metadata commands detected (SHOW, DESCRIBE, DESC, USE)
  - INFORMATION_SCHEMA queries correctly allowed (no false positives)
  - Empty allowedStatements blocks all metadata commands
  - Populated allowedStatements allows specific types only
  - Default configuration: enabled=true, violationStrategy=WARN, allowedStatements=[]

- **File Locations:**
  - Implementation: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MetadataStatementChecker.java`
  - Config: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MetadataStatementConfig.java`
  - Tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/MetadataStatementCheckerTest.java`
  - Documentation: `docs/user-guide/rules/metadata-statement.md`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_8_MetadataStatementChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
