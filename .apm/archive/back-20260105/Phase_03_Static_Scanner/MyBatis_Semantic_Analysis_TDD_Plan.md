# MyBatis 语义分析 - TDD 实施计划

## 📋 补充需求

### 1. MyBatis-Plus 分页检测

MyBatis-Plus 提供了强大的分页功能，需要检测：

**分页方式**：
1. **IPage 接口**
```java
// Mapper 接口
IPage<User> selectPageVo(Page<?> page, @Param("age") Integer age);

// 调用
Page<User> page = new Page<>(1, 10);
IPage<User> userPage = userMapper.selectPageVo(page, 18);
```

2. **Page 参数**
```java
// Mapper 接口
List<User> selectByPage(Page<User> page);

// XML
<select id="selectByPage">
  SELECT * FROM users WHERE age > #{age}
</select>
```

3. **PaginationInnerInterceptor**
```java
// 配置
@Bean
public MybatisPlusInterceptor mybatisPlusInterceptor() {
    MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
    interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
    return interceptor;
}
```

**检测策略**：
- 检查 Mapper 接口方法参数是否包含 `IPage` 或 `Page`
- 检查是否配置了 `PaginationInnerInterceptor`
- 如果都没有，且是大表查询 → 发出警告

### 2. 借鉴 MyBatis 官方代码

