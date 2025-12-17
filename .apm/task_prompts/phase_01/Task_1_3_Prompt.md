---
task_ref: "Task 1.3 - Configuration Model with YAML Support"
agent_assignment: "Agent_Core_Engine_Foundation"
memory_log_path: ".apm/Memory/Phase_01_Foundation/Task_1_3_Configuration_Model_YAML_Support.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Configuration Model with YAML Support

## Task Reference
Implementation Plan: **Task 1.3 - Configuration Model with YAML Support** assigned to **Agent_Core_Engine_Foundation**

## Context from Dependencies
Based on your Task 1.1 work, use the project structure you established:
- Work in the `sql-guard-core` module
- Use SnakeYAML 1.33 dependency from parent POM dependency management
- Use the package `com.footstone.sqlguard.config` for configuration classes
- Follow TDD methodology with JUnit 5 and Mockito from your build setup

## Objective
Implement comprehensive configuration system supporting YAML file loading, nested rule configurations for all 7 validation rules, fail-fast validation, and default configuration merging to enable flexible runtime behavior control.

## Detailed Instructions
Complete this task in **6 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Root Config TDD
**Test First:**
Write test class `SqlGuardConfigTest` covering:
- Default config creation
- Enabled flag toggle
- activeStrategy setting (dev/test/prod)
- Interceptors config structure (mybatis.enabled, mybatis-plus.enabled, jdbc.enabled + type)
- Deduplication config (enabled, cacheSize, ttlMs values)

**Then Implement:**
Create `SqlGuardConfig` class in `com.footstone.sqlguard.config` package with:
- Fields: boolean enabled (default true), String activeStrategy (default "prod"), InterceptorsConfig interceptors, DeduplicationConfig deduplication, RulesConfig rules
- Nested static classes:
  - **InterceptorsConfig:** MyBatisConfig mybatis, MyBatisPlusConfig mybatisPlus, JdbcConfig jdbc
  - **DeduplicationConfig:** boolean enabled, int cacheSize, long ttlMs
  - **RulesConfig:** Fields for each of 7 rule configs

### Step 2: Rule Configs TDD
**Test First:**
Write test classes for each rule config covering valid configurations and constraint violations.

**Then Implement:**
Create config classes in config package:
- **NoWhereClauseConfig:** boolean enabled, RiskLevel riskLevel
- **DummyConditionConfig:** enabled, riskLevel, List<String> patterns, List<String> customPatterns
- **BlacklistFieldsConfig:** enabled, riskLevel, Set<String> fields
- **WhitelistFieldsConfig:** enabled, riskLevel, Set<String> fields, Map<String, List<String>> byTable, boolean enforceForUnknownTables
- **PaginationAbuseConfig:** enabled, riskLevel, nested configs (LogicalPaginationConfig, PhysicalNoConditionConfig, PhysicalDeepPaginationConfig with maxOffset/maxPageNum, LargePageSizeConfig with maxPageSize, NoOrderByConfig)
- **NoPaginationConfig:** enabled, riskLevel, boolean enforceForAllQueries, List<String> whitelistMapperIds, List<String> whitelistTables, List<String> uniqueKeyFields
- **EstimatedRowsConfig:** enabled, riskLevel, Map<SqlCommandType, Integer> thresholds

Each config class is mutable POJO for YAML deserialization.

### Step 3: YAML Loader Implementation
Add SnakeYAML 1.33 dependency to sql-guard-core module POM.

**Implement `YamlConfigLoader` class** in config package with methods:
- `loadFromFile(Path path)` throws IOException for file-based loading
- `loadFromClasspath(String resourcePath)` for classpath resource loading
- Use SnakeYAML's Constructor with PropertyUtils for proper nested type mapping
- Configure SnakeYAML to handle `Map<String, List<String>>` in WhitelistFieldsConfig.byTable correctly
- Error handling: catch FileNotFoundException, YAMLException (malformed YAML), ClassCastException (type mismatches)
- Wrap exceptions in custom ConfigLoadException with descriptive messages indicating which field/section failed

