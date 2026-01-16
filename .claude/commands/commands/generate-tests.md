# Test Generation Command

Generates comprehensive test cases for EXISTING untested code. For NEW code, use TDD instead.

**IMPORTANT:** This command invokes the `test-generation-expert` skill. Always read code before generating tests.

## Iron Laws

```
1. NEVER generate tests without reading source code first
2. NEVER generate tests for new code (use TDD instead)
3. NEVER claim tests complete without running them
4. NEVER generate partial mocks (implement complete interface)
```

## Usage

```bash
/generate-tests [options]
```

## Options

- `--file <path>`: Target source file path (REQUIRED)
- `--function <name>`: Specific function (optional, generates all if not specified)
- `--language <lang>`: Language (go/java/javascript/typescript/python/cpp/c/rust/vue/react), auto-detected
- `--coverage <percent>`: Target coverage (default: 85%)
- `--include-security`: Include security tests
- `--output <path>`: Output file path (auto-generated if not specified)

## Prerequisites

Before running:
- ✅ Target code MUST already exist
- ✅ Code compiles successfully
- ✅ Dependencies installed
- ✅ For new code, use TDD instead (do NOT use this command)

## Execution Process

**This command follows the 6-phase test generation process from the test-generation-expert skill:**

### Phase 1: Read and Understand Code

**MANDATORY:** Read source code completely before generating tests.

**Steps:**
1. Read the complete source file
2. Analyze structure:
   - Identify all public functions/methods
   - Understand parameters and return types
   - Map dependencies
   - Identify error handling patterns

3. Create analysis summary:
```yaml
analysis:
  file: {file_path}
  language: {language}
  package: {package_name}
  dependencies:
    - repository.Interface1
    - repository.Interface2
  public_functions:
    - Function1(ctx, param1, param2)
    - Function2(ctx, id)
  complexity: {medium/high}
```

### Phase 2: Plan Test Scenarios

For each function, plan scenarios using **FIRST principles**:
- **F**ast: Tests run quickly
- **I**solated: Independent tests
- **R**epeatable: Same result every time
- **S**elf-validating: Clear pass/fail
- **T**imely: Written close to code

**Scenarios to cover:**
1. **Happy Path**: Valid inputs → successful results
2. **Error Handling**: Invalid inputs, dependency failures
3. **Edge Cases**: nil/null, boundaries, empty strings
4. **Security** (if --include-security): Injection, validation

### Phase 3: Generate Complete Mock Implementations

**CRITICAL:** Implement COMPLETE mock interfaces.

**Steps:**
1. Find interface definition:
   ```bash
   grep -A 20 "type RepositoryInterface interface" ./internal/repository/*.go
   ```

2. Implement ALL methods (not just the ones you think you need):
   ```go
   type MockRepository struct {
       mock.Mock
   }

   // ALL interface methods implemented
   func (m *MockRepository) Create(...) error { ... }
   func (m *MockRepository) FindByID(...) (..., error) { ... }
   // ... ALL other methods
   ```

### Phase 4: Generate Test Code

**Follow AAA pattern (Arrange-Act-Assert):**

**Go Example:**
```go
func TestCreateEnvironment_Success(t *testing.T) {
    // Arrange
    mockRepo := new(MockEnvironmentRepository)
    service := NewEnvironmentService(mockRepo)
    ctx := context.Background()
    req := &CreateRequest{ID: "test"}

    mockRepo.On("FindByID", ctx, "test").Return(nil, nil)
    mockRepo.On("Create", ctx, mock.AnythingOfType("*models.Environment")).Return(nil)

    // Act
    result, err := service.Create(ctx, req)

    // Assert
    require.NoError(t, err)
    assert.Equal(t, "test", result.ID)
    mockRepo.AssertExpectations(t)
}
```

**Test Naming:**
- Go: `Test{Function}_{Scenario}`
- Java: `should{Behavior}When{Condition}`
- JS/TS: `should {behavior} when {condition}`

### Phase 5: Run and Verify Tests

**MANDATORY verification:**

1. **Run tests:**
   ```bash
   go test -v ./path/to/package -run TestFunction
   ```

2. **Verify output:**
   - ✅ All tests PASS
   - ✅ No compilation errors
   - ✅ No warnings

3. **Check coverage:**
   ```bash
   go test -coverprofile=coverage.out ./path/to/package
   go tool cover -func=coverage.out | grep target_file.go
   ```

4. **Verify improvement:**
   ```
   Before: {X}% coverage
   After: {Y}% coverage (VERIFIED)
   ```

### Phase 6: Report with Evidence

**Evidence-based reporting:**

```markdown
## Generated Tests for {file}

### Summary
- Generated {N} test cases
- Coverage: {X}% (VERIFIED by go tool cover -func)
- All tests passing (VERIFIED by go test)

### Test File
Created: `{test_file_path}`

### Scenarios Covered
| Test Name | Type | Description |
|-----------|------|-------------|
| TestCreate_Success | happy_path | Creates entity successfully |
| TestCreate_DuplicateID | error_handling | Rejects duplicate |
| ... | ... | ... |

### Verification Evidence
```bash
$ go test -v ./internal/service -run TestEnvironmentService
PASS
ok      internal/service    0.123s

$ go test -coverprofile=coverage.out ./internal/service
$ go tool cover -func=coverage.out | grep environment_service.go
Total: 84.6%
```

### Recommendations
- Consider adding concurrent access tests
- Integration tests for external dependencies
```

## Examples

### Example 1: Generate tests for service
```bash
/generate-tests --file internal/service/environment_service.go --coverage 85
```

**Output:**
- Reads complete source file
- Generates 20+ tests with complete mocks
- Runs tests to verify
- Reports actual coverage with evidence

### Example 2: Generate tests for specific function
```bash
/generate-tests --file internal/auth/service.go --function Login --include-security
```

**Output:**
- Focuses on Login function only
- Includes security test scenarios
- Complete mock implementations
- Verification evidence

### Example 3: Generate with custom output
```bash
/generate-tests --file src/services/user.ts --output src/services/user.test.ts
```

**Output:**
- Generates TypeScript/Jest tests
- Writes to specified file
- Verifies compilation and execution

## Red Flags - Command Will STOP

The command will halt if:
- Target file doesn't exist
- Code doesn't compile
- This is for NEW code (use TDD instead)
- Trying to generate tests before reading code

## Integration with Other Skills/Commands

**Complete testing workflow:**

1. **For existing code:**
   - Use `/analyze-coverage` to identify gaps
   - Use `/generate-tests` (this command) for gaps
   - Verify with fresh coverage run

2. **For new code:**
   - Use `test-driven-development` skill (NOT this command)
   - Write test first
   - Implement feature
   - Verify coverage

## Anti-Patterns to Avoid

**Don't:**
- Generate tests without reading code
- Create partial mocks
- Skip running generated tests
- Use estimates instead of measured coverage
- Generate tests for code you're about to write (use TDD)

**Do:**
- Read complete source code first
- Implement complete mock interfaces
- Run all tests after generation
- Report actual metrics with evidence
- Use TDD for new features

## Reference

- **Test Generation Skill**: `.claude/skills/test-generation-expert/SKILL.md`
- **TDD Skill**: `.claude/skills/test-driven-development/SKILL.md`
- **Coverage Analyzer Skill**: `.claude/skills/coverage-analyzer/SKILL.md`
- **Best Practices**: `docs/3-guides/ai-testing/best-practices.md`
