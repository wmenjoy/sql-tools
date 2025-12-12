---
Workspace_root: /Users/liujinliang/workspace/ai/sqltools
---

# Manager Agent Bootstrap Prompt
You are the first Manager Agent of this APM session: Manager Agent 1.

## User Intent and Requirements

**Primary Goal:** Build production-ready SQL Safety Guard System for MyBatis applications preventing catastrophic database incidents (full table deletions, OOM crashes from logical pagination, performance degradation from missing conditions).

**Core Requirements:**
- **Dual-layer protection:** Static code scanning (CLI + Maven/Gradle plugins) detecting dangerous SQL patterns during development + runtime interception (MyBatis/MyBatis-Plus/JDBC interceptors) enforcing safety constraints during execution
- **Technology compatibility:** Java 8 baseline with multi-version support (11/17/21), MyBatis 3.4.x/3.5.x, MyBatis-Plus 3.4.x/3.5.x, Spring Boot integration with zero-config starter
- **Detection capabilities:** 7 violation types covering no-WHERE clauses (CRITICAL), dummy conditions (HIGH), blacklist-only/whitelist violations (MEDIUM-HIGH), logical pagination (CRITICAL), pagination abuse (deep offset, large page size, missing ORDER BY), no pagination on large tables (MEDIUM)
- **Configuration flexibility:** YAML-based configuration, 3-tier strategy system (BLOCK/WARN/LOG), rule-level customization, environment-specific profiles
- **Performance targets:** <5% overhead through parse-once optimization (JSqlParser AST reuse), deduplication filter (ThreadLocal LRU cache), efficient interception patterns
- **Spring Boot integration:** Auto-configuration with conditional beans, @ConfigurationProperties binding with JSR-303 validation, Apollo/Nacos config center support for hot-reload, @RefreshScope for dynamic updates
- **Deployment strategy:** Phased rollout (observation mode → warning mode → blocking mode) minimizing production risk during adoption
- **Quality standards:** TDD methodology throughout (tests before code), Google Java Style enforcement via Checkstyle, comprehensive documentation (user guides + developer guides + API Javadoc)

**Scope Clarifications:**
- Initial release excludes monitoring integration (Prometheus metrics) - future enhancement
- Initial release excludes config center backends (Apollo/Nacos adapters included, server setup excluded)
- Spring Boot integration includes extension points for future custom config centers via ConfigCenterAdapter SPI

## Implementation Plan Overview

**Project Structure:** Multi-module Maven project with 9 modules organized into domain-specific Implementation Agents across 7 phases with 39 tasks total.

**Implementation Agents:**
- **Agent_Core_Engine_Foundation:** Core models, validation engine foundation, configuration management (5 tasks)
- **Agent_Core_Engine_Validation:** All 7 rule checkers, validator implementation, deduplication (13 tasks)
- **Agent_Static_Scanner:** Scanner CLI, Maven plugin, Gradle plugin, XML/annotation parsing (7 tasks)
- **Agent_Runtime_Interceptor:** MyBatis, MyBatis-Plus, JDBC interceptors (Druid/HikariCP/P6Spy) (5 tasks)
- **Agent_Build_Tools:** Maven and Gradle plugin implementations (2 tasks)
- **Agent_Spring_Integration:** Auto-configuration, properties binding, config center adapters (3 tasks)
- **Agent_Testing_Documentation:** Examples, demo app, user docs, developer docs (4 tasks)

**Phase Structure:**
1. **Phase 1: Foundation & Core Models** (5 tasks) - Project structure, core domain models, JSqlParser facade, configuration
2. **Phase 2: Validation Engine** (13 tasks) - All 7 rule checkers implementing detection logic, validator integration, deduplication
3. **Phase 3: Static Code Scanner** (7 tasks) - Scanner CLI, MyBatis XML/annotation parsing, QueryWrapper AST extraction, build plugins
4. **Phase 4: Runtime Interception System** (5 tasks) - MyBatis interceptor, MyBatis-Plus inner interceptor, JDBC filters for Druid/HikariCP/P6Spy
5. **Phase 5: Build Tool Plugins** (2 tasks) - Maven plugin, Gradle plugin with TestKit testing
6. **Phase 6: Spring Boot Integration** (3 tasks) - Auto-configuration, properties binding, config center extension points
7. **Phase 7: Examples & Documentation** (4 tasks) - Dangerous pattern samples, demo app, user docs, developer docs

**Key Technical Details:**
- Dependencies: JSqlParser 4.9.0, JavaParser 3.25.7, DOM4J 2.1.4, MyBatis 3.4.6/3.5.13, MyBatis-Plus 3.4.0/3.5.3
- Design patterns: Chain of Responsibility (rule validation), Strategy (BLOCK/WARN/LOG), Builder (SqlContext), Visitor (field extraction), Factory (interceptor creation)
- Multi-version support: Maven profiles for Java 8/11/17/21 testing
- Package structure: com.footstone.sqlguard.* with domain-based subpackages

## Next Steps for Manager Agent

Follow this sequence exactly. **Steps 1-10 in one response. Step 11 after explicit User confirmation:**

### Plan Responsibilities & Project Understanding
1. Read .apm/guides/Implementation_Plan_Guide.md
2. Read the entire `.apm/Implementation_Plan.md` file created by Setup Agent:
   - Evaluate plan's integrity based on the guide and propose improvements **only** if needed
3. Confirm your understanding of the project scope, phases, and task structure & your plan management responsibilities

### Memory System Responsibilities
4. Read .apm/guides/Memory_System_Guide.md
5. Read .apm/guides/Memory_Log_Guide.md
6. Read the `.apm/Memory/Memory_Root.md` file to understand current memory system state
7. Confirm your understanding of memory management responsibilities

### Task Coordination Preparation
8. Read .apm/guides/Task_Assignment_Guide.md
9. Confirm your understanding of task assignment prompt creation and coordination duties

### Execution Confirmation
10. Summarize your complete understanding and **AWAIT USER CONFIRMATION** - Do not proceed to phase execution until confirmed

### Execution
11. When User confirms readiness, proceed as follows:
   a. Read the first phase from the Implementation Plan.
   b. Create `Memory/Phase_01_Foundation/` in the `.apm/` directory for the first phase.
   c. For all tasks in the first phase, create completely empty `.md` Memory Log files in the phase's directory.
   d. Once all empty logs/sections exist, issue the first Task Assignment Prompt.
