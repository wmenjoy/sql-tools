# Phase 11: 架构重构与版本兼容

**阶段目标**: 基于 MyBatis-Plus 架构模式重构拦截器体系，实现 JDBC 层模块拆分和多版本兼容

**创建时间**: 2025-12-19
**预计工期**: 20-25 工作日

---

## 一、阶段目标

### 1.1 核心目标

1. **架构重构**: 参考 MyBatis-Plus InnerInterceptor 模式重构拦截器架构
2. **模块拆分**: 将 sql-guard-jdbc 拆分为独立的连接池专用模块
3. **版本兼容**: 支持 MyBatis 3.4.x/3.5.x 和 MyBatis-Plus 3.4.x/3.5.x 多版本
4. **兜底功能**: 实现 SelectLimitInnerInterceptor 自动添加 LIMIT 的兜底方案

### 1.2 关键创新

1. **SelectLimitInnerInterceptor**: 自动为无分页的 SELECT 添加 LIMIT（业界首创）
2. **统一插件架构**: SqlGuardInterceptor → SqlGuardInnerInterceptor → 具体实现
3. **优先级控制**: 检测类拦截器（1-99）优先，兜底类拦截器（100-199）最后
4. **运行时版本检测**: 自动检测 MyBatis 版本，零配置适配

---

## 二、任务分解

### Phase 11.1: TDD 测试用例库设计（3 天）

**负责人**: 待分配
**目标**: 基于 TDD 原则，先设计完整测试用例

**输出物**:
- 测试用例设计文档
- 测试数据库 Schema
- Mock 数据生成器

**详情**: 参见 `Task_11_1_Assignment.md`

---

### Phase 11.2: JDBC Common 模块提取（2 天）

**负责人**: 待分配
**目标**: 提取 JDBC 层公共抽象，为拆分做准备

**输出物**:
- sql-guard-jdbc-common 模块
- JdbcInterceptorBase 抽象基类
- ViolationStrategy 统一枚举
- SqlContextBuilder 工具类

**详情**: 参见 `Task_11_2_Assignment.md`

---

### Phase 11.3: Druid 模块独立（2 天）

**负责人**: 待分配
**目标**: 将 Druid 相关实现拆分为独立模块

**输出物**:
- sql-guard-jdbc-druid 模块
- DruidSqlSafetyFilter 迁移
- DruidSqlAuditFilter 实现
- Druid 专用配置类

**详情**: 参见 `Task_11_3_Assignment.md`

---

### Phase 11.4: HikariCP 模块独立（2 天）

**负责人**: 待分配
**目标**: 将 HikariCP 相关实现拆分为独立模块

**输出物**:
- sql-guard-jdbc-hikari 模块
- HikariSqlSafetyProxyFactory 迁移
- HikariSqlAuditProxyFactory 实现
- HikariCP 专用配置类

**详情**: 参见 `Task_11_4_Assignment.md`

---

### Phase 11.5: P6Spy 模块独立（2 天）

**负责人**: 待分配
**目标**: 将 P6Spy 相关实现拆分为独立模块

**输出物**:
- sql-guard-jdbc-p6spy 模块
- P6SpySqlSafetyListener 迁移
- P6SpySqlAuditModule 实现
- P6Spy 专用配置类

**详情**: 参见 `Task_11_5_Assignment.md`

---

### Phase 11.6: MyBatis 版本兼容层（3 天）

**负责人**: 待分配
**目标**: 实现 MyBatis 3.4.x 和 3.5.x 多版本兼容

**输出物**:
- MyBatisVersionDetector 版本检测器
- SqlExtractor 接口和实现
- IPageDetector MyBatis-Plus 检测器
- 兼容性测试用例

**详情**: 参见 `Task_11_6_Assignment.md`

---

### Phase 11.7: MyBatis-Plus InnerInterceptor 架构实现（5 天）

**负责人**: 待分配
**目标**: 参考 MyBatis-Plus 实现新的拦截器架构

**输出物**:
- SqlGuardInterceptor 主拦截器
- SqlGuardInnerInterceptor 接口
- SqlGuardJsqlParserSupport 基类
- 优先级排序机制

**详情**: 参见 `Task_11_7_Assignment.md`

---

### Phase 11.8: SelectLimit 兜底拦截器（4 天）

