# SQL Guard 审计日志架构分析

## 执行摘要

**核心理念**: 测试环境 + 完整测试用例覆盖 → 所有 SQL 都会被执行和记录
**设计原则**: Audit Log 写入**简单、高效**，事后分析**深度、灵活**

---

## 1. 当前 Audit Log 架构

### 1.1 写入流程（Runtime）

```
SQL 执行
    ↓
SQL Guard 拦截器 (MyBatis/MyBatis-Plus/JDBC)
    ├─ 安全验证 (11个检查器)
    ├─ ValidationResult → ThreadLocal
    └─ AuditLogWriter.writeAuditLog(event)
        ↓
    LogbackAuditWriter
        ├─ objectMapper.writeValueAsString(event)  // JSON序列化
        └─ AUDIT_LOGGER.info(json)                  // 写入日志
            ↓
    Logback AsyncAppender
        ├─ 8192容量异步队列
        ├─ <1ms p99 延迟
        └─ >10,000 events/sec 吞吐
            ↓
    RollingFileAppender
        ├─ logs/audit/current/audit.log
        ├─ 按小时/100MB 滚动
        └─ 保留30天 (720小时)
```

**性能特征**:
- ✅ **Write latency**: <1ms p99 (异步队列)
- ✅ **Throughput**: >10,000 events/sec
- ✅ **Overhead**: <1% SQL执行时间
- ✅ **Non-blocking**: AsyncAppender 不阻塞业务线程

### 1.2 Audit Log 格式

**JSON Lines 格式** (每行一个JSON对象):

```json
{"sqlId":"8c830ce1...","sql":"SELECT * FROM user WHERE status = ?","sqlType":"SELECT","executionLayer":"MYBATIS","statementId":"com.footstone.sqlguard.demo.mapper.UserMapper.findByStatusOnly","datasource":"MybatisSqlSessionFactoryBean","params":{"param1":"ACTIVE","status":"ACTIVE"},"executionTimeMs":66,"rowsAffected":0,"errorMessage":null,"timestamp":"2026-01-04T08:02:53.035527Z","violations":{"passed":false,"riskLevel":"HIGH","violations":[{"riskLevel":"HIGH","message":"WHERE条件只包含黑名单字段[status],条件过于宽泛","suggestion":null},{"riskLevel":"HIGH","message":"SELECT查询条件只有黑名单字段[status]且无分页,可能返回大量数据","suggestion":"添加业务字段条件或分页限制"}],"details":{}}}
```

**字段说明**:
- `sqlId`: SQL指纹 (MD5)
- `sql`: 完整SQL语句
- `sqlType`: SELECT/INSERT/UPDATE/DELETE
- `executionLayer`: MYBATIS/MYBATIS_PLUS/JDBC
- `statementId`: Mapper方法ID 或 JDBC标识
- `params`: 参数映射
- `executionTimeMs`: 执行耗时
- `rowsAffected`: 影响行数
- `violations`: 验证结果 (passed, riskLevel, violations[])
- `timestamp`: ISO-8601 时间戳

### 1.3 文件滚动策略

```
logs/audit/
├── current/
│   └── audit.log              # 当前写入文件
├── 2026-01-04/
│   ├── audit.2026-01-04-08.0.log   # 8:00-9:00
│   ├── audit.2026-01-04-09.0.log   # 9:00-10:00
│   └── audit.2026-01-04-10.0.log   # ...
├── 2026-01-03/
│   └── ...
└── 2025-12-05/
    └── ...                    # 30天后自动删除
```

**滚动规则**:
- 每小时滚动一次
- 单文件超过 100MB 时滚动 (.0, .1, .2...)
- 保留最近 30 天 (720 小时)
- 总大小限制 10GB

---

## 2. 为什么这个设计是"简单、高效"的？

### 2.1 简单性分析

