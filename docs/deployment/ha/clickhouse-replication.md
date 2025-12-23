# ClickHouse High Availability Configuration

## Overview

This guide describes how to configure ClickHouse with ReplicatedMergeTree for high availability and data redundancy in the SQL Audit Service deployment.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    ClickHouse Cluster                       │
│                                                             │
│  ┌────────────┐      ┌────────────┐      ┌────────────┐   │
│  │   Shard 1  │      │   Shard 2  │      │   Shard 3  │   │
│  │            │      │            │      │            │   │
│  │ Replica 1  │      │ Replica 1  │      │ Replica 1  │   │
│  │ Replica 2  │      │ Replica 2  │      │ Replica 2  │   │
│  └────────────┘      └────────────┘      └────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                          │
                          │ Coordination
                          ▼
              ┌───────────────────────┐
              │  ZooKeeper Cluster    │
              │  (3+ nodes)           │
              └───────────────────────┘
```

## Prerequisites

- ClickHouse 23.8+ installed on all nodes
- ZooKeeper 3.6+ cluster (3 or 5 nodes for production)
- Network connectivity between all ClickHouse nodes
- Network connectivity to ZooKeeper ensemble
- Sufficient storage on all replica nodes

## ZooKeeper Setup

### 1. Install ZooKeeper

```bash
# Install on all ZooKeeper nodes
sudo apt-get update
sudo apt-get install zookeeper zookeeperd
```

### 2. Configure ZooKeeper

#### 2.1 zoo.cfg on Node 1

```ini
# File: /etc/zookeeper/conf/zoo.cfg
tickTime=2000
initLimit=10
syncLimit=5
dataDir=/var/lib/zookeeper
clientPort=2181
autopurge.snapRetainCount=3
autopurge.purgeInterval=1

# Cluster configuration
server.1=zk1.example.com:2888:3888
server.2=zk2.example.com:2888:3888
server.3=zk3.example.com:2888:3888
```

#### 2.2 Set Server ID

```bash
# On zk1
echo "1" > /var/lib/zookeeper/myid

# On zk2
echo "2" > /var/lib/zookeeper/myid

# On zk3
echo "3" > /var/lib/zookeeper/myid
```

#### 2.3 Start ZooKeeper

```bash
# On all ZooKeeper nodes
sudo systemctl start zookeeper
sudo systemctl enable zookeeper

# Verify status
echo stat | nc localhost 2181
# Expected: Mode: follower or leader
```

## ClickHouse Cluster Configuration

### 1. Single Shard with Replication (Small/Medium Scale)

#### 1.1 config.xml

```xml
<!-- File: /etc/clickhouse-server/config.xml -->
<clickhouse>
    <!-- ZooKeeper configuration -->
    <zookeeper>
        <node>
            <host>zk1.example.com</host>
            <port>2181</port>
        </node>
        <node>
            <host>zk2.example.com</host>
            <port>2181</port>
        </node>
        <node>
            <host>zk3.example.com</host>
            <port>2181</port>
        </node>
        <session_timeout_ms>30000</session_timeout_ms>
        <operation_timeout_ms>10000</operation_timeout_ms>
        <root>/clickhouse</root>
    </zookeeper>

    <!-- Remote servers configuration -->
    <remote_servers>
        <audit_cluster>
            <shard>
                <internal_replication>true</internal_replication>
                <replica>
                    <host>ch1.example.com</host>
                    <port>9000</port>
                </replica>
                <replica>
                    <host>ch2.example.com</host>
                    <port>9000</port>
                </replica>
            </shard>
        </audit_cluster>
    </remote_servers>

    <!-- Macros for replica identification -->
    <macros>
        <shard>01</shard>
        <!-- On ch1 -->
        <replica>replica1</replica>
        <!-- On ch2: <replica>replica2</replica> -->
    </macros>

    <!-- Distributed DDL -->
    <distributed_ddl>
        <path>/clickhouse/task_queue/ddl</path>
    </distributed_ddl>
