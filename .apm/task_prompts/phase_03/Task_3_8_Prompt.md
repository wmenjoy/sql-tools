---
agent: Agent_Static_Scanner
task_ref: Task 3.8
depends_on:
  - Task 2.13 (DefaultSqlSafetyValidator Assembly)
  - Task 3.1 (Scanner Core Framework)
ad_hoc_delegation: false
---

# Task 3.8 - Integrate DefaultSqlSafetyValidator with Static Scanner

## Objective
Integrate Phase 2's DefaultSqlSafetyValidator into SqlScanner to enable static SQL security validation during scan execution, addressing the critical gap where CLI tool reports "No violations found" despite SQL injection vulnerabilities.

## Context

**Critical Issue Discovered**: User tested CLI tool on real project (api-gateway-manager) and found:
- 562 occurrences of `${}` SQL injection vulnerabilities in codebase
- CLI extracted 539 SQL statements successfully
- CLI reported "No violations found" ✓ (technically correct)
- But security violations were NOT detected ✗

**Root Cause**: Phase 3 SqlScanner only extracts SQL statements, doesn't validate them. Phase 2's DefaultSqlSafetyValidator (10 Rule Checkers, 468 tests) exists but is unused by static scanner.

**Architecture Goal**: Complete dual-layer defense:
- **Static (Phase 3)**: Validate all SQL in codebase at compile-time
- **Runtime (Phase 4)**: Validate executed SQL at runtime

## Dependencies

### Input from Task 2.13 (Phase 2):
```java
// Available validator with 10 rule checkers
public class DefaultSqlSafetyValidator implements SqlSafetyValidator {
    public ValidationResult validate(SqlContext context);
}

// Supporting classes
public class SqlDeduplicationFilter {
    public boolean shouldValidate(String sql);
}
```

### Input from Task 3.1 (Phase 3):
```java
// Current scanner (extraction only)
public class SqlScanner {
    private final List<SqlParser> parsers;
    private final WrapperScanner wrapperScanner;

    public ScanReport scan(ScanContext context);
}

// Data model to extend
public class SqlEntry {
    // Has: rawSql, sqlType, mapperId, lineNumber, fileName
    // Need to add: violations
}
```

## Implementation Steps

### Step 1: Extend SqlEntry Model with Violations Support
**Goal**: Add violation tracking to SqlEntry

**Tasks**:
1. Add `private List<ViolationInfo> violations = new ArrayList<>()` field to SqlEntry
2. Add `public void addViolation(ViolationInfo violation)` method
3. Add `public void addViolations(List<ViolationInfo> violations)` method
4. Add `public List<ViolationInfo> getViolations()` method
5. Add `public boolean hasViolations()` method
6. Add `public RiskLevel getHighestRiskLevel()` method (returns highest severity among violations)

**Test Requirements**:
- `SqlEntryViolationsTest.java` (10 tests):
  - Adding single violation
  - Adding multiple violations
  - Getting violations list
  - hasViolations() returns true/false correctly
  - getHighestRiskLevel() returns CRITICAL/HIGH/MEDIUM/LOW correctly
  - Empty violations returns RiskLevel.LOW
  - Violations are immutable (defensive copy)

**Files to Modify**:
- `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/model/SqlEntry.java`

**Files to Create**:
- `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/model/SqlEntryViolationsTest.java`

---

### Step 2: Add Validator Integration to SqlScanner
**Goal**: Inject DefaultSqlSafetyValidator and validate extracted SQL

**Tasks**:
1. Add `private final DefaultSqlSafetyValidator validator` field to SqlScanner
2. Update constructor to accept `DefaultSqlSafetyValidator validator` parameter
3. Create `private void validateSqlEntries(List<SqlEntry> entries, SqlGuardConfig config)` method:
   ```java
   private void validateSqlEntries(List<SqlEntry> entries, SqlGuardConfig config) {
       for (SqlEntry entry : entries) {
           // Build SqlContext from SqlEntry
           SqlContext context = SqlContext.builder()
               .sql(entry.getRawSql())
               .type(entry.getSqlType())
               .mapperId(entry.getMapperId())
               .build();

           // Validate
           ValidationResult result = validator.validate(context);

           // Populate violations
           if (!result.passed()) {
               entry.addViolations(result.getViolations());
           }
       }
   }
   ```
