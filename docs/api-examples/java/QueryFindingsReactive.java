import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Example: Query audit findings using reactive WebClient.
 *
 * <p>This example demonstrates how to query the SQL Audit Service API
 * using Spring's reactive WebClient for non-blocking I/O.</p>
 *
 * <h2>Prerequisites</h2>
 * <ul>
 *   <li>Audit service running at localhost:8090</li>
 *   <li>Spring WebFlux dependency</li>
 * </ul>
 */
public class QueryFindingsReactive {

    private static final String BASE_URL = "http://localhost:8090/api/v1";

    public static void main(String[] args) {
        WebClient webClient = WebClient.create(BASE_URL);

        // Query CRITICAL findings reactively
        webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/audits")
                .queryParam("riskLevel", "CRITICAL")
                .queryParam("size", 10)
                .queryParam("sort", "createdAt,desc")
                .build())
            .retrieve()
            .bodyToMono(AuditFindingsResponse.class)
            .subscribe(
                findings -> {
                    System.out.println("Total CRITICAL findings: " + findings.getTotalElements());
                    findings.getContent().forEach(f -> {
                        System.out.println("----------------------------------------");
                        System.out.println("SQL: " + f.getSql());
                        System.out.println("Risk Score: " + f.getRiskScore());
                        System.out.println("Message: " + f.getMessage());
                    });
                },
                error -> System.err.println("Error: " + error.getMessage())
            );

        // Keep main thread alive for async processing
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get audit report by ID using reactive API.
     */
    public static Mono<AuditReport> getAuditByIdAsync(String reportId) {
        WebClient webClient = WebClient.create(BASE_URL);

        return webClient.get()
            .uri("/audits/{id}", reportId)
            .retrieve()
            .bodyToMono(AuditReport.class);
    }

    /**
     * Get dashboard statistics using reactive API.
     */
    public static void getDashboardStatsAsync() {
        WebClient webClient = WebClient.create(BASE_URL);

        webClient.get()
            .uri("/statistics/dashboard")
            .retrieve()
            .bodyToMono(DashboardStats.class)
            .subscribe(
                stats -> {
                    System.out.println("Dashboard Statistics:");
                    System.out.println("Total Findings: " + stats.getTotalFindings());
                    System.out.println("Critical: " + stats.getCriticalCount());
                    System.out.println("High: " + stats.getHighCount());
                    System.out.println("Medium: " + stats.getMediumCount());
                    System.out.println("Low: " + stats.getLowCount());
                },
                error -> System.err.println("Error: " + error.getMessage())
            );
    }
}

class DashboardStats {
    private long totalFindings;
    private int criticalCount;
    private int highCount;
    private int mediumCount;
    private int lowCount;

    // Getters and setters
    public long getTotalFindings() { return totalFindings; }
    public void setTotalFindings(long totalFindings) { this.totalFindings = totalFindings; }
    public int getCriticalCount() { return criticalCount; }
    public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }
    public int getHighCount() { return highCount; }
    public void setHighCount(int highCount) { this.highCount = highCount; }
    public int getMediumCount() { return mediumCount; }
    public void setMediumCount(int mediumCount) { this.mediumCount = mediumCount; }
    public int getLowCount() { return lowCount; }
    public void setLowCount(int lowCount) { this.lowCount = lowCount; }
}
