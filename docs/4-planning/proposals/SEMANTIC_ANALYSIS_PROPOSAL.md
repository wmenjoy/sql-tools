# MyBatis 语义分析方案 - 实施提案

## 🎯 核心问题

当前的简单模式匹配方案存在以下问题：

1. **误报率高**：所有 `${}` 都被标记为 CRITICAL（530个），但实际上很多是合法用法
2. **缺少分页检测**：完全没有检测 MyBatis 的分页方式（RowBounds、PageHelper、LIMIT）
3. **缺乏语义理解**：简单的字符串匹配，不理解 SQL 的结构和上下文
4. **建议不可行**：比如建议在 ORDER BY 中使用 `#{}`（这是不可行的）

## 💡 新方案：语义分析

### 核心改进

**从"模式匹配"到"语义理解"**

```
旧方案: 正则匹配 ${} → 全部标记为 CRITICAL
新方案: 构建 AST → 理解上下文 → 智能评估风险
```

### 关键特性

1. **上下文感知的风险评估**
   - ORDER BY 中的 String `${}` → HIGH（需要白名单）
   - LIMIT 中的 Integer `${}` → LOW（数字类型，风险较低）
   - WHERE 中的 String `${}` → CRITICAL（真正的 SQL 注入）
   - 白名单中的参数 → NONE

2. **完整的分页检测**
   - 检测 LIMIT 子句
   - 检测 LIMIT 值是否过大
   - 检测大表查询是否有分页
   - （未来）检测 RowBounds 和 PageHelper

3. **动态条件分析**
   - 分析 `<if>`, `<where>` 等动态标签
   - 检测"所有条件为 false"的场景
   - 对 DELETE/UPDATE 可能没有 WHERE 发出警告

4. **精确的修复建议**
   - ORDER BY: 提供白名单验证代码示例
   - LIMIT: 提供最大值校验代码示例
   - 分页: 提供多种分页方案选择

5. **配置化**
   - 用户可自定义风险级别
   - 用户可配置白名单
   - 用户可配置大表列表
   - 用户可添加自定义规则

## 📊 效果对比

### 示例：ORDER BY 参数

**SQL**:
```xml
<select id="search">
  SELECT * FROM users ORDER BY ${orderBy}
</select>
```

**旧方案输出**:
```
[CRITICAL] SQL injection risk - ${orderBy} detected
Suggestion: Use #{} parameterized query instead of ${}
```
❌ 问题：风险级别过高，建议不可行

**新方案输出**:
```
[HIGH] SQL Injection Risk - Dynamic ORDER BY
  Parameter: ${orderBy} in ORDER BY clause
  Risk: User can inject arbitrary ORDER BY expression
  
  Suggestion:
    1. Use whitelist validation:
       String[] allowed = {"name", "age", "created_at"};
       if (!Arrays.asList(allowed).contains(orderBy)) {
         throw new IllegalArgumentException();
       }
    
    2. Or use CASE statement in SQL:
       ORDER BY CASE #{orderBy}
         WHEN 'name' THEN name
         WHEN 'age' THEN age
         ELSE created_at
       END
```
✅ 改进：风险级别合理，建议可行

### 统计对比

| 指标 | 旧方案 | 新方案 | 改进 |
|------|--------|--------|------|
| `${}` 检测数 | 530 | 530 | - |
| CRITICAL 级别 | 530 | ~50 | -90% |
| HIGH 级别 | 0 | ~200 | +200 |
| MEDIUM/LOW 级别 | 0 | ~280 | +280 |
| 分页检测 | 0 | ✅ | 新增 |
| 误报率 | 高 | 低 | ✅ |

## 🚀 实施计划

### Phase 1：核心框架（1-2 周）
- 设计 SqlStatementAST 数据结构
- 实现 SqlStatementASTBuilder
- 实现参数提取和位置分析

### Phase 2：参数风险分析（1 周）
- 实现 ParameterRiskAnalyzer
- 基于位置、类型、命名的风险评估
- 配置化支持（白名单等）

### Phase 3：分页检测（1 周）
- 实现 PaginationDetector
- LIMIT 检测和验证
- 大表配置

### Phase 4：集成和测试（1 周）
- 集成到现有代码
- 对比测试
- 真实项目验证
- 文档编写

**总时间**：4-6 周

## 📝 下一步

1. **评审设计**：确认方案是否满足需求
2. **开始实施**：按照 Phase 1-4 的顺序实施
3. **持续验证**：在真实项目上验证效果

---

详细设计文档：`.apm/Memory/Phase_03_Static_Scanner/MyBatis_Semantic_Analysis_Design.md`

