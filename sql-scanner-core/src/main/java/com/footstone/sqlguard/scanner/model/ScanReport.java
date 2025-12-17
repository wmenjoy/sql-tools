package com.footstone.sqlguard.scanner.model;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.ViolationInfo;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates results from static SQL scanning across multiple sources.
 *
 * <p>ScanReport collects SqlEntry instances from XML, annotation, and wrapper
 * scanning, then calculates comprehensive statistics about SQL usage patterns.</p>
 *
 * <p><strong>Mutability:</strong> This class is mutable to allow incremental
 * addition of entries during the scanning process.</p>
 *
 * @see SqlEntry
 * @see WrapperUsage
 */
public class ScanReport {
  private final List<SqlEntry> entries;
  private final List<WrapperUsage> wrapperUsages;
  private final Map<String, Integer> statistics;
  private final Map<RiskLevel, Integer> violationsByRisk;
  private int totalViolations;

  /**
   * Constructs a new empty ScanReport.
   */
  public ScanReport() {
    this.entries = new ArrayList<>();
    this.wrapperUsages = new ArrayList<>();
    this.statistics = new LinkedHashMap<>();
    this.violationsByRisk = new EnumMap<>(RiskLevel.class);
    this.totalViolations = 0;
  }

  /**
   * Adds a SqlEntry to this report.
   *
   * @param entry the SQL entry to add
   */
  public void addEntry(SqlEntry entry) {
    if (entry != null) {
      entries.add(entry);
    }
  }

  /**
   * Adds a WrapperUsage to this report.
   *
   * @param usage the wrapper usage to add
   */
  public void addWrapperUsage(WrapperUsage usage) {
    if (usage != null) {
      wrapperUsages.add(usage);
    }
  }

  /**
   * Gets all SQL entries in this report.
   *
   * @return list of SQL entries
   */
  public List<SqlEntry> getEntries() {
    return entries;
  }

  /**
   * Gets all wrapper usages in this report.
   *
   * @return list of wrapper usages
   */
  public List<WrapperUsage> getWrapperUsages() {
    return wrapperUsages;
  }

  /**
   * Gets the statistics map for this report.
   *
   * <p>Statistics are calculated by calling {@link #calculateStatistics()}
   * and include counts for SQL types, sources, and violations.</p>
   *
   * @return map of statistic names to counts (insertion-ordered)
   */
  public Map<String, Integer> getStatistics() {
    return statistics;
  }

  /**
   * Gets the total number of violations across all entries.
   *
   * @return total violation count
   */
  public int getTotalViolations() {
    return totalViolations;
  }

  /**
   * Gets the count of violations for a specific risk level.
   *
   * @param riskLevel the risk level to query
   * @return count of violations at that risk level
   */
  public int getViolationCount(RiskLevel riskLevel) {
    return violationsByRisk.getOrDefault(riskLevel, 0);
  }

  /**
   * Checks if this report has any violations.
   *
   * @return true if violations exist, false otherwise
   */
  public boolean hasViolations() {
    return totalViolations > 0;
  }

  /**
   * Checks if this report has any CRITICAL violations.
   *
   * @return true if CRITICAL violations exist, false otherwise
   */
  public boolean hasCriticalViolations() {
    return getViolationCount(RiskLevel.CRITICAL) > 0;
  }

  /**
   * Calculates comprehensive statistics from collected entries.
   *
   * <p>Populates the statistics map with the following metrics:</p>
   * <ul>
   *   <li>totalSqlCount - total number of SQL entries</li>
   *   <li>dynamicSqlCount - number of dynamic SQL entries</li>
   *   <li>selectCount - number of SELECT statements</li>
   *   <li>insertCount - number of INSERT statements</li>
   *   <li>updateCount - number of UPDATE statements</li>
   *   <li>deleteCount - number of DELETE statements</li>
   *   <li>xmlSourceCount - SQL from XML mappers</li>
   *   <li>annotationSourceCount - SQL from annotations</li>
   *   <li>wrapperSourceCount - SQL from wrappers</li>
   *   <li>wrapperUsageCount - total wrapper usages detected</li>
   * </ul>
   *
   * <p>Also computes violation statistics by risk level.</p>
   */
  public void calculateStatistics() {
    statistics.clear();
    violationsByRisk.clear();
    totalViolations = 0;

    // Total SQL count
    statistics.put("totalSqlCount", entries.size());

    // Dynamic SQL count
    long dynamicCount = entries.stream()
        .filter(SqlEntry::isDynamic)
        .count();
    statistics.put("dynamicSqlCount", (int) dynamicCount);

    // Count by SQL command type
    long selectCount = entries.stream()
        .filter(e -> e.getSqlType() == com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
        .count();
    statistics.put("selectCount", (int) selectCount);

    long insertCount = entries.stream()
        .filter(e -> e.getSqlType() == com.footstone.sqlguard.core.model.SqlCommandType.INSERT)
        .count();
    statistics.put("insertCount", (int) insertCount);

    long updateCount = entries.stream()
        .filter(e -> e.getSqlType() == com.footstone.sqlguard.core.model.SqlCommandType.UPDATE)
        .count();
    statistics.put("updateCount", (int) updateCount);

    long deleteCount = entries.stream()
        .filter(e -> e.getSqlType() == com.footstone.sqlguard.core.model.SqlCommandType.DELETE)
        .count();
    statistics.put("deleteCount", (int) deleteCount);

    // Count by source type
    long xmlCount = entries.stream()
        .filter(e -> e.getSource() == SourceType.XML)
        .count();
    statistics.put("xmlSourceCount", (int) xmlCount);

    long annotationCount = entries.stream()
        .filter(e -> e.getSource() == SourceType.ANNOTATION)
        .count();
    statistics.put("annotationSourceCount", (int) annotationCount);

    long wrapperCount = entries.stream()
        .filter(e -> e.getSource() == SourceType.WRAPPER)
        .count();
    statistics.put("wrapperSourceCount", (int) wrapperCount);

    // Wrapper usage count
    statistics.put("wrapperUsageCount", wrapperUsages.size());

    // Compute violation statistics
    for (SqlEntry entry : entries) {
      for (ViolationInfo violation : entry.getViolations()) {
        RiskLevel risk = violation.getRiskLevel();
        violationsByRisk.merge(risk, 1, Integer::sum);
        totalViolations++;
      }
    }
  }

  /**
   * Returns a string representation of this report.
   *
   * @return summary string
   */
  @Override
  public String toString() {
    return "ScanReport{" +
        "entries=" + entries.size() +
        ", wrapperUsages=" + wrapperUsages.size() +
        ", statistics=" + statistics +
        '}';
  }
}



