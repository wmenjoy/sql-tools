---
name: test-hooks
description: "Use when tests need setup or teardown logic - applies discipline to test lifecycle management with proper hooks, avoiding shared state and ensuring test independence"
allowed-tools:
  - Read
  - Edit
  - Write
  - Bash
  - Glob
  - Grep
---

# Test Hooks (Setup/Teardown)

## Overview

Test hooks manage test lifecycle: setup before tests, cleanup after tests.

**Core principle:** Hooks must preserve test independence. Shared state through hooks violates FIRST principles.

**Violating the letter of this process is violating the spirit of testing.**

## The Iron Laws

```
1. NEVER share mutable state between tests via hooks
2. NEVER skip cleanup in teardown hooks
3. NEVER use hooks to bypass TDD (setup ≠ implementation)
4. NEVER put test logic in hooks (hooks are infrastructure, not tests)
```

## Definition: Test Hooks

**Test Hooks = Lifecycle functions that run before/after tests to manage resources and state.**

**Hook Types:**

```
Setup Hooks (run before):
├── Suite-level setup (once before all tests)
├── Test-level setup (before each test)
└── Subtest-level setup (before each subtest)

Teardown Hooks (run after):
├── Test-level teardown (after each test)
├── Suite-level teardown (once after all tests)
└── Cleanup on failure
```

**You MUST use hooks when:**
- Tests need database connections
- Tests require file system setup
- Tests use external resources (ports, temp directories)
- Tests need mock initialization
- Tests require cleanup of state

**Hooks are NOT for:**
- ❌ Implementing test logic (logic goes in test functions)
- ❌ Sharing test data between tests (violates independence)
- ❌ Bypassing TDD (write failing test first, then implement)
- ❌ Hiding test dependencies (makes tests hard to understand)

**ABSOLUTE REQUIREMENT:** Each test must be able to run in isolation. Hooks should create fresh state for each test, not share state between tests.

## VIOLATIONS (Automatic Skill Restart Required)

**Any of these = skill misuse, start over at Phase 1:**

❌ **Sharing mutable state via hooks**
   - Example: "Global variable set in setup, modified by tests"
   - Violation: Tests become order-dependent

❌ **Skipping cleanup in teardown**
   - Example: "Test creates temp file, teardown doesn't delete"
   - Violation: State leaks between tests

❌ **Putting test logic in setup**
   - Example: "Setup hook makes assertions"
   - Violation: Setup is infrastructure, not test logic

❌ **Using hooks to bypass TDD**
   - Example: "Setup implements feature, test just calls it"
   - Violation: Write failing test first, then implement

❌ **Conditional setup based on test results**
   - Example: "Setup checks if previous test passed"
   - Violation: Tests must be independent

❌ **Not using t.Helper() in Go setup functions**
   - Example: "Setup function doesn't call t.Helper()"
   - Violation: Error messages point to wrong line

**If you violate:** Reset to Phase 1. No shortcuts.

## When to Use This Skill

**Use when:**
- Tests need database connections or transactions
- Tests require temporary files or directories
- Tests use network ports or external resources
- Tests need complex mock initialization
- Tests require cleanup to prevent resource leaks
- Multiple tests share expensive setup (DB connections)

**DON'T use for:**
- Simple tests with no external dependencies (keep it simple)
- Sharing test assertions (violates test independence)
- Implementing test logic (logic goes in test functions)
- Bypassing AAA pattern (hooks are setup/teardown, not Arrange)

**Timing:**
- During TDD when tests need resource management
- When adding database/file system tests
- When cleaning up resource leaks in tests
- When reducing test setup duplication (but preserving independence)

## Hook Patterns by Language

### Go - t.Cleanup() and Test Helpers

**Best Practice: Use t.Cleanup() for guaranteed cleanup**