| 维度 | 当前设计 | 复杂方案对比 |
|-----|---------|------------|
| **写入路径** | Java → Logback → File | Java → Kafka → Consumer → DB |
| **组件数量** | 3个 (Writer/AsyncAppender/FileAppender) | 6个+ (Producer/Broker/Consumer/DB...) |
| **外部依赖** | 0 (Logback内置) | Kafka/Zookeeper/数据库 |
| **配置复杂度** | 1个XML文件 | Kafka配置+消费者配置+DB配置 |
| **运维复杂度** | 文件滚动+定期清理 | Kafka集群+消费者监控+数据库维护 |
| **故障点** | 1个 (磁盘写满) | 多个 (Kafka宕机/消费者堵塞/DB故障) |

**结论**: 当前设计极简，没有引入额外的中间件依赖。

### 2.2 高效性分析

**写入性能**:
```
SQL Guard拦截器
    ↓ (JSON序列化 <0.5ms)
LogbackAuditWriter
    ↓ (入队列 <0.1ms, 异步不阻塞)
AsyncAppender Queue
    ↓ (后台线程批量写入)
File I/O
```

**性能数据**:
- 业务线程开销: ~0.6ms (序列化 + 入队)
- 异步队列容量: 8192 events
- 吞吐量: >10,000 events/sec
- 对 SQL 执行的影响: <1%

**对比 Kafka 方案**:
| 指标 | Logback AsyncAppender | Kafka Producer |
|-----|---------------------|---------------|
| 业务线程开销 | 0.6ms | 1-5ms |
| 网络IO | 无 | 有 |
| 序列化 | JSON (一次) | JSON + Kafka格式 |
| 失败处理 | 阻塞等待队列 | 重试/超时 |
| 运维复杂度 | 低 | 高 |

**结论**: 文件写入比 Kafka 更简单、更快（无网络开销）。

---

## 3. 测试环境策略

### 3.1 核心思想

**"理论上，在测试环境跑完所有测试用例，所有 SQL 应该都被执行过"**

```
测试环境
    ↓
完整测试用例覆盖
    ├─ 单元测试 (Mapper层)
    ├─ 集成测试 (Service层)
    └─ 端到端测试 (API层)
    ↓
所有 SQL 都被执行
    ↓
audit.log 记录所有 SQL
    ↓
SQL Audit Service 分析
    ├─ 风险评估
    ├─ 性能分析
    └─ 生成报告
```

### 3.2 测试覆盖策略

**测试用例设计**:

```java
// 示例：完整的 UserMapper 测试
@SpringBootTest
public class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    public void testAllSqlStatements() {
        // 触发所有 Mapper 方法
        userMapper.findById(1L);                    // SELECT by ID
        userMapper.findByStatusOnly("ACTIVE");      // SELECT by status (黑名单字段)
        userMapper.findWithDummyCondition();        // SELECT with 1=1
        userMapper.findWithDeepPagination(10000);   // SELECT with OFFSET 10000
        userMapper.findWithLargePageSize(5000);     // SELECT LIMIT 5000
        userMapper.updateWithoutWhere();            // UPDATE without WHERE
        userMapper.deleteAllUnsafe();               // DELETE without WHERE
        // ...
    }
}
```

**覆盖矩阵**:

| Mapper方法 | SQL模式 | 触发的检查器 | 期望结果 |
|-----------|---------|------------|---------|
| findById | SELECT with WHERE | - | PASS |
| findByStatusOnly | SELECT + 黑名单字段 | BlacklistFieldChecker | HIGH |
| findWithDummyCondition | SELECT + 1=1 | DummyConditionChecker | CRITICAL |
| findWithDeepPagination | SELECT + OFFSET 10000 | DeepPaginationChecker | HIGH |
| updateWithoutWhere | UPDATE without WHERE | NoWhereClauseChecker | CRITICAL |
| deleteAllUnsafe | DELETE without WHERE | NoWhereClauseChecker | CRITICAL |

### 3.3 生产环境的作用

**生产环境 Audit Log 主要用于**:

1. ✅ **发现测试未覆盖的边缘情况**
   - 用户输入的特殊组合
   - 动态生成的 SQL（MyBatis动态标签）

