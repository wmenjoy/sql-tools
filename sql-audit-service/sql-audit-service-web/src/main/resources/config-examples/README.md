# SQL审计服务配置示例说明

本目录包含SQL审计服务在不同存储方案下的配置示例文件，帮助您快速部署和配置系统。

## 📁 配置文件列表

| 配置文件 | 存储模式 | 适用场景 | 日均审计量 | 部署复杂度 |
|---------|---------|---------|-----------|----------|
| `application-mysql-es.yml` | MySQL + Elasticsearch | 中大型企业（推荐） | 100万-1000万 | ⭐⭐⭐ |
| `application-mysql-only.yml` | MySQL单库 | 小型项目 | < 100万 | ⭐ |
| `application-postgresql-only.yml` | PostgreSQL单库 | 中型企业 | 100万-500万 | ⭐⭐ |
| `application-full.yml` | PostgreSQL + ClickHouse | 大型企业 | > 1000万 | ⭐⭐⭐⭐⭐ |
| `application-elasticsearch.yml` | Elasticsearch单库 | 搜索场景 | 100万-1000万 | ⭐⭐⭐ |

## 🚀 快速开始

### 1. 选择配置模式

根据您的业务场景选择合适的配置文件：

- **小型项目/快速验证**: `application-mysql-only.yml`
- **中大型企业（推荐）**: `application-mysql-es.yml`
- **需要强大搜索**: `application-elasticsearch.yml`
- **超大规模**: `application-full.yml`

### 2. 复制配置文件

将选择的配置文件复制到 `src/main/resources/` 目录，并重命名为 `application.yml`：

```bash
# 示例：使用MySQL+ES模式
cp config-examples/application-mysql-es.yml ../application.yml
```

### 3. 修改配置参数

打开复制的 `application.yml` 文件，修改以下必要参数：

#### 数据库连接配置
```yaml
spring:
  datasource:
    url: jdbc:mysql://YOUR_DB_HOST:3306/sql_audit
    username: YOUR_USERNAME
    password: YOUR_PASSWORD
```

#### Kafka连接配置
```yaml
spring:
  kafka:
    bootstrap-servers: YOUR_KAFKA_HOST:9092
```

#### 存储特定配置
根据选择的模式，修改相应的存储配置：

**MySQL+ES模式**:
```yaml
audit:
  storage:
    elasticsearch:
      hosts: YOUR_ES_HOST:9200
      username: elastic
      password: YOUR_ES_PASSWORD
```

**ClickHouse模式**:
```yaml
audit:
  storage:
    clickhouse:
      url: jdbc:clickhouse://YOUR_CH_HOST:8123/audit
      username: default
      password: YOUR_CH_PASSWORD
```

### 4. 启动服务

```bash
# 开发环境
mvn spring-boot:run

# 生产环境
java -jar sql-audit-service-web-1.0.0-SNAPSHOT.jar
```

## 📊 存储方案对比

### MySQL + Elasticsearch (推荐) ⭐⭐⭐⭐⭐

**优点**:
- ✅ MySQL存储元数据，事务性强
- ✅ Elasticsearch存储时序数据，搜索能力强
- ✅ Kibana可视化分析强大
- ✅ 成熟稳定，运维经验丰富

**缺点**:
- ❌ 需要维护两套存储系统
- ❌ 运维成本相对较高

**推荐场景**:
- 中大型企业
- 需要全文搜索SQL语句
- 已有ES基础设施
- 需要强大的可视化能力

---

### MySQL单库模式 ⭐⭐⭐

**优点**:
- ✅ 部署简单，只需MySQL
- ✅ 运维成本低
- ✅ 事务性强

**缺点**:
- ❌ 大量时序数据影响性能
- ❌ 搜索能力有限
- ❌ 需要定期清理数据

**推荐场景**:
- 小型项目快速验证
- 数据量不大（< 100万/日）
- 预算有限

**重要提示**:
- 建议保留天数不超过30天
- 定期监控数据库磁盘使用率

---

### PostgreSQL单库模式 ⭐⭐⭐⭐

**优点**:
- ✅ 原生JSON支持，查询性能好
- ✅ 内置全文搜索功能
- ✅ 分区表性能优秀
- ✅ 可扩展性强（TimescaleDB、Citus）

**缺点**:
- ❌ 超大规模需要分库分表
- ❌ 学习曲线略陡

