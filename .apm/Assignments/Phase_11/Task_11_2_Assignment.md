---
Task_ID: 11.2
Task_Name: JDBC Common Module Extraction
Assigned_Agent: Agent_Core_Engine_Foundation
Phase: Phase 11 - JDBC Module Separation
Priority: HIGH (Blocks Tasks 11.3, 11.4, 11.5)
Estimated_Duration: 2 days
Dependencies: Task 11.1 (Test Design - COMPLETED)
Output_Location: .apm/Memory/Phase_11_JDBC_Module_Separation/Task_11_2_Common_Module_Extraction.md
---

# Task 11.2 – JDBC Common Module Extraction

## Objective

Extract common JDBC abstractions into new `sql-guard-jdbc-common` module, unifying `ViolationStrategy` enum (eliminating 3 duplicates), creating `JdbcInterceptorBase` abstract base class with template method pattern for SQL interception lifecycle, defining `JdbcInterceptorConfig` interface for configuration abstraction, implementing utility classes for SqlContext construction and audit event creation.

**CRITICAL**: This task creates the foundation module that Tasks 11.3-11.5 depend on. 100% backward compatibility is mandatory.

---

## Context

### Current State
**Problem**: `sql-guard-jdbc` module contains 3 duplicate implementations:
- `com.footstone.sqlguard.interceptor.druid.ViolationStrategy` (identical enum)
- `com.footstone.sqlguard.interceptor.hikari.ViolationStrategy` (identical enum)
- `com.footstone.sqlguard.interceptor.p6spy.ViolationStrategy` (identical enum)

Each connection pool module duplicates interceptor logic for SQL validation, audit event creation, and violation handling.

### Target Architecture
```
sql-guard-jdbc-common/
├── pom.xml (dependencies: sql-guard-core, sql-guard-audit-api ONLY)
└── src/main/java/com/footstone/sqlguard/interceptor/jdbc/common/
    ├── ViolationStrategy.java             # Unified enum (BLOCK, WARN, LOG)
    ├── JdbcInterceptorBase.java           # Template method pattern
    ├── JdbcInterceptorConfig.java         # Configuration interface
    ├── SqlContextBuilder.java             # Context construction utility
    └── JdbcAuditEventBuilder.java         # Audit event creation utility
```

### Dependencies
**Task 11.1 Output**: 40 test specifications available in `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md`

**Downstream Tasks**: Tasks 11.3, 11.4, 11.5 will refactor Druid/HikariCP/P6Spy modules to use this common module.

---

## Expected Outputs

### 1. Maven Module Structure
**Location**: `sql-guard-jdbc-common/`

**POM Requirements**:
```xml
<dependencies>
    <!-- ONLY these 2 dependencies allowed -->
    <dependency>
        <groupId>com.footstone</groupId>
        <artifactId>sql-guard-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.footstone</groupId>
        <artifactId>sql-guard-audit-api</artifactId>
    </dependency>

    <!-- NO connection pool dependencies (Druid, HikariCP, P6Spy) -->
    <!-- Maven Enforcer will validate this -->
</dependencies>
```

### 2. ViolationStrategy Enum
**Location**: `sql-guard-jdbc-common/src/main/java/com/footstone/sqlguard/interceptor/jdbc/common/ViolationStrategy.java`

**Requirements**:
- Enum values: `BLOCK`, `WARN`, `LOG`
- Javadoc explaining each strategy
- No external dependencies
- Unit tests: 4 tests validating all values and behaviors

**Backward Compatibility**:
- Keep old enums in `druid`, `hikari`, `p6spy` packages
- Mark as `@Deprecated(since = "1.x.0", forRemoval = false)`
- Old enums delegate to new common enum
- Deprecation message guides migration

### 3. JdbcInterceptorBase Abstract Class
**Location**: `sql-guard-jdbc-common/src/main/java/com/footstone/sqlguard/interceptor/jdbc/common/JdbcInterceptorBase.java`

