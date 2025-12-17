---
task_ref: "Task 7.1 - Dangerous SQL Pattern Samples"
agent_assignment: "Agent_Testing_Documentation"
memory_log_path: ".apm/Memory/Phase_07_Examples_Documentation/Task_7_1_Dangerous_SQL_Pattern_Samples.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Dangerous SQL Pattern Samples

## Task Reference
Implementation Plan: **Task 7.1 - Dangerous SQL Pattern Samples** assigned to **Agent_Testing_Documentation**

## Context from Dependencies
This task builds upon Task 3.7 (CLI Tool Implementation) completed by Agent_Static_Scanner:

**Integration Steps (complete in one response):**
1. Read `sql-scanner-cli/README.md` to understand CLI usage patterns and command-line arguments
2. Review `sql-scanner-cli/src/main/java/com/footstone/sqlguard/cli/SqlScannerCli.java` to understand scan execution and report generation
3. Examine test files in `sql-scanner-cli/src/test/java/` for CLI invocation patterns and validation approaches
4. Review Phase 2 Memory Logs (Tasks 2.2-2.12) to understand all 10 rule checker implementations and expected violation messages

**Producer Output Summary:**
- **CLI Tool**: Production-ready scanner with `--project-path`, `--config-file`, `--output-format`, `--fail-on-critical` arguments
- **Scanner Integration**: XmlMapperParser, AnnotationParser, QueryWrapperScanner orchestration with DefaultSqlSafetyValidator
- **Report Formats**: Console (ANSI colors) and HTML (sortable tables) report generation
- **Exit Codes**: 0=success/warnings, 1=critical violations, 2=invalid arguments
- **Validation Engine**: 4 core checkers (NoWhereClause, DummyCondition, BlacklistField, WhitelistField) integrated

**Integration Requirements:**
- **Example Organization**: Create examples module with bad/ and good/ subdirectories for MyBatis XML/annotation/QueryWrapper samples
- **Scanner Invocation**: Use CLI tool to scan examples and validate detection accuracy in integration tests
- **Violation Verification**: Assert expected violations (CRITICAL/HIGH/MEDIUM/LOW) with correct messages match Phase 2 checker implementations
- **Regression Testing**: Maintain test suite ensuring scanner catches all known dangerous patterns

**User Clarification Protocol:**
If scanner CLI arguments or validation engine behavior is unclear after reviewing integration files, ask User for clarification on specific usage patterns or expected violation messages.

## Objective
Create comprehensive example repository demonstrating all dangerous SQL patterns detected by system, providing educational samples with BAD and GOOD versions for comparison, integration test validating scanner accuracy, and regression test suite preventing future false negatives.

## Detailed Instructions
Complete all items in one response:

1. **MyBatis XML Examples Creation:**
   - Create `examples/src/main/resources/mappers/bad/` directory with XML mappers for all 10 violation types:
     - NoWhereClauseMapper.xml (DELETE/UPDATE/SELECT without WHERE)
     - DummyConditionMapper.xml (WHERE 1=1, WHERE true, WHERE 'a'='a')
     - BlacklistOnlyMapper.xml (WHERE deleted=0, WHERE status='active' AND enabled=1)
     - WhitelistViolationMapper.xml (queries missing required whitelist fields)
     - LogicalPaginationMapper.xml (RowBounds without PageHelper)
     - NoConditionPaginationMapper.xml (LIMIT without WHERE)
     - DeepPaginationMapper.xml (LIMIT 20 OFFSET 50000)
     - LargePageSizeMapper.xml (LIMIT 5000)
     - MissingOrderByMapper.xml (pagination without ORDER BY)
     - NoPaginationMapper.xml (SELECT * FROM large_table without pagination)
     - CombinedViolationsMapper.xml (multiple violations in single SQL)
   - Create `examples/src/main/resources/mappers/good/` with corrected versions mirroring bad/ structure
   - Add comprehensive XML comment headers explaining pattern, expected violation, real-world impact, fix applied

2. **Annotation and QueryWrapper Examples:**
   - Create `examples/src/main/java/com/footstone/sqlguard/examples/bad/` with annotation mappers demonstrating all patterns using @Select/@Update/@Delete
   - Create service classes using QueryWrapper with problematic conditions (empty wrapper, blacklist-only, no pagination)
   - Create `examples/src/main/java/.../good/` with corrected versions
   - Add Javadoc comments to each class explaining pattern and fix

3. **Organization and Documentation:**
   - Create `examples/README.md` explaining purpose, directory structure, how to run scanner, index of examples by violation type
   - For each example file, add header comment: violation type, pattern description, why dangerous, real-world impact, expected violation message, fix recommendation, design reference (section + task number)

4. **Integration and Regression Testing:**
   - Create test class `ExamplesValidationTest` in examples module
   - Implement `testScanBadExamples_shouldDetectAllViolations()` running scanner CLI on bad/ directory, parsing output, asserting each expected violation detected with correct risk level
   - Implement `testScanGoodExamples_shouldPassAllChecks()` running scanner on good/ directory, asserting zero violations
   - Implement regression test `testAllKnownPatterns_shouldBeDetected()` maintaining list of all known dangerous patterns, preventing future detection regressions
   - Run tests during implementation ensuring validation passes

## Expected Output
- **Deliverables:**
  - Examples module with sample MyBatis XML mappers for all 10 violation types (bad/ and good/ versions)
  - Sample annotation-based mappers (@Select/@Update/@Delete) with dangerous patterns
  - Sample MyBatis-Plus service classes using QueryWrapper with problematic conditions
  - Organized samples by violation type with explanatory comments
  - Comprehensive comments explaining danger, expected message, suggested fix, design references
  - Integration test running scanner validating detection accuracy
  - Regression test suite ensuring scanner catches all expected violations
  - examples/README.md documenting structure and usage

- **Success Criteria:**
  - All 10 violation types have BAD and GOOD example pairs
  - Each example has comprehensive header comment with all required fields
  - Integration tests pass with 100% detection accuracy for bad examples
  - Good examples pass scanner with zero violations
  - Regression test maintains list of all patterns preventing future false negatives

- **File Locations:**
  - `examples/src/main/resources/mappers/bad/*.xml` (11+ XML mappers)
  - `examples/src/main/resources/mappers/good/*.xml` (11+ corrected XMLs)
  - `examples/src/main/java/com/footstone/sqlguard/examples/bad/*.java` (annotation mappers + services)
  - `examples/src/main/java/.../good/*.java` (corrected versions)
  - `examples/src/test/java/.../ExamplesValidationTest.java`
  - `examples/README.md`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_07_Examples_Documentation/Task_7_1_Dangerous_SQL_Pattern_Samples.md`
Follow .apm/guides/Memory_Log_Guide.md instructions.
