# MyBatis 语义分析方案设计

## 📋 问题分析

### 用户反馈的核心问题

1. **`${}` 不等于 SQL 注入**
   - `${}` 本身是 MyBatis 的合法用法（分页、ORDER BY 等）
   - 不能简单地将所有 `${}` 都标记为 CRITICAL
   - 需要根据上下文判断风险

2. **分页检测缺失**
   - MyBatis 有多种分页方式：RowBounds、PageHelper、手动 LIMIT
   - 当前方案完全没有检测这些

3. **缺乏语义理解**
   - 应该完全理解 MyBatis Mapper 的语义
   - 理解 SQL 如何拼接、如何执行
   - 而不是简单的模式匹配和硬替换

4. **应该检测的真实风险**
   - 没有分页的大表查询
   - 分页过大（LIMIT 10000）
   - 动态条件永远为 true（WHERE 1=1）
   - 缺少必要的索引字段条件

## 🎯 新方案：基于语义的智能分析

### 核心思想

**从"模式匹配"到"语义理解"**

不是简单地检测 `${}`，而是：
1. 构建 MyBatis SQL 的抽象语法树（AST）
2. 理解参数在 SQL 中的位置和作用
3. 基于上下文进行智能的风险评估
4. 提供精确的修复建议

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                MybatisSemanticAnalyzer                      │
│                                                             │
│  输入: Mapper XML Element                                   │
│  输出: List<SecurityRisk>                                   │
└─────────────────────────────────────────────────────────────┘
                          ↓
        ┌─────────────────┴─────────────────┐
        │                                   │
        ↓                                   ↓
┌──────────────────┐              ┌──────────────────┐
│  SqlStatementAST │              │ SecurityRules    │
│    Builder       │              │    Config        │
└──────────────────┘              └──────────────────┘
        ↓                                   ↓
┌─────────────────────────────────────────────────────────────┐
│                      SqlStatementAST                        │
│                                                             │
│  - staticParts: List<String>                                │
│  - dynamicParts: List<DynamicNode>                          │
│  - parameters: List<Parameter>                              │
│  - structure: SqlStructure                                  │
└─────────────────────────────────────────────────────────────┘
                          ↓
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                 ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Parameter   │  │  Pagination  │  │  Dynamic     │
│  Risk        │  │  Detector    │  │  Condition   │
│  Analyzer    │  │              │  │  Analyzer    │
└──────────────┘  └──────────────┘  └──────────────┘
```

## 🔗 借鉴 MyBatis 官方代码

参考 [MyBatis GitHub](https://github.com/mybatis/mybatis-3) 的实现，复用其核心功能：

### ✅ 关键发现：不需要加载业务类！

**MyBatis 类加载机制分析**：

| 阶段 | 操作 | 是否需要业务类 | 说明 |
|------|------|---------------|------|
| **解析阶段** | XMLMapperBuilder.parse() | ❌ 不需要 | 只记录类名字符串 |
| **解析阶段** | 获取 MappedStatement | ❌ 不需要 | 已创建完成 |
| **解析阶段** | 获取 SqlSource/SqlNode | ❌ 不需要 | 不涉及类加载 |
| **执行阶段** | 执行查询 | ✅ 需要 | 需要实例化对象 |
| **执行阶段** | 结果映射 | ✅ 需要 | 需要业务类 |

**结论**：我们只需要解析阶段，所以 ✅ **可以直接复用 MyBatis 原生类，不需要加载业务类！**

### 关键类和接口

1. **XMLMapperBuilder** - XML Mapper 解析器
   - 用途：解析 Mapper XML 文件
   - 优势：完整支持 MyBatis 所有标签
   - 使用：`builder.parse()` 解析后得到 `MappedStatement`
   - ✅ 不需要业务类

2. **Configuration** - MyBatis 配置
   - 用途：管理所有 `MappedStatement`
   - 优势：统一的配置管理
   - 使用：`configuration.getMappedStatement(id)`
   - ✅ 不需要业务类

3. **SqlSource** - SQL 源码接口
   - `StaticSqlSource` - 静态 SQL
   - `DynamicSqlSource` - 动态 SQL（包含 `<if>`, `<where>` 等）
   - `RawSqlSource` - 原始 SQL
   - ✅ 不需要业务类

4. **SqlNode** - SQL 节点树
   - `IfSqlNode` - `<if>` 标签
   - `WhereSqlNode` - `<where>` 标签
   - `ForeachSqlNode` - `<foreach>` 标签
   - `MixedSqlNode` - 混合节点
   - ✅ 不需要业务类

5. **BoundSql** - 绑定的 SQL
   - 用途：生成最终的 SQL（用于测试和验证）
   - 包含：SQL 字符串、参数映射、参数值
   - ⚠️ 需要参数对象（使用 Map 代替业务 POJO）

### 复用策略（不需要业务类）

```java
// 1. 使用 XMLMapperBuilder 解析 Mapper（不需要业务类）
Configuration configuration = new Configuration();

