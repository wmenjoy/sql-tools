package com.footstone.sqlguard.scanner.mybatis;

import com.footstone.sqlguard.scanner.mybatis.model.*;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Combined analyzer that matches XML and Java interface information
 * 
 * Provides:
 * - Parameter type matching
 * - Parameter position detection
 * - Pagination detection
 */
public class CombinedAnalyzer {
    
    // Patterns to detect parameter positions
    private static final Pattern WHERE_PATTERN = Pattern.compile("WHERE.*?[#$]\\{(\\w+)\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("ORDER\\s+BY.*?[#$]\\{(\\w+)\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern LIMIT_PATTERN = Pattern.compile("LIMIT.*?[#$]\\{(\\w+)\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SET_PATTERN = Pattern.compile("SET.*?[#$]\\{(\\w+)\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    
    // Pattern to find all parameters
    private static final Pattern PARAM_PATTERN = Pattern.compile("[#$]\\{(\\w+)\\}");
    
    /**
     * Analyze a MappedStatement with Java interface information
     * 
     * @param mappedStatement MyBatis MappedStatement
     * @param interfaceInfo Mapper interface information
     * @return Combined analysis result
     */
    public CombinedAnalysisResult analyze(MappedStatement mappedStatement, MapperInterfaceInfo interfaceInfo) {
        return analyze(mappedStatement, interfaceInfo, null);
    }
    
    /**
     * Analyze a MappedStatement with Java interface information and raw SQL
     * 
     * @param mappedStatement MyBatis MappedStatement
     * @param interfaceInfo Mapper interface information
     * @param rawSql Raw SQL with #{} and ${} placeholders (optional)
     * @return Combined analysis result
     */
    public CombinedAnalysisResult analyze(MappedStatement mappedStatement, MapperInterfaceInfo interfaceInfo, String rawSql) {
        String statementId = mappedStatement.getId();
        
        // Extract method name from statement ID
        String methodName = extractMethodName(statementId);
        
        // Find matching method in interface
        MethodInfo methodInfo = interfaceInfo.getMethod(methodName);
        
        CombinedAnalysisResult result = new CombinedAnalysisResult(statementId, methodInfo);
        
        // Use provided raw SQL or try to extract it
        String sql = rawSql;
        if (sql == null || sql.isEmpty()) {
            SqlSource sqlSource = mappedStatement.getSqlSource();
            sql = extractRawSqlFromSource(sqlSource, mappedStatement);
        }
        
        // Save raw SQL in result
        result.setRawSql(sql);
        
        // Find all parameter usages
        extractParameterUsages(sql, result);
        
        return result;
    }
    
    /**
     * Extract method name from statement ID
     * Example: "com.example.mapper.UserMapper.selectAll" -> "selectAll"
     */
    private String extractMethodName(String statementId) {
        int lastDot = statementId.lastIndexOf('.');
        if (lastDot > 0) {
            return statementId.substring(lastDot + 1);
        }
        return statementId;
    }
    
    /**
     * Extract raw SQL from SqlSource
     * 
     * For static SQL, we can get it directly.
     * For dynamic SQL, we need to access the SqlNode tree via reflection.
     */
    private String extractRawSqlFromSource(SqlSource sqlSource, MappedStatement mappedStatement) {
        try {
            // Try to get the SQL command from the resource
            // This is a workaround to get the original SQL with #{} and ${}
            String resource = mappedStatement.getResource();
            if (resource != null && resource.contains(".xml")) {
                // For XML-based mappers, we need to parse the XML again
                // For now, use a simpler approach: get SQL from BoundSql and look for patterns
                return extractSqlWithPlaceholders(sqlSource);
            }
            
            return extractSqlWithPlaceholders(sqlSource);
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Extract SQL with placeholders by generating BoundSql with test parameters
     * and then reconstructing the original SQL pattern
     */
    private String extractSqlWithPlaceholders(SqlSource sqlSource) {
        try {
            // Create test parameters
            Map<String, Object> testParams = new HashMap<>();
            testParams.put("name", "TEST");
            testParams.put("age", 25);
            testParams.put("orderBy", "id");
            testParams.put("limit", 10);
            testParams.put("email", "test@example.com");
            
            BoundSql boundSql = sqlSource.getBoundSql(testParams);
            String sql = boundSql.getSql();
            
            // The BoundSql.getSql() returns SQL with ? placeholders
            // We need to map them back to parameter names
            // For now, return the SQL as-is and rely on the resource XML
            
            // Better approach: Access SqlNode directly via reflection
            return getRawSqlFromSqlNode(sqlSource);
            
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Get raw SQL from SqlNode via reflection
     */
    private String getRawSqlFromSqlNode(SqlSource sqlSource) {
        try {
            // For DynamicSqlSource, access rootSqlNode
            if (sqlSource.getClass().getSimpleName().equals("DynamicSqlSource")) {
                java.lang.reflect.Field field = sqlSource.getClass().getDeclaredField("rootSqlNode");
                field.setAccessible(true);
                Object sqlNode = field.get(sqlSource);
                
                // Apply SqlNode to get the SQL
                org.apache.ibatis.scripting.xmltags.DynamicContext context = 
                    new org.apache.ibatis.scripting.xmltags.DynamicContext(
                        new org.apache.ibatis.session.Configuration(), 
                        new HashMap<>()
                    );
                
                ((org.apache.ibatis.scripting.xmltags.SqlNode) sqlNode).apply(context);
                return context.getSql();
            }
            
            // For RawSqlSource or StaticSqlSource
            if (sqlSource.getClass().getSimpleName().equals("RawSqlSource")) {
                java.lang.reflect.Field field = sqlSource.getClass().getDeclaredField("sqlSource");
                field.setAccessible(true);
                Object staticSqlSource = field.get(sqlSource);
                return getRawSqlFromStaticSqlSource(staticSqlSource);
            }
            
            if (sqlSource.getClass().getSimpleName().equals("StaticSqlSource")) {
                return getRawSqlFromStaticSqlSource(sqlSource);
            }
            
        } catch (Exception e) {
            // Ignore
        }
        
        return "";
    }
    
    /**
     * Get raw SQL from StaticSqlSource
     */
    private String getRawSqlFromStaticSqlSource(Object staticSqlSource) {
        try {
            java.lang.reflect.Field field = staticSqlSource.getClass().getDeclaredField("sql");
            field.setAccessible(true);
            return (String) field.get(staticSqlSource);
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Extract parameter usages from SQL
     */
    private void extractParameterUsages(String sql, CombinedAnalysisResult result) {
        if (sql == null || sql.isEmpty()) {
            return;
        }
        
        // Find all parameters
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        while (matcher.find()) {
            String fullMatch = matcher.group(0);  // #{name} or ${name}
            String paramName = matcher.group(1);   // name
            
            boolean isDynamic = fullMatch.startsWith("${");
            SqlPosition position = detectPosition(sql, paramName, matcher.start());
            
            ParameterUsage usage = new ParameterUsage(paramName, position, isDynamic);
            result.addParameterUsage(usage);
        }
    }
    
    /**
     * Detect the position where a parameter is used
     * 
     * Strategy: Check keywords in reverse order (most specific first)
     */
    private SqlPosition detectPosition(String sql, String paramName, int paramIndex) {
        // Get the SQL before the parameter
        String sqlBefore = sql.substring(0, paramIndex).toUpperCase();
        
        // Find the last occurrence of each keyword
        int limitIndex = sqlBefore.lastIndexOf("LIMIT");
        int orderByIndex = sqlBefore.lastIndexOf("ORDER BY");
        int whereIndex = sqlBefore.lastIndexOf("WHERE");
        int setIndex = sqlBefore.lastIndexOf("SET");
        int selectIndex = sqlBefore.lastIndexOf("SELECT");
        int fromIndex = sqlBefore.lastIndexOf("FROM");
        
        // Check LIMIT (highest priority for this parameter)
        if (limitIndex > orderByIndex && limitIndex > whereIndex && limitIndex > setIndex) {
            return SqlPosition.LIMIT;
        }
        
        // Check ORDER BY
        if (orderByIndex > whereIndex && orderByIndex > setIndex && orderByIndex > fromIndex) {
            return SqlPosition.ORDER_BY;
        }
        
        // Check SET (UPDATE)
        if (setIndex > whereIndex && setIndex > fromIndex) {
            return SqlPosition.SET;
        }
        
        // Check WHERE
        if (whereIndex > setIndex && whereIndex > fromIndex && whereIndex > selectIndex) {
            return SqlPosition.WHERE;
        }
        
        // Check SELECT columns
        if (selectIndex > fromIndex && selectIndex >= 0) {
            return SqlPosition.SELECT;
        }
        
        // Check table name (FROM/JOIN)
        if (sqlBefore.matches(".*\\b(FROM|JOIN)\\s*$")) {
            return SqlPosition.TABLE_NAME;
        }
        
        return SqlPosition.OTHER;
    }
}