4. Call `validateSqlEntries()` in `scan()` method after SQL extraction, before report generation

**Test Requirements**:
- `SqlScannerValidationIntegrationTest.java` (12 tests):
  - Scanner with validator validates SQL correctly
  - CRITICAL violations detected (e.g., `${}` injection)
  - HIGH violations detected (blacklist fields)
  - MEDIUM violations detected (deep pagination)
  - LOW violations detected (missing ORDER BY)
  - Multiple violations per SQL statement
  - Violations populated in SqlEntry correctly
  - Valid SQL produces no violations
  - Empty project produces empty report
  - Validator called for each SqlEntry
  - SqlContext built correctly (sql, type, mapperId)
  - Performance: 100 SQL entries validated in <1 second

**Files to Modify**:
- `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/SqlScanner.java`

**Files to Create**:
- `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/SqlScannerValidationIntegrationTest.java`

---

### Step 3: Update ScanReport with Violation Statistics
**Goal**: Add violation aggregation and statistics to ScanReport

**Tasks**:
1. Add `private final Map<RiskLevel, Integer> violationsByRisk = new EnumMap<>(RiskLevel.class)` field
2. Add `private final int totalViolations` field
3. Add `public int getTotalViolations()` method
4. Add `public int getViolationCount(RiskLevel risk)` method
5. Add `public boolean hasViolations()` method
6. Add `public boolean hasCriticalViolations()` method
7. Compute statistics in constructor from SqlEntry list:
   ```java
   public ScanReport(List<SqlEntry> entries, ...) {
       // ... existing code ...

       // Compute violation statistics
       int total = 0;
       for (SqlEntry entry : entries) {
           for (ViolationInfo violation : entry.getViolations()) {
               RiskLevel risk = violation.getRiskLevel();
               violationsByRisk.merge(risk, 1, Integer::sum);
               total++;
           }
       }
       this.totalViolations = total;
   }
   ```

**Test Requirements**:
- `ScanReportViolationStatsTest.java` (10 tests):
  - getTotalViolations() correct count
  - getViolationCount(CRITICAL) correct
  - getViolationCount(HIGH) correct
  - getViolationCount(MEDIUM) correct
  - getViolationCount(LOW) correct
  - hasViolations() returns true/false correctly
  - hasCriticalViolations() returns true/false correctly
  - Empty report has zero violations
  - Multiple entries with violations aggregated correctly
  - Violation statistics immutable

**Files to Modify**:
- `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/model/ScanReport.java`

**Files to Create**:
- `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/model/ScanReportViolationStatsTest.java`

---

### Step 4: Update Report Generators to Display Violations
**Goal**: Enhance console and HTML reports to show violations

**Tasks for ConsoleReportGenerator**:
1. Add `printViolationSummary(ScanReport report)` method:
   ```java
   private void printViolationSummary(ScanReport report) {
       if (!report.hasViolations()) {
           System.out.println(ANSI_GREEN + "✓ No violations found" + ANSI_RESET);
           return;
       }

       System.out.println(ANSI_RED + "✗ " + report.getTotalViolations() + " violations found:" + ANSI_RESET);
       System.out.println("  CRITICAL: " + report.getViolationCount(RiskLevel.CRITICAL));
       System.out.println("  HIGH:     " + report.getViolationCount(RiskLevel.HIGH));
       System.out.println("  MEDIUM:   " + report.getViolationCount(RiskLevel.MEDIUM));
       System.out.println("  LOW:      " + report.getViolationCount(RiskLevel.LOW));
   }
   ```
2. Add `printEntryViolations(SqlEntry entry)` method to show violations per SQL statement
3. Update `printToConsole()` to call violation printing methods

**Tasks for HtmlReportGenerator**:
1. Add violation summary section to HTML dashboard
2. Add violations column to SQL entries table
3. Add color coding: RED for CRITICAL, ORANGE for HIGH, YELLOW for MEDIUM, BLUE for LOW
4. Add expandable violation details (message, suggestion, risk level)
5. Add violation filtering/sorting controls

**Test Requirements**:
- `ConsoleReportViolationsTest.java` (8 tests):
  - Violation summary displayed correctly
  - Color coding for risk levels
  - Individual entry violations shown
  - No violations message shown for clean SQL
  - Multiple violations per entry formatted correctly
  - ANSI colors disabled when NO_COLOR=1
  - Performance: 100 violations printed in <500ms

