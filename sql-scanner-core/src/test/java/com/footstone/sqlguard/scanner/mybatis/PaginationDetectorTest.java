package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.mybatis.model.*;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 5: Pagination Detection
 * 
 * Tests for PaginationDetector - comprehensive pagination detection:
 * - SQL LIMIT clause
 * - MyBatis RowBounds
 * - PageHelper
 * - MyBatis-Plus IPage
 * - MyBatis-Plus Page
 */
@DisplayName("Phase 5: Pagination Detection")
class PaginationDetectorTest extends MyBatisSemanticAnalysisTestBase {
    
    private PaginationDetector paginationDetector;
    private CombinedAnalyzer combinedAnalyzer;
    private MapperInterfaceAnalyzer interfaceAnalyzer;
    
    @BeforeEach
    void setUp() {
        super.setUp();
        paginationDetector = new PaginationDetector();
        combinedAnalyzer = new CombinedAnalyzer();
        interfaceAnalyzer = new MapperInterfaceAnalyzer();
    }
    
    @Test
    @DisplayName("5.1: Should detect SQL LIMIT clause")
    void testDetectSqlLimit() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='selectAll'>" +
            "    SELECT * FROM users LIMIT 10" +
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
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        PaginationInfo paginationInfo = paginationDetector.detect(combined);
        
        // Then
        assertTrue(paginationInfo.hasPagination());
        assertTrue(paginationInfo.getTypes().contains(PaginationType.SQL_LIMIT));
        assertEquals(10, paginationInfo.getPageSize());
    }
    
    @Test
    @DisplayName("5.2: Should detect MyBatis RowBounds")
    void testDetectRowBounds() {
        // Given
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
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        PaginationInfo paginationInfo = paginationDetector.detect(combined);
        
        // Then
        assertTrue(paginationInfo.hasPagination());
        assertTrue(paginationInfo.getTypes().contains(PaginationType.MYBATIS_ROWBOUNDS));
    }
    
    @Test
    @DisplayName("5.3: Should detect MyBatis-Plus IPage")
    void testDetectIPage() {
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
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        PaginationInfo paginationInfo = paginationDetector.detect(combined);
        
        // Then
        assertTrue(paginationInfo.hasPagination());
        assertTrue(paginationInfo.getTypes().contains(PaginationType.MYBATIS_PLUS_IPAGE));
    }
    
    @Test
    @DisplayName("5.4: Should detect MyBatis-Plus Page parameter")
    void testDetectPageParameter() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='selectPage'>" +
            "    SELECT * FROM users" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import com.baomidou.mybatisplus.extension.plugins.pagination.Page;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> selectPage(Page<User> page);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        PaginationInfo paginationInfo = paginationDetector.detect(combined);
        
        // Then
        assertTrue(paginationInfo.hasPagination());
        assertTrue(paginationInfo.getTypes().contains(PaginationType.MYBATIS_PLUS_PAGE));
    }
    
    @Test
    @DisplayName("5.5: Should detect no pagination")
    void testNoPagination() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='selectAll'>" +
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
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        PaginationInfo paginationInfo = paginationDetector.detect(combined);
        
        // Then
        assertFalse(paginationInfo.hasPagination());
        assertTrue(paginationInfo.getTypes().isEmpty());
    }
    
    @Test
    @DisplayName("5.6: Should detect excessive page size in LIMIT")
    void testExcessivePageSize() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='selectAll'>" +
            "    SELECT * FROM users LIMIT 10000" +
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
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        PaginationInfo paginationInfo = paginationDetector.detect(combined);
        
        // Then
        assertTrue(paginationInfo.hasPagination());
        assertEquals(10000, paginationInfo.getPageSize());
        assertTrue(paginationInfo.isExcessivePageSize(), "Page size 10000 should be excessive");
    }
    
    @Test
    @DisplayName("5.7: Should detect dynamic LIMIT with parameter")
    void testDynamicLimit() {
        // Given
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='selectAll'>" +
            "    SELECT * FROM users LIMIT #{pageSize}" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> selectAll(@Param(\"pageSize\") Integer pageSize);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        PaginationInfo paginationInfo = paginationDetector.detect(combined);
        
        // Then
        assertTrue(paginationInfo.hasPagination());
        assertTrue(paginationInfo.getTypes().contains(PaginationType.SQL_LIMIT));
        assertTrue(paginationInfo.isDynamic(), "LIMIT with parameter should be dynamic");
    }
    
    @Test
    @DisplayName("5.8: Should detect multiple pagination mechanisms")
    void testMultiplePaginationMechanisms() {
        // Given: Both LIMIT and RowBounds
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='selectAll'>" +
            "    SELECT * FROM users LIMIT 100" +
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
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        PaginationInfo paginationInfo = paginationDetector.detect(combined);
        
        // Then
        assertTrue(paginationInfo.hasPagination());
        assertEquals(2, paginationInfo.getTypes().size());
        assertTrue(paginationInfo.getTypes().contains(PaginationType.SQL_LIMIT));
        assertTrue(paginationInfo.getTypes().contains(PaginationType.MYBATIS_ROWBOUNDS));
    }
    
    @Test
    @DisplayName("5.9: Should warn about missing pagination for large table query")
    void testMissingPaginationWarning() {
        // Given: Query without pagination
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='selectAll'>" +
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
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        PaginationInfo paginationInfo = paginationDetector.detect(combined);
        
        // Then
        assertFalse(paginationInfo.hasPagination());
        // Should generate warning for SELECT without WHERE and without pagination
        assertTrue(paginationInfo.shouldWarnMissingPagination());
    }
}















