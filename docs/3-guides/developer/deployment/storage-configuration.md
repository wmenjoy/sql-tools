# Storage Configuration Guide

SQL Audit Service supports multiple storage backends for audit log persistence. This guide covers configuration for all supported storage modes.

## Storage Modes Overview

| Mode | Metadata Storage | Log Storage | Best For |
|------|------------------|-------------|----------|
| `sqlite` | SQLite | SQLite | **Local dev/testing** |
| `mysql-es` | MySQL | Elasticsearch | **Production** - Most organizations |
| `mysql-only` | MySQL | MySQL | Simple production deployment |
| `postgresql-only` | PostgreSQL | PostgreSQL | PostgreSQL-only infra |
| `full` | PostgreSQL | ClickHouse | Large-scale analytics (>1M events/day) |

## Environment Recommendations

| Environment | Recommended Mode | Components |
|-------------|------------------|------------|
| 本地开发 | `sqlite` | SQLite (无需外部服务) |
| 测试环境 | `mysql-only` | MySQL |
| 生产环境 | `mysql-es` | MySQL + Elasticsearch |
| 大规模生产 | `full` | PostgreSQL + ClickHouse (可选) |

## 0. SQLite Mode (Local Development)

**Best for local development and testing** - no external database required.

### Configuration

```yaml
audit:
  storage:
    mode: sqlite

spring:
  datasource:
    url: jdbc:sqlite:./data/audit.db
    driver-class-name: org.sqlite.JDBC
  flyway:
    enabled: true
```

### Features

- **Zero setup**: No database server installation needed
- **File-based**: Database stored in a single file
- **Cross-platform**: Works on Windows, macOS, Linux
- **Fast startup**: Perfect for development iteration

### Limitations

- Not suitable for production (single-writer)
- No full-text search capability
- No distributed/multi-node support

### Quick Start

```bash
# 1. Clone and build
git clone <repo>
mvn clean package

# 2. Run with SQLite (auto-creates audit.db)
java -jar sql-audit-service.jar --audit.storage.mode=sqlite
```

---

## 1. MySQL + Elasticsearch Mode (Production Recommended)

**Best for production** - most organizations have existing MySQL and Elasticsearch infrastructure.

### Configuration

```yaml
audit:
  storage:
    mode: mysql-es  # This is the default, can be omitted
    elasticsearch:
      hosts: localhost:9200
      username: elastic
      password: changeme
      ssl-enabled: false

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/audit?useSSL=false&serverTimezone=UTC
    username: audit_user
    password: secure_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    enabled: true
```

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    mysql-es Mode                         │
├─────────────────────────────────────────────────────────┤
│  MySQL (Metadata)              │  Elasticsearch (Logs)  │
│  - audit_reports_mysql         │  - sql-audit-*         │
│  - checker_config              │  - Time-series data    │
│  - JPA repositories            │  - Full-text search    │
└─────────────────────────────────────────────────────────┘
```

### Features

- **MySQL**: Stores audit reports, checker configurations (via JPA)
- **Elasticsearch**: Stores execution logs with:
  - Time-based indices (`sql-audit-YYYY.MM.dd`)
  - Full-text search on SQL statements
  - Kibana dashboard integration
  - ILM for automatic retention

### Infrastructure Requirements

- **MySQL**: 8.0+ with InnoDB
- **Elasticsearch**: 8.x cluster

---

## 2. MySQL-Only Mode

Simpler deployment for MySQL-only environments.

### Configuration

```yaml
audit:
  storage:
    mode: postgresql-only

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/audit
    username: audit_user
    password: secure_password
  flyway:
    enabled: true
```

### Features

- BRIN indexes for time-range queries
- Table partitioning support
- Single database for simplified operations

---

## 3. MySQL-Only Mode

For organizations with MySQL-based infrastructure.

### Configuration

```yaml
audit:
  storage:
    mode: mysql-only

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/audit?useSSL=false&serverTimezone=UTC
    username: audit_user
    password: secure_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  flyway:
    enabled: true
