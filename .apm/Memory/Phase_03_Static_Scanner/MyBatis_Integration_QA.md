# MyBatis 集成关键问题解答

## 问题 1：MyBatis 原生类是否依赖业务类的加载？

### 答案：✅ 不依赖！可以直接复用！

### 详细分析

#### MyBatis 的类加载机制

**解析阶段（我们需要的）**：
```java
// 1. 创建 Configuration
Configuration config = new Configuration();

// 2. 解析 Mapper XML
XMLMapperBuilder builder = new XMLMapperBuilder(
    inputStream, config, "UserMapper.xml", config.getSqlFragments()
);
builder.parse();  // ✅ 不需要加载业务类！

// 3. 获取 MappedStatement
MappedStatement ms = config.getMappedStatement("UserMapper.selectUser");
```

**执行阶段（我们不需要）**：
```java
// 这个阶段才需要业务类
SqlSession session = sqlSessionFactory.openSession();
User user = session.selectOne("UserMapper.selectUser", 1);  // ← 需要 User.class
```

#### 关键发现

| 阶段 | 操作 | 是否需要业务类 |
|------|------|---------------|
| **解析阶段** | XMLMapperBuilder.parse() | ❌ 不需要 |
| **解析阶段** | 获取 MappedStatement | ❌ 不需要 |
| **解析阶段** | 获取 SqlSource | ❌ 不需要 |
| **解析阶段** | 获取 SqlNode | ❌ 不需要 |
| **执行阶段** | 执行查询 | ✅ 需要 |
| **执行阶段** | 结果映射 | ✅ 需要 |

**我们只需要解析阶段，所以不需要业务类！**

#### 验证代码

```java
@Test
@DisplayName("Verify: MyBatis parsing does not require business classes")
void testParseMapperWithoutBusinessClasses() {
    // Given: Mapper XML references non-existent business class
    String xml = 
        "<?xml version='1.0' encoding='UTF-8'?>" +
        "<!DOCTYPE mapper PUBLIC '-//mybatis.org//DTD Mapper 3.0//EN' " +
        "'http://mybatis.org/dtd/mybatis-3-mapper.dtd'>" +
        "<mapper namespace='UserMapper'>" +
        "  <select id='selectUser' resultType='com.example.NonExistentUser'>" +
        "    SELECT * FROM users WHERE id = #{id}" +
        "  </select>" +
        "</mapper>";
    
    // When: Parse without loading NonExistentUser class
    Configuration config = new Configuration();
    InputStream is = new ByteArrayInputStream(xml.getBytes());
    XMLMapperBuilder builder = new XMLMapperBuilder(
        is, config, "UserMapper.xml", config.getSqlFragments()
    );
    
    // Then: Should parse successfully
    assertDoesNotThrow(() -> builder.parse());
    
    // Verify: MappedStatement is created
    MappedStatement ms = config.getMappedStatement("UserMapper.selectUser");
    assertNotNull(ms);
    
    // Verify: resultType is just a string
    String resultType = ms.getResultMaps().get(0).getType().getName();
    assertEquals("com.example.NonExistentUser", resultType);
    
    // ✅ Success! No ClassNotFoundException
}
```

### 潜在问题和解决方案

#### 问题 1：TypeAlias 解析

**问题**：
```xml
<select id="selectUser" resultType="User">
  SELECT * FROM users
</select>
```
如果 "User" 别名没有注册，会抛出异常。

**解决方案 A：预注册常见别名**
```java
Configuration config = new Configuration();
TypeAliasRegistry registry = config.getTypeAliasRegistry();

// 注册为 Object.class（不需要实际的业务类）
registry.registerAlias("User", Object.class);
registry.registerAlias("Order", Object.class);
registry.registerAlias("Product", Object.class);
```

**解决方案 B：容错处理**
```java
try {
    builder.parse();
} catch (TypeException e) {
    // 提取别名，注册为 Object.class，重试
    String alias = extractAliasFromException(e);
    config.getTypeAliasRegistry().registerAlias(alias, Object.class);
    builder.parse();  // 重试
}
```

