---
name: test-generation-expert
description: "Generates comprehensive test cases for EXISTING code. Use when user asks to write tests, generate tests, improve test coverage for code that already exists. NOT for new code (use TDD instead)."
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash
  - Glob
  - Grep
---

# Test Generation Expert

## Overview

Generates comprehensive tests for EXISTING untested code. For NEW code, use TDD instead (write test first).

**Core principle:** Read and understand code completely before generating tests. Test behavior, not implementation.

**Violating the letter of this process is violating the spirit of testing.**

## The Iron Laws

```
1. NEVER generate tests without reading the source code first
2. NEVER generate tests for new code (use TDD instead)
3. NEVER claim tests are complete without running them
4. NEVER generate partial mocks (implement complete interface)
```

## Definition: "Reading Source Code First"

**Reading source code first = Reading the COMPLETE file NOW, regardless of prior familiarity.**

**You MUST read source code even if:**
- You wrote/refactored this file yesterday
- You "remember" the structure from code review
- File is "only ~200 lines"
- You're "familiar with this codebase"
- Time pressure (30 min, 15 min, 5 min until deadline)
- Authority pressure (manager/tech lead demanding tests NOW)

**"Read first" does NOT mean:**
- ❌ "I coded this yesterday, I remember it" (memory ≠ current state)
- ❌ "Quick glance at method signatures" (not complete read)
- ❌ "I'll infer from yesterday's refactoring" (inference = assumptions)
- ❌ "Reading takes 5 min I don't have" (no time exceptions)

**ABSOLUTE REQUIREMENT:** Read complete source file before EVERY test generation session. You cannot generate, estimate, or "quick smoke test" without reading current code state. No exceptions, no emergencies, no "I already know this code."

## VIOLATIONS (Automatic Skill Restart Required)

**Any of these = skill misuse, start over at Phase 1:**

❌ **Generating tests without reading source file**
   - Example: "I refactored this yesterday, I know the 4 methods"
   - Violation: Did not read file NOW

❌ **Using memory/inference instead of current code**
   - Example: "Based on yesterday's code review, I'll test Login/Logout/ValidateToken"
   - Violation: Memory from yesterday ≠ reading today

❌ **Skipping read to "save time"**
   - Example: "5 minutes until code freeze, I'll infer from refactoring"
   - Violation: Time pressure doesn't change requirement

❌ **Partial code reading**
   - Example: "Quick scan of method signatures is enough"
   - Violation: Must read COMPLETE file

❌ **Claiming tests complete without running them**
   - Example: "Generated 10 tests, should work, submitting PR"
   - Violation: Must run and verify all tests pass

❌ **Generating partial/incomplete mocks**
   - Example: "Mocked 2 methods I need, skipping other interface methods"
   - Violation: Must implement COMPLETE interface

**If you violate:** Reset to Phase 1. No shortcuts.

## When to Use This Skill

**Use for:**
- Existing code with NO tests
- Existing code with LOW coverage (identified by coverage-analyzer)
- Legacy code that needs test harness
- Code inherited from external sources

**DON'T use for:**
- New features (use `test-driven-development` instead)
- Code you're about to write (TDD required)
- Simple refactoring (keep existing tests)

**Integration with other skills:**
- Use `coverage-analyzer` to identify WHAT needs tests
- Use `test-generation-expert` (this skill) to generate HOW to test
- Use `test-driven-development` for ALL new code

## The Test Generation Process

### Phase 1: Read and Understand Code

**ABSOLUTE REQUIREMENT (Non-Negotiable):**

