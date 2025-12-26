# MyBatis 占位符处理方案

**日期**: 2025-12-15  
**问题**: JSqlParser 无法解析包含 MyBatis 占位符（`#{}` 和 `${}`）的 SQL  
**影响**: 静态扫描时这些 SQL 被跳过，无法进行安全验证  

## 问题分析

### 当前状况
- JSqlParser 是标准 SQL 解析器，不理解 MyBatis 特有语法
- MyBatis 占位符：
  - `#{}` - 预编译参数（安全）
  - `${}` - 字符串替换（SQL 注入风险）
- 动态 SQL 片段（`<if>`, `<choose>` 等）生成不完整的 SQL

### 观察到的警告示例
```
WARN - Validation failed for AdminMapper.sumByExample: 
       Failed to parse SQL: select sum(${sumCol}) from admin

WARN - Validation failed for AdminMapper.insert: 
       Failed to parse SQL: insert into admin (...) values (#{userId,jdbcType=INTEGER}, ...)

WARN - Validation failed for AdminMapper.insertSelective: 
       Failed to parse SQL: insert into admin (不完整)
```

## 解决方案对比

### 方案 1: 预处理 MyBatis 占位符 ⭐ 推荐
**优点**:
- 实现简单，改动最小
- 保留 SQL 结构，可以进行大部分安全检查
- 性能开销小（正则替换）
- 可以区分 `#{}` 和 `${}`，对 `${}` 标记为高风险

**缺点**:
- 无法验证参数相关的问题
- 对于复杂的 MyBatis 表达式可能不完美

**实现复杂度**: ⭐ 低

**适用场景**: 
- 快速启用静态验证
- 大部分 SQL 使用标准 MyBatis 占位符
- 需要检测 `${}` SQL 注入风险

### 方案 2: 集成 MyBatis SQL 解析器
**优点**:
- 完全理解 MyBatis 语法
- 可以处理动态 SQL
- 可以生成所有可能的 SQL 变体

**缺点**:
- 实现复杂，需要深度集成 MyBatis
- 依赖 MyBatis 内部 API
- 性能开销较大
- 可能需要运行时上下文

**实现复杂度**: ⭐⭐⭐⭐ 高

**适用场景**:
- 需要完整的 MyBatis 支持
- 项目大量使用动态 SQL
- 有足够的开发资源

### 方案 3: 依赖运行时验证
**优点**:
- 静态扫描保持简单
- 运行时可以获取实际执行的 SQL
- 无需处理 MyBatis 特殊语法

**缺点**:
- 静态扫描覆盖率低
- 问题发现晚（运行时才发现）
- 需要完整的测试覆盖

**实现复杂度**: ⭐ 低（当前方案）

**适用场景**:
- Phase 4 运行时验证已实现
- 静态扫描作为辅助手段
- 测试覆盖率高的项目

### 方案 4: 混合方案（推荐长期方案）
**策略**:
1. 静态扫描：使用方案 1（预处理占位符）
2. 运行时验证：Phase 4 捕获所有实际执行的 SQL
3. 特殊处理：对 `${}` 标记为 CRITICAL 风险

**优点**:
- 双层防护
- 静态扫描快速反馈
- 运行时验证完整覆盖

**实现复杂度**: ⭐⭐ 中等

## 方案 1 详细设计：预处理 MyBatis 占位符

### 实现位置
`SqlScanner.validateSqlEntries()` 方法中，在调用 validator 之前

### 核心代码
```java
/**
 * Normalizes MyBatis SQL by replacing placeholders with standard SQL syntax.
 * 
 * @param sql the raw SQL with MyBatis placeholders
 * @return normalized SQL that can be parsed by JSqlParser
 */
private String normalizeMybatisSql(String sql) {
    if (sql == null || sql.trim().isEmpty()) {
        return sql;
    }
    
    // Replace #{...} with ? (safe parameterized query)
    String normalized = sql.replaceAll("#\\{[^}]+\\}", "?");
    
    // Replace ${...} with ? (but mark for SQL injection check)
    // Note: ${} should be flagged as potential SQL injection
    normalized = normalized.replaceAll("\\$\\{[^}]+\\}", "?");
    
    return normalized;
}

/**
 * Detects SQL injection risks from ${} usage.
 * 
 * @param sql the raw SQL
 * @return true if ${} placeholders are found
 */
private boolean hasSqlInjectionRisk(String sql) {
    return sql != null && sql.contains("${");
}
```

