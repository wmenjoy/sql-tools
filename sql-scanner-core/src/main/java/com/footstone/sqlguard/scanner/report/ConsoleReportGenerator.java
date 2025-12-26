package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.WrapperUsage;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Generates console reports with ANSI-colored output for terminal display.
 *
 * <p>ConsoleReportGenerator produces formatted text output with color-coded
 * risk levels, violation details, and statistics summary.</p>
 *
 * <p><strong>Output Format:</strong></p>
 * <ul>
 *   <li>Header with statistics summary</li>
 *   <li>Violations grouped by risk level (CRITICAL first)</li>
 *   <li>Each violation shows: file:line, mapper ID, SQL snippet, message, suggestion</li>
 *   <li>Wrapper usages section</li>
 * </ul>
 *
 * <p><strong>ANSI Colors:</strong></p>
 * <ul>
 *   <li>CRITICAL: Red (31m)</li>
 *   <li>HIGH: Yellow (33m)</li>
 *   <li>MEDIUM: Blue (34m)</li>
 *   <li>LOW: Default color</li>
 * </ul>
 */
public class ConsoleReportGenerator {

  // ANSI color codes
  private static final String ANSI_RESET = "\033[0m";
  private static final String ANSI_RED = "\033[31m";
  private static final String ANSI_YELLOW = "\033[33m";
  private static final String ANSI_BLUE = "\033[34m";
  private static final String ANSI_BOLD = "\033[1m";

  private static final String SEPARATOR = "================================================================================";

  private final ReportProcessor processor;
  private final boolean useColors;

  /**
   * Constructs a new ConsoleReportGenerator with color support detection.
   */
  public ConsoleReportGenerator() {
    this.processor = new ReportProcessor();
    this.useColors = supportsAnsiColors();
  }

  /**
   * Prints a formatted report to console.
   *
   * @param report the scan report to print
   */
  public void printToConsole(ScanReport report) {
    if (report == null) {
      throw new IllegalArgumentException("report cannot be null");
    }

    ProcessedReport processed = processor.process(report);

    // Print header with statistics
    printHeader(processed.getStatistics());

    // Print violations grouped by risk level
    if (processed.hasViolations()) {
      System.out.println();
      printViolationsByLevel(processed);
    } else {
      System.out.println();
      System.out.println("✓ No violations found - all SQL statements are safe!");
      System.out.println();
    }

    // Print wrapper usages summary
    if (!report.getWrapperUsages().isEmpty()) {
      printWrapperUsages(report.getWrapperUsages());
    }

    System.out.println(SEPARATOR);
  }

  /**
   * Prints the report header with statistics.
   *
   * @param statistics the statistics map
   */
  private void printHeader(Map<String, Integer> statistics) {
    System.out.println(SEPARATOR);
    System.out.println(bold("SQL Safety Scan Report"));
    System.out.println(SEPARATOR);

    int totalSql = statistics.getOrDefault("totalSqlCount", 0);
    int totalViolations = statistics.getOrDefault("totalViolations", 0);
    int critical = statistics.getOrDefault("criticalCount", 0);
    int high = statistics.getOrDefault("highCount", 0);
    int medium = statistics.getOrDefault("mediumCount", 0);
    int wrapperCount = statistics.getOrDefault("wrapperUsageCount", 0);

    StringBuilder summary = new StringBuilder();
    summary.append("Total SQL: ").append(totalSql);
    summary.append(" | Violations: ").append(totalViolations);

    if (totalViolations > 0) {
      summary.append(" (");
      if (critical > 0) {
        summary.append("CRITICAL: ").append(critical);
      }
      if (high > 0) {
        if (critical > 0) summary.append(", ");
        summary.append("HIGH: ").append(high);
      }
      if (medium > 0) {
        if (critical > 0 || high > 0) summary.append(", ");
        summary.append("MEDIUM: ").append(medium);
      }
      summary.append(")");
    }

    if (wrapperCount > 0) {
      summary.append(" | Wrapper Usages: ").append(wrapperCount);
    }

    System.out.println(summary.toString());
    System.out.println(SEPARATOR);
  }

  /**
   * Prints violations grouped by risk level.
   *
   * @param processed the processed report
   */
  private void printViolationsByLevel(ProcessedReport processed) {
    Map<RiskLevel, List<ViolationEntry>> violationsByLevel = processed.getViolationsByLevel();

    // Print in severity order: CRITICAL, HIGH, MEDIUM, LOW
    for (RiskLevel level : Arrays.asList(RiskLevel.CRITICAL, RiskLevel.HIGH, 
                                         RiskLevel.MEDIUM, RiskLevel.LOW)) {
      List<ViolationEntry> violations = violationsByLevel.get(level);
      if (violations != null && !violations.isEmpty()) {
        printRiskLevelSection(level, violations);
        System.out.println();
      }
    }
  }

