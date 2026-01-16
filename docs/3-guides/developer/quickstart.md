# Developer Quickstart Guide

Get up and running with the SQL Audit Platform in less than 5 minutes.

## Overview

This guide walks you through setting up a local development environment for the SQL Audit Platform, including:
- Starting required services (Kafka, PostgreSQL, ClickHouse)
- Launching the audit service
- Accessing the API documentation
- Running example queries

**Estimated Time:** < 5 minutes

## Prerequisites

Before you begin, ensure you have:
- **Docker** and **Docker Compose** installed
- **Java 8+** (for running the audit service)
- **Maven 3.6+** (for building)
- **curl** or equivalent HTTP client

## Quick Start (5 Steps)

### Step 1: Clone the Repository (30 seconds)

```bash
git clone https://github.com/example/sql-guard.git
cd sql-guard
```

### Step 2: Start Infrastructure Services (2 minutes)

Start Kafka, PostgreSQL, and ClickHouse using Docker Compose:

```bash
docker-compose up -d kafka postgres clickhouse
```

**Wait for services to be ready:**
```bash
# Check Kafka
docker-compose logs kafka | grep "started (kafka.server.KafkaServer)"

# Check PostgreSQL
docker-compose exec postgres pg_isready

# Check ClickHouse
docker-compose exec clickhouse clickhouse-client --query "SELECT 1"
```

**Expected output:**
```
[Kafka] INFO [KafkaServer id=1] started (kafka.server.KafkaServer)
/var/run/postgresql:5432 - accepting connections
1
```

### Step 3: Build and Start the Audit Service (1 minute)

```bash
cd sql-audit-service
mvn clean package -DskipTests
mvn spring-boot:run
```

**Alternative: Run from JAR**
```bash
java -jar sql-audit-service-web/target/sql-audit-service-web-1.0.0-SNAPSHOT.jar
```

**Wait for startup:**
Look for this line in the logs:
```
Started AuditServiceApplication in 15.234 seconds
```

### Step 4: Access API Documentation (30 seconds)

Open your browser and navigate to:

**Swagger UI:**
```
http://localhost:8090/swagger-ui.html
```

**OpenAPI Spec:**
```
http://localhost:8090/v3/api-docs
http://localhost:8090/v3/api-docs.yaml
```

You should see the interactive API documentation with all available endpoints.

### Step 5: Run Example Queries (1 minute)

Test the API with these sample queries:

**1. Check Service Health:**
```bash
curl http://localhost:8090/health

# Expected response:
# {
#   "status": "UP",
#   "components": {
#     "kafka": {"status": "UP"},
#     "postgres": {"status": "UP"},
#     "clickhouse": {"status": "UP"}
#   }
# }
```

**2. Get Dashboard Statistics:**
```bash
curl http://localhost:8090/api/v1/statistics/dashboard

# Expected response:
# {
#   "totalFindings": 0,
#   "criticalCount": 0,
#   "highCount": 0,
#   "mediumCount": 0,
#   "lowCount": 0,
#   "topRiskySql": [],
#   "trendData": []
# }
```

**3. Query Audit Findings:**
```bash
curl "http://localhost:8090/api/v1/audits?size=10&page=0"

# Expected response (initially empty):
# {
#   "content": [],
#   "totalElements": 0,
#   "totalPages": 0,
#   "size": 10,
#   "number": 0
# }
```

**4. List Registered Checkers:**
```bash
curl http://localhost:8090/api/v1/configuration/checkers

# Expected response:
# [
#   {
#     "checkerId": "SLOW_QUERY",
#     "name": "Slow Query Checker",
#     "description": "Detects slow-running queries",
#     "enabled": true
#   },
#   ...
# ]
```

**Total Time:** ~5 minutes ✅

## Next Steps

### 1. Generate Sample Audit Data

Run the demo application to generate sample audit findings:

```bash
cd ../examples/sql-guard-demo
mvn spring-boot:run
```

This will execute various SQL patterns and generate audit events.

### 2. Explore the API

Use Swagger UI to interactively explore the API:
- http://localhost:8090/swagger-ui.html

Try different API endpoints:
- **Query findings:** GET `/api/v1/audits`
- **Get specific finding:** GET `/api/v1/audits/{id}`
- **Dashboard stats:** GET `/api/v1/statistics/dashboard`
- **Trend data:** GET `/api/v1/statistics/trends`

### 3. Configure a Custom Checker

Follow the [Custom Audit Checker Guide](./custom-audit-checker.md) to create your own checker.

### 4. Integrate with Your Application

See the [Integration Tutorials](../integration/README.md) for:
- CI/CD integration (Jenkins, GitHub Actions)
- Slack alerts
- Metrics export (Prometheus, Grafana)

### 5. Use the API

Check out the [API Examples](../api-examples/README.md) for code samples in:
- Java (RestTemplate and WebClient)
- Python (requests)
- JavaScript/Node.js (fetch and axios)

## Docker Compose Reference

