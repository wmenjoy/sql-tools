# SQL Guard 功能缺口分析（基于 Druid WallFilter）

## 执行摘要

**分析目标**: 评估 Druid WallFilter 49个功能中，SQL Guard **运行时拦截器**缺失哪些关键安全功能
**分析原则**: SQL Guard 应作为**独立完整的 SQL 安全框架**，不依赖 Druid WallFilter 补充功能

---

## 1. 架构分层理解

### 1.1 运行时防护（Real-time Protection）

```
Application Layer
     ↓
SQL Guard 拦截器 (MyBatis/MyBatis-Plus/JDBC)
     ├─ [现有] 业务规则检测 (11个检查器)
     ├─ [缺失] SQL注入防护
     ├─ [缺失] 危险操作检测
     └─ 写入 audit.log → ThreadLocal
     ↓
Connection Pool (Druid/HikariCP/任意)
     └─ [不依赖] WallFilter 功能
     ↓
Database
```

**职责**: 实时阻断危险 SQL，防止执行

### 1.2 事后分析（Post-analysis）

```
audit.log (JSON Lines)
     ↓
SQL Audit Service
     ├─ Kafka Consumer (异步)
     ├─ 审计引擎 (11+ checkers)
     ├─ 风险评分和聚合
     ├─ 存储 (MySQL/ES/ClickHouse)
     └─ REST API (统计/趋势/报表)
```

**职责**: 深度分析、风险评估、趋势预测、审计报告

---

## 2. Druid WallFilter 49个功能分类评估

### 2.1 SQL注入防护类（HIGH PRIORITY - 运行时必须）

| 配置项 | 功能描述 | SQL Guard 状态 | 应实现层次 | 优先级 |
|-------|---------|--------------|----------|--------|
| **multiStatementAllow** | 多语句执行检测 | ❌ 未实现 | 运行时拦截器 | **P0** |
| **selectUnionCheck** | UNION注入检测 | ❌ 未实现 | 运行时拦截器 | **P0** |
| **selectIntoOutfileAllow** | SELECT INTO OUTFILE | ❌ 未实现 | 运行时拦截器 | **P0** |
| **commentAllow** | SQL注释检测 | ❌ 未实现 | 运行时拦截器 | **P0** |
| **selectMinusCheck** | SELECT MINUS (Oracle) | ❌ 未实现 | 运行时拦截器 | P1 |
| **selectExceptCheck** | SELECT EXCEPT (SQL Server) | ❌ 未实现 | 运行时拦截器 | P1 |
| **selectIntersectCheck** | SELECT INTERSECT | ❌ 未实现 | 运行时拦截器 | P1 |
| **conditionOpXorAllow** | XOR运算符 | ❌ 未实现 | 运行时拦截器 | P2 |

**缺口**: 8/8 (100%) 的 SQL 注入防护功能未实现

**影响**: 当不使用 Druid 时（如 HikariCP），SQL Guard 无法防御 SQL 注入攻击

---

### 2.2 危险操作检测类（HIGH PRIORITY - 运行时必须）

| 配置项 | 功能描述 | SQL Guard 状态 | 应实现层次 | 优先级 |
|-------|---------|--------------|----------|--------|
| **truncateAllow** | TRUNCATE检测 | ❌ 未实现 | 运行时拦截器 | **P0** |
| **createTableAllow** | CREATE TABLE检测 | ❌ 未实现 | 运行时拦截器 | **P0** |
| **alterTableAllow** | ALTER TABLE检测 | ❌ 未实现 | 运行时拦截器 | **P0** |
| **dropTableAllow** | DROP TABLE检测 | ❌ 未实现 | 运行时拦截器 | **P0** |
| **setAllow** | SET语句检测 | ❌ 未实现 | 运行时拦截器 | P1 |
| **callAllow** | 存储过程调用 | ❌ 未实现 | 运行时拦截器 | P1 |
| **replaceAllow** | REPLACE语句 (MySQL) | ❌ 未实现 | 运行时拦截器 | P2 |
| **mergeAllow** | MERGE语句 (Oracle) | ❌ 未实现 | 运行时拦截器 | P2 |

**缺口**: 8/8 (100%) 的危险操作检测未实现

**影响**: 无法阻止 DDL 操作、TRUNCATE 等危险操作

---

### 2.3 元数据保护类（MEDIUM PRIORITY - 运行时建议）

