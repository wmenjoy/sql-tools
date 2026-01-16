---
name: test-review
description: "Use when reviewing PRs with test changes, before merging tests, or when tests need quality assessment - applies code review discipline to test code with systematic checklist"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash
---

# Test Review

## Overview

Test code requires the same review rigor as production code. Bad tests are worse than no tests.

**Core principle:** Test quality determines production quality. Review tests like you review production code.

**Violating the letter of this process is violating the spirit of testing.**

## The Iron Laws

```
1. NEVER approve tests without reviewing them
2. NEVER skip test review because "tests are passing"
3. NEVER accept tests that test mock behavior instead of real behavior
4. NEVER merge tests without verification (run them yourself)
```

## Definition: Test Review

**Test Review = Systematic quality assessment of test code before merging.**

**You MUST review tests when:**
- PR adds new test files
- PR modifies existing tests
- PR claims "added tests" in description
- PR increases test coverage
- Team member requests test review
- Before merging any code with test changes

**"Reviewing tests" does NOT mean:**
- ❌ Just checking tests pass in CI
- ❌ Skimming test names only
- ❌ Approving because "it's just tests"
- ❌ Trusting coverage numbers without reading tests

**ABSOLUTE REQUIREMENT:** Read test code completely. You cannot review tests by looking at CI status alone.

## VIOLATIONS (Automatic Skill Restart Required)

**Any of these = skill misuse, start over at Phase 1:**

❌ **Approving tests without reading them**
   - Example: "CI is green, tests look fine"
   - Violation: Must read test code

❌ **Accepting tests that test mocks**
   - Example: Test verifies mock exists, not real behavior
   - Violation: Tests must test real behavior

❌ **Skipping verification**
   - Example: "I trust they ran the tests"
   - Violation: Must run tests yourself

❌ **Approving based on coverage number alone**
   - Example: "Coverage increased to 85%, approve"
   - Violation: High coverage with bad tests = false confidence

❌ **Accepting unclear test names**
   - Example: "test1, test2, testMethod"
   - Violation: Names must describe behavior

❌ **Ignoring test anti-patterns**
   - Example: Test has duplication, magic numbers, no AAA structure
   - Violation: Request fixes before merge

**If you violate:** Reset to Phase 1. No shortcuts.

## When to Use This Skill

**Use when:**
- Reviewing PRs with test changes
- Before merging new tests
- Evaluating test quality
- Mentoring developers on testing
- Establishing testing standards
- Catching test anti-patterns early

**DON'T skip for:**
- "Small" test changes (bad tests are bad regardless of size)
- "Obvious" tests (obvious to author ≠ obvious to readers)
- "Just refactoring" (refactoring can break tests)
- Time pressure (bad tests slow you down later)

**Timing:**
- During PR review (before approval)
- Before merge (final check)
- After implementation (immediate feedback loop)
- Regular test audits (monthly/quarterly)

## The Test Review Process

### Phase 1: Read Test Code Completely

**ABSOLUTE REQUIREMENT:** You MUST read complete test code before reviewing.

**Gate Function:**
```
BEFORE reviewing any test:
  1. Have you read the complete test file?
  2. Do you understand what each test verifies?
  3. Can you explain test behavior in simple terms?
  4. Have you identified test dependencies?

  If NO to any → Read more, don't review yet
  If ALL YES → Proceed to checklist review
```

**What to read:**
```bash
# Find all test files in PR
git diff --name-only origin/main | grep -E "_test\.(go|java|ts|tsx|py|cpp|rs)$"

# Read each test file completely
Read file_path:path/to/test_file.go
```

**Understanding checklist:**
- [ ] Identified all test functions
- [ ] Understood what each test verifies
- [ ] Mapped tests to production code
- [ ] Identified test dependencies (mocks, fixtures)
- [ ] Noted test structure (AAA, naming, organization)

**Red Flags - STOP:**
- Can't understand what test does → Request clarification
- Test name doesn't match behavior → Request rename
- Test too complex to follow → Request simplification

### Phase 2: Systematic Checklist Review

**Use comprehensive review checklist:**

#### 2.1 Test Structure (AAA Pattern)

**Check each test follows Arrange-Act-Assert:**

