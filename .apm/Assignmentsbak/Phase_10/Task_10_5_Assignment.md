---
task_ref: "Task 10.5 - REST API & Monitoring Endpoints"
agent_assignment: "Agent_Audit_Service"
memory_log_path: ".apm/Memory/Phase_10_Audit_Service/Task_10_5_REST_API_Monitoring_Endpoints.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: REST API & Monitoring Endpoints

## Task Reference
Implementation Plan: **Task 10.5 - REST API & Monitoring Endpoints** assigned to **Agent_Audit_Service**

**This is the FINAL task of Phase 10.**

## Context from Dependencies

### Task 10.3 Output (Required)
- `AuditEngine` interface for querying audit results
- `DefaultAuditEngine` implementation
- `AuditReport` model
- `CheckerResult` model

### Task 10.4 Output (Required)
- `AuditReportRepository` implementation (JpaAuditReportRepository)
- `ClickHouseExecutionLogger` for querying execution data
- `PostgreSQLOnlyStorageAdapter` for simplified queries

### Existing Infrastructure
- Spring Boot 3.2+ with Actuator (from Task 10.1)
- Virtual Thread configuration
- Micrometer metrics (from Task 10.2)

## Objective
Expose REST API for querying audit results, accessing execution statistics, generating compliance reports, and managing checker configuration. Implement Spring MVC controllers with pagination support, comprehensive error handling, API documentation via SpringDoc OpenAPI, and monitoring endpoints for service health and Kafka consumer metrics.

## Detailed Instructions

Complete in **5 exchanges**, one step per response. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: AuditReport Controller (TDD)
**先写测试，再实现：**

1. **Write test class AuditReportControllerTest** covering:
   - `testGetAudits_shouldReturnPagedResults()`
   - `testGetAudits_withFilters_shouldFilter()`
   - `testGetAudits_withSorting_shouldSort()`
   - `testGetAuditById_existingId_shouldReturn()`
   - `testGetAuditById_nonExistingId_shouldReturn404()`
   - `testGetAuditsBySqlId_shouldReturnMatches()`
   - `testGetAuditsByRiskLevel_shouldFilter()`
   - `testGetAuditsByTimeRange_shouldFilter()`
   - `testPagination_defaultPageSize_shouldBe20()`
   - `testPagination_maxPageSize_shouldBe100()`

2. **Then implement AuditReportController:**
   ```java
   @RestController
   @RequestMapping("/api/v1/audits")
   public class AuditReportController {

       @GetMapping
       public Page<AuditReportDto> getAudits(
           @RequestParam(required = false) String sqlId,
           @RequestParam(required = false) RiskLevel riskLevel,
           @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant startTime,
           @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) Instant endTime,
           Pageable pageable) {
           // Implementation
       }

       @GetMapping("/{reportId}")
       public AuditReportDto getAuditById(@PathVariable String reportId) {
           // Implementation
       }
   }
   ```

3. **Verify:** Controller tests pass with MockMvc

### Step 2: Statistics Controller (TDD)
**先写测试，再实现：**

1. **Write test class StatisticsControllerTest** covering:
   - `testGetOverview_shouldReturnAggregatedStats()`
   - `testGetTopRiskySql_shouldReturnTop10()`
   - `testGetSlowQueryTrends_shouldReturnTimeSeries()`
   - `testGetErrorRates_shouldReturnPercentages()`
   - `testGetCheckerStats_shouldReturnPerCheckerCounts()`
   - `testGetDailyStats_shouldReturnDailyBreakdown()`
   - `testGetHourlyStats_shouldReturnHourlyBreakdown()`
   - `testStatistics_caching_shouldCacheResults()`

2. **Then implement StatisticsController:**
   ```java
   @RestController
   @RequestMapping("/api/v1/statistics")
   public class StatisticsController {

       @GetMapping("/overview")
       public StatisticsOverviewDto getOverview(
           @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant startTime,
           @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant endTime) {
           // Total audits, by risk level, top checkers triggered
       }

       @GetMapping("/top-risky-sql")
       public List<RiskySqlDto> getTopRiskySql(
           @RequestParam(defaultValue = "10") int limit) {
           // Most frequently flagged SQL statements
       }

       @GetMapping("/trends/slow-queries")
       public List<TrendDataPoint> getSlowQueryTrends(
           @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant startTime,
           @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant endTime,
           @RequestParam(defaultValue = "HOURLY") Granularity granularity) {
           // Time-series data for slow query counts
       }
   }
   ```

3. **Verify:** Statistics controller tests pass

### Step 3: Configuration Controller (TDD)
**先写测试，再实现：**

1. **Write test class ConfigurationControllerTest** covering:
   - `testGetCheckers_shouldReturnAllCheckers()`
   - `testGetCheckerConfig_shouldReturnConfig()`
   - `testUpdateCheckerConfig_shouldPersist()`
   - `testEnableChecker_shouldActivate()`
   - `testDisableChecker_shouldDeactivate()`
   - `testUpdateThreshold_shouldApply()`
   - `testAddWhitelistRule_shouldAdd()`
   - `testRemoveWhitelistRule_shouldRemove()`
   - `testConfigValidation_invalidThreshold_shouldReject()`
   - `testConfigAuditLog_shouldRecordChanges()`

