# SQL Safety Guard 文档索引 / Documentation Index

## 中文文档 / Chinese Documentation

### 主要文档
- **[项目主页 README](../README_CN.md)** - 项目概述、快速开始、构建说明
- **[SQL Scanner CLI 完整指南](../sql-scanner-cli/README_CN.md)** - CLI 工具详细使用文档
- **[CLI 使用指南](CLI-Usage-Guide_CN.md)** 🆕 - 实用场景和最佳实践
- **[CLI 快速参考](CLI-Quick-Reference_CN.md)** - 命令行选项快速查找
- **[配置示例](../sql-scanner-cli/config-example_CN.yml)** - 带中文注释的配置模板

### 核心概念
- **静态分析**：在构建时或按需扫描 SQL 代码
- **运行时验证**：在运行时拦截和验证 SQL 执行
- **风险级别**：CRITICAL、HIGH、MEDIUM、LOW

### 快速链接
- [安装和构建](../README_CN.md#构建项目)
- [CLI 基本用法](../sql-scanner-cli/README_CN.md#使用方法)
- [CI/CD 集成](../sql-scanner-cli/README_CN.md#cicd-集成示例)
- [配置指南](../sql-scanner-cli/README_CN.md#配置)
- [故障排除](../sql-scanner-cli/README_CN.md#故障排除)

---

## English Documentation

### Main Documentation
- **[Project README](../README.md)** - Project overview, quick start, build instructions
- **[SQL Scanner CLI Complete Guide](../sql-scanner-cli/README.md)** - Detailed CLI tool documentation
- **[CLI Quick Reference](CLI-Quick-Reference.md)** - Quick lookup for command-line options
- **[Configuration Example](../sql-scanner-cli/config-example.yml)** - Configuration template with comments

### Core Concepts
- **Static Analysis**: Scan SQL code at build time or on-demand
- **Runtime Validation**: Intercept and validate SQL execution at runtime
- **Risk Levels**: CRITICAL, HIGH, MEDIUM, LOW

### Quick Links
- [Installation and Build](../README.md#building-the-project)
- [CLI Basic Usage](../sql-scanner-cli/README.md#usage)
- [CI/CD Integration](../sql-scanner-cli/README.md#cicd-integration-examples)
- [Configuration Guide](../sql-scanner-cli/README.md#configuration)
- [Troubleshooting](../sql-scanner-cli/README.md#troubleshooting)

---

## 文档结构 / Documentation Structure

```
sqltools/
├── README.md / README_CN.md                     # 主项目文档
├── docs/
│   ├── README.md                                # 文档索引（本文件）
│   ├── CLI-Usage-Guide_CN.md                   # 中文 CLI 使用指南 🆕
│   ├── CLI-Quick-Reference.md                   # 英文快速参考
│   ├── CLI-Quick-Reference_CN.md                # 中文快速参考
│   └── plans/
│       └── 2025-12-10-sql-safety-guard-design.md
├── sql-scanner-cli/
│   ├── README.md                                # 英文 CLI 完整指南
│   ├── README_CN.md                             # 中文 CLI 完整指南
│   ├── config-example.yml                       # 英文配置示例
│   └── config-example_CN.yml                    # 中文配置示例
└── sql-guard-core/
    └── docs/
        └── Dual-Config-Pattern.md               # 配置系统设计
```

## 使用场景 / Use Cases

### 场景 1：开发者本地扫描 / Developer Local Scan
```bash
java -jar sql-scanner-cli.jar -p /path/to/project
```
**文档**: [CLI 基本用法 / CLI Basic Usage](../sql-scanner-cli/README_CN.md#使用方法)

### 场景 2：生成 HTML 报告 / Generate HTML Report
```bash
java -jar sql-scanner-cli.jar -p /path/to/project -f html -o report.html
```
**文档**: [报告格式 / Report Formats](../sql-scanner-cli/README_CN.md#报告格式)

### 场景 3：CI/CD 集成 / CI/CD Integration
```bash
java -jar sql-scanner-cli.jar -p . --fail-on-critical -q
```
**文档**: [CI/CD 集成示例 / CI/CD Integration Examples](../sql-scanner-cli/README_CN.md#cicd-集成示例)

### 场景 4：自定义配置 / Custom Configuration
```bash
java -jar sql-scanner-cli.jar -p /path/to/project -c config.yml
```
**文档**: [配置指南 / Configuration Guide](../sql-scanner-cli/README_CN.md#配置)

## 常见问题 / FAQ

### Q: 如何开始使用？/ How to get started?
**A**: 查看 [快速开始 / Quick Start](../README_CN.md#快速开始) 部分

### Q: 支持哪些 SQL 源？/ What SQL sources are supported?
**A**: 
- XML 映射器 / XML Mappers (MyBatis)
- Java 注解 / Java Annotations (@Select, @Insert, etc.)
- QueryWrapper 使用 / QueryWrapper usage (MyBatis-Plus)

### Q: 如何在 CI/CD 中使用？/ How to use in CI/CD?
**A**: 查看 [CI/CD 集成 / CI/CD Integration](../sql-scanner-cli/README_CN.md#cicd-集成示例) 部分

### Q: 如何自定义规则？/ How to customize rules?
**A**: 查看 [配置示例 / Configuration Example](../sql-scanner-cli/config-example_CN.yml)

### Q: 遇到问题怎么办？/ What to do if I encounter issues?
**A**: 查看 [故障排除 / Troubleshooting](../sql-scanner-cli/README_CN.md#故障排除) 部分

## 贡献 / Contributing

欢迎贡献！请参考主项目文档了解更多信息。

Contributions are welcome! Please refer to the main project documentation for more information.

## 许可证 / License

Copyright (c) 2025 Footstone

