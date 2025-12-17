# SQL审计平台完整设计方案

**最后更新**: 2024-12-17
**版本**: 2.0
**状态**: Phase 1完成，Phase 2完成

---

## 项目概述

### 目标
构建企业级SQL审计平台，支持公司数百个Java项目的SQL安全与性能监控。

### 核心特性
- ✅ 纯Java技术栈（业务层Java 8+，审计服务Java 21）
- ✅ 三层Checker架构（执行前防御 + 执行后审计）
- ✅ 标准JSON日志输出
- ✅ 多层级配置开关（全局 > 层级 > Checker）
- ✅ TDD开发方法论（先设计测试矩阵，再实现）
- ✅ 渐进式架构演进（SQLite → PostgreSQL → ClickHouse）

---

## Phase 1总结：需求理解与架构设计

### 1.1 用户需求（中文原文）

**初始需求**：
- "使用druid、mybatis把sql技术都打印出来，统一审计一下"
- "只需要sql写到日志文件即可，有专门的日志收集软件"
- "本地用sqlite测试，还是要支持更大数据库的，公司几百个项目"

**关键约束**：
- 对用户侵入最小（仅需配置开启日志）
- 要对用户侵入少

**关键洞察（用户深度思考）**：
> "你还需要深入思考，druid的拦截规则，是在SQL执行前，现在做的这个基于日志的是在SQL执行后。所以我们对Checker要从第一性原则出发，更深层次的分类和使用"

这个洞察彻底改变了架构方向。

### 1.2 核心架构决策

#### 决策1：纯Java方案（非Golang）

**理由**：
- 直接引用sql-guard-core JAR包，无HTTP开销
- 技术栈统一，降低维护成本
- 代码复用度最高（静态扫描器、运行时拦截器、审计服务共享规则）
- 开发周期更短（2-3周 vs 4-6周）

#### 决策2：不依赖Archery作为主审计引擎

**分析**：
- Archery是**执行前审批工作流**，不适合**执行后审计分析**
- Archery的goInception只支持MySQL，我们需要多数据库支持
- Archery缺少批量处理API

**方案**：SQL Guard作为主引擎，可选择将CRITICAL级别SQL推送到Archery供DBA人工复审。

#### 决策3：Java 21用于审计服务

**理由**：
- 审计服务与业务无关，可用新版本
- Virtual Threads支持10000+ msg/s吞吐
- Record类简化数据模型
- Pattern Matching增强代码可读性

### 1.3 三层Checker架构（最关键）

这是整个设计的核心创新点。

```
┌─────────────────────────────────────────────────────┐
│          sql-guard-core（共享基础层）                 │
│  - RuleChecker接口                                   │
│  - AbstractRuleChecker（工具方法）                   │
│  - JSqlParser封装                                    │
│  - 配置模型                                          │
└─────────────────────────────────────────────────────┘
           ↓                              ↓
┌──────────────────────────┐   ┌──────────────────────────┐
│   sql-guard-runtime      │   │    sql-guard-audit       │
│   （执行前防御层）        │   │    （执行后审计层）       │
│                          │   │                          │
│  使用场景：               │   │  使用场景：               │
│  - Druid WallFilter      │   │  - sql-audit-service     │
│  - MyBatis拦截器         │   │                          │
│  - MyBatis-Plus拦截器    │   │  可用信息：               │
│                          │   │  - SQL文本+参数          │
│  可用信息：               │   │  - execution_time        │
│  - 仅SQL文本+参数        │   │  - rows_affected         │
│                          │   │  - rows_examined         │
│  设计原则：               │   │  - success/error         │
│  ⚡ 性能要求：<5ms        │   │                          │
│  🚫 决策模式：二元        │   │  设计原则：               │
│     (阻止/允许)          │   │  🔍 性能要求：宽松        │
│  🎯 目标：防止危险SQL     │   │     (<1000ms)           │
│                          │   │  📊 决策模式：多维度评分  │
│  核心Checkers（15+）：    │   │     (LOW/MEDIUM/HIGH/    │
│  P0: 强制参数化、注释检测 │   │      CRITICAL)          │
│  P1: SELECT *、UNION      │   │  🎯 目标：发现问题、      │
│  P2: 危险语句类型         │   │     性能分析、行为建模   │
│  P3: 危险函数、表黑名单   │   │                          │
│                          │   │  核心Checkers（12+）：    │
└──────────────────────────┘   │  P0: 慢查询、全表扫描     │
                               │  P1: 缺失索引、大结果集   │
                               │  P2: 频率异常、访问模式   │
                               └──────────────────────────┘
```

