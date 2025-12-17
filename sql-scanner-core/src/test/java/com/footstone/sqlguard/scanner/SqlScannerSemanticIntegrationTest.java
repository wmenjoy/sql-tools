package com.footstone.sqlguard.scanner;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.scanner.model.ScanContext;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.mybatis.MyBatisSemanticAnalysisService;
import com.footstone.sqlguard.scanner.mybatis.config.MyBatisAnalysisConfig;
import com.footstone.sqlguard.scanner.parser.SqlParser;
import com.footstone.sqlguard.scanner.parser.WrapperScanner;
import com.footstone.sqlguard.scanner.parser.impl.XmlMapperParser;
import com.footstone.sqlguard.scanner.parser.impl.AnnotationParser;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SqlScanner with semantic analysis.
 */
public class SqlScannerSemanticIntegrationTest {

    @TempDir
    Path tempDir;

    private SqlScanner scanner;
    private MyBatisSemanticAnalysisService semanticService;

    @BeforeEach
    public void setUp() {
        // Create semantic analysis service
        MyBatisAnalysisConfig config = MyBatisAnalysisConfig.createDefault();
        semanticService = new MyBatisSemanticAnalysisService(config);

        // Create scanner with semantic analysis using REAL parsers
        SqlParser xmlParser = new XmlMapperParser();
        SqlParser annotationParser = new AnnotationParser();
        WrapperScanner wrapperScanner = new TestWrapperScanner();
        DefaultSqlSafetyValidator validator = null; // Not needed for this test

        scanner = new SqlScanner(xmlParser, annotationParser, wrapperScanner, validator, semanticService);
    }

    @Test
    public void testScanWithSemanticAnalysis() throws IOException {
        // Arrange
        Path projectPath = createTestProject();
        SqlGuardConfig config = new SqlGuardConfig();
        ScanContext context = new ScanContext(projectPath, config);

        // Act
        ScanReport report = scanner.scan(context);

        // Assert
        assertNotNull(report);
        assertFalse(report.getEntries().isEmpty());

        // Check that semantic analysis violations were added
        boolean hasSemanticViolations = report.getEntries().stream()
            .anyMatch(entry -> entry.hasViolations() && 
                entry.getViolations().stream()
                    .anyMatch(v -> v.getMessage().contains("Parameter")));

        assertTrue(hasSemanticViolations, "Should have semantic analysis violations");
    }

    @Test
    public void testScanWithoutSemanticAnalysis() throws IOException {
        // Arrange
        Path projectPath = createTestProject();
        SqlGuardConfig config = new SqlGuardConfig();
        ScanContext context = new ScanContext(projectPath, config);

        // Create scanner WITHOUT semantic analysis
        SqlParser xmlParser = new XmlMapperParser();
        SqlParser annotationParser = new AnnotationParser();
        WrapperScanner wrapperScanner = new TestWrapperScanner();
        SqlScanner scannerNoSemantic = new SqlScanner(xmlParser, annotationParser, wrapperScanner, null, null);

        // Act
        ScanReport report = scannerNoSemantic.scan(context);

        // Assert
        assertNotNull(report);
        // Should still work, just without semantic violations
    }

    @Test
    public void testSemanticAnalysisDetectsStringInOrderBy() throws IOException {
        // Arrange
        Path projectPath = createProjectWithOrderByRisk();
        SqlGuardConfig config = new SqlGuardConfig();
        ScanContext context = new ScanContext(projectPath, config);

        // Act
        ScanReport report = scanner.scan(context);

        // Assert
        assertNotNull(report);
        
        // Should detect String parameter in ORDER BY
        boolean hasOrderByRisk = report.getEntries().stream()
            .anyMatch(entry -> entry.hasViolations() && 
                entry.getViolations().stream()
                    .anyMatch(v -> v.getRiskLevel().compareTo(RiskLevel.HIGH) >= 0 &&
                        v.getMessage().toLowerCase().contains("order")));

        assertTrue(hasOrderByRisk, "Should detect ORDER BY risk");
    }

    @Test
    public void testSemanticAnalysisDetectsMissingPagination() throws IOException {
        // Arrange
        Path projectPath = createProjectWithoutPagination();
        SqlGuardConfig config = new SqlGuardConfig();
        ScanContext context = new ScanContext(projectPath, config);

        // Act
        ScanReport report = scanner.scan(context);

        // Assert
        assertNotNull(report);
        
        // Should detect missing pagination
        boolean hasPaginationRisk = report.getEntries().stream()
            .anyMatch(entry -> entry.hasViolations() && 
                entry.getViolations().stream()
                    .anyMatch(v -> v.getMessage().toLowerCase().contains("pagination")));

        assertTrue(hasPaginationRisk, "Should detect missing pagination");
    }

    // Helper methods

    private Path createTestProject() throws IOException {
        Path projectPath = tempDir.resolve("test-project");
        Files.createDirectories(projectPath);

        // Create src/main/resources
        Path resourcesPath = projectPath.resolve("src/main/resources/mappers");
        Files.createDirectories(resourcesPath);

        // Create src/main/java
        Path javaPath = projectPath.resolve("src/main/java/com/example");
        Files.createDirectories(javaPath);

        // Create a mapper XML with risky SQL
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" " +
            "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" +
            "<mapper namespace=\"com.example.UserMapper\">\n" +
            "  <select id=\"selectUsers\" resultType=\"map\">\n" +
            "    SELECT * FROM users ORDER BY ${sortColumn}\n" +
            "  </select>\n" +
            "</mapper>";
        Files.write(resourcesPath.resolve("UserMapper.xml"), xml.getBytes());

        // Create corresponding Java interface
        String java = "package com.example;\n" +
            "import java.util.List;\n" +
            "import java.util.Map;\n" +
            "public interface UserMapper {\n" +
            "    List<Map<String, Object>> selectUsers(String sortColumn);\n" +
            "}";
        Files.write(javaPath.resolve("UserMapper.java"), java.getBytes());

        return projectPath;
    }

    private Path createProjectWithOrderByRisk() throws IOException {
        return createTestProject(); // Same as test project
    }

    private Path createProjectWithoutPagination() throws IOException {
        Path projectPath = tempDir.resolve("test-project-no-pagination");
        Files.createDirectories(projectPath);

        Path resourcesPath = projectPath.resolve("src/main/resources/mappers");
        Files.createDirectories(resourcesPath);

        Path javaPath = projectPath.resolve("src/main/java/com/example");
        Files.createDirectories(javaPath);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" " +
            "\"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n" +
            "<mapper namespace=\"com.example.UserMapper\">\n" +
            "  <select id=\"selectAllUsers\" resultType=\"map\">\n" +
            "    SELECT * FROM users\n" +
            "  </select>\n" +
            "</mapper>";
        Files.write(resourcesPath.resolve("UserMapper.xml"), xml.getBytes());

        String java = "package com.example;\n" +
            "import java.util.List;\n" +
            "import java.util.Map;\n" +
            "public interface UserMapper {\n" +
            "    List<Map<String, Object>> selectAllUsers();\n" +
            "}";
        Files.write(javaPath.resolve("UserMapper.java"), java.getBytes());

        return projectPath;
    }

    // Test implementation for WrapperScanner

    private static class TestWrapperScanner implements WrapperScanner {
        @Override
        public List<com.footstone.sqlguard.scanner.model.WrapperUsage> scan(File projectRoot) throws IOException {
            return new ArrayList<>();
        }
    }
}

