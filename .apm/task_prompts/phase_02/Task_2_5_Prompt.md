---
task_ref: "Task 2.5 - WhitelistFieldChecker Implementation"
agent_assignment: "Agent_Core_Engine_Validation"
memory_log_path: ".apm/Memory/Phase_02_Validation_Engine/Task_2_5_WhitelistFieldChecker_Implementation.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: WhitelistFieldChecker Implementation

## Task Reference
Implementation Plan: **Task 2.5 - WhitelistFieldChecker Implementation** assigned to **Agent_Core_Engine_Validation**

## Context from Dependencies
Based on Task 2.1 completed work:
- Use `extractTableName(Statement)` from AbstractRuleChecker
- Use `extractFields(Expression)` from AbstractRuleChecker
- Follow TDD with table-specific whitelist enforcement
- Create configuration with byTable map and enforcement flag

## Objective
Implement checker enforcing table-specific mandatory WHERE fields (whitelist) to ensure queries include primary keys, tenant IDs, or other high-selectivity fields, providing additional safety layer for critical tables.

## Detailed Instructions
Complete all items in **one response**.

### Implementation Requirements

**Test-Driven Development:**
Write test class `WhitelistFieldCheckerTest` with test methods:

**Table-Specific Whitelist:**
- `testUserTableWithId_shouldPass()` - user table requires [id, user_id], WHERE id=? passes
- `testUserTableWithoutRequiredField_shouldViolate()` - WHERE status=? violates (no id/user_id)
- `testOrderTableNoWhitelist_shouldPass()` - order table not in whitelist, any WHERE passes
- `testMultipleRequiredFieldsAny_shouldPass()` - user requires [id, user_id], WHERE user_id=? passes

**Edge Cases:**
- `testJoinMultipleTables_shouldUsePrimaryTable()` - JOIN query checks primary table whitelist
- `testTableWithMultipleRequiredFields_anyOneSatisfies()` - user requires [id, user_id, email], WHERE email=? passes
- `testTableNotInWhitelist_shouldPass()` - unknown table passes
- `testEmptyRequiredFields_shouldPass()` - table in byTable map with empty list passes
- `testEnforceForUnknownTables_shouldViolate()` - enforceForUnknownTables=true, unknown table without required fields violates
- `testDisabledChecker_shouldSkip()`

**Implementation:**
Create `WhitelistFieldChecker` in `com.footstone.sqlguard.validator.rule.impl` package:
- Extend `AbstractRuleChecker`
- Create `WhitelistFieldsConfig` extending `CheckerConfig` with:
  - `Set<String> fields` - global whitelist (optional, default empty)
  - `Map<String, List<String>> byTable` - table-specific whitelist (required)
  - `boolean enforceForUnknownTables` - default false
  - Getters and setters for all fields

**check() Implementation:**
1. Extract table name using `extractTableName(statement)`
2. Lookup `requiredFields = config.getByTable().get(tableName)`
3. If requiredFields is null:
   - If `config.isEnforceForUnknownTables()` is false, return (skip)
   - If true, use global `config.getFields()` as required fields
4. Extract WHERE fields using `extractFields(where)`
5. Check if ANY required field is present:
   - `whereFields.stream().anyMatch(requiredFields::contains)`
6. If false (no required field present), add MEDIUM violation:
   - Message: "表" + tableName + "的WHERE条件必须包含以下字段之一:" + requiredFields
   - Suggestion: "添加主键或业务唯一键字段到WHERE条件"

**Constraints:**
- MEDIUM risk level (less severe than blacklist-only, opt-in enforcement)
- Configuration uses Map<String, List<String>> where key=table name, value=required fields (any one sufficient)
- Tables not in whitelist map are skipped unless enforceForUnknownTables=true
- Useful for multi-tenant systems (enforce tenant_id), GDPR compliance (enforce user_id), critical business tables
- JOIN queries use primary table for whitelist lookup

## Expected Output
- **WhitelistFieldChecker class:** With table-specific whitelist enforcement
- **WhitelistFieldsConfig class:** With byTable map and enforceForUnknownTables flag
- **Comprehensive tests:** 10+ test scenarios covering table-specific rules and edge cases
- **Success Criteria:** All tests pass, table-specific matching working

**File Locations:**
- Implementation: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/WhitelistFieldChecker.java`
- Config: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/WhitelistFieldsConfig.java`
- Tests: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/WhitelistFieldCheckerTest.java`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_02_Validation_Engine/Task_2_5_WhitelistFieldChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions.
