package com.footstone.sqlguard.scanner;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.core.model.ViolationInfo;
import com.footstone.sqlguard.scanner.model.ScanContext;
import com.footstone.sqlguard.scanner.model.ScanReport;
import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.model.WrapperUsage;
import com.footstone.sqlguard.scanner.mybatis.LightweightSqlNodeBuilder;
import com.footstone.sqlguard.scanner.mybatis.MyBatisSemanticAnalysisService;
import com.footstone.sqlguard.scanner.mybatis.model.SecurityRisk;
import com.footstone.sqlguard.scanner.mybatis.util.SecurityRiskConverter;
import com.footstone.sqlguard.scanner.parser.SqlParser;
import com.footstone.sqlguard.scanner.parser.WrapperScanner;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import org.apache.ibatis.scripting.xmltags.SqlNode;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates static SQL scanning across multiple source types.
 *
 * <p>SqlScanner coordinates XML parsers, annotation parsers, and wrapper scanners
 * to produce a comprehensive scan report of all SQL usage in a project.</p>
 *
 * <p><strong>Scanning Process:</strong></p>
 * <ol>
 *   <li>Discover XML mapper files under src/main/resources</li>
 *   <li>Discover Java source files under src/main/java</li>
 *   <li>Parse XML files using xmlParser</li>
 *   <li>Parse Java files using annotationParser</li>
 *   <li>Scan project root using wrapperScanner</li>
 *   <li>Aggregate all results into ScanReport</li>
 *   <li>Calculate comprehensive statistics</li>
 * </ol>
 *
 * <p><strong>Error Handling:</strong> Individual file parse errors are logged
 * but do not fail the entire scan. The scanner continues processing remaining
 * files and returns partial results.</p>
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe if all injected
 * parsers are thread-safe.</p>
 *
 * @see ScanContext
 * @see ScanReport
 * @see SqlParser
 * @see WrapperScanner
 */
public class SqlScanner {

  private static final Logger logger = LoggerFactory.getLogger(SqlScanner.class);

  private final SqlParser xmlParser;
  private final SqlParser annotationParser;
  private final WrapperScanner wrapperScanner;
  private final DefaultSqlSafetyValidator validator;
  private final MyBatisSemanticAnalysisService semanticAnalysisService;

  /**
   * Constructs a SqlScanner with dependency injection (backward compatibility).
   *
   * @param xmlParser parser for XML mapper files
   * @param annotationParser parser for Java annotation mappers
   * @param wrapperScanner scanner for MyBatis-Plus wrapper usage
   * @deprecated Use {@link #SqlScanner(SqlParser, SqlParser, WrapperScanner, DefaultSqlSafetyValidator, MyBatisSemanticAnalysisService)}
   *             to enable SQL validation and semantic analysis
   */
  @Deprecated
  public SqlScanner(SqlParser xmlParser, SqlParser annotationParser, WrapperScanner wrapperScanner) {
    this(xmlParser, annotationParser, wrapperScanner, null, null);
  }

  /**
   * Constructs a SqlScanner with dependency injection and SQL validation.
   *
   * @param xmlParser parser for XML mapper files
   * @param annotationParser parser for Java annotation mappers
   * @param wrapperScanner scanner for MyBatis-Plus wrapper usage
   * @param validator SQL safety validator for security validation (may be null)
   * @deprecated Use {@link #SqlScanner(SqlParser, SqlParser, WrapperScanner, DefaultSqlSafetyValidator, MyBatisSemanticAnalysisService)}
   *             to enable semantic analysis
   */
  @Deprecated
  public SqlScanner(SqlParser xmlParser, SqlParser annotationParser, 
                    WrapperScanner wrapperScanner, DefaultSqlSafetyValidator validator) {
    this(xmlParser, annotationParser, wrapperScanner, validator, null);
  }

