package com.example.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Exports SQL Audit metrics to monitoring systems (Prometheus, Grafana, etc.).
 *
 * <p>This component periodically queries the SQL Audit Service API and exports
 * metrics to a MeterRegistry, which can be consumed by Prometheus, Grafana,
 * Datadog, or other monitoring systems.</p>
 *
 * <h2>Exported Metrics</h2>
 * <ul>
 *   <li><b>sql.audit.findings.total</b> - Total number of audit findings</li>
 *   <li><b>sql.audit.findings.critical</b> - Count of CRITICAL findings</li>
 *   <li><b>sql.audit.findings.high</b> - Count of HIGH findings</li>
 *   <li><b>sql.audit.findings.medium</b> - Count of MEDIUM findings</li>
 *   <li><b>sql.audit.findings.low</b> - Count of LOW findings</li>
 *   <li><b>sql.audit.checkers.active</b> - Number of active checkers</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <pre>{@code
 * # application.yml
 * audit:
 *   service:
 *     url: http://localhost:8090/api/v1
 *   metrics:
 *     enabled: true
 *     export-interval: 60000  # 1 minute
 * }</pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Metrics are exported automatically via @Scheduled
 * // Access metrics via Prometheus endpoint:
 * // GET /actuator/prometheus
 * }</pre>
 *
 * @see MeterRegistry
 * @since 2.0.0
 */
@Component
public class MetricsExporter {

    private static final Logger logger = Logger.getLogger(MetricsExporter.class.getName());

    @Value("${audit.service.url:http://localhost:8090/api/v1}")
    private String auditServiceUrl;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private RestTemplate restTemplate;

    private final Map<String, Double> lastMetrics = new ConcurrentHashMap<>();

    /**
     * Exports metrics every minute (configurable via fixedRateString).
     *
     * <p>This method queries the audit service dashboard API and exports
     * metrics to the MeterRegistry.</p>
     */
    @Scheduled(fixedRateString = "${audit.metrics.export-interval:60000}")
    public void exportMetrics() {
        try {
            logger.info("Exporting SQL Audit metrics...");

            // Query dashboard statistics
            DashboardStats stats = queryDashboardStats();

            if (stats != null) {
                // Export finding counts
                exportFindingMetrics(stats);

                // Export checker metrics
                exportCheckerMetrics();

                // Export trend metrics
                exportTrendMetrics(stats);

                logger.info("Successfully exported SQL Audit metrics");
            }

        } catch (Exception e) {
            logger.severe("Error exporting metrics: " + e.getMessage());
        }
    }

    /**
     * Exports finding count metrics.
     */
    private void exportFindingMetrics(DashboardStats stats) {
        // Total findings
        registerGauge("sql.audit.findings.total",
            "Total number of audit findings",
            stats.getTotalFindings());

        // Findings by severity
        registerGauge("sql.audit.findings.critical",
            "Count of CRITICAL findings",
            stats.getCriticalCount());

        registerGauge("sql.audit.findings.high",
            "Count of HIGH findings",
            stats.getHighCount());

        registerGauge("sql.audit.findings.medium",
            "Count of MEDIUM findings",
            stats.getMediumCount());

        registerGauge("sql.audit.findings.low",
            "Count of LOW findings",
            stats.getLowCount());

        // Calculate percentages
        long total = stats.getTotalFindings();
        if (total > 0) {
            registerGauge("sql.audit.findings.critical.percentage",
                "Percentage of CRITICAL findings",
                (double) stats.getCriticalCount() / total * 100);

            registerGauge("sql.audit.findings.high.percentage",
                "Percentage of HIGH findings",
                (double) stats.getHighCount() / total * 100);
        }
    }

    /**
     * Exports checker-related metrics.
     */
    private void exportCheckerMetrics() {
        try {
            String url = auditServiceUrl + "/configuration/checkers";
            CheckerInfo[] checkers = restTemplate.getForObject(url, CheckerInfo[].class);

            if (checkers != null) {
                // Count active checkers
                long activeCount = 0;
                long totalCount = checkers.length;

                for (CheckerInfo checker : checkers) {
                    if (checker.isEnabled()) {
                        activeCount++;

                        // Export per-checker metrics
                        registerGauge("sql.audit.checker.status",
                            "Checker status (1=enabled, 0=disabled)",
                            1.0,
                            Tags.of("checker_id", checker.getCheckerId()));
                    }
                }

                registerGauge("sql.audit.checkers.active",
                    "Number of active checkers",
                    activeCount);

                registerGauge("sql.audit.checkers.total",
                    "Total number of checkers",
                    totalCount);
            }

        } catch (Exception e) {
            logger.warning("Error exporting checker metrics: " + e.getMessage());
        }
    }

