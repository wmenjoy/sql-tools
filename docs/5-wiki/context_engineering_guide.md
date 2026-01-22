---
type: Knowledge Base
component: Wiki
version: 1.2.0
created: 2026-01-09
updated: 2026-01-22
status: Active
maintainer: SQL Safety Guard Team
language: zh-CN
---

# 上下文工程：AI协作的最佳实践指南

## 综合

### 1.初始化

1.1 apm

```markdown
npm install -g agentic-pm

# 在项目中引用初始化, 暂不支持trae,可以选择claude code
apm init
```

1.2 superpower

```
git clone git@github.com:obra/superpowers.git
```

### 2. 使用

```
由于trae暂时不支持commands和skill(很快就会见面)，通过文件应用的方式使用
```

---

## 一、什么是上下文工程？

想象你在和一个健忘的天才合作。他每次只能记住最近的对话，超过一定量就会忘记之前说的话。但他的推理能力极强，只要你给他正确的信息，他就能出色地完成任务。

这就是与大语言模型（LLM）协作的真实情况。上下文工程就是一套方法论，教你如何：
- 管理这个"天才"的记忆
- 组织信息让他更高效
- 设计工作流程避免混乱
- 在长期项目中保持连续性

## 二、核心问题：有限记忆 vs 无限需求

### 问题的本质（第一性原理）

LLM本质上是一个函数：
```
输入（提示词 + 上下文） → 输出（回答）
```

核心矛盾：
- **LLM的工作记忆有限**：上下文窗口通常几千到几十万tokens
- **实际项目信息无限增长**：需求文档、代码库、讨论历史、决策记录...
- **记忆会衰减**：对话越长，早期信息越容易被遗忘、扭曲或忽略

就像你的工作记忆只能同时处理5-7个项目，但实际工作需要记住成百上千个细节。

### 人类如何解决这个问题？

1. **外部化记忆**：写笔记、做文档、画图表 (存储)
2. **结构化信息**：不是所有信息都同等重要，分层组织 （分层）
3. **专业化分工**：项目经理、开发者、测试人员各司其职（拆分思想）
4. **显式流程**：明确的工作阶段和交接机制 (复用流程)

上下文工程就是将这些人类智慧应用到AI协作中。

## 三、核心解决思路

### 1. 外部化记忆
不依赖LLM的"记忆力"，将关键信息存储在外部文件中：
- 需求规格说明
- 设计文档
- 实现计划
- 决策日志
- 任务清单

**示例**：APM的Memory Bank、Superpowers的SKILL.md文件

### 2. 结构化上下文
信息分层，按需加载：
```
层级1：核心规范（总是需要）
  ├─ 层级2：当前阶段计划（经常需要）
  │   └─ 层级3：具体任务细节（按需加载）
  │       └─ 层级4：执行日志（归档）
```

### 3. 专业化分工
不同的AI会话/Agent处理不同类型的任务：
- **Setup Agent**：收集需求、创建实现计划
- **Manager Agent**：协调任务、管理记忆系统
- **Implementation Agent**：执行具体编码任务
- **Ad-Hoc Agent**：临时专家（研究或调试）

**核心原则**：一个会话只做一类事情，避免职责混杂，同时保证上下文不会过度膨胀。当Agent上下文将满时，通过Handover机制交接给新Agent。

### 4. 显式工作流
明确的阶段划分（以APM为例）：
```
阶段1：Context Synthesis（需求收集，迭代式Q&A）
    ↓
阶段2：Project Breakdown（任务分解，anti-packing）
    ↓
阶段3：Review & Refinement（系统审查）
    ↓
阶段4：Enhancement（详细化任务规格）
    ↓
阶段5：Bootstrap（启动Manager Agent执行）
```

每个阶段有清晰的输入、输出和验收标准。**关键点**：在进入下一阶段前必须获得用户确认。

## 四、十大最佳实践

### 实践1：永远先规划后执行

**核心原则**：没有计划的编码会导致返工，浪费上下文窗口。**计划是最重要的上下文**。

#### 1.1 错误与正确做法对比

| 错误做法 | 正确做法 |
|---------|---------|
| 用户："帮我做一个用户管理系统" | 用户："帮我做一个用户管理系统" |
| AI：（直接开始写代码） | AI："让我先理解需求..." //然后和用户一件件的沟通 |
| **后果**：方向错误，大量返工 | **效果**：需求明确，一次做对 |

#### 1.2 苏格拉底式提问法

苏格拉底式提问（Socratic Questioning）是一种通过**连续追问**来引导思考、发现真理的方法。在AI协作中特别有效，因为它能帮助用户和AI达成**共同理解**。

**核心好处**：

| 好处 | 提问方式 | 效果 |
|------|---------|------|
| **澄清模糊需求** | "你说的X具体是什么意思？" | 模糊概念 → 明确定义 |
| **发现隐藏假设** | "为什么你认为必须这样做？" | 暴露未意识到的假设 |
| **探索边界条件** | "如果发生Y情况怎么办？" | 提前识别异常场景 |
| **验证理解正确** | "我理解的是Z，对吗？" | 确保双方共识 |
| **激发更好方案** | "还有其他方式达到这个目标吗？" | 发现更优解决方案 |
| **减少返工成本** | 在编码前通过提问发现问题 | 比事后修复成本低10倍 |

**示例对话**：
```
用户："帮我做一个用户管理系统"

AI："我想先理解一下需求。这个用户管理系统主要是：
    A) 内部员工管理（HR系统）
    B) 外部客户账号管理
    C) 权限和角色管理
    D) 其他用途
    请选择或描述你的场景？"

用户："B，外部客户账号"

AI："明白了。对于外部客户账号管理，通常需要考虑：
    - 注册方式：邮箱/手机/第三方登录？
    - 安全要求：双因素认证？密码强度？
    - 用户规模：预计多少用户？
    
    先告诉我注册方式的偏好？"
```

#### 1.3 Brainstorming 最佳实践

来自 Superpowers 的经验总结：

1. **一次只问一个问题** - 不要用多个问题轰炸用户，让用户专注回答
2. **优先使用多选题** - 比开放式问题更容易回答，降低认知负担
3. **先理解项目上下文** - 检查现有文件、文档、最近提交，避免重复提问
4. **探索2-3种方案** - 提出多种方案及其权衡，给出推荐和理由
5. **分段呈现设计** - 每段200-300字，每段后确认是否正确
6. **YAGNI原则** - 从所有设计中删除不必要的功能，保持简洁

#### 1.4 实施流程

```
┌─────────────────────────────────────────────────────────────────┐
│  头脑风暴（苏格拉底式Q&A，每次一个问题）                         │
│      ↓                                                          │
│  设计呈现（分段200-300字，每段后确认）                          │
│      ↓                                                          │
│  设计文档（保存到 docs/plans/YYYY-MM-DD-<topic>-design.md）      │
│      ↓                                                          │
│  实现计划（详细任务分解，通过Anti-Packing测试）                  │
│      ↓                                                          │
│  执行（TDD，频繁提交）                                          │
└─────────────────────────────────────────────────────────────────┘
```

