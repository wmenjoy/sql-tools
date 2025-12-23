# API Usage Examples

This directory contains code examples demonstrating how to interact with the SQL Audit Service REST API using various programming languages.

## Overview

These examples show how to:
- Query audit findings with filtering and pagination
- Retrieve dashboard statistics
- Manage checker configuration
- Handle errors and retries

**Important:** These are code snippets for demonstration, not complete SDK packages. They use native HTTP clients to show direct API interaction.

## Languages

### Java

- **QueryFindings.java** - Query audit findings using RestTemplate (blocking I/O)
- **QueryFindingsReactive.java** - Query audit findings using WebClient (non-blocking I/O)

**Dependencies:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

**Usage:**
```bash
javac QueryFindings.java
java QueryFindings
```

### Python

- **query_findings.py** - Complete examples using the requests library

**Dependencies:**
```bash
pip install requests
```

**Usage:**
```bash
python query_findings.py
```

### JavaScript/Node.js

- **queryFindings.js** - Examples using native fetch API
- **getDashboardStats.js** - Examples using axios library

**Dependencies:**
```bash
npm install axios
```

**Usage:**
```bash
node queryFindings.js
node getDashboardStats.js
```

## Common Operations

### Query Critical Findings

**Java:**
```java
String url = BASE_URL + "/audits?riskLevel=CRITICAL&size=10";
ResponseEntity<AuditFindingsResponse> response =
    restTemplate.getForEntity(url, AuditFindingsResponse.class);
```

**Python:**
```python
response = requests.get(
    f"{BASE_URL}/audits",
    params={"riskLevel": "CRITICAL", "size": 10}
)
findings = response.json()
```

**JavaScript:**
```javascript
const url = new URL(`${BASE_URL}/audits`);
url.searchParams.append('riskLevel', 'CRITICAL');
url.searchParams.append('size', '10');
const response = await fetch(url);
const findings = await response.json();
```

### Get Dashboard Statistics

**Java:**
```java
DashboardStats stats = restTemplate.getForObject(
    BASE_URL + "/statistics/dashboard",
    DashboardStats.class
);
```

**Python:**
```python
response = requests.get(f"{BASE_URL}/statistics/dashboard")
stats = response.json()
```

**JavaScript:**
```javascript
const response = await axios.get(`${BASE_URL}/statistics/dashboard`);
const stats = response.data;
```

### Update Checker Configuration

**Java:**
```java
CheckerConfigUpdate update = new CheckerConfigUpdate();
update.setEnabled(true);
update.setThreshold(1500);

restTemplate.put(
    BASE_URL + "/configuration/checkers/TABLE_LOCK",
    update
);
```

**Python:**
```python
config = {
    "enabled": True,
    "threshold": 1500
}
requests.put(
    f"{BASE_URL}/configuration/checkers/TABLE_LOCK",
    json=config
)
```

**JavaScript:**
```javascript
const config = {
    enabled: true,
    threshold: 1500
};
await axios.put(
    `${BASE_URL}/configuration/checkers/TABLE_LOCK`,
    config
);
```

## Error Handling

### Java
```java
try {
    ResponseEntity<AuditReport> response = restTemplate.getForEntity(url, AuditReport.class);
} catch (HttpClientErrorException.NotFound e) {
    System.err.println("Report not found: " + e.getMessage());
} catch (RestClientException e) {
    System.err.println("API error: " + e.getMessage());
}
```

### Python
```python
try:
    response = requests.get(url)
    response.raise_for_status()
except requests.exceptions.HTTPError as e:
    print(f"HTTP error: {e}")
except requests.exceptions.RequestException as e:
    print(f"Request error: {e}")
```

### JavaScript
```javascript
try {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
    }
    const data = await response.json();
} catch (error) {
    console.error('Error:', error.message);
}
```

## Pagination

All list endpoints support pagination:

```
GET /audits?page=0&size=20&sort=createdAt,desc
```

**Parameters:**
- `page`: Page number (zero-based, default: 0)
- `size`: Page size (default: 20, max: 1000)
- `sort`: Sort criteria (default: "createdAt,desc")

**Response:**
```json
{
  "content": [...],
  "totalElements": 1234,
  "totalPages": 62,
  "size": 20,
  "number": 0
}
```

## Filtering

### By Risk Level
```
GET /audits?riskLevel=CRITICAL
```

### By Time Range
```
GET /audits?startTime=2024-01-01T00:00:00Z&endTime=2024-12-31T23:59:59Z
```

### By SQL ID
```
GET /audits?sqlId=a3d4e5f6789012345678901234567890
```

## Rate Limiting

Production environments enforce rate limiting:
- **Limit:** 100 requests per minute per IP
- **Headers:**
  - `X-RateLimit-Limit`: Request limit
  - `X-RateLimit-Remaining`: Remaining requests
  - `X-RateLimit-Reset`: Reset time

## Prerequisites

Before running these examples:

1. **Start the Audit Service:**
   ```bash
   cd sql-audit-service
   mvn spring-boot:run
   ```

2. **Verify Service is Running:**
   ```bash
   curl http://localhost:8090/health
   ```

3. **Check API Documentation:**
   - Swagger UI: http://localhost:8090/swagger-ui.html
   - OpenAPI Spec: http://localhost:8090/v3/api-docs

## Further Reading

- [REST API Reference](../api/rest-api-reference.md)
- [Developer Quickstart](../developer-guide/quickstart.md)
- [Integration Tutorials](../integration/README.md)
- [Custom Checker Guide](../developer-guide/custom-audit-checker.md)

## Support

For questions and issues:
- GitHub Issues: https://github.com/example/sql-guard/issues
- Documentation: https://sql-guard.readthedocs.io
- Slack: #sql-guard-dev
