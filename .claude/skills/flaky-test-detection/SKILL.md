---
name: flaky-test-detection
description: "Use when tests fail intermittently, pass locally but fail in CI, or show non-deterministic behavior - systematically detects and fixes flaky tests to ensure repeatability"
allowed-tools:
  - Read
  - Edit
  - Write
  - Bash
  - Glob
  - Grep
---

# Flaky Test Detection & Fixing

## Overview

Flaky tests violate the "Repeatable" principle in FIRST. Tests must produce same result every time.

**Core principle:** If a test can fail without code changes, it's not testing code - it's testing luck.

**Violating the letter of this process is violating the spirit of testing.**

## The Iron Laws

```
1. NEVER tolerate flaky tests (fix or delete them)
2. NEVER add timeouts without understanding root cause
3. NEVER blame "CI environment" without investigating
4. NEVER claim fix complete without verification (100+ runs)
```

## Definition: Flaky Test

**Flaky Test = Test that fails intermittently without code changes.**

**Symptoms:**
- Test passes locally, fails in CI (or vice versa)
- Test passes 9/10 times, fails 1/10
- Test results change between runs without code modification
- "Works on my machine" syndrome
- Test failure is not reproducible

**Flaky does NOT mean:**
- ❌ Test failing consistently (that's a real bug)
- ❌ Test failing after code change (that's expected)
- ❌ Test with wrong assertions (that's bad test)
- ❌ Test failing due to missing dependencies (that's environment issue)

**ABSOLUTE REQUIREMENT:** Fix flaky tests immediately. You cannot ship code with flaky tests - they erode trust and hide real failures.

## VIOLATIONS (Automatic Skill Restart Required)

**Any of these = skill misuse, start over at Phase 1:**

❌ **Tolerating flaky tests**
   - Example: "It usually passes, good enough"
   - Violation: Fix or delete, never tolerate

❌ **Adding arbitrary timeouts without understanding**
   - Example: "Just increase timeout from 1s to 10s"
   - Violation: Must understand root cause first

❌ **Re-running until pass without fixing**
   - Example: "Retry 3 times, it'll eventually pass"
   - Violation: Masks problem, doesn't fix it

❌ **Blaming environment without investigation**
   - Example: "CI is slower, that's why it fails"
   - Violation: Must reproduce and fix root cause

❌ **Skipping flaky tests**
   - Example: "Mark as @Ignore until we have time"
   - Violation: Fix or delete, never ignore

❌ **Claiming fix without verification**
   - Example: "Added timeout, should be fixed"
   - Violation: Must run 100+ times to verify

**If you violate:** Reset to Phase 1. No shortcuts.

## When to Use This Skill

**Use when:**
- Test fails intermittently (passes sometimes, fails sometimes)
- Test passes locally but fails in CI
- Test shows non-deterministic behavior
- Test has timing issues
- Test occasionally hangs or timeouts
- Test results vary between runs
- Team reports "test is flaky"

**DON'T use for:**
- Tests failing consistently (that's a real bug, fix the code)
- Tests with wrong assertions (fix assertions)
- Tests missing dependencies (fix environment)
- Tests that never pass (delete or fix tests)

**Red flags indicating flakiness:**
- "Just run it again"
- "It passes on my machine"
- "CI is slower, that's why"
- "Sometimes it fails"
- "Timing issue"

## Common Causes of Flaky Tests

### Cause 1: Time Dependencies

**Symptoms:**
- Test uses real time/dates
- Test has `sleep()` calls
- Test depends on current time
- Test fails at different times of day
- Test uses timeouts

**Example:**
```go
func TestExpiration(t *testing.T) {
    token := CreateToken()
    time.Sleep(1 * time.Second)  // ❌ Timing-dependent
    assert.True(t, token.IsExpired())
}
```

**Why flaky:** System load affects timing, sleep duration is arbitrary

### Cause 2: Race Conditions

**Symptoms:**
- Test uses goroutines/threads
- Test accesses shared state
- Test fails more often with `-race` flag
- Test fails under load
- Test has concurrent operations

**Example:**
```go
func TestConcurrentAccess(t *testing.T) {
    counter := 0
    var wg sync.WaitGroup
    for i := 0; i < 10; i++ {
        wg.Add(1)
        go func() {
            counter++  // ❌ Race condition
            wg.Done()
        }()
    }
    wg.Wait()
    assert.Equal(t, 10, counter)  // Fails intermittently
}
```

**Why flaky:** No synchronization, race condition

### Cause 3: State Leaks Between Tests

**Symptoms:**
- Test passes in isolation, fails in suite
- Test order affects results
- Test depends on previous test
- Global state not cleaned up

**Example:**
```go
var globalCache = make(map[string]string)  // ❌ Shared global state

func TestCacheInsert(t *testing.T) {
    globalCache["key"] = "value"
    // No cleanup - affects next test
}

func TestCacheEmpty(t *testing.T) {
    assert.Empty(t, globalCache)  // Fails if TestCacheInsert ran first
}
```

**Why flaky:** Test execution order is not guaranteed

### Cause 4: External Dependencies

**Symptoms:**
- Test hits real network
- Test depends on external service
- Test uses real database
- Test fails when offline
- Test depends on third-party API

**Example:**
```javascript
test('fetches user data', async () => {
    const data = await fetch('https://api.example.com/users');  // ❌ Real network call
    expect(data.users).toHaveLength(10);  // Fails when API is slow/down
});
```

**Why flaky:** External service availability and latency varies

### Cause 5: Non-Deterministic Test Data

**Symptoms:**
- Test uses random values
- Test uses timestamps
- Test uses UUIDs
- Test results vary with different data

**Example:**
```python
def test_random_selection():
    value = random.choice([1, 2, 3, 4, 5])  # ❌ Random
    assert value > 3  # Fails 40% of the time
```

**Why flaky:** Random values produce random results

## The Flaky Test Detection & Fixing Process

### Phase 1: Detect Flakiness (Reproduce Reliably)

**ABSOLUTE REQUIREMENT:** You must REPRODUCE flakiness before fixing.

**Gate Function:**
```
BEFORE attempting to fix flaky test:
  1. Can you reproduce the failure?
  2. Do you understand WHEN it fails?
  3. Have you run test 10+ times?
  4. Have you identified failure pattern?

  If NO to any → Continue detection phase
  IF ALL YES → Proceed to root cause analysis
```

**Detection steps:**

1. **Run test multiple times:**
   ```bash
   # Go
   for i in {1..20}; do go test -run TestFlaky && echo "Pass $i" || echo "FAIL $i"; done

   # Python
   for i in range(20):
       pytest tests/test_flaky.py::test_function

   # JavaScript
   for i in {1..20}; do npm test -- --testNamePattern="flaky test"; done

   # Generic bash loop
   for i in {1..20}; do make test-single TEST=TestFlaky; done
   ```

2. **Record results:**
   ```yaml
   flakiness_detection:
     test_name: TestCreateOrder
     total_runs: 20
     passes: 17
     failures: 3
     failure_rate: 15%

     failure_pattern:
       - "Failed at run 5, 12, 19"
       - "No obvious pattern in timing"
       - "Always same assertion failure"
   ```

3. **Increase runs if needed:**
   - If 20/20 pass → Test might not be flaky, or very rare flake
   - If intermittent failures → Continue to 50 or 100 runs
   - Goal: Reproduce failure at least 3 times

4. **Run with different conditions:**
   ```bash
   # Run with race detector
   go test -race -run TestFlaky

   # Run in parallel
   go test -parallel 10 -run TestFlaky

   # Run under load
   stress go test -run TestFlaky
   ```

**Red Flags - STOP:**
- Can't reproduce after 100 runs → Might not be flaky, investigate environment
- Only fails in CI → Compare CI vs local environment
- Failure pattern unclear → Need more data, continue detection

### Phase 2: Root Cause Analysis

**Use systematic debugging (superpowers:systematic-debugging mindset):**

**Gate Function:**
```
BEFORE proposing fix:
  FOR EACH failure:
    1. Read complete test code
    2. Identify all dependencies (time, network, state)
    3. Map exact failure point (which assertion?)
    4. Determine root cause category
    5. Understand WHY it fails intermittently

  If root cause unclear → Add logging, run again
  If multiple causes → Address one at a time
  ONLY propose fix when root cause confirmed
```

**Analysis checklist:**

```yaml
root_cause_analysis:
  test_name: TestCreateOrder

  timing_dependencies:
    - [ ] Uses time.Now(), Date.now()
    - [ ] Has sleep/setTimeout calls
    - [ ] Depends on operation duration
    - [ ] Uses timeouts for async operations

  concurrency_issues:
    - [ ] Uses goroutines/threads/async
    - [ ] Accesses shared state
    - [ ] Has race conditions (-race flag confirms)
    - [ ] No synchronization primitives

  state_management:
    - [ ] Uses global variables
    - [ ] Depends on test execution order
    - [ ] Doesn't clean up after itself
    - [ ] Shares resources with other tests

  external_dependencies:
    - [ ] Hits real network
    - [ ] Connects to real database
    - [ ] Calls third-party APIs
    - [ ] Depends on filesystem state

  non_determinism:
    - [ ] Uses random values
    - [ ] Uses unstable sorting
    - [ ] Depends on iteration order (maps)
    - [ ] Generates timestamps
```

**Add targeted logging:**
```go
func TestFlaky(t *testing.T) {
    t.Logf("Starting test at %v", time.Now())

    result := FlakeyOperation()
    t.Logf("Operation completed: %+v", result)

    assert.Equal(t, expected, result)
}
```

**Re-run with logging to confirm root cause.**

### Phase 3: Fix Using TDD Approach

**CRITICAL:** Apply RED-GREEN-REFACTOR to fix.

**Process:**

#### Step 1: Write Test Reproducing Flakiness

**Make test fail reliably (RED):**
```go
func TestOriginal_Flaky(t *testing.T) {
    // Original flaky test - save for reference
    t.Skip("Known flaky - see TestOriginal_Fixed")
}

func TestOriginal_Fixed(t *testing.T) {
    // New stable test
    // ... (implement fix)
}
```

Or isolate flaky behavior:
```go
func TestTimeDependency_CausesFlake(t *testing.T) {
    // Test that demonstrates the timing issue
    start := time.Now()
    result := operationWithTimeout(1 * time.Second)
    duration := time.Since(start)

    // This should fail if timing is the issue
    t.Logf("Duration: %v", duration)
    assert.True(t, duration < 1*time.Second)  // Might fail under load
}
```

#### Step 2: Implement Fix (GREEN)

**Fix strategies by cause:**

**For Time Dependencies:**
```go
// ❌ BAD: Real time
func TestExpiration(t *testing.T) {
    token := CreateToken()
    time.Sleep(2 * time.Second)
    assert.True(t, token.IsExpired())
}

// ✅ GOOD: Inject time
type Clock interface {
    Now() time.Time
}

func TestExpiration(t *testing.T) {
    mockClock := &MockClock{}
    token := CreateTokenWithClock(mockClock)

    mockClock.Advance(2 * time.Second)  // Deterministic
    assert.True(t, token.IsExpired())
}
```

**For Race Conditions:**
```go
// ❌ BAD: No synchronization
func TestConcurrent(t *testing.T) {
    counter := 0
    for i := 0; i < 10; i++ {
        go func() { counter++ }()
    }
    time.Sleep(100 * time.Millisecond)  // Hope they finish
    assert.Equal(t, 10, counter)
}

// ✅ GOOD: Proper synchronization
func TestConcurrent(t *testing.T) {
    var counter int32
    var wg sync.WaitGroup

    for i := 0; i < 10; i++ {
        wg.Add(1)
        go func() {
            atomic.AddInt32(&counter, 1)
            wg.Done()
        }()
    }
    wg.Wait()  // Deterministic wait
    assert.Equal(t, int32(10), counter)
}
```

**For State Leaks:**
```go
// ❌ BAD: Shared global state
var globalCache = make(map[string]string)

func TestCache(t *testing.T) {
    globalCache["key"] = "value"
    // No cleanup
}

// ✅ GOOD: Isolated state
func TestCache(t *testing.T) {
    cache := NewCache()  // Fresh instance
    cache.Set("key", "value")

    // Cleanup handled by test scope
}

// Or with cleanup
func TestCacheWithCleanup(t *testing.T) {
    cache := NewCache()
    t.Cleanup(func() { cache.Clear() })

    cache.Set("key", "value")
}
```

**For External Dependencies:**
```go
// ❌ BAD: Real network
func TestFetchUser(t *testing.T) {
    user := fetchFromAPI("https://api.example.com/users/1")
    assert.Equal(t, "Alice", user.Name)
}

// ✅ GOOD: Mock external dependency
func TestFetchUser(t *testing.T) {
    mockAPI := &MockAPI{}
    mockAPI.On("GetUser", "1").Return(&User{Name: "Alice"}, nil)

    client := NewClient(mockAPI)
    user := client.FetchUser("1")

    assert.Equal(t, "Alice", user.Name)
}
```

**For Non-Deterministic Data:**
```go
// ❌ BAD: Random data
func TestRandomSelection(t *testing.T) {
    value := randomChoice([]int{1, 2, 3})
    assert.Greater(t, value, 0)  // Always passes, not testing much
}

// ✅ GOOD: Deterministic with seed
func TestRandomSelection(t *testing.T) {
    rng := rand.New(rand.NewSource(42))  // Fixed seed
    value := randomChoiceWithRNG(rng, []int{1, 2, 3})
    assert.Equal(t, 2, value)  // Deterministic with seed
}
```

**For Async Operations:**
```go
// ❌ BAD: Arbitrary timeout
func TestAsyncOperation(t *testing.T) {
    ch := startAsyncOperation()
    time.Sleep(100 * time.Millisecond)  // Guess
    result := <-ch
    assert.NotNil(t, result)
}

// ✅ GOOD: Wait for condition
func TestAsyncOperation(t *testing.T) {
    ch := startAsyncOperation()

    select {
    case result := <-ch:
        assert.NotNil(t, result)
    case <-time.After(5 * time.Second):  // Safety timeout
        t.Fatal("Operation timed out")
    }
}

// Even better: Use proper synchronization
func TestAsyncOperation(t *testing.T) {
    result, err := startAsyncOperationSync()  // Returns when done
    require.NoError(t, err)
    assert.NotNil(t, result)
}
```

#### Step 3: Run Fixed Test (GREEN)

**Verify fix works:**
```bash
# Run fixed test 50 times
for i in {1..50}; do go test -run TestOriginal_Fixed; done
```

Expected: 50/50 passes

### Phase 4: Verify Stability (100+ Runs)

**MANDATORY:** Run test 100+ times to verify fix.

**Gate Function:**
```
BEFORE claiming flaky test is fixed:
  1. Have you run test 100+ times?
  2. Do ALL runs pass?
  3. Have you tested under different conditions?
  4. Have you tested in CI environment?

  Evidence before claims, ALWAYS
```

**Verification script:**

```bash
#!/bin/bash
# verify-test-stability.sh

TEST_NAME=$1
RUNS=${2:-100}

echo "Running $TEST_NAME $RUNS times..."

PASSES=0
FAILURES=0

for i in $(seq 1 $RUNS); do
    if go test -run $TEST_NAME > /dev/null 2>&1; then
        PASSES=$((PASSES + 1))
        echo -n "."
    else
        FAILURES=$((FAILURES + 1))
        echo -n "F"
    fi

    if [ $((i % 50)) -eq 0 ]; then
        echo " [$i/$RUNS]"
    fi
done

echo ""
echo "Results: $PASSES passes, $FAILURES failures"
echo "Success rate: $(echo "scale=2; $PASSES * 100 / $RUNS" | bc)%"

if [ $FAILURES -gt 0 ]; then
    echo "❌ Test is still flaky"
    exit 1
else
    echo "✅ Test is stable"
    exit 0
fi
```

**Usage:**
```bash
chmod +x verify-test-stability.sh
./verify-test-stability.sh TestOriginal_Fixed 100
```

**Also test under stress:**
```bash
# Run with race detector
go test -race -run TestOriginal_Fixed -count=50

# Run in parallel
go test -parallel 20 -run TestOriginal_Fixed -count=50

# Run under load (if available)
stress -cpu 8 -timeout 5m go test -run TestOriginal_Fixed
```

**Verification checklist:**
- [ ] 100+ consecutive passes
- [ ] Tested with race detector (-race flag)
- [ ] Tested in parallel (-parallel flag)
- [ ] Tested in CI environment
- [ ] Tested under load (if applicable)
- [ ] Tested on different machines/OSes (if applicable)

**Success criteria:**
- ✅ 100/100 passes (or 99+% with documented rare edge case)
- ✅ No race conditions detected
- ✅ Passes in CI consistently
- ✅ Team confirms stability

**Red Flags - STOP:**
- Any failures in 100 runs → Still flaky, continue fixing
- Failures only in CI → Investigate CI-specific conditions
- Failures under race detector → Race condition not fixed
- Failures under parallel execution → Shared state issue

### Phase 5: Clean Up

After fixing flaky test:

1. **Delete or update old flaky test:**
   ```go
   // Delete if replaced
   // Or mark as fixed:
   func TestOriginal_WasFlaky_NowFixed(t *testing.T) {
       // Fixed version
   }
   ```

2. **Document the fix:**
   ```go
   // TestCreateOrder tests order creation with proper synchronization.
   // Previously flaky due to race condition (fixed 2025-12-08).
   // Uses WaitGroup to ensure all goroutines complete before assertions.
   func TestCreateOrder(t *testing.T) { ... }
   ```

3. **Add to team knowledge base:**
   ```markdown
   ## Fixed Flaky Tests

   ### TestCreateOrder (2025-12-08)
   - **Symptom**: Failed 15% of time
   - **Root Cause**: Race condition in concurrent order processing
   - **Fix**: Added proper synchronization with sync.WaitGroup
   - **Verification**: 100/100 passes
   ```

4. **Update CI if needed:**
   - Remove retry logic for this test
   - Remove from flaky test list
   - Update test timeout if changed

## Prevention Strategies

### Strategy 1: Use Dependency Injection

**Inject time, randomness, and external dependencies:**
```go
type Service struct {
    clock Clock          // Inject time
    rng   *rand.Rand     // Inject randomness
    api   APIClient      // Inject external dependency
}

// In production
service := NewService(RealClock{}, rand.New(rand.NewSource(time.Now().Unix())), RealAPI{})

// In tests
service := NewService(MockClock{}, rand.New(rand.NewSource(42)), MockAPI{})
```

### Strategy 2: Use Proper Synchronization

**For concurrent tests:**
```go
func TestConcurrent(t *testing.T) {
    var wg sync.WaitGroup
    var mu sync.Mutex

    // Proper sync primitives
}
```

### Strategy 3: Isolate Test State

**Each test gets fresh state:**
```go
func setupTest(t *testing.T) *Service {
    t.Helper()
    return NewService()  // Fresh instance
}
```

### Strategy 4: Mock External Dependencies

**Never hit real network/databases in unit tests:**
```go
type APIClient interface {
    Get(url string) (*Response, error)
}

// Test with mock
mockAPI := &MockAPIClient{}
```

### Strategy 5: Use Test Utilities

**Condition-based waiting (not sleep):**
```go
// ❌ BAD
time.Sleep(100 * time.Millisecond)

// ✅ GOOD
func waitForCondition(t *testing.T, condition func() bool, timeout time.Duration) {
    deadline := time.Now().Add(timeout)
    for time.Now().Before(deadline) {
        if condition() {
            return
        }
        time.Sleep(10 * time.Millisecond)
    }
    t.Fatal("Condition not met within timeout")
}

waitForCondition(t, func() bool { return resource.IsReady() }, 5*time.Second)
```

## Testing Anti-Patterns for Flaky Tests

### Anti-Pattern 1: "Just Add More Timeout"

**Bad:**
```go
time.Sleep(1 * time.Second)  // Flakes
time.Sleep(5 * time.Second)  // Still flakes
time.Sleep(30 * time.Second) // Desperate
```

**Good:**
Replace sleep with proper synchronization or condition waiting.

### Anti-Pattern 2: "Retry Until Pass"

**Bad:**
```yaml
# CI configuration
retry: 3  # Hide flakiness
```

**Good:**
Fix the root cause, don't mask it with retries.

### Anti-Pattern 3: "It's the CI Environment"

**Bad:**
"Test works locally, must be CI's fault."

**Good:**
Reproduce CI environment locally, identify actual difference, fix test.

### Anti-Pattern 4: "Mark as Ignore"

**Bad:**
```go
func TestFlaky(t *testing.T) {
    t.Skip("Flaky, will fix later")  // Never fixed
}
```

**Good:**
Fix immediately or delete test. Don't accumulate flaky tests.

## Common Rationalizations (and Why They're Wrong)

| Excuse | Reality |
|--------|---------|
| "It usually passes" | Usually ≠ always. Flaky test hides real failures. |
| "Just run it again" | Masking problem, not fixing it. |
| "CI is slower, that's why" | Then test should account for varying speeds. |
| "We'll fix it later" | Later never comes. Fix now or delete test. |
| "It's not critical" | Flaky tests erode trust in all tests. |
| "Retry will handle it" | Retry masks flakiness, doesn't fix root cause. |
| "Too hard to fix" | Then test is testing wrong thing. Delete and rewrite. |
| "Works on my machine" | Test must work on all machines. That's the point. |

## Red Flags - STOP

If you catch yourself:
- Saying "just run it again"
- Adding arbitrary timeouts
- Increasing retry counts
- Blaming CI without investigating
- Marking tests as skip
- Tolerating low pass rate
- Not verifying fix with 100+ runs
- Claiming "it's fixed" without evidence

**STOP. Go back to Phase 1.**

## Verification Checklist

Before claiming flaky test is fixed:

- [ ] Reproduced flakiness reliably (3+ failures)
- [ ] Identified root cause category
- [ ] Implemented fix using TDD approach
- [ ] Ran test 100+ times (all pass)
- [ ] Tested with race detector (-race)
- [ ] Tested in parallel execution
- [ ] Tested in CI environment
- [ ] Documented fix and root cause
- [ ] Removed retry logic for this test
- [ ] Team confirms stability

## When Stuck

| Problem | Solution |
|---------|----------|
| Can't reproduce flakiness | Run more times (100+), try different conditions |
| Root cause unclear | Add logging, run with race detector, compare environments |
| Fix doesn't work | Run 100 times to verify, may need different approach |
| Still flaky after fix | Wrong root cause, analyze failure pattern again |
| Multiple root causes | Fix one at a time, verify each fix |
| Test too complex to fix | Consider deleting and rewriting with TDD |

## Multi-Language Examples

### Go - Fix Race Condition

**Flaky:**
```go
func TestCounter(t *testing.T) {
    counter := 0
    for i := 0; i < 100; i++ {
        go func() { counter++ }()
    }
    time.Sleep(100 * time.Millisecond)
    assert.Equal(t, 100, counter)  // Flaky
}
```

**Fixed:**
```go
func TestCounter(t *testing.T) {
    var counter int32
    var wg sync.WaitGroup

    for i := 0; i < 100; i++ {
        wg.Add(1)
        go func() {
            atomic.AddInt32(&counter, 1)
            wg.Done()
        }()
    }
    wg.Wait()
    assert.Equal(t, int32(100), counter)  // Stable
}
```

### JavaScript - Fix Async Timing

**Flaky:**
```javascript
test('async operation', async () => {
    const promise = asyncOperation();
    await new Promise(resolve => setTimeout(resolve, 100));  // Guess
    const result = await promise;
    expect(result).toBe('done');
});
```

**Fixed:**
```javascript
test('async operation', async () => {
    const result = await asyncOperation();  // Proper await
    expect(result).toBe('done');
});
```

### Python - Fix Time Dependency

**Flaky:**
```python
def test_expiration():
    token = create_token(expires_in=1)
    time.sleep(1.1)  # Timing-dependent
    assert token.is_expired()
```

**Fixed:**
```python
def test_expiration():
    mock_time = Mock()
    mock_time.return_value = 1000

    with patch('time.time', mock_time):
        token = create_token(expires_in=1)
        mock_time.return_value = 1002  # Advance time deterministically
        assert token.is_expired()
```

### Java - Fix State Leak

**Flaky:**
```java
class TestDatabase {
    @Test
    void testInsert() {
        database.insert("key", "value");
        // No cleanup - affects next test
    }

    @Test
    void testEmpty() {
        assertEquals(0, database.size());  // Flaky if testInsert ran first
    }
}
```

**Fixed:**
```java
class TestDatabase {
    @BeforeEach
    void setUp() {
        database.clear();  // Fresh state
    }

    @Test
    void testInsert() {
        database.insert("key", "value");
        assertEquals(1, database.size());
    }

    @Test
    void testEmpty() {
        assertEquals(0, database.size());  // Stable
    }
}
```

## Final Rule

```
Flaky tests = broken tests
Fix or delete, never tolerate
100+ run verification required
Evidence before claims, ALWAYS
```

**No exceptions. No "it usually passes." No "good enough." Fix it.**

## Real-World Impact

From applying flaky test fixing discipline:
- Reduced flaky tests from 15% to <1%
- CI pipeline more reliable
- Developer confidence in tests increased
- Faster debugging (tests trustworthy)
- Less "just run it again" culture

**Success factors:**
- Systematic root cause analysis
- TDD approach to fixing
- 100+ run verification
- Zero tolerance for flakiness
- Team culture of immediate fixes

## Reference Documents

When fixing flaky tests, reference:
- **TDD Skill**: `.claude/skills/test-driven-development/SKILL.md`
- **Systematic Debugging**: Superpowers systematic-debugging skill
- **Condition-Based Waiting**: Superpowers condition-based-waiting skill
- **Best Practices**: `docs/3-guides/ai-testing/best-practices.md`