| 配置项 | 功能描述 | SQL Guard 状态 | 应实现层次 | 优先级 |
|-------|---------|--------------|----------|--------|
| **showAllow** | SHOW语句检测 | ❌ 未实现 | 运行时拦截器 | P1 |
| **describeAllow** | DESCRIBE检测 | ❌ 未实现 | 运行时拦截器 | P1 |
| **useAllow** | USE语句检测 | ❌ 未实现 | 运行时拦截器 | P2 |
| **functionCheck** | 危险函数检测 | ❌ 未实现 | 运行时拦截器 | **P0** |
| **tableCheck** | 禁用表检测 | ❌ 未实现 | 运行时拦截器 | P1 |
| **schemaCheck** | 禁用Schema检测 | ❌ 未实现 | 运行时拦截器 | P2 |
| **objectCheck** | 禁用对象检测 | ❌ 未实现 | 运行时拦截器 | P2 |
| **variantCheck** | 禁用变量检测 | ❌ 未实现 | 运行时拦截器 | P2 |

**缺口**: 8/8 (100%) 的元数据保护未实现

**影响**: 无法防止元数据暴露、危险函数调用（如 load_file）

---

### 2.4 WHERE条件检测类（PARTIALLY IMPLEMENTED - 运行时已有）

| 配置项 | 功能描述 | SQL Guard 状态 | 实现类 | 优先级 |
|-------|---------|--------------|--------|--------|
| **selectWhereAlwayTrueCheck** | SELECT永真检测 | ✅ 已实现 | DummyConditionChecker | - |
| **deleteWhereAlwayTrueCheck** | DELETE永真检测 | ✅ 已实现 | DummyConditionChecker | - |
| **updateWhereAlayTrueCheck** | UPDATE永真检测 | ✅ 已实现 | DummyConditionChecker | - |
| **conditionAndAlwayTrueAllow** | AND永真检测 | ✅ 已实现 | DummyConditionChecker | - |
| **deleteWhereNoneCheck** | DELETE无WHERE | ✅ 已实现 | NoWhereClauseChecker | - |
| **updateWhereNoneCheck** | UPDATE无WHERE | ✅ 已实现 | NoWhereClauseChecker | - |
| **selectHavingAlwayTrueCheck** | HAVING永真检测 | ⚠️ 部分实现 | DummyConditionChecker | P2 |
| **conditionAndAlwayFalseAllow** | AND永假检测 | ❌ 未实现 | - | P2 |
| **conditionLikeTrueAllow** | LIKE永真检测 | ❌ 未实现 | - | P2 |

**缺口**: 2/9 (22%) 未实现

**影响**: 已有良好覆盖，缺少永假条件和LIKE永真检测

---

### 2.5 SQL语句类型控制类（LOW PRIORITY - 应用层策略）

| 配置项 | 功能描述 | SQL Guard 状态 | 推荐层次 | 说明 |
|-------|---------|--------------|---------|------|
| **selectAllow** | 是否允许SELECT | ❌ 未实现 | 应用层/权限系统 | 不是SQL Guard职责 |
| **deleteAllow** | 是否允许DELETE | ❌ 未实现 | 应用层/权限系统 | 不是SQL Guard职责 |
| **updateAllow** | 是否允许UPDATE | ❌ 未实现 | 应用层/权限系统 | 不是SQL Guard职责 |
| **insertAllow** | 是否允许INSERT | ❌ 未实现 | 应用层/权限系统 | 不是SQL Guard职责 |
| **selectAllColumnAllow** | 是否允许SELECT * | ❌ 未实现 | 代码审查/Lint | 开发规范问题 |
| **selectIntoAllow** | SELECT INTO检测 | ❌ 未实现 | 运行时拦截器 | P2 |

**缺口**: 6/6 (100%)

**影响**: 建议通过应用层权限系统控制，不是 SQL Guard 核心职责

---

### 2.6 JDBC安全类（LOW PRIORITY - 连接池层）

| 配置项 | 功能描述 | SQL Guard 状态 | 推荐层次 | 说明 |
|-------|---------|--------------|---------|------|
| **metadataAllow** | getMetadata检测 | ❌ 未实现 | 连接池层 | JDBC API控制 |
| **wrapAllow** | unwrap检测 | ❌ 未实现 | 连接池层 | JDBC API控制 |

**缺口**: 2/2 (100%)

**影响**: 应由连接池或 JDBC 代理层控制

---

### 2.7 其他配置类

| 配置项 | 功能描述 | SQL Guard 状态 | 推荐层次 | 说明 |
|-------|---------|--------------|---------|------|
| **mustParameterized** | 强制参数化 | ❌ 未实现 | 代码审查/Lint | 开发规范问题 |
| **strictSyntaxCheck** | 严格语法检测 | ✅ 已实现 | JSqlParser | - |
| **conditionOpBitwseAllow** | 位运算符检测 | ❌ 未实现 | 运行时拦截器 | P2 |
| **readOnlyTables** | 只读表检测 | ❌ 未实现 | 运行时拦截器 | P1 |
| **commitAllow** | COMMIT检测 | ❌ 未实现 | 应用层 | 事务管理问题 |
| **rollbackAllow** | ROLLBACK检测 | ❌ 未实现 | 应用层 | 事务管理问题 |
| **noneBaseStatementAllow** | 非基本语句 | ❌ 未实现 | 运行时拦截器 | P2 |