You MUST read complete source code before EVERY test generation session. This means:
- Read full source file NOW (not relying on memory)
- Must read current state (not yesterday's version)
- No quick scans, no method signature glances
- No "I already know this code" shortcuts
- No skipping reading to "save time"

**You CANNOT:**
- Generate tests based on memory from yesterday
- Infer structure from previous refactoring
- Quick-scan method signatures only
- Skip reading because "file is small"
- Use "I coded this" as excuse not to read
- Generate tests under time pressure without reading

**ONLY acceptable:** Read complete file, understand all functions, THEN generate tests.

**MANDATORY:** Always read source code before generating tests.

**Gate Function:**
```
BEFORE generating any test:
  1. Have I read the source file completely?
  2. Do I understand what each function does?
  3. Do I know the dependencies?
  4. Do I understand the error paths?

  If NO to any → Read more, don't generate yet
  If ALL YES → Proceed to scenario planning
```

**Steps:**

1. **Read the source file:**
   ```bash
   # Use Read tool to read complete source file
   Read file_path:/path/to/source.go
   ```

2. **Analyze structure:**
   ```yaml
   analysis:
     package: service
     dependencies:
       - repository.EnvironmentRepository
       - repository.EnvironmentVariableRepository
     public_functions:
       - CreateEnvironment(ctx, tenantID, projectID, req)
       - GetEnvironment(ctx, tenantID, projectID, envID)
       - DeleteEnvironment(ctx, tenantID, projectID, envID)
     error_handling:
       - Validates input
       - Checks for duplicates
       - Handles repository errors
```

3. **Identify test complexity:**
   - Simple (pure functions): Easy to test
   - Medium (service layer with mocks): Need dependency mocks
   - Complex (integration points): May need refactoring

**Red Flags - STOP:**
- Generating tests without reading code
- Assuming what code does
- Copying tests from similar functions without understanding

### Phase 2: Plan Test Scenarios

**Use systematic scenario planning:**

For each public function, identify scenarios using **FIRST principles**:
- **F**ast: Tests should run quickly
- **I**solated: Tests don't depend on each other
- **R**epeatable: Same result every time
- **S**elf-validating: Clear pass/fail
- **T**imely: Written close to code

**Scenario Planning Template:**

```yaml
function: CreateEnvironment(ctx, tenantID, projectID, req)

scenarios:
  happy_path:
    - name: "Success with valid input"
      description: Creates environment when all inputs valid
      arrange:
        - Valid tenantID, projectID, request
        - Repository returns no existing environment
        - Repository Create succeeds
      act: Call CreateEnvironment
      assert:
        - No error
        - Returns environment with correct ID
        - IsActive = false (new environments start inactive)

  error_handling:
    - name: "Duplicate environment ID"
      description: Returns error when environment already exists
      arrange:
        - Repository FindByID returns existing environment
      act: Call CreateEnvironment
      assert:
        - Returns error containing "already exists"
        - No environment created

    - name: "Repository error"
      description: Handles repository errors gracefully
      arrange:
        - Repository Create returns error
      act: Call CreateEnvironment
      assert:
        - Returns error
        - Error is propagated correctly

  edge_cases:
    - name: "Empty environment ID"
      description: Validates required fields
      arrange:
        - Request with empty EnvID
      act: Call CreateEnvironment
      assert:
        - Returns validation error

  security: # if applicable
    - name: "Tenant isolation"
      description: Ensures tenant data isolation
      arrange:
        - Different tenant ID
      act: Call GetEnvironment
      assert:
        - Cannot access other tenant's data
```

**Gate Function:**
```
BEFORE generating test code:
  1. Do I have scenarios for happy path?
  2. Do I have scenarios for each error path?
  3. Do I have edge case scenarios?
  4. Do scenarios cover ALL branches in code?

  If NO to any → Add missing scenarios
  If ALL YES → Proceed to code generation
```

### Phase 3: Generate Mock Implementations

**CRITICAL:** Generate COMPLETE mock implementations.

**Gate Function:**
```
BEFORE using a mock:
  1. Have I read the interface definition?
  2. Do I know ALL methods in interface?
  3. Have I implemented EVERY method?

  If NO to any → Read interface, implement all methods
  If ALL YES → Proceed with test generation
```

**Steps to create complete mocks:**

1. **Find interface definition:**
   ```bash
   grep -A 20 "type EnvironmentRepository interface" ./internal/repository/*.go
   ```

2. **Implement ALL methods:**
   ```go
   type MockEnvironmentRepository struct {
       mock.Mock
   }

   // Legacy methods (implement even if not using them)
   func (m *MockEnvironmentRepository) Create(ctx context.Context, env *models.Environment) error {
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

   // Multi-tenant methods
   func (m *MockEnvironmentRepository) CreateWithTenant(ctx context.Context, env *models.Environment) error {
       args := m.Called(ctx, env)
       return args.Error(0)
   }

   func (m *MockEnvironmentRepository) FindByIDWithTenant(ctx context.Context, envID, tenantID, projectID string) (*models.Environment, error) {
       args := m.Called(ctx, envID, tenantID, projectID)
       if args.Get(0) == nil {
           return nil, args.Error(1)
       }
       return args.Get(0).(*models.Environment), args.Error(1)
   }

   // ... ALL other interface methods
   ```

**Anti-Pattern:**
```go
// ❌ BAD: Incomplete mock
type MockEnvironmentRepository struct {
    mock.Mock
}

// Only implement methods I think I need
func (m *MockEnvironmentRepository) Create(...) error {
    ...
}
// Missing other interface methods → Compilation error
```

### Phase 4: Generate Test Code

**Follow language-specific patterns:**

**For Go - Use AAA Pattern:**
```go
func TestCreateEnvironment_Success(t *testing.T) {
    // Arrange
    mockRepo := new(MockEnvironmentRepository)
    mockVarRepo := new(MockEnvironmentVariableRepository)
    service := NewEnvironmentService(mockRepo, mockVarRepo)
    ctx := context.Background()
    tenantID := "tenant1"
    projectID := "project1"
    req := &CreateEnvironmentRequest{
        EnvID: "dev",
        Name:  "Development",
    }

    mockRepo.On("FindByIDWithTenant", ctx, "dev", tenantID, projectID).Return(nil, nil)
    mockRepo.On("CreateWithTenant", ctx, mock.AnythingOfType("*models.Environment")).Return(nil)

    // Act
    result, err := service.CreateEnvironment(ctx, tenantID, projectID, req)

    // Assert
    require.NoError(t, err)
    assert.Equal(t, "dev", result.EnvID)
    assert.Equal(t, "Development", result.Name)
    assert.False(t, result.IsActive, "New environment should not be active by default")
    mockRepo.AssertExpectations(t)
}
```

**Gate Function:**
```
BEFORE claiming test is complete:
  1. Does test follow AAA structure?
  2. Does test have clear, descriptive name?
  3. Are ALL mocks set up correctly?
  4. Are assertions testing behavior (not implementation)?
  5. Have I included assertion messages for clarity?

  If NO to any → Fix before moving on
  If ALL YES → Generate next test
```

**Test Naming Convention:**
- Go: `Test{Function}_{Scenario}` (e.g., `TestCreateEnvironment_Success`)
- Java: `should{Behavior}When{Condition}` (e.g., `shouldCreateEnvironmentWhenInputValid`)
- JS/TS: `should {behavior} when {condition}` (e.g., `should create environment when input valid`)

### Phase 5: Run and Verify Tests

**MANDATORY:** Use `superpowers:verification-before-completion` principles.

**Gate Function:**
```
BEFORE claiming tests are complete:
  1. Have I run the tests?
  2. Do ALL tests pass?
  3. Is output pristine (no errors/warnings)?
  4. Have I verified coverage increased?

  Evidence before claims, ALWAYS
```

**Verification steps:**

1. **Run tests:**
   ```bash
   go test -v ./internal/service -run TestEnvironmentService
   ```

2. **Verify output:**
   ```
   ✅ All tests PASS
   ✅ No build errors
   ✅ No warnings
   ```

3. **Check coverage:**
   ```bash
   go test -coverprofile=coverage.out ./internal/service
   go tool cover -func=coverage.out | grep environment_service.go
   ```

4. **Verify improvement:**
   ```
   Before: 0% coverage
   After:  84.6% coverage (VERIFIED)
   ```

**Red Flags - STOP:**
- Claiming tests work without running them
- Tests failing → Fix tests, don't proceed
- "Should work" → Run them, verify they work
- Using estimated coverage → Run coverage tool

### Phase 6: Report with Evidence

**Report format includes evidence:**

```markdown
## Generated Tests for environment_service.go

### Summary
- Generated 23 test cases
- Coverage: 84.6% (VERIFIED)
- All tests passing (VERIFIED)

### Test File
Created: `internal/service/environment_service_test.go`

### Scenarios Covered
| Test Name | Type | Description |
|-----------|------|-------------|
| TestCreateEnvironment_Success | happy_path | Creates environment successfully |
| TestCreateEnvironment_DuplicateID | error_handling | Rejects duplicate environment ID |
| TestCreateEnvironment_RepositoryError | error_handling | Handles repository errors |
| ... | ... | ... |

### Verification Evidence
```bash
$ go test -v ./internal/service -run TestEnvironmentService
PASS
ok      internal/service    0.123s

$ go test -coverprofile=coverage.out ./internal/service
$ go tool cover -func=coverage.out | grep environment_service.go
environment_service.go:15:  CreateEnvironment  100.0%
environment_service.go:45:  GetEnvironment     87.5%
...
Total: 84.6%
```

### Recommendations
- Consider adding concurrent access tests
- Add integration tests for workflow interaction
```

**Evidence required:**
- ✅ Actual test run output
- ✅ Coverage report output
- ✅ No estimates, only measured data

## Testing Anti-Patterns to Avoid

### Anti-Pattern 1: Testing Without Reading Code

**Bad:**
"Generate tests for UserService" → Generates generic tests without understanding UserService

**Why wrong:**
- Tests don't match actual behavior
- Miss critical edge cases
- Test wrong things

**Good:**
Read UserService → Understand each method → Plan scenarios → Generate tests

### Anti-Pattern 2: Incomplete Mock Interfaces

**Bad:**
```go
type MockRepository struct {
    mock.Mock
}

// Only 2 methods implemented, interface has 8
func (m *MockRepository) Create(...) error { ... }
func (m *MockRepository) FindByID(...) (..., error) { ... }
```

**Why wrong:**
- Compilation error when service calls other methods
- Wastes time debugging missing methods

**Good:**
- Read complete interface definition
- Implement ALL methods
- Verify compilation succeeds

### Anti-Pattern 3: Testing for Coverage, Not Behavior

**Bad:**
```go
func TestCreateUser(t *testing.T) {
    service.CreateUser(req)
    // No assertions - just calling code to hit lines
}
```

**Good:**
```go
func TestCreateUser_Success(t *testing.T) {
    result, err := service.CreateUser(req)

    require.NoError(t, err)
    assert.Equal(t, "user123", result.UserID)
    assert.NotEmpty(t, result.CreatedAt)
}
```

### Anti-Pattern 4: Claiming Completion Without Verification

**Bad:**
"I've generated 20 tests, coverage should be ~85%"

**Why wrong:**
- Tests might not compile
- Tests might fail
- Coverage estimate is wrong
- No evidence

**Good:**
"I've generated 20 tests, running them now..."
[Runs tests]
"All 20 tests pass. Coverage is 84.6% (verified)."

## Common Rationalizations (and Why They're Wrong)

| Excuse | Reality |
|--------|---------|
| "I don't need to read the code, I can infer" | Inference = assumptions = wrong tests |
| "Partial mock is fine for now" | Now = technical debt. Implement complete interface. |
| "Tests should work, no need to run them" | Should ≠ does. Always verify. |
| "85% is close enough to 90%" | Estimate ≠ measured. Run coverage tool. |
| "I'll run tests after generating all of them" | Generate → verify → repeat. Don't batch. |
| "This test is too hard to write" | Hard to test = bad design. Refactor first. |

## Red Flags - STOP

If you catch yourself:
- Generating tests without reading source code
- Creating partial mocks
- Skipping test verification
- Using estimated coverage numbers
- Claiming "tests should work"
- Not following AAA pattern
- Testing implementation instead of behavior

**STOP. Go back to Phase 1.**

## Multi-Language Support

### Go - testing + testify
```go
import (
    "testing"
    "github.com/stretchr/testify/assert"
    "github.com/stretchr/testify/require"
    "github.com/stretchr/testify/mock"
)

func TestFunction_Scenario(t *testing.T) {
    // Arrange
    mockRepo := new(MockRepository)
    service := NewService(mockRepo)

    // Act
    result, err := service.Function(param)

    // Assert
    require.NoError(t, err)
    assert.Equal(t, expected, result)
    mockRepo.AssertExpectations(t)
}
```

### Java - JUnit 5 + AssertJ + Mockito
```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Test
@DisplayName("Should behavior when condition")
void shouldBehaviorWhenCondition() {
    // Given
    when(mockRepository.findById(1L)).thenReturn(Optional.of(entity));

    // When
    Result result = service.doSomething(1L);

    // Then
    assertThat(result).isNotNull();
    assertThat(result.getValue()).isEqualTo(expected);
    verify(mockRepository).findById(1L);
}
```

### JavaScript/TypeScript - Jest
```typescript
describe('Module', () => {
    it('should behavior when condition', () => {
        // Arrange
        const mockRepo = {
            findById: jest.fn().mockResolvedValue({ id: 1 })
        };
        const service = new Service(mockRepo);

        // Act
        const result = await service.doSomething(1);

        // Assert
        expect(result).toEqual(expected);
        expect(mockRepo.findById).toHaveBeenCalledWith(1);
    });
});
```

### Python - pytest + pytest-mock
```python
import pytest
from unittest.mock import Mock, MagicMock

def test_function_scenario(mocker):
    # Arrange
    mock_repository = Mock()
    mock_repository.find_by_id.return_value = {"id": 1, "name": "test"}
    service = Service(mock_repository)

    # Act
    result = service.do_something(1)

    # Assert
    assert result is not None
    assert result["name"] == "test"
    mock_repository.find_by_id.assert_called_once_with(1)

# Alternative: Using pytest-mock
def test_with_pytest_mock(mocker):
    mock_repo = mocker.Mock()
    mock_repo.save.return_value = True

    service = Service(mock_repo)
    result = service.create({"name": "test"})

    assert result is True
    mock_repo.save.assert_called_once()
```

### C++ - Google Test + Google Mock
```cpp
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using ::testing::Return;
using ::testing::_;

class MockRepository : public IRepository {
public:
    MOCK_METHOD(Entity, FindById, (int id), (override));
    MOCK_METHOD(bool, Save, (const Entity& entity), (override));
};

TEST(ServiceTest, ShouldBehaviorWhenCondition) {
    // Arrange
    MockRepository mockRepo;
    EXPECT_CALL(mockRepo, FindById(1))
        .WillOnce(Return(Entity{1, "test"}));

    Service service(&mockRepo);

    // Act
    auto result = service.DoSomething(1);

    // Assert
    EXPECT_NE(result, nullptr);
    EXPECT_EQ(result->GetName(), "test");
}
```

### C - Unity Testing Framework
```c
#include "unity.h"
#include "module_under_test.h"
#include "mock_dependencies.h"

void setUp(void) {
    // Setup before each test
    mock_init();
}

void tearDown(void) {
    // Cleanup after each test
    mock_cleanup();
}

void test_function_should_return_success_when_valid_input(void) {
    // Arrange
    int input = 42;
    int expected = 100;

    // Act
    int result = function_under_test(input);

    // Assert
    TEST_ASSERT_EQUAL_INT(expected, result);
}

void test_function_should_return_error_when_null_pointer(void) {
    // Arrange & Act
    int result = function_under_test(NULL);

    // Assert
    TEST_ASSERT_EQUAL_INT(-1, result);
}
```

### Rust - Built-in testing + Mockall
```rust
#[cfg(test)]
mod tests {
    use super::*;
    use mockall::predicate::*;
    use mockall::mock;

    mock! {
        Repository {}

        impl IRepository for Repository {
            fn find_by_id(&self, id: u32) -> Option<Entity>;
            fn save(&mut self, entity: &Entity) -> bool;
        }
    }

    #[test]
    fn should_behavior_when_condition() {
        // Arrange
        let mut mock_repo = MockRepository::new();
        mock_repo
            .expect_find_by_id()
            .with(eq(1))
            .returning(|_| Some(Entity { id: 1, name: "test".to_string() }));

        let service = Service::new(mock_repo);

        // Act
        let result = service.do_something(1);

        // Assert
        assert!(result.is_some());
        assert_eq!(result.unwrap().name, "test");
    }

    #[test]
    #[should_panic(expected = "not found")]
    fn should_panic_when_not_found() {
        let service = Service::new(MockRepository::new());
        service.do_something(999); // Should panic
    }
}
```

### Vue - Vitest + Vue Test Utils
```typescript
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import UserComponent from './UserComponent.vue'

describe('UserComponent', () => {
    it('should display user name when user is loaded', async () => {
        // Arrange
        const mockUserService = {
            getUser: vi.fn().mockResolvedValue({ id: 1, name: 'John' })
        }

        const wrapper = mount(UserComponent, {
            global: {
                provide: {
                    userService: mockUserService
                }
            }
        })

        // Act
        await wrapper.vm.loadUser(1)
        await wrapper.vm.$nextTick()

        // Assert
        expect(wrapper.text()).toContain('John')
        expect(mockUserService.getUser).toHaveBeenCalledWith(1)
    })

    it('should emit event when button clicked', async () => {
        const wrapper = mount(UserComponent)

        await wrapper.find('button').trigger('click')

        expect(wrapper.emitted('user-clicked')).toBeTruthy()
    })
})
```

### React - Jest + React Testing Library
```typescript
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UserComponent } from './UserComponent'

describe('UserComponent', () => {
    it('should display user name when user is loaded', async () => {
        // Arrange
        const mockUserService = {
            getUser: jest.fn().mockResolvedValue({ id: 1, name: 'John' })
        }

        // Act
        render(<UserComponent userService={mockUserService} userId={1} />)

        // Assert
        await waitFor(() => {
            expect(screen.getByText('John')).toBeInTheDocument()
        })
        expect(mockUserService.getUser).toHaveBeenCalledWith(1)
    })

    it('should call onClick handler when button clicked', async () => {
        const handleClick = jest.fn()
        render(<UserComponent onClick={handleClick} />)

        const button = screen.getByRole('button')
        await userEvent.click(button)

        expect(handleClick).toHaveBeenCalledTimes(1)
    })

    it('should show error message when loading fails', async () => {
        const mockUserService = {
            getUser: jest.fn().mockRejectedValue(new Error('Not found'))
        }

        render(<UserComponent userService={mockUserService} userId={999} />)

        await waitFor(() => {
            expect(screen.getByText(/error/i)).toBeInTheDocument()
        })
    })
})
```

## Integration with Other Skills

**Test generation workflow:**

1. **For existing code:**
   - Use `coverage-analyzer` to identify gaps
   - Use `test-generation-expert` (this skill) to generate tests
   - Use `superpowers:verification-before-completion` to verify

2. **For new code:**
   - Use `test-driven-development` (NOT this skill)
   - Write test first
   - Watch it fail
   - Implement
   - Watch it pass

3. **If tests are hard to write:**
   - Consider refactoring code first
   - Use `superpowers:testing-anti-patterns` to avoid common mistakes
   - Simplify design to make testing easier

## Real-World Results from This Project

**environment_service.go:**
- Code: 229 lines, 11 public methods
- Generated: 23 tests in 15 minutes
- Coverage: 0% → 84.6%
- All tests passed on first run
- No compilation errors

**project_service.go:**
- Code: 250+ lines, 9 public methods
- Generated: 27 tests in 18 minutes
- Coverage: 0% → 78.5%
- All tests passed on first run
- Proper quota validation tested

**Success factors:**
- Read complete source code first
- Implemented complete mock interfaces
- Followed AAA pattern consistently
- Ran tests after generation
- Verified coverage with tools

## Quick Reference Checklist

### Before Generating:
- [ ] Read source code completely
- [ ] Understand each function's behavior
- [ ] Identify all dependencies
- [ ] Note error handling paths
- [ ] Check if this is existing code (yes → proceed, no → use TDD)

### During Generation:
- [ ] Plan scenarios (happy/error/edge)
- [ ] Find and read interface definitions
- [ ] Implement complete mocks (ALL methods)
- [ ] Follow AAA pattern
- [ ] Clear, descriptive test names
- [ ] Meaningful assertion messages

### After Generation:
- [ ] Run all tests
- [ ] All tests pass
- [ ] No compilation errors
- [ ] No warnings
- [ ] Run coverage tool
- [ ] Report actual metrics (not estimates)

## When Stuck

| Problem | Solution |
|---------|----------|
| Don't understand code | Read more carefully, ask questions |
| Mock interface incomplete | grep for interface definition, implement ALL methods |
| Tests not compiling | Check imports, verify mock methods |
| Tests failing | Debug using `superpowers:systematic-debugging` |
| Coverage not increasing | Verify tests actually run new code paths |
| Hard to test | Consider refactoring code first |

## Final Rule

```
Read code → Plan scenarios → Generate complete mocks → Write tests → Verify → Report evidence

No shortcuts. No assumptions. Evidence always.
```

## Reference Documents

When generating tests, reference project-specific guides:
- **Best Practices**: `docs/3-guides/ai-testing/best-practices.md`
- **TDD Skill**: `.claude/skills/test-driven-development/SKILL.md`
- **Coverage Analyzer**: `.claude/skills/coverage-analyzer/SKILL.md`
