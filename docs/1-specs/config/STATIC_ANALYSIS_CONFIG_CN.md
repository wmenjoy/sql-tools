---
type: Technical Specification
component: Configuration
version: 1.0
created: 2024-12-01
updated: 2025-01-16
status: Active
maintainer: SQL Safety Guard Team
language: zh-CN
---

# 静态分析配置说明

## 概述

SQL Scanner 提供了灵活的配置选项，允许您根据项目需求自定义检查规则。本文档详细说明了静态分析的配置选项。

## 配置文件位置

配置文件使用 YAML 格式，默认位置：
- `config.yml`（当前目录）
- 或通过 `-c` / `--config` 参数指定

示例：
```bash
java -jar sql-scanner-cli.jar -c config-example.yml -p /path/to/project
```

## 静态分析检查开关

### 1. SELECT * 检查

**配置项**: `staticAnalysis.checkSelectStar`

**说明**: 检测 `SELECT *` 的使用

**默认状态**: 关闭（`enabled: false`）

**风险级别**: LOW

**原因**: 
- `SELECT *` 在某些场景下是合理的（如小表、内部工具）
- 过度报告会产生噪音

**何时开启**:
- 大型项目，性能敏感
- 严格的代码规范要求

**配置示例**:
```yaml
rules:
  staticAnalysis:
    checkSelectStar:
      enabled: true  # 开启检查
      riskLevel: LOW
```

---

### 2. 敏感表访问检查

**配置项**: `staticAnalysis.checkSensitiveTables`

**说明**: 检测对敏感表的访问（如 user, password, admin 等）

**默认状态**: 关闭（`enabled: false`）

**风险级别**: MEDIUM

**原因**: 
- 敏感表定义因项目而异
- 需要根据实际业务定制

**何时开启**:
- 有明确的敏感表定义
- 需要审计敏感数据访问

**配置示例**:
```yaml
rules:
  staticAnalysis:
    checkSensitiveTables:
      enabled: true
      riskLevel: MEDIUM
      tables:
        - user
        - password
        - admin
        - role
        - permission
        - auth
        - token
        # 添加您的敏感表
        - customer_data
        - payment_info
```

---

### 3. 动态 WHERE 条件检查

**配置项**: `staticAnalysis.checkDynamicWhereClause`

**说明**: 检测动态 WHERE 条件可能为空的情况

**默认状态**: 关闭（`enabled: false`）

**风险级别**: HIGH

**典型场景**:
```xml
<delete id="deleteByExample">
    delete from user
    <if test="_parameter != null">
      <include refid="Example_Where_Clause"/>
    </if>
</delete>
```

**为什么默认关闭**:
- 这是 **MyBatis Generator 的标准安全模式**
- `_parameter != null` 确保 Example 对象存在
- `Example_Where_Clause` 根据 Example 条件动态生成 WHERE
- 如果 Example 无条件，不会生成 WHERE（业务层责任）
- 这是清晰的责任分离设计

**何时开启**:
- 项目有自定义的动态 WHERE 逻辑
- 不使用 MyBatis Generator
- 需要更严格的检查

**配置示例**:
```yaml
rules:
  staticAnalysis:
    checkDynamicWhereClause:
      enabled: true  # 谨慎开启
      riskLevel: HIGH
```

**智能识别**:

工具会自动识别以下安全模式，即使开启检查也不会报告：
- `<if test="_parameter != null"><include refid="Example_Where_Clause"/></if>`
- `<if test="_parameter != null"><include refid="Update_By_Example_Where_Clause"/></if>`
- 任何包含 `Where_Clause` 的 `refid`

---

### 4. ORDER BY 注入检查

**配置项**: `staticAnalysis.checkOrderByInjection`

**说明**: 检测 ORDER BY 子句中的 `${}` 占位符

**默认状态**: 关闭（`enabled: false`）

**风险级别**: CRITICAL

**典型场景**:
```xml
<select id="selectByExample">
    select * from user
    <if test="orderByClause != null">
      order by ${orderByClause}
    </if>
</select>
```

**为什么默认关闭**:
- 这是 **MyBatis Generator 的常见模式**
- 通常有业务层白名单控制
- 在很多项目中被广泛使用且安全

**何时开启**:
- 新项目，严格的安全要求
- 需要强制使用更安全的方案
- 审计现有代码的安全性

**配置示例**:
```yaml
rules:
  staticAnalysis:
    checkOrderByInjection:
      enabled: true  # 谨慎开启
      riskLevel: CRITICAL
```

**安全建议**:

如果开启检查，建议使用以下安全方案：

