# Implementation Plan Phase 11-13 重构方案

**创建时间**: 2025-12-19
**基于**: Architecture_Review_Report.md 系统性审视结论

---

## 一、重构原因

### 1.1 现有 Phase 11 问题

**原 Phase 11** 混合了三类不同性质的工作：

```
Task 11.1-11.5: JDBC 模块拆分
Task 11.6-11.7: 版本兼容层
Task 11.8: CI/CD 测试矩阵
```

**新架构文档** 增加了原计划之外的工作：
- RuleChecker 接口重构
- SqlContext 重构
- StatementVisitor 统一抽象
- InnerInterceptor 架构实现

**导致问题**：
1. ❌ 范围扩大：从 19 天增加到 36+ 天
2. ❌ 风险集中：所有变更一次性完成，难以回退
3. ❌ 依赖混乱：RuleChecker 重构影响所有任务
4. ❌ 测试负担：46 个测试文件需要迁移

### 1.2 重构策略

采用 **渐进式重构 (Incremental Refactoring)** 策略：

```
Phase 11: 模块拆分（不改接口）
  ↓ 独立交付，风险低
Phase 12: 架构统一（重构接口）
  ↓ 独立交付，有测试保护
Phase 13: InnerInterceptor（新架构）
  ↓ 独立交付，与现有并行
```

**优势**：
- ✅ 每个 Phase 独立交付价值
- ✅ 风险可控，可以回退
- ✅ 测试增量进行
- ✅ 时间估算准确

---

## 二、新 Phase 结构

### Phase 11: JDBC 模块拆分（10 天）

**目标**: 拆分 sql-guard-jdbc 为独立连接池模块，**不改现有接口**

**原则**:
- ✅ 只拆分模块结构
- ✅ 保持现有 API 不变
- ✅ 100% 向后兼容
- ✅ 不涉及架构重构

**任务清单**:

#### Task 11.1: 测试用例库设计（2 天）
- **范围**: 只针对模块拆分的测试用例
- **输出**:
  - 模块隔离测试用例
  - 依赖验证测试用例
  - 向后兼容测试用例
  - **不包含**: 架构重构相关测试

#### Task 11.2: JDBC Common 模块提取（2 天）
- **输出**:
  - sql-guard-jdbc-common 模块
  - ViolationStrategy 统一枚举
  - JdbcInterceptorBase 抽象基类
  - 公共配置接口
- **保持**:
  - 现有 Checker 接口不变
  - 现有 Interceptor 接口不变

#### Task 11.3-11.5: 模块拆分（4 天，可并行）
- Task 11.3: Druid 模块独立（2 天）
- Task 11.4: HikariCP 模块独立（2 天）
- Task 11.5: P6Spy 模块独立（2 天）

**每个模块**:
- 独立 Maven 模块
- 只依赖 sql-guard-jdbc-common
- 只依赖对应连接池（provided scope）
- 迁移现有代码，不改逻辑

#### Task 11.6: 集成测试（2 天）
- 验证模块隔离
- 验证向后兼容
- 性能回归测试
- 验收测试

**验收标准**:
- [ ] sql-guard-jdbc-common 模块独立编译
- [ ] sql-guard-jdbc-druid/hikari/p6spy 模块独立运行
- [ ] 现有测试 100% 通过
- [ ] 性能无回退
- [ ] 用户只引入需要的模块，无多余依赖

**不包含的工作**:
- ❌ RuleChecker 重构
- ❌ SqlContext 重构
- ❌ StatementVisitor 引入
- ❌ InnerInterceptor 实现

---

### Phase 12: 核心架构统一（15 天）

**目标**: 统一 StatementVisitor 抽象，重构 RuleChecker，**解决重复解析问题**

**原则**:
- ✅ 解决 SQL 重复解析（N 次 → 1 次）
- ✅ 统一 Checker 和 InnerInterceptor 基础设施
- ✅ 渐进式迁移，支持过渡期

**任务清单**:

#### Task 12.1: SqlContext 重构（1 天）
- 添加 `statement` 字段（@NonNull）
- 保留 `parsedSql` 字段（@Deprecated）
- 添加兼容方法
```java
@Deprecated
public Statement getParsedSql() {
    return statement;  // 委托到新字段
}
```

#### Task 12.2: StatementVisitor 接口设计（1 天）
- 定义统一访问者接口
```java
public interface StatementVisitor {
    void visitSelect(Select select, SqlContext context);
    void visitUpdate(Update update, SqlContext context);
    void visitDelete(Delete delete, SqlContext context);
    void visitInsert(Insert insert, SqlContext context);
}
```

#### Task 12.3: RuleChecker 接口重构（2 天）
- RuleChecker 继承 StatementVisitor
- 保留 check() 方法（向后兼容）
```java
public interface RuleChecker extends StatementVisitor {
    void check(SqlContext context, ValidationResult result);  // 保留
    boolean isEnabled();
}
```

#### Task 12.4: AbstractRuleChecker 重构（2 天）
- 实现模板方法模式
- check() 方法变为 final，自动分发
```java
@Override
public final void check(SqlContext context, ValidationResult result) {
    Statement stmt = context.getStatement();
    if (stmt instanceof Select) {
        visitSelect((Select) stmt, context);
    }
    // ...
}
```

#### Task 12.5-12.8: Checker 迁移（6 天，部分并行）
- Task 12.5: NoWhereClauseChecker（1 天）
- Task 12.6: BlacklistFieldChecker（1 天）
- Task 12.7: WhitelistFieldChecker（1 天）
- Task 12.8: NoPaginationChecker（1 天）
- Task 12.9: 其他 Checker（2 天）

**每个 Checker**:
- 从 `check()` 迁移到 `visitXxx()`
- 删除内部解析逻辑
- 直接使用传入的 Statement
```java
// 旧实现
@Override
public void check(SqlContext context, ValidationResult result) {
    Statement stmt = JSqlParser.parse(context.getSql());  // ❌ 重复解析
    // ...
}

// 新实现
@Override
protected void visitUpdate(Update update, SqlContext context) {
    Expression where = update.getWhere();  // ✅ 直接使用
    // ...
}
```

#### Task 12.9: 测试迁移（2 天）
- 全局替换: `parsedSql` → `statement`
- 修改 Checker 构造（传入 ValidationResult）
- 新增 visitXxx() 直接测试
- 46 个测试文件迁移

#### Task 12.10: 性能验证（1 天）
- 验证解析次数：N 次 → 1 次
- 性能基准测试
- 对照旧版本结果

**验收标准**:
- [ ] 所有 Checker 迁移到新架构
- [ ] 所有测试通过
- [ ] SQL 解析次数显著减少
- [ ] 性能提升可测量
- [ ] 向后兼容性保持（过渡期）

**依赖**:
- ✅ Phase 11 完成（模块已拆分）

---

### Phase 13: InnerInterceptor 架构（20 天）

**目标**: 实现 MyBatis-Plus 风格拦截器，支持兜底功能（SelectLimit）

**原则**:
- ✅ 参考 MyBatis-Plus InnerInterceptor
- ✅ 支持优先级控制
- ✅ 实现 SelectLimitInnerInterceptor（业界首创）
- ✅ 多版本兼容（MyBatis 3.4.x/3.5.x, MyBatis-Plus 3.4.x/3.5.x）

**任务清单**:

#### Task 13.1: InnerInterceptor 接口设计（2 天）
- 定义 SqlGuardInnerInterceptor 接口
- 参考 MyBatis-Plus InnerInterceptor
```java
public interface SqlGuardInnerInterceptor {
    boolean willDoQuery(...);
    void beforeQuery(...);
    boolean willDoUpdate(...);
    void beforeUpdate(...);
    int getPriority();
}
```

#### Task 13.2: SqlGuardInterceptor 主拦截器（2 天）
- 实现 MyBatis Interceptor
- 管理 InnerInterceptor 链
- 优先级排序机制
```java
@Intercepts({...})
public class SqlGuardInterceptor implements Interceptor {
    private final List<SqlGuardInnerInterceptor> innerInterceptors;
    // ...
}
```

