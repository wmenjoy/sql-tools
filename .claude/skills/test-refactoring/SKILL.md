---
name: test-refactoring
description: "Use when tests become hard to understand, have duplication, or need cleanup - applies same refactoring discipline to test code as production code, maintaining test quality over time"
allowed-tools:
  - Read
  - Edit
  - Write
  - Bash
  - Glob
  - Grep
---

# Test Refactoring

## Overview

Test code needs refactoring just like production code. Tests that aren't maintained become unmaintainable.

**Core principle:** Test code is first-class code. Same quality standards, same refactoring discipline.

**Violating the letter of this process is violating the spirit of testing.**

## The Iron Laws

```
1. NEVER refactor tests and production code simultaneously
2. NEVER skip refactoring because "it's just test code"
3. NEVER break tests during refactoring (stay green)
4. NEVER claim refactoring complete without running tests
```

## Definition: Test Refactoring

**Test Refactoring = Improving test code structure WITHOUT changing what it tests.**

**You MUST refactor tests when:**
- Tests have duplicated setup code
- Test names don't describe what they test
- Tests are hard to understand
- Adding new tests requires copying/pasting
- Tests break when unrelated production code changes
- Setup code is longer than test logic

**"Refactoring" does NOT mean:**
- ❌ Changing what the test verifies (that's changing behavior)
- ❌ Making tests pass by weakening assertions
- ❌ Removing tests because they're "redundant"
- ❌ Fixing tests broken by production changes (that's maintenance, not refactoring)

**ABSOLUTE REQUIREMENT:** Tests must pass before AND after refactoring. You cannot refactor failing tests - fix them first, THEN refactor.

## VIOLATIONS (Automatic Skill Restart Required)

**Any of these = skill misuse, start over at Phase 1:**

❌ **Refactoring tests while they're failing**
   - Example: "Test is broken, I'll refactor while fixing it"
   - Violation: Must fix first, then refactor

❌ **Refactoring tests and production code together**
   - Example: "I'll rename this method and update all its tests"
   - Violation: Refactor one at a time (production first, then tests)

❌ **Changing test behavior during refactoring**
   - Example: "While refactoring, I'll also add this edge case"
   - Violation: Refactoring = structure only, not behavior

❌ **Skipping test runs after refactoring**
   - Example: "Just renamed variables, tests should still pass"
   - Violation: Must run tests to verify

❌ **Rationalizing "it's just test code"**
   - Example: "Test code doesn't need to be clean"
   - Violation: Test code is first-class code

❌ **Breaking tests to make refactoring easier**
   - Example: "I'll comment out this test, refactor, then fix it"
   - Violation: Tests must stay green throughout

**If you violate:** Reset to Phase 1. No shortcuts.

## When to Use This Skill

**Use when:**
- Tests have obvious duplication
- Test names are unclear ("test1", "testMethod")
- Setup code is repeated across tests
- Tests are hard to understand
- Adding new tests requires copy/paste
- Tests break when unrelated code changes (brittle tests)
- Test file is >500 lines with duplication

**DON'T use for:**
- Fixing failing tests (fix first, then refactor)
- Adding new test cases (that's expansion, not refactoring)
- Changing what tests verify (that's behavior change)
- Deleting tests (requires careful analysis first)
- Production emergencies (use TDD to fix bug first, then refactor later)

**Timing:**
- After implementing feature (tests pass)
- During code review (before merging)
- Regular maintenance (monthly test review)
- When adding similar test cases (extract pattern first)

## Test Smells (When to Refactor)

### Smell 1: Duplicated Setup

**Example:**
```go
func TestCreateUser_ValidEmail(t *testing.T) {
    mockRepo := new(MockUserRepository)
    mockEmail := new(MockEmailService)
    mockAuth := new(MockAuthService)
    service := NewUserService(mockRepo, mockEmail, mockAuth)
    // ... test logic
}

func TestCreateUser_InvalidEmail(t *testing.T) {
    mockRepo := new(MockUserRepository)
    mockEmail := new(MockEmailService)
    mockAuth := new(MockAuthService)
    service := NewUserService(mockRepo, mockEmail, mockAuth)
    // ... test logic
}
```

**Smell:** Setup repeated in every test

**Fix:** Extract to test helper or setup function

### Smell 2: Unclear Test Names

**Example:**
```go
func TestUser1(t *testing.T) { ... }
func TestUser2(t *testing.T) { ... }
func TestUserMethod(t *testing.T) { ... }
```

**Smell:** Names don't describe what they test

**Fix:** Rename to describe behavior: `TestCreateUser_RejectsEmptyEmail`

### Smell 3: Long Test Functions

**Example:**
```go
func TestCreateOrder(t *testing.T) {
    // 50 lines of setup
    // 10 lines of test logic
    // 30 lines of assertions
}
```

**Smell:** Test does too much, hard to understand

**Fix:** Split into focused tests, extract helpers

### Smell 4: Magic Numbers/Strings

**Example:**
```go
assert.Equal(t, 42, result.Status)
assert.Equal(t, "abc123xyz", result.Token)
```

**Smell:** Values have no meaning

**Fix:** Use named constants: `StatusProcessing`, `testAuthToken`

### Smell 5: Testing Implementation Details

**Example:**
```typescript
expect(component.state.internalCounter).toBe(5);
```

**Smell:** Test coupled to implementation

**Fix:** Test behavior, not internal state

## The Test Refactoring Process

### Phase 1: Ensure Tests Pass (Green)

**ABSOLUTE REQUIREMENT (Non-Negotiable):**

You MUST have all tests passing before refactoring. This is the "Green" in RED-GREEN-REFACTOR.

**You CANNOT refactor if:**
- Any tests are failing
- Tests are commented out
- Tests are marked as "skip" or "ignore"
- You don't know if tests pass

**ONLY acceptable:** All tests green, output pristine, THEN refactor.

**Gate Function:**
```
BEFORE starting refactoring:
  1. Run full test suite
  2. Verify ALL tests pass
  3. Verify output pristine (no warnings)
  4. ONLY THEN proceed to smell identification

  If tests fail → Fix tests first, refactoring later
  If unsure → Run tests, confirm green
```

**Run tests:**
```bash
# Go
go test ./... -v

# Java
mvn test

# JavaScript/TypeScript
npm test

# Python
pytest

# C++
./run_tests

# Rust
cargo test
```

**Verification:**
- ✅ All tests PASS (not SKIP, not FAIL)
- ✅ No warnings or errors in output
- ✅ Coverage report available (to verify after refactoring)

**Red Flags - STOP:**
- Tests failing → Fix before refactoring
- Tests skipped → Enable or delete them first
- Uncertain about test status → Run tests now

### Phase 2: Identify Test Smells

**Use systematic smell detection:**

```yaml
smell_checklist:
  duplication:
    - [ ] Setup code repeated across tests
    - [ ] Teardown code repeated
    - [ ] Same mock expectations in multiple tests
    - [ ] Similar assertion patterns

  clarity:
    - [ ] Test names unclear ("test1", "testMethod")
    - [ ] Long test functions (>50 lines)
    - [ ] Magic numbers/strings without meaning
    - [ ] Unclear what test is verifying

  structure:
    - [ ] No AAA pattern (Arrange-Act-Assert)
    - [ ] Mixed concerns (testing multiple things)
    - [ ] Complex setup hiding test intent

  brittleness:
    - [ ] Tests break when unrelated code changes
    - [ ] Testing implementation details
    - [ ] Tight coupling to mock structure
```

**Gate Function:**
```
BEFORE refactoring any test:
  FOR EACH smell identified:
    1. Read the test code completely
    2. Identify specific smell type
    3. Determine root cause
    4. Plan minimal refactoring to address

  If smell unclear → Read more, don't refactor yet
  If multiple smells → Address one at a time
  ONLY refactor what you understand
```

### Phase 3: Plan Refactoring

**For each smell, choose refactoring strategy:**

| Smell | Refactoring Strategy |
|-------|---------------------|
| **Duplicated setup** | Extract to test helper function |
| **Unclear names** | Rename following pattern: `Test{Function}_{Scenario}` |
| **Long tests** | Split into focused tests, one behavior each |
| **Magic values** | Extract to named constants |
| **Implementation coupling** | Test behavior through public API |
| **Brittle assertions** | Use behavior-focused assertions |

**Planning checklist:**
- [ ] Identified specific smell
- [ ] Chosen refactoring strategy
- [ ] Know what "good" looks like
- [ ] Can do refactoring in small steps
- [ ] Tests will stay green throughout

### Phase 4: Refactor in Small Steps

**CRITICAL:** Refactor incrementally, keep tests green.

**Process:**

1. **Make one small change**
   ```
   Example: Extract setup for one test to helper
   ```

2. **Run tests immediately**
   ```bash
   go test ./... -v
   ```

3. **Verify still green**
   - All tests pass?
   - Output pristine?

4. **Commit or continue**
   - If green → Commit or continue to next change
   - If red → Undo change, try smaller step

**Gate Function:**
```
AFTER each refactoring change:
  1. Run affected tests
  2. Verify all pass
  3. Check output pristine

  If tests fail:
    STOP - Undo change
    Understand why it failed
    Try smaller change

  If tests pass:
    Commit change (or continue if change too small)
    Move to next refactoring

  NEVER accumulate multiple changes without testing
```

**Example - Extract Test Helper:**

**Before:**
```go
func TestCreateUser_ValidEmail(t *testing.T) {
    mockRepo := new(MockUserRepository)
    mockEmail := new(MockEmailService)
    service := NewUserService(mockRepo, mockEmail)

    result, err := service.CreateUser("test@example.com")

    require.NoError(t, err)
    assert.NotNil(t, result)
}

func TestCreateUser_InvalidEmail(t *testing.T) {
    mockRepo := new(MockUserRepository)
    mockEmail := new(MockEmailService)
    service := NewUserService(mockRepo, mockEmail)

    result, err := service.CreateUser("invalid")

    require.Error(t, err)
    assert.Nil(t, result)
}
```

**Step 1: Extract helper**
```go
func setupUserService(t *testing.T) (*UserService, *MockUserRepository, *MockEmailService) {
    t.Helper()
    mockRepo := new(MockUserRepository)
    mockEmail := new(MockEmailService)
    service := NewUserService(mockRepo, mockEmail)
    return service, mockRepo, mockEmail
}
```

**Step 2: Run tests** → All green ✅

**Step 3: Refactor first test**
```go
func TestCreateUser_ValidEmail(t *testing.T) {
    service, _, _ := setupUserService(t)

    result, err := service.CreateUser("test@example.com")

    require.NoError(t, err)
    assert.NotNil(t, result)
}
```

**Step 4: Run tests** → All green ✅

**Step 5: Refactor second test**
```go
func TestCreateUser_InvalidEmail(t *testing.T) {
    service, _, _ := setupUserService(t)

    result, err := service.CreateUser("invalid")

    require.Error(t, err)
    assert.Nil(t, result)
}
```

**Step 6: Run tests** → All green ✅

### Phase 5: Verify Tests Still Pass

**MANDATORY:** Use `superpowers:verification-before-completion` principles.

**Gate Function:**
```
BEFORE claiming refactoring complete:
  1. Run FULL test suite (not just changed tests)
  2. Verify ALL tests pass
  3. Compare coverage before/after (should be same)
  4. Review refactored code for clarity

  Evidence before claims, ALWAYS
```

**Verification steps:**

1. **Run full test suite:**
   ```bash
   go test ./... -v
   ```

2. **Verify coverage unchanged:**
   ```bash
   go test -coverprofile=coverage.out ./...
   go tool cover -func=coverage.out
   ```

3. **Compare before/after:**
   ```yaml
   before_refactoring:
     tests_passing: 45/45
     coverage: 84.5%

   after_refactoring:
     tests_passing: 45/45  # Same ✅
     coverage: 84.5%       # Same ✅
   ```

4. **Code review:**
   - Are test names clearer?
   - Is duplication removed?
   - Are tests easier to understand?
   - Is AAA pattern followed?

**Red Flags - STOP:**
- Tests failing after refactoring → Undo and investigate
- Coverage decreased → Lost test behavior, undo
- Tests harder to understand → Revert refactoring
- Can't explain what changed → Too big, undo and split

### Phase 6: Clean Up

After refactoring, verify:

- [ ] No unused test helpers
- [ ] No commented-out tests
- [ ] No TODOs or FIXMEs introduced
- [ ] Test file organization is clear
- [ ] Related tests are grouped

## Refactoring Patterns by Language

### Go - Extract Test Helper

**Pattern:**
```go
func setupTestService(t *testing.T) (*Service, *MockDependency) {
    t.Helper()  // Mark as helper for better error reporting
    mock := new(MockDependency)
    service := NewService(mock)
    return service, mock
}

// Use in tests
func TestSomething(t *testing.T) {
    service, mock := setupTestService(t)
    // ... test logic
}
```

### Java - Use @BeforeEach

**Pattern:**
```java
class UserServiceTest {
    private UserService service;
    private MockUserRepository mockRepo;

    @BeforeEach
    void setUp() {
        mockRepo = mock(UserRepository.class);
        service = new UserService(mockRepo);
    }

    @Test
    void shouldCreateUser() {
        // Setup already done, focus on test logic
    }
}
```

### JavaScript/TypeScript - Extract Factory Functions

**Pattern:**
```typescript
describe('UserService', () => {
    const createTestService = () => ({
        service: new UserService(mockRepo),
        mockRepo: createMockRepository()
    });

    it('should create user', () => {
        const { service, mockRepo } = createTestService();
        // ... test logic
    });
});
```

### Python - Use Fixtures

**Pattern:**
```python
@pytest.fixture
def user_service():
    mock_repo = Mock(spec=UserRepository)
    return UserService(mock_repo), mock_repo

def test_create_user(user_service):
    service, mock_repo = user_service
    # ... test logic
```

### C++ - Extract Test Fixtures

**Pattern:**
```cpp
class UserServiceTest : public ::testing::Test {
protected:
    void SetUp() override {
        mockRepo = std::make_unique<MockUserRepository>();
        service = std::make_unique<UserService>(mockRepo.get());
    }

    std::unique_ptr<MockUserRepository> mockRepo;
    std::unique_ptr<UserService> service;
};

TEST_F(UserServiceTest, CreateUser) {
    // Setup already done via SetUp()
}
```

### Rust - Extract Helper Functions

**Pattern:**
```rust
#[cfg(test)]
mod tests {
    use super::*;

    fn setup_test_service() -> (UserService, MockRepository) {
        let mock_repo = MockRepository::new();
        let service = UserService::new(mock_repo.clone());
        (service, mock_repo)
    }

    #[test]
    fn test_create_user() {
        let (service, mock_repo) = setup_test_service();
        // ... test logic
    }
}
```

## Test Refactoring Anti-Patterns

### Anti-Pattern 1: "It's Just Test Code"

**Bad:**
"Test code doesn't need to be clean. It's not production code."

**Why wrong:**
- Tests are documentation of how code works
- Unmaintainable tests get deleted or ignored
- Brittle tests slow development
- Test quality reflects production code quality

**Good:**
Test code is first-class code. Same standards, same care.

### Anti-Pattern 2: Big Bang Refactoring

**Bad:**
Refactor all tests in file at once without running tests between changes.

**Why wrong:**
- Can't identify which change broke tests
- Accumulates multiple mistakes
- Hard to undo
- Violates incremental refactoring principle

**Good:**
One smell at a time. Run tests after each change. Commit frequently.

### Anti-Pattern 3: Refactoring Without Understanding

**Bad:**
"I'll just extract this to a helper without understanding what it does."

**Why wrong:**
- Might extract the wrong thing
- Could introduce bugs
- Loses test intent
- Makes tests harder to understand

**Good:**
Read and understand test completely before refactoring.

### Anti-Pattern 4: Over-Abstraction

**Bad:**
```go
func testHelper(t *testing.T, input string, expected Result, setupFunc func(),
                teardownFunc func(), mockConfig MockConfig) {
    // 50 lines of generic test logic
}
```

**Why wrong:**
- Helper is more complex than tests
- Hides test intent
- Hard to understand what each test does
- Makes debugging harder

**Good:**
Extract only clear, focused helpers. Keep test logic visible.

### Anti-Pattern 5: Changing Behavior During Refactoring

**Bad:**
"While refactoring, I'll also strengthen this assertion and add this edge case."

**Why wrong:**
- Mixing refactoring with behavior change
- Can't tell if tests fail due to refactoring or new assertions
- Violates single responsibility

**Good:**
Refactoring = structure only. Behavior changes are separate commits.

## Common Rationalizations (and Why They're Wrong)

| Excuse | Reality |
|--------|---------|
| "Test code doesn't need to be clean" | Tests are documentation. Unmaintainable tests get deleted. |
| "I'll refactor tests later" | Later never comes. Refactor now while context is fresh. |
| "Refactoring tests is risky" | Keeping brittle tests is riskier. Small steps keep tests green. |
| "It's faster to copy/paste" | Today yes, tomorrow no. Duplication slows future changes. |
| "Tests are working, don't touch them" | Working but unmaintainable = technical debt. |
| "I'll refactor when I have time" | You'll never have time. Make time now. |
| "Only production code needs refactoring" | Test code is first-class code. Same standards apply. |
| "Refactoring will break tests" | Only if done wrong. Small steps keep tests green. |
| "This test is too complex to refactor" | Complex test = smell. Start with small improvements. |
| "Production is down, refactor while fixing" | Fix bug first with TDD, then refactor. Never do both simultaneously. |

## Red Flags - STOP

If you catch yourself:
- Refactoring tests while they're failing
- Changing test behavior during refactoring
- Refactoring without running tests
- Saying "it's just test code"
- Accumulating multiple changes without testing
- Not understanding what test does
- Making tests more complex, not simpler
- Commenting out tests to make refactoring easier

**STOP. Go back to Phase 1.**

## Verification Checklist

Before claiming refactoring complete:

- [ ] All tests pass (VERIFIED: ran full suite)
- [ ] Coverage unchanged (before = after)
- [ ] Test names are clearer
- [ ] Duplication removed
- [ ] AAA pattern followed
- [ ] Tests easier to understand
- [ ] No commented-out code
- [ ] No TODOs introduced
- [ ] Changes committed with clear message

## When Stuck

| Problem | Solution |
|---------|----------|
| Don't know where to start | Start with most obvious duplication |
| Refactoring breaks tests | Undo, take smaller steps |
| Tests too complex to refactor | Start with small improvements (names, constants) |
| Uncertain if change is good | Ask: "Is test easier to understand now?" |
| Multiple smells overwhelming | Address one smell at a time |
| Tests failing after refactoring | Undo immediately, investigate, try smaller change |

## Final Rule

```
Test code = first-class code
Same quality standards
Same refactoring discipline
Small steps, stay green, verify constantly
```

**No exceptions. No "it's just test code." Evidence before claims, always.**

## Real-World Results

From applying test refactoring discipline:
- Reduced test duplication by 60%
- Test clarity improved (code review feedback)
- Adding new tests became easier
- Test maintenance time decreased
- Tests became documentation developers trust

**Key success factors:**
- Small incremental changes
- Running tests after each change
- Treating test code as first-class code
- Regular refactoring (not "later")

## Reference Documents

When refactoring tests, reference:
- **TDD Skill**: `.claude/skills/test-driven-development/SKILL.md`
- **Testing Anti-Patterns**: Superpowers testing-anti-patterns skill
- **Best Practices**: `docs/3-guides/ai-testing/best-practices.md`
