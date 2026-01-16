---
name: coverage-analyzer
description: "Analyzes test coverage and identifies gaps. Use when user mentions coverage percentage, asks what tests are missing, or wants to improve/increase test coverage."
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
  - Grep
---

# Coverage Analyzer

## Overview

Coverage analysis identifies what code is tested and what isn't. But raw coverage numbers are meaningless without understanding WHY code is uncovered and WHAT to do about it.

**Core principle:** Evidence-based gap analysis before test generation. Always verify coverage improvements.

**Violating the letter of this process is violating the spirit of testing.**

## The Iron Laws

```
1. NEVER claim coverage improvement without running fresh coverage
2. NEVER generate tests without analyzing WHY code is uncovered
3. NEVER skip verification after adding tests
```

## Definition: Fresh Coverage

**Fresh Coverage = Measured within 60 minutes on current branch.**

**You MUST re-run coverage if:**
- Measurement older than 60 minutes
- ANY code commits since measurement
- Branch changed since measurement
- Tests added/modified since measurement
- Different environment/config since measurement

**"Fresh" does NOT mean:**
- ❌ "From this morning" (too old)
- ❌ "From yesterday's CI run" (too old)
- ❌ "From Monday when I started work" (way too old)
- ❌ "Recent enough for planning" (not measured = not fresh)

**ABSOLUTE REQUIREMENT:** Run fresh coverage before EVERY analysis. You cannot analyze, estimate, project, or report on coverage without measured data from current code state. No exceptions, no estimates, no "good enough" old data.

## VIOLATIONS (Automatic Skill Restart Required)

**Any of these = skill misuse, start over at Phase 1:**

❌ **Reporting coverage without running tests**
   - Example: "Coverage is probably 75% based on code review"
   - Violation: No measured data

❌ **Using old coverage data**
   - Example: "Monday's 72% coverage still applies"
   - Violation: Measurement older than 60 minutes

❌ **Estimating instead of measuring**
   - Example: "Estimated coverage: ~77%"
   - Violation: "Estimated", "Projected", "Likely" = not measured

❌ **Skipping Phase 1 data collection**
   - Example: "I'll skip to gap analysis using old data"
   - Violation: Must complete Phase 1 first, always

❌ **Reporting improvements without before/after evidence**
   - Example: "Coverage improved by ~5%"
   - Violation: Need measured before AND after

❌ **Conditional/future tense coverage claims**
   - Example: "Coverage should improve", "will reach 80%"
   - Violation: Only present tense with evidence allowed

**If you violate:** Reset to Phase 1. No shortcuts.

## When to Use This Skill

**Use this skill when:**
- User mentions "coverage" or "test coverage"
- User asks "what tests am I missing"
- User wants to "improve coverage" or "increase coverage"
- User shares coverage reports or asks about coverage gaps
- User wants to reach a specific coverage target
- After implementing new features (verify coverage)
- Before claiming implementation is complete

**Don't use for:**
- Initial test generation for new code (use TDD instead)
- Code that will be deleted
- Throwaway prototypes

## Capabilities

### 1. Coverage Data Collection
- Run coverage tools for different languages
- Parse coverage reports (lcov, cobertura, go cover, etc.)
- Identify uncovered lines and branches

### 2. Gap Analysis
- Categorize uncovered code
- Prioritize by importance (critical vs utility)
- Identify patterns in coverage gaps

### 3. Root Cause Analysis
- Why is code uncovered?
  - Missing test case
  - Dead code
  - Hard to test code
  - Error handling paths

### 4. Test Generation
- Generate tests specifically for uncovered code
- Focus on high-value coverage improvements
- Suggest refactoring for hard-to-test code

## The Coverage Analysis Process

### Phase 1: Collect Fresh Coverage Data

**ABSOLUTE REQUIREMENT (Non-Negotiable):**