```go
func TestExample(t *testing.T) {
    // ✅ Arrange - Setup is clear and minimal
    user := &User{Email: "test@example.com"}
    mockRepo := new(MockUserRepository)
    service := NewUserService(mockRepo)

    // ✅ Act - Single clear action
    result, err := service.CreateUser(user)

    // ✅ Assert - Clear expectations
    require.NoError(t, err)
    assert.Equal(t, "test@example.com", result.Email)
}
```

**Checklist:**
- [ ] Each test has clear Arrange section
- [ ] Act section has single behavior under test
- [ ] Assert section verifies expected outcome
- [ ] Sections are visually separated (blank lines or comments)

**Red Flags:**
- Mixed arrange/act/assert
- Multiple actions in one test
- Assertions scattered throughout
- Unclear what's being tested

#### 2.2 Test Naming

**Check test names describe behavior:**

**Good patterns:**
- Go: `Test{Function}_{Scenario}` → `TestCreateUser_RejectsInvalidEmail`
- Java: `should{Behavior}When{Condition}` → `shouldRejectUserWhenEmailInvalid`
- JS/TS: `should {behavior} when {condition}` → `should reject user when email invalid`

**Checklist:**
- [ ] Names describe WHAT not HOW
- [ ] Names include scenario/condition
- [ ] Names are readable (no abbreviations)
- [ ] Names indicate success/failure case
- [ ] Grouped tests share clear prefix

**Red Flags:**
- Generic names: `test1`, `testMethod`, `testUser`
- Implementation names: `testUserRepositorySave`
- Unclear scenarios: `testEdgeCase`, `testSpecial`
- Missing context: `testError` (which error?)

#### 2.3 FIRST Principles

**Verify tests follow FIRST:**

**F - Fast:**
- [ ] Tests run in milliseconds
- [ ] No real network calls
- [ ] No real database operations
- [ ] No file I/O (or in-memory)
- [ ] No `sleep()` calls

**I - Independent:**
- [ ] Tests can run in any order
- [ ] No shared global state
- [ ] Each test has own setup
- [ ] Tests don't depend on each other

**R - Repeatable:**
- [ ] Same result every time
- [ ] No random values (or fixed seed)
- [ ] No time dependencies (or mocked time)
- [ ] No external dependencies

**S - Self-Validating:**
- [ ] Clear pass/fail (no manual inspection)
- [ ] Assertions with meaningful messages
- [ ] No "verify output manually"

**T - Timely:**
- [ ] Tests written close to code
- [ ] Test covers new functionality
- [ ] Test covers bug fix (if bug)

#### 2.4 Test Coverage

**Check what test covers:**

**Checklist:**
- [ ] Happy path covered
- [ ] Error paths covered
- [ ] Edge cases covered (empty, nil, boundary)
- [ ] Security scenarios (if applicable)
- [ ] Concurrent scenarios (if applicable)

**Edge case examples:**
```yaml
edge_cases:
  numeric:
    - Zero value
    - Negative numbers
    - Boundary values (min/max)
    - Overflow scenarios

  strings:
    - Empty string
    - Very long string
    - Special characters
    - Unicode characters

  collections:
    - Empty collection
    - Single element
    - Large collection
    - Null/nil

  time:
    - Past dates
    - Future dates
    - Timezone boundaries
    - Leap year/seconds
```

**Red Flags:**
- Only happy path tested
- Error paths ignored
- Edge cases missing
- Security scenarios not considered

#### 2.5 Assertions Quality

**Check assertions are meaningful:**

**Good assertions:**
```go
// ✅ Specific assertion with message
assert.Equal(t, "test@example.com", user.Email, "User email should match input")

// ✅ Multiple focused assertions
require.NoError(t, err, "CreateUser should not return error for valid input")
assert.NotEmpty(t, user.ID, "Created user should have ID")
assert.False(t, user.IsAdmin, "New users should not be admin by default")
```

**Bad assertions:**
```go
// ❌ Vague assertion
assert.NotNil(t, result)

// ❌ No assertion message
assert.Equal(t, 5, result)

// ❌ Testing existence instead of behavior
assert.True(t, mockCalled)
```

**Checklist:**
- [ ] Assertions verify behavior (not implementation)
- [ ] Assertions have meaningful messages
- [ ] Multiple assertions are focused
- [ ] Assertions test expected values (not existence)
- [ ] Error assertions check error content