### 1.4 执行前 vs 执行后对比

| 维度 | 执行前（Runtime） | 执行后（Audit） |
|------|------------------|----------------|
| **时机** | SQL提交到数据库前 | SQL执行完成后 |
| **可用数据** | SQL文本、参数 | + 执行时间、影响行数、扫描行数、成功/失败 |
| **性能要求** | 严格（<5ms） | 宽松（可复杂分析） |
| **决策模式** | 二元（阻止/允许） | 多维度评分 |
| **主要目标** | 防止危险SQL执行 | 发现问题、优化建议、行为分析 |
| **误判代价** | 高（阻止正常业务） | 低（仅产生告警） |

**核心示例**（NoWhereClauseChecker）：

```java
// 执行前：二元决策
if (extractWhere(stmt) == null) {
    result.addViolation(RiskLevel.CRITICAL,
        "SQL缺少WHERE条件，禁止执行",
        "必须添加WHERE条件限制操作范围");
}

// 执行后：基于实际影响的精准评级
if (extractWhere(stmt) == null) {
    int rowsAffected = context.getExecutionResult().getRowsAffected();

    if (rowsAffected == 0) {
        result.addViolation(RiskLevel.LOW,
            "SQL缺少WHERE条件，但未影响任何数据（可能表为空）",
            "建议添加WHERE条件以避免将来误操作");
    } else if (rowsAffected < 10) {
        result.addViolation(RiskLevel.MEDIUM,
            "SQL缺少WHERE条件，实际影响" + rowsAffected + "行数据",
            "建议添加WHERE条件，当前影响较小但存在风险");
    } else if (rowsAffected < 100) {
        result.addViolation(RiskLevel.HIGH,
            "SQL缺少WHERE条件，影响了" + rowsAffected + "行数据",
            "立即检查业务逻辑，添加WHERE条件");
    } else {
        result.addViolation(RiskLevel.CRITICAL,
            "SQL缺少WHERE条件，大规模影响" + rowsAffected + "行数据",
            "严重数据操作，需立即人工审查");
    }
}
```

### 1.5 审计日志格式

**标准JSON格式**：
```json
{
  "timestamp": "2024-12-17T10:30:45.123Z",
  "app": "order-service",
  "sql": "DELETE FROM user_sessions",
  "type": "DELETE",
  "params": null,
  "time_ms": 3258.7,
  "rows": 125000,
  "db_name": "order_db",
  "db_type": "mysql",
  "db_version": "8.0.35",
  "success": true
}
```

### 1.6 多层级配置系统

**配置参考Druid第一性原则**：

```yaml
sql-guard:
  # 全局总开关
  enabled: true

  # 运行时防御层开关
  runtime:
    enabled: true
    timeout-ms: 5
    fail-fast: true
    default-action: warn  # block | warn | allow

    checkers:
      must-parameterized:
        enabled: true
        excluded-patterns:
          - "SELECT 1"
          - "SELECT NOW()"

      no-where-clause:
        enabled: true
        excluded-tables:
          - "sys_config"

  # 审计分析层开关
  audit:
    enabled: true
    async: true
    batch-size: 100

    storage:
      type: postgresql
      retention-days: 90

    checkers:
      slow-query:
        enabled: true
        threshold-ms: 1000
        levels:
          - threshold: 1000
            level: MEDIUM
          - threshold: 3000
            level: HIGH
```

**配置优先级**：全局开关 > 层级开关 > 单个Checker开关

### 1.7 TDD开发方法论

**核心原则**：先设计完整测试矩阵，再实现。

**7维度测试矩阵**：
1. 功能测试（Functional Tests）
2. 边界测试（Boundary Tests）
3. 配置测试（Configuration Tests）
4. 数据库兼容性测试（Database Compatibility Tests）
5. 性能测试（Performance Tests）
6. 集成测试（Integration Tests）
7. 异常测试（Exception Tests）

