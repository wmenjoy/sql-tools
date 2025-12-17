---
task_ref: "Task 2.4 - BlacklistFieldChecker Implementation"
agent_assignment: "Agent_Core_Engine_Validation"
memory_log_path: ".apm/Memory/Phase_02_Validation_Engine/Task_2_4_BlacklistFieldChecker_Implementation.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: BlacklistFieldChecker Implementation

## Task Reference
Implementation Plan: **Task 2.4 - BlacklistFieldChecker Implementation** assigned to **Agent_Core_Engine_Validation**

## Objective
Implement checker detecting WHERE conditions using only blacklisted fields (deleted, del_flag, status, is_deleted, etc.) which are typically state flags with low cardinality causing excessive row matches and near-full-table scans.

## Context from Dependencies
Based on Task 2.1 completed work:
- Use `extractFields(Expression)` from AbstractRuleChecker with FieldExtractorVisitor
- Use `extractWhere(Statement)` to get WHERE clause
- Follow TDD with blacklist-only and mixed condition tests
- Create configuration with field set and wildcard pattern support

## Detailed Instructions
Complete all items in **one response**.

### Implementation Requirements

**Test-Driven Development:**
Write test class `BlacklistFieldCheckerTest` with test methods:

**Blacklist Detection:**
- `testDeletedOnly_shouldViolate()` - WHERE deleted=0
- `testStatusOnly_shouldViolate()` - WHERE status='active'
- `testMultipleBlacklistFields_shouldViolate()` - WHERE deleted=0 AND enabled=1
- `testMixedConditions_shouldPass()` - WHERE id=1 AND deleted=0 (id not blacklist)
- `testNonBlacklistOnly_shouldPass()` - WHERE user_id=? AND order_id=?
- `testNoWhereClause_shouldPass()` - checker skips (handled by NoWhereClauseChecker)

**Edge Cases:**
- `testWildcardPattern_shouldMatch()` - blacklist "create_*", test WHERE create_time > ? violates
- `testCaseInsensitive_shouldMatch()` - WHERE DELETED=0 and WHERE deleted=0 both violate
- `testEmptyBlacklist_shouldPassAll()` - empty fields set should not violate
- `testBlacklistPlusDummy_shouldBothViolate()` - WHERE deleted=0 AND 1=1 (triggers both checkers)
- `testDisabledChecker_shouldSkip()`

**Implementation:**
Create `BlacklistFieldChecker` in `com.footstone.sqlguard.validator.rule.impl` package:
- Extend `AbstractRuleChecker`
- Create `BlacklistFieldsConfig` extending `CheckerConfig` with:
  - `Set<String> fields` - default blacklist ["deleted", "del_flag", "status", "is_deleted", "enabled", "type"]
  - Getter and setter for fields

**check() Implementation:**
1. Extract WHERE using `extractWhere()`
2. If WHERE is null, return (skip - NoWhereClauseChecker handles this)
3. Call `extractFields(where)` to get Set<String> of field names
4. Check if ALL fields are blacklisted:
   - `fields.stream().allMatch(f -> isBlacklisted(f, config.getFields()))`
5. If true, add HIGH violation:
   - Message: "WHERE条件只包含黑名单字段" + fields + ",条件过于宽泛"
   - Suggestion: "添加主键或业务唯一键字段(如id, user_id)"

**Helper Method:**
Implement `isBlacklisted(String field, Set<String> blacklist)`:
- Convert both to lowercase for case-insensitive matching
- Check exact match first
- Support wildcard pattern matching (e.g., "create_*" matches "create_time", "create_by")
- Use simple pattern: if blacklist item ends with *, check startsWith

**Constraints:**
- HIGH risk level (appears to have WHERE but still matches most rows)
- Common blacklist fields: deleted, del_flag, status, is_deleted, enabled, type, create_*, update_*
- If ANY non-blacklist field present, condition is acceptable (mixed conditions allow sufficient selectivity)
- Wildcard patterns enable flexible blacklist definitions
- Case-insensitive matching for robustness

## Expected Output
- **BlacklistFieldChecker class:** With blacklist-only detection and wildcard support
- **BlacklistFieldsConfig class:** With default blacklist set
- **Comprehensive tests:** 10+ test scenarios including wildcards and edge cases
- **Success Criteria:** All tests pass, wildcard matching working

**File Locations:**
- Implementation: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/BlacklistFieldChecker.java`
- Config: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/BlacklistFieldsConfig.java`
- Tests: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/impl/BlacklistFieldCheckerTest.java`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_02_Validation_Engine/Task_2_4_BlacklistFieldChecker_Implementation.md`

Follow .apm/guides/Memory_Log_Guide.md instructions.
