package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.SqlEntry;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Processes ScanReport data structures for report generation.
 *
 * <p>ReportProcessor aggregates violations, groups them by risk level,
 * sorts by severity, and prepares formatted data structures optimized
 * for console and HTML rendering.</p>
 *
 * <p><strong>Processing Steps:</strong></p>
 * <ol>
 *   <li>Extract violations from SqlEntry list</li>
 *   <li>Group violations by RiskLevel</li>
 *   <li>Sort groups by severity (CRITICAL first)</li>
 *   <li>Sort violations within each group by file path then line number</li>
 *   <li>Truncate SQL snippets to 100 characters for console display</li>
 *   <li>Calculate comprehensive statistics</li>
 * </ol>
 */
public class ReportProcessor {

  private static final int MAX_SQL_SNIPPET_LENGTH = 100;

  /**
   * Processes a ScanReport into a ProcessedReport ready for rendering.
   *
   * @param report the scan report to process
   * @return processed report with grouped violations and statistics
   */
  public ProcessedReport process(ScanReport report) {
    if (report == null) {
      throw new IllegalArgumentException("report cannot be null");
    }

    // Extract violations from SqlEntry list and create ViolationEntry instances
    List<ViolationEntry> allViolations = extractViolations(report);

    // Group by RiskLevel
    Map<RiskLevel, List<ViolationEntry>> violationsByLevel = groupByRiskLevel(allViolations);

    // Sort violations within each risk level by file path then line number
    sortViolationsWithinGroups(violationsByLevel);

    // Calculate statistics
    Map<String, Integer> statistics = calculateStatistics(report, violationsByLevel);

    return new ProcessedReport(violationsByLevel, statistics);
  }

  /**
   * Extracts violations from SqlEntry list and creates ViolationEntry instances.
   *
   * @param report the scan report
   * @return list of violation entries
   */
  private List<ViolationEntry> extractViolations(ScanReport report) {
    List<ViolationEntry> violations = new ArrayList<>();

    for (SqlEntry entry : report.getEntries()) {
      if (entry.hasViolations()) {
        for (ViolationInfo violation : entry.getViolations()) {
          // Skip SAFE level violations
          if (violation.getRiskLevel() == RiskLevel.SAFE) {
            continue;
          }

          ViolationEntry violationEntry = new ViolationEntry(
              entry.getFilePath(),
              entry.getLineNumber(),
              entry.getMapperId(),
              truncateSql(entry.getRawSql()),
              violation.getRiskLevel(),
              violation.getMessage(),
              violation.getSuggestion(),
              entry.getXmlSnippet(),
              entry.getJavaMethodSignature()
          );
          violations.add(violationEntry);
        }
      }
    }

    return violations;
  }

  /**
   * Groups violations by risk level.
   *
   * @param violations list of all violations
   * @return map of violations grouped by risk level
   */
  private Map<RiskLevel, List<ViolationEntry>> groupByRiskLevel(List<ViolationEntry> violations) {
    return violations.stream()
        .collect(Collectors.groupingBy(
            ViolationEntry::getRiskLevel,
            LinkedHashMap::new,
            Collectors.toList()
        ));
  }

  /**
   * Sorts violations within each risk level group by file path then line number.
   *
   * @param violationsByLevel map of violations grouped by risk level
   */
  private void sortViolationsWithinGroups(Map<RiskLevel, List<ViolationEntry>> violationsByLevel) {
    for (List<ViolationEntry> violations : violationsByLevel.values()) {
      violations.sort(Comparator
          .comparing(ViolationEntry::getFilePath)
          .thenComparingInt(ViolationEntry::getLineNumber));
    }
  }

  /**
   * Calculates comprehensive statistics from the report.
   *
   * @param report the scan report
   * @param violationsByLevel grouped violations
   * @return map of statistic names to counts
   */
  private Map<String, Integer> calculateStatistics(ScanReport report,
                                                    Map<RiskLevel, List<ViolationEntry>> violationsByLevel) {
    Map<String, Integer> stats = new LinkedHashMap<>();

    // Copy statistics from ScanReport
    stats.putAll(report.getStatistics());

    // Calculate violation counts by level
    stats.put("criticalCount", violationsByLevel.getOrDefault(RiskLevel.CRITICAL, Collections.emptyList()).size());
    stats.put("highCount", violationsByLevel.getOrDefault(RiskLevel.HIGH, Collections.emptyList()).size());
    stats.put("mediumCount", violationsByLevel.getOrDefault(RiskLevel.MEDIUM, Collections.emptyList()).size());
    stats.put("lowCount", violationsByLevel.getOrDefault(RiskLevel.LOW, Collections.emptyList()).size());

    // Calculate total violations
    int totalViolations = violationsByLevel.values().stream()
        .mapToInt(List::size)
        .sum();
    stats.put("totalViolations", totalViolations);

    return stats;
  }

  /**
   * Truncates SQL snippet to maximum length for console display.
   *
   * @param sql the SQL string to truncate
   * @return truncated SQL with "..." suffix if needed
   */
  private String truncateSql(String sql) {
    if (sql == null) {
      return "";
    }

    String trimmed = sql.trim();
    if (trimmed.length() <= MAX_SQL_SNIPPET_LENGTH) {
      return trimmed;
    }

    return trimmed.substring(0, MAX_SQL_SNIPPET_LENGTH) + "...";
  }
}



