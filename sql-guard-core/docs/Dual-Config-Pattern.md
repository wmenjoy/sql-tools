# Dual-Config Pattern Architecture

## Overview

SQL Guard System采用**Dual-Config Pattern（双配置模式）**，使用两套独立的配置类层次结构，分别服务于不同的职责：

1. **YAML配置层** (`com.footstone.sqlguard.config` package)
2. **运行时配置层** (`com.footstone.sqlguard.validator.rule.impl` package)

## 架构设计

### 1. YAML配置层 (config package)

**职责：** YAML文件反序列化

**特点：**
- 简单的POJO类（Plain Old Java Object）
- 无继承关系
- 可变字段（mutable），支持setter
- 与SnakeYAML完全兼容

**典型类：**
```java
package com.footstone.sqlguard.config;

public class BlacklistFieldsConfig {
    private boolean enabled = true;
    private RiskLevel riskLevel = RiskLevel.HIGH;
    private Set<String> fields = new HashSet<>();

    // Standard getters/setters
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    // ...
}
```

**使用场景：**
- `SqlGuardConfig.RulesConfig` 中的字段类型
- `YamlConfigLoader` 反序列化目标
- 用户配置文件 (`application.yml`) 映射

### 2. 运行时配置层 (validator/rule/impl package)

**职责：** Checker运行时行为控制

**特点：**
- 继承 `CheckerConfig` 基类
- 包含完整的Javadoc文档和使用示例
- 提供配置验证逻辑
- 可包含复杂业务逻辑

**典型类：**
```java
package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration for BlacklistFieldChecker.
 *
 * <p>详细的Javadoc说明...</p>
 */
public class BlacklistFieldsConfig extends CheckerConfig {
    private Set<String> fields = new HashSet<>(Arrays.asList(
        "deleted", "del_flag", "status"
    ));

    // Inherits enabled/riskLevel from CheckerConfig
    // Additional validation logic if needed
}
```

**使用场景：**
- Checker构造函数参数类型
- 运行时规则检查配置
- 提供默认值和业务约束

## 配置流转过程

```
YAML文件
  ↓ (SnakeYAML反序列化)
config包Config类 (SqlGuardConfig.RulesConfig)
  ↓ (应用层转换)
validator包Config类
  ↓ (构造Checker)
Checker实例
```

## 为什么需要两层配置？

### 1. 关注点分离 (Separation of Concerns)

- **YAML层**：专注于配置文件格式兼容性
- **运行时层**：专注于业务逻辑和默认值

### 2. SnakeYAML兼容性

SnakeYAML要求：
- 简单的JavaBean结构
- 无复杂继承关系
- 标准getter/setter命名

运行时配置类如果继承CheckerConfig会导致YAML反序列化复杂化。

### 3. 文档清晰性

- **config包**：用户配置视角，简洁明了
- **validator包**：开发者视角，包含详细设计文档

### 4. 默认值管理

- **config包**：从YAML加载的用户配置
- **validator包**：框架提供的合理默认值

## 同名类处理规则

### ✅ 正确的使用方式

1. **在config包内部** - 使用同包类，无需import：
```java
package com.footstone.sqlguard.config;

public class SqlGuardConfig {
    public static class RulesConfig {
        // 直接使用同包的BlacklistFieldsConfig
        private BlacklistFieldsConfig blacklistFields = new BlacklistFieldsConfig();
    }
}
```

2. **在validator包内部** - 使用同包类：
```java
package com.footstone.sqlguard.validator.rule.impl;

public class BlacklistFieldChecker extends AbstractRuleChecker {
    // 使用同包的BlacklistFieldsConfig (extends CheckerConfig)
    private final BlacklistFieldsConfig config;

    public BlacklistFieldChecker(BlacklistFieldsConfig config) {
        this.config = config;
    }
}
```

### ❌ 错误的使用方式

1. **在config包中import validator包的Config**：
```java
package com.footstone.sqlguard.config;

// ❌ 错误：不应该import validator包
import com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig;

public class CustomYamlConstructor {
    // 这会导致YAML反序列化使用错误的类
}
```

2. **在validator包中import config包的Config**：
```java
package com.footstone.sqlguard.validator.rule.impl;

// ❌ 错误：Checker不应该使用config包的类
import com.footstone.sqlguard.config.BlacklistFieldsConfig;

public class BlacklistFieldChecker {
    // 这会丢失CheckerConfig的继承功能
}
```

## 常见问题

### Q: 为什么不重命名其中一层的类？

A:
1. 两层的职责不同，但表示的配置概念相同
2. 包名已经提供了清晰的命名空间隔离
3. 保持类名一致更符合DDD（领域驱动设计）原则

### Q: 如何避免import错误？

A: 遵循规则：
- **config包** → 只使用 `com.footstone.sqlguard.config.*Config`
- **validator包** → 只使用 `com.footstone.sqlguard.validator.rule.impl.*Config`
- **跨包使用** → 明确使用完全限定名或正确的import

### Q: 配置如何从YAML层转到运行时层？

A: 通常在DefaultSqlSafetyValidator或类似的组装器中进行转换：
```java
// 从YAML配置创建运行时配置
BlacklistFieldsConfig yamlConfig = sqlGuardConfig.getRules().getBlacklistFields();

// 转换为运行时配置（可能需要复制字段或映射）
com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig runtimeConfig =
    new com.footstone.sqlguard.validator.rule.impl.BlacklistFieldsConfig();
runtimeConfig.setEnabled(yamlConfig.isEnabled());
runtimeConfig.setFields(yamlConfig.getFields());

// 创建Checker
BlacklistFieldChecker checker = new BlacklistFieldChecker(runtimeConfig);
```

## 相关文件

### Config包文件
- `SqlGuardConfig.java` - 根配置类
- `NoWhereClauseConfig.java`
- `DummyConditionConfig.java`
- `BlacklistFieldsConfig.java`
- `WhitelistFieldsConfig.java`
- `PaginationAbuseConfig.java`
- `NoPaginationConfig.java`
- `EstimatedRowsConfig.java`

### Validator包文件
- `CheckerConfig.java` - 基类
- `NoWhereClauseConfig.java`
- `DummyConditionConfig.java`
- `BlacklistFieldsConfig.java`
- `WhitelistFieldsConfig.java`
- `PaginationAbuseConfig.java` (嵌套配置)
- `LogicalPaginationConfig.java`
- `NoConditionPaginationConfig.java`
- `NoPaginationConfig.java`
- `EstimatedRowsConfig.java`

## 修订历史

- **2025-12-15**: 初始版本，文档化Dual-Config Pattern架构
- **2025-12-15**: 修复CustomYamlConstructor和ImmutablePropertyUtils的错误import
