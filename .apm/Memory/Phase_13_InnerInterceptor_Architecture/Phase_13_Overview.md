# Phase 13: InnerInterceptor Architecture

## 阶段概述

**阶段目标**: 实现 MyBatis-Plus 风格的 InnerInterceptor 架构，提供优先级控制机制、ThreadLocal Statement 共享、自动 LIMIT 降级功能，支持 MyBatis 3.4.x/3.5.x 和 MyBatis-Plus 3.4.x/3.5.x 多版本兼容。

**执行时间**: 待定（预计 8-10 天）

**当前状态**: 🔄 **进行中 (0/6+ 任务)**

---

## 阶段依赖

### 输入依赖
- ✅ Phase 11: 模块结构（JDBC 模块拆分，虽然不是严格必需）
- ✅ Phase 12: StatementVisitor 抽象，SqlContext 包含 statement 字段

### 输出产物
- SqlGuardInnerInterceptor 接口
- SqlGuardInterceptor 主拦截器
- SqlGuardCheckInnerInterceptor（桥接 RuleChecker）
- SelectLimitInnerInterceptor（自动 LIMIT 降级）
- 优先级控制机制
- ThreadLocal Statement 共享

---

## 主要任务

| 任务 | 任务名称 | 负责 Agent | 状态 |
|------|---------|-----------|------|
| **13.1** | SqlGuardInnerInterceptor 接口设计 | Agent_Advanced_Interceptor | 🔄 进行中 |
| **13.2** | SqlGuardInterceptor 主拦截器实现 | Agent_Advanced_Interceptor | ⏳ 待开始 |
| **13.3** | SqlGuardCheckInnerInterceptor 实现 | Agent_Advanced_Interceptor | ⏳ 待开始 |
| **13.4** | SelectLimitInnerInterceptor 实现 | Agent_Advanced_Interceptor | ⏳ 待开始 |
| **13.5** | ThreadLocal Statement 共享 | Agent_Advanced_Interceptor | ⏳ 待开始 |
| **13.6** | 多版本兼容测试 | Agent_Testing_Validation | ⏳ 待开始 |

---

## 架构设计

### InnerInterceptor 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                    SqlGuardInterceptor                       │
│                   (MyBatis Interceptor)                      │
│                                                              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  1. Parse SQL once (JSqlParserFacade)                │  │
│  │  2. Cache Statement in ThreadLocal                   │  │
│  │  3. Sort InnerInterceptors by priority               │  │
│  │  4. Invoke willDoXxx() chain (stop if any false)     │  │
│  │  5. Invoke beforeXxx() chain (modify BoundSql)       │  │
│  │  6. Cleanup ThreadLocal in finally block             │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────┐               │
│  │     InnerInterceptor Chain (by priority) │               │
│  ├──────────────────────────────────────────┤               │
│  │ Priority 10: SqlGuardCheckInnerInterceptor│ (checks)     │
│  │              → Bridge to RuleChecker      │               │
│  ├──────────────────────────────────────────┤               │
│  │ Priority 100: SelectLimitInnerInterceptor │ (fallback)   │
│  │               → Auto LIMIT 1000           │               │
│  ├──────────────────────────────────────────┤               │
│  │ Priority 200+: Custom Interceptors        │ (rewrite)    │
│  └──────────────────────────────────────────┘               │
└─────────────────────────────────────────────────────────────┘
```

### 优先级机制

| 优先级范围 | 用途 | 示例 |
|-----------|------|------|
| **1-99** | Check interceptors（检查拦截器） | SqlGuardCheckInnerInterceptor (10) |
| **100-199** | Fallback interceptors（降级拦截器） | SelectLimitInnerInterceptor (100) |
| **200+** | Rewrite interceptors（重写拦截器） | 自定义 SQL 重写 |

**执行顺序**: 数字越小优先级越高（先执行）

---

## 核心特性

### 1. 生命周期方法

```java
public interface SqlGuardInnerInterceptor {
    // Query lifecycle
    boolean willDoQuery(...);  // Pre-check (return false to skip)
    void beforeQuery(...);     // SQL modification

    // Update lifecycle
    boolean willDoUpdate(...); // Pre-check (return false to skip)
    void beforeUpdate(...);    // SQL modification

    // Priority
    int getPriority();         // Execution order
}
```

### 2. ThreadLocal Statement 共享

```java
public class StatementContext {
    private static final ThreadLocal<Statement> STATEMENT_CACHE = new ThreadLocal<>();

    public static void cache(String sql, Statement statement) {
        STATEMENT_CACHE.set(statement);
    }

    public static Statement get(String sql) {
        return STATEMENT_CACHE.get();
    }

    public static void clear() {
        STATEMENT_CACHE.remove();
    }
}
```

**优势**: 避免 InnerInterceptor 链中重复解析 SQL

### 3. 自动 LIMIT 降级

```java
public class SelectLimitInnerInterceptor implements SqlGuardInnerInterceptor {
    @Override
    public void beforeQuery(...) {
        // If SELECT without LIMIT, add LIMIT 1000
        if (sql.contains("SELECT") && !sql.contains("LIMIT")) {
            modifiedSql = sql + " LIMIT 1000";
        }
    }

    @Override
    public int getPriority() {
        return 100;  // Fallback interceptor
    }
}
```

---

## 预期收益

### 性能优化
- ✅ SQL 解析一次（SqlGuardInterceptor 解析，所有 InnerInterceptor 复用）
- ✅ ThreadLocal 缓存避免重复解析

### 架构灵活性
- ✅ 优先级控制（Check → Fallback → Rewrite）
- ✅ 可插拔设计（新增 InnerInterceptor 无需修改主拦截器）
- ✅ 短路机制（willDoXxx() 返回 false 跳过后续）

### 安全增强
- ✅ 自动 LIMIT 降级（防止大查询）
- ✅ SQL 安全检查（SqlGuardCheckInnerInterceptor）

---

## 当前进度

```
Phase 13 进度: 0% (0/6 任务)

🔄 Task 13.1 - SqlGuardInnerInterceptor 接口设计 (进行中)
⏳ Task 13.2 - SqlGuardInterceptor 主拦截器实现
⏳ Task 13.3 - SqlGuardCheckInnerInterceptor 实现
⏳ Task 13.4 - SelectLimitInnerInterceptor 实现
⏳ Task 13.5 - ThreadLocal Statement 共享
⏳ Task 13.6 - 多版本兼容测试
```

---

**Created**: 2025-12-22
**Phase**: 13 - InnerInterceptor Architecture
**Status**: 🔄 In Progress