- 将规划结果写入文档（README.md、DESIGN.md、PLAN.md）
- 在执行前让AI复述计划，确认理解正确

---

### 实践2：显式化所有关键信息

**核心原则**：如果信息很重要，就写下来，不要指望AI记住。

**需要显式化的信息**：
- 项目目标和约束
- 技术栈选择及原因
- 设计决策及trade-off
- 命名约定和代码规范
- 已知问题和临时方案
- 依赖关系

**格式建议**：
```markdown
# PROJECT_CONTEXT.md

## 核心目标
- 主要目标：...
- 成功标准：...

## 技术约束
- 必须使用Python 3.8+
- 不能使用外部数据库（嵌入式SQLite）

## 重要决策
- 2026-01-15：选择FastAPI而非Flask，原因：需要async支持
- 2026-01-20：用户认证使用JWT，原因：无状态、易扩展

## 待办清单
- [ ] 实现用户登录
- [ ] 添加权限检查
- [ ] 编写单元测试
```

---

### 实践3：小批量任务

**原则**：一个任务应该在2-5分钟内完成。

**错误示例**：
```
任务："实现用户管理模块"
（太大，包含太多子任务）
```

**正确示例**：
```
任务1："创建User模型类，包含id、username、email字段"
任务2："实现用户创建API端点 POST /users"
任务3："添加用户创建的输入验证"
任务4："为用户创建功能编写单元测试"
```

**为什么**：
- 小任务失败时容易恢复
- 减少单次对话的上下文负担
- 更容易跟踪进度
- 降低AI犯错的概率

**Anti-Packing 防护测试**（APM方法论）：

在分解任务时，对每个潜在任务应用以下5个测试：

| 测试名称 | 问题 | 如果"否"则... |
|---------|------|--------------|
| **单一焦点测试** | 能否由一个Agent在一次专注会话中完成，无需切换上下文？ | 拆分任务 |
| **领域边界测试** | 是否涉及多个不相关的技术领域或技能集？ | 按领域拆分 |
| **独立价值测试** | 如果拆分成组件，每个组件是否能独立交付价值？ | 保持合并或重新设计 |
| **单一交付物测试** | 完成此任务是否产生一个可作为单一工作单元完成的交付物？ | 拆分交付物 |
| **复杂度一致性测试** | 此任务的复杂度是否与阶段中其他任务匹配？ | 进一步细分 |

**任务分解检查清单**：
- [ ] 任务有明确的输入（需要什么信息/文件）
- [ ] 任务有明确的输出（会产生什么文件/变更）
- [ ] 任务有验收标准（如何判断完成）
- [ ] 任务可以在一个会话中完成
- [ ] 任务失败不会破坏整个项目
- [ ] 通过所有5个Anti-Packing测试

---

### 实践4：建立检查点机制

**核心概念**：定期保存项目状态，支持从任意点恢复。

**实施方法**：

**A. 代码层面**：
```bash
# 为每个大任务创建Git分支
git checkout -b feature/user-authentication

# 完成一个子任务就提交
git add .
git commit -m "Add User model with basic fields"

# 完成整个任务后合并
git checkout main
git merge feature/user-authentication
```

**B. 上下文层面**：
每个阶段结束时创建"移交文档"：
```markdown
# HANDOVER.md

## 已完成
- User模型已实现（位置：models/user.py）
- 创建用户API已实现（位置：api/users.py）
- 单元测试通过（覆盖率75%）

## 当前状态
- 正在实现用户认证功能
- JWT token生成已完成
- 正在添加token验证中间件

## 下一步
1. 完成token验证中间件
2. 添加认证装饰器
3. 更新API文档

## 重要上下文
- 使用PyJWT库版本2.8.0
- Token过期时间设为24小时
- Secret key存储在环境变量JWT_SECRET中
```

---

### 实践5：专用会话处理专门任务

**原则**：不要在一个对话里混杂多种职责。

**会话类型示例**：

**规划会话**：
- 目的：理解需求、设计架构、制定计划
- 输入：项目需求、约束条件
- 输出：设计文档、实现计划
- 何时结束：计划获得确认

**实现会话**：
- 目的：编写具体代码
- 输入：任务描述、相关文档、依赖代码
- 输出：代码文件、测试
- 何时结束：任务完成并通过测试

**调试会话**：
- 目的：排查和修复bug
- 输入：错误现象、相关代码、日志
- 输出：问题诊断、修复方案
- 何时结束：问题解决

**重构会话**：
- 目的：改进代码质量
- 输入：现有代码、重构目标
- 输出：重构后的代码
- 何时结束：目标达成且测试通过

**为什么要分离**：
- 避免上下文污染（调试信息不会干扰实现）
- 每个会话都有清晰的目标
- 更容易管理和恢复
- 减少AI的认知负担

---

### 实践6：标准化上下文传递

**问题**：如何让新会话快速"上岗"？

**解决方案**：使用标准化的"引导提示"（Bootstrap Prompt）

**模板示例**：
```markdown
# 会话初始化提示

你是一个专门的实现Agent。你的职责是：
- 根据任务描述编写代码
- 遵循项目规范和约定
- 记录实现过程和决策

## 项目上下文
请阅读以下文件了解项目背景：
- @PROJECT_CONTEXT.md - 项目目标和约束
- @DESIGN.md - 架构设计
- @CODING_STANDARDS.md - 编码规范

## 当前任务
文件位置：@tasks/task-005-user-auth.md

任务内容：
- 实现JWT token验证中间件
- 添加到FastAPI应用
- 编写单元测试

## 依赖上下文
相关代码：
- @models/user.py - User模型
- @utils/jwt.py - JWT工具函数

## 执行要求
1. 先阅读所有相关文件
2. 确认理解任务要求
3. 编写代码并测试
4. 更新任务状态为"已完成"

准备好了吗？请确认你理解了任务。
```

**标准化的好处**：
- 新会话能立即进入工作状态
- 减少来回澄清的轮次
- 确保关键信息不遗漏
- 可以模板化、自动化

---

### 实践7：测试驱动开发（TDD）

**核心原则**：测试是验证上下文理解的最好方式。

**标准TDD流程**：
```python
# 1. 先写测试（红色阶段）
def test_create_user():
    user = create_user(username="alice", email="alice@example.com")
    assert user.username == "alice"
    assert user.email == "alice@example.com"
    assert user.id is not None

# 运行测试 → 失败（因为还没实现）

# 2. 实现功能（绿色阶段）
def create_user(username: str, email: str) -> User:
    user = User(username=username, email=email)
    user.save()
    return user

# 运行测试 → 通过

# 3. 重构（保持绿色）
# 优化代码，测试始终通过
```

