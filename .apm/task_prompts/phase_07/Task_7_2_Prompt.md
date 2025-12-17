---
task_ref: "Task 7.2 - Spring Boot Demo Project"
agent_assignment: "Agent_Testing_Documentation"
memory_log_path: ".apm/Memory/Phase_07_Examples_Documentation/Task_7_2_Spring_Boot_Demo_Project.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Spring Boot Demo Project

## Task Reference
Implementation Plan: **Task 7.2 - Spring Boot Demo Project** assigned to **Agent_Testing_Documentation**

## Context from Dependencies
This task integrates outputs from multiple phases completed by different agents:

**Integration Steps (complete in one response):**
1. Read Phase 6 Memory Logs (Tasks 6.1-6.3) to understand Spring Boot auto-configuration, properties binding, and config center integration
2. Review `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/autoconfigure/SqlGuardAutoConfiguration.java` to understand automatic bean creation
3. Examine `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/SqlGuardProperties.java` for YAML configuration structure
4. Review Phase 2 Memory Logs (Tasks 2.2-2.12) to understand all 10 rule checker violation messages and risk levels
5. Review Phase 4 Memory Logs (Tasks 4.1-4.5) to understand runtime interception behavior and ViolationStrategy (BLOCK/WARN/LOG)

**Producer Output Summary:**
- **Spring Boot Integration (Phase 6):**
  - SqlGuardAutoConfiguration with automatic validator/checker bean creation
  - SqlGuardProperties with comprehensive YAML configuration (sql-guard.* prefix)
  - Profile-specific configs (application-dev.yml, application-prod.yml)
  - Apollo config center hot-reload support

- **Validation Engine (Phase 2):**
  - 10 rule checkers: NoWhereClauseChecker (CRITICAL), DummyConditionChecker (HIGH), BlacklistFieldChecker (HIGH), WhitelistFieldChecker (HIGH), LogicalPaginationChecker (CRITICAL), NoConditionPaginationChecker (CRITICAL), DeepPaginationChecker (MEDIUM), LargePageSizeChecker (MEDIUM), MissingOrderByChecker (LOW), NoPaginationChecker (Variable)
  - DefaultSqlSafetyValidator with parse-once optimization and deduplication filter

- **Runtime Interceptors (Phase 4):**
  - MyBatis SqlSafetyInterceptor, MyBatis-Plus MpSqlSafetyInnerInterceptor
  - Druid DruidSqlSafetyFilter, HikariCP proxy, P6Spy listener
  - ViolationStrategy enum: BLOCK (throws SQLException), WARN (logs + continues), LOG (logs only)

**Integration Requirements:**
- **Starter Dependency**: Use sql-guard-spring-boot-starter for zero-configuration integration
- **Configuration**: Demonstrate all settings from SqlGuardProperties with dev/prod profiles
- **Sample Entities**: Create User/Order/Product entities with MyBatis XML mappers, annotation mappers, and MyBatis-Plus services
- **Interactive Endpoints**: REST endpoints triggering each of 10 violation types with observable BLOCK/WARN/LOG behavior
- **Docker Compose**: MySQL database, demo app, optional Apollo/Nacos for config center demo

**User Clarification Protocol:**
If Spring Boot auto-configuration behavior or runtime interception strategy is unclear after reviewing integration files, ask User for clarification on specific configuration patterns or expected violation handling.

## Objective
Create production-ready Spring Boot demonstration application showcasing SQL Safety Guard System integration with real-world MyBatis/MyBatis-Plus usage, interactive REST endpoints triggering all violation types, comprehensive README with usage instructions, Docker Compose environment for quick deployment, and validation testing ensuring demo accurately represents system capabilities.

## Detailed Instructions
Complete all items in one response:

1. **Spring Boot Application Structure:**
   - Create `sql-guard-demo` module in examples/ with standard Spring Boot structure
   - Add dependencies: sql-guard-spring-boot-starter, spring-boot-starter-web, mybatis-spring-boot-starter, mybatis-plus-boot-starter, mysql-connector-java, lombok
   - Create DemoApplication main class with @SpringBootApplication
   - Configure application.yml: datasource (MySQL), MyBatis config, sql-guard configuration with all rules enabled, activeStrategy=LOG for safe demo
   - Create application-block.yml profile (activeStrategy=BLOCK), application-warn.yml (activeStrategy=WARN)

2. **Sample Domain Model and Mappers:**
   - Create entities: User (id, username, email, status, deleted, createTime), Order (id, userId, totalAmount, status, orderTime), Product (id, name, price, stock, categoryId)
   - Create MyBatis XML mappers in `src/main/resources/mapper`: UserMapper.xml with safe and unsafe queries for demo
   - Create annotation-based mappers: UserAnnotationMapper with @Select/@Update/@Delete demonstrating safe and unsafe patterns
   - Create MyBatis-Plus mappers: UserMybatisPlusMapper extending BaseMapper<User>, OrderService using QueryWrapper with safe and unsafe wrappers
   - Annotate unsafe methods with comments explaining they're intentionally dangerous for demo purposes