参考 [MyBatis GitHub](https://github.com/mybatis/mybatis-3) 的实现：

**关键类**：
1. **XMLMapperBuilder** - XML Mapper 解析
2. **SqlSource** - SQL 源码接口
3. **DynamicSqlSource** - 动态 SQL 处理
4. **SqlNode** - SQL 节点树
5. **MappedStatement** - 映射语句

**复用策略**：
- 使用 MyBatis 的 `XMLMapperBuilder` 解析 Mapper 文件
- 使用 `SqlNode` 树来理解动态 SQL 结构
- 使用 `BoundSql` 来生成最终的 SQL（用于测试）

## 🧪 TDD 实施计划

### Phase 1：参数风险分析（TDD）

#### Test 1.1：检测 ORDER BY 中的 ${}

**测试先行**：
```java
@Test
@DisplayName("Should detect HIGH risk for ${} in ORDER BY clause")
void testOrderByRisk() {
    // Given: Mapper XML with ${} in ORDER BY
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='search'>" +
        "    SELECT * FROM users ORDER BY ${orderBy}" +
        "  </select>" +
        "</mapper>";
    
    // When: Analyze the mapper
    List<SecurityRisk> risks = analyzer.analyze(parseXml(xml));
    
    // Then: Should detect HIGH risk
    assertThat(risks)
        .hasSize(1)
        .first()
        .satisfies(risk -> {
            assertThat(risk.getLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(risk.getCategory()).isEqualTo(RiskCategory.SQL_INJECTION);
            assertThat(risk.getMessage()).contains("ORDER BY");
            assertThat(risk.getSuggestion()).contains("whitelist");
        });
}
```

**实现**：
```java
public class ParameterRiskAnalyzer {
    public List<SecurityRisk> analyze(Element mapperElement) {
        // 实现逻辑...
    }
}
```

**运行测试** → 失败 → 实现代码 → 测试通过

#### Test 1.2：检测 LIMIT 中的 Integer ${}

```java
@Test
@DisplayName("Should detect LOW risk for Integer ${} in LIMIT clause")
void testLimitWithInteger() {
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='list'>" +
        "    SELECT * FROM users LIMIT ${limit}" +
        "  </select>" +
        "</mapper>";
    
    List<SecurityRisk> risks = analyzer.analyze(parseXml(xml));
    
    assertThat(risks)
        .hasSize(1)
        .first()
        .satisfies(risk -> {
            assertThat(risk.getLevel()).isEqualTo(RiskLevel.LOW);
            assertThat(risk.getMessage()).contains("LIMIT");
        });
}
```

#### Test 1.3：白名单参数应该被忽略

```java
@Test
@DisplayName("Should ignore whitelisted parameters")
void testWhitelistParameter() {
    // Given: Configuration with whitelist
    SecurityRulesConfig config = new SecurityRulesConfig();
    config.addWhitelist("defaultOrderBy");
    
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='search'>" +
        "    SELECT * FROM users ORDER BY ${defaultOrderBy}" +
        "  </select>" +
        "</mapper>";
    
    // When: Analyze with config
    ParameterRiskAnalyzer analyzer = new ParameterRiskAnalyzer(config);
    List<SecurityRisk> risks = analyzer.analyze(parseXml(xml));
    
    // Then: Should not detect any risk
    assertThat(risks).isEmpty();
}
```

### Phase 2：分页检测（TDD）

#### Test 2.1：检测缺少分页的大表查询

```java
@Test
@DisplayName("Should detect missing pagination for large table")
void testMissingPagination() {
    // Given: Configuration with large tables
    SecurityRulesConfig config = new SecurityRulesConfig();
    config.addLargeTable("users");
    
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='selectAll'>" +
        "    SELECT * FROM users" +
        "  </select>" +
        "</mapper>";
    
    // When: Analyze
    PaginationDetector detector = new PaginationDetector(config);
    List<SecurityRisk> risks = detector.analyze(parseXml(xml));
    
    // Then: Should detect missing pagination
    assertThat(risks)
        .hasSize(1)
        .first()
        .satisfies(risk -> {
            assertThat(risk.getLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(risk.getCategory()).isEqualTo(RiskCategory.MISSING_PAGINATION);
            assertThat(risk.getMessage()).contains("large table");
        });
}
```

#### Test 2.2：检测 LIMIT 值过大

```java
@Test
@DisplayName("Should detect large LIMIT value")
void testLargeLimitValue() {
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='list'>" +
        "    SELECT * FROM users LIMIT 5000" +
        "  </select>" +
        "</mapper>";
    
    List<SecurityRisk> risks = detector.analyze(parseXml(xml));
    
    assertThat(risks)
        .hasSize(1)
        .first()
        .satisfies(risk -> {
            assertThat(risk.getLevel()).isEqualTo(RiskLevel.MEDIUM);
            assertThat(risk.getMessage()).contains("too large");
            assertThat(risk.getMessage()).contains("5000");
        });
}
```

#### Test 2.3：检测 MyBatis-Plus IPage 分页

```java
@Test
@DisplayName("Should detect MyBatis-Plus IPage pagination")
void testMyBatisPlusIPagePagination() {
    // Given: Mapper interface with IPage parameter
    String javaInterface = 
        "public interface UserMapper {" +
        "  IPage<User> selectPageVo(Page<?> page, @Param(\"age\") Integer age);" +
        "}";
    
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='selectPageVo'>" +
        "    SELECT * FROM users WHERE age > #{age}" +
        "  </select>" +
        "</mapper>";
    
    // When: Analyze with Java interface
    PaginationDetector detector = new PaginationDetector();
    PaginationInfo info = detector.detectPagination(
        parseXml(xml), 
        parseJavaInterface(javaInterface)
    );
    
    // Then: Should detect MyBatis-Plus pagination
    assertThat(info.getType()).isEqualTo(PaginationType.MYBATIS_PLUS_IPAGE);
    assertThat(info.hasRisk()).isFalse();
}
```

#### Test 2.4：检测 MyBatis-Plus Page 参数

```java
@Test
@DisplayName("Should detect MyBatis-Plus Page parameter")
void testMyBatisPlusPageParameter() {
    String javaInterface = 
        "public interface UserMapper {" +
        "  List<User> selectByPage(Page<User> page);" +
        "}";
    
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='selectByPage'>" +
        "    SELECT * FROM users" +
        "  </select>" +
        "</mapper>";
    
    PaginationInfo info = detector.detectPagination(
        parseXml(xml), 
        parseJavaInterface(javaInterface)
    );
    
    assertThat(info.getType()).isEqualTo(PaginationType.MYBATIS_PLUS_PAGE);
}
```

### Phase 3：动态条件分析（TDD）

#### Test 3.1：检测所有条件为 false 的场景

```java
@Test
@DisplayName("Should detect DELETE without WHERE when all conditions are false")
void testDeleteWithoutWhereInEdgeCase() {
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <delete id='deleteByCondition'>" +
        "    DELETE FROM users" +
        "    <where>" +
        "      <if test='name != null'>name = #{name}</if>" +
        "      <if test='age != null'>AND age = #{age}</if>" +
        "    </where>" +
        "  </delete>" +
        "</mapper>";
    
    // When: Analyze dynamic conditions
    DynamicConditionAnalyzer analyzer = new DynamicConditionAnalyzer();
    List<SecurityRisk> risks = analyzer.analyze(parseXml(xml));
    
    // Then: Should detect CRITICAL risk
    assertThat(risks)
        .hasSize(1)
        .first()
        .satisfies(risk -> {
            assertThat(risk.getLevel()).isEqualTo(RiskLevel.CRITICAL);
            assertThat(risk.getMessage()).contains("DELETE without WHERE");
            assertThat(risk.getMessage()).contains("name=null AND age=null");
        });
}
```

#### Test 3.2：有强制条件的 DELETE 应该安全

```java
@Test
@DisplayName("Should not flag DELETE with mandatory condition")
void testDeleteWithMandatoryCondition() {
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <delete id='deleteById'>" +
        "    DELETE FROM users WHERE id = #{id}" +
        "    <if test='name != null'>AND name = #{name}</if>" +
        "  </delete>" +
        "</mapper>";
    
    List<SecurityRisk> risks = analyzer.analyze(parseXml(xml));
    
    // Should not detect risk because id is mandatory
    assertThat(risks).isEmpty();
}
```

### Phase 4：借鉴 MyBatis 官方代码（TDD）

#### Test 4.1：使用 MyBatis XMLMapperBuilder 解析

```java
@Test
@DisplayName("Should parse mapper using MyBatis XMLMapperBuilder")
void testParseWithMyBatisBuilder() {
    // Given: MyBatis Configuration
    Configuration configuration = new Configuration();
    
    // Given: Mapper XML file
    String xml = 
        "<?xml version='1.0' encoding='UTF-8'?>" +
        "<!DOCTYPE mapper PUBLIC '-//mybatis.org//DTD Mapper 3.0//EN' " +
        "'http://mybatis.org/dtd/mybatis-3-mapper.dtd'>" +
        "<mapper namespace='UserMapper'>" +
        "  <select id='selectUsers'>" +
        "    SELECT * FROM users WHERE name = #{name}" +
        "  </select>" +
        "</mapper>";
    
    // When: Parse using MyBatis XMLMapperBuilder
    InputStream inputStream = new ByteArrayInputStream(xml.getBytes());
    XMLMapperBuilder builder = new XMLMapperBuilder(
        inputStream, 
        configuration, 
        "UserMapper.xml", 
        configuration.getSqlFragments()
    );
    builder.parse();
    
    // Then: Should have MappedStatement
    MappedStatement ms = configuration.getMappedStatement("UserMapper.selectUsers");
    assertThat(ms).isNotNull();
    assertThat(ms.getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
}
```

#### Test 4.2：使用 SqlNode 分析动态 SQL

```java
@Test
@DisplayName("Should analyze dynamic SQL using SqlNode")
void testAnalyzeDynamicSqlWithSqlNode() {
    // Given: Dynamic SQL with <if> tag
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='search'>" +
        "    SELECT * FROM users" +
        "    <where>" +
        "      <if test='name != null'>name = #{name}</if>" +
        "      <if test='age != null'>AND age = #{age}</if>" +
        "    </where>" +
        "  </select>" +
        "</mapper>";
    
    // When: Parse and get SqlNode
    Configuration config = new Configuration();
    // ... parse mapper ...
    MappedStatement ms = config.getMappedStatement("UserMapper.search");
    SqlSource sqlSource = ms.getSqlSource();
    
    // Then: Should be DynamicSqlSource
    assertThat(sqlSource).isInstanceOf(DynamicSqlSource.class);
    
    // When: Generate SQL with different parameters
    Map<String, Object> params1 = new HashMap<>();
    params1.put("name", "Alice");
    params1.put("age", 25);
    BoundSql boundSql1 = sqlSource.getBoundSql(params1);
    
    Map<String, Object> params2 = new HashMap<>();
    BoundSql boundSql2 = sqlSource.getBoundSql(params2);
    
    // Then: Should generate different SQL
    assertThat(boundSql1.getSql()).contains("name = ?");
    assertThat(boundSql1.getSql()).contains("age = ?");
    assertThat(boundSql2.getSql()).doesNotContain("name");
    assertThat(boundSql2.getSql()).doesNotContain("age");
}
```

#### Test 4.3：使用 BoundSql 验证参数位置

```java
@Test
@DisplayName("Should detect parameter position using BoundSql")
void testDetectParameterPositionWithBoundSql() {
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='search'>" +
        "    SELECT * FROM users WHERE name = #{name} ORDER BY ${orderBy}" +
        "  </select>" +
        "</mapper>";
    
    // Parse and get BoundSql
    Configuration config = new Configuration();
    // ... parse ...
    MappedStatement ms = config.getMappedStatement("UserMapper.search");
    
    Map<String, Object> params = new HashMap<>();
    params.put("name", "Alice");
    params.put("orderBy", "name");
    
    BoundSql boundSql = ms.getBoundSql(params);
    
    // Analyze SQL structure
    String sql = boundSql.getSql();
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    
    // Should detect:
    // - #{name} in WHERE clause (safe)
    // - ${orderBy} in ORDER BY clause (risk)
    assertThat(sql).contains("WHERE name = ?");
    assertThat(sql).contains("ORDER BY name");  // ${} is replaced
    assertThat(parameterMappings).hasSize(1);  // Only #{name}
}
```

## 📦 实施步骤

### Step 1：搭建测试框架（1天）

```java
// Test base class
public abstract class MyBatisSemanticAnalysisTestBase {
    
    protected Configuration configuration;
    protected SecurityRulesConfig securityConfig;
    
    @BeforeEach
    void setUp() {
        configuration = new Configuration();
        
        // 预注册常见别名（避免 TypeAlias 解析失败）
        TypeAliasRegistry registry = configuration.getTypeAliasRegistry();
        registry.registerAlias("string", String.class);
        registry.registerAlias("int", Integer.class);
        registry.registerAlias("long", Long.class);
        registry.registerAlias("map", Map.class);
        registry.registerAlias("list", List.class);
        // 对于未知的别名，使用 Object.class
        registry.registerAlias("User", Object.class);
        registry.registerAlias("Order", Object.class);
        
        securityConfig = new SecurityRulesConfig();
    }
    
    protected Element parseXml(String xml) {
        // Parse XML to DOM4J Element
        SAXReader reader = new SAXReader();
        return reader.read(new StringReader(xml)).getRootElement();
    }
    
    protected MappedStatement parseMybatisMapper(String xml) {
        // Parse using MyBatis XMLMapperBuilder（不需要业务类）
        try {
            InputStream is = new ByteArrayInputStream(xml.getBytes("UTF-8"));
            XMLMapperBuilder builder = new XMLMapperBuilder(
                is, 
                configuration, 
                "test.xml", 
                configuration.getSqlFragments()
            );
            builder.parse();
            
            // 获取第一个 MappedStatement
            return configuration.getMappedStatements().iterator().next();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse mapper", e);
        }
    }
    
    protected MapperInterfaceInfo parseJavaInterface(String javaCode) {
        // Parse Java interface (using JavaParser)
        CompilationUnit cu = StaticJavaParser.parse(javaCode);
        MapperInterfaceAnalyzer analyzer = new MapperInterfaceAnalyzer();
        return analyzer.analyze(cu);
    }
    
    protected Map<String, Object> createTestParams(Object... keyValues) {
        // Helper method to create test parameters（不需要业务 POJO）
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return params;
    }
}
```

### Step 2：实现参数风险分析（3-4天）

**Day 1**: 编写测试 1.1-1.3
**Day 2**: 实现 ParameterRiskAnalyzer，让测试通过
**Day 3**: 编写更多边界测试，完善实现
**Day 4**: 重构和优化

### Step 3：实现分页检测（3-4天）

**Day 1**: 编写测试 2.1-2.2（基础分页）
**Day 2**: 实现 PaginationDetector 基础功能
**Day 3**: 编写测试 2.3-2.4（MyBatis-Plus）
**Day 4**: 实现 MyBatis-Plus 分页检测

### Step 4：集成 MyBatis 官方代码（3-4天）

**Day 1**: 编写测试 4.1-4.3
**Day 2**: 集成 XMLMapperBuilder
**Day 3**: 使用 SqlNode 分析动态 SQL
**Day 4**: 使用 BoundSql 验证参数

### Step 5：实现动态条件分析（3-4天）

**Day 1**: 编写测试 3.1-3.2
**Day 2**: 实现 DynamicConditionAnalyzer
**Day 3**: 场景生成和优化
**Day 4**: 完善和测试

### Step 6：集成测试和文档（2-3天）

**Day 1**: 端到端集成测试
**Day 2**: 性能测试和优化
**Day 3**: 文档编写

## 🎯 TDD 原则

1. **红-绿-重构循环**
   - 🔴 Red: 先写测试，测试失败
   - 🟢 Green: 写最少的代码让测试通过
   - 🔵 Refactor: 重构代码，保持测试通过

2. **测试优先**
   - 每个功能都先写测试
   - 测试即文档
   - 测试即规格

3. **小步前进**
   - 每次只实现一个小功能
   - 频繁运行测试
   - 持续集成

4. **测试覆盖率**
   - 目标：> 90% 代码覆盖率
   - 关键路径：100% 覆盖
   - 边界情况：完整测试

## 📊 预期成果

### 测试统计

| 模块 | 测试数量 | 覆盖率 |
|------|---------|--------|
| ParameterRiskAnalyzer | 15+ | > 95% |
| PaginationDetector | 12+ | > 90% |
| DynamicConditionAnalyzer | 10+ | > 90% |
| MyBatis Integration | 8+ | > 85% |
| **总计** | **45+** | **> 90%** |

### 功能清单

- ✅ 检测 ORDER BY 中的 `${}`（HIGH）
- ✅ 检测 WHERE 中的 `${}`（CRITICAL）
- ✅ 检测 LIMIT 中的 `${}`（LOW/MEDIUM）
- ✅ 白名单支持
- ✅ 检测缺少分页的大表查询
- ✅ 检测 LIMIT 值过大
- ✅ 检测 MyBatis RowBounds 分页
- ✅ 检测 MyBatis-Plus IPage 分页
- ✅ 检测 MyBatis-Plus Page 参数
- ✅ 检测 DELETE/UPDATE 可能没有 WHERE
- ✅ 使用 MyBatis XMLMapperBuilder 解析
- ✅ 使用 SqlNode 分析动态 SQL
- ✅ 使用 BoundSql 验证参数位置
- ✅ 配置化规则
- ✅ 精确的修复建议

## 🔗 参考资源

- [MyBatis GitHub](https://github.com/mybatis/mybatis-3)
- [MyBatis 官方文档](https://mybatis.org/mybatis-3/)
- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [TDD 最佳实践](https://martinfowler.com/bliki/TestDrivenDevelopment.html)


---

## 🔄 更新后的实施计划

### 关键更新

1. **✅ 验证：MyBatis 不需要加载业务类**
2. **✅ 新增：Java 代码检测（JavaParser）**
3. **✅ 新增：结合 XML + Java 的精确分析**

### 更新后的 Phase 划分

#### Phase 0：搭建测试框架（1天）
- [x] 集成 MyBatis Configuration
- [x] 预注册 TypeAlias
- [x] 集成 JavaParser
- [x] 准备测试工具方法

#### Phase 1：验证 MyBatis 集成（2天）

**测试 1.1：验证不需要业务类**
```java
@Test
@DisplayName("Verify: MyBatis parsing does not require business classes")
void testParseMapperWithoutBusinessClasses() {
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='selectUser' resultType='com.example.NonExistentUser'>" +
        "    SELECT * FROM users WHERE id = #{id}" +
        "  </select>" +
        "</mapper>";
    
    // Should parse successfully without loading NonExistentUser
    assertDoesNotThrow(() -> parseMybatisMapper(xml));
}
```

**测试 1.2：使用 Map 代替业务 POJO**
```java
@Test
@DisplayName("Should use Map instead of business POJO for BoundSql")
void testBoundSqlWithMap() {
    MappedStatement ms = parseMybatisMapper(dynamicSqlXml);
    SqlSource sqlSource = ms.getSqlSource();
    
    // Use Map（不需要业务类）
    Map<String, Object> params = Map.of("name", "Alice", "age", 25);
    BoundSql boundSql = sqlSource.getBoundSql(params);
    
    assertThat(boundSql.getSql()).contains("name = ?");
}
```

#### Phase 2：Mapper 接口分析（3天）

**测试 2.1：解析 Mapper 接口**
```java
@Test
@DisplayName("Should parse Mapper interface and extract parameter types")
void testParseMapperInterface() {
    String javaCode = 
        "public interface UserMapper {" +
        "  List<User> search(@Param(\"orderBy\") String orderBy);" +
        "  IPage<User> selectPage(Page<?> page);" +
        "}";
    
    MapperInterfaceInfo info = parseJavaInterface(javaCode);
    
    // Verify: Parameter type extracted
    MethodInfo search = info.getMethod("search");
    ParameterInfo orderBy = search.getParameter("orderBy");
    assertEquals("String", orderBy.getType());
}
```

**测试 2.2：检测分页参数**
```java
@Test
@DisplayName("Should detect MyBatis-Plus IPage pagination")
void testDetectIPagePagination() {
    String javaCode = 
        "public interface UserMapper {" +
        "  IPage<User> selectPage(Page<?> page);" +
        "}";
    
    MapperInterfaceInfo info = parseJavaInterface(javaCode);
    MethodInfo method = info.getMethod("selectPage");
    
    assertTrue(method.hasPagination());
    assertEquals(PaginationType.MYBATIS_PLUS_IPAGE, method.getPaginationType());
}
```

#### Phase 3：结合 XML + Java 分析（3天）

**测试 3.1：匹配参数类型**
```java
@Test
@DisplayName("Should match parameter type from Java interface with XML usage")
void testMatchParameterType() {
    // XML
    String xml = 
        "<mapper namespace='UserMapper'>" +
        "  <select id='search'>" +
        "    SELECT * FROM users ORDER BY ${orderBy}" +
        "  </select>" +
        "</mapper>";
    
    // Java
    String javaCode = 
        "public interface UserMapper {" +
        "  List<User> search(@Param(\"orderBy\") String orderBy);" +
        "}";
    
    // Analyze
    MappedStatement ms = parseMybatisMapper(xml);
    MapperInterfaceInfo interfaceInfo = parseJavaInterface(javaCode);
    
    CombinedAnalyzer analyzer = new CombinedAnalyzer();
    List<SecurityRisk> risks = analyzer.analyze(ms, interfaceInfo);
    
    // Verify: Knows parameter type is String
    SecurityRisk risk = risks.get(0);
    assertEquals(RiskLevel.HIGH, risk.getLevel());
    assertTrue(risk.getContext().get("parameterType").equals("String"));
}
```

**测试 3.2：基于类型的风险评估**
```java
@Test
@DisplayName("Should assess risk based on parameter type")
void testRiskAssessmentBasedOnType() {
    // Test 1: String in ORDER BY → HIGH
    assertRisk("String", SqlPosition.ORDER_BY, RiskLevel.HIGH);
    
    // Test 2: Integer in LIMIT → LOW
    assertRisk("Integer", SqlPosition.LIMIT, RiskLevel.LOW);
    
    // Test 3: String in WHERE → CRITICAL
    assertRisk("String", SqlPosition.WHERE, RiskLevel.CRITICAL);
}
```

### 依赖管理

```xml
<dependencies>
    <!-- MyBatis 核心（< 2MB，不需要业务类） -->
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

### 时间估算（更新）

| Phase | 内容 | 时间 | 累计 |
|-------|------|------|------|
| Phase 0 | 搭建测试框架 | 1天 | 1天 |
| Phase 1 | 验证 MyBatis 集成 | 2天 | 3天 |
| Phase 2 | Mapper 接口分析 | 3天 | 6天 |
| Phase 3 | 结合 XML + Java | 3天 | 9天 |
| Phase 4 | 参数风险分析 | 3天 | 12天 |
| Phase 5 | 分页检测 | 3天 | 15天 |
| Phase 6 | 动态条件分析 | 3天 | 18天 |
| Phase 7 | 配置化 | 2天 | 20天 |
| Phase 8 | 集成测试 | 2天 | 22天 |

**总计：22天（约 4.5 周）**

### 关键里程碑

- **Week 1 结束**：MyBatis 集成 + Mapper 接口分析完成
- **Week 2 结束**：结合分析 + 参数风险分析完成
- **Week 3 结束**：分页检测 + 动态条件分析完成
- **Week 4 结束**：配置化 + 集成测试完成

### MVP 定义

**最小可行产品（2周）**：
- ✅ MyBatis 集成（不需要业务类）
- ✅ Mapper 接口分析
- ✅ 结合 XML + Java 分析
- ✅ 基于类型的参数风险评估
- ✅ 基础分页检测

**提供价值**：80%

