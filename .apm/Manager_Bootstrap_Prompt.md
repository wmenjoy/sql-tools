---
Workspace_root: /Users/liujinliang/workspace/ai/sqltools
Bootstrap_date: 2025-12-19
Implementation_plan: .apm/Implementation_Plan.md
Memory_bank: .apm/Memory/
Current_phase: Phase 11
Phase_status: Ready to Start
---

# Manager Agent Bootstrap Prompt

You are taking over as **Manager Agent** for the SQL Guard project. This Bootstrap Prompt provides complete context for orchestrating Phase 11 implementation.

---

## 1. Project Context

### Project Overview
**SQL Guard** is an enterprise-grade SQL safety validation framework for Java applications, providing:
- Runtime SQL safety validation (MyBatis/MyBatis-Plus/JDBC interceptors)
- Static code scanning (XML mappers, annotations, QueryWrapper)
- Dual-layer audit platform (runtime monitoring + historical analysis)
- Multi-database dialect support (MySQL/Oracle/SQL Server/PostgreSQL)

### Current Development Status
- ✅ **Phases 1-10 COMPLETED**: Core validation engine, static scanner, runtime interceptors, audit platform foundation
- 🎯 **Phase 11 READY TO START**: JDBC Module Separation
- 📋 **Phases 12-13 PLANNED**: Core architecture unification, InnerInterceptor architecture

### Architecture Review Context
Phase 11-13 plan is based on systematic architecture review (see `.apm/Memory/Phase_11_Reference_Architecture/Architecture_Review_Report.md`). Original Phase 11 was split into 3 independent phases to:
- Reduce risk through incremental delivery
- Enable independent phase validation
- Improve time estimation accuracy (45 days vs original 19 days)

---

## 2. Implementation Plan

### Location
**Primary Plan**: `.apm/Implementation_Plan.md` (774 lines, 26 tasks across 3 phases)

### Plan Structure
```
Phase 11: JDBC Module Separation (10 days, 6 tasks)
  - Agent_Core_Engine_Foundation: Tasks 11.2-11.5
  - Agent_Testing_Validation: Tasks 11.1, 11.6

Phase 12: Core Architecture Unification (15 days, 11 tasks)
  - Agent_Architecture_Refactoring: Tasks 12.1-12.9
  - Agent_Testing_Validation: Tasks 12.10-12.11

Phase 13: InnerInterceptor Architecture (20 days, 9 tasks)
  - Agent_Advanced_Interceptor: Tasks 13.1-13.8
  - Agent_Testing_Validation: Task 13.9
```

### Plan Quality
- ✅ **APM Systematic Review COMPLETED**: All 26 tasks reviewed, 7 fixes applied
- ✅ **TDD Methodology**: 300+ test cases designed across all tasks
- ✅ **Acceptance Criteria**: Each task has measurable validation criteria
- ✅ **Test Matrices**: Detailed test specifications for TDD implementation

---

## 3. Phase 11: JDBC Module Separation

### Phase Objective
Extract `sql-guard-jdbc` into independent connection pool modules following minimal dependency principle, enabling users to include only needed pool modules without dependency pollution, maintaining 100% backward compatibility.

### Phase Constraints (CRITICAL)
**DO** ✅:
- Module structure refactoring
- Dependency isolation
- Code extraction to specialized modules

**DON'T** ❌:
- RuleChecker refactoring (belongs to Phase 12)
- SqlContext changes (belongs to Phase 12)
- StatementVisitor introduction (belongs to Phase 12)
- InnerInterceptor implementation (belongs to Phase 13)

### Tasks Overview

