# ClickHouse Backup and Recovery Guide

## Overview

This guide provides instructions for backing up and restoring ClickHouse audit log data.

## Backup Strategy

- **Full Backup**: Daily at 3 AM UTC
- **Incremental**: Not needed (immutable time-series data)
- **Retention**: 90 days (compliance requirement)
- **Storage**: S3 with lifecycle policy

## 1. Using clickhouse-backup Tool

### 1.1 Installation

```bash
# Download latest release
wget https://github.com/AlexAkulov/clickhouse-backup/releases/download/v2.4.0/clickhouse-backup-linux-amd64.tar.gz

# Extract
tar -xzf clickhouse-backup-linux-amd64.tar.gz

# Move to system path
sudo mv clickhouse-backup /usr/local/bin/

# Verify installation
clickhouse-backup --version
```

### 1.2 Configuration

**/etc/clickhouse-backup/config.yml:**

```yaml
general:
  remote_storage: s3
  max_file_size: 1073741824  # 1GB per file
  backups_to_keep_local: 3
  backups_to_keep_remote: 90
  upload_concurrency: 4
  download_concurrency: 4

clickhouse:
  username: default
  password: ""
  host: localhost
  port: 9000
  data_path: /var/lib/clickhouse
  skip_tables:
    - system.*
    - INFORMATION_SCHEMA.*

s3:
  access_key: "${AWS_ACCESS_KEY_ID}"
  secret_key: "${AWS_SECRET_ACCESS_KEY}"
  bucket: "sqlguard-backups"
  region: "us-west-2"
  path: "clickhouse/"
  disable_ssl: false
  compression_format: "gzip"
  compression_level: 1
  sse: "AES256"
```

### 1.3 Create Backup

```bash
# Create local backup
clickhouse-backup create audit-$(date +%Y%m%d)

# List local backups
clickhouse-backup list local

# Upload to S3
clickhouse-backup upload audit-$(date +%Y%m%d)

# List remote backups
clickhouse-backup list remote
```

### 1.4 Automated Backup Script

```bash
#!/bin/bash
# /opt/scripts/clickhouse-backup.sh
set -e

BACKUP_NAME="audit-$(date +%Y%m%d_%H%M%S)"

echo "[$(date)] Creating ClickHouse backup: ${BACKUP_NAME}"

# Create backup
clickhouse-backup create "${BACKUP_NAME}"

# Upload to S3
clickhouse-backup upload "${BACKUP_NAME}"

# Verify upload
if clickhouse-backup list remote | grep -q "${BACKUP_NAME}"; then
  echo "[$(date)] Backup uploaded successfully: ${BACKUP_NAME}"

  # Delete local backup after successful upload
  clickhouse-backup delete local "${BACKUP_NAME}"
else
  echo "[$(date)] ERROR: Backup upload failed"
  exit 1
fi

# Clean up old remote backups (keep 90 days)
clickhouse-backup list remote | tail -n +91 | while read backup_name; do
  clickhouse-backup delete remote "${backup_name}"
  echo "[$(date)] Deleted old backup: ${backup_name}"
done

echo "[$(date)] Backup process completed"
```

### 1.5 Schedule with Cron

```bash
# Daily at 3 AM UTC
0 3 * * * /opt/scripts/clickhouse-backup.sh >> /var/log/clickhouse-backup.log 2>&1
```

## 2. Kubernetes CronJob

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: clickhouse-backup
  namespace: sqlguard
spec:
  schedule: "0 3 * * *"
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: alexakulov/clickhouse-backup:latest
            env:
            - name: CLICKHOUSE_HOST
              value: "clickhouse.sqlguard.svc.cluster.local"
            - name: AWS_ACCESS_KEY_ID
              valueFrom:
                secretKeyRef:
                  name: s3-credentials
                  key: access-key-id
            - name: AWS_SECRET_ACCESS_KEY
              valueFrom:
                secretKeyRef:
                  name: s3-credentials
                  key: secret-access-key
            - name: S3_BUCKET
              value: "sqlguard-backups"
            - name: S3_PATH
              value: "clickhouse/"
            command:
            - /bin/bash
            - -c
            - |
              BACKUP_NAME="audit-$(date +%Y%m%d_%H%M%S)"

              clickhouse-backup create "${BACKUP_NAME}"
              clickhouse-backup upload "${BACKUP_NAME}"

              echo "Backup completed: ${BACKUP_NAME}"
            volumeMounts:
            - name: config
              mountPath: /etc/clickhouse-backup
          volumes:
          - name: config
            configMap:
              name: clickhouse-backup-config
          restartPolicy: OnFailure
```

## 3. Restore from Backup

### 3.1 List Available Backups

```bash
# List remote backups
clickhouse-backup list remote

# Expected output:
# audit-20250101_030000
# audit-20250102_030000
# audit-20250103_030000
```

### 3.2 Download and Restore

```bash
# Download backup from S3
clickhouse-backup download audit-20250101_030000

# Restore backup (this will overwrite existing data!)
clickhouse-backup restore audit-20250101_030000

