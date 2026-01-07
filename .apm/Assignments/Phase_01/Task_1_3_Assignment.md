---
task_ref: "Task 1.3 - SqlCommentChecker Implementation"
agent_assignment: "Agent_SQL_Injection"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_3_SqlCommentChecker_Implementation.md"
execution_type: "multi-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: SqlCommentChecker Implementation

## Task Reference
Implementation Plan: **Task 1.3 - SqlCommentChecker Implementation** assigned to **Agent_SQL_Injection**

## Objective
Implement SqlCommentChecker to detect SQL comments (--, /* */, #) used to bypass security detection and hide malicious code, with special handling for Oracle optimizer hints (/*+ INDEX */).

## Detailed Instructions
Complete in 6 exchanges, one step per response. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Research JSqlParser Comment Extraction API
Investigate JSqlParser capabilities for extracting comments from parsed SQL statements:
- Research approach (a): Check if Statement.toString() preserves comments in parsed output
- Research approach (b): Investigate CCJSqlParserUtil parsing options for comment retention
- Research approach (c): Examine TextBlock or metadata fields on Statement objects for comment access
- Research approach (d): Test SQL string pattern matching as fallback if JSqlParser doesn't expose comments
- Document findings: Determine whether JSqlParser API exposes single-line (--), multi-line (/* */), and MySQL (#) comments
- Output format: Document the correct approach for accessing comments (JSqlParser API vs SQL string parsing fallback strategy)

### Step 2: Implement SqlCommentChecker with Comment Detection Logic
Based on Step 1 findings, implement the checker:
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SqlCommentChecker.java`
- Extend AbstractRuleChecker using template method pattern
- Override check() method to extract comments from SQL using the approach identified in Step 1
- If JSqlParser API is unavailable, implement SQL string parsing with proper handling of:
  - String literal boundaries (', ", ``) - comments inside strings should be ignored
  - Escaped comment markers within strings (e.g., `'--'` is not a comment)
  - Comment precedence (string literals take priority over comment markers)
- Detection patterns: Detect presence of --, /* */, and # patterns outside string contexts
- Note: SQL string parsing fallback adds ~1-2ms overhead but ensures complete detection

### Step 3: Add Optimizer Hint Filtering Support
Implement allowHintComments configuration logic:
- Distinguish hint comments (starting with /*+ ) from regular multi-line comments
- When allowHintComments=true, permit Oracle-style hints (e.g., /*+ INDEX(users idx_email) */)
- Only block non-hint comments when allowHintComments=true
- Regular -- and # comments are always blocked regardless of allowHintComments setting

### Step 4: Write Comprehensive Tests
Create test class with ≥18 tests covering MySQL (#), Oracle (hints), PostgreSQL dialects:
- Location: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/SqlCommentCheckerTest.java`
- PASS tests (≥5): normal SQL without comments, string literals containing comment markers (e.g., `SELECT * FROM user WHERE name = '--test'`), optimizer hints when allowHintComments=true
- FAIL tests (≥10): single-line -- comments, multi-line /* */ comments, MySQL # comments, nested comments, comments in WHERE/SELECT/FROM clauses
- 边界 tests (≥3): comments at different SQL positions, only comments (no SQL), escaped comment markers in strings

### Step 5: Create SqlCommentConfig.java
Configuration class implementation:
- Location: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SqlCommentConfig.java`
- Extend CheckerConfig base class
- Fields: enabled (default true), violationStrategy (default BLOCK), boolean allowHintComments (default false)
- Follow established config pattern from Phase 2

### Step 6: Write User Documentation
Complete rule documentation:
- Location: `docs/user-guide/rules/sql-comment.md`
- Follow no-where-clause.md template structure
- Detailed explanations of 3 comment types:
  - Single-line -- comments (SQL standard)
  - Multi-line /* */ comments (SQL standard, Oracle hints)
  - MySQL # comments (MySQL-specific)
- Why each is dangerous: Bypass detection mechanisms, hide malicious code, comment out WHERE clauses
- BAD examples: Show each comment type being used to bypass security
- GOOD examples: Clean SQL without comments, optimizer hints when allowHintComments configured
- Configuration section: Show allowHintComments usage for legitimate optimization scenarios

## Expected Output
- **Deliverables:**
  - SqlCommentChecker.java (production-ready, ≥18 passing tests, handles 3 comment types)
  - SqlCommentConfig.java with allowHintComments boolean field
  - docs/user-guide/rules/sql-comment.md explaining security risks per comment type
  - Research documentation of JSqlParser comment extraction capabilities

- **Success Criteria:**
  - All tests passing (≥18 tests: PASS≥5, FAIL≥10, 边界≥3)
  - Detects all 3 comment types (--, /* */, #)
  - Properly handles string literals with comment markers (should pass)
  - Oracle optimizer hints work when allowHintComments=true
  - Performance acceptable (~1-2ms overhead if using string parsing fallback)
  - Default configuration: enabled=true, violationStrategy=BLOCK, allowHintComments=false

- **File Locations:**
  - Implementation: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SqlCommentChecker.java`
  - Config: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SqlCommentConfig.java`
  - Tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/SqlCommentCheckerTest.java`
  - Documentation: `docs/user-guide/rules/sql-comment.md`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_3_SqlCommentChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.