You MUST run fresh coverage before EVERY analysis session. This means:
- Run full test suite with coverage measurement NOW
- Measurement must be < 60 minutes old
- No estimates, projections, or conditional predictions
- No "good enough" old data from yesterday/this morning/Monday
- No skipping Phase 1 to "save time"

**You CANNOT:**
- Analyze coverage without running tests
- Estimate coverage based on code review
- Project coverage from old baseline
- Combine old measurements with new code review
- Report coverage without measured evidence
- Use conditional language ("should be", "likely is", "estimated at")

**ONLY acceptable format:** "Coverage is X% (measured: [command output])"

**Gate Function:**
```
BEFORE analyzing coverage:
  1. Run full test suite with coverage
  2. Verify tests pass (if tests fail, fix tests first)
  3. Read coverage report
  4. ONLY THEN analyze gaps

  If tests fail → Fix tests before coverage analysis
  If coverage is old → Re-run, don't trust stale data
```

**Run coverage tools based on project language:**

**Go:**
```bash
cd backend
go test -coverprofile=coverage.out ./...
go tool cover -func=coverage.out
```

**Verification:**
- ✅ All tests pass (PASS, not FAIL)
- ✅ coverage.out file created
- ✅ No build errors

**Java (Maven):**
```bash
mvn clean test jacoco:report
# Read: target/site/jacoco/jacoco.xml
```

**JavaScript/TypeScript:**
```bash
npm test -- --coverage
# Read: coverage/coverage-summary.json
```

**Python:**
```bash
pytest --cov=. --cov-report=xml --cov-report=html
# Read: coverage.xml or htmlcov/index.html
```

**C++ (gcov + lcov):**
```bash
# Compile with coverage flags
g++ -fprofile-arcs -ftest-coverage -o test_app *.cpp
./test_app
# Generate coverage report
gcov *.cpp
lcov --capture --directory . --output-file coverage.info
lcov --list coverage.info
```

**C (gcov):**
```bash
# Compile with coverage
gcc -fprofile-arcs -ftest-coverage -o test_program *.c
./test_program
# Generate report
gcov *.c
```

**Rust (tarpaulin):**
```bash
cargo install cargo-tarpaulin  # If not installed
cargo tarpaulin --out Xml --out Html
# Read: cobertura.xml or tarpaulin-report.html
```

**Vue (Vitest):**
```bash
npm run test -- --coverage
# or with vitest directly
vitest run --coverage
# Read: coverage/coverage-summary.json
```

**React (Jest):**
```bash
npm test -- --coverage --watchAll=false
# or with yarn
yarn test --coverage --watchAll=false
# Read: coverage/lcov-report/index.html or coverage/coverage-summary.json
```

**Red Flags - STOP:**
- Tests failing → Fix tests first (use `superpowers:systematic-debugging`)
- Coverage report missing → Check test command
- Using old coverage data → Re-run tests

### Phase 2: Parse and Understand Coverage

**Extract key metrics with context:**

```yaml
coverage_summary:
  total:
    lines: {covered}/{total} ({percentage}%)
    branches: {covered}/{total} ({percentage}%)
    functions: {covered}/{total} ({percentage}%)

  by_file:
    - file: path/to/file.go
      lines: 85%
      branches: 70%
      uncovered_lines: [45, 67-72, 89]
      uncovered_branches:
        - line: 45
          condition: "err != nil"
          branch: "true"
```

**Gate Function:**
```
BEFORE identifying gaps:
  1. Do I understand the coverage data format?
  2. Can I map uncovered lines to actual code?
  3. Do I know what each metric means?

  If NO to any → Read coverage tool docs
  If ALL YES → Proceed to gap identification
```

### Phase 3: Identify Coverage Gaps with Root Cause Analysis

**CRITICAL:** Don't just list uncovered lines. Understand WHY they're uncovered.

**Use `superpowers:systematic-debugging` mindset:**
- What is this code doing?
- Why might it not be tested?
- Is this a gap or dead code?

**Categorize uncovered code by priority AND root cause:**

