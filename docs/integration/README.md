# Integration Tutorials

This directory contains tutorials and examples for integrating the SQL Audit Service with CI/CD pipelines, alerting systems, and monitoring tools.

## Available Tutorials

### 1. CI/CD Integration (Jenkins)

**File:** `ci-cd-jenkins.groovy`

Integrate SQL audit checks into your Jenkins CI/CD pipeline. This example shows how to:
- Query audit API during build
- Fail builds if CRITICAL findings exist
- Generate audit reports as build artifacts

**Usage:**
```groovy
// Add to your Jenkinsfile
stage('SQL Audit Check') {
    steps {
        script {
            load 'ci-cd-jenkins.groovy'
        }
    }
}
```

### 2. Custom Alerting (Slack)

**File:** `slack-alert.py`

Set up automated Slack notifications for CRITICAL SQL findings. This script:
- Polls audit API for new findings
- Sends formatted messages to Slack channels
- Includes SQL text, risk score, and recommendations

**Usage:**
```bash
# Set environment variables
export SLACK_TOKEN="xoxb-your-token"
export SLACK_CHANNEL="#sql-audit-alerts"

# Run as background service
python slack-alert.py &
```

### 3. Metrics Export

**File:** `MetricsExporter.java`

Export audit statistics to custom monitoring systems. This example:
- Periodically queries dashboard statistics
- Exports metrics to Prometheus, Grafana, or custom systems
- Provides gauge metrics for critical/high/medium/low counts

**Usage:**
```java
@Component
public class AuditMetricsExporter {
    @Scheduled(fixedRate = 60000)
    public void exportMetrics() {
        // Export logic
    }
}
```

## Integration Patterns

### Pattern 1: Build-Time Validation

**Goal:** Prevent deployment if dangerous SQL patterns are detected

**Approach:**
1. Run static scanner during build
2. Query audit service for recent findings
3. Fail build if threshold exceeded

**Example:**
```bash
# In CI/CD pipeline
CRITICAL_COUNT=$(curl -s "http://audit-service:8090/api/v1/statistics/dashboard" | jq '.criticalCount')

if [ "$CRITICAL_COUNT" -gt 0 ]; then
    echo "Found $CRITICAL_COUNT critical SQL issues. Build failed."
    exit 1
fi
```

### Pattern 2: Runtime Monitoring

**Goal:** Continuous monitoring of production SQL patterns

**Approach:**
1. Audit service collects execution data via Kafka
2. Background job polls for new findings
3. Alert on high-risk patterns

**Example:**
```python
while True:
    findings = query_recent_critical_findings()
    for finding in findings:
        send_alert(finding)
    time.sleep(60)
```

### Pattern 3: Dashboard Integration

**Goal:** Display audit metrics in existing dashboards

**Approach:**
1. Query statistics API
2. Transform to dashboard format
3. Update dashboard periodically

**Example:**
```javascript
setInterval(async () => {
    const stats = await getDashboardStats();
    updateDashboardWidget(stats);
}, 30000); // Every 30 seconds
```

## CI/CD Integration Details

### Jenkins

**Jenkinsfile Example:**
```groovy
pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                sh 'mvn clean package'
            }
        }

        stage('SQL Audit Check') {
            steps {
                script {
                    def auditUrl = "http://audit-service:8090/api/v1/audits"
                    def response = sh(
                        script: "curl -s '${auditUrl}?riskLevel=CRITICAL&limit=1'",
                        returnStdout: true
                    ).trim()

                    def findings = readJSON text: response

                    if (findings.totalElements > 0) {
                        error("Found ${findings.totalElements} CRITICAL audit findings")
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                sh 'kubectl apply -f deployment.yaml'
            }
        }
    }

    post {
        always {
            script {
                // Generate audit report
                sh 'curl -s http://audit-service:8090/api/v1/statistics/dashboard > audit-report.json'
                archiveArtifacts artifacts: 'audit-report.json'
            }
        }
    }
}
```

### GitHub Actions

**.github/workflows/sql-audit.yml:**
```yaml
name: SQL Audit Check

on: [pull_request]

jobs:
  audit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2

      - name: Check for Critical SQL Issues
        run: |
          RESPONSE=$(curl -s "http://audit-service:8090/api/v1/audits?riskLevel=CRITICAL&limit=1")
          TOTAL=$(echo $RESPONSE | jq '.totalElements')

          if [ "$TOTAL" -gt 0 ]; then
            echo "::error::Found $TOTAL critical SQL issues"
            exit 1
          fi
```

