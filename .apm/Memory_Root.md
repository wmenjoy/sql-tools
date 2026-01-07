# SQL Guard 完整安全功能实现 - Memory Root

## 项目愿景

**核心目标**: 将 SQL Guard 打造成**独立完整的 SQL 安全框架**，实现灵活组合、功能完整的安全防护体系。

**关键原则**:
- ✅ **独立性**: 不依赖 Druid WallFilter，支持多种连接池（Druid/HikariCP/P6Spy）
- ✅ **完整性**: 实现所有关键安全功能，包括 SQL 注入防护、DDL 控制、危险操作检测
- ✅ **灵活性**: 通过配置实现 WARN/BLOCK/AUDIT 三种策略，支持业务场景自由组合
- ✅ **高质量**: TDD 优先，测试用例先行，确保代码质量和可维护性

---

## 技术上下文

### 现有基础设施（Phase 12 已完成）

**AbstractRuleChecker 模板方法模式**:
```java
public abstract class AbstractRuleChecker implements StatementVisitor {
    public final ValidationResult check(Statement statement, SqlContext context) {
        statement.accept(this);
        return getResult();
    }

    public void visitSelect(Select select, SqlContext context) {}
    public void visitUpdate(Update update, SqlContext context) {}
    public void visitDelete(Delete delete, SqlContext context) {}
    public void visitInsert(Insert insert, SqlContext context) {}
}
```

**SqlContext 增强**（支持预解析 Statement）:
```java
public class SqlContext {
    @NonNull
    private final Statement statement;  // Phase 12 新增
    @NonNull
    private final String sql;
    private final SqlType type;
    private final ExecutionLayer executionLayer;
    private final String statementId;
    private final Map<String, Object> params;
}
```

**性能优化机制**:
- ✅ Parse-once: SQL 只解析一次，AST 在所有检查器间共享
- ✅ Deduplication: 100ms TTL 内相同 SQL 跳过验证
- ✅ LRU Cache: JSqlParser 缓存 1000 个解析结果
- ✅ Lenient Mode: 解析失败时优雅降级（WARN + PASS）

**多层拦截架构**:
```
应用层: MyBatis/MyBatis-Plus 拦截器
    ↓
连接池层: Druid/HikariCP/其他
    ↓
JDBC层: P6Spy/原生JDBC
```

---

## 实施需求

### 功能范围：11 个 RuleChecker

**从 17 个原始需求优化到 11 个 Checker**（代码量减少 35%）:

#### Group 1: SQL 注入防护（4 个 Checkers）
1. **MultiStatementChecker** - 检测分号分隔的多语句执行
   - 风险等级: CRITICAL
   - 功能: 防止 `SELECT * FROM user; DROP TABLE user--` 类型注入

2. **SetOperationChecker** - 检测 UNION/MINUS/EXCEPT/INTERSECT 注入
   - 风险等级: CRITICAL
   - 功能: 统一检测集合操作注入，通过配置控制允许的类型

3. **SqlCommentChecker** - 检测 SQL 注释（--、/**/、#）
   - 风险等级: CRITICAL
   - 功能: 防止注释绕过检测

4. **IntoOutfileChecker** - 检测 SELECT INTO OUTFILE 文件操作
   - 风险等级: CRITICAL
   - 功能: 防止文件写入攻击

#### Group 2: DDL 和危险操作（3 个 Checkers）
5. **DdlOperationChecker** - 检测 CREATE/ALTER/DROP/TRUNCATE 操作
   - 风险等级: CRITICAL
   - 功能: 统一检测所有 DDL 操作，通过配置控制允许的类型

6. **DangerousFunctionChecker** - 检测 load_file、sys_exec 等危险函数
   - 风险等级: CRITICAL
   - 功能: 检测和阻止危险函数调用

7. **CallStatementChecker** - 检测存储过程调用
   - 风险等级: HIGH
   - 功能: 监控和控制存储过程调用

#### Group 3: 元数据和表访问控制（4 个 Checkers）
8. **MetadataStatementChecker** - 检测 SHOW/DESCRIBE/USE 语句
   - 风险等级: HIGH
   - 功能: 防止元数据泄露

9. **SetStatementChecker** - 检测 SET 语句（会话变量修改）
   - 风险等级: MEDIUM
   - 功能: 防止应用层修改会话变量

10. **DeniedTableChecker** - 检测禁用表访问（黑名单）
    - 风险等级: HIGH
    - 功能: 表级别黑名单访问控制，支持通配符

11. **ReadOnlyTableChecker** - 检测只读表的写操作
    - 风险等级: HIGH
    - 功能: 表级别只读保护，支持通配符

---

## 实施方法论

### TDD 实施流程（严格遵守）

每个 Checker 的开发流程：

```
1. 编写测试用例
   ├─ PASS 场景：至少 5 个合法 SQL
   ├─ FAIL 场景：至少 10 个违规 SQL
   └─ 边界场景：NULL、空字符串、嵌套 SQL、多方言

2. 实现 Checker 代码
   ├─ 继承 AbstractRuleChecker
   ├─ 实现 visitXxx 方法（StatementVisitor 模式）
   └─ 让所有测试通过

3. 集成测试
   ├─ 验证与 DefaultSqlSafetyValidator 集成
   ├─ 验证配置加载和应用
   └─ 验证 ViolationStrategy（WARN/BLOCK/AUDIT）

4. 编写文档
   ├─ 创建规则文档（docs/user-guide/rules/xxx.md）
   ├─ 更新总览文档（docs/user-guide/rules/README.md）
   └─ 更新 CHANGELOG.md

5. Code Review
   └─ 确保代码质量和文档完整性
```

### 并行开发策略