```yaml
coverage_gaps:
  critical: # High priority - business logic, security, data integrity
    - file: internal/auth/service.go
      lines: [45-52]
      code_function: "Login error handling"
      root_cause: "Error path not tested - no DB failure injection"
      impact: "Security-sensitive code"
      solution: "Mock DB error in test"

  important: # Medium priority - core functionality
    - file: internal/order/processor.go
      lines: [120-135]
      code_function: "Order validation edge case"
      root_cause: "Edge case not covered - missing boundary test"
      impact: "Could cause bugs in production"
      solution: "Add boundary value tests"

  low: # Lower priority - utilities, logging
    - file: pkg/utils/strings.go
      lines: [78-80]
      code_function: "String formatting utility"
      root_cause: "Utility function rarely used"
      impact: "Minimal risk"
      solution: "Add basic test or mark as dead code"

  dead_code_candidates:
    - file: pkg/utils/deprecated.go
      lines: [45-60]
      reason: "No execution path reaches this"
      action: "Delete or document why it exists"
```

**Gate Function:**
```
BEFORE proposing tests:
  FOR EACH uncovered code section:
    1. Read the actual source code
    2. Identify what it does
    3. Determine WHY it's uncovered:
       - Missing test case
       - Dead code
       - Hard to test (bad design)
       - Error handling path
       - Edge case

  If "hard to test" → Consider refactoring before testing
  If "dead code" → Suggest removal, don't test
  ONLY propose tests for legitimate gaps
```

### Phase 4: Generate Targeted Tests (Use TDD Skill)

**IMPORTANT:** Use `test-driven-development` skill for generating tests.

**For each high-priority gap:**

1. **Understand the gap completely**:
   ```
   Gap: internal/auth/service.go:45-52 (error handling)

   Analysis:
   - Code handles database connection error
   - Never triggered in current tests
   - Need to mock DB failure
   - Security-sensitive path
   ```

2. **Generate test following TDD:**
   ```go
   func TestLogin_DatabaseError_ReturnsError(t *testing.T) {
       // Arrange
       mockDB := new(MockDatabase)
       mockDB.On("Connect").Return(nil, errors.New("connection refused"))
       service := NewAuthService(mockDB)

       // Act
       _, err := service.Login(context.Background(), "user", "pass")

       // Assert
       require.Error(t, err)
       assert.Contains(t, err.Error(), "database")
       mockDB.AssertExpectations(t)
   }
   ```

3. **Verify test increases coverage:**
   ```bash
   go test -coverprofile=coverage.out ./internal/auth
   go tool cover -func=coverage.out | grep service.go
   ```

**Gate Function:**
```
BEFORE claiming test complete:
  1. Does test follow AAA structure?
  2. Does test have clear name?
  3. Have I run the test and verified it passes?
  4. Have I verified coverage increased?

  If NO to any → Fix before proceeding
  If ALL YES → Move to next gap
```

**Integration with test-generation-expert:**
- Use coverage-analyzer to identify WHAT to test
- Use test-generation-expert to generate HOW to test
- Use test-driven-development for new code (not coverage gaps)

### Phase 5: Verify Coverage Improvement

**MANDATORY:** Use `superpowers:verification-before-completion` principles.

**Gate Function:**
```
BEFORE claiming coverage improved:
  1. Run FRESH coverage (not old data)
  2. Compare before/after metrics
  3. Verify target coverage reached
  4. Verify ALL tests pass

  Evidence before claims, ALWAYS
```

**Verification steps:**

1. **Run fresh coverage:**
   ```bash
   go test -coverprofile=coverage.out ./...
   go tool cover -func=coverage.out
   ```

2. **Calculate actual improvement:**
   ```yaml
   improvement_actual:
     before:
       line_coverage: 72%
       branch_coverage: 65%

     after:
       line_coverage: 85%  # +13% (VERIFIED)
       branch_coverage: 78% # +13% (VERIFIED)

     tests_added: 8
     files_affected: 4
   ```