**Template Method Pattern**:
```java
public abstract class JdbcInterceptorBase {

    // Final template method - orchestrates lifecycle
    public final void interceptSql(String sql, Object... params) {
        try {
            beforeValidation(sql, params);
            SqlContext context = buildSqlContext(sql, params);
            ValidationResult result = validate(context);
            handleViolation(result);
            afterValidation(result);
        } catch (Exception e) {
            onError(e);
        }
    }

    // Hook methods for subclass customization
    protected abstract SqlContext buildSqlContext(String sql, Object... params);
    protected void beforeValidation(String sql, Object... params) { }
    protected void afterValidation(ValidationResult result) { }
    protected void onError(Exception e) { }
    protected abstract void handleViolation(ValidationResult result);
}
```

**Requirements**:
- Thread-safe implementation
- Proper exception handling
- Logging at appropriate levels
- Unit tests: 8 tests validating lifecycle and hook invocations

### 4. JdbcInterceptorConfig Interface
**Location**: `sql-guard-jdbc-common/src/main/java/com/footstone/sqlguard/interceptor/jdbc/common/JdbcInterceptorConfig.java`

**Interface Definition**:
```java
public interface JdbcInterceptorConfig {
    boolean isEnabled();
    ViolationStrategy getStrategy();
    boolean isAuditEnabled();
    List<String> getExcludePatterns();
}
```

**Pool-Specific Extensions** (to be created in Tasks 11.3-11.5):
- `DruidInterceptorConfig extends JdbcInterceptorConfig` (adds: filterPosition)
- `HikariInterceptorConfig extends JdbcInterceptorConfig` (adds: leakDetectionThreshold)
- `P6SpyInterceptorConfig extends JdbcInterceptorConfig` (adds: systemPropertyConfig)

**Requirements**:
- Interface-only (no implementation in common module)
- Javadoc for all methods
- Default method support for backward compatibility
- Unit test: 1 test validating interface contract

### 5. SqlContextBuilder Utility
**Location**: `sql-guard-jdbc-common/src/main/java/com/footstone/sqlguard/interceptor/jdbc/common/SqlContextBuilder.java`

**Purpose**: Standardize SqlContext construction across all JDBC interceptors

**Requirements**:
- Static factory methods: `buildContext(sql, params, metadata)`
- Handles JDBC-specific metadata extraction
- Thread-safe and reusable
- Unit tests: 1 test validating context building

### 6. JdbcAuditEventBuilder Utility
**Location**: `sql-guard-jdbc-common/src/main/java/com/footstone/sqlguard/interceptor/jdbc/common/JdbcAuditEventBuilder.java`

**Purpose**: Standardize AuditEvent creation for JDBC interceptions

**Requirements**:
- Static factory methods: `createEvent(context, result)`
- Includes timestamp, SQL, parameters, validation result
- Thread-safe
- Unit tests: 1 test validating event creation

---

## Implementation Guidance

