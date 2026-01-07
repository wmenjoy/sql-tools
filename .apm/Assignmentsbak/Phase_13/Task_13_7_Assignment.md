---
task_ref: "Task 13.7 - MyBatis Version Compatibility Layer"
agent_assignment: "Agent_Advanced_Interceptor"
memory_log_path: ".apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_7_MyBatis_Compatibility_Implementation.md"
execution_type: "single-step"
dependency_context: true
ad_hoc_delegation: false
phase: 13
task_id: 13.7
estimated_duration: 3 days
dependencies: [Task_13.1, Task_13.2, Task_13.6]
parallel_with: [Task_13.8]
priority: "High"
---

# Task 13.7 Assignment: MyBatis Version Compatibility Layer

## 任务目标

创建 MyBatis 版本兼容性层支持 3.4.x 和 3.5.x，实现 MyBatisVersionDetector 检测版本特定标记类，创建 SqlExtractor 接口定义版本无关的 SQL 提取抽象，实现 LegacySqlExtractor (3.4.x) 和 ModernSqlExtractor (3.5.x) 使用基于反射的 API 访问，创建 SqlExtractorFactory 根据检测版本选择实现。

---

## 背景说明

### 为什么需要 MyBatis 版本兼容性层？

**问题**: MyBatis 3.4.x 和 3.5.x API 存在差异:

```java
// MyBatis 3.4.x
public class MappedStatement {
    private SqlSource sqlSource;  // Older API
}

// MyBatis 3.5.x
public class MappedStatement {
    private SqlSource sqlSource;  // API improved
    // + New marker interfaces/classes
}
```

**MyBatis 3.5.0 引入的变化**:
1. **Marker Class**: `org.apache.ibatis.session.ProviderMethodResolver` (3.5.0+ 专有)
2. **API 改进**: 更清晰的参数处理
3. **BoundSql**: 处理 DynamicSqlSource vs StaticSqlSource 的差异

**SqlGuard 需要支持的版本**:
- MyBatis 3.4.6 (LTS, 许多老项目仍在使用)
- MyBatis 3.5.6, 3.5.13, 3.5.16 (现代版本)

---

### MyBatisVersionDetector 设计

**目标**: 运行时检测 MyBatis 版本

**实现方案**:

```java
package com.footstone.sqlguard.compat.mybatis;

/**
 * MyBatis version detector using marker class checking.
 *
 * <h2>Detection Strategy</h2>
 * <p>Checks for existence of {@code org.apache.ibatis.session.ProviderMethodResolver}
 * which exists only in MyBatis 3.5.0+.
 *
 * @since 1.1.0
 */
public final class MyBatisVersionDetector {

    /**
     * Marker class existing only in MyBatis 3.5.0+.
     */
    private static final String MARKER_CLASS_35 =
        "org.apache.ibatis.session.ProviderMethodResolver";

    /**
     * Cached detection result.
     */
    private static final boolean IS_35_OR_ABOVE = detectVersion();

    private MyBatisVersionDetector() {
        // Utility class
    }

    /**
     * Checks if MyBatis version is 3.5.0 or above.
     *
     * @return {@code true} if MyBatis 3.5.0+, {@code false} if MyBatis 3.4.x
     */
    public static boolean is35OrAbove() {
        return IS_35_OR_ABOVE;
    }

    /**
     * Detects MyBatis version using marker class checking.
     *
     * @return {@code true} if marker class exists (3.5.0+), {@code false} otherwise
     */
    private static boolean detectVersion() {
        try {
            Class.forName(MARKER_CLASS_35);
            return true;  // MyBatis 3.5.0+
        } catch (ClassNotFoundException e) {
            return false; // MyBatis 3.4.x
        }
    }
}
```

**关键特性**:
- ✅ 静态缓存检测结果（避免重复 Class.forName）
- ✅ 使用 Marker Class 检测（可靠且简单）
- ✅ 无需正则解析版本字符串

---

### SqlExtractor 接口设计

**目标**: 定义版本无关的 SQL 提取抽象

**接口定义**:

```java
package com.footstone.sqlguard.compat.mybatis;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * Interface for version-agnostic SQL extraction from MyBatis components.
 *
 * <h2>Purpose</h2>
 * <p>Abstracts differences in SQL extraction between MyBatis 3.4.x and 3.5.x:
 * <ul>
 *   <li>MyBatis 3.4.x: Uses {@code LegacySqlExtractor} with 3.4.x-specific API</li>
 *   <li>MyBatis 3.5.x: Uses {@code ModernSqlExtractor} with 3.5.x-specific API</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * SqlExtractor extractor = SqlExtractorFactory.create();
 * String sql = extractor.extractSql(mappedStatement, parameter, boundSql);
 * }</pre>
 *
 * @see LegacySqlExtractor
 * @see ModernSqlExtractor
 * @see SqlExtractorFactory
 * @since 1.1.0
 */
public interface SqlExtractor {

    /**
     * Extracts SQL string from MappedStatement and BoundSql.
     *
     * <p>Handles differences in parameter mapping and SQL generation between
     * MyBatis versions.
     *
     * @param ms        MappedStatement containing SQL metadata
     * @param parameter SQL parameter object
     * @param boundSql  BoundSql containing generated SQL
     * @return Extracted SQL string
     */
    String extractSql(MappedStatement ms, Object parameter, BoundSql boundSql);
}
```

---

### LegacySqlExtractor 实现 (MyBatis 3.4.x)

**文件**: `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mybatis/LegacySqlExtractor.java`

**实现要点**:

```java
package com.footstone.sqlguard.compat.mybatis;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SqlExtractor implementation for MyBatis 3.4.x using reflection-based API access.
 *
 * <h2>MyBatis 3.4.x Specifics</h2>
 * <p>Handles older API for SQL extraction:
 * <ul>
 *   <li>DynamicSqlSource vs StaticSqlSource handling</li>
 *   <li>Parameter mapping differences</li>
 *   <li>BoundSql generation via reflection</li>
 * </ul>
 *
 * @since 1.1.0
 */
public class LegacySqlExtractor implements SqlExtractor {

    private static final Logger log = LoggerFactory.getLogger(LegacySqlExtractor.class);

    /**
     * Extracts SQL from MyBatis 3.4.x components.
     *
     * @param ms        MappedStatement
     * @param parameter SQL parameter
     * @param boundSql  BoundSql
     * @return Extracted SQL string
     */
    @Override
    public String extractSql(MappedStatement ms, Object parameter, BoundSql boundSql) {
        // Extract SQL from BoundSql (compatible with 3.4.x)
        String sql = boundSql.getSql();

        log.trace("Extracted SQL from MyBatis 3.4.x: {}", sql);
        return sql;
    }
}
```

**关键差异**:
- MyBatis 3.4.x 的 BoundSql 生成逻辑稍有不同
- 需要处理 DynamicSqlSource vs StaticSqlSource

---

### ModernSqlExtractor 实现 (MyBatis 3.5.x)

**文件**: `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mybatis/ModernSqlExtractor.java`

**实现要点**:

```java
package com.footstone.sqlguard.compat.mybatis;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SqlExtractor implementation for MyBatis 3.5.x using improved API.
 *
 * <h2>MyBatis 3.5.x Improvements</h2>
 * <p>Leverages improved API for SQL extraction:
 * <ul>
 *   <li>Cleaner parameter handling</li>
 *   <li>Improved BoundSql generation</li>
 *   <li>Better type safety</li>
 * </ul>
 *
 * @since 1.1.0
 */
public class ModernSqlExtractor implements SqlExtractor {

    private static final Logger log = LoggerFactory.getLogger(ModernSqlExtractor.class);

    /**
     * Extracts SQL from MyBatis 3.5.x components.
     *
     * @param ms        MappedStatement
     * @param parameter SQL parameter
     * @param boundSql  BoundSql
     * @return Extracted SQL string
     */
    @Override
    public String extractSql(MappedStatement ms, Object parameter, BoundSql boundSql) {
        // Extract SQL from BoundSql (using 3.5.x improved API)
        String sql = boundSql.getSql();

        log.trace("Extracted SQL from MyBatis 3.5.x: {}", sql);
        return sql;
    }
}
```