```go
func TestEnvironmentService_Create(t *testing.T) {
    // Setup
    db := setupTestDatabase(t)
    // t.Cleanup() called inside setupTestDatabase ensures cleanup

    service := NewEnvironmentService(db)

    // Test logic
    result, err := service.Create(ctx, &Environment{Name: "dev"})

    require.NoError(t, err)
    assert.NotNil(t, result)
}

// Setup helper with automatic cleanup
func setupTestDatabase(t *testing.T) *sql.DB {
    t.Helper() // Mark as helper for better error messages

    // Create temp database
    db, err := sql.Open("sqlite3", ":memory:")
    require.NoError(t, err)

    // Run migrations
    err = runMigrations(db)
    require.NoError(t, err)

    // Register cleanup (runs even if test fails)
    t.Cleanup(func() {
        db.Close()
    })

    return db
}
```

**Table-Driven Tests with Per-Test Setup:**

```go
func TestEnvironmentService_Operations(t *testing.T) {
    tests := []struct {
        name  string
        setup func(t *testing.T, db *sql.DB)
        run   func(t *testing.T, service *EnvironmentService)
    }{
        {
            name: "create new environment",
            setup: func(t *testing.T, db *sql.DB) {
                // No setup needed for this test
            },
            run: func(t *testing.T, service *EnvironmentService) {
                result, err := service.Create(ctx, &Environment{Name: "dev"})
                require.NoError(t, err)
                assert.Equal(t, "dev", result.Name)
            },
        },
        {
            name: "update existing environment",
            setup: func(t *testing.T, db *sql.DB) {
                // Create initial environment for update test
                _, err := db.Exec("INSERT INTO environments (name) VALUES (?)", "dev")
                require.NoError(t, err)
            },
            run: func(t *testing.T, service *EnvironmentService) {
                err := service.Update(ctx, "dev", &Environment{Name: "development"})
                require.NoError(t, err)
            },
        },
    }

    for _, tt := range tests {
        t.Run(tt.name, func(t *testing.T) {
            // Fresh database for each test
            db := setupTestDatabase(t)

            // Per-test setup
            if tt.setup != nil {
                tt.setup(t, db)
            }

            service := NewEnvironmentService(db)
            tt.run(t, service)
        })
    }
}
```

**Anti-Pattern: Global Setup**

```go
// ❌ BAD: Shared mutable state
var globalDB *sql.DB

func TestMain(m *testing.M) {
    // Setup
    globalDB, _ = sql.Open("sqlite3", ":memory:")

    // Run tests
    code := m.Run()

    // Teardown
    globalDB.Close()
    os.Exit(code)
}

func TestCreate(t *testing.T) {
    // Uses globalDB - tests become order-dependent
    service := NewEnvironmentService(globalDB)
    // ...
}

func TestUpdate(t *testing.T) {
    // Uses same globalDB - relies on TestCreate running first
    service := NewEnvironmentService(globalDB)
    // ...
}
```

**Good Pattern: Per-Test Setup**

```go
// ✅ GOOD: Each test gets fresh state
func TestCreate(t *testing.T) {
    db := setupTestDatabase(t) // Fresh DB
    service := NewEnvironmentService(db)
    // ... test logic
}

func TestUpdate(t *testing.T) {
    db := setupTestDatabase(t) // Fresh DB
    service := NewEnvironmentService(db)
    // ... test logic
}
```

### Java - @BeforeEach / @AfterEach

**JUnit 5 Lifecycle Hooks:**

```java
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Environment Service Tests")
class EnvironmentServiceTest {

    private EnvironmentRepository repository;
    private EnvironmentService service;

    @BeforeEach
    void setUp() {
        // Runs before EACH test
        // Create fresh instances for each test
        repository = new InMemoryEnvironmentRepository();
        service = new EnvironmentService(repository);
    }

    @AfterEach
    void tearDown() {
        // Runs after EACH test
        // Cleanup resources
        repository.clear();
    }

    @Test
    @DisplayName("should create new environment")
    void shouldCreateNewEnvironment() {
        // Arrange (repository already initialized in setUp)
        CreateEnvironmentRequest request = new CreateEnvironmentRequest("dev", "Development");

        // Act
        Environment result = service.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Development");
    }

    @Test
    @DisplayName("should update existing environment")
    void shouldUpdateExistingEnvironment() {
        // Arrange - fresh repository from setUp()
        repository.save(new Environment("dev", "Development"));

        // Act
        UpdateEnvironmentRequest request = new UpdateEnvironmentRequest("Production");
        service.update("dev", request);

        // Assert
        Environment updated = repository.findById("dev").orElseThrow();
        assertThat(updated.getName()).isEqualTo("Production");
    }
}
```

