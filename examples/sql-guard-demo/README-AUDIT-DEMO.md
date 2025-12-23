# SqlGuard Audit Platform Demo

完整的 SQL 审计平台演示，展示双层保护架构：**Prevention Layer** (实时拦截) + **Discovery Layer** (审计分析)。

## 架构概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SQL Guard Demo Application                          │
│                     (Spring Boot + MyBatis/MyBatis-Plus)                    │
│                                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ Prevention   │  │ Audit        │  │ Load         │  │ Scenario     │    │
│  │ Layer        │  │ Logger       │  │ Generator    │  │ Endpoints    │    │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘    │
│         │                 │                                                  │
└─────────┼─────────────────┼──────────────────────────────────────────────────┘
          │                 │
          │                 ▼
          │         ┌──────────────┐
          │         │    Kafka     │
          │         └──────────────┘
          │                 │
          ▼                 ▼
    ┌──────────┐    ┌──────────────────┐
    │  MySQL   │    │ SQL Audit Service│
    │  (Demo)  │    │                  │
    └──────────┘    └──────────────────┘
                            │
              ┌─────────────┼─────────────┐
              ▼             ▼             ▼
        ┌──────────┐  ┌───────────┐  ┌─────────┐
        │PostgreSQL│  │ClickHouse │  │ Grafana │
        │(Metadata)│  │(Audit Log)│  │(Visual) │
        └──────────┘  └───────────┘  └─────────┘
```

## 快速开始

### 1. 启动完整栈 (约 2 分钟)

```bash
cd examples/sql-guard-demo
docker-compose up -d
```

等待所有服务健康检查通过：

```bash
# 检查服务状态
docker-compose ps

# 查看日志
docker-compose logs -f demo-app
```

**服务端口:**
| 服务 | 端口 | 用途 |
|------|------|------|
| Demo App | 8080 | 演示应用 API |
| Audit Service | 8090 | 审计服务 API |
| Grafana | 3000 | 可视化仪表板 |
| MySQL | 3306 | 演示数据库 |
| Kafka | 9092 | 消息队列 |
| ClickHouse | 8123 | 审计日志存储 |
| PostgreSQL | 5432 | 审计元数据 |

### 2. 运行负载生成器

```bash
# 启动 5 分钟负载生成 (默认)
curl -X POST http://localhost:8080/api/load-generator/start

# 或指定时间 (分钟)
curl -X POST http://localhost:8080/api/load-generator/start/10

# 查看状态
curl http://localhost:8080/api/load-generator/status

# 停止
curl -X POST http://localhost:8080/api/load-generator/stop
```

**负载分布:**
- 80% 快查询 (<100ms) - 正常操作
- 15% 慢查询 (深度分页) - 性能问题
- 5% 错误查询 - 触发错误率检测

### 3. 查看 Grafana 仪表板

打开浏览器访问：**http://localhost:3000**

- 用户名: `admin`
- 密码: `admin`

**可用仪表板:**

1. **Risk Overview** - 风险概览
   - Severity 分布饼图 (CRITICAL/HIGH/MEDIUM/LOW)
   - Top 10 高风险 SQL 表格
   - 风险趋势折线图

2. **Performance** - 性能分析
   - 查询延迟折线图 (P50/P95/P99)
   - 最慢查询 Top 10
   - 慢查询热力图

3. **Errors** - 错误分析
   - 错误率时间线
   - 错误分类饼图
   - Top 错误消息表格

### 4. 审计服务 REST API

```bash
# 获取最近的 CRITICAL 发现
curl "http://localhost:8090/api/audit/findings?severity=CRITICAL&limit=10"

# 获取仪表板统计数据
curl http://localhost:8090/api/audit/statistics/dashboard

# 获取慢查询 Top 10
curl "http://localhost:8090/api/audit/slow-queries?limit=10"
```

### 5. 查询 ClickHouse 原始数据

```bash
# 进入 ClickHouse 容器
docker exec -it sqlguard-clickhouse clickhouse-client

# 查询审计日志
SELECT sql, severity, risk_score, execution_time_ms
FROM audit.sql_executions
WHERE severity = 'CRITICAL'
ORDER BY timestamp DESC
LIMIT 10;

# 统计各严重级别数量
SELECT severity, count(*) as count
FROM audit.sql_executions
WHERE timestamp >= now() - INTERVAL 1 HOUR
GROUP BY severity;

# 退出
exit
```

---

## 审计场景演示

### 场景 1: 慢查询检测

```bash
curl http://localhost:8080/api/audit-scenarios/slow-query
```

**预期结果:**
- 查询耗时约 5 秒 (使用 MySQL SLEEP)
- SlowQueryChecker 生成 **HIGH** 严重度审计日志
- Grafana Performance 仪表板显示延迟飙升

### 场景 2: 无WHERE更新检测

```bash
curl -X POST http://localhost:8080/api/audit-scenarios/missing-where
```

**预期结果:**
- 更新所有 user 记录
- ActualImpactNoWhereChecker 生成 **CRITICAL** 严重度审计日志
- Grafana Risk Overview 显示高风险 SQL

> ⚠️ **警告**: 此操作会将所有用户状态设为 INACTIVE

### 场景 3: 深度分页检测

```bash
curl http://localhost:8080/api/audit-scenarios/deep-pagination
```

**预期结果:**
- 查询使用 OFFSET 10000
- DeepPaginationChecker 生成 **MEDIUM** 严重度审计日志
- 性能仪表板显示慢查询

### 场景 4: 错误率飙升

```bash
# 快速触发 100 次错误
for i in {1..100}; do
  curl http://localhost:8080/api/audit-scenarios/error-sql