**Red Flags:**
- Assertions without messages
- Testing mock was called (not behavior)
- Vague assertions: `assert.NotNil`, `assert.True`
- No assertions (just calling code)

#### 2.6 Test Anti-Patterns

**Check for common anti-patterns:**

**Anti-Pattern 1: Testing Mock Behavior**
```typescript
// ❌ BAD: Testing mock exists
test('renders sidebar', () => {
    render(<Page />);
    expect(screen.getByTestId('sidebar-mock')).toBeInTheDocument();
});

// ✅ GOOD: Testing real behavior
test('renders sidebar', () => {
    render(<Page />);
    expect(screen.getByRole('navigation')).toBeInTheDocument();
});
```

**Anti-Pattern 2: Test-Only Methods in Production**
```java
// ❌ BAD: destroy() only for tests
class Session {
    public void destroy() { /* cleanup */ }  // Only tests use this
}

// ✅ GOOD: Test utilities handle cleanup
class TestUtils {
    static void cleanupSession(Session session) { /* cleanup */ }
}
```

**Anti-Pattern 3: Incomplete Mocks**
```python
# ❌ BAD: Partial mock
mock_response = {"status": "success", "data": {...}}  # Missing metadata

# ✅ GOOD: Complete mock matching real API
mock_response = {
    "status": "success",
    "data": {...},
    "metadata": {"requestId": "...", "timestamp": ...}
}
```

**Anti-Pattern 4: Over-Complex Tests**
```go
// ❌ BAD: Too many responsibilities
func TestUser(t *testing.T) {
    // Test create
    user, _ := service.Create(...)
    assert.NotNil(t, user)

    // Test update
    service.Update(user.ID, ...)

    // Test delete
    service.Delete(user.ID)
}

// ✅ GOOD: One behavior per test
func TestUser_Create_Success(t *testing.T) { /* create only */ }
func TestUser_Update_Success(t *testing.T) { /* update only */ }
func TestUser_Delete_Success(t *testing.T) { /* delete only */ }
```

**Checklist:**
- [ ] No testing mock behavior
- [ ] No test-only methods in production
- [ ] Complete mocks (not partial)
- [ ] One behavior per test
- [ ] No brittle assertions (implementation details)

#### 2.7 Test Maintenance

**Check tests are maintainable:**

**Checklist:**
- [ ] No duplicated setup code
- [ ] Test helpers are focused and clear
- [ ] Magic numbers extracted to constants
- [ ] Test data is representative
- [ ] Comments explain WHY (not what)

**Good maintenance patterns:**
```go
// ✅ GOOD: Extracted helper
func setupUserService(t *testing.T) (*UserService, *MockRepository) {
    t.Helper()
    mockRepo := new(MockRepository)
    service := NewUserService(mockRepo)
    return service, mockRepo
}

// ✅ GOOD: Named constants
const (
    testUserEmail    = "test@example.com"
    testAdminEmail   = "admin@example.com"
    maxRetryAttempts = 3
)
```

**Red Flags:**
- Duplicated setup in every test
- Magic numbers everywhere
- Copy/paste test code
- No helper functions
- Overly complex helpers

#### 2.8 Test Dependencies

**Check test dependencies are appropriate:**

**Checklist:**
- [ ] External services are mocked
- [ ] Database is mocked or in-memory
- [ ] Network calls are mocked
- [ ] File I/O is mocked or in-memory
- [ ] Time is mocked (if time-dependent)
- [ ] Random values use fixed seed

**Red Flags:**
- Real network calls in tests
- Real database connections
- Real file system operations
- Uncontrolled randomness
- Real time dependencies

### Phase 3: Run Tests Yourself

**MANDATORY:** Run tests locally before approving.

**Gate Function:**
```
BEFORE approving test changes:
  1. Have you checked out the PR branch?
  2. Have you run all tests?
  3. Do all tests pass?
  4. Is output pristine (no warnings)?

  Evidence before approval, ALWAYS
```

**Verification steps:**

1. **Check out PR branch:**
   ```bash
   git fetch origin pull/123/head:pr-123
   git checkout pr-123
   ```

2. **Run all tests:**
   ```bash
   # Go
   go test ./... -v

   # Java
   mvn test

   # JavaScript/TypeScript
   npm test

   # Python
   pytest

   # Rust
   cargo test

   # C++
   make test
   ```

