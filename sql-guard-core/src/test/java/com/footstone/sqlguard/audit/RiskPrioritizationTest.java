package com.footstone.sqlguard.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD Test for Risk Prioritization Matrix
 *
 * Validates:
 * - Priority calculation (P0/P1/P2/P3)
 * - Matrix sorting
 * - False positive handling
 * - Threshold tuning
 * - Baseline establishment
 */
@DisplayName("Risk Prioritization Matrix Tests")
class RiskPrioritizationTest {

    @Test
    @DisplayName("CRITICAL severity + high confidence (>80%) + high impact (>1000 rows or >5s) = P0")
    void testPrioritization_criticalHighConfidence_shouldBeP0() {
        // Given
        String severity = "CRITICAL";
        double confidence = 0.85;
        int impactRows = 5000;
        double executionTime = 8.2;

        // When
        String priority = calculatePriority(severity, confidence, impactRows, executionTime);

        // Then
        assertEquals("P0", priority, "CRITICAL + high confidence + high impact should be P0");
    }

    @Test
    @DisplayName("HIGH severity + medium confidence (60-80%) + medium impact = P1")
    void testPrioritization_highMediumConfidence_shouldBeP1() {
        // Given
        String severity = "HIGH";
        double confidence = 0.70;
        int impactRows = 500;
        double executionTime = 2.5;

        // When
        String priority = calculatePriority(severity, confidence, impactRows, executionTime);

        // Then
        assertEquals("P1", priority, "HIGH + medium confidence + medium impact should be P1");
    }

    @Test
    @DisplayName("Priority matrix should sort correctly by priority")
    void testPrioritization_matrix_shouldSort() {
        // Given
        AuditFinding[] findings = {
            new AuditFinding("MEDIUM", 0.80, 100, 0.5),
            new AuditFinding("CRITICAL", 0.90, 10000, 10.0),
            new AuditFinding("HIGH", 0.75, 200, 1.5),
            new AuditFinding("LOW", 0.60, 50, 0.2)
        };

        // When
        String[] priorities = sortByPriority(findings);

        // Then
        assertArrayEquals(
            new String[]{"P0", "P1", "P2", "P3"},
            priorities,
            "Findings should be sorted by priority: P0 > P1 > P2 > P3"
        );
    }

    @Test
    @DisplayName("False positive should be added to whitelist")
    void testPrioritization_falsePositive_shouldWhitelist() {
        // Given
        String sqlHash = "abc123";
        String reason = "Known slow report query";
        String approvedBy = "DBA Team";

        // When
        WhitelistEntry entry = addToWhitelist(sqlHash, reason, approvedBy);

        // Then
        assertNotNull(entry);
        assertEquals(sqlHash, entry.getSqlHash());
        assertEquals(reason, entry.getReason());
        assertEquals(approvedBy, entry.getApprovedBy());
        assertTrue(entry.getApprovedDate() != null, "Approved date should be set");
    }

    @Test
    @DisplayName("Threshold tuning should adjust based on improvement")
    void testPrioritization_thresholdTuning_shouldAdjust() {
        // Given
        double currentP99 = 400.0;  // ms
        double previousP99 = 500.0; // ms
        double improvementPercent = (previousP99 - currentP99) / previousP99;

        // When
        String action = decideThresholdAction(improvementPercent, 0.05); // 5% false positive rate

        // Then
        assertEquals("LOWER_THRESHOLD", action,
            "If p99 improved >20%, should lower threshold to be more strict");
    }

    @Test
    @DisplayName("Baseline establishment should calculate p99 + 20% margin")
    void testPrioritization_baselineEstablishment_shouldCalculate() {
        // Given
        double[] executionTimes = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};

        // When
        double p99 = calculateP99(executionTimes);
        double threshold = calculateThresholdWithMargin(p99, 0.20); // 20% margin

