---
task_ref: "Task 3.6 - Report Generator Implementation"
agent_assignment: "Agent_Static_Scanner"
memory_log_path: ".apm/Memory/Phase_03_Static_Scanner/Task_3_6_Report_Generator_Implementation.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Report Generator Implementation

## Task Reference
Implementation Plan: **Task 3.6 - Report Generator Implementation (Console + HTML)** assigned to **Agent_Static_Scanner**

## Context from Dependencies
Based on your Task 3.1 work:

**Core Framework Outputs:**
- Use `ScanReport` class from `com.footstone.sqlguard.scanner.model` with fields: entries (List<SqlEntry>), wrapperUsages (List<WrapperUsage>), statistics (Map<String, Integer>)
- Use `SqlEntry` class containing: filePath, lineNumber, mapperId, sqlType, rawSql, dynamic flag, violation information
- Use `WrapperUsage` class containing: filePath, lineNumber, methodName, wrapperType

**Integration Approach:**
Your report generators will process ScanReport data structures:
- ReportProcessor: Aggregate and prepare data for rendering
- ConsoleReportGenerator: Produce ANSI-colored terminal output
- HtmlReportGenerator: Produce styled HTML with sortable tables

These will be used by CLI tool (Task 3.7) for scan result presentation.

## Objective
Implement dual-format report generation system producing actionable scan reports in console (ANSI-colored text for terminal display) and HTML (styled web page with sortable tables) formats, processing ScanReport data structures to group violations by severity, format output with file:line references, SQL snippets, violation messages, and suggestions.

## Detailed Instructions
Complete this task in **4 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Report Processing TDD

**Test First:**
Write test class `ReportProcessorTest` in `com.footstone.sqlguard.scanner.report` package covering:
- `testGroupByRiskLevel_shouldGroupCorrectly()` - ScanReport with violations at CRITICAL/HIGH/MEDIUM creates grouped map
- `testSortBySeverity_shouldOrderCriticalFirst()` - Violations sorted CRITICAL > HIGH > MEDIUM > LOW
- `testExtractStatistics_shouldCalculateCorrectly()` - Total SQL count, violation count by level, wrapper usage count accurate
- `testPrepareFormattedData_shouldCreateRenderStructure()` - Prepares data structures optimized for console and HTML renderers
- `testTruncateSqlSnippet_shouldLimitLength()` - SQL > 100 chars truncated with "..."
- `testEmptyReport_shouldHandleGracefully()` - Report with no violations returns empty groups

**Sample Test Data:**
Create test helper building ScanReport with violations:
```java
private ScanReport createTestReport() {
    ScanReport report = new ScanReport();

    // Add SQL entries with violations
    SqlEntry criticalEntry = new SqlEntry(
        SourceType.XML,
        "/path/to/UserMapper.xml",
        "com.example.UserMapper.deleteAll",
        SqlCommandType.DELETE,
        "DELETE FROM user",
        42,
        false,
        new ArrayList<>()
    );
    // Add violation info to criticalEntry...

    report.addEntry(criticalEntry);
    // Add more entries with different risk levels...

    return report;
}
```

**Then Implement:**
1. Create `ReportProcessor` class in `com.footstone.sqlguard.scanner.report` package
2. Create `ViolationEntry` data class:
   ```java
   public class ViolationEntry {
       private final String filePath;
       private final int lineNumber;
       private final String mapperId;
       private final String sqlSnippet;
       private final RiskLevel riskLevel;
       private final String message;
       private final String suggestion;

       // Constructor, getters...
   }
   ```

3. Create `ProcessedReport` data class:
   ```java
   public class ProcessedReport {
       private final Map<RiskLevel, List<ViolationEntry>> violationsByLevel;
       private final Map<String, Integer> statistics;

       // Constructor, getters...
   }
   ```

4. Implement `process(ScanReport report)` method:
   ```java
   public ProcessedReport process(ScanReport report) {
       // Extract violations from SqlEntry list
       // Group by RiskLevel
       // Sort groups by severity (CRITICAL first)
       // Calculate statistics
       // Prepare ViolationEntry list with truncated SQL
       // Return ProcessedReport
   }
   ```

