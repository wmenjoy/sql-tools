---
task_ref: "Task 14.1 - Audit-Enhanced Demo Application"
agent_assignment: "Agent_Documentation"
memory_log_path: ".apm/Memory/Phase_14_Audit_Examples_Documentation/Task_14_1_Demo_Application_Implementation.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
phase: 14
task_id: 14.1
estimated_duration: 5 days
dependencies: [Phase_8, Phase_9, Phase_10, Phase_7_Demo]
parallel_with: [Task_14.2, Task_14.3, Task_14.4]
priority: "High"
---

# Task 14.1 Assignment: Audit-Enhanced Demo Application

## 任务目标

扩展现有 sql-guard-demo 应用集成完整的 SQL Audit Platform，展示双层保护架构（prevention + discovery），包括审计日志生成、审计服务消费和分析、REST API 查询、Grafana 可视化仪表板。

---

## 背景说明

### 为什么需要 Audit-Enhanced Demo？

**问题**: 用户需要看到完整的 SqlGuard 双层架构运行效果：
1. **Prevention Layer (Phase 1-6)**: 实时拦截危险 SQL
2. **Discovery Layer (Phase 8-10)**: 审计日志 + 分析 + 可视化

**Demo 价值**：
- ✅ 展示完整的审计流程：拦截器 → Kafka → 审计服务 → 数据库 → Grafana
- ✅ 提供真实的审计场景：慢查询、无WHERE更新、错误率飙升、深度分页
- ✅ 演示负载生成和审计分析能力
- ✅ 快速上手：docker-compose up 一键启动完整栈

**Demo 架构**:
```
sql-guard-demo (Spring Boot)
    ↓ (审计日志)
Kafka
    ↓
sql-audit-service
    ↓ (写入)
PostgreSQL + ClickHouse
    ↑ (查询)
Grafana Dashboards
```

---

## 核心组件设计

### 1. 扩展的 Demo 应用

**目标**: 为现有 sql-guard-demo 启用审计日志

**实现要点**:

#### 1.1 审计日志配置

**文件**: `sql-guard-demo/src/main/resources/application-audit.yml`

```yaml
sqlguard:
  audit:
    enabled: true
    log-writer: logback  # Task 8.1
    kafka:
      bootstrap-servers: localhost:9092
      topic: sql-audit-events

  # 启用所有审计拦截器
  interceptors:
    mybatis:
      audit: true
    mybatis-plus:
      audit: true
    druid:
      audit: true
```

#### 1.2 审计场景 Mapper

**文件**: `sql-guard-demo/src/main/resources/mapper/AuditScenarioMapper.xml`

```xml
<!-- 场景 1: 慢查询 (5秒延迟) -->
<select id="slowQuery" resultType="User">
    SELECT * FROM users WHERE SLEEP(5) = 0
</select>

<!-- 场景 2: 无WHERE更新 (影响所有行) -->
<update id="updateWithoutWhere">
    UPDATE users SET status = 'INACTIVE'
</update>

<!-- 场景 3: 深度分页 (offset 10000) -->
<select id="deepPagination" resultType="User">
    SELECT * FROM users ORDER BY id LIMIT 100 OFFSET 10000
</select>
```

#### 1.3 审计场景 Controller

**文件**: `sql-guard-demo/src/main/java/com/footstone/sqlguard/demo/controller/AuditScenarioController.java`

```java
@RestController
@RequestMapping("/api/audit-scenarios")
public class AuditScenarioController {

    @Autowired
    private AuditScenarioMapper mapper;

    @GetMapping("/slow-query")
    public ResponseEntity<String> triggerSlowQuery() {
        // 触发慢查询，生成 SlowQueryChecker 审计日志
        List<User> users = mapper.slowQuery();
        return ResponseEntity.ok("Slow query executed, check audit logs");
    }

    @PostMapping("/missing-where")
    public ResponseEntity<String> triggerMissingWhere() {
        // 触发无WHERE更新，生成 ActualImpactNoWhereChecker 审计日志
        int affected = mapper.updateWithoutWhere();
        return ResponseEntity.ok("Updated " + affected + " rows, check audit logs");
    }

    @GetMapping("/deep-pagination")
    public ResponseEntity<String> triggerDeepPagination() {
        // 触发深度分页，生成 DeepPaginationChecker 审计日志
        List<User> users = mapper.deepPagination();
        return ResponseEntity.ok("Deep pagination executed, check audit logs");
    }

    @GetMapping("/error-sql")
    public ResponseEntity<String> triggerError() {
        // 触发SQL错误，生成 ErrorRateChecker 审计日志
        try {
            mapper.invalidSql();
        } catch (Exception e) {
            return ResponseEntity.ok("SQL error triggered, check audit logs");
        }
        return ResponseEntity.ok("Unexpected success");
    }
}
```