### Step 1: TDD - Write Tests First
**Reference**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md` (Section 3: Module Isolation Tests, Test 3.1-3.12)

**Test Classes to Implement** (30+ tests total):

1. **CommonModuleExtractionTest** (12 tests):
   - `testViolationStrategy_unified_hasAllThreeValues()`
   - `testViolationStrategy_BLOCK_behavior_matchesLegacy()`
   - `testViolationStrategy_WARN_behavior_matchesLegacy()`
   - `testViolationStrategy_LOG_behavior_matchesLegacy()`
   - `testJdbcInterceptorBase_templateMethod_invokesInOrder()`
   - `testJdbcInterceptorBase_beforeValidation_hookCalled()`
   - `testJdbcInterceptorBase_afterValidation_hookCalled()`
   - `testJdbcInterceptorBase_onError_hookCalled()`
   - `testJdbcInterceptorConfig_interface_definesAllProperties()`
   - `testSqlContextBuilder_jdbc_buildsCorrectly()`
   - `testJdbcAuditEventBuilder_createsEventCorrectly()`
   - `testPackageStructure_common_isCorrect()`

2. **DependencyIsolationTest** (10 tests):
   - `testCommonModule_noDruidDependency_compiles()`
   - `testCommonModule_noHikariDependency_compiles()`
   - `testCommonModule_noP6SpyDependency_compiles()`
   - `testCommonModule_onlyCoreAndAuditApi_sufficient()`
   - `testCommonModule_transitiveDeps_none()`
   - `testCommonModule_classLoading_noPoolClassesRequired()`
   - `testCommonModule_optionalDeps_properlyMarked()`
   - `testCommonModule_providedScope_notLeaking()`
   - `testCommonModule_testScope_isolated()`
   - `testCommonModule_runtimeScope_minimal()`

3. **BackwardCompatibilityTest** (8 tests):
   - `testLegacyViolationStrategy_druid_stillWorks()`
   - `testLegacyViolationStrategy_hikari_stillWorks()`
   - `testLegacyViolationStrategy_p6spy_stillWorks()`
   - `testLegacyImports_compileWithDeprecationWarning()`
   - `testLegacyConfig_mapsToNewConfig()`
   - `testLegacyBehavior_preserved()`
   - `testDeprecationAnnotations_present()`
   - `testMigrationPath_documented()`

**TDD Process**:
1. Write failing test (RED)
2. Implement minimum code to pass (GREEN)
3. Refactor for quality (REFACTOR)
4. Repeat for each component

### Step 2: Minimal Dependency Principle
**CRITICAL**: `sql-guard-jdbc-common` must have ZERO connection pool dependencies.

**Validation**:
```bash
# Verify no connection pool classes on classpath
mvn dependency:tree -DoutputFile=deps.txt
grep -i "druid\|hikari\|p6spy" deps.txt
# Expected: NO MATCHES (except in provided/test scope)
```

**Maven Enforcer Plugin** (add to POM):
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-enforcer-plugin</artifactId>
    <executions>
        <execution>
            <id>enforce-no-pool-dependencies</id>
            <goals><goal>enforce</goal></goals>
            <configuration>
                <rules>
                    <bannedDependencies>
                        <excludes>
                            <exclude>com.alibaba:druid</exclude>
                            <exclude>com.zaxxer:HikariCP</exclude>
                            <exclude>p6spy:p6spy</exclude>
                        </excludes>
                    </bannedDependencies>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Step 3: ViolationStrategy Unification
**Current Duplicates** (identified in Task 11.1):
- `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/druid/ViolationStrategy.java`
- `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/hikari/ViolationStrategy.java`
- `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/p6spy/ViolationStrategy.java`

**Migration Strategy**:
1. Create unified enum in `sql-guard-jdbc-common`
2. Keep old enums as deprecated forwarders:
```java
@Deprecated(since = "1.x.0", forRemoval = false)
public enum ViolationStrategy {
    BLOCK, WARN, LOG;

    // Delegate to common enum
    public com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy toCommon() {
        return com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy.valueOf(name());
    }
}
```

### Step 4: Template Method Pattern Implementation
**Design Pattern**: Template Method (GoF)

**Benefits**:
- Eliminates code duplication across Druid/HikariCP/P6Spy
- Standardizes interception lifecycle
- Pool-specific customization via hook methods
- Testability through abstract class

**Lifecycle Flow**:
```
interceptSql() [final]
  ├─→ beforeValidation() [hook - optional]
  ├─→ buildSqlContext() [hook - required]
  ├─→ validate() [template logic]
  ├─→ handleViolation() [hook - required]
  ├─→ afterValidation() [hook - optional]
  └─→ onError() [hook - optional]
```

**Thread Safety**:
- No instance state (stateless base class)
- Subclasses responsible for thread-local storage
- Documented thread-safety contract

### Step 5: Backward Compatibility Preservation
**100% Compatibility Requirement**: All existing code must work without changes.

**Deprecation Strategy**:
- Old classes remain in original packages
- Marked with `@Deprecated(since = "1.x.0", forRemoval = false)`
- Javadoc includes migration guide
- Behavior preserved through delegation

**Migration Guide** (create in `docs/migration/`):
```markdown
# Migration Guide: ViolationStrategy Unification

## Old Import (Deprecated)
```java
import com.footstone.sqlguard.interceptor.druid.ViolationStrategy;
```

## New Import (Recommended)
```java
import com.footstone.sqlguard.interceptor.jdbc.common.ViolationStrategy;
```