- `HtmlReportViolationsTest.java` (10 tests):
  - Violation summary section present
  - Risk level color coding correct
  - Violations table column present
  - Expandable violation details work
  - XSS-safe violation messages (HTML escaping)
  - Violation count badges correct
  - Filtering by risk level works
  - Sorting by violation count works
  - No violations shows success message
  - Performance: 1000 violations in HTML <2 seconds

**Files to Modify**:
- `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/report/ConsoleReportGenerator.java`
- `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/report/HtmlReportGenerator.java`

**Files to Create**:
- `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/report/ConsoleReportViolationsTest.java`
- `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/report/HtmlReportViolationsTest.java`

---

### Step 5: Update CLI Tool to Use Validator
**Goal**: Inject validator into CLI execution flow

**Tasks**:
1. Update `SqlScannerCli.call()` method to create DefaultSqlSafetyValidator:
   ```java
   @Override
   public Integer call() throws Exception {
       // ... existing validation and config loading ...

       // Create validator
       DefaultSqlSafetyValidator validator = new DefaultSqlSafetyValidator(config);

       // Create scanner with validator
       SqlScanner scanner = new SqlScanner(parsers, wrapperScanner, validator);

       // ... rest of execution ...
   }
   ```
2. Update exit code logic to handle violations:
   ```java
   if (failOnCritical && report.hasCriticalViolations()) {
       return 1; // Exit with error
   }
   ```

**Test Requirements**:
- `CliValidatorIntegrationTest.java` (10 tests):
  - CLI creates validator correctly
  - Validator injected into scanner
  - Violations reported in console output
  - --fail-on-critical exits with code 1 when CRITICAL violations
  - --fail-on-critical exits with code 0 when only HIGH/MEDIUM/LOW
  - Quiet mode suppresses violation details but shows count
  - HTML report includes violations
  - Real-world test project: detects `${}` injection (use test resources with `${}` patterns)
  - Performance: CLI execution overhead <5% with validator
  - Integration test with all 10 rule checkers active

**Files to Modify**:
- `sql-scanner-cli/src/main/java/com/footstone/sqlguard/scanner/cli/SqlScannerCli.java`

**Files to Create**:
- `sql-scanner-cli/src/test/java/com/footstone/sqlguard/scanner/cli/CliValidatorIntegrationTest.java`

---

### Step 6: Create Real-World Test Resources
**Goal**: Test with realistic SQL injection scenarios

**Tasks**:
1. Create `test-resources/real-world-project/mappers/VulnerableMapper.xml`:
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
       "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
   <mapper namespace="com.example.VulnerableMapper">
       <!-- CRITICAL: SQL injection via ${} -->
       <select id="findByDynamicOrder" resultType="User">
           SELECT * FROM user ORDER BY ${orderByClause}
       </select>

       <!-- CRITICAL: SQL injection in WHERE -->
       <select id="findByDynamicColumn" resultType="User">
           SELECT ${selectColumns} FROM user WHERE id = #{id}
       </select>

       <!-- CRITICAL: No WHERE clause -->
       <update id="updateAllUsers">
           UPDATE user SET status = 'active'
       </update>

       <!-- HIGH: Blacklist field in WHERE -->
       <select id="findDeletedUsers" resultType="User">
           SELECT * FROM user WHERE is_deleted = 1
       </select>

       <!-- MEDIUM: Deep pagination -->
       <select id="findPagedUsers" resultType="User">
           SELECT * FROM user LIMIT 100000, 20
       </select>
   </mapper>
   ```

2. Create `test-resources/real-world-project/mappers/SafeMapper.xml`:
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
       "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
   <mapper namespace="com.example.SafeMapper">
       <!-- Safe: Parameterized query with WHERE -->
       <select id="findById" resultType="User">
           SELECT * FROM user WHERE id = #{id}
       </select>

       <!-- Safe: Proper pagination with ORDER BY -->
       <select id="findPaged" resultType="User">
           SELECT * FROM user
           WHERE status = #{status}
           ORDER BY id
           LIMIT #{offset}, #{limit}
       </select>
   </mapper>
   ```

3. Create test configuration: `test-resources/real-world-project/config.yml` with all rules enabled

