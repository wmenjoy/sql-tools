# MyBatis SqlNode 复用方案分析

**日期**: 2025-12-15  
**状态**: ✅ 可行  

## 核心洞察

MyBatis 的动态 SQL 处理是**模块化**的，可以独立使用其 SqlNode 体系，而不需要完整的 Configuration 和类加载！

## MyBatis 动态 SQL 架构

### 核心组件

```
XMLMapperBuilder (需要类加载) ❌
    ↓
XMLScriptBuilder (解析动态标签) ✅ 可复用
    ↓
SqlNode 树 (动态 SQL 逻辑) ✅ 可复用
    ↓
DynamicContext (SQL 构建器) ✅ 可复用
    ↓
最终 SQL 字符串 ✅
```

### SqlNode 接口体系

```java
public interface SqlNode {
    boolean apply(DynamicContext context);
}

// 实现类（都是独立的，不依赖类加载）
- StaticTextSqlNode      // 静态文本
- IfSqlNode              // <if test="...">
- ChooseSqlNode          // <choose><when><otherwise>
- ForeachSqlNode         // <foreach>
- WhereSqlNode           // <where>
- SetSqlNode             // <set>
- TrimSqlNode            // <trim>
- MixedSqlNode           // 混合节点容器
```

## 可复用的部分 ✅

### 1. SqlNode 类（完全独立）

```java
// MyBatis 源码中的 SqlNode 实现不依赖类加载
public class IfSqlNode implements SqlNode {
    private final ExpressionEvaluator evaluator;
    private final String test;
    private final SqlNode contents;
    
    @Override
    public boolean apply(DynamicContext context) {
        // 评估条件表达式
        if (evaluator.evaluateBoolean(test, context.getBindings())) {
            // 应用子节点
            contents.apply(context);
            return true;
        }
        return false;
    }
}
```

**关键**: 这些类只操作字符串和表达式，不需要加载 Java 类！

### 2. DynamicContext（SQL 构建器）

```java
public class DynamicContext {
    private final StringJoiner sqlBuilder;
    private final Map<String, Object> bindings;
    
    public void appendSql(String sql) {
        sqlBuilder.add(sql);
    }
    
    public String getSql() {
        return sqlBuilder.toString();
    }
    
    public Map<String, Object> getBindings() {
        return bindings;
    }
}
```

**用途**: 
- 收集生成的 SQL 片段
- 提供参数绑定上下文（我们可以用空 Map）

### 3. OGNL 表达式评估器（可简化）

```java
// MyBatis 使用 OGNL 评估 test 表达式
// 例如: test="id != null"

// 我们可以：
// 1. 使用 MyBatis 的 OGNL（需要 ognl 依赖）
// 2. 简化处理：假设所有条件为 true（生成所有可能的 SQL）
```

## 不需要的部分 ❌

### 1. XMLMapperBuilder
- 依赖 Configuration
- 需要加载 resultType/parameterType 类
- 需要注册 TypeHandler

### 2. MappedStatement
- 需要完整的配置信息
- 依赖类加载

### 3. BoundSql
- 需要参数对象
- 需要 TypeHandler

## 实现方案

### 方案：轻量级 SqlNode 构建器

