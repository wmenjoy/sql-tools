# Coverage Analysis Command

Analyzes code coverage, identifies gaps with root cause analysis, and generates targeted test recommendations.

**IMPORTANT:** This command invokes the `coverage-analyzer` skill. Always follow evidence-based analysis principles.

## Iron Laws

```
1. NEVER use old coverage data - always run fresh
2. NEVER propose tests without understanding WHY code is uncovered
3. NEVER claim improvement without verification
```

## Usage

```bash
/analyze-coverage [options]
```

## Options

- `--path <path>`: Code path to analyze (file or directory)
- `--report <path>`: Existing coverage report file (optional, will run fresh if not provided)
- `--threshold <percentage>`: Target coverage threshold (default: 80%)
- `--generate`: Automatically generate tests for gaps (requires user confirmation)
- `--output <format>`: Output format (text/json/markdown), default: markdown

## Prerequisites

Before running this command:
- ✅ All tests must pass (fix failing tests first)
- ✅ Code compiles successfully
- ✅ Dependencies installed

## Execution Process

**This command follows the 6-phase coverage analysis process from the coverage-analyzer skill:**

### Phase 1: Collect Fresh Coverage Data

**Always run fresh coverage. Never trust old reports.**

**Go:**
```bash
cd backend
go test -coverprofile=coverage.out ./...
go tool cover -func=coverage.out
```

**Verification:**
- ✅ All tests PASS
- ✅ coverage.out file created
- ✅ No build errors

**Java:**
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

**C++:**
```bash
# Compile with coverage flags
g++ -fprofile-arcs -ftest-coverage -o test_app *.cpp
./test_app
# Generate coverage report
gcov *.cpp
lcov --capture --directory . --output-file coverage.info
lcov --list coverage.info
```

**C:**
```bash
# Compile with coverage
gcc -fprofile-arcs -ftest-coverage -o test_program *.c
./test_program
# Generate report
gcov *.c
```

**Rust:**
```bash
# Install tarpaulin if not installed
cargo install cargo-tarpaulin
# Run coverage
cargo tarpaulin --out Xml --out Html
# Read: cobertura.xml or tarpaulin-report.html
```

**Vue (Vitest):**
```bash
npm run test -- --coverage
# or
vitest run --coverage
# Read: coverage/coverage-summary.json
```

**React (Jest):**
```bash
npm test -- --coverage --watchAll=false
# or
yarn test --coverage --watchAll=false
# Read: coverage/lcov-report/index.html
```

### Phase 2: Parse and Understand Coverage

Extract metrics:
- Total line/branch/function coverage
- Per-file coverage breakdown
- Uncovered line numbers
- Uncovered branch conditions

### Phase 3: Identify Gaps with Root Cause Analysis

**CRITICAL:** Don't just list uncovered lines. Understand WHY.

For each gap, determine:
- What is this code doing?
- Why is it uncovered?
  - Missing test case
  - Dead code
  - Hard to test (bad design)
  - Error handling path
  - Edge case

**Categorize by priority:**
- **Critical** (security, auth, payments): Target 95%+
- **Important** (core business logic): Target 85%+
- **Low** (utilities): Target 70%+
- **Dead code candidates**: Recommend deletion

### Phase 4: Generate Targeted Test Recommendations

For each legitimate gap (not dead code):
1. Read actual source code
2. Identify what behavior to test
3. Suggest specific test case
4. Estimate coverage improvement

**Use test-generation-expert skill if --generate flag provided.**

### Phase 5: Verify Coverage Improvement (if --generate used)

**MANDATORY verification:**
1. Run fresh coverage
2. Compare before/after metrics
3. Verify target reached
4. All tests pass

**Evidence before claims, ALWAYS.**

### Phase 6: Report with Evidence

Include in report:
- Actual measured metrics (not estimates)
- Command outputs
- Before/after comparison (if tests generated)
- Dead code candidates
- Prioritized improvement plan

## Output Format

```markdown
# Coverage Analysis Report

## Current Coverage (MEASURED)
- Line Coverage: {X}% (VERIFIED: go tool cover -func)
- Branch Coverage: {Y}%
- Function Coverage: {Z}%
- Target: {threshold}%

## Coverage Gaps (By Priority)

### Critical Gaps ({count})
| File | Lines | Function | Root Cause | Solution |
|------|-------|----------|------------|----------|
| auth/service.go | 45-52 | Login error handling | Error injection not tested | Mock DB failure |

### Important Gaps ({count})
| File | Lines | Function | Root Cause | Solution |
|------|-------|----------|------------|----------|
| order/processor.go | 120-135 | Validation edge case | Missing boundary test | Add boundary tests |

### Dead Code Candidates ({count})
- `pkg/utils/deprecated.go:45-60` - No execution path
- `internal/legacy/handler.go:100-120` - Unreachable code

## Improvement Plan

### Stage 1: Critical Gaps (Target: +{X}%)
1. Add error injection tests for auth service
2. Add security tests for payment handling
   Estimated improvement: {X}% → {Y}%

### Stage 2: Important Gaps (Target: +{Y}%)
1. Add boundary tests for validation
2. Add edge case tests for business logic
   Estimated improvement: {Y}% → {Z}%

## Evidence
```bash
$ go test -coverprofile=coverage.out ./...
PASS
$ go tool cover -func=coverage.out | tail -1
total: (statements) {X}.{Y}%
```
```

## Examples

### Example 1: Analyze service layer
```bash
/analyze-coverage --path ./internal/service --threshold 85
```

**Output:**
- Runs fresh coverage
- Identifies gaps with root cause
- Provides prioritized improvement plan
- **Does NOT generate tests** (no --generate flag)

### Example 2: Analyze and generate tests
```bash
/analyze-coverage --path ./internal/service --threshold 85 --generate
```

**Output:**
- Runs fresh coverage
- Identifies gaps
- **Automatically generates tests** for gaps
- Runs tests to verify
- Reports actual coverage improvement with evidence

### Example 3: Quick check
```bash
/analyze-coverage --path . --threshold 80
```

**Output:**
- Full project analysis
- Highlights files below 80% threshold
- Improvement recommendations

## Red Flags - Command Will STOP

The command will halt if:
- Tests are failing (fix tests first)
- Code doesn't compile
- Using old coverage data (run fresh)
- Cannot parse coverage report

## Integration with CI/CD

**Pre-commit hook:**
```bash
#!/bin/bash
# Enforce 80% coverage minimum
claude "/analyze-coverage --path . --threshold 80"
```

**GitHub Actions:**
```yaml
- name: Coverage Check
  run: |
    go test -coverprofile=coverage.out ./...
    claude "/analyze-coverage --report coverage.out --threshold 80"
```

## Reference

- **Coverage Analyzer Skill**: `.claude/skills/coverage-analyzer/SKILL.md`
- **Test Generation Skill**: `.claude/skills/test-generation-expert/SKILL.md`
- **Best Practices**: `docs/3-guides/ai-testing/best-practices.md`