#### Task 13.3: SqlGuardCheckInnerInterceptor（2 天）
- 桥接 RuleChecker 和 InnerInterceptor
- 解析 SQL 并缓存到 ThreadLocal
```java
public class SqlGuardCheckInnerInterceptor implements SqlGuardInnerInterceptor {
    @Override
    public boolean willDoQuery(...) {
        Statement stmt = StatementContext.get(sql);
        if (stmt == null) {
            stmt = JsqlParserGlobal.parse(sql);
            StatementContext.cache(sql, stmt);  // ✅ 缓存
        }
        // 执行所有 Checker
    }
}
```

#### Task 13.4: SqlGuardRewriteInnerInterceptor（2 天）
- 桥接 StatementRewriter 和 InnerInterceptor
- 复用 ThreadLocal 缓存的 Statement
```java
public class SqlGuardRewriteInnerInterceptor implements SqlGuardInnerInterceptor {
    @Override
    public void beforeQuery(...) {
        Statement stmt = StatementContext.get(sql);  // ✅ 复用
        // 应用所有 Rewriter
    }
}
```

#### Task 13.5: SelectLimitInnerInterceptor（3 天）
- 实现自动添加 LIMIT 的兜底拦截器
- 支持多数据库方言（MySQL/Oracle/SQL Server/PostgreSQL）
```java
public class SelectLimitInnerInterceptor extends JsqlParserSupport
        implements SqlGuardInnerInterceptor {
    @Override
    protected String processSelect(Select select, ...) {
        if (hasPagination(select)) {
            return sql;  // 已有分页
        }
        // 添加 LIMIT
    }
}
```

#### Task 13.6: StatementContext（ThreadLocal 共享）（1 天）
- 实现 ThreadLocal 缓存
- 放在 sql-guard-core 模块
```java
public class StatementContext {
    private static final ThreadLocal<Map<String, Statement>> CACHE = ...;
    public static void cache(String sql, Statement statement) { ... }
    public static Statement get(String sql) { ... }
    public static void clear() { ... }
}
```

#### Task 13.7: MyBatis 版本兼容层（3 天）
- MyBatisVersionDetector
- SqlExtractor 接口和实现
- 支持 MyBatis 3.4.x/3.5.x

#### Task 13.8: MyBatis-Plus 版本兼容层（3 天）
- MyBatisPlusVersionDetector
- IPageDetector
- 支持 MyBatis-Plus 3.4.x/3.5.x

#### Task 13.9: CI/CD 多版本测试矩阵（2 天）
- GitHub Actions 测试矩阵
- 多版本兼容性测试
- 版本覆盖：
  - Java: 8, 11, 17, 21
  - MyBatis: 3.4.6, 3.5.6, 3.5.13, 3.5.16
  - MyBatis-Plus: 3.4.0, 3.4.3, 3.5.3, 3.5.5

**验收标准**:
- [ ] SqlGuardInterceptor + InnerInterceptor 架构正常工作
- [ ] SelectLimitInnerInterceptor 能自动添加 LIMIT
- [ ] 支持 MySQL/Oracle/SQL Server/PostgreSQL 四种数据库
- [ ] 检测类拦截器优先于兜底类拦截器
- [ ] 多版本兼容性测试全部通过
- [ ] SQL 解析只执行一次（ThreadLocal 共享）

**依赖**:
- ✅ Phase 11 完成（模块已拆分）
- ✅ Phase 12 完成（RuleChecker 已重构）

---

## 三、时间估算对比

### 3.1 原计划 vs 新计划

| Phase | 原计划 | 新计划 | 说明 |
|-------|--------|--------|------|
| **Phase 11** | 19 天 | 10 天 | 只模块拆分 |
| **Phase 12** | - | 15 天 | 架构统一（新增） |
| **Phase 13** | - | 20 天 | InnerInterceptor（新增） |
| **总计** | 19 天 | 45 天 | 实际工作量 |

