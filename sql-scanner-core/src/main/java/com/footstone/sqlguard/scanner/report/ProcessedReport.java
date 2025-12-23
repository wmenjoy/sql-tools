package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Represents a processed scan report ready for rendering.
 *
 * <p>ProcessedReport contains violations grouped by risk level and
 * calculated statistics, optimized for console and HTML report generation.</p>
 *
 * <p><strong>Immutability:</strong> This class is immutable. All collections
 * are defensively copied during construction and returned as unmodifiable views.</p>
 */
public class ProcessedReport {
  private final Map<RiskLevel, List<ViolationEntry>> violationsByLevel;
  private final Map<String, Integer> statistics;

  /**
   * Constructs a new ProcessedReport.
   *
   * @param violationsByLevel map of violations grouped by risk level
   * @param statistics map of statistic names to counts
   */
  public ProcessedReport(Map<RiskLevel, List<ViolationEntry>> violationsByLevel,
                         Map<String, Integer> statistics) {
    this.violationsByLevel = Collections.unmodifiableMap(violationsByLevel);
    this.statistics = Collections.unmodifiableMap(statistics);
  }

  /**
   * Gets violations grouped by risk level.
   *
   * @return unmodifiable map of violations by risk level
   */
  public Map<RiskLevel, List<ViolationEntry>> getViolationsByLevel() {
    return violationsByLevel;
  }

  /**
   * Gets the statistics map.
   *
   * @return unmodifiable map of statistics
   */
  public Map<String, Integer> getStatistics() {
    return statistics;
  }

  /**
   * Gets the total number of violations across all risk levels.
   *
   * @return total violation count
   */
  public int getTotalViolations() {
    return violationsByLevel.values().stream()
        .mapToInt(List::size)
        .sum();
  }

  /**
   * Checks if this report has any violations.
   *
   * @return true if violations exist, false otherwise
   */
  public boolean hasViolations() {
    return getTotalViolations() > 0;
  }

  @Override
  public String toString() {
    return "ProcessedReport{" +
        "totalViolations=" + getTotalViolations() +
        ", statistics=" + statistics +
        '}';
  }
}

















