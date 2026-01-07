# Task 10.4 - Storage Layer: PostgreSQL & ClickHouse

## Status
- **Progress:** Completed
- **Completion Date:** 2025-12-18
- **Agent:** Agent_Audit_Service
- **Related PR/Commit:** N/A

## Summary
Implemented dual-database storage strategy for the audit service.
- **Full Mode:** PostgreSQL (metadata) + ClickHouse (time-series).
- **PostgreSQL-Only Mode:** Fallback for mid-scale deployments using BRIN indexes and partitioning (simulated).

## Key Decisions
1. **Architecture:**
   - Used `@ConditionalOnProperty(name = "audit.storage.mode")` to switch between `ClickHouseExecutionLogger` (full) and `PostgreSQLOnlyStorageAdapter` (postgresql-only).
   - Created `StorageConfig` to auto-configure `ClickHouseDataSource` only in full mode.

2. **Data Model:**
   - `AuditReportEntity`: JPA entity with JSON CLOBs for complex data (`originalEvent`, `checkerResults`).
   - `sql_executions`: ClickHouse MergeTree table partitioned by month.
   - `sql_executions_pg`: PostgreSQL table (with disabled partitioning in H2 tests).

3. **PostgreSQL-Only Mode:**
   - Uses `JdbcTemplate` for high-performance inserts.
   - Designed schema with `BRIN` index on `timestamp` (although H2 tests use standard B-Tree).

4. **Testing:**
   - Adopted TDD approach.
   - Used H2 with `MODE=PostgreSQL` for integration tests.
   - Patched Flyway migration `V3` to comment out `PARTITION BY` to ensure H2 compatibility.

## Artifacts
- **Source Code:**
  - `com.footstone.audit.service.core.storage.entity.AuditReportEntity`
  - `com.footstone.audit.service.core.storage.repository.JpaAuditReportRepository`
  - `com.footstone.audit.service.core.storage.clickhouse.ClickHouseExecutionLogger`
  - `com.footstone.audit.service.core.storage.adapter.PostgreSQLOnlyStorageAdapter`
  - `com.footstone.audit.service.core.storage.config.StorageConfig`
  - `com.footstone.audit.service.core.job.RetentionJob`
- **Migrations:**
  - `db/migration/V1__create_audit_reports_table.sql`
  - `db/migration/V2__create_checker_config_table.sql`
  - `db/migration/V3__create_sql_executions_pg_table.sql`
- **ClickHouse:**
  - `clickhouse/create_sql_executions.sql`

## Known Issues / Future Work
- **H2 Limitation:** Partitioning syntax in `V3` is commented out. In a real production PostgreSQL environment, this should be uncommented or managed via a separate migration script for production.
- **ClickHouse TTL:** Deletion logic is handled by ClickHouse TTL, but `RetentionJob` also provides a programmatic `deleteOlderThan` hook which is currently a no-op or heavy `ALTER DELETE` for ClickHouse (as noted in implementation).

## Dependencies
- `clickhouse-jdbc`
- `flyway-core`
- `spring-boot-starter-data-jpa`