3. **Run specific tests if needed:**
   ```bash
   # Run new tests only
   go test -run TestNewFeature ./...
   ```

4. **Check coverage:**
   ```bash
   # Go
   go test -coverprofile=coverage.out ./...
   go tool cover -func=coverage.out

   # Show coverage for changed files
   git diff --name-only origin/main | grep "\.go$" | xargs go test -cover
   ```

5. **Run with race detector:**
   ```bash
   go test -race ./...
   ```

**Verification checklist:**
- [ ] All tests pass locally
- [ ] No test warnings or errors
- [ ] Coverage report generated
- [ ] Coverage meets threshold
- [ ] No race conditions detected
- [ ] Tests run in reasonable time

**Red Flags - STOP:**
- Tests failing locally → Request fix before merge
- Warnings in test output → Request cleanup
- Tests very slow → Investigate why
- Race conditions detected → Must fix

### Phase 4: Provide Constructive Feedback

**Give specific, actionable feedback:**

#### Good Feedback Examples:

**Issue: Unclear test name**
```
❌ Bad feedback: "Rename test"
✅ Good feedback:
   "Test name `testUser` doesn't describe what it tests.
   Suggest: `TestCreateUser_RejectsInvalidEmail`
   This follows our naming pattern: Test{Function}_{Scenario}"
```

**Issue: Missing AAA structure**
```
❌ Bad feedback: "Fix structure"
✅ Good feedback:
   "Test doesn't follow AAA pattern. Add blank lines to separate:
   - Arrange: Lines 10-15 (setup)
   - Act: Line 17 (calling CreateUser)
   - Assert: Lines 19-21 (expectations)
   Reference: docs/3-guides/ai-testing/best-practices.md#aaa-pattern"
```

**Issue: Testing mock behavior**
```
❌ Bad feedback: "This is wrong"
✅ Good feedback:
   "Line 25: `expect(mockRepo.save).toHaveBeenCalled()`
   This tests the mock was called, not that user was created.
   Instead, assert on the actual result:
   `expect(result.id).toBeDefined()`
   `expect(result.email).toBe('test@example.com')`"
```

**Issue: Missing edge cases**
```
❌ Bad feedback: "Add more tests"
✅ Good feedback:
   "Tests cover happy path but missing edge cases:
   - Empty email string
   - Invalid email format
   - Duplicate email (already exists)
   - Null/undefined input
   Suggest adding 4 test cases for these scenarios."
```

#### Feedback Template:

```markdown
## Test Review Feedback

### Strengths
- Clear AAA structure in most tests
- Good test naming convention
- Comprehensive happy path coverage

### Issues Requiring Changes
1. **Test name unclear** (Line 45: `testMethod`)
   - Current: Generic name
   - Suggest: `TestCreateOrder_RejectsNegativeQuantity`
   - Reason: Names should describe behavior

2. **Missing error case** (No test for duplicate order ID)
   - Add: `TestCreateOrder_RejectsDuplicateID`
   - Verify: Error message and status code

3. **Magic number** (Line 67: `assert.Equal(t, 42, status)`)
   - Extract: `const StatusProcessing = 42`
   - Use: `assert.Equal(t, StatusProcessing, status)`

### Suggestions (Optional)
- Consider extracting setup to helper function (lines 12-20 repeated)
- Test names could include expected behavior: `_ReturnsOrderID`

### Verification
- [ ] All tests pass locally (verified)
- [ ] Coverage: 87% (meets 85% threshold)
- [ ] No race conditions detected
```

#### Feedback Principles:

**DO:**
- Be specific (line numbers, exact issue)
- Provide examples (show good vs bad)
- Explain WHY (help them learn)
- Reference documentation
- Acknowledge strengths
- Distinguish required vs optional changes

**DON'T:**
- Be vague ("fix tests")
- Be harsh ("this is terrible")
- Just point out problems (suggest solutions)
- Assume knowledge (explain standards)
- Focus only on negatives

### Phase 5: Approve or Request Changes

**Make clear decision:**

#### Approve When:
```
✅ All critical issues addressed:
   - Tests follow AAA pattern
   - Names are clear
   - Coverage adequate
   - No anti-patterns
   - Tests pass locally

✅ Minor suggestions can be addressed later
✅ Tests meet quality standards
```

