---
phase: 14
assignment_type: parallel
tasks: [14.1, 14.2, 14.3, 14.4]
agent: Agent_Documentation
created: 2025-12-22
---

# Phase 14 并行任务分配

## 概述

Phase 14 的 4 个任务可以完全并行执行，因为它们都依赖已完成的 Phase 1-13 代码，相互之间无阻塞依赖。

## 并行任务清单

### Task 14.1 - Audit-Enhanced Demo Application
**负责人**: Agent_Documentation_Task_14_1
**预计工期**: 5 工作日
**输出**: 扩展 demo + Docker Compose + Grafana + 负载生成器

### Task 14.2 - Production Deployment Guide
**负责人**: Agent_Documentation_Task_14_2
**预计工期**: 4 工作日
**输出**: K8s YAML + 部署文档 + 运维手册

### Task 14.3 - Audit Analysis Best Practices
**负责人**: Agent_Documentation_Task_14_3
**预计工期**: 3 工作日
**输出**: 分析指南 + 风险矩阵 + 修复手册 + 案例研究

### Task 14.4 - API Reference & Developer Documentation
**负责人**: Agent_Documentation_Task_14_4
**预计工期**: 4 工作日
**输出**: Javadoc + 自定义checker教程 + REST API文档 + API示例

---

## 任务分配提示语

### 提示语 1：Task 14.1 - Audit-Enhanced Demo Application

```markdown
你是 Agent_Documentation，负责 Phase 14 Task 14.1 - Audit-Enhanced Demo Application。

**任务目标**：
扩展 sql-guard-demo 应用集成完整的 audit platform，展示双层保护架构（prevention + discovery）。

**核心交付物**：
1. 扩展的 sql-guard-demo（启用所有 Task 8 审计拦截器）
2. Docker Compose 栈：demo app + Kafka + audit service + PostgreSQL + ClickHouse + Grafana
3. 4 个审计场景：慢查询、无WHERE更新、错误率分析、深度分页
4. Grafana 仪表板：风险概览、性能趋势、错误分布
5. 负载生成器：模拟真实SQL工作负载（80%快查询、15%慢查询、5%错误）
6. README with 演示步骤

**技术要点**：
- Demo 架构：Spring Boot + MyBatis/MyBatis-Plus + Druid
- 审计日志配置：启用 SlowQueryChecker, ActualImpactNoWhereChecker, ErrorRateChecker
- Docker Compose：7 个服务编排
- Grafana 数据源：连接 audit service REST API + ClickHouse
- 负载生成器：JMeter 或 Spring Boot 脚本，运行 5 分钟生成足够数据

**测试要求**（30+ tests）：
1. DemoApplicationTest（15 tests）：全栈启动、4个场景检测、负载生成器分布验证
2. GrafanaDashboardValidationTest（8 tests）：仪表板渲染、数据源连接
3. LoadGeneratorValidationTest（7 tests）：吞吐量、SQL多样性、数据充足性

**依赖**：
- Phase 8-10 Output（audit platform 完整实现）
- sql-guard-demo from Phase 7

**验收标准**：
- Demo 启动成功率 100%
- 所有 4 个场景可复现
- Dashboard 数据准确
- 负载生成器达到目标分布

**Memory 日志路径**：
`.apm/Memory/Phase_14_Audit_Examples_Documentation/Task_14_1_Demo_Application_Implementation.md`

请开始实现 Task 14.1，遵循 TDD 流程，先编写测试再实现功能。
```

---

### 提示语 2：Task 14.2 - Production Deployment Guide