**关键改进**:
- MyBatis 3.5.x 的参数处理更清晰
- BoundSql 生成逻辑更稳定

---

### SqlExtractorFactory 实现

**文件**: `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mybatis/SqlExtractorFactory.java`

**实现要点**:

```java
package com.footstone.sqlguard.compat.mybatis;

/**
 * Factory for creating version-appropriate SqlExtractor instances.
 *
 * <h2>Selection Strategy</h2>
 * <p>Uses {@link MyBatisVersionDetector} to determine MyBatis version:
 * <ul>
 *   <li>MyBatis 3.5.0+: Returns {@link ModernSqlExtractor}</li>
 *   <li>MyBatis 3.4.x: Returns {@link LegacySqlExtractor}</li>
 * </ul>
 *
 * <h2>Caching</h2>
 * <p>Caches SqlExtractor instance to avoid repeated creation.
 *
 * @since 1.1.0
 */
public final class SqlExtractorFactory {

    /**
     * Cached SqlExtractor instance.
     */
    private static final SqlExtractor INSTANCE = createExtractor();

    private SqlExtractorFactory() {
        // Utility class
    }

    /**
     * Returns cached SqlExtractor instance for current MyBatis version.
     *
     * @return SqlExtractor instance (ModernSqlExtractor for 3.5.x, LegacySqlExtractor for 3.4.x)
     */
    public static SqlExtractor create() {
        return INSTANCE;
    }

    /**
     * Creates SqlExtractor based on detected MyBatis version.
     *
     * @return SqlExtractor implementation
     */
    private static SqlExtractor createExtractor() {
        if (MyBatisVersionDetector.is35OrAbove()) {
            return new ModernSqlExtractor();
        } else {
            return new LegacySqlExtractor();
        }
    }
}
```

**关键特性**:
- ✅ 静态缓存 SqlExtractor 实例
- ✅ 根据 MyBatisVersionDetector 选择实现
- ✅ 线程安全（静态初始化）

---

## 实现要求

### 1. MyBatisVersionDetector 实现

**包路径**: `com.footstone.sqlguard.compat.mybatis`

**核心功能**:
- ✅ 检测 Marker Class: `org.apache.ibatis.session.ProviderMethodResolver`
- ✅ 静态缓存检测结果
- ✅ 提供 `is35OrAbove()` 方法

### 2. SqlExtractor 接口定义

**包路径**: `com.footstone.sqlguard.compat.mybatis`

**方法签名**:
```java
String extractSql(MappedStatement ms, Object parameter, BoundSql boundSql);
```

### 3. LegacySqlExtractor 实现

**包路径**: `com.footstone.sqlguard.compat.mybatis`

**核心功能**:
- ✅ 兼容 MyBatis 3.4.6
- ✅ 处理 DynamicSqlSource vs StaticSqlSource
- ✅ 使用反射访问 3.4.x API

### 4. ModernSqlExtractor 实现

**包路径**: `com.footstone.sqlguard.compat.mybatis`

**核心功能**:
- ✅ 兼容 MyBatis 3.5.6, 3.5.13, 3.5.16
- ✅ 使用 3.5.x 改进的 API
- ✅ 更清晰的参数处理

### 5. SqlExtractorFactory 实现

**包路径**: `com.footstone.sqlguard.compat.mybatis`

**核心功能**:
- ✅ 根据 MyBatisVersionDetector 选择实现
- ✅ 静态缓存 SqlExtractor 实例
- ✅ 提供 `create()` 工厂方法

---

## 验收标准

### 功能验收
- [ ] MyBatisVersionDetector 类创建
- [ ] is35OrAbove() 方法正确检测版本
- [ ] SqlExtractor 接口定义
- [ ] LegacySqlExtractor 实现 (3.4.x)
- [ ] ModernSqlExtractor 实现 (3.5.x)
- [ ] SqlExtractorFactory 根据版本选择实现
- [ ] 静态缓存机制正常工作

### 测试验收

