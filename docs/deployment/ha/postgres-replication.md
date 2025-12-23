# PostgreSQL High Availability Configuration

## Overview

This guide describes how to configure PostgreSQL with streaming replication for high availability in the SQL Audit Service deployment.

## Architecture

```
┌──────────────────┐         Streaming          ┌──────────────────┐
│                  │         Replication         │                  │
│  PostgreSQL      │────────────────────────────>│  PostgreSQL      │
│  Primary         │         (async/sync)        │  Standby         │
│  (Read/Write)    │                             │  (Read-only)     │
└──────────────────┘                             └──────────────────┘
        │                                                 │
        │                                                 │
        └─────────────────┬───────────────────────────────┘
                          │
                  Automatic Failover
                  (Patroni or Stolon)
```

## Prerequisites

- PostgreSQL 14+ installed on both primary and standby nodes
- Network connectivity between primary and standby (port 5432)
- Sufficient storage on standby node
- NTP synchronized between nodes

## Configuration Steps

### 1. Primary Node Configuration

#### 1.1 Edit postgresql.conf

```ini
# File: /var/lib/postgresql/data/postgresql.conf

# Enable replication
wal_level = replica
max_wal_senders = 10
max_replication_slots = 10
wal_keep_size = 1GB

# Archive configuration (optional, for PITR)
archive_mode = on
archive_command = 'cp %p /archive/%f'

# Connection settings
listen_addresses = '*'
port = 5432

# Performance tuning
shared_buffers = 2GB
effective_cache_size = 6GB
maintenance_work_mem = 512MB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 100
random_page_cost = 1.1
effective_io_concurrency = 200
work_mem = 10MB
min_wal_size = 1GB
max_wal_size = 4GB
```

#### 1.2 Edit pg_hba.conf

```ini
# File: /var/lib/postgresql/data/pg_hba.conf

# Allow replication connections from standby
host    replication     replication     <STANDBY_IP>/32         scram-sha-256

# Allow application connections
host    audit          audit_user       0.0.0.0/0              scram-sha-256
```

#### 1.3 Create Replication User

```sql
-- Connect to primary as superuser
CREATE USER replication REPLICATION LOGIN ENCRYPTED PASSWORD 'CHANGE_ME_REPLICATION_PASSWORD';

-- Create replication slot
SELECT * FROM pg_create_physical_replication_slot('standby_slot');
```

#### 1.4 Restart Primary

```bash
sudo systemctl restart postgresql
```

### 2. Standby Node Configuration

#### 2.1 Base Backup from Primary

```bash
# Stop PostgreSQL on standby
sudo systemctl stop postgresql

# Remove existing data directory
sudo rm -rf /var/lib/postgresql/data/*

# Take base backup from primary
sudo -u postgres pg_basebackup \
  -h <PRIMARY_IP> \
  -D /var/lib/postgresql/data \
  -U replication \
  -P \
  -v \
  -R \
  -X stream \
  -C -S standby_slot

# The -R flag automatically creates standby.signal and recovery configuration
```

#### 2.2 Verify standby.signal

```bash
# File should exist: /var/lib/postgresql/data/standby.signal
ls -la /var/lib/postgresql/data/standby.signal
```

#### 2.3 Edit postgresql.auto.conf

```ini
# File: /var/lib/postgresql/data/postgresql.auto.conf
# (created automatically by pg_basebackup -R)

primary_conninfo = 'host=<PRIMARY_IP> port=5432 user=replication password=CHANGE_ME_REPLICATION_PASSWORD application_name=standby1'
primary_slot_name = 'standby_slot'
hot_standby = on
```

#### 2.4 Start Standby

```bash
sudo systemctl start postgresql
```

### 3. Verification

#### 3.1 Check Replication Status on Primary

```sql
-- Connect to primary
SELECT * FROM pg_stat_replication;

-- Expected output:
--  pid  | usename     | application_name | client_addr | state     | sync_state
-- ------+-------------+------------------+-------------+-----------+------------
-- 12345 | replication | standby1         | 10.0.1.100  | streaming | async
```

#### 3.2 Check Replication Status on Standby

```sql
-- Connect to standby
SELECT pg_is_in_recovery();

-- Expected output: t (true)

SELECT * FROM pg_stat_wal_receiver;

-- Shows connection to primary and streaming status
```

