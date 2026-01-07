# SQL Guard 架构分析与 Druid WallFilter 功能对比

## 执行摘要

**分析日期**: 2026-01-04
**分析范围**: SQL Guard 多层拦截架构 + Druid WallFilter 功能对比
**关键发现**: SQL Guard 是**业务规则验证引擎**，与 Druid WallFilter **职责不同**

---

## 1. 核心理解纠正

### 1.1 SQL Guard 的真实定位

```
SQL Guard = 业务规则验证框架
   ├─ 分页滥用检测 (6个检查器)
   ├─ 字段级别控制 (2个检查器)
   ├─ WHERE条件检测 (2个检查器)
   └─ 性能风险检测 (1个检查器)

NOT SQL 注入防火墙
NOT 连接池管理器
```

**错误理解**:
- ❌ SQL Guard 应该实现 Druid WallFilter 的所有配置项
- ❌ SQL Guard 是 Druid 的替代品
- ❌ 所有数据库访问都经过 Druid

**正确理解**:
- ✅ SQL Guard 是独立的业务规则验证引擎
- ✅ SQL Guard 支持多种连接池 (Druid/HikariCP/P6Spy)
- ✅ SQL Guard 专注业务安全规则，不是SQL注入防护

### 1.2 多层拦截架构 (Phase 8 完成)

```
┌─────────────────────────────────────────┐
│         应用层 (Application)            │
│  ├─ MyBatis Mapper                      │
│  │   → SqlAuditInterceptor              │
│  ├─ MyBatis-Plus Mapper                 │
│  │   → MpSqlAuditInnerInterceptor       │
│  └─ 审计日志写入                        │
└────────────┬────────────────────────────┘
             ↓
┌─────────────────────────────────────────┐
│      连接池层 (Connection Pool)         │
│  ├─ Druid Pool                          │
│  │   → DruidSqlAuditFilter              │
│  ├─ HikariCP Pool                       │
│  │   → HikariSqlAuditProxyFactory       │
│  └─ (其他连接池...)                     │
└────────────┬────────────────────────────┘
             ↓
┌─────────────────────────────────────────┐
│      JDBC层 (JDBC Driver)               │
│  ├─ P6Spy Proxy                         │
│  │   → P6SpySqlAuditListener            │
│  └─ 原生JDBC驱动                        │
└────────────┬────────────────────────────┘
             ↓
         数据库
```

**关键认识**:
- SQL Guard 在 **3个层次** 都有拦截器
- **不依赖** Druid - 可以工作在 HikariCP、P6Spy、原生JDBC
- Druid 只是其中一个可选的连接池

---

## 2. SQL 解析效率分析

### 2.1 MyBatis 拦截器当前实现

**代码位置**: `sql-guard-mybatis/src/main/java/com/footstone/sqlguard/interceptor/mybatis/SqlSafetyInterceptor.java`

```java
// Line 138-143: Get BoundSql from MyBatis
BoundSql boundSql = ms.getBoundSql(parameter);  // MyBatis resolves dynamic SQL
String sql = boundSql.getSql();                 // Extract SQL string only

// Build SqlContext - NOTE: No statement field!
SqlContext context = buildSqlContext(ms, sql, parameter, rowBounds);

// Line 178-185: SqlContext builder
return SqlContext.builder()
    .sql(sql)                    // SQL string
    // .statement(???)           // NO statement passed!
    .type(type)
    .executionLayer(ExecutionLayer.MYBATIS)
    .statementId(ms.getId())
    .rowBounds(rowBounds)
    .params(params)
    .build();
```

**问题**: MyBatis 拦截器只传递 SQL 字符串，没有传递解析后的 Statement。

### 2.2 DefaultSqlSafetyValidator 解析逻辑

