# SQL Guard Examples

Comprehensive example repository demonstrating all dangerous SQL patterns detected by SQL Guard, with BAD and GOOD versions for educational comparison.

## Purpose

This module serves as:
- **Educational Resource**: Learn SQL safety best practices through real-world examples
- **Regression Test Suite**: Ensure scanner catches all known dangerous patterns
- **Integration Validation**: Verify scanner accuracy with comprehensive test coverage
- **Documentation**: Reference examples for each violation type with explanations

## Directory Structure

```
examples/
├── src/
│   ├── main/
│   │   ├── java/com/footstone/sqlguard/examples/
│   │   │   ├── bad/                          # Dangerous patterns
│   │   │   │   ├── BadAnnotationMapper.java  # @Select/@Update/@Delete violations
│   │   │   │   └── BadQueryWrapperService.java # QueryWrapper violations
│   │   │   └── good/                         # Corrected patterns
│   │   │       ├── GoodAnnotationMapper.java
│   │   │       └── GoodQueryWrapperService.java
│   │   └── resources/mappers/
│   │       ├── bad/                          # Dangerous XML mappers
│   │       │   ├── NoWhereClauseMapper.xml
│   │       │   ├── DummyConditionMapper.xml
│   │       │   ├── BlacklistOnlyMapper.xml
│   │       │   ├── WhitelistViolationMapper.xml
│   │       │   ├── LogicalPaginationMapper.xml
│   │       │   ├── NoConditionPaginationMapper.xml
│   │       │   ├── DeepPaginationMapper.xml
│   │       │   ├── LargePageSizeMapper.xml
│   │       │   ├── MissingOrderByMapper.xml
│   │       │   ├── NoPaginationMapper.xml
│   │       │   └── CombinedViolationsMapper.xml
│   │       └── good/                         # Corrected XML mappers
│   │           └── (same structure as bad/)
│   └── test/
│       └── java/com/footstone/sqlguard/examples/
│           └── ExamplesValidationTest.java   # Integration tests
└── README.md                                  # This file
```

## Violation Types Index

### 1. NoWhereClause (CRITICAL)
**Pattern**: DELETE/UPDATE/SELECT without WHERE clause

**Files**:
- XML: `bad/NoWhereClauseMapper.xml` → `good/NoWhereClauseMapper.xml`
- Annotation: `BadAnnotationMapper.deleteAllUsers()` → `GoodAnnotationMapper.deleteUserById()`

**Why Dangerous**: Irreversibly deletes/updates ALL table data, memory exhaustion from loading entire table

**Fix**: Always add WHERE clause to restrict operation scope

**Design Reference**: Phase 2, Task 2.2

---

### 2. DummyCondition (HIGH)
**Pattern**: WHERE 1=1, WHERE true, WHERE 'a'='a' (meaningless conditions)

**Files**:
- XML: `bad/DummyConditionMapper.xml` → `good/DummyConditionMapper.xml`
- Annotation: `BadAnnotationMapper.selectUsersWithDummy()` → `GoodAnnotationMapper.selectUsersByName()`

**Why Dangerous**: Appears to filter but causes full table scan identical to missing WHERE

**Fix**: Remove dummy condition, use MyBatis dynamic SQL `<where>` tag instead

**Design Reference**: Phase 2, Task 2.3

---

### 3. BlacklistFields (HIGH)
**Pattern**: WHERE clause using ONLY low-cardinality state flags (deleted, status, enabled)

**Files**:
- XML: `bad/BlacklistOnlyMapper.xml` → `good/BlacklistOnlyMapper.xml`
- Annotation: `BadAnnotationMapper.selectActiveUsers()` → `GoodAnnotationMapper.selectActiveUserById()`
- QueryWrapper: `BadQueryWrapperService.selectActiveUsersBlacklistOnly()` → `GoodQueryWrapperService.selectActiveUserById()`

**Why Dangerous**: WHERE deleted=0 matches 99%+ of rows (near-full-table scan)

**Fix**: Add primary key or business unique key (id, user_id, order_id)

**Design Reference**: Phase 2, Task 2.4

---

### 4. WhitelistFields (HIGH)
**Pattern**: Queries missing required whitelist fields (tenant_id for multi-tenant systems)

**Files**:
- XML: `bad/WhitelistViolationMapper.xml` → `good/WhitelistViolationMapper.xml`

**Why Dangerous**: Missing tenant_id allows cross-tenant data leakage (GDPR/HIPAA violations)

**Fix**: Always include tenant_id in WHERE clause for multi-tenant queries

**Design Reference**: Phase 2, Task 2.5

---

### 5. LogicalPagination (HIGH)
**Pattern**: RowBounds pagination without PageHelper plugin (in-memory pagination)

**Files**:
- XML: `bad/LogicalPaginationMapper.xml` → `good/LogicalPaginationMapper.xml`

**Why Dangerous**: RowBounds without plugin loads ALL rows into memory THEN paginates