**解决方案 C：扫描项目自动注册**
```java
// 扫描项目中的所有类
scanProjectClasses(projectDir).forEach(className -> {
    String alias = getSimpleName(className);
    registry.registerAlias(alias, Object.class);
});
```

#### 问题 2：BoundSql 生成需要参数对象

**问题**：
```java
// 生成 BoundSql 需要参数对象
BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
```

**解决方案：使用 Map 代替业务 POJO**
```java
// 不需要业务类，使用 Map
Map<String, Object> params = new HashMap<>();
params.put("id", 1);
params.put("name", "test");
params.put("age", 25);

BoundSql boundSql = sqlSource.getBoundSql(params);
String sql = boundSql.getSql();  // ✅ 成功生成 SQL
```

#### 问题 3：OGNL 表达式求值

**问题**：
```xml
<if test="name != null and name.length() > 0">
  WHERE name = #{name}
</if>
```

**解决方案：提供合适的测试数据**
```java
Map<String, Object> params = new HashMap<>();
params.put("name", "test");  // String 对象，有 length() 方法
params.put("age", 25);       // Integer 对象

// 生成多个测试场景
List<Map<String, Object>> testCases = Arrays.asList(
    Collections.emptyMap(),           // 所有条件为 null
    Map.of("name", "test"),           // 只有 name
    Map.of("age", 25),                // 只有 age
    Map.of("name", "test", "age", 25) // 都有
);

for (Map<String, Object> testParams : testCases) {
    BoundSql boundSql = sqlSource.getBoundSql(testParams);
    analyzeSql(boundSql.getSql());
}
```

### 依赖管理

```xml
<dependencies>
    <!-- MyBatis 核心 -->
    <dependency>
        <groupId>org.mybatis</groupId>
        <artifactId>mybatis</artifactId>
        <version>3.5.19</version>
    </dependency>
</dependencies>
```

**MyBatis 的传递依赖**（非常干净）：
```
mybatis-3.5.19.jar
├── ognl-3.3.4.jar          (OGNL 表达式，60KB)
├── javassist-3.29.2-GA.jar (字节码操作，800KB)
└── slf4j-api-2.0.9.jar     (日志接口，60KB)
```

**总大小**：< 2MB，非常轻量！

✅ **结论：可以安全引入，不会有依赖冲突**

---

## 问题 2：Java 代码如何更有效地检测？

### 答案：三层检测策略

### Layer 1：Mapper 接口分析（基础，必须实施）

#### 目标
- 检测参数类型
- 检测分页参数
- 检测返回类型

#### 工具
JavaParser - 轻量级 Java 代码解析器

```xml
<dependency>
    <groupId>com.github.javaparser</groupId>
    <artifactId>javaparser-core</artifactId>
    <version>3.25.8</version>
</dependency>
```

#### 实现示例

```java
public class MapperInterfaceAnalyzer {
    
    public MapperInterfaceInfo analyze(File javaFile) {
        // 1. 解析 Java 文件
        CompilationUnit cu = StaticJavaParser.parse(javaFile);
        
        // 2. 找到 Mapper 接口
        ClassOrInterfaceDeclaration mapperInterface = 
            cu.findFirst(ClassOrInterfaceDeclaration.class)
              .filter(ClassOrInterfaceDeclaration::isInterface)
              .orElse(null);
        
        if (mapperInterface == null) return null;
        
        MapperInterfaceInfo info = new MapperInterfaceInfo();
        info.setName(mapperInterface.getNameAsString());
        
        // 3. 分析每个方法
        for (MethodDeclaration method : mapperInterface.getMethods()) {
            MethodInfo methodInfo = analyzeMethod(method);
            info.addMethod(methodInfo);
        }
        
        return info;
    }
    
    private MethodInfo analyzeMethod(MethodDeclaration method) {
        MethodInfo info = new MethodInfo();
        info.setName(method.getNameAsString());
        
        // 分析返回类型
        String returnType = method.getTypeAsString();
        if (returnType.contains("IPage") || returnType.contains("Page")) {
            info.setPagination(PaginationType.MYBATIS_PLUS_RETURN);
        }
        
        // 分析参数
        for (Parameter param : method.getParameters()) {
            ParameterInfo paramInfo = analyzeParameter(param);
            info.addParameter(paramInfo);
            
            // 检测分页参数
            String paramType = param.getTypeAsString();
            if (paramType.contains("Page") || 
                paramType.contains("IPage") || 
                paramType.contains("RowBounds")) {
                info.setPagination(PaginationType.PARAMETER);
            }
        }
        
        return info;
    }
    
    private ParameterInfo analyzeParameter(Parameter param) {
        ParameterInfo info = new ParameterInfo();
        info.setName(param.getNameAsString());
        info.setType(param.getTypeAsString());
        
        // 检查 @Param 注解
        param.getAnnotationByName("Param").ifPresent(anno -> {
            String paramName = anno.asNormalAnnotationExpr()
                .getPairs().get(0)
                .getValue().toString()
                .replace("\"", "");
            info.setParamName(paramName);
        });
        
        return info;
    }
}
```