**为什么TDD对上下文工程重要**：
- 测试就是可执行的规格说明
- 测试失败立即暴露理解偏差
- 测试套件是项目真相的唯一来源
- 重构时有安全网保护

**TDD铁律**（来自 Superpowers）：
```
没有失败的测试，就没有生产代码
```

如果你在测试之前写了代码？**删除它。重新开始。**

- 不要把它当作"参考"
- 不要一边写测试一边"调整"它
- 不要看它
- 删除就是删除

**常见借口及其反驳**：

| 借口 | 现实 |
|------|------|
| "太简单不需要测试" | 简单的代码也会出错。测试只需30秒。 |
| "我之后会写测试" | 之后写的测试立即通过，证明不了任何事。 |
| "已经手动测试过了" | 手动测试是临时的。没有记录，无法重复运行。 |
| "删除X小时的工作太浪费" | 沉没成本谬误。保留未验证的代码才是技术债务。 |
| "TDD会拖慢我" | TDD比调试更快。务实=测试先行。 |
| "这次不一样因为..." | 所有这些都意味着：删除代码，用TDD重新开始。 |

**红旗警告 - 立即停止并重新开始**：
- 测试之前写了代码
- 实现之后才添加测试
- 测试立即通过
- 无法解释为什么测试失败
- 自我说服"就这一次"

**实施建议**：
- 每个任务都先写测试
- 测试覆盖率至少70%
- 测试即文档（用清晰的测试名称）
- 集成到CI/CD流程
- **必须看到测试失败**，才能知道测试真的在验证正确的事情

---

### 实践8：元认知能力：教AI何时做什么

**核心概念**：AI需要知道自己的能力边界，知道何时使用什么技能。

**Superpowers的实现方式：SKILL.md**

Superpowers 使用简洁的 YAML frontmatter 格式定义技能，`description` 字段同时说明技能功能和使用时机：

```markdown
---
name: root-cause-tracing
description: Use when errors occur deep in execution and you need to trace back to find the original trigger - systematically traces bugs backward through call stack
---

# Root Cause Tracing

## Overview
Bugs often manifest deep in the call stack. Your instinct is to fix where the error appears, but that's treating a symptom.

**Core principle:** Trace backward through the call chain until you find the original trigger, then fix at the source.

## When to Use
- ✅ Error happens deep in execution (not at entry point)
- ✅ Stack trace shows long call chain
- ✅ Unclear where invalid data originated
- ❌ Error cause is already clear
- ❌ Simple syntax errors

## The Tracing Process
1. Observe the symptom
2. Find immediate cause
3. Ask: What called this?
4. Keep tracing up
5. Find original trigger

**Key Principle:** NEVER fix just where the error appears. Trace back to find the original trigger.
```

**注意**：Superpowers 技能文件使用 kebab-case 命名（如 `root-cause-tracing`），放置在 `skills/<skill-name>/SKILL.md` 目录结构中。

**APM的实现方式：多Agent协作体系**

APM（Agentic Project Management）定义了明确的Agent分工和交互协议：

```markdown
# APM Agent 角色体系

## 1. Setup Agent（规划阶段）
职责：需求收集、项目分解、创建 Implementation Plan
- ✅ 通过迭代式Q&A收集项目上下文
- ✅ 将需求分解为可执行的任务
- ✅ 创建 Manager Agent Bootstrap Prompt
- ❌ 不执行任何实现工作

## 2. Manager Agent（协调阶段）
职责：任务协调、Memory管理、Task Assignment创建
- ✅ 根据 Implementation Plan 创建 Task Assignment Prompt
- ✅ 管理 Memory 系统（创建目录、审查日志）
- ✅ 处理跨Agent依赖和任务交接
- ❌ 不直接编写代码

## 3. Implementation Agent（执行阶段）
职责：执行具体编码任务
- ✅ 接收 Task Assignment Prompt 执行任务
- ✅ 遵循 single-step 或 multi-step 执行模式
- ✅ 完成后更新 Memory Log
- ❌ 超过2次调试失败必须委托

## 4. Ad-Hoc Agent（临时专家）
职责：处理委托的专项任务
- Research Agent：信息收集、文档研究
- Debug Agent：复杂问题调试、根因分析
- ✅ 独立上下文，不访问APM主系统
- ✅ 结果以 markdown code block 返回

## 委托机制
- 遇到复杂bug（2次尝试失败） → 委托 Debug Agent
- 需要研究最新文档/最佳实践 → 委托 Research Agent
- Agent上下文窗口将满 → 触发 Handover 交接
```

**实施建议**：
- 为每种AI角色/会话创建"使用说明书"
- 明确能做什么、不能做什么
- 定义何时应该委托/请求帮助
- 定期更新技能库

---

### 实践9：版本控制和可追溯性

**核心原则**：记录所有重要决策的"为什么"，不只是"是什么"。

**实施方法**：

**A. 代码层面**：
```bash
# 提交信息要说明原因
git commit -m "Use Redis for session storage

Reason: In-memory sessions don't work with multiple servers.
Redis provides persistence and can be shared across instances.
Alternative considered: PostgreSQL (rejected: overkill for simple key-value)
"
```

**B. 文档层面**：
```markdown
# DECISIONS.md

## ADR-001: 使用FastAPI而非Flask
日期：2024-01-15
状态：已采纳

### 上下文
需要选择Python web框架

### 决策
选择FastAPI

### 理由
- 原生async支持（需求中有高并发要求）
- 自动API文档生成（减少维护负担）
- 类型提示支持（减少运行时错误）

### 后果
- 团队需要学习async/await
- 某些第三方库可能不兼容
- 整体开发效率预期提升30%

### 备选方案
- Flask + Flask-RESTX：成熟但缺乏async
- Django REST Framework：过于重量级
```

**为什么重要**：
- 新成员（包括新AI会话）能理解历史决策
- 避免重复犯错
- 当环境变化时知道哪些假设需要重新审视
- 上下文不会随着人员/会话变化而丢失

---

### 实践10：渐进式详细化

**核心原则**：从高层概念开始，逐步细化，避免过早陷入细节。

**错误做法**：
```
用户："做一个博客系统"
AI：（立即开始讨论数据库schema细节）
```

**正确做法（层层递进）**：

**第1层：核心概念（1页纸）**
```markdown
# 博客系统 - 概念

## 核心功能
1. 文章管理（发布、编辑、删除）
2. 用户系统（注册、登录）
3. 评论功能

## 技术栈
- Python + FastAPI
- SQLite
- 前端：Vue.js
```

**第2层：架构设计（5页）**
```markdown
# 博客系统 - 架构设计

## 系统架构
[简单的架构图]

## 模块划分
1. 认证模块
2. 文章模块
3. 评论模块
4. API层

## 数据模型（高层）
- User: id, username, email
- Article: id, title, content, author_id
- Comment: id, content, article_id, author_id
```

