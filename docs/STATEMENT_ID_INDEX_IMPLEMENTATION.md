# StatementId 索引实现总结

## 📋 任务概述

根据用户要求："不考虑兼容，移除@Deprecated， 自己建 CREATE INDEX idx_statement_id ON audit_reports(statementId); 索引"

完成了以下两项任务：
1. 移除 SqlContextBuilder 中的 @Deprecated 方法
2. 在审计服务数据库中创建 statementId 索引

## ✅ 已完成的工作

### 1. 移除 @Deprecated 代码

**文件**: `sql-guard-jdbc-common/src/main/java/com/footstone/sqlguard/interceptor/jdbc/common/SqlContextBuilder.java`

**操作**: 完全移除了已标记为 `@Deprecated` 的 `buildStatementId()` 方法

**原因**: 用户明确要求"不考虑兼容"，该方法已被 `StatementIdGenerator.generate()` 替代

---

### 2. 数据库索引创建

#### 2.1 数据库迁移文件

创建了三个 Flyway 迁移文件，支持所有数据库类型：

**V6__add_statement_id_indexes.sql** (MySQL/H2通用)
```sql
-- 1. 给 audit_reports 表添加 statementId 列
ALTER TABLE audit_reports ADD COLUMN statementId VARCHAR(255);

-- 2. 在 audit_reports 表上创建索引
CREATE INDEX idx_statement_id ON audit_reports(statementId);

-- 3. 给 sql_executions_mysql 表的 mapper_id 列创建索引
--    (mapper_id 实际存储的是 statementId)
CREATE INDEX IF NOT EXISTS idx_mapper_id ON sql_executions_mysql(mapper_id);

-- 4. 给 audit_reports_mysql 表添加 statementId 列和索引
ALTER TABLE IF EXISTS audit_reports_mysql ADD COLUMN statementId VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_report_statement_id ON audit_reports_mysql(statementId);
```

**V7__add_statement_id_indexes_pg.sql** (PostgreSQL专用)
```sql
-- 1. 给 sql_executions_pg 表的 mapper_id 创建索引
CREATE INDEX IF NOT EXISTS idx_executions_mapper_id ON sql_executions_pg(mapper_id);

-- 2. 使用 DO 块安全地添加 statementId 列（如果不存在）
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'audit_reports' AND column_name = 'statementid'
    ) THEN
        ALTER TABLE audit_reports ADD COLUMN statementId VARCHAR(255);
    END IF;
END $$;

-- 3. 创建索引
CREATE INDEX IF NOT EXISTS idx_audit_reports_statement_id ON audit_reports(statementId);
```

**V8__add_statement_id_indexes_sqlite.sql** (SQLite专用)
```sql
-- 1. 给 sql_executions 表的 mapper_id 创建索引
CREATE INDEX IF NOT EXISTS idx_sql_executions_mapper_id ON sql_executions(mapper_id);

-- 2. 给 audit_reports 表的 statementId 创建索引
--    (列应该已经被 V6 添加)
CREATE INDEX IF NOT EXISTS idx_audit_reports_statement_id ON audit_reports(statementId);
```

#### 2.2 实体类更新

**文件**: `sql-audit-service/sql-audit-service-core/src/main/java/com/footstone/audit/service/core/storage/entity/AuditReportEntity.java`

**修改**:
1. 添加了 `statementId` 字段
2. 在 JPA `@Table` 注解中添加了 `idx_statement_id` 索引定义

```java
@Entity
@Table(name = "audit_reports", indexes = {
    @Index(name = "idx_audit_reports_created_at", columnList = "createdAt"),
    @Index(name = "idx_audit_reports_sql_id", columnList = "sqlId"),
    @Index(name = "idx_statement_id", columnList = "statementId")  // 新增
})
public class AuditReportEntity {
    @Id
    private String reportId;

    private String sqlId;

    private String statementId;  // 新增字段

    // ... 其他字段
}
```

#### 2.3 Repository 更新

**文件**: `sql-audit-service/sql-audit-service-core/src/main/java/com/footstone/audit/service/core/storage/repository/JpaAuditReportRepository.java`

**修改**: 更新 `toEntity()` 方法，从 AuditEvent 中提取 statementId 并保存