// 1.1 预注册常见别名（避免 TypeAlias 解析失败）
TypeAliasRegistry registry = configuration.getTypeAliasRegistry();
registry.registerAlias("string", String.class);
registry.registerAlias("int", Integer.class);
registry.registerAlias("map", Map.class);
registry.registerAlias("list", List.class);
// 对于未知的别名，使用 Object.class
registry.registerAlias("User", Object.class);
registry.registerAlias("Order", Object.class);

InputStream inputStream = new FileInputStream("UserMapper.xml");
XMLMapperBuilder builder = new XMLMapperBuilder(
    inputStream, 
    configuration, 
    "UserMapper.xml", 
    configuration.getSqlFragments()
);

try {
    builder.parse();  // ✅ 成功！不需要业务类
} catch (TypeException e) {
    // 容错：如果遇到未注册的别名，注册后重试
    String alias = extractAliasFromException(e);
    registry.registerAlias(alias, Object.class);
    builder.parse();  // 重试
}

// 2. 获取 MappedStatement（不需要业务类）
MappedStatement ms = configuration.getMappedStatement("UserMapper.selectUsers");

// 3. 分析 SqlSource（不需要业务类）
SqlSource sqlSource = ms.getSqlSource();
if (sqlSource instanceof DynamicSqlSource) {
    // 动态 SQL，通过反射获取 SqlNode 树
    Field field = DynamicSqlSource.class.getDeclaredField("rootSqlNode");
    field.setAccessible(true);
    SqlNode rootNode = (SqlNode) field.get(sqlSource);
    // 分析 SqlNode 树
}

// 4. 生成 BoundSql 进行验证（使用 Map 代替业务 POJO）
Map<String, Object> params = new HashMap<>();
params.put("name", "test");
params.put("age", 25);
BoundSql boundSql = sqlSource.getBoundSql(params);  // ✅ 不需要业务类

// 5. 分析生成的 SQL
String sql = boundSql.getSql();
List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

// 6. 检测参数位置和风险
for (ParameterMapping pm : parameterMappings) {
    String property = pm.getProperty();
    // 分析参数在 SQL 中的位置
}

// 7. 测试动态 SQL 的不同场景（不需要业务类）
List<Map<String, Object>> testCases = Arrays.asList(
    Collections.emptyMap(),           // 所有条件为 null
    Map.of("name", "test"),           // 只有 name
    Map.of("age", 25),                // 只有 age
    Map.of("name", "test", "age", 25) // 都有
);

for (Map<String, Object> testParams : testCases) {
    BoundSql testBoundSql = sqlSource.getBoundSql(testParams);
    analyzeSqlScenario(testBoundSql.getSql());
}
```

### 优势

1. **✅ 不需要加载业务类**
   - 解析阶段不需要业务 POJO
   - 使用 Map 代替业务对象
   - 依赖干净（< 2MB）

2. **完整的 MyBatis 语义支持**
   - 不需要自己实现 XML 解析
   - 支持所有 MyBatis 标签
   - 支持 `<include>` 引用

3. **动态 SQL 处理**
   - 使用 `SqlNode` 树理解动态结构
   - 使用 `BoundSql` 生成实际 SQL
   - 可以测试不同参数下的 SQL 变化

4. **参数映射**
   - 自动识别 `#{}` 和 `${}`
   - 获取参数的详细信息
   - 追踪参数在 SQL 中的使用

### 依赖管理