### Step 4: Fail-Fast Validation
**Implement validation method** in SqlGuardConfig:
- `validate()` throws IllegalArgumentException with clear messages

**Validation rules:**
- enabled must be boolean
- activeStrategy must be one of [dev, test, prod]
- deduplication.cacheSize > 0
- deduplication.ttlMs > 0
- PaginationAbuseConfig.maxOffset > 0
- PaginationAbuseConfig.maxPageSize > 0
- Pattern lists not empty if configured

**Write tests:**
- Valid YAML loads successfully
- Invalid YAML syntax throws exception with line/column info
- Invalid values (maxOffset = -1) throw IllegalArgumentException
- Type mismatches (string instead of int) throw ConfigLoadException
- Missing required nested sections use defaults

### Step 5: Defaults & Merging
**Implement `SqlGuardConfigDefaults` class** providing:
- Static method `getDefault()` returning SqlGuardConfig with sensible defaults:
  - enabled=true
  - activeStrategy="prod"
  - mybatis.enabled=true, mybatisPlus.enabled=false, jdbc.enabled=true with type="auto"
  - deduplication.enabled=true with cacheSize=1000 and ttlMs=100
  - All rules enabled with appropriate risk levels:
    - NoWhereClauseConfig: CRITICAL
    - DummyConditionConfig: HIGH
    - BlacklistFieldsConfig: HIGH with default fields [deleted, del_flag, status]
    - etc.

**Implement merging logic** in YamlConfigLoader:
- Load user YAML
- Load defaults
- Overlay user values over defaults (non-null user values override defaults)
- Return merged SqlGuardConfig

**Write tests:**
- Partial user config merges with defaults correctly
- User config overrides specific fields while inheriting others
- Empty YAML file results in complete default configuration

### Step 6: Comprehensive Testing
Write integration test `YamlConfigLoaderIntegrationTest` with sample YAML files in src/test/resources:
- **valid-complete.yml:** All sections defined
- **valid-partial.yml:** Only some rules configured
- **invalid-syntax.yml:** Malformed YAML
- **invalid-values.yml:** maxOffset=-1
- **invalid-types.yml:** String where int expected
- **missing-required.yml:** Missing critical sections

**Each test verifies:**
- Successful load
- ConfigLoadException with descriptive message for errors
- IllegalArgumentException for constraint violations
- Correct merging with defaults

Verify fail-fast: invalid config detected before any SQL validation occurs.
Run `mvn test` ensuring all config tests pass.

**Constraints:**
- Configuration system must support both file-based (deployment) and classpath-based (testing) loading
- Use SnakeYAML for YAML parsing with proper type mapping for nested structures
- Implement fail-fast validation in setters or post-construction to catch misconfigurations early
- Default configuration should match design document to work out-of-box for common scenarios
- Merging logic allows user config to override only specific fields while inheriting defaults
- All config classes should be mutable POJOs for YAML binding but validate constraints

## Expected Output
- **SqlGuardConfig root class:** With all configuration sections
- **7 Rule Config classes:** One for each validation rule type
- **YamlConfigLoader:** With file and classpath loading methods
- **SqlGuardConfigDefaults:** Providing sensible defaults
- **Comprehensive validation:** Fail-fast on invalid values
- **Integration tests:** With sample YAML files covering all scenarios
- **Success Criteria:** All tests pass, YAML loading/merging works, validation enforced

**File Locations:**
- Config classes: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/main/java/com/footstone/sqlguard/config/`
- Tests: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/test/java/com/footstone/sqlguard/config/`
- Test YAML files: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/test/resources/`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Foundation/Task_1_3_Configuration_Model_YAML_Support.md`

Follow .apm/guides/Memory_Log_Guide.md instructions.
