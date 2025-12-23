# SQL Audit Service - Production Deployment Guide

## Overview

This guide provides comprehensive instructions for deploying the SQL Audit Service to production environments, covering infrastructure requirements, scaling strategies, high availability configuration, security hardening, monitoring, and operational procedures.

## Table of Contents

1. [Infrastructure Requirements](#infrastructure-requirements)
2. [Resource Sizing](#resource-sizing)
3. [Kubernetes Deployment](#kubernetes-deployment)
4. [High Availability](#high-availability)
5. [Security Configuration](#security-configuration)
6. [Monitoring and Alerting](#monitoring-and-alerting)
7. [Backup and Recovery](#backup-and-recovery)
8. [Operations Manual](#operations-manual)

---

## 1. Infrastructure Requirements

### 1.1 Resource Sizing Guide

The SQL Audit Service resource requirements scale based on the daily SQL query volume. Choose the appropriate tier based on your expected load:

| Scale | SQL Queries/Day | CPU | Memory | Storage | Network |
|-------|----------------|-----|--------|---------|---------|
| **Small** | < 10,000 | 2 cores | 4GB | 100GB | 1Gbps |
| **Medium** | < 100,000 | 4 cores | 8GB | 500GB | 1Gbps |
| **Large** | < 1,000,000 | 8 cores | 16GB | 2TB | 10Gbps |
| **X-Large** | > 1,000,000 | 16+ cores | 32GB+ | 5TB+ | 10Gbps |

### 1.2 Horizontal Scaling Strategy

**Small/Medium Scale:**
- Single instance + read replica
- Suitable for development and staging environments
- Vertical scaling preferred

**Large Scale:**
- 2-3 instances with Kafka consumer group
- Horizontal scaling for load distribution
- Regional deployment

**X-Large Scale:**
- 5+ instances with Kafka partition expansion
- Multi-region deployment
- Dedicated infrastructure per service component

### 1.3 Dependent Service Sizing

| Service | Small | Medium | Large | X-Large |
|---------|-------|--------|-------|---------|
| **Kafka** | 1 broker, 3 partitions | 3 brokers, 6 partitions | 5 brokers, 12 partitions | 10+ brokers, 24+ partitions |
| **PostgreSQL** | 2 cores / 4GB | 4 cores / 8GB | 8 cores / 16GB | 16 cores / 32GB |
| **ClickHouse** | 2 cores / 8GB | 4 cores / 16GB | 8 cores / 32GB | 16 cores / 64GB |

**Storage Requirements:**

- **PostgreSQL**: Stores checker configuration, whitelist rules, metadata
  - Small: 10GB
  - Medium: 50GB
  - Large: 200GB
  - X-Large: 500GB+

- **ClickHouse**: Stores audit event logs (time-series data)
  - Small: 100GB (30 days retention)
  - Medium: 500GB (90 days retention)
  - Large: 2TB (180 days retention)
  - X-Large: 5TB+ (365 days retention)

- **Kafka**: Message buffer (transient storage)
  - Retention: 7 days
  - Replication factor: 3
  - Storage: 2x daily volume

### 1.4 Network Requirements

**Bandwidth:**
- Internal: 1Gbps minimum (10Gbps for large deployments)
- External: Based on API query load

**Latency:**
- Kafka to Audit Service: < 5ms
- Audit Service to PostgreSQL: < 10ms
- Audit Service to ClickHouse: < 20ms

**Ports:**
- 8090: HTTP API
- 8091: Actuator/Health endpoints
- 9092: Kafka brokers
- 5432: PostgreSQL
- 8123: ClickHouse HTTP
- 9000: ClickHouse Native

---

## 2. Architecture Overview

### 2.1 Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Services                     │
│  (sql-guard-spring-boot-starter + Runtime Interceptors)    │
└──────────────────────┬──────────────────────────────────────┘
                       │ Audit Events
                       ▼
              ┌────────────────┐
              │  Kafka Cluster │
              │  (3+ brokers)  │
              └────────┬───────┘
                       │ Consumer Group
                       ▼
         ┌─────────────────────────────┐
         │  SQL Audit Service Cluster  │
         │  ┌─────┐  ┌─────┐  ┌─────┐ │
         │  │ Pod │  │ Pod │  │ Pod │ │
         │  │  0  │  │  1  │  │  2  │ │
         │  └─────┘  └─────┘  └─────┘ │
         └──────┬──────────────┬───────┘
                │              │
        ┌───────▼─────┐   ┌────▼──────────┐
        │ PostgreSQL  │   │  ClickHouse   │
        │  (Primary + │   │  (Replicated  │
        │   Standby)  │   │   Cluster)    │
        └─────────────┘   └───────────────┘
```

### 2.2 Data Flow

1. **Event Generation**: Application services generate audit events via interceptors
2. **Event Streaming**: Events published to Kafka topic (`sql-audit-events`)
3. **Event Consumption**: Audit service instances consume from Kafka (consumer group)
4. **Event Processing**: Run checker pipeline (SlowQuery, ActualImpact, ErrorRate, etc.)
5. **Data Storage**:
   - Configuration/Metadata → PostgreSQL
   - Audit logs → ClickHouse (time-series)
6. **Query/API**: REST API serves data from PostgreSQL + ClickHouse

---

## 3. Deployment Models

### 3.1 Single-Region Deployment (Recommended for Most)

```yaml
Region: us-west-2
AZ Distribution:
  - AZ-1: Kafka-0, Postgres-Primary, ClickHouse-1, Audit-Pod-0
  - AZ-2: Kafka-1, Postgres-Standby, ClickHouse-2, Audit-Pod-1
  - AZ-3: Kafka-2, ClickHouse-3, Audit-Pod-2

Benefits:
  - Low latency
  - Simple management
  - Cost-effective

Limitations:
  - Regional failure impacts all services
```

### 3.2 Multi-Region Deployment (For Global Scale)

```yaml
Primary Region: us-west-2
Secondary Region: eu-west-1

Data Replication:
  - Kafka: MirrorMaker 2.0 (async replication)
  - PostgreSQL: Streaming replication (async)
  - ClickHouse: Cross-region replication

Benefits:
  - Disaster recovery
  - Regional data compliance (GDPR)
  - Lower latency for global users

Limitations:
  - Higher complexity
  - Higher cost
  - Eventual consistency
```

---

## 4. Performance Characteristics

### 4.1 Expected Throughput

| Scale | Events/sec | API Queries/sec | Latency (p99) |
|-------|-----------|----------------|---------------|
| Small | 10-100 | 5-10 | < 200ms |
| Medium | 100-1,000 | 10-50 | < 300ms |
| Large | 1,000-10,000 | 50-200 | < 500ms |
| X-Large | 10,000+ | 200+ | < 1s |

### 4.2 Bottleneck Analysis

**Common Bottlenecks:**

1. **Kafka Consumer Lag**
   - Symptom: `kafka_consumer_lag > 10000`
   - Cause: Slow checkers, insufficient consumer instances
   - Solution: Scale horizontally, optimize checker logic

2. **ClickHouse Write Latency**
   - Symptom: `clickhouse.write.latency > 500ms`
   - Cause: Large batch size, network latency, table schema
   - Solution: Tune batch size, optimize MergeTree settings

3. **Database Connection Pool Exhaustion**
   - Symptom: `hikaricp.connections.active == max`
   - Cause: Slow queries, insufficient pool size
   - Solution: Increase pool size, optimize queries, add indexes

4. **Memory Pressure**
   - Symptom: `jvm.memory.used > 80%`
   - Cause: Large batches in memory, caching overhead
   - Solution: Increase heap size, tune GC, reduce batch size

---

## 5. Cost Estimation

### 5.1 AWS Cost Breakdown (Monthly)

**Small Scale:**
- Compute (EKS): 2 x t3.large = $120
- Kafka (MSK): 3 x kafka.t3.small = $80
- PostgreSQL (RDS): db.t3.medium = $60
- ClickHouse (EC2): t3.xlarge = $150
- Storage (EBS/S3): 200GB = $20
- Network: $30
- **Total: ~$460/month**

**Medium Scale:**
- Compute (EKS): 3 x t3.xlarge = $360
- Kafka (MSK): 3 x kafka.m5.large = $450
- PostgreSQL (RDS): db.r5.large = $240
- ClickHouse (EC2): r5.2xlarge = $600
- Storage: 1TB = $100
- Network: $100
- **Total: ~$1,850/month**

**Large Scale:**
- Compute (EKS): 5 x m5.2xlarge = $1,200
- Kafka (MSK): 5 x kafka.m5.xlarge = $1,500
- PostgreSQL (RDS): db.r5.2xlarge = $960
- ClickHouse Cluster: 3 x r5.4xlarge = $3,600
- Storage: 5TB = $500
- Network: $300
- **Total: ~$8,060/month**

### 5.2 Cost Optimization Tips

1. **Use Reserved Instances**: Save 30-40% for stable workloads
2. **Auto-scaling**: Scale down during off-peak hours
3. **Data Lifecycle**: Archive old audit logs to S3 Glacier
4. **Compression**: Enable ClickHouse compression (saves 70-80% storage)
5. **Spot Instances**: Use for non-critical consumer pods (save 70%)

---

## 6. Pre-Deployment Checklist

### 6.1 Infrastructure Preparation

- [ ] Kubernetes cluster provisioned (EKS, GKE, or AKS)
- [ ] Kubectl configured with cluster access
- [ ] Helm 3+ installed
- [ ] Namespace `sqlguard` created
- [ ] Service accounts configured
- [ ] RBAC policies applied

### 6.2 Dependent Services

- [ ] Kafka cluster running (3+ brokers)
- [ ] Topic `sql-audit-events` created (12+ partitions)
- [ ] PostgreSQL database `audit` created
- [ ] PostgreSQL user `audit` with permissions
- [ ] ClickHouse cluster running
- [ ] ClickHouse database `audit` created
- [ ] ClickHouse tables created (see schema)

### 6.3 Security

- [ ] TLS certificates provisioned (cert-manager)
- [ ] Secrets created (PostgreSQL, ClickHouse, JWT)
- [ ] Network policies configured
- [ ] Firewall rules applied
- [ ] VPN/bastion access configured

### 6.4 Monitoring

- [ ] Prometheus deployed
- [ ] Grafana deployed
- [ ] Alert manager configured
- [ ] Log aggregation (ELK/Loki) configured
- [ ] Dashboards imported

---

## 7. Deployment Steps

See the Kubernetes deployment guide in `docs/deployment/k8s/` for detailed YAML manifests and deployment instructions.

**Quick Start:**

```bash
# Step 1: Create namespace
kubectl create namespace sqlguard

# Step 2: Create secrets
kubectl apply -f docs/deployment/k8s/secret.yaml

# Step 3: Create ConfigMap
kubectl apply -f docs/deployment/k8s/configmap.yaml

# Step 4: Deploy StatefulSet
kubectl apply -f docs/deployment/k8s/statefulset.yaml

# Step 5: Create Service
kubectl apply -f docs/deployment/k8s/service.yaml

# Step 6: Create Ingress (optional)
kubectl apply -f docs/deployment/k8s/ingress.yaml

# Step 7: Verify deployment
kubectl get pods -n sqlguard
kubectl logs -f sql-audit-service-0 -n sqlguard
```

---

## 8. Post-Deployment Validation

### 8.1 Health Checks

```bash
# Check pod status
kubectl get pods -n sqlguard

# Expected output:
# NAME                    READY   STATUS    RESTARTS   AGE
# sql-audit-service-0     1/1     Running   0          5m
# sql-audit-service-1     1/1     Running   0          4m
# sql-audit-service-2     1/1     Running   0          3m

# Check liveness
curl http://audit.example.com/actuator/health/liveness

# Check readiness
curl http://audit.example.com/actuator/health/readiness
```

### 8.2 Functional Validation

```bash
# Test API endpoints
curl -H "Authorization: Bearer $JWT_TOKEN" \
  http://audit.example.com/api/audit/reports?startTime=2025-01-01T00:00:00

# Check Kafka consumer lag
kubectl exec -n sqlguard sql-audit-service-0 -- \
  curl localhost:8091/actuator/metrics/kafka.consumer.lag

# Query ClickHouse
curl http://clickhouse:8123/?query="SELECT COUNT(*) FROM audit.audit_logs"
```

### 8.3 Performance Validation

```bash
# Generate load
ab -n 1000 -c 10 -H "Authorization: Bearer $JWT_TOKEN" \
  http://audit.example.com/api/audit/statistics/overview

# Monitor metrics
kubectl port-forward -n sqlguard svc/sql-audit-service 8091:8091
curl http://localhost:8091/actuator/prometheus
```

---

## 9. Upgrade Procedures

### 9.1 Rolling Update (Zero Downtime)

```bash
# Update image version in statefulset.yaml
# Change: image: sql-audit-service:2.0.0 → sql-audit-service:2.1.0

# Apply update
kubectl apply -f docs/deployment/k8s/statefulset.yaml

# Monitor rollout
kubectl rollout status statefulset/sql-audit-service -n sqlguard

# Expected: Rolling update, one pod at a time
# sql-audit-service-0: Terminating → Running (new version)
# sql-audit-service-1: Terminating → Running (new version)
# sql-audit-service-2: Terminating → Running (new version)
```

### 9.2 Rollback Procedure

```bash
# If new version has issues, rollback
kubectl rollout undo statefulset/sql-audit-service -n sqlguard

# Verify rollback
kubectl rollout status statefulset/sql-audit-service -n sqlguard
```

### 9.3 Database Schema Migration

```bash
# Use Flyway or Liquibase for PostgreSQL schema updates
# Migrations run automatically on service startup

# Manual verification
kubectl logs sql-audit-service-0 -n sqlguard | grep "Flyway"

# Expected output:
# Flyway: Migrating schema to version 2.1.0
# Flyway: Successfully applied 3 migrations
```

---

## 10. Troubleshooting Quick Reference

| Symptom | Possible Cause | Quick Fix |
|---------|---------------|-----------|
| Pods not starting | Image pull error | Check image tag, registry credentials |
| Consumer lag increasing | Slow processing | Scale up replicas |
| High memory usage | Large batches | Reduce batch size in ConfigMap |
| Database connection errors | Wrong credentials | Verify Secret values |
| API 401 Unauthorized | Invalid JWT | Check token, refresh if expired |

For detailed troubleshooting, see: `docs/operations/troubleshooting-guide.md`

---

## 11. References

- [Kubernetes Deployment Templates](./k8s/)
- [High Availability Configuration](./ha/)
- [Security Configuration](./security/)
- [Monitoring Setup](./monitoring/)
- [Backup and Recovery](./backup/)
- [Operations Guide](../operations/troubleshooting-guide.md)

---

**Document Version**: 1.0
**Last Updated**: 2025-01-01
**Maintainer**: DevOps Team