#### 检测示例

**Mapper 接口**：
```java
public interface UserMapper {
    // 1. 检测：String orderBy → 可能有风险
    List<User> search(@Param("orderBy") String orderBy);
    
    // 2. 检测：返回 IPage → 有分页 ✅
    IPage<User> selectPage(Page<?> page);
    
    // 3. 检测：没有分页参数 → 可能有风险 ⚠️
    List<User> selectAll();
    
    // 4. 检测：int pageSize → 相对安全（数字类型）
    List<User> list(@Param("pageSize") int pageSize);
}
```

**检测结果**：
```
[HIGH] Method: search
  Parameter: orderBy (String)
  Risk: String parameter in potential ORDER BY clause
  
[OK] Method: selectPage
  Pagination: MyBatis-Plus IPage detected
  
[WARNING] Method: selectAll
  Risk: No pagination parameter detected
  
[LOW] Method: list
  Parameter: pageSize (int)
  Risk: Numeric type, relatively safe
```

### Layer 2：结合 XML + Java 分析（核心，必须实施）

#### 目标
- 匹配 XML 中的参数使用和 Java 接口中的参数定义
- 基于参数类型提供精确的风险评估

#### 实现示例

```java
public class CombinedAnalyzer {
    
    public List<SecurityRisk> analyze(
        MappedStatement mappedStatement,
        MapperInterfaceInfo interfaceInfo
    ) {
        List<SecurityRisk> risks = new ArrayList<>();
        
        // 1. 从 XML 中提取参数使用
        SqlSource sqlSource = mappedStatement.getSqlSource();
        List<ParameterUsage> usages = extractParameterUsages(sqlSource);
        
        // 2. 从 Java 接口中获取参数定义
        String methodId = mappedStatement.getId();
        MethodInfo methodInfo = interfaceInfo.getMethod(methodId);
        
        // 3. 匹配参数
        for (ParameterUsage usage : usages) {
            ParameterInfo paramInfo = methodInfo.getParameter(usage.getName());
            
            if (paramInfo == null) continue;
            
            // 4. 基于类型和位置评估风险
            RiskLevel risk = assessRisk(usage, paramInfo);
            
            if (risk != RiskLevel.NONE) {
                risks.add(createRisk(usage, paramInfo, risk));
            }
        }
        
        return risks;
    }
    
    private RiskLevel assessRisk(
        ParameterUsage usage, 
        ParameterInfo paramInfo
    ) {
        // 基于位置和类型的智能评估
        if (usage.isStringSubstitution()) {  // ${}
            if (usage.getPosition() == SqlPosition.ORDER_BY) {
                if (paramInfo.getType().equals("String")) {
                    return RiskLevel.HIGH;  // String 在 ORDER BY
                }
            }
            if (usage.getPosition() == SqlPosition.WHERE) {
                return RiskLevel.CRITICAL;  // WHERE 子句注入
            }
            if (usage.getPosition() == SqlPosition.LIMIT) {
                if (paramInfo.getType().equals("Integer") || 
                    paramInfo.getType().equals("int")) {
                    return RiskLevel.LOW;  // 数字类型，风险较低
                } else {
                    return RiskLevel.MEDIUM;  // 字符串类型
                }
            }
        }
        
        return RiskLevel.NONE;
    }
}
```

#### 检测效果对比