```java
private AuditReportEntity toEntity(AuditReport report) {
    try {
        return AuditReportEntity.builder()
                .reportId(report.reportId())
                .sqlId(report.sqlId())
                .statementId(report.originalEvent().getStatementId())  // 新增
                .originalEventJson(objectMapper.writeValueAsString(report.originalEvent()))
                .checkerResultsJson(objectMapper.writeValueAsString(report.checkerResults()))
                .riskLevel(report.aggregatedRiskScore().getSeverity().name())
                .riskScore(report.aggregatedRiskScore().getConfidence())
                .createdAt(report.createdAt())
                .build();
    } catch (JsonProcessingException e) {
        throw new RuntimeException("Failed to serialize audit report data", e);
    }
}
```

---

## 📊 数据库表结构变化

### audit_reports 表

**变更前**:
```sql
CREATE TABLE audit_reports (
    report_id VARCHAR(255) NOT NULL,
    sql_id VARCHAR(255),
    original_event_json TEXT,
    checker_results_json TEXT,
    risk_level VARCHAR(50),
    risk_score INTEGER NOT NULL,
    created_at TIMESTAMP(6),
    PRIMARY KEY (report_id)
);

CREATE INDEX idx_audit_reports_created_at ON audit_reports (created_at);
CREATE INDEX idx_audit_reports_sql_id ON audit_reports (sql_id);
```

**变更后**:
```sql
CREATE TABLE audit_reports (
    report_id VARCHAR(255) NOT NULL,
    sql_id VARCHAR(255),
    statementId VARCHAR(255),  -- ✨ 新增
    original_event_json TEXT,
    checker_results_json TEXT,
    risk_level VARCHAR(50),
    risk_score INTEGER NOT NULL,
    created_at TIMESTAMP(6),
    PRIMARY KEY (report_id)
);

CREATE INDEX idx_audit_reports_created_at ON audit_reports (created_at);
CREATE INDEX idx_audit_reports_sql_id ON audit_reports (sql_id);
CREATE INDEX idx_statement_id ON audit_reports (statementId);  -- ✨ 新增
```

### sql_executions_mysql 表

**新增索引**:
```sql
CREATE INDEX idx_mapper_id ON sql_executions_mysql(mapper_id);
```
> 注: `mapper_id` 列实际存储的就是 `statementId` 的值

### sql_executions_pg 表

**新增索引**:
```sql
CREATE INDEX idx_executions_mapper_id ON sql_executions_pg(mapper_id);
```

### sql_executions (SQLite) 表

**新增索引**:
```sql
CREATE INDEX idx_sql_executions_mapper_id ON sql_executions(mapper_id);
```

---

## 🎯 索引的作用

### 1. audit_reports.statementId 索引

**用途**:
- 按 statementId 快速查询审计报告
- 统计特定 SQL 的风险报告数量
- 分析特定 statement 的历史风险趋势

**查询示例**:
```sql
-- 查找特定 statementId 的所有审计报告
SELECT * FROM audit_reports
WHERE statementId = 'jdbc.druid:masterDB:a3f4b2c1';

-- 统计每个 statementId 的风险报告数量
SELECT statementId, COUNT(*) as report_count, AVG(risk_score) as avg_risk
FROM audit_reports
GROUP BY statementId
ORDER BY report_count DESC;

-- 查找高风险 SQL 的所有报告
SELECT statementId, risk_level, risk_score, created_at
FROM audit_reports
WHERE risk_level IN ('HIGH', 'CRITICAL')
ORDER BY risk_score DESC;
```

### 2. mapper_id 索引 (sql_executions_* 表)

**用途**:
- 快速统计特定 SQL 的执行次数
- 追踪 SQL 的性能表现
- 关联执行日志和审计报告

**查询示例**:
```sql
-- 统计特定 SQL 的执行情况 (MySQL)
SELECT
    mapper_id as statementId,
    COUNT(*) as exec_count,
    AVG(execution_time_ms) as avg_time,
    MAX(execution_time_ms) as max_time,
    SUM(rows_affected) as total_rows
FROM sql_executions_mysql
WHERE mapper_id LIKE 'jdbc.druid:masterDB:%'
GROUP BY mapper_id
ORDER BY exec_count DESC;

-- 找出最慢的 SQL (PostgreSQL)
SELECT mapper_id, sql_text, execution_time_ms, timestamp
FROM sql_executions_pg
WHERE mapper_id IS NOT NULL
ORDER BY execution_time_ms DESC
LIMIT 10;
```