**推荐场景**:
- 中型企业
- 已有PostgreSQL基础设施
- 需要JSON查询能力
- 数据量中等（100万-500万/日）

---

### PostgreSQL + ClickHouse (大规模) ⭐⭐⭐⭐⭐

**优点**:
- ✅ ClickHouse列式存储，压缩比高（节省90%空间）
- ✅ 查询性能极快，支持PB级数据
- ✅ 水平扩展能力强
- ✅ 可保留长期数据（1年+）

**缺点**:
- ❌ 架构复杂，运维成本高
- ❌ 需要专业团队维护
- ❌ 学习成本高

**推荐场景**:
- 大型企业、超大规模部署
- 海量数据分析需求（> 1000万/日）
- 需要长期数据归档
- 有专业运维团队

**性能对比**:
- 插入性能: 50万-200万行/秒
- 查询性能: 亿级数据秒级响应
- 压缩比: 10-100倍

---

### Elasticsearch单库模式 ⭐⭐⭐⭐

**优点**:
- ✅ 全文搜索能力最强
- ✅ Kibana可视化强大
- ✅ 横向扩展容易
- ✅ 实时性好

**缺点**:
- ❌ 事务支持弱
- ❌ 复杂关系查询性能差
- ❌ 堆内存消耗大

**推荐场景**:
- 需要强大的全文搜索
- 已有ES基础设施
- 重搜索轻事务
- 需要Kibana可视化

## 🔧 高级配置

### Kafka消费者调优

根据数据量调整并发数：

```yaml
audit:
  kafka:
    consumer:
      virtual-thread:
        concurrency: 4  # 小规模: 1-2, 中等: 4-8, 大规模: 16-32
```

### 背压控制调优

根据系统性能调整阈值：

```yaml
audit:
  kafka:
    consumer:
      backpressure:
        latency-threshold-ms: 200  # 实时系统: 100-200ms, 批处理: 500-1000ms
        failure-threshold: 5       # 容错能力: 3-10次
```

### 数据保留策略

根据需求调整保留天数：

```yaml
audit:
  storage:
    retention:
      retention-days: 90  # MySQL: 30天, PostgreSQL: 60天, ClickHouse: 365天
```

## 📝 数据库初始化

### MySQL

```sql
CREATE DATABASE sql_audit CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'audit_user'@'%' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON sql_audit.* TO 'audit_user'@'%';
FLUSH PRIVILEGES;
```

### PostgreSQL

```sql
CREATE DATABASE sql_audit ENCODING 'UTF8';

CREATE USER audit_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE sql_audit TO audit_user;
```

### ClickHouse

```sql
CREATE DATABASE audit;

CREATE USER audit_user IDENTIFIED WITH plaintext_password BY 'your_password';
GRANT ALL ON audit.* TO audit_user;
```

## 🔍 验证部署

启动服务后，访问以下端点验证：

- **健康检查**: `http://localhost:8080/actuator/health`
- **API文档**: `http://localhost:8080/swagger-ui.html`
- **Prometheus指标**: `http://localhost:8080/actuator/prometheus`

## 📞 获取帮助

如有问题，请查看：
- 主配置文件: `../application.yml`
- 各配置示例文件中的详细注释
- 项目文档: `docs/` 目录

## 🔄 迁移指南

### 从MySQL单库迁移到MySQL+ES

1. 部署Elasticsearch集群
2. 修改配置: `mode: mysql-only` → `mode: mysql-es`
3. 配置Elasticsearch连接信息
4. 重启服务（历史数据会保留在MySQL）

### 从MySQL+ES迁移到Full模式

1. 部署ClickHouse集群
2. 部署PostgreSQL（或继续使用MySQL）
3. 修改配置: `mode: mysql-es` → `mode: full`
4. 配置ClickHouse连接信息
5. 迁移历史数据（可选）
6. 重启服务

## ⚠️ 注意事项

1. **生产环境部署**:
   - 使用外部配置文件，不要硬编码密码
   - 启用SSL/TLS加密
   - 配置防火墙规则
   - 定期备份数据

2. **性能优化**:
   - 根据实际负载调整虚拟线程并发数
   - 监控Kafka消费lag
   - 定期清理历史数据

3. **监控告警**:
   - 配置Prometheus+Grafana监控
   - 设置关键指标告警
   - 监控磁盘使用率

4. **安全建议**:
   - 定期更新密码
   - 使用专用账号，最小权限原则
   - 审计日志不要包含敏感信息