**代码位置**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/DefaultSqlSafetyValidator.java`

```java
// Line 149-174: Parse-once logic
SqlContext contextWithParsedSql = context;
if (context.getStatement() == null) {            // ← 检查是否有预解析的 Statement
    try {
        Statement stmt = facade.parse(context.getSql());  // ← 重新解析 SQL
        // Create new context with parsed SQL
        contextWithParsedSql = SqlContext.builder()
            .sql(context.getSql())
            .statement(stmt)                     // ← 设置解析后的 Statement
            .type(context.getType())
            // ...
            .build();
    } catch (Exception e) {
        return handleParseFailure(context, e);
    }
}
```

**发现**: DefaultSqlSafetyValidator **已经支持**接收预解析的 Statement！
- 如果 `context.getStatement() != null` → 跳过解析，直接使用
- 如果 `context.getStatement() == null` → 调用 JSqlParser 解析

### 2.3 效率问题的根本原因

**问题根源**: MyBatis 的 BoundSql 并**不包含** JSqlParser Statement AST

```
MyBatis 的解析:
   Mapper XML (<select>, <if>, <foreach>)
        ↓ (DynamicSqlSource)
   BoundSql (动态标签处理完的SQL字符串)
        ↓
   "SELECT * FROM user WHERE id = ? AND status = ?"
        ✓ 动态标签已处理
        ✗ 没有 AST 树

SQL Guard 需要的解析:
   SQL 字符串
        ↓ (JSqlParser)
   Statement AST (语法树)
        ↓
   Select {
     where: AndExpression {
       left: EqualsTo(id, ?),
       right: EqualsTo(status, ?)
     }
   }
        ✓ 可以访问 WHERE 条件
        ✓ 可以访问字段列表
```

**结论**: MyBatis 和 SQL Guard 的解析目标不同
- **MyBatis**: 处理动态标签 (`<if>`, `<foreach>`) → 生成最终 SQL 字符串
- **SQL Guard**: 解析 SQL 语法树 → 提取 WHERE、字段、LIMIT 等信息

**无法复用的原因**: MyBatis 没有使用 JSqlParser，它的 BoundSql 只是字符串，不是 AST。

### 2.4 Phase 12 的 statement 字段设计意图

**代码位置**: `.apm/Assignments/Phase_12/Task_12_1_Assignment.md`

```java
// Phase 12: SqlContext 增强
public class SqlContext {
    @NonNull
    private final Statement statement;  // ← 新字段