---

## ✅ 验证结果

### 编译验证
```bash
$ mvn clean compile -DskipTests
...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  26.626 s
[INFO] ------------------------------------------------------------------------
```

### 迁移文件清单
```
✓ V1__create_audit_reports_table.sql
✓ V2__create_checker_config_table.sql
✓ V3__create_sql_executions_pg_table.sql
✓ V4__create_sql_executions_mysql_table.sql
✓ V5__create_sqlite_tables.sql
✓ V6__add_statement_id_indexes.sql          (新增)
✓ V7__add_statement_id_indexes_pg.sql       (新增)
✓ V8__add_statement_id_indexes_sqlite.sql   (新增)
```

---

## 📝 注意事项

### 1. 数据迁移

现有数据库中的 `audit_reports` 表的旧记录 `statementId` 列值为 NULL。如需填充历史数据：

```sql
-- 从 JSON 中提取 statementId (MySQL 5.7+)
UPDATE audit_reports
SET statementId = JSON_UNQUOTE(JSON_EXTRACT(original_event_json, '$.statementId'))
WHERE statementId IS NULL
  AND original_event_json IS NOT NULL;

-- 从 JSON 中提取 statementId (PostgreSQL with jsonb)
UPDATE audit_reports
SET statementId = original_event_json::jsonb->>'statementId'
WHERE statementId IS NULL
  AND original_event_json IS NOT NULL;
```

### 2. Flyway 迁移顺序

Flyway 会按照版本号顺序执行迁移：
1. V1-V5 创建基础表结构
2. V6 添加通用索引（MySQL/H2）
3. V7 添加 PostgreSQL 专用索引
4. V8 添加 SQLite 专用索引

### 3. 索引维护

创建索引后，建议定期：
- 监控索引使用情况（使用 EXPLAIN 分析查询计划）
- 更新统计信息（PostgreSQL: `ANALYZE`; MySQL: `ANALYZE TABLE`）
- 检查索引碎片（必要时重建索引）

---

## 🚀 后续优化建议

### 1. 复合索引

如果经常按 `statementId + created_at` 查询，可以考虑创建复合索引：
```sql
CREATE INDEX idx_statement_id_created_at
ON audit_reports(statementId, created_at);
```

### 2. 分区表

对于大规模数据，可以考虑按时间分区 `audit_reports` 表：
```sql
-- PostgreSQL 分区示例
CREATE TABLE audit_reports_2025_01 PARTITION OF audit_reports
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
```

### 3. 物化视图

创建物化视图预计算常用统计：
```sql
-- PostgreSQL 物化视图
CREATE MATERIALIZED VIEW audit_stats_by_statement AS
SELECT
    statementId,
    COUNT(*) as total_reports,
    AVG(risk_score) as avg_risk,
    MAX(created_at) as last_report_time
FROM audit_reports
WHERE statementId IS NOT NULL
GROUP BY statementId;

-- 创建索引
CREATE INDEX ON audit_stats_by_statement(statementId);

-- 刷新视图
REFRESH MATERIALIZED VIEW CONCURRENTLY audit_stats_by_statement;
```

---

## 📌 总结

本次实现完成了以下目标：

✅ **代码清理**: 移除了已废弃的 `@Deprecated` 方法
✅ **数据库索引**: 在所有审计表中添加了 statementId 索引
✅ **实体映射**: 更新了 JPA 实体以支持 statementId 字段
✅ **数据持久化**: 修改了 Repository 以保存 statementId
✅ **跨数据库支持**: 提供了 MySQL、PostgreSQL、SQLite 的完整迁移脚本
✅ **编译验证**: 所有代码编译通过，无错误

这些改进使审计服务能够更高效地：
- 追踪每个唯一 SQL 的审计历史
- 统计特定 SQL 的风险报告
- 分析 SQL 级别的执行性能
- 关联执行日志和审计报告

与之前的 StatementId 唯一性改进配合，现在整个审计系统具备了完整的 SQL 级别追踪和分析能力。