#### Request Changes When:
```
❌ Any critical issue present:
   - Tests don't pass
   - Testing mock behavior
   - Missing critical scenarios
   - Security vulnerabilities
   - Anti-patterns present

❌ Test quality below standards
❌ Cannot understand what tests do
```

#### Comment for Discussion:
```
💬 When uncertain about approach:
   - Alternative testing strategy possible
   - Trade-off decisions needed
   - Architectural question
   - Team consensus required
```

**Decision template:**

```markdown
## Review Decision: [APPROVE / REQUEST CHANGES / COMMENT]

### Summary
[Brief overview of test changes and quality]

### Required Changes (if any)
1. [Specific issue with action needed]
2. [Specific issue with action needed]

### Next Steps
- [What author should do]
- [What you'll verify in next review]

### References
- [Link to relevant documentation]
- [Link to similar good tests]
```

## Test Quality Scoring

**Use objective quality metrics:**

### Quality Score Calculation:

```yaml
test_quality_score:
  structure: /25
    - AAA pattern: 10 points
    - Clear separation: 5 points
    - Single responsibility: 10 points

  naming: /20
    - Describes behavior: 10 points
    - Follows convention: 5 points
    - Includes scenario: 5 points

  coverage: /20
    - Happy path: 5 points
    - Error cases: 5 points
    - Edge cases: 5 points
    - Security: 5 points

  assertions: /15
    - Meaningful assertions: 10 points
    - Assertion messages: 5 points

  maintenance: /10
    - No duplication: 5 points
    - Clear helpers: 5 points

  independence: /10
    - No shared state: 5 points
    - Isolated setup: 5 points

total: /100
```

### Score Interpretation:

| Score | Grade | Action |
|-------|-------|--------|
| 90-100 | A | Approve immediately |
| 80-89 | B | Approve with minor suggestions |
| 70-79 | C | Request changes (non-critical) |
| 60-69 | D | Request changes (critical issues) |
| 0-59 | F | Reject, needs major rework |

**Example scoring:**

```markdown
## Test Quality Score: 82/100 (B)

### Breakdown:
- Structure: 20/25 (AAA mostly followed, one test mixed)
- Naming: 18/20 (Good names, one generic "testMethod")
- Coverage: 15/20 (Happy + errors, missing edge cases)
- Assertions: 12/15 (Good assertions, some missing messages)
- Maintenance: 8/10 (Little duplication, good helpers)
- Independence: 9/10 (Good isolation, one shared variable)

### Recommendation: APPROVE with suggestions
Minor issues can be addressed in follow-up
```

## Multi-Language Review Checklist

### Go Tests

**Specific checks:**
- [ ] Uses `testing` package
- [ ] Test files end with `_test.go`
- [ ] Test functions start with `Test`
- [ ] Uses `t.Helper()` for helper functions
- [ ] Uses `require` for must-pass assertions
- [ ] Uses `assert` for nice-to-have assertions
- [ ] Parallel tests marked with `t.Parallel()`
- [ ] Table-driven tests when appropriate

### Java Tests

**Specific checks:**
- [ ] Uses JUnit 5 (`@Test` annotation)
- [ ] Uses `@DisplayName` for readability
- [ ] Uses `@BeforeEach`/`@AfterEach` appropriately
- [ ] Uses `@ParameterizedTest` for parameterization
- [ ] Uses AssertJ for fluent assertions
- [ ] Uses Mockito correctly (verify, when, then)
- [ ] Test classes named `*Test`

### JavaScript/TypeScript Tests

**Specific checks:**
- [ ] Uses `describe`/`it` structure
- [ ] Uses `beforeEach`/`afterEach` for setup
- [ ] Uses `jest.fn()` for mocks
- [ ] Uses `expect` for assertions
- [ ] Async tests use `async/await`
- [ ] Uses `test.each` for parameterization
- [ ] React tests use Testing Library (not Enzyme)

### Python Tests

**Specific checks:**
- [ ] Uses `pytest` framework
- [ ] Test files start with `test_`
- [ ] Test functions start with `test_`
- [ ] Uses `pytest.fixture` for setup
- [ ] Uses `@pytest.mark.parametrize` for parameterization
- [ ] Uses `pytest.raises` for exception testing
- [ ] Uses `assert` statements (not `assertEqual`)

### C++ Tests