### 集成到验证流程
```java
private void validateSqlEntries(List<SqlEntry> entries) {
    if (entries == null || entries.isEmpty()) {
        return;
    }

    logger.info("Validating {} SQL entries with DefaultSqlSafetyValidator", entries.size());

    for (SqlEntry entry : entries) {
        try {
            String rawSql = entry.getRawSql();
            
            // Check for ${} SQL injection risk BEFORE normalization
            if (hasSqlInjectionRisk(rawSql)) {
                ViolationInfo injectionViolation = new ViolationInfo(
                    RiskLevel.CRITICAL,
                    "SQL injection risk - ${} string interpolation detected",
                    "Use #{} parameterized query instead of ${}"
                );
                entry.addViolation(injectionViolation);
            }
            
            // Normalize MyBatis SQL for parsing
            String normalizedSql = normalizeMybatisSql(rawSql);
            
            // Build SqlContext with normalized SQL
            SqlContext context = SqlContext.builder()
                .sql(normalizedSql)
                .type(entry.getSqlType())
                .mapperId(entry.getMapperId())
                .build();

            // Validate
            ValidationResult result = validator.validate(context);

            // Populate violations if any
            if (!result.isPassed()) {
                entry.addViolations(result.getViolations());
            }

            logger.debug("Validation completed for {}: {} violations", 
                entry.getMapperId(), entry.getViolations().size());
        } catch (Exception e) {
            // Fail-open: Log warning but continue processing
            logger.warn("Validation failed for {}: {}", entry.getMapperId(), e.getMessage());
        }
    }
}
```

### 测试用例
```java
@Test
void testNormalizeMybatisSql_SafeParameterizedQuery() {
    String sql = "SELECT * FROM user WHERE id = #{id}";
    String normalized = normalizeMybatisSql(sql);
    assertEquals("SELECT * FROM user WHERE id = ?", normalized);
}

@Test
void testNormalizeMybatisSql_SqlInjectionRisk() {
    String sql = "SELECT * FROM user ORDER BY ${orderBy}";
    String normalized = normalizeMybatisSql(sql);
    assertEquals("SELECT * FROM user ORDER BY ?", normalized);
    assertTrue(hasSqlInjectionRisk(sql));
}

@Test
void testNormalizeMybatisSql_ComplexParameters() {
    String sql = "INSERT INTO user (id, name) VALUES (#{id,jdbcType=INTEGER}, #{name,jdbcType=VARCHAR})";
    String normalized = normalizeMybatisSql(sql);
    assertEquals("INSERT INTO user (id, name) VALUES (?, ?)", normalized);
}

@Test
void testValidation_DetectsSqlInjectionFromDollarBrace() {
    SqlEntry entry = createEntry("SELECT * FROM user WHERE ${condition}");
    validateSqlEntries(Arrays.asList(entry));
    
    assertTrue(entry.hasViolations());
    assertEquals(RiskLevel.CRITICAL, entry.getHighestRiskLevel());
    assertTrue(entry.getViolations().stream()
        .anyMatch(v -> v.getMessage().contains("SQL injection")));
}
```

## 实施计划

### Phase 1: 基础实现（当前）✅
- [x] 实现 `normalizeMybatisSql()` 方法
- [x] 实现 `hasSqlInjectionRisk()` 方法
- [x] 集成到 `validateSqlEntries()` 方法
- [x] 添加单元测试

### Phase 2: 增强检测（可选）
- [ ] 检测 `${}` 在不同位置的风险级别
  - WHERE 子句中的 `${}` → CRITICAL
  - ORDER BY 中的 `${}` → HIGH
  - SELECT 列表中的 `${}` → MEDIUM
- [ ] 提供更具体的修复建议
- [ ] 支持白名单（某些安全的 `${}` 用法）

### Phase 3: 高级功能（未来）
- [ ] 处理嵌套占位符
- [ ] 支持 MyBatis 动态 SQL 标签
- [ ] 生成 SQL 变体进行验证

## 性能影响

### 预期开销
- 正则替换：~0.1ms per SQL
- SQL 注入检测：~0.05ms per SQL
- 总开销：<1% for typical projects

### 优化建议
1. 缓存正则表达式 Pattern 对象
2. 只对包含 `{` 的 SQL 进行处理
3. 使用 StringBuilder 进行字符串操作

## 监控指标

### 建议添加的日志
```java
logger.info("MyBatis placeholder normalization: {} SQL entries processed", entries.size());
logger.info("SQL injection risks detected: {} entries with ${} usage", injectionCount);
logger.debug("Normalized SQL: {} -> {}", rawSql, normalizedSql);
```

### 统计信息
- 处理的 SQL 总数
- 包含 `#{}` 的 SQL 数量
- 包含 `${}` 的 SQL 数量（高风险）
- 规范化成功率
- 验证成功率（规范化后）

## 已知限制

1. **复杂表达式**: 嵌套的 `{}` 可能无法正确处理
   ```sql
   SELECT ${col${index}} FROM table  -- 可能有问题
   ```

2. **字符串内的占位符**: 字符串字面量中的 `{}` 会被误处理
   ```sql
   SELECT '${not_a_placeholder}' FROM table  -- 会被误判
   ```

3. **动态 SQL 片段**: 不完整的 SQL 仍然无法验证
   ```sql
   insert into user  -- 缺少 VALUES 子句
   ```

## 参考资料

- MyBatis 官方文档: https://mybatis.org/mybatis-3/sqlmap-xml.html
- JSqlParser GitHub: https://github.com/JSQLParser/JSqlParser
- SQL 注入防护最佳实践: OWASP SQL Injection Prevention Cheat Sheet

## 更新日志

- 2025-12-15: 初始方案设计
- 2025-12-15: 实施 Phase 1 - 基础实现

