---

## 3. 功能缺口总结

### 3.1 按优先级分类

| 优先级 | 功能类别 | 缺失数量 | 影响范围 |
|-------|---------|---------|---------|
| **P0 (关键)** | SQL注入防护 | 4项 | 无法防御SQL注入 |
| **P0 (关键)** | 危险操作检测 | 4项 | 无法阻止DDL/TRUNCATE |
| **P0 (关键)** | 危险函数检测 | 1项 | 无法阻止load_file等 |
| **P1 (重要)** | 元数据保护 | 3项 | 信息泄露风险 |
| **P1 (重要)** | SQL注入防护 | 3项 | 特定数据库注入 |
| **P2 (次要)** | 条件增强 | 3项 | 业务规则完善 |
| **不需要** | 应用层策略 | 8项 | 超出SQL Guard范围 |

### 3.2 总体覆盖率

```
Druid WallFilter 49个功能:
├─ ✅ 已实现: 9项 (18%)
├─ ⚠️ 部分实现: 1项 (2%)
├─ ❌ P0缺失: 9项 (18%) ← 关键缺口
├─ ❌ P1缺失: 6项 (12%)
├─ ❌ P2缺失: 8项 (16%)
└─ 不需要: 16项 (33%)
```

**核心问题**: **P0级别的9项关键安全功能缺失**

---

## 4. SQL Audit Service 的职责边界

### 4.1 SQL Audit Service 应该做什么

**基于 audit.log 的事后分析**:

```json
// audit.log 示例
{
  "sqlId": "8c830ce1...",
  "sql": "SELECT * FROM user WHERE status = ?",
  "executionTimeMs": 66,
  "rowsAffected": 0,
  "violations": {
    "passed": false,
    "riskLevel": "HIGH",
    "violations": [...]
  }
}
```

**职责**:
1. ✅ **深度风险评估**: 11+ 检查器并发执行
2. ✅ **趋势分析**: 历史数据聚合（按天/周/月）
3. ✅ **慢查询分析**: executionTimeMs 统计
4. ✅ **Top N风险SQL**: 按 riskLevel 排序
5. ✅ **错误率分析**: errorMessage 聚合
6. ✅ **检查器触发统计**: 哪些规则命中最多
7. ✅ **审计报告生成**: PDF/Excel 导出

### 4.2 SQL Audit Service 不应该做什么

**运行时阻断** (这是 SQL Guard 拦截器的职责):
- ❌ 阻止 SQL 执行（已经执行完才到 audit.log）
- ❌ 实时拦截（异步 Kafka 消费，有延迟）
- ❌ BLOCK 模式（只能 AUDIT 模式）

**结论**: SQL Audit Service 是**事后分析系统**，无法替代运行时防护

---

## 5. 灵活组合方案设计

### 5.1 功能分级矩阵

| 安全功能 | SQL Guard 运行时 | SQL Audit Service | Druid WallFilter | 推荐组合 |
|---------|----------------|------------------|-----------------|---------|
| **SQL注入防护** | ⚠️ 应实现 (P0) | ✅ 可分析 | ✅ 已有 | Guard + Audit |
| **危险操作检测** | ⚠️ 应实现 (P0) | ✅ 可分析 | ✅ 已有 | Guard + Audit |
| **WHERE条件检测** | ✅ 已实现 | ✅ 可分析 | ✅ 已有 | Guard + Audit |
| **分页滥用检测** | ✅ 已实现 | ✅ 可分析 | ❌ 无 | Guard + Audit |
| **字段级别控制** | ✅ 已实现 | ✅ 可分析 | ❌ 无 | Guard + Audit |
| **慢查询分析** | ❌ 无 | ✅ 可分析 | ✅ StatFilter | Audit |
| **趋势预测** | ❌ 无 | ✅ 可分析 | ❌ 无 | Audit |

### 5.2 组合策略 A: 完全独立 (推荐)

```yaml
# SQL Guard 运行时配置
sql-guard:
  enabled: true
  violation-strategy: BLOCK

  rules:
    # 业务规则 (已有)
    no-where-clause: { enabled: true }
    dummy-condition: { enabled: true }
    blacklist-fields: { enabled: true }
    # ... 11个检查器

    # SQL注入防护 (需新增)
    multi-statement: { enabled: true }      # P0
    union-injection: { enabled: true }       # P0
    sql-comment: { enabled: true }           # P0
    into-outfile: { enabled: true }          # P0

    # 危险操作 (需新增)
    ddl-operation: { enabled: true }         # P0
    truncate-table: { enabled: true }        # P0
    dangerous-function: { enabled: true }    # P0

  audit:
    enabled: true
    writer: logback  # 写入 audit.log

# SQL Audit Service (独立部署)
audit-service:
  kafka:
    bootstrap-servers: localhost:9092
    topic: sql-audit-events

  storage:
    type: mysql
    # 或 elasticsearch, clickhouse
```

