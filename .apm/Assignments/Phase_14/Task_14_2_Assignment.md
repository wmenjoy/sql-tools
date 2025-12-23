---
task_ref: "Task 14.2 - Production Deployment Guide"
agent_assignment: "Agent_Documentation"
memory_log_path: ".apm/Memory/Phase_14_Audit_Examples_Documentation/Task_14_2_Deployment_Guide_Implementation.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
phase: 14
task_id: 14.2
estimated_duration: 4 days
dependencies: [Phase_10_Audit_Service]
parallel_with: [Task_14.1, Task_14.3, Task_14.4]
priority: "High"
---

# Task 14.2 Assignment: Production Deployment Guide

## 任务目标

创建 sql-audit-service 的生产部署指南，涵盖基础设施需求、扩展策略、高可用配置、安全加固、监控告警、备份恢复、运维手册，提供 Kubernetes 部署模板和运维手册。

---

## 背景说明

### 为什么需要生产部署指南？

**问题**: 用户将 sql-audit-service 部署到生产环境时面临以下挑战：
1. **资源规划**: 需要多少 CPU/内存/存储？
2. **高可用**: 如何实现零停机和自动故障转移？
3. **安全**: 如何保护审计数据和 API 访问？
4. **监控**: 如何知道系统健康状态？
5. **备份**: 审计数据如何备份和恢复？
6. **运维**: 故障排查和常见问题处理

**部署指南价值**：
- ✅ 资源 Sizing 指南（小/中/大/超大规模）
- ✅ Kubernetes 部署模板（生产级配置）
- ✅ 高可用架构（Kafka consumer group + DB replication）
- ✅ 安全加固（认证、授权、加密）
- ✅ 监控告警（Prometheus + Grafana）
- ✅ 备份恢复（PostgreSQL + ClickHouse）
- ✅ 运维手册（故障排查、应急处理）

---

## 核心组件设计

### 1. 基础设施需求

#### 1.1 资源 Sizing 指南

**文件**: `docs/deployment/production-deployment.md` - Section 1

| 规模 | SQL 数量/天 | CPU | 内存 | 存储 | 网络 |
|------|-------------|-----|------|------|------|
| **小型** | <10k | 2 核 | 4GB | 100GB | 1Gbps |
| **中型** | <100k | 4 核 | 8GB | 500GB | 1Gbps |
| **大型** | <1M | 8 核 | 16GB | 2TB | 10Gbps |
| **超大型** | >1M | 16 核+ | 32GB+ | 5TB+ | 10Gbps |

**水平扩展策略**:
- 小/中型: 单实例 + 副本（只读）
- 大型: 2-3 实例 + Kafka consumer group
- 超大型: 5+ 实例 + Kafka 分区扩展

#### 1.2 依赖服务 Sizing

| 服务 | 小型 | 中型 | 大型 | 超大型 |
|------|------|------|------|--------|
| **Kafka** | 1 broker | 3 brokers | 5 brokers | 10+ brokers |
| **PostgreSQL** | 2 核/4GB | 4 核/8GB | 8 核/16GB | 16 核/32GB |
| **ClickHouse** | 2 核/8GB | 4 核/16GB | 8 核/32GB | 16 核/64GB |

---

### 2. Kubernetes 部署模板

#### 2.1 StatefulSet

**文件**: `docs/deployment/k8s/statefulset.yaml`

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: sql-audit-service
  namespace: sqlguard
spec:
  serviceName: sql-audit-service
  replicas: 3  # 高可用: 3个实例
  selector:
    matchLabels:
      app: sql-audit-service
  template:
    metadata:
      labels:
        app: sql-audit-service
    spec:
      containers:
      - name: audit-service
        image: sql-audit-service:2.0.0
        ports:
        - containerPort: 8090
          name: http
        - containerPort: 8091
          name: actuator
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
          valueFrom:
            configMapKeyRef:
              name: audit-config
              key: kafka.bootstrap.servers
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: audit-secrets
              key: postgres.url
        - name: SPRING_DATASOURCE_USERNAME
          valueFrom:
            secretKeyRef:
              name: audit-secrets
              key: postgres.username
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: audit-secrets
              key: postgres.password
        - name: CLICKHOUSE_URL
          valueFrom:
            secretKeyRef:
              name: audit-secrets
              key: clickhouse.url
        resources:
          requests:
            cpu: "2"
            memory: "4Gi"
          limits:
            cpu: "4"
            memory: "8Gi"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8091
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8091
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
        volumeMounts:
        - name: cache
          mountPath: /app/cache
  volumeClaimTemplates:
  - metadata:
      name: cache
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 10Gi
```

#### 2.2 ConfigMap

**文件**: `docs/deployment/k8s/configmap.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: audit-config
  namespace: sqlguard