</clickhouse>
```

### 2. Multi-Shard with Replication (Large/X-Large Scale)

#### 2.1 config.xml for Sharded Cluster

```xml
<remote_servers>
    <audit_cluster_3shards_2replicas>
        <!-- Shard 1 -->
        <shard>
            <internal_replication>true</internal_replication>
            <replica>
                <host>ch1.example.com</host>
                <port>9000</port>
            </replica>
            <replica>
                <host>ch2.example.com</host>
                <port>9000</port>
            </replica>
        </shard>

        <!-- Shard 2 -->
        <shard>
            <internal_replication>true</internal_replication>
            <replica>
                <host>ch3.example.com</host>
                <port>9000</port>
            </replica>
            <replica>
                <host>ch4.example.com</host>
                <port>9000</port>
            </replica>
        </shard>

        <!-- Shard 3 -->
        <shard>
            <internal_replication>true</internal_replication>
            <replica>
                <host>ch5.example.com</host>
                <port>9000</port>
            </replica>
            <replica>
                <host>ch6.example.com</host>
                <port>9000</port>
            </replica>
        </shard>
    </audit_cluster_3shards_2replicas>
</remote_servers>
```

#### 2.2 Macros Configuration

```xml
<!-- On ch1 and ch2 (Shard 1) -->
<macros>
    <shard>01</shard>
    <replica>replica1</replica> <!-- ch1 -->
    <!-- <replica>replica2</replica> for ch2 -->
</macros>

<!-- On ch3 and ch4 (Shard 2) -->
<macros>
    <shard>02</shard>
    <replica>replica1</replica> <!-- ch3 -->
    <!-- <replica>replica2</replica> for ch4 -->
</macros>

<!-- On ch5 and ch6 (Shard 3) -->
<macros>
    <shard>03</shard>
    <replica>replica1</replica> <!-- ch5 -->
    <!-- <replica>replica2</replica> for ch6 -->
</macros>
```

### 3. Create Replicated Tables

#### 3.1 Create Database

```sql
-- Run on any node (distributed DDL)
CREATE DATABASE IF NOT EXISTS audit ON CLUSTER audit_cluster;
```

#### 3.2 Create Replicated Table

```sql
-- ReplicatedMergeTree table (single shard)
CREATE TABLE IF NOT EXISTS audit.audit_logs ON CLUSTER audit_cluster
(
    event_id String,
    timestamp DateTime,
    application_name String,
    sql_text String,
    sql_hash String,
    severity String,
    risk_score Float32,
    execution_time_ms Int64,
    rows_affected Int64,
    error_occurred UInt8,
    error_message String,
    checker_results String,
    stack_trace String,
    user_name String,
    session_id String,
    transaction_id String,
    pod_name String,
    pod_ip String,
    INDEX idx_severity severity TYPE set(0) GRANULARITY 4,
    INDEX idx_application application_name TYPE set(100) GRANULARITY 4
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/audit_logs', '{replica}')
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, severity, application_name)
TTL timestamp + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;
```

#### 3.3 Create Distributed Table

```sql
-- Distributed table for queries across all shards
CREATE TABLE IF NOT EXISTS audit.audit_logs_distributed ON CLUSTER audit_cluster AS audit.audit_logs
ENGINE = Distributed(audit_cluster, audit, audit_logs, rand());
```

#### 3.4 Create Materialized Views for Aggregations

```sql
-- Daily statistics materialized view
CREATE MATERIALIZED VIEW IF NOT EXISTS audit.audit_stats_daily ON CLUSTER audit_cluster
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, application_name, severity)
AS SELECT
    toDate(timestamp) AS date,
    application_name,
    severity,
    count() AS event_count,
    sum(execution_time_ms) AS total_execution_time,
    sum(rows_affected) AS total_rows_affected,
    sum(error_occurred) AS error_count
FROM audit.audit_logs
GROUP BY date, application_name, severity;

-- Distributed view for the materialized view
CREATE TABLE IF NOT EXISTS audit.audit_stats_daily_distributed ON CLUSTER audit_cluster AS audit.audit_stats_daily
ENGINE = Distributed(audit_cluster, audit, audit_stats_daily, rand());
```

## Verification

### 1. Check Cluster Status

```sql
-- List all clusters
SELECT * FROM system.clusters;

-- Check cluster nodes
SELECT cluster, shard_num, replica_num, host_name, host_address, port
FROM system.clusters
WHERE cluster = 'audit_cluster'
ORDER BY shard_num, replica_num;
```

### 2. Check Replication Status

```sql
-- Check replication queue
SELECT
    database,
    table,
    replica_name,
    position,
    node_name,
    type,
    create_time,
    num_tries