**Test Requirements**:
- `RealWorldIntegrationTest.java` (8 tests):
  - VulnerableMapper.xml: detects 5 violations (3 CRITICAL + 1 HIGH + 1 MEDIUM)
  - SafeMapper.xml: no violations
  - Specific violations:
    - findByDynamicOrder: SQL injection detected
    - findByDynamicColumn: SQL injection detected
    - updateAllUsers: No WHERE clause detected
    - findDeletedUsers: Blacklist field detected
    - findPagedUsers: Deep pagination detected
  - CLI execution on test project succeeds
  - HTML report generated correctly
  - Performance: Full scan completes in <3 seconds

**Files to Create**:
- `sql-scanner-core/src/test/resources/real-world-project/mappers/VulnerableMapper.xml`
- `sql-scanner-core/src/test/resources/real-world-project/mappers/SafeMapper.xml`
- `sql-scanner-core/src/test/resources/real-world-project/config.yml`
- `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/RealWorldIntegrationTest.java`

---

## Expected Outcomes

### Functional Outcomes:
1. ✅ CLI tool detects SQL injection vulnerabilities (e.g., 562 `${}` violations in api-gateway-manager)
2. ✅ Violations displayed in console report with color coding
3. ✅ Violations displayed in HTML report with risk level badges
4. ✅ `--fail-on-critical` exits with code 1 when CRITICAL violations found
5. ✅ "No violations found" only when SQL is actually safe

### Test Outcomes:
- Total new tests: **58 tests** across 8 test classes
- All existing tests remain passing (zero regressions)
- Real-world integration test validates complete flow

### Performance Outcomes:
- Validation overhead: <5% additional time
- 100 SQL entries validated in <1 second
- Full scan + validation + report: <3 seconds for typical project

### Architecture Outcomes:
- ✅ Dual-layer defense complete: Static (Phase 3) + Runtime (Phase 4)
- ✅ Phase 2 validator properly integrated into static scanner
- ✅ CLI tool provides actionable security feedback

## Validation Criteria

### Must Pass Before Completion:
1. All 58 new tests passing (100% pass rate)
2. All existing Phase 3 tests still passing (281 tests, zero regressions)
3. Real-world integration test detects all 5 expected violations
4. CLI execution on test project exits with code 1 (CRITICAL violations)
5. Console report shows violation summary and details
6. HTML report includes violations table and dashboard

### Performance Benchmarks:
1. SqlScanner.scan() with validator: <5% overhead vs without validator
2. Validation of 100 SQL entries: <1 second
3. HTML report with 1000 violations: <3 seconds
4. CLI execution on 500 SQL entries: <5 seconds total

### Code Quality:
1. Google Java Style compliance (Checkstyle passing)
2. No compiler warnings
3. Comprehensive Javadoc for new public methods
4. Defensive copying for violation collections
5. XSS-safe HTML escaping in report generators

## Dependencies Required

### From Phase 2 (Already Available):
```xml
<!-- sql-guard-core/pom.xml -->
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-core</artifactId>
    <version>${project.version}</version>
</dependency>
```

This brings in:
- `com.footstone.sqlguard.validator.DefaultSqlSafetyValidator`
- `com.footstone.sqlguard.validator.SqlDeduplicationFilter`
- `com.footstone.sqlguard.core.model.ValidationResult`
- `com.footstone.sqlguard.core.model.ViolationInfo`
- `com.footstone.sqlguard.core.model.RiskLevel`
- All 10 RuleChecker implementations

### Test Dependencies (Already Available):
- JUnit 5
- AssertJ
- Mockito

## File Structure Summary

### New Test Classes (8 files):
```
sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/
├── model/
│   ├── SqlEntryViolationsTest.java (10 tests)
│   └── ScanReportViolationStatsTest.java (10 tests)
├── SqlScannerValidationIntegrationTest.java (12 tests)
├── RealWorldIntegrationTest.java (8 tests)
└── report/
    ├── ConsoleReportViolationsTest.java (8 tests)
    └── HtmlReportViolationsTest.java (10 tests)

sql-scanner-cli/src/test/java/com/footstone/sqlguard/scanner/cli/
└── CliValidatorIntegrationTest.java (10 tests)
```

### New Test Resources (3 files):
```
sql-scanner-core/src/test/resources/real-world-project/
├── mappers/
│   ├── VulnerableMapper.xml
│   └── SafeMapper.xml
└── config.yml
```