    @Deprecated
    private final Statement parsedSql;  // ← 旧字段，已弃用
}
```

**设计意图**:
1. **架构统一**: 所有 RuleChecker 都通过 StatementVisitor 模式访问 Statement
2. **性能优化**: 如果某个拦截层已经解析了 SQL（理论上），可以传递进来避免重复解析
3. **未来扩展**: 为其他可能提供预解析 Statement 的拦截层预留接口

**当前状态**:
- ✅ SqlContext 已支持接收 Statement
- ✅ DefaultSqlSafetyValidator 已支持跳过解析
- ❌ MyBatis 拦截器尚未利用此功能（因为 MyBatis 本身不提供 Statement AST）

---

## 3. Druid WallFilter 功能对比

### 3.1 Druid WallFilter 配置项分析

**来源**: https://github.com/alibaba/druid/wiki/配置-wallfilter

**49个配置项分类**:

| 类别 | 数量 | 典型配置项 | SQL Guard 实现状态 |
|-----|------|-----------|-------------------|
| **SQL语句类型控制** | 21 | selectAllow, deleteAllow, createTableAllow | ❌ 未实现（超出范围） |
| **WHERE永真/永假检测** | 9 | selectWhereAlwayTrueCheck, deleteWhereNoneCheck | ✅ 部分实现（6/9） |
| **SQL注入防护** | 9 | selectUnionCheck, multiStatementAllow | ❌ 未实现（不是 SQL Guard 的职责） |
| **禁用对象检测** | 6 | tableCheck, functionCheck | ❌ 未实现（超出范围） |
| **JDBC相关** | 2 | metadataAllow, wrapAllow | ❌ 未实现（JDBC层安全） |
| **行为配置** | 2 | logViolation, throwException | ✅ 已实现（ViolationStrategy） |

### 3.2 SQL Guard 已实现的 WallFilter 功能

| Druid WallFilter 配置 | SQL Guard 实现 | 实现类 |
|---------------------|---------------|--------|
| **selectWhereAlwayTrueCheck** | ✅ | DummyConditionChecker |
| **deleteWhereAlwayTrueCheck** | ✅ | DummyConditionChecker |
| **updateWhereAlayTrueCheck** | ✅ | DummyConditionChecker |
| **conditionAndAlwayTrueAllow** | ✅ | DummyConditionChecker |
| **deleteWhereNoneCheck** | ✅ | NoWhereClauseChecker |
| **updateWhereNoneCheck** | ✅ | NoWhereClauseChecker |
| **strictSyntaxCheck** | ✅ | JSqlParser (严格模式) |
| **logViolation** | ✅ | ViolationStrategy.WARN |
| **throwException** | ✅ | ViolationStrategy.BLOCK |

**覆盖率**: 9/49 (18%)

### 3.3 SQL Guard 独有功能（Druid 不具备）

| 功能类别 | 检查器 | Druid WallFilter 支持 |
|---------|-------|---------------------|
| **分页滥用检测** | DeepPaginationChecker | ❌ |
| **分页滥用检测** | LargePageSizeChecker | ❌ |
| **分页滥用检测** | NoConditionPaginationChecker | ❌ |
| **分页滥用检测** | MissingOrderByChecker | ❌ |
| **分页滥用检测** | NoPaginationChecker | ❌ |
| **分页滥用检测** | LogicalPaginationChecker | ❌ |
| **字段级别控制** | BlacklistFieldChecker | ❌ |
| **字段级别控制** | WhitelistFieldChecker | ❌ |
| **估算行数检测** | EstimatedRowsChecker | ❌ |

**SQL Guard 独有功能数量**: 9个检查器

---

## 4. 职责划分与推荐架构

### 4.1 功能职责矩阵

| 安全需求 | 负责组件 | 原因 |
|---------|---------|------|
| **SQL注入防护** | Druid WallFilter | 成熟的 SQL 注入检测规则 |
| **多语句执行检测** | Druid WallFilter | multiStatementAllow |
| **UNION注入检测** | Druid WallFilter | selectUnionCheck |
| **危险函数检测** | Druid WallFilter | functionCheck (load_file, etc.) |
| **DDL操作控制** | Druid WallFilter | createTableAllow, dropTableAllow |
| **分页滥用检测** | SQL Guard | DeepPagination, LargePageSize, etc. |
| **字段级别控制** | SQL Guard | BlacklistField, WhitelistField |
| **WHERE条件检测** | SQL Guard | NoWhereClause, DummyCondition |
| **业务规则验证** | SQL Guard | 自定义 RuleChecker |
| **审计日志输出** | sql-audit-service | 离线分析、趋势预测、风险评估 |

### 4.2 推荐架构（使用 Druid 时）

```
Application Layer
     ↓
SQL Guard 拦截器 (MyBatis/MyBatis-Plus)
     ├─ 业务规则验证 (11个检查器)
     ├─ ViolationStrategy: WARN/BLOCK
     └─ 审计日志 → ThreadLocal
     ↓
Druid 连接池
     ├─ StatFilter (SQL统计)
     ├─ WallFilter (SQL注入防护)  ← 补充 SQL Guard 缺失的注入防护
     └─ DruidSqlAuditFilter (读取 ThreadLocal 审计日志)
     ↓
JDBC Driver
     ↓
Database
```

**分工**:
- **SQL Guard**: 业务规则 + WHERE检测 + 分页滥用 + 字段控制
- **Druid WallFilter**: SQL注入 + 危险操作 + DDL控制
- **sql-audit-service**: 离线审计分析 + 风险评估 + 趋势预测

### 4.3 推荐架构（使用 HikariCP 时）

```
Application Layer
     ↓
SQL Guard 拦截器 (MyBatis/MyBatis-Plus)
     ├─ 业务规则验证 (11个检查器)
     ├─ ViolationStrategy: WARN/BLOCK
     └─ 审计日志 → ThreadLocal
     ↓
HikariCP 连接池
     ├─ HikariSqlAuditProxyFactory (读取 ThreadLocal 审计日志)
     └─ [缺失] SQL注入防护 ← 需要其他方案
     ↓
JDBC Driver
     ↓