data:
  kafka.bootstrap.servers: "kafka-0.kafka:9092,kafka-1.kafka:9092,kafka-2.kafka:9092"
  kafka.topic: "sql-audit-events"
  kafka.consumer.group: "audit-service-group"
  kafka.consumer.concurrency: "3"

  checker.slowQuery.threshold: "1000"
  checker.actualImpact.threshold: "1000"
  checker.errorRate.window: "60"

  logging.level.root: "INFO"
  logging.level.com.footstone: "DEBUG"
```

#### 2.3 Secret

**文件**: `docs/deployment/k8s/secret.yaml`

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: audit-secrets
  namespace: sqlguard
type: Opaque
stringData:
  postgres.url: "jdbc:postgresql://postgres:5432/audit"
  postgres.username: "audit"
  postgres.password: "CHANGE_ME"

  clickhouse.url: "jdbc:clickhouse://clickhouse:8123/audit"
  clickhouse.username: "audit"
  clickhouse.password: "CHANGE_ME"

  jwt.secret: "CHANGE_ME_JWT_SECRET_KEY"
```

#### 2.4 Service

**文件**: `docs/deployment/k8s/service.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: sql-audit-service
  namespace: sqlguard
spec:
  selector:
    app: sql-audit-service
  ports:
  - name: http
    port: 8090
    targetPort: 8090
  - name: actuator
    port: 8091
    targetPort: 8091
  type: ClusterIP
```

#### 2.5 Ingress

**文件**: `docs/deployment/k8s/ingress.yaml`

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: sql-audit-service
  namespace: sqlguard
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - audit.example.com
    secretName: audit-tls
  rules:
  - host: audit.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: sql-audit-service
            port:
              number: 8090
```

---

### 3. 高可用配置

#### 3.1 Kafka Consumer Group

**关键配置**:

```yaml
spring:
  kafka:
    consumer:
      group-id: audit-service-group  # 所有实例使用相同 group
      enable-auto-commit: false  # 手动提交
      max-poll-records: 500
      properties:
        max.poll.interval.ms: 300000
        session.timeout.ms: 30000
```

**HA 保证**:
- 3 个实例共享 Kafka partitions
- 实例失败 → Kafka 自动 rebalance → 其他实例接管
- 无数据丢失（manual commit）

#### 3.2 PostgreSQL 主从复制

**架构**: Primary + Standby

**文件**: `docs/deployment/ha/postgres-replication.md`

```bash
# Primary 配置
postgresql.conf:
  wal_level = replica
  max_wal_senders = 5
  max_replication_slots = 5

# Standby 配置
recovery.conf:
  primary_conninfo = 'host=postgres-primary port=5432 user=replication'
  primary_slot_name = 'standby_slot'
```

**故障转移**:
- Primary 失败 → Standby 提升为 Primary
- 使用 Patroni 或 Stolon 自动故障转移

#### 3.3 ClickHouse 复制

**架构**: ReplicatedMergeTree

**表创建**:

```sql
CREATE TABLE audit.audit_logs ON CLUSTER cluster_3shards_1replica
(
    timestamp DateTime,
    sql String,
    severity String,
    risk_score Float32,
    -- ...
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/audit_logs', '{replica}')
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, severity);
```

**HA 保证**:
- 数据自动复制到多个副本
- 任一节点失败不影响查询

---

### 4. 安全配置

#### 4.1 JWT 认证

**文件**: `docs/deployment/security/jwt-authentication.md`

**Spring Security 配置**:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/audit/**").authenticated()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt());
        return http.build();
    }
}
```

**Token 生成**:

```bash
curl -X POST https://auth.example.com/oauth/token \
  -d "grant_type=client_credentials" \
  -d "client_id=audit-service" \
  -d "client_secret=SECRET"
```

#### 4.2 RBAC

**角色定义**:

```yaml
roles:
  - name: audit-admin
    permissions:
      - read:audit-logs
      - write:checker-config
      - delete:audit-data

  - name: audit-viewer
    permissions:
      - read:audit-logs
```

#### 4.3 TLS 加密

**传输加密**:
- Kafka: `security.protocol=SSL`
- PostgreSQL: `sslmode=require`
- ClickHouse: `ssl=true`
- REST API: HTTPS (Ingress TLS)

**证书管理**:
- 使用 cert-manager 自动续期
- Let's Encrypt 免费证书

---

### 5. 监控告警

#### 5.1 Prometheus 指标

**文件**: `docs/deployment/monitoring/prometheus-metrics.md`

**关键指标**:

```yaml
# Kafka Consumer Lag
kafka_consumer_lag{group="audit-service-group",topic="sql-audit-events"}

# Processing Latency
audit_checker_duration_seconds_bucket{checker="SlowQueryChecker"}

# Error Rate
audit_processing_errors_total

# Throughput
audit_events_processed_total
```

**Prometheus 抓取配置**:

```yaml
scrape_configs:
  - job_name: 'sql-audit-service'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - sqlguard
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: sql-audit-service
```

#### 5.2 告警规则

**文件**: `docs/deployment/monitoring/alert-rules.yaml`

```yaml
groups:
  - name: audit-service-alerts
    rules:
      # Consumer Lag 告警
      - alert: HighConsumerLag
        expr: kafka_consumer_lag > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Kafka consumer lag is high"
          description: "Consumer lag is {{ $value }} messages"

      # Processing Latency 告警
      - alert: HighProcessingLatency
        expr: histogram_quantile(0.99, audit_checker_duration_seconds_bucket) > 0.5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Processing latency is high"
          description: "p99 latency is {{ $value }}s"

      # Error Rate 告警
      - alert: HighErrorRate
        expr: rate(audit_processing_errors_total[5m]) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Error rate is high"
          description: "Error rate is {{ $value }} per second"
```

#### 5.3 Grafana 仪表板

**文件**: `docs/deployment/monitoring/grafana-dashboard.json`

**包含 Panel**:
1. Kafka Consumer Lag
2. Processing Latency (p95/p99)
3. Throughput (events/sec)
4. Error Rate
5. JVM Memory Usage
6. Database Connection Pool

---

### 6. 备份恢复

#### 6.1 PostgreSQL 备份

**文件**: `docs/deployment/backup/postgres-backup.md`

**每日全量备份**:

```bash
#!/bin/bash
# postgres-backup.sh

DATE=$(date +%Y%m%d)
pg_dump -h postgres -U audit audit > /backup/audit-$DATE.sql
gzip /backup/audit-$DATE.sql

# 保留 30 天
find /backup -name "audit-*.sql.gz" -mtime +30 -delete
```

**WAL 归档（PITR）**:

```ini
# postgresql.conf
archive_mode = on
archive_command = 'cp %p /archive/%f'
```

**恢复步骤**:

```bash
# 1. 恢复基础备份
gunzip -c audit-20250101.sql.gz | psql -h postgres -U audit audit

# 2. 应用 WAL 日志（PITR）
recovery.conf:
  restore_command = 'cp /archive/%f %p'
  recovery_target_time = '2025-01-01 12:00:00'
```

#### 6.2 ClickHouse 备份

**文件**: `docs/deployment/backup/clickhouse-backup.md`

**使用 clickhouse-backup 工具**:

```bash
# 安装
wget https://github.com/AlexAkulov/clickhouse-backup/releases/download/v2.0.0/clickhouse-backup-linux-amd64.tar.gz
tar -xzf clickhouse-backup-linux-amd64.tar.gz

# 配置
clickhouse-backup.yml:
  clickhouse:
    host: localhost
    port: 9000
  general:
    remote_storage: s3
  s3:
    bucket: clickhouse-backups
    region: us-west-2

# 创建备份
clickhouse-backup create audit-20250101

# 上传到 S3
clickhouse-backup upload audit-20250101

# 恢复
clickhouse-backup download audit-20250101
clickhouse-backup restore audit-20250101
```