---

### 2. Docker Compose 栈

**目标**: 一键启动完整的审计平台演示环境

**实现要点**:

**文件**: `sql-guard-demo/docker-compose.yml`

```yaml
version: '3.8'

services:
  # 1. Demo 应用
  demo-app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: audit
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      - mysql
      - kafka
    networks:
      - sqlguard-net

  # 2. MySQL (Demo 数据库)
  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: demo
    volumes:
      - ./schema.sql:/docker-entrypoint-initdb.d/schema.sql
    networks:
      - sqlguard-net

  # 3. Kafka
  kafka:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
    depends_on:
      - zookeeper
    networks:
      - sqlguard-net

  # 4. Zookeeper
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    ports:
      - "2181:2181"
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    networks:
      - sqlguard-net

  # 5. SQL Audit Service
  audit-service:
    image: sql-audit-service:latest
    ports:
      - "8090:8090"
    environment:
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/audit
      CLICKHOUSE_URL: jdbc:clickhouse://clickhouse:8123/audit
    depends_on:
      - kafka
      - postgres
      - clickhouse
    networks:
      - sqlguard-net

  # 6. PostgreSQL (审计元数据)
  postgres:
    image: postgres:15
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: audit
      POSTGRES_USER: audit
      POSTGRES_PASSWORD: audit
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - sqlguard-net

  # 7. ClickHouse (审计日志存储)
  clickhouse:
    image: clickhouse/clickhouse-server:23.8
    ports:
      - "8123:8123"
      - "9000:9000"
    environment:
      CLICKHOUSE_DB: audit
      CLICKHOUSE_USER: audit
      CLICKHOUSE_PASSWORD: audit
    volumes:
      - clickhouse-data:/var/lib/clickhouse
    networks:
      - sqlguard-net

  # 8. Grafana
  grafana:
    image: grafana/grafana:10.0.0
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - ./grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./grafana/datasources:/etc/grafana/provisioning/datasources
      - grafana-data:/var/lib/grafana
    depends_on:
      - audit-service
      - clickhouse
    networks:
      - sqlguard-net

networks:
  sqlguard-net:
    driver: bridge

volumes:
  postgres-data:
  clickhouse-data:
  grafana-data:
```

---

### 3. Grafana 仪表板

**目标**: 可视化审计发现和趋势

**实现要点**:

#### 3.1 风险概览仪表板

**文件**: `sql-guard-demo/grafana/dashboards/risk-overview.json`

**包含 Panel**:
1. **Severity 分布饼图**: CRITICAL/HIGH/MEDIUM/LOW 占比
2. **Top 10 高风险 SQL 表格**: SQL + Risk Score + Severity + 执行次数
3. **风险趋势折线图**: 每小时 CRITICAL 发现数量

**数据源**: Audit Service REST API + ClickHouse

#### 3.2 性能仪表板

**文件**: `sql-guard-demo/grafana/dashboards/performance.json`

**包含 Panel**:
1. **查询延迟折线图**: p95/p99 延迟随时间变化
2. **最慢查询条形图**: Top 10 慢查询及其平均延迟
3. **慢查询分布热力图**: 按小时统计慢查询频率

#### 3.3 错误分析仪表板

**文件**: `sql-guard-demo/grafana/dashboards/errors.json`

**包含 Panel**:
1. **错误率时间线**: 每分钟错误数量
2. **错误分类饼图**: 语法错误、约束冲突、死锁、超时
3. **Top 错误消息表格**: 最频繁的错误消息及其出现次数

---

### 4. 负载生成器

**目标**: 模拟真实的 SQL 工作负载

**实现要点**:

**文件**: `sql-guard-demo/src/main/java/com/footstone/sqlguard/demo/load/LoadGenerator.java`