```xml
<dependencies>
    <!-- MyBatis 核心 -->
    <dependency>
        <groupId>org.mybatis</groupId>
        <artifactId>mybatis</artifactId>
        <version>3.5.19</version>
    </dependency>
    
    <!-- JavaParser（解析 Java 接口） -->
    <dependency>
        <groupId>com.github.javaparser</groupId>
        <artifactId>javaparser-core</artifactId>
        <version>3.25.8</version>
    </dependency>
</dependencies>
```

**MyBatis 传递依赖**（非常干净）：
```
mybatis-3.5.19.jar
├── ognl-3.3.4.jar          (OGNL 表达式，60KB)
├── javassist-3.29.2-GA.jar (字节码操作，800KB)
└── slf4j-api-2.0.9.jar     (日志接口，60KB)
总大小: < 2MB
```

✅ **可以安全引入，不会有依赖冲突**

## 🔍 核心组件

### 1. SqlStatementAST（SQL 语句抽象语法树）

```java
class SqlStatementAST {
  String id;                          // mapper id
  SqlCommandType type;                // SELECT/INSERT/UPDATE/DELETE
  List<StaticPart> staticParts;       // 静态 SQL 片段
  List<DynamicNode> dynamicNodes;     // 动态节点 (<if>, <where>, etc.)
  List<Parameter> parameters;         // 所有参数
  SqlStructure structure;             // SQL 结构信息
}

class Parameter {
  String name;                        // 参数名
  ParameterType type;                 // String, Integer, etc.
  boolean isStringSubstitution;       // true: ${}, false: #{}
  SqlPosition position;               // WHERE, ORDER BY, LIMIT, etc.
  ParameterSource source;             // USER_INPUT, CONFIGURATION, UNKNOWN
}

class SqlStructure {
  boolean hasWhereClause;
  boolean hasLimitClause;
  boolean hasOrderByClause;
  List<String> tables;
  List<String> columns;
}
```

### 2. ParameterRiskAnalyzer（参数风险分析器）

**智能风险评估规则**：

| 位置 | 类型 | 使用方式 | 风险级别 | 说明 |
|------|------|---------|---------|------|
| ORDER BY | String | `${}` | HIGH | 用户可控的排序 |
| ORDER BY | String | `${}` + 白名单 | LOW | 配置的排序 |
| WHERE | String | `${}` | CRITICAL | 直接 SQL 注入 |
| LIMIT | Integer | `${}` | LOW | 数字类型，风险较低 |
| LIMIT | String | `${}` | MEDIUM | 字符串类型，有风险 |
| 任何位置 | 任何 | `#{}` | NONE | 参数化查询，安全 |

**启发式推断参数来源**：

```java
// 基于参数名推断来源
PARAMETER_NAME_HINTS = {
  // 可能来自用户输入
  "userInput", "keyword", "searchTerm", "query" → USER_INPUT,
  
  // 可能来自配置
  "pageSize", "limit", "offset", "defaultValue" → CONFIGURATION,
  
  // 特定参数
  "orderBy", "sortBy" → USER_INPUT (需要白名单验证),
}
```

**输出示例**：

```
[HIGH] SQL Injection Risk - Dynamic ORDER BY
  Location: UserMapper.selectUsers
  Parameter: ${orderBy} in ORDER BY clause
  Risk: User can inject arbitrary ORDER BY expression
  
  Suggestion:
    1. Use whitelist validation:
       String[] allowedColumns = {"name", "age", "created_at"};
       if (!Arrays.asList(allowedColumns).contains(orderBy)) {
         throw new IllegalArgumentException("Invalid sort column");
       }
    
    2. Or use CASE statement in SQL:
       ORDER BY CASE #{orderBy}
         WHEN 'name' THEN name
         WHEN 'age' THEN age
         ELSE created_at
       END

[LOW] Dynamic LIMIT parameter
  Location: UserMapper.selectUsers
  Parameter: ${limit} in LIMIT clause (Integer type)
  Risk: Low risk due to integer type, but recommend using #{}
  Suggestion: Replace with LIMIT #{limit}
```

### 3. PaginationDetector（分页检测器）

**检测 MyBatis/MyBatis-Plus 的所有分页方式**：

1. **手动 LIMIT**
```xml
<select id="selectUsers">
  SELECT * FROM users
  LIMIT #{pageSize} OFFSET #{offset}
</select>
```
检测：✅ 有分页  
验证：检查 LIMIT 值是否合理（不超过 1000）

