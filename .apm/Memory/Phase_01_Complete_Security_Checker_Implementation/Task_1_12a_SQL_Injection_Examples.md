---
task_ref: "Task 1.12a - SQL Injection Examples"
status: "completed"
agent: "Agent_SQL_Injection"
created: "2026-01-05"
updated: "2026-01-05"
---

# Task 1.12a - SQL Injection Examples

## Summary
Created 8 mapper XML files (4 bad + 4 good) demonstrating SQL Injection Checker behavior for MultiStatement, SetOperation, SqlComment, and IntoOutfile checkers. All examples verified detectable by SQL Scanner CLI with zero false positives/negatives.

## Files Created

### BAD Example Mappers (examples/src/main/resources/mappers/bad/)

1. **MultiStatementMapper.xml** - Multi-statement SQL injection patterns
   - `classicInjection`: `SELECT * FROM users WHERE id = 1; DROP TABLE users`
   - `stackedQueriesInjection`: `SELECT * FROM users WHERE id = 1; INSERT INTO hacker_log...`
   - `timeBasedInjection`: `SELECT * FROM users WHERE id = 1; SELECT SLEEP(5)`

2. **SetOperationMapper.xml** - UNION/set operation attacks
   - `unionCredentialTheft`: `SELECT id, username, email FROM users UNION SELECT id, username, password FROM admin_users`
   - `unionSchemaEnumeration`: `SELECT table_name, column_name FROM user_tables UNION ALL SELECT table_name, column_name FROM information_schema.columns`
   - `exceptDataComparison`: `SELECT email FROM users EXCEPT SELECT email FROM blocked_users`

3. **SqlCommentMapper.xml** - Comment-based injection
   - `singleLineCommentBypass`: `SELECT * FROM users WHERE username = 'admin'-- AND password = 'wrong_password'`
   - `multiLineCommentHiding`: `SELECT * FROM users /* WHERE role = 'user' AND status = 'active' */`
   - `mysqlHashCommentBypass`: `SELECT * FROM users WHERE id = 1 # AND password = 'secret' AND status = 'active'`

4. **IntoOutfileMapper.xml** - MySQL file write operations
   - `credentialExport`: `SELECT id, username, password INTO OUTFILE '/tmp/credentials.txt' FROM users`
   - `webShellUpload`: `SELECT '<?php system($_GET["cmd"]); ?>' INTO OUTFILE '/var/www/html/shell.php'`
   - `binaryFileWrite`: `SELECT UNHEX('4D5A90') INTO DUMPFILE '/tmp/malware.exe'`

### GOOD Example Mappers (examples/src/main/resources/mappers/good/)

1. **MultiStatementMapper.xml** - Safe single-statement queries
2. **SetOperationMapper.xml** - Safe JOINs and subqueries instead of UNION
3. **SqlCommentMapper.xml** - Clean SQL without comments
4. **IntoOutfileMapper.xml** - Application-level data export (no file writes)

## Code Changes Made

### 1. SqlScannerCli.java
- Added SQL injection checkers (MultiStatementChecker, SetOperationChecker, SqlCommentChecker, IntoOutfileChecker) to `createAllCheckers()` method
- Changed JSqlParserFacade from fail-fast to lenient mode to allow raw SQL checkers to run on parse failures

### 2. DefaultSqlSafetyValidator.java
- Modified `validate()` method to execute all checkers even when SQL parsing fails
- This allows raw-SQL-based checkers (MultiStatement, SqlComment, IntoOutfile) to detect violations in MySQL-specific syntax that JSqlParser cannot parse

### 3. IntoOutfileChecker.java
- Changed from extending `AbstractRuleChecker` to implementing `RuleChecker` directly
- Implemented `check()` method to analyze raw SQL string directly (not dependent on parsed AST)
- This ensures detection of INTO OUTFILE/DUMPFILE even when JSqlParser fails to parse

### 4. SqlCommentChecker.java
- Fixed false positive: MyBatis parameter syntax `#{param}` was incorrectly detected as MySQL `#` comment
- Added `findClosingBrace()` method to skip MyBatis parameters

## Verification Results

### Scanner CLI Command
```bash
java -jar sql-scanner-cli/target/sql-scanner-cli.jar --project-path=examples --output-format=html --output-file=/tmp/scan-report.html
```

### BAD Examples - Violations Detected ✅

| Checker | File | Violations |
|---------|------|------------|
| MultiStatementChecker | MultiStatementMapper.xml | 3 (classicInjection, stackedQueriesInjection, timeBasedInjection) |
| SetOperationChecker | SetOperationMapper.xml | 3 (UNION, UNION_ALL, EXCEPT) |
| SqlCommentChecker | SqlCommentMapper.xml | 3 (single-line --, multi-line /* */, MySQL #) |
| IntoOutfileChecker | IntoOutfileMapper.xml | 3 (INTO OUTFILE x2, INTO DUMPFILE x1) |

### GOOD Examples - No SQL Injection Violations ✅

| File | SQL Injection Violations |
|------|--------------------------|
| good/MultiStatementMapper.xml | 0 |
| good/SetOperationMapper.xml | 0 |
| good/SqlCommentMapper.xml | 0 |
| good/IntoOutfileMapper.xml | 0 |

Note: GOOD examples may have other non-SQL-injection violations (e.g., NoPagination) which is expected.

## Violation Messages

### MultiStatementChecker
```
检测到多语句执行(SQL注入风险),请确保SQL不包含多个语句
```

### SetOperationChecker
```
检测到SQL集合操作: UNION (可能存在SQL注入风险)
检测到SQL集合操作: UNION_ALL (可能存在SQL注入风险)
检测到SQL集合操作: EXCEPT (可能存在SQL注入风险)
```

### SqlCommentChecker
```
检测到SQL注释: 单行注释(--) (可能存在SQL注入风险)
检测到SQL注释: 多行注释(/* */) (可能存在SQL注入风险)
检测到SQL注释: MySQL注释(#) (可能存在SQL注入风险)
```

### IntoOutfileChecker
```
检测到MySQL文件写入操作: INTO OUTFILE '/tmp/credentials.txt' (可能存在任意文件写入攻击风险)
检测到MySQL文件写入操作: INTO OUTFILE '/var/www/html/shell.php' (可能存在任意文件写入攻击风险)
检测到MySQL文件写入操作: INTO DUMPFILE '/tmp/malware.exe' (可能存在二进制文件写入攻击风险)
```

## Technical Notes

1. **CDATA Usage**: BAD example files use `<![CDATA[...]]>` to wrap SQL containing special characters (like `--`) that would otherwise cause XML parsing errors.

2. **Lenient Mode**: The Scanner CLI now uses lenient parsing mode, allowing checkers to run even when JSqlParser cannot parse MySQL-specific syntax (like INTO OUTFILE).

3. **MyBatis Parameter Handling**: SqlCommentChecker now correctly distinguishes between:
   - MyBatis parameters: `#{param}` - NOT a comment (skipped)
   - MySQL comments: `# comment text` - IS a comment (detected)

## Dependencies Verified
- Task 1.1 (MultiStatementChecker) ✅
- Task 1.2 (SetOperationChecker) ✅
- Task 1.3 (SqlCommentChecker) ✅
- Task 1.4 (IntoOutfileChecker) ✅