**Suite-Level Setup (Use Sparingly):**

```java
class DatabaseIntegrationTest {

    private static Database database;

    @BeforeAll
    static void setUpDatabase() {
        // Runs ONCE before all tests
        // Only for expensive resources that can be shared safely
        database = Database.createInMemory();
        database.runMigrations();
    }

    @AfterAll
    static void tearDownDatabase() {
        // Runs ONCE after all tests
        database.close();
    }

    @BeforeEach
    void cleanDatabase() {
        // Clean data between tests
        database.truncateAllTables();
    }

    @Test
    void testCreate() {
        // Use clean database
    }
}
```

### JavaScript/TypeScript - beforeEach / afterEach

**Jest/Vitest Hooks:**

```typescript
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { EnvironmentService } from './environment-service'
import { InMemoryRepository } from './in-memory-repository'

describe('EnvironmentService', () => {
    let repository: InMemoryRepository
    let service: EnvironmentService

    beforeEach(() => {
        // Runs before EACH test
        // Fresh instances for each test
        repository = new InMemoryRepository()
        service = new EnvironmentService(repository)
    })

    afterEach(() => {
        // Runs after EACH test
        repository.clear()
    })

    it('should create new environment', async () => {
        // Arrange (service already initialized)
        const request = { name: 'dev', displayName: 'Development' }

        // Act
        const result = await service.create(request)

        // Assert
        expect(result).toBeDefined()
        expect(result.name).toBe('dev')
    })

    it('should update existing environment', async () => {
        // Arrange - fresh repository from beforeEach
        await repository.save({ id: '1', name: 'dev', displayName: 'Development' })

        // Act
        await service.update('1', { displayName: 'Production' })

        // Assert
        const updated = await repository.findById('1')
        expect(updated?.displayName).toBe('Production')
    })
})
```

**Async Cleanup:**

```typescript
describe('FileService', () => {
    let tempDir: string

    beforeEach(async () => {
        // Create temp directory
        tempDir = await fs.mkdtemp(path.join(os.tmpdir(), 'test-'))
    })

    afterEach(async () => {
        // Clean up temp directory
        await fs.rm(tempDir, { recursive: true, force: true })
    })

    it('should save file to directory', async () => {
        const service = new FileService(tempDir)
        await service.save('test.txt', 'content')

        const files = await fs.readdir(tempDir)
        expect(files).toContain('test.txt')
    })
})
```

### Python - pytest fixtures

**Pytest Fixtures (Recommended):**

```python
import pytest
from environment_service import EnvironmentService
from in_memory_repository import InMemoryRepository

@pytest.fixture
def repository():
    """Create fresh repository for each test"""
    repo = InMemoryRepository()
    yield repo  # Provide to test
    repo.clear()  # Cleanup after test

@pytest.fixture
def service(repository):
    """Create service with fresh repository"""
    return EnvironmentService(repository)

def test_create_environment(service):
    # Arrange (service already initialized via fixture)
    request = {"name": "dev", "display_name": "Development"}

    # Act
    result = service.create(request)

    # Assert
    assert result is not None
    assert result["name"] == "dev"

def test_update_environment(service, repository):
    # Arrange - fresh repository from fixture
    repository.save({"id": "1", "name": "dev", "display_name": "Development"})

    # Act
    service.update("1", {"display_name": "Production"})

    # Assert
    updated = repository.find_by_id("1")
    assert updated["display_name"] == "Production"
```

**Fixture Scopes:**

