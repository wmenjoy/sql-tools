package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.WrapperUsage;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Generates HTML reports with styled web pages and sortable tables.
 *
 * <p>HtmlReportGenerator produces modern, responsive HTML reports with:
 * <ul>
 *   <li>Statistics dashboard with visual indicators</li>
 *   <li>Sortable violation table</li>
 *   <li>Collapsible SQL preview sections</li>
 *   <li>Color-coded risk levels</li>
 *   <li>XSS-safe HTML escaping</li>
 * </ul>
 */
public class HtmlReportGenerator {

  private final ReportProcessor processor;

  /**
   * Constructs a new HtmlReportGenerator.
   */
  public HtmlReportGenerator() {
    this.processor = new ReportProcessor();
  }

  /**
   * Writes an HTML report to a file.
   *
   * @param report the scan report
   * @param outputPath the output file path
   * @throws IOException if file writing fails
   */
  public void writeToFile(ScanReport report, Path outputPath) throws IOException {
    if (report == null) {
      throw new IllegalArgumentException("report cannot be null");
    }
    if (outputPath == null) {
      throw new IllegalArgumentException("outputPath cannot be null");
    }

    ProcessedReport processed = processor.process(report);

    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html>\n<html>\n");

    // Append <head> with CSS
    appendHead(html);

    // Append <body> with dashboard and table
    html.append("<body>\n");
    appendDashboard(html, processed.getStatistics());

    if (processed.hasViolations()) {
      appendViolationsTable(html, processed.getViolationsByLevel());
    } else {
      appendNoViolationsMessage(html);
    }

    if (!report.getWrapperUsages().isEmpty()) {
      appendWrapperSection(html, report.getWrapperUsages());
    }

    appendJavaScript(html);
    html.append("</body>\n</html>");

    // Write to file (Java 8 compatible)
    Files.write(outputPath, html.toString().getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Appends HTML head with CSS styles.
   */
  private void appendHead(StringBuilder html) {
    html.append("<head>\n");
    html.append("    <meta charset=\"UTF-8\">\n");
    html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
    html.append("    <title>SQL Safety Scan Report</title>\n");
    html.append("    <style>\n");
    html.append(getCss());
    html.append("    </style>\n");
    html.append("</head>\n");
  }

  /**
   * Returns CSS styles for the report.
   */
  private String getCss() {
    return "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
        "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; " +
        "               background: #f5f5f5; padding: 20px; color: #333; }\n" +
        "        .container { max-width: 1400px; margin: 0 auto; background: white; " +
        "                     border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }\n" +
        "        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); " +
        "                  color: white; padding: 30px; border-radius: 8px 8px 0 0; }\n" +
        "        .header h1 { font-size: 28px; margin-bottom: 10px; }\n" +
        "        .dashboard { display: flex; gap: 15px; padding: 20px; flex-wrap: wrap; }\n" +
        "        .stat-box { flex: 1; min-width: 150px; padding: 20px; border-radius: 6px; " +
        "                    background: #f8f9fa; border-left: 4px solid #6c757d; }\n" +
        "        .stat-box.critical { background: #fff5f5; border-color: #dc3545; }\n" +
        "        .stat-box.high { background: #fffbf0; border-color: #ffc107; }\n" +
        "        .stat-box.medium { background: #f0f8ff; border-color: #17a2b8; }\n" +
        "        .stat-box .label { font-size: 12px; color: #666; text-transform: uppercase; " +
        "                           letter-spacing: 0.5px; margin-bottom: 5px; }\n" +
        "        .stat-box .value { font-size: 32px; font-weight: bold; color: #333; }\n" +
        "        .content { padding: 20px; }\n" +
        "        table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n" +
        "        thead { background: #f8f9fa; }\n" +
        "        th { padding: 12px; text-align: left; font-weight: 600; color: #495057; " +
        "             border-bottom: 2px solid #dee2e6; cursor: pointer; user-select: none; }\n" +
        "        th:hover { background: #e9ecef; }\n" +
        "        td { padding: 12px; border-bottom: 1px solid #dee2e6; }\n" +
        "        tr.critical { background: #fff5f5; }\n" +
        "        tr.high { background: #fffbf0; }\n" +
        "        tr.medium { background: #f0f8ff; }\n" +
        "        tr.low { background: #f8f9fa; }\n" +
        "        .risk-badge { display: inline-block; padding: 4px 8px; border-radius: 4px; " +
        "                      font-size: 11px; font-weight: bold; text-transform: uppercase; }\n" +
        "        .risk-badge.critical { background: #dc3545; color: white; }\n" +
        "        .risk-badge.high { background: #ffc107; color: #333; }\n" +
        "        .risk-badge.medium { background: #17a2b8; color: white; }\n" +
        "        .risk-badge.low { background: #6c757d; color: white; }\n" +
        "        details { cursor: pointer; }\n" +
        "        summary { font-weight: 500; color: #667eea; }\n" +
        "        summary:hover { text-decoration: underline; }\n" +
        "        code { background: #f8f9fa; padding: 2px 6px; border-radius: 3px; " +
        "               font-family: 'Courier New', monospace; font-size: 13px; }\n" +
        "        .file-path { font-family: 'Courier New', monospace; font-size: 12px; color: #666; }\n" +
        "        .no-violations { text-align: center; padding: 60px 20px; color: #28a745; }\n" +
        "        .no-violations h2 { font-size: 48px; margin-bottom: 10px; }\n" +
        "        .wrapper-section { margin-top: 30px; padding: 20px; background: #fffbf0; " +
        "                           border-radius: 6px; border-left: 4px solid #ffc107; }\n" +
        "        .wrapper-section h3 { margin-bottom: 15px; color: #333; }\n" +
        "        .wrapper-item { padding: 10px; background: white; margin-bottom: 10px; " +
        "                        border-radius: 4px; font-family: 'Courier New', monospace; " +
        "                        font-size: 13px; }\n";
  }

  /**
   * Appends statistics dashboard.
   */
  private void appendDashboard(StringBuilder html, Map<String, Integer> statistics) {
    int totalSql = statistics.getOrDefault("totalSqlCount", 0);
    int totalViolations = statistics.getOrDefault("totalViolations", 0);
    int critical = statistics.getOrDefault("criticalCount", 0);
    int high = statistics.getOrDefault("highCount", 0);
    int medium = statistics.getOrDefault("mediumCount", 0);
    int wrapperCount = statistics.getOrDefault("wrapperUsageCount", 0);

    html.append("<div class=\"container\">\n");
    html.append("    <div class=\"header\">\n");
    html.append("        <h1>SQL Safety Scan Report</h1>\n");
    html.append("        <p>Comprehensive static analysis results</p>\n");
    html.append("    </div>\n");

    html.append("    <div class=\"dashboard\">\n");

    // Total SQL
    html.append("        <div class=\"stat-box\">\n");
    html.append("            <div class=\"label\">Total SQL</div>\n");
    html.append("            <div class=\"value\">").append(totalSql).append("</div>\n");
    html.append("        </div>\n");

    // Critical
    if (critical > 0) {
      html.append("        <div class=\"stat-box critical\">\n");
      html.append("            <div class=\"label\">CRITICAL</div>\n");
      html.append("            <div class=\"value\">").append(critical).append("</div>\n");
      html.append("        </div>\n");
    }

    // High
    if (high > 0) {
      html.append("        <div class=\"stat-box high\">\n");
      html.append("            <div class=\"label\">HIGH</div>\n");
      html.append("            <div class=\"value\">").append(high).append("</div>\n");
      html.append("        </div>\n");
    }

    // Medium
    if (medium > 0) {
      html.append("        <div class=\"stat-box medium\">\n");
      html.append("            <div class=\"label\">MEDIUM</div>\n");
      html.append("            <div class=\"value\">").append(medium).append("</div>\n");
      html.append("        </div>\n");
    }

    // Total Violations
    html.append("        <div class=\"stat-box\">\n");
    html.append("            <div class=\"label\">Total Violations</div>\n");
    html.append("            <div class=\"value\">").append(totalViolations).append("</div>\n");
    html.append("        </div>\n");

    // Wrappers
    if (wrapperCount > 0) {
      html.append("        <div class=\"stat-box\">\n");
      html.append("            <div class=\"label\">Wrappers</div>\n");
      html.append("            <div class=\"value\">").append(wrapperCount).append("</div>\n");
      html.append("        </div>\n");
    }

    html.append("    </div>\n");
    html.append("    <div class=\"content\">\n");
  }

  /**
   * Appends violations table.
   */
  private void appendViolationsTable(StringBuilder html, Map<RiskLevel, List<ViolationEntry>> violationsByLevel) {
    html.append("        <table id=\"violations-table\">\n");
    html.append("            <thead>\n");
    html.append("                <tr>\n");
    html.append("                    <th onclick=\"sortTable(0)\">Risk Level</th>\n");
    html.append("                    <th onclick=\"sortTable(1)\">File:Line</th>\n");
    html.append("                    <th onclick=\"sortTable(2)\">Mapper ID</th>\n");
    html.append("                    <th onclick=\"sortTable(3)\">Message</th>\n");
    html.append("                    <th>SQL Preview</th>\n");
    html.append("                    <th>Suggestion</th>\n");
    html.append("                </tr>\n");
    html.append("            </thead>\n");
    html.append("            <tbody>\n");

    // Add rows for each risk level
    for (RiskLevel level : Arrays.asList(RiskLevel.CRITICAL, RiskLevel.HIGH, 
                                         RiskLevel.MEDIUM, RiskLevel.LOW)) {
      List<ViolationEntry> violations = violationsByLevel.get(level);
      if (violations != null && !violations.isEmpty()) {
        for (ViolationEntry entry : violations) {
          appendViolationRow(html, entry);
        }
      }
    }

    html.append("            </tbody>\n");
    html.append("        </table>\n");
  }

  /**
   * Appends a single violation row.
   */
  private void appendViolationRow(StringBuilder html, ViolationEntry entry) {
    String riskClass = entry.getRiskLevel().name().toLowerCase();

    html.append("                <tr class=\"").append(riskClass).append("\">\n");

    // Risk Level
    html.append("                    <td><span class=\"risk-badge ").append(riskClass).append("\">");
    html.append(entry.getRiskLevel().name()).append("</span></td>\n");

    // File:Line
    html.append("                    <td class=\"file-path\">");
    html.append(escapeHtml(entry.getFilePath())).append(":").append(entry.getLineNumber());
    html.append("</td>\n");

    // Mapper ID
    html.append("                    <td>").append(escapeHtml(entry.getMapperId())).append("</td>\n");

    // Message
    html.append("                    <td>").append(escapeHtml(entry.getMessage())).append("</td>\n");

    // SQL Preview (collapsible)
    html.append("                    <td><details><summary>View SQL</summary>");
    html.append("<code>").append(escapeHtml(entry.getSqlSnippet())).append("</code>");
    html.append("</details></td>\n");

    // Suggestion
    String suggestion = entry.getSuggestion();
    html.append("                    <td>");
    if (suggestion != null && !suggestion.trim().isEmpty()) {
      html.append(escapeHtml(suggestion));
    } else {
      html.append("-");
    }
    html.append("</td>\n");

    html.append("                </tr>\n");
  }

  /**
   * Appends no violations message.
   */
  private void appendNoViolationsMessage(StringBuilder html) {
    html.append("        <div class=\"no-violations\">\n");
    html.append("            <h2>✓</h2>\n");
    html.append("            <h3>No violations found</h3>\n");
    html.append("            <p>All SQL statements passed safety checks</p>\n");
    html.append("        </div>\n");
  }

  /**
   * Appends wrapper usages section.
   */
  private void appendWrapperSection(StringBuilder html, List<WrapperUsage> wrapperUsages) {
    html.append("        <div class=\"wrapper-section\">\n");
    html.append("            <h3>Wrapper Usages (").append(wrapperUsages.size());
    html.append(" location").append(wrapperUsages.size() == 1 ? "" : "s").append(")</h3>\n");
    html.append("            <p>These locations use dynamic query builders and require runtime validation:</p>\n");

    for (WrapperUsage usage : wrapperUsages) {
      html.append("            <div class=\"wrapper-item\">");
      html.append(escapeHtml(usage.getFilePath())).append(":").append(usage.getLineNumber());
      html.append(" - ").append(escapeHtml(usage.getMethodName()));
      html.append(" (").append(escapeHtml(usage.getWrapperType())).append(")");
      html.append("</div>\n");
    }

    html.append("        </div>\n");
  }

  /**
   * Appends JavaScript for table sorting.
   */
  private void appendJavaScript(StringBuilder html) {
    html.append("    </div>\n"); // Close content div
    html.append("</div>\n"); // Close container div

    html.append("<script>\n");
    html.append("function sortTable(columnIndex) {\n");
    html.append("    var table = document.getElementById('violations-table');\n");
    html.append("    if (!table) return;\n");
    html.append("    var tbody = table.getElementsByTagName('tbody')[0];\n");
    html.append("    var rows = Array.from(tbody.getElementsByTagName('tr'));\n");
    html.append("    var ascending = table.getAttribute('data-sort-col') != columnIndex || " +
        "table.getAttribute('data-sort-dir') == 'desc';\n");
    html.append("    rows.sort(function(a, b) {\n");
    html.append("        var aVal = a.getElementsByTagName('td')[columnIndex].textContent.trim();\n");
    html.append("        var bVal = b.getElementsByTagName('td')[columnIndex].textContent.trim();\n");
    html.append("        if (aVal < bVal) return ascending ? -1 : 1;\n");
    html.append("        if (aVal > bVal) return ascending ? 1 : -1;\n");
    html.append("        return 0;\n");
    html.append("    });\n");
    html.append("    rows.forEach(function(row) { tbody.appendChild(row); });\n");
    html.append("    table.setAttribute('data-sort-col', columnIndex);\n");
    html.append("    table.setAttribute('data-sort-dir', ascending ? 'asc' : 'desc');\n");
    html.append("}\n");
    html.append("</script>\n");
  }

  /**
   * Escapes HTML special characters to prevent XSS.
   *
   * @param text the text to escape
   * @return escaped HTML
   */
  private String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return StringEscapeUtils.escapeHtml4(text);
  }
}