#### MyBatis版本检测TDD (5 tests)
- [ ] `testVersionDetector_MyBatis346_detectsAs34()` - 检测 3.4.6 为 3.4.x
- [ ] `testVersionDetector_MyBatis356_detectsAs35()` - 检测 3.5.6 为 3.5.x
- [ ] `testVersionDetector_MyBatis3513_detectsAs35()` - 检测 3.5.13 为 3.5.x
- [ ] `testVersionDetector_MyBatis3516_detectsAs35()` - 检测 3.5.16 为 3.5.x
- [ ] `testVersionDetector_caching_works()` - 缓存机制正常

#### SqlExtractor实现TDD (5 tests)
- [ ] `testLegacySqlExtractor_MyBatis34_extractsSql()` - 3.4.x SQL 提取
- [ ] `testModernSqlExtractor_MyBatis35_extractsSql()` - 3.5.x SQL 提取
- [ ] `testSqlExtractorFactory_selectsCorrectImplementation()` - Factory 选择正确
- [ ] `testSqlExtractor_handlesParameters()` - 参数处理正确
- [ ] `testSqlExtractor_handlesDynamicSql()` - 动态 SQL 处理正确

#### MyBatis兼容性集成测试TDD (5 tests)
- [ ] `testMyBatis346_interceptorWorks()` - 3.4.6 集成测试
- [ ] `testMyBatis356_interceptorWorks()` - 3.5.6 集成测试
- [ ] `testMyBatis3513_interceptorWorks()` - 3.5.13 集成测试
- [ ] `testMyBatis3516_interceptorWorks()` - 3.5.16 集成测试
- [ ] `testAllVersions_behaviorConsistent()` - 所有版本行为一致

### 集成验收
- [ ] 与 SqlGuardInterceptor 集成正常
- [ ] 所有 MyBatis 版本 (3.4.6, 3.5.6, 3.5.13, 3.5.16) 测试通过
- [ ] 版本检测准确率 100%
- [ ] SQL 提取行为一致

### 代码质量验收
- [ ] Javadoc 完整（接口、类、方法）
- [ ] 包含版本兼容性说明
- [ ] SLF4J 日志记录
- [ ] 异常处理正确
- [ ] 反射代码安全

### 构建验收
- [ ] 编译成功
- [ ] 测试通过（15 tests, 0 failures）
- [ ] BUILD SUCCESS

---

## 输出文件

### 新增文件 (8 个)

**Source Files (5)**:
1. `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mybatis/MyBatisVersionDetector.java`
2. `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mybatis/SqlExtractor.java`
3. `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mybatis/LegacySqlExtractor.java`
4. `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mybatis/ModernSqlExtractor.java`
5. `sql-guard-core/src/main/java/com/footstone/sqlguard/compat/mybatis/SqlExtractorFactory.java`

**Test Files (3)**:
1. `sql-guard-core/src/test/java/com/footstone/sqlguard/compat/mybatis/MyBatisVersionDetectionTest.java`
2. `sql-guard-core/src/test/java/com/footstone/sqlguard/compat/mybatis/SqlExtractorImplementationTest.java`
3. `sql-guard-core/src/test/java/com/footstone/sqlguard/compat/mybatis/MyBatisCompatibilityIntegrationTest.java`

---

## 依赖与限制

### 输入依赖
- ✅ Task 13.1: SqlGuardInnerInterceptor 接口
- ✅ Task 13.2: SqlGuardInterceptor 主拦截器
- ✅ Task 13.6: StatementContext
- ✅ MyBatis API (3.4.x 和 3.5.x)
- ✅ JUnit 5 (测试框架)

### 限制
- ⚠️ Marker Class 检测依赖类路径
- ⚠️ 需要 Maven profiles 测试多版本
- ⚠️ 反射访问可能在某些环境失败

---

## 注意事项

### 1. Maven Profile 配置

**问题**: 如何测试多个 MyBatis 版本？

**解决方案**: 使用 Maven Profiles