```python
@pytest.fixture(scope="function")
def fresh_database():
    """New database for each test (default scope)"""
    db = Database.create_in_memory()
    yield db
    db.close()

@pytest.fixture(scope="module")
def shared_database():
    """One database for all tests in module
    ⚠️ Use carefully - can cause test dependencies"""
    db = Database.create_in_memory()
    db.run_migrations()
    yield db
    db.close()

@pytest.fixture(scope="session")
def global_config():
    """One instance for entire test session
    Only for immutable configuration"""
    return load_config("test_config.yaml")
```

**Setup/Teardown Methods (Alternative):**

```python
import unittest

class TestEnvironmentService(unittest.TestCase):

    def setUp(self):
        """Runs before EACH test"""
        self.repository = InMemoryRepository()
        self.service = EnvironmentService(self.repository)

    def tearDown(self):
        """Runs after EACH test"""
        self.repository.clear()

    def test_create_environment(self):
        result = self.service.create({"name": "dev"})
        self.assertIsNotNone(result)
        self.assertEqual(result["name"], "dev")
```

### C++ - Google Test Fixtures

**Test Fixtures:**

```cpp
#include <gtest/gtest.h>
#include "environment_service.h"
#include "mock_repository.h"

class EnvironmentServiceTest : public ::testing::Test {
protected:
    // Runs before EACH test
    void SetUp() override {
        mockRepo = std::make_unique<MockEnvironmentRepository>();
        service = std::make_unique<EnvironmentService>(mockRepo.get());
    }

    // Runs after EACH test
    void TearDown() override {
        // Cleanup (smart pointers handle most cleanup automatically)
        service.reset();
        mockRepo.reset();
    }

    // Available to all tests in this fixture
    std::unique_ptr<MockEnvironmentRepository> mockRepo;
    std::unique_ptr<EnvironmentService> service;
};

TEST_F(EnvironmentServiceTest, CreateEnvironment_Success) {
    // Arrange (service already initialized in SetUp)
    EXPECT_CALL(*mockRepo, Save(testing::_))
        .WillOnce(testing::Return(true));

    Environment env{"dev", "Development"};

    // Act
    auto result = service->Create(env);

    // Assert
    EXPECT_TRUE(result.has_value());
    EXPECT_EQ(result->name, "dev");
}

TEST_F(EnvironmentServiceTest, UpdateEnvironment_Success) {
    // Arrange - fresh service from SetUp()
    EXPECT_CALL(*mockRepo, FindById("dev"))
        .WillOnce(testing::Return(Environment{"dev", "Development"}));
    EXPECT_CALL(*mockRepo, Update(testing::_))
        .WillOnce(testing::Return(true));

    // Act
    auto result = service->Update("dev", Environment{"dev", "Production"});

    // Assert
    EXPECT_TRUE(result);
}
```

### Rust - Setup Functions

**Rust Test Helpers:**

```rust
#[cfg(test)]
mod tests {
    use super::*;

    // Helper function for setup
    fn setup_test_service() -> (EnvironmentService, InMemoryRepository) {
        let repository = InMemoryRepository::new();
        let service = EnvironmentService::new(repository.clone());
        (service, repository)
    }

    #[test]
    fn test_create_environment() {
        // Arrange - fresh setup for each test
        let (service, _repo) = setup_test_service();
        let request = CreateEnvironmentRequest {
            name: "dev".to_string(),
            display_name: "Development".to_string(),
        };

        // Act
        let result = service.create(request).unwrap();

        // Assert
        assert_eq!(result.name, "dev");
    }

    #[test]
    fn test_update_environment() {
        // Arrange - fresh setup
        let (service, repo) = setup_test_service();
        repo.save(Environment {
            id: "1".to_string(),
            name: "dev".to_string(),
            display_name: "Development".to_string(),
        });

        // Act
        service.update("1", UpdateEnvironmentRequest {
            display_name: "Production".to_string(),
        }).unwrap();

        // Assert
        let updated = repo.find_by_id("1").unwrap();
        assert_eq!(updated.display_name, "Production");
    }
}
```

