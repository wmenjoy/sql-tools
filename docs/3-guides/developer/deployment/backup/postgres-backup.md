# PostgreSQL Backup and Recovery Guide

## Overview

This guide provides comprehensive instructions for backing up and restoring PostgreSQL databases used by the SQL Audit Service.

## Backup Strategy

### RTO/RPO Objectives

- **RTO (Recovery Time Objective)**: < 1 hour
- **RPO (Recovery Point Objective)**: < 5 minutes

### Backup Types

| Type | Frequency | Retention | Storage Location |
|------|-----------|-----------|------------------|
| Full Backup | Daily (2 AM UTC) | 30 days | S3/GCS |
| WAL Archive | Continuous | 30 days | S3/GCS |
| Snapshot | Weekly (Sunday 2 AM) | 90 days | Block Storage |

## 1. Full Database Backup

### 1.1 Using pg_dump

**Backup Script** (`/opt/scripts/postgres-backup.sh`):

```bash
#!/bin/bash
set -e

# Configuration
POSTGRES_HOST="${POSTGRES_HOST:-postgres-primary.sqlguard.svc.cluster.local}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-audit}"
POSTGRES_DB="${POSTGRES_DB:-audit}"
BACKUP_DIR="${BACKUP_DIR:-/backup/postgresql}"
S3_BUCKET="${S3_BUCKET:-s3://sqlguard-backups/postgresql}"
RETENTION_DAYS=30

# Generate backup filename
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="audit-backup-${DATE}.sql.gz"
BACKUP_PATH="${BACKUP_DIR}/${BACKUP_FILE}"

# Create backup directory
mkdir -p "${BACKUP_DIR}"

# Perform backup
echo "[$(date)] Starting PostgreSQL backup..."
PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
  -h "${POSTGRES_HOST}" \
  -p "${POSTGRES_PORT}" \
  -U "${POSTGRES_USER}" \
  -d "${POSTGRES_DB}" \
  --format=plain \
  --no-owner \
  --no-privileges \
  --verbose \
  | gzip > "${BACKUP_PATH}"

# Verify backup
if [ -f "${BACKUP_PATH}" ]; then
  SIZE=$(du -h "${BACKUP_PATH}" | cut -f1)
  echo "[$(date)] Backup completed: ${BACKUP_FILE} (${SIZE})"
else
  echo "[$(date)] ERROR: Backup file not created"
  exit 1
fi

# Upload to S3
echo "[$(date)] Uploading to S3..."
aws s3 cp "${BACKUP_PATH}" "${S3_BUCKET}/" \
  --storage-class STANDARD_IA \
  --metadata "backup-date=${DATE},database=${POSTGRES_DB}"

if [ $? -eq 0 ]; then
  echo "[$(date)] Successfully uploaded to S3"
else
  echo "[$(date)] ERROR: S3 upload failed"
  exit 1
fi

# Clean up local backups older than retention period
echo "[$(date)] Cleaning up old backups..."
find "${BACKUP_DIR}" -name "audit-backup-*.sql.gz" -mtime +${RETENTION_DAYS} -delete

# Clean up S3 backups (if not using S3 lifecycle policy)
aws s3 ls "${S3_BUCKET}/" | \
  awk '{if ($1 < "'$(date -d "${RETENTION_DAYS} days ago" +%Y-%m-%d)'") print $4}' | \
  xargs -I {} aws s3 rm "${S3_BUCKET}/{}"

echo "[$(date)] Backup process completed successfully"
```

**Make executable:**

```bash
chmod +x /opt/scripts/postgres-backup.sh
```

### 1.2 Schedule with Cron

```bash
# Edit crontab
crontab -e

# Add daily backup at 2 AM UTC
0 2 * * * /opt/scripts/postgres-backup.sh >> /var/log/postgres-backup.log 2>&1
```

