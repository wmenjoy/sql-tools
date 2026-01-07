# SQL Safety Guard 系统

为使用 MyBatis、MyBatis-Plus 和 JDBC 的 Java 应用程序提供静态分析和运行时验证的综合 SQL 安全框架。

## 功能特性

- 🔍 **静态分析**：扫描 XML 映射器、Java 注解和 QueryWrapper 使用中的 SQL
- 🛡️ **运行时验证**：在运行时拦截和验证 SQL
- 📊 **多种报告格式**：控制台（ANSI 彩色）和 HTML（样式化网页）
- 🚀 **CI/CD 集成**：带退出码的命令行工具用于构建管道
- ⚙️ **灵活配置**：基于 YAML 的配置，具有合理的默认值
- 🔌 **框架支持**：MyBatis、MyBatis-Plus、JDBC、Spring Boot

## 快速开始

### 1. 使用 CLI 工具进行静态分析

扫描您的项目以查找 SQL 安全问题：

```bash
# 构建项目
mvn clean install

# 运行 SQL 扫描器
java -jar sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
  --project-path=/path/to/your/project

# 生成 HTML 报告
java -jar sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
  --project-path=/path/to/your/project \
  --output-format=html \
  --output-file=report.html
```

**详细的 CLI 文档请参见 [sql-scanner-cli/README_CN.md](sql-scanner-cli/README_CN.md)。**

### 2. 使用 Spring Boot 进行运行时验证

在您的 `pom.xml` 中添加依赖：

```xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

在 `application.yml` 中配置：

```yaml
sql-guard:
  enabled: true
  active-strategy: prod
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
```

## 项目结构

这是一个 Maven 多模块项目，包含以下模块：

### 静态分析（Scanner）
- **sql-scanner-core** - 核心 SQL 扫描引擎
- **sql-scanner-cli** - 命令行界面工具
- **sql-scanner-maven** - 用于构建时扫描的 Maven 插件
- **sql-scanner-gradle** - 用于构建时扫描的 Gradle 插件

### 运行时验证（Guard）
- **sql-guard-core** - 运行时验证引擎
- **sql-guard-mybatis** - MyBatis 拦截器
- **sql-guard-mp** - MyBatis-Plus 拦截器
- **sql-guard-jdbc** - JDBC 层拦截器
- **sql-guard-spring-boot-starter** - Spring Boot 自动配置

## 构建要求

- Maven 3.6+
- Java 8+（基线兼容性：Java 8）

## 构建项目

### 默认构建（Java 8）
```bash
mvn clean install
```

### 多版本 Java 兼容性

项目支持使用 Maven 配置文件构建不同的 Java 版本：

#### Java 11
```bash
mvn clean install -Pjava11
```

#### Java 17
```bash
mvn clean install -Pjava17
```

#### Java 21
```bash
mvn clean install -Pjava21
```

### CI/CD 矩阵构建

对于持续集成管道，使用配置文件激活来测试多个 Java 版本：

```yaml
# GitHub Actions 矩阵示例
strategy:
  matrix:
    java: [8, 11, 17, 21]
steps:
  - uses: actions/setup-java@v3
    with:
      java-version: ${{ matrix.java }}
  - run: mvn clean verify -Pjava${{ matrix.java }}
```

## 代码质量

项目使用 Checkstyle 强制执行 Google Java 风格。样式检查在 `verify` 阶段自动运行：

```bash
mvn verify
```

跳过 Checkstyle 检查（不推荐）：
```bash
mvn verify -Dcheckstyle.skip=true
```

## 测试

运行所有测试：
```bash
mvn test
```

运行特定模块的测试：
```bash
mvn test -pl sql-guard-core
```

## 文档

### 模块文档
- **[SQL Scanner CLI](sql-scanner-cli/README_CN.md)** - 完整的 CLI 工具使用指南
- **[双配置模式](sql-guard-core/docs/Dual-Config-Pattern.md)** - 配置系统设计

### 核心概念

#### 静态分析
扫描器模块在构建时或按需分析 SQL：
- **XML 映射器**：解析 MyBatis XML 映射器文件
- **Java 注解**：分析 @Select、@Insert、@Update、@Delete 注解
- **QueryWrapper**：检测 MyBatis-Plus QueryWrapper 使用

#### 运行时验证
守护模块在运行时拦截和验证 SQL：
- **MyBatis 拦截器**：拦截 MyBatis SQL 执行
- **MyBatis-Plus 拦截器**：拦截 MyBatis-Plus 操作
- **JDBC 拦截器**：拦截 JDBC PreparedStatement 执行

#### 风险级别
违规按严重性分类：
- **CRITICAL**：严重的安全或数据完整性问题（例如，没有 WHERE 的 DELETE）
- **HIGH**：需要注意的严重问题（例如，虚拟条件）
- **MEDIUM**：应该解决的中等问题（例如，缺少分页）
- **LOW**：次要问题，仅供参考

## CI/CD 集成

### 退出码
CLI 工具为 CI/CD 集成返回标准退出码：
- `0` - 成功或非严重警告
- `1` - CRITICAL 违规（使用 `--fail-on-critical`）或错误
- `2` - 无效的命令行参数

### 示例：GitHub Actions

```yaml
- name: SQL 安全扫描
  run: |
    java -jar sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
      --project-path=. \
      --fail-on-critical \
      --quiet
```

更多 CI/CD 示例请参见 [sql-scanner-cli/README_CN.md](sql-scanner-cli/README_CN.md)。

## 许可证

Copyright (c) 2025 Footstone



















