---
Workspace_root: /Users/liujinliang/workspace/ai/sqltools
---

# Manager Agent Bootstrap Prompt
You are the first Manager Agent of this APM session: Manager Agent 1.

## User Intent and Requirements

**Primary Objective:** Implement 11 missing RuleCheckers to make SQL Guard an independent, complete SQL security framework with production-quality standards.

**Scope:**
- ALL 11 Checkers must be implemented (optimized from 17 original requirements)
- Group 1 (SQL Injection): MultiStatementChecker, SetOperationChecker, SqlCommentChecker, IntoOutfileChecker
- Group 2 (Dangerous Operations): DdlOperationChecker, DangerousFunctionChecker, CallStatementChecker
- Group 3 (Access Control): MetadataStatementChecker, SetStatementChecker, DeniedTableChecker, ReadOnlyTableChecker

**Methodology Requirements:**
- TDD approach: Test cases first (PASS≥5, FAIL≥10, 边界≥3), then implementation
- No priority consideration: Use most suitable method, parallel execution
- Production standards: No shortcuts, all code meets production quality
- Configuration consistency: Follow first principles (user-friendly, intuitive, simple)

**Deliverables per Checker:**
1. Production-ready Checker.java extending AbstractRuleChecker
2. Config.java with YAML binding support
3. Comprehensive test coverage (≥18 tests, >80% coverage, ≥3 dialects)
4. User documentation following template structure
5. Integration with DefaultSqlSafetyValidator and RuleCheckerOrchestrator

**Additional Deliverables:**
- Example code updates (bad/good mapper files for all 11 Checkers)
- Integration test scenarios (CheckerIntegrationTest.java with programmatic 7-item checklist verification)

**Quality Control:**
- Manager Agent automatic validation using 7-item acceptance checklist
- Programmatic verification (JaCoCo, JMH, SnakeYAML, ServiceLoader)
- Multi-dialect support (MySQL, Oracle, PostgreSQL minimum)
- Performance requirement: <5ms per Checker

## Implementation Plan Overview

**Structure:** Single phase with 15 tasks across 3 specialized agents

**Agent Assignments:**
- **Agent_SQL_Injection:** Tasks 1.1-1.4 (SQL Injection Checkers) + Task 1.12a (Examples)
- **Agent_Dangerous_Operations:** Tasks 1.5-1.7 (Dangerous Operations Checkers) + Task 1.12b (Examples)
- **Agent_Access_Control:** Tasks 1.8-1.11 (Access Control Checkers) + Task 1.12c (Examples) + Task 1.13 (Integration Tests)

**Execution Strategy:**
- Tasks 1.1-1.11: Fully parallel (no inter-dependencies)
- Tasks 1.12a/b/c: Depend on respective agent's Checker implementations (parallel across agents)
- Task 1.13: Depends on all Checkers (final integration task)

**Technical Foundation (Phase 12 Complete):**
- AbstractRuleChecker with Template Method + Visitor pattern
- SqlContext with statement field for parse-once optimization
- StatementVisitor pattern (visitSelect, visitUpdate, visitDelete, visitInsert)
- JSqlParser AST for SQL analysis

**High-Complexity Tasks (6-step multi-step breakdown):**
- Task 1.3: SqlCommentChecker (JSqlParser comment extraction research required)
- Task 1.6: DangerousFunctionChecker (recursive ExpressionVisitor traversal)
- Task 1.10: DeniedTableChecker (recursive FromItemVisitor + wildcard matching)

**Critical Implementation Details:**
- Task 1.10: Wildcard pattern sys_* → ^sys_[^_]*$ (NOT ^sys_.*$ which matches "system")
- Task 1.3: SQL string parsing fallback if JSqlParser doesn't expose comments (~1-2ms overhead)
- Task 1.6: ExpressionVisitor recursion safety via Set<Expression> visitedSet (~2-3ms overhead)

## Next steps for the Manager Agent

Follow this sequence exactly. Steps 1-10 in one response. Step 11 after explicit User confirmation:

**Plan Responsibilities & Project Understanding**
1. Read .apm/guides/Implementation_Plan_Guide.md
2. Read the entire `.apm/Implementation_Plan.md` file created by Setup Agent:
   - Evaluate plan's integrity based on the guide and propose improvements **only** if needed
3. Confirm your understanding of the project scope, phases, and task structure & your plan management responsibilities

**Memory System Responsibilities**
4. Read .apm/guides/Memory_System_Guide.md
5. Read .apm/guides/Memory_Log_Guide.md
6. Read the `.apm/Memory/Memory_Root.md` file to understand current memory system state
7. Confirm your understanding of memory management responsibilities

**Task Coordination Preparation**
8. Read .apm/guides/Task_Assignment_Guide.md
9. Confirm your understanding of task assignment prompt creation and coordination duties

**Execution Confirmation**
10. Summarize your complete understanding and **AWAIT USER CONFIRMATION** - Do not proceed to phase execution until confirmed

**Execution**
11. When User confirms readiness, proceed as follows:
   a. Read the first phase from the Implementation Plan.
   b. Create `Memory/Phase_01_Complete_Security_Checker_Implementation/` in the `.apm/` directory for the first phase.
   c. For all tasks in the first phase, create completely empty `.md` Memory Log files in the phase's directory.
   d. Once all empty logs/sections exist, issue the first Task Assignment Prompt.
