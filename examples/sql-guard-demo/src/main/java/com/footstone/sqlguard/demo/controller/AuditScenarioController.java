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
 *   <li>GET /api/audit-scenarios/missing-orderby - Trigger pagination without ORDER BY</li>
 *   <li>GET /api/audit-scenarios/no-condition-pagination - Trigger pagination without WHERE</li>
 *   <li>GET /api/audit-scenarios/blacklist-field - Trigger blacklist-only WHERE clause</li>
 *   <li>GET /api/audit-scenarios/whitelist-violation - Trigger non-whitelisted field access</li>
 *   <li>GET /api/audit-scenarios/dummy-condition - Trigger dummy condition (1=1)</li>
 *   <li>GET /api/audit-scenarios/no-where - Trigger SELECT without WHERE</li>
 *   <li>POST /api/audit-scenarios/delete-no-where - Trigger DELETE with broad WHERE</li>
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
     * Trigger missing ORDER BY scenario.
     *
     * <p>Executes a pagination query without ORDER BY clause.
     * This triggers the MissingOrderByChecker and generates a LOW severity audit log.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/missing-orderby")
    public ResponseEntity<Map<String, Object>> triggerMissingOrderBy() {
        log.info("Triggering missing ORDER BY scenario...");
        long startTime = System.currentTimeMillis();

        try {
            List<User> users = auditScenarioMapper.paginationWithoutOrderBy();
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "missing-orderby");
            response.put("status", "success");
            response.put("message", "Pagination without ORDER BY executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsReturned", users.size());
            response.put("expectedAudit", "MissingOrderByChecker - LOW severity");

            log.info("Missing ORDER BY completed in {}ms, returned {} rows", executionTime, users.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Missing ORDER BY failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "missing-orderby",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Trigger no condition pagination scenario.
     *
     * <p>Executes a pagination query without WHERE clause.
     * This triggers the NoConditionPaginationChecker and generates a MEDIUM severity audit log.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/no-condition-pagination")
    public ResponseEntity<Map<String, Object>> triggerNoConditionPagination() {
        log.info("Triggering no condition pagination scenario...");
        long startTime = System.currentTimeMillis();

        try {
            List<User> users = auditScenarioMapper.paginationWithoutCondition();
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "no-condition-pagination");
            response.put("status", "success");
            response.put("message", "Pagination without WHERE executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsReturned", users.size());
            response.put("expectedAudit", "NoConditionPaginationChecker - MEDIUM severity");

            log.info("No condition pagination completed in {}ms, returned {} rows", executionTime, users.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("No condition pagination failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "no-condition-pagination",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Trigger blacklist field only scenario.
     *
     * <p>Executes a query using only blacklisted fields in WHERE clause.
     * This triggers the BlacklistFieldChecker and generates a HIGH severity audit log.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/blacklist-field")
    public ResponseEntity<Map<String, Object>> triggerBlacklistField() {
        log.info("Triggering blacklist field only scenario...");
        long startTime = System.currentTimeMillis();

        try {
            List<User> users = auditScenarioMapper.selectWithBlacklistFieldOnly();
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "blacklist-field");
            response.put("status", "success");
            response.put("message", "Query with blacklist-only fields executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsReturned", users.size());
            response.put("expectedAudit", "BlacklistFieldChecker - HIGH severity");

            log.info("Blacklist field completed in {}ms, returned {} rows", executionTime, users.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Blacklist field failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "blacklist-field",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Trigger whitelist field violation scenario.
     *
     * <p>Executes a query accessing non-whitelisted fields.
     * This triggers the WhitelistFieldChecker and generates a HIGH severity audit log.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/whitelist-violation")
    public ResponseEntity<Map<String, Object>> triggerWhitelistViolation() {
        log.info("Triggering whitelist violation scenario...");
        long startTime = System.currentTimeMillis();

        try {
            User user = auditScenarioMapper.selectWithNonWhitelistFields(1L);
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "whitelist-violation");
            response.put("status", "success");
            response.put("message", "Query accessing non-whitelisted fields executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("expectedAudit", "WhitelistFieldChecker - HIGH severity");

            log.info("Whitelist violation completed in {}ms", executionTime);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Whitelist violation failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "whitelist-violation",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Trigger dummy condition scenario.
     *
     * <p>Executes a query with dummy condition like "1=1".
     * This triggers the DummyConditionChecker and generates a HIGH severity audit log.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/dummy-condition")
    public ResponseEntity<Map<String, Object>> triggerDummyCondition() {
        log.info("Triggering dummy condition scenario...");
        long startTime = System.currentTimeMillis();

        try {
            List<User> users = auditScenarioMapper.selectWithDummyCondition();
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "dummy-condition");
            response.put("status", "success");
            response.put("message", "Query with dummy condition (1=1) executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsReturned", users.size());
            response.put("expectedAudit", "DummyConditionChecker - HIGH severity");

            log.info("Dummy condition completed in {}ms, returned {} rows", executionTime, users.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Dummy condition failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "dummy-condition",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Trigger no WHERE clause scenario.
     *
     * <p>Executes a SELECT without WHERE clause.
     * This triggers the NoWhereClauseChecker and generates a HIGH severity audit log.</p>
     *
     * @return response with execution details
     */
    @GetMapping("/no-where")
    public ResponseEntity<Map<String, Object>> triggerNoWhere() {
        log.info("Triggering no WHERE clause scenario...");
        long startTime = System.currentTimeMillis();

        try {
            List<User> users = auditScenarioMapper.selectWithoutWhere();
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "no-where");
            response.put("status", "success");
            response.put("message", "SELECT without WHERE executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsReturned", users.size());
            response.put("expectedAudit", "NoWhereClauseChecker - HIGH severity");

            log.info("No WHERE completed in {}ms, returned {} rows", executionTime, users.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("No WHERE failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "no-where",
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * Trigger DELETE without WHERE scenario.
     *
     * <p>Executes a DELETE with limited WHERE clause.
     * This triggers the NoWhereClauseChecker and generates a CRITICAL severity audit log.</p>
     *
     * @return response with execution details
     */
    @PostMapping("/delete-no-where")
    public ResponseEntity<Map<String, Object>> triggerDeleteNoWhere() {
        log.warn("Triggering DELETE scenario...");
        long startTime = System.currentTimeMillis();

        try {
            int affected = auditScenarioMapper.deleteWithoutProperWhere();
            long executionTime = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("scenario", "delete-no-where");
            response.put("status", "success");
            response.put("message", "DELETE executed, check audit logs");
            response.put("executionTimeMs", executionTime);
            response.put("rowsAffected", affected);
            response.put("expectedAudit", "NoWhereClauseChecker - CRITICAL severity");

            log.warn("DELETE completed in {}ms, affected {} rows", executionTime, affected);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("DELETE failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "scenario", "delete-no-where",
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
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/missing-orderby",
                "method", "GET",
                "description", "Trigger pagination without ORDER BY",
                "checker", "MissingOrderByChecker",
                "severity", "LOW"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/no-condition-pagination",
                "method", "GET",
                "description", "Trigger pagination without WHERE clause",
                "checker", "NoConditionPaginationChecker",
                "severity", "MEDIUM"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/blacklist-field",
                "method", "GET",
                "description", "Trigger query with blacklist-only fields",
                "checker", "BlacklistFieldChecker",
                "severity", "HIGH"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/whitelist-violation",
                "method", "GET",
                "description", "Trigger query accessing non-whitelisted fields",
                "checker", "WhitelistFieldChecker",
                "severity", "HIGH"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/dummy-condition",
                "method", "GET",
                "description", "Trigger query with dummy condition (1=1)",
                "checker", "DummyConditionChecker",
                "severity", "HIGH"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/no-where",
                "method", "GET",
                "description", "Trigger SELECT without WHERE clause",
                "checker", "NoWhereClauseChecker",
                "severity", "HIGH"
            ),
            Map.of(
                "endpoint", "/api/audit-scenarios/delete-no-where",
                "method", "POST",
                "description", "Trigger DELETE with broad WHERE",
                "checker", "NoWhereClauseChecker",
                "severity", "CRITICAL"
            )
        ));

        return ResponseEntity.ok(response);
    }
}





