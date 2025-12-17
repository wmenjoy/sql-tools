package com.footstone.sqlguard.scanner.report;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.WrapperUsage;
import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Enhanced HTML report generator with advanced features:
 * - Error type classification
 * - XML/Java content display
 * - Search and filter functionality
 * - Multi-dimensional grouping (by file, severity, error type)
 * - Interactive UI with tabs and collapsible sections
 */
public class EnhancedHtmlReportGenerator {

    private final ReportProcessor processor;

    public EnhancedHtmlReportGenerator() {
        this.processor = new ReportProcessor();
    }

    /**
     * Writes an enhanced HTML report to a file.
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
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n");

        appendHead(html);

        html.append("<body>\n");
        appendDashboard(html, processed.getStatistics(), processed);
        
        if (processed.hasViolations()) {
            appendControlPanel(html);
            appendViolationsContent(html, processed.getViolationsByLevel());
        } else {
            appendNoViolationsMessage(html);
        }

        if (!report.getWrapperUsages().isEmpty()) {
            appendWrapperSection(html, report.getWrapperUsages());
        }

        appendJavaScript(html, processed);
        html.append("</body>\n</html>");

        Files.write(outputPath, html.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void appendHead(StringBuilder html) {
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>SQL 安全扫描报告 - 增强版</title>\n");
        html.append("    <style>\n");
        html.append(getEnhancedCss());
        html.append("    </style>\n");
        html.append("</head>\n");
    }

    private String getEnhancedCss() {
        return "        * { margin: 0; padding: 0; box-sizing: border-box; }\n" +
            "        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Microsoft YaHei', sans-serif; " +
            "               background: #f5f5f5; padding: 20px; color: #333; }\n" +
            "        .container { max-width: 1600px; margin: 0 auto; background: white; " +
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
            "        \n" +
            "        /* Control Panel */\n" +
            "        .control-panel { padding: 20px; background: #f8f9fa; border-bottom: 1px solid #dee2e6; }\n" +
            "        .search-box { display: flex; gap: 10px; margin-bottom: 15px; flex-wrap: wrap; }\n" +
            "        .search-box input { flex: 1; min-width: 200px; padding: 10px; border: 1px solid #ced4da; " +
            "                            border-radius: 4px; font-size: 14px; }\n" +
            "        .search-box button { padding: 10px 20px; background: #667eea; color: white; " +
            "                             border: none; border-radius: 4px; cursor: pointer; font-weight: 500; }\n" +
            "        .search-box button:hover { background: #5568d3; }\n" +
            "        .filter-group { display: flex; gap: 15px; flex-wrap: wrap; }\n" +
            "        .filter-group label { display: flex; align-items: center; gap: 5px; cursor: pointer; }\n" +
            "        .filter-group input[type=\"checkbox\"] { cursor: pointer; }\n" +
            "        \n" +
            "        /* Tabs */\n" +
            "        .tabs { display: flex; gap: 10px; padding: 0 20px; background: white; " +
            "                border-bottom: 2px solid #dee2e6; }\n" +
            "        .tab { padding: 15px 25px; cursor: pointer; border-bottom: 3px solid transparent; " +
            "               font-weight: 500; color: #666; transition: all 0.3s; }\n" +
            "        .tab:hover { color: #667eea; }\n" +
            "        .tab.active { color: #667eea; border-bottom-color: #667eea; }\n" +
            "        .tab-content { display: none; padding: 20px; }\n" +
            "        .tab-content.active { display: block; }\n" +
            "        \n" +
            "        /* Violation Card */\n" +
            "        .violation-card { background: white; border: 1px solid #dee2e6; border-radius: 6px; " +
            "                          margin-bottom: 15px; padding: 20px; transition: box-shadow 0.3s; }\n" +
            "        .violation-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.1); }\n" +
            "        .violation-card.critical { border-left: 4px solid #dc3545; }\n" +
            "        .violation-card.high { border-left: 4px solid #ffc107; }\n" +
            "        .violation-card.medium { border-left: 4px solid #17a2b8; }\n" +
            "        .violation-card.low { border-left: 4px solid #6c757d; }\n" +
            "        \n" +
            "        .violation-header { display: flex; justify-content: space-between; align-items: start; " +
            "                            margin-bottom: 15px; }\n" +
            "        .violation-title { flex: 1; }\n" +
            "        .violation-title h3 { font-size: 16px; margin-bottom: 5px; color: #333; }\n" +
            "        .violation-meta { font-size: 13px; color: #666; }\n" +
            "        .violation-badges { display: flex; gap: 8px; flex-wrap: wrap; }\n" +
            "        \n" +
            "        .risk-badge { display: inline-block; padding: 4px 12px; border-radius: 4px; " +
            "                      font-size: 11px; font-weight: bold; text-transform: uppercase; }\n" +
            "        .risk-badge.critical { background: #dc3545; color: white; }\n" +
            "        .risk-badge.high { background: #ffc107; color: #333; }\n" +
            "        .risk-badge.medium { background: #17a2b8; color: white; }\n" +
            "        .risk-badge.low { background: #6c757d; color: white; }\n" +
            "        \n" +
            "        .type-badge { display: inline-block; padding: 4px 12px; border-radius: 4px; " +
            "                      font-size: 11px; font-weight: 500; background: #e9ecef; color: #495057; }\n" +
            "        \n" +
            "        .violation-message { padding: 15px; background: #f8f9fa; border-radius: 4px; " +
            "                             margin-bottom: 15px; line-height: 1.6; }\n" +
            "        .violation-suggestion { padding: 15px; background: #d1ecf1; border-radius: 4px; " +
            "                                border-left: 3px solid #17a2b8; line-height: 1.6; }\n" +
            "        \n" +
            "        /* Code Display */\n" +
            "        .code-section { margin-top: 15px; }\n" +
            "        .code-section h4 { font-size: 14px; margin-bottom: 10px; color: #495057; " +
            "                           display: flex; align-items: center; gap: 8px; }\n" +
            "        .code-block { background: #282c34; color: #abb2bf; padding: 15px; border-radius: 4px; " +
            "                      overflow-x: auto; font-family: 'Courier New', monospace; font-size: 13px; " +
            "                      line-height: 1.5; white-space: pre-wrap; word-wrap: break-word; }\n" +
            "        .code-block.xml { background: #f8f9fa; color: #333; border: 1px solid #dee2e6; }\n" +
            "        \n" +
            "        /* Group Headers */\n" +
            "        .group-header { background: #667eea; color: white; padding: 15px 20px; " +
            "                        border-radius: 6px; margin: 20px 0 15px 0; font-weight: 600; " +
            "                        display: flex; justify-content: space-between; align-items: center; " +
            "                        cursor: pointer; transition: all 0.3s; }\n" +
            "        .group-header:hover { background: #5568d3; transform: translateX(5px); }\n" +
            "        .group-header.active { background: #4c51bf; box-shadow: 0 4px 12px rgba(102,126,234,0.4); }\n" +
            "        .group-count { background: rgba(255,255,255,0.2); padding: 4px 12px; " +
            "                       border-radius: 12px; font-size: 14px; }\n" +
            "        \n" +
            "        /* Sidebar Filters */\n" +
            "        .sidebar { position: fixed; right: 20px; top: 100px; width: 280px; " +
            "                   background: white; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); " +
            "                   padding: 20px; max-height: calc(100vh - 140px); overflow-y: auto; z-index: 1000; }\n" +
            "        .sidebar h3 { font-size: 16px; margin-bottom: 15px; color: #333; " +
            "                      border-bottom: 2px solid #667eea; padding-bottom: 10px; }\n" +
            "        .filter-item { padding: 10px; margin-bottom: 8px; background: #f8f9fa; " +
            "                       border-radius: 4px; cursor: pointer; transition: all 0.2s; " +
            "                       display: flex; justify-content: space-between; align-items: center; }\n" +
            "        .filter-item:hover { background: #e9ecef; transform: translateX(3px); }\n" +
            "        .filter-item.active { background: #667eea; color: white; }\n" +
            "        .filter-item .count { font-size: 12px; background: rgba(0,0,0,0.1); " +
            "                              padding: 2px 8px; border-radius: 10px; }\n" +
            "        .filter-item.active .count { background: rgba(255,255,255,0.3); }\n" +
            "        .clear-filter { margin-top: 15px; padding: 10px; background: #6c757d; color: white; " +
            "                        border: none; border-radius: 4px; width: 100%; cursor: pointer; " +
            "                        font-weight: 500; }\n" +
            "        .clear-filter:hover { background: #5a6268; }\n" +
            "        \n" +
            "        /* Content with sidebar */\n" +
            "        .content-with-sidebar { margin-right: 320px; }\n" +
            "        \n" +
            "        /* Utilities */\n" +
            "        .file-path { font-family: 'Courier New', monospace; font-size: 12px; color: #666; }\n" +
            "        .no-violations { text-align: center; padding: 60px 20px; color: #28a745; }\n" +
            "        .no-violations h2 { font-size: 48px; margin-bottom: 10px; }\n" +
            "        .hidden { display: none !important; }\n" +
            "        .wrapper-section { margin-top: 30px; padding: 20px; background: #fffbf0; " +
            "                           border-radius: 6px; border-left: 4px solid #ffc107; }\n" +
            "        .wrapper-item { padding: 10px; background: white; margin-bottom: 10px; " +
            "                        border-radius: 4px; font-family: 'Courier New', monospace; font-size: 13px; }\n" +
            "        \n" +
            "        /* Responsive */\n" +
            "        @media (max-width: 768px) {\n" +
            "            .violation-header { flex-direction: column; }\n" +
            "            .violation-badges { margin-top: 10px; }\n" +
            "            .search-box { flex-direction: column; }\n" +
            "            .search-box input, .search-box button { width: 100%; }\n" +
            "        }\n";
    }

    private void appendDashboard(StringBuilder html, Map<String, Integer> statistics, ProcessedReport processed) {
        int totalSql = statistics.getOrDefault("totalSqlCount", 0);
        int totalViolations = statistics.getOrDefault("totalViolations", 0);
        int critical = statistics.getOrDefault("criticalCount", 0);
        int high = statistics.getOrDefault("highCount", 0);
        int medium = statistics.getOrDefault("mediumCount", 0);
        int wrapperCount = statistics.getOrDefault("wrapperUsageCount", 0);

        // Count unique error types
        Set<String> errorTypes = new HashSet<>();
        for (List<ViolationEntry> entries : processed.getViolationsByLevel().values()) {
            for (ViolationEntry entry : entries) {
                errorTypes.add(classifyViolationType(entry.getMessage()));
            }
        }

        html.append("<div class=\"container\">\n");
        html.append("    <div class=\"header\">\n");
        html.append("        <h1>SQL 安全扫描报告 - 增强版</h1>\n");
        html.append("        <p>全面的静态分析结果，支持搜索、过滤和分组</p>\n");
        html.append("    </div>\n");

        html.append("    <div class=\"dashboard\">\n");

        // Total SQL
        html.append("        <div class=\"stat-box\">\n");
        html.append("            <div class=\"label\">扫描 SQL 总数</div>\n");
        html.append("            <div class=\"value\">").append(totalSql).append("</div>\n");
        html.append("        </div>\n");

        // Critical
        if (critical > 0) {
            html.append("        <div class=\"stat-box critical\">\n");
            html.append("            <div class=\"label\">严重 (CRITICAL)</div>\n");
            html.append("            <div class=\"value\">").append(critical).append("</div>\n");
            html.append("        </div>\n");
        }

        // High
        if (high > 0) {
            html.append("        <div class=\"stat-box high\">\n");
            html.append("            <div class=\"label\">高危 (HIGH)</div>\n");
            html.append("            <div class=\"value\">").append(high).append("</div>\n");
            html.append("        </div>\n");
        }

        // Medium
        if (medium > 0) {
            html.append("        <div class=\"stat-box medium\">\n");
            html.append("            <div class=\"label\">中危 (MEDIUM)</div>\n");
            html.append("            <div class=\"value\">").append(medium).append("</div>\n");
            html.append("        </div>\n");
        }

        // Total Violations
        html.append("        <div class=\"stat-box\">\n");
        html.append("            <div class=\"label\">违规总数</div>\n");
        html.append("            <div class=\"value\">").append(totalViolations).append("</div>\n");
        html.append("        </div>\n");

        // Error Types
        html.append("        <div class=\"stat-box\">\n");
        html.append("            <div class=\"label\">错误类型数</div>\n");
        html.append("            <div class=\"value\">").append(errorTypes.size()).append("</div>\n");
        html.append("        </div>\n");

        // Wrappers
        if (wrapperCount > 0) {
            html.append("        <div class=\"stat-box\">\n");
            html.append("            <div class=\"label\">动态查询</div>\n");
            html.append("            <div class=\"value\">").append(wrapperCount).append("</div>\n");
            html.append("        </div>\n");
        }

        html.append("    </div>\n");
    }

    private void appendControlPanel(StringBuilder html) {
        html.append("    <div class=\"control-panel\">\n");
        html.append("        <div class=\"search-box\">\n");
        html.append("            <input type=\"text\" id=\"searchInput\" placeholder=\"搜索文件名、Mapper ID、错误信息...\" />\n");
        html.append("            <button onclick=\"performSearch()\">搜索</button>\n");
        html.append("            <button onclick=\"clearSearch()\" style=\"background: #6c757d;\">清除</button>\n");
        html.append("        </div>\n");
        html.append("        <div class=\"filter-group\">\n");
        html.append("            <label><input type=\"checkbox\" class=\"risk-filter\" value=\"CRITICAL\" checked> 严重</label>\n");
        html.append("            <label><input type=\"checkbox\" class=\"risk-filter\" value=\"HIGH\" checked> 高危</label>\n");
        html.append("            <label><input type=\"checkbox\" class=\"risk-filter\" value=\"MEDIUM\" checked> 中危</label>\n");
        html.append("            <label><input type=\"checkbox\" class=\"risk-filter\" value=\"LOW\" checked> 低危</label>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
    }

    private void appendViolationsContent(StringBuilder html, Map<RiskLevel, List<ViolationEntry>> violationsByLevel) {
        html.append("    <div class=\"tabs\">\n");
        html.append("        <div class=\"tab active\" onclick=\"switchTab('all')\">全部违规</div>\n");
        html.append("        <div class=\"tab\" onclick=\"switchTab('by-severity')\">按严重程度</div>\n");
        html.append("        <div class=\"tab\" onclick=\"switchTab('by-type')\">按错误类型</div>\n");
        html.append("        <div class=\"tab\" onclick=\"switchTab('by-file')\">按文件</div>\n");
        html.append("    </div>\n");

        // Add sidebar
        appendSidebar(html, violationsByLevel);

        html.append("    <div class=\"content content-with-sidebar\">\n");

        // Tab 1: All Violations
        html.append("        <div id=\"tab-all\" class=\"tab-content active\">\n");
        appendAllViolations(html, violationsByLevel);
        html.append("        </div>\n");

        // Tab 2: By Severity
        html.append("        <div id=\"tab-by-severity\" class=\"tab-content\">\n");
        appendViolationsBySeverity(html, violationsByLevel);
        html.append("        </div>\n");

        // Tab 3: By Type
        html.append("        <div id=\"tab-by-type\" class=\"tab-content\">\n");
        appendViolationsByType(html, violationsByLevel);
        html.append("        </div>\n");

        // Tab 4: By File
        html.append("        <div id=\"tab-by-file\" class=\"tab-content\">\n");
        appendViolationsByFile(html, violationsByLevel);
        html.append("        </div>\n");

        html.append("    </div>\n");
    }

    private void appendSidebar(StringBuilder html, Map<RiskLevel, List<ViolationEntry>> violationsByLevel) {
        html.append("    <div class=\"sidebar\" id=\"sidebar\">\n");
        
        // Error Type Filters
        html.append("        <h3>🏷️ 错误类型</h3>\n");
        html.append("        <div id=\"type-filters\">\n");
        
        // Collect all types with counts
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        for (List<ViolationEntry> entries : violationsByLevel.values()) {
            for (ViolationEntry entry : entries) {
                String type = classifyViolationType(entry.getMessage());
                typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
            }
        }
        
        // Sort by count (descending)
        List<Map.Entry<String, Integer>> sortedTypes = typeCounts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .collect(Collectors.toList());
        
        for (Map.Entry<String, Integer> entry : sortedTypes) {
            html.append("            <div class=\"filter-item\" onclick=\"filterByType('");
            html.append(escapeHtml(entry.getKey())).append("')\">\n");
            html.append("                <span>").append(escapeHtml(entry.getKey())).append("</span>\n");
            html.append("                <span class=\"count\">").append(entry.getValue()).append("</span>\n");
            html.append("            </div>\n");
        }
        
        html.append("        </div>\n");
        
        // File Filters
        html.append("        <h3 style=\"margin-top: 25px;\">📁 文件</h3>\n");
        html.append("        <div id=\"file-filters\">\n");
        
        // Collect all files with counts
        Map<String, Integer> fileCounts = new LinkedHashMap<>();
        for (List<ViolationEntry> entries : violationsByLevel.values()) {
            for (ViolationEntry entry : entries) {
                String fileName = extractFileName(entry.getFilePath());
                fileCounts.put(fileName, fileCounts.getOrDefault(fileName, 0) + 1);
            }
        }
        
        // Sort by count (descending)
        List<Map.Entry<String, Integer>> sortedFiles = fileCounts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .limit(20) // Show top 20 files
            .collect(Collectors.toList());
        
        for (Map.Entry<String, Integer> entry : sortedFiles) {
            html.append("            <div class=\"filter-item\" onclick=\"filterByFile('");
            html.append(escapeHtml(entry.getKey())).append("')\">\n");
            html.append("                <span>").append(escapeHtml(entry.getKey())).append("</span>\n");
            html.append("                <span class=\"count\">").append(entry.getValue()).append("</span>\n");
            html.append("            </div>\n");
        }
        
        html.append("        </div>\n");
        
        // Clear button
        html.append("        <button class=\"clear-filter\" onclick=\"clearAllFilters()\">清除所有过滤</button>\n");
        
        html.append("    </div>\n");
    }

    private void appendAllViolations(StringBuilder html, Map<RiskLevel, List<ViolationEntry>> violationsByLevel) {
        List<ViolationEntry> allViolations = new ArrayList<>();
        for (RiskLevel level : Arrays.asList(RiskLevel.CRITICAL, RiskLevel.HIGH, RiskLevel.MEDIUM, RiskLevel.LOW)) {
            List<ViolationEntry> violations = violationsByLevel.get(level);
            if (violations != null) {
                allViolations.addAll(violations);
            }
        }

        for (ViolationEntry entry : allViolations) {
            appendViolationCard(html, entry);
        }
    }

    private void appendViolationsBySeverity(StringBuilder html, Map<RiskLevel, List<ViolationEntry>> violationsByLevel) {
        for (RiskLevel level : Arrays.asList(RiskLevel.CRITICAL, RiskLevel.HIGH, RiskLevel.MEDIUM, RiskLevel.LOW)) {
            List<ViolationEntry> violations = violationsByLevel.get(level);
            if (violations != null && !violations.isEmpty()) {
                html.append("            <div class=\"group-header\" onclick=\"filterByRisk('").append(level.name()).append("')\">\n");
                html.append("                <span>").append(getRiskLevelName(level)).append(" (点击筛选)</span>\n");
                html.append("                <span class=\"group-count\">").append(violations.size()).append(" 个</span>\n");
                html.append("            </div>\n");

                for (ViolationEntry entry : violations) {
                    appendViolationCard(html, entry);
                }
            }
        }
    }

    private void appendViolationsByType(StringBuilder html, Map<RiskLevel, List<ViolationEntry>> violationsByLevel) {
        // Group by error type
        Map<String, List<ViolationEntry>> byType = new LinkedHashMap<>();
        for (List<ViolationEntry> entries : violationsByLevel.values()) {
            for (ViolationEntry entry : entries) {
                String type = classifyViolationType(entry.getMessage());
                byType.computeIfAbsent(type, k -> new ArrayList<>()).add(entry);
            }
        }

        // Sort by count (descending)
        List<Map.Entry<String, List<ViolationEntry>>> sorted = byType.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .collect(Collectors.toList());

        for (Map.Entry<String, List<ViolationEntry>> entry : sorted) {
            html.append("            <div class=\"group-header\" onclick=\"filterByType('").append(escapeHtml(entry.getKey())).append("')\">\n");
            html.append("                <span>").append(escapeHtml(entry.getKey())).append(" (点击筛选)</span>\n");
            html.append("                <span class=\"group-count\">").append(entry.getValue().size()).append(" 个</span>\n");
            html.append("            </div>\n");

            for (ViolationEntry violation : entry.getValue()) {
                appendViolationCard(html, violation);
            }
        }
    }

    private void appendViolationsByFile(StringBuilder html, Map<RiskLevel, List<ViolationEntry>> violationsByLevel) {
        // Group by file
        Map<String, List<ViolationEntry>> byFile = new LinkedHashMap<>();
        for (List<ViolationEntry> entries : violationsByLevel.values()) {
            for (ViolationEntry entry : entries) {
                String fileName = extractFileName(entry.getFilePath());
                byFile.computeIfAbsent(fileName, k -> new ArrayList<>()).add(entry);
            }
        }

        // Sort by count (descending)
        List<Map.Entry<String, List<ViolationEntry>>> sorted = byFile.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
            .collect(Collectors.toList());

        for (Map.Entry<String, List<ViolationEntry>> entry : sorted) {
            html.append("            <div class=\"group-header\" onclick=\"filterByFile('").append(escapeHtml(entry.getKey())).append("')\">\n");
            html.append("                <span>").append(escapeHtml(entry.getKey())).append(" (点击筛选)</span>\n");
            html.append("                <span class=\"group-count\">").append(entry.getValue().size()).append(" 个</span>\n");
            html.append("            </div>\n");

            for (ViolationEntry violation : entry.getValue()) {
                appendViolationCard(html, violation);
            }
        }
    }

    private void appendViolationCard(StringBuilder html, ViolationEntry entry) {
        String riskClass = entry.getRiskLevel().name().toLowerCase();
        String violationType = classifyViolationType(entry.getMessage());

        html.append("            <div class=\"violation-card ").append(riskClass).append("\" ");
        html.append("data-risk=\"").append(entry.getRiskLevel().name()).append("\" ");
        html.append("data-type=\"").append(escapeHtml(violationType)).append("\" ");
        html.append("data-file=\"").append(escapeHtml(entry.getFilePath())).append("\" ");
        html.append("data-mapper=\"").append(escapeHtml(entry.getMapperId())).append("\" ");
        html.append("data-message=\"").append(escapeHtml(entry.getMessage())).append("\">\n");

        // Header
        html.append("                <div class=\"violation-header\">\n");
        html.append("                    <div class=\"violation-title\">\n");
        html.append("                        <h3>").append(escapeHtml(entry.getMapperId())).append("</h3>\n");
        html.append("                        <div class=\"violation-meta file-path\">");
        html.append(escapeHtml(entry.getFilePath())).append(":").append(entry.getLineNumber());
        html.append("</div>\n");
        html.append("                    </div>\n");
        html.append("                    <div class=\"violation-badges\">\n");
        html.append("                        <span class=\"risk-badge ").append(riskClass).append("\">");
        html.append(getRiskLevelName(entry.getRiskLevel())).append("</span>\n");
        html.append("                        <span class=\"type-badge\">").append(escapeHtml(violationType)).append("</span>\n");
        html.append("                    </div>\n");
        html.append("                </div>\n");

        // Message
        html.append("                <div class=\"violation-message\">");
        html.append(escapeHtml(entry.getMessage()));
        html.append("</div>\n");

        // Java Method Signature
        if (entry.getJavaMethodSignature() != null && !entry.getJavaMethodSignature().trim().isEmpty()) {
            html.append("                <div class=\"code-section\">\n");
            html.append("                    <h4>📋 Java 方法签名</h4>\n");
            html.append("                    <div class=\"code-block xml\">");
            html.append(escapeHtml(entry.getJavaMethodSignature()));
            html.append("</div>\n");
            html.append("                </div>\n");
        }

        // XML Content
        if (entry.getXmlSnippet() != null && !entry.getXmlSnippet().trim().isEmpty()) {
            html.append("                <div class=\"code-section\">\n");
            html.append("                    <h4>📄 XML 语句</h4>\n");
            html.append("                    <div class=\"code-block xml\">");
            html.append(escapeHtml(entry.getXmlSnippet()));
            html.append("</div>\n");
            html.append("                </div>\n");
        } else {
            // Fallback to SQL snippet
            html.append("                <div class=\"code-section\">\n");
            html.append("                    <h4>📝 SQL 语句</h4>\n");
            html.append("                    <div class=\"code-block\">");
            html.append(escapeHtml(entry.getSqlSnippet()));
            html.append("</div>\n");
            html.append("                </div>\n");
        }

        // Suggestion
        if (entry.getSuggestion() != null && !entry.getSuggestion().trim().isEmpty()) {
            html.append("                <div class=\"violation-suggestion\">");
            html.append("<strong>💡 建议：</strong> ").append(escapeHtml(entry.getSuggestion()));
            html.append("</div>\n");
        }

        html.append("            </div>\n");
    }

    private void appendNoViolationsMessage(StringBuilder html) {
        html.append("    <div class=\"content\">\n");
        html.append("        <div class=\"no-violations\">\n");
        html.append("            <h2>✓</h2>\n");
        html.append("            <h3>未发现违规</h3>\n");
        html.append("            <p>所有 SQL 语句均通过安全检查</p>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
    }

    private void appendWrapperSection(StringBuilder html, List<WrapperUsage> wrapperUsages) {
        html.append("        <div class=\"wrapper-section\">\n");
        html.append("            <h3>动态查询构建器 (").append(wrapperUsages.size());
        html.append(" 处)</h3>\n");
        html.append("            <p>这些位置使用动态查询构建器，需要运行时验证：</p>\n");

        for (WrapperUsage usage : wrapperUsages) {
            html.append("            <div class=\"wrapper-item\">");
            html.append(escapeHtml(usage.getFilePath())).append(":").append(usage.getLineNumber());
            html.append(" - ").append(escapeHtml(usage.getMethodName()));
            html.append(" (").append(escapeHtml(usage.getWrapperType())).append(")");
            html.append("</div>\n");
        }

        html.append("        </div>\n");
    }

    private void appendJavaScript(StringBuilder html, ProcessedReport processed) {
        html.append("<script>\n");
        
        // Tab switching
        html.append("function switchTab(tabName) {\n");
        html.append("    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));\n");
        html.append("    document.querySelectorAll('.tab-content').forEach(t => t.classList.remove('active'));\n");
        html.append("    event.target.classList.add('active');\n");
        html.append("    document.getElementById('tab-' + tabName).classList.add('active');\n");
        html.append("}\n\n");

        // Search functionality
        html.append("function performSearch() {\n");
        html.append("    const searchTerm = document.getElementById('searchInput').value.toLowerCase();\n");
        html.append("    const cards = document.querySelectorAll('.violation-card');\n");
        html.append("    let visibleCount = 0;\n");
        html.append("    cards.forEach(card => {\n");
        html.append("        const file = card.dataset.file.toLowerCase();\n");
        html.append("        const mapper = card.dataset.mapper.toLowerCase();\n");
        html.append("        const message = card.dataset.message.toLowerCase();\n");
        html.append("        const matches = file.includes(searchTerm) || mapper.includes(searchTerm) || message.includes(searchTerm);\n");
        html.append("        if (matches && isRiskFilterActive(card.dataset.risk)) {\n");
        html.append("            card.classList.remove('hidden');\n");
        html.append("            visibleCount++;\n");
        html.append("        } else {\n");
        html.append("            card.classList.add('hidden');\n");
        html.append("        }\n");
        html.append("    });\n");
        html.append("    updateGroupHeaders();\n");
        html.append("}\n\n");

        html.append("function clearSearch() {\n");
        html.append("    document.getElementById('searchInput').value = '';\n");
        html.append("    performSearch();\n");
        html.append("}\n\n");

        // Risk filter
        html.append("function isRiskFilterActive(risk) {\n");
        html.append("    const checkbox = document.querySelector('.risk-filter[value=\"' + risk + '\"]');\n");
        html.append("    return checkbox ? checkbox.checked : true;\n");
        html.append("}\n\n");

        html.append("document.querySelectorAll('.risk-filter').forEach(filter => {\n");
        html.append("    filter.addEventListener('change', performSearch);\n");
        html.append("});\n\n");

        // Update group headers
        html.append("function updateGroupHeaders() {\n");
        html.append("    document.querySelectorAll('.group-header').forEach(header => {\n");
        html.append("        let nextElement = header.nextElementSibling;\n");
        html.append("        let visibleCount = 0;\n");
        html.append("        while (nextElement && nextElement.classList.contains('violation-card')) {\n");
        html.append("            if (!nextElement.classList.contains('hidden')) visibleCount++;\n");
        html.append("            nextElement = nextElement.nextElementSibling;\n");
        html.append("        }\n");
        html.append("        const countSpan = header.querySelector('.group-count');\n");
        html.append("        if (countSpan) countSpan.textContent = visibleCount + ' 个';\n");
        html.append("        header.style.display = visibleCount > 0 ? 'flex' : 'none';\n");
        html.append("    });\n");
        html.append("}\n\n");

        // Enter key for search
        html.append("document.getElementById('searchInput').addEventListener('keypress', function(e) {\n");
        html.append("    if (e.key === 'Enter') performSearch();\n");
        html.append("});\n\n");

        // Filter by type
        html.append("let activeTypeFilter = null;\n");
        html.append("let activeFileFilter = null;\n");
        html.append("let activeRiskFilter = null;\n\n");

        html.append("function filterByType(type) {\n");
        html.append("    activeTypeFilter = activeTypeFilter === type ? null : type;\n");
        html.append("    updateSidebarActive('type-filters', type);\n");
        html.append("    applyFilters();\n");
        html.append("}\n\n");

        html.append("function filterByFile(file) {\n");
        html.append("    activeFileFilter = activeFileFilter === file ? null : file;\n");
        html.append("    updateSidebarActive('file-filters', file);\n");
        html.append("    applyFilters();\n");
        html.append("}\n\n");

        html.append("function filterByRisk(risk) {\n");
        html.append("    activeRiskFilter = activeRiskFilter === risk ? null : risk;\n");
        html.append("    document.querySelectorAll('.group-header').forEach(h => h.classList.remove('active'));\n");
        html.append("    if (activeRiskFilter) {\n");
        html.append("        event.target.closest('.group-header').classList.add('active');\n");
        html.append("    }\n");
        html.append("    applyFilters();\n");
        html.append("}\n\n");

        html.append("function updateSidebarActive(containerId, value) {\n");
        html.append("    const container = document.getElementById(containerId);\n");
        html.append("    container.querySelectorAll('.filter-item').forEach(item => {\n");
        html.append("        item.classList.remove('active');\n");
        html.append("        if (item.textContent.includes(value) && value) {\n");
        html.append("            item.classList.add('active');\n");
        html.append("        }\n");
        html.append("    });\n");
        html.append("}\n\n");

        html.append("function applyFilters() {\n");
        html.append("    const cards = document.querySelectorAll('.violation-card');\n");
        html.append("    const searchTerm = document.getElementById('searchInput').value.toLowerCase();\n");
        html.append("    let visibleCount = 0;\n");
        html.append("    cards.forEach(card => {\n");
        html.append("        let visible = true;\n");
        html.append("        \n");
        html.append("        // Type filter\n");
        html.append("        if (activeTypeFilter && card.dataset.type !== activeTypeFilter) visible = false;\n");
        html.append("        \n");
        html.append("        // File filter\n");
        html.append("        if (activeFileFilter) {\n");
        html.append("            const fileName = card.dataset.file.split('/').pop().split('\\\\\\\\').pop();\n");
        html.append("            if (fileName !== activeFileFilter) visible = false;\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        // Risk filter\n");
        html.append("        if (activeRiskFilter && card.dataset.risk !== activeRiskFilter) visible = false;\n");
        html.append("        \n");
        html.append("        // Search filter\n");
        html.append("        if (searchTerm) {\n");
        html.append("            const file = card.dataset.file.toLowerCase();\n");
        html.append("            const mapper = card.dataset.mapper.toLowerCase();\n");
        html.append("            const message = card.dataset.message.toLowerCase();\n");
        html.append("            if (!file.includes(searchTerm) && !mapper.includes(searchTerm) && !message.includes(searchTerm)) {\n");
        html.append("                visible = false;\n");
        html.append("            }\n");
        html.append("        }\n");
        html.append("        \n");
        html.append("        // Risk level checkbox filter\n");
        html.append("        if (!isRiskFilterActive(card.dataset.risk)) visible = false;\n");
        html.append("        \n");
        html.append("        card.classList.toggle('hidden', !visible);\n");
        html.append("        if (visible) visibleCount++;\n");
        html.append("    });\n");
        html.append("    updateGroupHeaders();\n");
        html.append("    updateFilterStatus(visibleCount, cards.length);\n");
        html.append("}\n\n");

        html.append("function clearAllFilters() {\n");
        html.append("    activeTypeFilter = null;\n");
        html.append("    activeFileFilter = null;\n");
        html.append("    activeRiskFilter = null;\n");
        html.append("    document.getElementById('searchInput').value = '';\n");
        html.append("    document.querySelectorAll('.filter-item').forEach(item => item.classList.remove('active'));\n");
        html.append("    document.querySelectorAll('.group-header').forEach(h => h.classList.remove('active'));\n");
        html.append("    applyFilters();\n");
        html.append("}\n\n");

        html.append("function updateFilterStatus(visible, total) {\n");
        html.append("    // Could add a status bar here if needed\n");
        html.append("}\n\n");

        // Update existing search and filter functions to use applyFilters
        html.append("// Override performSearch to use applyFilters\n");
        html.append("performSearch = applyFilters;\n");
        html.append("clearSearch = function() {\n");
        html.append("    document.getElementById('searchInput').value = '';\n");
        html.append("    applyFilters();\n");
        html.append("};\n");

        html.append("</script>\n");
    }

    private String classifyViolationType(String message) {
        if (message.contains("SQL injection") || message.contains("SQL 注入") || message.contains("${")) {
            return "SQL 注入风险";
        } else if (message.contains("WHERE") && message.contains("空")) {
            return "WHERE 条件缺失";
        } else if (message.contains("分页")) {
            return "分页问题";
        } else if (message.contains("SELECT *")) {
            return "SELECT * 使用";
        } else if (message.contains("敏感表")) {
            return "敏感表访问";
        } else if (message.contains("LIMIT")) {
            return "LIMIT 参数问题";
        } else if (message.contains("ORDER BY")) {
            return "ORDER BY 注入";
        } else {
            return "其他安全问题";
        }
    }

    private String getRiskLevelName(RiskLevel level) {
        switch (level) {
            case CRITICAL: return "严重";
            case HIGH: return "高危";
            case MEDIUM: return "中危";
            case LOW: return "低危";
            default: return level.name();
        }
    }

    private String extractFileName(String filePath) {
        if (filePath == null) return "Unknown";
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return StringEscapeUtils.escapeHtml4(text);
    }
}

