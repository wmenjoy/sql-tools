---
task_ref: "Task 1.1 - Project Structure & Multi-Module Build Configuration"
agent_assignment: "Agent_Core_Engine_Foundation"
memory_log_path: ".apm/Memory/Phase_01_Foundation/Task_1_1_Project_Structure_Multi_Module_Build_Configuration.md"
execution_type: "multi-step"
dependency_context: false
ad_hoc_delegation: false
---

# APM Task Assignment: Project Structure & Multi-Module Build Configuration

## Task Reference
Implementation Plan: **Task 1.1 - Project Structure & Multi-Module Build Configuration** assigned to **Agent_Core_Engine_Foundation**

## Objective
Establish complete Maven multi-module project foundation with standardized structure, dependency management, quality enforcement, and multi-version Java compatibility for the entire SQL Safety Guard System.

## Detailed Instructions
Complete this task in **6 exchanges, one step per response**. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Parent POM Setup
Create parent `pom.xml` in project root with:
- **GroupId:** `com.footstone`
- **ArtifactId:** `sql-safety-guard-parent`
- **Packaging:** `pom`
- **Properties Section:** Define all dependency versions:
  - JSqlParser 4.9.0
  - JavaParser 3.25.7
  - DOM4J 2.1.4
  - MyBatis 3.4.6/3.5.13
  - MyBatis-Plus 3.4.0/3.5.3
  - SLF4J 1.7.36
  - Logback 1.2.12
  - SnakeYAML 1.33
  - JUnit 5.10.1
  - Mockito 4.11.0
- **DependencyManagement Section:** Import all libraries with versions from properties
- **Compiler Settings:** Set maven.compiler.source and maven.compiler.target to 1.8, project.build.sourceEncoding to UTF-8

### Step 2: Multi-Version Profiles
Define Maven profiles in parent POM for Java version compatibility:
- **Profile ID `java11`:** Override compiler source/target to 11
- **Profile ID `java17`:** Override compiler source/target to 17
- **Profile ID `java21`:** Override compiler source/target to 21
- **Documentation:** Document profile activation in project README for CI/CD matrix builds

### Step 3: Module POM Creation
Create child module directories and POMs for all nine modules:
1. `sql-scanner-core` (core scanning engine)
2. `sql-scanner-cli` (CLI tool)
3. `sql-scanner-maven` (Maven plugin)
4. `sql-scanner-gradle` (Gradle plugin)
5. `sql-guard-core` (validation engine)
6. `sql-guard-mybatis` (MyBatis interceptor)
7. `sql-guard-mp` (MyBatis-Plus interceptor)
8. `sql-guard-jdbc` (JDBC layer interceptors)
9. `sql-guard-spring-boot-starter` (Spring Boot auto-configuration)

**For each module POM:**
- Specify parent reference to sql-safety-guard-parent
- Declare direct dependencies (no versions - inherited from parent)
- Configure inter-module dependencies where needed (e.g., sql-guard-mybatis depends on sql-guard-core)

### Step 4: Directory Structure
For each of the nine modules, create standard Maven layout:
- `src/main/java` (production source)
- `src/main/resources` (configuration files)
- `src/test/java` (test source)
- `src/test/resources` (test resources)

Establish base package structure:
- `com.footstone.sqlguard.core` (core models)
- `com.footstone.sqlguard.scanner` (scanning engine)
- `com.footstone.sqlguard.interceptor` (interceptors)
- `com.footstone.sqlguard.config` (configuration)

### Step 5: Build Plugin Configuration
In parent POM `<build><pluginManagement>`, configure:
- **maven-compiler-plugin** version 3.11.0:
  - source/target 1.8
  - encoding UTF-8
  - showWarnings true
- **maven-surefire-plugin** version 3.2.3 (JUnit 5 support)
- **maven-source-plugin** version 3.3.0 (attach sources on package)
- **maven-javadoc-plugin** version 3.6.3 (generate javadoc JAR)
- **checkstyle-maven-plugin** version 3.3.1:
  - Use Google Java Style (google_checks.xml)
  - Bind to verify phase
  - Set failOnViolation true

### Step 6: Build Verification
Execute build verification:
1. Run `mvn clean compile` from project root to verify parent + all nine modules compile successfully
2. Create placeholder test class `com.footstone.sqlguard.core.FoundationTest` in sql-guard-core module:
   - Simple JUnit 5 @Test annotated method
   - Use Mockito mock() to verify test infrastructure (JUnit Platform + Mockito framework) is properly configured
3. Run `mvn test` to confirm test execution works
4. Check Checkstyle reports to ensure Google Java Style enforcement is active

**Constraints:**
- Use Maven 3.6+ best practices for parent POM structure
- Configure all dependency versions in parent POM `<dependencyManagement>` to ensure consistency
- Set Java 8 as baseline (source/target 1.8) with profiles for 11/17/21 testing
- Integrate Google Java Style via Checkstyle plugin with enforcement in verify phase to prevent style violations early
- The placeholder test class validates JUnit 5 + Mockito 4.x integration works correctly before real test development begins
- All nine modules must compile successfully to ensure proper dependency resolution

## Expected Output
- **Parent POM:** Complete parent pom.xml with dependency management, profiles, and plugin configuration
- **Nine Module POMs:** All module pom.xml files with proper inheritance and inter-module dependencies
- **Directory Structure:** Standard Maven layout across all modules with package hierarchy
- **Build Verification:** Successful `mvn clean compile` and `mvn test` execution
- **Success Criteria:** All modules compile without errors, placeholder test passes, Checkstyle enforcement active

**File Locations:**
- Parent POM: `/Users/liujinliang/workspace/ai/sqltools/pom.xml`
- Module POMs: `/Users/liujinliang/workspace/ai/sqltools/<module-name>/pom.xml`
- Module directories: `/Users/liujinliang/workspace/ai/sqltools/<module-name>/src/{main,test}/{java,resources}`
- Placeholder test: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/test/java/com/footstone/sqlguard/core/FoundationTest.java`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Foundation/Task_1_1_Project_Structure_Multi_Module_Build_Configuration.md`

Follow .apm/guides/Memory_Log_Guide.md instructions.