```markdown
你是 Agent_Documentation，负责 Phase 14 Task 14.2 - Production Deployment Guide。

**任务目标**：
创建 sql-audit-service 的生产部署指南，涵盖基础设施需求、扩展策略、高可用配置、安全加固、运维手册。

**核心交付物**：
1. 生产部署指南：docs/deployment/production-deployment.md（200+ 行）
2. 资源规格：小/中/大/超大规模的 CPU/内存/存储sizing
3. Kubernetes 部署模板：StatefulSet, ConfigMap, Secret, Service, Ingress
4. 高可用配置：Kafka consumer group, PostgreSQL replication, ClickHouse replication
5. 安全配置：JWT认证、RBAC、TLS加密、凭证管理
6. 监控告警：Prometheus + Grafana dashboard 模板、告警规则
7. 备份恢复：PostgreSQL/ClickHouse 备份策略、灾难恢复手册
8. **运维手册**：docs/operations/troubleshooting-guide.md（故障排查与常见问题）

**技术要点**：
- Sizing 指南：
  - 小规模（<10k SQL/天）：2 CPU / 4GB RAM / 100GB 存储
  - 中等规模（<100k SQL/天）：4 CPU / 8GB RAM / 500GB 存储
  - 大规模（<1M SQL/天）：8 CPU / 16GB RAM / 2TB 存储
- K8s 部署：
  - StatefulSet 用于稳定网络标识（Kafka consumer）
  - 多副本部署（consumer group 协调）
  - Health check 触发 pod 重启
- HA 配置：
  - Kafka consumer lag <1000 messages
  - Processing latency p99 <200ms
  - 自动故障转移
- 安全：
  - API 认证（JWT）
  - 传输加密（TLS）
  - 静态加密（数据库级）
  - PII 参数脱敏

**测试要求**（35+ tests）：
1. KubernetesDeploymentTest（15 tests）：使用 kind/minikube 验证 K8s 部署
2. HighAvailabilityTest（10 tests）：故障转移、数据一致性、RTO 验证
3. SecurityHardeningTest（5 tests）：JWT、RBAC、TLS、凭证安全
4. OperationsGuideValidationTest（5 tests）：运维手册步骤可执行性

**运维手册内容**：
- 常见问题：Kafka消费积压、ClickHouse超时、OOM、配置不生效
- 故障诊断流程：症状识别、日志分析、指标检查、根因定位
- 应急处理：快速降级、数据恢复、回滚操作
- 性能调优：JVM参数、数据库连接池、常见瓶颈

**依赖**：
- Phase 10 Output（audit service deployable artifact）

**验收标准**：
- K8s 部署成功率 100%
- HA 切换时间 <30s
- 所有运维手册步骤可执行

**Memory 日志路径**：
`.apm/Memory/Phase_14_Audit_Examples_Documentation/Task_14_2_Deployment_Guide_Implementation.md`

请开始实现 Task 14.2，遵循 TDD 流程，先编写测试再创建文档和配置。
```

---

### 提示语 3：Task 14.3 - Audit Analysis Best Practices

```markdown
你是 Agent_Documentation，负责 Phase 14 Task 14.3 - Audit Analysis Best Practices & Remediation Guide。

**任务目标**：
创建审计分析最佳实践文档，指导用户解读审计发现、优先级排序、阈值调优、效果度量。

**核心交付物**：
1. 审计分析指南：docs/user-guide/audit-analysis-best-practices.md（150+ 行）
2. 风险优先级矩阵：severity + confidence + impact → action priority（P0/P1/Backlog）
3. 修复手册（Playbook）：针对每种 checker 发现的具体修复步骤
4. 阈值调优指南：建立基线、调整误报、月度审查
5. 成功度量指标：慢查询率改善、错误率降低、高风险SQL消除
6. 案例研究：3-5 个真实场景的审计发现和解决方案

**技术要点**：
- 风险优先级矩阵：
  - **P0（立即处理）**：CRITICAL + 高置信度(>80%) + 高影响(>1000行或>5s)
  - **P1（计划修复）**：HIGH + 中置信度(60-80%) + 中等影响
  - **Backlog（接受风险）**：MEDIUM/LOW 或已知问题
- 修复手册示例：
  - **SlowQueryChecker** → 分析执行计划 → 检查索引 → 添加缺失索引 → 查询重写 → 缓存考虑
  - **ActualImpactNoWhereChecker** → 确认意图 → 添加WHERE/分块 → 审查应用逻辑
  - **ErrorRateChecker** → 错误分类 → 语法错误代码审查 → 约束冲突数据验证 → 死锁分析
- 阈值调优：
  - 基线建立：收集 7 天生产数据
  - 计算 p95/p99
  - 阈值设为 p99 + 20% margin
  - 月度审查和调整
- 案例研究示例：
  1. **电商**：慢查询优化（8s → 200ms）
  2. **金融**：无WHERE更新检测（避免50k账户数据损坏）
  3. **SaaS**：错误率飙升检测（部署前回滚）
  4. **分析**：零影响查询识别（应用逻辑bug）
  5. **合规**：敏感数据访问模式（未授权PII查询）

**测试要求**（20+ tests）：
1. RiskPrioritizationTest（8 tests）：优先级矩阵、阈值调优、基线计算
2. RemediationPlaybookTest（7 tests）：各种 checker 发现的修复步骤有效性
3. CaseStudiesValidationTest（5 tests）：5个案例可复现

**依赖**：
- Phase 9 Output（checker documentation）
- Phase 12 Output（demo examples）- 可选

**验收标准**：
- 所有案例可复现
- 优先级矩阵准确
- 修复手册有效

**Memory 日志路径**：
`.apm/Memory/Phase_14_Audit_Examples_Documentation/Task_14_3_Best_Practices_Implementation.md`

请开始实现 Task 14.3，遵循 TDD 流程，先编写测试验证案例，再创建文档。
```