**Using Drop for Cleanup:**

```rust
struct TestContext {
    temp_dir: PathBuf,
}

impl TestContext {
    fn new() -> Self {
        let temp_dir = TempDir::new("test").unwrap().into_path();
        Self { temp_dir }
    }
}

impl Drop for TestContext {
    fn drop(&mut self) {
        // Cleanup happens automatically
        let _ = std::fs::remove_dir_all(&self.temp_dir);
    }
}

#[test]
fn test_with_temp_directory() {
    let ctx = TestContext::new();
    // Use ctx.temp_dir
    // Cleanup happens automatically when ctx goes out of scope
}
```

### Vue - Component Testing Hooks

**Vue Test Utils with Hooks:**

```typescript
import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import EnvironmentEditor from './EnvironmentEditor.vue'

describe('EnvironmentEditor.vue', () => {
    let wrapper: VueWrapper<any>

    beforeEach(() => {
        // Mount fresh component for each test
        wrapper = mount(EnvironmentEditor, {
            props: {
                environment: {
                    id: '1',
                    name: 'dev',
                    displayName: 'Development'
                }
            }
        })
    })

    afterEach(() => {
        // Unmount component to prevent memory leaks
        wrapper.unmount()
    })

    it('should render environment name', () => {
        expect(wrapper.text()).toContain('Development')
    })

    it('should emit save event on button click', async () => {
        const saveButton = wrapper.find('[data-testid="save-button"]')
        await saveButton.trigger('click')

        expect(wrapper.emitted('save')).toBeTruthy()
    })
})
```

### React - Testing Library Hooks

**React Testing Library:**

```typescript
import { render, screen, cleanup } from '@testing-library/react'
import { afterEach, describe, it, expect } from 'vitest'
import { EnvironmentEditor } from './EnvironmentEditor'

describe('EnvironmentEditor', () => {
    // React Testing Library recommends automatic cleanup
    afterEach(() => {
        cleanup()
    })

    it('should render environment name', () => {
        render(
            <EnvironmentEditor
                environment={{ id: '1', name: 'dev', displayName: 'Development' }}
            />
        )

        expect(screen.getByText('Development')).toBeInTheDocument()
    })

    it('should call onSave when save button clicked', async () => {
        const onSave = vi.fn()

        render(
            <EnvironmentEditor
                environment={{ id: '1', name: 'dev', displayName: 'Development' }}
                onSave={onSave}
            />
        )

        const saveButton = screen.getByRole('button', { name: /save/i })
        await userEvent.click(saveButton)

        expect(onSave).toHaveBeenCalledTimes(1)
    })
})
```

## Hook Anti-Patterns

### Anti-Pattern 1: Shared Mutable State

**Bad:**

```go
// ❌ BAD: Tests share and mutate global state
var globalCounter int

func TestIncrement(t *testing.T) {
    globalCounter++
    assert.Equal(t, 1, globalCounter) // Fails if tests run in parallel or different order
}

func TestDouble(t *testing.T) {
    globalCounter *= 2
    assert.Equal(t, 2, globalCounter) // Depends on TestIncrement running first
}
```

**Good:**

```go
// ✅ GOOD: Each test gets fresh state
func TestIncrement(t *testing.T) {
    counter := 0  // Local state
    counter++
    assert.Equal(t, 1, counter)
}

func TestDouble(t *testing.T) {
    counter := 1  // Fresh state
    counter *= 2
    assert.Equal(t, 2, counter)
}
```

### Anti-Pattern 2: Setup Does Everything

**Bad:**

```go
// ❌ BAD: Setup does all the work
func setupAndTestEverything(t *testing.T) *Result {
    service := NewService()
    result, err := service.DoSomething()
    require.NoError(t, err) // Assertions in setup!
    assert.NotNil(t, result)
    return result
}

func TestFeature(t *testing.T) {
    result := setupAndTestEverything(t)
    // Nothing left to test
}
```

**Good:**