**第3层：详细设计（20页）**
```markdown
# 博客系统 - 详细设计

## 认证模块详细设计

### API端点
- POST /auth/register
- POST /auth/login
- POST /auth/logout

### 数据模型详细
class User(Base):
    id: int (primary key, auto increment)
    username: str (unique, max 50 chars)
    email: str (unique, validated)
    password_hash: str (bcrypt)
    created_at: datetime
    ...
```

**第4层：实现计划（任务列表）**
```markdown
# 实现计划

## Sprint 1: 基础设施
- Task 1.1: 项目初始化
- Task 1.2: 数据库连接
- Task 1.3: 基础模型

## Sprint 2: 认证系统
- Task 2.1: User模型实现
- Task 2.2: 注册功能
- Task 2.3: 登录功能
...
```

**为什么渐进式重要**：
- 早期避免浪费上下文在可能改变的细节上
- 在高层达成共识后再投入详细工作
- 每一层都是下一层的高质量上下文
- 更容易发现设计问题

---

## 五、进阶技巧

以下技巧用于处理AI协作中的特殊情况和问题恢复。

### 技巧1：上下文混乱时的恢复

当模型能力较弱或对话过长导致**上下文混乱**时，使用以下恢复策略：

| 症状 | 恢复策略 |
|------|---------|
| AI开始重复之前的错误 | **重置上下文**：开启新会话，只带入关键文档（PLAN.md、当前任务描述） |
| AI忘记了之前的决策 | **显式提醒**：将决策记录为文件，每次对话开头引用该文件 |
| AI理解偏离预期 | **强制复述**："请用你自己的话复述一下当前任务的目标和约束" |
| 对话过长(>50轮) | **总结压缩**：让AI总结关键决策和当前状态，保存后开启新会话 |
| AI持续犯同样的错 | **明确禁止**：创建 `CONSTRAINTS.md` 文件，列出"绝对不要做的事" |

**上下文压缩模板**：
```markdown
# 上下文总结（用于新会话启动）

## 项目状态
- 当前阶段：Phase 2 - 核心功能开发
- 已完成：Task 2.1, 2.2
- 进行中：Task 2.3 - 用户认证

## 关键决策
- 使用 JWT 而非 Session（原因：无状态、易扩展）
- 密码使用 bcrypt 哈希（原因：安全标准）

## 当前任务
- 目标：实现 Token 刷新机制
- 输入：@auth/jwt_utils.py
- 输出：refresh_token 端点

## 约束
- Token 有效期：15分钟（Access）、7天（Refresh）
- 不使用 Redis（项目约束）
```

**预防措施**：
- 定期（每10-15轮）让AI总结当前状态
- 将关键决策立即写入文件，而非仅在对话中提及
- 使用 `@文件名` 引用外部文件，减少上下文占用
- 对于复杂任务，主动拆分为多个会话

---

### 技巧2：计划决策错误时的修正

当发现**计划的决策方向不对**时，按以下步骤处理：

#### 第一步：立即停止执行
```
用户："暂停当前任务。我发现之前的设计决策有问题。"
```

#### 第二步：明确问题所在
```
用户："问题是：我们选择了 X 方案，但实际上 Y 方案更适合，因为 [具体原因]。"
```

#### 第三步：评估影响范围
```markdown
## 决策变更影响评估

### 原决策
- 选择：使用关系型数据库存储日志
- 原因：查询方便

### 新决策
- 选择：使用时序数据库
- 原因：日志量大，查询模式固定

### 影响范围
- 需要修改：
  - [ ] 数据库连接配置
  - [ ] 日志写入模块
  - [ ] 查询接口
- 可以保留：
  - [x] API 接口设计
  - [x] 前端代码
```

#### 第四步：更新计划文档
- 在 `DECISIONS.md` 中记录变更原因
- 更新 `PLAN.md` 中受影响的任务
- 如有必要，回滚到上一个稳定的 Git 检查点

#### 第五步：重新开始受影响的任务
```
用户："基于新的决策，让我们重新规划 Task 2.3。请先阅读更新后的 PLAN.md。"
```

**关键原则**：
- **越早发现问题越好** - 规划阶段发现问题的修复成本是实现阶段的1/10
- **不要试图"打补丁"** - 如果根本决策错误，重做比修补更高效
- **记录变更原因** - 避免未来再次犯同样的错误

**常见决策错误类型及应对**：

| 错误类型 | 信号 | 应对方式 |
|---------|------|---------|
| 技术选型错误 | 性能问题、兼容性问题频繁出现 | 评估迁移成本 vs 继续使用成本 |
| 架构设计错误 | 每次新功能都需要大量修改 | 识别根本问题，进行重构 |
| 需求理解偏差 | 用户反馈"不是我想要的" | 回到苏格拉底式提问，重新澄清 |
| 过度设计 | 大量代码没有被使用 | 应用 YAGNI，删除冗余 |
| 设计不足 | 频繁打补丁 | 暂停实现，补充设计文档 |

---

### 技巧3：不支持 Skill/Command 时的替代方案

并非所有AI工具都支持 Claude Code 的 skill 和 command 系统。以下是在通用AI工具（如Trae 等）中应用上下文工程的技巧：

#### 3.1 使用提示词模板替代 Skill

**创建项目专用的提示词库**：

```markdown
# prompts/
├── 01-brainstorm.md      # 头脑风暴提示词
├── 02-design-review.md   # 设计审查提示词
├── 03-code-review.md     # 代码审查提示词
├── 04-debug.md           # 调试提示词
└── 05-tdd.md             # TDD提示词
```

**示例：TDD 提示词模板**（替代 TDD skill）
```markdown
# TDD 执行提示词

你现在需要使用测试驱动开发（TDD）方法来实现功能。

## 铁律
- 没有失败的测试，就没有生产代码
- 如果测试立即通过，说明测试写错了

## 执行步骤
1. **红色阶段**：先写一个会失败的测试
2. **运行测试**：确认测试失败，且失败原因是功能未实现（不是语法错误）
3. **绿色阶段**：写最少的代码让测试通过
4. **运行测试**：确认测试通过
5. **重构**：在保持测试通过的前提下优化代码

## 当前任务
[在此描述你要实现的功能]

## 约束
- 不要一次写多个测试
- 不要在测试通过前重构
- 每个步骤完成后告诉我结果
```

#### 3.2 使用文件引用替代 Command

**对话开始时引入上下文**：
```
用户：请先阅读以下文件，理解项目背景：
- @docs/DESIGN.md - 架构设计
- @docs/PLAN.md - 实现计划
- @src/models/user.py - 当前要修改的文件

然后执行 Task 2.3：实现用户认证功能。
```