**结论**：
- ✅ 新计划更准确：45 天 vs 原计划低估（19 天）
- ✅ 渐进式交付：每个 Phase 独立交付价值
- ✅ 风险分散：不是一次性完成所有变更

### 3.2 关键路径

```
Phase 11 (10 天)
  ↓
Phase 12 (15 天)
  ↓
Phase 13 (20 天)
───────────────────
总计: 45 工作日
```

**并行机会**:
- Phase 11: Task 11.3-11.5 可并行（Druid/HikariCP/P6Spy）
- Phase 12: Task 12.5-12.8 部分并行（不同 Checker）
- Phase 13: Task 13.7-13.8 可并行（MyBatis/MyBatis-Plus 版本兼容）

---

## 四、风险管理

### 4.1 Phase 11 风险（低）

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|------------|
| 模块依赖冲突 | 低 | 中 | Maven Enforcer 验证 |
| 向后兼容破坏 | 低 | 高 | 100% 测试覆盖 |
| 性能回退 | 低 | 中 | 性能基准测试 |

### 4.2 Phase 12 风险（中）

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|------------|
| Checker 迁移错误 | 中 | 高 | 逐个迁移，增量测试 |
| 测试迁移遗漏 | 中 | 中 | 字段重命名工具验证 |
| 性能未达预期 | 低 | 中 | 性能验证 Task |

### 4.3 Phase 13 风险（中）

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|------------|
| 版本兼容误判 | 低 | 高 | 多重检测机制 |
| 方言实现不完整 | 中 | 中 | 优先支持主流数据库 |
| ThreadLocal 内存泄漏 | 低 | 高 | 严格 finally 清理 |

---

## 五、向后兼容策略

### 5.1 Phase 11 兼容策略

**100% 向后兼容**:
- ✅ 现有 API 不变
- ✅ 模块结构透明
- ✅ 用户代码无需修改

### 5.2 Phase 12 兼容策略

**渐进式迁移（3 阶段）**:

**阶段 1: 添加新字段（Phase 12.1）**
```java
public class SqlContext {
    private Statement parsedSql;  // ✅ 保留旧字段
    private Statement statement;  // ✅ 新增新字段

    @Deprecated
    public Statement getParsedSql() {
        return statement != null ? statement : parsedSql;
    }
}
```

**阶段 2: 迁移代码（Phase 12.5-12.9）**
- 逐步迁移测试和 Checker
- parsedSql → statement

**阶段 3: 删除旧字段（下个大版本）**
```java
public class SqlContext {
    private Statement statement;  // ✅ 只保留新字段
}
```

### 5.3 Phase 13 兼容策略

**并行架构**:
- ✅ 新架构与现有代码并行
- ✅ 用户可选择启用 InnerInterceptor
- ✅ 现有 Interceptor 继续工作

---

## 六、依赖关系

### 6.1 Phase 依赖图

```
Phase 11: JDBC 模块拆分
  11.1 测试设计
    ↓
  11.2 JDBC Common
    ↓
  ┌─11.3 Druid───┐
  ├─11.4 HikariCP┼─ 并行
  └─11.5 P6Spy───┘
    ↓
  11.6 集成测试

Phase 12: 核心架构统一
  12.1 SqlContext 重构
    ↓
  12.2 StatementVisitor
    ↓
  12.3 RuleChecker 重构
    ↓
  12.4 AbstractRuleChecker
    ↓
  ┌─12.5 NoWhereClause────┐
  ├─12.6 BlacklistField───┤
  ├─12.7 WhitelistField───┼─ 部分并行
  ├─12.8 NoPagination─────┤
  ├─12.9 其他 Checker─────┤
  └────────────────────────┘
    ↓
  12.10 测试迁移
    ↓
  12.11 性能验证

Phase 13: InnerInterceptor 架构
  13.1 InnerInterceptor 接口
    ↓
  13.2 SqlGuardInterceptor
    ↓
  13.3 CheckInnerInterceptor
    ↓
  13.4 RewriteInnerInterceptor
    ↓
  13.5 SelectLimitInnerInterceptor
    ↓
  13.6 StatementContext
    ↓
  ┌─13.7 MyBatis 版本兼容─┐
  ├─13.8 MP 版本兼容──────┼─ 并行
  └────────────────────────┘
    ↓
  13.9 CI/CD 测试矩阵
```