**Constraints:**
- SQL snippets truncated to 100 characters for console display
- Statistics must include: totalSqlCount, totalViolations, violationsByLevel, wrapperUsageCount
- Violations sorted within each risk level by file path then line number
- Handle null/empty reports gracefully

### Step 2: Console Report Generation TDD

**Test First:**
Write test class `ConsoleReportGeneratorTest` covering:
- `testConsoleOutputFormat_shouldMatchDesign()` - Output matches design 6.8 format with header, statistics, grouped violations
- `testAnsiColors_shouldApplyCorrectly()` - CRITICAL uses red ANSI code \033[31m, HIGH uses yellow \033[33m, MEDIUM uses blue \033[34m
- `testViolationEntry_shouldFormatCorrectly()` - Entry format: "[file:line] mapperId - message"
- `testSqlSnippet_shouldDisplay()` - SQL snippet shown with proper indentation
- `testSummaryStatistics_shouldDisplay()` - Header shows total SQL count, violation count by level, wrapper count
- `testEmptyReport_shouldShowNoViolations()` - "No violations found" message for clean reports

**Expected Console Output Format:**
```
================================================================================
SQL Safety Scan Report
================================================================================
Total SQL: 150 | Violations: 23 (CRITICAL: 5, HIGH: 12, MEDIUM: 6) | Wrapper Usages: 8
================================================================================

[CRITICAL] 5 violations

  [/path/to/UserMapper.xml:42] com.example.UserMapper.deleteAll
  SQL: DELETE FROM user
  Message: Missing WHERE clause in DELETE statement
  Suggestion: Add WHERE clause to restrict affected rows

  [/path/to/OrderService.java:78] com.example.OrderService.findAll
  SQL: SELECT * FROM orders
  Message: No pagination or limit on large table
  Suggestion: Add LIMIT clause or use pagination

[HIGH] 12 violations

  [/path/to/ProductMapper.xml:156] com.example.ProductMapper.search
  SQL: SELECT * FROM product WHERE 1=1 AND name = ?
  Message: Dummy condition '1=1' detected
  Suggestion: Remove dummy condition and use proper WHERE clause

...

[MEDIUM] 6 violations
...

[WRAPPER USAGES] 8 locations require runtime validation
...
```

**Then Implement:**
1. Create `ConsoleReportGenerator` class in `com.footstone.sqlguard.scanner.report` package
2. Define ANSI color constants:
   ```java
   private static final String ANSI_RESET = "\033[0m";
   private static final String ANSI_RED = "\033[31m";
   private static final String ANSI_YELLOW = "\033[33m";
   private static final String ANSI_BLUE = "\033[34m";
   private static final String ANSI_BOLD = "\033[1m";
   ```

3. Implement `printToConsole(ScanReport report)` method:
   ```java
   public void printToConsole(ScanReport report) {
       ProcessedReport processed = processor.process(report);

       // Print header with statistics
       printHeader(processed.getStatistics());

       // Print violations grouped by risk level
       for (RiskLevel level : Arrays.asList(CRITICAL, HIGH, MEDIUM, LOW)) {
           List<ViolationEntry> violations = processed.getViolationsByLevel().get(level);
           if (violations != null && !violations.isEmpty()) {
               printRiskLevelSection(level, violations);
           }
       }

       // Print wrapper usages summary
       printWrapperUsages(report.getWrapperUsages());
   }
   ```

4. Implement helper methods:
   - `printHeader(Map<String, Integer> stats)` - Formatted statistics header
   - `printRiskLevelSection(RiskLevel level, List<ViolationEntry> violations)` - Colored section with violations
   - `printViolationEntry(ViolationEntry entry)` - Individual violation formatting
   - `printWrapperUsages(List<WrapperUsage> usages)` - Wrapper usage locations

**Constraints:**
- Use System.out.println() for output
- Apply ANSI colors only if terminal supports them (check environment)
- Format SQL snippets with indentation for readability
- Keep line length under 100 characters for terminal compatibility
- Empty sections should not be printed

### Step 3: HTML Report Generation TDD

**Test First:**
Write test class `HtmlReportGeneratorTest` covering:
- `testHtmlStructure_shouldBeValid()` - Generated HTML parses with JSoup without errors
- `testSortableTable_shouldIncludeColumns()` - Table has columns: Risk Level, File, Mapper, Message with sortable headers
- `testCollapsibleSqlSections_shouldWork()` - SQL preview sections expandable via JavaScript
- `testStatisticsDashboard_shouldDisplay()` - Top dashboard shows total counts with visual indicators
- `testSpecialCharacters_shouldEscape()` - SQL with <, >, &, quotes properly HTML-escaped
- `testEmptyReport_shouldHandleGracefully()` - Report with no violations shows "No violations found" message
- `testCssStyles_shouldBeIncluded()` - CSS for modern table design, color-coded risk levels included

**Expected HTML Structure:**
```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>SQL Safety Scan Report</title>
    <style>
        /* Modern table styles, color-coded risk levels, responsive layout */
    </style>
</head>
<body>
    <div class="dashboard">
        <div class="stat-box">Total SQL: 150</div>
        <div class="stat-box critical">CRITICAL: 5</div>
        <div class="stat-box high">HIGH: 12</div>
        <div class="stat-box medium">MEDIUM: 6</div>
        <div class="stat-box">Wrappers: 8</div>
    </div>

    <table id="violations-table">
        <thead>
            <tr>
                <th onclick="sortTable(0)">Risk Level</th>
                <th onclick="sortTable(1)">File:Line</th>
                <th onclick="sortTable(2)">Mapper ID</th>
                <th onclick="sortTable(3)">Message</th>
                <th>SQL Preview</th>
                <th>Suggestion</th>
            </tr>
        </thead>
        <tbody>
            <tr class="critical">
                <td>CRITICAL</td>
                <td>/path/to/UserMapper.xml:42</td>
                <td>com.example.UserMapper.deleteAll</td>
                <td>Missing WHERE clause</td>
                <td><details><summary>View SQL</summary><code>DELETE FROM user</code></details></td>
                <td>Add WHERE clause to restrict affected rows</td>
            </tr>
            ...
        </tbody>
    </table>

    <script>
        // JavaScript for table sorting
    </script>
</body>
</html>
```

**Then Implement:**
1. Create `HtmlReportGenerator` class in `com.footstone.sqlguard.scanner.report` package
2. Add Apache Commons Text dependency for HTML escaping
3. Implement `writeToFile(ScanReport report, Path outputPath)` method:
   ```java
   public void writeToFile(ScanReport report, Path outputPath) throws IOException {
       ProcessedReport processed = processor.process(report);

       StringBuilder html = new StringBuilder();
       html.append("<!DOCTYPE html>\n<html>\n");

       // Append <head> with CSS
       appendHead(html);

       // Append <body> with dashboard and table
       html.append("<body>\n");
       appendDashboard(html, processed.getStatistics());
       appendViolationsTable(html, processed.getViolationsByLevel());
       appendWrapperSection(html, report.getWrapperUsages());
       appendJavaScript(html);
       html.append("</body>\n</html>");

       // Write to file
       Files.writeString(outputPath, html.toString(), StandardCharsets.UTF_8);
   }
   ```

4. Implement helper methods:
   - `appendHead(StringBuilder html)` - <head> with CSS styles
   - `appendDashboard(StringBuilder html, Map<String, Integer> stats)` - Statistics dashboard
   - `appendViolationsTable(StringBuilder html, Map<RiskLevel, List<ViolationEntry>> violations)` - Sortable table
   - `appendWrapperSection(StringBuilder html, List<WrapperUsage> usages)` - Wrapper usages table
   - `appendJavaScript(StringBuilder html)` - Table sorting JavaScript
   - `escapeHtml(String text)` - HTML entity escaping

**Constraints:**
- Use StringEscapeUtils.escapeHtml4() for XSS prevention
- CSS should be inline (no external files for portability)
- JavaScript should be simple and not require external libraries
- HTML5 <details>/<summary> for collapsible SQL sections
- Table sortable by clicking column headers
- Color-coded risk levels matching console colors

### Step 4: Comprehensive Testing

**Create Integration Test:**
Write `ReportGeneratorIntegrationTest` with comprehensive test scenarios:

1. **Large report test:**
   - Create ScanReport with 100 violations (mix of CRITICAL/HIGH/MEDIUM)
   - 200 total SQL entries
   - 20 wrapper usages
   - Generate console output: verify formatting correct
   - Generate HTML output: verify table contains 100 rows
   - Parse HTML with JSoup: verify valid structure

2. **Special characters test:**
   - SQL with `<script>alert('XSS')</script>`
   - SQL with quotes: `SELECT * FROM user WHERE name='O'Brien'`
   - SQL with ampersands: `SELECT * FROM user WHERE id > 100 && status = 'active'`
   - SQL with Unicode: `SELECT * FROM user WHERE name = '李明'`
   - Verify console output displays correctly
   - Verify HTML escapes dangerous characters

3. **Empty report test:**
   - ScanReport with no violations
   - Verify console shows "No violations found"
   - Verify HTML shows success message

4. **Long SQL test:**
   - SQL statements > 1000 characters
   - Verify console truncates to 100 chars with "..."
   - Verify HTML shows full SQL in collapsible section

5. **Edge cases:**
   - Violation with null message/suggestion (use defaults)
   - File path with spaces and special characters
   - Mapper ID with Unicode characters
   - Very long file paths (>200 chars)

**Performance Test:**
Write `ReportGeneratorPerformanceTest`:
- Generate report with 10000 violations
- Measure console output time (should be <1 second)
- Measure HTML generation time (should be <3 seconds)
- Verify HTML file size reasonable (<5MB for 10000 violations)
- Test HTML rendering in browser (manual verification)

**HTML Validation:**
- Use JSoup to parse generated HTML
- Verify no parse errors
- Verify table structure correct (thead, tbody, expected columns)
- Verify CSS styles present
- Verify JavaScript code valid
- Test sortable table functionality (manual browser testing)

**Run Tests:**
Execute `mvn test -Dtest=*Report*` ensuring all report generator tests pass (expect 35+ tests).

## Expected Output

**Deliverables:**
1. `ReportProcessor` class aggregating and preparing report data structures
2. `ConsoleReportGenerator` producing ANSI-colored terminal output matching design 6.8 format
3. `HtmlReportGenerator` producing styled HTML with sortable violation tables
4. `ViolationEntry` and `ProcessedReport` data classes
5. Violation grouping by risk level (CRITICAL/HIGH/MEDIUM/LOW) with severity sorting
6. Statistics summary (total SQL count, violation counts by level, wrapper usage count)
7. Formatted violation entries with file:line, mapper ID, SQL snippet, message, suggestion
8. Collapsible SQL preview sections and syntax highlighting in HTML
9. 35+ comprehensive tests covering formatting accuracy, HTML validity, special character handling

**Success Criteria:**
- Console output matches design 6.8 specification
- ANSI colors applied correctly (red=CRITICAL, yellow=HIGH, blue=MEDIUM)
- HTML output is valid HTML5 and renders correctly in browsers
- Special characters properly escaped (no XSS vulnerabilities)
- Table sorting works in HTML output
- SQL snippets truncated appropriately for each format
- Statistics calculated and displayed accurately
- Empty reports handled gracefully
- All tests passing (no failures, no errors)

**File Locations:**
- Processor: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/report/ReportProcessor.java`
- Console: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/report/ConsoleReportGenerator.java`
- HTML: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/report/HtmlReportGenerator.java`
- Data classes: `sql-scanner-core/src/main/java/com/footstone/sqlguard/scanner/report/ViolationEntry.java`, `ProcessedReport.java`
- Processor test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/report/ReportProcessorTest.java`
- Console test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/report/ConsoleReportGeneratorTest.java`
- HTML test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/report/HtmlReportGeneratorTest.java`
- Integration test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/report/ReportGeneratorIntegrationTest.java`
- Performance test: `sql-scanner-core/src/test/java/com/footstone/sqlguard/scanner/report/ReportGeneratorPerformanceTest.java`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_03_Static_Scanner/Task_3_6_Report_Generator_Implementation.md`

Follow `.apm/guides/Memory_Log_Guide.md` instructions including:
- Task completion status
- All deliverables created with file paths
- Test results (total count, pass/fail)
- Console output format specification followed
- HTML generation approach (inline CSS, JavaScript sorting)
- Special character escaping strategy
- Performance measurements for large reports