**技巧**：将常用的引导提示保存为文件
```markdown
# prompts/session-start.md

## 项目上下文
请先阅读以下文件理解项目：
- @README.md - 项目概述
- @docs/DESIGN.md - 架构设计
- @docs/PLAN.md - 当前实现计划

## 工作规范
1. 遵循 TDD：测试先行
2. 小步提交：每个功能完成后提交
3. 文档同步：代码变更同步更新文档

## 当前状态
[在此填写当前进度]
```

#### 3.3 手动实现 Agent 分工

在不支持多 Agent 的工具中，通过**不同对话窗口**模拟 Agent 分工：

| 对话窗口 | 模拟的 Agent | 用途 |
|---------|-------------|------|
| 窗口1 | Setup Agent | 需求分析、计划制定 |
| 窗口2 | Implementation Agent | 具体编码 |
| 窗口3 | Debug Agent | 问题调试 |

**关键**：每个窗口开始时，粘贴对应的"角色提示词"：
```markdown
# 窗口2 开始提示词

你现在是 Implementation Agent，专门负责执行编码任务。

## 你的职责
- 接收任务描述，编写代码
- 遵循 TDD 方法
- 完成后记录工作日志

## 你不应该做的事
- 不要质疑任务本身的合理性（那是 Setup Agent 的工作）
- 不要花超过2次尝试调试问题（委托给 Debug Agent）

## 当前任务
[粘贴从窗口1获得的任务描述]
```

#### 3.4 通用工具的最佳实践总结

| 技巧 | 目的 | 实现方式 |
|------|------|---------|
| 提示词模板 | 替代 Skill | 创建 `prompts/` 目录存放常用提示词 |
| 文件引用 | 替代 Command | 对话开头粘贴 `@文件名` 引用 |
| 多窗口分工 | 替代多 Agent | 不同窗口承担不同角色 |
| 文档记忆 | 替代 Memory Bank | 维护 `docs/memory/` 目录 |
| 会话摘要 | 处理上下文限制 | 每次对话结束时更新 `current-status.md` |

**核心原则**：无论工具是否支持高级功能，上下文工程的核心思想（外部化记忆、专业化分工、显式工作流）都可以通过**文件 + 提示词模板**来实现。

## 六、三种应用场景

### 场景1：小型项目（个人项目、原型）

**特点**：
- 1-2周完成
- 功能相对简单
- 可能只有你一个人

**推荐方法**：
1. **轻量级规划**：
   - 一个README.md说明目标
   - 简单的TODO列表
   - 基本的代码结构

2. **单会话完成小任务**：
   - 不需要复杂的多Agent系统
   - 一个会话处理一个小功能
   - 完成后总结到README

3. **最小化文档**：
   ```markdown
   # README.md
   
   ## 目标
   一个简单的待办事项应用
   
   ## 功能
   - [ ] 添加任务
   - [ ] 标记完成
   - [ ] 删除任务
   
   ## 技术
   - Python + FastAPI
   - SQLite
   
   ## 进展
   - 2024-01-15: 基础框架 ✓
   - 2024-01-16: CRUD API ✓
   - 2024-01-17: 前端界面 (进行中)
   ```

**关键原则**：保持简单，但不要跳过规划步骤。

---

### 场景2：中型项目（团队项目、产品功能）

**特点**：
- 1-3个月完成
- 多个模块或功能
- 2-5个人协作

**推荐方法**（类似Superpowers）：

1. **结构化工作流**：
   ```
   头脑风暴 → 设计确认 → 计划制定 → 迭代实现 → 代码审查
   ```

2. **技能库管理**：
   创建项目特定的SKILLS目录：
   ```
   .skills/
   ├── brainstorming.md
   ├── api-design.md
   ├── database-migration.md
   ├── debugging.md
   └── code-review.md
   ```

3. **专用会话**：
   - 设计会话：讨论架构和接口
   - 实现会话：编写功能代码
   - 审查会话：代码review
   - Debug会话：问题排查

4. **核心文档**：
   ```
   docs/
   ├── DESIGN.md          # 架构设计
   ├── API.md             # API规范
   ├── PLAN.md            # 实现计划
   └── DECISIONS.md       # 重要决策
   ```

5. **Git工作流**：
   ```bash
   # 每个功能一个分支
   git checkout -b feature/user-profile
   
   # 小步提交
   git commit -m "Add profile model"
   git commit -m "Add profile API"
   git commit -m "Add profile tests"
   
   # PR审查后合并
   ```

---

### 场景3：大型项目（企业级、长期项目）

**特点**：
- 3个月以上
- 复杂的多模块系统
- 团队规模5人以上
- 需要长期维护

**推荐方法**（类似APM）：

1. **完整的多Agent系统**：
   ```
   Setup Agent (初始化项目)
      ↓
   Manager Agent (任务协调)
      ↓
   Implementation Agents (并行实现)
      ↓
   Ad-Hoc Agents (专项支持)
   ```

2. **Memory Bank系统**（APM 实际结构）：
   ```
   .apm/
   ├── guides/                           # 方法论指南（7个）
   │   ├── Context_Synthesis_Guide.md    # 需求收集指南
   │   ├── Project_Breakdown_Guide.md    # 任务分解指南
   │   ├── Implementation_Plan_Guide.md  # 计划格式指南
   │   ├── Memory_System_Guide.md        # 记忆系统指南
   │   ├── Memory_Log_Guide.md           # 日志格式指南
   │   └── Task_Assignment_Guide.md      # 任务分配指南
   ├── Memory/
   │   ├── Memory_Root.md                # 项目愿景和技术上下文
   │   └── Phase_XX_<slug>/              # 阶段目录
   │       ├── Task_X_Y_<slug>.md        # 任务级 Memory Log
   │       └── ...
   ├── Assignments/                      # Task Assignment Prompts
   │   └── Phase_XX/
   │       └── Task_X_Y_Assignment.md
   ├── Implementation_Plan.md            # 详细实现计划
   └── Manager_Agent_Bootstrap.md        # Manager 启动提示
   ```
   
   **关键概念**：
   - `Memory_Root.md`：项目愿景、技术栈、验收标准（持久上下文）
   - `Task_X_Y_<slug>.md`：任务执行记录（由 Implementation Agent 填写）
   - `guides/`：标准化的工作方法论（Agent 必须遵循）