#### 3.3 Test Replication Lag

```sql
-- On primary
SELECT pg_current_wal_lsn();

-- On standby
SELECT pg_last_wal_receive_lsn(), pg_last_wal_replay_lsn();

-- Calculate lag in bytes
SELECT
  pg_wal_lsn_diff(pg_last_wal_receive_lsn(), pg_last_wal_replay_lsn()) AS lag_bytes;
```

## Synchronous vs Asynchronous Replication

### Asynchronous Replication (Default)

**Configuration**: No additional settings needed

**Pros**:
- No impact on primary performance
- Standby can lag without blocking primary

**Cons**:
- Potential data loss if primary fails (RPO > 0)

**Use Case**: Most production deployments

### Synchronous Replication

**Configuration on Primary**:

```ini
# postgresql.conf
synchronous_commit = on
synchronous_standby_names = 'standby1'
```

**Pros**:
- Zero data loss (RPO = 0)
- Guarantees durability

**Cons**:
- Primary writes blocked until standby confirms
- Higher latency

**Use Case**: Financial systems, critical data

## Automatic Failover with Patroni

### 1. Install Patroni

```bash
pip install patroni[etcd]
```

### 2. Patroni Configuration

```yaml
# File: /etc/patroni/patroni.yml

scope: audit-postgres
namespace: /service/
name: postgres-node1

restapi:
  listen: 0.0.0.0:8008
  connect_address: <NODE_IP>:8008

etcd:
  hosts: etcd1:2379,etcd2:2379,etcd3:2379

bootstrap:
  dcs:
    ttl: 30
    loop_wait: 10
    retry_timeout: 10
    maximum_lag_on_failover: 1048576
    postgresql:
      use_pg_rewind: true
      parameters:
        wal_level: replica
        hot_standby: on
        max_wal_senders: 10
        max_replication_slots: 10

  initdb:
  - encoding: UTF8
  - data-checksums

postgresql:
  listen: 0.0.0.0:5432
  connect_address: <NODE_IP>:5432
  data_dir: /var/lib/postgresql/data
  pgpass: /tmp/pgpass
  authentication:
    replication:
      username: replication
      password: CHANGE_ME_REPLICATION_PASSWORD
    superuser:
      username: postgres
      password: CHANGE_ME_POSTGRES_PASSWORD
  parameters:
    unix_socket_directories: '/var/run/postgresql'

tags:
  nofailover: false
  noloadbalance: false
  clonefrom: false
  nosync: false
```

### 3. Start Patroni

```bash
sudo systemctl enable patroni
sudo systemctl start patroni

# Check status
patronictl -c /etc/patroni/patroni.yml list
```

### 4. Verify Patroni Cluster

```bash
$ patronictl -c /etc/patroni/patroni.yml list

+ Cluster: audit-postgres (7234567890123456789) --------+----+-----------+
| Member         | Host         | Role    | State     | TL | Lag in MB |
+----------------+--------------+---------+-----------+----+-----------+
| postgres-node1 | 10.0.1.10    | Leader  | running   |  1 |           |
| postgres-node2 | 10.0.1.11    | Replica | streaming |  1 |         0 |
+----------------+--------------+---------+-----------+----+-----------+
```

### 5. Test Failover

```bash
# Manual failover
patronictl -c /etc/patroni/patroni.yml failover

# Automatic failover test: stop primary
sudo systemctl stop patroni  # On primary node

# Watch Patroni promote standby to leader (typically < 30s)
```

## Kubernetes Deployment

### PostgreSQL StatefulSet with HA

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: sqlguard
spec:
  serviceName: postgres
  replicas: 2
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:14
        env:
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secrets
              key: postgres-password
        - name: POSTGRES_REPLICATION_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secrets
              key: replication-password
        ports:
        - containerPort: 5432
          name: postgres
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
        - name: config
          mountPath: /etc/postgresql
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: fast-ssd
      resources:
        requests:
          storage: 100Gi
```

### Using PostgreSQL Operator (Recommended)

**Install Zalando Postgres Operator**:

```bash
helm repo add postgres-operator-charts https://opensource.zalando.com/postgres-operator/charts/postgres-operator
helm install postgres-operator postgres-operator-charts/postgres-operator
```

**Create PostgreSQL Cluster**:

```yaml
apiVersion: "acid.zalan.do/v1"
kind: postgresql
metadata:
  name: audit-postgres
  namespace: sqlguard