# Verify restoration
clickhouse-client --query "SELECT count(*) FROM audit.audit_logs"
```

### 3.3 Restore Specific Tables

```bash
# Restore only specific tables
clickhouse-backup restore --table audit.audit_logs audit-20250101_030000
clickhouse-backup restore --table audit.audit_stats_daily audit-20250101_030000
```

## 4. Partition-Level Backup

### 4.1 Backup Specific Partition

```sql
-- Freeze partition (creates hardlink snapshot)
ALTER TABLE audit.audit_logs FREEZE PARTITION '202501';

-- Backup frozen partition to S3
-- Use clickhouse-backup with partition filter
```

### 4.2 Restore Partition

```sql
-- Detach partition
ALTER TABLE audit.audit_logs DETACH PARTITION '202501';

-- Copy partition data from backup
-- (manually copy files to detached directory)

-- Attach partition
ALTER TABLE audit.audit_logs ATTACH PARTITION '202501';
```

## 5. Data Export/Import

### 5.1 Export to S3

```sql
-- Export to S3 in Parquet format
INSERT INTO FUNCTION
s3('https://s3.us-west-2.amazonaws.com/sqlguard-backups/clickhouse/export/audit_logs_202501.parquet',
   'AccessKeyId', 'SecretKey',
   'Parquet')
SELECT * FROM audit.audit_logs
WHERE toYYYYMM(timestamp) = 202501;
```

### 5.2 Import from S3

```sql
-- Import from S3
INSERT INTO audit.audit_logs
SELECT * FROM s3('https://s3.us-west-2.amazonaws.com/sqlguard-backups/clickhouse/export/audit_logs_202501.parquet',
                 'AccessKeyId', 'SecretKey',
                 'Parquet');
```

## 6. Disaster Recovery

### 6.1 Complete Cluster Loss

**Recovery Steps:**

1. **Provision New ClickHouse Cluster**
   - Same configuration as original
   - Same number of nodes and shards

2. **Restore Schema**
   ```bash
   # Download schema from backup
   clickhouse-backup download audit-latest --schema

   # Restore schema only
   clickhouse-backup restore --schema audit-latest
   ```

3. **Restore Data**
   ```bash
   # Restore data
   clickhouse-backup restore --data audit-latest
   ```

4. **Verify Data**
   ```sql
   SELECT
     database,
     table,
     formatReadableSize(sum(bytes)) AS size,
     sum(rows) AS rows
   FROM system.parts
   WHERE database = 'audit' AND active
   GROUP BY database, table;
   ```

5. **Update Application Configuration**
   ```bash
   # Update Kubernetes secret
   kubectl patch secret audit-secrets -n sqlguard \
     -p '{"stringData":{"clickhouse.url":"jdbc:clickhouse://new-clickhouse:8123/audit"}}'

   # Restart audit service
   kubectl rollout restart statefulset/sql-audit-service -n sqlguard
   ```

## 7. Backup Verification

### 7.1 Verify Backup Integrity

```bash
# Download backup
clickhouse-backup download audit-20250101_030000

# List backup contents
clickhouse-backup list local

# Check backup metadata
cat /var/lib/clickhouse/backup/audit-20250101_030000/metadata.json
```

### 7.2 Test Restore (Staging Environment)

```bash
# Monthly restore test
clickhouse-backup restore --table audit.audit_logs_test audit-latest

# Verify data
clickhouse-client --query "SELECT count(*) FROM audit.audit_logs_test"

# Clean up
clickhouse-client --query "DROP TABLE audit.audit_logs_test"
```

## 8. Monitoring

### 8.1 Backup Success Tracking

```bash
# Check last backup
aws s3 ls s3://sqlguard-backups/clickhouse/ | tail -1

# Check backup size trend
aws s3 ls s3://sqlguard-backups/clickhouse/ --recursive | \
  awk '{print $3, $4}' | grep "audit-"
```

### 8.2 Alerts

```yaml
- alert: ClickHouseBackupMissing
  expr: (time() - clickhouse_backup_last_success_timestamp_seconds) > 90000
  for: 1h
  labels:
    severity: critical
  annotations:
    summary: "ClickHouse backup hasn't run in 25+ hours"
```

## 9. Performance Optimization

### 9.1 Compression

- Use `gzip` compression level 1 for faster backups
- Trade-off: slightly larger backup size vs. faster backup time

### 9.2 Incremental Backups

```yaml
general:
  incremental: true  # Only backup new/changed parts
```

### 9.3 Parallel Upload

```yaml
general:
  upload_concurrency: 8  # Increase for faster uploads
```

## 10. Security

### 10.1 Encryption

```yaml
s3:
  sse: "aws:kms"
  sse_kms_key_id: "arn:aws:kms:us-west-2:123456789012:key/12345678-1234-1234-1234-123456789012"
```

### 10.2 Access Control

- Use IAM roles for S3 access (no static credentials)
- Enable S3 bucket versioning
- Use S3 Object Lock for immutable backups

## References

- [clickhouse-backup GitHub](https://github.com/AlexAkulov/clickhouse-backup)
- [ClickHouse Backup Documentation](https://clickhouse.com/docs/en/operations/backup/)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-01
**Maintainer**: Database Team
