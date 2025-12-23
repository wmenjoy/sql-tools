// Jenkinsfile: SQL Audit Check Integration
// This Groovy script integrates SQL audit checks into Jenkins CI/CD pipeline

def auditServiceUrl = 'http://audit-service:8090/api/v1'
def criticalThreshold = 0  // Fail build if any CRITICAL findings exist
def highThreshold = 5      // Fail build if more than 5 HIGH findings exist

pipeline {
    agent any

    environment {
        AUDIT_SERVICE_URL = "${auditServiceUrl}"
    }

    stages {
        stage('Build') {
            steps {
                echo 'Building application...'
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('SQL Audit Check') {
            steps {
                script {
                    echo 'Checking for SQL audit findings...'

                    // Query CRITICAL findings
                    def criticalFindings = queryCriticalFindings()

                    // Query HIGH findings
                    def highFindings = queryHighFindings()

                    // Check thresholds
                    if (criticalFindings.totalElements > criticalThreshold) {
                        error("Build failed: Found ${criticalFindings.totalElements} CRITICAL SQL findings. " +
                              "Please fix before merging.")
                    }

                    if (highFindings.totalElements > highThreshold) {
                        error("Build failed: Found ${highFindings.totalElements} HIGH SQL findings. " +
                              "Threshold is ${highThreshold}.")
                    }

                    // Print summary
                    echo "SQL Audit Summary:"
                    echo "  Critical: ${criticalFindings.totalElements}"
                    echo "  High: ${highFindings.totalElements}"

                    // Generate detailed report
                    generateAuditReport(criticalFindings, highFindings)
                }
            }
        }

        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                echo 'Deploying to production...'
                sh 'kubectl apply -f deployment.yaml'
            }
        }
    }

    post {
        always {
            script {
                // Archive audit report
                archiveArtifacts artifacts: 'audit-report.json, audit-report.html', allowEmptyArchive: true

                // Publish report to Jenkins
                publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: '.',
                    reportFiles: 'audit-report.html',
                    reportName: 'SQL Audit Report'
                ])
            }
        }

        failure {
            script {
                // Send notification on failure
                sendNotification('FAILED')
            }
        }

        success {
            script {
                sendNotification('SUCCESS')
            }
        }
    }
}

// Helper function: Query CRITICAL findings
def queryCriticalFindings() {
    def response = sh(
        script: "curl -s '${auditServiceUrl}/audits?riskLevel=CRITICAL&size=100'",
        returnStdout: true
    ).trim()

    return readJSON text: response
}

// Helper function: Query HIGH findings
def queryHighFindings() {
    def response = sh(
        script: "curl -s '${auditServiceUrl}/audits?riskLevel=HIGH&size=100'",
        returnStdout: true
    ).trim()

    return readJSON text: response
}

// Helper function: Generate audit report
def generateAuditReport(criticalFindings, highFindings) {
    // Generate JSON report
    def report = [
        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss'),
        buildNumber: env.BUILD_NUMBER,
        branch: env.BRANCH_NAME,
        critical: [
            count: criticalFindings.totalElements,
            findings: criticalFindings.content
        ],
        high: [
            count: highFindings.totalElements,
            findings: highFindings.content
        ]
    ]

    writeJSON file: 'audit-report.json', json: report

    // Generate HTML report
    def htmlReport = """
    <html>
    <head>
        <title>SQL Audit Report - Build #${env.BUILD_NUMBER}</title>
        <style>
            body { font-family: Arial, sans-serif; margin: 20px; }
            h1 { color: #333; }
            .summary { background: #f5f5f5; padding: 15px; margin: 20px 0; }
            .critical { color: #d32f2f; font-weight: bold; }
            .high { color: #f57c00; font-weight: bold; }
            table { border-collapse: collapse; width: 100%; margin-top: 20px; }
            th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
            th { background-color: #4CAF50; color: white; }
        </style>
    </head>
    <body>
        <h1>SQL Audit Report</h1>
        <div class="summary">
            <p>Build: #${env.BUILD_NUMBER}</p>
            <p>Branch: ${env.BRANCH_NAME}</p>
            <p>Timestamp: ${new Date().format('yyyy-MM-dd HH:mm:ss')}</p>
            <p class="critical">Critical Findings: ${criticalFindings.totalElements}</p>
            <p class="high">High Findings: ${highFindings.totalElements}</p>
        </div>
    """

    // Add CRITICAL findings
    if (criticalFindings.totalElements > 0) {
        htmlReport += """
        <h2 class="critical">Critical Findings</h2>
        <table>
            <tr>
                <th>SQL</th>
                <th>Checker</th>
                <th>Risk Score</th>
                <th>Message</th>
                <th>Recommendation</th>
            </tr>
        """

        criticalFindings.content.each { finding ->
            htmlReport += """
            <tr>
                <td>${finding.sql}</td>
                <td>${finding.checkerId}</td>
                <td>${finding.riskScore}</td>
                <td>${finding.message}</td>
                <td>${finding.recommendation}</td>
            </tr>
            """
        }

        htmlReport += "</table>"
    }

    // Add HIGH findings
    if (highFindings.totalElements > 0) {
        htmlReport += """
        <h2 class="high">High Findings</h2>
        <table>
            <tr>
                <th>SQL</th>
                <th>Checker</th>
                <th>Risk Score</th>
                <th>Message</th>
                <th>Recommendation</th>
            </tr>
        """

        highFindings.content.each { finding ->
            htmlReport += """
            <tr>
                <td>${finding.sql}</td>
                <td>${finding.checkerId}</td>
                <td>${finding.riskScore}</td>
                <td>${finding.message}</td>
                <td>${finding.recommendation}</td>
            </tr>
            """
        }

        htmlReport += "</table>"
    }

    htmlReport += """
    </body>
    </html>
    """

    writeFile file: 'audit-report.html', text: htmlReport
}

// Helper function: Send notification
def sendNotification(status) {
    def color = status == 'SUCCESS' ? 'good' : 'danger'
    def message = status == 'SUCCESS' ?
        "SQL Audit check passed for build #${env.BUILD_NUMBER}" :
        "SQL Audit check failed for build #${env.BUILD_NUMBER}"

    // Example: Slack notification
    // slackSend(
    //     color: color,
    //     message: message,
    //     channel: '#sql-audit'
    // )

    echo "Notification: ${message}"
}
