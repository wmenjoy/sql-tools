import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * Example: Query audit findings using RestTemplate.
 *
 * <p>This example demonstrates how to query the SQL Audit Service API
 * to retrieve critical findings using Spring's RestTemplate.</p>
 *
 * <h2>Prerequisites</h2>
 * <ul>
 *   <li>Audit service running at localhost:8090</li>
 *   <li>Spring Web dependency</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * java QueryFindings.java
 * }</pre>
 */
public class QueryFindings {

    private static final String BASE_URL = "http://localhost:8090/api/v1";

    public static void main(String[] args) {
        RestTemplate restTemplate = new RestTemplate();

        // Query recent CRITICAL findings
        String url = BASE_URL + "/audits?riskLevel=CRITICAL&size=10&sort=createdAt,desc";

        try {
            ResponseEntity<AuditFindingsResponse> response =
                restTemplate.getForEntity(url, AuditFindingsResponse.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                AuditFindingsResponse findings = response.getBody();

                System.out.println("Total CRITICAL findings: " + findings.getTotalElements());
                System.out.println("Page " + (findings.getNumber() + 1) + " of " + findings.getTotalPages());
                System.out.println("\nFindings:");

                for (AuditReport finding : findings.getContent()) {
                    System.out.println("----------------------------------------");
                    System.out.println("ID: " + finding.getId());
                    System.out.println("SQL: " + finding.getSql());
                    System.out.println("Risk Score: " + finding.getRiskScore());
                    System.out.println("Checker: " + finding.getCheckerId());
                    System.out.println("Message: " + finding.getMessage());
                    System.out.println("Recommendation: " + finding.getRecommendation());
                }
            }
        } catch (Exception e) {
            System.err.println("Error querying audit findings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Query findings with custom filters.
     */
    public static void queryWithFilters() {
        RestTemplate restTemplate = new RestTemplate();

        // Build URL with parameters
        String url = BASE_URL + "/audits?sqlId={sqlId}&riskLevel={riskLevel}&page={page}&size={size}";

        Map<String, Object> params = new HashMap<>();
        params.put("sqlId", "a3d4e5f6789012345678901234567890");
        params.put("riskLevel", "HIGH");
        params.put("page", 0);
        params.put("size", 20);

        ResponseEntity<AuditFindingsResponse> response =
            restTemplate.getForEntity(url, AuditFindingsResponse.class, params);

        System.out.println("Filtered results: " + response.getBody().getTotalElements());
    }

    /**
     * Get a specific audit report by ID.
     */
    public static void getAuditById(String reportId) {
        RestTemplate restTemplate = new RestTemplate();

        String url = BASE_URL + "/audits/" + reportId;

        try {
            AuditReport report = restTemplate.getForObject(url, AuditReport.class);

            System.out.println("Audit Report: " + report.getId());
            System.out.println("SQL: " + report.getSql());
            System.out.println("Risk Score: " + report.getRiskScore());
            System.out.println("Metadata: " + report.getMetadata());
        } catch (Exception e) {
            System.err.println("Error retrieving audit report: " + e.getMessage());
        }
    }
}

// Response DTOs

class AuditFindingsResponse {
    private java.util.List<AuditReport> content;
    private long totalElements;
    private int totalPages;
    private int size;
    private int number;

    // Getters and setters
    public java.util.List<AuditReport> getContent() { return content; }
    public void setContent(java.util.List<AuditReport> content) { this.content = content; }
    public long getTotalElements() { return totalElements; }
    public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }
}

class AuditReport {
    private String id;
    private String sqlId;
    private String sql;
    private String riskLevel;
    private int riskScore;
    private String checkerId;
    private String message;
    private String recommendation;
    private Map<String, Object> metadata;
    private String createdAt;

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSqlId() { return sqlId; }
    public void setSqlId(String sqlId) { this.sqlId = sqlId; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public String getCheckerId() { return checkerId; }
    public void setCheckerId(String checkerId) { this.checkerId = checkerId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
