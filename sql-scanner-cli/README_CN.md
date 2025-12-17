# SQL Scanner CLI - 命令行工具

MyBatis 应用程序的静态 SQL 安全扫描命令行工具。

## 概述

SQL Scanner CLI 提供了一个生产就绪的命令行工具，用于扫描 Java 项目以检测 SQL 安全问题。它分析 XML 映射器、Java 注解映射器和 MyBatis-Plus QueryWrapper 使用情况，以识别潜在的安全风险和代码质量问题。

## 功能特性

- 🔍 **多源扫描**：XML 映射器、Java 注解、QueryWrapper 使用
- 📊 **双格式报告**：控制台（ANSI 彩色）和 HTML（样式化网页）
- ⚙️ **灵活配置**：YAML 配置或合理的默认值
- 🚀 **CI/CD 集成**：构建管道集成的退出码
- 🔇 **静默模式**：自动化环境的最小输出
- ✅ **快速失败验证**：带建议的清晰错误消息

## 安装

### 从源码构建

```bash
# 克隆仓库
git clone <repository-url>
cd sqltools

# 构建项目
mvn clean install

# 可执行 JAR 将位于：
# sql-scanner-cli/target/sql-scanner-cli.jar
```

### 运行 CLI

```bash
# 显示帮助信息
java -jar sql-scanner-cli/target/sql-scanner-cli.jar --help

# 或者使用简化的文件名
java -jar sql-scanner-cli.jar --help
```

> **注意**：构建过程使用 Maven Shade 插件创建一个包含所有依赖的可执行 fat JAR（`sql-scanner-cli.jar`），可以直接运行。

## 使用方法

### 基本语法

```bash
java -jar sql-scanner-cli.jar [选项]
```

### 命令行选项

#### 必需选项

| 选项 | 简写 | 说明 |
|------|------|------|
| `--project-path` | `-p` | 要扫描的项目根目录（必需）|

#### 可选选项

| 选项 | 简写 | 默认值 | 说明 |
|------|------|--------|------|
| `--config-file` | `-c` | - | YAML 配置文件路径 |
| `--output-format` | `-f` | `console` | 输出格式：`console` 或 `html` |
| `--output-file` | `-o` | - | 输出文件路径（format=html 时必需）|
| `--fail-on-critical` | - | `false` | 发现 CRITICAL 违规时以代码 1 退出 |
| `--quiet` | `-q` | `false` | 为 CI/CD 抑制非错误输出 |
| `--help` | `-h` | - | 显示帮助信息 |
| `--version` | `-v` | - | 显示版本信息 |

### 使用示例

#### 1. 基本扫描（控制台输出）

使用默认配置和控制台输出扫描项目：

```bash
java -jar sql-scanner-cli.jar --project-path=/path/to/project
```

**输出：**
```
配置加载成功
初始化解析器...
扫描项目：/path/to/project
扫描完成：发现 42 条 SQL 语句
================================================================================
SQL 安全扫描报告
================================================================================
总 SQL 数：42 | 违规数：0
================================================================================

✓ 未发现违规 - 所有 SQL 语句都是安全的！

================================================================================

扫描完成：未发现违规 ✓
```

#### 2. 使用自定义配置扫描

使用自定义 YAML 配置文件：

```bash
java -jar sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --config-file=config.yml
```

**示例 config.yml：**
```yaml
enabled: true
activeStrategy: prod

rules:
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
  
  dummyCondition:
    enabled: true
    riskLevel: HIGH
```

#### 3. 生成 HTML 报告

生成样式化的 HTML 报告：

```bash
java -jar sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --output-format=html \
  --output-file=report.html
```

HTML 报告包括：
- 带统计信息的交互式仪表板
- 可排序的违规表格
- 可折叠的 SQL 预览部分
- 颜色编码的风险级别
- 响应式设计

#### 4. CI/CD 集成

在 CI/CD 管道中运行，遇到严重问题时失败：

```bash
java -jar sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --fail-on-critical \
  --quiet
```

**退出码：**
- `0` - 成功或非严重警告（HIGH/MEDIUM/LOW 违规）
- `1` - 发现 CRITICAL 违规（使用 `--fail-on-critical`）或错误
- `2` - 无效的命令行参数

#### 5. 短选项别名

使用短选项别名以简洁：

```bash
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  -c config.yml \
  -f html \
  -o report.html \
  -q
```

### CI/CD 集成示例

#### GitHub Actions

