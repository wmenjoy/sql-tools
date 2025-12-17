package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.mybatis.model.*;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3: Combined XML + Java Analysis
 * 
 * Tests for CombinedAnalyzer that matches XML and Java information
 */
@DisplayName("Phase 3: Combined XML + Java Analysis")
class CombinedAnalyzerTest extends MyBatisSemanticAnalysisTestBase {
    
    private CombinedAnalyzer analyzer;
    private MapperInterfaceAnalyzer interfaceAnalyzer;
    
    @BeforeEach
    void setUp() {
        super.setUp();
        analyzer = new CombinedAnalyzer();
        interfaceAnalyzer = new MapperInterfaceAnalyzer();
    }
    
    @Test
    @DisplayName("3.1: Should match parameter types from Java interface")
    void testMatchParameterTypes() {
        // Given: XML with parameter usage
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users WHERE name = #{name} AND age = #{age}" +
            "  </select>" +
            "</mapper>";
        
        // Java interface
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"name\") String name, @Param(\"age\") Integer age);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult result = analyzer.analyze(ms, interfaceInfo, rawSql);
        
        // Then
        assertNotNull(result);
        assertEquals("com.example.mapper.UserMapper.search", result.getStatementId());
        assertNotNull(result.getMethodInfo());
        
        // Should have matched method info
        MethodInfo methodInfo = result.getMethodInfo();
        assertEquals("search", methodInfo.getName());
        assertEquals(2, methodInfo.getParameters().size());
        
        // Should have parameter usages
        List<ParameterUsage> usages = result.getParameterUsages();
        assertEquals(2, usages.size());
        
        // name parameter
        ParameterUsage nameUsage = findUsage(usages, "name");
        assertNotNull(nameUsage);
        assertEquals(SqlPosition.WHERE, nameUsage.getPosition());
        assertFalse(nameUsage.isDynamic(), "#{} should not be dynamic");
        assertTrue(nameUsage.isSafe(), "#{} should be safe");
        
        // age parameter
        ParameterUsage ageUsage = findUsage(usages, "age");
        assertNotNull(ageUsage);
        assertEquals(SqlPosition.WHERE, ageUsage.getPosition());
        assertFalse(ageUsage.isDynamic());
    }
    
    @Test
    @DisplayName("3.2: Should detect ${} as dynamic parameter")
    void testDetectDynamicParameter() {
        // Given: XML with ${} parameter
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
        CombinedAnalysisResult result = analyzer.analyze(ms, interfaceInfo, rawSql);
        
        // Then
        List<ParameterUsage> usages = result.getParameterUsages();
        assertEquals(1, usages.size());
        
        ParameterUsage usage = usages.get(0);
        assertEquals("orderBy", usage.getParameterName());
        assertEquals(SqlPosition.ORDER_BY, usage.getPosition());
        assertTrue(usage.isDynamic(), "${} should be dynamic");
        assertFalse(usage.isSafe(), "${} should be unsafe");
    }
    
    @Test
    @DisplayName("3.3: Should detect pagination from Java interface")
    void testDetectPaginationFromInterface() {
        // Given: Method with RowBounds
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='selectAll'>" +
            "    SELECT * FROM users" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.session.RowBounds;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> selectAll(RowBounds rowBounds);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult result = analyzer.analyze(ms, interfaceInfo, rawSql);
        
        // Then
        assertTrue(result.hasPagination(), "Should detect pagination from RowBounds");
        assertEquals(PaginationType.MYBATIS_ROWBOUNDS, result.getMethodInfo().getPaginationType());
    }
    
    @Test
    @DisplayName("3.4: Should detect MyBatis-Plus IPage pagination")
    void testDetectIPagePagination() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='selectPage'>" +
            "    SELECT * FROM users" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import com.baomidou.mybatisplus.core.metadata.IPage;\n" +
            "import com.baomidou.mybatisplus.extension.plugins.pagination.Page;\n" +
            "public interface UserMapper {\n" +
            "  IPage<User> selectPage(Page<?> page);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult result = analyzer.analyze(ms, interfaceInfo, rawSql);
        
        // Then
        assertTrue(result.hasPagination());
        assertEquals(PaginationType.MYBATIS_PLUS_IPAGE, result.getMethodInfo().getPaginationType());
    }
    
    @Test
    @DisplayName("3.5: Should handle mixed #{} and ${} parameters")
    void testMixedParameters() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users " +
            "    WHERE name = #{name} " +
            "    ORDER BY ${orderBy} " +
            "    LIMIT #{limit}" +
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
            "    @Param(\"limit\") Integer limit);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult result = analyzer.analyze(ms, interfaceInfo, rawSql);
        
        // Then
        List<ParameterUsage> usages = result.getParameterUsages();
        assertEquals(3, usages.size());
        
        // name: #{} in WHERE - safe
        ParameterUsage nameUsage = findUsage(usages, "name");
        assertFalse(nameUsage.isDynamic());
        assertEquals(SqlPosition.WHERE, nameUsage.getPosition());
        
        // orderBy: ${} in ORDER BY - dynamic
        ParameterUsage orderByUsage = findUsage(usages, "orderBy");
        assertTrue(orderByUsage.isDynamic());
        assertEquals(SqlPosition.ORDER_BY, orderByUsage.getPosition());
        
        // limit: #{} in LIMIT - safe
        ParameterUsage limitUsage = findUsage(usages, "limit");
        assertFalse(limitUsage.isDynamic());
        assertEquals(SqlPosition.LIMIT, limitUsage.getPosition());
    }
    
    @Test
    @DisplayName("3.6: Should handle method not found in interface")
    void testMethodNotFound() {
        // Given: XML with method that doesn't exist in interface
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='nonExistentMethod'>" +
            "    SELECT * FROM users" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> selectAll();\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult result = analyzer.analyze(ms, interfaceInfo, rawSql);
        
        // Then: Should still return result, but methodInfo might be null
        assertNotNull(result);
        // Method info should be null since method doesn't exist in interface
        assertNull(result.getMethodInfo());
    }
    
    @Test
    @DisplayName("3.7: Should extract parameter positions from dynamic SQL")
    void testDynamicSqlParameterPositions() {
        // Given: Dynamic SQL with <if> tags
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users" +
            "    <where>" +
            "      <if test='name != null'>name = #{name}</if>" +
            "      <if test='age != null'>AND age = #{age}</if>" +
            "    </where>" +
            "    <if test='orderBy != null'>ORDER BY ${orderBy}</if>" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(" +
            "    @Param(\"name\") String name, " +
            "    @Param(\"age\") Integer age, " +
            "    @Param(\"orderBy\") String orderBy);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult result = analyzer.analyze(ms, interfaceInfo, rawSql);
        
        // Then
        List<ParameterUsage> usages = result.getParameterUsages();
        assertTrue(usages.size() >= 2, "Should find at least name and age parameters");
        
        // name in WHERE
        ParameterUsage nameUsage = findUsage(usages, "name");
        assertNotNull(nameUsage);
        assertEquals(SqlPosition.WHERE, nameUsage.getPosition());
        
        // age in WHERE
        ParameterUsage ageUsage = findUsage(usages, "age");
        assertNotNull(ageUsage);
        assertEquals(SqlPosition.WHERE, ageUsage.getPosition());
    }
    
    // Helper method
    private ParameterUsage findUsage(List<ParameterUsage> usages, String paramName) {
        return usages.stream()
            .filter(u -> u.getParameterName().equals(paramName))
            .findFirst()
            .orElse(null);
    }
}