3. **Implementation Plan**（APM详细格式）：
   ```markdown
   # <Project Name> – Implementation Plan
   
   **Memory Strategy:** Dynamic-MD (directory structure with Markdown logs)
   **Last Modification:** [由Manager Agent维护]
   **Project Overview:** [项目目标和上下文]
   
   ## Phase 1: Foundation - Agent_Backend, Agent_Frontend
   
   ### Task 1.1 – Project Setup │ Agent_Backend
   - **Objective:** 初始化项目结构和依赖
   - **Output:** 可运行的项目骨架
   - **Guidance:** 使用既定技术栈，遵循项目规范
   
   **Steps:**（多步任务使用有序列表）
   1. 创建目录结构
   2. 配置依赖管理
   3. 设置基础配置
   
   ### Task 1.2 – Database Schema │ Agent_Backend
   - **Objective:** 设计和实现数据库模型
   - **Output:** 完整的数据库迁移脚本
   - **Guidance:** Depends on Task 1.1 Output
   
   **Subtasks:**（单步任务使用无序列表）
   - 定义实体关系
   - 创建迁移文件
   - 编写种子数据
   ```
   
   **关键格式**：
   - 任务标题包含Agent分配：`Task X.Y – Title │ Agent_Domain`
   - 依赖声明：`Depends on Task X.Y Output` 或 `Depends on Task X.Y Output by Agent_Z`（跨Agent）
   - 多步任务用有序列表（1, 2, 3...），单步任务用无序列表（-）

4. **Handover Protocol**（会话交接）：
   ```markdown
   # Handover Document
   
   ## From: Manager Agent (Session #5)
   ## To: Manager Agent (Session #6)
   ## Date: 2024-01-20
   
   ## Context Summary
   - Phase 2 完成70%
   - User management完全实现
   - Content creation部分完成
   
   ## Current State
   Memory Bank位置: /memory/
   Implementation Plan: /PLAN.md (已更新到Task 2.3)
   
   ## Blockers
   - Task 2.2.3需要前端团队输入
   - 性能测试发现查询慢，需要优化
   
   ## Next Actions
   1. 完成Task 2.2.4
   2. 开始Task 2.3准备
   3. 协调前端对接
   
   ## Important Notes
   - 数据库schema在Phase 1被修改，注意依赖
   - JWT过期时间从1小时改为24小时（见ADR-012）
   ```

5. **持续监控和调整**：
   - 每周回顾Memory Bank是否需要更新
   - 定期检查Implementation Plan是否与实际同步
   - 识别常见任务模式，提炼为Skills
   - 监控不同Agent的效率，优化分工

---

## 七、常见陷阱与解决方案

### 陷阱1：过度规划（分析瘫痪）

**表现**：
- 花费数周制定完美的计划
- 文档写了上百页还没开始编码
- 不断修改设计方案

**问题**：
- 浪费宝贵的上下文窗口
- 过早的设计决策可能是错误的
- 实际编码会揭示计划中的问题

**解决方案**：
- 采用"刚刚好"的规划深度
- 小项目：README + TODO列表（30分钟）
- 中项目：设计文档 + 实现计划（1-2天）
- 大项目：分阶段规划，只详细规划下一阶段（1周）
- 记住：计划是用来执行的，不是用来完美的

---

### 陷阱2：规划不足（盲目编码）

**表现**：
- 直接让AI开始写代码
- 没有明确的目标和验收标准
- 频繁返工和重构

**问题**：
- 实现方向错误，浪费时间
- 上下文混乱，AI不知道整体目标
- 难以维护和扩展

**解决方案**：
- 强制执行"规划-确认-执行"流程
- 最小规划清单：
  - [ ] 明确的目标（要实现什么）
  - [ ] 技术选型（用什么技术）
  - [ ] 成功标准（如何判断完成）
  - [ ] 任务分解（分几步完成）
- 用10分钟规划能省1小时编码

---

### 陷阱3：上下文泄漏（职责混淆）

**表现**：
- 在同一个会话里既做规划又做实现又做调试
- AI开始混淆不同阶段的信息
- 对话越长，错误越多

**问题**：
- 上下文污染：调试信息干扰正常实现
- AI认知负担过重，容易出错
- 难以恢复和追溯

**解决方案**：
- 严格遵守"一个会话一个职责"
- 当会话开始偏离主题时，立即开启新会话
- 使用明确的会话初始化提示
- 示例：
  ```markdown
  # 错误（在实现会话中）
  用户："为什么登录功能不工作？"
  （应该开启调试会话，而不是在实现会话中调试）
  
  # 正确
  用户："当前实现会话暂停。我开启调试会话。"
  （开新会话）"这是登录功能的错误日志..."
  ```

---

### 陷阱4：文档陈旧（记忆过期）

**表现**：
- 设计文档和实际代码不一致
- Memory Bank没有更新
- AI基于错误的上下文做决策

**问题**：
- 新会话获得错误信息
- 决策基于过时的假设
- 浪费时间追查差异

**解决方案**：
- 将"更新文档"作为任务的一部分
- 每个任务的标准流程：
  1. 阅读相关文档
  2. 实现代码
  3. 测试
  4. 更新文档 ← 不可跳过
- 定期审查（每周）：
  - README是否准确
  - DESIGN.md是否过时
  - TODO是否同步
- 使用自动化：
  - API文档自动生成
  - 代码注释生成文档
  - 测试覆盖率报告

---

### 陷阱5：过度依赖AI记忆

**表现**：
- "你还记得我上次说的吗？"
- 期望AI记住几小时前的对话细节
- 不提供必要的上下文就提问

**问题**：
- AI会产生幻觉（编造记忆）
- 基于不完整信息做出错误判断
- 浪费轮次来回澄清

**解决方案**：
- **永远假设AI不记得任何事**
- 每次请求都提供必要上下文：
  ```markdown
  # 错误
  用户："修复那个bug"
  
  # 正确
  用户："修复用户登录的bug。
  
  背景：
  - 文件：@auth/login.py
  - 问题：JWT token验证失败
  - 错误信息：'Invalid signature'
  - 相关代码：@auth/jwt_utils.py
  
  请诊断并修复。"
  ```
- 使用文件引用而非口头描述
- 关键信息写入文档，让AI读取而非记忆

---

### 陷阱6：任务粒度过大

**表现**：
- 一个任务包含10+个子步骤
- 任务描述超过一页纸
- 单个会话持续1小时以上

**问题**：
- 失败时难以恢复（不知道哪一步出错）
- AI容易遗漏步骤
- 难以跟踪进度

**解决方案**：
- 使用"2-5分钟规则"：每个任务应在2-5分钟内完成
- 任务分解示例：
  ```markdown
  # 过大的任务
  "实现用户认证系统"
  
  # 正确的分解
  Task 1: "创建User模型（5分钟）"
    - 定义字段
    - 添加验证
    - 编写测试
  
  Task 2: "实现密码哈希功能（3分钟）"
    - 使用bcrypt
    - hash_password函数
    - verify_password函数
  
  Task 3: "实现注册API（5分钟）"
    - POST /auth/register端点
    - 输入验证
    - 返回token
  
  Task 4: "实现登录API（5分钟）"
    - POST /auth/login端点
    - 验证凭证
    - 返回token
  ```
- 每个任务完成后提交代码，创建检查点

---

### 陷阱7：忽略测试

**表现**：
- "先实现功能，测试以后再说"
- 手动测试代替自动化测试
- 没有测试就提交代码

**问题**：
- 无法验证AI是否正确理解需求
- 重构时没有安全网
- Bug累积，难以追溯