2. **MyBatis RowBounds**（需要 Java 代码分析）
```java
List<User> selectUsers(RowBounds rowBounds);
```
检测：✅ 有分页

3. **PageHelper 插件**（需要 Java 代码分析）
```java
@PageHelper
List<User> selectUsers();
```
检测：✅ 有分页

4. **MyBatis-Plus IPage**（需要 Java 代码分析）
```java
IPage<User> selectPageVo(Page<?> page, @Param("age") Integer age);
```
检测：✅ 有分页（MyBatis-Plus）

5. **MyBatis-Plus Page 参数**（需要 Java 代码分析）
```java
List<User> selectByPage(Page<User> page);
```
检测：✅ 有分页（MyBatis-Plus）

6. **没有分页**
```xml
<select id="selectAll">
  SELECT * FROM users
</select>
```
检测：❌ 没有分页  
如果是大表 → HIGH 风险

**大表配置**：

```yaml
large_tables:
  - users
  - orders
  - logs
  - audit_log
  - transactions
```

**输出示例**：

```
[HIGH] Missing Pagination
  Location: UserMapper.selectAll
  SQL: SELECT * FROM users
  Table: users (large table, estimated 1M+ rows)
  Risk: Full table scan without pagination
  
  Suggestion:
    1. Add LIMIT clause: LIMIT #{pageSize}
    2. Or use RowBounds parameter
    3. Or use PageHelper plugin

[MEDIUM] Large Pagination Limit
  Location: OrderMapper.selectOrders
  SQL: SELECT * FROM orders LIMIT 5000
  Risk: LIMIT value too large (5000)
  Suggestion: Reduce LIMIT to 1000 or less
```

### 4. DynamicConditionAnalyzer（动态条件分析器）

**检测动态条件可能导致的风险**：

```xml
<delete id="deleteUsers">
  DELETE FROM users
  <where>
    <if test="name != null">name = #{name}</if>
    <if test="age != null">AND age = #{age}</if>
  </where>
</delete>
```

**场景分析**：

| 场景 | name | age | 生成的 SQL | 风险 |
|------|------|-----|-----------|------|
| 1 | ✓ | ✓ | DELETE FROM users WHERE name = ? AND age = ? | NONE |
| 2 | ✓ | ✗ | DELETE FROM users WHERE name = ? | NONE |
| 3 | ✗ | ✓ | DELETE FROM users WHERE age = ? | NONE |
| 4 | ✗ | ✗ | DELETE FROM users | **CRITICAL** |

**输出示例**：

```
[CRITICAL] DELETE without WHERE in edge case
  Location: UserMapper.deleteByCondition
  Scenario: When name=null AND age=null
  Generated SQL: DELETE FROM users
  Risk: All conditions false, no WHERE clause, will delete all data
  
  Suggestion:
    1. Add mandatory condition:
       <where>
         id = #{id}  <!-- mandatory -->
         <if test="name != null">AND name = #{name}</if>
         <if test="age != null">AND age = #{age}</if>
       </where>
    
    2. Or add guard clause in Java:
       if (name == null && age == null) {
         throw new IllegalArgumentException("At least one condition required");
       }
```

## ⚙️ 配置化设计

```yaml
mybatis_security_rules:
  
  # 1. 参数风险规则
  parameter_risks:
    order_by_substitution:
      enabled: true
      level: HIGH
      
      # 白名单：这些参数名被认为是安全的
      whitelist:
        - "defaultOrderBy"
        - "systemOrderBy"
      
      # 低风险模式
      low_risk_patterns:
        - ".*Config$"
        - "default.*"
    
    where_substitution:
      enabled: true
      level: CRITICAL
    
    limit_substitution:
      enabled: true
      level: MEDIUM
      # 基于类型调整
      type_based_adjustment:
        Integer: LOW
        Long: LOW
        String: HIGH
  
  # 2. 分页规则
  pagination_rules:
    large_tables:
      - users
      - orders
      - logs
    
    require_pagination_for_large_tables:
      enabled: true
      level: HIGH
    
    max_limit_value:
      enabled: true
      threshold: 1000
      level: MEDIUM
  
  # 3. 动态条件规则
  dynamic_condition_rules:
    delete_update_without_where:
      enabled: true
      level: CRITICAL
      
      # 例外：这些表允许全表操作
      exception_tables:
        - temp_table
        - cache_table
```

