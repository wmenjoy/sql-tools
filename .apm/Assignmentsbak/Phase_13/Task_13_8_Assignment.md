---
task_ref: "Task 13.8 - MyBatis-Plus Version Compatibility Layer"
agent_assignment: "Agent_Advanced_Interceptor"
memory_log_path: ".apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_8_MyBatisPlus_Compatibility_Implementation.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
phase: 13
task_id: 13.8
estimated_duration: 3 days
dependencies: [Task_13.1, Task_13.2, Task_13.6]
parallel_with: [Task_13.7]
priority: "High"
---

# Task 13.8 Assignment: MyBatis-Plus Version Compatibility Layer

## 任务目标

创建 MyBatis-Plus 版本兼容性层支持 3.4.x 和 3.5.x，实现 MyBatisPlusVersionDetector 检测版本特定标记类，创建 IPageDetector 使用基于反射的 IPage 检测从参数类型和 Map 内容中提取，创建 QueryWrapperInspector 使用反射从 QueryWrapper/LambdaQueryWrapper 提取条件，创建 WrapperTypeDetector 识别 wrapper 类型，实现空 wrapper 检测关键用于安全验证。

---

## 背景说明

### 为什么需要 MyBatis-Plus 版本兼容性层？

**问题**: MyBatis-Plus 3.4.x 和 3.5.x API 存在差异:

```java
// MyBatis-Plus 3.4.x
public interface IPage<T> {
    long getCurrent();  // Older signature
    long getSize();
}

// MyBatis-Plus 3.5.x
public interface IPage<T> {
    long getCurrent();  // Signature may change
    long getSize();
    // + New methods
}
```

**MyBatis-Plus 特有的挑战**:
1. **IPage 分页检测**: 参数可能是 IPage 对象或 Map 包含 IPage
2. **QueryWrapper 条件提取**: 需要反射访问 private final expression 字段
3. **空 Wrapper 检测**: 关键安全检查（无条件 = 危险）

**SqlGuard 需要支持的版本**:
- MyBatis-Plus 3.4.0, 3.4.3 (早期版本)
- MyBatis-Plus 3.5.3, 3.5.5 (现代版本)

---

## 核心组件设计

### 1. MyBatisPlusVersionDetector

**目标**: 运行时检测 MyBatis-Plus 版本

**实现方案**:

```java
package com.footstone.sqlguard.compat.mp;

/**
 * MyBatis-Plus version detector using marker class checking.
 *
 * <h2>Detection Strategy</h2>
 * <p>Checks for signature changes in {@code com.baomidou.mybatisplus.core.metadata.IPage}
 * or existence of version-specific classes.
 *
 * @since 1.1.0
 */
public final class MyBatisPlusVersionDetector {

    /**
     * Marker class existing in MyBatis-Plus 3.5.x.
     */
    private static final String MARKER_CLASS_35 =
        "com.baomidou.mybatisplus.core.toolkit.support.SFunction";

    /**
     * Cached detection result.
     */
    private static final boolean IS_35_OR_ABOVE = detectVersion();

    private MyBatisPlusVersionDetector() {
        // Utility class
    }

    /**
     * Checks if MyBatis-Plus version is 3.5.0 or above.
     *
     * @return {@code true} if MyBatis-Plus 3.5.0+, {@code false} if 3.4.x
     */
    public static boolean is35OrAbove() {
        return IS_35_OR_ABOVE;
    }

    /**
     * Detects MyBatis-Plus version using marker class checking.
     *
     * @return {@code true} if marker class exists (3.5.x), {@code false} otherwise
     */
    private static boolean detectVersion() {
        try {
            Class.forName(MARKER_CLASS_35);
            return true;  // MyBatis-Plus 3.5.x
        } catch (ClassNotFoundException e) {
            return false; // MyBatis-Plus 3.4.x
        }
    }
}
```

---

### 2. IPageDetector