2. ✅ **监控运行时风险**
   - 实时风险 SQL 告警
   - 慢查询监控
   - 错误率分析

3. ✅ **审计合规**
   - 历史 SQL 查询
   - 安全审计报告
   - 问题溯源

**不需要在生产环境做**:
- ❌ 大规模 SQL 发现（测试环境已完成）
- ❌ 功能验证（测试环境已覆盖）

---

## 4. SQL Audit Service 的角色

### 4.1 事后分析工具

**输入**: `audit.log` 文件（JSON Lines）

**处理流程**:
```
audit.log (文件)
    ↓
文件读取 / Kafka 消费 (可选)
    ↓
SQL Audit Service
    ├─ 解析 JSON
    ├─ 审计引擎 (11+ checkers 并发执行)
    ├─ 风险评分聚合
    ├─ 数据持久化 (MySQL/ES/ClickHouse)
    └─ REST API 查询
        ↓
    结果输出
        ├─ Top N 风险 SQL
        ├─ 慢查询统计
        ├─ 趋势分析
        └─ 审计报告 (PDF/Excel)
```

### 4.2 SQL Audit Service 提供的价值

**1. 深度分析**（运行时拦截器做不到的）:

```java
// 示例：跨多次执行的统计分析
@Service
public class StatisticsService {

    // 分析某个 SQL 的历史执行情况
    public SqlStatistics analyzeSqlHistory(String sqlId) {
        List<AuditReport> reports = findBySqlId(sqlId);

        return SqlStatistics.builder()
            .totalExecutions(reports.size())
            .avgExecutionTime(calculateAvg(reports, r -> r.getExecutionTimeMs()))
            .maxExecutionTime(calculateMax(reports, r -> r.getExecutionTimeMs()))
            .errorRate(calculateErrorRate(reports))
            .riskTrend(calculateRiskTrend(reports))  // 风险趋势变化
            .build();
    }

    // 检测新出现的 SQL（测试未覆盖）
    public List<String> detectNewSqls(Instant since) {
        // 查找首次出现时间在 since 之后的 SQL
        return auditReportRepository.findNewSqlsSince(since);
    }
}
```

**2. 趋势预测**:

```java
// 示例：预测哪些 SQL 可能成为性能瓶颈
public List<RiskySqlDto> predictPerformanceRisks() {
    // 分析执行时间增长趋势
    return auditReportRepository.findSqlsWithIncreasingExecutionTime();
}
```

**3. 审计报告**:

```
SQL 安全审计报告 (2025-12-05 ~ 2026-01-04)

1. 执行概况
   - 总SQL执行数: 1,234,567
   - 唯一SQL数: 523
   - 平均执行时间: 45ms
   - 错误率: 0.5%

2. 风险分级
   - CRITICAL: 23 条 (4.4%)
   - HIGH: 87 条 (16.6%)
   - MEDIUM: 156 条 (29.8%)
   - LOW: 257 条 (49.1%)

3. Top 10 风险 SQL
   [详细列表...]

4. 慢查询 Top 10
   [详细列表...]

5. 检查器触发统计
   - NoWhereClauseChecker: 23次
   - BlacklistFieldChecker: 87次
   - DeepPaginationChecker: 45次
```

---

## 5. 架构优势总结

### 5.1 当前设计的优势

| 维度 | 优势 | 对比传统方案 |
|-----|------|------------|
| **简单性** | 无外部依赖 | Kafka需要额外部署 |
| **性能** | <1ms异步写入 | Kafka 1-5ms网络开销 |
| **可靠性** | 文件不会丢失 | Kafka需要副本保证 |
| **运维** | 文件滚动+清理 | Kafka集群运维复杂 |
| **调试** | 直接查看文件 | Kafka需要工具消费 |
| **成本** | 磁盘空间 | Kafka集群资源 |

### 5.2 适用场景