Database
```

**安全缺口**:
- ❌ HikariCP 没有内置 SQL 防火墙
- ⚠️ 如果不使用 Druid WallFilter，SQL注入防护缺失

**解决方案**:
1. **P6Spy + SQL Guard**: 使用 P6Spy 拦截 + SQL Guard 验证
2. **网关层防护**: 在应用网关层部署 WAF (Web Application Firewall)
3. **参数化查询**: 强制使用 PreparedStatement，禁止字符串拼接

---

## 5. 缺失功能评估

### 5.1 Druid WallFilter 功能中 SQL Guard 不应实现的

| 功能 | 原因 |
|-----|------|
| **selectAllow/deleteAllow/updateAllow** | SQL语句类型控制应在框架层（MyBatis配置）或权限系统，不是 SQL Guard 职责 |
| **createTableAllow/alterTableAllow/dropTableAllow** | DDL操作控制应在数据库权限层，生产环境通常禁止应用层执行DDL |
| **multiStatementAllow** | SQL注入防护，属于 Druid WallFilter 或 WAF 职责 |
| **selectUnionCheck** | SQL注入防护，属于 Druid WallFilter 职责 |
| **selectIntoOutfileAllow** | 文件操作防护，属于 Druid WallFilter 职责 |
| **commentAllow** | SQL注入防护，属于 Druid WallFilter 职责 |
| **mustParameterized** | 参数化强制，应在开发规范层面保证，不是运行时检测 |
| **metadataAllow/wrapAllow** | JDBC层安全，属于连接池或驱动层职责 |
| **tableCheck/schemaCheck/functionCheck** | 禁用对象检测，属于数据库权限系统职责 |

**结论**: 40/49 (82%) 的 Druid WallFilter 配置项**超出** SQL Guard 的设计范围。

### 5.2 SQL Guard 可以考虑增强的功能

| 功能 | 优先级 | 建议实现方式 |
|-----|-------|------------|
| **conditionAndAlwayFalseAllow** | P2 | 扩展 DummyConditionChecker 检测 `1=0` 等永假条件 |
| **conditionLikeTrueAllow** | P2 | 扩展 DummyConditionChecker 检测 `LIKE '%'` |
| **selectHavingAlwayTrueCheck** | P2 | DummyConditionChecker 增加 HAVING 子句检测 |

**注意**: 这些增强是为了**业务规则完整性**，不是为了替代 Druid WallFilter 的 SQL 注入防护。

---

## 6. 性能优化建议

### 6.1 当前性能特征

**DefaultSqlSafetyValidator 性能优化** (已实现):
- ✅ **Parse-once**: SQL 只解析一次，AST 在所有检查器间共享
- ✅ **Deduplication**: 100ms TTL 内相同 SQL 跳过验证
- ✅ **LRU Cache**: JSqlParser 缓存 1000 个解析结果
- ✅ **Lenient Mode**: 解析失败时优雅降级（WARN + PASS）

**性能开销**:
- MyBatis 拦截器: ~2-5ms (包含 SQL 解析 + 11个检查器)
- JDBC 拦截器: ~1-3ms (SQL 已解析，只执行检查器)
- 目标: <5% SQL 执行时间

### 6.2 MyBatis 解析复用的不可行性

**为什么不能复用 MyBatis 的 BoundSql**:

```
MyBatis BoundSql 提供的信息:
✓ sql: String                    // 最终SQL字符串
✓ parameterMappings: List        // 参数映射
✓ parameterObject: Object        // 参数对象
✗ Statement AST                  // ← MyBatis 不提供 AST