**解决方案**：
- 强制执行TDD：
  1. 写测试（必须失败）
  2. 写代码（让测试通过）
  3. 重构（保持通过）
- 每个任务的标准输出包括：
  - 功能代码
  - 单元测试
  - 测试通过的截图/日志
- 将测试作为验收标准：
  ```markdown
  ## Task: 实现用户创建功能
  
  验收标准：
  - [ ] test_create_user_success 通过
  - [ ] test_create_user_duplicate_email 通过
  - [ ] test_create_user_invalid_email 通过
  - [ ] 测试覆盖率 > 80%
  ```

---

### 陷阱8：没有回滚机制

**表现**：
- 直接在main分支上改代码
- 没有Git提交或提交信息太简单
- 改坏了不知道如何恢复

**问题**：
- 无法回到稳定状态
- 难以追踪何时引入bug
- 团队协作混乱

**解决方案**：
- 严格的Git工作流：
  ```bash
  # 1. 为每个任务创建分支
  git checkout -b task/user-registration
  
  # 2. 小步提交（每个子任务一次提交）
  git add models/user.py
  git commit -m "Add User model with email validation"
  
  git add api/auth.py
  git commit -m "Add registration endpoint"
  
  git add tests/test_auth.py
  git commit -m "Add registration tests"
  
  # 3. 测试通过后合并
  git checkout main
  git merge task/user-registration
  
  # 4. 如果出问题，回滚到上一个稳定点
  git revert HEAD
  # 或
  git reset --hard <commit-hash>
  ```
- 提交信息要有意义（说明"做了什么"和"为什么"）
- 使用tag标记重要里程碑：
  ```bash
  git tag -a v0.1.0 -m "User authentication completed"
  ```

---

## 八、实施检查清单

### 项目启动清单

**第一次与AI协作时**：
- [ ] 创建项目目录结构
- [ ] 编写README.md（目标、技术栈、成功标准）
- [ ] 创建DESIGN.md或PLAN.md
- [ ] 设置Git仓库
- [ ] 定义代码规范（CODING_STANDARDS.md）
- [ ] 创建第一个任务列表
- [ ] （可选）创建SKILLS目录或Memory Bank

---

### 任务执行清单

**每次开始一个新任务时**：
- [ ] 任务描述清晰（输入、输出、验收标准）
- [ ] 创建Git分支（如果任务>5分钟）
- [ ] 提供所有必要的上下文给AI
- [ ] 先写测试（TDD）
- [ ] 实现功能
- [ ] 运行测试确认通过
- [ ] 更新相关文档
- [ ] 提交代码（有意义的提交信息）
- [ ] 更新任务状态

---

### 会话管理清单

**开始新AI会话时**：
- [ ] 明确会话的职责（规划/实现/调试/重构）
- [ ] 使用标准化的初始化提示
- [ ] 提供项目核心文档的引用
- [ ] 说明当前任务和上下文
- [ ] 确认AI理解了任务

**会话进行中**：
- [ ] 会话保持单一职责
- [ ] 如果偏离主题，开启新会话
- [ ] 定期让AI总结当前进展
- [ ] 保存重要决策到文档

**会话结束时**：
- [ ] 让AI总结完成的工作
- [ ] 识别遗留问题
- [ ] 如果会话会继续，创建Handover文档
- [ ] 提交所有变更

---

### 定期维护清单

**每周**：
- [ ] 审查README是否准确
- [ ] 审查DESIGN/PLAN是否过时
- [ ] 检查TODO列表是否同步
- [ ] 识别重复的任务模式（可以提炼为Skills）
- [ ] 清理过时的文档和注释

**每个里程碑**：
- [ ] 完整的代码审查
- [ ] 更新架构文档
- [ ] 记录重要决策（ADR）
- [ ] 创建Git tag
- [ ] 团队分享经验教训

---

## 九、快速参考

### 核心原则（记住这5条）

1. **外部化记忆**：不依赖AI记忆，将关键信息写入文档
2. **小批量任务**：每个任务2-5分钟，失败时容易恢复
3. **专业化分工**：一个会话一个职责，避免上下文污染
4. **先规划后执行**：没有计划的编码是浪费上下文
5. **测试驱动**：测试是验证理解的最好方式

---

### 最小可行文档集

**超小项目**（1-2天）：
- README.md（目标 + TODO列表）

**小项目**（1-2周）：
- README.md（项目说明）
- TODO.md（任务列表）
- PLAN.md（实现计划）

**中项目**（1-3月）：
- README.md
- DESIGN.md（架构设计）
- PLAN.md（实现计划）
- API.md（API规范）
- SKILLS/（技能库）

**大项目**（3月+）：
- README.md
- memory/（Memory Bank）
  - 00-overview.md
  - 01-requirements.md
  - 02-architecture.md
  - 03-api-design.md
  - 04-data-model.md
- PLAN.md
- DECISIONS/（ADR记录）
- HANDOVER.md（会话交接）

---

### Agent类型速查（APM）

| Agent类型 | 目的 | 输入 | 输出 | 何时结束 |
|-----------|------|------|------|---------|
| **Setup Agent** | 需求收集、计划制定 | 用户需求、项目约束 | Implementation Plan、Bootstrap Prompt | 用户确认计划 |
| **Manager Agent** | 任务协调、Memory管理 | Implementation Plan、Memory Logs | Task Assignment Prompts、Phase Summaries | 所有阶段完成 |
| **Implementation Agent** | 执行编码任务 | Task Assignment Prompt、依赖上下文 | 代码、测试、Memory Log | 任务完成并记录 |
| **Ad-Hoc Agent (Research)** | 信息收集 | 研究主题、具体问题 | 结构化研究结果（markdown） | 信息收集完成 |
| **Ad-Hoc Agent (Debug)** | 问题调试 | 错误信息、复现步骤、失败尝试 | 诊断结果、修复方案 | 问题解决或升级 |

**委托触发条件**：
- 调试超过2次失败 → 必须委托 Debug Agent
- 需要研究最新文档/最佳实践 → 委托 Research Agent
- 上下文窗口将满 → 触发 Handover 交接新Agent

---

### 任务分解公式

```
大任务 → 子任务（目标：2-5分钟完成）

子任务必须包含：
1. 明确的输入（需要什么文件/信息）
2. 明确的输出（会产生什么）
3. 验收标准（如何判断完成）
4. 依赖关系（依赖哪些其他任务）

示例：
❌ "实现用户管理"（太大）
✅ "创建User模型，包含id、username、email字段，添加email验证"（刚好）
```

---

### 标准化提示模板

**会话初始化**：
```markdown
你是[角色名称]。你的职责是[职责描述]。

## 项目上下文
阅读以下文件：
- @README.md
- @DESIGN.md

## 当前任务
[任务描述]

## 相关文件
- @[文件1]
- @[文件2]

## 执行要求
1. [要求1]
2. [要求2]

请确认你理解了任务。
```