```java
@Component
public class LoadGenerator {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AuditScenarioMapper scenarioMapper;

    /**
     * 运行 5 分钟负载生成
     * - 80% 快查询 (<100ms)
     * - 15% 慢查询 (>1s)
     * - 5% 错误查询
     */
    public void run() {
        long endTime = System.currentTimeMillis() + 5 * 60 * 1000; // 5分钟

        Random random = new Random();

        while (System.currentTimeMillis() < endTime) {
            int scenario = random.nextInt(100);

            if (scenario < 80) {
                // 80%: 快查询
                userMapper.selectById(random.nextInt(1000));
            } else if (scenario < 95) {
                // 15%: 慢查询
                scenarioMapper.slowQuery();
            } else {
                // 5%: 错误查询
                try {
                    scenarioMapper.invalidSql();
                } catch (Exception e) {
                    // 预期错误
                }
            }

            // 控制 QPS
            Thread.sleep(10); // ~100 QPS
        }
    }
}
```

**启动方式**:
```bash
curl -X POST http://localhost:8080/api/load-generator/start
```

---

### 5. 演示步骤 README

**目标**: 用户 5 分钟快速上手

**实现要点**:

**文件**: `sql-guard-demo/README-AUDIT-DEMO.md`

```markdown
# SqlGuard Audit Platform Demo

## 快速开始

### 1. 启动完整栈 (1分钟)

```bash
cd sql-guard-demo
docker-compose up -d
```

等待所有服务健康检查通过 (~30秒)

### 2. 运行负载生成器 (5分钟)

```bash
curl -X POST http://localhost:8080/api/load-generator/start
```

负载生成器将运行 5 分钟，生成：
- 48,000 次快查询 (80%)
- 9,000 次慢查询 (15%)
- 3,000 次错误查询 (5%)

### 3. 查看 Grafana 仪表板

打开浏览器访问：http://localhost:3000
- 用户名: admin
- 密码: admin

查看 3 个仪表板：
1. **Risk Overview**: 风险概览、Top 10 高风险 SQL
2. **Performance**: 延迟趋势、最慢查询
3. **Errors**: 错误率、错误分类

### 4. 查询审计服务 REST API

```bash
# 获取最近的 CRITICAL 发现
curl http://localhost:8090/api/audit/findings?severity=CRITICAL&limit=10

# 获取仪表板统计数据
curl http://localhost:8090/api/audit/statistics/dashboard

# 获取慢查询 Top 10
curl http://localhost:8090/api/audit/slow-queries?limit=10
```

### 5. 查询 ClickHouse 原始数据

```bash
docker exec -it sql-guard-demo-clickhouse-1 clickhouse-client

# 查询审计日志
SELECT sql, severity, risk_score, execution_time
FROM audit.audit_logs
WHERE severity = 'CRITICAL'
ORDER BY timestamp DESC
LIMIT 10;
```

## 审计场景演示

### 场景 1: 慢查询检测

```bash
curl http://localhost:8080/api/audit-scenarios/slow-query
```

**预期结果**:
- 查询耗时 5 秒
- SlowQueryChecker 生成 HIGH 严重度审计日志
- Grafana 性能仪表板显示延迟飙升

### 场景 2: 无WHERE更新检测

```bash
curl -X POST http://localhost:8080/api/audit-scenarios/missing-where
```

**预期结果**:
- 更新 1000+ 行
- ActualImpactNoWhereChecker 生成 CRITICAL 严重度审计日志
- Grafana 风险仪表板显示高风险 SQL

### 场景 3: 错误率飙升

```bash
for i in {1..100}; do
  curl http://localhost:8080/api/audit-scenarios/error-sql
done
```

**预期结果**:
- ErrorRateChecker 聚合 100 次错误
- Grafana 错误仪表板显示错误率飙升

### 场景 4: 深度分页滥用

```bash
curl http://localhost:8080/api/audit-scenarios/deep-pagination
```

**预期结果**:
- DeepPaginationChecker 检测 offset 10000
- 生成 MEDIUM 严重度审计日志

## 故障排查

### 查看服务日志

```bash
# Demo 应用日志
docker logs sql-guard-demo-demo-app-1

# 审计服务日志
docker logs sql-guard-demo-audit-service-1