spec:
  teamId: "audit"
  volume:
    size: 100Gi
    storageClass: fast-ssd
  numberOfInstances: 2
  users:
    audit_user:
    - superuser
    - createdb
  databases:
    audit: audit_user
  postgresql:
    version: "14"
    parameters:
      shared_buffers: "2GB"
      max_connections: "100"
      work_mem: "10MB"
  resources:
    requests:
      cpu: 2
      memory: 4Gi
    limits:
      cpu: 4
      memory: 8Gi
  patroni:
    initdb:
      encoding: "UTF8"
      locale: "en_US.UTF-8"
      data-checksums: "true"
    pg_hba:
    - host all all 0.0.0.0/0 md5
    - host replication replication 0.0.0.0/0 md5
```

## Monitoring Replication Health

### Key Metrics

```sql
-- Replication lag (bytes)
SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), replay_lsn) AS lag_bytes
FROM pg_stat_replication;

-- Replication lag (seconds)
SELECT EXTRACT(EPOCH FROM (now() - pg_last_xact_replay_timestamp())) AS lag_seconds;

-- Replication slots status
SELECT slot_name, active, restart_lsn, pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn) AS lag_bytes
FROM pg_replication_slots;
```

### Prometheus Metrics

```yaml
# postgres_exporter metrics
pg_replication_lag_bytes
pg_replication_lag_seconds
pg_stat_replication_up
```

### Alerts

```yaml
groups:
  - name: postgres-replication
    rules:
    - alert: PostgreSQLReplicationLag
      expr: pg_replication_lag_bytes > 104857600  # 100MB
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "PostgreSQL replication lag is high"
        description: "Replication lag is {{ $value }} bytes"

    - alert: PostgreSQLReplicationDown
      expr: pg_stat_replication_up == 0
      for: 1m
      labels:
        severity: critical
      annotations:
        summary: "PostgreSQL replication is down"
```

## Backup Strategy

Even with replication, regular backups are essential:

```bash
# Daily base backup
pg_basebackup -h <PRIMARY_IP> -D /backup/base-$(date +%Y%m%d) -U replication -P

# Continuous WAL archiving (already configured in postgresql.conf)
# WAL files are archived to /archive/ directory

# Point-in-Time Recovery (PITR)
# Can restore to any point in time using base backup + WAL files
```

## Troubleshooting

### Issue: Replication Slot Filling Up

```sql
-- Check slot status
SELECT * FROM pg_replication_slots;

-- If standby is down, slot will fill up and consume disk
-- Temporarily drop slot if needed
SELECT pg_drop_replication_slot('standby_slot');

-- Recreate when standby is back
SELECT pg_create_physical_replication_slot('standby_slot');
```

### Issue: Standby Cannot Catch Up

```bash
# Re-sync standby from primary
sudo systemctl stop postgresql  # On standby
sudo rm -rf /var/lib/postgresql/data/*
sudo -u postgres pg_basebackup -h <PRIMARY_IP> -D /var/lib/postgresql/data -U replication -P -R
sudo systemctl start postgresql
```

### Issue: Split-Brain Scenario

**Patroni prevents this automatically**. If using manual failover:

```sql
-- On suspected old primary, check if read-only
SHOW transaction_read_only;

-- If "on", it's correctly demoted
-- If "off", manually promote the correct new primary:
SELECT pg_promote();
```

## Performance Tuning

### Optimize Replication Throughput

```ini
# postgresql.conf on primary
wal_compression = on
wal_writer_delay = 200ms
max_wal_size = 4GB

# Reduce checkpoint frequency
checkpoint_timeout = 15min
checkpoint_completion_target = 0.9
```

### Tune Standby for Read Queries

```ini
# postgresql.conf on standby
hot_standby_feedback = on  # Prevent query cancellations
max_standby_streaming_delay = 30s
```

## References

- [PostgreSQL Streaming Replication Documentation](https://www.postgresql.org/docs/current/warm-standby.html)
- [Patroni Documentation](https://patroni.readthedocs.io/)
- [Zalando PostgreSQL Operator](https://github.com/zalando/postgres-operator)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-01
**Maintainer**: Database Team