### Modified Implementation Files (5 files):
```
sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/
├── model/
│   ├── SqlEntry.java (add violations support)
│   └── ScanReport.java (add statistics)
├── SqlScanner.java (add validator integration)
└── report/
    ├── ConsoleReportGenerator.java (add violation display)
    └── HtmlReportGenerator.java (add violation display)

sql-scanner-cli/src/main/java/com/footstone/sqlguard/scanner/cli/
└── SqlScannerCli.java (add validator creation)
```

## Implementation Notes

### TDD Approach:
1. Write tests first for each step
2. Run tests (expect failures)
3. Implement minimum code to pass tests
4. Refactor while keeping tests green
5. Verify zero regressions in existing tests

### Backward Compatibility:
- SqlScanner constructor: Add new constructor with validator parameter
- Keep existing constructor deprecated for transition period
- Default behavior: If no validator, skip validation (backward compatible)

### Error Handling:
- Validator instantiation failure: Log error, continue without validation
- Validation throws exception: Log warning, treat as passed (fail-open)
- Malformed SQL: Catch parser exceptions, report as CRITICAL violation

### Logging:
```java
private static final Logger logger = LoggerFactory.getLogger(SqlScanner.class);

logger.info("Validating {} SQL entries with DefaultSqlSafetyValidator", entries.size());
logger.debug("Validation result for {}: {} violations", entry.getMapperId(), violations.size());
logger.warn("Validation failed for {}: {}", entry.getMapperId(), e.getMessage());
```

## Success Metrics

### Immediate Success (Task 3.8 Completion):
- ✅ 58 new tests passing (100%)
- ✅ 281 existing tests still passing (zero regressions)
- ✅ Real-world test project: 5 violations detected correctly
- ✅ CLI tool reports violations with proper formatting
- ✅ Performance targets met (<5% overhead)

### User Validation (Post-Deployment):
User will test on api-gateway-manager project expecting:
```bash
$ sql-scanner --project-path=/path/to/api-gateway-manager --fail-on-critical

[CRITICAL] 562 violations found:
  [AlarmApiRuleMapper.xml:42] order by ${orderByClause}
    ✗ SQL injection risk - ${} string interpolation
    → Use #{} parameterized query instead

  [AlarmApiRuleMapper.xml:87] select sum(${sumCol})
    ✗ SQL injection risk - ${} string interpolation
    → Use case/when statement or whitelist validation

  ... (560 more)

Exit code: 1 (CRITICAL violations detected)
```

This fixes the issue where CLI previously reported "No violations found" ✓

## Timeline Estimate
- Step 1: 30 minutes (SqlEntry violations support)
- Step 2: 1 hour (SqlScanner validator integration)
- Step 3: 30 minutes (ScanReport statistics)
- Step 4: 1.5 hours (Report generators update)
- Step 5: 30 minutes (CLI tool update)
- Step 6: 1 hour (Real-world test resources)

**Total**: ~5 hours estimated implementation + testing time

## Agent Responsibilities

As Agent_Static_Scanner, you are responsible for:
1. **Writing tests first** (TDD) for all 6 steps
2. **Implementing code** to pass tests
3. **Verifying zero regressions** in existing 281 tests
4. **Performance testing** to ensure <5% overhead
5. **Creating Memory Log** at `.apm/Memory/Phase_03_Static_Scanner/Task_3_8_Validator_Integration.md`
6. **Reporting completion** to Manager Agent with test results

## Definition of Done

- [ ] All 58 new tests passing (100% pass rate)
- [ ] All 281 existing Phase 3 tests still passing
- [ ] All 468 Phase 2 tests still passing (validator dependency)
- [ ] Real-world integration test detects 5 violations correctly
- [ ] CLI tool execution on test project exits with code 1
- [ ] Console report shows violations with colors
- [ ] HTML report includes violation dashboard
- [ ] Performance benchmarks met (<5% overhead, <1s validation)
- [ ] Google Java Style compliance (Checkstyle passing)
- [ ] Memory Log created and comprehensive
- [ ] User can detect 562 SQL injections in api-gateway-manager project

---

**End of Task Assignment**

This task addresses the critical architecture gap discovered by user testing, completing the dual-layer defense by adding static SQL validation to Phase 3.