**Specific checks:**
- [ ] Uses Google Test framework
- [ ] Uses `TEST` or `TEST_F` macros
- [ ] Uses test fixtures for common setup
- [ ] Uses `EXPECT_*` for non-fatal assertions
- [ ] Uses `ASSERT_*` for fatal assertions
- [ ] Uses `INSTANTIATE_TEST_SUITE_P` for parameterization
- [ ] Mocks use Google Mock

### Rust Tests

**Specific checks:**
- [ ] Uses `#[cfg(test)]` module
- [ ] Test functions have `#[test]` attribute
- [ ] Uses `assert!`, `assert_eq!`, `assert_ne!`
- [ ] Uses `#[should_panic]` for panic tests
- [ ] Uses `Result<(), Box<dyn Error>>` for tests
- [ ] Uses `mockall` for mocking (if needed)
- [ ] Integration tests in `tests/` directory

## Test Review Anti-Patterns

### Anti-Pattern 1: "CI is Green" Approval

**Bad:**
Approving PR because CI shows all tests passing, without reading test code.

**Why wrong:**
- Tests might pass but test wrong things
- Coverage might be high but assertions weak
- Tests might be flaky
- Anti-patterns might be present

**Good:**
Read test code completely, understand what's tested, verify locally.

### Anti-Pattern 2: Nitpicking Without Impact

**Bad:**
Focusing on minor style issues while missing major test problems.

**Why wrong:**
- Wastes reviewer and author time
- Misses real quality issues
- Discourages testing

**Good:**
Prioritize: Critical issues (wrong behavior) > Important issues (anti-patterns) > Nice-to-haves (style).

### Anti-Pattern 3: "Looks Good to Me"

**Bad:**
Generic approval without specific feedback or verification.

**Why wrong:**
- No learning for author
- No evidence review was thorough
- Misses opportunity to improve

**Good:**
Specific feedback on what's good, what needs work, with examples.

### Anti-Pattern 4: Accepting Low Standards

**Bad:**
Approving tests that don't follow best practices "to move fast."

**Why wrong:**
- Technical debt accumulates
- Sets bad precedent
- Slows down future work
- Erodes quality culture

**Good:**
Maintain standards, explain why, help fix issues.

### Anti-Pattern 5: Not Running Tests

**Bad:**
Trusting author ran tests, not verifying locally.

**Why wrong:**
- Tests might not run on other machines
- Hidden dependencies might exist
- Race conditions might appear
- False confidence in quality

**Good:**
Always run tests locally before approval.

## Common Rationalizations (and Why They're Wrong)

| Excuse | Reality |
|--------|---------|
| "CI is green, tests are fine" | Green CI ≠ good tests. Must read test code. |
| "We need to move fast" | Bad tests slow you down. Quality now = speed later. |
| "Just small test changes" | Small bad tests = bad tests. Size doesn't matter. |
| "Author knows what they're doing" | Everyone makes mistakes. Review catches them. |
| "I trust they ran tests" | Trust but verify. Run tests yourself. |
| "Coverage increased, approve" | High coverage with bad tests = false confidence. |
| "Tests are passing, that's enough" | Passing tests might test wrong things. |
| "Too many PRs to review thoroughly" | Thorough reviews prevent future bugs. Worth the time. |
| "I don't understand tests well enough" | Ask questions, learn together, improve standards. |

## Red Flags - STOP

If you catch yourself:
- Approving without reading tests
- Skipping local verification
- Accepting unclear test names
- Ignoring anti-patterns
- Approving based on coverage alone
- Not providing specific feedback
- Saying "LGTM" without details
- Rushing review to "move fast"

**STOP. Go back to Phase 1.**

## Verification Checklist

Before approving test changes:

- [ ] Read all test code completely
- [ ] Verified AAA structure
- [ ] Checked test names describe behavior
- [ ] Verified FIRST principles
- [ ] Checked coverage (happy + error + edge)
- [ ] Verified assertions meaningful
- [ ] Checked for anti-patterns
- [ ] Ran tests locally (all pass)
- [ ] Checked coverage meets threshold
- [ ] Ran race detector (Go)
- [ ] Provided specific feedback
- [ ] Made clear approve/request changes decision

## When Stuck

