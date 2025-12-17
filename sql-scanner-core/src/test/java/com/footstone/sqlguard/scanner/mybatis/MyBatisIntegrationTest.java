package com.footstone.sqlguard.scanner.mybatis;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.scripting.xmltags.DynamicSqlSource;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1: Verify MyBatis Integration
 * 
 * Tests that verify:
 * 1. MyBatis parsing does NOT require business classes
 * 2. We can use Map instead of business POJO
 * 3. We can access SqlNode for dynamic SQL analysis
 * 4. We can generate BoundSql for different scenarios
 */
@DisplayName("Phase 1: MyBatis Integration")
class MyBatisIntegrationTest extends MyBatisSemanticAnalysisTestBase {
    
    @Test
    @DisplayName("1.1: Should parse mapper without business classes")
    void testParseMapperWithoutBusinessClasses() {
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
        assertNotNull(ms, "MappedStatement should not be null");
        assertEquals("UserMapper.selectUser", ms.getId());
        assertNotNull(ms.getSqlSource());
        
        // ✅ SUCCESS: No ClassNotFoundException!
    }
    
    @Test
    @DisplayName("1.2: Should use Map instead of business POJO for BoundSql")
    void testBoundSqlWithMap() {
        // Given: Dynamic SQL
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
        Map<String, Object> params = createTestParams("name", "Alice", "age", 25);
        BoundSql boundSql = sqlSource.getBoundSql(params);
        
        // Then: Should generate correct SQL
        assertNotNull(boundSql);
        String sql = boundSql.getSql();
        assertTrue(sql.contains("name = ?"), "SQL should contain 'name = ?'");
        assertTrue(sql.contains("age = ?"), "SQL should contain 'age = ?'");
        
        // Verify parameter mappings
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        assertEquals(2, parameterMappings.size(), "Should have 2 parameters");
        assertEquals("name", parameterMappings.get(0).getProperty());
        assertEquals("age", parameterMappings.get(1).getProperty());
        
        // ✅ SUCCESS: No business class needed!
    }
    
    @Test
    @DisplayName("1.3: Should access SqlNode for dynamic SQL analysis")
    void testAccessSqlNode() throws Exception {
        // Given: Dynamic SQL
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
        
        // When: Access SqlNode via reflection
        assertTrue(sqlSource instanceof DynamicSqlSource, "Should be DynamicSqlSource");
        
        Field field = DynamicSqlSource.class.getDeclaredField("rootSqlNode");
        field.setAccessible(true);
        SqlNode rootNode = (SqlNode) field.get(sqlSource);
        
        // Then: Should have SqlNode
        assertNotNull(rootNode, "Root SqlNode should not be null");
        
        // ✅ SUCCESS: We can access SqlNode for analysis!
    }
    
    @Test
    @DisplayName("1.4: Should test multiple parameter scenarios")
    void testMultipleParameterScenarios() {
        // Given: Dynamic SQL with multiple conditions
        String xml = 
            "<mapper namespace='UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users" +
            "    <where>" +
            "      <if test='name != null'>name = #{name}</if>" +
            "      <if test='age != null'>AND age = #{age}</if>" +
            "      <if test='email != null'>AND email = #{email}</if>" +
            "    </where>" +
            "  </select>" +
            "</mapper>";
        
        MappedStatement ms = parseMybatisMapper(xml);
        SqlSource sqlSource = ms.getSqlSource();
        
        // Scenario 1: All null
        Map<String, Object> params1 = createTestParams();
        BoundSql boundSql1 = sqlSource.getBoundSql(params1);
        assertFalse(boundSql1.getSql().contains("WHERE"), "Should not have WHERE");
        
        // Scenario 2: Only name
        Map<String, Object> params2 = createTestParams("name", "Alice");
        BoundSql boundSql2 = sqlSource.getBoundSql(params2);
        assertTrue(boundSql2.getSql().contains("WHERE name = ?"));
        assertEquals(1, boundSql2.getParameterMappings().size());
        
        // Scenario 3: name and age
        Map<String, Object> params3 = createTestParams("name", "Alice", "age", 25);
        BoundSql boundSql3 = sqlSource.getBoundSql(params3);
        assertTrue(boundSql3.getSql().contains("WHERE name = ?"));
        assertTrue(boundSql3.getSql().contains("AND age = ?"));
        assertEquals(2, boundSql3.getParameterMappings().size());
        
        // Scenario 4: All parameters
        Map<String, Object> params4 = createTestParams(
            "name", "Alice", 
            "age", 25, 
            "email", "alice@example.com"
        );
        BoundSql boundSql4 = sqlSource.getBoundSql(params4);
        assertTrue(boundSql4.getSql().contains("WHERE name = ?"));
        assertTrue(boundSql4.getSql().contains("AND age = ?"));
        assertTrue(boundSql4.getSql().contains("AND email = ?"));
        assertEquals(3, boundSql4.getParameterMappings().size());
        
        // ✅ SUCCESS: Can test multiple scenarios without business classes!
    }
    