  /**
   * Constructs a SqlScanner with dependency injection, SQL validation, and semantic analysis.
   *
   * @param xmlParser parser for XML mapper files
   * @param annotationParser parser for Java annotation mappers
   * @param wrapperScanner scanner for MyBatis-Plus wrapper usage
   * @param validator SQL safety validator for security validation (may be null)
   * @param semanticAnalysisService MyBatis semantic analysis service (may be null)
   */
  public SqlScanner(SqlParser xmlParser, SqlParser annotationParser, 
                    WrapperScanner wrapperScanner, DefaultSqlSafetyValidator validator,
                    MyBatisSemanticAnalysisService semanticAnalysisService) {
    if (xmlParser == null) {
      throw new IllegalArgumentException("xmlParser cannot be null");
    }
    if (annotationParser == null) {
      throw new IllegalArgumentException("annotationParser cannot be null");
    }
    if (wrapperScanner == null) {
      throw new IllegalArgumentException("wrapperScanner cannot be null");
    }

    this.xmlParser = xmlParser;
    this.annotationParser = annotationParser;
    this.wrapperScanner = wrapperScanner;
    this.validator = validator;
    this.semanticAnalysisService = semanticAnalysisService;
  }

  /**
   * Scans a project for SQL usage and produces a comprehensive report.
   *
   * <p>This method orchestrates all parsers and scanners to analyze the project,
   * collecting SQL entries from XML mappers, Java annotations, and wrapper usage.</p>
   *
   * @param context the scan context containing project path and configuration
   * @return comprehensive scan report with all findings and statistics
   * @throws IllegalArgumentException if context is null
   */
  public ScanReport scan(ScanContext context) {
    if (context == null) {
      throw new IllegalArgumentException("context cannot be null");
    }

    logger.info("Starting SQL scan for project: {}", context.getProjectPath());

    ScanReport report = new ScanReport();

    // Discover and parse XML files
    try {
      List<File> xmlFiles = discoverXmlFiles(context.getProjectPath());
      logger.info("Found {} XML files", xmlFiles.size());

      for (File xmlFile : xmlFiles) {
        try {
          List<SqlEntry> entries = xmlParser.parse(xmlFile);
          entries.forEach(report::addEntry);
          logger.debug("Parsed {} entries from {}", entries.size(), xmlFile.getName());
        } catch (IOException | ParseException e) {
          logger.error("Failed to parse XML file {}: {}", xmlFile.getAbsolutePath(), e.getMessage());
          // Continue processing other files
        }
      }
    } catch (IOException e) {
      logger.error("Failed to discover XML files: {}", e.getMessage());
    }

    // Discover and parse Java files
    try {
      List<File> javaFiles = discoverJavaFiles(context.getProjectPath());
      logger.info("Found {} Java files", javaFiles.size());

      for (File javaFile : javaFiles) {
        try {
          List<SqlEntry> entries = annotationParser.parse(javaFile);
          entries.forEach(report::addEntry);
          logger.debug("Parsed {} entries from {}", entries.size(), javaFile.getName());
        } catch (IOException | ParseException e) {
          logger.error("Failed to parse Java file {}: {}", javaFile.getAbsolutePath(), e.getMessage());
          // Continue processing other files
        }
      }
    } catch (IOException e) {
      logger.error("Failed to discover Java files: {}", e.getMessage());
    }

    // Scan for wrapper usage
    try {
      List<WrapperUsage> wrapperUsages = wrapperScanner.scan(context.getProjectPath().toFile());
      wrapperUsages.forEach(report::addWrapperUsage);
      logger.info("Found {} wrapper usages", wrapperUsages.size());
    } catch (IOException e) {
      logger.error("Failed to scan for wrapper usage: {}", e.getMessage());
    }

    // Perform SQL safety validation if validator is available
    if (validator != null) {
      performValidation(report.getEntries());
    }

    // Perform semantic analysis if service is available
    if (semanticAnalysisService != null) {
      performSemanticAnalysis(report.getEntries(), context.getProjectPath());
    }

    // Calculate statistics
    report.calculateStatistics();

    logger.info("SQL scan completed. Total SQL entries: {}, Wrapper usages: {}",
        report.getEntries().size(), report.getWrapperUsages().size());

    return report;
  }

