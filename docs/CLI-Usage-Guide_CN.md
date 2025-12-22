# SQL Scanner CLI 使用指南

## 快速开始

### 1. 构建项目

```bash
cd sqltools
mvn clean install
```

构建完成后，可执行 JAR 文件位于：
```
sql-scanner-cli/target/sql-scanner-cli.jar
```

### 2. 基本使用

```bash
# 扫描项目
java -jar sql-scanner-cli.jar -p /path/to/your/project

# 查看帮助
java -jar sql-scanner-cli.jar --help

# 查看版本
java -jar sql-scanner-cli.jar --version
```

## 常用场景

### 场景 1: 本地开发 - 快速扫描

在开发过程中快速检查 SQL 安全问题：

```bash
java -jar sql-scanner-cli.jar -p .
```

**输出示例:**
```
================================================================================
SQL Safety Scan Report
================================================================================
Total SQL: 42 | Violations: 3 | Wrapper Usages: 5
================================================================================

[CRITICAL] 1 violation

  [UserMapper.xml:45] deleteAllUsers - No WHERE clause in DELETE
    DELETE FROM users

[HIGH] 2 violations

  [OrderMapper.xml:23] queryOrders - Dummy condition detected
    SELECT * FROM orders WHERE 1=1 AND status = #{status}

  [ProductMapper.java:15] findProducts - Blacklist field in query
    SELECT * FROM products WHERE deleted = 0

[WRAPPER USAGES] 5 locations require runtime validation
  ...

================================================================================
```

### 场景 2: 生成 HTML 报告

生成详细的 HTML 报告用于团队审查：

```bash
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  -f html \
  -o sql-safety-report.html
```

然后在浏览器中打开 `sql-safety-report.html` 查看详细报告。

### 场景 3: 使用自定义配置

使用自定义规则配置：

```bash
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  -c custom-config.yml
```

**custom-config.yml 示例:**
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
  
  blacklistFields:
    enabled: true
    riskLevel: HIGH
```

### 场景 4: CI/CD 集成

在 CI/CD 管道中使用静默模式和失败退出：

```bash
java -jar sql-scanner-cli.jar \
  -p . \
  --fail-on-critical \
  --quiet
```

**特点:**
- `--quiet`: 只显示错误和最终报告
- `--fail-on-critical`: 发现 CRITICAL 违规时退出码为 1
- 适合自动化流程

### 场景 5: 代码审查

在代码审查时生成 HTML 报告并分享：

```bash
# 生成报告
java -jar sql-scanner-cli.jar \
  -p feature-branch \
  -f html \
  -o review-report.html

# 将报告添加到 PR 评论或上传到共享位置
```

## CI/CD 集成示例

### GitHub Actions

```yaml
name: SQL Safety Check

on: [push, pull_request]

jobs:
  sql-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 8
        uses: actions/setup-java@v3
        with:
          java-version: '8'
          distribution: 'temurin'
      
      - name: Build SQL Scanner
        run: mvn clean install -pl sql-scanner-cli
      
      - name: Run SQL Safety Scan
        run: |
          java -jar sql-scanner-cli/target/sql-scanner-cli.jar \
            -p . \
            -f html \
            -o sql-scan-report.html \
            --fail-on-critical
      
      - name: Upload Report
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: sql-safety-report
          path: sql-scan-report.html
```

### Jenkins Pipeline

```groovy
pipeline {
    agent any
    
    stages {
        stage('Build') {
            steps {
                sh 'mvn clean install -pl sql-scanner-cli'
            }
        }
        
        stage('SQL Safety Scan') {
            steps {
                script {
                    def exitCode = sh(
                        script: '''
                            java -jar sql-scanner-cli/target/sql-scanner-cli.jar \
                              -p ${WORKSPACE} \
                              -f html \
                              -o sql-scan-report.html \
                              --fail-on-critical \
                              --quiet
                        ''',
                        returnStatus: true
                    )
                    
                    if (exitCode != 0) {
                        error("SQL safety scan found CRITICAL violations")
                    }
                }
            }
        }
    }
    
    post {
        always {
            publishHTML([
                reportDir: '.',
                reportFiles: 'sql-scan-report.html',
                reportName: 'SQL Safety Report'
            ])
        }
    }
}
```

### GitLab CI

```yaml
stages:
  - build
  - scan

build:
  stage: build
  script:
    - mvn clean install -pl sql-scanner-cli
  artifacts:
    paths:
      - sql-scanner-cli/target/sql-scanner-cli.jar

sql_scan:
  stage: scan
  dependencies:
    - build
  script:
    - |
      java -jar sql-scanner-cli/target/sql-scanner-cli.jar \
        -p . \
        -f html \
        -o sql-scan-report.html \
        --fail-on-critical \
        --quiet
  artifacts:
    when: always
    paths:
      - sql-scan-report.html
    reports:
      junit: sql-scan-report.html
