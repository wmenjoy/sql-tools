---
task_ref: "Task 3.1 - Scanner Core Framework & Orchestration"
agent_assignment: "Agent_Static_Scanner"
memory_log_path: ".apm/Memory/Phase_03_Static_Scanner/Task_3_1_Scanner_Core_Framework_Orchestration.md"
execution_type: "multi-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: Scanner Core Framework & Orchestration

## Task Reference
Implementation Plan: **Task 3.1 - Scanner Core Framework & Orchestration** assigned to **Agent_Static_Scanner**

## Objective
Establish complete static analysis framework providing core data models (SqlEntry, ScanReport, ScanContext), parser interfaces (SqlParser, WrapperScanner), and orchestration engine (SqlScanner) that coordinates XML/annotation/wrapper scanning to produce comprehensive scan reports for dangerous SQL detection.

## Detailed Instructions
Complete this task in **4 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Data Model TDD

**Test First:**
Write test class `SqlEntryTest` in `com.footstone.sqlguard.scanner.model` package covering:
- Creation with all fields (source type, filePath, mapperId, sqlType, rawSql, lineNumber, dynamic flag, sqlVariants list)
- Source type validation (XML/ANNOTATION/WRAPPER enum values)
- Dynamic flag toggling (default false, can set to true)
- SqlVariants list manipulation (add, get, isEmpty)
- Equals/hashCode based on filePath + lineNumber (two SqlEntry with same file and line are equal)
- Validation: filePath and rawSql cannot be null/empty, lineNumber > 0

Write test class `ScanReportTest` covering:
- Adding SqlEntry items to report
- Adding WrapperUsage items to report
- Statistics calculation (total SQL count, violation counts by risk level, wrapper usage count)
- Empty report handling (0 entries, 0 violations)

Write test class `ScanContextTest` covering:
- Construction with projectPath and SqlGuardConfig
- Null handling for parameters (should throw exception)
- Immutability verification (fields cannot be changed after construction)

**Then Implement:**
1. Create `SourceType` enum in `com.footstone.sqlguard.scanner.model` package:
   - Values: XML, ANNOTATION, WRAPPER

2. Create `SqlEntry` class in same package with fields:
   - `SourceType source` (required)
   - `String filePath` (required, absolute path)
   - `String mapperId` (required, format: namespace.methodId)
   - `SqlCommandType sqlType` (required, from Phase 1)
   - `String rawSql` (required, actual SQL text)
   - `int lineNumber` (required, > 0)
   - `boolean dynamic` (default false)
   - `List<String> sqlVariants` (ArrayList, for dynamic SQL scenarios)
   - Constructor with all fields, validation throwing IllegalArgumentException
   - Override equals() using filePath + lineNumber
   - Override hashCode() using filePath + lineNumber
   - Standard getters

3. Create `ScanReport` class with fields:
   - `List<SqlEntry> entries` (ArrayList)
   - `List<WrapperUsage> wrapperUsages` (ArrayList)
   - `Map<String, Integer> statistics` (LinkedHashMap for insertion order)
   - Methods: addEntry(), addWrapperUsage(), getEntries(), getWrapperUsages(), getStatistics()

4. Create `ScanContext` class with fields:
   - `Path projectPath` (final, required)
   - `SqlGuardConfig config` (final, required)
   - Constructor with validation (null checks)
   - Getters only (immutable)

**Constraints:**
- Use Phase 1's SqlCommandType enum (from Task 1.2)
- SqlEntry validation must fail fast with clear error messages
- ScanReport must be mutable (allow adding entries after creation)
- ScanContext must be immutable (cannot change after construction)

### Step 2: Parser Interface TDD

**Test First:**
Write test class `SqlParserInterfaceTest` with mock implementations verifying:
- `parse(File file)` returns `List<SqlEntry>`
- Parse handles file not found (throws IOException with clear message)
- Parse handles malformed XML/Java gracefully (throws ParseException or returns partial results)

Write test class `WrapperScannerInterfaceTest` with mock implementations verifying:
- `scan(File projectRoot)` returns `List<WrapperUsage>`
- Scan handles directory traversal (processes nested directories)
- Scan handles parse errors gracefully (logs errors, continues processing)

**Then Implement:**
1. Create `SqlParser` interface in `com.footstone.sqlguard.scanner.parser` package:
   ```java
   public interface SqlParser {
       List<SqlEntry> parse(File file) throws IOException, ParseException;
   }
   ```

2. Create `WrapperScanner` interface in same package:
   ```java
   public interface WrapperScanner {
       List<WrapperUsage> scan(File projectRoot) throws IOException;
   }
   ```

3. Create `WrapperUsage` class in `com.footstone.sqlguard.scanner.model` package with fields:
   - `String filePath` (absolute path to Java file)
   - `String methodName` (method containing wrapper usage)
   - `int lineNumber` (line where wrapper created)
   - `String wrapperType` (QueryWrapper/LambdaQueryWrapper/UpdateWrapper/LambdaUpdateWrapper)
   - `boolean needsRuntimeCheck` (default true - static analysis insufficient for wrappers)
   - Constructor with all fields
   - Standard getters

**Constraints:**
- Interfaces define uniform contracts for polymorphic execution
- WrapperUsage.needsRuntimeCheck always true (static analysis cannot determine dynamic query conditions)
- ParseException should be from standard java.text package

