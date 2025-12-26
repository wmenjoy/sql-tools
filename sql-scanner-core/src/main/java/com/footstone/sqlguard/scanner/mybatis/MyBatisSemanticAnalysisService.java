package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.model.SqlEntry;
import com.footstone.sqlguard.scanner.mybatis.config.MyBatisAnalysisConfig;
import com.footstone.sqlguard.scanner.mybatis.model.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Service for performing semantic analysis on MyBatis mappers.
 * 
 * <p>This service combines information from MyBatis XML files and Java interface files
 * to perform comprehensive security analysis, including:</p>
 * <ul>
 *   <li>Parameter risk analysis (type and position based)</li>
 *   <li>Pagination detection (MyBatis, PageHelper, MyBatis-Plus)</li>
 *   <li>Dynamic condition analysis (WHERE clause issues)</li>
 * </ul>
 * 
 * <p><strong>Error Handling:</strong> This service follows a fail-open strategy.
 * Errors during analysis are logged but do not prevent the analysis from continuing.</p>
 * 
 * @see MapperInterfaceAnalyzer
 * @see CombinedAnalyzer
 * @see ParameterRiskAnalyzer
 * @see PaginationDetector
 * @see DynamicConditionAnalyzer
 */
public class MyBatisSemanticAnalysisService {

    private static final Logger logger = LoggerFactory.getLogger(MyBatisSemanticAnalysisService.class);

    private final MapperInterfaceAnalyzer interfaceAnalyzer;
    private final CombinedAnalyzer combinedAnalyzer;
    private final ParameterRiskAnalyzer riskAnalyzer;
    private final PaginationDetector paginationDetector;
    private final DynamicConditionAnalyzer conditionAnalyzer;
    private final MyBatisAnalysisConfig config;

    /**
     * Constructs a new MyBatisSemanticAnalysisService with the given configuration.
     * 
     * @param config the analysis configuration (must not be null)
     * @throws IllegalArgumentException if config is null
     */
    public MyBatisSemanticAnalysisService(MyBatisAnalysisConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }

        this.config = config;
        this.interfaceAnalyzer = new MapperInterfaceAnalyzer();
        this.combinedAnalyzer = new CombinedAnalyzer();
        this.riskAnalyzer = new ParameterRiskAnalyzer();
        this.paginationDetector = new PaginationDetector();
        this.conditionAnalyzer = new DynamicConditionAnalyzer();
    }

    /**
     * Analyzes a MyBatis mapper and returns security risks for each SQL statement.
     * 
     * @param xmlFile the MyBatis XML mapper file (must not be null)
     * @param javaFile the corresponding Java interface file (may be null)
     * @param entries the SQL entries extracted from the XML file (must not be null)
     * @return map of mapper ID to list of security risks
     * @throws IllegalArgumentException if xmlFile or entries is null
     */
    public Map<String, List<SecurityRisk>> analyzeMapper(File xmlFile, File javaFile, List<SqlEntry> entries) {
        if (xmlFile == null) {
            throw new IllegalArgumentException("xmlFile cannot be null");
        }
        if (entries == null) {
            throw new IllegalArgumentException("entries cannot be null");
        }

        Map<String, List<SecurityRisk>> allRisks = new HashMap<>();

        if (entries.isEmpty()) {
            logger.debug("No SQL entries to analyze for {}", xmlFile.getName());
            return allRisks;
        }

        try {
            // Parse Java interface if available
            MapperInterfaceInfo interfaceInfo = null;
            if (javaFile != null && javaFile.exists()) {
                try {
                    String javaCode = new String(java.nio.file.Files.readAllBytes(javaFile.toPath()));
                    interfaceInfo = interfaceAnalyzer.analyze(javaCode);
                    logger.debug("Parsed Java interface: {}", javaFile.getName());
                } catch (Exception e) {
                    logger.warn("Failed to parse Java interface {}: {}", javaFile.getName(), e.getMessage());
                    // Continue with XML-only analysis
                }
            } else {
                logger.debug("No Java interface file provided for {}", xmlFile.getName());
            }

            // Parse MyBatis XML
            Configuration mybatisConfig = createMyBatisConfiguration();
            Map<String, MappedStatement> statements = parseMyBatisXml(xmlFile, mybatisConfig);

            // Analyze each SQL entry
            for (SqlEntry entry : entries) {
                try {
                    List<SecurityRisk> risks = analyzeStatement(entry, statements, interfaceInfo);
                    if (!risks.isEmpty()) {
                        allRisks.put(entry.getStatementId(), risks);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to analyze statement {}: {}", entry.getStatementId(), e.getMessage());
                    // Continue with next statement
                }
            }

        } catch (Exception e) {
            logger.error("Failed to analyze mapper {}: {}", xmlFile.getName(), e.getMessage());
            // Return partial results
        }

        return allRisks;
    }

    /**
     * Analyzes a single SQL statement.
     */
    private List<SecurityRisk> analyzeStatement(SqlEntry entry, Map<String, MappedStatement> statements,
                                                 MapperInterfaceInfo interfaceInfo) {
        List<SecurityRisk> risks = new ArrayList<>();

        String statementId = entry.getStatementId();
        MappedStatement statement = statements.get(statementId);

        if (statement == null) {
            logger.debug("No MappedStatement found for {}", statementId);
            return risks;
        }

        // Get method info from Java interface
        MethodInfo methodInfo = null;
        if (interfaceInfo != null) {
            String methodName = extractMethodName(statementId);
            methodInfo = interfaceInfo.getMethods().stream()
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElse(null);
        }

        // Perform combined analysis (XML + Java)
        CombinedAnalysisResult combinedResult = combinedAnalyzer.analyze(
            statement,
            interfaceInfo,
            entry.getRawSql()
        );

        // Parameter risk analysis
        List<SecurityRisk> paramRisks = riskAnalyzer.analyze(combinedResult);
        risks.addAll(paramRisks);

        // Pagination detection
        PaginationInfo paginationInfo = paginationDetector.detect(combinedResult);
        if (paginationInfo.shouldWarnMissingPagination()) {
            risks.add(new SecurityRisk(
                null,
                RiskLevel.HIGH,
                "SELECT statement without pagination may return too many rows",
                "Add LIMIT clause or use RowBounds/IPage parameter",
                SqlPosition.SELECT,
                null
            ));
        }
        if (paginationInfo.isExcessivePageSize()) {
            risks.add(new SecurityRisk(
                null,
                RiskLevel.MEDIUM,
                "Pagination limit exceeds recommended maximum (" + paginationInfo.getPageSize() + ")",
                "Reduce page size to 1000 or less",
                SqlPosition.LIMIT,
                null
            ));
        }

        // Dynamic condition analysis
        DynamicConditionIssues conditionIssues = conditionAnalyzer.analyze(combinedResult);
        if (conditionIssues.hasWhereClauseMightDisappear()) {
            risks.add(new SecurityRisk(
                null,
                RiskLevel.HIGH,
                "WHERE clause may disappear due to dynamic conditions",
                "Ensure at least one condition is always present or add default WHERE clause",
                SqlPosition.WHERE,
                null
            ));
        }
        if (conditionIssues.hasAlwaysTrueCondition()) {
            risks.add(new SecurityRisk(
                null,
                RiskLevel.MEDIUM,
                "Condition may always be true",
                "Review dynamic condition logic",
                SqlPosition.WHERE,
                null
            ));
        }

        return risks;
    }

    /**
     * Creates a MyBatis Configuration for parsing.
     */
    private Configuration createMyBatisConfiguration() {
        Configuration config = new Configuration();
        // Disable loading of actual classes
        config.setLazyLoadingEnabled(false);
        return config;
    }

    /**
     * Parses MyBatis XML file and returns MappedStatements.
     */
    private Map<String, MappedStatement> parseMyBatisXml(File xmlFile, Configuration config) throws IOException {
        Map<String, MappedStatement> statements = new HashMap<>();

        try (InputStream inputStream = new FileInputStream(xmlFile)) {
            XMLMapperBuilder builder = new XMLMapperBuilder(
                inputStream,
                config,
                xmlFile.getAbsolutePath(),
                config.getSqlFragments()
            );
            builder.parse();

            // Extract all mapped statements
            for (String id : config.getMappedStatementNames()) {
                MappedStatement statement = config.getMappedStatement(id);
                statements.put(id, statement);
            }
        }

        return statements;
    }

    /**
     * Extracts method name from mapper ID (namespace.methodName).
     */
    private String extractMethodName(String mapperId) {
        int lastDot = mapperId.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < mapperId.length() - 1) {
            return mapperId.substring(lastDot + 1);
        }
        return mapperId;
    }

}