```xml
<!-- pom.xml -->
<profiles>
    <!-- MyBatis 3.4.6 -->
    <profile>
        <id>mybatis-3.4.6</id>
        <properties>
            <mybatis.version>3.4.6</mybatis.version>
        </properties>
    </profile>

    <!-- MyBatis 3.5.6 -->
    <profile>
        <id>mybatis-3.5.6</id>
        <properties>
            <mybatis.version>3.5.6</mybatis.version>
        </properties>
    </profile>

    <!-- MyBatis 3.5.13 -->
    <profile>
        <id>mybatis-3.5.13</id>
        <properties>
            <mybatis.version>3.5.13</mybatis.version>
        </properties>
    </profile>

    <!-- MyBatis 3.5.16 -->
    <profile>
        <id>mybatis-3.5.16</id>
        <properties>
            <mybatis.version>3.5.16</mybatis.version>
        </properties>
    </profile>
</profiles>
```

**运行测试**:
```bash
# Test with MyBatis 3.4.6
mvn test -Pmybatis-3.4.6

# Test with MyBatis 3.5.16
mvn test -Pmybatis-3.5.16
```

---

### 2. Marker Class 检测可靠性

**为什么选择 ProviderMethodResolver？**

```java
// MyBatis 3.5.0 引入的新接口
package org.apache.ibatis.session;

public interface ProviderMethodResolver {
    // 3.5.0+ exclusive marker
}
```

**优点**:
- ✅ 简单可靠（仅需 Class.forName）
- ✅ 不依赖版本字符串解析
- ✅ 线程安全（静态缓存）

**缺点**:
- ⚠️ 如果类路径混乱可能误检测
- ⚠️ 需要确保 MyBatis 依赖正确

---

### 3. SQL 提取差异

**MyBatis 3.4.x vs 3.5.x 差异**:

| 方面 | MyBatis 3.4.x | MyBatis 3.5.x |
|------|---------------|---------------|
| **BoundSql 生成** | 旧 API | 改进的 API |
| **参数处理** | 基础 | 更清晰 |
| **DynamicSqlSource** | 需要特殊处理 | 更统一 |

**实现策略**:
- LegacySqlExtractor: 使用反射访问 3.4.x API
- ModernSqlExtractor: 使用 3.5.x 改进的 API

---

### 4. 测试策略

**需要测试的场景**:

1. **版本检测测试**:
   - 3.4.6 → 检测为 3.4.x ✅
   - 3.5.6, 3.5.13, 3.5.16 → 检测为 3.5.x ✅
   - 缓存机制 → 不重复检测 ✅

2. **SQL 提取测试**:
   - LegacySqlExtractor 提取 3.4.x SQL ✅
   - ModernSqlExtractor 提取 3.5.x SQL ✅
   - 参数处理 ✅
   - DynamicSql 处理 ✅

3. **集成测试**:
   - SqlGuardInterceptor + 3.4.6 ✅
   - SqlGuardInterceptor + 3.5.6/3.5.13/3.5.16 ✅
   - 行为一致性验证 ✅

---

## Memory Logging

Upon completion, you **MUST** log work in:
```
.apm/Memory/Phase_13_InnerInterceptor_Architecture/Task_13_7_MyBatis_Compatibility_Implementation.md
```

**Required Log Sections**:
- Summary: MyBatis version compatibility layer implementation outcome
- Details: MyBatisVersionDetector, SqlExtractor interface, LegacySqlExtractor, ModernSqlExtractor, SqlExtractorFactory
- Output: List of created files (8 files), test results (15 tests)
- Issues: Any version detection or SQL extraction issues or "None"
- Next Steps: Integration with SqlGuardInterceptor, Maven profile testing

---

## 执行时间线

- **预计时间**: 3 工作日
  - Day 1 上午：实现 MyBatisVersionDetector + SqlExtractor 接口
  - Day 1 下午：实现 LegacySqlExtractor (3.4.x)
  - Day 2 上午：实现 ModernSqlExtractor (3.5.x) + SqlExtractorFactory
  - Day 2 下午：编写版本检测测试 (5 tests)
  - Day 3 上午：编写 SqlExtractor 实现测试 (5 tests)
  - Day 3 下午：编写集成测试 (5 tests) + Maven profiles 配置

---

**Created**: 2025-12-22
**Phase**: 13 - InnerInterceptor Architecture
**Task ID**: 13.7
**Priority**: High (兼容性支持关键)
**Parallel**: Can run in parallel with Task 13.8 (after 13.1, 13.2, 13.6 complete)
