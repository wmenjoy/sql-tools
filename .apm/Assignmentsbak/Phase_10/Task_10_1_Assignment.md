---
task_ref: "Task 10.1 - Project Foundation & Architecture Setup"
agent_assignment: "Agent_Audit_Service"
memory_log_path: ".apm/Memory/Phase_10_Audit_Service/Task_10_1_Project_Foundation_Architecture_Setup.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Project Foundation & Architecture Setup

## Task Reference
Implementation Plan: **Task 10.1 - Project Foundation & Architecture Setup** assigned to **Agent_Audit_Service**

## Context from Dependencies
This task depends on Phase 9 Output (audit checkers compiled as library JAR dependency):

**Integration Steps (complete before main task):**
1. Verify `sql-guard-audit-checker` module exists with all Phase 9 checkers (AbstractAuditChecker, SlowQueryChecker, ActualImpactNoWhereChecker, LargeResultChecker, UnboundedReadChecker, ErrorPatternChecker)
2. Verify `sql-guard-audit-api` module exists with AuditEvent, AuditLogWriter interfaces from Phase 8
3. Review Phase 9 checker implementations at `sql-guard-audit-checker/src/main/java/` for API understanding

**Producer Output Summary:**
- Phase 8.1: AuditEvent model, AuditLogWriter interface, JSON schema
- Phase 9: AbstractAuditChecker base class, ExecutionResult, RiskScore, AuditResult models
- Phase 9 Checkers: SlowQueryChecker, ActualImpactNoWhereChecker, LargeResultChecker, UnboundedReadChecker, ErrorPatternChecker

**Integration Requirements:**
- New `sql-audit-service` module must declare dependency on `sql-guard-audit-checker` and `sql-guard-audit-api`
- Use Java 21 for new service (existing library modules remain Java 8 compatible)
- Service is standalone microservice, no integration with existing business code required

## Objective
Establish sql-audit-service project structure as independent Spring Boot 3.2+ application with Java 21 baseline, configure Maven multi-module layout separating audit-service-core (business logic), audit-service-web (REST API), and audit-service-consumer (Kafka integration), and establish Virtual Thread executor configuration for high-concurrency audit processing.

## Detailed Instructions

Complete in **5 exchanges**, one step per response. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: Maven Multi-Module Project Structure (TDD)
**先写测试，再实现：**

1. **Write test class AuditServiceBuildTest** covering:
   - `testParentPomCompilation_shouldSucceed()`
   - `testAllModulesCompilation_shouldSucceed()`
   - `testJava21Features_shouldCompile()` (Record classes, Pattern Matching)
   - `testDependencyResolution_shouldResolveCorrectly()`
   - `testMultiModuleDependency_coreToWeb_shouldWork()`
   - `testMultiModuleDependency_coreToConsumer_shouldWork()`
   - `testMavenProfileActivation_dev_shouldUseDevConfig()`
   - `testMavenProfileActivation_prod_shouldUseProdConfig()`

2. **Then implement:**
   - Create `sql-audit-service/` directory as new Maven module
   - Create parent POM with Java 21 configuration:
     ```xml
     <java.version>21</java.version>
     <spring-boot.version>3.2.0</spring-boot.version>
     ```
   - Create sub-modules:
     - `sql-audit-service-core/` - 业务逻辑，无Spring依赖
     - `sql-audit-service-web/` - Spring MVC REST API
     - `sql-audit-service-consumer/` - Kafka consumer

3. **Verify:** `mvn clean compile` succeeds for all modules

### Step 2: Virtual Thread Configuration (TDD)
**先写测试，再实现：**

1. **Write test class VirtualThreadConfigurationTest** covering:
   - `testVirtualThreadExecutor_shouldCreate()`
   - `testVirtualThreadExecutor_shouldHandleConcurrency10k()` [性能基准]
   - `testVirtualThreadExecutor_shouldNotExhaustMemory()`
   - `testVirtualThreadExecutor_shouldHandleException()`
   - `testVirtualThreadExecutor_shouldPropagateThreadLocal()`
   - `testVirtualThreadExecutor_shouldSupportTimeout()`
   - `testCompletableFutureAllOf_shouldReplaceStructuredConcurrency()` [Critical Fix C1验证]
   - `testVirtualThreadMonitoring_shouldExposeMetrics()` [Medium Fix M10]
   - `testVirtualThreadFallback_shouldFallbackToPlatformThreads()` [M10备用]
   - `testAsyncAnnotation_shouldUseVirtualThreads()`
   - `testKafkaListener_shouldUseVirtualThreads()`

2. **Then implement VirtualThreadConfig:**
   ```java
   @Configuration
   public class VirtualThreadConfig {
       @Bean
       public Executor virtualThreadExecutor() {
           return Executors.newVirtualThreadPerTaskExecutor();
       }
   }
   ```

3. **Configure spring.threads.virtual.enabled=true** in application.yml

4. **Verify:** Virtual Thread tests pass, concurrency test handles 10k tasks

### Step 3: Spring Boot 3.2+ Application Setup (TDD)
**先写测试，再实现：**

