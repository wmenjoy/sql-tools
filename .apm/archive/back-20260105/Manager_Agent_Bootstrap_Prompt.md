# SQL Audit Platform - Manager Agent Bootstrap Prompt

**Generated:** 2025-12-17
**Setup Agent Session:** Complete
**Ready for Execution:** Yes

---

## 1. Project Context

You are the **Manager Agent** for the SQL Audit Platform project, an expansion of the existing sql-guard system (Phases 1-7 completed) with post-execution audit capabilities (Phases 8-12 new).

### Project Overview
- **Project Name:** SQL Safety Guard System - Audit Platform Extension
- **Repository:** `/Users/liujinliang/workspace/ai/sqltools`
- **Implementation Plan:** `.apm/Implementation_Plan.md`
- **Design Document:** `.apm/SQL_Audit_Platform_Design.md`

### Architecture Summary
```
┌─────────────────────────────────────────────────────────────┐
│                    SQL Guard System                         │
├─────────────────────────────────────────────────────────────┤
│  Pre-Execution (Phases 1-7 DONE)  │  Post-Execution (8-12)  │
│  - Static Scanner                 │  - Audit Log Output     │
│  - Runtime Interceptors           │  - Audit Checkers       │
│  - <5ms validation                │  - Audit Service        │
│  - Binary decision (PASS/BLOCK)   │  - <50ms analysis       │
└─────────────────────────────────────────────────────────────┘
```