#### 6.3 灾难恢复手册

**文件**: `docs/deployment/backup/disaster-recovery.md`

**RTO/RPO 目标**:
- RTO (Recovery Time Objective): <1 小时
- RPO (Recovery Point Objective): <5 分钟

**恢复步骤**:
1. 评估损坏范围
2. 决定恢复点
3. 恢复 PostgreSQL（元数据）
4. 恢复 ClickHouse（审计日志）
5. 重启审计服务
6. 验证数据完整性

---

### 7. 运维手册

**文件**: `docs/operations/troubleshooting-guide.md`

#### 7.1 常见问题

##### 问题 1: Kafka 消费积压

**症状**:
```
kafka_consumer_lag > 10000
```

**排查**:
1. 检查 checker 性能：`grep "checker.duration" logs/audit-service.log`
2. 检查数据库连接池：`/actuator/metrics/hikaricp.connections`
3. 检查 ClickHouse 写入性能

**解决方案**:
- 增加消费者实例（scale up replicas）
- 增加 Kafka partitions
- 优化慢 checker 逻辑

##### 问题 2: ClickHouse 写入超时

**症状**:
```
2025-01-01 12:00:00 ERROR ClickHouseWriter - Write timeout
```

**排查**:
1. 检查网络延迟：`ping clickhouse`
2. 检查 ClickHouse 负载：`SELECT * FROM system.processes`
3. 检查 batch size 配置

**解决方案**:
- 增加 `clickhouse.batch.size`
- 增加 `clickhouse.flush.interval`
- 检查 ClickHouse 节点资源

##### 问题 3: 审计服务 OOM

**症状**:
```
java.lang.OutOfMemoryError: Java heap space
```

**排查**:
1. 生成 heap dump：`jmap -dump:live,format=b,file=heap.bin PID`
2. 分析 heap dump：使用 MAT (Memory Analyzer Tool)
3. 检查内存泄漏

**解决方案**:
- 增加堆内存：`-Xmx8g`
- 优化 checker 内存使用
- 检查缓存配置

##### 问题 4: 配置不生效

**症状**:
```
配置更新后，checker 阈值未变化
```

**排查**:
1. 检查 ConfigMap：`kubectl get configmap audit-config -o yaml`
2. 检查 Pod 环境变量：`kubectl exec POD -- env | grep CHECKER`
3. 使用配置诊断端点：`/actuator/configprops`

**解决方案**:
- 重启 Pod 使配置生效：`kubectl rollout restart statefulset/sql-audit-service`
- 使用动态配置（Spring Cloud Config）

#### 7.2 故障诊断流程

**步骤 1: 症状识别**
- 用户报告 / 监控告警
- 确定影响范围（部分/全部功能）

**步骤 2: 日志分析**
- 查看应用日志：`kubectl logs -f POD`
- 关键日志模式：
  - `ERROR` - 错误信息
  - `WARN` - 警告信息
  - `kafka.consumer.lag` - 消费积压
  - `checker.duration` - Checker 耗时

**步骤 3: 指标检查**
- Consumer Lag: 正常 <1000，异常 >10000
- Processing Latency: 正常 p99 <200ms，异常 >500ms
- Error Rate: 正常 <1%，异常 >5%

**步骤 4: 根因定位**
- Kafka 问题 → 检查 broker 健康
- 数据库问题 → 检查连接池和慢查询
- Checker 问题 → 检查特定 checker 耗时
- 网络问题 → 检查 DNS 和连接

#### 7.3 应急处理

##### 快速降级

**场景**: 审计服务故障影响主服务

**步骤**:
1. 禁用审计层（保持 runtime 防御层正常）
2. 修改 ConfigMap:
   ```yaml
   sqlguard.audit.enabled: false
   ```
3. 重启应用
4. 验证主服务恢复

##### 数据恢复

**场景**: ClickHouse 数据损坏

**步骤**:
1. 停止审计服务（防止继续写入）
2. 从备份恢复 ClickHouse
3. 重放 Kafka 消息（从最后一次备份时间点）
4. 启动审计服务

