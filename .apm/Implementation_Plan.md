# SQL Guard – Implementation Plan

**Memory Strategy:** Dynamic-MD (directory structure with Markdown logs)
**Last Modification:** Initial creation by Setup Agent (2026-01-05) – Project decomposition complete, systematic review applied, APM artifact format applied
**Project Overview:** Implement 11 missing RuleCheckers to make SQL Guard an independent, complete SQL security framework. Deliverables include production-quality Checker implementations with TDD methodology, comprehensive multi-dialect testing, configuration classes, user documentation, example code, and integration tests. All work follows parallel execution strategy across 3 specialized agents (SQL_Injection, Dangerous_Operations, Access_Control) with automatic Manager Agent validation using 7-item acceptance checklist.

---

## Phase 1: Complete Security Checker Implementation

### Task 1.1 – MultiStatementChecker Implementation │ Agent_SQL_Injection

- **Objective:** Implement MultiStatementChecker to detect and block SQL injection via multi-statement execution (e.g., `SELECT * FROM user; DROP TABLE user--`), providing CRITICAL-level protection against statement separator attacks.
- **Output:** Production-ready MultiStatementChecker.java with ≥18 passing tests (PASS≥5, FAIL≥10, 边界≥3), MultiStatementConfig.java with YAML binding support, docs/user-guide/rules/multi-statement.md following template structure.
- **Guidance:** Exclude trailing semicolons and semicolons within string literals from detection. Use SQL string parsing for semicolon detection since JSqlParser Statement AST doesn't preserve statement separators. Follow AbstractRuleChecker template method pattern. Default configuration: enabled=true, violationStrategy=BLOCK. Test coverage must include MySQL, Oracle, PostgreSQL dialects.

- Write comprehensive tests first (PASS≥5: normal SQL with end semicolon, single statements; FAIL≥10: multi-statement injection variants, semicolons in different positions; 边界≥3: empty SQL, only semicolons, string literals with semicolons), covering MySQL, Oracle, PostgreSQL dialects
- Implement MultiStatementChecker extending AbstractRuleChecker, detecting semicolons in SQL string (excluding trailing semicolon and semicolons within string literals), adding CRITICAL violation when multiple statements detected
- Create MultiStatementConfig.java with enabled (default true) and violationStrategy (default BLOCK) fields, following established config pattern
- Write docs/user-guide/rules/multi-statement.md following no-where-clause.md template structure (Overview, What It Detects, Why Dangerous, Examples, Configuration, Edge Cases), including BAD examples (multi-statement injection) and GOOD examples (single statements with trailing semicolon)

### Task 1.2 – SetOperationChecker Implementation │ Agent_SQL_Injection

- **Objective:** Implement SetOperationChecker to detect and control SQL set operations (UNION, MINUS, EXCEPT, INTERSECT) that enable data exfiltration and injection attacks, with configurable allowlist for legitimate use cases.
- **Output:** Production-ready SetOperationChecker.java with ≥18 passing tests, SetOperationConfig.java supporting allowedOperations list configuration, docs/user-guide/rules/set-operation.md with per-operation injection examples.
- **Guidance:** Override visitSelect() to detect SetOperationList in Select statement. Empty allowedOperations list blocks all set operations (default behavior). Validate operation names in config setter to prevent typos. Cover MySQL (UNION), Oracle (MINUS/INTERSECT), PostgreSQL (EXCEPT), SQL Server dialects. Default: enabled=true, violationStrategy=BLOCK, allowedOperations=[].

- Write comprehensive tests (PASS≥5: normal SELECT/JOIN/subqueries without set operations; FAIL≥10: UNION, UNION ALL, MINUS, EXCEPT, INTERSECT injections, nested set operations; 边界≥3: empty allowedOperations blocks all, populated allowedOperations allows specific types), covering MySQL (UNION), Oracle (MINUS/INTERSECT), PostgreSQL (EXCEPT), SQL Server dialects
- Implement SetOperationChecker extending AbstractRuleChecker, override visitSelect() to detect SetOperationList in Select statement, check operation type against config.allowedOperations (empty list = block all), add CRITICAL violation with operation type in message
- Create SetOperationConfig.java with enabled, violationStrategy, and List<String> allowedOperations fields (default empty list blocks all set operations), validate operation names in setter
- Write docs/user-guide/rules/set-operation.md with separate BAD examples for each operation type (UNION/MINUS/EXCEPT/INTERSECT injection scenarios) and GOOD examples (normal queries, allowed operations when configured), include configuration example showing allowedOperations: [UNION, INTERSECT]

### Task 1.3 – SqlCommentChecker Implementation │ Agent_SQL_Injection

