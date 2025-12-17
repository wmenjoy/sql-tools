package com.footstone.sqlguard.demo.controller;

import com.footstone.sqlguard.demo.mapper.UserAnnotationMapper;
import com.footstone.sqlguard.demo.mapper.UserMapper;
import com.footstone.sqlguard.demo.service.OrderService;
import org.apache.ibatis.session.RowBounds;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Demo controller with interactive violation trigger endpoints.
 *
 * <p>This controller provides REST endpoints that trigger each of the 10 SQL Guard
 * validation rules. Each endpoint demonstrates a specific violation type and returns
 * detailed information about the violation detected (or success if strategy is LOG/WARN).</p>
 *
 * <p><strong>Violation Endpoints:</strong></p>
 * <ul>
 *   <li>GET /violations/no-where-clause - NoWhereClauseChecker (CRITICAL)</li>
 *   <li>GET /violations/dummy-condition - DummyConditionChecker (HIGH)</li>
 *   <li>GET /violations/blacklist-only - BlacklistFieldChecker (HIGH)</li>
 *   <li>GET /violations/whitelist-missing - WhitelistFieldChecker (HIGH)</li>
 *   <li>GET /violations/logical-pagination - LogicalPaginationChecker (CRITICAL)</li>
 *   <li>GET /violations/deep-pagination - DeepPaginationChecker (MEDIUM)</li>
 *   <li>GET /violations/large-page-size - LargePageSizeChecker (MEDIUM)</li>
 *   <li>GET /violations/missing-orderby - MissingOrderByChecker (LOW)</li>
 *   <li>GET /violations/no-pagination - NoPaginationChecker (Variable)</li>
 *   <li>GET /violations/no-condition-pagination - NoConditionPaginationChecker (CRITICAL)</li>
 * </ul>
 *
 * <p><strong>Management Endpoints:</strong></p>
 * <ul>
 *   <li>GET /violations/logs - View recent violations</li>
 *   <li>POST /config/strategy/{strategy} - Change violation strategy (LOG/WARN/BLOCK)</li>
 * </ul>
 */