**方案 1: XML 白名单**
```xml
<select id="selectByExample">
    select * from user
    order by
    <choose>
      <when test="orderBy == 'id'">id</when>
      <when test="orderBy == 'name'">name</when>
      <when test="orderBy == 'create_time'">create_time</when>
      <otherwise>id</otherwise>  <!-- 默认排序 -->
    </choose>
</select>
```

**方案 2: 业务层白名单**
```java
public List<User> selectByExample(UserExample example) {
    // 白名单验证
    Set<String> allowedFields = Set.of("id", "name", "create_time");
    if (example.getOrderByClause() != null && 
        !allowedFields.contains(example.getOrderByClause())) {
        throw new IllegalArgumentException("Invalid order by field");
    }
    return mapper.selectByExample(example);
}
```

---

## 完整配置示例

### 默认配置（推荐）

适合大多数项目，聚焦核心安全问题：

```yaml
rules:
  staticAnalysis:
    checkSelectStar:
      enabled: false  # 关闭 SELECT * 检查
      riskLevel: LOW
    
    checkSensitiveTables:
      enabled: false  # 关闭敏感表检查
      riskLevel: MEDIUM
    
    checkDynamicWhereClause:
      enabled: false  # 关闭动态 WHERE 检查
      riskLevel: HIGH
    
    checkOrderByInjection:
      enabled: false  # 关闭 ORDER BY 注入检查
      riskLevel: CRITICAL
```

**检测内容**:
- ✅ SQL 注入风险（非 ORDER BY 场景）
- ✅ WHERE 条件完全缺失
- ✅ WHERE 条件永真（1=1）
- ✅ 物理分页缺失
- ✅ 聚合查询智能识别

**违规数量**: 约 250 个（基于示例项目）

---

### 严格模式

适合新项目或高安全要求：

```yaml
rules:
  staticAnalysis:
    checkSelectStar:
      enabled: true  # 开启 SELECT * 检查
      riskLevel: LOW
    
    checkSensitiveTables:
      enabled: true  # 开启敏感表检查
      riskLevel: MEDIUM
      tables:
        - user
        - password
        - admin
    
    checkDynamicWhereClause:
      enabled: true  # 开启动态 WHERE 检查
      riskLevel: HIGH
    
    checkOrderByInjection:
      enabled: true  # 开启 ORDER BY 注入检查
      riskLevel: CRITICAL
```

**检测内容**:
- ✅ 所有默认检查
- ✅ SELECT * 使用
- ✅ 敏感表访问
- ✅ 动态 WHERE 条件可能为空
- ✅ ORDER BY 注入风险

**违规数量**: 约 700+ 个（基于示例项目）

**注意**: 会产生较多误报，需要人工审核

---

### 自定义模式

根据项目实际情况调整：

```yaml
rules:
  staticAnalysis:
    # 开启 SELECT * 检查（代码规范要求）
    checkSelectStar:
      enabled: true
      riskLevel: LOW
    
    # 开启敏感表检查（有明确定义）
    checkSensitiveTables:
      enabled: true
      riskLevel: MEDIUM
      tables:
        - customer
        - payment
        - credit_card
    
    # 关闭动态 WHERE 检查（使用 MyBatis Generator）
    checkDynamicWhereClause:
      enabled: false
      riskLevel: HIGH
    
    # 关闭 ORDER BY 检查（有业务层控制）
    checkOrderByInjection:
      enabled: false
      riskLevel: CRITICAL
```

---

## 语义分析开关

**CLI 参数**: `--disable-semantic-analysis`

**说明**: 禁用 MyBatis 语义分析（默认启用）

**语义分析功能**:
- 解析 Java Mapper 接口
- 检测分页参数（RowBounds, IPage, Page）
- 分析参数类型和使用
- 上下文相关的风险评估

**何时禁用**:
- 快速扫描，只需基本检查
- 项目不使用 MyBatis
- 调试或测试

**示例**:
```bash
# 启用语义分析（默认）
java -jar sql-scanner-cli.jar -p /path/to/project

# 禁用语义分析
java -jar sql-scanner-cli.jar -p /path/to/project --disable-semantic-analysis
```

---

## 检查规则总结

| 检查项 | 默认状态 | 风险级别 | 适用场景 |
|--------|---------|---------|---------|
| SQL 注入（核心） | ✅ 启用 | CRITICAL | 所有项目 |
| WHERE 条件缺失 | ✅ 启用 | CRITICAL | 所有项目 |
| WHERE 条件永真 | ✅ 启用 | CRITICAL/HIGH | 所有项目 |
| 物理分页缺失 | ✅ 启用 | HIGH | 所有项目 |
| 聚合查询识别 | ✅ 启用 | - | 所有项目 |
| SELECT * | ❌ 关闭 | LOW | 性能敏感项目 |
| 敏感表访问 | ❌ 关闭 | MEDIUM | 有明确定义的项目 |
| 动态 WHERE | ❌ 关闭 | HIGH | 非 Generator 项目 |
| ORDER BY 注入 | ❌ 关闭 | CRITICAL | 新项目/严格要求 |