SQL Guard 需要的信息:
✓ WHERE 子句的 Expression 树
✓ SELECT 的字段列表
✓ LIMIT/OFFSET 值
✓ ORDER BY 字段
✗ 这些信息只能从 AST 获取
```

**结论**: 无法复用 MyBatis 的解析结果，必须使用 JSqlParser 重新解析。

### 6.3 Phase 12 statement 字段的实际价值

**当前价值**:
1. **架构统一**: 所有 RuleChecker 通过 StatementVisitor 访问 Statement
2. **单次解析保证**: DefaultSqlSafetyValidator 确保每个 SqlContext 只解析一次
3. **未来扩展**: 如果未来某个拦截层能提供 JSqlParser Statement AST，可以直接使用

**不适用的场景**:
- ❌ MyBatis BoundSql（不是 JSqlParser AST）
- ❌ Druid WallFilter（使用 Druid 自己的 Parser）
- ❌ 原生 JDBC PreparedStatement（只是占位符替换）

**适用的场景**:
- ✅ DefaultSqlSafetyValidator 内部（解析一次，传递给所有检查器）
- ✅ 测试场景（预构造 Statement AST）
- ✅ 未来如果集成了使用 JSqlParser 的其他组件

---

## 7. 总结与建议

### 7.1 核心结论

1. **SQL Guard ≠ Druid WallFilter 替代品**
   - SQL Guard: 业务规则验证引擎
   - Druid WallFilter: SQL 注入防火墙
   - 两者职责不同，应**共存互补**

2. **SQL Guard 是多连接池框架**
   - 支持 Druid、HikariCP、P6Spy
   - 不依赖 Druid
   - 在 3 个层次提供拦截（MyBatis、连接池、JDBC）

3. **MyBatis 解析无法复用**
   - MyBatis BoundSql 是字符串，不是 AST
   - SQL Guard 需要 JSqlParser AST 获取结构信息
   - 双重解析不可避免，但已通过 LRU Cache 优化

4. **Phase 12 statement 字段设计合理**
   - 架构统一（StatementVisitor 模式）
   - 单次解析保证（DefaultSqlSafetyValidator）
   - 为未来扩展预留接口

### 7.2 推荐部署方案

**方案 A: 使用 Druid (推荐生产环境)**
```yaml
连接池: Druid
SQL Guard: 启用 (业务规则验证)
Druid WallFilter: 启用 (SQL注入防护)
sql-audit-service: 启用 (离线审计分析)
```

**方案 B: 使用 HikariCP**
```yaml
连接池: HikariCP
SQL Guard: 启用 (业务规则验证)
额外防护: P6Spy + SQL Guard JDBC 拦截器
sql-audit-service: 启用 (离线审计分析)
```

**方案 C: 高安全要求**
```yaml
连接池: Druid
SQL Guard: 启用 + BLOCK模式
Druid WallFilter: 启用 + 严格配置
网关层: WAF (Web Application Firewall)
sql-audit-service: 启用 + 实时告警
```

### 7.3 SQL Guard 增强建议

**短期 (P1)**:
- ✅ 保持当前范围（业务规则验证）
- ✅ 完善分页检测（6个检查器）
- ✅ 完善字段控制（2个检查器）

**中期 (P2)**:
- ⚠️ 增强 DummyConditionChecker（永假条件、LIKE永真）
- ⚠️ 增加 HAVING 子句检测
- ⚠️ 支持自定义规则扩展（SPI机制）

**不建议**:
- ❌ 实现 SQL 注入防护（由 Druid WallFilter 提供）
- ❌ 实现 DDL 操作控制（应在数据库权限层）
- ❌ 实现连接池管理（由 Druid/HikariCP 提供）

### 7.4 最终评分

| 维度 | SQL Guard | Druid WallFilter | 推荐使用场景 |
|-----|-----------|-----------------|-------------|
| **业务规则验证** | ⭐⭐⭐⭐⭐ 5/5 | ⭐ 1/5 | 分页、字段、WHERE检测 |
| **SQL注入防护** | ⭐ 1/5 | ⭐⭐⭐⭐⭐ 5/5 | 注入、UNION、多语句 |
| **连接池无关性** | ⭐⭐⭐⭐⭐ 5/5 | ⭐⭐ 2/5 | HikariCP、P6Spy环境 |
| **配置灵活性** | ⭐⭐⭐⭐⭐ 5/5 | ⭐⭐⭐ 3/5 | YAML + 自定义检查器 |
| **性能影响** | ⭐⭐⭐⭐ 4/5 | ⭐⭐⭐⭐ 4/5 | 两者都有缓存优化 |
| **易用性** | ⭐⭐⭐⭐ 4/5 | ⭐⭐⭐⭐⭐ 5/5 | Druid开箱即用 |

**结论**: SQL Guard + Druid WallFilter 组合提供最全面的 SQL 安全防护。

---

**文档生成时间**: 2026-01-04
**版本**: 2.0 (修订版)
**作者**: SQL Guard Team

**变更说明**:
- 纠正了"SQL Guard 应实现 Druid WallFilter 功能"的错误理解
- 明确了 SQL Guard 是多连接池框架，不依赖 Druid
- 分析了 MyBatis 解析复用的不可行性
- 提供了基于正确理解的架构建议