# Kafka 消费者日志
docker logs sql-guard-demo-kafka-1
```

### 健康检查

```bash
# Demo 应用
curl http://localhost:8080/actuator/health

# 审计服务
curl http://localhost:8090/actuator/health
```

## 清理环境

```bash
docker-compose down -v
```
```

---

## 实现要求

### 1. 扩展 Demo 应用

**核心功能**:
- ✅ 启用审计日志配置（application-audit.yml）
- ✅ 创建 4 个审计场景 Mapper + Controller
- ✅ 实现负载生成器（80%/15%/5% 分布）
- ✅ 添加健康检查端点

### 2. Docker Compose 栈

**核心功能**:
- ✅ 8 个服务编排（demo-app, mysql, zookeeper, kafka, audit-service, postgres, clickhouse, grafana）
- ✅ 网络配置（sqlguard-net）
- ✅ 卷挂载（数据持久化）
- ✅ 依赖顺序（depends_on）

### 3. Grafana 仪表板

**核心功能**:
- ✅ 3 个仪表板 JSON 文件
- ✅ 数据源配置（Audit Service API + ClickHouse）
- ✅ 自动 provisioning

### 4. 负载生成器

**核心功能**:
- ✅ 5 分钟运行时间
- ✅ 80%/15%/5% SQL 分布
- ✅ ~100 QPS 吞吐量
- ✅ 足够数据生成用于审计分析

### 5. README 文档

**核心功能**:
- ✅ 快速开始步骤（<5分钟）
- ✅ 4 个场景演示命令
- ✅ 故障排查指南

---

## 验收标准

### 功能验收
- [ ] Demo 应用启动成功（docker-compose up）
- [ ] 所有 8 个服务健康检查通过
- [ ] 4 个审计场景可触发并生成审计日志
- [ ] 负载生成器达到目标分布（80%/15%/5%）
- [ ] Grafana 仪表板正确显示数据
- [ ] REST API 查询返回正确结果
- [ ] ClickHouse 查询返回审计日志

### 测试验收

#### Demo功能TDD (DemoApplicationTest - 15 tests)
- [ ] `testDemo_fullStack_shouldStart()` - 完整栈启动成功
- [ ] `testDemo_slowQueryScenario_shouldDetect()` - 慢查询场景检测
- [ ] `testDemo_missingWhereScenario_shouldDetect()` - 无WHERE场景检测
- [ ] `testDemo_errorRateScenario_shouldAggregate()` - 错误率场景聚合
- [ ] `testDemo_paginationAbuseScenario_shouldDetect()` - 分页滥用检测
- [ ] `testDemo_loadGenerator_shouldProduceEvents()` - 负载生成器产生事件
- [ ] `testDemo_loadGenerator_80percent_fast_shouldMaintain()` - 80%快查询
- [ ] `testDemo_loadGenerator_15percent_slow_shouldMaintain()` - 15%慢查询
- [ ] `testDemo_loadGenerator_5percent_error_shouldMaintain()` - 5%错误查询
- [ ] `testDemo_auditService_shouldConsumeEvents()` - 审计服务消费事件
- [ ] `testDemo_grafanaDashboard_shouldDisplay()` - Grafana仪表板显示
- [ ] `testDemo_restAPI_shouldRespond()` - REST API响应
- [ ] `testDemo_clickHouseQuery_shouldWork()` - ClickHouse查询工作
- [ ] `testDemo_dockerCompose_allHealthy_shouldVerify()` - Docker Compose健康检查
- [ ] `testDemo_README_walkthrough_shouldExecute()` - README步骤可执行

#### Grafana Dashboard验证TDD (GrafanaDashboardValidationTest - 8 tests)
- [ ] `testDashboard_riskOverview_shouldRender()` - 风险概览渲染
- [ ] `testDashboard_riskOverview_pieChart_shouldShowSeverity()` - 饼图显示严重度
- [ ] `testDashboard_riskOverview_table_shouldShowTop10()` - 表格显示Top10
- [ ] `testDashboard_performance_lineChart_shouldShowP95P99()` - 折线图显示p95/p99
- [ ] `testDashboard_performance_barChart_shouldShowSlowest()` - 条形图显示最慢
- [ ] `testDashboard_errors_rateChart_shouldShowTimeline()` - 错误率时间线
- [ ] `testDashboard_errors_categoryChart_shouldShowDistribution()` - 错误分类分布
- [ ] `testDashboard_dataSource_shouldConnect()` - 数据源连接成功

