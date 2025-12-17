package com.footstone.sqlguard.scanner.mybatis;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.dom4j.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for MyBatisSemanticAnalysisTestBase
 * 
 * This test validates that our test framework can:
 * 1. Parse MyBatis Mapper XML without business classes
 * 2. Parse Java interfaces
 * 3. Create test parameters
 */
@DisplayName("MyBatis Semantic Analysis Test Framework")
class MyBatisSemanticAnalysisTestBaseTest extends MyBatisSemanticAnalysisTestBase {
    
    @Test
    @DisplayName("Should parse XML to DOM4J Element")
    void testParseXml() {
        // Given
        String xml = 
            "<mapper namespace='UserMapper'>" +
            "  <select id='selectUser'>" +
            "    SELECT * FROM users" +
            "  </select>" +
            "</mapper>";
        
        // When
        Element element = parseXml(xml);
        
        // Then
        assertNotNull(element);
        assertEquals("mapper", element.getName());
        assertEquals("UserMapper", element.attributeValue("namespace"));
    }
    
    @Test
    @DisplayName("Should parse MyBatis Mapper XML without business classes")
    void testParseMybatisMapper() {
        // Given: Mapper XML references non-existent business class
        String xml = 
            "<mapper namespace='UserMapper'>" +
            "  <select id='selectUser' resultType='com.example.NonExistentUser'>" +
            "    SELECT * FROM users WHERE id = #{id}" +
            "  </select>" +
            "</mapper>";
        
        // When: Parse without loading NonExistentUser class
        MappedStatement ms = parseMybatisMapper(xml);
        
        // Then: Should parse successfully
        assertNotNull(ms);
        assertEquals("UserMapper.selectUser", ms.getId());
        assertNotNull(ms.getSqlSource());
    }
    
    @Test
    @DisplayName("Should parse MyBatis Mapper with dynamic SQL")
    void testParseMybatisMapperWithDynamicSql() {
        // Given
        String xml = 
            "<mapper namespace='UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users" +
            "    <where>" +
            "      <if test='name != null'>name = #{name}</if>" +
            "      <if test='age != null'>AND age = #{age}</if>" +
            "    </where>" +
            "  </select>" +
            "</mapper>";
        
        // When
        MappedStatement ms = parseMybatisMapper(xml);
        
        // Then
        assertNotNull(ms);
        assertEquals("UserMapper.search", ms.getId());
        assertNotNull(ms.getSqlSource());
    }
    
    @Test
    @DisplayName("Should generate BoundSql using Map instead of business POJO")
    void testBoundSqlWithMap() {
        // Given
        String xml = 
            "<mapper namespace='UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users" +
            "    <where>" +
            "      <if test='name != null'>name = #{name}</if>" +
            "      <if test='age != null'>AND age = #{age}</if>" +
            "    </where>" +
            "  </select>" +
            "</mapper>";
        
        MappedStatement ms = parseMybatisMapper(xml);
        SqlSource sqlSource = ms.getSqlSource();
        
        // When: Generate BoundSql with Map (no business POJO needed)
        Map<String, Object> params = createTestParams(
            "name", "Alice",
            "age", 25
        );
        
        BoundSql boundSql = sqlSource.getBoundSql(params);
        
        // Then: Should generate correct SQL
        assertNotNull(boundSql);
        String sql = boundSql.getSql();
        assertNotNull(sql);
        assertTrue(sql.contains("name = ?"), "SQL should contain 'name = ?'");
        assertTrue(sql.contains("age = ?"), "SQL should contain 'age = ?'");
    }
    