FROM system.replication_queue
WHERE database = 'audit'
ORDER BY create_time;

-- Should be empty or small for healthy replication
```

### 3. Check ZooKeeper Status

```sql
SELECT * FROM system.zookeeper WHERE path = '/clickhouse';

-- Check table paths in ZooKeeper
SELECT * FROM system.zookeeper WHERE path = '/clickhouse/tables/01/audit_logs/replicas';
```

### 4. Test Data Replication

```sql
-- Insert on replica1
INSERT INTO audit.audit_logs VALUES (
    'test-event-1',
    now(),
    'test-app',
    'SELECT * FROM test',
    'hash123',
    'LOW',
    10.0,
    100,
    0,
    0,
    '',
    '',
    '',
    'test-user',
    'session-1',
    'tx-1',
    'pod-1',
    '10.0.1.1'
);

-- Query from replica2
SELECT count() FROM audit.audit_logs WHERE event_id = 'test-event-1';

-- Expected: 1
```

## Kubernetes Deployment

### 1. ClickHouse Operator

**Install Altinity ClickHouse Operator**:

```bash
kubectl apply -f https://raw.githubusercontent.com/Altinity/clickhouse-operator/master/deploy/operator/clickhouse-operator-install-bundle.yaml
```

### 2. ClickHouse Cluster Definition

```yaml
apiVersion: "clickhouse.altinity.com/v1"
kind: "ClickHouseInstallation"
metadata:
  name: audit-clickhouse
  namespace: sqlguard
spec:
  configuration:
    zookeeper:
      nodes:
        - host: zk-0.zk-headless.sqlguard.svc.cluster.local
          port: 2181
        - host: zk-1.zk-headless.sqlguard.svc.cluster.local
          port: 2181
        - host: zk-2.zk-headless.sqlguard.svc.cluster.local
          port: 2181
    clusters:
      - name: audit-cluster
        layout:
          shardsCount: 3
          replicasCount: 2
        templates:
          podTemplate: clickhouse-pod
          dataVolumeClaimTemplate: data-volume
          logVolumeClaimTemplate: log-volume

  templates:
    podTemplates:
      - name: clickhouse-pod
        spec:
          containers:
            - name: clickhouse
              image: clickhouse/clickhouse-server:23.8
              resources:
                requests:
                  cpu: "2"
                  memory: "8Gi"
                limits:
                  cpu: "4"
                  memory: "16Gi"

    volumeClaimTemplates:
      - name: data-volume
        spec:
          accessModes:
            - ReadWriteOnce
          storageClassName: fast-ssd
          resources:
            requests:
              storage: 500Gi

      - name: log-volume
        spec:
          accessModes:
            - ReadWriteOnce
          storageClassName: standard
          resources:
            requests:
              storage: 10Gi
```

## Monitoring Replication Health

### 1. Key Metrics

```sql
-- Replication queue size (should be close to 0)
SELECT count() FROM system.replication_queue WHERE database = 'audit';

-- Parts status
SELECT
    database,
    table,
    sum(rows) AS rows,
    sum(bytes_on_disk) AS bytes,
    count() AS parts
FROM system.parts
WHERE database = 'audit' AND active
GROUP BY database, table;

-- Replica status
SELECT
    database,
    table,
    is_leader,
    is_readonly,
    absolute_delay,
    queue_size,
    inserts_in_queue,
    merges_in_queue
FROM system.replicas
WHERE database = 'audit';
```

### 2. Prometheus Metrics

```yaml
# clickhouse_exporter metrics
clickhouse_replication_queue_size
clickhouse_replication_delay_seconds
clickhouse_parts_count
clickhouse_rows_count
```

### 3. Alerts

```yaml
groups:
  - name: clickhouse-replication
    rules:
    - alert: ClickHouseReplicationQueueHigh
      expr: clickhouse_replication_queue_size > 100
      for: 10m
      labels:
        severity: warning
      annotations:
        summary: "ClickHouse replication queue is high"
        description: "Replication queue size is {{ $value }}"

    - alert: ClickHouseReplicaReadOnly
      expr: clickhouse_replica_is_readonly == 1
      for: 5m
      labels:
        severity: critical
      annotations:
        summary: "ClickHouse replica is in read-only mode"
        description: "Replica {{ $labels.replica }} is read-only"

    - alert: ClickHouseReplicationDelayHigh
      expr: clickhouse_replication_delay_seconds > 300
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "ClickHouse replication delay is high"
        description: "Replication delay is {{ $value }} seconds"
