---
task_ref: "Task 10.4 - Storage Layer: PostgreSQL & ClickHouse"
agent_assignment: "Agent_Audit_Service"
memory_log_path: ".apm/Memory/Phase_10_Audit_Service/Task_10_4_Storage_Layer_PostgreSQL_ClickHouse.md"
execution_type: "multi-step"
dependency_context: true
ad_hoc_delegation: false
---

# APM Task Assignment: Storage Layer - PostgreSQL & ClickHouse

## Task Reference
Implementation Plan: **Task 10.4 - Storage Layer: PostgreSQL & ClickHouse** assigned to **Agent_Audit_Service**

## Context from Dependencies

### Task 10.3 Output (Required)
This task depends on Task 10.3 completion:
- `AuditReport` record model
- `AuditReportRepository` interface (to be implemented)
- `CheckerResult` model
- `AuditProcessingResult` model

### Phase 8.1 Output (External)
- `AuditEvent` model from `sql-guard-audit-api` module

**Key Interface to Implement:**
```java
// Defined in Task 10.3 (sql-audit-service-core)
public interface AuditReportRepository {
    void save(AuditReport report);
    Optional<AuditReport> findById(String reportId);
    List<AuditReport> findByTimeRange(Instant start, Instant end);
}
```

## Objective
Implement dual-database storage strategy with PostgreSQL for audit metadata (AuditReport, checker configuration, user management) and ClickHouse for high-volume time-series SQL execution data (raw AuditEvents, execution metrics, trends). Provide JPA repositories for PostgreSQL and ClickHouse JDBC client for time-series inserts. Include PostgreSQL-Only mode (High Fix H2) for mid-scale deployments (<1M events/day).

## Detailed Instructions

Complete in **5 exchanges**, one step per response. **AWAIT USER CONFIRMATION** before proceeding to each subsequent step.

### Step 1: PostgreSQL Schema & JPA Entity (TDD)
**先写测试，再实现：**

1. **Write test class PostgreSQLSchemaTest** covering:
   - `testAuditReportEntity_shouldMapCorrectly()`
   - `testAuditReportEntity_shouldGenerateId()`
   - `testAuditReportTable_shouldHaveCorrectColumns()`
   - `testAuditReportTable_shouldHaveIndexes()`
   - `testCheckerConfigEntity_shouldMapCorrectly()`
   - `testJpaRepository_save_shouldPersist()`
   - `testJpaRepository_findById_shouldReturn()`
   - `testJpaRepository_findByTimeRange_shouldFilter()`

2. **Then implement:**
   ```java
   @Entity
   @Table(name = "audit_reports", indexes = {
       @Index(name = "idx_audit_reports_created_at", columnList = "createdAt"),
       @Index(name = "idx_audit_reports_sql_id", columnList = "sqlId")
   })
   public class AuditReportEntity {
       @Id
       private String reportId;
       private String sqlId;
       @Lob
       private String originalEventJson;
       @Lob
       private String checkerResultsJson;
       private String riskLevel;
       private int riskScore;
       private Instant createdAt;
   }
   ```

3. **Create Flyway migrations:**
   - `V1__create_audit_reports_table.sql`
   - `V2__create_checker_config_table.sql`

4. **Verify:** PostgreSQL schema tests pass with H2 in-memory database

### Step 2: JPA Repository Implementation (TDD)
**先写测试，再实现：**

1. **Write test class JpaAuditReportRepositoryTest** covering:
   - `testSave_newReport_shouldPersist()`
   - `testSave_existingReport_shouldUpdate()`
   - `testFindById_existingId_shouldReturn()`
   - `testFindById_nonExistingId_shouldReturnEmpty()`
   - `testFindByTimeRange_shouldFilterCorrectly()`
   - `testFindByTimeRange_emptyRange_shouldReturnEmpty()`
   - `testFindBySqlId_shouldReturn()`
   - `testFindByRiskLevel_shouldFilter()`
   - `testCount_shouldReturnCorrectCount()`
   - `testDelete_shouldRemove()`