##### 回滚操作

**场景**: 新版本部署后出现问题

**步骤**:
```bash
# Kubernetes 回滚
kubectl rollout undo statefulset/sql-audit-service

# 验证回滚成功
kubectl rollout status statefulset/sql-audit-service
```

#### 7.4 性能调优

##### 常见性能瓶颈

1. **Checker 慢**
   - 症状：`checker.duration > 100ms`
   - 解决：优化 checker 逻辑、增加缓存

2. **数据库连接池不足**
   - 症状：`hikaricp.connections.active == max`
   - 解决：增加 `spring.datasource.hikari.maximum-pool-size`

3. **ClickHouse 写入慢**
   - 症状：`clickhouse.write.latency > 500ms`
   - 解决：增加 batch size、优化表结构

##### JVM 参数优化

```bash
# 堆内存
-Xms4g -Xmx8g

# GC 配置 (G1GC)
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:InitiatingHeapOccupancyPercent=45

# GC 日志
-Xlog:gc*:file=/logs/gc.log:time,uptime:filecount=10,filesize=10M
```

##### 数据库连接池调优

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # 根据并发调整
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

---

## 实现要求

### 1. 生产部署指南文档

**核心内容**:
- ✅ 资源 Sizing（4个规模）
- ✅ Kubernetes 部署模板（StatefulSet, ConfigMap, Secret, Service, Ingress）
- ✅ 高可用配置（Kafka consumer group, DB replication）
- ✅ 安全配置（JWT, RBAC, TLS）
- ✅ 监控告警（Prometheus, Grafana）
- ✅ 备份恢复（PostgreSQL, ClickHouse）

### 2. Kubernetes 部署模板

**核心功能**:
- ✅ StatefulSet（3 replicas）
- ✅ Health checks（liveness, readiness）
- ✅ Resource limits
- ✅ ConfigMap 和 Secret 注入
- ✅ Volume claims

### 3. 运维手册

**核心内容**:
- ✅ 4 个常见问题（Kafka积压、ClickHouse超时、OOM、配置不生效）
- ✅ 故障诊断流程（4步）
- ✅ 应急处理（降级、恢复、回滚）
- ✅ 性能调优（瓶颈分析、JVM参数、连接池）

---

## 验收标准

### 功能验收
- [ ] 生产部署指南文档完整（200+ 行）
- [ ] Kubernetes 部署模板完整（5 个 YAML）
- [ ] 高可用配置文档完整
- [ ] 安全配置文档完整
- [ ] 监控告警配置完整
- [ ] 备份恢复文档完整
- [ ] 运维手册完整

### 测试验收

#### Kubernetes部署TDD (KubernetesDeploymentTest - 15 tests)
- [ ] `testK8s_StatefulSet_shouldDeploy()` - StatefulSet 部署成功
- [ ] `testK8s_multipleReplicas_shouldScale()` - 多副本扩展
- [ ] `testK8s_ConfigMap_shouldMount()` - ConfigMap 挂载
- [ ] `testK8s_Secret_shouldInject()` - Secret 注入
- [ ] `testK8s_PersistentVolumeClaim_shouldProvision()` - PVC 供应
- [ ] `testK8s_Service_shouldExpose()` - Service 暴露
- [ ] `testK8s_Ingress_shouldRoute()` - Ingress 路由
- [ ] `testK8s_healthCheck_shouldRestartOnFailure()` - 健康检查重启
- [ ] `testK8s_rollingUpdate_shouldZeroDowntime()` - 滚动更新零停机
- [ ] `testK8s_resourceLimits_shouldEnforce()` - 资源限制强制
- [ ] `testK8s_nodeAffinity_shouldSchedule()` - 节点亲和性调度
- [ ] `testK8s_podDisruptionBudget_shouldRespect()` - Pod 中断预算
- [ ] `testK8s_horizontalPodAutoscaler_shouldScale()` - 水平自动扩展
- [ ] `testK8s_networkPolicy_shouldIsolate()` - 网络策略隔离
- [ ] `testK8s_serviceAccount_shouldAuthorize()` - ServiceAccount 授权