  /**
   * Prints a risk level section with all violations.
   *
   * @param level the risk level
   * @param violations list of violations at this level
   */
  private void printRiskLevelSection(RiskLevel level, List<ViolationEntry> violations) {
    String coloredLevel = colorizeRiskLevel(level);
    System.out.println(coloredLevel + " " + violations.size() + 
                       (violations.size() == 1 ? " violation" : " violations"));
    System.out.println();

    for (ViolationEntry violation : violations) {
      printViolationEntry(violation);
    }
  }

  /**
   * Prints a single violation entry.
   *
   * @param entry the violation entry
   */
  private void printViolationEntry(ViolationEntry entry) {
    // File:line and mapper ID
    System.out.println("  [" + entry.getFilePath() + ":" + entry.getLineNumber() + "] " +
                       entry.getStatementId());

    // Java method signature (if available)
    if (entry.getJavaMethodSignature() != null && !entry.getJavaMethodSignature().trim().isEmpty()) {
      System.out.println("  Java: " + entry.getJavaMethodSignature());
    }

    // XML snippet (if available)
    if (entry.getXmlSnippet() != null && !entry.getXmlSnippet().trim().isEmpty()) {
      System.out.println("  XML:");
      String[] xmlLines = entry.getXmlSnippet().split("\n");
      for (String line : xmlLines) {
        if (line.trim().isEmpty()) continue;
        System.out.println("    " + line);
      }
    } else {
      // Fallback to SQL snippet
      System.out.println("  SQL: " + entry.getSqlSnippet());
    }

    // Risk Level
    System.out.println("  Level: " + colorizeRiskLevel(entry.getRiskLevel()));

    // Message
    System.out.println("  Message: " + entry.getMessage());

    // Suggestion (if present)
    if (entry.getSuggestion() != null && !entry.getSuggestion().trim().isEmpty()) {
      System.out.println("  Suggestion: " + entry.getSuggestion());
    }

    System.out.println();
  }

  /**
   * Prints wrapper usages section.
   *
   * @param wrapperUsages list of wrapper usages
   */
  private void printWrapperUsages(List<WrapperUsage> wrapperUsages) {
    System.out.println("[WRAPPER USAGES] " + wrapperUsages.size() + 
                       " location" + (wrapperUsages.size() == 1 ? "" : "s") +
                       " require runtime validation");
    System.out.println();

    for (WrapperUsage usage : wrapperUsages) {
      System.out.println("  [" + usage.getFilePath() + ":" + usage.getLineNumber() + "] " +
                         usage.getMethodName() + " - " + usage.getWrapperType());
    }

    System.out.println();
  }

  /**
   * Colorizes a risk level label with ANSI codes.
   *
   * @param level the risk level
   * @return colorized label
   */
  private String colorizeRiskLevel(RiskLevel level) {
    if (!useColors) {
      return "[" + level.name() + "]";
    }

    String color;
    switch (level) {
      case CRITICAL:
        color = ANSI_RED;
        break;
      case HIGH:
        color = ANSI_YELLOW;
        break;
      case MEDIUM:
        color = ANSI_BLUE;
        break;
      default:
        color = ANSI_RESET;
    }

    return color + ANSI_BOLD + "[" + level.name() + "]" + ANSI_RESET;
  }

  /**
   * Applies bold formatting to text.
   *
   * @param text the text to format
   * @return bold text
   */
  private String bold(String text) {
    if (!useColors) {
      return text;
    }
    return ANSI_BOLD + text + ANSI_RESET;
  }

  /**
   * Checks if the terminal supports ANSI colors.
   *
   * @return true if colors are supported
   */
  private boolean supportsAnsiColors() {
    // Check common environment variables
    String term = System.getenv("TERM");
    String colorTerm = System.getenv("COLORTERM");

    // If running in a known terminal emulator
    if (colorTerm != null) {
      return true;
    }

    // Check TERM variable
    if (term != null && (term.contains("color") || term.contains("xterm") || 
                         term.contains("screen") || term.equals("ansi"))) {
      return true;
    }

    // Check if running in IntelliJ IDEA or other IDEs
    String ideaInitialDirectory = System.getenv("IDEA_INITIAL_DIRECTORY");
    if (ideaInitialDirectory != null) {
      return true;
    }

    // Default to true for modern systems
    return true;
  }
}

