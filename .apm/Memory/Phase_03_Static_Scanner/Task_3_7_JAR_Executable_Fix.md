# Task 3.7 - JAR 可执行文件修复

## 问题描述

用户报告 `sql-scanner-cli/target/sql-scanner-cli-1.0.0-SNAPSHOT.jar` 中没有主清单属性，导致无法直接执行 JAR 文件。

## 问题原因

Maven 默认构建的 JAR 文件不包含 `MANIFEST.MF` 中的 `Main-Class` 属性，因此无法使用 `java -jar` 命令直接运行。此外，默认 JAR 不包含依赖项，需要在运行时手动指定 classpath。

## 解决方案

### 1. 添加 Maven Shade Plugin

在 `sql-scanner-cli/pom.xml` 中添加了 Maven Shade Plugin 配置：

```xml
<build>
    <plugins>
        <!-- Maven Shade Plugin for creating executable fat JAR -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals>
                        <goal>shade</goal>
                    </goals>
                    <configuration>
                        <transformers>
                            <!-- Set the main class -->
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>com.footstone.sqlguard.scanner.cli.SqlScannerCli</mainClass>
                            </transformer>
                        </transformers>
                        <finalName>sql-scanner-cli</finalName>
                        <createDependencyReducedPom>false</createDependencyReducedPom>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### 2. 关键配置说明

- **ManifestResourceTransformer**: 自动在 MANIFEST.MF 中添加 `Main-Class` 属性
- **mainClass**: 指定主类为 `com.footstone.sqlguard.scanner.cli.SqlScannerCli`
- **finalName**: 设置输出 JAR 名称为 `sql-scanner-cli.jar`（不带版本号，更简洁）
- **createDependencyReducedPom**: 设置为 `false` 以避免生成额外的 POM 文件
- **Fat JAR**: Shade Plugin 会将所有依赖打包到一个 JAR 中，创建一个自包含的可执行文件

## 验证结果

### 1. 构建成功

```bash
cd /Users/liujinliang/workspace/ai/sqltools/sql-scanner-cli
mvn clean package
```

构建成功，生成 `target/sql-scanner-cli.jar`。

### 2. 帮助信息测试

```bash
java -jar target/sql-scanner-cli.jar --help
```

输出：
```
Usage: sql-scanner [-hqV] [--fail-on-critical] [-c=<configFile>]
                   [-f=<outputFormat>] [-o=<outputFile>] -p=<projectPath>
Static SQL safety scanner for MyBatis applications
  -c, --config-file=<configFile>
                           Configuration YAML file path (optional)
  -f, --output-format=<outputFormat>
                           Output format: console or html (default: console)
      --fail-on-critical   Exit with code 1 if CRITICAL violations found
                             (default: false)
  -h, --help               Show this help message and exit.
  -o, --output-file=<outputFile>
                           Output file path for HTML format (required if
                             format=html)
  -p, --project-path=<projectPath>
                           Project root directory to scan
  -q, --quiet              Suppress non-error output for CI/CD (default: false)
  -V, --version            Print version information and exit.
```

### 3. 实际扫描测试

```bash
java -jar target/sql-scanner-cli.jar -p src/test/resources/test-project
```

成功执行扫描并生成报告：
```
Using default configuration
Configuration loaded successfully
Initializing parsers...
Scanning project: src/test/resources/test-project
...
Scan complete: 2 SQL statements found
================================================================================
SQL Safety Scan Report
================================================================================
Total SQL: 2 | Violations: 0 | Wrapper Usages: 1
================================================================================

✓ No violations found - all SQL statements are safe!

[WRAPPER USAGES] 1 location require runtime validation

  [.../UserService.java:7] findUsers - QueryWrapper

================================================================================

Scan complete: No violations found ✓
```

## 文档更新

### 1. 更新 README 文件

#### 中文版 (`sql-scanner-cli/README_CN.md`)
- 更新安装部分，说明生成的 JAR 文件名为 `sql-scanner-cli.jar`
- 添加关于 Maven Shade Plugin 和 fat JAR 的说明
- 更新运行命令示例

#### 英文版 (`sql-scanner-cli/README.md`)
- 同步更新英文版本的相同内容

### 2. 快速参考文档

`docs/CLI-Quick-Reference.md` 和 `docs/CLI-Quick-Reference_CN.md` 已经使用简化的 JAR 名称，无需更新。

## 技术要点

### Maven Shade Plugin 的优势

1. **自包含**: 将所有依赖打包到一个 JAR 中
2. **可执行**: 自动配置 MANIFEST.MF 的 Main-Class
3. **简化部署**: 只需分发一个 JAR 文件
4. **无需 classpath**: 不需要手动指定依赖路径

### 生成的 JAR 文件

- **位置**: `sql-scanner-cli/target/sql-scanner-cli.jar`
- **大小**: 约 10+ MB（包含所有依赖）
- **可移植性**: 可以复制到任何位置直接运行
- **Java 版本要求**: Java 8+

## 使用建议

### 开发环境

```bash
# 构建
mvn clean package

# 运行
java -jar sql-scanner-cli/target/sql-scanner-cli.jar -p <project-path>
```

### 生产环境

```bash
# 复制 JAR 到部署位置
cp sql-scanner-cli/target/sql-scanner-cli.jar /usr/local/bin/

# 创建别名（可选）
alias sql-scanner='java -jar /usr/local/bin/sql-scanner-cli.jar'

# 使用
sql-scanner -p /path/to/project
```

### CI/CD 集成

```yaml
# GitHub Actions 示例
- name: Build SQL Scanner
  run: mvn clean package -pl sql-scanner-cli

- name: Run SQL Scan
  run: |
    java -jar sql-scanner-cli/target/sql-scanner-cli.jar \
      -p . \
      --fail-on-critical \
      --quiet
```

## 总结

通过添加 Maven Shade Plugin，成功解决了 JAR 文件无法执行的问题。现在用户可以：

1. ✅ 使用 `java -jar` 直接运行 CLI 工具
2. ✅ 无需手动配置 classpath
3. ✅ 获得一个自包含的可执行 JAR
4. ✅ 简化部署和分发流程
5. ✅ 在 CI/CD 环境中轻松集成

这个改进大大提升了工具的易用性和可移植性。