**旧方案**（只看 XML）：
```
[CRITICAL] SQL injection risk - ${orderBy} detected
```
❌ 问题：不知道参数类型，无法精确评估

**新方案**（结合 Java）：
```
[HIGH] SQL Injection Risk - Dynamic ORDER BY
  Parameter: ${orderBy} (String type)
  Location: ORDER BY clause
  Risk: User can inject arbitrary ORDER BY expression
  
  Suggestion:
    1. Use whitelist validation:
       String[] allowed = {"name", "age", "created_at"};
       if (!Arrays.asList(allowed).contains(orderBy)) {
         throw new IllegalArgumentException();
       }
```
✅ 改进：知道参数类型，提供精确的建议

### Layer 3：调用链分析（增强，可选实施）

#### 目标
- 检测参数来源（Controller 层）
- 检测白名单验证
- 检测参数校验

#### 实现示例

```java
public class CallChainAnalyzer {
    
    public List<SecurityRisk> analyze(File controllerFile, File serviceFile) {
        // 1. 解析 Controller
        CompilationUnit controllerCu = StaticJavaParser.parse(controllerFile);
        
        // 2. 找到 @RestController 类
        ClassOrInterfaceDeclaration controller = 
            controllerCu.findFirst(ClassOrInterfaceDeclaration.class)
                .filter(c -> c.getAnnotationByName("RestController").isPresent())
                .orElse(null);
        
        if (controller == null) return Collections.emptyList();
        
        List<SecurityRisk> risks = new ArrayList<>();
        
        // 3. 分析每个方法
        for (MethodDeclaration method : controller.getMethods()) {
            if (method.getAnnotationByName("GetMapping").isPresent() ||
                method.getAnnotationByName("PostMapping").isPresent()) {
                
                // 4. 分析参数来源
                for (Parameter param : method.getParameters()) {
                    if (param.getAnnotationByName("RequestParam").isPresent()) {
                        // 这是用户输入，追踪它的使用
                        risks.addAll(traceParameter(param, method));
                    }
                }
            }
        }
        
        return risks;
    }
    
    private List<SecurityRisk> traceParameter(
        Parameter param, 
        MethodDeclaration method
    ) {
        // 追踪参数在方法体中的使用
        String paramName = param.getNameAsString();
        
        // 查找方法调用
        List<MethodCallExpr> calls = method.findAll(MethodCallExpr.class);
        
        for (MethodCallExpr call : calls) {
            // 检查是否传递了这个参数
            if (call.getArguments().stream()
                .anyMatch(arg -> arg.toString().equals(paramName))) {
                
                // 检查是否有验证
                if (!hasValidation(method, paramName)) {
                    return Collections.singletonList(new SecurityRisk(
                        RiskLevel.HIGH,
                        "User input passed to " + call.getNameAsString() + 
                        " without validation",
                        "Add whitelist validation before passing to mapper"
                    ));
                }
            }
        }
        
        return Collections.emptyList();
    }
}
```

### 实施优先级

| 层级 | 功能 | 优先级 | 时间 | 价值 |
|------|------|--------|------|------|
| **Layer 1** | Mapper 接口分析 | P0 | 1周 | 40% |
| **Layer 2** | 结合 XML + Java | P0 | 1周 | 50% |
| **Layer 3** | 调用链分析 | P1 | 1周 | 10% |

**最小可行产品（MVP）**：
- Layer 1 + Layer 2 = 2周
- 可以提供 90% 的价值

### 总结

1. ✅ **MyBatis 原生类可以直接复用**
   - 不需要加载业务类
   - 依赖干净，< 2MB
   - 完整支持所有 MyBatis 标签

2. ✅ **Java 代码检测：三层策略**
   - Layer 1: Mapper 接口分析（必须）
   - Layer 2: 结合 XML + Java（必须）
   - Layer 3: 调用链分析（可选）

3. ✅ **实施时间：3周**
   - Week 1: MyBatis 集成
   - Week 2: JavaParser 集成
   - Week 3: 结合分析

4. ✅ **关键优势**
   - 不需要业务类
   - 准确的类型分析
   - 完整的分页检测
   - TDD 保证质量

