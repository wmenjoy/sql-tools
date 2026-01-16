---
name: test-driven-development
description: "Use when implementing any feature or bugfix, before writing implementation code - write the test first, watch it fail, write minimal code to pass; ensures tests actually verify behavior by requiring failure first"
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
  - Grep
---

**⚠️ BEFORE READING THIS DOCUMENT:**
- **If you wrote code without a failing test first:** Close this file. Go delete the code now. Don't read further until code is deleted.
- **If you're reading this to find permission to skip TDD:** The answer is no. Close this file. Follow the process.
- **If you're looking for exceptions or workarounds:** None exist. Either follow TDD or don't use this skill.

---

# Test-Driven Development (TDD)

## Overview

Write the test first. Watch it fail. Write minimal code to pass.

**Core principle:** If you didn't watch the test fail, you don't know if it tests the right thing.

**Violating the letter of the rules is violating the spirit of the rules.**

## The Iron Law

```
NO PRODUCTION CODE WITHOUT A FAILING TEST FIRST
```

**This is non-negotiable. Zero exceptions. No "just this once."**

## Wrote Code Before Test? Stop Reading

**Did you write production code without a failing test first?**

**Right now, before reading further:**

1. **Close your editor** (don't look at the code anymore)
2. **Delete the code:**
   ```bash
   # If uncommitted:
   git reset --hard
   # If committed:
   git reset --hard HEAD~1
   # Or simply:
   rm <file>
   ```
3. **Don't save it as "reference"**
4. **Don't look at it again**
5. **Take a 2-minute break**
6. **Return to Phase 1: RED** (write failing test first)

**Still hesitating?**

You're experiencing sunk cost fallacy. The time is already gone. Your only choice now: keep technical debt or fix it.

**The fact you're reading this section means you already know you violated TDD.**

Don't argue with yourself. Don't explain why this case is special.

**Delete the code. Now. I'll wait.**

---

*(Continue reading only after untested code is deleted)*

---

## When to Use

**Always:**
- New features
- Bug fixes
- Refactoring
- Behavior changes

**The ONLY exception:**
- Your human partner explicitly says "this is a throwaway prototype"
- They must say "throwaway" out loud
- They must confirm it will be deleted, not merged
- You must delete it after demonstration

**Everything else:** Follow TDD. No discussion. No permission-seeking.

Thinking "skip TDD just this once"? Stop. That's rationalization.

**No exceptions:**
- Don't keep untested code as "reference"
- Don't "adapt" it while writing tests
- Don't look at it
- Delete means delete
- "Just this once" becomes every time

Implement fresh from tests. Period.

## Red-Green-Refactor Cycle

### Phase 1: RED - Write Failing Test

Write one minimal test showing what should happen.

**Good Example (Go):**
```go
func TestCreateEnvironment_Success(t *testing.T) {
    // Arrange
    mockRepo := new(MockEnvironmentRepository)
    service := NewEnvironmentService(mockRepo)
    ctx := context.Background()
    req := &CreateEnvironmentRequest{
        EnvID: "dev",
        Name:  "Development",
    }

    mockRepo.On("FindByIDWithTenant", ctx, "dev", "tenant1", "project1").Return(nil, nil)
    mockRepo.On("CreateWithTenant", ctx, mock.AnythingOfType("*models.Environment")).Return(nil)

    // Act
    result, err := service.CreateEnvironment(ctx, "tenant1", "project1", req)

    // Assert
    require.NoError(t, err)
    assert.Equal(t, "dev", result.EnvID)
    mockRepo.AssertExpectations(t)
}
```

**Bad Example:**
```go
func TestEnvironment(t *testing.T) {
    // Tests multiple things at once
    // Vague name
    // No clear AAA structure
}
```

**Requirements:**
- One behavior per test
- Clear name describing what's tested
- AAA structure (Arrange, Act, Assert)
- Use testify/require for errors, testify/assert for values
- Real code (mocks only when unavoidable)

### Phase 2: Verify RED - Watch It Fail

**MANDATORY. Never skip.**

```bash
cd backend
go test -v ./internal/service -run TestCreateEnvironment_Success
```

**Gate Function:**
```
BEFORE writing implementation:
  1. Does test fail? (not error)
  2. Is failure message expected?
  3. Does it fail because feature is missing (not typos)?

  If NO to any → Fix test, re-run
  If ALL YES → Proceed to GREEN
```

**Confirm:**
- ✅ Test fails (not errors)
- ✅ Failure message is expected
- ✅ Fails because feature missing (not typos)

**Test passes?** You're testing existing behavior. Fix test.

**Test errors?** Fix error, re-run until it fails correctly.

### Phase 3: GREEN - Minimal Code

Write simplest code to pass the test.

**Good Example:**
```go
func (s *EnvironmentService) CreateEnvironment(
    ctx context.Context,
    tenantID, projectID string,
    req *CreateEnvironmentRequest,
) (*Environment, error) {
    // Check if environment already exists
    existing, err := s.repo.FindByIDWithTenant(ctx, req.EnvID, tenantID, projectID)
    if err != nil {
        return nil, err
    }
    if existing != nil {
        return nil, fmt.Errorf("environment %s already exists", req.EnvID)
    }

    // Create new environment
    env := &models.Environment{
        EnvID:     req.EnvID,
        Name:      req.Name,
        TenantID:  tenantID,
        ProjectID: projectID,
        IsActive:  false, // New environments start inactive
    }

    if err := s.repo.CreateWithTenant(ctx, env); err != nil {
        return nil, err
    }

    return toEnvironmentResponse(env), nil
}
```

**Bad Example:**
```go
// Over-engineered with features not needed by test
func (s *EnvironmentService) CreateEnvironment(
    ctx context.Context,
    tenantID, projectID string,
    req *CreateEnvironmentRequest,
    opts ...EnvironmentOption, // YAGNI
) (*Environment, error) {
    // Complex option handling not required by test
    // Caching logic not tested
    // Background validation not tested
}
```

**Don't:**
- Add features beyond the test
- Refactor other code
- "Improve" unrelated areas

### Phase 4: Verify GREEN - Watch It Pass

**MANDATORY.**

```bash
go test -v ./internal/service -run TestCreateEnvironment_Success
```

**Gate Function:**
```
BEFORE claiming success:
  1. Does this test pass?
  2. Do ALL other tests still pass?
  3. Is output pristine (no errors, warnings)?
  4. Is coverage for this function increased?

  If NO to any → Fix, don't proceed
  If ALL YES → Proceed to REFACTOR
```

**Confirm:**
- ✅ Test passes
- ✅ Other tests still pass
- ✅ Output pristine (no errors, warnings)

**Test fails?** Fix code, not test.

**Other tests fail?** Fix now, don't proceed.

### Phase 5: REFACTOR - Clean Up

After green only:
- Remove duplication
- Improve names
- Extract helpers

**Keep tests green. Don't add behavior.**

**Verify after refactor:**
```bash
go test -v ./internal/service
```

All tests must still pass.

### Phase 6: Repeat

Next failing test for next feature.

## Integration with Coverage-Analyzer

**After completing RED-GREEN-REFACTOR cycle:**

1. **Check coverage improvement:**
   ```bash
   go test -coverprofile=coverage.out ./internal/service
   go tool cover -func=coverage.out | grep environment_service.go
   ```

2. **If coverage < 85%:**
   - Invoke `/analyze-coverage` to identify gaps
   - Add missing test cases following TDD cycle
   - Re-verify coverage

3. **Coverage targets:**
   - Critical paths (auth, payment): 95%+
   - Core business logic: 85%+
   - Utilities: 70%+

## Integration with Test-Generation-Expert

**When adding tests to existing untested code:**

1. **First, use test-generation-expert to generate initial test suite**
2. **Then, for new features, always use TDD:**
   - RED: Write failing test
   - GREEN: Implement feature
   - Coverage-analyzer: Verify completeness

**Don't:**
- Use test-generation-expert for new code (use TDD)
- Skip RED-GREEN for "quick fixes" to existing code

## Testing Anti-Patterns to Avoid

### Anti-Pattern 1: Testing Mock Behavior

**Bad:**
```go
func TestCreateEnvironment_CallsRepo(t *testing.T) {
    mockRepo := new(MockEnvironmentRepository)
    service := NewEnvironmentService(mockRepo)

    mockRepo.On("CreateWithTenant", mock.Anything, mock.Anything).Return(nil)

    service.CreateEnvironment(ctx, "tenant1", "project1", req)

    // ❌ Testing that mock was called, not actual behavior
    mockRepo.AssertCalled(t, "CreateWithTenant", mock.Anything, mock.Anything)
}
```

**Good:**
```go
func TestCreateEnvironment_Success(t *testing.T) {
    mockRepo := new(MockEnvironmentRepository)
    service := NewEnvironmentService(mockRepo)

    mockRepo.On("FindByIDWithTenant", ctx, "dev", "tenant1", "project1").Return(nil, nil)
    mockRepo.On("CreateWithTenant", ctx, mock.AnythingOfType("*models.Environment")).Return(nil)

    // ✅ Testing actual behavior and result
    result, err := service.CreateEnvironment(ctx, "tenant1", "project1", req)

    require.NoError(t, err)
    assert.Equal(t, "dev", result.EnvID)
    assert.False(t, result.IsActive, "New environment should not be active")
}
```

### Anti-Pattern 2: Incomplete Mocks

**Bad:**
```go
type MockRepository struct {
    mock.Mock
}

// ❌ Only implementing methods you think you need
func (m *MockRepository) Create(ctx context.Context, env *models.Environment) error {
    args := m.Called(ctx, env)
    return args.Error(0)
}
// Missing other interface methods
```

**Good:**
```go
type MockEnvironmentRepository struct {
    mock.Mock
}

// ✅ Complete interface implementation
// Read the interface definition and implement ALL methods
func (m *MockEnvironmentRepository) Create(ctx context.Context, env *models.Environment) error {
    args := m.Called(ctx, env)
    return args.Error(0)
}

func (m *MockEnvironmentRepository) CreateWithTenant(ctx context.Context, env *models.Environment) error {
    args := m.Called(ctx, env)
    return args.Error(0)
}

func (m *MockEnvironmentRepository) FindByID(ctx context.Context, envID string) (*models.Environment, error) {
    args := m.Called(ctx, envID)
    if args.Get(0) == nil {
        return nil, args.Error(1)
    }
    return args.Get(0).(*models.Environment), args.Error(1)
}

// ... all other interface methods
```

**How to ensure completeness:**
```bash
# Find interface definition
grep -A 20 "type EnvironmentRepository interface" ./internal/repository/*.go

# Implement ALL methods listed
```

### Anti-Pattern 3: Test-Only Methods in Production

**Bad:**
```go
// ❌ In production code
type EnvironmentService struct {
    repo repository.EnvironmentRepository
}

// Only used in tests for cleanup
func (s *EnvironmentService) DestroyAll() error {
    return s.repo.DeleteAll()
}
```

**Good:**
```go
// ✅ In test utilities (test-utils.go)
func CleanupEnvironments(t *testing.T, repo repository.EnvironmentRepository) {
    // Test-only cleanup logic here
}

// In tests
func TestSomething(t *testing.T) {
    defer CleanupEnvironments(t, repo)
    // test logic
}
```

## Go-Specific Best Practices

### 1. Test Organization

```go
// File: environment_service_test.go
package service_test // Use _test package for black-box testing

import (
    "context"
    "testing"

    "github.com/stretchr/testify/assert"
    "github.com/stretchr/testify/mock"
    "github.com/stretchr/testify/require"

    "your-project/internal/models"
    "your-project/internal/service"
)

// Group related tests with Table-Driven Tests
func TestEnvironmentService_Create(t *testing.T) {
    tests := []struct {
        name    string
        setup   func(*MockEnvironmentRepository)
        request *service.CreateEnvironmentRequest
        wantErr bool
        errMsg  string
    }{
        {
            name: "success",
            setup: func(m *MockEnvironmentRepository) {
                m.On("FindByIDWithTenant", mock.Anything, "dev", "tenant1", "project1").Return(nil, nil)
                m.On("CreateWithTenant", mock.Anything, mock.AnythingOfType("*models.Environment")).Return(nil)
            },
            request: &service.CreateEnvironmentRequest{
                EnvID: "dev",
                Name:  "Development",
            },
            wantErr: false,
        },
        {
            name: "duplicate environment",
            setup: func(m *MockEnvironmentRepository) {
                existing := &models.Environment{EnvID: "dev"}
                m.On("FindByIDWithTenant", mock.Anything, "dev", "tenant1", "project1").Return(existing, nil)
            },
            request: &service.CreateEnvironmentRequest{
                EnvID: "dev",
                Name:  "Development",
            },
            wantErr: true,
            errMsg:  "already exists",
        },
    }

    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            // Arrange
            mockRepo := new(MockEnvironmentRepository)
            if tt.setup != nil {
                tt.setup(mockRepo)
            }
            service := service.NewEnvironmentService(mockRepo)

            // Act
            result, err := service.CreateEnvironment(context.Background(), "tenant1", "project1", tt.request)

            // Assert
            if tt.wantErr {
                require.Error(t, err)
                if tt.errMsg != "" {
                    assert.Contains(t, err.Error(), tt.errMsg)
                }
            } else {
                require.NoError(t, err)
                assert.Equal(t, tt.request.EnvID, result.EnvID)
            }
            mockRepo.AssertExpectations(t)
        })
    }
}
```

### 2. Mock Implementation

```go
// File: mock_repository_test.go
package service_test

import (
    "context"
    "github.com/stretchr/testify/mock"
    "your-project/internal/models"
)

// MockEnvironmentRepository implements repository.EnvironmentRepository
type MockEnvironmentRepository struct {
    mock.Mock
}

// Implement ALL interface methods - no shortcuts
func (m *MockEnvironmentRepository) Create(ctx context.Context, env *models.Environment) error {
    args := m.Called(ctx, env)
    return args.Error(0)
}

func (m *MockEnvironmentRepository) CreateWithTenant(ctx context.Context, env *models.Environment) error {
    args := m.Called(ctx, env)
    return args.Error(0)
}

func (m *MockEnvironmentRepository) FindByID(ctx context.Context, envID string) (*models.Environment, error) {
    args := m.Called(ctx, envID)
    if args.Get(0) == nil {
        return nil, args.Error(1)
    }
    return args.Get(0).(*models.Environment), args.Error(1)
}

// ... ALL other methods from interface
```

### 3. Context Handling

```go
// Always test with real context, not nil
func TestWithContext(t *testing.T) {
    ctx := context.Background()
    // For timeout testing
    ctx, cancel := context.WithTimeout(context.Background(), 100*time.Millisecond)
    defer cancel()

    // For cancellation testing
    ctx, cancel := context.WithCancel(context.Background())
    cancel() // Cancel immediately to test cancellation handling
}
```

### 4. Error Testing

```go
func TestCreateEnvironment_RepositoryError(t *testing.T) {
    // Arrange
    mockRepo := new(MockEnvironmentRepository)
    service := NewEnvironmentService(mockRepo)

    expectedErr := errors.New("database connection failed")
    mockRepo.On("FindByIDWithTenant", mock.Anything, "dev", "tenant1", "project1").
        Return(nil, expectedErr)

    // Act
    result, err := service.CreateEnvironment(ctx, "tenant1", "project1", req)

    // Assert
    require.Error(t, err)
    assert.Nil(t, result)
    assert.Contains(t, err.Error(), "database connection failed")
    mockRepo.AssertExpectations(t)
}
```

## Emergency Brake: Catching Yourself Mid-Rationalization

**Are you currently thinking ANY of these thoughts?**

- [ ] "I'll test after to verify it works"
- [ ] "I already manually tested the edge cases"
- [ ] "Deleting X hours of work is wasteful"
- [ ] "TDD is dogmatic, I need to be pragmatic"
- [ ] "Tests after achieve the same goal"
- [ ] "Keep as reference while writing tests"
- [ ] "This situation is different because..."
- [ ] "Most experienced developers would..."
- [ ] "Work-life balance means..."
- [ ] "I can write better tests when I'm fresh tomorrow"
- [ ] "The code already works, tests are just formality"

**If you checked ANY box above:**

### You Are Mid-Rationalization RIGHT NOW

**Immediate action required:**

1. **Stop typing immediately**
2. **Close your editor** (don't look at the code)
3. **Check what you created:**
   ```bash
   git status
   git diff
   ```
4. **Delete it:**
   ```bash
   git reset --hard  # Or rm <file>
   ```
5. **Take 2-minute break** (walk away from computer)
6. **Return to Phase 1: RED** (write failing test first)

**No discussion. No negotiation with yourself.**

The fact you're reading this section means you already know you violated TDD.

**This document cannot give you permission to skip TDD.**
**Your human partner cannot give you permission to skip TDD.**
**"The real world" does not change this rule.**
**"I already tested it manually" does not change this rule.**
**"Most developers would..." does not change this rule.**

**Delete the code. Now.**

---

## Common Rationalizations (and Why They're Wrong)

| Excuse | Reality |
|--------|---------|
| "Issue is simple, don't need TDD" | Simple issues have bugs too. TDD takes 30 seconds. |
| "I'll test after implementation" | Tests passing immediately prove nothing. |
| "Already manually tested" | Ad-hoc ≠ systematic. No regression protection. |
| "Deleting X hours is wasteful" | Sunk cost fallacy. Keeping unverified code is tech debt. |
| "Keep as reference, write tests first" | You'll adapt it. That's testing after. Delete means delete. |
| "TDD will slow me down" | TDD faster than debugging. Proven by our results. |
| "This test is hard to write" | Listen to the test. Hard to test = bad design. |
| "Just this once" | "Just this once" becomes "every time". |

## Red Flags - STOP and Start Over

If you catch yourself thinking:
- "Let me implement first, then test"
- "I'll write tests after confirming it works"
- "Keep code as reference"
- "Test passes immediately, great!"
- "This mock is too complex, skip it"
- "I already manually tested it"
- "Just this once"
- "TDD is overkill for this"

**ALL of these mean: STOP. Delete code. Start with RED.**

## Verification Checklist

Before marking work complete:

- [ ] Every new function/method has a test
- [ ] Watched each test fail (RED) before implementing
- [ ] Each test failed for expected reason (feature missing, not typo)
- [ ] Wrote minimal code to pass each test (GREEN)
- [ ] All tests pass with pristine output
- [ ] Coverage meets target (use `/analyze-coverage`)
- [ ] Tests use real code (mocks only if unavoidable)
- [ ] Mocks implement complete interface (grep for interface definition)
- [ ] AAA structure in all tests
- [ ] No test-only methods in production code

Can't check all boxes? You skipped TDD. Start over.

## Integration with Other Skills

**Use these skills together:**

1. **Before starting**: Read `superpowers:brainstorming` if design unclear
2. **During TDD**: Follow this skill (test-driven-development)
3. **After implementation**: Use `/analyze-coverage` to verify completeness
4. **For existing code**: Use `test-generation-expert` to generate initial suite
5. **Before completion**: Read `superpowers:verification-before-completion`

## Automation Integration - Enforcing TDD at Tool Level

**TDD discipline requires automated enforcement.** Git hooks can be bypassed with `--no-verify`, manual testing is unreliable, and promises to "test later" fail.

**Solution: Defense in Depth**

```
Layer 1: Local (Git Hooks) → First line of defense
Layer 2: Remote (Pre-push) → Before code reaches others
Layer 3: CI/CD (Pipeline) → Cannot be bypassed
Layer 4: Dashboard (Metrics) → Visibility and tracking
```

### Layer 1: Git Pre-commit Hooks

**Purpose**: Prevent untested code from being committed locally

**Setup**: See `docs/3-guides/development/automation/git-hooks-tdd.md`

**What it enforces**:
- Tests run before every commit
- Coverage must meet threshold (default 80%)
- Code formatting and linting
- Tests must pass (green)

**Installation**:
```bash
# Copy hook template
cp docs/3-guides/development/automation/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit

# Or use make command (if configured)
make install-hooks
```

**What happens on commit**:
```bash
git commit -m "feat: add environment service"

# Hook automatically runs:
# 1. go fmt check
# 2. go vet check
# 3. go test ./...
# 4. Coverage check (must be >= 80%)
# 5. If any fail → commit blocked

✅ All TDD checks passed! Proceeding with commit...
```

**Can be bypassed?** Yes, with `--no-verify` (emergency only). That's why Layer 3 exists.

### Layer 2: Git Pre-push Hooks

**Purpose**: Run slower tests before pushing to remote

**What it enforces**:
- Integration tests
- E2E tests
- Full coverage report generation

**Use for**:
- Tests that take >10 seconds
- Tests requiring external resources
- Comprehensive regression suites

See `docs/3-guides/development/automation/git-hooks-tdd.md#pre-push-hook`

### Layer 3: CI/CD Pipeline (Mandatory)

**Purpose**: Cannot be bypassed - PRs cannot merge without passing

**Setup**: See `docs/3-guides/development/automation/ci-cd-integration.md`

**What it enforces**:
1. **Code Quality Job**: fmt, vet, lint
2. **Unit Tests Job**: All tests pass + coverage check
3. **Integration Tests Job**: DB tests, API tests
4. **TDD Compliance Job**: Every changed file has test file
5. **Quality Gate**: All jobs must pass before merge

**Example - GitHub Actions**:
```yaml
# .github/workflows/tdd-enforcement.yml
name: TDD Enforcement

on:
  pull_request:
    branches: [main]

jobs:
  tdd-compliance:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0

      - name: Check for untested code
        run: |
          # Get changed files
          CHANGED_FILES=$(git diff --name-only origin/${{ github.base_ref }}...HEAD | grep '\.go$' | grep -v '_test\.go$' || true)

          # Check each has test file
          for file in $CHANGED_FILES; do
            TEST_FILE="${file%.go}_test.go"
            if [ ! -f "$TEST_FILE" ]; then
              echo "❌ TDD Violation: $file has no test file"
              exit 1
            fi
          done
```

**Why mandatory**:
- Cannot use `--no-verify`
- Branch protection rules enforce
- Team-wide quality gate

**Platform templates available**:
- GitHub Actions
- GitLab CI
- Jenkins Pipeline
- CircleCI

All in `docs/3-guides/development/automation/ci-cd-integration.md`

### Layer 4: Test Metrics Dashboard

**Purpose**: Monitor team TDD compliance and test quality over time

**Setup**: See `docs/3-guides/development/automation/test-dashboard.md`

**Key Metrics**:

1. **TDD Compliance Score (0-100)**:
   ```
   Score = (
       Test-First Rate × 0.3 +      # Tests before code
       Coverage Rate × 0.25 +         # Meets threshold
       Test Quality × 0.25 +          # Assessment score
       CI Pass Rate × 0.2             # Pipeline success
   )
   ```

2. **Coverage Trend**: 30-day rolling average
3. **Test Quality Score**: 100-point objective assessment
4. **Flaky Test Rate**: Intermittent failures
5. **Hook Bypass Rate**: `--no-verify` usage

**Implementation Options**:

**Option 1: Grafana + Prometheus** (Enterprise)
- Real-time metrics
- Historical trends
- Alert integration
- Full customization

**Option 2: GitHub Actions + Badges** (Lightweight)
- Badge in README
- Auto-updated metrics
- Free for public repos

**Option 3: Custom Dashboard** (Full control)
- React frontend
- Go backend API
- Database for history

**Example - Prometheus Metrics**:
```go
// Expose TDD metrics for Prometheus
var (
    tddCompliance = prometheus.NewGauge(
        prometheus.GaugeOpts{
            Name: "tdd_compliance_score",
            Help: "TDD compliance score (0-100)",
        },
    )

    coverage = prometheus.NewGaugeVec(
        prometheus.GaugeOpts{
            Name: "test_coverage_percentage",
            Help: "Test coverage by module",
        },
        []string{"module", "type"},
    )
)
```

See full implementation in `docs/3-guides/development/automation/test-dashboard.md`

### Workflow Integration

**Full TDD workflow with automation**:

```
┌─────────────────────────────────────────────────────────┐
│ Phase 1: RED - Write Failing Test                       │
├─────────────────────────────────────────────────────────┤
│ 1. Write test first (TestCreateEnvironment_Success)     │
│ 2. Run: go test -v ./internal/service -run TestCreate*  │
│ 3. Verify: Test fails (RED)                             │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ Phase 2: GREEN - Minimal Implementation                 │
├─────────────────────────────────────────────────────────┤
│ 1. Write minimal code to pass                           │
│ 2. Run: go test -v ./internal/service                   │
│ 3. Verify: All tests pass (GREEN)                       │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ Phase 3: REFACTOR - Clean Up                            │
├─────────────────────────────────────────────────────────┤
│ 1. Remove duplication, improve names                    │
│ 2. Run: go test -v ./internal/service                   │
│ 3. Verify: Still green                                  │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ Phase 4: COMMIT - Automated Enforcement                 │
├─────────────────────────────────────────────────────────┤
│ git commit -m "feat: add CreateEnvironment"             │
│                                                          │
│ ⚡ Pre-commit Hook Runs:                                │
│   ✅ go fmt check                                       │
│   ✅ go vet check                                       │
│   ✅ go test ./...                                      │
│   ✅ Coverage check (84.6% >= 80%)                      │
│                                                          │
│ ✅ Commit allowed                                       │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ Phase 5: PUSH - Remote Validation                       │
├─────────────────────────────────────────────────────────┤
│ git push origin feature/environment-service             │
│                                                          │
│ ⚡ Pre-push Hook Runs:                                  │
│   ✅ Integration tests                                  │
│   ✅ E2E tests                                          │
│   ✅ Full coverage report                               │
│                                                          │
│ ✅ Push allowed                                         │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ Phase 6: PR - CI/CD Pipeline (Cannot Bypass)            │
├─────────────────────────────────────────────────────────┤
│ Create PR → GitHub Actions triggered                    │
│                                                          │
│ Job 1: Code Quality                                     │
│   ✅ fmt, vet, lint                                     │
│                                                          │
│ Job 2: Unit Tests                                       │
│   ✅ All tests pass                                     │
│   ✅ Coverage: 84.6% >= 80%                             │
│   ✅ Upload to Codecov                                  │
│                                                          │
│ Job 3: TDD Compliance                                   │
│   ✅ Every .go file has _test.go                        │
│                                                          │
│ Job 4: Quality Gate                                     │
│   ✅ All jobs passed → PR can merge                     │
│                                                          │
│ Branch Protection: Requires all checks                  │
└─────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────┐
│ Phase 7: MERGE - Metrics Update                         │
├─────────────────────────────────────────────────────────┤
│ PR merged → Main branch updated                         │
│                                                          │
│ 📊 Dashboard Updated:                                   │
│   • TDD Compliance: 87.5% → 88.2% ↑                     │
│   • Coverage: 84.0% → 84.6% ↑                           │
│   • Test Quality: 89/100                                │
│   • CI Pass Rate: 98.5%                                 │
│                                                          │
│ 🔔 Slack Notification:                                  │
│   "✅ environment-service merged                        │
│    Coverage: +0.6% | Tests: +3 | TDD Score: 88.2"      │
└─────────────────────────────────────────────────────────┘
```

### Emergency Bypass Procedures

**When to use `git commit --no-verify`**:
- Production down, hotfix needed NOW
- Build system broken (not your fault)
- Hook itself has bug preventing commit

**Required when bypassing**:
1. Document reason in commit message
2. Create issue to add missing tests
3. Fix in next commit (same day)
4. Update team in Slack/email

**Example**:
```bash
# Production is down, bypass allowed
git commit --no-verify -m "hotfix: fix null pointer in payment

EMERGENCY BYPASS: Production down, customers affected
Issue created: #1234 - Add tests for payment null handling
Will fix within 2 hours per incident protocol"

# Immediately after hotfix deployed:
# 1. Write failing test
# 2. Verify code fixes it (already deployed)
# 3. Commit test
# 4. Close issue #1234
```

**Team tracking**: Dashboard shows bypass rate as metric

### Setting Up Automation

**Quick Start (5 minutes)**:

1. **Install Git Hooks**:
   ```bash
   # Go to docs directory
   cd docs/3-guides/development/automation

   # Copy pre-commit hook
   cp git-hooks-tdd.md .git/hooks/pre-commit
   chmod +x .git/hooks/pre-commit

   # Test it
   .git/hooks/pre-commit
   ```

2. **Add CI/CD Pipeline**:
   ```bash
   # For GitHub
   mkdir -p .github/workflows
   # Copy template from ci-cd-integration.md
   # Customize coverage threshold, branches, etc.
   ```

3. **Configure Dashboard** (optional, later):
   ```bash
   # Choose implementation:
   # - Grafana + Prometheus (complex)
   # - GitHub Badges (simple)
   # - Custom dashboard (full control)

   # See test-dashboard.md for setup
   ```

**Complete documentation**:
- Git Hooks: `docs/3-guides/development/automation/git-hooks-tdd.md`
- CI/CD Integration: `docs/3-guides/development/automation/ci-cd-integration.md`
- Test Dashboard: `docs/3-guides/development/automation/test-dashboard.md`

**Key Principle**: Automation enforces TDD discipline, making it the path of least resistance. Manual bypass requires extra effort, creating natural incentive to follow TDD.

## Real-World Results from This Project

From our testing:
- **environment_service_test.go**: 23 tests, 84.6% coverage, 100% pass rate
- **project_service_test.go**: 27 tests, 78.5% coverage, 100% pass rate
- **Time saved**: 48-72x faster than manual
- **First-run success**: 100% (all generated tests passed)

**Key insight**: TDD with proper tools (coverage-analyzer, test-generation-expert) delivers high-quality tests faster than manual approaches.

## Final Rule

```
If production code exists → failing test MUST have existed first
If no failing test first → DELETE the code immediately
No test failure witnessed → NOT TDD → DELETE
```

**This is absolute. No negotiation.**

**This document cannot give you permission to skip TDD.**
**Your human partner cannot give you permission to skip TDD.**
**"The real world" does not exempt you from TDD.**
**"Emergency" does not exempt you from TDD.**
**"I'm being pragmatic" is rationalization, not exemption.**

**The ONLY exception:**

Your human partner explicitly says out loud: "This is a throwaway prototype that will be deleted."
- They must use the word "throwaway"
- They must confirm deletion, not merge
- You must delete it after demonstration
- No other exceptions exist

**Everything else: Follow TDD. Delete untested code. No discussion.**

**Evidence before claims. Always.**