**示例**（MustParameterizedChecker完整测试矩阵）：

```java
/**
 * MustParameterizedChecker 测试矩阵
 *
 * 1. 功能测试 - 12个
 * 2. 边界测试 - 6个
 * 3. 配置测试 - 5个
 * 4. 数据库兼容性测试 - 4个
 * 5. 性能测试 - 3个
 * 6. 集成测试 - 4个
 * 7. 异常测试 - 3个
 *
 * 总计：37个测试用例
 */
@DisplayName("MustParameterizedChecker - 参数化强制检查器")
class MustParameterizedCheckerTest {

    @Test
    @DisplayName("F01 - 非参数化WHERE条件应该违规")
    void nonParameterizedWhere_shouldViolate() {
        // Given
        String sql = "SELECT * FROM users WHERE id = 1001";

        // When
        ValidationResult result = check(sql);

        // Then
        assertCriticalViolation(result, "参数化");
    }

    // ... 其余36个测试
}
```

### 1.8 渐进式架构演进

**阶段1：测试环境（2-3个项目）**
- 数据采集：应用 → sql-audit.log
- 数据处理：定时任务读取日志文件
- 存储：SQLite
- 展示：简单Web界面

**阶段2：小规模生产（10-20个项目）**
- 数据采集：应用 → sql-audit.log → Filebeat → Kafka
- 数据处理：sql-audit-service消费Kafka
- 存储：PostgreSQL
- 展示：Spring Boot Admin + 简单报表

**阶段3：大规模生产（100+项目）**
- 数据采集：应用 → sql-audit.log → Filebeat → Kafka
- 数据处理：sql-audit-service集群（多实例并行消费）
- 存储：ClickHouse（时序分析）+ PostgreSQL（元数据）
- 展示：Grafana仪表盘 + 自定义报表

---

## Phase 2：详细实施计划

### 总体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     业务应用层                               │
│  Spring Boot App (Java 8/11/17)                             │
└────────────┬────────────────────────────────────────────────┘
             │
             ├─── Druid DataSource
             │    └─── SqlAuditFilter (输出JSON log)
             │
             ├─── MyBatis Interceptor
             │    └─── SqlAuditInterceptor (输出JSON log)
             │
             ├─── MyBatis-Plus InnerInterceptor
             │    └─── MpSqlAuditInterceptor (输出JSON log)
             │
             └─── P6Spy Listener
                  └─── P6SpySqlAuditListener (输出JSON log)
                           │
                           ↓
┌─────────────────────────────────────────────────────────────┐
│            日志文件层 (sql-audit.log)                        │
│  {"timestamp":"2024-12-17T10:30:45.123Z","app":"order-     │
│   service","sql":"SELECT * FROM orders WHERE id = ?",       │
│   "type":"SELECT","params":["1001"],"time_ms":45.8,...}     │
└────────────┬────────────────────────────────────────────────┘
             │
             ↓ Filebeat/Fluentd
             │
┌────────────┴────────────────────────────────────────────────┐
│                 Kafka 消息队列                               │
│  Topic: sql-audit                                           │
└────────────┬────────────────────────────────────────────────┘
             │
             ↓