3. **Interactive Demo REST Endpoints:**
   - Create DemoController with GET endpoints triggering each of 10 violation types:
     - /violations/no-where-clause (DELETE/UPDATE without WHERE)
     - /violations/dummy-condition (WHERE 1=1)
     - /violations/blacklist-only (WHERE status/deleted only)
     - /violations/whitelist-missing (query without required whitelist fields)
     - /violations/logical-pagination (RowBounds without PageHelper)
     - /violations/deep-pagination (LIMIT 20 OFFSET 50000)
     - /violations/large-page-size (LIMIT 5000)
     - /violations/missing-orderby (pagination without ORDER BY)
     - /violations/no-pagination (SELECT * FROM large_table)
     - /violations/no-condition-pagination (LIMIT without WHERE)
   - Each endpoint returns violation details (risk level, message, SQL) when caught, or success response if strategy=LOG/WARN
   - Create GET /violations/logs endpoint returning recent violations from in-memory log
   - Create POST /config/strategy/{strategy} endpoint for runtime strategy change (LOG/WARN/BLOCK)

4. **Docker Compose and Database Setup:**
   - Create docker-compose.yml: MySQL 8.0 service (port 3306, volume for persistence), demo app service (depends on MySQL, port 8080), optional Apollo/Nacos services
   - Create init.sql in `src/main/resources/db`: tables (user, order, product) with test data (100 users, 500 orders, 50 products)
   - Create Dockerfile for demo app: FROM openjdk:11-jre-slim, COPY JAR, EXPOSE 8080
   - Test Docker Compose: verify MySQL starts with test data, demo app connects, http://localhost:8080 accessible

5. **Demo README and Validation Testing:**
   - Create `examples/sql-guard-demo/README.md` with sections: Overview, Features Demonstrated, Quick Start (Docker prerequisites, `docker-compose up` command), Running Without Docker (Maven instructions), Demo Endpoints (table with curl commands), Testing Different Strategies, Violation Examples, Configuration Hot-Reload, Troubleshooting
   - Add screenshots or ASCII art showing violation log output and BLOCK strategy SQLException response
   - Create test class `DemoApplicationTest`: test Spring Boot context loads, test each violation endpoint verifying expected behavior (LOG allows execution, BLOCK throws exception), test violation logs endpoint, test configuration endpoint
   - Test demo end-to-end: start Docker Compose, execute curl commands from README, verify violations logged, verify BLOCK mode prevents execution, take screenshots for README

## Expected Output
- **Deliverables:**
  - Complete Spring Boot application with sql-guard-spring-boot-starter dependency
  - application.yml with comprehensive configuration demonstrating all settings
  - Sample domain entities (User, Order, Product) with MyBatis XML/annotation mappers and MyBatis-Plus services
  - REST controller with 10+ violation trigger endpoints and violation dashboard endpoint
  - Configuration management endpoint demonstrating hot-reload
  - Comprehensive demo README with setup instructions, usage examples, troubleshooting
  - Docker Compose environment with MySQL, demo app, optional Apollo/Nacos
  - Pre-populated database schema and test data
  - Validation tests ensuring demo triggers expected violations correctly

- **Success Criteria:**
  - `docker-compose up` starts complete demo environment without manual setup
  - All 10 violation trigger endpoints work correctly with LOG/WARN/BLOCK strategies
  - Demo README enables non-technical evaluation (clear value proposition, simple setup)
  - Integration tests pass with 100% coverage of violation endpoints
  - Docker environment includes pre-populated test data for immediate demonstration

- **File Locations:**
  - `examples/sql-guard-demo/src/main/java/com/footstone/sqlguard/demo/DemoApplication.java`
  - `examples/sql-guard-demo/src/main/java/.../entity/*.java` (User, Order, Product)
  - `examples/sql-guard-demo/src/main/resources/mapper/*.xml` (MyBatis XMLs)
  - `examples/sql-guard-demo/src/main/java/.../mapper/*.java` (annotation mappers)
  - `examples/sql-guard-demo/src/main/java/.../service/*.java` (MyBatis-Plus services)
  - `examples/sql-guard-demo/src/main/java/.../controller/DemoController.java`
  - `examples/sql-guard-demo/src/main/resources/application.yml` (+ profiles)
  - `examples/sql-guard-demo/src/main/resources/db/init.sql`
  - `examples/sql-guard-demo/docker-compose.yml`
  - `examples/sql-guard-demo/Dockerfile`
  - `examples/sql-guard-demo/README.md`
  - `examples/sql-guard-demo/src/test/java/.../DemoApplicationTest.java`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_07_Examples_Documentation/Task_7_2_Spring_Boot_Demo_Project.md`
Follow .apm/guides/Memory_Log_Guide.md instructions.