**目标**: 检测方法参数中的 IPage 分页对象

**挑战**:
```java
// 场景 1: 直接 IPage 参数
List<User> selectPage(IPage<User> page, QueryWrapper<User> wrapper);

// 场景 2: IPage 包装在 Map 中
@Param("page") IPage<User> page, @Param("ew") QueryWrapper<User> wrapper
// MyBatis-Plus 将 IPage 放入 param map: {"page": IPage, "ew": QueryWrapper}
```

**实现方案**:

```java
package com.footstone.sqlguard.compat.mp;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Detector for IPage pagination in method parameters using reflection.
 *
 * <h2>Detection Strategy</h2>
 * <p>Checks for IPage in two scenarios:
 * <ol>
 *   <li>Direct parameter: {@code parameter instanceof IPage}</li>
 *   <li>Wrapped in Map: {@code ((Map)parameter).get("page") instanceof IPage}</li>
 * </ol>
 *
 * @since 1.1.0
 */
public final class IPageDetector {

    private static final Logger log = LoggerFactory.getLogger(IPageDetector.class);

    /**
     * Common keys for IPage in parameter map.
     */
    private static final String[] PAGE_KEYS = {"page", "Page", "PAGE"};

    private IPageDetector() {
        // Utility class
    }

    /**
     * Detects IPage from method parameter.
     *
     * @param parameter Method parameter object
     * @return Detected IPage or {@code null} if not found
     */
    public static IPage<?> detect(Object parameter) {
        if (parameter == null) {
            return null;
        }

        // Scenario 1: Direct IPage parameter
        if (parameter instanceof IPage) {
            return (IPage<?>) parameter;
        }

        // Scenario 2: IPage wrapped in Map
        if (parameter instanceof Map) {
            Map<?, ?> paramMap = (Map<?, ?>) parameter;
            for (String key : PAGE_KEYS) {
                Object value = paramMap.get(key);
                if (value instanceof IPage) {
                    log.trace("Detected IPage from param map with key: {}", key);
                    return (IPage<?>) value;
                }
            }
        }

        return null;
    }

    /**
     * Checks if parameter contains IPage pagination.
     *
     * @param parameter Method parameter object
     * @return {@code true} if IPage detected, {@code false} otherwise
     */
    public static boolean hasPagination(Object parameter) {
        return detect(parameter) != null;
    }

    /**
     * Extracts pagination info (current, size) from IPage.
     *
     * @param parameter Method parameter object
     * @return String representation of pagination info or {@code null}
     */
    public static String extractPaginationInfo(Object parameter) {
        IPage<?> page = detect(parameter);
        if (page != null) {
            return String.format("IPage(current=%d, size=%d)",
                page.getCurrent(), page.getSize());
        }
        return null;
    }
}
```

---

### 3. QueryWrapperInspector

**目标**: 从 QueryWrapper/LambdaQueryWrapper 提取 WHERE 条件

**挑战**:
```java
// QueryWrapper 内部结构
public abstract class AbstractWrapper<T, R, Children> {
    private final T entity;  // 实体对象
    private final MergeSegments expression;  // ⚠️ private final - 需要反射访问

    // expression 包含 WHERE 条件树
}
```

**实现方案**:

```java
package com.footstone.sqlguard.compat.mp;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * Inspector for extracting conditions from QueryWrapper/LambdaQueryWrapper using reflection.
 *
 * <h2>Purpose</h2>
 * <p>Accesses private {@code expression} field in {@code AbstractWrapper} to:
 * <ul>
 *   <li>Extract WHERE conditions</li>
 *   <li>Detect empty wrappers (critical for no-condition validation)</li>
 *   <li>Analyze complex condition trees</li>
 * </ul>
 *
 * <h2>Empty Wrapper Detection</h2>
 * <p>Empty wrapper = no WHERE conditions = dangerous (selects all rows).
 * This is critical for {@code NoWhereClauseChecker} integration.
 *
 * @since 1.1.0
 */
public final class QueryWrapperInspector {

    private static final Logger log = LoggerFactory.getLogger(QueryWrapperInspector.class);

    /**
     * Cached reflection field for AbstractWrapper.expression.
     */
    private static volatile Field expressionField;

    private QueryWrapperInspector() {
        // Utility class
    }

    /**
     * Checks if QueryWrapper is empty (no conditions).
     *
     * <p><b>Critical for safety validation</b>: Empty wrapper means no WHERE clause,
     * which can lead to full table scans.
     *
     * @param wrapper QueryWrapper or LambdaQueryWrapper
     * @return {@code true} if wrapper has no conditions, {@code false} otherwise
     */
    public static boolean isEmpty(AbstractWrapper<?, ?, ?> wrapper) {
        if (wrapper == null) {
            return true;
        }

        try {
            Object expression = getExpression(wrapper);
            if (expression == null) {
                return true;
            }

            // Check if expression is empty (implementation-specific)
            // MergeSegments has getSqlSegment() method returning SQL string
            Field sqlSegmentField = expression.getClass().getDeclaredField("sqlSegment");
            sqlSegmentField.setAccessible(true);
            Object sqlSegment = sqlSegmentField.get(expression);

            return sqlSegment == null || sqlSegment.toString().trim().isEmpty();
        } catch (Exception e) {
            log.warn("Failed to check if wrapper is empty via reflection", e);
            return false; // Conservative: assume not empty if reflection fails
        }
    }

    /**
     * Extracts WHERE conditions from QueryWrapper.
     *
     * @param wrapper QueryWrapper or LambdaQueryWrapper
     * @return SQL condition string or {@code null} if empty
     */
    public static String extractConditions(AbstractWrapper<?, ?, ?> wrapper) {
        if (wrapper == null) {
            return null;
        }

        try {
            Object expression = getExpression(wrapper);
            if (expression == null) {
                return null;
            }

            // Get SQL segment from expression
            Field sqlSegmentField = expression.getClass().getDeclaredField("sqlSegment");
            sqlSegmentField.setAccessible(true);
            Object sqlSegment = sqlSegmentField.get(expression);

            return sqlSegment != null ? sqlSegment.toString() : null;
        } catch (Exception e) {
            log.warn("Failed to extract conditions from wrapper via reflection", e);
            return null;
        }
    }

    /**
     * Gets expression field from AbstractWrapper using reflection.
     *
     * @param wrapper QueryWrapper or LambdaQueryWrapper
     * @return Expression object or {@code null}
     */
    private static Object getExpression(AbstractWrapper<?, ?, ?> wrapper) throws Exception {
        if (expressionField == null) {
            synchronized (QueryWrapperInspector.class) {
                if (expressionField == null) {
                    expressionField = AbstractWrapper.class.getDeclaredField("expression");
                    expressionField.setAccessible(true);
                }
            }
        }

        return expressionField.get(wrapper);
    }
}
```

---

### 4. WrapperTypeDetector

**目标**: 识别 Wrapper 类型

**实现方案**:

```java
package com.footstone.sqlguard.compat.mp;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

/**
 * Detector for identifying wrapper types.
 *
 * <h2>Supported Types</h2>
 * <ul>
 *   <li>{@link QueryWrapper} - Standard query wrapper</li>
 *   <li>{@link LambdaQueryWrapper} - Lambda-based query wrapper</li>
 *   <li>{@link UpdateWrapper} - Standard update wrapper</li>
 *   <li>{@link LambdaUpdateWrapper} - Lambda-based update wrapper</li>
 * </ul>
 *
 * @since 1.1.0
 */
public final class WrapperTypeDetector {

    private WrapperTypeDetector() {
        // Utility class
    }

    /**
     * Checks if wrapper is QueryWrapper (standard or lambda).
     *
     * @param wrapper AbstractWrapper instance
     * @return {@code true} if QueryWrapper or LambdaQueryWrapper
     */
    public static boolean isQueryWrapper(AbstractWrapper<?, ?, ?> wrapper) {
        return wrapper instanceof QueryWrapper || wrapper instanceof LambdaQueryWrapper;
    }

    /**
     * Checks if wrapper is UpdateWrapper (standard or lambda).
     *
     * @param wrapper AbstractWrapper instance
     * @return {@code true} if UpdateWrapper or LambdaUpdateWrapper
     */
    public static boolean isUpdateWrapper(AbstractWrapper<?, ?, ?> wrapper) {
        return wrapper instanceof UpdateWrapper || wrapper instanceof LambdaUpdateWrapper;
    }

    /**
     * Checks if wrapper is lambda-based.
     *
     * @param wrapper AbstractWrapper instance
     * @return {@code true} if LambdaQueryWrapper or LambdaUpdateWrapper
     */
    public static boolean isLambdaWrapper(AbstractWrapper<?, ?, ?> wrapper) {
        return wrapper instanceof LambdaQueryWrapper || wrapper instanceof LambdaUpdateWrapper;
    }

    /**
     * Gets wrapper type name for logging.
     *
     * @param wrapper AbstractWrapper instance
     * @return Wrapper type name (e.g., "QueryWrapper", "LambdaQueryWrapper")
     */
    public static String getTypeName(AbstractWrapper<?, ?, ?> wrapper) {
        if (wrapper == null) {
            return "null";
        }
        return wrapper.getClass().getSimpleName();
    }
}
```

---

## 实现要求

### 1. MyBatisPlusVersionDetector 实现

**包路径**: `com.footstone.sqlguard.compat.mp`

**核心功能**:
- ✅ 检测 Marker Class: `com.baomidou.mybatisplus.core.toolkit.support.SFunction`
- ✅ 静态缓存检测结果
- ✅ 提供 `is35OrAbove()` 方法

### 2. IPageDetector 实现

**包路径**: `com.footstone.sqlguard.compat.mp`

**核心功能**:
- ✅ 检测直接 IPage 参数
- ✅ 检测 Map 包装的 IPage (keys: "page", "Page", "PAGE")
- ✅ 提取分页信息 (current, size)

### 3. QueryWrapperInspector 实现

**包路径**: `com.footstone.sqlguard.compat.mp`

**核心功能**:
- ✅ 反射访问 AbstractWrapper.expression 字段
- ✅ 提取 WHERE 条件字符串
- ✅ **空 Wrapper 检测** (关键安全功能)
- ✅ 支持 QueryWrapper 和 LambdaQueryWrapper

### 4. WrapperTypeDetector 实现

**包路径**: `com.footstone.sqlguard.compat.mp`

**核心功能**:
- ✅ 识别 QueryWrapper vs UpdateWrapper
- ✅ 识别 Lambda vs Standard wrapper
- ✅ 提供类型名称用于日志

---

## 验收标准

### 功能验收
- [ ] MyBatisPlusVersionDetector 类创建
- [ ] is35OrAbove() 方法正确检测版本
- [ ] IPageDetector 类创建
- [ ] detect() 方法检测 IPage 参数
- [ ] hasPagination() 方法正确判断
- [ ] extractPaginationInfo() 提取分页信息
- [ ] QueryWrapperInspector 类创建
- [ ] isEmpty() 方法检测空 Wrapper
- [ ] extractConditions() 提取条件
- [ ] WrapperTypeDetector 类创建
- [ ] isQueryWrapper/isUpdateWrapper/isLambdaWrapper 方法正确

### 测试验收