| Problem | Solution |
|---------|----------|
| Don't understand what test does | Ask author to explain, suggest better names/comments |
| Uncertain if test is good enough | Use quality scoring, compare to examples |
| Conflicting feedback from reviewers | Discuss as team, establish consensus |
| Author disagrees with feedback | Explain reasoning, provide references, discuss trade-offs |
| Too many issues to list | Start with critical issues, group similar feedback |
| Tests failing locally but pass in CI | Investigate environment differences, may need fixes |

## Quick Reference - Review Priorities

**Critical (Must fix before merge):**
1. Tests fail
2. Testing mock behavior instead of real behavior
3. Missing critical scenarios (security, errors)
4. Anti-patterns that break tests
5. No AAA structure
6. Unclear what test verifies

**Important (Should fix before merge):**
1. Test names unclear
2. Missing edge cases
3. Magic numbers
4. Code duplication
5. Weak assertions
6. Missing assertion messages

**Nice-to-have (Can address later):**
1. Minor naming improvements
2. Extract helpers
3. Refactoring opportunities
4. Additional test cases
5. Documentation improvements

## Example Review Session

**PR**: "Add user registration tests"

### Step 1: Read Tests
```bash
$ git diff --name-only origin/main
internal/service/user_service_test.go

$ Read internal/service/user_service_test.go
# ... reading complete file
```

### Step 2: Apply Checklist

**Findings:**
✅ AAA structure followed
✅ Good test names
❌ Missing edge case: empty email
❌ Magic number on line 45
✅ Assertions clear
❌ No error case for duplicate email

### Step 3: Run Tests
```bash
$ go test ./internal/service -v
PASS
ok      internal/service    0.123s

$ go test -coverprofile=coverage.out ./internal/service
$ go tool cover -func=coverage.out | grep user_service.go
user_service.go:15:  CreateUser     85.0%
```

### Step 4: Provide Feedback

```markdown
## Test Review Feedback

### Strengths ✅
- Clear AAA structure throughout
- Good test naming: `TestCreateUser_{Scenario}`
- Comprehensive happy path coverage
- Tests pass locally

### Required Changes ❌

1. **Missing edge case: empty email** (No test)
   Add: `TestCreateUser_RejectsEmptyEmail`
   ```go
   func TestCreateUser_RejectsEmptyEmail(t *testing.T) {
       service := setupUserService(t)
       _, err := service.CreateUser("")
       require.Error(t, err)
       assert.Contains(t, err.Error(), "email required")
   }
   ```

2. **Magic number** (Line 45: `assert.Equal(t, 201, status)`)
   Extract: `const StatusCreated = 201`
   Use: `assert.Equal(t, StatusCreated, status)`

3. **Missing error case** (No test for duplicate email)
   Add: `TestCreateUser_RejectsDuplicateEmail`

### Quality Score: 82/100 (B)
- Structure: 25/25 ✅
- Naming: 20/20 ✅
- Coverage: 12/20 ⚠️ (Missing edge cases)
- Assertions: 15/15 ✅
- Maintenance: 10/10 ✅
- Independence: 10/10 ✅

### Decision: REQUEST CHANGES
Please address 3 required changes above. After changes, I'll re-review.

### References
- Best practices: docs/3-guides/ai-testing/best-practices.md
- Similar tests: internal/service/auth_service_test.go
```

### Step 5: Re-review After Changes

Author addresses feedback → Re-read tests → Run tests → Approve if satisfied

## Final Rule

```
Test code = production code
Same review rigor
Same quality standards
Evidence before approval, ALWAYS
```

**No shortcuts. No "CI is green." No "LGTM" without details.**

## Real-World Impact

From applying test review discipline:
- Caught 40% of test anti-patterns before merge
- Improved team testing skills through feedback
- Reduced flaky tests (caught early)
- Increased test maintainability
- Established consistent quality standards

**Success factors:**
- Systematic checklist use
- Running tests locally every time
- Specific, actionable feedback
- Maintaining standards consistently
- Teaching through reviews

## Reference Documents

When reviewing tests, reference:
- **TDD Skill**: `.claude/skills/test-driven-development/SKILL.md`
- **Testing Anti-Patterns**: Superpowers testing-anti-patterns skill
- **Test Refactoring**: `.claude/skills/test-refactoring/SKILL.md`
- **Flaky Tests**: `.claude/skills/flaky-test-detection/SKILL.md`
- **Best Practices**: `docs/3-guides/ai-testing/best-practices.md`
