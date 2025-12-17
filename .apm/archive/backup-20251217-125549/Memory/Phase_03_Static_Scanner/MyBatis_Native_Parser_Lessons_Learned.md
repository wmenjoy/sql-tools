# MyBatis 原生解析器尝试 - 经验教训

**日期**: 2025-12-15  
**状态**: ❌ 不可行（静态扫描场景）  

## 问题背景

在实际项目测试中发现，简单的正则替换方案对于包含动态 SQL 标签（`<if>`, `<choose>`, `<foreach>` 等）的 MyBatis mapper 文件处理效果不佳，导致大量不完整的 SQL 无法被 JSqlParser 解析。

## 尝试的方案

使用 MyBatis 原生 `XMLMapperBuilder` 来解析 mapper 文件，期望能够：
1. 正确处理动态 SQL 标签
2. 生成完整的可执行 SQL
3. 自动替换 `#{}` 占位符为 `?`

## 实施细节

### 代码实现

1. **添加 MyBatis 依赖**
```xml
<dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis</artifactId>
</dependency>
```

2. **创建 `MybatisSqlExtractor`**
```java
public class MybatisSqlExtractor {
    public List<ExtractedSql> extractSql(File mapperFile) {
        Configuration configuration = new Configuration();
        XMLMapperBuilder builder = new XMLMapperBuilder(
            inputStream, configuration, 
            mapperFile.getAbsolutePath(),
            configuration.getSqlFragments()
        );
        builder.parse();
        
        // 从 MappedStatement 提取 SQL
        for (MappedStatement statement : configuration.getMappedStatements()) {
            BoundSql boundSql = statement.getSqlSource().getBoundSql(new HashMap<>());
            String sql = boundSql.getSql();
            // ...
        }
    }
}
```

3. **集成到 `SqlScanner`**
   - 按文件分组 SQL entries
   - 对 XML 文件尝试使用 MyBatis 提取器
   - 失败时回退到正则替换方案

## 失败原因

### 核心问题：ClassNotFoundException

MyBatis `XMLMapperBuilder` 需要加载 mapper 中引用的所有 Java 类：

```
Error resolving class. Cause: org.apache.ibatis.type.TypeException: 
Could not resolve type alias 'cn.org.bjca.footstone.gateway.dao.model.ApiVersion'.  
Cause: java.lang.ClassNotFoundException: Cannot find class: cn.org.bjca.footstone.gateway.dao.model.ApiVersion
```

### 为什么会失败？

1. **类型解析依赖**
   - `resultType="com.example.User"` 需要加载 `User` 类
   - `parameterType="com.example.Query"` 需要加载 `Query` 类
   - TypeHandler 注册需要实际的类对象

2. **静态扫描的限制**
   - 扫描工具运行在独立的 JVM 中
   - 没有被扫描项目的类路径
   - 无法加载项目的编译后的 `.class` 文件

3. **动态类路径加载的复杂性**
   - 需要找到项目的 `target/classes` 目录
   - 需要加载所有依赖的 JAR 文件
   - 可能存在类版本冲突
   - Maven 多模块项目更复杂

## 测试结果

在 `api-gateway-manager` 项目上测试：
- **总 SQL 数**: 600+ 条
- **MyBatis 提取成功**: 0 条
- **回退到正则方案**: 600+ 条
- **失败的 mapper 文件**: 47/48 个（所有包含 resultType 的文件）

## 替代方案对比

### 方案 A: 动态类路径加载 ⚠️

**思路**: 动态加载项目的编译输出和依赖

```java
URLClassLoader classLoader = new URLClassLoader(
    projectClasspathUrls,
    getClass().getClassLoader()
);
Configuration config = new Configuration();
config.setDefaultClassLoader(classLoader);
```

**优点**:
- 理论上可以解决 ClassNotFoundException
- 完整支持 MyBatis 所有特性

**缺点**:
- 实现极其复杂
- 需要解析 `pom.xml` 或 `build.gradle`
- 需要递归解析所有依赖
- 类版本冲突风险高
- 性能开销大
- 不适合 CI/CD 快速扫描