**优势**:
- ✅ 不依赖 Druid WallFilter
- ✅ 适用于任何连接池 (HikariCP/Druid/P6Spy)
- ✅ 功能完整、配置统一

### 5.3 组合策略 B: 与 Druid 互补 (现状)

```yaml
sql-guard:
  enabled: true
  rules:
    # 只实现 Druid 没有的功能
    deep-pagination: { enabled: true }
    blacklist-fields: { enabled: true }

druid:
  filters: wall,stat
  wall:
    config:
      # Druid 负责 SQL 注入防护
      multi-statement-allow: false
      select-union-check: true
      delete-where-none-check: true
```

**劣势**:
- ❌ 依赖 Druid (换 HikariCP 就失去保护)
- ❌ 配置分散 (Druid配置 + SQL Guard配置)
- ❌ 功能重复 (deleteWhereNoneCheck 两边都有)

---

## 6. 实现建议

### 6.1 Phase 15: SQL注入防护 (P0)

**新增 RuleChecker**:

```java
// 1. MultiStatementChecker
@RuleChecker(id = "multi-statement", riskLevel = CRITICAL)
public class MultiStatementChecker extends AbstractRuleChecker {
    @Override
    public void visitSelect(Select select, SqlContext context) {
        // 检测 SQL 中是否包含分号分隔的多语句
        String sql = context.getSql();
        if (sql.contains(";")) {
            fail("检测到多语句执行，可能存在SQL注入风险");
        }
    }
}

// 2. UnionInjectionChecker
@RuleChecker(id = "union-injection", riskLevel = CRITICAL)
public class UnionInjectionChecker extends AbstractRuleChecker {
    @Override
    public void visitSelect(Select select, SqlContext context) {
        // 检测 SELECT 语句是否包含 UNION
        if (select.toString().toUpperCase().contains("UNION")) {
            fail("检测到UNION语句，可能存在UNION注入风险");
        }
    }
}

// 3. SqlCommentChecker
// 4. IntoOutfileChecker
```

### 6.2 Phase 16: 危险操作检测 (P0)

```java
// 1. DdlOperationChecker
@RuleChecker(id = "ddl-operation", riskLevel = CRITICAL)
public class DdlOperationChecker extends AbstractRuleChecker {
    @Override
    public ValidationResult check(Statement statement, SqlContext context) {
        if (statement instanceof CreateTable ||
            statement instanceof AlterTable ||
            statement instanceof Drop ||
            statement instanceof Truncate) {
            return fail("生产环境禁止应用层执行DDL操作");
        }
        return pass();
    }
}

// 2. DangerousFunctionChecker
@RuleChecker(id = "dangerous-function", riskLevel = CRITICAL)
public class DangerousFunctionChecker extends AbstractRuleChecker {
    private static final Set<String> DANGEROUS_FUNCTIONS = Set.of(
        "load_file", "into_outfile", "into_dumpfile",
        "sys_exec", "sys_eval"
    );
    // 检测 SQL 中是否使用危险函数
}
```

### 6.3 Phase 17: 元数据保护 (P1)

```java
// 1. MetadataLeakChecker
// 2. ShowDescribeChecker
```

---

## 7. 结论

### 7.1 核心问题

**SQL Guard 缺少 9 项 P0 级别的关键安全功能**:
1. multiStatementAllow (多语句检测)
2. selectUnionCheck (UNION注入)
3. selectIntoOutfileAllow (文件操作)
4. commentAllow (注释注入)
5. truncateAllow (TRUNCATE检测)
6. createTableAllow (CREATE TABLE)
7. alterTableAllow (ALTER TABLE)
8. dropTableAllow (DROP TABLE)
9. functionCheck (危险函数)

**后果**: 当不使用 Druid 时，SQL Guard 无法提供完整的 SQL 安全防护。

### 7.2 解决方案

**推荐: 实现完整的安全功能矩阵**

```
Phase 15 (P0): SQL注入防护
├─ MultiStatementChecker
├─ UnionInjectionChecker
├─ SqlCommentChecker
└─ IntoOutfileChecker

Phase 16 (P0): 危险操作检测
├─ DdlOperationChecker (CREATE/ALTER/DROP)
├─ TruncateChecker
└─ DangerousFunctionChecker

Phase 17 (P1): 元数据保护
├─ MetadataLeakChecker
└─ ShowDescribeChecker
```

**目标**: SQL Guard 成为**独立完整的 SQL 安全框架**，灵活组合，不依赖底层连接池。

---

**文档生成时间**: 2026-01-04
**版本**: 1.0
**作者**: SQL Guard Team
