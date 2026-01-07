---
task_ref: "Task 1.6 - DangerousFunctionChecker Implementation"
agent_assignment: "Agent_Dangerous_Operations"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_6_DangerousFunctionChecker_Implementation.md"
execution_type: "multi-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: DangerousFunctionChecker Implementation

## Task Reference
Implementation Plan: **Task 1.6 - DangerousFunctionChecker Implementation** assigned to **Agent_Dangerous_Operations**

## Objective
Implement DangerousFunctionChecker to detect and block dangerous database functions (load_file, sys_exec, sleep, etc.) that enable file operations, OS command execution, and DoS attacks, using recursive AST traversal for complete detection.

## Detailed Instructions
Complete in 6 exchanges, one step per response. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Design Function Extraction Strategy
Analyze JSqlParser Expression hierarchy to identify all locations where Function objects can appear:
- Key Expression types to handle:
  - Column (may contain functions in computed columns)
  - BinaryExpression including AndExpression, OrExpression (functions in conditions)
  - CaseExpression (functions in WHEN/THEN/ELSE clauses)
  - SubSelect (functions in subquery expressions)
  - Function arguments themselves (nested functions)
- Plan recursive ExpressionVisitor approach to traverse entire AST and collect all Function instances
- Document the extraction strategy with example SQL showing functions in different locations

### Step 2: Implement DangerousFunctionChecker with Recursive Function Extraction
Create checker with recursive ExpressionVisitor:
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DangerousFunctionChecker.java`
- Extend AbstractRuleChecker using template method pattern
- Create inner ExpressionVisitor implementation to traverse Expression trees recursively:
  - Implement visit methods: visit(Column), visit(BinaryExpression), visit(Function), visit(CaseExpression), visit(SubSelect)
  - Use JSqlParser Visitor pattern for AST traversal
- Add recursion safety: Maintain Set<Expression> visitedSet to prevent infinite loops on circular references
- Override visitSelect/visitUpdate/visitDelete to extract all Expression instances:
  - From SelectItem (SELECT clause)
  - From WHERE clause conditions
  - From HAVING clause
  - From ORDER BY expressions
- Apply visitor to all extracted expressions and collect Function.getName() from all found Function objects
- Performance note: ExpressionVisitor traversal adds ~2-3ms for complex queries with deep nesting

### Step 3: Add Denied Function Matching Logic
Implement denied function validation:
- Extract function names using Function.getName() from all collected Function objects
- Implement case-insensitive comparison between extracted function names and config.deniedFunctions list
- Convert both function name and config list to lowercase for matching
- Add CRITICAL violation for each match with function name in message (e.g., "Detected dangerous function: load_file")

### Step 4: Write Comprehensive Tests
Create test class with ≥18 tests covering MySQL (load_file/sleep), Oracle (sys_exec), PostgreSQL dangerous functions:
- Location: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/DangerousFunctionCheckerTest.java`
- PASS tests (≥5): safe functions like MAX/SUM/CONCAT/COUNT, normal queries without dangerous functions
- FAIL tests (≥10): load_file, into_outfile, sys_exec, sys_eval, sleep, benchmark in various positions:
  - SELECT clause: `SELECT load_file('/etc/passwd')`
  - WHERE clause: `WHERE sleep(5) = 0`
  - Nested in other functions: `WHERE CONCAT(load_file('/etc/passwd'), 'x')`
  - Different case variations: `LOAD_FILE`, `Load_File`, `load_FILE`
- 边界 tests (≥3): empty deniedFunctions allows all functions, nested functions (function inside function), function in subquery

### Step 5: Create DangerousFunctionConfig.java
Configuration class implementation:
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DangerousFunctionConfig.java`
- Extend CheckerConfig base class
- Fields: enabled (default true), violationStrategy (default BLOCK), List<String> deniedFunctions
- Default deniedFunctions list includes: load_file, into_outfile, into_dumpfile, sys_exec, sys_eval, sleep, benchmark
- Store function names in lowercase for case-insensitive matching

### Step 6: Write User Documentation
Complete rule documentation:
- Location: `docs/user-guide/rules/dangerous-function.md`
- Follow no-where-clause.md template structure
- Explain each dangerous function's risk:
  - load_file: Arbitrary file read from server filesystem
  - sys_exec/sys_eval: OS command execution
  - sleep/benchmark: DoS attacks via query delay
  - into_outfile/into_dumpfile: File write operations
- BAD examples: Show each function in different contexts (SELECT, WHERE, nested)
- GOOD examples: Safe functions (MAX, SUM, CONCAT, etc.)
- Configuration section: Show how to customize deniedFunctions list for specific database environments

## Expected Output
- **Deliverables:**
  - DangerousFunctionChecker.java with recursive ExpressionVisitor implementation (≥18 passing tests)
  - DangerousFunctionConfig.java with customizable deniedFunctions list
  - docs/user-guide/rules/dangerous-function.md explaining per-function risks
  - Recursive AST traversal design documentation

- **Success Criteria:**
  - All tests passing (≥18 tests: PASS≥5, FAIL≥10, 边界≥3)
  - Detects functions in all Expression locations (SELECT, WHERE, HAVING, ORDER BY, nested)
  - Case-insensitive function name matching
  - Recursion safety via visitedSet (no infinite loops)
  - Performance acceptable (~2-3ms overhead for complex queries)
  - Default deniedFunctions: load_file, into_outfile, into_dumpfile, sys_exec, sys_eval, sleep, benchmark

- **File Locations:**
  - Implementation: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DangerousFunctionChecker.java`
  - Config: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DangerousFunctionConfig.java`
  - Tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/DangerousFunctionCheckerTest.java`
  - Documentation: `docs/user-guide/rules/dangerous-function.md`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_6_DangerousFunctionChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