### Step 3: Orchestration TDD

**Test First:**
Write test class `SqlScannerTest` covering:
- Scan with all parsers returns complete ScanReport
- Scan aggregates entries from XML and annotation parsers
- Scan aggregates wrapper usages from wrapper scanner
- Scan calculates statistics (total SQL count, dynamic SQL count, wrapper count)
- Scan handles empty project (no SQL files returns empty report)
- Scan handles parser exceptions gracefully (logs error, continues with other parsers)

**Then Implement:**
Create `SqlScanner` class in `com.footstone.sqlguard.scanner` package:

1. **Constructor with dependency injection:**
   ```java
   public SqlScanner(
       SqlParser xmlParser,
       SqlParser annotationParser,
       WrapperScanner wrapperScanner,
       SqlRiskEvaluator evaluator
   ) {
       // Store as final fields
   }
   ```

2. **Method: `ScanReport scan(ScanContext context)`**
   - Discover all relevant files:
     - XML files: `*.xml` under `src/main/resources` (MyBatis mappers)
     - Java files: `*.java` under `src/main/java` (annotations and wrappers)
   - Delegate XML files to `xmlParser.parse(file)`
   - Delegate Java files to `annotationParser.parse(file)` for annotation extraction
   - Delegate project root to `wrapperScanner.scan(projectRoot)` for wrapper detection
   - Collect all SqlEntry results into ScanReport.entries list
   - Collect all WrapperUsage into ScanReport.wrapperUsages list
   - Calculate statistics:
     - Total SQL count: entries.size()
     - Violation count by risk level: use evaluator.evaluate(entry) for each entry
     - Wrapper usage count: wrapperUsages.size()
   - Return populated ScanReport

3. **Error Handling:**
   - Wrap parser exceptions in try-catch
   - Log errors using SLF4J logger
   - Continue processing other files if one fails
   - Don't fail entire scan due to single file error

**Constraints:**
- Use Files.walk() for recursive directory traversal
- Use Path.toString().endsWith() for file extension filtering
- Orchestrator must be thread-safe (parsers may be called concurrently in future)
- Statistics calculation must match design section 6.2

### Step 4: Integration Testing

**Create Mock Implementations:**
1. `MockXmlMapperParser` implementing SqlParser:
   - Returns predefined list of SqlEntry with source=XML
   - Simulates parsing 3 XML mapper files

2. `MockAnnotationParser` implementing SqlParser:
   - Returns predefined list of SqlEntry with source=ANNOTATION
   - Simulates parsing 2 Java annotation mappers

3. `MockWrapperScanner` implementing WrapperScanner:
   - Returns predefined list of WrapperUsage
   - Simulates finding 5 wrapper usages

4. `MockSqlRiskEvaluator`:
   - Assigns risk levels to SqlEntry instances
   - Returns CRITICAL for "SELECT * FROM user" (no WHERE)
   - Returns HIGH for dummy conditions
   - Returns SAFE for proper queries

**Integration Test:**
Write test class `SqlScannerIntegrationTest`:
- Create SqlScanner with all mock parsers and evaluator
- Create ScanContext with test project path (use temp directory)
- Call `scan(context)`
- Verify ScanReport contains:
  - Entries from XML parser (3 entries)
  - Entries from annotation parser (2 entries)
  - Wrapper usages (5 usages)
  - Statistics accurate (5 total SQL, violation counts match evaluator, 5 wrappers)
- Verify orchestrator handled all parser results correctly

**Error Handling Test:**
- Create mock parser throwing IOException
- Verify SqlScanner handles gracefully without crashing
- Verify error logged (use Logback test appender to capture log)
- Verify partial results still returned (other parsers processed)

**Run Tests:**
Execute `mvn test` ensuring all framework tests pass (expect 20+ tests).

## Expected Output

**Deliverables:**
1. `SourceType` enum with XML/ANNOTATION/WRAPPER values
2. `SqlEntry` class with comprehensive validation
3. `ScanReport` class with aggregation methods
4. `ScanContext` immutable configuration holder
5. `SqlParser` and `WrapperScanner` interfaces
6. `WrapperUsage` class for wrapper tracking
7. `SqlScanner` orchestration class
8. 20+ comprehensive tests (unit + integration)

**Success Criteria:**
- All data models implement proper validation
- SqlEntry equals/hashCode based on filePath + lineNumber
- ScanReport correctly aggregates entries and statistics
- Parser interfaces define clear contracts
- SqlScanner orchestrates all parsers successfully
- Integration tests verify end-to-end framework functionality
- All tests passing (no failures, no errors)

**File Locations:**
- Models: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/model/`
  - SourceType.java
  - SqlEntry.java
  - ScanReport.java
  - ScanContext.java
  - WrapperUsage.java
- Interfaces: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/parser/`
  - SqlParser.java
  - WrapperScanner.java
- Orchestration: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/`
  - SqlScanner.java
- Tests: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/`
  - model/*Test.java
  - SqlScannerTest.java
  - SqlScannerIntegrationTest.java

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_03_Static_Scanner/Task_3_1_Scanner_Core_Framework_Orchestration.md`

Follow `.apm/guides/Memory_Log_Guide.md` instructions for proper logging format including:
- Task completion status
- All deliverables created with file paths
- Test results (total count, pass/fail)
- Any technical decisions or challenges encountered
- Framework design patterns used (constructor injection, interface-based design)