#### 负载测试验证TDD (LoadGeneratorValidationTest - 7 tests)
- [ ] `testLoadGenerator_5minutes_shouldComplete()` - 5分钟完成
- [ ] `testLoadGenerator_distribution_shouldMatch()` - 分布匹配目标
- [ ] `testLoadGenerator_throughput_shouldMeetTarget()` - 吞吐量达标
- [ ] `testLoadGenerator_diversity_shouldGenerateVariedSQL()` - SQL多样性
- [ ] `testLoadGenerator_auditData_shouldBeSufficient()` - 审计数据充足
- [ ] `testLoadGenerator_jmeter_shouldExecute()` - JMeter执行（可选）
- [ ] `testLoadGenerator_customScript_shouldExecute()` - 自定义脚本执行

### 集成验收
- [ ] 完整演示流程可复现（启动 → 负载 → 查看）
- [ ] 所有审计场景正确检测
- [ ] Grafana 仪表板数据准确
- [ ] README 步骤 100% 可执行

### 代码质量验收
- [ ] Docker Compose 文件语法正确
- [ ] Grafana JSON 文件格式正确
- [ ] 负载生成器线程安全
- [ ] 异常处理完善
- [ ] 日志输出清晰

### 构建验收
- [ ] docker-compose up 成功启动
- [ ] 测试通过（30 tests, 0 failures）
- [ ] BUILD SUCCESS

---

## 输出文件

### 新增文件 (20+ 个)

**Demo 应用扩展 (5)**:
1. `sql-guard-demo/src/main/resources/application-audit.yml`
2. `sql-guard-demo/src/main/resources/mapper/AuditScenarioMapper.xml`
3. `sql-guard-demo/src/main/java/com/footstone/sqlguard/demo/controller/AuditScenarioController.java`
4. `sql-guard-demo/src/main/java/com/footstone/sqlguard/demo/mapper/AuditScenarioMapper.java`
5. `sql-guard-demo/src/main/java/com/footstone/sqlguard/demo/load/LoadGenerator.java`

**Docker Compose (1)**:
1. `sql-guard-demo/docker-compose.yml`

**Grafana 配置 (6)**:
1. `sql-guard-demo/grafana/dashboards/risk-overview.json`
2. `sql-guard-demo/grafana/dashboards/performance.json`
3. `sql-guard-demo/grafana/dashboards/errors.json`
4. `sql-guard-demo/grafana/datasources/audit-service.yml`
5. `sql-guard-demo/grafana/datasources/clickhouse.yml`
6. `sql-guard-demo/grafana/provisioning/dashboards.yml`

**文档 (1)**:
1. `sql-guard-demo/README-AUDIT-DEMO.md`

**测试文件 (3)**:
1. `sql-guard-demo/src/test/java/com/footstone/sqlguard/demo/DemoApplicationTest.java`
2. `sql-guard-demo/src/test/java/com/footstone/sqlguard/demo/GrafanaDashboardValidationTest.java`
3. `sql-guard-demo/src/test/java/com/footstone/sqlguard/demo/LoadGeneratorValidationTest.java`

---

## 依赖与限制

### 输入依赖
- ✅ Phase 8 Output: Audit API + Audit Writers
- ✅ Phase 9 Output: Audit Checkers (SlowQueryChecker, ActualImpactNoWhereChecker, ErrorRateChecker)
- ✅ Phase 10 Output: Audit Service (完整实现)
- ✅ Phase 7 Output: sql-guard-demo (现有 demo 应用)

### 限制
- ⚠️ 需要 Docker 和 docker-compose 环境
- ⚠️ 需要 8GB+ 内存运行完整栈
- ⚠️ Grafana 仪表板需要手动导入（或使用 provisioning）
- ⚠️ 负载生成器可能需要调优以达到目标分布

---

## 注意事项

### 1. Docker Compose 服务启动顺序

**问题**: 服务依赖关系可能导致启动失败

**解决方案**: 使用 healthcheck 和 depends_on

