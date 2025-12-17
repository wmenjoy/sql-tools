package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.scanner.model.SourceType;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.mybatis.config.MyBatisAnalysisConfig;
import com.footstone.sqlguard.scanner.mybatis.model.RiskLevel;
import com.footstone.sqlguard.scanner.mybatis.model.SecurityRisk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MyBatisSemanticAnalysisService.
 */
public class MyBatisSemanticAnalysisServiceTest {

    @TempDir
    Path tempDir;

    private MyBatisSemanticAnalysisService service;
    private MyBatisAnalysisConfig config;

    @BeforeEach
    public void setUp() {
        config = MyBatisAnalysisConfig.createDefault();
        service = new MyBatisSemanticAnalysisService(config);
    }

    @Test
    public void testAnalyzeMapperWithMatchingJavaInterface() throws IOException {
        // Arrange
        File xmlFile = createTestXmlFile();
        File javaFile = createTestJavaFile();
        List<SqlEntry> entries = createTestSqlEntries(xmlFile.getAbsolutePath());

        // Act
        Map<String, List<SecurityRisk>> risks = service.analyzeMapper(xmlFile, javaFile, entries);

        // Assert
        assertNotNull(risks);
        // The simple test case may or may not have risks, so we just verify it doesn't crash
        // and returns a valid result
    }

    @Test
    public void testAnalyzeMapperWithoutJavaInterface() throws IOException {
        // Arrange
        File xmlFile = createTestXmlFile();
        List<SqlEntry> entries = createTestSqlEntries(xmlFile.getAbsolutePath());

        // Act - Java file is null
        Map<String, List<SecurityRisk>> risks = service.analyzeMapper(xmlFile, null, entries);

        // Assert - Should still work, but with limited analysis
        assertNotNull(risks);
        // May or may not have risks depending on XML-only analysis
    }

    @Test
    public void testAnalyzeMapperWithEmptyEntries() throws IOException {
        // Arrange
        File xmlFile = createTestXmlFile();
        File javaFile = createTestJavaFile();
        List<SqlEntry> entries = new ArrayList<>();

        // Act
        Map<String, List<SecurityRisk>> risks = service.analyzeMapper(xmlFile, javaFile, entries);

        // Assert
        assertNotNull(risks);
        assertTrue(risks.isEmpty());
    }