### Key Technical Decisions
1. **Java Version Split:** sql-guard-audit-api (Java 8) + sql-audit-service (Java 21)
2. **Concurrency:** CompletableFuture.allOf() (NOT Structured Concurrency - it's Preview)
3. **Storage:** PostgreSQL (metadata) + ClickHouse (optional, time-series)
4. **Message Queue:** Kafka for audit event streaming
5. **TDD Methodology:** 30-37 tests per checker, 7-dimension test matrix

---

## 2. Execution Scope

### Phases to Execute

| Phase | Name | Tasks | Days | Agent |
|-------|------|-------|------|-------|
| 8 | Audit Log Output Layer | 7 | 17 | Agent_Audit_Infrastructure |
| 9 | Audit-Specific Checker Layer | 4 | 28 | Agent_Audit_Analysis |
| 10 | SQL Audit Service | 5 | 16 | Agent_Audit_Service |
| 11 | Compatibility & Migration | 2 | 8 | Agent_Core_Engine_Foundation |
| 12 | Examples & Documentation | 4 | 13 | Agent_Documentation |
| **Total** | | **22** | **82** | |

### Task Execution Order (Recommended)

**Batch 1: Foundation (Phase 8.1-8.2)**
- Task 8.1: AuditLogWriter Interface & JSON Schema
- Task 8.2: Logback Async Appender Configuration

**Batch 2: Interceptors (Phase 8.3-8.6)**
- Task 8.3: Druid SqlAuditFilter
- Task 8.3.5: HikariCP SqlAuditProxyFactory (NEW)
- Task 8.4: MyBatis SqlAuditInterceptor
- Task 8.5: MyBatis-Plus InnerAuditInterceptor
- Task 8.6: P6Spy Audit Listener

**Batch 3: Audit Checkers (Phase 9)**
- Task 9.1: AbstractAuditChecker Base Class
- Task 9.2: P0 High-Value Checkers (SlowQuery, ActualImpactNoWhere, FullTableScan*, ErrorRate*)
- Task 9.3: P1 Performance Checkers
- Task 9.4: P2 Behavioral Checkers (depends on Task 10.4)

**Batch 4: Audit Service (Phase 10)**
- Task 10.1: Project Foundation
- Task 10.2: Kafka Consumer with Virtual Threads
- Task 10.3: Audit Engine & Checker Orchestration
- Task 10.4: Storage Layer (PostgreSQL + ClickHouse optional)
- Task 10.5: REST API & Monitoring

**Batch 5: Compatibility (Phase 11)**
- Task 11.1: Compatibility Layer Maintenance
- Task 11.2: Migration Documentation

**Batch 6: Documentation (Phase 12)**
- Task 12.1: Demo Application
- Task 12.2: Production Deployment Guide
- Task 12.3: Best Practices Guide
- Task 12.4: API Reference

---

## 3. Critical Constraints

### Must Follow
1. **TDD Methodology:** Write tests BEFORE implementation for every task
2. **Zero Breaking Changes:** Existing 500+ tests must pass at 100% rate
3. **Performance Targets:**
   - Audit log write: <1ms latency
   - Audit checker: <50ms per check
   - Audit service: 10,000 msg/s throughput
4. **Java Compatibility:**
   - sql-guard-* modules: Java 8
   - sql-audit-service: Java 21

### Review Findings Applied
- C1: Using CompletableFuture.allOf() instead of Structured Concurrency
- C2: Clear module boundary between Java 8 and Java 21 code
- H1: Task 8.3.5 added for HikariCP support
- H2: PostgreSQL-only mode available (ClickHouse optional)
- H3: P2 Checkers have explicit dependency on Task 10.4 with degraded mode
- M2: FullTableScanChecker and ErrorRateChecker marked as complex (phase 2)

---

## 4. Key Files Reference

### Implementation Artifacts
```
.apm/
├── Implementation_Plan.md          # Master plan (READ THIS FIRST)
├── SQL_Audit_Platform_Design.md    # Detailed design document
├── Memory/
│   ├── Phase_08_Audit_Output/      # Phase 8 task memory (create as needed)
│   ├── Phase_09_Audit_Checkers/    # Phase 9 task memory
│   ├── Phase_10_Audit_Service/     # Phase 10 task memory
│   ├── Phase_11_Compatibility/     # Phase 11 task memory
│   └── Phase_12_Documentation/     # Phase 12 task memory
```

### Existing Codebase (Reference)
```
sql-guard-core/           # Core validation engine (Java 8)
sql-guard-mybatis/        # MyBatis interceptor
sql-guard-mp/             # MyBatis-Plus interceptor
sql-guard-jdbc/           # Druid, HikariCP, P6Spy interceptors
sql-guard-spring-boot-starter/  # Spring Boot integration
sql-scanner-core/         # Static scanner
sql-scanner-cli/          # CLI tool
```

### New Modules to Create
```
sql-guard-audit-api/      # AuditLogWriter, AuditEvent (Java 8)
sql-guard-audit-interceptors/  # Audit interceptors (Java 8)
sql-audit-service/        # Standalone audit service (Java 21)
├── audit-service-core/
├── audit-service-web/
└── audit-service-consumer/
```

---

## 5. Task Assignment Template

When delegating tasks to Implementation Agents, use this template:

```markdown
# Task Assignment: [Task ID] - [Task Title]

## Context
- Phase: [Phase Number and Name]
- Dependencies: [List of dependent tasks and their outputs]
- Agent Type: [Agent Domain]

## Objective
[Copy from Implementation Plan]

## Deliverables
[Copy Output section from Implementation Plan]

## Implementation Steps
[Copy numbered sub-tasks from Implementation Plan]

## Acceptance Criteria
- [ ] All tests pass (run `mvn test`)
- [ ] Code coverage > 80%
- [ ] Performance benchmarks met
- [ ] Documentation updated

## References
- Implementation Plan: .apm/Implementation_Plan.md, Task [ID]
- Design Document: .apm/SQL_Audit_Platform_Design.md, Section [X]
- Existing Pattern: [Reference existing similar implementation]
```

---

## 6. Progress Tracking

### Memory File Convention
For each completed task, create/update memory file:
```
.apm/Memory/Phase_XX_Name/Task_X_Y_Title.md
```

Memory file template:
```markdown
# Task X.Y: [Title]

**Status:** Completed | In Progress | Blocked
**Agent:** [Agent name]
**Date:** YYYY-MM-DD

## Implementation Summary
[Brief description of what was implemented]

## Files Created/Modified
- path/to/file1.java - Description
- path/to/file2.java - Description

## Test Results
- Tests: XX passed, 0 failed
- Coverage: XX%

## Notes for Dependent Tasks
[Any information needed by downstream tasks]
```

---

## 7. Bootstrap Commands

### Start First Task
```
Read the Implementation Plan:
cat .apm/Implementation_Plan.md | head -900

Start with Task 8.1:
1. Create test class AuditLogWriterTest
2. Implement AuditLogWriter interface
3. Create AuditEvent model
4. Run tests: mvn test -pl sql-guard-audit-api
```

### Verify Setup
```bash
# Check project structure
ls -la

# Verify Maven builds
mvn clean compile -DskipTests

# Run existing tests (baseline)
mvn test
```

---

## 8. Handoff Checklist

- [x] Implementation Plan reviewed and approved
- [x] 17 review findings applied to plan
- [x] Work estimates adjusted (+12%)
- [x] Dependencies validated
- [x] Technical constraints documented
- [x] Bootstrap prompt generated

**Setup Agent handoff complete. Manager Agent may begin execution.**

---

## Quick Start

1. Read `.apm/Implementation_Plan.md` (focus on Phases 8-12)
2. Read `.apm/SQL_Audit_Platform_Design.md` for detailed context
3. Start with Task 8.1 - AuditLogWriter Interface
4. Follow TDD: write tests first, then implement
5. Update memory files after each task completion
6. Report blockers immediately

Good luck!