---

## 最佳实践

### 1. 渐进式启用

**阶段 1: 默认配置**
- 使用默认配置运行扫描
- 修复所有 CRITICAL 和 HIGH 级别问题
- 建立基线

**阶段 2: 选择性启用**
- 根据项目特点启用部分检查
- 例如：有敏感表定义，启用 `checkSensitiveTables`
- 修复新发现的问题

**阶段 3: 严格模式**
- 新项目从严格模式开始
- 老项目逐步迁移

### 2. CI/CD 集成

```bash
# 只检查核心安全问题（快速）
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  --fail-on-critical

# 完整检查（定期运行）
java -jar sql-scanner-cli.jar \
  -p /path/to/project \
  -c config-strict.yml \
  --output-format=html \
  --output-file=report.html
```

### 3. 团队协作

- 将配置文件纳入版本控制
- 团队统一使用相同配置
- 定期审查和更新配置

### 4. 白名单管理

对于误报，使用白名单：

```yaml
rules:
  staticAnalysis:
    checkOrderByInjection:
      enabled: true
      whitelistMapperIds:
        - com.example.mapper.UserMapper.selectByExample
        - com.example.mapper.OrderMapper.selectByExample
```

---

## 常见问题

### Q1: 为什么默认关闭这么多检查？

**A**: 
- 聚焦核心安全问题，减少噪音
- 理解框架语义（MyBatis Generator 的安全模式）
- 适应大多数项目的实际情况
- 提供灵活性，根据需求开启

### Q2: 我的项目使用 MyBatis Generator，需要开启动态 WHERE 检查吗？

**A**: 
- **不需要**。工具已经智能识别 MyBatis Generator 的标准模式
- 即使开启，标准模式也不会被报告
- 只有非标准的动态 WHERE 才会被检测

### Q3: ORDER BY ${orderByClause} 真的安全吗？

**A**: 
- **取决于业务层控制**
- 如果有白名单验证，是安全的
- 如果直接使用用户输入，不安全
- 建议：开启检查，审查所有使用场景，确保有白名单

### Q4: 如何处理大量的违规报告？

**A**: 
1. 优先修复 CRITICAL 级别
2. 然后修复 HIGH 级别
3. MEDIUM 和 LOW 可以逐步改进
4. 使用白名单排除已确认安全的代码
5. 调整配置，关闭不适用的检查

### Q5: 工具会误报吗？

**A**: 
- 静态分析有局限性，可能误报
- 工具已经做了智能识别（聚合查询、Generator 模式）
- 通过配置开关，可以减少误报
- 最终需要人工审核

---

## 7. 限制性字段模式配置

### 配置项: `paginationAbuse.limitingFieldPatterns`

**说明**: 配置哪些字段在 WHERE 条件中出现时，可以认为结果集已被限制，不需要物理分页

**默认值**:
```yaml
limitingFieldPatterns:
  - id                # 单独的 id 字段
  - "[a-z_]+_id"      # 以 _id 结尾的字段（如 user_id, developer_id）
  - "[a-z]+Id"        # 驼峰命名的 Id 字段（如 userId, developerId）
  - uuid              # UUID 字段
  - primary_key       # 主键字段
  - pk                # 主键缩写
```

**支持正则表达式**: 是

**使用场景**:
- 查询条件包含主键或唯一索引字段
- 结果集天然被限制在少量记录
- 不需要额外的物理分页

**示例**:

```xml
<!-- 不会报告缺少物理分页 - 使用 = 操作符 -->
<select id="findUserById" resultType="User">
  SELECT * FROM users WHERE id = #{id}
</select>

<select id="findByDeveloperId" resultType="ApiGroup">
  SELECT * FROM api_group WHERE developer_id = #{developerId}
</select>

<!-- 不会报告缺少物理分页 - 使用 IN 操作符 -->
<select id="findByGroupIds" resultType="ApiGroup">
  SELECT * FROM api_group WHERE group_id IN (
    <include refid="selectGroupIdsByDeveloperId"/>
  )
</select>
```

**自定义配置**:

```yaml
paginationAbuse:
  limitingFieldPatterns:
    - id
    - "[a-z_]+_id"
    - "[a-z]+Id"
    - uuid
    - code              # 添加唯一编码字段
    - "[a-z]+_code"     # 以 _code 结尾的字段
    - order_no          # 订单号
    - serial_number     # 序列号
```