```yaml
name: SQL 安全扫描

on: [push, pull_request]

jobs:
  scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: 设置 JDK 8
        uses: actions/setup-java@v3
        with:
          java-version: '8'
          distribution: 'temurin'
      
      - name: 构建 SQL Scanner
        run: |
          cd sqltools
          mvn clean install -DskipTests
      
      - name: 运行 SQL 安全扫描
        run: |
          java -jar sqltools/sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
            --project-path=. \
            --fail-on-critical \
            --quiet
      
      - name: 生成 HTML 报告
        if: always()
        run: |
          java -jar sqltools/sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
            --project-path=. \
            --output-format=html \
            --output-file=sql-safety-report.html
      
      - name: 上传报告
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: sql-safety-report
          path: sql-safety-report.html
```

#### GitLab CI

```yaml
sql-safety-scan:
  stage: test
  image: maven:3.8-openjdk-8
  script:
    - cd sqltools
    - mvn clean install -DskipTests
    - |
      java -jar sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
        --project-path=. \
        --fail-on-critical \
        --output-format=html \
        --output-file=sql-safety-report.html
  artifacts:
    when: always
    paths:
      - sql-safety-report.html
    reports:
      junit: sql-safety-report.html
```

#### Jenkins Pipeline

```groovy
pipeline {
    agent any
    
    stages {
        stage('SQL 安全扫描') {
            steps {
                sh '''
                    cd sqltools
                    mvn clean install -DskipTests
                    
                    java -jar sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
                        --project-path=. \
                        --fail-on-critical \
                        --quiet
                '''
            }
        }
        
        stage('生成报告') {
            when {
                always()
            }
            steps {
                sh '''
                    java -jar sqltools/sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar \
                        --project-path=. \
                        --output-format=html \
                        --output-file=sql-safety-report.html
                '''
                
                publishHTML([
                    reportDir: '.',
                    reportFiles: 'sql-safety-report.html',
                    reportName: 'SQL 安全报告'
                ])
            }
        }
    }
}
```

## 配置

### 默认配置

如果未提供配置文件，CLI 使用合理的默认值：

```yaml
enabled: true
activeStrategy: prod

interceptors:
  mybatis:
    enabled: true
  mybatisPlus:
    enabled: false
  jdbc:
    enabled: true
    type: auto

deduplication:
  enabled: true
  cacheSize: 1000
  ttlMs: 100

rules:
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
  
  dummyCondition:
    enabled: true
    riskLevel: HIGH
  
  blacklistFields:
    enabled: true
    riskLevel: HIGH
    fields:
      - deleted
      - del_flag
      - status
  
  paginationAbuse:
    enabled: true
    riskLevel: HIGH
  
  noPagination:
    enabled: true
    riskLevel: MEDIUM
  
  estimatedRows:
    enabled: true
    riskLevel: HIGH
```

### 自定义配置

创建 `config.yml` 文件以自定义扫描行为：

```yaml
# 启用/禁用整个系统
enabled: true

# 活动策略：dev、test 或 prod
activeStrategy: prod

# 规则配置
rules:
  # 无 WHERE 子句检测
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
    exemptTables:
      - config_table
      - metadata_table
  
  # 虚拟条件检测（1=1、1<>1 等）
  dummyCondition:
    enabled: true
    riskLevel: HIGH
    patterns:
      - "1\\s*=\\s*1"
      - "1\\s*<>\\s*1"
  
  # 黑名单字段检测
  blacklistFields:
    enabled: true
    riskLevel: HIGH
    fields:
      - deleted
      - del_flag
      - is_deleted
      - status
  
  # 分页滥用检测
  paginationAbuse:
    enabled: true
    riskLevel: HIGH
    physicalDeepPagination:
      maxOffset: 10000
      maxPageNum: 1000
    largePageSize:
      maxPageSize: 500
  
  # 缺少分页检测
  noPagination:
    enabled: true
    riskLevel: MEDIUM
    enforceForAllQueries: false
    whitelistMapperIds:
      - "selectById"
      - "selectByPrimaryKey"
    whitelistTables:
      - config
      - metadata
```

## 扫描流程

CLI 工具执行以下步骤：

1. **输入验证**：验证所有命令行参数（快速失败）
2. **配置加载**：加载 YAML 配置或使用默认值
3. **解析器初始化**：实例化 XML、注解和 QueryWrapper 解析器
4. **项目扫描**：
   - 发现 `src/main/resources` 下的 XML 映射器文件
   - 发现 `src/main/java` 下的 Java 源文件
   - 解析 XML 映射器中的 SQL 语句
   - 解析 Java 注解（@Select、@Insert、@Update、@Delete）
   - 扫描 MyBatis-Plus QueryWrapper 使用