## 📊 对比：旧方案 vs 新方案

| 维度 | 旧方案（简单模式匹配） | 新方案（语义分析） |
|------|---------------------|------------------|
| **核心方法** | 正则匹配 `${}` | 构建 AST，理解语义 |
| **风险评估** | 所有 `${}` → CRITICAL | 基于上下文智能评估 |
| **分页检测** | ❌ 无 | ✅ 完整支持 |
| **动态条件** | ❌ 简单检查 WHERE | ✅ 场景分析 |
| **配置化** | ❌ 硬编码 | ✅ 完全配置化 |
| **误报率** | ❌ 高（530个都是CRITICAL） | ✅ 低（智能分级） |
| **修复建议** | ⚠️ 通用建议 | ✅ 精确建议 |

### 示例对比

**场景：ORDER BY 参数**

```xml
<select id="search">
  SELECT * FROM users
  ORDER BY ${orderBy}
</select>
```

**旧方案输出**：
```
[CRITICAL] SQL injection risk - ${orderBy} detected
Suggestion: Use #{} parameterized query instead of ${}
```
❌ 问题：
- 风险级别过高（ORDER BY 不能用 `#{}`）
- 建议不可行

**新方案输出**：
```
[HIGH] SQL Injection Risk - Dynamic ORDER BY
  Parameter: ${orderBy} in ORDER BY clause
  Risk: User can inject arbitrary ORDER BY expression
  
  Suggestion:
    1. Use whitelist validation:
       String[] allowedColumns = {"name", "age", "created_at"};
       if (!Arrays.asList(allowedColumns).contains(orderBy)) {
         throw new IllegalArgumentException();
       }
    
    2. Or use CASE statement in SQL
```
✅ 改进：
- 风险级别合理（HIGH，不是 CRITICAL）
- 提供可行的解决方案
- 理解 ORDER BY 的特殊性

## 🚀 实施计划（TDD 方式）

### Phase 0：搭建测试框架（1 天）
- [ ] 创建测试基类 `MyBatisSemanticAnalysisTestBase`
- [ ] 集成 MyBatis Configuration
- [ ] 集成 XMLMapperBuilder
- [ ] 准备测试工具方法

### Phase 1：参数风险分析（3-4 天，TDD）
**Day 1**: 编写测试
- [ ] Test: 检测 ORDER BY 中的 `${}`
- [ ] Test: 检测 LIMIT 中的 Integer `${}`
- [ ] Test: 白名单参数应该被忽略

**Day 2**: 实现功能
- [ ] 实现 ParameterRiskAnalyzer
- [ ] 让所有测试通过

**Day 3**: 边界测试
- [ ] Test: WHERE 中的 `${}`
- [ ] Test: 表名/列名中的 `${}`
- [ ] 完善实现

**Day 4**: 重构和优化

### Phase 2：分页检测（3-4 天，TDD）
**Day 1**: 基础分页测试
- [ ] Test: 检测缺少分页的大表查询
- [ ] Test: 检测 LIMIT 值过大
- [ ] Test: 检测 LIMIT 子句

**Day 2**: 实现基础功能
- [ ] 实现 PaginationDetector
- [ ] 让基础测试通过

**Day 3**: MyBatis-Plus 测试
- [ ] Test: 检测 MyBatis-Plus IPage 分页
- [ ] Test: 检测 MyBatis-Plus Page 参数
- [ ] Test: 检测 RowBounds

**Day 4**: 实现 MyBatis-Plus 检测
- [ ] 集成 Java 接口解析
- [ ] 实现 MyBatis-Plus 分页检测

### Phase 3：集成 MyBatis 官方代码（3-4 天，TDD）
**Day 1**: XMLMapperBuilder 测试
- [ ] Test: 使用 MyBatis XMLMapperBuilder 解析
- [ ] Test: 获取 MappedStatement

**Day 2**: 实现 MyBatis 集成
- [ ] 集成 XMLMapperBuilder
- [ ] 使用 Configuration 管理 Mapper

**Day 3**: SqlNode 测试
- [ ] Test: 使用 SqlNode 分析动态 SQL
- [ ] Test: 使用 BoundSql 验证参数位置