**代码配置**:

```java
MyBatisMapperValidator validator = new MyBatisMapperValidator();
validator.setLimitingFieldPatterns(Arrays.asList(
    "id",
    "[a-z_]+_id",
    "[a-z]+Id",
    "uuid",
    "code",
    "order_no"
));
```

**注意事项**:
- 模式使用正则表达式，支持灵活匹配
- 匹配时不区分大小写
- 支持 `=` 和 `IN` 操作符（如 `id = #{id}` 或 `group_id IN (...)`）
- 不匹配 `LIKE`、`>` 等其他操作符

---

## 8. 表白名单和黑名单配置

### 配置项: `paginationAbuse.tableWhitelist` 和 `paginationAbuse.tableBlacklist`

**说明**: 配置哪些表需要或不需要检查物理分页

**白名单 (tableWhitelist)**:
- 白名单中的表不需要检查物理分页
- 适用于：配置表、字典表、小数据量表
- 例如：`sys_config`, `dict_type`, `api_version_plugins`

**黑名单 (tableBlacklist)**:
- 黑名单中的表必须检查物理分页
- 优先级高于白名单
- 适用于：大数据量表、核心业务表
- 例如：`user`, `order`, `log`

**优先级规则**:
1. **黑名单优先级最高**：如果表在黑名单中，必须检查分页
2. **白名单其次**：如果表在白名单中（且不在黑名单），豁免检查
3. **默认行为**：既不在白名单也不在黑名单，需要检查分页

**使用场景**:

```yaml
paginationAbuse:
  # 白名单：小表不需要分页
  tableWhitelist:
    - sys_config           # 系统配置表（几十条记录）
    - dict_type            # 字典类型表（几百条记录）
    - api_version_plugins  # API 版本插件配置表（几十条记录）
    - sys_menu             # 系统菜单表（几百条记录）
  
  # 黑名单：大表必须分页（即使在白名单中也要检查）
  tableBlacklist:
    - user                 # 用户表（百万级）
    - order                # 订单表（千万级）
    - log                  # 日志表（亿级）
```

**示例**:

```xml
<!-- 不会报告缺少物理分页 - 表在白名单中 -->
<select id="gwApiVersionPluginsSql" resultType="java.util.Map">
  SELECT * FROM api_version_plugins
</select>

<select id="getAllDictTypes" resultType="DictType">
  SELECT * FROM dict_type
</select>

<!-- 会报告缺少物理分页 - 表在黑名单中 -->
<select id="getAllUsers" resultType="User">
  SELECT * FROM user
</select>
```

**配置示例**:

```yaml
rules:
  paginationAbuse:
    enabled: true
    
    # 表白名单
    tableWhitelist:
      - sys_config
      - dict_type
      - dict_data
      - api_version_plugins
      - sys_menu
      - sys_role
      - sys_dept
    
    # 表黑名单（可选）
    tableBlacklist:
      - user
      - order
      - log
      - transaction
```

**代码配置**:

```java
MyBatisMapperValidator validator = new MyBatisMapperValidator();

// 设置白名单
validator.setTableWhitelist(Arrays.asList(
    "sys_config",
    "dict_type",
    "api_version_plugins"
));

// 设置黑名单
validator.setTableBlacklist(Arrays.asList(
    "user",
    "order",
    "log"
));
```

**注意事项**:
- 表名匹配不区分大小写
- 只匹配表名，不支持正则表达式
- 黑名单优先级高于白名单
- 建议定期审查白名单，确保表的数据量没有显著增长

---

## 更新日志

### v1.0.0 (2025-12-16)

**新增功能**:
- ✅ SELECT * 检查开关
- ✅ 敏感表访问检查开关
- ✅ 动态 WHERE 条件检查开关
- ✅ ORDER BY 注入检查开关
- ✅ LIMIT/OFFSET 注入检查开关
- ✅ 聚合函数动态字段检查开关
- ✅ 限制性字段模式配置（可自定义，支持 = 和 IN 操作符）
- ✅ 表白名单和黑名单配置（豁免小表的分页检查）
- ✅ 聚合查询智能识别
- ✅ MyBatis Generator 标准模式识别

**改进**:
- 大幅减少误报（从 707 降到 160）
- 智能上下文建议
- 中文消息和建议
- 完整的 XML 和 Java 上下文显示
- 可配置的限制性字段模式
- 灵活的表级别分页检查控制

---

## 联系我们

如有问题或建议，请提交 Issue 或 Pull Request。

