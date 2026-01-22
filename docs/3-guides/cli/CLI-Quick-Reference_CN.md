---
type: User Guide
component: CLI
version: 1.0
created: 2024-12-01
updated: 2025-01-16
status: Active
maintainer: SQL Safety Guard Team
language: zh-CN
---

# SQL Scanner CLI - 快速参考

## 安装

```bash
mvn clean install
```

## 基本用法

```bash
java -jar sql-scanner-cli.jar --project-path=<路径>
```

## 命令行选项

### 必需
| 选项 | 简写 | 说明 |
|------|------|------|
| `--project-path` | `-p` | 项目根目录 |

### 可选
| 选项 | 简写 | 默认值 | 说明 |
|------|------|---------|------|
| `--config-file` | `-c` | - | YAML 配置文件 |
| `--output-format` | `-f` | `console` | `console` 或 `html` |
| `--output-file` | `-o` | - | 输出文件路径 |
| `--fail-on-critical` | - | `false` | CRITICAL 时退出 1 |
| `--quiet` | `-q` | `false` | 抑制输出 |

## 常用命令

### 控制台报告
```bash
java -jar sql-scanner-cli.jar -p /path/to/project
```

### HTML 报告
```bash
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  -f html \
  -o report.html
```

### 自定义配置
```bash
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  -c config.yml
```

### CI/CD 模式
```bash
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  --fail-on-critical \
  -q
```

## 退出码

| 代码 | 含义 |
|------|------|
| `0` | 成功或非严重警告 |
| `1` | CRITICAL 违规或错误 |
| `2` | 无效参数 |

## 风险级别

| 级别 | 说明 | 示例 |
|------|------|------|
| **CRITICAL** | 严重安全问题 | 没有 WHERE 的 DELETE |
| **HIGH** | 严重问题 | 虚拟条件（1=1）|
| **MEDIUM** | 中等问题 | 缺少分页 |
| **LOW** | 次要问题 | 仅供参考 |

## 配置示例

**config.yml：**
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
  
  paginationAbuse:
    enabled: true
    riskLevel: HIGH
    physicalDeepPagination:
      maxOffset: 10000
      maxPageNum: 1000
```

## CI/CD 集成

### GitHub Actions
```yaml
- name: SQL 安全扫描
  run: |
    java -jar sql-scanner-cli.jar \
      -p . \
      --fail-on-critical \
      -q
```

### GitLab CI
```yaml
sql-scan:
  script:
    - java -jar sql-scanner-cli.jar -p . --fail-on-critical -q
```

### Jenkins
```groovy
sh 'java -jar sql-scanner-cli.jar -p . --fail-on-critical -q'
```

## 故障排除

### 缺少必需选项
```bash
# ❌ 错误
java -jar sql-scanner-cli.jar

# ✅ 正确
java -jar sql-scanner-cli.jar -p /path/to/project
```

### HTML 格式需要输出文件
```bash
# ❌ 错误
java -jar sql-scanner-cli.jar -p . -f html

# ✅ 正确
java -jar sql-scanner-cli.jar -p . -f html -o report.html
```

### 找不到配置文件
```bash
# ❌ 错误（相对路径可能失败）
java -jar sql-scanner-cli.jar -p . -c config.yml

# ✅ 正确（绝对路径）
java -jar sql-scanner-cli.jar -p . -c /absolute/path/config.yml
```

## 项目结构

预期的项目布局：
```
project/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/
│       │       ├── UserMapper.java      # @Select、@Insert 等
│       │       └── UserService.java     # QueryWrapper 使用
│       └── resources/
│           └── mappers/
│               └── UserMapper.xml       # XML 映射器
```

## 性能

| 项目规模 | 扫描时间 |
|----------|----------|
| 小型（<100 个文件）| 约 1-2 秒 |
| 中型（100-1000 个文件）| 约 5-10 秒 |
| 大型（>1000 个文件）| 约 20-30 秒 |

## 帮助和版本

```bash
# 显示帮助
java -jar sql-scanner-cli.jar --help

# 显示版本
java -jar sql-scanner-cli.jar --version
```

## 完整文档

完整文档请参见 [sql-scanner-cli/README_CN.md](../sql-scanner-cli/README_CN.md)。



