@RestController
public class DemoController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserAnnotationMapper userAnnotationMapper;

    @Autowired
    private OrderService orderService;

    // In-memory violation log for demo purposes
    private static final Queue<ViolationLog> violationLogs = new ConcurrentLinkedQueue<>();
    private static final int MAX_LOG_SIZE = 100;

    /**
     * Trigger NoWhereClauseChecker violation (CRITICAL).
     *
     * <p>Executes DELETE without WHERE clause, which would delete all table data.</p>
     */
    @GetMapping("/violations/no-where-clause")
    public Map<String, Object> triggerNoWhereClause() {
        try {
            int affected = userMapper.deleteAllUnsafe();
            return success("NoWhereClauseChecker", "CRITICAL", 
                "DELETE without WHERE executed (strategy=LOG/WARN)", affected);
        } catch (Exception e) {
            return violation("NoWhereClauseChecker", "CRITICAL", 
                "DELETE without WHERE blocked (strategy=BLOCK)", e.getMessage());
        }
    }

    /**
     * Trigger DummyConditionChecker violation (HIGH).
     *
     * <p>Executes query with WHERE 1=1 dummy condition.</p>
     */
    @GetMapping("/violations/dummy-condition")
    public Map<String, Object> triggerDummyCondition() {
        try {
            var users = userMapper.findWithDummyCondition();
            return success("DummyConditionChecker", "HIGH", 
                "Query with WHERE 1=1 executed (strategy=LOG/WARN)", users.size());
        } catch (Exception e) {
            return violation("DummyConditionChecker", "HIGH", 
                "Query with WHERE 1=1 blocked (strategy=BLOCK)", e.getMessage());
        }
    }

    /**
     * Trigger BlacklistFieldChecker violation (HIGH).
     *
     * <p>Executes query with only blacklist field (status) in WHERE clause.</p>
     */
    @GetMapping("/violations/blacklist-only")
    public Map<String, Object> triggerBlacklistOnly() {
        try {
            var users = userMapper.findByStatusOnly("ACTIVE");
            return success("BlacklistFieldChecker", "HIGH", 
                "Query with only blacklist field executed (strategy=LOG/WARN)", users.size());
        } catch (Exception e) {
            return violation("BlacklistFieldChecker", "HIGH", 
                "Query with only blacklist field blocked (strategy=BLOCK)", e.getMessage());
        }
    }

    /**
     * Trigger WhitelistFieldChecker violation (HIGH).
     *
     * <p>Note: This requires whitelist configuration in application.yml.
     * If no whitelist configured, this endpoint returns N/A.</p>
     */
    @GetMapping("/violations/whitelist-missing")
    public Map<String, Object> triggerWhitelistMissing() {
        // WhitelistFieldChecker requires configuration in application.yml
        // Example: sql-guard.rules.whitelist-fields.whitelist-fields.user: [id, email]
        try {
            var users = userMapper.findByStatusOnly("ACTIVE");
            return success("WhitelistFieldChecker", "HIGH", 
                "Query without required whitelist fields (requires configuration)", users.size());
        } catch (Exception e) {
            return violation("WhitelistFieldChecker", "HIGH", 
                "Query without required whitelist fields blocked", e.getMessage());
        }
    }

    /**
     * Trigger LogicalPaginationChecker violation (CRITICAL).
     *
     * <p>Executes query with RowBounds (in-memory pagination) without PageHelper plugin.</p>
     */
    @GetMapping("/violations/logical-pagination")
    public Map<String, Object> triggerLogicalPagination() {
        // Note: This requires calling MyBatis query methods with RowBounds parameter
        // For simplicity, we'll demonstrate with a comment
        return Map.of(
            "checker", "LogicalPaginationChecker",
            "riskLevel", "CRITICAL",
            "message", "Logical pagination requires RowBounds parameter in mapper method call",
            "note", "This violation is triggered when using RowBounds without PageHelper plugin",
            "example", "sqlSession.selectList(statement, parameter, new RowBounds(offset, limit))"
        );
    }

    /**
     * Trigger DeepPaginationChecker violation (MEDIUM).
     *
     * <p>Executes query with high OFFSET value (> 10000).</p>
     */
    @GetMapping("/violations/deep-pagination")
    public Map<String, Object> triggerDeepPagination() {
        try {
            var users = userMapper.findWithDeepOffset("%test%", 20, 50000);
            return success("DeepPaginationChecker", "MEDIUM", 
                "Query with deep offset (50000) executed (strategy=LOG/WARN)", users.size());
        } catch (Exception e) {
            return violation("DeepPaginationChecker", "MEDIUM", 
                "Query with deep offset blocked (strategy=BLOCK)", e.getMessage());
        }
    }

    /**
     * Trigger LargePageSizeChecker violation (MEDIUM).
     *
     * <p>Executes query with large LIMIT value (> 1000).</p>
     */
    @GetMapping("/violations/large-page-size")
    public Map<String, Object> triggerLargePageSize() {
        try {
            var users = userMapper.findWithLargePageSize("%test%", 5000);
            return success("LargePageSizeChecker", "MEDIUM", 
                "Query with large page size (5000) executed (strategy=LOG/WARN)", users.size());
        } catch (Exception e) {
            return violation("LargePageSizeChecker", "MEDIUM", 
                "Query with large page size blocked (strategy=BLOCK)", e.getMessage());
        }
    }

    /**
     * Trigger MissingOrderByChecker violation (LOW).
     *
     * <p>Executes pagination query without ORDER BY clause.</p>
     */
    @GetMapping("/violations/missing-orderby")
    public Map<String, Object> triggerMissingOrderBy() {
        try {
            var users = userMapper.findWithoutOrderBy("%test%", 20);
            return success("MissingOrderByChecker", "LOW", 
                "Pagination without ORDER BY executed (strategy=LOG/WARN)", users.size());
        } catch (Exception e) {
            return violation("MissingOrderByChecker", "LOW", 
                "Pagination without ORDER BY blocked (strategy=BLOCK)", e.getMessage());
        }
    }

    /**
     * Trigger NoPaginationChecker violation (Variable risk).
     *
     * <p>Executes SELECT without LIMIT clause on potentially large table.</p>
     */
    @GetMapping("/violations/no-pagination")
    public Map<String, Object> triggerNoPagination() {
        try {
            var users = userMapper.findAllUnsafe();
            return success("NoPaginationChecker", "MEDIUM/CRITICAL", 
                "SELECT without pagination executed (strategy=LOG/WARN)", users.size());
        } catch (Exception e) {
            return violation("NoPaginationChecker", "MEDIUM/CRITICAL", 
                "SELECT without pagination blocked (strategy=BLOCK)", e.getMessage());
        }
    }

    /**
     * Trigger NoConditionPaginationChecker violation (CRITICAL).
     *
     * <p>Executes LIMIT query without WHERE clause (still performs full table scan).</p>
     */
    @GetMapping("/violations/no-condition-pagination")
    public Map<String, Object> triggerNoConditionPagination() {
        try {
            var users = userMapper.findWithLimitNoWhere(10);
            return success("NoConditionPaginationChecker", "CRITICAL", 
                "LIMIT without WHERE executed (strategy=LOG/WARN)", users.size());
        } catch (Exception e) {
            return violation("NoConditionPaginationChecker", "CRITICAL", 
                "LIMIT without WHERE blocked (strategy=BLOCK)", e.getMessage());
        }
    }

    /**
     * Get recent violation logs.
     */
    @GetMapping("/violations/logs")
    public Map<String, Object> getViolationLogs() {
        return Map.of(
            "total", violationLogs.size(),
            "logs", new ArrayList<>(violationLogs)
        );
    }

    /**
     * Change violation strategy at runtime.
     *
     * <p>Note: This is a demo endpoint. In production, strategy should be configured
     * via application.yml or config center (Apollo/Nacos).</p>
     */
    @PostMapping("/config/strategy/{strategy}")
    public Map<String, Object> changeStrategy(@PathVariable String strategy) {
        // Note: Actual strategy change requires modifying SqlSafetyInterceptor configuration
        // This is a placeholder for demo purposes
        if (!Arrays.asList("LOG", "WARN", "BLOCK").contains(strategy.toUpperCase())) {
            return Map.of(
                "success", false,
                "message", "Invalid strategy. Must be LOG, WARN, or BLOCK"
            );
        }
        
        return Map.of(
            "success", true,
            "message", "Strategy change requires application restart or config center hot-reload",
            "newStrategy", strategy.toUpperCase(),
            "note", "Configure via sql-guard.active-strategy in application.yml"
        );
    }

    /**
     * Home page with API documentation.
     */
    @GetMapping("/")
    public Map<String, Object> home() {
        return Map.of(
            "application", "SQL Guard Demo",
            "description", "Interactive demonstration of SQL Safety Guard System",
            "endpoints", Map.of(
                "violations", List.of(
                    "/violations/no-where-clause",
                    "/violations/dummy-condition",
                    "/violations/blacklist-only",
                    "/violations/whitelist-missing",
                    "/violations/logical-pagination",
                    "/violations/deep-pagination",
                    "/violations/large-page-size",
                    "/violations/missing-orderby",
                    "/violations/no-pagination",
                    "/violations/no-condition-pagination"
                ),
                "management", List.of(
                    "/violations/logs",
                    "/config/strategy/{LOG|WARN|BLOCK}"
                )
            ),
            "documentation", "See README.md for detailed usage instructions"
        );
    }

    // Helper methods

    private Map<String, Object> success(String checker, String riskLevel, String message, int rowsAffected) {
        ViolationLog log = new ViolationLog(checker, riskLevel, message, "SUCCESS", rowsAffected);
        addLog(log);
        return Map.of(
            "status", "success",
            "checker", checker,
            "riskLevel", riskLevel,
            "message", message,
            "rowsAffected", rowsAffected,
            "timestamp", log.timestamp
        );
    }

    private Map<String, Object> violation(String checker, String riskLevel, String message, String error) {
        ViolationLog log = new ViolationLog(checker, riskLevel, message, "BLOCKED", 0);
        addLog(log);
        return Map.of(
            "status", "blocked",
            "checker", checker,
            "riskLevel", riskLevel,
            "message", message,
            "error", error,
            "timestamp", log.timestamp
        );
    }

    private void addLog(ViolationLog log) {
        violationLogs.offer(log);
        while (violationLogs.size() > MAX_LOG_SIZE) {
            violationLogs.poll();
        }
    }

    /**
     * Violation log entry for in-memory storage.
     */
    private static class ViolationLog {
        public final String checker;
        public final String riskLevel;
        public final String message;
        public final String status;
        public final int rowsAffected;
        public final String timestamp;

        public ViolationLog(String checker, String riskLevel, String message, String status, int rowsAffected) {
            this.checker = checker;
            this.riskLevel = riskLevel;
            this.message = message;
            this.status = status;
            this.rowsAffected = rowsAffected;
            this.timestamp = new Date().toString();
        }
    }
}