```

### Features

- InnoDB engine with optimized indexes
- TEXT column for SQL storage (up to 65KB)
- Composite indexes for common queries
- Pre-aggregated statistics table

### Table Schema

```sql
CREATE TABLE sql_executions_mysql (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    sql_id VARCHAR(32) NOT NULL,
    sql_text TEXT NOT NULL,
    sql_type VARCHAR(20) NOT NULL,
    mapper_id VARCHAR(255),
    datasource VARCHAR(100),
    execution_time_ms BIGINT NOT NULL DEFAULT 0,
    rows_affected INT NOT NULL DEFAULT -1,
    error_message TEXT,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    
    INDEX idx_created_at (created_at),
    INDEX idx_sql_id (sql_id),
    INDEX idx_sql_type (sql_type),
    INDEX idx_execution_time (execution_time_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 4. Elasticsearch Mode

For full-text search, advanced analytics, and Kibana integration.

### Configuration

```yaml
audit:
  storage:
    mode: elasticsearch
    elasticsearch:
      hosts: localhost:9200
      username: elastic
      password: changeme
      ssl-enabled: false
      connect-timeout: 5000
      socket-timeout: 60000
```

### Multi-Host Configuration

```yaml
audit:
  storage:
    elasticsearch:
      hosts: es-node1:9200,es-node2:9200,es-node3:9200
      ssl-enabled: true
```

### Features

- **Full-text search** on SQL statements
- **Time-based indices**: `sql-audit-YYYY.MM.dd`
- **Index Lifecycle Management (ILM)**: Automatic rollover and deletion
- **Kibana integration**: Pre-built dashboards

### Index Template

The service automatically creates indices with optimized mappings:

```json
{
  "index_patterns": ["sql-audit-*"],
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1
  },
  "mappings": {
    "properties": {
      "sql_text": { "type": "text", "analyzer": "sql_analyzer" },
      "sql_type": { "type": "keyword" },
      "@timestamp": { "type": "date" },
      "execution_time_ms": { "type": "long" }
    }
  }
}
```

### Index Lifecycle Management

Configure retention with ILM policy:

| Phase | Age | Actions |
|-------|-----|---------|
| Hot | 0d | Active indexing |
| Warm | 7d | Shrink to 1 shard, force merge |
| Cold | 30d | Freeze (read-only) |
| Delete | 90d | Delete index |

---

## Migration Between Storage Modes

### PostgreSQL → MySQL

1. Export data from PostgreSQL:
   ```bash
   pg_dump -t sql_executions_pg audit > audit_data.sql
   ```

2. Convert SQL syntax for MySQL

3. Import to MySQL:
   ```bash
   mysql audit < audit_data_mysql.sql
   ```

4. Update configuration:
   ```yaml
   audit.storage.mode: mysql-only
   ```

### Any → Elasticsearch

1. Use Logstash with JDBC input plugin:
   ```
   input {
     jdbc {
       jdbc_connection_string => "jdbc:postgresql://localhost:5432/audit"
       statement => "SELECT * FROM sql_executions_pg"
     }
   }
   output {
     elasticsearch {
       hosts => ["localhost:9200"]
       index => "sql-audit-%{+YYYY.MM.dd}"
     }
   }
   ```

---

## Performance Tuning

### MySQL Optimization

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### Elasticsearch Optimization

```yaml
audit:
  storage:
    elasticsearch:
      bulk-size: 1000
      flush-interval-ms: 5000
      concurrent-requests: 4
```

---

## Monitoring

### MySQL Metrics

Monitor these queries:
```sql
-- Slow queries
SELECT * FROM sql_executions_mysql 
WHERE execution_time_ms > 1000 
ORDER BY created_at DESC LIMIT 100;

-- Events per hour
SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:00') as hour, COUNT(*)
FROM sql_executions_mysql
GROUP BY hour
ORDER BY hour DESC;
```

### Elasticsearch Metrics

Use Kibana or query API:
```bash
# Index stats
curl "localhost:9200/sql-audit-*/_stats"

# Document count
curl "localhost:9200/sql-audit-*/_count"
```

---

## Troubleshooting

### MySQL Issues

| Issue | Solution |
|-------|----------|
| Connection timeout | Increase `connection-timeout` |
| Deadlocks | Use `READ COMMITTED` isolation |
| Slow inserts | Check index fragmentation |

### Elasticsearch Issues

| Issue | Solution |
|-------|----------|
| Bulk reject | Increase queue size |
| Mapping explosion | Use strict mappings |
| Slow queries | Add more shards |

---

## See Also

- [Production Deployment Guide](production-deployment.md)
- [High Availability Setup](ha/postgres-replication.md)
- [Monitoring Configuration](monitoring/prometheus-metrics.md)

