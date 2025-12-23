package com.footstone.sqlguard.demo.controller;

import com.footstone.sqlguard.demo.entity.User;
import com.footstone.sqlguard.demo.mapper.AuditScenarioMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for triggering audit scenarios.
 *
 * <p>This controller provides endpoints to trigger specific SQL patterns that
 * generate audit log entries for demonstration purposes. Each endpoint triggers
 * a different audit scenario to showcase the SQL Audit Platform capabilities.</p>
 *
 * <p><strong>Endpoints:</strong></p>
 * <ul>
 *   <li>GET /api/audit-scenarios/slow-query - Trigger 5-second slow query</li>
 *   <li>POST /api/audit-scenarios/missing-where - Trigger UPDATE without WHERE</li>
 *   <li>GET /api/audit-scenarios/deep-pagination - Trigger deep pagination query</li>
 *   <li>GET /api/audit-scenarios/error-sql - Trigger SQL error</li>
 *   <li>GET /api/audit-scenarios/large-page-size - Trigger large page size query</li>
 *   <li>GET /api/audit-scenarios/no-pagination - Trigger query without pagination</li>
 * </ul>
 *
 * @see AuditScenarioMapper
 */
@RestController
@RequestMapping("/api/audit-scenarios")
public class AuditScenarioController {

    private static final Logger log = LoggerFactory.getLogger(AuditScenarioController.class);

    @Autowired
    private AuditScenarioMapper auditScenarioMapper;

