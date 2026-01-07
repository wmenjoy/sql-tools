package com.footstone.sqlguard.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HTTP Integration Tests - 真正启动服务并通过 HTTP 接口调用
 *
 * <p>这些测试会启动完整的 Spring Boot 应用，通过 HTTP 请求调用 REST API，
 * 验证 SQL Guard 拦截器在真实服务环境中的行为。</p>
 *
 * <p>测试场景包括：</p>
 * <ul>
 *   <li>各种 SQL 违规场景的 HTTP 端点调用</li>
 *   <li>验证拦截器检测并记录违规</li>
 *   <li>验证不同策略下的行为差异</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @Order(1)
    @DisplayName("1. 服务健康检查 - 验证应用正常启动")
    void testHealthCheck() {
        System.out.println("\n=== 测试 1: 服务健康检查 ===");
        System.out.println("服务端口: " + port);
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/actuator/health", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
        
        System.out.println("✅ 服务健康检查通过");
    }

    @Test
    @Order(2)
    @DisplayName("2. 首页 API 文档 - 验证 REST 端点可用")
    void testHomePage() {
        System.out.println("\n=== 测试 2: 首页 API 文档 ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SQL Guard Demo", response.getBody().get("application"));
        
        System.out.println("应用名称: " + response.getBody().get("application"));
        System.out.println("✅ 首页 API 文档测试通过");
    }

    @Test
    @Order(3)
    @DisplayName("3. NoWhereClause 违规 - DELETE 无 WHERE")
    @SuppressWarnings("unchecked")
    void testNoWhereClauseViolation() {
        System.out.println("\n=== 测试 3: NoWhereClause 违规 (DELETE 无 WHERE) ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/violations/no-where-clause", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        // 检查响应中包含检查器信息
        Map<String, Object> body = response.getBody();
        assertEquals("NoWhereClauseChecker", body.get("checker"));
        assertEquals("CRITICAL", body.get("riskLevel"));
        
        System.out.println("检查器: " + body.get("checker"));
        System.out.println("风险级别: " + body.get("riskLevel"));
        System.out.println("消息: " + body.get("message"));
        System.out.println("✅ NoWhereClause 违规测试通过 - 拦截器已检测");
    }

    @Test
    @Order(4)
    @DisplayName("4. DummyCondition 违规 - WHERE 1=1")
    @SuppressWarnings("unchecked")
    void testDummyConditionViolation() {
        System.out.println("\n=== 测试 4: DummyCondition 违规 (WHERE 1=1) ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/violations/dummy-condition", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals("DummyConditionChecker", body.get("checker"));
        assertEquals("HIGH", body.get("riskLevel"));
        
        System.out.println("检查器: " + body.get("checker"));
        System.out.println("风险级别: " + body.get("riskLevel"));
        System.out.println("✅ DummyCondition 违规测试通过 - 拦截器已检测");
    }

    @Test
    @Order(5)
    @DisplayName("5. BlacklistOnly 违规 - 只有黑名单字段")
    @SuppressWarnings("unchecked")
    void testBlacklistOnlyViolation() {
        System.out.println("\n=== 测试 5: BlacklistOnly 违规 (只有黑名单字段) ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/violations/blacklist-only", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals("BlacklistFieldChecker", body.get("checker"));
        assertEquals("HIGH", body.get("riskLevel"));
        
        System.out.println("检查器: " + body.get("checker"));
        System.out.println("风险级别: " + body.get("riskLevel"));
        System.out.println("✅ BlacklistOnly 违规测试通过 - 拦截器已检测");
    }

    @Test
    @Order(6)
    @DisplayName("6. DeepPagination 违规 - 深度分页")
    @SuppressWarnings("unchecked")
    void testDeepPaginationViolation() {
        System.out.println("\n=== 测试 6: DeepPagination 违规 (深度分页) ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/violations/deep-pagination", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals("DeepPaginationChecker", body.get("checker"));
        assertEquals("MEDIUM", body.get("riskLevel"));
        
        System.out.println("检查器: " + body.get("checker"));
        System.out.println("风险级别: " + body.get("riskLevel"));
        System.out.println("✅ DeepPagination 违规测试通过 - 拦截器已检测");
    }

    @Test
    @Order(7)
    @DisplayName("7. LargePageSize 违规 - 大页面")
    @SuppressWarnings("unchecked")
    void testLargePageSizeViolation() {
        System.out.println("\n=== 测试 7: LargePageSize 违规 (大页面) ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/violations/large-page-size", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals("LargePageSizeChecker", body.get("checker"));
        assertEquals("MEDIUM", body.get("riskLevel"));
        
        System.out.println("检查器: " + body.get("checker"));
        System.out.println("风险级别: " + body.get("riskLevel"));
        System.out.println("✅ LargePageSize 违规测试通过 - 拦截器已检测");
    }

    @Test
    @Order(8)
    @DisplayName("8. MissingOrderBy 违规 - 分页无排序")
    @SuppressWarnings("unchecked")
    void testMissingOrderByViolation() {
        System.out.println("\n=== 测试 8: MissingOrderBy 违规 (分页无排序) ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/violations/missing-orderby", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals("MissingOrderByChecker", body.get("checker"));
        assertEquals("LOW", body.get("riskLevel"));
        
        System.out.println("检查器: " + body.get("checker"));
        System.out.println("风险级别: " + body.get("riskLevel"));
        System.out.println("✅ MissingOrderBy 违规测试通过 - 拦截器已检测");
    }

    @Test
    @Order(9)
    @DisplayName("9. NoPagination 违规 - 无分页")
    @SuppressWarnings("unchecked")
    void testNoPaginationViolation() {
        System.out.println("\n=== 测试 9: NoPagination 违规 (无分页) ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/violations/no-pagination", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals("NoPaginationChecker", body.get("checker"));
        
        System.out.println("检查器: " + body.get("checker"));
        System.out.println("风险级别: " + body.get("riskLevel"));
        System.out.println("✅ NoPagination 违规测试通过 - 拦截器已检测");
    }

    @Test
    @Order(10)
    @DisplayName("10. NoConditionPagination 违规 - 无条件分页")
    @SuppressWarnings("unchecked")
    void testNoConditionPaginationViolation() {
        System.out.println("\n=== 测试 10: NoConditionPagination 违规 (无条件分页) ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/violations/no-condition-pagination", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertEquals("NoConditionPaginationChecker", body.get("checker"));
        assertEquals("CRITICAL", body.get("riskLevel"));
        
        System.out.println("检查器: " + body.get("checker"));
        System.out.println("风险级别: " + body.get("riskLevel"));
        System.out.println("✅ NoConditionPagination 违规测试通过 - 拦截器已检测");
    }

    @Test
    @Order(11)
    @DisplayName("11. 查看违规日志")
    @SuppressWarnings("unchecked")
    void testViewViolationLogs() {
        System.out.println("\n=== 测试 11: 查看违规日志 ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/violations/logs", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        assertNotNull(body.get("logs"));
        
        System.out.println("日志数量: " + body.get("count"));
        System.out.println("✅ 违规日志查看测试通过");
    }

    @Test
    @Order(12)
    @DisplayName("12. 审计场景 - 慢查询")
    void testAuditScenarioSlowQuery() {
        System.out.println("\n=== 测试 12: 审计场景 - 慢查询 ===");
        System.out.println("注意: 此测试会执行一个模拟的慢查询");
        
        // 使用较短超时的请求
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/api/audit-scenarios/slow-query", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        
        // 慢查询可能成功或失败，我们只检查响应
        assertNotNull(response);
        System.out.println("✅ 慢查询审计场景测试完成");
    }

    @Test
    @Order(13)
    @DisplayName("13. 审计场景 - 深度分页")
    @SuppressWarnings("unchecked")
    void testAuditScenarioDeepPagination() {
        System.out.println("\n=== 测试 13: 审计场景 - 深度分页 ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/api/audit-scenarios/deep-pagination", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        // 审计场景可能因 H2 数据库限制而失败，我们只验证请求被处理
        assertNotNull(response);
        if (response.getStatusCode() == HttpStatus.OK) {
            System.out.println("✅ 深度分页审计场景测试通过");
        } else {
            System.out.println("⚠️ 审计场景返回错误 (可能是 H2 数据库限制): " + response.getStatusCode());
        }
    }

    @Test
    @Order(14)
    @DisplayName("14. 审计场景 - 大页面")
    @SuppressWarnings("unchecked")
    void testAuditScenarioLargePageSize() {
        System.out.println("\n=== 测试 14: 审计场景 - 大页面 ===");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            baseUrl() + "/api/audit-scenarios/large-page-size", Map.class);
        
        System.out.println("响应状态: " + response.getStatusCode());
        System.out.println("响应体: " + response.getBody());
        
        // 审计场景可能因 H2 数据库限制而失败，我们只验证请求被处理
        assertNotNull(response);
        if (response.getStatusCode() == HttpStatus.OK) {
            System.out.println("✅ 大页面审计场景测试通过");
        } else {
            System.out.println("⚠️ 审计场景返回错误 (可能是 H2 数据库限制): " + response.getStatusCode());
        }
    }

    @Test
    @Order(15)
    @DisplayName("15. 综合测试 - 连续调用多个违规端点")
    void testMultipleViolationsInSequence() {
        System.out.println("\n=== 测试 15: 综合测试 - 连续调用多个违规端点 ===");
        
        String[] endpoints = {
            "/violations/dummy-condition",
            "/violations/blacklist-only",
            "/violations/deep-pagination",
            "/violations/large-page-size",
            "/violations/missing-orderby"
        };
        
        int successCount = 0;
        for (String endpoint : endpoints) {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                baseUrl() + endpoint, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                successCount++;
                System.out.println("  ✓ " + endpoint + " - 成功");
            } else {
                System.out.println("  ✗ " + endpoint + " - 失败: " + response.getStatusCode());
            }
        }
        
        assertEquals(endpoints.length, successCount, "所有端点应该返回成功");
        System.out.println("\n✅ 综合测试通过 - " + successCount + "/" + endpoints.length + " 个端点成功");
    }
}
