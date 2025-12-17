package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.mybatis.model.MapperInterfaceInfo;
import com.footstone.sqlguard.scanner.mybatis.model.MethodInfo;
import com.footstone.sqlguard.scanner.mybatis.model.ParameterInfo;
import com.footstone.sqlguard.scanner.mybatis.model.PaginationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 2: Mapper Interface Analysis
 * 
 * Tests for MapperInterfaceAnalyzer using JavaParser
 */
@DisplayName("Phase 2: Mapper Interface Analysis")
class MapperInterfaceAnalyzerTest {
    
    private MapperInterfaceAnalyzer analyzer;
    
    @BeforeEach
    void setUp() {
        analyzer = new MapperInterfaceAnalyzer();
    }
    
    @Test
    @DisplayName("2.1: Should parse simple Mapper interface")
    void testParseSimpleInterface() {
        // Given
        String javaCode = 
            "package com.example.mapper;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> selectAll();\n" +
            "}";
        
        // When
        MapperInterfaceInfo info = analyzer.analyze(javaCode);
        
        // Then
        assertNotNull(info);
        assertEquals("com.example.mapper.UserMapper", info.getNamespace());
        assertTrue(info.hasMethod("selectAll"));
        
        MethodInfo method = info.getMethod("selectAll");
        assertNotNull(method);
        assertEquals("selectAll", method.getName());
        assertEquals("List", method.getReturnType());
        assertEquals(0, method.getParameters().size());
        assertFalse(method.hasPagination());
    }
    
