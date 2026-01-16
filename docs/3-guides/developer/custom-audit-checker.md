# Custom Audit Checker Development Guide

This guide provides a step-by-step tutorial for developing custom audit checkers for the SQL Guard audit platform.

## Overview

Audit checkers analyze SQL execution results (execution time, affected rows, errors) and calculate risk scores for discovery and retrospective analysis. Unlike the prevention layer (Phases 1-6), audit checkers run **after** SQL execution for monitoring and analysis purposes.

## Seven-Step Development Process

### Step 1: Extend AbstractAuditChecker

Create a new class that extends the `AbstractAuditChecker` base class:

```java
package com.example.custom;

import com.footstone.sqlguard.audit.checker.AbstractAuditChecker;
import org.springframework.stereotype.Component;

@Component
public class TableLockChecker extends AbstractAuditChecker {
    // Implementation will be added in next steps
}
```

**Key Points:**
- Use `@Component` annotation for Spring auto-discovery
- Extend `AbstractAuditChecker` to inherit core functionality
- Choose a descriptive name that reflects the checker's purpose

### Step 2: Implement performAudit() Logic

Implement the `performAudit()` method to analyze SQL execution results:

```java
@Override
protected AuditResult performAudit(SqlContext context, ExecutionResult result) {
    // 1. Extract relevant metrics from the execution result
    long lockTime = detectLockTime(context, result);

    // 2. Apply detection logic
    if (lockTime > config.getThreshold()) {
        return AuditResult.builder()
            .severity(Severity.HIGH)
            .checkerId("TABLE_LOCK")
            .message("Table lock held for " + lockTime + "ms")
            .recommendation("Optimize transaction scope or use row-level locks")
            .sqlHash(context.getSqlHash())
            .mapperId(context.getMapperId())
            .metadata(Map.of(
                "lock_time_ms", lockTime,
                "affected_tables", extractTables(context)
            ))
            .build();
    }

    // 3. Return pass result if no issues detected
    return AuditResult.pass();
}

private long detectLockTime(SqlContext context, ExecutionResult result) {
    // Custom detection logic here
    // For this example, we use execution time as a proxy
    return result.getExecutionTime();
}
```

**Key Points:**
- Analyze execution metrics (time, rows affected, errors)
- Return `AuditResult.pass()` when no issues are found
- Include actionable recommendations in findings
- Add metadata for detailed analysis

### Step 3: Calculate Risk Score

Implement the `calculateRiskScore()` method to quantify the risk level:

```java
@Override
protected RiskScore calculateRiskScore(AuditResult result) {
    if (result.getSeverity() == Severity.HIGH) {
        return RiskScore.builder()
            .score(80)  // 0-100 scale
            .confidence(0.9)  // 0.0-1.0 scale (90% confidence)
            .factors(Map.of(
                "lock_time", result.getMessage(),
                "severity", "HIGH",
                "impact", "Table-level blocking"
            ))
            .build();
    } else if (result.getSeverity() == Severity.MEDIUM) {
        return RiskScore.builder()
            .score(50)
            .confidence(0.7)
            .factors(result.getMetadata())
            .build();
    }

    return RiskScore.safe();  // Score = 0, Confidence = 1.0
}
```

**Risk Score Guidelines:**
- **Score Range:** 0-100 (0 = safe, 100 = critical)
- **Confidence Range:** 0.0-1.0 (how certain is the detection)
- **Factors:** Map of contributing factors for explainability

**Recommended Score Ranges:**
- **0-30:** Low risk (informational)
- **31-60:** Medium risk (warning)
- **61-85:** High risk (requires attention)
- **86-100:** Critical risk (immediate action needed)

### Step 4: Write Comprehensive Tests

Create a comprehensive test suite following TDD principles:

```java
package com.example.custom;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TableLockCheckerTest {

    private TableLockChecker checker = new TableLockChecker();

    @Test
    public void testTableLock_longLock_shouldDetect() {
        // Given: Query with 2-second lock
        SqlContext context = createContext("UPDATE users SET status = ?");
        ExecutionResult result = createResult(2000);  // 2s execution time

        // When: Perform audit
        AuditResult audit = checker.performAudit(context, result);

        // Then: Should detect HIGH severity issue
        assertEquals(Severity.HIGH, audit.getSeverity());
        assertEquals("TABLE_LOCK", audit.getCheckerId());
        assertTrue(audit.getMessage().contains("2000ms"));
        assertNotNull(audit.getRecommendation());
    }

    @Test
    public void testTableLock_shortLock_shouldPass() {
        // Given: Quick query (100ms)
        SqlContext context = createContext("UPDATE users SET status = ? WHERE id = ?");
        ExecutionResult result = createResult(100);

        // When: Perform audit
        AuditResult audit = checker.performAudit(context, result);

        // Then: Should pass
        assertTrue(audit.isPassed());
        assertEquals(Severity.NONE, audit.getSeverity());
    }

    @Test
    public void testRiskScore_highSeverity_shouldCalculateCorrectly() {
        // Given: HIGH severity result
        AuditResult result = AuditResult.builder()
            .severity(Severity.HIGH)
            .checkerId("TABLE_LOCK")
            .message("Lock held for 2000ms")
            .build();

        // When: Calculate risk score
        RiskScore score = checker.calculateRiskScore(result);

        // Then: Should have high score and high confidence
        assertEquals(80, score.getScore());
        assertEquals(0.9, score.getConfidence(), 0.01);
        assertNotNull(score.getFactors());
    }

    // Helper methods
    private SqlContext createContext(String sql) {
        return SqlContext.builder()
            .sql(sql)
            .mapperId("TestMapper.update")
            .build();
    }

    private ExecutionResult createResult(long executionTime) {
        return ExecutionResult.builder()
            .executionTime(executionTime)
            .affectedRows(1)
            .build();
    }
}
```

**Test Coverage Requirements:**
- **Positive Cases:** Verify detection of problematic patterns
- **Negative Cases:** Verify no false positives for safe patterns
- **Edge Cases:** Boundary conditions, null handling
- **Risk Calculation:** Verify score and confidence values
- **Target:** 30+ test cases covering all scenarios

### Step 5: Register as Spring Bean

Ensure your checker is discoverable by Spring:

**Option 1: Component Scanning (Recommended)**
```java
@Component
public class TableLockChecker extends AbstractAuditChecker {
    // Spring will automatically discover and register this bean
}
```

**Option 2: Explicit Configuration**
```java
@Configuration
public class AuditCheckerConfig {

    @Bean
    public TableLockChecker tableLockChecker() {
        return new TableLockChecker();
    }
}
```

**Verification:**
- Run the application and check Spring logs for bean registration
- Access the health endpoint: `GET /actuator/health/checkers`
- Expected output:
  ```json
  {
    "table-lock": "UP",
    "slow-query": "UP",
    ...
  }
  ```

### Step 6: Configure Checker Properties

Add configuration properties for your checker:

**application.yml:**
```yaml
sqlguard:
  audit:
    checkers:
      table-lock:
        enabled: true
        threshold: 1000  # Lock time threshold in milliseconds
        severity-levels:
          warning: 500
          critical: 2000
```

**Configuration Class:**
```java
@ConfigurationProperties(prefix = "sqlguard.audit.checkers.table-lock")
public class TableLockConfig {
    private boolean enabled = true;
    private long threshold = 1000;
    private SeverityLevels severityLevels = new SeverityLevels();

    // Getters and setters

    public static class SeverityLevels {
        private long warning = 500;
        private long critical = 2000;
        // Getters and setters
    }
}
```

**Inject Configuration:**
```java
@Component
public class TableLockChecker extends AbstractAuditChecker {

    private final TableLockConfig config;

    @Autowired
    public TableLockChecker(TableLockConfig config) {
        this.config = config;
    }

    @Override
    protected AuditResult performAudit(SqlContext context, ExecutionResult result) {
        if (!config.isEnabled()) {
            return AuditResult.pass();
        }

        long lockTime = detectLockTime(context, result);

        if (lockTime > config.getSeverityLevels().getCritical()) {
            return buildResult(Severity.CRITICAL, lockTime);
        } else if (lockTime > config.getSeverityLevels().getWarning()) {
            return buildResult(Severity.MEDIUM, lockTime);
        }

        return AuditResult.pass();
    }
}
```

### Step 7: Deploy and Validate

#### 7.1 Build the Project

```bash
# Clean build
mvn clean package

# Run tests
mvn test

# Verify Javadoc generation
mvn javadoc:javadoc
```

#### 7.2 Deploy

**Option 1: Standalone JAR**
```bash
java -jar target/sql-audit-service.jar
```

**Option 2: Docker**
```bash
docker build -t sql-audit-service:latest .
docker run -p 8090:8090 sql-audit-service:latest
```

**Option 3: Kubernetes**
```bash
kubectl apply -f deployment.yaml
```

#### 7.3 Validate Deployment

**Check Health Endpoint:**
```bash
curl http://localhost:8090/actuator/health/checkers
```