  /**
   * Performs SQL safety validation on all SQL entries using the configured validator.
   *
   * <p>This method creates a SqlContext for each entry and invokes the validator,
   * adding any detected violations to the corresponding SqlEntry.</p>
   *
   * @param entries the list of SQL entries to validate
   */
  private void performValidation(List<SqlEntry> entries) {
    if (entries == null || entries.isEmpty()) {
      return;
    }

    logger.info("Performing SQL safety validation for {} entries", entries.size());

    int totalViolationsFound = 0;
    for (SqlEntry entry : entries) {
      try {
        SqlContext context = SqlContext.builder()
            .sql(entry.getRawSql())
            .type(entry.getSqlType())
            .executionLayer(ExecutionLayer.MYBATIS)
            .statementId(entry.getStatementId())
            .build();

        ValidationResult result = validator.validate(context);
        if (!result.isPassed()) {
          for (ViolationInfo violation : result.getViolations()) {
            entry.addViolation(violation);
            totalViolationsFound++;
          }
        }
      } catch (Exception e) {
        logger.warn("Failed to validate SQL entry {}: {}", entry.getStatementId(), e.getMessage());
      }
    }

    logger.info("SQL safety validation completed. Total violations found: {}", totalViolationsFound);
  }

  /**
   * Performs semantic analysis on SQL entries using the configured service.
   *
   * <p>This method groups SQL entries by their XML file and performs batch analysis
   * for each mapper, combining information from XML and Java interface files.</p>
   *
   * @param entries the list of SQL entries to analyze
   * @param projectPath the project root path
   */
  private void performSemanticAnalysis(List<SqlEntry> entries, Path projectPath) {
    if (entries == null || entries.isEmpty()) {
      return;
    }

    logger.info("Performing semantic analysis for {} entries", entries.size());

    // Group entries by XML file
    Map<String, List<SqlEntry>> entriesByFile = entries.stream()
        .collect(Collectors.groupingBy(SqlEntry::getFilePath));

    int totalRisksFound = 0;

    for (Map.Entry<String, List<SqlEntry>> fileEntry : entriesByFile.entrySet()) {
      String xmlFilePath = fileEntry.getKey();
      List<SqlEntry> fileEntries = fileEntry.getValue();

      try {
        File xmlFile = new File(xmlFilePath);
        if (!xmlFile.exists()) {
          logger.warn("XML file not found: {}", xmlFilePath);
          continue;
        }

        // Find corresponding Java interface file
        File javaFile = findJavaInterfaceFile(fileEntries, projectPath);

        // Extract Java method signatures if Java file exists
        if (javaFile != null && javaFile.exists()) {
          try {
            String javaCode = new String(java.nio.file.Files.readAllBytes(javaFile.toPath()));
            com.footstone.sqlguard.scanner.mybatis.MapperInterfaceAnalyzer analyzer = 
                new com.footstone.sqlguard.scanner.mybatis.MapperInterfaceAnalyzer();
            com.footstone.sqlguard.scanner.mybatis.model.MapperInterfaceInfo interfaceInfo = 
                analyzer.analyze(javaCode);
            
            // Add Java method signatures to entries
            for (SqlEntry entry : fileEntries) {
              String methodId = entry.getStatementId().substring(entry.getStatementId().lastIndexOf('.') + 1);
              for (com.footstone.sqlguard.scanner.mybatis.model.MethodInfo method : interfaceInfo.getMethods()) {
                if (method.getName().equals(methodId)) {
                  // Build full signature with return type and parameters
                  String signature = method.getReturnType() + " " + method.getName() + "(" +
                      String.join(", ", method.getParameters().stream()
                          .map(p -> p.getType() + " " + p.getName())
                          .collect(java.util.stream.Collectors.toList())) + ")";
                  entry.setJavaMethodSignature(signature);
                  break;
                }
              }
            }
          } catch (Exception e) {
            logger.debug("Failed to extract Java method signatures: {}", e.getMessage());
          }
        }

        // Perform semantic analysis
        Map<String, List<SecurityRisk>> risks = semanticAnalysisService.analyzeMapper(
            xmlFile, javaFile, fileEntries);

        // Convert risks to violations and add to entries
        for (SqlEntry entry : fileEntries) {
          List<SecurityRisk> entryRisks = risks.get(entry.getStatementId());
          if (entryRisks != null && !entryRisks.isEmpty()) {
            for (SecurityRisk risk : entryRisks) {
              ViolationInfo violation = SecurityRiskConverter.toViolationInfo(risk, entry.getStatementId());
              entry.addViolation(violation);
              totalRisksFound++;
            }
          }
        }

        logger.debug("Semantic analysis completed for {}: {} risks found", 
            xmlFile.getName(), risks.values().stream().mapToInt(List::size).sum());

      } catch (Exception e) {
        logger.warn("Failed to perform semantic analysis for {}: {}", 
            xmlFilePath, e.getMessage());
        // Continue with other files
      }
    }

    logger.info("Semantic analysis completed: {} risks found", totalRisksFound);
  }

