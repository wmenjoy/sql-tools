# SQL 审核存储优化方案

**创建日期**: 2024-12-23
**状态**: 设计提案
**优先级**: 高

---

## 1. 问题分析

### 1.1 当前设计问题

当前系统存储**每次 SQL 执行**的完整记录：

```sql
sql_executions (
  event_id,           -- 每次执行唯一 ID
  sql_id,             -- SQL 哈希
  sql_text,           -- 完整 SQL
  execution_time_ms,  -- 执行时间
  created_at,         -- 时间戳
  ...
)
```

**问题**：
- 每个程序的 SQL 种类有限（几百到几千种）
- 但同一个 SQL 可能执行**百万次**
- 存储大量冗余数据
- 查询时关注的是"有哪些 SQL"，而非"执行了多少次"

### 1.2 数据量估算

| 场景 | 每天执行量 | 唯一 SQL 数 | 当前存储 | 优化后存储 |
|------|-----------|------------|----------|-----------|
| 小型应用 | 10,000 | ~50 | 10,000 条 | ~50 条 |
| 中型应用 | 100,000 | ~200 | 100,000 条 | ~200 条 |
| 大型应用 | 1,000,000 | ~500 | 1,000,000 条 | ~500 条 |

**优化效果：存储减少 2000x+**

---

## 2. 核心设计思路

### 2.1 从"执行记录"到"SQL 指纹"

**核心思想**：存储的是"SQL 模式"，而不是"每次执行"

- 相同的 SQL（去参数化后）只存储一条记录
- 累计执行统计（次数、平均时间、最大时间）
- 风险评估只做一次（不重复分析）

### 2.2 SQL 规范化

```sql
-- 原始 SQL
SELECT * FROM users WHERE id = 123 AND status = 'active'

-- 规范化后（参数化）
SELECT * FROM users WHERE id = ? AND status = ?

-- SQL ID = MD5(规范化后的 SQL)
sql_id = "a1b2c3d4e5f6..."
```

---

## 3. 存储模型设计

### 3.1 Tier 1: SQL 指纹表（核心）

**必须存储**，包含所有已发现的 SQL 模式及其风险评估。

```sql
CREATE TABLE sql_fingerprints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- SQL 标识
    sql_id VARCHAR(32) NOT NULL UNIQUE,  -- MD5(normalized_sql)
    sql_template TEXT NOT NULL,          -- 参数化后的 SQL 模板
    sql_type VARCHAR(20) NOT NULL,       -- SELECT/UPDATE/DELETE/INSERT
    
    -- 发现信息
    first_seen TIMESTAMP NOT NULL,       -- 首次发现时间
    last_seen TIMESTAMP NOT NULL,        -- 最后执行时间
    
    -- 执行统计（聚合更新）
    execution_count BIGINT DEFAULT 0,    -- 累计执行次数
    total_time_ms BIGINT DEFAULT 0,      -- 累计执行时间
    avg_time_ms DOUBLE DEFAULT 0,        -- 平均执行时间
    max_time_ms BIGINT DEFAULT 0,        -- 最大执行时间
    min_time_ms BIGINT DEFAULT 0,        -- 最小执行时间
    
    -- 来源追踪
    datasources JSON,                    -- 使用此 SQL 的数据源列表
    mappers JSON,                        -- 调用此 SQL 的 Mapper 列表
    applications JSON,                   -- 使用此 SQL 的应用列表
    
    -- 风险评估（只做一次）
    risk_score INT DEFAULT 0,            -- 风险分数 (0-100)
    severity VARCHAR(20),                -- CRITICAL/HIGH/MEDIUM/LOW/INFO
    checker_results JSON,                -- 检查器结果
    
    -- 元数据
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_sql_type (sql_type),
    INDEX idx_severity (severity),
    INDEX idx_risk_score (risk_score),
    INDEX idx_last_seen (last_seen),
    INDEX idx_execution_count (execution_count)
);
```