**负责人**: 待分配
**目标**: 实现自动添加 LIMIT 的兜底拦截器

**输出物**:
- SelectLimitInnerInterceptor 实现
- SqlGuardDialect 接口
- 多数据库方言实现（MySQL/Oracle/SQL Server/PostgreSQL）
- DialectFactory 工厂类

**详情**: 参见 `Task_11_8_Assignment.md`

---

### Phase 11.9: 检测类拦截器迁移（3 天）

**负责人**: 待分配
**目标**: 将现有 Checker 迁移到新架构

**输出物**:
- SqlGuardNoWhereInnerInterceptor
- SqlGuardFieldInnerInterceptor（黑白名单）
- SqlGuardPaginationInnerInterceptor
- SqlGuardConditionInnerInterceptor

**详情**: 参见 `Task_11_9_Assignment.md`

---

### Phase 11.10: CI/CD 多版本测试矩阵（2 天）

**负责人**: 待分配
**目标**: 建立多版本兼容性测试

**输出物**:
- GitHub Actions 测试矩阵
- Maven Profile 配置
- 兼容性测试报告模板

**详情**: 参见 `Task_11_10_Assignment.md`

---

## 三、技术参考

### 3.1 核心参考文档

1. **MyBatis-Plus 框架分析**: `.apm/Memory/Phase_11_Reference_Architecture/MyBatis_Plus_Framework_Analysis.md`
   - InnerInterceptor 接口设计
   - PaginationInnerInterceptor 实现
   - JsqlParserSupport 基类模式
   - 数据库方言设计

2. **SQL Guard 重设计方案**: `.apm/Memory/Phase_11_Reference_Architecture/SQL_Guard_Redesign_Proposal.md`
   - SqlGuardInnerInterceptor 接口
   - SelectLimitInnerInterceptor 设计
   - 优先级控制机制
   - 配置示例

3. **模块拆分与版本兼容**: `.apm/Memory/Phase_11_Reference_Architecture/Module_Separation_And_Version_Compatibility.md`
   - JDBC 层模块拆分方案
   - JdbcInterceptorBase 模板方法
   - MyBatis 版本检测策略
   - 反射适配器设计

### 3.2 MyBatis-Plus 源码

**已下载源码**: `/tmp/mybatis-plus-source/`

**关键文件**:
- `mybatis-plus-extension/src/main/java/com/baomidou/mybatisplus/extension/plugins/inner/InnerInterceptor.java`
- `mybatis-plus-extension/src/main/java/com/baomidou/mybatisplus/extension/plugins/inner/PaginationInnerInterceptor.java`
- `mybatis-plus-extension/src/main/java/com/baomidou/mybatisplus/extension/plugins/inner/TenantLineInnerInterceptor.java`
- `mybatis-plus-extension/src/main/java/com/baomidou/mybatisplus/extension/plugins/inner/BlockAttackInnerInterceptor.java`
- `mybatis-plus-jsqlparser/src/main/java/com/baomidou/mybatisplus/extension/parser/JsqlParserSupport.java`

---

## 四、依赖关系

### 4.1 任务依赖图

```
11.1 TDD 测试用例库设计
  ↓
11.2 JDBC Common 模块提取
  ↓
┌─────────┬─────────┬─────────┐
│  11.3   │  11.4   │  11.5   │  (可并行)
│ Druid   │ HikariCP│ P6Spy   │
└─────────┴─────────┴─────────┘
  ↓
11.6 MyBatis 版本兼容层
  ↓
11.7 InnerInterceptor 架构实现
  ↓
┌─────────┬─────────┐
│  11.8   │  11.9   │  (可并行)
│SelectLmt│ Checker │
└─────────┴─────────┘
  ↓
11.10 CI/CD 多版本测试矩阵
```

### 4.2 关键路径

**关键路径**: 11.1 → 11.2 → 11.6 → 11.7 → 11.8
**并行任务**: 11.3/11.4/11.5 可并行，11.8/11.9 可并行

---

## 五、质量标准

### 5.1 代码质量

| 指标 | 目标 | 说明 |
|-----|------|------|
| **单元测试覆盖率** | ≥ 80% | JaCoCo 报告 |
| **集成测试覆盖率** | ≥ 70% | 多版本测试 |
| **代码复杂度** | ≤ 15 | SonarQube 分析 |
| **代码重复率** | ≤ 5% | SonarQube 分析 |