┌─────────────────────────────────────────────────────────────┐
│      sql-audit-service (Java 21 + Virtual Threads)          │
│  Kafka Consumer → Audit Engine → Storage → Alert            │
└─────────────────────────────────────────────────────────────┘
```

### 实施阶段划分

#### **阶段1：基础设施建设（4天）**

**Task 1.1: 多层级配置系统设计与实现（1天）**
- 输出：SqlGuardConfig、RuntimeConfig、AuditConfig类
- TDD测试：三层开关逻辑、配置加载、动态更新

**Task 1.2: AbstractRuntimeChecker基类（1天）**
- 输出：Runtime Checker的抽象基类
- 核心能力：<5ms性能要求、fail-fast机制

**Task 1.3: AbstractAuditChecker基类（1天）**
- 输出：Audit Checker的抽象基类
- 核心能力：访问ExecutionResult、多维度评分

**Task 1.4: 保留缓冲（1天）**

#### **阶段2：Runtime Checkers实现（20天，可2人并行10天）**

**P0 - 安全关键（4个Checker，6天）**
1. MustParameterizedChecker（2天，37测试）
2. CommentDetectionChecker（1.5天）
3. MultiStatementChecker（1.5天）
4. RuntimeNoWhereClauseChecker（1天）

**P1 - 数据保护（5个Checker，7天）**
5. SelectAllColumnsChecker（1.5天）
6. UnionQueryChecker（1.5天）
7. SelectIntoOutfileChecker（1天）
8. TruncateTableChecker（1天）
9. DropTableChecker（1天）

**P2 - 操作限制（3个Checker，2.5天）**
10. DangerousStatementTypeChecker（1.5天）
11. RuntimeDummyConditionChecker（0.5天）
12. RuntimeBlacklistFieldChecker（0.5天）

**P3 - 精细控制（3个Checker，4.5天）**
13. DangerousFunctionChecker（2天）
14. TableBlacklistChecker（1天）
15. ReadOnlyTableChecker（1天）

#### **阶段2.5：Audit Log输出层（12.5天，可2人并行6.5天）**

**Task 2.5.1: AuditLogWriter接口设计（1天）**
- 输出：统一的日志写入接口
- 数据模型：SqlAuditLog Record类

**Task 2.5.2: Logback异步Appender（1天）**
- 输出：高性能异步日志输出
- 性能目标：<100ms写入1000条

**Task 2.5.3: Druid SqlAuditFilter（3天）**
- 输出：Druid拦截器实现
- 功能：拦截+输出JSON日志

**Task 2.5.4: MyBatis SqlAuditInterceptor（2天）**
- 输出：MyBatis拦截器实现

**Task 2.5.5: MyBatis-Plus Audit InnerInterceptor（2天）**
- 输出：MyBatis-Plus拦截器实现

**Task 2.5.6: P6Spy Audit Listener（1.5天，可选）**
- 输出：通用JDBC拦截方案

**Task 2.5.7: 日志格式验证测试（2天）**
- 验证：所有拦截器输出格式一致

#### **阶段3：Audit Checkers实现（26天，可2人并行13天）**

**P0 - 高价值审计（4个Checker，9天）**
1. SlowQueryChecker（2天，35测试）
2. ActualImpactNoWhereChecker（2天）
3. FullTableScanChecker（2天）
4. ErrorRateChecker（3天）

**P1 - 性能优化（4个Checker，7天）**
5. IndexMissingChecker（2天）
6. LargeResultSetChecker（1.5天）
7. MustParameterizedAuditChecker（2天）
8. SelectAllColumnsAuditChecker（1.5天）

**P2 - 行为分析（4个Checker，10天）**
9. FrequencyAnomalyChecker（3天）
10. DataAccessPatternChecker（3天）
11. ZeroImpactQueryChecker（1.5天）
12. SensitiveDataAccessChecker（2天）

#### **阶段4：sql-audit-service实现（13天，Java 21）**

**Task 4.1: 项目基础架构（2天）**
- Spring Boot 3.2+ + Java 21

**Task 4.2: Kafka消费者（Virtual Threads）（3天）**
- 吞吐量目标：10000 msg/s

**Task 4.3: 审计引擎实现（2天）**
- 集成所有Audit Checkers

**Task 4.4: 存储层实现（3天）**
- PostgreSQL（元数据）
- ClickHouse（时序数据）

**Task 4.5: REST API实现（3天）**
- 查询、统计、报表API

#### **阶段5：兼容性迁移（4天）**

**Task 5.1: 兼容层维护（2天）**
- 现有代码100%可用
- @Deprecated标记

**Task 5.2: 迁移文档编写（2天）**
- 详细的迁移指南

#### **阶段6：集成测试与文档（13天）**

**Task 6.1: 端到端集成测试（5天）**
- 完整流程测试

**Task 6.2: 性能测试（3天）**
- Runtime Checker: <5ms
- Audit Service: 10000 msg/s

**Task 6.3: 用户文档（5天）**
- 快速开始、配置参考、最佳实践

---

## 总工作量估算

| 阶段 | 任务数 | 1人工作量 | 2人工作量 |
|------|--------|----------|----------|
| 阶段1：基础设施 | 3个Task | 4天 | 2天 |
| 阶段2：Runtime Checkers | 15个Task | 20天 | 10天 |
| 阶段2.5：Audit Log输出层 | 7个Task | 12.5天 | 6.5天 |
| 阶段3：Audit Checkers | 12个Task | 26天 | 13天 |
| 阶段4：sql-audit-service | 5个Task | 13天 | 7天 |
| 阶段5：兼容性迁移 | 2个Task | 4天 | 2天 |
| 阶段6：测试与文档 | 3个Task | 13天 | 7天 |
| **总计** | **47个Task** | **92.5天** | **47.5天** |

**推荐配置**：2人团队，预计**7-8周**完成

---

## 关键技术决策记录

### 1. 为什么分离Runtime和Audit Checkers？

**问题**：用户洞察指出"druid的拦截规则是在SQL执行前，现在做的这个基于日志的是在SQL执行后"

**分析**：
- 执行前：只有SQL文本，必须严格（误杀影响业务）
- 执行后：有执行结果，可以精准评级（0行影响 vs 1000行影响）

**决策**：完全分离两层Checker实现，不复用代码

**影响**：开发量增加（15+12=27个Checker），但准确性大幅提升

### 2. 为什么审计服务用Java 21？

**问题**：新版本Java可能存在兼容性风险

**分析**：
- 审计服务与业务完全解耦（消费Kafka，不直接调用）
- Virtual Threads对高并发Kafka消费性能提升显著
- Record类简化数据模型，减少样板代码

**决策**：业务层Java 8兼容，审计服务Java 21

**影响**：需要维护两套Java版本，但性能和代码质量提升明显

### 3. 为什么不用Golang？

**问题**：goInception性能可能更好

**用户反驳**："那使用golang还有什么意义，直接使用java消费得了"

**分析**：
- 引入新语言增加维护成本
- Java直接引用JAR包，代码复用度100%
- Golang需要HTTP调用Java validator，性能反而更差

**决策**：纯Java方案

**影响**：技术栈统一，开发效率提升

---

## 成功标准

### 功能完整性
- ✅ 15个Runtime Checkers全部实现且测试覆盖>95%
- ✅ 12个Audit Checkers全部实现且测试覆盖>95%
- ✅ 4种拦截器（Druid/MyBatis/MP/P6Spy）全部输出标准JSON
- ✅ sql-audit-service正常消费Kafka并存储结果

### 性能指标
- ✅ Runtime Checker单次检查<5ms
- ✅ Audit Service吞吐量>10000 msg/s
- ✅ Virtual Threads并发能力>100000

### 兼容性
- ✅ 现有代码100%可用（零破坏性变更）
- ✅ 支持Java 8/11/17/21
- ✅ 支持MySQL/PostgreSQL/Oracle/SQL Server

### 文档完整性
- ✅ 用户文档（快速开始、配置参考、FAQ）
- ✅ 开发文档（架构设计、扩展指南）
- ✅ 迁移文档（从旧版本到新版本）

---

## 风险与应对

### 风险1：开发周期超期

**概率**：中
**影响**：高
**应对**：
- 2人并行开发（关键路径从92天降到47天）
- 每周review进度，及时调整优先级
- P3级别Checker可延后（不影响核心功能）

### 风险2：性能不达标

**概率**：低
**影响**：高
**应对**：
- 每个Checker都有性能测试
- 持续benchmark，发现问题立即优化
- 预留性能优化专项时间（阶段6）

### 风险3：兼容性问题

**概率**：中
**影响**：中
**应对**：
- 兼容层保持现有代码可用
- 多版本测试（Java 8/11/17/21）
- 渐进式迁移策略（可选升级）

---

## 下一步行动

1. ✅ **Phase 1完成**：需求理解与架构设计
2. ✅ **Phase 2完成**：详细任务分解与计划创建
3. ⏳ **Phase 3（可选）**：系统化审查
4. ⏳ **Phase 4**：计划优化与最终确认
5. ⏳ **开始实施**：从阶段1-基础设施开始

**准备进入Phase 3还是直接开始实施？**