---

### 提示语 4：Task 14.4 - API Reference & Developer Documentation

```markdown
你是 Agent_Documentation，负责 Phase 14 Task 14.4 - API Reference & Developer Documentation。

**任务目标**：
生成 audit platform 的完整 API 文档，包括 Javadoc、自定义 checker 教程、REST API 参考、API 使用示例。

**核心交付物**：
1. Javadoc API 参考：覆盖所有 audit platform 公开 API
2. 自定义 checker 教程：docs/developer-guide/custom-audit-checker.md（完整示例）
3. REST API 参考：OpenAPI spec + 多语言代码示例
4. **API 使用示例**：Java/Python/JavaScript 代码片段（非完整 SDK）
5. 集成教程：CI/CD 集成、自定义告警集成
6. 开发者快速开始：5 分钟本地环境搭建

**技术要点**：
- **Javadoc 覆盖**：
  - 所有 audit 模块公开类（AbstractAuditChecker, ExecutionResult, RiskScore, AuditResult）
  - Audit service 公开 API（AuditEngine, repositories, controllers）
  - 扩展点（custom checkers, custom storage adapters）
  - 类级别代码示例
  - @since 2.0 标记新 audit API
- **自定义 Checker 教程**：
  - 7 步指南：extend → implement → calculate risk → tests → register → configure → deploy
  - 完整示例：TableLockChecker（检测锁持有时间 >1s）
  - 30+ test matrix 覆盖
- **REST API 参考**：
  - 从 OpenAPI 注解生成
  - 代码示例：curl, Java (RestTemplate/WebClient), Python (requests), JavaScript (fetch/axios)
  - 分页、错误处理、认证示例
- **API 使用示例（重要澄清）**：
  - **非完整 SDK 包**，而是代码片段
  - Java：RestTemplate/WebClient 调用示例
  - Python：requests 库示例
  - JavaScript：fetch/axios 示例
  - 常见用例：查询 CRITICAL 发现、获取统计数据、更新 checker 配置
  - 示例位置：docs/api-examples/

**集成教程**：
1. **CI/CD 集成**：管道中查询 audit API，PR 有 critical findings 则失败构建
2. **自定义告警**：轮询 audit API，CRITICAL 风险发送 Slack 通知
3. **指标导出**：获取统计数据，导出到自定义监控系统
4. **合规报告**：定期查询，生成 PDF 报告

**开发者快速开始**：
- `git clone` → `docker-compose up` → `mvn spring-boot:run`
- 访问 Swagger UI
- 运行示例查询
- 5 分钟完整本地环境

**测试要求**（30+ tests）：
1. JavadocCoverageTest（10 tests）：覆盖率 >90%、代码示例可编译、链接有效
2. CustomCheckerTutorialTest（10 tests）：7步教程每步可执行、TableLockChecker 示例工作
3. APIExamplesValidationTest（10 tests）：所有代码片段可编译可执行

**依赖**：
- Phase 10 Output（audit service with APIs）
- Phase 9 Output（checker extension points）

**验收标准**：
- Javadoc 覆盖率 >90%
- 所有代码示例可编译
- 所有 API 示例可执行

**Memory 日志路径**：
`.apm/Memory/Phase_14_Audit_Examples_Documentation/Task_14_4_API_Documentation_Implementation.md`

请开始实现 Task 14.4，遵循 TDD 流程，先编写测试验证 Javadoc 和代码示例，再生成文档。
```

---

## 并行执行建议

### 方式 1：单个大模型顺序执行（推荐）
由于这是文档工作，单个 Agent_Documentation 可以顺序完成：
1. Day 1-2: Task 14.4（API 文档，最基础）
2. Day 3-5: Task 14.1（Demo，最复杂）
3. Day 6-8: Task 14.2（部署指南）
4. Day 9-10: Task 14.3（最佳实践）

### 方式 2：多智能体并行执行
如果使用多个 agent 实例：
- Agent_Documentation_1 → Task 14.1 (5天)
- Agent_Documentation_2 → Task 14.2 (4天)
- Agent_Documentation_3 → Task 14.3 (3天)
- Agent_Documentation_4 → Task 14.4 (4天)

并行完成时间：5 工作日（最长任务）

### 协调机制
- 每日同步：各 agent 报告进度
- 交叉引用：Task 14.3 可以引用 14.1 的 demo 作为案例
- 统一格式：所有文档使用相同的 Markdown 格式和术语

---

**创建时间**: 2025-12-22
**Phase**: 14 - Audit Platform Examples & Documentation
**并行任务数**: 4
**预计总工期**: 5 工作日（并行）或 16 工作日（顺序）
