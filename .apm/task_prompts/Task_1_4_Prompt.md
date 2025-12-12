---
task_ref: "Task 1.4 - JSqlParser Integration Facade"
agent_assignment: "Agent_Core_Engine_Foundation"
memory_log_path: ".apm/Memory/Phase_01_Foundation/Task_1_4_JSqlParser_Integration_Facade.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: JSqlParser Integration Facade

## Task Reference
Implementation Plan: **Task 1.4 - JSqlParser Integration Facade** assigned to **Agent_Core_Engine_Foundation**

## Context from Dependencies
Based on your Task 1.1 work, use the project structure you established:
- Work in the `sql-guard-core` module
- **Use JSqlParser 4.6 dependency** from parent POM dependency management (version 4.9.0 was not available, changed to 4.6)
- Use the package `com.footstone.sqlguard.parser` for facade classes
- Follow TDD methodology with JUnit 5 and test with multiple database dialects

## Objective
Create unified facade for JSqlParser 4.x providing SQL parsing, AST extraction utilities, fail-fast error handling with configurable lenient mode, and LRU caching for parsed statements to optimize repeated SQL validation performance.

## Detailed Instructions
Complete this task in **5 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Basic Parsing TDD
**Test First:**
Write test class `JSqlParserFacadeTest` covering:
- Parse valid SELECT statement returns Statement
- Parse valid UPDATE/DELETE/INSERT statements
- Parse invalid SQL syntax throws SqlParseException
- Parse null SQL throws IllegalArgumentException
- Parse empty/whitespace-only SQL throws SqlParseException

**Then Implement:**
Note: JSqlParser 4.6 dependency is already configured in parent POM (Task 1.1 changed from 4.9.0 to 4.6 due to availability).

Create `JSqlParserFacade` class in `com.footstone.sqlguard.parser` package with:
- Constructor accepting `boolean lenientMode` (default false)
- Method: `parse(String sql)` that:
  - Validates sql not null/empty
  - Calls CCJSqlParserUtil.parse(sql) catching JSQLParserException
  - In fail-fast mode: throw custom SqlParseException (extends RuntimeException) wrapping original exception and including original SQL in message
  - In lenient mode: log warning via SLF4J and return null

### Step 2: Error Handling Strategy
**Implement custom `SqlParseException`** extending RuntimeException in parser package:
- Constructor accepting String message and Throwable cause
- Exception message format: "Failed to parse SQL: [first 100 chars of SQL]... - Reason: [JSQLParserException message]"

**Add to JSqlParserFacade:**
- Method: `boolean isLenientMode()` getter

**Write tests:**
- Fail-fast mode throws SqlParseException with descriptive message containing SQL snippet
- Lenient mode returns null and logs warning at WARN level (verify with Logback test appender)
- SqlParseException message includes both SQL and parse error reason
- Exception includes original JSQLParserException as cause for debugging

### Step 3: Extraction Utilities TDD
**Test First:**
Write tests for utility methods:
- extractWhere(SELECT) returns WHERE Expression
- extractWhere(UPDATE) returns WHERE
- extractWhere(DELETE) returns WHERE
- extractWhere(INSERT) returns null (no WHERE)
- extractTableName(SELECT) returns table name
- extractTableName(multi-table JOIN) returns first/primary table
- extractFields(Expression) returns Set containing all field names from WHERE

**Then Implement:**
Add utility methods in JSqlParserFacade:
- `extractWhere(Statement)` using instanceof checks and casting
- `extractTableName(Statement)` traversing FromItem to get table name
- `extractFields(Expression)` implementing custom FieldExtractorVisitor (extends ExpressionVisitorAdapter) that visits Column nodes and collects getColumnName() into Set<String>
- Add null-safe handling: return null/empty set for null inputs

**Write edge case tests:**
- Complex WHERE with AND/OR/NOT operators
- Nested subqueries
- Table aliases in field references

### Step 4: LRU Cache Implementation
**Implement LRU cache** using LinkedHashMap with accessOrder=true and size bound:
- Add constructor parameter `int cacheSize` (default 1000)
- Method: `parseCached(String sql)`:
  - Normalize SQL (trim, lowercase)
  - Check cache with get(normalizedSql)
  - If hit: increment hit counter and return cached Statement
  - If miss: call parse(sql), cache result with put(normalizedSql, stmt)
  - If cache exceeds size: remove eldest entry
  - Increment miss counter
- Method: `getCacheStatistics()` returning CacheStats object with hitCount, missCount, size, hitRate
- Method: `clearCache()`

**Write tests:**
- Cache hit returns same Statement instance
- Cache miss calls parse and caches result
- Cache size limit enforced (eldest evicted when exceeded)
- Cache statistics accurate
- clearCache() empties cache

### Step 5: Multi-Database Dialect Testing
Write integration test `JSqlParserMultiDialectTest` with SQL samples from different databases in src/test/resources:
- **MySQL syntax:** LIMIT, backtick identifiers, UNSIGNED
- **PostgreSQL syntax:** LIMIT/OFFSET, ::cast, dollar quotes
- **Oracle syntax:** ROWNUM, dual table, (+) outer join
- **SQL Server syntax:** TOP, square bracket identifiers, NOLOCK hint

**For each dialect:**
- Parse valid simple SELECT/UPDATE/DELETE/INSERT
- Parse complex SQL with subqueries and joins
- Verify extractWhere/extractTableName/extractFields work correctly

**Test edge cases:**
- Empty SQL, null SQL, whitespace-only SQL
- SQL comments (-- and /* */)
- Dynamic SQL with ? placeholders
- Very long SQL (10000+ chars)

Verify fail-fast exceptions for truly invalid syntax.
Run comprehensive tests ensuring JSqlParser handles expected SQL variations.

**Constraints:**
- JSqlParser is the core SQL parsing engine used throughout validation
- Facade pattern isolates rest of system from JSqlParser API changes
- Fail-fast mode (default) ensures invalid SQL is rejected immediately
- Lenient mode (for optional rules) logs warnings and continues
- LRU cache is critical for performance when same SQL template is validated repeatedly
- Cache key should be normalized SQL (trimmed, lowercase) to maximize hit rate
- Utility methods reduce code duplication across rule checkers
- Test with multiple database dialects since JSqlParser supports MySQL, PostgreSQL, Oracle, SQL Server syntax variations

## Expected Output
- **JSqlParserFacade class:** With parse(), extractWhere(), extractTableName(), extractFields() methods
- **SqlParseException:** Custom exception for parse failures
- **LRU Cache:** With statistics tracking and configurable size
- **Comprehensive tests:** Covering multiple database dialects and edge cases
- **Success Criteria:** All tests pass, cache statistics accurate, multi-dialect support verified

**File Locations:**
- Facade: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/main/java/com/footstone/sqlguard/parser/JSqlParserFacade.java`
- Exception: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/main/java/com/footstone/sqlguard/parser/SqlParseException.java`
- Tests: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/test/java/com/footstone/sqlguard/parser/`
- Test SQL files: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/test/resources/`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Foundation/Task_1_4_JSqlParser_Integration_Facade.md`

Follow .apm/guides/Memory_Log_Guide.md instructions.