    @Test
    @DisplayName("Should generate different SQL for different parameter scenarios")
    void testBoundSqlWithDifferentScenarios() {
        // Given
        String xml = 
            "<mapper namespace='UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users" +
            "    <where>" +
            "      <if test='name != null'>name = #{name}</if>" +
            "      <if test='age != null'>AND age = #{age}</if>" +
            "    </where>" +
            "  </select>" +
            "</mapper>";
        
        MappedStatement ms = parseMybatisMapper(xml);
        SqlSource sqlSource = ms.getSqlSource();
        
        // Scenario 1: All parameters null
        Map<String, Object> params1 = createTestParams();
        BoundSql boundSql1 = sqlSource.getBoundSql(params1);
        String sql1 = boundSql1.getSql();
        assertFalse(sql1.contains("WHERE"), "SQL should not contain WHERE when all conditions are null");
        
        // Scenario 2: Only name
        Map<String, Object> params2 = createTestParams("name", "Alice");
        BoundSql boundSql2 = sqlSource.getBoundSql(params2);
        String sql2 = boundSql2.getSql();
        assertTrue(sql2.contains("WHERE"), "SQL should contain WHERE");
        assertTrue(sql2.contains("name = ?"), "SQL should contain 'name = ?'");
        assertFalse(sql2.contains("age = ?"), "SQL should not contain 'age = ?'");
        
        // Scenario 3: Only age
        Map<String, Object> params3 = createTestParams("age", 25);
        BoundSql boundSql3 = sqlSource.getBoundSql(params3);
        String sql3 = boundSql3.getSql();
        assertTrue(sql3.contains("WHERE"), "SQL should contain WHERE");
        assertTrue(sql3.contains("age = ?"), "SQL should contain 'age = ?'");
        assertFalse(sql3.contains("name = ?"), "SQL should not contain 'name = ?'");
        
        // Scenario 4: Both parameters
        Map<String, Object> params4 = createTestParams("name", "Alice", "age", 25);
        BoundSql boundSql4 = sqlSource.getBoundSql(params4);
        String sql4 = boundSql4.getSql();
        assertTrue(sql4.contains("WHERE"), "SQL should contain WHERE");
        assertTrue(sql4.contains("name = ?"), "SQL should contain 'name = ?'");
        assertTrue(sql4.contains("age = ?"), "SQL should contain 'age = ?'");
    }
    
    @Test
    @DisplayName("Should parse Java interface")
    void testParseJavaInterface() {
        // Given
        String javaCode = 
            "package com.example;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(String name, Integer age);\n" +
            "}";
        
        // When
        CompilationUnit cu = parseJavaInterface(javaCode);
        
        // Then
        assertNotNull(cu);
        ClassOrInterfaceDeclaration interfaceDecl = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
        assertNotNull(interfaceDecl);
        assertTrue(interfaceDecl.isInterface());
        assertEquals("UserMapper", interfaceDecl.getNameAsString());
        
        MethodDeclaration method = interfaceDecl.findFirst(MethodDeclaration.class).orElse(null);
        assertNotNull(method);
        assertEquals("search", method.getNameAsString());
        assertEquals(2, method.getParameters().size());
    }
    
    @Test
    @DisplayName("Should create test parameters correctly")
    void testCreateTestParams() {
        // When
        Map<String, Object> params = createTestParams(
            "name", "Alice",
            "age", 25,
            "active", true
        );
        
        // Then
        assertEquals(3, params.size());
        assertEquals("Alice", params.get("name"));
        assertEquals(25, params.get("age"));
        assertEquals(true, params.get("active"));
    }
    
    @Test
    @DisplayName("Should throw exception for invalid keyValues")
    void testCreateTestParamsInvalid() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            createTestParams("name", "Alice", "age");
        });
    }
    
    @Test
    @DisplayName("Should register custom type alias")
    void testRegisterTypeAlias() {
        // Given
        String xml = 
            "<mapper namespace='OrderMapper'>" +
            "  <select id='selectOrder' resultType='CustomOrder'>" +
            "    SELECT * FROM orders WHERE id = #{id}" +
            "  </select>" +
            "</mapper>";
        
        // When: Register custom alias
        registerTypeAlias("CustomOrder", Object.class);
        
        // Then: Should parse successfully
        assertDoesNotThrow(() -> parseMybatisMapper(xml));
    }
}