- **Objective:** Implement SqlCommentChecker to detect SQL comments (--, /* */, #) used to bypass security detection and hide malicious code, with special handling for Oracle optimizer hints (/*+ INDEX */).
- **Output:** Production-ready SqlCommentChecker.java with ≥18 passing tests covering 3 comment types and hint filtering, SqlCommentConfig.java with allowHintComments boolean field, docs/user-guide/rules/sql-comment.md explaining each comment type's security risk.
- **Guidance:** JSqlParser may not expose comments in Statement AST - research Statement.toString(), CCJSqlParserUtil options, TextBlock metadata first. If JSqlParser API unavailable, implement SQL string parsing fallback with proper string literal boundary handling (adds ~1-2ms overhead). Distinguish Oracle hints (/*+ ) from regular comments. Default: enabled=true, violationStrategy=BLOCK, allowHintComments=false.

1. Research JSqlParser comment extraction API - investigate Statement parsing options for extracting comments. Start with: (a) Statement.toString() to check if comments are preserved in parsed output, (b) CCJSqlParserUtil parsing options for comment retention, (c) TextBlock or metadata fields on Statement objects, (d) SQL string pattern matching as fallback if JSqlParser doesn't expose comments. Document correct approach for accessing single-line (--), multi-line (/* */), and MySQL (#) comments. Output format: document whether using JSqlParser API or SQL string parsing as fallback strategy
2. Implement SqlCommentChecker extending AbstractRuleChecker with comment detection logic - override check() method to extract comments from SQL using approach identified in step 1. If JSqlParser API unavailable, implement SQL string parsing with proper handling of: (a) string literal boundaries (', ", ``), (b) escaped comment markers within strings, (c) comment precedence (string literals take priority over comment markers). Detect presence of --, /* */, and # patterns outside string contexts. Note: SQL string parsing fallback adds ~1-2ms overhead but ensures complete detection
3. Add optimizer hint filtering support - implement allowHintComments config logic to permit Oracle-style hints (/*+ INDEX */), distinguish hint comments (starting with /*+ ) from regular multi-line comments, only block non-hint comments when allowHintComments=true
4. Write comprehensive tests (PASS≥5: normal SQL without comments, string literals containing comment markers, optimizer hints when allowed; FAIL≥10: single-line -- comments, multi-line /* */ comments, MySQL # comments, nested comments, comments in WHERE/SELECT/FROM clauses; 边界≥3: comments at different positions, only comments, escaped markers), covering MySQL (#), Oracle (hints), PostgreSQL dialects with ≥18 total test cases
5. Create SqlCommentConfig.java with enabled, violationStrategy, and boolean allowHintComments (default false) fields following established pattern
6. Write docs/user-guide/rules/sql-comment.md with detailed explanations of 3 comment types, why each is dangerous (bypass detection, hide malicious code), BAD examples for each type, GOOD examples (clean SQL, optimizer hints when configured), configuration section showing allowHintComments usage for legitimate optimization scenarios

### Task 1.4 – IntoOutfileChecker Implementation │ Agent_SQL_Injection

- **Objective:** Implement IntoOutfileChecker to detect and block MySQL file write operations (SELECT INTO OUTFILE/DUMPFILE) that enable arbitrary file writes and data exfiltration, while permitting Oracle SELECT INTO variable syntax.
- **Output:** Production-ready IntoOutfileChecker.java with ≥18 passing tests differentiating MySQL file operations from Oracle variable assignment, IntoOutfileConfig.java, docs/user-guide/rules/into-outfile.md explaining attack vectors.
- **Guidance:** Override visitSelect() to check Select.getIntoTables() or parse SQL string for INTO OUTFILE/INTO DUMPFILE keywords. Differentiate from Oracle INTO variable syntax (should pass validation). Include specific file path in violation message. Test various path formats ('/tmp/file', 'C:\\file'). Default: enabled=true, violationStrategy=BLOCK.

- Write comprehensive tests (PASS≥5: normal SELECT, Oracle SELECT INTO variable; FAIL≥10: SELECT INTO OUTFILE with various paths, SELECT INTO DUMPFILE, different path formats ('/tmp/file', 'C:\\file'), file operations in subqueries; 边界≥3: path injection attempts, encoded paths, INTO without OUTFILE), covering MySQL (INTO OUTFILE/DUMPFILE) and Oracle (INTO variable - should pass) dialects with ≥18 total test cases
- Implement IntoOutfileChecker extending AbstractRuleChecker, override visitSelect() to check Select.getIntoTables() or parse SQL string for INTO OUTFILE/INTO DUMPFILE keywords (differentiate from Oracle INTO variable syntax), add CRITICAL violation when file operations detected with specific file path in message
- Create IntoOutfileConfig.java with standard enabled (default true) and violationStrategy (default BLOCK) fields
- Write docs/user-guide/rules/into-outfile.md explaining file write attack vectors, why INTO OUTFILE/DUMPFILE are dangerous (arbitrary file writes, data exfiltration), BAD examples (file write attacks with different paths), GOOD examples (normal SELECT, Oracle INTO variable usage), note MySQL-specific risk and Oracle syntax difference

### Task 1.5 – DdlOperationChecker Implementation │ Agent_Dangerous_Operations

- **Objective:** Implement DdlOperationChecker to detect and block DDL operations (CREATE/ALTER/DROP/TRUNCATE) executed at application layer, enforcing production best practice that schema changes occur via controlled migration scripts.
- **Output:** Production-ready DdlOperationChecker.java with ≥18 passing tests covering all DDL types, DdlOperationConfig.java supporting allowedOperations exemptions for migration scripts, docs/user-guide/rules/ddl-operation.md explaining production DDL prohibition rationale.
- **Guidance:** Override check() using instanceof checks for CreateTable, AlterTable, Drop, Truncate Statement types. Empty allowedOperations list blocks all DDL (default). Support operation names: CREATE, ALTER, DROP, TRUNCATE with validation in setter. Document statementId pattern exemptions for migration scripts in docs. Default: enabled=true, violationStrategy=BLOCK, allowedOperations=[].

- Write comprehensive tests (PASS≥5: SELECT/INSERT/UPDATE/DELETE, DML operations, allowed DDL types when configured; FAIL≥10: CREATE TABLE, ALTER TABLE, DROP TABLE, TRUNCATE TABLE, CREATE INDEX, DROP INDEX, CREATE VIEW, DROP VIEW; 边界≥3: temporary tables, empty allowedOperations blocks all, populated allowedOperations allows specific types), covering MySQL, Oracle, PostgreSQL DDL syntax variants with ≥18 total test cases
- Implement DdlOperationChecker extending AbstractRuleChecker, override check() method to use instanceof checks for CreateTable, AlterTable, Drop, Truncate Statement types, validate against config.allowedOperations (empty list = block all DDL), add CRITICAL violation with specific DDL operation type in message
- Create DdlOperationConfig.java with enabled, violationStrategy, and List<String> allowedOperations fields (default empty list blocks all), support operation names: CREATE, ALTER, DROP, TRUNCATE, validate in setter
- Write docs/user-guide/rules/ddl-operation.md explaining why application-layer DDL is prohibited in production (schema changes should be via migration scripts, not runtime code), BAD examples for each DDL type, GOOD examples (DML operations), configuration section showing allowedOperations for migration script exemptions with statementId patterns

### Task 1.6 – DangerousFunctionChecker Implementation │ Agent_Dangerous_Operations

- **Objective:** Implement DangerousFunctionChecker to detect and block dangerous database functions (load_file, sys_exec, sleep, etc.) that enable file operations, OS command execution, and DoS attacks, using recursive AST traversal for complete detection.
- **Output:** Production-ready DangerousFunctionChecker.java with recursive ExpressionVisitor implementation, ≥18 passing tests covering nested function detection, DangerousFunctionConfig.java with customizable deniedFunctions list, docs/user-guide/rules/dangerous-function.md explaining per-function risks.
- **Guidance:** Implement recursive ExpressionVisitor to traverse Expression trees (Column, BinaryExpression, CaseExpression, SubSelect, Function arguments). Add recursion safety via Set<Expression> visitedSet. ExpressionVisitor adds ~2-3ms for complex queries. Extract Function.getName() case-insensitively. Default deniedFunctions: load_file, into_outfile, into_dumpfile, sys_exec, sys_eval, sleep, benchmark. Default: enabled=true, violationStrategy=BLOCK.

1. Design function extraction strategy - analyze JSqlParser Expression hierarchy to identify all locations where Function objects can appear. Key Expression types to handle: (a) Column - may contain functions in computed columns, (b) BinaryExpression (AndExpression, OrExpression) - functions in conditions, (c) CaseExpression - functions in WHEN/THEN/ELSE, (d) SubSelect - functions in subquery expressions, (e) Function arguments (nested functions). Plan recursive ExpressionVisitor approach to traverse entire AST and collect all Function instances
2. Implement DangerousFunctionChecker with recursive function extraction - extend AbstractRuleChecker, create inner ExpressionVisitor implementation to traverse Expression trees recursively using Visitor pattern (implement visit(Column), visit(BinaryExpression), visit(Function), etc.). Add recursion safety: maintain Set<Expression> visitedSet to prevent infinite loops on circular references. Override visitSelect/visitUpdate/visitDelete to extract all Expression instances (from SelectItem, WHERE, HAVING, ORDER BY) and apply visitor, collect Function.getName() from all found Function objects. Performance note: ExpressionVisitor traversal adds ~2-3ms for complex queries with deep nesting
3. Add denied function matching logic - implement case-insensitive comparison between extracted function names and config.deniedFunctions list (convert both to lowercase), add CRITICAL violation for each match with function name in message
4. Write comprehensive tests (PASS≥5: safe functions like MAX/SUM/CONCAT, normal queries; FAIL≥10: load_file, into_outfile, sys_exec, sys_eval, sleep, benchmark in various positions - SELECT clause, WHERE clause, nested in other functions, different case variations; 边界≥3: empty deniedFunctions allows all, nested functions, function in subquery), covering MySQL (load_file/sleep), Oracle (sys_exec), PostgreSQL dangerous functions with ≥18 total test cases
5. Create DangerousFunctionConfig.java with enabled, violationStrategy, and List<String> deniedFunctions fields (default list includes: load_file, into_outfile, into_dumpfile, sys_exec, sys_eval, sleep, benchmark), store function names in lowercase for matching
6. Write docs/user-guide/rules/dangerous-function.md explaining each dangerous function's risk (load_file - arbitrary file read, sys_exec - OS command execution, sleep - DoS attacks), BAD examples showing each function in different contexts, GOOD examples (safe functions), configuration section showing how to customize deniedFunctions list for specific database environments

### Task 1.7 – CallStatementChecker Implementation │ Agent_Dangerous_Operations

- **Objective:** Implement CallStatementChecker to detect stored procedure calls (CALL/EXECUTE/EXEC) that introduce opaque logic and potential permission escalation, using HIGH severity with WARN default to balance monitoring with legitimate use cases.
- **Output:** Production-ready CallStatementChecker.java with ≥18 passing tests covering MySQL/Oracle/SQL Server procedure syntax, CallStatementConfig.java, docs/user-guide/rules/call-statement.md explaining when procedures are acceptable vs problematic.
- **Guidance:** Override check() to detect Execute Statement type via instanceof or parse SQL string for CALL/EXECUTE/EXEC keywords at statement start (case-insensitive). Use HIGH violation (not CRITICAL) since procedures may be legitimate architecture. Include procedure name in message. Default: enabled=true, violationStrategy=WARN (not BLOCK).

- Write comprehensive tests (PASS≥5: SELECT/INSERT/UPDATE/DELETE, function calls in expressions; FAIL≥10: MySQL CALL procedure(), Oracle EXECUTE procedure, SQL Server EXEC procedure, procedures with parameters, nested procedure calls; 边界≥3: case variations, procedure names with special characters, empty parameter lists), covering MySQL (CALL), Oracle (EXECUTE), SQL Server (EXEC) syntaxes with ≥18 total test cases
- Implement CallStatementChecker extending AbstractRuleChecker, override check() to detect Execute Statement type via instanceof or parse SQL string for CALL/EXECUTE/EXEC keywords at statement start (case-insensitive), add HIGH violation (not CRITICAL since procedures can be legitimate in some architectures) with procedure name in message
- Create CallStatementConfig.java with standard enabled (default true) and violationStrategy (default WARN, not BLOCK - procedures may be valid business logic) fields
- Write docs/user-guide/rules/call-statement.md explaining stored procedure security concerns (opaque logic, potential permission escalation, difficult to audit), when procedures are acceptable vs problematic, BAD examples (unexpected procedure calls), GOOD examples (direct SQL), note that default strategy is WARN to balance monitoring with legitimate use cases

### Task 1.8 – MetadataStatementChecker Implementation │ Agent_Access_Control

- **Objective:** Implement MetadataStatementChecker to detect metadata disclosure statements (SHOW/DESCRIBE/USE) that leak schema information to attackers, with configurable exemptions for legitimate admin tools.
- **Output:** Production-ready MetadataStatementChecker.java with ≥18 passing tests differentiating metadata commands from INFORMATION_SCHEMA queries, MetadataStatementConfig.java with allowedStatements list, docs/user-guide/rules/metadata-statement.md explaining information disclosure risks.
- **Guidance:** Override check() to parse SQL string for SHOW/DESCRIBE/DESC/USE keywords at statement start (trim and case-insensitive). INFORMATION_SCHEMA queries via SELECT should pass validation. Empty allowedStatements blocks all metadata commands (default). Default: enabled=true, violationStrategy=WARN, allowedStatements=[].

- Write comprehensive tests (PASS≥5: SELECT/INSERT/UPDATE/DELETE, DML operations, allowed metadata types when configured; FAIL≥10: SHOW TABLES, SHOW DATABASES, DESCRIBE table, DESC table, USE database, case variations, metadata commands with options; 边界≥3: empty allowedStatements blocks all, populated allowedStatements allows specific types, INFORMATION_SCHEMA queries - should pass as normal SELECT), covering MySQL (SHOW/DESCRIBE/USE), PostgreSQL (\d commands), Oracle (DESC) variants with ≥18 total test cases
- Implement MetadataStatementChecker extending AbstractRuleChecker, override check() to parse SQL string for keywords SHOW/DESCRIBE/DESC/USE at statement start (trim and case-insensitive check), validate against config.allowedStatements (empty list = block all), add HIGH violation (not CRITICAL - metadata queries are less severe than data modification) with specific statement type in message
- Create MetadataStatementConfig.java with enabled, violationStrategy (default WARN), and List<String> allowedStatements fields (default empty list), support statement names: SHOW, DESCRIBE, USE
- Write docs/user-guide/rules/metadata-statement.md explaining metadata leakage risks (attackers learn table structures, database names, schema information for targeted attacks), BAD examples for each metadata type showing information disclosure scenarios, GOOD examples (querying INFORMATION_SCHEMA via SELECT - proper metadata access), configuration showing allowedStatements for legitimate admin tools

### Task 1.9 – SetStatementChecker Implementation │ Agent_Access_Control

- **Objective:** Implement SetStatementChecker to detect session variable modification statements (SET) that enable transaction isolation bypass and SQL mode manipulation attacks, differentiating from UPDATE...SET column assignments.
- **Output:** Production-ready SetStatementChecker.java with ≥18 passing tests correctly disambiguating SET statement from UPDATE...SET, SetStatementConfig.java, docs/user-guide/rules/set-statement.md explaining session modification risks.
- **Guidance:** Override check() to parse SQL string for SET keyword at statement start (trim and case-insensitive). Differentiate from UPDATE...SET by checking for table name context. Use MEDIUM violation (less severe than structural changes). Include variable name in message if parseable. Default: enabled=true, violationStrategy=WARN.

- Write comprehensive tests (PASS≥5: SELECT/INSERT/UPDATE/DELETE, UPDATE table SET column (not SET statement), DML operations; FAIL≥10: SET autocommit, SET sql_mode, SET @variable, SET NAMES, SET CHARSET, case variations; 边界≥3: SET with different spacing, multiple SET in batch, UPDATE vs SET statement disambiguation), covering MySQL (SET autocommit/sql_mode), PostgreSQL (SET statement variants) with ≥18 total test cases
- Implement SetStatementChecker extending AbstractRuleChecker, override check() to parse SQL string for SET keyword at statement start (trim and case-insensitive), differentiate from UPDATE...SET by checking for table name context, add MEDIUM violation (less severe than structural changes) with variable name in message if parseable
- Create SetStatementConfig.java with standard enabled (default true) and violationStrategy (default WARN - SET statements may be necessary in some frameworks) fields
- Write docs/user-guide/rules/set-statement.md explaining session variable modification risks (unexpected behavior changes, transaction isolation bypass, SQL mode manipulation enabling attacks), when SET is acceptable (framework initialization) vs problematic (runtime modification), BAD examples (runtime SET modifications), GOOD examples (proper application configuration)

### Task 1.10 – DeniedTableChecker Implementation │ Agent_Access_Control

- **Objective:** Implement DeniedTableChecker to enforce table-level access control blacklist with wildcard pattern support (sys_* blocks sys_user, sys_config but NOT system), using recursive table extraction to detect denied tables in FROM/JOIN/subqueries/CTEs.
- **Output:** Production-ready DeniedTableChecker.java with recursive table extraction (FromItemVisitor or TablesNamesFinder utility), ≥18 passing tests including wildcard boundary cases, DeniedTableConfig.java, docs/user-guide/rules/denied-table.md with wildcard semantics explanation.
- **Guidance:** Investigate JSqlParser TablesNamesFinder first - use if it handles nested subqueries/CTEs completely. Otherwise implement custom FromItemVisitor. Extract Table.getName() NOT Table.getAlias(). Wildcard matching: sys_* → ^sys_[^_]*$ regex (NOT ^sys_.*$ which incorrectly matches system). Default: enabled=true, violationStrategy=BLOCK, deniedTables=[].

1. Design table extraction strategy - analyze JSqlParser query structure to identify all table locations (FROM clause, JOIN clauses, subqueries in SELECT/WHERE/FROM, CTEs/WITH clauses). Investigate JSqlParser TablesNamesFinder utility class as potential simpler alternative to custom FromItemVisitor. If TablesNamesFinder provides complete table extraction (including nested subqueries and CTEs), use it directly. Otherwise, plan recursive FromItemVisitor approach to traverse entire query and collect all Table instances including nested ones. Output format: document chosen approach (TablesNamesFinder vs custom visitor) with justification
2. Implement DeniedTableChecker with recursive table extraction - extend AbstractRuleChecker. If using custom visitor: create inner FromItemVisitor to traverse FromItem hierarchy recursively, override visitSelect/visitUpdate/visitDelete/visitInsert to extract FromItem and apply visitor. For both approaches: collect table names from Table objects using Table.getName() (actual table name) NOT Table.getAlias() (alias name), also collect Table.getSchemaName() for schema-qualified references. Handle table aliases correctly: sys_user AS u → extract "sys_user", not "u"
3. Add wildcard pattern matching logic - implement pattern matching supporting * wildcard with correct semantics: (a) sys_* should match sys_user, sys_config but NOT system (asterisk only matches within word boundary), (b) convert SQL pattern to Java regex: sys_* → ^sys_[^_]*$ (NOT ^sys_.*$ which would match system), (c) compare extracted table names (without schema prefix) against each pattern in config.deniedTables case-insensitively using Pattern.matches()
4. Write comprehensive tests (PASS≥5: tables not in denied list, allowed tables; FAIL≥10: exact match (sys_user in denied list), wildcard match (sys_user matches sys_*), wildcard non-match (system does NOT match sys_*), table in JOIN clause, table in subquery, table in CTE, multiple denied tables in same query, schema-qualified denied table (db.sys_user); 边界≥3: table alias vs actual name, empty deniedTables allows all, case-insensitive matching), covering MySQL, Oracle, PostgreSQL syntax with ≥18 total test cases
5. Create DeniedTableConfig.java with enabled, violationStrategy (default BLOCK), and List<String> deniedTables fields (default empty list), store patterns case-insensitively, document wildcard usage in JavaDoc with examples: sys_* matches sys_user but NOT system
6. Write docs/user-guide/rules/denied-table.md explaining table-level access control use cases (protecting sensitive tables like sys_user, admin_config), wildcard pattern examples (sys_* protects all system tables with sys_ prefix), BAD examples (accessing denied tables directly or via JOIN/subquery), GOOD examples (accessing allowed tables), configuration showing deniedTables list with both exact names and patterns, security note about this being defense-in-depth (database permissions are primary control)

### Task 1.11 – ReadOnlyTableChecker Implementation │ Agent_Access_Control

- **Objective:** Implement ReadOnlyTableChecker to protect read-only tables (audit logs, historical records) from write operations using wildcard pattern matching, detecting write attempts at INSERT/UPDATE/DELETE operation level.
- **Output:** Production-ready ReadOnlyTableChecker.java with ≥18 passing tests covering wildcard matching and write-only detection, ReadOnlyTableConfig.java with readonlyTables list, docs/user-guide/rules/readonly-table.md explaining application-level safety rationale.
- **Guidance:** Override visitInsert/visitUpdate/visitDelete to extract target table name (Insert.getTable(), Update.getTable(), Delete.getTable()). Reuse DeniedTableChecker wildcard pattern matching logic. READ operations on readonly tables should pass. Use HIGH violation (not CRITICAL). Default: enabled=true, violationStrategy=BLOCK, readonlyTables=[] (includes audit_log, history_* patterns in examples).

- Write comprehensive tests (PASS≥5: SELECT from readonly tables, write to non-readonly tables, UPDATE/DELETE with readonly table in WHERE clause only (not target); FAIL≥10: INSERT into readonly table, UPDATE readonly table, DELETE from readonly table, wildcard match (audit_log matches history_*), write operations on multiple readonly tables; 边界≥3: schema-qualified names, case-insensitive matching, empty readonlyTables allows all writes), covering MySQL, Oracle, PostgreSQL with ≥18 total test cases
- Implement ReadOnlyTableChecker extending AbstractRuleChecker, override visitInsert/visitUpdate/visitDelete to extract target table name (Insert.getTable(), Update.getTable(), Delete.getTable()), apply wildcard pattern matching logic against config.readonlyTables (reuse DeniedTableChecker pattern matching approach: convert * to regex), add HIGH violation (not CRITICAL - data modification vs structure) with table name and operation type in message
- Create ReadOnlyTableConfig.java with enabled, violationStrategy (default BLOCK), and List<String> readonlyTables fields (default empty list includes audit_log, history_* patterns), document wildcard usage
- Write docs/user-guide/rules/readonly-table.md explaining read-only table protection scenarios (audit logs, historical records, reference data), wildcard pattern examples (history_* protects all historical tables), BAD examples (writing to readonly tables), GOOD examples (reading readonly tables, writing to normal tables), configuration showing readonlyTables list, note that readonly protection is for application-level safety (database permissions should also enforce)

### Task 1.12a – SQL Injection Examples │ Agent_SQL_Injection

- **Objective:** Create realistic bad/good example mapper files demonstrating SQL Injection Checker behavior (MultiStatement, SetOperation, SqlComment, IntoOutfile), enabling developers to understand attack patterns and validate Scanner CLI detection.
- **Output:** 8 mapper XML files (4 bad, 4 good) in examples/src/main/resources/mappers/ with 2-3 SQL examples per file, all examples verified detectable by SQL Scanner CLI with zero false positives/negatives.
- **Guidance:** Depends on Task 1.1-1.4 Output by Agent_SQL_Injection. Examples must match patterns from Checker test cases for consistency. Include XML comments explaining why each BAD example is dangerous and each GOOD example is safe. Verify via: mvn -pl sql-scanner-cli exec:java -Dexec.args="scan --source-dirs=examples/src/main/resources".

- Create bad example mapper files in examples/src/main/resources/mappers/bad/ for SQL Injection Checkers (MultiStatementMapper.xml, SetOperationMapper.xml, SqlCommentMapper.xml, IntoOutfileMapper.xml), each containing 2-3 SQL examples that violate the corresponding rule
- Create good example mapper files in examples/src/main/resources/mappers/good/ with same naming pattern, each containing 2-3 compliant SQL examples showing proper usage
- Ensure all example SQL statements are realistic and match patterns from Checker test cases, include comments explaining why each BAD example is dangerous and each GOOD example is safe
- Verify examples are detectable by running SQL Scanner CLI: mvn -pl sql-scanner-cli exec:java -Dexec.args="scan --source-dirs=examples/src/main/resources" and confirm all 4 SQL Injection Checkers report violations in bad examples, no violations in good examples

### Task 1.12b – Dangerous Operations Examples │ Agent_Dangerous_Operations

- **Objective:** Create realistic bad/good example mapper files demonstrating Dangerous Operations Checker behavior (DdlOperation, DangerousFunction, CallStatement), enabling developers to understand prohibited operations and validate Scanner CLI detection.
- **Output:** 6 mapper XML files (3 bad, 3 good) in examples/src/main/resources/mappers/ with 2-3 SQL examples per file, all examples verified detectable by SQL Scanner CLI with zero false positives/negatives.
- **Guidance:** Depends on Task 1.5-1.7 Output by Agent_Dangerous_Operations. Examples must match patterns from Checker test cases. DangerousFunction examples should demonstrate nested function detection. Verify via: mvn -pl sql-scanner-cli exec:java -Dexec.args="scan --source-dirs=examples/src/main/resources".

- Create bad example mapper files in examples/src/main/resources/mappers/bad/ for Dangerous Operations Checkers (DdlOperationMapper.xml, DangerousFunctionMapper.xml, CallStatementMapper.xml), each containing 2-3 SQL examples that violate the corresponding rule
- Create good example mapper files in examples/src/main/resources/mappers/good/ with same naming pattern, each containing 2-3 compliant SQL examples showing proper usage
- Ensure all example SQL statements are realistic and match patterns from Checker test cases, include comments explaining why each BAD example is dangerous and each GOOD example is safe
- Verify examples are detectable by running SQL Scanner CLI: mvn -pl sql-scanner-cli exec:java -Dexec.args="scan --source-dirs=examples/src/main/resources" and confirm all 3 Dangerous Operations Checkers report violations in bad examples, no violations in good examples

### Task 1.12c – Access Control Examples │ Agent_Access_Control

- **Objective:** Create realistic bad/good example mapper files demonstrating Access Control Checker behavior (MetadataStatement, SetStatement, DeniedTable, ReadOnlyTable), enabling developers to understand access control violations and validate Scanner CLI detection.
- **Output:** 8 mapper XML files (4 bad, 4 good) in examples/src/main/resources/mappers/ with 2-3 SQL examples per file including wildcard pattern demonstrations, all examples verified detectable by SQL Scanner CLI with zero false positives/negatives.
- **Guidance:** Depends on Task 1.8-1.11 Output by Agent_Access_Control. DeniedTable/ReadOnlyTable examples must demonstrate wildcard matching (sys_* patterns). SetStatement examples must show UPDATE...SET differentiation. Verify via: mvn -pl sql-scanner-cli exec:java -Dexec.args="scan --source-dirs=examples/src/main/resources".

- Create bad example mapper files in examples/src/main/resources/mappers/bad/ for Access Control Checkers (MetadataStatementMapper.xml, SetStatementMapper.xml, DeniedTableMapper.xml, ReadOnlyTableMapper.xml), each containing 2-3 SQL examples that violate the corresponding rule
- Create good example mapper files in examples/src/main/resources/mappers/good/ with same naming pattern, each containing 2-3 compliant SQL examples showing proper usage
- Ensure all example SQL statements are realistic and match patterns from Checker test cases, include comments explaining why each BAD example is dangerous and each GOOD example is safe
- Verify examples are detectable by running SQL Scanner CLI: mvn -pl sql-scanner-cli exec:java -Dexec.args="scan --source-dirs=examples/src/main/resources" and confirm all 4 Access Control Checkers report violations in bad examples, no violations in good examples

### Task 1.13 – Integration Test Scenarios │ Agent_Access_Control

- **Objective:** Create comprehensive integration tests validating multi-Checker interactions, ViolationStrategy behaviors, and programmatic 7-item checklist verification, enabling Manager Agent automatic validation without manual inspection.
- **Output:** CheckerIntegrationTest.java with ≥20 test scenarios covering Checker combinations, strategy switching, and automated checklist validation using JaCoCo/JMH/SnakeYAML/ServiceLoader programmatic verification.
- **Guidance:** Depends on Task 1.1-1.11 Output by all agents. Read all 11 Checker implementations from sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/ before writing tests. Programmatic checklist validation enables Manager Agent to verify: (1) coverage via JaCoCo, (2) dialect tests via annotations, (3) performance via JMH, (4) YAML via SnakeYAML, (5) docs via Files.exists(), (6) pipeline integration, (7) ServiceLoader registration.

- Read implementations of all 11 Checkers from sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/ to understand actual validation logic before writing integration tests. Create CheckerIntegrationTest.java in sql-guard-core/src/test/java/com/footstone/sqlguard/validator/ with test scenarios combining multiple Checkers (e.g., SQL with both multi-statement AND sql comment violations triggers both Checkers, SQL with denied table AND readonly table triggers both if applicable)
- Add ViolationStrategy behavior tests validating WARN mode logs violations without blocking, BLOCK mode throws exceptions, AUDIT mode records but doesn't block, test strategy switching via configuration reload
- Implement 7-item validation checklist verification scenarios programmatically: (1) unit test coverage check via JaCoCo report parsing, (2) multi-dialect test presence via test method annotation scanning, (3) performance measurement using JMH microbenchmarks confirming <5ms, (4) config YAML loading via SnakeYAML validation, (5) documentation file existence via Files.exists() checks, (6) integration with DefaultSqlSafetyValidator via end-to-end validation pipeline test, (7) RuleCheckerOrchestrator registration via ServiceLoader verification. This programmatic verification enables Manager Agent to automatically validate checklist completion without manual inspection
- Create end-to-end workflow tests simulating Manager Agent validation: load all 11 Checkers via RuleCheckerOrchestrator, process sample SQL through complete validation pipeline, verify all Checkers execute and aggregate results correctly, confirm ValidationResult contains all detected violations with proper RiskLevel hierarchy

---

## Phase 1 Dependencies
- Task 1.12a depends on Task 1.1-1.4 Output (Agent_SQL_Injection Checkers implementation)
- Task 1.12b depends on Task 1.5-1.7 Output (Agent_Dangerous_Operations Checkers implementation)
- Task 1.12c depends on Task 1.8-1.11 Output (Agent_Access_Control Checkers implementation)
- Task 1.13 depends on Task 1.1-1.11 Output (all Checkers implementation - cross-agent dependency requiring completion from all three agents)
- Tasks 1.1-1.11 have no dependencies on each other and can execute fully in parallel

## 7-Item Acceptance Checklist (Applied to Each Checker)
1. Unit tests pass (PASS≥5, FAIL≥10, 边界≥3)
2. Test coverage >80%
3. Multi-dialect tests (MySQL, Oracle, PostgreSQL - at least 3)
4. Performance <5ms per Checker
5. Config class implemented and YAML loadable
6. Complete rule documentation following no-where-clause.md template
7. Integration tests pass with DefaultSqlSafetyValidator and RuleCheckerOrchestrator