#### MyBatis-Plus版本检测TDD (5 tests)
- [ ] `testVersionDetector_MP340_detectsAs34()` - 检测 3.4.0 为 3.4.x
- [ ] `testVersionDetector_MP343_detectsAs34()` - 检测 3.4.3 为 3.4.x
- [ ] `testVersionDetector_MP353_detectsAs35()` - 检测 3.5.3 为 3.5.x
- [ ] `testVersionDetector_MP355_detectsAs35()` - 检测 3.5.5 为 3.5.x
- [ ] `testVersionDetector_caching_works()` - 缓存机制正常

#### IPageDetector实现TDD (5 tests)
- [ ] `testIPageDetector_fromParameter_detects()` - 直接参数检测
- [ ] `testIPageDetector_fromParamMap_detects()` - Map 包装检测
- [ ] `testIPageDetector_noPagination_returnsNull()` - 无分页返回 null
- [ ] `testIPageDetector_extractsCurrentAndSize()` - 提取分页信息
- [ ] `testIPageDetector_MP34_and_MP35_bothWork()` - 3.4.x/3.5.x 都正常

#### QueryWrapperInspector实现TDD (5 tests)
- [ ] `testQueryWrapperInspector_extractsConditions()` - 提取条件
- [ ] `testQueryWrapperInspector_lambdaWrapper_extracts()` - Lambda Wrapper 提取
- [ ] `testQueryWrapperInspector_emptyWrapper_detects()` - **空 Wrapper 检测**
- [ ] `testQueryWrapperInspector_complexConditions_extracts()` - 复杂条件提取
- [ ] `testWrapperTypeDetector_identifiesTypes()` - 类型识别

#### MyBatis-Plus兼容性集成测试TDD (5 tests)
- [ ] `testMP340_wrapperInspection_works()` - 3.4.0 Wrapper 检查
- [ ] `testMP343_iPageDetection_works()` - 3.4.3 IPage 检测
- [ ] `testMP353_compatibility()` - 3.5.3 兼容性
- [ ] `testMP355_compatibility()` - 3.5.5 兼容性
- [ ] `testAllVersions_behaviorConsistent()` - 所有版本行为一致

### 集成验收
- [ ] 与 SelectLimitInnerInterceptor 集成 (IPage 检测)
- [ ] 与 SqlGuardCheckInnerInterceptor 集成 (空 Wrapper 检测)
- [ ] 所有 MyBatis-Plus 版本 (3.4.0, 3.4.3, 3.5.3, 3.5.5) 测试通过
- [ ] 版本检测准确率 100%
- [ ] 空 Wrapper 检测准确率 100%

### 代码质量验收
- [ ] Javadoc 完整（接口、类、方法）
- [ ] 包含版本兼容性说明
- [ ] SLF4J 日志记录
- [ ] 异常处理正确
- [ ] 反射代码安全（双重检查锁定）

### 构建验收
- [ ] 编译成功
- [ ] 测试通过（20 tests, 0 failures）
- [ ] BUILD SUCCESS

---

## 输出文件

### 新增文件 (7 个)

