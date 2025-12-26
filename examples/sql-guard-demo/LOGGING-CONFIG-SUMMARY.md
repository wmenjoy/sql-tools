# SQL Guard Demo - Logback 日志配置完成总结

## ✅ 配置完成

SQL Guard Demo 已成功配置 **Logback** 日志系统，确保 SQL 语句和 Audit 审计日志输出到专门的文件。

---

## 📊 配置内容

### 1. 创建的文件

| 文件 | 说明 |
|------|------|
| `logback-spring.xml` | Logback 主配置文件 |
| `LOGGING-SETUP.md` | 日志配置使用文档 |
| `test-logging.sh` | 日志配置测试脚本 |
| `logs/.gitignore` | 忽略日志文件的 Git 配置 |

### 2. 修改的文件

| 文件 | 修改内容 |
|------|----------|
| `application.yml` | 添加详细的日志级别配置 |

---

## 📁 日志文件说明

### 核心日志文件

```
logs/
├── sql/
│   └── sql.log                    # ✅ SQL 语句日志
├── audit/
│   └── audit.log                  # ✅ Audit 审计日志（JSON 格式）
├── sqlguard/
│   └── validation.log             # SQL Guard 验证日志
├── application.log                # 应用主日志
└── archive/                       # 自动滚动归档
    ├── sql.*.log.gz
    └── audit.*.log.gz
```

---

## 🎯 日志配置详情

### SQL 语句日志

**文件**: `logs/sql/sql.log`

**配置的 Logger**:
```yaml
com.footstone.sqlguard.demo.mapper: DEBUG  # MyBatis Mapper
org.apache.ibatis: DEBUG                   # MyBatis 内部
com.baomidou.mybatisplus: DEBUG            # MyBatis-Plus
```

**输出示例**:
```
2025-12-26 16:30:15.123 | ==>  Preparing: SELECT * FROM user WHERE id = ?
2025-12-26 16:30:15.124 | ==> Parameters: 1(Long)
2025-12-26 16:30:15.135 | <==      Total: 1
```

### Audit 审计日志

**文件**: `logs/audit/audit.log`

**配置的 Logger**:
```yaml
com.footstone.sqlguard.audit.AUDIT: INFO
```

**输出格式**: 纯 JSON，每行一个审计事件

**输出示例**:
```json
{"sql":"SELECT * FROM user","sqlType":"SELECT","executionTimeMs":150,"rowsAffected":100,"riskLevel":"MEDIUM"}
```

---

## 🚀 快速验证

### 1. 运行测试脚本

```bash
./test-logging.sh
```

**预期输出**:
```
✅ logback-spring.xml 存在
✅ MyBatis Mapper 日志级别已配置为 DEBUG
✅ MyBatis 内部日志级别已配置为 DEBUG
✅ MyBatis-Plus 日志级别已配置为 DEBUG
✅ 配置完成! 🎉
```

### 2. 启动应用并查看日志

```bash
# 启动应用
mvn spring-boot:run

# 在另一个终端查看 SQL 日志
tail -f logs/sql/sql.log

# 查看 Audit 日志
tail -f logs/audit/audit.log

# 格式化 Audit JSON 输出（需要 jq）
tail -f logs/audit/audit.log | jq '.'
```

---

## 📝 关键配置

### logback-spring.xml

```xml
<!-- SQL 语句日志 - 异步输出 -->
<appender name="ASYNC_SQL_FILE" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>1024</queueSize>
    <appender-ref ref="SQL_FILE"/>
</appender>

<!-- Audit 审计日志 - 异步输出 + 不丢失 -->
<appender name="ASYNC_AUDIT_FILE" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>8192</queueSize>
    <neverBlock>false</neverBlock> <!-- 防止审计日志丢失 -->
    <appender-ref ref="AUDIT_FILE"/>
</appender>

<!-- MyBatis SQL Logger -->
<logger name="com.footstone.sqlguard.demo.mapper" level="DEBUG" additivity="false">
    <appender-ref ref="ASYNC_SQL_FILE"/>
    <appender-ref ref="CONSOLE"/>
</logger>

<!-- Audit Logger (isolated) -->
<logger name="com.footstone.sqlguard.audit.AUDIT" level="INFO" additivity="false">
    <appender-ref ref="ASYNC_AUDIT_FILE"/>
</logger>
```

### application.yml

```yaml
logging:
  file:
    path: logs
  level:
    com.footstone.sqlguard.demo.mapper: DEBUG  # ✅ SQL 输出
    org.apache.ibatis: DEBUG                   # ✅ MyBatis SQL
    com.baomidou.mybatisplus: DEBUG            # ✅ MyBatis-Plus
    com.footstone.sqlguard: DEBUG              # SQL Guard 详情
```

---

## 🎨 日志特性

- ✅ **异步日志输出** - 使用 AsyncAppender，不阻塞业务逻辑
- ✅ **独立日志通道** - SQL、Audit、应用日志互不干扰
- ✅ **自动滚动归档** - 按日期和大小滚动，自动压缩
- ✅ **防止丢失** - Audit 日志配置 neverBlock=false，确保不丢失
- ✅ **彩色控制台** - 开发环境下更易读
- ✅ **纯 JSON 格式** - Audit 日志便于解析和分析

---

## 📚 文档

详细使用说明请参考: **[LOGGING-SETUP.md](LOGGING-SETUP.md)**

---

## ✅ 验证清单

- [x] Logback 配置文件已创建
- [x] application.yml 日志级别已配置
- [x] 日志目录已创建
- [x] SQL 语句日志配置完成（DEBUG 级别）
- [x] Audit 审计日志配置完成（INFO 级别）
- [x] 测试脚本验证通过
- [x] 文档已创建

---

**配置完成！** 🎉

现在启动应用时，所有 SQL 语句和 Audit 审计日志都会自动输出到专门的日志文件中。