**Day 4**: 实现 SqlNode 分析
- [ ] 使用 DynamicSqlSource
- [ ] 生成 BoundSql 进行验证

### Phase 4：动态条件分析（3-4 天，TDD）
**Day 1**: 编写测试
- [ ] Test: 检测所有条件为 false 的场景
- [ ] Test: 有强制条件的 DELETE 应该安全

**Day 2**: 实现功能
- [ ] 实现 DynamicConditionAnalyzer
- [ ] 场景生成（优化为 O(n)）

**Day 3**: 边界测试
- [ ] Test: 复杂嵌套条件
- [ ] Test: <choose> 标签

**Day 4**: 完善和优化

### Phase 5：配置化（2-3 天）
- [ ] 设计配置文件格式
- [ ] 实现配置加载
- [ ] 白名单支持
- [ ] 自定义规则支持

### Phase 6：集成测试和文档（2-3 天）
- [ ] 端到端集成测试
- [ ] 性能测试和优化
- [ ] 文档编写
- [ ] 真实项目验证

**总时间估计**：4-5 周

**TDD 原则**：
- 🔴 Red: 先写测试，测试失败
- 🟢 Green: 写最少的代码让测试通过
- 🔵 Refactor: 重构代码，保持测试通过

## 🎯 预期效果

### 1. 更准确的风险评估

**旧方案**：
- 530 个 `${}` 全部标记为 CRITICAL
- 误报率高，用户无法区分真正的风险

**新方案**：
- 智能分级：
  - CRITICAL: WHERE 子句中的字符串拼接（真正的 SQL 注入）
  - HIGH: ORDER BY 中的用户可控参数
  - MEDIUM: LIMIT 中的字符串参数
  - LOW: LIMIT 中的数字参数、白名单参数
- 误报率低，用户可以聚焦真正的风险

### 2. 完整的分页检测

**旧方案**：
- 完全没有分页检测

**新方案**：
- 检测所有分页方式（LIMIT, RowBounds, PageHelper）
- 检测分页值是否合理
- 对大表查询无分页发出警告

### 3. 更有价值的修复建议

**旧方案**：
- 通用建议："Use #{} instead of ${}"

**新方案**：
- 针对性建议：
  - ORDER BY: 提供白名单验证代码
  - LIMIT: 提供最大值校验代码
  - 分页: 提供多种分页方案选择

### 4. 可配置和可扩展

**旧方案**：
- 硬编码规则

**新方案**：
- 用户可以自定义：
  - 风险级别
  - 白名单
  - 大表列表
  - 例外情况
  - 自定义规则

## 🔍 Java 代码检测策略

### 三层检测架构

```
┌─────────────────────────────────────────────────────────────┐
│              MybatisSemanticAnalyzer（主入口）              │
│                                                             │
│  输入: Mapper XML + Mapper Java Interface                   │
│  输出: List<SecurityRisk>                                   │
└─────────────────────────────────────────────────────────────┘
                          ↓
        ┌─────────────────┴─────────────────┐
        │                                   │
        ↓                                   ↓
┌──────────────────┐              ┌──────────────────┐
│  MyBatis         │              │  JavaParser      │
│  XMLMapper       │              │  Mapper          │
│  Builder         │              │  Interface       │
│                  │              │  Analyzer        │
│  解析 XML        │              │                  │
│  获取 SqlSource  │              │  解析 Java       │
│  获取 SqlNode    │              │  获取参数类型    │
│  ✅ 不需要业务类 │              │  检测分页参数    │
└──────────────────┘              └──────────────────┘
        ↓                                   ↓
        └─────────────────┬─────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                  CombinedAnalyzer（核心）                   │
│                                                             │
│  结合 XML 和 Java 信息进行精确分析                          │
│  - 匹配参数类型和使用位置                                   │
│  - 基于类型的智能风险评估                                   │
│  - 检测分页参数（含 MyBatis-Plus）                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                 ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Parameter   │  │  Pagination  │  │  Dynamic     │
│  Risk        │  │  Detector    │  │  Condition   │
│  Analyzer    │  │              │  │  Analyzer    │
│              │  │  支持:       │  │              │
│  检测:       │  │  - LIMIT     │  │  检测:       │
│  - ORDER BY  │  │  - RowBounds │  │  - 条件为空  │
│  - WHERE     │  │  - PageHelper│  │  - WHERE消失 │
│  - LIMIT     │  │  - IPage     │  │              │
│  - 白名单    │  │  - Page      │  │              │
│              │  │              │  │              │
│  基于类型:   │  │              │  │              │
│  - String    │  │              │  │              │
│  - Integer   │  │              │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
```