## Behavior
- Old imports still work (100% compatible)
- Compile warnings indicate deprecation
- Migrate at your convenience (no forced timeline)
```

---

## Test Matrix (30+ tests)

| Test Class | Test Count | Purpose |
|------------|-----------|---------|
| CommonModuleExtractionTest | 12 tests | Verify common abstractions work correctly |
| DependencyIsolationTest | 10 tests | Ensure no connection pool dependencies |
| BackwardCompatibilityTest | 8 tests | Preserve existing code compatibility |
| **TOTAL** | **30 tests** | **Complete TDD coverage** |

---

## Acceptance Criteria

- [ ] **Module Structure**: `sql-guard-jdbc-common` module created with correct POM
- [ ] **Dependency Isolation**: ZERO connection pool dependencies (verified by Maven Enforcer)
- [ ] **ViolationStrategy**: Unified enum with 3 values (BLOCK, WARN, LOG)
- [ ] **JdbcInterceptorBase**: Template method pattern correctly implemented
- [ ] **JdbcInterceptorConfig**: Interface defined with 4 core methods
- [ ] **Utility Classes**: SqlContextBuilder and JdbcAuditEventBuilder functional
- [ ] **Package Structure**: `com.footstone.sqlguard.interceptor.jdbc.common`
- [ ] **Backward Compatibility**: 100% - old enums deprecated but functional
- [ ] **Test Coverage**: All 30+ tests passing
- [ ] **Documentation**: Migration guide created
- [ ] **Compilation**: Module compiles independently

---

## Reference Documents

### Required Reading
1. **Test Design Document**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md`
   - Section 2: Test Fixture Design (AbstractJdbcModuleTest)
   - Section 3: Module Isolation Tests (Tests 3.1-3.15)
   - Section 4: Backward Compatibility Tests (Tests 4.1-4.12)

2. **Architecture Review**: `.apm/Memory/Phase_11_Reference_Architecture/Architecture_Review_Report.md`
   - Minimal dependency principle
   - Module separation rationale

3. **Module Separation Strategy**: `.apm/Memory/Phase_11_Reference_Architecture/Module_Separation_And_Version_Compatibility.md`
   - Provided scope strategy
   - Dependency management patterns

### Current Codebase References
- **Current Druid ViolationStrategy**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/druid/ViolationStrategy.java`
- **Current HikariCP ViolationStrategy**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/hikari/ViolationStrategy.java`
- **Current P6Spy ViolationStrategy**: `sql-guard-jdbc/src/main/java/com/footstone/sqlguard/interceptor/p6spy/ViolationStrategy.java`

---

## Deliverable Format

**Memory Log**: `.apm/Memory/Phase_11_JDBC_Module_Separation/Task_11_2_Common_Module_Extraction.md`

**Structure**:
```markdown
# Task 11.2 - Common Module Extraction

## Summary
[Brief overview of work completed]

## Details
- Module structure created
- ViolationStrategy unified
- JdbcInterceptorBase implemented
- Tests written and passing

## Output
- Files created
- POM structure
- Package organization

## Issues
[Any issues encountered and resolutions]

## Important Findings
[Technical decisions, migration notes]

## Next Steps
[Handoff to Tasks 11.3-11.5]
```

---

## Success Metrics

- [ ] **30+ tests** implemented and passing
- [ ] **Common module** compiles independently
- [ ] **Dependency validation** passes (Maven Enforcer)
- [ ] **Backward compatibility** 100% preserved
- [ ] **Template method pattern** correctly implemented
- [ ] **Migration guide** documented
- [ ] **Code review** passed (Manager Agent verification)

---

## Notes for Implementation Agent

**Agent_Core_Engine_Foundation**: This is foundational work - Tasks 11.3-11.5 depend on your output.

**Critical Requirements**:
1. **TDD Rigor**: Write tests BEFORE implementation (RED-GREEN-REFACTOR)
2. **Dependency Discipline**: Zero connection pool dependencies (Maven Enforcer validates)
3. **Backward Compatibility**: 100% - existing code must work unchanged
4. **Template Method Pattern**: Follow GoF design pattern precisely
5. **Documentation**: Migration guide is mandatory for deprecated APIs

**Timeline**: 2 days. Output blocks 3 downstream tasks.

**Quality Gates**:
- All 30+ tests passing
- Maven Enforcer validation passing
- Existing JDBC tests still passing (smoke test)
- Module compiles independently

---

**Task Assignment Complete. Agent_Core_Engine_Foundation may begin execution.**