### 5.2 性能标准

| 指标 | 目标 | 说明 |
|-----|------|------|
| **版本检测开销** | < 1ms | 首次检测 |
| **SQL 解析缓存** | > 90% | 缓存命中率 |
| **拦截器开销** | < 5% | 相对无拦截器 |
| **内存占用** | < 50MB | 运行时额外开销 |

### 5.3 兼容性标准

| 组件 | 支持版本 | 测试覆盖 |
|-----|---------|----------|
| **MyBatis** | 3.4.6, 3.5.6, 3.5.13, 3.5.16 | 全版本测试 |
| **MyBatis-Plus** | 3.4.0, 3.4.3, 3.5.3, 3.5.5 | 全版本测试 |
| **Java** | 8, 11, 17, 21 | 全版本测试 |
| **数据库** | MySQL, Oracle, SQL Server, PostgreSQL | 集成测试 |

---

## 六、风险管理

### 6.1 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|---------|
| **JSqlParser 解析失败** | 中 | 中 | 降级到字符串匹配 |
| **版本检测误判** | 低 | 高 | 多重检测机制 |
| **性能影响** | 低 | 高 | 性能测试 + 缓存优化 |
| **方言实现不完整** | 中 | 中 | 优先支持主流数据库 |

### 6.2 时间风险

| 风险 | 概率 | 影响 | 缓解措施 |
|-----|------|------|---------|
| **测试用例设计不完整** | 中 | 高 | 提前 Review，TDD 原则 |
| **多版本测试矩阵耗时** | 高 | 中 | 并行测试，缓存依赖 |
| **架构重构范围扩大** | 中 | 高 | 严格控制范围，分阶段实施 |

---

## 七、验收标准

### 7.1 功能验收

- [ ] 所有现有 Checker 功能迁移到新架构，功能不变
- [ ] SelectLimitInnerInterceptor 能自动为 SELECT 添加 LIMIT
- [ ] 支持 MySQL/Oracle/SQL Server/PostgreSQL 四种数据库
- [ ] 已有分页的 SQL 不被修改
- [ ] 检测类拦截器优先于兜底类拦截器执行

### 7.2 架构验收

- [ ] sql-guard-jdbc-common 模块提取完成
- [ ] sql-guard-jdbc-druid/hikari/p6spy 模块独立
- [ ] 用户只引入需要的连接池模块，无依赖污染
- [ ] SqlGuardInterceptor + InnerInterceptor 架构正常工作
- [ ] 优先级排序机制正常工作

### 7.3 兼容性验收

- [ ] MyBatis 3.4.6/3.5.6/3.5.13/3.5.16 全部通过测试
- [ ] MyBatis-Plus 3.4.0/3.4.3/3.5.3/3.5.5 全部通过测试
- [ ] Java 8/11/17/21 全部通过测试
- [ ] 版本检测自动且准确，无需用户配置

### 7.4 性能验收

- [ ] 版本检测开销 < 1ms
- [ ] 拦截器总开销 < 5%（相对无拦截器）
- [ ] SQL 解析缓存命中率 > 90%
- [ ] 无内存泄漏

### 7.5 文档验收

- [ ] 架构设计文档完整
- [ ] API 文档完整
- [ ] 迁移指南完整
- [ ] 配置参考文档完整

---

## 八、后续阶段

### Phase 12: 企业级特性增强（预计 15 工作日）

1. **动态配置**: Apollo/Nacos 配置中心集成
2. **监控统计**: SQL 执行统计、违规 Top 10
3. **深度分页改写**: 自动改写 LIMIT 1000000 → LIMIT 1000
4. **自定义拦截器**: 用户自定义 InnerInterceptor 扩展点

---

## 九、参考资料

### 9.1 官方文档

- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [MyBatis-Plus 分页插件](https://baomidou.com/plugins/pagination/)
- [JSqlParser GitHub](https://github.com/JSQLParser/JSqlParser)

### 9.2 内部文档

- `.apm/Memory/Phase_11_Reference_Architecture/` - 所有参考架构文档
- `.apm/Implementation_Plan.md` - 总体实施计划

---

**文档版本**: v1.0
**创建日期**: 2025-12-19
**最后更新**: 2025-12-19