```

## Backup and Restore

### 1. Using clickhouse-backup

```bash
# Create backup
clickhouse-backup create audit-backup-$(date +%Y%m%d)

# Upload to S3
clickhouse-backup upload audit-backup-$(date +%Y%m%d)

# List backups
clickhouse-backup list

# Download from S3
clickhouse-backup download audit-backup-20250101

# Restore
clickhouse-backup restore audit-backup-20250101
```

### 2. Configuration

```yaml
# /etc/clickhouse-backup/config.yml
general:
  remote_storage: s3
  disable_progress_bar: false
  backups_to_keep_local: 3
  backups_to_keep_remote: 30

clickhouse:
  host: localhost
  port: 9000
  username: default
  password: ""
  data_path: /var/lib/clickhouse

s3:
  access_key: "AWS_ACCESS_KEY"
  secret_key: "AWS_SECRET_KEY"
  bucket: "clickhouse-backups"
  region: "us-west-2"
  path: "audit/"
  disable_ssl: false
  compression_format: "gzip"
```

## Performance Tuning

### 1. MergeTree Settings

```sql
-- Optimize for write-heavy workload
ALTER TABLE audit.audit_logs MODIFY SETTING
    merge_with_ttl_timeout = 3600,
    min_bytes_for_wide_part = 10485760,
    max_bytes_to_merge_at_max_space_in_pool = 161061273600;
```

### 2. Compression

```sql
-- Use ZSTD compression for better compression ratio
ALTER TABLE audit.audit_logs MODIFY COLUMN sql_text String CODEC(ZSTD(3));
ALTER TABLE audit.audit_logs MODIFY COLUMN error_message String CODEC(ZSTD(3));
```

### 3. Partitioning Strategy

```sql
-- Partition by month (default)
-- For very high volume, consider partitioning by day:
ALTER TABLE audit.audit_logs MODIFY SETTING
    partition_by = toYYYYMMDD(timestamp);
```

## Troubleshooting

### Issue: Replica Falls Behind

```sql
-- Check replication delay
SELECT absolute_delay FROM system.replicas WHERE database = 'audit';

-- Force sync
SYSTEM SYNC REPLICA audit.audit_logs;
```

### Issue: Too Many Parts

```sql
-- Check parts count
SELECT count() FROM system.parts WHERE database = 'audit' AND active;

-- Force merge
OPTIMIZE TABLE audit.audit_logs FINAL;
```

### Issue: ZooKeeper Connection Lost

```bash
# Check ZooKeeper status
echo stat | nc zk1.example.com 2181

# Restart ClickHouse if ZooKeeper recovered
sudo systemctl restart clickhouse-server
```

### Issue: Read-Only Replica

```sql
-- Check why replica is read-only
SELECT * FROM system.replicas WHERE database = 'audit' AND is_readonly = 1;

-- Common causes:
-- 1. ZooKeeper connection lost
-- 2. Disk full
-- 3. Replication queue too large

-- Force writable (use with caution)
SYSTEM ENABLE WRITES;
```

## Best Practices

1. **Use ReplicatedMergeTree**: Always use ReplicatedMergeTree for production tables
2. **Distributed Queries**: Query distributed tables for multi-shard queries
3. **Direct Writes**: Write directly to local tables (not distributed) for better performance
4. **Partition Pruning**: Always filter by partition key (timestamp) in queries
5. **Regular Backups**: Automate backups with clickhouse-backup
6. **Monitor Replication**: Set up alerts for replication lag and queue size
7. **ZooKeeper Health**: Ensure ZooKeeper cluster is stable and has sufficient resources

## References

- [ClickHouse Replication Documentation](https://clickhouse.com/docs/en/engines/table-engines/mergetree-family/replication/)
- [ClickHouse Operator](https://github.com/Altinity/clickhouse-operator)
- [clickhouse-backup Tool](https://github.com/AlexAkulov/clickhouse-backup)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-01
**Maintainer**: Database Team