    @Test
    @DisplayName("2.2: Should extract parameter types")
    void testExtractParameterTypes() {
        // Given
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> search(@Param(\"name\") String name, @Param(\"age\") Integer age);\n" +
            "}";
        
        // When
        MapperInterfaceInfo info = analyzer.analyze(javaCode);
        
        // Then
        MethodInfo method = info.getMethod("search");
        assertNotNull(method);
        assertEquals(2, method.getParameters().size());
        
        ParameterInfo nameParam = method.getParameter("name");
        assertNotNull(nameParam);
        assertEquals("name", nameParam.getName());
        assertEquals("String", nameParam.getType());
        assertEquals(0, nameParam.getIndex());
        assertTrue(nameParam.isString());
        
        ParameterInfo ageParam = method.getParameter("age");
        assertNotNull(ageParam);
        assertEquals("age", ageParam.getName());
        assertEquals("Integer", ageParam.getType());
        assertEquals(1, ageParam.getIndex());
        assertTrue(ageParam.isInteger());
    }
    
    @Test
    @DisplayName("2.3: Should detect MyBatis RowBounds pagination")
    void testDetectRowBoundsPagination() {
        // Given
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.session.RowBounds;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> selectAll(RowBounds rowBounds);\n" +
            "}";
        
        // When
        MapperInterfaceInfo info = analyzer.analyze(javaCode);
        
        // Then
        MethodInfo method = info.getMethod("selectAll");
        assertNotNull(method);
        assertTrue(method.hasPagination());
        assertEquals(PaginationType.MYBATIS_ROWBOUNDS, method.getPaginationType());
    }
    
    @Test
    @DisplayName("2.4: Should detect MyBatis-Plus IPage pagination")
    void testDetectIPagePagination() {
        // Given
        String javaCode = 
            "package com.example.mapper;\n" +
            "import com.baomidou.mybatisplus.core.metadata.IPage;\n" +
            "import com.baomidou.mybatisplus.extension.plugins.pagination.Page;\n" +
            "public interface UserMapper {\n" +
            "  IPage<User> selectPage(Page<?> page);\n" +
            "}";
        
        // When
        MapperInterfaceInfo info = analyzer.analyze(javaCode);
        
        // Then
        MethodInfo method = info.getMethod("selectPage");
        assertNotNull(method);
        assertTrue(method.hasPagination());
        assertEquals(PaginationType.MYBATIS_PLUS_IPAGE, method.getPaginationType());
    }
    
    @Test
    @DisplayName("2.5: Should detect MyBatis-Plus Page parameter")
    void testDetectPageParameter() {
        // Given
        String javaCode = 
            "package com.example.mapper;\n" +
            "import com.baomidou.mybatisplus.extension.plugins.pagination.Page;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> selectPage(Page<User> page);\n" +
            "}";
        
        // When
        MapperInterfaceInfo info = analyzer.analyze(javaCode);
        
        // Then
        MethodInfo method = info.getMethod("selectPage");
        assertNotNull(method);
        assertTrue(method.hasPagination());
        assertEquals(PaginationType.MYBATIS_PLUS_PAGE, method.getPaginationType());
    }
    
    @Test
    @DisplayName("2.6: Should handle multiple methods")
    void testMultipleMethods() {
        // Given
        String javaCode = 
            "package com.example.mapper;\n" +
            "import org.apache.ibatis.annotations.Param;\n" +
            "import org.apache.ibatis.session.RowBounds;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  List<User> selectAll();\n" +
            "  User selectById(@Param(\"id\") Long id);\n" +
            "  List<User> selectPage(RowBounds rowBounds);\n" +
            "}";
        
        // When
        MapperInterfaceInfo info = analyzer.analyze(javaCode);
        
        // Then
        assertEquals(3, info.getMethods().size());
        
        assertTrue(info.hasMethod("selectAll"));
        assertTrue(info.hasMethod("selectById"));
        assertTrue(info.hasMethod("selectPage"));
        
        // selectAll: no parameters, no pagination
        MethodInfo selectAll = info.getMethod("selectAll");
        assertEquals(0, selectAll.getParameters().size());
        assertFalse(selectAll.hasPagination());
        
        // selectById: one parameter, no pagination
        MethodInfo selectById = info.getMethod("selectById");
        assertEquals(1, selectById.getParameters().size());
        assertFalse(selectById.hasPagination());
        
        // selectPage: RowBounds pagination
        MethodInfo selectPage = info.getMethod("selectPage");
        assertTrue(selectPage.hasPagination());
        assertEquals(PaginationType.MYBATIS_ROWBOUNDS, selectPage.getPaginationType());
    }
    
    @Test
    @DisplayName("2.7: Should handle parameters without @Param annotation")
    void testParametersWithoutParamAnnotation() {
        // Given
        String javaCode = 
            "package com.example.mapper;\n" +
            "import java.util.List;\n" +
            "public interface UserMapper {\n" +
            "  User selectById(Long id);\n" +
            "}";
        
        // When
        MapperInterfaceInfo info = analyzer.analyze(javaCode);
        
        // Then
        MethodInfo method = info.getMethod("selectById");
        assertNotNull(method);
        assertEquals(1, method.getParameters().size());
        
        ParameterInfo param = method.getParameters().get(0);
        // Without @Param, parameter name might be "arg0" or "param1" in MyBatis
        // For now, we use the variable name from source code
        assertNotNull(param);
        assertEquals("Long", param.getType());
    }
    
    @Test
    @DisplayName("2.8: Should extract return type correctly")
    void testExtractReturnType() {
        // Given
        String javaCode = 
            "package com.example.mapper;\n" +
            "import java.util.List;\n" +
            "import java.util.Map;\n" +
            "public interface UserMapper {\n" +
            "  List<User> selectList();\n" +
            "  User selectOne();\n" +
            "  Map<String, Object> selectMap();\n" +
            "  int count();\n" +
            "  void delete(Long id);\n" +
            "}";
        
        // When
        MapperInterfaceInfo info = analyzer.analyze(javaCode);
        
        // Then
        assertEquals("List", info.getMethod("selectList").getReturnType());
        assertEquals("User", info.getMethod("selectOne").getReturnType());
        assertEquals("Map", info.getMethod("selectMap").getReturnType());
        assertEquals("int", info.getMethod("count").getReturnType());
        assertEquals("void", info.getMethod("delete").getReturnType());
    }
}