```

## 命令行选项详解

### 必需选项

| 选项 | 简写 | 说明 | 示例 |
|------|------|------|------|
| `--project-path` | `-p` | 要扫描的项目根目录 | `-p /path/to/project` |

### 可选选项

| 选项 | 简写 | 默认值 | 说明 |
|------|------|--------|------|
| `--config-file` | `-c` | - | YAML 配置文件路径 |
| `--output-format` | `-f` | `console` | 输出格式：`console` 或 `html` |
| `--output-file` | `-o` | - | 输出文件路径（format=html 时必需）|
| `--fail-on-critical` | - | `false` | 发现 CRITICAL 违规时以代码 1 退出 |
| `--quiet` | `-q` | `false` | 抑制非错误输出 |
| `--help` | `-h` | - | 显示帮助信息 |
| `--version` | `-V` | - | 显示版本信息 |

## 退出码

| 退出码 | 含义 | 说明 |
|--------|------|------|
| 0 | 成功 | 扫描完成，无 CRITICAL 违规或未启用 `--fail-on-critical` |
| 1 | 失败 | 输入验证错误、配置错误、或发现 CRITICAL 违规（启用 `--fail-on-critical` 时）|

## 输出格式

### 控制台输出（默认）

彩色的 ANSI 格式输出，包括：
- 扫描统计信息
- 按风险级别分组的违规列表
- QueryWrapper 使用位置
- 详细的 SQL 语句和位置信息

**优点:**
- 实时查看结果
- 彩色高亮，易于阅读
- 适合本地开发

### HTML 输出

样式化的 HTML 报告，包括：
- 完整的扫描结果
- 交互式表格
- 风险级别颜色标记
- 可打印和分享

**优点:**
- 适合团队分享
- 可以保存和归档
- 适合代码审查和报告

## 常见问题

### Q1: 如何忽略特定的 SQL 语句？

A: 在配置文件中使用 `exemptMapperIds`:

```yaml
rules:
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
    # 在实际配置类中实现此功能
```

### Q2: 扫描速度慢怎么办？

A: 
1. 确保只扫描必要的目录
2. 排除测试代码和第三方库
3. 使用 `--quiet` 模式减少日志输出

### Q3: 如何自定义规则？

A: 创建自定义配置文件：

```yaml
enabled: true
activeStrategy: prod

rules:
  noWhereClause:
    enabled: true
    riskLevel: CRITICAL
  
  dummyCondition:
    enabled: false  # 禁用此规则
  
  blacklistFields:
    enabled: true
    riskLevel: HIGH
```

### Q4: 支持哪些 MyBatis 版本？

A: 支持：
- MyBatis 3.x
- MyBatis-Plus 3.x
- 基于注解和 XML 的映射器

### Q5: 如何在 Docker 中使用？

A: 创建 Dockerfile:

```dockerfile
FROM openjdk:8-jre-alpine
COPY sql-scanner-cli.jar /app/
WORKDIR /app
ENTRYPOINT ["java", "-jar", "sql-scanner-cli.jar"]
```

使用：
```bash
docker build -t sql-scanner .
docker run -v /path/to/project:/project sql-scanner -p /project
```

## 最佳实践

### 1. 在提交前本地扫描

```bash
# 添加到 git pre-commit hook
#!/bin/bash
java -jar sql-scanner-cli.jar -p . --fail-on-critical --quiet
if [ $? -ne 0 ]; then
    echo "SQL safety scan failed! Please fix CRITICAL violations."
    exit 1
fi
```

### 2. 在 PR 中自动扫描

在 CI/CD 管道中配置自动扫描，并将报告附加到 PR 评论。

### 3. 定期生成报告

设置定时任务，定期生成 HTML 报告并发送给团队：

```bash
# 每天生成报告
0 9 * * * java -jar sql-scanner-cli.jar -p /path/to/project -f html -o daily-report-$(date +\%Y\%m\%d).html
```

### 4. 逐步提高标准

开始时可以只警告，不失败构建：
```bash
java -jar sql-scanner-cli.jar -p .
```

当团队适应后，启用 `--fail-on-critical`：
```bash
java -jar sql-scanner-cli.jar -p . --fail-on-critical
```

### 5. 使用自定义配置

为不同环境使用不同配置：
- `config-dev.yml`: 开发环境，规则较宽松
- `config-prod.yml`: 生产环境，规则严格

```bash
# 开发环境
java -jar sql-scanner-cli.jar -p . -c config-dev.yml

# 生产环境
java -jar sql-scanner-cli.jar -p . -c config-prod.yml --fail-on-critical
```

## 技术支持

如有问题或建议，请：
1. 查看完整文档：`sql-scanner-cli/README_CN.md`
2. 查看快速参考：`docs/CLI-Quick-Reference_CN.md`
3. 提交 Issue 或 PR

## 相关文档

- [完整 CLI 文档](../sql-scanner-cli/README_CN.md)
- [快速参考卡](CLI-Quick-Reference_CN.md)
- [配置示例](../sql-scanner-cli/config-example_CN.yml)
- [主项目 README](../README_CN.md)












