# Disaster Recovery Manual

## Overview

This manual provides step-by-step procedures for recovering the SQL Audit Service from various disaster scenarios.

## RTO/RPO Objectives

| Scenario | RTO (Recovery Time Objective) | RPO (Recovery Point Objective) |
|----------|-------------------------------|--------------------------------|
| Single pod failure | < 1 minute (automatic) | 0 (no data loss) |
| Database failure | < 30 minutes | < 5 minutes |
| Complete cluster loss | < 1 hour | < 5 minutes |
| Region failure | < 2 hours | < 15 minutes |

## Disaster Scenarios

### Scenario 1: Single Pod Failure

**Symptoms:**
- One or more audit service pods not running
- Reduced processing capacity
- Consumer lag increasing

**Recovery Procedure:**

1. **Verify Pod Status**
   ```bash
   kubectl get pods -n sqlguard
   # Look for pods not in Running state
   ```

2. **Check Pod Logs**
   ```bash
   kubectl logs sql-audit-service-0 -n sqlguard --tail=100
   ```

3. **Automatic Recovery**
   - Kubernetes will automatically restart failed pods
   - StatefulSet ensures pod identity is preserved
   - No manual intervention needed in most cases

4. **Manual Restart (if needed)**
   ```bash
   kubectl delete pod sql-audit-service-0 -n sqlguard
   # Pod will be recreated automatically
   ```

**Expected Recovery Time:** < 1 minute
**Data Loss:** None (Kafka consumer group will rebalance)

---

### Scenario 2: Kafka Consumer Lag Critical

**Symptoms:**
- Consumer lag > 50,000 messages
- Events not being processed
- Alert: HighKafkaConsumerLag

**Recovery Procedure:**

1. **Check Current Lag**
   ```bash
   kubectl exec -n sqlguard sql-audit-service-0 -- \
     curl -s localhost:8091/actuator/metrics/kafka.consumer.lag | jq
   ```

2. **Scale Up Consumers**
   ```bash
   # Increase replicas
   kubectl scale statefulset sql-audit-service --replicas=5 -n sqlguard
   ```

3. **Increase Kafka Partitions (if needed)**
   ```bash
   kafka-topics.sh --bootstrap-server kafka:9092 \
     --alter --topic sql-audit-events --partitions 24
   ```

4. **Monitor Recovery**
   ```bash
   # Watch consumer lag decrease
   watch -n 5 'kubectl exec -n sqlguard sql-audit-service-0 -- \
     curl -s localhost:8091/actuator/metrics/kafka.consumer.lag'
   ```

**Expected Recovery Time:** 10-30 minutes
**Data Loss:** None (events are in Kafka)

---

### Scenario 3: PostgreSQL Database Failure

**Symptoms:**
- Application cannot connect to PostgreSQL
- Errors: "connection refused" or "database unavailable"
- Checker configuration updates failing

**Recovery Procedure:**

1. **Check Database Status**
   ```bash
   # Check primary
   kubectl exec -n sqlguard postgres-0 -- pg_isready

   # Check standby
   kubectl exec -n sqlguard postgres-1 -- pg_isready
   ```

2. **Check Patroni Status** (if using Patroni)
   ```bash
   kubectl exec -n sqlguard postgres-0 -- patronictl list
   ```

3. **Automatic Failover** (Patroni will promote standby)
   - Wait 30-60 seconds for automatic failover
   - Verify new primary:
     ```bash
     kubectl exec -n sqlguard postgres-1 -- \
       psql -c "SELECT pg_is_in_recovery();"
     # Should return 'f' (false) if promoted to primary
     ```

4. **Manual Failover** (if automatic fails)
   ```bash
   kubectl exec -n sqlguard postgres-0 -- \
     patronictl failover audit-postgres --candidate postgres-1 --force
   ```

5. **Restore from Backup** (if both primary and standby failed)
   ```bash
   # Download latest backup
   LATEST_BACKUP=$(aws s3 ls s3://sqlguard-backups/postgresql/ | tail -1 | awk '{print $4}')
   aws s3 cp "s3://sqlguard-backups/postgresql/${LATEST_BACKUP}" /tmp/

   # Restore to new database instance
   gunzip -c /tmp/${LATEST_BACKUP} | psql -h new-postgres -d audit

   # Update Kubernetes secret
   kubectl patch secret audit-secrets -n sqlguard \
     -p '{"stringData":{"postgres.url":"jdbc:postgresql://new-postgres:5432/audit"}}'

   # Restart audit service
   kubectl rollout restart statefulset/sql-audit-service -n sqlguard
   ```