**Source Files (4)**:
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mp/MyBatisPlusVersionDetector.java`
2. `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mp/IPageDetector.java`
3. `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mp/QueryWrapperInspector.java`
4. `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mp/WrapperTypeDetector.java`

**Test Files (3)**:
1. `sql-guard-core/src/test/java/com/footstone/sqlguard/compat/mp/MyBatisPlusVersionDetectionTest.java`
2. `sql-guard-core/src/test/java/com/footstone/sqlguard/compat/mp/IPageDetectorImplementationTest.java`
3. `sql-guard-core/src/test/java/com/footstone/sqlguard/compat/mp/QueryWrapperInspectorImplementationTest.java`
4. `sql-guard-core/src/test/java/com/footstone/sqlguard/compat/mp/MyBatisPlusCompatibilityIntegrationTest.java`

---

## 依赖与限制

### 输入依赖
- ✅ Task 13.1: SqlGuardInnerInterceptor 接口
- ✅ Task 13.2: SqlGuardInterceptor 主拦截器
- ✅ Task 13.5: SelectLimitInnerInterceptor (IPage 检测集成)
- ✅ Task 13.6: StatementContext
- ✅ MyBatis-Plus API (3.4.x 和 3.5.x)
- ✅ JUnit 5 (测试框架)

### 限制
- ⚠️ Marker Class 检测依赖类路径
- ⚠️ 反射访问 AbstractWrapper.expression private final 字段
- ⚠️ 需要 Maven profiles 测试多版本
- ⚠️ 空 Wrapper 检测依赖反射（性能开销）

---

## 注意事项

### 1. Maven Profile 配置

**问题**: 如何测试多个 MyBatis-Plus 版本？

**解决方案**: 使用 Maven Profiles

```xml
<!-- pom.xml -->
<profiles>
    <!-- MyBatis-Plus 3.4.0 -->
    <profile>
        <id>mp-3.4.0</id>
        <properties>
            <mybatis-plus.version>3.4.0</mybatis-plus.version>
        </properties>
    </profile>

    <!-- MyBatis-Plus 3.4.3 -->
    <profile>
        <id>mp-3.4.3</id>
        <properties>
            <mybatis-plus.version>3.4.3</mybatis-plus.version>
        </properties>
    </profile>

    <!-- MyBatis-Plus 3.5.3 -->
    <profile>
        <id>mp-3.5.3</id>
        <properties>
            <mybatis-plus.version>3.5.3</mybatis-plus.version>
        </properties>
    </profile>

    <!-- MyBatis-Plus 3.5.5 -->
    <profile>
        <id>mp-3.5.5</id>
        <properties>
            <mybatis-plus.version>3.5.5</mybatis-plus.version>
        </properties>
    </profile>
</profiles>
```

**运行测试**:
```bash
# Test with MyBatis-Plus 3.4.0
mvn test -Pmp-3.4.0

# Test with MyBatis-Plus 3.5.5
mvn test -Pmp-3.5.5
```

---

### 2. 空 Wrapper 检测的重要性

**为什么这是关键安全功能？**

```java
// 危险代码示例
QueryWrapper<User> wrapper = new QueryWrapper<>();
// 忘记添加 WHERE 条件！
List<User> users = userMapper.selectList(wrapper);
// ❌ 会查询所有用户（全表扫描）
```

**SqlGuard 如何防护**:
```java
// SelectLimitInnerInterceptor 检测
boolean isEmpty = QueryWrapperInspector.isEmpty(wrapper);
if (isEmpty) {
    log.warn("Empty QueryWrapper detected, adding LIMIT fallback");
    // 自动添加 LIMIT 1000
}
```

**NoWhereClauseChecker 集成**:
```java
// 在 Phase 2 的 NoWhereClauseChecker 中集成
if (QueryWrapperInspector.isEmpty(wrapper)) {
    return ViolationInfo.builder()
        .ruleId("NO_WHERE_CLAUSE")
        .message("QueryWrapper is empty (no conditions)")
        .build();
}
```

---

### 3. IPage 检测的两种场景

**场景 1: 直接参数**:
```java
// Mapper 接口
List<User> selectPage(IPage<User> page, QueryWrapper<User> wrapper);

// 参数对象
Object parameter = iPageInstance;  // ✅ instanceof IPage
```

**场景 2: Map 包装**:
```java
// Mapper 接口
List<User> selectPage(@Param("page") IPage<User> page,
                      @Param("ew") QueryWrapper<User> wrapper);

// 参数对象
Object parameter = Map.of("page", iPageInstance, "ew", wrapperInstance);
// ✅ parameter instanceof Map, map.get("page") instanceof IPage
```

**IPageDetector 处理**:
```java
public static IPage<?> detect(Object parameter) {
    if (parameter instanceof IPage) {
        return (IPage<?>) parameter;  // 场景 1
    }

    if (parameter instanceof Map) {
        Map<?, ?> map = (Map<?, ?>) parameter;
        for (String key : PAGE_KEYS) {  // "page", "Page", "PAGE"
            Object value = map.get(key);
            if (value instanceof IPage) {
                return (IPage<?>) value;  // 场景 2
            }
        }
    }

    return null;
}
```

---

### 4. 反射访问 AbstractWrapper.expression

**为什么需要反射？**

```java
// MyBatis-Plus 源码
public abstract class AbstractWrapper<T, R, Children> {
    private final MergeSegments expression;  // ❌ private final