1. **Write test class SpringBoot3IntegrationTest** covering:
   - `testApplicationContext_shouldLoad()`
   - `testAutoConfiguration_shouldEnableVirtualThreads()`
   - `testHealthEndpoint_shouldReturnUp()`
   - `testActuatorEndpoints_shouldExpose()`
   - `testPrometheusMetrics_shouldExport()`
   - `testApplicationYaml_devProfile_shouldLoad()`
   - `testApplicationYaml_stagingProfile_shouldLoad()`
   - `testApplicationYaml_prodProfile_shouldLoad()`
   - `testBeanCreation_allModules_shouldSucceed()`
   - `testComponentScan_shouldDiscoverAllBeans()`

2. **Then implement:**
   - Create `AuditServiceApplication.java` with `@SpringBootApplication`
   - Create `application.yml` with profiles:
     - `dev`: 本地开发配置
     - `staging`: 测试环境配置
     - `prod`: 生产环境配置
   - Configure Actuator endpoints (health, prometheus, info)

3. **Verify:** Application starts, health endpoint returns UP

### Step 4: Docker Compose Environment (TDD)
**先写测试，再实现：**

1. **Write test class DockerComposeIntegrationTest** (使用Testcontainers) covering:
   - `testKafkaContainer_shouldStart()`
   - `testKafkaContainer_shouldAcceptConnections()`
   - `testPostgreSQLContainer_shouldStart()`
   - `testPostgreSQLContainer_shouldExecuteQueries()`
   - `testClickHouseContainer_shouldStart()` [可选测试，可用H2替代]
   - `testClickHouseContainer_shouldAcceptInserts()`
   - `testAuditServiceContainer_shouldStart()`
   - `testAuditServiceContainer_shouldConnectToKafka()`
   - `testFullStack_shouldCommunicate()` [端到端]
   - `testHealthChecks_allServices_shouldBeHealthy()`

2. **Then create docker-compose.yml:**
   ```yaml
   services:
     kafka:
       image: confluentinc/cp-kafka:7.5.0
     postgresql:
       image: postgres:15
     clickhouse:  # 可选
       image: clickhouse/clickhouse-server:23.8
     audit-service:
       build: .
       depends_on: [kafka, postgresql]
   ```

3. **Verify:** `docker-compose up` starts all services, health checks pass

### Step 5: Performance Baseline Verification
**验证所有性能目标：**

1. Run all test classes:
   - AuditServiceBuildTest (8 tests)
   - VirtualThreadConfigurationTest (12 tests)
   - SpringBoot3IntegrationTest (10 tests)
   - DockerComposeIntegrationTest (10 tests)

2. **Performance targets verification:**
   - Virtual Thread创建: <1μs per thread
   - 应用启动时间: <10s (包含所有模块)
   - Docker Compose启动: <60s (完整技术栈)

3. **Final verification:** `mvn clean test` - all 40+ tests pass

## Expected Output
- **Deliverables:**
  - Maven multi-module project: `sql-audit-service/` with parent POM (Java 21)
  - 3 sub-modules: `sql-audit-service-core/`, `sql-audit-service-web/`, `sql-audit-service-consumer/`
  - Virtual Thread executor configuration
  - Docker Compose: Kafka, PostgreSQL, ClickHouse (optional), audit service
  - application.yml with 3 profiles (dev/staging/prod)

- **Success criteria:**
  - 40+ tests passing (8+12+10+10)
  - `mvn clean test` succeeds
  - `docker-compose up` starts all services
  - Application health check returns UP

- **File locations:**
  - `sql-audit-service/pom.xml` (parent)
  - `sql-audit-service/sql-audit-service-core/`
  - `sql-audit-service/sql-audit-service-web/`
  - `sql-audit-service/sql-audit-service-consumer/`
  - `sql-audit-service/docker-compose.yml`
  - `sql-audit-service/src/main/resources/application.yml`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_10_Audit_Service/Task_10_1_Project_Foundation_Architecture_Setup.md`
Follow .apm/guides/Memory_Log_Guide.md instructions.

## Technical Notes
1. **Java 21 新特性使用策略:**
   - ✅ Virtual Threads: `Executors.newVirtualThreadPerTaskExecutor()`
   - ✅ Record Classes: 用于不可变领域模型
   - ✅ Pattern Matching: 用于SQL类型切换
   - ❌ Structured Concurrency: Preview特性，使用CompletableFuture.allOf()替代

2. **独立服务，无业务集成:**
   - 不需要考虑与现有代码的兼容性
   - 可以自由使用最新SDK和框架版本
   - 充分利用Virtual Thread的协程优势

3. **依赖声明:**
   ```xml
   <dependency>
     <groupId>com.footstone.sqlguard</groupId>
     <artifactId>sql-guard-audit-checker</artifactId>
     <version>${project.version}</version>
   </dependency>
   <dependency>
     <groupId>com.footstone.sqlguard</groupId>
     <artifactId>sql-guard-audit-api</artifactId>
     <version>${project.version}</version>
   </dependency>
   ```

---

**Assignment Created:** 2025-12-18
**Manager Agent:** Manager_Agent_3
**Status:** Ready for Assignment
