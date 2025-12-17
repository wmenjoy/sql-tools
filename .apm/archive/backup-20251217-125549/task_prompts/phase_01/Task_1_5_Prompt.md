---
task_ref: "Task 1.5 - Logging Infrastructure Setup"
agent_assignment: "Agent_Core_Engine_Foundation"
memory_log_path: ".apm/Memory/Phase_01_Foundation/Task_1_5_Logging_Infrastructure_Setup.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Logging Infrastructure Setup

## Task Reference
Implementation Plan: **Task 1.5 - Logging Infrastructure Setup** assigned to **Agent_Core_Engine_Foundation**

## Context from Dependencies
Based on your Task 1.1 work, use the project structure you established:
- Configure logging in `sql-guard-core` and `sql-scanner-core` modules
- Use SLF4J 1.7.36 and Logback 1.2.12 from parent POM dependency management
- Create configuration files in src/main/resources and src/test/resources

## Objective
Configure SLF4J and Logback logging framework across core modules providing consistent log formatting, appropriate log levels for development and testing, and verification that logging infrastructure works correctly before core development begins.

## Detailed Instructions
Complete all items in **one response**.

### 1. Dependency Configuration
In parent POM `<dependencyManagement>`:
- Add SLF4J API version 1.7.36 (groupId org.slf4j, artifactId slf4j-api)
- Add Logback Classic version 1.2.12 (groupId ch.qos.logback, artifactId logback-classic)
  - Note: Logback Classic transitively includes logback-core

In `sql-guard-core/pom.xml` and `sql-scanner-core/pom.xml`:
- Add SLF4J API dependency (without version tag, inherited from parent)
- Add Logback Classic dependency (without version tag)
  - Note: Logback Classic includes SLF4J binding, so no additional binding dependency needed

### 2. Production Logging Configuration
Create file `src/main/resources/logback.xml` in sql-guard-core module:
- `<configuration>` root element
- Define `<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">`
  - `<encoder>` pattern: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`
    - Includes: timestamp, thread name, log level padded to 5 chars, logger name truncated to 36 chars, message, newline
- Configure `<logger name="com.footstone.sqlguard" level="DEBUG"/>` for development visibility
- Set `<root level="INFO"><appender-ref ref="CONSOLE"/></root>` to reduce third-party library noise

Copy this logback.xml to `sql-scanner-core/src/main/resources` as well for consistency.

### 3. Test Logging Configuration
Create file `src/test/resources/logback-test.xml` in sql-guard-core module:
- Note: Logback prioritizes logback-test.xml during test execution, overriding logback.xml
- Define console appender with simpler pattern: `%d{HH:mm:ss.SSS} %-5level %logger{20} - %msg%n`
  - Shorter timestamp, truncated logger for readability
- Configure `<logger name="com.footstone.sqlguard" level="DEBUG"/>` to enable our debug logs during testing
- Set `<root level="WARN">` to suppress INFO logs from test frameworks (JUnit, Mockito) and third-party libraries

Copy to `sql-scanner-core/src/test/resources`.

### 4. Logging Verification Test
Create test class `LoggingInfrastructureTest` in:
- `sql-guard-core/src/test/java/com/footstone/sqlguard/core/`

With JUnit 5 @Test method `testLoggingWorks()`:
- Get SLF4J Logger via `LoggerFactory.getLogger(LoggingInfrastructureTest.class)`
- Log messages at different levels:
  - `logger.trace("Trace message")`
  - `logger.debug("Debug message")`
  - `logger.info("Info message")`
  - `logger.warn("Warn message")`
  - `logger.error("Error message")`
- Run test with `mvn test`
- Verify console output shows debug/info/warn/error messages
  - Note: trace not shown since root level WARN in logback-test.xml, but com.footstone.sqlguard at DEBUG shows debug
- Verify logback-test.xml is applied (shorter timestamp format visible)
- Verify no duplicate log entries (single appender)
- Check log output format matches expected pattern with timestamp, level, logger name, message

**Constraints:**
- SLF4J provides logging API abstraction while Logback implements actual logging
- Configure dependencies in parent POM `<dependencyManagement>` for version consistency
- Production config (logback.xml) should output structured logs with timestamp, level, logger name, and message for operational visibility
- Test config (logback-test.xml) should reduce noise (root at WARN) while enabling DEBUG for our code to facilitate test debugging
- Verification test confirms Logback test configuration overrides production config during test execution

## Expected Output
- **SLF4J and Logback dependencies:** Properly configured in parent POM and core modules
- **Production logging:** logback.xml with structured console output in both core modules
- **Test logging:** logback-test.xml with reduced noise for test execution in both core modules
- **Verification test:** LoggingInfrastructureTest demonstrating logging works at different levels
- **Success Criteria:** Test passes, log output format correct, logback-test.xml overrides production config

**File Locations:**
- Parent POM dependency management: `/Users/liujinliang/workspace/ai/sqltools/pom.xml`
- Module dependencies: `/Users/liujinliang/workspace/ai/sqltools/{sql-guard-core,sql-scanner-core}/pom.xml`
- Production config: `/Users/liujinliang/workspace/ai/sqltools/{sql-guard-core,sql-scanner-core}/src/main/resources/logback.xml`
- Test config: `/Users/liujinliang/workspace/ai/sqltools/{sql-guard-core,sql-scanner-core}/src/test/resources/logback-test.xml`
- Verification test: `/Users/liujinliang/workspace/ai/sqltools/sql-guard-core/src/test/java/com/footstone/sqlguard/core/LoggingInfrastructureTest.java`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_01_Foundation/Task_1_5_Logging_Infrastructure_Setup.md`

Follow .apm/guides/Memory_Log_Guide.md instructions.
