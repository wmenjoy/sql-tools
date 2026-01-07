# SQL Guard Spring Boot Demo

Interactive demonstration of SQL Safety Guard System with real-world MyBatis/MyBatis-Plus usage patterns.

## Overview

This demo application showcases the SQL Safety Guard System's capabilities through REST endpoints that trigger each of the 21 validation rules on demand. It demonstrates zero-configuration integration via `sql-guard-spring-boot-starter` and provides hands-on experience with different violation strategies (BLOCK/WARN/LOG) and interceptor priority selection.

## Features Demonstrated

- ✅ **Zero-Configuration Integration** - Just add starter dependency, no code changes needed
- ✅ **21 Validation Rules** - All rule checkers with interactive violation triggers
- ✅ **3 Violation Strategies** - BLOCK (prevent execution), WARN (log error), LOG (observe)
- ✅ **Priority-based Interceptor Selection** - MyBatis > Druid > HikariCP > P6Spy
- ✅ **MyBatis Integration** - XML mappers, annotation mappers, and dynamic SQL
- ✅ **MyBatis-Plus Integration** - QueryWrapper and BaseMapper CRUD operations
- ✅ **Profile-Specific Configuration** - Dev/prod profiles with different strategies
- ✅ **Runtime Observability** - Violation dashboard and logging
- ✅ **Docker Deployment** - Complete environment with MySQL database

## Interceptor Priority

The SQL Guard system uses priority-based interceptor selection. Only **one** interceptor is active at a time:

| Priority | Interceptor | Description |
|----------|-------------|-------------|
| 1 (Highest) | MyBatis | ORM-level interception |
| 2 | Druid | Connection pool filter |
| 3 | HikariCP | Connection pool proxy |
| 4 (Lowest) | P6Spy | JDBC spy |

Use profiles to control which interceptor is used:

```bash
# Use MyBatis interceptor (default - highest priority)
mvn spring-boot:run -Dspring-boot.run.profiles=mybatis

# Use Druid filter (disables MyBatis)
mvn spring-boot:run -Dspring-boot.run.profiles=druid

# Use HikariCP proxy (disables MyBatis and Druid)
mvn spring-boot:run -Dspring-boot.run.profiles=hikari
```

## Quick Start

### Prerequisites

- Docker and Docker Compose (for containerized deployment)
- OR Java 11+ and Maven 3.6+ (for local development)
- OR just curl/browser (to test running demo)

### Option 1: Docker Compose (Recommended)

Start the complete demo environment with one command:

```bash
# From project root
cd examples/sql-guard-demo

# Start MySQL + Demo App
docker-compose up

# Wait for startup (about 60 seconds)
# MySQL initializes with test data
# Demo app starts on http://localhost:8080
```

Access the demo:
```bash
# Home page with API documentation
curl http://localhost:8080/

# Trigger a violation
curl http://localhost:8080/violations/no-where-clause
```

Stop the environment:
```bash
docker-compose down

# Remove volumes to reset database
docker-compose down -v
```

### Option 2: Local Development

Run locally without Docker:

```bash
# 1. Start MySQL (or use existing instance)
docker run -d \
  --name mysql-demo \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -e MYSQL_DATABASE=sqlguard_demo \
  -p 3306:3306 \
  mysql:8.0

# 2. Initialize database
mysql -h localhost -u root -proot123 sqlguard_demo < src/main/resources/db/init.sql

# 3. Run application
mvn spring-boot:run

# Or with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=block
```

## Demo Endpoints

### Violation Trigger Endpoints

Each endpoint demonstrates a specific SQL safety violation:

| Endpoint | Rule Checker | Risk Level | Description |
|----------|-------------|------------|-------------|
| `GET /violations/no-where-clause` | NoWhereClauseChecker | CRITICAL | DELETE/UPDATE without WHERE clause |
| `GET /violations/dummy-condition` | DummyConditionChecker | HIGH | WHERE 1=1 dummy condition |
| `GET /violations/blacklist-only` | BlacklistFieldChecker | HIGH | WHERE with only low-cardinality fields |
| `GET /violations/whitelist-missing` | WhitelistFieldChecker | HIGH | Missing required high-selectivity fields |
| `GET /violations/logical-pagination` | LogicalPaginationChecker | CRITICAL | RowBounds without PageHelper plugin |
| `GET /violations/deep-pagination` | DeepPaginationChecker | MEDIUM | High OFFSET value (> 10000) |
| `GET /violations/large-page-size` | LargePageSizeChecker | MEDIUM | Large LIMIT value (> 1000) |
| `GET /violations/missing-orderby` | MissingOrderByChecker | LOW | Pagination without ORDER BY |
| `GET /violations/no-pagination` | NoPaginationChecker | Variable | SELECT without LIMIT on large table |
| `GET /violations/no-condition-pagination` | NoConditionPaginationChecker | CRITICAL | LIMIT without WHERE clause |