**Expected Response:**
```json
{
  "status": "UP",
  "components": {
    "table-lock": {
      "status": "UP",
      "details": {
        "enabled": true,
        "threshold": 1000
      }
    }
  }
}
```

**Check Checker Registration:**
```bash
curl http://localhost:8090/api/v1/audits/checkers
```

**Expected Response:**
```json
{
  "checkers": [
    {
      "id": "TABLE_LOCK",
      "name": "Table Lock Checker",
      "enabled": true,
      "description": "Detects queries holding table locks for extended periods"
    }
  ]
}
```

**Monitor Audit Logs:**
```bash
tail -f /var/log/audit/audit.log

# Or query via API
curl http://localhost:8090/api/v1/audits?checkerId=TABLE_LOCK&limit=10
```

## Complete Example: TableLockChecker

See [TableLockChecker.java](./examples/TableLockChecker.java) for a complete, production-ready implementation.

## Best Practices

### 1. Performance Considerations

- **Minimize Overhead:** Keep audit logic lightweight
- **Async Processing:** Use async methods for expensive operations
- **Caching:** Cache frequently accessed data
- **Batch Processing:** Process audit events in batches when possible

```java
@Async
protected CompletableFuture<AuditResult> performAuditAsync(SqlContext context, ExecutionResult result) {
    return CompletableFuture.supplyAsync(() -> performAudit(context, result));
}
```

### 2. Error Handling

- **Graceful Degradation:** Don't fail the SQL execution if audit fails
- **Logging:** Log errors for debugging
- **Circuit Breaker:** Disable checker if it fails repeatedly

```java
@Override
protected AuditResult performAudit(SqlContext context, ExecutionResult result) {
    try {
        // Audit logic here
    } catch (Exception e) {
        logger.error("Audit failed for checker: " + getCheckerId(), e);
        return AuditResult.error(e.getMessage());
    }
}
```

### 3. Testing Strategy

- **Unit Tests:** Test individual checker logic
- **Integration Tests:** Test with Spring context
- **Performance Tests:** Verify overhead is acceptable
- **Test Matrix:** Cover all severity levels and edge cases

### 4. Documentation

- **Javadoc:** Document all public methods
- **Configuration:** Document all configuration properties
- **Examples:** Provide usage examples
- **Troubleshooting:** Document common issues

## Common Patterns

### Pattern 1: Threshold-Based Detection

```java
if (metric > threshold) {
    return AuditResult.builder()
        .severity(Severity.HIGH)
        .message("Threshold exceeded: " + metric)
        .build();
}
```

### Pattern 2: Multi-Level Severity

```java
if (metric > criticalThreshold) {
    return buildResult(Severity.CRITICAL, metric);
} else if (metric > highThreshold) {
    return buildResult(Severity.HIGH, metric);
} else if (metric > mediumThreshold) {
    return buildResult(Severity.MEDIUM, metric);
}
```

### Pattern 3: Composite Conditions

```java
boolean hasLongLock = lockTime > threshold;
boolean affectsMultipleTables = tableCount > 1;
boolean hasNoWhere = !hasWhereClause(context);

if (hasLongLock && affectsMultipleTables && hasNoWhere) {
    return AuditResult.builder()
        .severity(Severity.CRITICAL)
        .message("Dangerous combination detected")
        .build();
}
```

## Troubleshooting

### Checker Not Registered

**Symptom:** Checker doesn't appear in `/actuator/health/checkers`

**Solutions:**
1. Verify `@Component` annotation is present
2. Ensure package is scanned by Spring Boot
3. Check Spring logs for bean creation errors

### Configuration Not Loading

**Symptom:** Default values are used instead of configured values

**Solutions:**
1. Verify `application.yml` syntax
2. Add `@EnableConfigurationProperties(TableLockConfig.class)` to main class
3. Check property prefix matches: `sqlguard.audit.checkers.table-lock`

### Audit Events Not Appearing

**Symptom:** No audit events in logs or database

**Solutions:**
1. Verify `enabled: true` in configuration
2. Check Kafka connectivity
3. Verify SQL execution is actually happening
4. Check audit log writer configuration

## Further Reading

- [Audit Platform Architecture](../ARCHITECTURE.md#audit-platform)
- [API Reference](../api/rest-api-reference.md)
- [Testing Guide](./testing-guide.md)
- [Deployment Guide](../user-guide/deployment.md)

## Support

For questions and issues:
- GitHub Issues: https://github.com/example/sql-guard/issues
- Documentation: https://sql-guard.readthedocs.io
- Slack: #sql-guard-dev
