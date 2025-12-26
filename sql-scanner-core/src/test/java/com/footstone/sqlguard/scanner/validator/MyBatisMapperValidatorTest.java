package com.footstone.sqlguard.scanner.validator;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.ViolationInfo;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MyBatisMapperValidator.
 *
 * <p>Verifies that XML-level security validation works correctly for MyBatis Mapper files.</p>
 */
@DisplayName("MyBatis Mapper Validator Tests")
class MyBatisMapperValidatorTest {

    private MyBatisMapperValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MyBatisMapperValidator();
        // Enable all checks for comprehensive testing
        validator.setCheckSelectStar(true);
        validator.setCheckSensitiveTables(true);
        validator.setCheckDynamicWhereClause(true);
        validator.setCheckOrderByInjection(true);  // Enable ORDER BY injection check
    }

    @Test
    @DisplayName("Should detect SQL injection risk with ${}")
    void testDetectSqlInjection() throws Exception {
        String xml = "<select id=\"test\">" +
                "SELECT * FROM users WHERE name = '${userName}'" +
                "</select>";

        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.selectUsers");

        assertFalse(violations.isEmpty(), "Should detect SQL injection");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getRiskLevel() == RiskLevel.CRITICAL),
                "Should be CRITICAL risk");
        // Check for Chinese or English SQL injection message
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("SQL injection") || v.getMessage().contains("SQL 注入")),
                "Message should mention SQL injection");
    }

    @Test
    @DisplayName("Should not flag #{} as SQL injection")
    void testSafeParameterizedQuery() throws Exception {
        String xml = "<select id=\"test\">" +
                "SELECT * FROM users WHERE name = #{userName}" +
                "</select>";
        
        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.selectUsers");
        
        assertTrue(violations.stream()
                .noneMatch(v -> v.getMessage().contains("SQL injection") || v.getMessage().contains("SQL 注入")),
                "Should not flag #{} as SQL injection");
    }

    @Test
    @DisplayName("Should detect multiple ${} placeholders")
    void testMultipleSqlInjectionRisks() throws Exception {
        String xml = "<select id=\"test\">" +
                "SELECT * FROM users WHERE name = '${userName}' AND age = ${age}" +
                "</select>";

        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.selectUsers");

        long injectionCount = violations.stream()
                .filter(v -> v.getMessage().contains("SQL injection") || v.getMessage().contains("SQL 注入"))
                .count();

        assertEquals(2, injectionCount, "Should detect 2 SQL injection risks");
    }

    @Test
    @DisplayName("Should detect DELETE without WHERE")
    void testDeleteWithoutWhere() throws Exception {
        String xml = "<delete id=\"test\">" +
                "DELETE FROM users" +
                "</delete>";

        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.deleteAll");

        // Accept either English or Chinese message
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("DELETE without WHERE") ||
                              v.getMessage().contains("缺少 WHERE") ||
                              v.getMessage().contains("DELETE 语句")),
                "Should detect DELETE without WHERE");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getRiskLevel() == RiskLevel.HIGH || v.getRiskLevel() == RiskLevel.CRITICAL),
                "Should be HIGH or CRITICAL risk");
    }

    @Test
    @DisplayName("Should detect UPDATE without WHERE")
    void testUpdateWithoutWhere() throws Exception {
        String xml = "<update id=\"test\">" +
                "UPDATE users SET status = 1" +
                "</update>";

        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.updateAll");

        // Accept either English or Chinese message
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("UPDATE without WHERE") ||
                              v.getMessage().contains("缺少 WHERE") ||
                              v.getMessage().contains("UPDATE 语句")),
                "Should detect UPDATE without WHERE");
    }

    @Test
    @DisplayName("Should not flag DELETE with WHERE keyword")
    void testDeleteWithWhereKeyword() throws Exception {
        String xml = "<delete id=\"test\">" +
                "DELETE FROM users WHERE id = #{id}" +
                "</delete>";
        
        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.deleteById");
        
        assertTrue(violations.stream()
                .noneMatch(v -> v.getMessage().contains("DELETE without WHERE")),
                "Should not flag DELETE with WHERE");
    }

    @Test
    @DisplayName("Should not flag DELETE with <where> tag")
    void testDeleteWithWhereTag() throws Exception {
        String xml = "<delete id=\"test\">" +
                "DELETE FROM users" +
                "<where>" +
                "  <if test=\"id != null\">id = #{id}</if>" +
                "</where>" +
                "</delete>";
        
        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.deleteById");
        
        assertTrue(violations.stream()
                .noneMatch(v -> v.getMessage().contains("DELETE without WHERE")),
                "Should not flag DELETE with <where> tag");
    }

    @Test
    @DisplayName("Should detect SELECT *")
    void testSelectStar() throws Exception {
        String xml = "<select id=\"test\">" +
                "SELECT * FROM users" +
                "</select>";
        
        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.selectAll");
        
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("SELECT *")),
                "Should detect SELECT *");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getRiskLevel() == RiskLevel.MEDIUM),
                "Should be MEDIUM risk");
    }

    @Test
    @DisplayName("Should not flag explicit column selection")
    void testExplicitColumns() throws Exception {
        String xml = "<select id=\"test\">" +
                "SELECT id, name, email FROM users" +
                "</select>";
        
        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.selectUsers");
        
        assertTrue(violations.stream()
                .noneMatch(v -> v.getMessage().contains("SELECT *")),
                "Should not flag explicit column selection");
    }

    @Test
    @DisplayName("Should detect sensitive table access")
    void testSensitiveTableAccess() throws Exception {
        String xml = "<select id=\"test\">" +
                "SELECT * FROM user WHERE id = #{id}" +
                "</select>";

        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.selectUser");

        // Accept both "sensitive table" and "Accessing sensitive table" message patterns
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().toLowerCase().contains("sensitive table") ||
                              v.getMessage().contains("Accessing sensitive")),
                "Should detect sensitive table access");
    }

    @Test
    @DisplayName("Should detect SQL injection in dynamic tags")
    void testSqlInjectionInDynamicTags() throws Exception {
        String xml = "<select id=\"test\">" +
                "SELECT * FROM users" +
                "<if test=\"orderBy != null\">" +
                "  ORDER BY ${orderBy}" +
                "</if>" +
                "</select>";

        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.selectUsers");

        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("SQL injection") || v.getMessage().contains("SQL 注入")),
                "Should detect SQL injection in dynamic tags");
    }

    @Test
    @DisplayName("Should handle complex nested dynamic SQL")
    void testComplexDynamicSql() throws Exception {
        String xml = "<select id=\"test\">" +
                "SELECT * FROM users" +
                "<where>" +
                "  <if test=\"name != null\">name = #{name}</if>" +
                "  <if test=\"age != null\">AND age = #{age}</if>" +
                "</where>" +
                "</select>";

        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.selectUsers");

        // Should detect SELECT *, but not SQL injection (uses #{})
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("SELECT *")),
                "Should detect SELECT *");
        assertTrue(violations.stream()
                .noneMatch(v -> v.getMessage().contains("SQL injection") || v.getMessage().contains("SQL 注入")),
                "Should not flag #{} as SQL injection");
    }

    @Test
    @DisplayName("Should handle empty SQL element")
    void testEmptySqlElement() throws Exception {
        String xml = "<select id=\"test\"></select>";
        
        Element element = DocumentHelper.parseText(xml).getRootElement();
        List<ViolationInfo> violations = validator.validate(element, "test.empty");
        
        // Should not throw exception, just return empty or minimal violations
        assertNotNull(violations);
    }

    @Test
    @DisplayName("Should handle null element gracefully")
    void testNullElement() {
        List<ViolationInfo> violations = validator.validate(null, "test.null");
        
        assertNotNull(violations);
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("Should allow custom limiting field patterns")
    void testCustomLimitingFieldPatterns() throws Exception {
        // 配置自定义的限制性字段模式
        validator.setLimitingFieldPatterns(java.util.Arrays.asList(
            "order_no",      // 订单号
            "serial_number", // 序列号
            "[a-z]+_code"    // 以 _code 结尾的字段
        ));
        
        // 测试 order_no 字段
        String xml1 = "<select id=\"findByOrderNo\" resultType=\"Order\">" +
                "SELECT * FROM orders WHERE order_no = #{orderNo}" +
                "</select>";
        Element element1 = DocumentHelper.parseText(xml1).getRootElement();
        List<ViolationInfo> violations1 = validator.validate(element1, "test.findByOrderNo");
        
        // 不应该报告缺少物理分页
        assertFalse(violations1.stream()
                .anyMatch(v -> v.getMessage().contains("缺少物理分页")),
                "Should not report missing pagination for order_no");
        
        // 测试 product_code 字段（匹配 [a-z]+_code 模式）
        String xml2 = "<select id=\"findByProductCode\" resultType=\"Product\">" +
                "SELECT * FROM products WHERE product_code = #{productCode}" +
                "</select>";
        Element element2 = DocumentHelper.parseText(xml2).getRootElement();
        List<ViolationInfo> violations2 = validator.validate(element2, "test.findByProductCode");
        
        // 不应该报告缺少物理分页
        assertFalse(violations2.stream()
                .anyMatch(v -> v.getMessage().contains("缺少物理分页")),
                "Should not report missing pagination for product_code");
        
        // 测试不在配置中的字段（如 name）
        String xml3 = "<select id=\"findByName\" resultType=\"Product\">" +
                "SELECT * FROM products WHERE name = #{name}" +
                "</select>";
        Element element3 = DocumentHelper.parseText(xml3).getRootElement();
        List<ViolationInfo> violations3 = validator.validate(element3, "test.findByName");
        
        // 应该报告缺少物理分页
        assertTrue(violations3.stream()
                .anyMatch(v -> v.getMessage().contains("缺少物理分页")),
                "Should report missing pagination for name field");
    }

    @Test
    @DisplayName("Should use default patterns when not configured")
    void testDefaultLimitingFieldPatterns() throws Exception {
        // 使用默认配置
        
        // 测试默认的 id 字段
        String xml1 = "<select id=\"findById\" resultType=\"User\">" +
                "SELECT * FROM users WHERE id = #{id}" +
                "</select>";
        Element element1 = DocumentHelper.parseText(xml1).getRootElement();
        List<ViolationInfo> violations1 = validator.validate(element1, "test.findById");
        
        assertFalse(violations1.stream()
                .anyMatch(v -> v.getMessage().contains("缺少物理分页")),
                "Should not report missing pagination for id");
        
        // 测试默认的 user_id 字段
        String xml2 = "<select id=\"findByUserId\" resultType=\"Order\">" +
                "SELECT * FROM orders WHERE user_id = #{userId}" +
                "</select>";
        Element element2 = DocumentHelper.parseText(xml2).getRootElement();
        List<ViolationInfo> violations2 = validator.validate(element2, "test.findByUserId");
        
        assertFalse(violations2.stream()
                .anyMatch(v -> v.getMessage().contains("缺少物理分页")),
                "Should not report missing pagination for user_id");
        
        // 测试默认的 uuid 字段
        String xml3 = "<select id=\"findByUuid\" resultType=\"User\">" +
                "SELECT * FROM users WHERE uuid = #{uuid}" +
                "</select>";
        Element element3 = DocumentHelper.parseText(xml3).getRootElement();
        List<ViolationInfo> violations3 = validator.validate(element3, "test.findByUuid");
        
        assertFalse(violations3.stream()
                .anyMatch(v -> v.getMessage().contains("缺少物理分页")),
                "Should not report missing pagination for uuid");
    }

    @Test
    @DisplayName("Should reject null or empty patterns")
    void testInvalidPatterns() {
        assertThrows(IllegalArgumentException.class, 
            () -> validator.setLimitingFieldPatterns(null),
            "Should reject null patterns");
        
        assertThrows(IllegalArgumentException.class, 
            () -> validator.setLimitingFieldPatterns(java.util.Collections.emptyList()),
            "Should reject empty patterns");
    }
}