#### 高可用性TDD (HighAvailabilityTest - 10 tests)
- [ ] `testHA_kafkaConsumerGroup_shouldDistribute()` - Kafka consumer group 分配
- [ ] `testHA_instanceFailure_shouldReassign()` - 实例失败重分配
- [ ] `testHA_postgresReplication_shouldSync()` - PostgreSQL 复制同步
- [ ] `testHA_clickHouseReplication_shouldSync()` - ClickHouse 复制同步
- [ ] `testHA_loadBalancer_shouldDistribute()` - 负载均衡分发
- [ ] `testHA_healthCheck_shouldDetectFailure()` - 健康检查检测失败
- [ ] `testHA_failover_shouldBeAutomatic()` - 自动故障转移
- [ ] `testHA_splitBrain_shouldPrevent()` - 防止脑裂
- [ ] `testHA_dataConsistency_shouldMaintain()` - 维护数据一致性
- [ ] `testHA_RTO_shouldMeetTarget()` - RTO 达标

#### 安全TDD (SecurityHardeningTest - 5 tests)
- [ ] `testSecurity_JWT_shouldValidate()` - JWT 验证
- [ ] `testSecurity_RBAC_shouldEnforce()` - RBAC 强制
- [ ] `testSecurity_TLS_shouldEncrypt()` - TLS 加密
- [ ] `testSecurity_credentials_shouldBeSecured()` - 凭证安全
- [ ] `testSecurity_auditLog_PII_shouldSanitize()` - 审计日志 PII 脱敏

#### 运维手册验证TDD (OperationsGuideValidationTest - 5 tests)
- [ ] `testTroubleshooting_kafkaLag_shouldResolve()` - Kafka 积压解决
- [ ] `testTroubleshooting_clickHouseTimeout_shouldResolve()` - ClickHouse 超时解决
- [ ] `testTroubleshooting_OOM_shouldDiagnose()` - OOM 诊断
- [ ] `testTroubleshooting_configNotEffective_shouldDebug()` - 配置不生效调试
- [ ] `testEmergencyProcedure_degradation_shouldExecute()` - 应急降级执行

### 集成验收
- [ ] Kubernetes 部署成功率 100%
- [ ] HA 切换时间 <30s
- [ ] 所有运维手册步骤可执行
- [ ] 备份恢复流程验证通过

### 代码质量验收
- [ ] Kubernetes YAML 语法正确
- [ ] 文档格式统一（Markdown）
- [ ] 命令示例可执行
- [ ] 配置参数说明清晰

### 构建验收
- [ ] Kubernetes 部署测试通过（15 tests）
- [ ] HA 测试通过（10 tests）
- [ ] 安全测试通过（5 tests）
- [ ] 运维手册测试通过（5 tests）
- [ ] BUILD SUCCESS

---

## 输出文件

### 新增文件 (20+ 个)

**部署指南 (1)**:
1. `docs/deployment/production-deployment.md`

**Kubernetes 模板 (5)**:
1. `docs/deployment/k8s/statefulset.yaml`
2. `docs/deployment/k8s/configmap.yaml`
3. `docs/deployment/k8s/secret.yaml`
4. `docs/deployment/k8s/service.yaml`
5. `docs/deployment/k8s/ingress.yaml`

**HA 配置 (2)**:
1. `docs/deployment/ha/postgres-replication.md`
2. `docs/deployment/ha/clickhouse-replication.md`

**安全配置 (1)**:
1. `docs/deployment/security/jwt-authentication.md`

**监控配置 (3)**:
1. `docs/deployment/monitoring/prometheus-metrics.md`
2. `docs/deployment/monitoring/alert-rules.yaml`
3. `docs/deployment/monitoring/grafana-dashboard.json`

**备份恢复 (3)**:
1. `docs/deployment/backup/postgres-backup.md`
2. `docs/deployment/backup/clickhouse-backup.md`
3. `docs/deployment/backup/disaster-recovery.md`

**运维手册 (1)**:
1. `docs/operations/troubleshooting-guide.md`

**测试文件 (4)**:
1. `sql-audit-service/src/test/java/com/footstone/audit/service/deployment/KubernetesDeploymentTest.java`
2. `sql-audit-service/src/test/java/com/footstone/audit/service/deployment/HighAvailabilityTest.java`
3. `sql-audit-service/src/test/java/com/footstone/audit/service/deployment/SecurityHardeningTest.java`
4. `sql-audit-service/src/test/java/com/footstone/audit/service/deployment/OperationsGuideValidationTest.java`