```yaml
audit-service:
  depends_on:
    kafka:
      condition: service_healthy
    postgres:
      condition: service_healthy
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8090/actuator/health"]
    interval: 10s
    timeout: 5s
    retries: 5
```

---

### 2. Grafana 数据源配置

**问题**: Grafana 如何连接审计服务和 ClickHouse？

**解决方案**: 使用 provisioning

**文件**: `sql-guard-demo/grafana/datasources/audit-service.yml`

```yaml
apiVersion: 1

datasources:
  - name: Audit Service API
    type: infinity
    access: proxy
    url: http://audit-service:8090/api
    isDefault: false
    jsonData:
      method: GET
```

**文件**: `sql-guard-demo/grafana/datasources/clickhouse.yml`

```yaml
apiVersion: 1

datasources:
  - name: ClickHouse
    type: clickhouse
    access: proxy
    url: http://clickhouse:8123
    isDefault: false
    jsonData:
      database: audit
```

---

### 3. 负载生成器分布控制

**问题**: 如何精确控制 80%/15%/5% 分布？

**解决方案**: 使用随机数分段

```java
int scenario = random.nextInt(100);

if (scenario < 80) {
    // 0-79: 80% 快查询
    executeFastQuery();
} else if (scenario < 95) {
    // 80-94: 15% 慢查询
    executeSlowQuery();
} else {
    // 95-99: 5% 错误查询
    executeErrorQuery();
}
```

**验证方法**: 统计实际执行次数

```java
int fastCount = 0, slowCount = 0, errorCount = 0;
// ... 执行负载
double fastPercent = (double) fastCount / total * 100;
// 验证: 79% < fastPercent < 81%
```

---

### 4. Grafana 仪表板 JSON 生成

**问题**: 手写 Grafana JSON 复杂且容易出错

**解决方案**: 使用 Grafana UI 创建原型，导出 JSON，再调整

**步骤**:
1. 启动 Grafana
2. 手动创建仪表板原型
3. 导出 JSON: Dashboard Settings → JSON Model
4. 调整 datasource UID 和变量
5. 保存为 provisioning 文件

---

### 5. 审计数据生成速度

**问题**: 5 分钟负载生成数据是否足够分析？

**计算**:
- QPS: 100
- 总查询数: 100 * 60 * 5 = 30,000
- 慢查询: 30,000 * 15% = 4,500
- 错误查询: 30,000 * 5% = 1,500

**结论**: 足够生成有意义的审计数据和统计趋势

---

### 6. Docker Compose 网络配置

**问题**: 服务间如何通信？

**解决方案**: 使用自定义网络

```yaml
networks:
  sqlguard-net:
    driver: bridge
```

**服务连接**:
- Demo 应用 → Kafka: `kafka:9092`
- Audit Service → PostgreSQL: `postgres:5432`
- Grafana → Audit Service: `http://audit-service:8090`

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_14_Audit_Examples_Documentation/Task_14_1_Demo_Application_Implementation.md
```

**Required Log Sections**:
- Summary: Audit-Enhanced Demo implementation outcome
- Details: Docker Compose栈、4个审计场景、Grafana仪表板、负载生成器
- Output: List of created files (20+ files), test results (30 tests)
- Issues: Docker启动问题、负载分布偏差、Grafana连接问题或"None"
- Important Findings: 负载生成器性能、审计数据充足性、仪表板实用性
- Next Steps: 用户文档更新、性能优化、更多场景添加

---

## 执行时间线

- **预计时间**: 5 工作日
  - Day 1 上午：扩展 Demo 应用（审计配置 + 场景 Mapper）
  - Day 1 下午：实现负载生成器
  - Day 2 上午：创建 Docker Compose 栈（8服务编排）
  - Day 2 下午：Grafana 仪表板设计（3个仪表板）
  - Day 3 上午：编写 Demo 功能测试（15 tests）
  - Day 3 下午：编写 Grafana 仪表板测试（8 tests）
  - Day 4 上午：编写负载生成器测试（7 tests）
  - Day 4 下午：集成测试和故障排查
  - Day 5：README 文档编写和完整演示验证

---

**Created**: 2025-12-22
**Phase**: 14 - Audit Platform Examples & Documentation
**Task ID**: 14.1
**Priority**: High（最复杂任务）
**Parallel**: Can run in parallel with Task 14.2, 14.3, 14.4