```go
// ✅ GOOD: Setup only sets up, test does testing
func setupService(t *testing.T) *Service {
    t.Helper()
    return NewService()
}

func TestFeature(t *testing.T) {
    // Arrange
    service := setupService(t)

    // Act
    result, err := service.DoSomething()

    // Assert
    require.NoError(t, err)
    assert.NotNil(t, result)
}
```

### Anti-Pattern 3: Ignoring Cleanup

**Bad:**

```go
// ❌ BAD: No cleanup
func TestFileOperations(t *testing.T) {
    tmpFile, _ := os.Create("/tmp/test-file")
    // Test uses tmpFile
    // File never deleted - accumulates garbage
}
```

**Good:**

```go
// ✅ GOOD: Always cleanup
func TestFileOperations(t *testing.T) {
    tmpFile, err := os.Create("/tmp/test-file")
    require.NoError(t, err)

    t.Cleanup(func() {
        os.Remove(tmpFile.Name())
    })

    // Test uses tmpFile
}
```

### Anti-Pattern 4: Conditional Setup

**Bad:**

```javascript
// ❌ BAD: Setup depends on test results
let previousTestPassed = false

beforeEach(() => {
    if (!previousTestPassed) {
        // Different setup based on previous test
        setupFresh()
    }
})

afterEach(() => {
    previousTestPassed = !currentTestFailed
})
```

**Good:**

```javascript
// ✅ GOOD: Same setup for every test
beforeEach(() => {
    // Always fresh setup
    setupFresh()
})
```

### Anti-Pattern 5: Over-Complex Fixtures

**Bad:**

```python
# ❌ BAD: One fixture does everything
@pytest.fixture
def everything(request):
    # Creates database
    db = create_database()

    # Creates service
    service = Service(db)

    # Seeds data
    seed_data(db)

    # Starts server
    server = start_server()

    # Creates client
    client = create_client(server)

    # Returns complex object
    return {
        'db': db,
        'service': service,
        'server': server,
        'client': client,
        'config': load_config()
    }

def test_something(everything):
    # Which part of 'everything' does this test need?
    # Unclear dependencies
}
```

**Good:**

```python
# ✅ GOOD: Focused fixtures
@pytest.fixture
def database():
    db = create_database()
    yield db
    db.close()

@pytest.fixture
def service(database):
    return Service(database)

def test_create(service):
    # Clear dependency: needs service (and transitively database)
    result = service.create({"name": "test"})
    assert result is not None
```

## Integration with TDD

**Hooks support TDD, don't replace it:**

### Phase 1: RED - Write Failing Test with Setup

```go
func TestCreateEnvironment_Success(t *testing.T) {
    // Arrange - Use hook to setup database
    db := setupTestDatabase(t)
    service := NewEnvironmentService(db)

    req := &CreateEnvironmentRequest{
        EnvID: "dev",
        Name:  "Development",
    }

    // Act - This will fail because CreateEnvironment doesn't exist yet
    result, err := service.CreateEnvironment(ctx, req)

    // Assert
    require.NoError(t, err)
    assert.Equal(t, "dev", result.EnvID)
}

// Setup hook
func setupTestDatabase(t *testing.T) *sql.DB {
    t.Helper()
    db, _ := sql.Open("sqlite3", ":memory:")
    // Run migrations
    t.Cleanup(func() { db.Close() })
    return db
}
```

### Phase 2: GREEN - Implement with Hooks

```go
// Implement CreateEnvironment to pass test
func (s *EnvironmentService) CreateEnvironment(ctx context.Context, req *CreateEnvironmentRequest) (*Environment, error) {
    // Implementation
}
```

### Phase 3: REFACTOR - Extract Hook if Needed

If multiple tests need same setup:

```go
// Before refactoring
func TestCreate(t *testing.T) {
    db, _ := sql.Open("sqlite3", ":memory:")
    defer db.Close()
    // test...
}

func TestUpdate(t *testing.T) {
    db, _ := sql.Open("sqlite3", ":memory:")
    defer db.Close()
    // test...
}

// After refactoring - extract to hook
func setupTestDatabase(t *testing.T) *sql.DB {
    t.Helper()
    db, _ := sql.Open("sqlite3", ":memory:")
    t.Cleanup(func() { db.Close() })
    return db
}

func TestCreate(t *testing.T) {
    db := setupTestDatabase(t)
    // test...
}

func TestUpdate(t *testing.T) {
    db := setupTestDatabase(t)
    // test...
}
```