### 1.3 Kubernetes CronJob

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
  namespace: sqlguard
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM UTC
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:14
            env:
            - name: POSTGRES_HOST
              value: "postgres-primary.sqlguard.svc.cluster.local"
            - name: POSTGRES_DB
              value: "audit"
            - name: POSTGRES_USER
              valueFrom:
                secretKeyRef:
                  name: audit-secrets
                  key: postgres.username
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: audit-secrets
                  key: postgres.password
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
            command:
            - /bin/bash
            - -c
            - |
              apt-get update && apt-get install -y awscli gzip
              DATE=$(date +%Y%m%d_%H%M%S)
              BACKUP_FILE="audit-backup-${DATE}.sql.gz"

              pg_dump -h $POSTGRES_HOST -U $POSTGRES_USER -d $POSTGRES_DB | gzip > /tmp/${BACKUP_FILE}

              aws s3 cp /tmp/${BACKUP_FILE} s3://sqlguard-backups/postgresql/

              echo "Backup completed: ${BACKUP_FILE}"
            volumeMounts:
            - name: backup-storage
              mountPath: /backup
          volumes:
          - name: backup-storage
            persistentVolumeClaim:
              claimName: postgres-backup-pvc
          restartPolicy: OnFailure
```

## 2. WAL (Write-Ahead Log) Archiving

### 2.1 Enable WAL Archiving

**postgresql.conf:**

```ini
# Enable WAL archiving
wal_level = replica
archive_mode = on
archive_command = '/opt/scripts/archive-wal.sh %p %f'
archive_timeout = 300  # Force archive every 5 minutes

# WAL retention
wal_keep_size = 1GB
max_wal_senders = 10
```

### 2.2 WAL Archive Script

**`/opt/scripts/archive-wal.sh`:**

```bash
#!/bin/bash
set -e

# Parameters
WAL_PATH=$1   # %p - full path to WAL file
WAL_FILE=$2   # %f - filename only

# Configuration
S3_BUCKET="s3://sqlguard-backups/postgresql/wal"
LOCAL_ARCHIVE="/archive/postgresql/wal"

# Create local archive directory
mkdir -p "${LOCAL_ARCHIVE}"

# Copy to local archive
cp "${WAL_PATH}" "${LOCAL_ARCHIVE}/${WAL_FILE}"

# Upload to S3
aws s3 cp "${LOCAL_ARCHIVE}/${WAL_FILE}" "${S3_BUCKET}/" \
  --storage-class STANDARD_IA

# Verify upload
if aws s3 ls "${S3_BUCKET}/${WAL_FILE}" > /dev/null; then
  # Clean up local file after successful upload
  rm -f "${LOCAL_ARCHIVE}/${WAL_FILE}"
  exit 0
else
  echo "ERROR: WAL file upload failed: ${WAL_FILE}"
  exit 1
fi
```

**Make executable:**

```bash
chmod +x /opt/scripts/archive-wal.sh
```

### 2.3 Verify WAL Archiving

```sql
-- Check archive status
SELECT * FROM pg_stat_archiver;

-- Expected output:
--  archived_count | last_archived_wal        | last_archived_time
-- ----------------+--------------------------+------------------------
--  1234           | 000000010000000100000ABC | 2025-01-01 12:00:00
```

## 3. Point-in-Time Recovery (PITR)

### 3.1 Restore from Backup

```bash
#!/bin/bash
# restore-postgres.sh

# Configuration
RESTORE_DATE="20250101_020000"  # Backup to restore
RECOVERY_TARGET_TIME="2025-01-01 12:00:00"  # Optional: specific time
S3_BUCKET="s3://sqlguard-backups/postgresql"
POSTGRES_DATA_DIR="/var/lib/postgresql/14/main"

# Step 1: Stop PostgreSQL
sudo systemctl stop postgresql

# Step 2: Backup current data (safety)
sudo mv "${POSTGRES_DATA_DIR}" "${POSTGRES_DATA_DIR}.old"

# Step 3: Create new data directory
sudo mkdir -p "${POSTGRES_DATA_DIR}"
sudo chown postgres:postgres "${POSTGRES_DATA_DIR}"

# Step 4: Download backup from S3
BACKUP_FILE="audit-backup-${RESTORE_DATE}.sql.gz"
aws s3 cp "${S3_BUCKET}/${BACKUP_FILE}" /tmp/

# Step 5: Initialize PostgreSQL
sudo -u postgres initdb -D "${POSTGRES_DATA_DIR}"

# Step 6: Start PostgreSQL temporarily
sudo systemctl start postgresql

# Step 7: Restore database
gunzip -c /tmp/${BACKUP_FILE} | sudo -u postgres psql -d audit

