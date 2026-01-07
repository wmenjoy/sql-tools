---
task_ref: "Task 1.13 - Integration Test Scenarios"
agent_assignment: "Agent_Access_Control"
memory_log_path: ".apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_13_Integration_Test_Scenarios.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Integration Test Scenarios

## Task Reference
Implementation Plan: **Task 1.13 - Integration Test Scenarios** assigned to **Agent_Access_Control**

## Context from Dependencies

This task depends on **ALL** Tasks 1.1-1.12c. You have completed:

### SQL Injection Checkers (Tasks 1.1-1.4)
1. **MultiStatementChecker** - Detects multi-statement SQL injection via semicolons
2. **SetOperationChecker** - Detects UNION/MINUS/EXCEPT/INTERSECT set operations
3. **SqlCommentChecker** - Detects SQL comments (--, /* */, #) with MyBatis parameter handling
4. **IntoOutfileChecker** - Detects MySQL file write operations (implements RuleChecker directly)

### Dangerous Operations Checkers (Tasks 1.5-1.7)
5. **DdlOperationChecker** - Detects DDL operations (CREATE/ALTER/DROP/TRUNCATE)
6. **DangerousFunctionChecker** - Detects dangerous functions with recursive nested detection
7. **CallStatementChecker** - Detects stored procedure calls (CALL/EXECUTE/EXEC)

### Access Control Checkers (Tasks 1.8-1.11)
8. **MetadataStatementChecker** - Detects metadata commands (SHOW/DESCRIBE/USE)
9. **SetStatementChecker** - Detects SET statements (with UPDATE...SET differentiation)
10. **DeniedTableChecker** - Table-level access control with wildcard patterns (sys_*, admin_*)
11. **ReadOnlyTableChecker** - Protects readonly tables with wildcard patterns (history_*, audit_*)

### Example Files (Tasks 1.12a/b/c)
- 22 mapper XML files demonstrating all 11 Checkers (bad/good pairs)
- Scanner CLI integration complete

## Objective
Create comprehensive integration tests validating multi-Checker interactions, ViolationStrategy behaviors (WARN/BLOCK), and programmatic 7-item acceptance checklist verification. Ensure production-ready quality with ≥85% code coverage and <50ms validation latency.

## Detailed Instructions

### **STEP 1: Read All Checker Implementations**

Before writing tests, read ALL 11 Checker implementations to understand their APIs and behaviors:

```bash
# Read all Checker implementations
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MultiStatementChecker.java
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SetOperationChecker.java
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SqlCommentChecker.java
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/IntoOutfileChecker.java
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DdlOperationChecker.java
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DangerousFunctionChecker.java
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/CallStatementChecker.java
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/MetadataStatementChecker.java
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/SetStatementChecker.java
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/DeniedTableChecker.java
sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/ReadOnlyTableChecker.java
```

Pay special attention to:
- Which Checkers implement `RuleChecker` directly (5 checkers: Multi-statement, SqlComment, IntoOutfile, MetadataStatement, SetStatement)
- Which Checkers extend `AbstractRuleChecker` (6 checkers: SetOperation, Ddl, DangerousFunction, CallStatement, DeniedTable, ReadOnlyTable)
- Checker configuration options and default values
- Risk levels (CRITICAL vs HIGH)
- Default ViolationStrategy (WARN vs BLOCK)

### **STEP 2: Multi-Checker Interaction Tests**

Create `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/MultiCheckerIntegrationTest.java`:

**Test Categories:**

1. **Multi-Violation SQL Detection (≥5 tests)**
   - SQL triggering multiple Checkers simultaneously
   - Example: `SELECT * FROM sys_config -- admin bypass; DROP TABLE users`
     - Should trigger: MultiStatementChecker, SqlCommentChecker, DeniedTableChecker
   - Verify all violations are collected (not just first)
   - Test violation order and priority

2. **Checker Independence (≥3 tests)**
   - Disabling one Checker should not affect others
   - Config one Checker as WARN, another as BLOCK
   - Verify independent violation collection

3. **Performance Under Load (≥2 tests)**
   - Validate 100+ SQL statements with multiple Checkers enabled
   - Measure total validation time (should be <5000ms for 100 statements)
   - Verify no memory leaks (same SQL validated repeatedly)

### **STEP 3: ViolationStrategy Behavior Tests**

Create `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/ViolationStrategyIntegrationTest.java`:

**Test Categories:**

1. **WARN Strategy Behavior (≥4 tests)**
   - SQL with violations + WARN strategy → ValidationResult with violations but valid=true
   - Verify violation messages are collected
   - Multiple Checkers with WARN strategy
   - Mixed WARN/BLOCK strategies

2. **BLOCK Strategy Behavior (≥4 tests)**
   - SQL with violations + BLOCK strategy → ValidationResult with valid=false
   - Verify exception can be thrown if configured
   - Multiple Checkers with BLOCK strategy
   - Test first-failure-stops vs collect-all-violations mode

3. **Strategy Override Tests (≥2 tests)**
   - Global WARN config overridden by specific Checker BLOCK config
   - Programmatic strategy changes at runtime

### **STEP 4: Programmatic 7-Item Acceptance Checklist**

Create `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/AcceptanceChecklistIntegrationTest.java`:

**Programmatically verify each checklist item:**

1. ✅ **Test Coverage ≥18 (实际357)**
   ```java
   @Test
   public void testCoverageRequirement() {
       // Count all @Test methods in *CheckerTest.java files
       // Assert total ≥ 18 (actual should be 357)
   }
   ```

2. ✅ **Code Coverage ≥85% (JaCoCo)**
   ```java
   @Test
   public void testCodeCoverageRequirement() {
       // Parse target/site/jacoco/jacoco.xml
       // Extract line coverage for sql-guard-core module
       // Assert coverage ≥ 85%
   }
   ```

3. ✅ **Benchmark <50ms (JMH)**
   ```java
   @Test
   public void testPerformanceBenchmark() {
       // Use JMH BenchmarkRunner to run validation benchmarks
       // Parse JSON results from target/benchmarks.json
       // Assert p99 latency < 50ms
   }
   ```

4. ✅ **YAML Config Parsing (SnakeYAML)**
   ```java
   @Test
   public void testYamlConfigSupport() {
       // Load test-config.yml with all 11 Checkers configured
       // Verify all Config classes deserialize correctly
       // Test enabled/disabled flags
       // Test custom field values (deniedTables, readonlyTables, etc.)
   }
   ```

5. ✅ **Multi-Dialect SQL Support**
   ```java
   @Test
   public void testMultiDialectSupport() {
       // Test MySQL, Oracle, PostgreSQL, SQL Server syntax
       // Verify Checkers work correctly for each dialect
       // Test dialect-specific features (e.g., MySQL backticks, Oracle MERGE)
   }
   ```

6. ✅ **User Documentation Exists**
   ```java
   @Test
   public void testDocumentationExists() {
       // Verify all 11 docs/user-guide/rules/*.md files exist
       // Verify each file contains required sections
       // (Overview, Configuration, Examples, Best Practices)
   }
   ```

7. ✅ **ServiceLoader Registration**
   ```java
   @Test
   public void testServiceLoaderRegistration() {
       // Use ServiceLoader to load RuleChecker implementations
       // Verify all 11 Checkers are discoverable
       // Verify META-INF/services file is correct
   }
   ```

### **STEP 5: Edge Cases and Boundary Tests**

**Test Categories:**

1. **Null and Empty Inputs (≥3 tests)**
   - null SQL string
   - Empty SQL string
   - Whitespace-only SQL

2. **Large SQL Statements (≥2 tests)**
   - 10KB+ SQL with multiple violations
   - 100+ table JOINs

3. **Malformed SQL (≥2 tests)**
   - Syntax errors should not crash Checkers
   - Verify graceful degradation for unparseable SQL

### **STEP 6: Scanner CLI Integration Tests**

Create `sql-scanner-cli/src/test/java/com/footstone/sqlguard/scanner/cli/ScannerCliIntegrationTest.java`:

**Verify Scanner CLI includes all 11 Checkers:**

```java
@Test
public void testAllCheckersRegistered() {
    List<RuleChecker> checkers = SqlScannerCli.createAllCheckers();
    assertEquals(11, checkers.size(), "Should have 11 security checkers");

    // Verify each Checker type is present
    assertTrue(hasCheckerType(checkers, MultiStatementChecker.class));
    assertTrue(hasCheckerType(checkers, SetOperationChecker.class));
    // ... verify all 11 types
}
```

**Verify Example Files Detection:**

```java
@Test
public void testExampleFilesScanned() {
    // Run scanner on examples/src/main/resources/mappers/bad/
    // Verify all 11 Checker types report violations
    // Verify violation counts match expected values
}
```

## Expected Output

### **Deliverables:**
1. `MultiCheckerIntegrationTest.java` - ≥10 tests
2. `ViolationStrategyIntegrationTest.java` - ≥10 tests
3. `AcceptanceChecklistIntegrationTest.java` - 7 tests (one per checklist item)
4. `ScannerCliIntegrationTest.java` - ≥5 tests
5. `test-config.yml` - Test configuration file for YAML parsing test

**Total Expected: ≥32 new integration tests**

### **Success Criteria:**
- All integration tests pass with 100% success rate ✅
- Multi-Checker interactions work correctly (all violations collected) ✅
- ViolationStrategy WARN/BLOCK behaviors verified ✅
- All 7 acceptance checklist items verified programmatically ✅
- Scanner CLI integration confirmed (all 11 Checkers registered) ✅
- Code coverage remains ≥85% after integration tests added ✅
- Performance benchmarks still <50ms p99 latency ✅
- Zero regression in existing 357 unit tests ✅

### **File Locations:**
- Integration tests: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/*IntegrationTest.java`
- Scanner CLI tests: `sql-scanner-cli/src/test/java/com/footstone/sqlguard/scanner/cli/ScannerCliIntegrationTest.java`
- Test config: `sql-guard-core/src/test/resources/test-config.yml`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Complete_Security_Checker_Implementation/Task_1_13_Integration_Test_Scenarios.md`

Follow .apm/guides/Memory_Log_Guide.md instructions for proper logging format with YAML frontmatter.

Include in your Memory Log:
- Total integration test count
- All 7 acceptance checklist verification results
- Scanner CLI integration confirmation
- Performance benchmark results
- Code coverage final report
- Any issues encountered and solutions