2. **Then implement ConfigurationController:**
   ```java
   @RestController
   @RequestMapping("/api/v1/checkers")
   public class ConfigurationController {

       @GetMapping
       public List<CheckerInfoDto> getAllCheckers() {
           // List all registered checkers with status
       }

       @GetMapping("/{checkerId}/config")
       public CheckerConfigDto getCheckerConfig(@PathVariable String checkerId) {
           // Get checker configuration
       }

       @PutMapping("/{checkerId}/config")
       public CheckerConfigDto updateCheckerConfig(
           @PathVariable String checkerId,
           @RequestBody @Valid CheckerConfigUpdateDto update) {
           // Update checker configuration
       }

       @PostMapping("/{checkerId}/whitelist")
       public void addWhitelistRule(
           @PathVariable String checkerId,
           @RequestBody @Valid WhitelistRuleDto rule) {
           // Add whitelist rule
       }
   }
   ```

3. **Verify:** Configuration controller tests pass

### Step 4: OpenAPI Documentation & Error Handling (TDD)
**先写测试，再实现：**

1. **Write test class OpenAPIDocumentationTest** covering:
   - `testSwaggerUI_shouldBeAccessible()`
   - `testOpenAPISpec_shouldBeValid()`
   - `testOpenAPISpec_allEndpoints_shouldBeDocumented()`
   - `testOpenAPISpec_examples_shouldBeValid()`
   - `testOpenAPISpec_schemas_shouldBeComplete()`
   - `testOpenAPISpec_versioning_shouldBeCorrect()`

2. **Write test class GlobalExceptionHandlerTest** covering:
   - `testNotFound_shouldReturn404()`
   - `testBadRequest_shouldReturn400()`
   - `testValidationError_shouldReturnDetails()`
   - `testInternalError_shouldReturn500()`
   - `testErrorResponse_shouldHaveStandardFormat()`

3. **Then implement:**
   ```java
   @Configuration
   public class OpenAPIConfig {
       @Bean
       public OpenAPI auditServiceOpenAPI() {
           return new OpenAPI()
               .info(new Info()
                   .title("SQL Audit Service API")
                   .version("1.0.0")
                   .description("REST API for SQL audit results and configuration"));
       }
   }

   @RestControllerAdvice
   public class GlobalExceptionHandler {
       @ExceptionHandler(ResourceNotFoundException.class)
       public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
           return ResponseEntity.status(HttpStatus.NOT_FOUND)
               .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
       }
   }
   ```

4. **Verify:** OpenAPI available at `/swagger-ui.html`, error handling tests pass

### Step 5: Health & Monitoring Endpoints
**验证所有功能：**

1. **Write test class HealthEndpointTest** covering:
   - `testHealthEndpoint_shouldReturnUp()`
   - `testHealthEndpoint_kafkaConnectivity_shouldReport()`
   - `testHealthEndpoint_databaseStatus_shouldReport()`
   - `testHealthEndpoint_clickHouseStatus_shouldReport()`

2. **Write test class MetricsEndpointTest** covering:
   - `testPrometheusEndpoint_shouldExpose()`
   - `testPrometheusEndpoint_kafkaMetrics_shouldInclude()`
   - `testPrometheusEndpoint_auditMetrics_shouldInclude()`
   - `testPrometheusEndpoint_jvmMetrics_shouldInclude()`

3. **Performance targets verification:**
   | 指标 | 目标值 |
   |------|--------|
   | API 响应时间 | <100ms (p95) |
   | 查询 API 分页 | <200ms (p95) |
   | 统计 API 聚合 | <500ms (p95) |
   | Actuator 响应 | <50ms |
   | Prometheus 抓取 | <5s |

4. **Final verification:** `mvn clean test -pl sql-audit-service-web` - all 50+ tests pass

## Expected Output
- **Deliverables:**
  - `AuditReportController` with pagination and filtering
  - `StatisticsController` with aggregation endpoints
  - `ConfigurationController` for checker management
  - `GlobalExceptionHandler` for consistent error responses
  - `OpenAPIConfig` with SpringDoc configuration
  - DTO classes for API responses
  - Custom health indicators

- **Success criteria:**
  - 50+ tests passing
  - `mvn clean test` succeeds
  - Swagger UI accessible at `/swagger-ui.html`
  - All Actuator endpoints functional
  - API response times within targets

- **File locations:**
  - `sql-audit-service-web/src/main/java/com/footstone/audit/service/web/`
    - `controller/AuditReportController.java`
    - `controller/StatisticsController.java`
    - `controller/ConfigurationController.java`
    - `dto/` (all DTO classes)
    - `exception/GlobalExceptionHandler.java`
    - `config/OpenAPIConfig.java`
    - `health/KafkaHealthIndicator.java`
    - `health/ClickHouseHealthIndicator.java`
  - `sql-audit-service-web/src/test/java/...` (all test classes)

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_10_Audit_Service/Task_10_5_REST_API_Monitoring_Endpoints.md`
Follow .apm/guides/Memory_Log_Guide.md instructions.

## Technical Notes
1. **SpringDoc OpenAPI 配置:**
   ```yaml
   springdoc:
     api-docs:
       path: /api-docs
     swagger-ui:
       path: /swagger-ui.html
       operationsSorter: method
   ```

2. **分页配置:**
   ```yaml
   spring:
     data:
       web:
         pageable:
           default-page-size: 20
           max-page-size: 100
   ```

3. **健康检查配置:**
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,prometheus,info
     health:
       show-details: always
   ```

4. **依赖版本:**
   ```xml
   <springdoc.version>2.3.0</springdoc.version>
   ```

5. **API 版本策略:**
   - URL 路径版本: `/api/v1/...`
   - 后续版本: `/api/v2/...` (保持向后兼容)

---

**Assignment Created:** 2025-12-18
**Manager Agent:** Manager_Agent_3
**Status:** Ready for Assignment
**Prerequisite:** Task 10.3 + Task 10.4 Completed
**Note:** This is the FINAL task of Phase 10
