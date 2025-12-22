package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.mybatis.model.*;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6: Dynamic Condition Analysis
 * 
 * Tests for DynamicConditionAnalyzer - analyze dynamic SQL conditions:
 * - Detect conditions that might be always true
 * - Detect WHERE clause that might disappear
 * - Detect missing conditions
 */
@DisplayName("Phase 6: Dynamic Condition Analysis")
class DynamicConditionAnalyzerTest extends MyBatisSemanticAnalysisTestBase {
    
    private DynamicConditionAnalyzer conditionAnalyzer;
    private CombinedAnalyzer combinedAnalyzer;
    private MapperInterfaceAnalyzer interfaceAnalyzer;
    
    @BeforeEach
    void setUp() {
        super.setUp();
        conditionAnalyzer = new DynamicConditionAnalyzer();
        combinedAnalyzer = new CombinedAnalyzer();
        interfaceAnalyzer = new MapperInterfaceAnalyzer();
    }
    
    @Test
    @DisplayName("6.1: Should detect WHERE clause that might disappear")
    void testWhereClauseMightDisappear() {
        // Given: All conditions are optional
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users" +
            "    <where>" +
            "      <if test='name != null'>name = #{name}</if>" +
            "      <if test='age != null'>AND age = #{age}</if>" +
            "    </where>" +
            "  </select>" +
            "</mapper>";
        
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
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        DynamicConditionIssues issues = conditionAnalyzer.analyze(combined);
        
        // Then
        assertTrue(issues.hasIssues());
        assertTrue(issues.hasWhereClauseMightDisappear());
    }
    
    @Test
    @DisplayName("6.2: Should detect no issues when WHERE has mandatory condition")
    void testWhereWithMandatoryCondition() {
        // Given: At least one mandatory condition
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users" +
            "    <where>" +
            "      id = #{id}" +
            "      <if test='name != null'>AND name = #{name}</if>" +
            "    </where>" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"id\") Long id, @Param(\"name\") String name);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        DynamicConditionIssues issues = conditionAnalyzer.analyze(combined);
        
        // Then
        assertFalse(issues.hasWhereClauseMightDisappear());
    }
    
    @Test
    @DisplayName("6.3: Should detect condition that is always true")
    void testConditionAlwaysTrue() {
        // Given: Condition like "1=1" or similar
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users WHERE 1=1" +
            "    <if test='name != null'>AND name = #{name}</if>" +
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
        
        DynamicConditionIssues issues = conditionAnalyzer.analyze(combined);
        
        // Then
        assertTrue(issues.hasAlwaysTrueCondition());
    }
    
    @Test
    @DisplayName("6.4: Should detect SELECT without WHERE")
    void testSelectWithoutWhere() {
        // Given: Simple SELECT without WHERE
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
        
        DynamicConditionIssues issues = conditionAnalyzer.analyze(combined);
        
        // Then
        assertTrue(issues.hasNoWhereClause());
    }
    
    @Test
    @DisplayName("6.5: Should handle <choose> with no <otherwise>")
    void testChooseWithoutOtherwise() {
        // Given: <choose> without <otherwise>
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users" +
            "    <where>" +
            "      <choose>" +
            "        <when test='name != null'>name = #{name}</when>" +
            "        <when test='email != null'>email = #{email}</when>" +
            "      </choose>" +
            "    </where>" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"name\") String name, @Param(\"email\") String email);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        DynamicConditionIssues issues = conditionAnalyzer.analyze(combined);
        
        // Then
        assertTrue(issues.hasWhereClauseMightDisappear(), 
            "WHERE with <choose> and no <otherwise> might disappear");
    }
    
    @Test
    @DisplayName("6.6: Should handle <choose> with <otherwise>")
    void testChooseWithOtherwise() {
        // Given: <choose> with <otherwise>
        String xml = 
            "<mapper namespace='com.example.mapper.UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users" +
            "    <where>" +
            "      <choose>" +
            "        <when test='name != null'>name = #{name}</when>" +
            "        <when test='email != null'>email = #{email}</when>" +
            "        <otherwise>1=1</otherwise>" +
            "      </choose>" +
            "    </where>" +
            "  </select>" +
            "</mapper>";
        
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"name\") String name, @Param(\"email\") String email);\n" +
            "}";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        MapperInterfaceInfo interfaceInfo = interfaceAnalyzer.analyze(javaCode);
        String rawSql = getRawSql(ms.getId());
        CombinedAnalysisResult combined = combinedAnalyzer.analyze(ms, interfaceInfo, rawSql);
        
        DynamicConditionIssues issues = conditionAnalyzer.analyze(combined);
        
        // Then
        assertFalse(issues.hasWhereClauseMightDisappear(), 
            "WHERE with <choose> and <otherwise> should not disappear");
        assertTrue(issues.hasAlwaysTrueCondition(), 
            "<otherwise>1=1</otherwise> is always true");
    }
}










