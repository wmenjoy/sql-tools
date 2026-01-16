# 测试改进命令

分析现有测试代码质量，提供改进建议并可选择自动修复。

## 使用方式

```
/improve-tests [选项]
```

## 选项

- `--file <路径>`: 测试文件路径（必需）
- `--fix`: 自动修复可修复的问题
- `--style`: 检查并修复代码风格
- `--performance`: 分析测试性能
- `--output <路径>`: 改进后的文件输出路径

## 执行流程

### 步骤 1: 读取测试文件

读取指定的测试文件，分析其结构和内容。

### 步骤 2: 质量分析

对测试代码进行多维度分析：

#### 2.1 结构分析

```yaml
structure_analysis:
  test_count: {number}
  avg_test_length: {lines}
  setup_teardown: {present/missing}
  test_grouping: {good/needs_improvement}
  naming_convention: {consistent/inconsistent}
```

#### 2.2 断言分析

```yaml
assertion_analysis:
  avg_assertions_per_test: {number}
  assertion_types_used: [{types}]
  missing_assertions: [{suggestions}]
  over_asserting: [{tests}]
```

#### 2.3 Mock 使用分析

```yaml
mock_analysis:
  mock_count: {number}
  mock_verification: {complete/incomplete}
  mock_reuse: {good/could_improve}
  unnecessary_mocks: [{mocks}]
```

#### 2.4 测试质量指标

| 指标 | 当前值 | 建议值 | 状态 |
|------|--------|--------|------|
| 单一职责 | {score}/10 | 8+ | {status} |
| 独立性 | {score}/10 | 9+ | {status} |
| 可读性 | {score}/10 | 8+ | {status} |
| 可维护性 | {score}/10 | 8+ | {status} |
| 确定性 | {score}/10 | 10 | {status} |

### 步骤 3: 问题检测

检测常见测试问题：

#### 3.1 代码异味 (Code Smells)

```yaml
code_smells:
  - type: multiple_assertions_unrelated
    location: {file}:{line}
    description: 一个测试中验证多个不相关的行为
    severity: warning
    fix: 拆分为独立测试

  - type: test_logic
    location: {file}:{line}
    description: 测试中包含条件逻辑
    severity: error
    fix: 使用参数化测试或拆分

  - type: shared_state
    location: {file}:{line}
    description: 测试之间共享可变状态
    severity: error
    fix: 每个测试使用独立状态

  - type: flaky_test
    location: {file}:{line}
    description: 测试可能不稳定（时间依赖、随机数）
    severity: warning
    fix: 固定时间/随机种子

  - type: missing_assertion
    location: {file}:{line}
    description: 测试没有断言
    severity: error
    fix: 添加必要断言

  - type: dead_code
    location: {file}:{line}
    description: 测试中存在无用代码
    severity: info
    fix: 移除无用代码
```

#### 3.2 命名问题

```yaml
naming_issues:
  - test: TestFunc1
    issue: 名称不描述测试场景
    suggestion: TestLogin_ValidCredentials_ReturnsToken

  - test: test_stuff
    issue: 名称过于模糊
    suggestion: test_create_user_with_valid_email_succeeds
```

#### 3.3 缺失的测试场景

```yaml
missing_scenarios:
  - function: Login
    missing:
      - 空用户名测试
      - 空密码测试
      - SQL 注入防护测试
      - 并发登录测试
```

### 步骤 4: 生成改进建议

为每个问题生成具体的改进建议：

```markdown
### 问题 1: {issue_type}

**位置:** {file}:{line}

**当前代码:**
```{language}
{current_code}
```

**问题说明:**
{description}

**改进后代码:**
```{language}
{improved_code}
```

**改进理由:**
{reasoning}
```

### 步骤 5: 如果指定 --fix，自动修复

自动修复以下类型的问题：
- 命名规范问题
- 代码格式问题
- 简单的断言改进
- 移除死代码
- 添加缺失的 t.Helper()

需要人工确认的修复：
- 测试逻辑重构
- Mock 改进
- 测试拆分

### 步骤 6: 输出改进报告

## 输出格式

```markdown
# 测试改进报告

## 概览

- **文件:** {file_path}
- **测试数量:** {test_count}
- **质量评分:** {score}/100

## 质量指标

| 维度 | 评分 | 状态 |
|------|------|------|
| 结构 | {score}/20 | {status} |
| 断言 | {score}/20 | {status} |
| 命名 | {score}/20 | {status} |
| 可维护性 | {score}/20 | {status} |
| 最佳实践 | {score}/20 | {status} |

## 发现的问题

### 严重问题 ({count})
{critical_issues}

### 警告 ({count})
{warnings}

### 建议 ({count})
{suggestions}

## 改进建议

### 1. {improvement_title}

**当前:**
```{language}
{current}
```

**建议:**
```{language}
{suggested}
```

**理由:** {reason}

---

## 缺失的测试场景

以下场景建议添加测试:

| 函数 | 缺失场景 | 优先级 |
|------|---------|--------|
| {func} | {scenario} | {priority} |

## 自动修复摘要

{if --fix specified}
- 已修复: {fixed_count} 个问题
- 需手动处理: {manual_count} 个问题
- 修复后文件: {output_path}
{endif}

## 后续步骤

1. {next_step_1}
2. {next_step_2}
3. {next_step_3}
```

## 示例

### 示例 1: 分析测试质量

```
用户: /improve-tests --file internal/auth/service_test.go
```

### 示例 2: 自动修复问题

```
用户: /improve-tests --file tests/user.test.ts --fix
```

### 示例 3: 检查代码风格和性能

```
用户: /improve-tests --file UserServiceTest.java --style --performance
```

## 质量评分标准

### 结构 (20分)
- 5分: 使用 setup/teardown
- 5分: 测试分组合理
- 5分: 测试长度适中
- 5分: 无重复代码

### 断言 (20分)
- 5分: 每个测试有断言
- 5分: 断言数量适中(1-3个)
- 5分: 使用合适的断言方法
- 5分: 断言信息有意义

### 命名 (20分)
- 10分: 测试名描述场景
- 10分: 遵循命名规范

### 可维护性 (20分)
- 5分: 测试独立
- 5分: 无共享状态
- 5分: 无测试逻辑
- 5分: 易于理解

### 最佳实践 (20分)
- 5分: 使用 AAA 模式
- 5分: 合理使用 Mock
- 5分: 覆盖边界条件
- 5分: 包含错误场景

## 参考文档

- 最佳实践指南: `docs/3-guides/ai-testing/best-practices.md`
- 代码生成规范: `docs/1-specs/ai-test-generation/code-generation-specification.md`