**Task 11.1** – TDD Test Case Library Design (2 days, Agent_Testing_Validation)
- Design 40+ test cases for module separation
- Create test fixtures and specifications
- Output: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md`

**Task 11.2** – JDBC Common Module Extraction (2 days, Agent_Core_Engine_Foundation)
- Extract `sql-guard-jdbc-common` module
- Unify ViolationStrategy enum (eliminate 3 duplicates)
- Create JdbcInterceptorBase template method pattern
- 30+ tests designed

**Task 11.3** – Druid Module Separation (2 days, Agent_Core_Engine_Foundation)
- Create `sql-guard-jdbc-druid` module
- Druid-specific Filter chain mechanism
- 25+ tests designed

**Task 11.4** – HikariCP Module Separation (2 days, Agent_Core_Engine_Foundation)
- Create `sql-guard-jdbc-hikari` module
- Multi-layer JDK Dynamic Proxy pattern
- Support HikariCP 4.x and 5.x
- 25+ tests designed

**Task 11.5** – P6Spy Module Separation (2 days, Agent_Core_Engine_Foundation)
- Create `sql-guard-jdbc-p6spy` module
- SPI-based ServiceLoader discovery
- Universal JDBC interception fallback
- 25+ tests designed

**Task 11.6** – Integration Testing & Performance Verification (2 days, Agent_Testing_Validation)
- Execute comprehensive integration tests
- Verify backward compatibility 100%
- Performance regression testing
- Generate acceptance report
- 20+ tests designed

### Success Criteria
- [ ] All 4 modules (common, druid, hikari, p6spy) compile independently
- [ ] Users can include only needed modules without transitive dependencies
- [ ] All existing tests pass 100%
- [ ] No performance regression (< 110% baseline)
- [ ] Acceptance report documents complete validation

---

## 4. Agent Assignments

### Agent Roles for Phase 11

**Agent_Core_Engine_Foundation**:
- **Responsibility**: Module extraction and refactoring
- **Tasks**: 11.2, 11.3, 11.4, 11.5
- **Skill Domain**: Maven module structure, dependency management, template method pattern, Spring Boot auto-configuration

**Agent_Testing_Validation**:
- **Responsibility**: TDD test design and validation
- **Tasks**: 11.1, 11.6
- **Skill Domain**: Test design, JUnit/Mockito, integration testing, performance benchmarking (JMH)

### Parallel Execution Opportunities
Tasks 11.3, 11.4, 11.5 can execute in parallel (independent module implementations) after Task 11.2 completes.

---

## 5. Memory Bank Structure

### Phase 11 Memory Directory
Create `.apm/Memory/Phase_11_JDBC_Module_Separation/` when phase starts.

### Required Memory Files
- Task completion summaries
- Technical decision logs
- Test execution reports
- Performance benchmark data

### Phase Summary
Create phase summary when Phase 11 completes documenting:
- Acceptance criteria validation
- Lessons learned
- Handoff notes for Phase 12

---

## 6. Reference Documentation

### Architecture References
- **Architecture Review**: `.apm/Memory/Phase_11_Reference_Architecture/Architecture_Review_Report.md`
- **Restructure Plan**: `.apm/Memory/Phase_11_Reference_Architecture/Implementation_Plan_Phase_11_12_13_Restructure.md`
- **Module Separation Strategy**: `.apm/Memory/Phase_11_Reference_Architecture/Module_Separation_And_Version_Compatibility.md`

### Existing Phase 11 Analysis
- **Detailed Plan**: `.apm/Assignments/Phase_11/Phase_11_JDBC_Module_Separation.md`
- **Overview**: `.apm/Assignments/Phase_11/Phase_11_Overview.md`

### APM Guides
- **Project Breakdown Guide**: `.apm/guides/Project_Breakdown_Guide.md`
- **Review Guide**: `.apm/guides/Project_Breakdown_Review_Guide.md`

---

## 7. Immediate Next Steps

### Step 1: Confirm Context Understanding
Review this Bootstrap Prompt and confirm:
- [ ] Implementation Plan location understood
- [ ] Phase 11 scope and constraints clear
- [ ] Agent assignment strategy understood
- [ ] Memory bank structure requirements clear

### Step 2: Create Phase 11 Memory Directory
```bash
mkdir -p .apm/Memory/Phase_11_JDBC_Module_Separation
```

### Step 3: Begin Task Assignment Loop
**First Task**: Task 11.1 – TDD Test Case Library Design
- **Assign to**: Agent_Testing_Validation
- **Priority**: CRITICAL (blocks all other tasks)
- **TDD Requirement**: Design tests BEFORE implementation
- **Expected Output**: `.apm/Assignments/Phase_11/Task_11_1_Test_Design.md`

### Step 4: Read Task Details
Read Implementation Plan Task 11.1 section (lines 19-42) to create detailed task assignment prompt for Agent_Testing_Validation.

---

## 8. Quality Standards

### TDD Methodology (MANDATORY)
- Tests MUST be designed before implementation
- Test matrices define specific test method names
- Acceptance criteria must be measurable
- Behavior verification tests compare old vs new implementation

### Backward Compatibility (100%)
- All existing tests must pass
- No breaking API changes
- Deprecated APIs must still function
- Migration guide for new APIs

### Performance Standards
- Module loading: < 10ms overhead
- Runtime performance: < 110% baseline
- Memory usage: no increase
- All benchmarks documented with JMH

### Documentation Requirements
- Javadoc for all public APIs
- Migration guides for deprecated APIs
- Technical decision logs in Memory Bank
- Acceptance reports for validation

---

## 9. Risk Management

### Phase 11 Risks (LOW)

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Module dependency conflicts | Low | Medium | Maven Enforcer Plugin validation |
| Backward compatibility break | Low | High | 100% test coverage requirement |
| Performance regression | Low | Medium | JMH benchmark baseline |
| Incomplete test coverage | Medium | Medium | Test design review before implementation |

### Escalation Protocol
If critical issues arise:
1. Document in Memory Bank
2. Pause task execution
3. Request user guidance
4. Update Implementation Plan if scope changes

---

## 10. Operating Instructions

### Read Implementation Plan
```
Action: Read .apm/Implementation_Plan.md
Purpose: Access full task details, test matrices, acceptance criteria
```

### Create Task Assignment Prompts
For each task, create assignment prompt including:
- Task objective and output
- Guidance section (implementation approach)
- Complete test matrix
- Acceptance criteria
- Reference to relevant Memory Bank documents

### Monitor Progress
- Track task completion status
- Verify acceptance criteria met
- Review implementation agent outputs
- Update Memory Bank with decisions

### Coordinate Parallel Tasks
- Tasks 11.3, 11.4, 11.5 can run in parallel
- Ensure Task 11.2 completes first (dependency)
- Aggregate results before Task 11.6

---

## 11. Success Metrics

### Phase 11 Completion Criteria
- [ ] All 6 tasks completed with acceptance criteria met
- [ ] 4 independent modules (common, druid, hikari, p6spy) created
- [ ] 145+ tests implemented and passing (40+30+25+25+25+20)
- [ ] Performance benchmarks show no regression
- [ ] Acceptance report documents 100% backward compatibility
- [ ] Phase summary created in Memory Bank
- [ ] Ready for Phase 12 handoff

### Quality Gates
- [ ] All test matrices implemented
- [ ] Maven Enforcer validates dependencies
- [ ] Integration tests pass on clean repository
- [ ] Performance comparison data documented
- [ ] No deprecated API functionality broken

---

## Your First Action

**IMMEDIATE TASK**:
1. Read Implementation Plan: `.apm/Implementation_Plan.md` (focus on Phase 11 section, lines 9-176)
2. Create Phase 11 Memory directory: `.apm/Memory/Phase_11_JDBC_Module_Separation/`
3. Prepare Task 11.1 assignment prompt for Agent_Testing_Validation

Confirm receipt of this Bootstrap Prompt and state your understanding of:
- Phase 11 objectives
- Task assignment strategy
- First task to execute (Task 11.1)

Then proceed with creating the Phase 11 memory directory and Task 11.1 assignment.

---

**Bootstrap Context Complete. Manager Agent Ready to Orchestrate Phase 11.**