**当前架构最适合**:
- ✅ 中小规模应用（QPS < 10,000）
- ✅ 测试环境完整覆盖
- ✅ 审计日志主要用于事后分析
- ✅ 不需要实时流处理

**如果需要升级**:
- ⚠️ 超大规模（QPS > 50,000） → 考虑 Kafka
- ⚠️ 实时流处理需求 → 考虑 Kafka + Flink
- ⚠️ 多数据中心 → 考虑 Kafka

### 5.3 推荐的完整方案

```yaml
# 测试环境配置
test-environment:
  sql-guard:
    enabled: true
    violation-strategy: WARN  # 记录所有违规，不阻断
    audit:
      enabled: true
      writer: logback

  test-coverage:
    strategy: 完整覆盖所有 Mapper 方法
    tools: JUnit + TestContainers

  audit-analysis:
    tool: SQL Audit Service (本地部署)
    action: 生成审计报告 → 修复问题 → 重新测试

# 生产环境配置
production-environment:
  sql-guard:
    enabled: true
    violation-strategy: BLOCK  # 阻断危险 SQL
    audit:
      enabled: true
      writer: logback
      retention: 30 days

  monitoring:
    real-time: SQL Guard 实时拦截
    post-analysis: SQL Audit Service (定期分析)
    alert: 新 SQL 出现告警 (测试未覆盖)
```

---

## 6. 待补充的功能（P0级别）

**回顾**: SQL Guard 运行时拦截器缺少的关键安全功能

### 6.1 SQL注入防护（4项）

1. **MultiStatementChecker** - 多语句检测
2. **UnionInjectionChecker** - UNION注入检测
3. **SqlCommentChecker** - SQL注释检测
4. **IntoOutfileChecker** - 文件操作检测

### 6.2 危险操作检测（4项）

5. **DdlOperationChecker** - DDL操作检测 (CREATE/ALTER/DROP)
6. **TruncateChecker** - TRUNCATE检测
7. **DangerousFunctionChecker** - 危险函数检测 (load_file, sys_exec)

### 6.3 实现建议

**Phase 15: SQL注入防护**
```java
@RuleChecker(id = "multi-statement", riskLevel = CRITICAL)
public class MultiStatementChecker extends AbstractRuleChecker {
    @Override
    public ValidationResult check(Statement statement, SqlContext context) {
        String sql = context.getSql();
        // 检测分号分隔的多语句
        if (sql.matches(".*;\\s*.*;.*")) {
            return fail("检测到多语句执行，可能存在SQL注入风险");
        }
        return pass();
    }
}
```

**优先级**: P0 (关键) - 在不使用 Druid 时无法防御 SQL 注入

---

## 7. 结论

### 7.1 当前架构评价

**Audit Log 写入** (LogbackAuditWriter):
- ✅ **简单**: 无外部依赖，配置简洁
- ✅ **高效**: <1ms异步写入，>10,000 events/sec
- ✅ **可靠**: 文件持久化，30天保留
- ✅ **完全符合"简单、高效"的要求**

**SQL Audit Service**:
- ✅ **事后分析**: 深度统计、趋势预测
- ✅ **灵活存储**: MySQL/ES/ClickHouse
- ✅ **REST API**: 易于集成

### 7.2 核心建议

1. **保持当前 Audit Log 架构** - 简单高效，无需改动
2. **补充 P0 安全功能** - 9项关键检查器（SQL注入+危险操作）
3. **测试环境完整覆盖** - 通过测试用例触发所有 SQL
4. **生产环境监控** - 发现测试未覆盖的边缘情况

### 7.3 最终目标

**SQL Guard = 独立完整的 SQL 安全框架**
- ✅ 业务规则验证（已有11个检查器）
- ⚠️ SQL注入防护（需补充4个检查器）
- ⚠️ 危险操作检测（需补充5个检查器）
- ✅ 灵活组合，不依赖底层连接池
- ✅ 简单高效的审计日志

---

**文档生成时间**: 2026-01-04
**版本**: 1.0
**作者**: SQL Guard Team