### 3.2 Tier 2: 异常事件表（可选）

**仅存储异常**，如慢查询、错误、风险触发等。

```sql
CREATE TABLE sql_anomalies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    
    -- 关联指纹
    sql_id VARCHAR(32) NOT NULL,         -- 关联 sql_fingerprints.sql_id
    
    -- 异常信息
    anomaly_type VARCHAR(50) NOT NULL,   -- SLOW_QUERY, ERROR, RISK_TRIGGERED, BLOCKED
    severity VARCHAR(20) NOT NULL,       -- 严重程度
    
    -- 执行上下文
    execution_time_ms BIGINT,
    rows_affected INT,
    error_message TEXT,
    
    -- 详细上下文（可选）
    context JSON,                        -- 包含参数、调用栈等
    
    -- 时间
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_sql_id (sql_id),
    INDEX idx_anomaly_type (anomaly_type),
    INDEX idx_created_at (created_at),
    
    FOREIGN KEY (sql_id) REFERENCES sql_fingerprints(sql_id)
);
```

### 3.3 Tier 3: 完整历史表（可选）

**仅在需要完整审计追溯的合规场景使用**。

```sql
CREATE TABLE sql_execution_history (
    -- 保持现有 sql_executions 表结构
    -- 仅在 strategy: full-history 模式下使用
);
```

---

## 4. 存储策略配置

### 4.1 策略选项

```yaml
audit:
  storage:
    # 策略选择
    strategy: fingerprint  # 默认，只存指纹
    # strategy: anomaly     # 指纹 + 异常
    # strategy: full        # 完整历史（合规场景）
    
    # 指纹配置
    fingerprint:
      # 是否规范化 SQL（去除参数）
      normalize-sql: true
      # 是否记录参数样本（用于调试）
      sample-params: true
      sample-count: 3  # 保留最近 3 个参数样本
    
    # 异常配置
    anomaly:
      # 慢查询阈值
      slow-query-threshold-ms: 1000
      # 是否记录所有错误
      record-errors: true
      # 是否记录风险触发
      record-risk-triggers: true
```

### 4.2 策略对比

| 策略 | 存储内容 | 存储量 | 适用场景 |
|------|---------|--------|----------|
| `fingerprint` | SQL 指纹 + 统计 | 极小 | 大多数场景 |
| `anomaly` | 指纹 + 异常事件 | 小 | 需要问题追溯 |
| `full` | 完整执行历史 | 大 | 审计合规 |

---

## 5. 更新机制

### 5.1 指纹更新策略

```java
public void recordExecution(AuditEvent event) {
    String sqlId = event.getSqlId();
    
    // 尝试更新现有指纹
    int updated = jdbcTemplate.update(
        "UPDATE sql_fingerprints SET " +
        "  last_seen = NOW(), " +
        "  execution_count = execution_count + 1, " +
        "  total_time_ms = total_time_ms + ?, " +
        "  avg_time_ms = (total_time_ms + ?) / (execution_count + 1), " +
        "  max_time_ms = GREATEST(max_time_ms, ?), " +
        "  min_time_ms = LEAST(min_time_ms, ?), " +
        "  datasources = JSON_ARRAY_APPEND(COALESCE(datasources, '[]'), '$', ?) " +
        "WHERE sql_id = ?",
        event.getExecutionTimeMs(),
        event.getExecutionTimeMs(),
        event.getExecutionTimeMs(),
        event.getExecutionTimeMs(),
        event.getDatasource(),
        sqlId
    );
    
    // 如果不存在，插入新指纹
    if (updated == 0) {
        insertNewFingerprint(event);
        analyzeRisk(event);  // 首次发现时分析风险
    }
}
```

### 5.2 批量更新优化

对于高吞吐量场景，使用内存缓冲 + 批量更新：