The `docker-compose.yml` includes:

```yaml
services:
  kafka:
    image: confluentinc/cp-kafka:latest
    ports:
      - "9092:9092"

  postgres:
    image: postgres:14
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: audit
      POSTGRES_USER: audit
      POSTGRES_PASSWORD: audit123

  clickhouse:
    image: clickhouse/clickhouse-server:latest
    ports:
      - "8123:8123"
      - "9000:9000"

  audit-service:
    build: ./sql-audit-service
    ports:
      - "8090:8090"
    depends_on:
      - kafka
      - postgres
      - clickhouse
    environment:
      SPRING_PROFILES_ACTIVE: dev
```

**Start all services:**
```bash
docker-compose up -d
```

**Stop all services:**
```bash
docker-compose down
```

**View logs:**
```bash
docker-compose logs -f audit-service
```

## Configuration

### Application Configuration

Edit `sql-audit-service/src/main/resources/application.yml`:

```yaml
server:
  port: 8090

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/audit
    username: audit
    password: audit123

  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: audit-service

clickhouse:
  url: jdbc:clickhouse://localhost:8123/default
  user: default
  password: ""

audit:
  service:
    storage:
      type: clickhouse  # or postgres
    retention-days: 90
```

### Profiles

The application supports multiple profiles:

- **dev** - Development (default)
- **test** - Testing
- **prod** - Production

**Run with specific profile:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

## Troubleshooting

### Service Won't Start

**Problem:** Audit service fails to start with connection errors.

**Solution:**
1. Verify infrastructure services are running:
   ```bash
   docker-compose ps
   ```

2. Check service logs:
   ```bash
   docker-compose logs kafka
   docker-compose logs postgres
   docker-compose logs clickhouse
   ```

3. Restart services:
   ```bash
   docker-compose restart
   ```

### Port Already in Use

**Problem:** Port 8090 is already in use.

**Solution:**
1. Change the port in `application.yml`:
   ```yaml
   server:
     port: 8091
   ```

2. Or stop the conflicting process:
   ```bash
   lsof -ti:8090 | xargs kill -9
   ```

### Kafka Connection Issues

**Problem:** "Connection to node -1 could not be established"

**Solution:**
1. Verify Kafka is accessible:
   ```bash
   docker-compose exec kafka kafka-topics --list --bootstrap-server localhost:9092
   ```

2. Check Kafka logs:
   ```bash
   docker-compose logs kafka | grep ERROR
   ```

3. Restart Kafka:
   ```bash
   docker-compose restart kafka
   ```

### Database Migration Errors

**Problem:** Flyway migration errors on startup.

**Solution:**
1. Clean database and restart:
   ```bash
   docker-compose down -v
   docker-compose up -d
   ```

2. Or manually reset Flyway:
   ```bash
   docker-compose exec postgres psql -U audit -d audit -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
   ```

## Development Workflow

### Hot Reload

Use Spring Boot DevTools for hot reloading:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <optional>true</optional>
</dependency>
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=AuditServiceTest

# Skip tests during build
mvn clean package -DskipTests
```

### Debugging

**Run in Debug Mode:**
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

**Attach Debugger:**
- IntelliJ IDEA: Run > Attach to Process > Select port 5005
- VS Code: Add launch configuration for remote debugging on port 5005

### Code Generation

**Generate API Client:**
```bash
# Using OpenAPI Generator
openapi-generator-cli generate \
  -i http://localhost:8090/v3/api-docs \
  -g java \
  -o ./generated-client
```

## Performance Tips

### 1. Use ClickHouse for High Volume

For high-volume environments, use ClickHouse for audit storage:

```yaml
audit:
  service:
    storage:
      type: clickhouse
```

### 2. Configure Kafka Partitions

Increase Kafka partitions for better throughput:

```bash
docker-compose exec kafka kafka-topics \
  --alter \
  --topic audit-events \
  --partitions 10 \
  --bootstrap-server localhost:9092
```

### 3. Tune JVM Settings

For production, tune JVM settings:

```bash
java -Xms2g -Xmx4g -XX:+UseG1GC \
  -jar sql-audit-service-web/target/sql-audit-service-web-1.0.0-SNAPSHOT.jar
```

## Further Reading

- **Architecture:** [ARCHITECTURE.md](../ARCHITECTURE.md)
- **API Reference:** [REST API Reference](../api/rest-api-reference.md)
- **Custom Checkers:** [Custom Audit Checker Guide](./custom-audit-checker.md)
- **Integration:** [Integration Tutorials](../integration/README.md)
- **Deployment:** [Deployment Guide](../user-guide/deployment.md)

## Support

Need help?
- **GitHub Issues:** https://github.com/example/sql-guard/issues
- **Documentation:** https://sql-guard.readthedocs.io
- **Slack:** #sql-guard-dev
- **Email:** support@example.com

---

**Congratulations!** You now have a fully functional SQL Audit Platform running locally. Start exploring the API and building custom checkers!
