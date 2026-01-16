---
type: Technical Specification
component: REST API
version: 1.0
created: 2024-12-01
updated: 2025-01-16
status: Active
maintainer: SQL Safety Guard Team
---

# REST API Reference

Complete reference for the SQL Audit Service REST API.

## Base URL

```
Local:       http://localhost:8090/api/v1
Development: https://audit-dev.example.com/api/v1
Production:  https://audit.example.com/api/v1
```

## Authentication

Currently, the API does not require authentication in development mode. For production deployments, configure OAuth 2.0 or API key authentication.

## OpenAPI Specification

The complete OpenAPI 3.0 specification is available at:
- YAML: `/v3/api-docs.yaml`
- JSON: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`

See the complete specification: [openapi.yaml](../../sql-audit-service/sql-audit-service-web/src/main/resources/openapi.yaml)

## API Endpoints

### Audit Reports

#### GET /audits

Query audit reports with filtering and pagination.

**Parameters:**
- `sqlId` (string, optional): Filter by SQL identifier
- `riskLevel` (string, optional): Filter by risk level (CRITICAL, HIGH, MEDIUM, LOW)
- `startTime` (datetime, optional): Start time for time range filter
- `endTime` (datetime, optional): End time for time range filter
- `page` (integer, optional, default: 0): Page number
- `size` (integer, optional, default: 20): Page size
- `sort` (string, optional, default: "createdAt,desc"): Sort field and direction

**Response:**
```json
{
  "content": [
    {
      "id": "audit-001",
      "sqlId": "a3d4e5f6",
      "sql": "DELETE FROM users",
      "riskLevel": "CRITICAL",
      "riskScore": 95,
      "checkerId": "NO_WHERE_CLAUSE",
      "message": "DELETE without WHERE clause",
      "recommendation": "Add WHERE clause to limit affected rows",
      "createdAt": "2024-12-23T10:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

**Example:**
```bash
curl "http://localhost:8090/api/v1/audits?riskLevel=CRITICAL&size=10"
```

#### GET /audits/{reportId}

Get a specific audit report by ID.

**Response:**
```json
{
  "id": "audit-001",
  "sqlId": "a3d4e5f6",
  "sql": "DELETE FROM users WHERE id = ?",
  "riskLevel": "HIGH",
  "riskScore": 80,
  "checkerId": "TABLE_LOCK",
  "message": "Table lock held for 2000ms",
  "recommendation": "Optimize transaction scope",
  "metadata": {
    "lock_time_ms": 2000,
    "affected_tables": "users"
  },
  "createdAt": "2024-12-23T10:00:00Z"
}
```

### Statistics

#### GET /statistics/dashboard

Get aggregated dashboard statistics.

**Response:**
```json
{
  "totalFindings": 1234,
  "criticalCount": 45,
  "highCount": 123,
  "mediumCount": 456,
  "lowCount": 610,
  "topRiskySql": [
    {
      "sqlId": "a3d4e5f6",
      "sql": "DELETE FROM users",
      "riskScore": 95,
      "occurrences": 5
    }
  ],
  "trendData": [
    {
      "date": "2024-12-17",
      "count": 150,
      "criticalCount": 10
    }
  ]
}
```

#### GET /statistics/trends

Get trend statistics over time.

**Parameters:**
- `startTime` (datetime, required): Start time
- `endTime` (datetime, required): End time
- `granularity` (string, optional, default: DAY): HOUR, DAY, WEEK, or MONTH

**Response:**
```json
[
  {
    "date": "2024-12-17",
    "count": 150,
    "criticalCount": 10,
    "highCount": 35
  },
  {
    "date": "2024-12-18",
    "count": 180,
    "criticalCount": 12,
    "highCount": 40
  }
]
```

### Configuration

#### GET /configuration/checkers

List all registered audit checkers.

**Response:**
```json
[
  {
    "checkerId": "TABLE_LOCK",
    "name": "Table Lock Checker",
    "description": "Detects queries holding table locks",
    "enabled": true
  },
  {
    "checkerId": "SLOW_QUERY",
    "name": "Slow Query Checker",
    "description": "Detects slow-running queries",
    "enabled": true
  }
]
```

#### GET /configuration/checkers/{checkerId}

Get configuration for a specific checker.

**Response:**
```json
{
  "checkerId": "TABLE_LOCK",
  "enabled": true,
  "threshold": 1000,
  "severityLevels": {
    "warning": 500,
    "critical": 2000
  }
}
```

#### PUT /configuration/checkers/{checkerId}

Update checker configuration.

**Request Body:**
```json
{
  "enabled": true,
  "threshold": 1500,
  "severityLevels": {
    "warning": 750,
    "critical": 2500
  }
}
```

**Response:**
```json
{
  "checkerId": "TABLE_LOCK",
  "enabled": true,
  "threshold": 1500,
  "severityLevels": {
    "warning": 750,
    "critical": 2500
  }
}
```

### Health

#### GET /health

Check service health and dependencies.

**Response:**
```json
{
  "status": "UP",
  "components": {
    "kafka": {
      "status": "UP",
      "details": {
        "brokers": 3
      }
    },
    "clickhouse": {
      "status": "UP"
    },
    "postgres": {
      "status": "UP"
    }
  }
}
```

## Error Handling

All error responses follow this structure:

```json
{
  "timestamp": "2024-12-23T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid risk level: INVALID",
  "path": "/api/v1/audits",
  "details": [
    "riskLevel must be one of: CRITICAL, HIGH, MEDIUM, LOW"
  ]
}
```

**HTTP Status Codes:**
- `200`: Success
- `400`: Bad Request - Invalid parameters
- `404`: Not Found - Resource not found
- `500`: Internal Server Error

## Pagination

All list endpoints support pagination with these parameters:
- `page`: Page number (zero-based, default: 0)
- `size`: Page size (default: 20, max: 1000)
- `sort`: Sort criteria (default: "createdAt,desc")

**Pagination Response:**
```json
{
  "content": [...],
  "totalElements": 1234,
  "totalPages": 62,
  "size": 20,
  "number": 0,
  "first": true,
  "last": false
}
```

## Filtering

### Date/Time Filtering

Use ISO-8601 format for date/time filters:
```
2024-12-23T10:00:00Z
2024-12-23T10:00:00+08:00
```

### Enum Filtering

Enums are case-sensitive:
- Risk Level: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`
- Granularity: `HOUR`, `DAY`, `WEEK`, `MONTH`

## Rate Limiting

Production environments enforce rate limiting:
- Limit: 100 requests per minute per IP
- Headers:
  - `X-RateLimit-Limit`: Request limit
  - `X-RateLimit-Remaining`: Remaining requests
  - `X-RateLimit-Reset`: Reset time (Unix timestamp)

## Code Examples

See the [API Examples](../api-examples/README.md) for complete code examples in:
- Java (RestTemplate and WebClient)
- Python (requests)
- JavaScript/Node.js (fetch and axios)

## Further Reading

- [Custom Audit Checker Guide](../developer-guide/custom-audit-checker.md)
- [Integration Tutorials](../integration/README.md)
- [Developer Quickstart](../developer-guide/quickstart.md)