```java
public class LightweightSqlNodeBuilder {
    
    /**
     * 从 XML 元素构建 SqlNode 树（不需要类加载）
     */
    public SqlNode buildSqlNode(Element element) {
        List<SqlNode> contents = new ArrayList<>();
        
        // 遍历子节点
        for (Node node : element.content()) {
            if (node instanceof Text) {
                // 静态文本
                String text = ((Text) node).getText();
                contents.add(new StaticTextSqlNode(text));
                
            } else if (node instanceof Element) {
                Element child = (Element) node;
                String nodeName = child.getName();
                
                switch (nodeName) {
                    case "if":
                        contents.add(buildIfNode(child));
                        break;
                    case "choose":
                        contents.add(buildChooseNode(child));
                        break;
                    case "foreach":
                        contents.add(buildForeachNode(child));
                        break;
                    case "where":
                        contents.add(buildWhereNode(child));
                        break;
                    case "set":
                        contents.add(buildSetNode(child));
                        break;
                    case "trim":
                        contents.add(buildTrimNode(child));
                        break;
                    default:
                        // 未知标签，当作静态文本
                        contents.add(new StaticTextSqlNode(child.getText()));
                }
            }
        }
        
        return new MixedSqlNode(contents);
    }
    
    private SqlNode buildIfNode(Element element) {
        String test = element.attributeValue("test");
        SqlNode contents = buildSqlNode(element);
        return new IfSqlNode(contents, test);
    }
    
    // ... 其他节点构建方法
    
    /**
     * 生成 SQL（使用假参数）
     */
    public String generateSql(SqlNode sqlNode) {
        // 创建 DynamicContext（使用空参数）
        DynamicContext context = new DynamicContext(
            new Configuration(), 
            new HashMap<>() // 空参数
        );
        
        // 应用 SqlNode 树
        sqlNode.apply(context);
        
        // 获取生成的 SQL
        return context.getSql();
    }
    
    /**
     * 生成所有可能的 SQL 变体（假设所有条件都可能为 true/false）
     */
    public List<String> generateAllVariants(SqlNode sqlNode) {
        List<String> variants = new ArrayList<>();
        
        // 策略1: 所有 <if> 都为 true
        variants.add(generateSql(sqlNode, allTrue()));
        
        // 策略2: 所有 <if> 都为 false  
        variants.add(generateSql(sqlNode, allFalse()));
        
        // 策略3: 可以生成更多组合...
        
        return variants;
    }
}
```

### 使用示例

```java
// 1. 解析 XML
Document doc = SAXReader.read(mapperFile);
Element selectElement = doc.selectSingleNode("//select[@id='selectUser']");

// 2. 构建 SqlNode 树
LightweightSqlNodeBuilder builder = new LightweightSqlNodeBuilder();
SqlNode sqlNode = builder.buildSqlNode(selectElement);

// 3. 生成 SQL
String sql = builder.generateSql(sqlNode);

// 4. 替换占位符
String normalized = sql
    .replaceAll("#\\{[^}]+\\}", "?")
    .replaceAll("\\$\\{[^}]+\\}", "?");

// 5. 验证
validator.validate(normalized);
```

## 优势分析

### ✅ 相比完整 MyBatis

| 特性 | 完整 MyBatis | 轻量级方案 |
|------|-------------|-----------|
| 需要类加载 | ✅ 是 | ❌ 否 |
| 需要依赖 | 完整依赖 | 仅 MyBatis Core |
| 动态 SQL 支持 | ✅ 完整 | ✅ 完整 |
| 参数绑定 | ✅ 真实 | ⚠️ 模拟 |
| 性能 | 中等 | ✅ 快 |
| 复杂度 | 高 | ✅ 低 |

### ✅ 相比正则替换

| 特性 | 正则替换 | 轻量级方案 |
|------|---------|-----------|
| 动态 SQL | ❌ 不支持 | ✅ 完整支持 |
| 实现复杂度 | ✅ 低 | 中等 |
| 准确性 | ⚠️ 中等 | ✅ 高 |
| 性能 | ✅ 快 | ✅ 快 |

## 实施计划

### Phase 1: 基础实现 ✅

1. **创建 `LightweightSqlNodeBuilder`**
   - 手动构建 SqlNode 树
   - 不依赖 XMLMapperBuilder

2. **复用 MyBatis SqlNode 类**
   - 直接使用 MyBatis 的 SqlNode 实现
   - 使用 MyBatis 的 DynamicContext

3. **简化表达式评估**
   - 方案 A: 使用 MyBatis 的 OGNL（需要 ognl 依赖）
   - 方案 B: 假设所有条件为 true（生成完整 SQL）
   - 方案 C: 生成多个变体（true/false 组合）