**依赖关系分析**:
- ✅ 所有 Checker 继承自 AbstractRuleChecker（Phase 12 已完成）
- ✅ 所有 Checker 使用 SqlContext（Phase 12 已完成）
- ✅ StatementVisitor 模式已就绪（Phase 12 已完成）
- ✅ Checker 之间无任何依赖关系

**并行方案**: 11 个 Checker 可完全并行开发

### 配置统一标准

所有 Checker 必须遵循统一的配置格式：

```yaml
sql-guard:
  rules:
    <checker-id>:
      # 标准配置（所有 Checker 必须）
      enabled: true
      violation-strategy: BLOCK  # WARN | BLOCK | AUDIT
      exemptions:
        - statementId: "com.example.batch.*"

      # 特定配置（根据 Checker 功能）
      <specific-config>: <value>
```

**配置示例**:
```yaml
sql-guard:
  rules:
    # SQL 注入防护
    multi-statement:
      enabled: true
      violation-strategy: BLOCK

    set-operation:
      enabled: true
      violation-strategy: BLOCK
      allowed-operations: []  # 空 = 禁止所有

    sql-comment:
      enabled: true
      violation-strategy: BLOCK
      allow-hint-comments: false

    # DDL 和危险操作
    ddl-operation:
      enabled: true
      violation-strategy: BLOCK
      allowed-operations: []
      exemptions:
        - statementId: "com.example.migration.*"

    dangerous-function:
      enabled: true
      violation-strategy: BLOCK
      denied-functions:
        - load_file
        - into_outfile
        - sys_exec

    # 元数据和表访问控制
    denied-table:
      enabled: true
      violation-strategy: BLOCK
      denied-tables:
        - sys_user
        - admin_config
        - sys_*

    readonly-table:
      enabled: true
      violation-strategy: BLOCK
      readonly-tables:
        - audit_log
        - history_*
```

---

## 质量标准

### 性能要求
- 单个 Checker 检测时间: <5ms
- 整体验证性能影响: <10% overhead
- 维持单次 SQL 解析策略（通过 context.getStatement()）

### 代码规范
- 遵循现有命名规范：`xxxChecker`, `xxxConfig`
- 所有公共方法必须有 Javadoc 注释
- 错误信息清晰明确（参考现有 Checker 格式）
- 测试覆盖率: >80%

### 完成定义（Definition of Done）

每个 Checker 完成的标准：
- ✅ 单元测试通过（PASS/FAIL/边界场景）
- ✅ 集成测试通过
- ✅ Config 类实现并测试
- ✅ 规则文档编写完成
- ✅ 总览文档和 CHANGELOG 更新
- ✅ Code Review 通过
- ✅ 性能测试满足要求（<5ms）

---

## 参考资料

### 核心分析文档

1. **SQL-GUARD-FEATURE-GAP-ANALYSIS.md**
   - Druid WallFilter 49 个配置项对比分析
   - 识别 SQL Guard 缺失的 9 个 P0 + 6 个 P1 功能
   - 职责划分和推荐架构

2. **SQL-GUARD-ARCHITECTURE-ANALYSIS.md**
   - 多层拦截架构详解
   - MyBatis 解析复用可行性分析
   - Phase 12 statement 字段设计意图

3. **AUDIT-LOG-ARCHITECTURE-ANALYSIS.md**
   - LogbackAuditWriter 架构验证
   - 性能特征分析（<1ms p99, >10K events/sec）
   - 测试环境策略和 SQL Audit Service 角色

4. **Implementation_Plan.md**
   - 11 个 Checker 详细实施计划
   - TDD 工作流规范
   - 配置标准和质量要求

### 现有代码参考

- **现有 Checker 实现**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/impl/`
- **现有测试用例**: `sql-guard-core/src/test/java/com/footstone/sqlguard/validator/rule/`
- **AbstractRuleChecker**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/rule/AbstractRuleChecker.java`
- **DefaultSqlSafetyValidator**: `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/DefaultSqlSafetyValidator.java`

---

## 实施里程碑

### Milestone 1: Group 1 完成（SQL 注入防护）
- 4 个 Checker 实现
- 测试覆盖完成（每个 Checker ≥18 个测试用例）
- 文档编写完成

### Milestone 2: Group 2 完成（DDL 和危险操作）
- 3 个 Checker 实现
- 测试覆盖完成
- 文档编写完成

### Milestone 3: Group 3 完成（元数据和表访问控制）
- 4 个 Checker 实现
- 测试覆盖完成
- 文档编写完成

### Milestone 4: 集成和验收
- 所有 Checker 集成到 RuleCheckerOrchestrator
- 默认配置和 Profile 准备
- 用户文档和示例更新
- 性能测试和优化

---

## 验收标准

### 功能验收
- ✅ 所有 11 个 Checker 实现并通过测试
- ✅ 配置格式统一，支持 YAML 加载
- ✅ 集成到 DefaultSqlSafetyValidator 无问题
- ✅ ViolationStrategy（WARN/BLOCK/AUDIT）正常工作

### 性能验收
- ✅ 单个 Checker 平均检测时间 <5ms
- ✅ 11 个 Checker 全部启用时，总体性能影响 <10%
- ✅ 去重缓存和 JSqlParser 缓存正常工作

### 文档验收
- ✅ 每个 Checker 有独立的规则文档
- ✅ docs/user-guide/rules/README.md 更新
- ✅ CHANGELOG.md 更新
- ✅ 配置示例和最佳实践文档完成

### 测试验收
- ✅ 单元测试覆盖率 >80%
- ✅ 集成测试覆盖所有 ViolationStrategy
- ✅ 多数据库方言测试（MySQL/Oracle/PostgreSQL/SQL Server）

---

**文档版本**: 1.0
**创建日期**: 2026-01-04
**最后更新**: 2026-01-04
**状态**: 活跃开发