2. **Then implement JpaAuditReportRepository:**
   ```java
   @Repository
   public class JpaAuditReportRepository implements AuditReportRepository {

       private final AuditReportJpaRepository jpaRepository;
       private final ObjectMapper objectMapper;

       @Override
       public void save(AuditReport report) {
           AuditReportEntity entity = toEntity(report);
           jpaRepository.save(entity);
       }

       @Override
       public Optional<AuditReport> findById(String reportId) {
           return jpaRepository.findById(reportId)
               .map(this::toDomain);
       }
   }
   ```

3. **Verify:** JPA repository tests pass

### Step 3: ClickHouse Schema & Client (TDD)
**先写测试，再实现：**

1. **Write test class ClickHouseSchemaTest** covering:
   - `testSqlExecutionsTable_shouldCreate()`
   - `testSqlExecutionsTable_shouldUseMergeTreeEngine()`
   - `testSqlExecutionsTable_shouldPartitionByDate()`
   - `testSqlExecutionsTable_shouldHaveTTL90Days()`
   - `testInsert_singleRow_shouldSucceed()`
   - `testInsert_batchRows_shouldSucceed()`
   - `testQuery_byTimeRange_shouldReturn()`
   - `testQuery_aggregation_shouldCalculate()`

2. **Then implement ClickHouse schema:**
   ```sql
   CREATE TABLE sql_executions (
       event_id UUID,
       sql_id String,
       sql String,
       sql_type Enum8('SELECT'=1, 'UPDATE'=2, 'DELETE'=3, 'INSERT'=4),
       mapper_id String,
       datasource String,
       execution_time_ms UInt64,
       rows_affected Int32,
       error_message Nullable(String),
       timestamp DateTime64(3),
       date Date MATERIALIZED toDate(timestamp)
   ) ENGINE = MergeTree()
   PARTITION BY toYYYYMM(date)
   ORDER BY (date, sql_id, timestamp)
   TTL date + INTERVAL 90 DAY;
   ```

3. **Implement ClickHouseExecutionLogger:**
   ```java
   @Component
   public class ClickHouseExecutionLogger {
       private final ClickHouseDataSource dataSource;

       public void log(AuditEvent event) {
           // Single insert
       }

       public void logBatch(List<AuditEvent> events) {
           // Batch insert for high throughput
       }
   }
   ```

4. **Verify:** ClickHouse tests pass (use Testcontainers or mock)

### Step 4: PostgreSQL-Only Mode (TDD) - High Fix H2
**先写测试，再实现：**

1. **Write test class PostgreSQLOnlyModeTest** covering:
   - `testPostgreSQLOnlyMode_shouldStoreExecutions()`
   - `testPostgreSQLOnlyMode_shouldQueryByTimeRange()`
   - `testPostgreSQLOnlyMode_shouldSupportAggregation()`
   - `testPostgreSQLOnlyMode_brinIndex_shouldOptimizeTimeQueries()`
   - `testPostgreSQLOnlyMode_partition_shouldWork()`
   - `testPostgreSQLOnlyMode_retention_shouldDeleteOldData()`
   - `testStorageAdapter_shouldSelectCorrectMode()`
   - `testStorageAdapter_clickHouseMode_shouldUseClickHouse()`
   - `testStorageAdapter_postgresOnlyMode_shouldUsePostgreSQL()`

2. **Then implement PostgreSQLOnlyStorageAdapter:**
   ```java
   @Component
   @ConditionalOnProperty(name = "audit.storage.mode", havingValue = "postgresql-only")
   public class PostgreSQLOnlyStorageAdapter implements ExecutionLogRepository {
       // Uses PostgreSQL with BRIN index for time-series queries
       // Suitable for <1M events/day
   }
   ```

3. **Create PostgreSQL execution log schema:**
   ```sql
   CREATE TABLE sql_executions_pg (
       event_id UUID PRIMARY KEY,
       sql_id VARCHAR(64),
       sql TEXT,
       timestamp TIMESTAMPTZ NOT NULL
       -- ... other columns
   ) PARTITION BY RANGE (timestamp);

   CREATE INDEX idx_executions_timestamp ON sql_executions_pg
       USING BRIN (timestamp);
   ```

4. **Verify:** PostgreSQL-Only mode tests pass

### Step 5: Data Retention & Integration Tests
**验证所有性能目标：**