**任务描述**：
```markdown
## Task: [任务标题]

### 背景
[为什么要做这个任务]

### 目标
[要达成什么目标]

### 输入
- 文件：@[文件名]
- 依赖：[依赖任务/功能]

### 输出
- [产出1]
- [产出2]

### 验收标准
- [ ] [标准1]
- [ ] [标准2]
- [ ] 所有测试通过

### 约束
- [约束1]
- [约束2]
```

---

## 十、进阶话题

### 何时需要多Agent系统？

**不需要多Agent**（使用专用会话即可）：
- 项目周期 < 1个月
- 团队规模 < 3人
- 功能相对简单
- 大部分任务串行执行

**需要多Agent**：
- 项目周期 > 3个月
- 需要并行开发多个模块
- 有复杂的依赖关系需要协调
- 需要专门的角色（如持续集成监控Agent）

**实施建议**：
- 先用专用会话（更简单）
- 当发现协调成本高时再引入多Agent
- 不要过早优化

---

### 自动化上下文工程

**可以自动化的部分**：

1. **文档生成**：
   - API文档（Swagger/OpenAPI）
   - 代码文档（Sphinx/JSDoc）
   - 测试报告

2. **任务追踪**：
   - 从Git commits提取任务进展
   - 自动更新TODO状态
   - 生成进度报告

3. **上下文管理**：
   - 自动提取相关代码片段
   - 生成依赖关系图
   - 检测文档过期

**工具推荐**：
- GitHub Actions（CI/CD）
- pre-commit hooks（代码检查）
- 文档生成工具（mkdocs, Sphinx）
- 项目管理工具（Jira, Linear）

---

### 团队协作中的上下文工程

**挑战**：
- 多人同时开发
- 知识在不同人之间传递
- AI会话无法共享

**解决方案**：

1. **统一的文档系统**：
   - 所有人遵循相同的文档结构
   - 使用wiki或内部文档系统
   - 定期同步

2. **标准化的工作流**：
   - 团队约定统一的分支策略
   - 代码审查流程
   - 发布流程

3. **知识共享机制**：
   - 定期的设计讨论
   - 代码walk-through
   - 经验教训总结会

4. **AI会话的团队使用**：
   - 关键决策的AI对话记录下来（复制到文档）
   - 共享有用的提示模板
   - 建立团队的Skills库

---

## 十一、总结

上下文工程的核心是**管理AI的"工作记忆"**，让有限的上下文窗口发挥最大价值。

**关键认知转变**：
- 从"AI是全知的助手" → "AI是健忘的天才"
- 从"自然对话" → "结构化协作"
- 从"一次性任务" → "可持续的项目"

**成功的标志**：
- 新AI会话能快速上手
- 项目信息不会随时间丢失
- 任务失败时容易恢复
- 团队成员能理解AI的决策
- 代码质量持续提升

**记住**：
上下文工程不是增加复杂度，而是**将复杂度显式化、结构化，让它可管理**。

就像人类团队需要文档、流程和沟通机制一样，与AI协作也需要这些。唯一的区别是，AI的"记忆"更短，所以我们需要更加显式和结构化。

**最后的建议**：
- 从小处开始（README + TODO列表）
- 根据项目复杂度渐进式增加结构
- 不要为了"完美"而陷入过度工程
- 记录你的经验教训，建立自己的实践库

祝你与AI协作愉快！🚀

---

## 附录：参考资源

### 开源项目
- **APM (Agentic Project Management)**: https://github.com/sdi2200262/agentic-project-management
  - 多Agent项目管理框架（Setup → Manager → Implementation → Ad-Hoc）
  - Dynamic-MD Memory系统（Memory_Root + Phase目录 + Task Memory Logs）
  - 完整的guides系统（7个详细方法论指南）
  - 适合大型长期项目（3个月+）

- **Superpowers**: https://github.com/obra/superpowers
  - Claude Code技能库（20+ battle-tested skills）
  - 核心命令：`/brainstorm`、`/write-plan`、`/execute-plan`
  - 关键技能：TDD、Root Cause Tracing、Systematic Debugging
  - 适合快速开发和技能标准化

### 本项目使用的结构
```
.apm/                          # APM项目管理目录
├── guides/                    # 方法论指南
├── Memory/                    # 项目记忆
├── Assignments/               # 任务分配
└── Implementation_Plan.md     # 实现计划

.claude/commands/              # Claude Code命令，trae 可以直接将文档拖拽到agent对话里，也是一样的。
├── apm-1-initiate-setup.md    # Setup Agent启动
├── apm-2-initiate-manager.md  # Manager Agent启动
├── apm-3-initiate-implementation.md  # Implementation Agent启动
├── apm-4-initiate-adhoc.md    # Ad-Hoc Agent启动，负责任务调试
└── apm-7/8-delegate-*.md      # 委托机制
```

### 相关概念
- **TDD (Test-Driven Development)**: 测试驱动开发 - 红绿重构循环
- **ADR (Architecture Decision Record)**: 架构决策记录 - 记录"为什么"
- **YAGNI (You Aren't Gonna Need It)**: 不要过度设计
- **DRY (Don't Repeat Yourself)**: 不要重复
- **Anti-Packing**: 防止任务过度打包的5个测试

### 进一步学习
- **提示工程（Prompt Engineering）**: 如何编写更有效的提示词
- **软件架构设计**: 系统设计原则和模式
- **敏捷开发方法**: Scrum、Kanban等迭代开发方法
- **文档驱动开发**: 先写文档再写代码的方法论

### 常见问题FAQ

**Q: 这套方法适用于所有AI工具吗？**
A: 核心思想（外部化记忆、专业化分工、显式工作流）是通用的。具体实现可以根据工具能力调整，参见"技巧3：不支持 Skill/Command 时的替代方案"。

**Q: 小项目也需要这么复杂的流程吗？**
A: 不需要。参见"快速入门（5分钟上手）"和"场景1：小型项目"。核心是根据项目复杂度渐进式增加结构。

**Q: 如何处理AI生成的错误代码？**
A: 首先确保需求和约束已经明确写入文档。如果问题持续，参见"技巧1：上下文混乱时的恢复"和"技巧2：计划决策错误时的修正"。

**Q: 多人协作时如何使用这套方法？**
A: 关键是统一文档结构和工作流程。参见"十、进阶话题"中的"团队协作中的上下文工程"。

---

**版本**: 1.2.0  
**最后更新**: 2026年1月  
**贡献者**: 基于APM和Superpowers项目的实践总结  
**修订说明**: 
- 1.0.0: 初始版本
- 1.1.0: 修正SKILL.md格式、补充完整APM Agent体系、更新Memory Bank结构
- 1.2.0: 添加快速入门、目录索引、进阶技巧（上下文恢复、决策修正、通用工具替代方案、模型适配）