### GitLab CI

**.gitlab-ci.yml:**
```yaml
sql-audit:
  stage: test
  script:
    - |
      RESPONSE=$(curl -s "http://audit-service:8090/api/v1/audits?riskLevel=CRITICAL&limit=1")
      TOTAL=$(echo $RESPONSE | jq '.totalElements')
      if [ "$TOTAL" -gt 0 ]; then
        echo "Found $TOTAL critical SQL issues"
        exit 1
      fi
  only:
    - merge_requests
```

## Alerting Integration

### Slack

**Features:**
- Rich message formatting
- Severity-based channels
- Interactive buttons for acknowledgment

**Setup:**
1. Create Slack app and bot token
2. Configure channel permissions
3. Run alert script as daemon

### PagerDuty

**Integration:**
```python
import requests

def send_pagerduty_alert(finding):
    event = {
        "routing_key": "YOUR_INTEGRATION_KEY",
        "event_action": "trigger",
        "payload": {
            "summary": f"Critical SQL: {finding['sql'][:100]}",
            "severity": "critical",
            "source": "SQL Audit Service",
            "custom_details": {
                "risk_score": finding['riskScore'],
                "checker": finding['checkerId'],
                "recommendation": finding['recommendation']
            }
        }
    }

    requests.post("https://events.pagerduty.com/v2/enqueue", json=event)
```

### Email

**Configuration:**
```python
import smtplib
from email.mime.text import MIMEText

def send_email_alert(finding):
    msg = MIMEText(f"""
    Critical SQL Issue Detected

    SQL: {finding['sql']}
    Risk Score: {finding['riskScore']}
    Message: {finding['message']}
    Recommendation: {finding['recommendation']}
    """)

    msg['Subject'] = 'SQL Audit Alert: CRITICAL'
    msg['From'] = 'audit@example.com'
    msg['To'] = 'team@example.com'

    smtp = smtplib.SMTP('localhost')
    smtp.send_message(msg)
    smtp.quit()
```

## Monitoring Integration

### Prometheus

**Metrics Export:**
```java
@Component
public class PrometheusExporter {
    private final MeterRegistry registry;

    @Scheduled(fixedRate = 60000)
    public void exportMetrics() {
        DashboardStats stats = queryDashboard();

        registry.gauge("sql_audit_findings_total", stats.getTotalFindings());
        registry.gauge("sql_audit_findings_critical", stats.getCriticalCount());
        registry.gauge("sql_audit_findings_high", stats.getHighCount());
    }
}
```

### Grafana

**Dashboard JSON:**
```json
{
  "dashboard": {
    "title": "SQL Audit Metrics",
    "panels": [
      {
        "title": "Critical Findings",
        "targets": [
          {
            "expr": "sql_audit_findings_critical"
          }
        ]
      }
    ]
  }
}
```

### Datadog

**Custom Metrics:**
```python
from datadog import initialize, api

initialize(api_key='YOUR_API_KEY', app_key='YOUR_APP_KEY')

def export_to_datadog(stats):
    api.Metric.send(
        metric='sql.audit.findings.total',
        points=stats['totalFindings']
    )
    api.Metric.send(
        metric='sql.audit.findings.critical',
        points=stats['criticalCount']
    )
```

## Best Practices

### 1. Polling Frequency

- **CI/CD:** On every build
- **Alerting:** Every 1-5 minutes
- **Metrics:** Every 30-60 seconds

### 2. Error Handling

- Implement retries with exponential backoff
- Log failed API calls
- Use circuit breaker pattern for failing services

### 3. Security

- Use HTTPS in production
- Store API tokens in secrets management
- Implement rate limiting on client side

### 4. Performance

- Cache dashboard statistics (TTL: 30s)
- Use pagination for large result sets
- Implement connection pooling

## Prerequisites

All integration examples require:
1. SQL Audit Service running and accessible
2. Network connectivity to audit service API
3. Appropriate credentials and permissions

## Further Reading

- [REST API Reference](../api/rest-api-reference.md)
- [API Examples](../api-examples/README.md)
- [Developer Quickstart](../developer-guide/quickstart.md)

## Support

For integration help:
- GitHub Issues: https://github.com/example/sql-guard/issues
- Documentation: https://sql-guard.readthedocs.io
- Slack: #sql-guard-integrations