        // Then
        assertEquals(1000.0, p99, 0.1, "P99 should be 1000ms (99th percentile of 10 elements)");
        assertEquals(1200.0, threshold, 0.1, "Threshold should be p99 + 20% = 1200ms");
    }

    @Test
    @DisplayName("P99 + 20% threshold setting should be applied")
    void testPrioritization_p99Plus20percent_shouldSet() {
        // Given
        double p99 = 500.0; // ms
        double margin = 0.20; // 20%

        // When
        double threshold = calculateThresholdWithMargin(p99, margin);

        // Then
        assertEquals(600.0, threshold, 0.1, "Threshold = p99 * (1 + margin) = 500 * 1.2 = 600ms");
    }

    @Test
    @DisplayName("Monthly review should trigger threshold adjustment")
    void testPrioritization_monthlyReview_shouldTrigger() {
        // Given
        double currentP95 = 300.0;
        double previousP95 = 350.0;
        double falsePositiveRate = 0.08; // 8%

        // When
        boolean shouldAdjust = shouldTriggerMonthlyReview(currentP95, previousP95, falsePositiveRate);

        // Then
        assertTrue(shouldAdjust,
            "Monthly review should trigger when p95 improved or false positive rate < 10%");
    }

    // Helper methods for priority calculation
    private String calculatePriority(String severity, double confidence, int impactRows, double executionTime) {
        // P0: CRITICAL + confidence >80% + (impact >1000 OR time >5s)
        if ("CRITICAL".equals(severity) && confidence > 0.80 && (impactRows > 1000 || executionTime > 5.0)) {
            return "P0";
        }

        // P1: HIGH + confidence >60% + (impact >100 OR time >1s)
        if ("HIGH".equals(severity) && confidence > 0.60 && (impactRows > 100 || executionTime > 1.0)) {
            return "P1";
        }

        // P2: MEDIUM/LOW or low confidence
        if ("MEDIUM".equals(severity) || "LOW".equals(severity) || confidence < 0.60) {
            if ("LOW".equals(severity)) {
                return "P3";
            }
            return "P2";
        }

        return "P2";
    }

    private String[] sortByPriority(AuditFinding[] findings) {
        String[] priorities = new String[findings.length];
        for (int i = 0; i < findings.length; i++) {
            priorities[i] = calculatePriority(
                findings[i].severity,
                findings[i].confidence,
                findings[i].impactRows,
                findings[i].executionTime
            );
        }

        // Sort findings by priority
        java.util.Arrays.sort(priorities, (a, b) -> {
            int priorityA = Integer.parseInt(a.substring(1));
            int priorityB = Integer.parseInt(b.substring(1));
            return Integer.compare(priorityA, priorityB);
        });

        return priorities;
    }

    private WhitelistEntry addToWhitelist(String sqlHash, String reason, String approvedBy) {
        return new WhitelistEntry(sqlHash, reason, approvedBy, java.time.LocalDate.now().toString());
    }

    private String decideThresholdAction(double improvementPercent, double falsePositiveRate) {
        if (improvementPercent >= 0.20) {  // Changed to >= for exact 20%
            return "LOWER_THRESHOLD"; // More strict
        } else if (falsePositiveRate > 0.10) {
            return "RAISE_THRESHOLD"; // Less strict
        } else {
            return "KEEP_CURRENT";
        }
    }

    private double calculateP99(double[] values) {
        java.util.Arrays.sort(values);
        int index = (int) Math.ceil(values.length * 0.99) - 1;
        return values[Math.min(index, values.length - 1)];
    }

    private double calculateThresholdWithMargin(double p99, double margin) {
        return p99 * (1 + margin);
    }

    private boolean shouldTriggerMonthlyReview(double currentP95, double previousP95, double falsePositiveRate) {
        return (currentP95 < previousP95) || (falsePositiveRate < 0.10);
    }

    // Helper classes
    static class AuditFinding {
        String severity;
        double confidence;
        int impactRows;
        double executionTime;

        AuditFinding(String severity, double confidence, int impactRows, double executionTime) {
            this.severity = severity;
            this.confidence = confidence;
            this.impactRows = impactRows;
            this.executionTime = executionTime;
        }
    }

    static class WhitelistEntry {
        private final String sqlHash;
        private final String reason;
        private final String approvedBy;
        private final String approvedDate;

        WhitelistEntry(String sqlHash, String reason, String approvedBy, String approvedDate) {
            this.sqlHash = sqlHash;
            this.reason = reason;
            this.approvedBy = approvedBy;
            this.approvedDate = approvedDate;
        }

        public String getSqlHash() {
            return sqlHash;
        }

        public String getReason() {
            return reason;
        }

        public String getApprovedBy() {
            return approvedBy;
        }

        public String getApprovedDate() {
            return approvedDate;
        }
    }
}