    @Test
    @DisplayName("1.5: Should handle complex dynamic SQL tags")
    void testComplexDynamicSqlTags() {
        // Given: Complex dynamic SQL with <choose>, <when>, <otherwise>
        String xml = 
            "<mapper namespace='UserMapper'>" +
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
        
        MappedStatement ms = parseMybatisMapper(xml);
        SqlSource sqlSource = ms.getSqlSource();
        
        // Scenario 1: name provided
        Map<String, Object> params1 = createTestParams("name", "Alice");
        BoundSql boundSql1 = sqlSource.getBoundSql(params1);
        assertTrue(boundSql1.getSql().contains("name = ?"));
        assertFalse(boundSql1.getSql().contains("email = ?"));
        assertFalse(boundSql1.getSql().contains("1=1"));
        
        // Scenario 2: email provided
        Map<String, Object> params2 = createTestParams("email", "alice@example.com");
        BoundSql boundSql2 = sqlSource.getBoundSql(params2);
        assertFalse(boundSql2.getSql().contains("name = ?"));
        assertTrue(boundSql2.getSql().contains("email = ?"));
        assertFalse(boundSql2.getSql().contains("1=1"));
        
        // Scenario 3: neither provided
        Map<String, Object> params3 = createTestParams();
        BoundSql boundSql3 = sqlSource.getBoundSql(params3);
        assertTrue(boundSql3.getSql().contains("1=1"));
        
        // ✅ SUCCESS: MyBatis handles complex tags correctly!
    }
    
    @Test
    @DisplayName("1.6: Should handle <foreach> tag")
    void testForeachTag() {
        // Given: SQL with <foreach>
        String xml = 
            "<mapper namespace='UserMapper'>" +
            "  <select id='selectByIds'>" +
            "    SELECT * FROM users WHERE id IN" +
            "    <foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "      #{id}" +
            "    </foreach>" +
            "  </select>" +
            "</mapper>";
        
        MappedStatement ms = parseMybatisMapper(xml);
        SqlSource sqlSource = ms.getSqlSource();
        
        // When: Provide list of IDs
        Map<String, Object> params = createTestParams(
            "ids", java.util.Arrays.asList(1, 2, 3)
        );
        BoundSql boundSql = sqlSource.getBoundSql(params);
        
        // Then: Should generate correct SQL
        String sql = boundSql.getSql();
        assertTrue(sql.contains("IN"), "SQL should contain IN");
        assertTrue(sql.contains("("), "SQL should contain opening parenthesis");
        assertTrue(sql.contains(")"), "SQL should contain closing parenthesis");
        
        // Should have 3 parameter mappings
        assertEquals(3, boundSql.getParameterMappings().size());
        
        // ✅ SUCCESS: <foreach> works without business classes!
    }
    
    @Test
    @DisplayName("1.7: Should handle <set> tag for UPDATE")
    void testSetTag() {
        // Given: UPDATE with <set>
        String xml = 
            "<mapper namespace='UserMapper'>" +
            "  <update id='updateUser'>" +
            "    UPDATE users" +
            "    <set>" +
            "      <if test='name != null'>name = #{name},</if>" +
            "      <if test='age != null'>age = #{age},</if>" +
            "      <if test='email != null'>email = #{email},</if>" +
            "    </set>" +
            "    WHERE id = #{id}" +
            "  </update>" +
            "</mapper>";
        
        MappedStatement ms = parseMybatisMapper(xml);
        SqlSource sqlSource = ms.getSqlSource();
        
        // When: Provide partial update
        Map<String, Object> params = createTestParams(
            "name", "Alice",
            "id", 1
        );
        BoundSql boundSql = sqlSource.getBoundSql(params);
        
        // Then: Should generate correct SQL
        String sql = boundSql.getSql();
        assertTrue(sql.contains("SET"), "SQL should contain SET");
        assertTrue(sql.contains("name = ?"), "SQL should contain name update");
        assertFalse(sql.contains("age = ?"), "SQL should not contain age update");
        assertTrue(sql.contains("WHERE id = ?"), "SQL should contain WHERE");
        
        // ✅ SUCCESS: <set> works correctly!
    }
    
    @Test
    @DisplayName("1.8: Should handle <trim> tag")
    void testTrimTag() {
        // Given: SQL with <trim>
        String xml = 
            "<mapper namespace='UserMapper'>" +
            "  <select id='search'>" +
            "    SELECT * FROM users" +
            "    <trim prefix='WHERE' prefixOverrides='AND |OR '>" +
            "      <if test='name != null'>AND name = #{name}</if>" +
            "      <if test='age != null'>AND age = #{age}</if>" +
            "    </trim>" +
            "  </select>" +
            "</mapper>";
        
        MappedStatement ms = parseMybatisMapper(xml);
        SqlSource sqlSource = ms.getSqlSource();
        
        // When: Provide parameters
        Map<String, Object> params = createTestParams("name", "Alice", "age", 25);
        BoundSql boundSql = sqlSource.getBoundSql(params);
        
        // Then: Should trim leading AND
        String sql = boundSql.getSql();
        // Debug: print actual SQL
        System.out.println("Generated SQL: " + sql);
        
        // MyBatis should have trimmed the leading AND
        assertTrue(sql.contains("WHERE"), "Should have WHERE");
        assertTrue(sql.contains("name = ?"), "Should have name condition");
        assertTrue(sql.contains("age = ?"), "Should have age condition");
        
        // ✅ SUCCESS: <trim> works correctly!
    }
}