---

## 依赖与限制

### 输入依赖
- ✅ Phase 10 Output: sql-audit-service (deployable artifact)
- ✅ Kubernetes 环境（kind/minikube/production cluster）
- ✅ Kafka, PostgreSQL, ClickHouse 部署

### 限制
- ⚠️ Kubernetes 测试需要 kind 或 minikube
- ⚠️ HA 测试需要多节点环境（可模拟）
- ⚠️ 安全测试需要证书配置
- ⚠️ 备份恢复测试需要 S3 或本地存储

---

## 注意事项

### 1. Kubernetes 测试环境

**问题**: 如何在本地测试 Kubernetes 部署？

**解决方案**: 使用 kind (Kubernetes in Docker)

```bash
# 安装 kind
brew install kind

# 创建集群
kind create cluster --name sqlguard

# 部署应用
kubectl apply -f docs/deployment/k8s/

# 清理
kind delete cluster --name sqlguard
```

---

### 2. StatefulSet vs Deployment

**问题**: 为什么使用 StatefulSet？

**解答**:
- StatefulSet 提供稳定的网络标识（audit-service-0, audit-service-1）
- Kafka consumer 需要稳定的 consumer ID
- PersistentVolumeClaim 绑定到特定 Pod

**何时使用 Deployment**:
- 无状态服务（如只读 API）
- 不需要稳定网络标识

---

### 3. Health Check 配置

**问题**: livenessProbe vs readinessProbe？

**解答**:
- **livenessProbe**: 容器存活检查，失败则重启容器
- **readinessProbe**: 就绪检查，失败则从 Service 移除

**最佳实践**:
- livenessProbe: 检查应用是否死锁/挂起
- readinessProbe: 检查应用是否可以处理请求（数据库连接、Kafka连接）

---

### 4. 备份策略选择

**问题**: 全量备份 vs 增量备份？

**解答**:

| 策略 | 优点 | 缺点 | 适用场景 |
|------|------|------|----------|
| **全量备份** | 恢复简单 | 占用空间大 | 小/中型数据库 |
| **增量备份** | 占用空间小 | 恢复复杂 | 大型数据库 |
| **WAL归档** | PITR支持 | 配置复杂 | 金融/关键业务 |

**推荐**:
- PostgreSQL: 每日全量 + WAL 归档
- ClickHouse: 每日全量 + S3 存储

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_14_Audit_Examples_Documentation/Task_14_2_Deployment_Guide_Implementation.md
```

**Required Log Sections**:
- Summary: Production deployment guide implementation outcome
- Details: Kubernetes 模板、HA 配置、安全配置、监控告警、备份恢复、运维手册
- Output: List of created files (20+ files), test results (35 tests)
- Issues: K8s 部署问题、HA 测试问题、安全配置问题或"None"
- Important Findings: K8s 最佳实践、HA 故障转移时间、运维手册实用性
- Next Steps: 生产环境验证、性能压测、文档优化

---

## 执行时间线

- **预计时间**: 4 工作日
  - Day 1 上午：生产部署指南（资源 Sizing + 架构设计）
  - Day 1 下午：Kubernetes 部署模板（StatefulSet + ConfigMap + Secret）
  - Day 2 上午：高可用配置（Kafka consumer group + DB replication）
  - Day 2 下午：安全配置（JWT + RBAC + TLS）
  - Day 3 上午：监控告警（Prometheus + Grafana + 告警规则）
  - Day 3 下午：备份恢复（PostgreSQL + ClickHouse + 灾难恢复）
  - Day 4 上午：运维手册（故障排查 + 应急处理 + 性能调优）
  - Day 4 下午：测试（K8s 部署 + HA + 安全 + 运维手册验证）

---

**Created**: 2025-12-22
**Phase**: 14 - Audit Platform Examples & Documentation
**Task ID**: 14.2
**Priority**: High（生产部署关键）
**Parallel**: Can run in parallel with Task 14.1, 14.3, 14.4
