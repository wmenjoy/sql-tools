package com.footstone.sqlguard.scanner.mybatis;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Extracts SQL statements from MyBatis mapper XML files using MyBatis native parser.
 *
 * <p>This extractor uses MyBatis's own XML parsing and SQL building capabilities
 * to handle dynamic SQL correctly, including:</p>
 * <ul>
 *   <li>Dynamic tags: {@code <if>}, {@code <choose>}, {@code <foreach>}, etc.</li>
 *   <li>Parameter placeholders: {@code #{}} and {@code ${}}</li>
 *   <li>SQL fragments: {@code <include>}</li>
 * </ul>
 *
 * @author SQL Guard Team
 * @since 1.0.0
 */
public class MybatisSqlExtractor {

  private static final Logger logger = LoggerFactory.getLogger(MybatisSqlExtractor.class);

  /**
   * Extracts SQL statements from a MyBatis mapper XML file.
   *
   * @param mapperFile the mapper XML file
   * @return list of extracted SQL information
   */
  public List<ExtractedSql> extractSql(File mapperFile) {
    List<ExtractedSql> results = new ArrayList<>();

    try {
      // Create a minimal MyBatis Configuration
      Configuration configuration = new Configuration();
      configuration.setMapUnderscoreToCamelCase(true);

      // Parse the mapper XML file
      try (InputStream inputStream = new FileInputStream(mapperFile)) {
        XMLMapperBuilder builder = new XMLMapperBuilder(
            inputStream,
            configuration,
            mapperFile.getAbsolutePath(),
            configuration.getSqlFragments()
        );
        builder.parse();
      }

      // Extract SQL from all mapped statements
      Collection<MappedStatement> statements = configuration.getMappedStatements();
      for (MappedStatement statement : statements) {
        try {
          ExtractedSql extractedSql = extractFromStatement(statement, mapperFile);
          if (extractedSql != null) {
            results.add(extractedSql);
          }
        } catch (Exception e) {
          logger.warn("Failed to extract SQL from statement {}: {}", 
              statement.getId(), e.getMessage());
        }
      }

      logger.debug("Extracted {} SQL statements from {}", results.size(), mapperFile.getName());

    } catch (Exception e) {
      logger.error("Failed to parse MyBatis mapper file {}: {}", 
          mapperFile.getAbsolutePath(), e.getMessage(), e);
    }

    return results;
  }

  /**
   * Extracts SQL from a MappedStatement.
   *
   * @param statement the mapped statement
   * @param mapperFile the source mapper file
   * @return extracted SQL information, or null if extraction failed
   */
  private ExtractedSql extractFromStatement(MappedStatement statement, File mapperFile) {
    try {
      SqlSource sqlSource = statement.getSqlSource();
      
      // Get BoundSql with empty parameters to get the SQL template
      // For dynamic SQL, this will generate a version with all dynamic parts included
      BoundSql boundSql = sqlSource.getBoundSql(new HashMap<>());
      String sql = boundSql.getSql();

      // Check if SQL contains ${} placeholders (SQL injection risk)
      boolean hasSqlInjectionRisk = sql.contains("${");

      // Get parameter mappings to understand #{} placeholders
      List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

      return new ExtractedSql(
          statement.getId(),
          sql,
          statement.getSqlCommandType().name(),
          mapperFile.getAbsolutePath(),
          hasSqlInjectionRisk,
          parameterMappings.size()
      );

    } catch (Exception e) {
      logger.debug("Failed to extract SQL from statement {}: {}", 
          statement.getId(), e.getMessage());
      return null;
    }
  }

  /**
   * Represents an extracted SQL statement with metadata.
   */
  public static class ExtractedSql {
    private final String statementId;
    private final String sql;
    private final String sqlType;
    private final String filePath;
    private final boolean hasSqlInjectionRisk;
    private final int parameterCount;

    public ExtractedSql(String statementId, String sql, String sqlType, 
                       String filePath, boolean hasSqlInjectionRisk, int parameterCount) {
      this.statementId = statementId;
      this.sql = sql;
      this.sqlType = sqlType;
      this.filePath = filePath;
      this.hasSqlInjectionRisk = hasSqlInjectionRisk;
      this.parameterCount = parameterCount;
    }

    public String getStatementId() {
      return statementId;
    }

    public String getSql() {
      return sql;
    }

    public String getSqlType() {
      return sqlType;
    }

    public String getFilePath() {
      return filePath;
    }

    public boolean hasSqlInjectionRisk() {
      return hasSqlInjectionRisk;
    }

    public int getParameterCount() {
      return parameterCount;
    }

    @Override
    public String toString() {
      return "ExtractedSql{" +
          "statementId='" + statementId + '\'' +
          ", sqlType='" + sqlType + '\'' +
          ", hasSqlInjectionRisk=" + hasSqlInjectionRisk +
          ", parameterCount=" + parameterCount +
          '}';
    }
  }
}