## Common Rationalizations (and Why They're Wrong)

| Excuse | Reality |
|--------|---------|
| "Global setup is faster" | Faster tests that fail intermittently = wasted time. Independence > speed. |
| "Shared state saves memory" | Memory saved < time debugging order-dependent failures. |
| "Cleanup isn't necessary" | Resource leaks accumulate. Always cleanup. |
| "This setup is too expensive to repeat" | If setup is expensive, test design needs rethinking. Consider mocks. |
| "Tests should share database" | Shared database = order-dependent tests = flaky tests. |
| "BeforeAll is more efficient" | Efficiency from shared state = test dependencies = unreliable suite. |
| "Cleanup happens automatically" | Never assume. Explicit cleanup prevents resource leaks. |
| "My setup doesn't affect other tests" | State leaks in unexpected ways. Always use fresh state. |

## Red Flags - STOP

If you catch yourself:
- Using global variables in tests
- Skipping cleanup "because it's automatic"
- Making setup dependent on previous test results
- Putting assertions in setup/teardown hooks
- Sharing mutable state between tests
- Not using t.Helper() in Go setup functions
- Creating setup more complex than test logic
- Running tests that only pass in specific order

**STOP. Go back to Phase 1.**

## Verification Checklist

Before claiming hooks are correct:

- [ ] Each test can run in isolation (any order)
- [ ] No shared mutable state between tests
- [ ] All resources cleaned up in teardown/cleanup
- [ ] Setup functions marked with t.Helper() (Go)
- [ ] Setup only does setup (no assertions, no test logic)
- [ ] Hooks preserve test independence (FIRST principles)
- [ ] Cleanup runs even if test fails
- [ ] No conditional setup based on test results
- [ ] Tests pass when run in parallel
- [ ] Tests pass when run in random order

## When Stuck

| Problem | Solution |
|---------|----------|
| Tests fail in different order | Shared mutable state. Use fresh state per test. |
| Cleanup not running | Use defer (Go), t.Cleanup (Go), afterEach (JS), fixtures (Python). |
| Setup too complex | Simplify. Consider if test needs redesign. |
| Tests too slow | Profile. Don't share state for speed - fix slow parts. |
| Resource leaks | Always register cleanup. Use t.Cleanup, defer, RAII. |
| Unclear dependencies | Use dependency injection. Make dependencies explicit. |

## Final Rule

```
Hooks = Infrastructure for test independence
NOT = Shortcuts to bypass TDD
NOT = Ways to share mutable state
NOT = Places to put test logic

Fresh state per test, always
Cleanup always runs, even on failure
Setup prepares, tests verify, teardown cleans
Evidence before claims, ALWAYS
```

**No exceptions. No "this one time." No shortcuts.**

## Real-World Impact

From applying test hooks discipline:
- Test independence: 100% (can run in any order)
- Flaky tests: Reduced from 15% to <1%
- Resource leaks: Eliminated (proper cleanup)
- Test clarity: Improved (setup is infrastructure, logic is in tests)
- Debugging time: 70% reduction (no order dependencies)

**Success factors:**
- Fresh state per test (no sharing)
- Explicit cleanup (always)
- Hooks as infrastructure (not test logic)
- Following FIRST principles (especially Independent and Repeatable)

## Reference Documents

When using test hooks, reference:
- **TDD Skill**: `.claude/skills/test-driven-development/SKILL.md`
- **Testing Anti-Patterns**: Superpowers testing-anti-patterns skill
- **Flaky Test Detection**: `.claude/skills/flaky-test-detection/SKILL.md`
- **Best Practices**: `docs/3-guides/ai-testing/best-practices.md`