3. **Verify all tests pass:**
   ```bash
   go test ./... -v
   # Must see: PASS for all tests
   ```

**Red Flags - STOP:**
- Claiming improvement without running coverage
- Using estimated numbers instead of actual
- Tests failing after adding new tests
- Coverage went down (investigate why)

### Phase 6: Report Results with Evidence

**Report format:**
```markdown
# Coverage Analysis Report

## Before Analysis
- Line Coverage: 72% (MEASURED: go tool cover -func)
- Branch Coverage: 65%
- Function Coverage: 80%

## Gaps Identified
1. **Critical** (3 gaps): auth service error paths
2. **Important** (5 gaps): business logic edge cases
3. **Low** (2 gaps): utility functions

## Tests Generated
- Created 8 new test functions
- All tests pass (VERIFIED: go test ./...)
- Files modified: 4

## After Implementation
- Line Coverage: 85% (+13%) (VERIFIED: go tool cover -func)
- Branch Coverage: 78% (+13%)
- Function Coverage: 92% (+12%)

## Evidence
```bash
$ go test -coverprofile=coverage.out ./...
PASS
$ go tool cover -func=coverage.out | tail -1
total: (statements) 85.0%
```
```

**Evidence before claims:**
- ✅ Fresh coverage run
- ✅ All tests passing
- ✅ Actual metrics, not estimates
- ✅ Command outputs included

## Coverage Anti-Patterns to Avoid

### Anti-Pattern 1: Chasing 100% Coverage

**Bad:**
"We need 100% coverage on everything!"

**Why wrong:**
- 100% coverage ≠ good tests
- Diminishing returns after 85-90%
- Tests getters/setters adds no value
- Forces testing implementation details

**Good:**
- Critical paths: 95%+
- Core logic: 85%+
- Utilities: 70%+
- Accept some code won't be tested (dead code, trivial getters)

### Anti-Pattern 2: Testing for Coverage, Not Behavior

**Bad:**
```go
func TestCreateEnvironment(t *testing.T) {
    service.CreateEnvironment(ctx, "tenant1", "project1", req)
    // No assertions - just calling code to increase coverage
}
```

**Good:**
```go
func TestCreateEnvironment_Success(t *testing.T) {
    result, err := service.CreateEnvironment(ctx, "tenant1", "project1", req)

    require.NoError(t, err)
    assert.Equal(t, "dev", result.EnvID)
    assert.False(t, result.IsActive)
}
```

### Anti-Pattern 3: Generating Tests Without Understanding

**Bad:**
"Coverage is 60%, let me generate tests for all uncovered lines."

**Why wrong:**
- May be dead code
- May be hard to test (bad design)
- May not need tests (logging, trivial code)

**Good:**
"Coverage is 60%. Let me analyze WHY code is uncovered first."
- Read uncovered code
- Identify root causes
- Prioritize by risk
- Generate tests for legitimate gaps only

### Anti-Pattern 4: Trusting Old Coverage Data

**Bad:**
"Last week's coverage was 70%, so let's work from there."

**Why wrong:**
- Code changed since then
- Tests may have been deleted
- Coverage tool version may differ
- No baseline to compare against

**Good:**
Always run fresh coverage before and after.

## Common Rationalizations (and Why They're Wrong)

| Excuse | Reality |
|--------|---------|
| "Coverage is just a number" | It's a signal. Low coverage = untested code = bugs. |
| "100% coverage is wasteful" | Agree, but < 70% is risky. Target 85% for critical code. |
| "Old coverage is good enough" | Code changed. Always run fresh. |
| "I'll add tests later" | Later never comes. Do it now. |
| "This code is too hard to test" | Hard to test = bad design. Refactor first. |
| "Dead code doesn't need tests" | Dead code doesn't need to exist. Delete it. |
| "Coverage went up, mission accomplished" | Run the tests. Do they pass? Evidence before claims. |

## Red Flags - STOP