**Expected Recovery Time:** < 30 minutes
**Data Loss:** < 5 minutes (from WAL replay)

---

### Scenario 4: ClickHouse Cluster Failure

**Symptoms:**
- Cannot write audit logs
- Errors: "ClickHouse connection timeout"
- Query API returns errors

**Recovery Procedure:**

1. **Check ClickHouse Status**
   ```bash
   kubectl exec -n sqlguard clickhouse-0 -- clickhouse-client --query "SELECT 1"
   ```

2. **Check Replication Status**
   ```sql
   SELECT
     database,
     table,
     is_leader,
     is_readonly,
     absolute_delay,
     queue_size
   FROM system.replicas
   WHERE database = 'audit';
   ```

3. **Restart ClickHouse Pod**
   ```bash
   kubectl delete pod clickhouse-0 -n sqlguard
   ```

4. **Restore from Backup** (if data corruption)
   ```bash
   # Download backup
   clickhouse-backup download audit-latest

   # Restore
   clickhouse-backup restore audit-latest

   # Verify
   kubectl exec -n sqlguard clickhouse-0 -- clickhouse-client \
     --query "SELECT count(*) FROM audit.audit_logs"
   ```

**Expected Recovery Time:** < 30 minutes
**Data Loss:** Minimal (events buffered in Kafka)

---

### Scenario 5: Complete Kubernetes Cluster Loss

**Symptoms:**
- Entire cluster unreachable
- All pods down
- No services responding

**Recovery Procedure:**

1. **Provision New Cluster**
   ```bash
   # Use infrastructure-as-code (Terraform/CloudFormation)
   terraform apply -var="cluster_name=sqlguard-recovery"
   ```

2. **Restore Kafka**
   ```bash
   # Deploy Kafka cluster
   kubectl apply -f kafka-cluster.yaml -n sqlguard

   # Restore Kafka data (if using persistent volumes)
   # Or rely on replication from other regions
   ```

3. **Restore PostgreSQL**
   ```bash
   # Deploy PostgreSQL
   kubectl apply -f postgres-statefulset.yaml -n sqlguard

   # Restore from backup
   # (See Scenario 3, step 5)
   ```

4. **Restore ClickHouse**
   ```bash
   # Deploy ClickHouse cluster
   kubectl apply -f clickhouse-installation.yaml -n sqlguard

   # Restore from backup
   clickhouse-backup download audit-latest
   clickhouse-backup restore audit-latest
   ```

5. **Deploy Audit Service**
   ```bash
   # Create secrets
   kubectl apply -f docs/deployment/k8s/secret.yaml

   # Create ConfigMap
   kubectl apply -f docs/deployment/k8s/configmap.yaml

   # Deploy StatefulSet
   kubectl apply -f docs/deployment/k8s/statefulset.yaml

   # Deploy Service
   kubectl apply -f docs/deployment/k8s/service.yaml

   # Deploy Ingress
   kubectl apply -f docs/deployment/k8s/ingress.yaml
   ```

6. **Verify System Health**
   ```bash
   # Check pods
   kubectl get pods -n sqlguard

   # Check consumer lag
   kubectl exec -n sqlguard sql-audit-service-0 -- \
     curl -s localhost:8091/actuator/metrics/kafka.consumer.lag

   # Test API
   curl -H "Authorization: Bearer $JWT_TOKEN" \
     https://audit.example.com/api/audit/reports?startTime=2025-01-01T00:00:00
   ```

**Expected Recovery Time:** < 2 hours
**Data Loss:** < 5 minutes (from backups + Kafka retention)

---

### Scenario 6: Region Failure (Multi-Region Setup)

**Symptoms:**
- Primary region completely unavailable
- All services in primary region unreachable

**Recovery Procedure:**

1. **Activate Secondary Region**
   ```bash
   # Update DNS to point to secondary region
   aws route53 change-resource-record-sets \
     --hosted-zone-id Z1234567890ABC \
     --change-batch file://failover-dns.json
   ```

2. **Promote Secondary Database to Primary**
   ```bash
   # Promote PostgreSQL standby in secondary region
   kubectl exec -n sqlguard postgres-secondary-0 -- \
     pg_ctl promote -D /var/lib/postgresql/data

   # Promote ClickHouse (if using cross-region replication)
   # Already available in secondary region
   ```