    /**
     * Trigger a slow query scenario.
     *
     * <p>Executes a query with 5-second delay using MySQL SLEEP function.
     * This triggers the SlowQueryChecker and generates a HIGH severity audit log.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/slow-query")
    public ResponseEntity<Map<String, Object>> triggerSlowQuery() {
        log.info("Triggering slow query scenario...");
        long startTime = System.currentTimeMillis();
        
        try {
            List<User> users = auditScenarioMapper.slowQuery();
            long executionTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "slow-query");
            response.put("status", "success");
            response.put("message", "Slow query executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsReturned", users.size());
            response.put("expectedAudit", "SlowQueryChecker - HIGH severity");
            
            log.info("Slow query completed in {}ms, returned {} rows", executionTime, users.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Slow query failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "slow-query",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Trigger a missing WHERE update scenario.
     *
     * <p>Executes an UPDATE without WHERE clause, affecting all rows.
     * This triggers the ActualImpactNoWhereChecker and generates a CRITICAL severity audit log.</p>
     *
     * <p><strong>WARNING:</strong> This endpoint modifies all user records!</p>
     *
     * @return response with execution details
     */
    @PostMapping("/missing-where")
    public ResponseEntity<Map<String, Object>> triggerMissingWhere() {
        log.warn("Triggering missing WHERE update scenario (affects all rows!)...");
        long startTime = System.currentTimeMillis();
        
        try {
            int affected = auditScenarioMapper.updateWithoutWhere();
            long executionTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "missing-where");
            response.put("status", "success");
            response.put("message", "Updated " + affected + " rows without WHERE clause, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsAffected", affected);
            response.put("expectedAudit", "ActualImpactNoWhereChecker - CRITICAL severity");
            response.put("warning", "All user records have been set to INACTIVE status");
            
            log.warn("Missing WHERE update completed in {}ms, affected {} rows", executionTime, affected);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Missing WHERE update failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "missing-where",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Trigger a deep pagination scenario.
     *
     * <p>Executes a query with OFFSET 10000, which is inefficient for large tables.
     * This triggers the DeepPaginationChecker and generates a MEDIUM severity audit log.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/deep-pagination")
    public ResponseEntity<Map<String, Object>> triggerDeepPagination() {
        log.info("Triggering deep pagination scenario (offset 10000)...");
        long startTime = System.currentTimeMillis();
        
        try {
            List<User> users = auditScenarioMapper.deepPagination();
            long executionTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "deep-pagination");
            response.put("status", "success");
            response.put("message", "Deep pagination executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsReturned", users.size());
            response.put("offset", 10000);
            response.put("limit", 100);
            response.put("expectedAudit", "DeepPaginationChecker - MEDIUM severity");
            
            log.info("Deep pagination completed in {}ms, returned {} rows", executionTime, users.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Deep pagination failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "deep-pagination",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Trigger an error SQL scenario.
     *
     * <p>Executes a query that references a non-existent table, causing an error.
     * This triggers the ErrorRateChecker when called multiple times.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/error-sql")
    public ResponseEntity<Map<String, Object>> triggerErrorSql() {
        log.info("Triggering error SQL scenario...");
        
        try {
            auditScenarioMapper.invalidSql();
            // Should not reach here
            return ResponseEntity.ok(Map.of(
                "scenario", "error-sql",
                "status", "unexpected_success",
                "message", "Query should have failed but succeeded"
            ));
        } catch (Exception e) {
            log.info("Error SQL triggered as expected: {}", e.getMessage());
            
            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "error-sql");
            response.put("status", "error_triggered");
            response.put("message", "SQL error triggered as expected, check audit logs");
            response.put("errorMessage", e.getMessage());
            response.put("expectedAudit", "ErrorRateChecker - aggregates errors for spike detection");
            
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Trigger a large page size scenario.
     *
     * <p>Executes a query with LIMIT 5000, which exceeds recommended page sizes.
     * This triggers the LargePageSizeChecker and generates a MEDIUM severity audit log.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/large-page-size")
    public ResponseEntity<Map<String, Object>> triggerLargePageSize() {
        log.info("Triggering large page size scenario (limit 5000)...");
        long startTime = System.currentTimeMillis();
        
        try {
            List<User> users = auditScenarioMapper.largePageSize(5000);
            long executionTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "large-page-size");
            response.put("status", "success");
            response.put("message", "Large page size query executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsReturned", users.size());
            response.put("limit", 5000);
            response.put("expectedAudit", "LargePageSizeChecker - MEDIUM severity");
            
            log.info("Large page size completed in {}ms, returned {} rows", executionTime, users.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Large page size failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "large-page-size",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Trigger a no-pagination scenario.
     *
     * <p>Executes a SELECT without LIMIT clause on a potentially large result set.
     * This triggers the NoPaginationChecker and generates a MEDIUM/CRITICAL severity audit log.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/no-pagination")
    public ResponseEntity<Map<String, Object>> triggerNoPagination() {
        log.info("Triggering no pagination scenario...");
        long startTime = System.currentTimeMillis();
        
        try {
            List<User> users = auditScenarioMapper.selectAllWithoutPagination();
            long executionTime = System.currentTimeMillis() - startTime;
            
            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "no-pagination");
            response.put("status", "success");
            response.put("message", "Query without pagination executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsReturned", users.size());
            response.put("expectedAudit", "NoPaginationChecker - MEDIUM/CRITICAL severity");
            
            log.info("No pagination completed in {}ms, returned {} rows", executionTime, users.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("No pagination failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "no-pagination",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Get all available audit scenarios.
     *
     * @return list of available scenarios with descriptions
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listScenarios() {
        Map<String, Object> response = new HashMap<>();
        response.put("scenarios", List.of(
            Map.of(
                "endpoint", "/api/audit-scenarios/slow-query",
                "method", "GET",
                "description", "Trigger 5-second slow query",
                "checker", "SlowQueryChecker",
                "severity", "HIGH"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/missing-where",
                "method", "POST",
                "description", "Trigger UPDATE without WHERE clause",
                "checker", "ActualImpactNoWhereChecker",
                "severity", "CRITICAL",
                "warning", "Modifies all user records!"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/deep-pagination",
                "method", "GET",
                "description", "Trigger deep pagination (offset 10000)",
                "checker", "DeepPaginationChecker",
                "severity", "MEDIUM"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/error-sql",
                "method", "GET",
                "description", "Trigger SQL error for error rate detection",
                "checker", "ErrorRateChecker",
                "severity", "Variable (based on aggregation)"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/large-page-size",
                "method", "GET",
                "description", "Trigger large page size query (limit 5000)",
                "checker", "LargePageSizeChecker",
                "severity", "MEDIUM"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/no-pagination",
                "method", "GET",
                "description", "Trigger query without pagination",
                "checker", "NoPaginationChecker",
                "severity", "MEDIUM/CRITICAL"
            )
        ));
        
        return ResponseEntity.ok(response);
    }
}