### 6.2 任务依赖说明

**Phase 12 依赖 Phase 11**:
- 原因：RuleChecker 在 sql-guard-core，不依赖 JDBC 模块
- 理论上可以并行，但建议串行（降低复杂度）

**Phase 13 依赖 Phase 12**:
- 原因：InnerInterceptor 需要复用 StatementVisitor 抽象
- 必须串行

---

## 七、验收标准

### 7.1 Phase 11 验收

**功能验收**:
- [ ] sql-guard-jdbc-common 模块独立编译
- [ ] sql-guard-jdbc-druid/hikari/p6spy 模块独立运行
- [ ] 用户只引入需要的模块
- [ ] 现有测试 100% 通过

**架构验收**:
- [ ] 模块依赖隔离（无多余依赖）
- [ ] ViolationStrategy 统一
- [ ] JdbcInterceptorBase 抽象正确

**性能验收**:
- [ ] 性能无回退（< 原版本 110%）
- [ ] 模块加载无额外开销

### 7.2 Phase 12 验收

**功能验收**:
- [ ] 所有 Checker 迁移完成
- [ ] 所有测试通过
- [ ] 功能行为与旧版本一致

**架构验收**:
- [ ] StatementVisitor 统一抽象正确
- [ ] RuleChecker 继承 StatementVisitor
- [ ] AbstractRuleChecker 模板方法正确

**性能验收**:
- [ ] SQL 解析次数：N 次 → 1 次
- [ ] 总耗时 < 原版本 110%
- [ ] 无内存泄漏

### 7.3 Phase 13 验收

**功能验收**:
- [ ] SqlGuardInterceptor + InnerInterceptor 架构正常工作
- [ ] SelectLimitInnerInterceptor 自动添加 LIMIT
- [ ] 已有分页的 SQL 不被修改
- [ ] 检测类优先于兜底类

**架构验收**:
- [ ] 优先级排序机制正常
- [ ] ThreadLocal 缓存正确清理
- [ ] 多数据库方言支持

**兼容性验收**:
- [ ] MyBatis 3.4.6/3.5.6/3.5.13/3.5.16 全部通过
- [ ] MyBatis-Plus 3.4.0/3.4.3/3.5.3/3.5.5 全部通过
- [ ] Java 8/11/17/21 全部通过

---

## 八、后续 Phase 调整

**原 Phase 12**: Audit Platform Examples & Documentation
**调整为**: Phase 14

**原因**: Phase 11 拆分为 Phase 11/12/13，序号顺延

---

## 九、总结

### 9.1 重构要点

1. **渐进式交付**: 每个 Phase 独立交付价值
2. **风险可控**: 可以在任何 Phase 后停止或回退
3. **测试保护**: 每个 Phase 都有完整测试
4. **时间准确**: 45 天 vs 原计划 19 天（更真实）

### 9.2 关键决策

| 决策 | 原方案 | 新方案 | 原因 |
|-----|--------|--------|------|
| **Phase 划分** | 1 个 Phase | 3 个 Phase | 降低风险 |
| **向后兼容** | 不考虑 | 渐进式迁移 | 测试已用 parsedSql |
| **StatementContext 位置** | - | sql-guard-core | 全层可访问 |
| **并行策略** | - | 模块拆分可并行 | 加快进度 |

### 9.3 下一步行动

1. ✅ 更新 `.apm/Implementation_Plan.md`
2. ✅ 创建 Phase 11 任务分配文档
3. ✅ 创建 Phase 12 任务分配文档
4. ✅ 创建 Phase 13 任务分配文档
5. ✅ 更新 `.apm/Assignments/Phase_11/Phase_11_Overview.md`

---

**文档版本**: v1.0
**创建日期**: 2025-12-19
**基于**: Architecture_Review_Report.md 系统性审视结论