**Fix**: Install PageHelper plugin or use physical LIMIT clause

**Design Reference**: Phase 2, Task 2.7

---

### 6. NoConditionPagination (HIGH)
**Pattern**: LIMIT clause without WHERE condition (paginating entire table)

**Files**:
- XML: `bad/NoConditionPaginationMapper.xml` → `good/NoConditionPaginationMapper.xml`
- Annotation: `BadAnnotationMapper.selectUsersLimitNoWhere()` → `GoodAnnotationMapper.selectActiveUsersWithLimit()`

**Why Dangerous**: LIMIT without WHERE still scans entire table, no index optimization

**Fix**: Add WHERE clause to enable index usage and reduce scan range

**Design Reference**: Phase 2, Task 2.8

---

### 7. DeepPagination (HIGH)
**Pattern**: LIMIT with very large OFFSET (deep pagination)

**Files**:
- XML: `bad/DeepPaginationMapper.xml` → `good/DeepPaginationMapper.xml`
- Annotation: `BadAnnotationMapper.selectUsersDeepOffset()` → `GoodAnnotationMapper.selectUsersAfterCursor()`

**Why Dangerous**: OFFSET 50000 requires database to scan and discard first 50,000 rows

**Fix**: Use cursor-based pagination (WHERE id > #{lastId} LIMIT 20)

**Design Reference**: Phase 2, Task 2.9

---

### 8. LargePageSize (MEDIUM)
**Pattern**: LIMIT clause with very large page size (LIMIT 5000)

**Files**:
- XML: `bad/LargePageSizeMapper.xml` → `good/LargePageSizeMapper.xml`
- Annotation: `BadAnnotationMapper.selectUsersLargeLimit()` → `GoodAnnotationMapper.selectUsersReasonableLimit()`

**Why Dangerous**: Large page sizes defeat pagination purpose, memory consumption increases

**Fix**: Use reasonable page size (10-100 rows), implement "Load More" for larger datasets

**Design Reference**: Phase 2, Task 2.10

---

### 9. MissingOrderBy (MEDIUM)
**Pattern**: Pagination (LIMIT) without ORDER BY clause

**Files**:
- XML: `bad/MissingOrderByMapper.xml` → `good/MissingOrderByMapper.xml`
- Annotation: `BadAnnotationMapper.selectUsersNoOrderBy()` → `GoodAnnotationMapper.selectUsersWithOrderBy()`

**Why Dangerous**: Without ORDER BY, row order is non-deterministic (rows may appear on multiple pages)

**Fix**: Always add ORDER BY when using LIMIT (e.g., ORDER BY id, created_at)

**Design Reference**: Phase 2, Task 2.11

---

### 10. NoPagination (CRITICAL/HIGH/MEDIUM)
**Pattern**: SELECT query completely lacking pagination (no LIMIT, no RowBounds, no IPage)

**Files**:
- XML: `bad/NoPaginationMapper.xml` → `good/NoPaginationMapper.xml`
- Annotation: `BadAnnotationMapper.selectAllOrders()` → `GoodAnnotationMapper.selectUserOrders()`
- QueryWrapper: `BadQueryWrapperService.selectAllOrdersNoPagination()` → `GoodQueryWrapperService.selectUsersWithPagination()`

**Why Dangerous**: Returns unbounded result sets (potentially millions of rows), memory exhaustion

**Risk Levels**:
- CRITICAL: No WHERE clause
- HIGH: Blacklist-only WHERE
- MEDIUM: Normal WHERE (if enforceForAllQueries=true)

**Fix**: Add LIMIT clause or use PageHelper/IPage for pagination

**Design Reference**: Phase 2, Task 2.12

---

### 11. CombinedViolations (Multiple)
**Pattern**: SQL statements with multiple concurrent violations

**Files**:
- XML: `bad/CombinedViolationsMapper.xml` → `good/CombinedViolationsMapper.xml`
- Annotation: `BadAnnotationMapper.selectMultipleViolations()` → `GoodAnnotationMapper.selectUsersByEmail()`

**Why Dangerous**: Compound violations have multiplicative impact on performance and safety

**Fix**: Address all violations systematically

**Design Reference**: Integration testing for all Phase 2 checkers

---

## Running the Scanner

### Scan BAD Examples (Expect Violations)

```bash
# Console output
java -jar sql-scanner-cli.jar \
  --project-path=examples \
  --output-format=console

# HTML report
java -jar sql-scanner-cli.jar \
  --project-path=examples/src/main/resources/mappers/bad \
  --output-format=html \
  --output-file=bad-examples-report.html
```

**Expected Result**: Multiple CRITICAL/HIGH/MEDIUM violations detected

### Scan GOOD Examples (Expect Clean)

```bash
java -jar sql-scanner-cli.jar \
  --project-path=examples/src/main/resources/mappers/good \
  --output-format=console
```

**Expected Result**: Zero violations (all queries pass validation)

### Scan with Custom Configuration

```bash
# Create config.yml
cat > config.yml <<EOF
enabled: true
activeStrategy: prod

rules:
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
  
  dummyCondition:
    enabled: true
    riskLevel: HIGH
  
  blacklistFields:
    enabled: true
    riskLevel: HIGH
    fields:
      - deleted
      - del_flag
      - status
      - is_deleted
      - enabled
      - type
EOF

# Scan with config
java -jar sql-scanner-cli.jar \
  --project-path=examples \
  --config-file=config.yml \
  --output-format=html \
  --output-file=examples-report.html
```

## Running Integration Tests

```bash
# Run all example validation tests
cd examples
mvn test

# Run specific test class
mvn test -Dtest=ExamplesValidationTest

# Run with verbose output
mvn test -Dtest=ExamplesValidationTest -X
```

**Test Coverage**:
- `testScanBadExamples_shouldDetectAllViolations()`: Validates scanner detects all expected violations
- `testScanGoodExamples_shouldPassAllChecks()`: Validates corrected examples pass validation
- `testAllKnownPatterns_shouldBeDetected()`: Regression test preventing future false negatives

## Example File Headers

Each example file includes comprehensive header comments:

```xml
<!--
  VIOLATION TYPE: NoWhereClause (CRITICAL)
  PATTERN: DELETE/UPDATE/SELECT without WHERE clause
  
  WHY DANGEROUS:
  - DELETE without WHERE: Irreversibly deletes ALL table data
  - UPDATE without WHERE: Irreversibly modifies ALL table rows
  - SELECT without WHERE: Memory exhaustion from loading entire table
  
  REAL-WORLD IMPACT:
  - Production data loss incidents
  - Database outages from memory overflow
  - Unintended mass updates affecting all customers
  
  EXPECTED VIOLATION MESSAGE: "SQL语句缺少WHERE条件,可能导致全表操作"
  EXPECTED RISK LEVEL: CRITICAL
  
  FIX RECOMMENDATION: Always add WHERE clause to restrict operation scope
  
  DESIGN REFERENCE: Phase 2, Task 2.2 - NoWhereClauseChecker Implementation
-->
```

## Integration with CI/CD

### GitHub Actions Example

```yaml
- name: SQL Safety Scan Examples
  run: |
    java -jar sql-scanner-cli.jar \
      --project-path=examples/src/main/resources/mappers/bad \
      --fail-on-critical \
      --output-format=html \
      --output-file=examples-violations.html
    
    # Expect exit code 1 (violations found)
    if [ $? -ne 1 ]; then
      echo "ERROR: Expected violations not detected!"
      exit 1
    fi
```

## Best Practices Demonstrated

### 1. WHERE Clause Design
- ✅ Always include WHERE for DELETE/UPDATE/SELECT
- ✅ Combine blacklist fields with business keys
- ✅ Use primary keys or unique indexes
- ❌ Never use empty WHERE or dummy conditions

### 2. Pagination Strategy
- ✅ Use LIMIT with ORDER BY for deterministic results
- ✅ Implement cursor-based pagination for deep pages
- ✅ Keep page sizes reasonable (10-100 rows)
- ❌ Never paginate without WHERE conditions

### 3. Multi-Tenant Isolation
- ✅ Always include tenant_id in WHERE clause
- ✅ Validate tenant_id at application layer
- ✅ Use database-level row-level security
- ❌ Never query without tenant isolation

### 4. QueryWrapper Usage
- ✅ Always add business key conditions
- ✅ Use IPage for pagination
- ✅ Combine blacklist with high-cardinality fields
- ❌ Never use empty QueryWrapper

## Troubleshooting

### Scanner Not Detecting Violations

1. **Check file locations**: Scanner looks for `src/main/resources` and `src/main/java`
2. **Verify XML syntax**: Malformed XML may skip parsing
3. **Review configuration**: Ensure rules are enabled in config.yml
4. **Check scanner version**: Ensure using latest version

### False Positives

1. **Whitelist exemptions**: Use `whitelistMapperIds` for known-safe queries
2. **Unique key detection**: Configure `uniqueKeyFields` for custom unique keys
3. **Table whitelist**: Add small tables to `whitelistTables`

### Integration Test Failures

1. **Scanner CLI not built**: Run `mvn clean install` in root directory
2. **Path issues**: Ensure `--project-path` points to correct directory
3. **Configuration mismatch**: Verify test expectations match scanner configuration

## Contributing

When adding new examples:

1. **Create BAD version first**: Demonstrate the dangerous pattern
2. **Add comprehensive header**: Include all required fields (violation type, pattern, why dangerous, impact, fix, design reference)
3. **Create GOOD version**: Show corrected implementation
4. **Update tests**: Add assertions to `ExamplesValidationTest`
5. **Update README**: Add entry to violation types index

## License

Copyright (c) 2025 Footstone

## Support

For issues or questions:
- Review main project documentation
- Check Phase 2 Memory Logs for rule checker details
- Consult CLI Quick Reference for scanner usage


