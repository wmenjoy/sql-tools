# MyBatis 占位符处理 - 快速参考

## 功能概述

✅ **自动处理 MyBatis 占位符，无需配置**

- `#{}` → 安全的参数化查询 → 正常验证
- `${}` → SQL 注入风险 → 标记为 CRITICAL

## 使用示例

### 1. 安全的 #{} 用法 ✅

```xml
<!-- 自动规范化为: SELECT * FROM user WHERE id = ? -->
<select id="selectById">
  SELECT * FROM user WHERE id = #{id}
</select>
```

**结果**: ✅ 无违规，正常验证

### 2. 危险的 ${} 用法 ⚠️

```xml
<!-- 检测到 SQL 注入风险 -->
<select id="selectByCondition">
  SELECT * FROM user WHERE ${condition}
</select>
```

**结果**: 🔴 CRITICAL - SQL injection risk

### 3. 复杂参数 ✅

```xml
<!-- 支持 jdbcType 等复杂参数 -->
<insert id="insertUser">
  INSERT INTO user (id, name) 
  VALUES (#{id,jdbcType=INTEGER}, #{name,jdbcType=VARCHAR})
</insert>
```

**结果**: ✅ 无违规

### 4. 混合使用 ⚠️

```xml
<!-- 同时包含安全和危险的占位符 -->
<select id="selectMixed">
  SELECT * FROM user 
  WHERE name = #{name}     <!-- 安全 -->
  ORDER BY ${orderColumn}  <!-- 危险 -->
</select>
```

**结果**: 🔴 CRITICAL - SQL injection risk (${orderColumn})

## 扫描输出示例

```bash
$ java -jar sql-scanner-cli.jar -p /path/to/project

Total SQL: 8 | Violations: 6 (CRITICAL: 6)

[CRITICAL] 6 violations

  [UserMapper.xml:19] selectByCondition
  SQL: SELECT * FROM user WHERE ${condition}
  Message: SQL injection risk - ${} string interpolation detected
  Suggestion: Use #{} parameterized query instead of ${}
```

## 常见场景

### ✅ 推荐做法

| 场景 | 推荐 | 原因 |
|------|------|------|
| WHERE 条件 | `WHERE id = #{id}` | 防止 SQL 注入 |
| INSERT 值 | `VALUES (#{name})` | 参数化查询 |
| UPDATE 值 | `SET name = #{name}` | 安全 |
| IN 子句 | `IN (#{ids})` + foreach | 安全 |

### ⚠️ 危险做法

| 场景 | 危险用法 | 风险 |
|------|----------|------|
| WHERE 条件 | `WHERE ${condition}` | SQL 注入 |
| ORDER BY | `ORDER BY ${column}` | SQL 注入 |
| 表名 | `FROM ${tableName}` | SQL 注入 |
| 列名 | `SELECT ${columns}` | SQL 注入 |

## 修复建议

### 场景 1: 动态 WHERE 条件

❌ **错误**:
```xml
<select id="search">
  SELECT * FROM user WHERE ${condition}
</select>
```

✅ **正确**:
```xml
<select id="search">
  SELECT * FROM user 
  <where>
    <if test="name != null">
      AND name = #{name}
    </if>
    <if test="age != null">
      AND age = #{age}
    </if>
  </where>
</select>
```

### 场景 2: 动态 ORDER BY

❌ **错误**:
```xml
<select id="list">
  SELECT * FROM user ORDER BY ${orderBy}
</select>
```

✅ **正确**:
```xml
<select id="list">
  SELECT * FROM user 
  <choose>
    <when test="orderBy == 'name'">ORDER BY name</when>
    <when test="orderBy == 'age'">ORDER BY age</when>
    <otherwise>ORDER BY id</otherwise>
  </choose>
</select>
```

### 场景 3: 动态列名

❌ **错误**:
```xml
<select id="selectColumns">
  SELECT ${columns} FROM user
</select>
```

✅ **正确**:
```xml
<select id="selectColumns">
  SELECT 
  <choose>
    <when test="includeEmail">id, name, email</when>
    <otherwise>id, name</otherwise>
  </choose>
  FROM user
</select>
```

## 性能影响

- **处理时间**: < 0.1ms per SQL
- **总开销**: < 1% for typical projects
- **内存影响**: 忽略不计

## 配置

**无需配置** - 功能自动启用

如果需要调整日志级别:

```yaml
# logback.xml
<logger name="com.footstone.sqlguard.scanner.SqlScanner" level="DEBUG"/>
```

## 日志输出

```
INFO  - Validating 8 SQL entries with DefaultSqlSafetyValidator
DEBUG - SQL injection risk detected in UserMapper.selectByCondition: contains ${{}}
WARN  - SQL injection risks detected: 5 entries with ${{}} usage
```

## 常见问题

### Q: 为什么我的 ${} 被标记为 CRITICAL？

A: `${}` 执行字符串替换，不使用参数化查询，存在 SQL 注入风险。应该使用 `#{}` 或 MyBatis 动态 SQL 标签。

### Q: 某些 ${} 用法是安全的，如何处理？

A: 目前所有 `${}` 都会被标记。未来版本将支持白名单配置。如果确认安全，可以在代码审查时忽略该警告。

### Q: 动态 SQL 标签会被处理吗？

A: 当前版本会生成所有可能的 SQL 变体。如果某个变体不完整（如缺少 WHERE 子句），会记录警告但不会中断扫描。

### Q: 性能影响如何？

A: 非常小（< 1%）。正则替换非常快速，对大型项目也适用。

## 技术细节

### 规范化规则

```java
// 输入
"SELECT * FROM user WHERE id = #{id} ORDER BY ${orderBy}"

// 输出
"SELECT * FROM user WHERE id = ? ORDER BY ?"

// 同时标记
ViolationInfo(CRITICAL, "SQL injection risk - ${} detected", ...)
```

### 检测逻辑

```java
// 简单高效的检测
boolean hasSqlInjectionRisk(String sql) {
    return sql != null && sql.contains("${");
}
```

## 相关文档

- 详细设计: `MyBatis_Placeholder_Handling_Solutions.md`
- 实施总结: `MyBatis_Placeholder_Implementation_Summary.md`
- MyBatis 官方文档: https://mybatis.org/mybatis-3/sqlmap-xml.html

## 版本信息

- **首次引入**: v1.0.0-SNAPSHOT
- **实施日期**: 2025-12-15
- **状态**: ✅ 生产就绪

---

**快速记忆**:
- `#{}` = 安全 ✅
- `${}` = 危险 ⚠️
- 无需配置，自动工作 🚀