    // 无公开 getter 方法获取 expression
}
```

**反射解决方案**:

```java
// QueryWrapperInspector.java
private static volatile Field expressionField;

private static Object getExpression(AbstractWrapper<?, ?, ?> wrapper) throws Exception {
    if (expressionField == null) {
        synchronized (QueryWrapperInspector.class) {  // 双重检查锁定
            if (expressionField == null) {
                expressionField = AbstractWrapper.class.getDeclaredField("expression");
                expressionField.setAccessible(true);  // 绕过 private 访问
            }
        }
    }

    return expressionField.get(wrapper);
}
```

**安全性考虑**:
- ✅ 缓存 Field 对象（避免重复反射）
- ✅ 双重检查锁定（线程安全）
- ✅ 异常处理（反射失败时降级）

---

### 5. 测试策略

**需要测试的场景**:

1. **版本检测测试**:
   - 3.4.0, 3.4.3 → 检测为 3.4.x ✅
   - 3.5.3, 3.5.5 → 检测为 3.5.x ✅
   - 缓存机制 → 不重复检测 ✅

2. **IPage 检测测试**:
   - 直接 IPage 参数 ✅
   - Map 包装 IPage (key="page") ✅
   - 无分页 → 返回 null ✅
   - 提取分页信息 (current, size) ✅

3. **QueryWrapper 检测测试**:
   - 提取 WHERE 条件 ✅
   - Lambda Wrapper 提取 ✅
   - **空 Wrapper 检测** ✅
   - 复杂条件提取 ✅

4. **Wrapper 类型检测测试**:
   - isQueryWrapper ✅
   - isUpdateWrapper ✅
   - isLambdaWrapper ✅
   - getTypeName ✅

5. **集成测试**:
   - SelectLimitInnerInterceptor + IPageDetector ✅
   - SqlGuardCheckInnerInterceptor + QueryWrapperInspector ✅
   - 所有版本 (3.4.0, 3.4.3, 3.5.3, 3.5.5) ✅

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_8_MyBatisPlus_Compatibility_Implementation.md
```

**Required Log Sections**:
- Summary: MyBatis-Plus version compatibility layer implementation outcome
- Details: MyBatisPlusVersionDetector, IPageDetector, QueryWrapperInspector, WrapperTypeDetector
- Output: List of created files (7 files), test results (20 tests)
- Issues: Any version detection, IPage detection, or wrapper inspection issues or "None"
- Important Findings: Empty wrapper detection accuracy, reflection performance
- Next Steps: Integration with SelectLimitInnerInterceptor and SqlGuardCheckInnerInterceptor

---

## 执行时间线

- **预计时间**: 3 工作日
  - Day 1 上午：实现 MyBatisPlusVersionDetector
  - Day 1 下午：实现 IPageDetector
  - Day 2 上午：实现 QueryWrapperInspector (重点：空 Wrapper 检测)
  - Day 2 下午：实现 WrapperTypeDetector + 版本检测测试 (5 tests)
  - Day 3 上午：编写 IPageDetector/QueryWrapperInspector 测试 (10 tests)
  - Day 3 下午：编写集成测试 (5 tests) + Maven profiles 配置

---

**Created**: 2025-12-22
**Phase**: 13 - InnerInterceptor Architecture
**Task ID**: 13.8
**Priority**: High (MyBatis-Plus 兼容性关键)
**Parallel**: Can run in parallel with Task 13.7 (after 13.1, 13.2, 13.6 complete)
