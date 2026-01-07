# Phase 01 Task Assignments - Complete Security Checker Implementation

**Total Tasks:** 11 Checkers (Parallel Execution)
**Created:** 2026-01-05
**Manager Agent:** Manager Agent 1

---

## Agent_SQL_Injection (4 Tasks)

### Task 1.1 - MultiStatementChecker Implementation
- **File:** [Task_1_1_Assignment.md](./Task_1_1_Assignment.md)
- **Type:** Single-step
- **Objective:** Detect multi-statement SQL injection (e.g., `SELECT *; DROP TABLE`)
- **Key Challenge:** Differentiate trailing semicolons from statement separators

### Task 1.2 - SetOperationChecker Implementation
- **File:** [Task_1_2_Assignment.md](./Task_1_2_Assignment.md)
- **Type:** Single-step
- **Objective:** Control UNION/MINUS/EXCEPT/INTERSECT operations
- **Key Feature:** Configurable allowlist for legitimate set operations

### Task 1.3 - SqlCommentChecker Implementation
- **File:** [Task_1_3_Assignment.md](./Task_1_3_Assignment.md)
- **Type:** Multi-step (6 steps)
- **Objective:** Detect SQL comments (--, /* */, #) used to bypass security
- **Key Challenge:** JSqlParser API research, Oracle hint filtering

### Task 1.4 - IntoOutfileChecker Implementation
- **File:** [Task_1_4_Assignment.md](./Task_1_4_Assignment.md)
- **Type:** Single-step
- **Objective:** Block MySQL file write operations (INTO OUTFILE/DUMPFILE)
- **Key Challenge:** Differentiate from Oracle INTO variable syntax

---

## Agent_Dangerous_Operations (3 Tasks)

### Task 1.5 - DdlOperationChecker Implementation
- **File:** [Task_1_5_Assignment.md](./Task_1_5_Assignment.md)
- **Type:** Single-step
- **Objective:** Block DDL operations (CREATE/ALTER/DROP/TRUNCATE) at application layer
- **Key Principle:** Schema changes via migration scripts only

### Task 1.6 - DangerousFunctionChecker Implementation
- **File:** [Task_1_6_Assignment.md](./Task_1_6_Assignment.md)
- **Type:** Multi-step (6 steps)
- **Objective:** Detect dangerous functions (load_file, sys_exec, sleep, etc.)
- **Key Challenge:** Recursive ExpressionVisitor for complete AST traversal

### Task 1.7 - CallStatementChecker Implementation
- **File:** [Task_1_7_Assignment.md](./Task_1_7_Assignment.md)
- **Type:** Single-step
- **Objective:** Detect stored procedure calls (CALL/EXECUTE/EXEC)
- **Severity:** HIGH with WARN default (procedures may be legitimate)

---

## Agent_Access_Control (4 Tasks)

### Task 1.8 - MetadataStatementChecker Implementation
- **File:** [Task_1_8_Assignment.md](./Task_1_8_Assignment.md)
- **Type:** Single-step
- **Objective:** Detect metadata disclosure (SHOW/DESCRIBE/USE)
- **Key Challenge:** Differentiate from INFORMATION_SCHEMA queries

### Task 1.9 - SetStatementChecker Implementation
- **File:** [Task_1_9_Assignment.md](./Task_1_9_Assignment.md)
- **Type:** Single-step
- **Objective:** Detect session variable modification (SET statements)
- **Key Challenge:** Differentiate from UPDATE...SET column assignments

### Task 1.10 - DeniedTableChecker Implementation
- **File:** [Task_1_10_Assignment.md](./Task_1_10_Assignment.md)
- **Type:** Multi-step (6 steps)
- **Objective:** Enforce table-level access control blacklist with wildcards
- **Key Challenge:** Wildcard semantics (sys_* matches sys_user but NOT system)

### Task 1.11 - ReadOnlyTableChecker Implementation
- **File:** [Task_1_11_Assignment.md](./Task_1_11_Assignment.md)
- **Type:** Single-step
- **Objective:** Protect read-only tables from write operations
- **Key Feature:** Wildcard pattern matching (history_*, audit_*)

---

## Execution Summary

**Parallel Execution Strategy:**
- All 11 tasks can execute simultaneously (no inter-dependencies)
- 3 agents working concurrently
- Multi-step tasks (1.3, 1.6, 1.10) require user confirmation between steps

**Success Criteria (Per Checker):**
1. ≥18 tests passing (PASS≥5, FAIL≥10, 边界≥3)
2. Test coverage >80%
3. Multi-dialect support (MySQL, Oracle, PostgreSQL minimum)
4. Performance <5ms per Checker
5. Config class with YAML binding
6. Complete rule documentation
7. Integration with DefaultSqlSafetyValidator

**Next Steps:**
1. Distribute Task Assignment files to Implementation Agents
2. Agents complete tasks and populate Memory Logs
3. Manager Agent reviews completed Memory Logs
4. After all 11 Checkers complete → Issue Tasks 1.12a/b/c (Examples)
5. After Examples complete → Issue Task 1.13 (Integration Tests)

---

**Memory Logs Location:** `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/`