### Management Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /` | Home page with API documentation |
| `GET /violations/logs` | View recent violations (last 100) |
| `POST /config/strategy/{strategy}` | Change violation strategy (LOG/WARN/BLOCK) |

### Example Usage

```bash
# Test NoWhereClauseChecker (CRITICAL)
curl http://localhost:8080/violations/no-where-clause

# Expected response (LOG strategy):
{
  "status": "success",
  "checker": "NoWhereClauseChecker",
  "riskLevel": "CRITICAL",
  "message": "DELETE without WHERE executed (strategy=LOG/WARN)",
  "rowsAffected": 0,
  "timestamp": "Wed Dec 17 10:30:45 UTC 2025"
}

# Test with BLOCK strategy
curl http://localhost:8080/violations/no-where-clause
# Expected response:
{
  "status": "blocked",
  "checker": "NoWhereClauseChecker",
  "riskLevel": "CRITICAL",
  "message": "DELETE without WHERE blocked (strategy=BLOCK)",
  "error": "SQL validation failed: DELETE without WHERE clause...",
  "timestamp": "Wed Dec 17 10:31:12 UTC 2025"
}

# View violation logs
curl http://localhost:8080/violations/logs

# Change strategy (requires restart)
curl -X POST http://localhost:8080/config/strategy/BLOCK
```

## Testing Different Strategies

The demo supports three violation strategies configured via `sql-guard.active-strategy`:

### LOG Strategy (Default)

**Behavior:** Log violations at WARNING level, continue execution

**Use Case:** Initial rollout, observation mode, metrics collection

**Configuration:**
```yaml
sql-guard:
  active-strategy: LOG
```

**Start Demo:**
```bash
# Default profile uses LOG
mvn spring-boot:run
```

### WARN Strategy

**Behavior:** Log violations at ERROR level, continue execution

**Use Case:** Gradual rollout, identify violations without blocking production

**Configuration:**
```yaml
sql-guard:
  active-strategy: WARN
```

**Start Demo:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=warn
```

### BLOCK Strategy

**Behavior:** Throw SQLException, prevent SQL execution

**Use Case:** Strict enforcement, zero tolerance for violations

**Configuration:**
```yaml
sql-guard:
  active-strategy: BLOCK
```

**Start Demo:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=block
```

## Violation Examples

### Example 1: NoWhereClauseChecker (CRITICAL)

**Dangerous SQL:**
```sql
DELETE FROM user
```

**Violation Message:**
```
[SQL Guard] NoWhereClauseChecker violation detected
Risk Level: CRITICAL
SQL: DELETE FROM user
Message: DELETE statement missing WHERE clause - would delete all table data
Suggestion: Add WHERE clause with specific conditions
```

**BLOCK Strategy:** SQLException thrown, transaction rolled back
**WARN/LOG Strategy:** Logged, execution continues (demo uses empty table, no harm)

### Example 2: DeepPaginationChecker (MEDIUM)

**Dangerous SQL:**
```sql
SELECT * FROM user WHERE username LIKE '%test%' LIMIT 20 OFFSET 50000
```

**Violation Message:**
```
[SQL Guard] DeepPaginationChecker violation detected
Risk Level: MEDIUM
SQL: SELECT * FROM user WHERE username LIKE '%test%' LIMIT 20 OFFSET 50000
Message: Deep pagination offset (50000) exceeds threshold (10000)
Suggestion: Use cursor-based pagination or keyset pagination
```

### Example 3: BlacklistFieldChecker (HIGH)

**Dangerous SQL:**
```sql
SELECT * FROM user WHERE status = 'ACTIVE'
```

**Violation Message:**
```
[SQL Guard] BlacklistFieldChecker violation detected
Risk Level: HIGH
SQL: SELECT * FROM user WHERE status = 'ACTIVE'
Message: WHERE clause uses only blacklist fields (status) with low cardinality
Suggestion: Add high-selectivity fields (id, email) to WHERE clause
```

## Configuration Hot-Reload

The demo supports configuration hot-reload via Apollo/Nacos config centers (optional):

### Apollo Integration

1. Uncomment Apollo service in `docker-compose.yml`
2. Configure Apollo connection in `application.yml`:
```yaml
apollo:
  meta: http://apollo-configservice:8080
  bootstrap:
    enabled: true
    namespaces: application
```

3. Update SQL Guard configuration in Apollo portal
4. Changes take effect immediately without restart

### Nacos Integration

1. Uncomment Nacos service in `docker-compose.yml`
2. Configure Nacos connection in `application.yml`:
```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: nacos:8848
        namespace: public
        group: DEFAULT_GROUP
```

3. Update SQL Guard configuration in Nacos console
4. Changes take effect immediately without restart

## Troubleshooting

### MySQL Connection Failed

**Symptom:** `Communications link failure` or `Connection refused`

