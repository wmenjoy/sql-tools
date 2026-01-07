package com.footstone.sqlguard.demo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test controller for Druid JDBC layer audit logging.
 *
 * <p>This controller uses JdbcTemplate to execute SQL directly through JDBC,
 * bypassing MyBatis layer and triggering Druid Filter interceptors.</p>
 *
 * <p>All SQL executions will be audited at the JDBC layer by DruidSqlAuditFilter
 * and validated by DruidSqlSafetyFilter.</p>
 */
@RestController
@RequestMapping("/druid-test")
public class DruidTestController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Test Druid audit logging - No WHERE clause violation.
     *
     * <p>Executes: DELETE FROM user (without WHERE clause)</p>
     * <p>Expected violation: CRITICAL - No WHERE clause in DELETE</p>
     *
     * @return test result
     */
    @GetMapping("/no-where-delete")
    public Map<String, Object> testNoWhereDelete() {
        Map<String, Object> result = new HashMap<>();
        try {
            // This will trigger CRITICAL violation
            int rows = jdbcTemplate.update("DELETE FROM user");
            result.put("status", "success");
            result.put("rowsAffected", rows);
            result.put("message", "DELETE without WHERE executed at JDBC layer");
            result.put("layer", "DRUID/JDBC");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Test Druid audit logging - Dummy condition violation.
     *
     * <p>Executes: SELECT * FROM user WHERE 1=1</p>
     * <p>Expected violation: HIGH - Dummy condition (1=1)</p>
     *
     * @return test result
     */
    @GetMapping("/dummy-condition")
    public Map<String, Object> testDummyCondition() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM user WHERE 1=1");
            result.put("status", "success");
            result.put("rowsReturned", rows.size());
            result.put("message", "Query with dummy condition executed at JDBC layer");
            result.put("layer", "DRUID/JDBC");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Test Druid audit logging - Blacklist field only violation.
     *
     * <p>Executes: SELECT * FROM user WHERE status = 'ACTIVE'</p>
     * <p>Expected violation: HIGH - Only blacklist field in WHERE</p>
     *
     * @return test result
     */
    @GetMapping("/blacklist-only")
    public Map<String, Object> testBlacklistOnly() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM user WHERE status = 'ACTIVE'"
            );
            result.put("status", "success");
            result.put("rowsReturned", rows.size());
            result.put("message", "Query with blacklist field only executed at JDBC layer");
            result.put("layer", "DRUID/JDBC");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Test Druid audit logging - No pagination violation.
     *
     * <p>Executes: SELECT * FROM user (without LIMIT)</p>
     * <p>Expected violation: CRITICAL - No pagination limit</p>
     *
     * @return test result
     */
    @GetMapping("/no-pagination")
    public Map<String, Object> testNoPagination() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM user");
            result.put("status", "success");
            result.put("rowsReturned", rows.size());
            result.put("message", "Query without pagination executed at JDBC layer");
            result.put("layer", "DRUID/JDBC");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Test Druid audit logging - Clean query (no violations).
     *
     * <p>Executes: SELECT * FROM user WHERE id = ?</p>
     * <p>Expected: No violations, clean execution</p>
     *
     * @param id user ID
     * @return test result
     */
    @GetMapping("/clean-query/{id}")
    public Map<String, Object> testCleanQuery(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM user WHERE id = ?",
                id
            );
            result.put("status", "success");
            result.put("rowsReturned", rows.size());
            result.put("message", "Clean query executed at JDBC layer");
            result.put("layer", "DRUID/JDBC");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Health check endpoint for Druid testing.
     *
     * @return health status
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> result = new HashMap<>();
        result.put("status", "UP");
        result.put("layer", "DRUID/JDBC");
        result.put("message", "Druid test endpoints are ready");
        return result;
    }
}
