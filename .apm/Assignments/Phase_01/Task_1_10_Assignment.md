---
task_ref: "Task 1.10 - DeniedTableChecker Implementation"
agent_assignment: "Agent_Access_Control"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_10_DeniedTableChecker_Implementation.md"
execution_type: "multi-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: DeniedTableChecker Implementation

## Task Reference
Implementation Plan: **Task 1.10 - DeniedTableChecker Implementation** assigned to **Agent_Access_Control**

## Objective
Implement DeniedTableChecker to enforce table-level access control blacklist with wildcard pattern support (sys_* blocks sys_user, sys_config but NOT system), using recursive table extraction to detect denied tables in FROM/JOIN/subqueries/CTEs.

## Detailed Instructions
Complete in 6 exchanges, one step per response. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Design Table Extraction Strategy
Analyze JSqlParser query structure to identify all table locations:
- FROM clause tables
- JOIN clause tables (INNER JOIN, LEFT JOIN, RIGHT JOIN, etc.)
- Subqueries in SELECT/WHERE/FROM clauses
- CTEs (WITH clauses)
- Investigate JSqlParser TablesNamesFinder utility class as potential simpler alternative to custom FromItemVisitor
- Evaluate if TablesNamesFinder provides complete table extraction (including nested subqueries and CTEs)
- Document chosen approach: TablesNamesFinder vs custom FromItemVisitor with justification

### Step 2: Implement DeniedTableChecker with Recursive Table Extraction
Create checker with table extraction logic:
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DeniedTableChecker.java`
- Extend AbstractRuleChecker using template method pattern
- If using custom visitor: Create inner FromItemVisitor to traverse FromItem hierarchy recursively
- Override visitSelect/visitUpdate/visitDelete/visitInsert to extract FromItem and apply extraction logic
- For both approaches (TablesNamesFinder or custom visitor):
  - Collect table names from Table objects using Table.getName() (actual table name)
  - **CRITICAL**: Use Table.getName() NOT Table.getAlias() - aliases are not the actual table names
  - Also collect Table.getSchemaName() for schema-qualified references
- Handle table aliases correctly: `sys_user AS u` → extract "sys_user", not "u"

### Step 3: Add Wildcard Pattern Matching Logic
Implement pattern matching with correct wildcard semantics:
- **CRITICAL Wildcard Semantics**: sys_* should match sys_user, sys_config but NOT system
- Asterisk (*) matches within word boundary only, not arbitrary characters
- Convert SQL pattern to Java regex: sys_* → ^sys_[^_]*$ (NOT ^sys_.*$ which incorrectly matches system)
- Pattern explanation:
  - ^sys_ : Starts with "sys_"
  - [^_]* : Followed by zero or more non-underscore characters
  - $ : End of string
  - This matches sys_user, sys_config but NOT system (missing underscore), sys_user_detail (extra underscore)
- Compare extracted table names (without schema prefix) against each pattern in config.deniedTables
- Use case-insensitive matching via Pattern.matches()

### Step 4: Write Comprehensive Tests
Create test class with ≥18 tests covering MySQL, Oracle, PostgreSQL syntax:
- Location: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/DeniedTableCheckerTest.java`
- PASS tests (≥5): tables not in denied list, allowed tables, queries without denied tables
- FAIL tests (≥10):
  - Exact match: sys_user in denied list → FAIL
  - Wildcard match: sys_user matches sys_* pattern → FAIL
  - **Wildcard non-match**: system does NOT match sys_* pattern → PASS (critical boundary test)
  - Table in JOIN clause → FAIL if denied
  - Table in subquery → FAIL if denied
  - Table in CTE (WITH clause) → FAIL if denied
  - Multiple denied tables in same query → FAIL with all violations reported
  - Schema-qualified denied table (e.g., db.sys_user) → FAIL
- 边界 tests (≥3): table alias vs actual name (extract actual name, not alias), empty deniedTables allows all, case-insensitive matching

### Step 5: Create DeniedTableConfig.java
Configuration class implementation:
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DeniedTableConfig.java`
- Extend CheckerConfig base class
- Fields: enabled (default true), violationStrategy (default BLOCK), List<String> deniedTables (default empty list)
- Store patterns case-insensitively
- Document wildcard usage in JavaDoc with examples:
  - sys_* matches sys_user but NOT system
  - admin_* matches admin_users but NOT administrator

### Step 6: Write User Documentation
Complete rule documentation:
- Location: `docs/user-guide/rules/denied-table.md`
- Follow no-where-clause.md template structure
- Explain table-level access control use cases:
  - Protecting sensitive tables like sys_user, admin_config
  - Preventing access to system tables
- Wildcard pattern examples:
  - sys_* protects all system tables with sys_ prefix (sys_user, sys_config)
  - Does NOT match system (missing underscore after sys)
- BAD examples: Accessing denied tables directly, via JOIN, via subquery
- GOOD examples: Accessing allowed tables
- Configuration section: Show deniedTables list with both exact names and patterns
- Security note: This is defense-in-depth (database permissions are primary control)

## Expected Output
- **Deliverables:**
  - DeniedTableChecker.java with recursive table extraction (≥18 passing tests)
  - DeniedTableConfig.java with deniedTables list
  - docs/user-guide/rules/denied-table.md with wildcard semantics explanation
  - Table extraction strategy documentation (TablesNamesFinder vs FromItemVisitor decision)

- **Success Criteria:**
  - All tests passing (≥18 tests: PASS≥5, FAIL≥10, 边界≥3)
  - Tables extracted from all locations (FROM, JOIN, subqueries, CTEs)
  - Wildcard matching with correct semantics (sys_* matches sys_user but NOT system)
  - Table aliases handled correctly (extract actual name, not alias)
  - Schema-qualified table names supported
  - Case-insensitive matching
  - Default configuration: enabled=true, violationStrategy=BLOCK, deniedTables=[]

- **File Locations:**
  - Implementation: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DeniedTableChecker.java`
  - Config: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DeniedTableConfig.java`
  - Tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/DeniedTableCheckerTest.java`
  - Documentation: `docs/user-guide/rules/denied-table.md`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_10_DeniedTableChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