# Step 8: Configure recovery (if doing PITR)
if [ -n "${RECOVERY_TARGET_TIME}" ]; then
  sudo systemctl stop postgresql

  # Create recovery.signal file
  sudo -u postgres touch "${POSTGRES_DATA_DIR}/recovery.signal"

  # Configure recovery settings
  cat >> "${POSTGRES_DATA_DIR}/postgresql.auto.conf" <<EOF
restore_command = 'aws s3 cp s3://sqlguard-backups/postgresql/wal/%f %p'
recovery_target_time = '${RECOVERY_TARGET_TIME}'
recovery_target_action = 'promote'
EOF

  # Start PostgreSQL for recovery
  sudo systemctl start postgresql

  # Monitor recovery
  sudo -u postgres tail -f /var/log/postgresql/postgresql-14-main.log
fi

echo "Restore completed successfully"
```

### 3.2 Verify Restored Database

```sql
-- Check database size
SELECT pg_size_pretty(pg_database_size('audit'));

-- Check table counts
SELECT
  schemaname,
  tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;

-- Check latest timestamp in audit tables
SELECT max(updated_at) FROM checker_config;
SELECT max(created_at) FROM whitelist_rules;
```

## 4. Volume Snapshots (Cloud Provider)

### 4.1 AWS EBS Snapshots

```bash
# Create snapshot
aws ec2 create-snapshot \
  --volume-id vol-1234567890abcdef0 \
  --description "PostgreSQL audit DB - $(date +%Y-%m-%d)" \
  --tag-specifications 'ResourceType=snapshot,Tags=[{Key=Name,Value=postgres-audit},{Key=Backup,Value=weekly}]'

# List snapshots
aws ec2 describe-snapshots \
  --owner-ids self \
  --filters "Name=tag:Name,Values=postgres-audit"

# Restore from snapshot
aws ec2 create-volume \
  --snapshot-id snap-1234567890abcdef0 \
  --availability-zone us-west-2a \
  --tag-specifications 'ResourceType=volume,Tags=[{Key=Name,Value=postgres-audit-restored}]'
```

### 4.2 GCP Persistent Disk Snapshots

```bash
# Create snapshot
gcloud compute disks snapshot postgres-audit-disk \
  --snapshot-names=postgres-audit-$(date +%Y%m%d) \
  --zone=us-west1-a

# List snapshots
gcloud compute snapshots list --filter="name:postgres-audit"

# Restore from snapshot
gcloud compute disks create postgres-audit-restored \
  --source-snapshot=postgres-audit-20250101 \
  --zone=us-west1-a
```

## 5. Backup Verification

### 5.1 Automated Verification Script

```bash
#!/bin/bash
# verify-backup.sh

BACKUP_FILE=$1

# Create temporary database
createdb audit_test

# Restore backup
gunzip -c "${BACKUP_FILE}" | psql -d audit_test

# Run verification queries
psql -d audit_test -c "SELECT count(*) FROM checker_config;" > /tmp/verify.log
psql -d audit_test -c "SELECT count(*) FROM whitelist_rules;" >> /tmp/verify.log

# Check for errors
if grep -q "ERROR" /tmp/verify.log; then
  echo "Backup verification FAILED"
  exit 1
else
  echo "Backup verification PASSED"
fi

# Clean up
dropdb audit_test
```

### 5.2 Monthly Full Restore Test

Schedule monthly restore tests to non-production environment:

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup-verify
  namespace: sqlguard
spec:
  schedule: "0 3 1 * *"  # First day of month at 3 AM
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: verify
            image: postgres:14
            command:
            - /bin/bash
            - -c
            - |
              # Download latest backup
              aws s3 cp s3://sqlguard-backups/postgresql/$(aws s3 ls s3://sqlguard-backups/postgresql/ | tail -1 | awk '{print $4}') /tmp/backup.sql.gz

              # Create test database
              createdb audit_test

              # Restore
              gunzip -c /tmp/backup.sql.gz | psql -d audit_test

              # Verify
              psql -d audit_test -c "SELECT count(*) FROM checker_config;"

              echo "Backup verification completed"
          restartPolicy: OnFailure
```

## 6. Disaster Recovery Procedures

### 6.1 Complete Database Loss

**Recovery Steps:**

1. **Assess Damage**
   ```bash
   # Check if any data can be salvaged
   sudo -u postgres pg_controldata /var/lib/postgresql/14/main
   ```

2. **Prepare New Environment**
   ```bash
   # Provision new PostgreSQL instance
   # Ensure same version as original
   ```

