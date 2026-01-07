# Batch 2 Task Assignments - Phase 8 Parallel Execution

**Generated:** 2025-12-17
**Prerequisite:** Task 8.1 ✅ Completed (28 tests passed)
**Parallelism:** 6 tasks can execute simultaneously

---

## Batch Overview

| Task | Title | Module | Key Output |
|------|-------|--------|------------|
| 8.2 | Logback Async Appender | sql-guard-audit-api | LogbackAuditWriter, logback-audit.xml |
| 8.3 | Druid SqlAuditFilter | sql-guard-jdbc | DruidSqlAuditFilter |
| 8.3.5 | HikariCP SqlAuditProxyFactory | sql-guard-jdbc | HikariSqlAuditProxyFactory |
| 8.4 | MyBatis SqlAuditInterceptor | sql-guard-mybatis | SqlAuditInterceptor |
| 8.5 | MyBatis-Plus InnerAuditInterceptor | sql-guard-mp | MpSqlAuditInnerInterceptor |
| 8.6 | P6Spy Audit Listener | sql-guard-jdbc | P6SpySqlAuditListener |

---

## Common Dependencies

All tasks depend on Task 8.1 outputs:
- `AuditLogWriter` interface: `com.footstone.sqlguard.audit.AuditLogWriter`
- `AuditEvent` model: `com.footstone.sqlguard.audit.AuditEvent`
- JSON serialization: Jackson ObjectMapper with JavaTimeModule

```xml
<dependency>
    <groupId>com.footstone</groupId>
    <artifactId>sql-guard-audit-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## Task 8.2: Logback Async Appender Configuration

**Objective:** Configure Logback AsyncAppender for high-throughput audit log writing with <1ms latency.

**Deliverables:**
- `LogbackAuditWriter` implementing `AuditLogWriter`
- `logback-audit.xml` with AsyncAppender configuration
- `docs/audit-log-filebeat.md` for Kafka ingestion

**Key Implementation:**
```java
public class LogbackAuditWriter implements AuditLogWriter {
    private static final Logger AUDIT_LOGGER =
        LoggerFactory.getLogger("com.footstone.sqlguard.audit.AUDIT");

    @Override
    public void writeAuditLog(AuditEvent event) {
        String json = objectMapper.writeValueAsString(event);
        AUDIT_LOGGER.info(json);  // Async write via AsyncAppender
    }
}
```

**Performance Target:** <1ms p99 write latency

---

## Task 8.3: Druid SqlAuditFilter

**Objective:** Implement Druid FilterAdapter for post-execution audit logging.

**Deliverables:**
- `DruidSqlAuditFilter` extending `FilterAdapter`
- Filter ordering (order=10, after StatFilter)
- ThreadLocal coordination with DruidSqlSafetyFilter

**Key Implementation:**
```java
public class DruidSqlAuditFilter extends FilterAdapter {
    @Override
    public boolean statement_execute(StatementProxy statement, String sql) {
        long startNano = System.nanoTime();
        try {
            boolean result = super.statement_execute(statement, sql);
            writeAuditEvent(statement, sql, startNano, null);
            return result;
        } catch (SQLException e) {
            writeAuditEvent(statement, sql, startNano, e);
            throw e;
        }
    }
}
```

---

## Task 8.3.5: HikariCP SqlAuditProxyFactory

**Objective:** 为HikariCP连接池提供审计日志采集能力。

**Deliverables:**
- `HikariSqlAuditProxyFactory` 使用JDK动态代理
- Statement/PreparedStatement代理
- 与HikariSqlSafetyProxyFactory共享ThreadLocal

**Key Implementation:**
```java
public class HikariSqlAuditProxyFactory {
    public Connection wrapConnection(Connection conn) {
        return (Connection) Proxy.newProxyInstance(
            conn.getClass().getClassLoader(),
            new Class[]{Connection.class},
            new ConnectionAuditHandler(conn, auditLogWriter)
        );
    }
}
```

---

## Task 8.4: MyBatis SqlAuditInterceptor

**Objective:** Implement MyBatis plugin for ORM-layer audit logging.

**Deliverables:**
- `SqlAuditInterceptor` with @Intercepts annotation
- ThreadLocal correlation via `SqlInterceptorContext`
- MyBatis 3.4.x and 3.5.x compatibility

**Key Implementation:**
```java
@Intercepts({
    @Signature(type = Executor.class, method = "update",
               args = {MappedStatement.class, Object.class}),
    @Signature(type = Executor.class, method = "query",
               args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class SqlAuditInterceptor implements Interceptor {
    @Override
    public Object intercept(Invocation invocation) {
        long startNano = System.nanoTime();
        Object result = invocation.proceed();
        writeAuditEvent(invocation, result, startNano);
        return result;
    }
}
```

---

## Task 8.5: MyBatis-Plus InnerAuditInterceptor

**Objective:** Implement InnerInterceptor for IPage and QueryWrapper audit.

**Deliverables:**
- `MpSqlAuditInnerInterceptor` implementing `InnerInterceptor`
- IPage pagination metadata capture
- QueryWrapper detection flag

**Key Implementation:**
```java
public class MpSqlAuditInnerInterceptor implements InnerInterceptor {
    private static final ThreadLocal<AuditContext> CONTEXT = new ThreadLocal<>();

    @Override
    public void beforeQuery(...) {
        CONTEXT.set(new AuditContext(System.nanoTime(), boundSql.getSql()));
    }

    @Override
    public void afterQuery(..., List<?> result) {
        AuditContext ctx = CONTEXT.get();
        writeAuditEvent(ctx, result);
        CONTEXT.remove();
    }
}
```

---

## Task 8.6: P6Spy Audit Listener

**Objective:** Implement universal JDBC audit via P6Spy JdbcEventListener.

**Deliverables:**
- `P6SpySqlAuditListener` extending `JdbcEventListener`
- `P6SpySqlAuditModule` for SPI registration
- spy.properties configuration

**Key Implementation:**
```java
public class P6SpySqlAuditListener extends JdbcEventListener {
    @Override
    public void onAfterAnyExecute(StatementInformation info) {
        String sql = info.getSqlWithValues();
        long durationMs = info.getExecuteTime();
        writeAuditEvent(sql, durationMs, extractRowsAffected(info));
    }
}
```

---

## Execution Instructions

Each task can be assigned to a separate Implementation Agent. All 6 tasks:

1. **Add sql-guard-audit-api dependency** to target module
2. **Follow TDD methodology** - write tests first
3. **Use shared ThreadLocal pattern** for safety-audit coordination
4. **Reference existing safety interceptors** (Tasks 4.1-4.5) for patterns
5. **Run `mvn test`** ensuring all tests pass

**Completion Criteria for Batch 2:**
- [ ] All 6 tasks completed
- [ ] All tests pass in each module
- [ ] ThreadLocal coordination verified
- [ ] Performance benchmarks met

---

## Next Batch

After Batch 2 completion, proceed to **Batch 3**:
- Task 9.1: AbstractAuditChecker Base Class
- Task 10.1: Audit Service Project Foundation

These can also run in parallel.