If you catch yourself:
- Claiming coverage improvement without running coverage
- Generating tests without reading source code
- Targeting 100% coverage on everything
- Testing to hit coverage metrics, not verify behavior
- Trusting old coverage data
- Skipping verification after adding tests
- Saying "coverage is probably around X%"

**STOP. Go back to Phase 1.**

## Integration with Other Skills

**Coverage-analyzer works with:**

1. **Before coverage analysis:**
   - Use `superpowers:test-driven-development` for new code
   - Don't use coverage-analyzer for TDD (write tests first!)

2. **During gap analysis:**
   - Use `superpowers:systematic-debugging` to understand WHY code is uncovered
   - Use `test-generation-expert` to generate tests for gaps

3. **After adding tests:**
   - Use `superpowers:verification-before-completion` to verify improvement
   - Re-run coverage-analyzer to confirm target reached

4. **If tests are hard to write:**
   - Consider refactoring first
   - Use `superpowers:testing-anti-patterns` to avoid bad patterns

## Real-World Results from This Project

From our coverage analysis:

**environment_service.go:**
- Before: 0% coverage (23 tests missing)
- After: 84.6% coverage (23 tests added)
- All tests passed on first run
- Time: 15 minutes with test-generation-expert

**project_service.go:**
- Before: 0% coverage (27 tests missing)
- After: 78.5% coverage (27 tests added)
- All tests passed on first run
- Time: 18 minutes with test-generation-expert

**Key insights:**
- Root cause analysis prevented testing dead code
- AAA pattern made tests readable
- Complete mock implementation prevented errors
- Evidence-based verification caught regressions

## Coverage Targets by Code Type

| Code Type | Target Coverage | Rationale |
|-----------|----------------|-----------|
| Authentication/Authorization | 95%+ | Security-critical |
| Payment Processing | 95%+ | Financial risk |
| Core Business Logic | 85%+ | High user impact |
| Data Validation | 85%+ | Data integrity |
| API Handlers | 80%+ | Integration points |
| Utilities | 70%+ | Lower risk |
| Logging/Metrics | 50%+ | Non-critical |
| Configuration | 50%+ | Tested in integration |

## Quick Reference Checklist

### Before Starting:
- [ ] Run fresh test suite with coverage
- [ ] All tests pass
- [ ] Record baseline coverage metrics

### During Analysis:
- [ ] Parse coverage report correctly
- [ ] Read actual source code for uncovered lines
- [ ] Identify root cause for each gap
- [ ] Categorize by priority (critical/important/low)
- [ ] Identify dead code candidates

### Generating Tests:
- [ ] Use test-driven-development skill
- [ ] Follow AAA structure
- [ ] Test behavior, not coverage
- [ ] Implement complete mocks
- [ ] Run each test after writing it

### Verification:
- [ ] Run fresh coverage
- [ ] All tests pass
- [ ] Coverage target reached
- [ ] Evidence included in report

### Completion:
- [ ] Report includes before/after metrics
- [ ] Report includes command outputs
- [ ] No claims without evidence
- [ ] Dead code identified for removal

## When Stuck

| Problem | Solution |
|---------|----------|
| Can't understand coverage data | Read coverage tool docs, check format |
| Coverage not increasing | Verify tests actually run new code paths |
| Tests too complex | Use `test-generation-expert` skill |
| Hard to mock dependencies | Consider refactoring for testability |
| Coverage target unreachable | Identify dead code, adjust target |
| Tests failing after adding new tests | Use `superpowers:systematic-debugging` |

## Final Rule

```
Evidence before claims, always.

Run coverage → Analyze gaps → Generate tests → Verify coverage → Report with evidence
```

**No shortcuts. No estimates. Fresh data only.**

## Reference Documents

When generating tests, reference project-specific guides:
- **Best Practices**: `docs/3-guides/ai-testing/best-practices.md`
- **TDD Skill**: `.claude/skills/test-driven-development/SKILL.md`
- **Test Generation**: `.claude/skills/test-generation-expert/SKILL.md`