1. **Write test class DataRetentionTest** covering:
   - `testClickHouseTTL_shouldExpireAfter90Days()`
   - `testPostgreSQLPartition_shouldDropOldPartitions()`
   - `testRetentionJob_shouldRunOnSchedule()`
   - `testRetentionJob_shouldLogDeletions()`

2. **Write test class StorageIntegrationTest** covering:
   - `testFullPipeline_auditReportStorage_shouldWork()`
   - `testFullPipeline_executionLogging_shouldWork()`
   - `testConcurrentWrites_shouldHandle()`
   - `testQueryPerformance_shouldMeetTargets()`

3. **Performance targets verification:**
   | 指标 | 目标值 |
   |------|--------|
   | PostgreSQL 写入 | >1000 writes/s |
   | ClickHouse 批量写入 | >10k rows/s |
   | PostgreSQL 查询 | <100ms (p95) |
   | ClickHouse 聚合查询 | <500ms (p95) |
   | PostgreSQL-Only 模式 | 支持 <1M events/day |

4. **Final verification:** `mvn clean test` - all 55+ tests pass

## Expected Output
- **Deliverables:**
  - `AuditReportEntity` JPA entity
  - `JpaAuditReportRepository` implementing `AuditReportRepository`
  - `ClickHouseExecutionLogger` for time-series data
  - `PostgreSQLOnlyStorageAdapter` for simplified deployment
  - `StorageConfig` with mode selection (full/postgresql-only)
  - Flyway migrations for PostgreSQL
  - ClickHouse DDL scripts
  - Data retention policies

- **Success criteria:**
  - 55+ tests passing
  - `mvn clean test` succeeds
  - PostgreSQL write >1000/s
  - ClickHouse batch >10k rows/s
  - Query latency targets met

- **File locations:**
  - `sql-audit-service-core/src/main/java/com/footstone/audit/service/core/storage/`
    - `entity/AuditReportEntity.java`
    - `repository/JpaAuditReportRepository.java`
    - `repository/AuditReportJpaRepository.java` (Spring Data JPA interface)
    - `clickhouse/ClickHouseExecutionLogger.java`
    - `adapter/PostgreSQLOnlyStorageAdapter.java`
    - `config/StorageConfig.java`
  - `sql-audit-service-core/src/main/resources/db/migration/`
    - `V1__create_audit_reports_table.sql`
    - `V2__create_checker_config_table.sql`
    - `V3__create_sql_executions_pg_table.sql`
  - `sql-audit-service-core/src/main/resources/clickhouse/`
    - `create_sql_executions.sql`

## Memory Logging
Upon completion, you **MUST** log work in: `.apm/Memory/Phase_10_Audit_Service/Task_10_4_Storage_Layer_PostgreSQL_ClickHouse.md`
Follow .apm/guides/Memory_Log_Guide.md instructions.

## Technical Notes
1. **双存储模式:**
   - **Full Mode:** PostgreSQL (metadata) + ClickHouse (time-series)
   - **PostgreSQL-Only Mode:** 仅 PostgreSQL，适合中小规模 (<1M events/day)

2. **ClickHouse 优化:**
   - MergeTree 引擎支持高效时间序列查询
   - 按月分区 (toYYYYMM)
   - 90 天 TTL 自动清理

3. **PostgreSQL-Only 优化:**
   - BRIN 索引优化时间范围查询
   - 分区表支持大数据量
   - 适合不需要 ClickHouse 复杂运维的团队

4. **配置示例:**
   ```yaml
   audit:
     storage:
       mode: full  # or postgresql-only
       postgresql:
         url: jdbc:postgresql://localhost:5432/audit
       clickhouse:
         url: jdbc:clickhouse://localhost:8123/audit
         batch-size: 1000
         flush-interval-ms: 5000
   ```

5. **依赖版本:**
   ```xml
   <clickhouse-jdbc.version>0.6.0</clickhouse-jdbc.version>
   <flyway.version>9.22.0</flyway.version>
   <testcontainers.version>1.19.0</testcontainers.version>
   ```

---

**Assignment Created:** 2025-12-18
**Manager Agent:** Manager_Agent_3
**Status:** Ready for Assignment
**Prerequisite:** Task 10.2 + Task 10.3 Completed