    @Test
    public void testAnalyzeMapperWithNullXmlFile() throws IOException {
        // Arrange
        File javaFile = createTestJavaFile();
        List<SqlEntry> entries = new ArrayList<>();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            service.analyzeMapper(null, javaFile, entries);
        });
    }

    @Test
    public void testAnalyzeMapperWithNullEntries() throws IOException {
        // Arrange
        File xmlFile = createTestXmlFile();
        File javaFile = createTestJavaFile();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            service.analyzeMapper(xmlFile, javaFile, null);
        });
    }

    @Test
    public void testDetectsStringInOrderBy() throws IOException {
        // Arrange
        File xmlFile = createXmlFileWithOrderBy();
        File javaFile = createJavaFileWithStringParameter();
        List<SqlEntry> entries = createSqlEntriesForOrderBy(xmlFile.getAbsolutePath());

        // Act
        Map<String, List<SecurityRisk>> risks = service.analyzeMapper(xmlFile, javaFile, entries);

        // Assert
        List<SecurityRisk> orderByRisks = risks.get("com.example.UserMapper.selectUsersOrderBy");
        assertNotNull(orderByRisks);
        assertFalse(orderByRisks.isEmpty());
        
        // Should detect String parameter in ORDER BY
        boolean hasOrderByRisk = orderByRisks.stream()
            .anyMatch(r -> r.getLevel().compareTo(RiskLevel.HIGH) >= 0);
        assertTrue(hasOrderByRisk, "Should detect high risk for String in ORDER BY");
    }

    @Test
    public void testDetectsMissingPagination() throws IOException {
        // Arrange
        File xmlFile = createXmlFileWithoutPagination();
        File javaFile = createJavaFileWithoutPagination();
        List<SqlEntry> entries = createSqlEntriesWithoutPagination(xmlFile.getAbsolutePath());

        // Act
        Map<String, List<SecurityRisk>> risks = service.analyzeMapper(xmlFile, javaFile, entries);

        // Assert
        List<SecurityRisk> paginationRisks = risks.get("com.example.UserMapper.selectAllUsers");
        assertNotNull(paginationRisks);
        
        // Should detect missing pagination
        boolean hasPaginationRisk = paginationRisks.stream()
            .anyMatch(r -> r.getMessage().toLowerCase().contains("pagination"));
        assertTrue(hasPaginationRisk, "Should detect missing pagination");
    }

    // Helper methods to create test files

    private File createTestXmlFile() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" " +
            "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" +
            "<mapper namespace=\"com.example.UserMapper\">\n" +
            "  <select id=\"selectUsers\" resultType=\"map\">\n" +
            "    SELECT * FROM users WHERE id = #{id}\n" +
            "  </select>\n" +
            "</mapper>";
        
        Path xmlPath = tempDir.resolve("UserMapper.xml");
        Files.write(xmlPath, xml.getBytes());
        return xmlPath.toFile();
    }

    private File createTestJavaFile() throws IOException {
        String java = "package com.example;\n" +
            "import java.util.List;\n" +
            "import java.util.Map;\n" +
            "public interface UserMapper {\n" +
            "    Map<String, Object> selectUsers(Long id);\n" +
            "}";
        
        Path javaPath = tempDir.resolve("UserMapper.java");
        Files.write(javaPath, java.getBytes());
        return javaPath.toFile();
    }

    private List<SqlEntry> createTestSqlEntries(String filePath) {
        List<SqlEntry> entries = new ArrayList<>();
        entries.add(new SqlEntry(
            SourceType.XML,
            filePath,
            "com.example.UserMapper.selectUsers",
            SqlCommandType.SELECT,
            "SELECT * FROM users WHERE id = #{id}",
            4
        ));
        return entries;
    }

    private File createXmlFileWithOrderBy() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" " +
            "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" +
            "<mapper namespace=\"com.example.UserMapper\">\n" +
            "  <select id=\"selectUsersOrderBy\" resultType=\"map\">\n" +
            "    SELECT * FROM users ORDER BY ${sortColumn}\n" +
            "  </select>\n" +
            "</mapper>";
        
        Path xmlPath = tempDir.resolve("UserMapperOrderBy.xml");
        Files.write(xmlPath, xml.getBytes());
        return xmlPath.toFile();
    }

    private File createJavaFileWithStringParameter() throws IOException {
        String java = "package com.example;\n" +
            "import java.util.List;\n" +
            "import java.util.Map;\n" +
            "public interface UserMapper {\n" +
            "    List<Map<String, Object>> selectUsersOrderBy(String sortColumn);\n" +
            "}";
        
        Path javaPath = tempDir.resolve("UserMapperOrderBy.java");
        Files.write(javaPath, java.getBytes());
        return javaPath.toFile();
    }

    private List<SqlEntry> createSqlEntriesForOrderBy(String filePath) {
        List<SqlEntry> entries = new ArrayList<>();
        entries.add(new SqlEntry(
            SourceType.XML,
            filePath,
            "com.example.UserMapper.selectUsersOrderBy",
            SqlCommandType.SELECT,
            "SELECT * FROM users ORDER BY ${sortColumn}",
            4
        ));
        return entries;
    }

    private File createXmlFileWithoutPagination() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" " +
            "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" +
            "<mapper namespace=\"com.example.UserMapper\">\n" +
            "  <select id=\"selectAllUsers\" resultType=\"map\">\n" +
            "    SELECT * FROM users\n" +
            "  </select>\n" +
            "</mapper>";
        
        Path xmlPath = tempDir.resolve("UserMapperNoPagination.xml");
        Files.write(xmlPath, xml.getBytes());
        return xmlPath.toFile();
    }

    private File createJavaFileWithoutPagination() throws IOException {
        String java = "package com.example;\n" +
            "import java.util.List;\n" +
            "import java.util.Map;\n" +
            "public interface UserMapper {\n" +
            "    List<Map<String, Object>> selectAllUsers();\n" +
            "}";
        
        Path javaPath = tempDir.resolve("UserMapperNoPagination.java");
        Files.write(javaPath, java.getBytes());
        return javaPath.toFile();
    }

    private List<SqlEntry> createSqlEntriesWithoutPagination(String filePath) {
        List<SqlEntry> entries = new ArrayList<>();
        entries.add(new SqlEntry(
            SourceType.XML,
            filePath,
            "com.example.UserMapper.selectAllUsers",
            SqlCommandType.SELECT,
            "SELECT * FROM users",
            4
        ));
        return entries;
    }
}