### Phase 2: 集成到 SqlScanner ✅

```java
private void validateSqlEntries(List<SqlEntry> entries) {
    for (SqlEntry entry : entries) {
        try {
            String rawSql = entry.getRawSql();
            
            // 检测是否包含动态 SQL 标签
            if (containsDynamicTags(rawSql)) {
                // 使用 SqlNode 处理
                SqlNode sqlNode = lightweightBuilder.parse(rawSql);
                String processedSql = lightweightBuilder.generateSql(sqlNode);
                validateSql(entry, processedSql);
            } else {
                // 使用正则替换（更快）
                String normalizedSql = normalizeMybatisSql(rawSql);
                validateSql(entry, normalizedSql);
            }
        } catch (Exception e) {
            logger.warn("Validation failed: {}", e.getMessage());
        }
    }
}
```

### Phase 3: 高级特性 ⚠️

1. **多变体生成**
   - 生成所有可能的 SQL 组合
   - 分别验证每个变体

2. **智能表达式评估**
   - 分析常见模式（如 `id != null`）
   - 生成最可能的 SQL

## 技术细节

### 依赖管理

```xml
<!-- 只需要 MyBatis Core -->
<dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis</artifactId>
    <version>3.5.x</version>
</dependency>

<!-- 如果需要 OGNL 表达式评估 -->
<dependency>
    <groupId>ognl</groupId>
    <artifactId>ognl</artifactId>
    <version>3.3.4</version>
</dependency>
```

### 性能优化

1. **缓存 SqlNode 树**
   ```java
   private final Map<String, SqlNode> sqlNodeCache = new ConcurrentHashMap<>();
   ```

2. **延迟解析**
   - 只在需要时解析动态 SQL
   - 静态 SQL 直接使用正则

3. **并行处理**
   - 多个 mapper 文件并行解析

## 风险和限制

### ⚠️ 潜在问题

1. **表达式评估**
   - 无法评估复杂的 OGNL 表达式
   - 解决：假设为 true 或生成多个变体

2. **嵌套动态标签**
   - 复杂的嵌套可能产生大量变体
   - 解决：限制变体数量

3. **自定义标签**
   - 项目可能有自定义 MyBatis 标签
   - 解决：当作静态文本处理

### ✅ 解决方案

1. **简化策略**
   - 对于 `<if>` 标签：假设条件为 true
   - 对于 `<choose>` 标签：取第一个 `<when>`
   - 对于 `<foreach>` 标签：展开一次

2. **回退机制**
   - SqlNode 处理失败 → 回退到正则
   - 保证不会因为动态 SQL 而完全失败

## 对比总结

### 三种方案对比

| 方案 | 动态 SQL | 类加载 | 复杂度 | 准确性 | 推荐度 |
|------|---------|--------|--------|--------|--------|
| 完整 MyBatis | ✅ | ❌ 需要 | 高 | 最高 | ⭐⭐ |
| SqlNode 复用 | ✅ | ✅ 不需要 | 中 | 高 | ⭐⭐⭐⭐⭐ |
| 正则替换 | ❌ | ✅ 不需要 | 低 | 中 | ⭐⭐⭐ |

## 结论

**✅ 强烈推荐使用 SqlNode 复用方案！**

### 理由

1. **技术可行**: 不需要类加载，只需要 MyBatis Core
2. **效果好**: 完整支持动态 SQL 标签
3. **性能好**: 比完整 MyBatis 快，比正则准确
4. **可维护**: 复用成熟的 MyBatis 代码
5. **渐进式**: 可以先实现基础功能，再逐步完善

### 实施优先级

1. **P0 (立即)**: 实现 `LightweightSqlNodeBuilder`
2. **P0 (立即)**: 支持 `<if>`, `<where>`, `<set>` 标签
3. **P1 (本周)**: 支持 `<choose>`, `<foreach>` 标签
4. **P2 (下周)**: 多变体生成和优化

---

**下一步**: 开始实现 `LightweightSqlNodeBuilder` 类！

