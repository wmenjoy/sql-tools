# SQL Safety Guard 文档索引 / Documentation Index

欢迎使用 SQL Safety Guard 文档系统。本项目采用**七层文档架构**，确保文档的结构化、可维护性和易发现性。

## 📚 七层文档架构 / Seven-Layer Documentation Architecture

### 1️⃣ 技术规范 (1-specs/)
系统的技术规格、API 规范、架构设计、配置规范等权威技术文档。

- **[架构文档](1-specs/architecture/ARCHITECTURE.md)** - 系统整体架构设计
- **[API 规范](1-specs/api/)** - API 接口技术规范
  - [审计日志 API](1-specs/api/audit-log.md)
- **[配置规范](1-specs/config/)** - 配置相关技术规范
  - [审计日志写入器配置](1-specs/config/audit-log-writer-configuration.md)
  - [静态分析配置](1-specs/config/STATIC_ANALYSIS_CONFIG_CN.md)
- **[验证方案对比](1-specs/VALIDATION_COMPARISON.md)** - 不同验证方案的技术对比

### 2️⃣ 产品需求 (2-requirements/)
产品需求文档 (PRD)、用户故事、功能需求等。

_（待补充）_

### 3️⃣ 用户/开发指南 (3-guides/)
面向最终用户和开发者的使用指南、教程、最佳实践。

#### CLI 工具指南
- **[CLI 使用指南](3-guides/cli/CLI-Usage-Guide_CN.md)** 🆕 - 实用场景和最佳实践
- **[CLI 快速参考（中文）](3-guides/cli/CLI-Quick-Reference_CN.md)** - 命令行选项快速查找
- **[CLI 快速参考（英文）](3-guides/cli/CLI-Quick-Reference.md)** - Command-line options quick reference

#### 用户指南
- **[增强型 HTML 报告](3-guides/user/ENHANCED_HTML_REPORT_CN.md)** - HTML 报告功能说明
- **[HTML 报告改进](3-guides/user/ENHANCED_HTML_REPORT_IMPROVEMENTS.md)** - 报告功能改进详情
- **[用户指南合集](3-guides/user/rules/)** - 规则说明和使用指南

#### 开发者指南
- **[API 示例](3-guides/developer/api-examples/)** - 各语言 API 使用示例
  - [Java 示例](3-guides/developer/api-examples/java/)
  - [JavaScript 示例](3-guides/developer/api-examples/javascript/)
  - [Python 示例](3-guides/developer/api-examples/python/)
- **[部署指南](3-guides/developer/deployment/)** - 部署相关文档
  - [Kubernetes 部署](3-guides/developer/deployment/k8s/)
  - [高可用部署](3-guides/developer/deployment/ha/)
  - [监控配置](3-guides/developer/deployment/monitoring/)
  - [安全配置](3-guides/developer/deployment/security/)
  - [备份策略](3-guides/developer/deployment/backup/)
- **[集成指南](3-guides/developer/integration/)** - 与其他系统集成
- **[迁移指南](3-guides/developer/migration/)** - 版本迁移和升级
- **[运维手册](3-guides/developer/operations/)** - 运维操作指南
- **[开发教程](3-guides/developer/tutorials/)** - 开发教程和示例
- **[开发示例](3-guides/developer/examples/)** - 代码示例

### 4️⃣ 规划文档 (4-planning/)
项目规划、技术提案、设计方案、实施计划等前瞻性文档。

#### 技术提案
- **[语义分析提案](4-planning/proposals/SEMANTIC_ANALYSIS_PROPOSAL.md)** - SQL 语义分析功能提案
- **[核心优化提案](4-planning/proposals/sql-scanner-core-optimization-proposal.md)** - Scanner 核心优化方案

#### 设计文档
- **[系统设计](4-planning/2025-12-10-sql-safety-guard-design.md)** - 系统整体设计规划

### 5️⃣ 业务知识库 (5-wiki/)
领域知识、最佳实践、案例研究、技术分享等知识沉淀。

- **[上下文工程指南](5-wiki/context_engineering_guide.md)** - AI 协作的上下文工程最佳实践
- **[SQL 安全最佳实践](5-wiki/sql-security-best-practices.md)** - SQL 注入防护、权限控制、审计策略等安全知识
- **[审计规则设计指南](5-wiki/rule-design-guide.md)** - 如何设计和实现自定义审计规则
- **[案例研究](5-wiki/case-studies/)** - 实际应用案例分析

### 6️⃣ 架构决策 (6-decisions/)
架构决策记录 (ADR)，记录重要的技术决策及其背景、权衡和结果。