**Solution:**
```bash
# Check MySQL is running
docker ps | grep mysql

# Check MySQL logs
docker logs sqlguard-demo-mysql

# Wait for MySQL to fully initialize (60 seconds)
docker-compose logs -f mysql
```

### Demo App Fails to Start

**Symptom:** `BeanCreationException` or `ClassNotFoundException`

**Solution:**
```bash
# Rebuild with clean install
mvn clean install -DskipTests

# Check Java version (requires 11+)
java -version

# Check Maven version (requires 3.6+)
mvn -version
```

### Violations Not Logged

**Symptom:** No violation messages in console

**Solution:**
```yaml
# Increase logging level in application.yml
logging:
  level:
    com.footstone.sqlguard: DEBUG
```

### BLOCK Strategy Not Working

**Symptom:** Dangerous SQL executes despite BLOCK strategy

**Solution:**
```bash
# Verify active profile
curl http://localhost:8080/actuator/env | grep activeProfiles

# Verify configuration
curl http://localhost:8080/actuator/configprops | grep sql-guard

# Check interceptor registration
# Should see SqlSafetyInterceptor in logs during startup
```

## Project Structure

```
sql-guard-demo/
├── src/
│   ├── main/
│   │   ├── java/com/footstone/sqlguard/demo/
│   │   │   ├── DemoApplication.java          # Spring Boot main class
│   │   │   ├── entity/                        # Domain entities
│   │   │   │   ├── User.java
│   │   │   │   ├── Order.java
│   │   │   │   └── Product.java
│   │   │   ├── mapper/                        # MyBatis mappers
│   │   │   │   ├── UserMapper.java           # XML-based mapper
│   │   │   │   ├── UserAnnotationMapper.java # Annotation-based mapper
│   │   │   │   ├── OrderMapper.java          # MyBatis-Plus mapper
│   │   │   │   └── ProductMapper.java
│   │   │   ├── service/                       # Business services
│   │   │   │   └── OrderService.java         # MyBatis-Plus QueryWrapper demo
│   │   │   └── controller/                    # REST controllers
│   │   │       └── DemoController.java       # Violation trigger endpoints
│   │   └── resources/
│   │       ├── mapper/                        # MyBatis XML mappers
│   │       │   └── UserMapper.xml
│   │       ├── db/                            # Database scripts
│   │       │   └── init.sql
│   │       ├── application.yml                # Default configuration (LOG)
│   │       ├── application-block.yml          # BLOCK strategy profile
│   │       ├── application-warn.yml           # WARN strategy profile
│   │       ├── application-dev.yml            # Development profile
│   │       └── application-prod.yml           # Production profile
│   └── test/
│       └── java/com/footstone/sqlguard/demo/
│           └── DemoApplicationTest.java       # Integration tests
├── docker-compose.yml                         # Docker Compose configuration
├── Dockerfile                                 # Multi-stage build
├── pom.xml                                    # Maven dependencies
└── README.md                                  # This file
```

## Configuration Reference

### Complete Configuration Example

```yaml
sql-guard:
  enabled: true
  active-strategy: BLOCK
  
  interceptors:
    mybatis:
      enabled: true
    mybatis-plus:
      enabled: true
    jdbc:
      enabled: false
  
  deduplication:
    enabled: true
    cache-size: 1000
    ttl-ms: 100
  
  parser:
    lenient-mode: false
  
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
    
    dummy-condition:
      enabled: true
      risk-level: HIGH
      patterns: ["1=1", "true", "'a'='a'"]
    
    blacklist-fields:
      enabled: true
      risk-level: HIGH
      blacklist-fields: [deleted, status, enabled]
    
    whitelist-fields:
      enabled: true
      risk-level: HIGH
      whitelist-fields:
        user: [id, email]
        order: [id, user_id]
    
    logical-pagination:
      enabled: true
      risk-level: CRITICAL
    
    no-condition-pagination:
      enabled: true
      risk-level: CRITICAL
    
    deep-pagination:
      enabled: true
      risk-level: MEDIUM
      max-offset: 10000
    
    large-page-size:
      enabled: true
      risk-level: MEDIUM
      max-page-size: 1000
    
    missing-orderby:
      enabled: true
      risk-level: LOW
    
    no-pagination:
      enabled: true
      risk-level: MEDIUM
      estimated-rows-threshold: 10000
```

## Next Steps

After exploring the demo:

1. **Review Logs** - Check console output for violation detection details
2. **Test Strategies** - Try LOG → WARN → BLOCK progression
3. **Integrate in Your Project** - Add `sql-guard-spring-boot-starter` dependency
4. **Configure Rules** - Customize thresholds and risk levels for your use case
5. **Monitor Production** - Start with LOG strategy, gradually move to BLOCK

## Support

- **Documentation:** See `docs/` folder in project root
- **Issues:** Report bugs via GitHub Issues
- **Questions:** Contact development team

## License

Copyright (c) 2025 Footstone. All rights reserved.