  /**
   * Finds the Java interface file corresponding to SQL entries.
   *
   * <p>This method extracts the namespace from the first entry's mapper ID and
   * converts it to a file path to locate the Java interface.</p>
   *
   * @param entries the SQL entries from a mapper
   * @param projectPath the project root path
   * @return the Java interface file, or null if not found
   */
  private File findJavaInterfaceFile(List<SqlEntry> entries, Path projectPath) {
    if (entries.isEmpty()) {
      return null;
    }

    // Extract namespace from mapper ID (e.g., "com.example.UserMapper.selectUsers" -> "com.example.UserMapper")
    String mapperId = entries.get(0).getStatementId();
    int lastDot = mapperId.lastIndexOf('.');
    if (lastDot < 0) {
      return null;
    }

    String namespace = mapperId.substring(0, lastDot);

    // Convert namespace to file path (e.g., "com.example.UserMapper" -> "com/example/UserMapper.java")
    String relativePath = namespace.replace('.', '/') + ".java";

    // Try to find in src/main/java
    Path javaPath = projectPath.resolve("src/main/java").resolve(relativePath);
    File javaFile = javaPath.toFile();

    if (javaFile.exists()) {
      logger.debug("Found Java interface: {}", javaFile.getAbsolutePath());
      return javaFile;
    }

    logger.debug("Java interface not found: {}", javaPath);
    return null;
  }



  /**
   * Discovers all XML files under src/main/resources.
   *
   * @param projectPath the project root path
   * @return list of XML files found
   * @throws IOException if directory traversal fails
   */
  private List<File> discoverXmlFiles(Path projectPath) throws IOException {
    Path resourcesDir = projectPath.resolve("src/main/resources");
    if (!Files.exists(resourcesDir)) {
      logger.warn("Resources directory not found: {}", resourcesDir);
      return java.util.Collections.emptyList();
    }

    return Files.walk(resourcesDir)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".xml"))
        .map(Path::toFile)
        .collect(Collectors.toList());
  }

  /**
   * Discovers all Java files under src/main/java.
   *
   * @param projectPath the project root path
   * @return list of Java files found
   * @throws IOException if directory traversal fails
   */
  private List<File> discoverJavaFiles(Path projectPath) throws IOException {
    Path javaDir = projectPath.resolve("src/main/java");
    if (!Files.exists(javaDir)) {
      logger.warn("Java source directory not found: {}", javaDir);
      return java.util.Collections.emptyList();
    }

    return Files.walk(javaDir)
        .filter(Files::isRegularFile)
        .filter(path -> path.toString().endsWith(".java"))
        .map(Path::toFile)
        .collect(Collectors.toList());
  }
}



