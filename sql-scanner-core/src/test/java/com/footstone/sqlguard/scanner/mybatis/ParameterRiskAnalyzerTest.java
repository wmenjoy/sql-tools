package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.mybatis.model.*;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4: Parameter Risk Analysis
 * 
 * Tests for ParameterRiskAnalyzer - intelligent risk assessment based on:
 * - Parameter type (String, Integer, etc.)
 * - Parameter position (WHERE, ORDER BY, LIMIT, etc.)
 * - Parameter usage (#{} vs ${})
 */
@DisplayName("Phase 4: Parameter Risk Analysis")
class ParameterRiskAnalyzerTest extends MyBatisSemanticAnalysisTestBase {
    
    private ParameterRiskAnalyzer riskAnalyzer;
    private CombinedAnalyzer combinedAnalyzer;
    private MapperInterfaceAnalyzer interfaceAnalyzer;
    
    @BeforeEach
    void setUp() {
        super.setUp();
        riskAnalyzer = new ParameterRiskAnalyzer();
        combinedAnalyzer = new CombinedAnalyzer();
        interfaceAnalyzer = new MapperInterfaceAnalyzer();
    }
    
    @Test
    @DisplayName("4.1: String in ORDER BY with ${} should be HIGH risk")
    void testStringInOrderByDynamic() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users ORDER BY ${orderBy}" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"orderBy\") String orderBy);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        List<SecurityRisk> risks = riskAnalyzer.analyze(combined);
        
        // Then
        assertEquals(1, risks.size());
        SecurityRisk risk = risks.get(0);
        assertEquals(RiskLevel.HIGH, risk.getLevel());
        assertEquals("orderBy", risk.getParameterName());
        assertEquals(SqlPosition.ORDER_BY, risk.getPosition());
        assertEquals("String", risk.getParameterType());
        assertTrue(risk.getMessage().contains("ORDER BY") || risk.getMessage().contains("ORDER_BY"));
        assertTrue(risk.getRecommendation().toLowerCase().contains("whitelist") || 
                   risk.getRecommendation().contains("白名单") ||
                   risk.getRecommendation().toLowerCase().contains("validate"));
    }
    
    @Test
    @DisplayName("4.2: Integer in LIMIT with ${} should be LOW risk")
    void testIntegerInLimitDynamic() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users LIMIT ${pageSize}" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"pageSize\") Integer pageSize);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        List<SecurityRisk> risks = riskAnalyzer.analyze(combined);
        
        // Then
        assertEquals(1, risks.size());
        SecurityRisk risk = risks.get(0);
        assertEquals(RiskLevel.LOW, risk.getLevel());
        assertEquals("pageSize", risk.getParameterName());
        assertEquals("Integer", risk.getParameterType());
    }
    
    @Test
    @DisplayName("4.3: String in WHERE with ${} should be CRITICAL risk")
    void testStringInWhereDynamic() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users WHERE name = '${name}'" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"name\") String name);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        List<SecurityRisk> risks = riskAnalyzer.analyze(combined);
        
        // Then
        assertEquals(1, risks.size());
        SecurityRisk risk = risks.get(0);
        assertEquals(RiskLevel.CRITICAL, risk.getLevel());
        assertEquals("name", risk.getParameterName());
        assertEquals(SqlPosition.WHERE, risk.getPosition());
        assertTrue(risk.getMessage().contains("SQL injection"));
    }
    
    @Test
    @DisplayName("4.4: String in WHERE with #{} should be safe (no risk)")
    void testStringInWhereSafe() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users WHERE name = #{name}" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"name\") String name);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        List<SecurityRisk> risks = riskAnalyzer.analyze(combined);
        
        // Then
        assertEquals(0, risks.size(), "#{} should be safe, no risks");
    }
    
    @Test
    @DisplayName("4.5: Multiple parameters with different risk levels")
    void testMultipleParametersWithDifferentRisks() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users " +
            "    WHERE name = #{name} " +
            "    ORDER BY ${orderBy} " +
            "    LIMIT ${pageSize}" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(" +
            "    @Param(\"name\") String name, " +
            "    @Param(\"orderBy\") String orderBy, " +
            "    @Param(\"pageSize\") Integer pageSize);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        List<SecurityRisk> risks = riskAnalyzer.analyze(combined);
        
        // Then
        assertEquals(2, risks.size(), "Should have 2 risks (orderBy and pageSize)");
        
        // orderBy: HIGH risk
        SecurityRisk orderByRisk = findRisk(risks, "orderBy");
        assertNotNull(orderByRisk);
        assertEquals(RiskLevel.HIGH, orderByRisk.getLevel());
        
        // pageSize: LOW risk
        SecurityRisk pageSizeRisk = findRisk(risks, "pageSize");
        assertNotNull(pageSizeRisk);
        assertEquals(RiskLevel.LOW, pageSizeRisk.getLevel());
    }
    
    @Test
    @DisplayName("4.6: Integer in ORDER BY with ${} should be MEDIUM risk")
    void testIntegerInOrderByDynamic() {
        // Given: Integer in ORDER BY is less risky but still not recommended
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users ORDER BY ${columnIndex}" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"columnIndex\") Integer columnIndex);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        List<SecurityRisk> risks = riskAnalyzer.analyze(combined);
        
        // Then
        assertEquals(1, risks.size());
        SecurityRisk risk = risks.get(0);
        assertEquals(RiskLevel.MEDIUM, risk.getLevel());
        assertEquals("columnIndex", risk.getParameterName());
    }
    
    @Test
    @DisplayName("4.7: String in TABLE_NAME with ${} should be CRITICAL risk")
    void testStringInTableNameDynamic() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM ${tableName} WHERE id = #{id}" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"tableName\") String tableName, @Param(\"id\") Long id);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        List<SecurityRisk> risks = riskAnalyzer.analyze(combined);
        
        // Then
        assertTrue(risks.size() >= 1);
        SecurityRisk tableNameRisk = findRisk(risks, "tableName");
        assertNotNull(tableNameRisk);
        assertEquals(RiskLevel.CRITICAL, tableNameRisk.getLevel());
    }
    
    @Test
    @DisplayName("4.8: No Java interface should still analyze risks")
    void testNoJavaInterface() {
        // Given: XML only, no Java interface
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users ORDER BY ${orderBy}" +
            "  </select>" +
            "</mapper>";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo emptyInterface = new MapperInterfaceInfo("com.example.mapper.UserMapper");
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, emptyInterface, rawSql);
        
        List<SecurityRisk> risks = riskAnalyzer.analyze(combined);
        
        // Then: Should still detect ${} as risky
        assertEquals(1, risks.size());
        SecurityRisk risk = risks.get(0);
        assertEquals("orderBy", risk.getParameterName());
        // Without type info, assume String (worst case)
        assertTrue(risk.getLevel() == RiskLevel.HIGH || risk.getLevel() == RiskLevel.CRITICAL);
    }
    
    // Helper method
    private SecurityRisk findRisk(List<SecurityRisk> risks, String paramName) {
        return risks.stream()
            .filter(r -> r.getParameterName().equals(paramName))
            .findFirst()
            .orElse(null);
    }
}