### Layer 1：Mapper 接口分析（P0，必须）

**目标**：
- 检测参数类型
- 检测分页参数
- 检测返回类型

**工具**：JavaParser

**实现**：
```java
public class MapperInterfaceAnalyzer {
    public MapperInterfaceInfo analyze(File javaFile) {
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        ClassOrInterfaceDeclaration mapperInterface = 
            cu.findFirst(ClassOrInterfaceDeclaration.class)
              .filter(ClassOrInterfaceDeclaration::isInterface)
              .orElse(null);
        
        MapperInterfaceInfo info = new MapperInterfaceInfo();
        
        for (MethodDeclaration method : mapperInterface.getMethods()) {
            MethodInfo methodInfo = analyzeMethod(method);
            info.addMethod(methodInfo);
        }
        
        return info;
    }
}
```

### Layer 2：结合 XML + Java 分析（P0，必须）

**目标**：
- 匹配参数类型和使用位置
- 基于类型的精确风险评估

**实现**：
```java
public class CombinedAnalyzer {
    public List<SecurityRisk> analyze(
        MappedStatement mappedStatement,
        MapperInterfaceInfo interfaceInfo
    ) {
        // 1. 从 XML 中提取参数使用
        List<ParameterUsage> usages = extractParameterUsages(mappedStatement);
        
        // 2. 从 Java 接口中获取参数定义
        MethodInfo methodInfo = interfaceInfo.getMethod(mappedStatement.getId());
        
        // 3. 匹配并评估风险
        for (ParameterUsage usage : usages) {
            ParameterInfo paramInfo = methodInfo.getParameter(usage.getName());
            RiskLevel risk = assessRisk(usage, paramInfo);
            // 基于类型和位置的智能评估
        }
    }
}
```

### Layer 3：调用链分析（P1，可选）

**目标**：
- 检测参数来源（@RequestParam）
- 检测白名单验证

**实现**：
```java
public class CallChainAnalyzer {
    public List<SecurityRisk> analyze(File controllerFile) {
        // 分析 Controller 层的参数来源
        // 追踪参数从 Controller 到 Mapper 的流程
    }
}
```

### 实施优先级

| 层级 | 功能 | 优先级 | 时间 | 价值 |
|------|------|--------|------|------|
| **Layer 1** | Mapper 接口分析 | P0 | 1周 | 40% |
| **Layer 2** | 结合 XML + Java | P0 | 1周 | 50% |
| **Layer 3** | 调用链分析 | P1 | 1周 | 10% |

**MVP**：Layer 1 + Layer 2 = 2周，提供 90% 价值

## 📝 总结

新方案完全解决了用户提出的所有问题：

1. ✅ **`${}` 不等于 SQL 注入**
   - 基于上下文和类型的智能风险评估
   - 不再简单地将所有 `${}` 标记为 CRITICAL
   - 结合 Java 接口的参数类型信息

2. ✅ **完整的分页检测**
   - 支持所有 MyBatis 分页方式
   - 支持 MyBatis-Plus（IPage, Page）
   - 检测分页值是否合理

3. ✅ **借鉴 MyBatis 官方代码**
   - 使用 XMLMapperBuilder 解析 XML
   - 使用 SqlNode 分析动态 SQL
   - 使用 BoundSql 验证参数位置
   - ✅ 不需要加载业务类

4. ✅ **Java 代码检测**
   - 使用 JavaParser 解析 Mapper 接口
   - 结合 XML + Java 进行精确分析
   - 基于参数类型的智能风险评估

5. ✅ **语义理解**
   - 构建 AST，理解 SQL 结构
   - 理解参数的位置和作用
   - 理解动态条件的组合

6. ✅ **智能风险推断**
   - 检测没有分页的大表查询
   - 检测分页过大
   - 检测动态条件可能为空
   - 提供精确的修复建议

这是一个更智能、更准确、更有价值的 MyBatis 安全分析方案。