3. **Update Application Configuration**
   ```bash
   # Update ConfigMap to use secondary region databases
   kubectl patch configmap audit-config -n sqlguard \
     --patch '{"data":{"postgres.url":"jdbc:postgresql://postgres-secondary:5432/audit"}}'

   # Restart pods to pick up new configuration
   kubectl rollout restart statefulset/sql-audit-service -n sqlguard
   ```

4. **Monitor Traffic**
   ```bash
   # Verify traffic is flowing to secondary region
   kubectl logs -f sql-audit-service-0 -n sqlguard

   # Check metrics
   watch -n 5 'kubectl exec -n sqlguard sql-audit-service-0 -- \
     curl -s localhost:8091/actuator/prometheus | grep audit_events_processed'
   ```

**Expected Recovery Time:** < 30 minutes (with automated failover)
**Data Loss:** < 15 minutes (from asynchronous replication lag)

---

## Recovery Verification Checklist

After any disaster recovery procedure, verify the following:

### Application Health
- [ ] All pods running (`kubectl get pods -n sqlguard`)
- [ ] Liveness probes passing
- [ ] Readiness probes passing
- [ ] No restart loops

### Data Pipeline
- [ ] Kafka consumer lag < 1000
- [ ] Events being processed (check metrics)
- [ ] No processing errors
- [ ] ClickHouse writes succeeding

### Databases
- [ ] PostgreSQL accessible and not in recovery mode
- [ ] ClickHouse accessible and accepting writes
- [ ] Replication working (if applicable)
- [ ] No data corruption

### API Functionality
- [ ] Health endpoints responding
- [ ] Authentication working
- [ ] Query API returning data
- [ ] Response times normal (< 500ms p99)

### Monitoring
- [ ] Prometheus scraping metrics
- [ ] Grafana dashboards showing data
- [ ] Alerts configured and working
- [ ] No critical alerts firing

---

## Escalation Procedures

### Level 1: On-Call Engineer
- **Scenarios**: Pod failures, consumer lag, slow queries
- **Response Time**: 15 minutes
- **Actions**: Restart pods, scale up, basic troubleshooting

### Level 2: Senior Engineer
- **Scenarios**: Database failures, persistent errors, performance degradation
- **Response Time**: 30 minutes
- **Actions**: Failover, restore from backup, deep troubleshooting

### Level 3: Architect/Manager
- **Scenarios**: Complete cluster loss, region failure, data corruption
- **Response Time**: 1 hour
- **Actions**: Disaster recovery, multi-team coordination, vendor escalation

### External Escalation
- **Cloud Provider Support**: Infrastructure issues
- **Database Vendor Support**: Complex database problems
- **Security Team**: Suspected security incidents

---

## Communication Plan

### Internal Communication

**Incident Declared:**
- Post in #incidents Slack channel
- Create incident in PagerDuty
- Update status page

**Regular Updates:**
- Every 30 minutes during active incident
- Include: Current status, actions taken, ETA

**Incident Resolved:**
- Post resolution in #incidents
- Update status page
- Schedule post-mortem

### External Communication

**For Customer-Facing Incidents:**
- Update status page within 15 minutes
- Send email notification if downtime > 1 hour
- Provide ETA and workarounds

---

## Post-Incident Review

Within 48 hours of incident resolution, conduct a post-mortem:

1. **Timeline**: Detailed timeline of events
2. **Root Cause**: What caused the incident
3. **Impact**: Systems affected, duration, customer impact
4. **Response**: What went well, what didn't
5. **Action Items**: Preventive measures, documentation updates

**Template**: Use internal post-mortem template

---

## Contact Information

| Role | Contact | Phone | Email |
|------|---------|-------|-------|
| On-Call Engineer | PagerDuty | - | oncall@example.com |
| Database Team Lead | John Doe | +1-555-0101 | john@example.com |
| Platform Manager | Jane Smith | +1-555-0102 | jane@example.com |
| AWS Support | - | - | Via AWS Console |
| Security Team | - | +1-555-0103 | security@example.com |

---

## References

- [PostgreSQL Backup Guide](./postgres-backup.md)
- [ClickHouse Backup Guide](./clickhouse-backup.md)
- [Kubernetes Troubleshooting](../operations/troubleshooting-guide.md)
- [High Availability Configuration](../ha/)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-01
**Maintainer**: Platform Team