_（待补充）_

### 7️⃣ 文档归档 (7-archive/)
已完成或过时的临时文档归档，按年份组织。

#### 2025 年归档
- [ExecutionResult 空安全分析](7-archive/2025/ExecutionResult_Null_Safety_Analysis.md)
- [ExecutionResult 空安全修复总结](7-archive/2025/ExecutionResult_Null_Safety_Fix_Summary.md)
- [ExecutionResult vs ValidationResult 概念澄清](7-archive/2025/ExecutionResult_vs_ValidationResult_Clarification.md)
- [Statement ID 改进总结](7-archive/2025/STATEMENT_ID_IMPROVEMENT_SUMMARY.md)
- [Statement ID 索引实现](7-archive/2025/STATEMENT_ID_INDEX_IMPLEMENTATION.md)
- [ValidationResult vs AuditService 分析](7-archive/2025/ValidationResult_vs_AuditService_Analysis.md)
- [文档更新总结](7-archive/2025/Documentation-Updates-Summary.md)
- [系统完整性评估](7-archive/2025/SYSTEM_COMPLETENESS_ASSESSMENT.md)
- [验证清单](7-archive/2025/VERIFICATION_CHECKLIST.md)

---

## 🚀 快速开始 / Quick Start

### 新用户
1. 阅读 [项目主页 README](../README_CN.md) 了解项目概述
2. 查看 [CLI 使用指南](3-guides/cli/CLI-Usage-Guide_CN.md) 开始使用
3. 参考 [CLI 快速参考](3-guides/cli/CLI-Quick-Reference_CN.md) 查找命令

### 开发者
1. 阅读 [架构文档](1-specs/architecture/ARCHITECTURE.md) 了解系统设计
2. 查看 [API 规范](1-specs/api/) 了解接口定义
3. 参考 [开发者指南](3-guides/developer/) 进行开发集成

### 运维人员
1. 查看 [部署指南](3-guides/developer/deployment/) 了解部署方案
2. 参考 [运维手册](3-guides/developer/operations/) 进行日常运维
3. 查阅 [监控配置](3-guides/developer/deployment/monitoring/) 配置监控

---

## 📖 外部文档链接 / External Documentation Links

### 项目主文档
- **[项目主页 README (中文)](../README_CN.md)** - 项目概述、快速开始、构建说明
- **[项目主页 README (English)](../README.md)** - Project overview, quick start, build instructions

### 模块文档
- **[SQL Scanner CLI 完整指南 (中文)](../sql-scanner-cli/README_CN.md)** - CLI 工具详细使用文档
- **[SQL Scanner CLI Complete Guide (English)](../sql-scanner-cli/README.md)** - Detailed CLI tool documentation
- **[配置示例 (中文)](../sql-scanner-cli/config-example_CN.yml)** - 带中文注释的配置模板
- **[Configuration Example (English)](../sql-scanner-cli/config-example.yml)** - Configuration template with comments

---

## 🔍 按主题查找文档 / Find Documentation by Topic

### 静态分析 / Static Analysis
- [静态分析配置规范](1-specs/config/STATIC_ANALYSIS_CONFIG_CN.md)
- [CLI 使用指南 - 静态扫描](3-guides/cli/CLI-Usage-Guide_CN.md)

### 审计日志 / Audit Logging
- [审计日志 API 规范](1-specs/api/audit-log.md)
- [审计日志写入器配置](1-specs/config/audit-log-writer-configuration.md)

### 部署运维 / Deployment & Operations
- [部署指南合集](3-guides/developer/deployment/)
- [运维手册](3-guides/developer/operations/)

### 开发集成 / Development & Integration
- [API 示例](3-guides/developer/api-examples/)
- [集成指南](3-guides/developer/integration/)

---

## 📝 文档规范 / Documentation Standards

本项目文档遵循以下规范：

1. **分层架构**：所有文档按七层结构组织
2. **元数据标准**：每个文档包含版本、日期、状态、维护者信息
3. **命名规范**：使用清晰、描述性的文件名
4. **交叉引用**：文档间通过相对路径链接
5. **版本控制**：重要变更记录在文档历史中

详见：[write-standard-document skill](../.claude/skills/write-standard-document/)

---

## 🤝 贡献文档 / Contributing Documentation

创建新文档时，请使用 `write-standard-document` skill 确保符合项目规范。

---

**版本**: 2.0
**最后更新**: 2025-01-15
**维护者**: SQL Safety Guard Team

---

*使用 [write-standard-document skill](../.claude/skills/write-standard-document/) 创建的标准化文档*