5. **报告生成**：生成控制台或 HTML 报告
6. **退出码确定**：为 CI/CD 返回适当的退出码

## 报告格式

### 控制台报告

ANSI 彩色终端输出，包括：
- 统计摘要
- 按风险级别分组的违规（CRITICAL → HIGH → MEDIUM → LOW）
- 每个违规的文件路径和行号
- SQL 片段预览
- 违规消息和建议

**示例：**
```
================================================================================
SQL 安全扫描报告
================================================================================
总 SQL 数：42 | 违规数：3（CRITICAL：1，HIGH：2）
================================================================================

[CRITICAL] 1 个违规

  [UserMapper.xml:15] com.example.UserMapper.deleteAllUsers
  SQL：DELETE FROM users
  消息：检测到没有 WHERE 子句的 DELETE 语句
  建议：添加 WHERE 子句以防止意外数据丢失

[HIGH] 2 个违规

  [UserMapper.xml:23] com.example.UserMapper.selectUsers
  SQL：SELECT * FROM users WHERE 1=1 AND name = #{name}
  消息：检测到虚拟条件：1=1
  建议：删除虚拟条件，改用动态 SQL

  [UserService.java:45] com.example.UserService.findUsers
  SQL：检测到 QueryWrapper 使用
  消息：QueryWrapper 需要运行时验证
  建议：确保适当的输入验证

================================================================================
```

### HTML 报告

样式化网页，包括：
- 带统计卡片的交互式仪表板
- 可排序的违规表格（点击列标题）
- 可折叠的 SQL 预览部分
- 颜色编码的风险级别徽章
- 移动/桌面响应式设计
- Wrapper 使用部分

## 错误处理

### 验证错误

带建议的清晰、可操作的错误消息：

```
验证错误：项目路径不存在：/invalid/path
请提供有效的项目根目录。
```

```
验证错误：HTML 格式需要 --output-file 选项
示例：--output-format=html --output-file=report.html
```

### 解析错误

带详细消息的优雅错误处理：

```
解析错误：无法解析 XML 文件：UserMapper.xml
第 23 行：意外的结束标签 </select>
```

### I/O 错误

文件系统错误处理：

```
I/O 错误：输出目录不可写：/readonly/path
请确保您有写入权限。
```

## 故障排除

### 常见问题

#### 1. "缺少必需选项：'--project-path'"

**解决方案**：始终提供 `--project-path` 选项：
```bash
java -jar sql-scanner-cli.jar --project-path=/path/to/project
```

#### 2. "HTML 格式需要 --output-file 选项"

**解决方案**：使用 HTML 格式时提供 `--output-file`：
```bash
java -jar sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --output-format=html \
  --output-file=report.html
```

#### 3. "配置文件不存在"

**解决方案**：验证配置文件路径是否正确：
```bash
# 检查文件是否存在
ls -la config.yml

# 如果需要，使用绝对路径
java -jar sql-scanner-cli.jar \
  --project-path=/path/to/project \
  --config-file=/absolute/path/to/config.yml
```

#### 4. 未找到 SQL 语句

**可能原因：**
- 项目结构不符合预期布局（`src/main/resources`、`src/main/java`）
- 项目中没有 XML 映射器或 Java 注解
- 文件位于非标准位置

**解决方案**：确保项目遵循标准 Maven/Gradle 结构：
```
project/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/
│       │       ├── UserMapper.java
│       │       └── UserService.java
│       └── resources/
│           └── mappers/
│               └── UserMapper.xml
```

## 性能

### 扫描速度

- **小型项目**（<100 个文件）：约 1-2 秒
- **中型项目**（100-1000 个文件）：约 5-10 秒
- **大型项目**（>1000 个文件）：约 20-30 秒

### 内存使用

- **基线**：约 50-100 MB
- **每 1000 条 SQL 语句**：额外约 10-20 MB

### 优化建议

1. **在 CI/CD 中使用静默模式**以减少日志开销
2. **如果不需要，排除测试目录**
3. **对大型报告使用 HTML 格式**（性能优于控制台）

## 开发

### 运行测试

```bash
cd sql-scanner-cli
mvn test
```

**测试覆盖率：**
- CLI 参数解析：12 个测试
- 输入验证：11 个测试
- 扫描编排：7 个测试
- 报告输出：7 个测试
- **总计：37 个测试**

### 构建

```bash
mvn clean package
```

可执行 JAR 将创建在：
```
target/sql-scanner-cli-1.0.0-SNAPSHOT.jar
```

## 许可证

Copyright (c) 2025 Footstone

## 支持

有关问题、疑问或贡献，请参阅主项目文档。