    /**
     * Exports trend metrics from dashboard.
     */
    private void exportTrendMetrics(DashboardStats stats) {
        List<TrendDataPoint> trendData = stats.getTrendData();
        if (trendData != null && !trendData.isEmpty()) {
            // Export today's findings
            TrendDataPoint today = trendData.get(trendData.size() - 1);

            registerGauge("sql.audit.findings.today",
                "Findings discovered today",
                today.getCount());

            registerGauge("sql.audit.findings.today.critical",
                "Critical findings discovered today",
                today.getCriticalCount());

            // Calculate trend (comparing to yesterday)
            if (trendData.size() > 1) {
                TrendDataPoint yesterday = trendData.get(trendData.size() - 2);
                long trend = today.getCount() - yesterday.getCount();

                registerGauge("sql.audit.findings.trend",
                    "Change in findings compared to previous period",
                    trend);
            }
        }
    }

    /**
     * Queries dashboard statistics from audit service.
     */
    private DashboardStats queryDashboardStats() {
        try {
            String url = auditServiceUrl + "/statistics/dashboard";
            return restTemplate.getForObject(url, DashboardStats.class);
        } catch (Exception e) {
            logger.warning("Error querying dashboard stats: " + e.getMessage());
            return null;
        }
    }

    /**
     * Registers or updates a gauge metric.
     */
    private void registerGauge(String name, String description, double value) {
        registerGauge(name, description, value, Tags.empty());
    }

    /**
     * Registers or updates a gauge metric with tags.
     */
    private void registerGauge(String name, String description, double value, Tags tags) {
        // Update last metrics cache
        String key = name + tags.toString();
        lastMetrics.put(key, value);

        // Register gauge that reads from cache
        meterRegistry.gauge(name, tags, lastMetrics, map -> map.getOrDefault(key, 0.0));
    }

    /**
     * Exports custom business metrics.
     */
    @Scheduled(fixedRate = 300000)  // Every 5 minutes
    public void exportCustomMetrics() {
        try {
            // Example: Export top risky SQL patterns
            DashboardStats stats = queryDashboardStats();
            if (stats != null && stats.getTopRiskySql() != null) {
                int topRiskyCount = stats.getTopRiskySql().size();

                registerGauge("sql.audit.risky.patterns.count",
                    "Number of distinct risky SQL patterns",
                    topRiskyCount);

                // Export highest risk score
                if (topRiskyCount > 0) {
                    RiskySqlSummary topRisky = stats.getTopRiskySql().get(0);
                    registerGauge("sql.audit.risk.score.max",
                        "Highest risk score across all patterns",
                        topRisky.getRiskScore());
                }
            }

        } catch (Exception e) {
            logger.warning("Error exporting custom metrics: " + e.getMessage());
        }
    }
}

// DTO classes

class DashboardStats {
    private long totalFindings;
    private int criticalCount;
    private int highCount;
    private int mediumCount;
    private int lowCount;
    private List<RiskySqlSummary> topRiskySql;
    private List<TrendDataPoint> trendData;

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
    public List<RiskySqlSummary> getTopRiskySql() { return topRiskySql; }
    public void setTopRiskySql(List<RiskySqlSummary> topRiskySql) { this.topRiskySql = topRiskySql; }
    public List<TrendDataPoint> getTrendData() { return trendData; }
    public void setTrendData(List<TrendDataPoint> trendData) { this.trendData = trendData; }
}

class RiskySqlSummary {
    private String sqlId;
    private String sql;
    private int riskScore;
    private int occurrences;

    public String getSqlId() { return sqlId; }
    public void setSqlId(String sqlId) { this.sqlId = sqlId; }
    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }
    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }
    public int getOccurrences() { return occurrences; }
    public void setOccurrences(int occurrences) { this.occurrences = occurrences; }
}

class TrendDataPoint {
    private String date;
    private long count;
    private int criticalCount;
    private int highCount;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
    public int getCriticalCount() { return criticalCount; }
    public void setCriticalCount(int criticalCount) { this.criticalCount = criticalCount; }
    public int getHighCount() { return highCount; }
    public void setHighCount(int highCount) { this.highCount = highCount; }
}

class CheckerInfo {
    private String checkerId;
    private String name;
    private String description;
    private boolean enabled;

    public String getCheckerId() { return checkerId; }
    public void setCheckerId(String checkerId) { this.checkerId = checkerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