3. **Download Latest Backup**
   ```bash
   # Get most recent backup
   LATEST_BACKUP=$(aws s3 ls s3://sqlguard-backups/postgresql/ | grep "audit-backup" | tail -1 | awk '{print $4}')
   aws s3 cp "s3://sqlguard-backups/postgresql/${LATEST_BACKUP}" /tmp/
   ```

4. **Restore Base Backup**
   ```bash
   gunzip -c /tmp/${LATEST_BACKUP} | psql -d audit
   ```

5. **Apply WAL Logs (PITR)**
   ```bash
   # Configure recovery
   cat > /var/lib/postgresql/14/main/postgresql.auto.conf <<EOF
restore_command = 'aws s3 cp s3://sqlguard-backups/postgresql/wal/%f %p'
recovery_target_timeline = 'latest'
EOF

   # Create recovery.signal
   touch /var/lib/postgresql/14/main/recovery.signal

   # Start PostgreSQL
   systemctl start postgresql
   ```

6. **Verify Recovery**
   ```sql
   SELECT pg_is_in_recovery();  -- Should return 'f' (false) when complete

   SELECT * FROM checker_config LIMIT 10;
   SELECT * FROM whitelist_rules LIMIT 10;
   ```

7. **Update Application Configuration**
   ```bash
   # Update Kubernetes secret with new database endpoint
   kubectl patch secret audit-secrets -n sqlguard \
     -p '{"stringData":{"postgres.url":"jdbc:postgresql://new-postgres:5432/audit"}}'

   # Restart audit service pods
   kubectl rollout restart statefulset/sql-audit-service -n sqlguard
   ```

### 6.2 RTO/RPO Validation

| Scenario | Expected RTO | Expected RPO | Test Result |
|----------|-------------|--------------|-------------|
| Full DB loss with PITR | < 1 hour | < 5 minutes | PASS |
| Full DB loss without PITR | < 30 minutes | < 24 hours | PASS |
| Single table corruption | < 15 minutes | 0 (from replica) | PASS |

## 7. Backup Monitoring

### 7.1 Backup Success Metrics

```bash
# Check last successful backup
aws s3 ls s3://sqlguard-backups/postgresql/ | tail -1

# Check backup size trend
aws s3 ls s3://sqlguard-backups/postgresql/ --recursive | \
  awk '{print $3, $4}' | \
  grep "audit-backup"
```

### 7.2 Alerting

```yaml
# Alert if backup hasn't run in 25 hours
- alert: PostgreSQLBackupMissing
  expr: (time() - postgres_backup_last_success_timestamp_seconds) > 90000
  for: 1h
  labels:
    severity: critical
  annotations:
    summary: "PostgreSQL backup hasn't run in 25+ hours"
    description: "Last successful backup: {{ $value }} seconds ago"
```

## 8. Security Considerations

### 8.1 Encryption at Rest

```bash
# Encrypt backups before uploading
openssl enc -aes-256-cbc -salt -in backup.sql.gz -out backup.sql.gz.enc -k "${ENCRYPTION_KEY}"

# Decrypt when restoring
openssl enc -aes-256-cbc -d -in backup.sql.gz.enc -out backup.sql.gz -k "${ENCRYPTION_KEY}"
```

### 8.2 Access Control

```bash
# S3 bucket policy - restrict access
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::123456789012:role/BackupRole"
      },
      "Action": [
        "s3:PutObject",
        "s3:GetObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::sqlguard-backups/*",
        "arn:aws:s3:::sqlguard-backups"
      ]
    }
  ]
}
```

## 9. Backup Checklist

### Daily Tasks
- [ ] Verify backup cron job executed successfully
- [ ] Check backup file size is reasonable
- [ ] Verify S3 upload completed
- [ ] Check WAL archiving is working

### Weekly Tasks
- [ ] Review backup logs for errors
- [ ] Verify backup retention policy is enforced
- [ ] Check available storage space

### Monthly Tasks
- [ ] Perform full restore test to staging environment
- [ ] Review and update disaster recovery procedures
- [ ] Verify RTO/RPO objectives are met

## References

- [PostgreSQL Backup Documentation](https://www.postgresql.org/docs/current/backup.html)
- [AWS RDS Backup Best Practices](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.html)
- [pg_dump Documentation](https://www.postgresql.org/docs/current/app-pgdump.html)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-01
**Maintainer**: Database Team