done
```

**预期结果:**
- ErrorRateChecker 聚合 100 次错误
- Grafana Errors 仪表板显示错误率飙升
- 错误分类饼图更新

### 场景 5: 大页面查询

```bash
curl http://localhost:8080/api/audit-scenarios/large-page-size
```

**预期结果:**
- 查询请求 5000 行数据
- LargePageSizeChecker 生成 **MEDIUM** 严重度审计日志

### 场景 6: 无分页查询

```bash
curl http://localhost:8080/api/audit-scenarios/no-pagination
```

**预期结果:**
- 查询返回所有 ACTIVE 用户
- NoPaginationChecker 生成审计日志

---

## API 参考

### Demo Application (端口 8080)

| 端点 | 方法 | 描述 |
|------|------|------|
| `/` | GET | API 文档首页 |
| `/api/audit-scenarios` | GET | 列出所有审计场景 |
| `/api/audit-scenarios/slow-query` | GET | 触发慢查询 |
| `/api/audit-scenarios/missing-where` | POST | 触发无WHERE更新 |
| `/api/audit-scenarios/deep-pagination` | GET | 触发深度分页 |
| `/api/audit-scenarios/error-sql` | GET | 触发SQL错误 |
| `/api/audit-scenarios/large-page-size` | GET | 触发大页面 |
| `/api/audit-scenarios/no-pagination` | GET | 触发无分页 |
| `/api/load-generator/start` | POST | 启动负载生成器 (5分钟) |
| `/api/load-generator/start/{minutes}` | POST | 启动负载生成器 (自定义时间) |
| `/api/load-generator/stop` | POST | 停止负载生成器 |
| `/api/load-generator/status` | GET | 获取负载生成器状态 |
| `/actuator/health` | GET | 健康检查 |

### Audit Service (端口 8090)

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/audit/findings` | GET | 获取审计发现 |
| `/api/audit/statistics/dashboard` | GET | 仪表板统计 |
| `/api/audit/slow-queries` | GET | 慢查询列表 |
| `/actuator/health` | GET | 健康检查 |
| `/swagger-ui.html` | GET | Swagger UI |

---

## 故障排查

### 查看服务日志

```bash
# Demo 应用日志
docker logs sqlguard-demo-app -f

# 审计服务日志
docker logs sqlguard-audit-service -f

# Kafka 日志
docker logs sqlguard-kafka -f

# 所有服务日志
docker-compose logs -f
```

### 健康检查

```bash
# Demo 应用
curl http://localhost:8080/actuator/health

# 审计服务
curl http://localhost:8090/actuator/health

# Kafka
docker exec sqlguard-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# ClickHouse
curl http://localhost:8123/ping

# PostgreSQL
docker exec sqlguard-postgres pg_isready -U audit -d audit
```

### 常见问题

**Q: 服务启动失败?**
```bash
# 检查端口冲突
lsof -i :8080
lsof -i :3306

# 重新启动
docker-compose down
docker-compose up -d
```

**Q: Kafka 连接失败?**
```bash
# 检查 Kafka 状态
docker exec sqlguard-kafka kafka-topics --bootstrap-server localhost:9092 --list

# 创建 topic
docker exec sqlguard-kafka kafka-topics --bootstrap-server localhost:9092 \
  --create --topic sql-audit-events --partitions 3 --replication-factor 1
```

**Q: Grafana 无数据?**
1. 检查 ClickHouse 连接
2. 确认负载生成器已运行
3. 刷新仪表板 (F5)

---

## 清理环境

```bash
# 停止并删除容器
docker-compose down

# 同时删除数据卷
docker-compose down -v

# 删除所有 (包括镜像)
docker-compose down -v --rmi all
```

---

## 技术栈

- **Demo Application**: Spring Boot 2.7 + MyBatis + MyBatis-Plus
- **Message Queue**: Apache Kafka 7.5
- **Audit Service**: Spring Boot 3.x + Virtual Threads
- **Metadata Storage**: PostgreSQL 15
- **Audit Log Storage**: ClickHouse 23.8
- **Visualization**: Grafana 10.0
- **Container**: Docker + Docker Compose

---

## 下一步

1. **探索仪表板**: 在 Grafana 中查看不同时间范围的数据
2. **自定义负载**: 修改负载生成器参数
3. **添加场景**: 在 `AuditScenarioMapper` 添加新的审计场景
4. **集成到项目**: 将 `sql-guard-spring-boot-starter` 添加到你的项目
5. **配置告警**: 在 Grafana 配置告警规则

---

## 相关文档

- [SQL Guard Core 文档](../../sql-guard-core/README.md)
- [Audit API 文档](../../sql-guard-audit-api/README.md)
- [Audit Service 文档](../../sql-audit-service/README.md)
- [Spring Boot Starter 文档](../../sql-guard-spring-boot-starter/README.md)