```java
@Component
public class FingerprintBuffer {
    private final Map<String, FingerprintStats> buffer = new ConcurrentHashMap<>();
    
    @Scheduled(fixedRate = 5000)  // 每 5 秒刷新
    public void flush() {
        Map<String, FingerprintStats> toFlush = new HashMap<>(buffer);
        buffer.clear();
        
        batchUpdate(toFlush);
    }
}
```

---

## 6. 查询优化

### 6.1 常见查询场景

```sql
-- 1. 查询高风险 SQL
SELECT * FROM sql_fingerprints 
WHERE risk_score >= 80 
ORDER BY risk_score DESC;

-- 2. 查询最近执行的 SQL
SELECT * FROM sql_fingerprints 
ORDER BY last_seen DESC 
LIMIT 100;

-- 3. 查询慢查询 SQL
SELECT * FROM sql_fingerprints 
WHERE avg_time_ms > 1000 
ORDER BY avg_time_ms DESC;

-- 4. 按服务过滤
SELECT * FROM sql_fingerprints 
WHERE JSON_CONTAINS(applications, '"order-service"');

-- 5. 查询特定 SQL 的异常历史
SELECT a.*, f.sql_template 
FROM sql_anomalies a 
JOIN sql_fingerprints f ON a.sql_id = f.sql_id 
WHERE a.sql_id = ? 
ORDER BY a.created_at DESC;
```

### 6.2 全文搜索（可选 Elasticsearch）

如果需要 SQL 全文搜索，可以将 `sql_fingerprints` 同步到 ES：

```yaml
audit:
  storage:
    strategy: fingerprint
    search:
      enabled: true
      engine: elasticsearch  # 仅用于搜索，不存储每次执行
```

---

## 7. 迁移方案

### 7.1 从完整历史迁移到指纹模式

```sql
-- Step 1: 生成指纹表
INSERT INTO sql_fingerprints (sql_id, sql_template, sql_type, first_seen, last_seen, execution_count, ...)
SELECT 
    sql_id,
    MIN(sql_text) as sql_template,
    sql_type,
    MIN(created_at) as first_seen,
    MAX(created_at) as last_seen,
    COUNT(*) as execution_count,
    AVG(execution_time_ms) as avg_time_ms,
    MAX(execution_time_ms) as max_time_ms
FROM sql_executions
GROUP BY sql_id, sql_type;

-- Step 2: 运行风险分析
-- (通过应用程序批量分析)

-- Step 3: 验证数据完整性

-- Step 4: 切换到指纹模式
-- 修改配置: audit.storage.strategy: fingerprint

-- Step 5: 清理旧数据（可选）
-- TRUNCATE TABLE sql_executions;  -- 或保留一段时间
```

---

## 8. 实现路线图

### Phase 1: 基础实现
- [ ] 实现 SQL 规范化器（去参数化）
- [ ] 创建 sql_fingerprints 表
- [ ] 实现 FingerprintStorageAdapter
- [ ] 配置策略切换

### Phase 2: 异常记录
- [ ] 创建 sql_anomalies 表
- [ ] 实现异常检测逻辑
- [ ] 实现 AnomalyStorageAdapter

### Phase 3: 优化
- [ ] 实现批量缓冲更新
- [ ] 添加 ES 搜索集成（可选）
- [ ] 实现迁移工具

### Phase 4: 监控
- [ ] 添加指纹统计指标
- [ ] 添加 Grafana 仪表板
- [ ] 添加告警规则

---

## 9. 总结

### 核心改进
1. **从"每次执行"到"SQL 指纹"** - 存储减少 2000x+
2. **风险分析只做一次** - 避免重复计算
3. **异常驱动记录** - 只记录有意义的事件
4. **灵活策略选择** - 适配不同场景

### 预期效果
- 存储成本降低 99%+
- 查询性能提升
- 数据更有意义（关注模式而非执行）
- 仍支持审计合规（full 模式）

---

**下一步**: 确认方案后，开始 Phase 1 实现。