**结论**: ❌ 不推荐

### 方案 B: 改进的正则替换 + 动态 SQL 预处理 ✅

**思路**: 增强当前的正则方案，添加动态 SQL 标签处理

```java
// 1. 移除或展开动态 SQL 标签
String processed = removeDynamicTags(rawSql);

// 2. 替换占位符
String normalized = processed
    .replaceAll("#\\{[^}]+\\}", "?")
    .replaceAll("\\$\\{[^}]+\\}", "?");

// 3. 验证
validator.validate(normalized);
```

**优点**:
- 实现简单
- 无需类路径
- 性能好
- 适合静态扫描

**缺点**:
- 无法处理所有动态 SQL 场景
- 可能产生不完整的 SQL

**结论**: ✅ 推荐（当前最佳方案）

### 方案 C: 混合方案（静态 + 运行时）✅

**思路**: 
- 静态扫描：使用改进的正则方案，快速反馈
- 运行时验证：Phase 4 拦截实际执行的 SQL

**优点**:
- 双层防护
- 静态扫描覆盖大部分场景
- 运行时捕获所有实际执行的 SQL

**缺点**:
- 需要两套系统

**结论**: ✅ 推荐（长期方案）

## 经验教训

### 1. 静态分析的局限性

- 静态扫描无法获得完整的运行时环境
- 依赖外部类的解析器不适合静态场景
- 需要权衡完整性和可行性

### 2. MyBatis 设计哲学

- MyBatis 设计为运行时框架
- XML 解析依赖完整的类型系统
- 不是为静态分析设计的

### 3. 工具选择原则

- **简单优于复杂**: 正则替换虽简单，但可靠
- **实用优于完美**: 80% 覆盖率足够有价值
- **渐进式改进**: 先解决主要问题，再优化边缘情况

## 下一步行动

### 立即行动

1. ✅ **回退到正则替换方案**
   - 保留当前的占位符处理逻辑
   - 移除 MyBatis 原生解析器代码

2. ✅ **增强动态 SQL 处理**
   - 添加 `<if>` 标签移除逻辑
   - 添加 `<choose>` 标签展开逻辑
   - 添加 `<foreach>` 标签简化逻辑

3. ✅ **改进错误处理**
   - 对无法解析的 SQL，记录详细信息
   - 提供更有用的错误提示

### 中期优化

1. **动态 SQL 标签库**
   - 创建专门的动态 SQL 预处理器
   - 支持常见的 MyBatis 动态标签
   - 生成多个 SQL 变体进行验证

2. **智能回退策略**
   - 检测 SQL 完整性
   - 自动选择最佳处理方式

### 长期规划

1. **Phase 4: 运行时验证**
   - 实现 MyBatis 拦截器
   - 捕获实际执行的 SQL
   - 与静态扫描结果对比

2. **混合验证报告**
   - 合并静态和运行时结果
   - 提供完整的覆盖率分析

## 代码清理

需要移除或重构的代码：

1. ❌ `MybatisSqlExtractor.java` - 删除
2. ❌ `SqlScanner.validateWithMybatisSql()` - 删除
3. ✅ `SqlScanner.validateWithRegexNormalization()` - 保留并增强
4. ❌ MyBatis 依赖 - 考虑移除（如果其他地方不用）

## 结论

**MyBatis 原生解析器在静态扫描场景下不可行**，原因是：
1. 需要完整的类路径环境
2. 动态类加载过于复杂且不可靠
3. 性能和维护成本过高

**推荐方案**：
- **短期**: 改进正则替换 + 动态 SQL 预处理
- **长期**: 静态扫描 + 运行时验证的混合方案

这次尝试虽然失败，但明确了技术边界，为后续方案选择提供了重要依据。

---

**教训**: 在选择技术方案时，要充分考虑实际运行环境的限制，不要盲目追求"完美"的解决方案。实用、可靠、可维护比理论上的完美更重要。

