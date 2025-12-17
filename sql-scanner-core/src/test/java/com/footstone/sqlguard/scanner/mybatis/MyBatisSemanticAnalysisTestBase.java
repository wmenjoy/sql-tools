package com.footstone.sqlguard.scanner.mybatis;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.BeforeEach;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for MyBatis semantic analysis tests
 * 
 * Provides utility methods for:
 * - Parsing MyBatis Mapper XML (without business classes)
 * - Parsing Java Mapper interfaces
 * - Creating test parameters
 */
public abstract class MyBatisSemanticAnalysisTestBase {
    
    protected Configuration configuration;
    private Map<String, String> rawSqlCache = new HashMap<>();  // Cache for raw SQL
    
    @BeforeEach
    void setUp() {
        configuration = new Configuration();
        
        // Pre-register common type aliases to avoid TypeAlias resolution failures
        TypeAliasRegistry registry = configuration.getTypeAliasRegistry();
        
        // Java built-in types
        registry.registerAlias("string", String.class);
        registry.registerAlias("int", Integer.class);
        registry.registerAlias("integer", Integer.class);
        registry.registerAlias("long", Long.class);
        registry.registerAlias("short", Short.class);
        registry.registerAlias("byte", Byte.class);
        registry.registerAlias("float", Float.class);
        registry.registerAlias("double", Double.class);
        registry.registerAlias("boolean", Boolean.class);
        registry.registerAlias("date", java.util.Date.class);
        
        // Collection types
        registry.registerAlias("map", Map.class);
        registry.registerAlias("hashmap", HashMap.class);
        registry.registerAlias("list", java.util.List.class);
        registry.registerAlias("arraylist", java.util.ArrayList.class);
        
        // For unknown business classes, use Object.class as placeholder
        // This allows parsing without loading actual business classes
        registry.registerAlias("User", Object.class);
        registry.registerAlias("Order", Object.class);
        registry.registerAlias("Product", Object.class);
        registry.registerAlias("Customer", Object.class);
    }
    
    /**
     * Parse XML string to DOM4J Element
     * 
     * @param xml XML string
     * @return DOM4J Element
     */
    protected Element parseXml(String xml) {
        try {
            SAXReader reader = new SAXReader();
            Document document = reader.read(new StringReader(xml));
            return document.getRootElement();
        } catch (DocumentException e) {
            throw new RuntimeException("Failed to parse XML", e);
        }
    }
    
    /**
     * Parse MyBatis Mapper XML using MyBatis XMLMapperBuilder
     * 
     * ✅ Does NOT require business classes to be loaded
     * 
     * @param xml Mapper XML string
     * @return MappedStatement
     */
    protected MappedStatement parseMybatisMapper(String xml) {
        try {
            // Pre-register any resultType classes found in XML as Object.class
            preRegisterResultTypes(xml);
            
            // Extract and cache raw SQL before MyBatis processes it
            cacheRawSql(xml);
            
            // Wrap in DOCTYPE if not present
            if (!xml.contains("<!DOCTYPE")) {
                xml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                      "<!DOCTYPE mapper PUBLIC '-//mybatis.org//DTD Mapper 3.0//EN' " +
                      "'http://mybatis.org/dtd/mybatis-3-mapper.dtd'>\n" +
                      xml;
            }
            
            InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
            XMLMapperBuilder builder = new XMLMapperBuilder(
                is, 
                configuration, 
                "test.xml", 
                configuration.getSqlFragments()
            );
            
            builder.parse();
            
            // Return the first MappedStatement
            if (configuration.getMappedStatementNames().isEmpty()) {
                throw new RuntimeException("No MappedStatement found in XML");
            }
            
            String statementId = configuration.getMappedStatementNames().iterator().next();
            return configuration.getMappedStatement(statementId);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse MyBatis mapper: " + e.getMessage(), e);
        }
    }
    
    /**
     * Cache raw SQL from XML before MyBatis processes it
     */
    private void cacheRawSql(String xml) {
        try {
            Element root = parseXml(xml);
            String namespace = root.attributeValue("namespace");
            
            // Find all SQL elements
            for (Object obj : root.elements()) {
                Element element = (Element) obj;
                String elementName = element.getName();
                
                if (elementName.equals("select") || elementName.equals("insert") || 
                    elementName.equals("update") || elementName.equals("delete")) {
                    String id = element.attributeValue("id");
                    String statementId = namespace + "." + id;
                    
                    // Extract raw SQL text (including dynamic tags)
                    String rawSql = element.asXML();
                    rawSqlCache.put(statementId, rawSql);
                }
            }
        } catch (Exception e) {
            // Ignore errors in caching
        }
    }
    
    /**
     * Get cached raw SQL for a statement
     */
    protected String getRawSql(String statementId) {
        return rawSqlCache.get(statementId);
    }
    
    /**
     * Pre-register any resultType classes found in XML as Object.class
     * This allows parsing without loading actual business classes
     * 
     * @param xml Mapper XML string
     */
    private void preRegisterResultTypes(String xml) {
        // Extract resultType values using regex
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("resultType=['\"]([^'\"]+)['\"]");
        java.util.regex.Matcher matcher = pattern.matcher(xml);
        
        while (matcher.find()) {
            String resultType = matcher.group(1);
            // Register as Object.class if not already registered
            try {
                configuration.getTypeAliasRegistry().resolveAlias(resultType);
            } catch (Exception e) {
                // Not registered, register as Object.class
                registerTypeAlias(resultType, Object.class);
            }
        }
    }
    
    /**
     * Parse Java interface using JavaParser
     * 
     * @param javaCode Java interface code
     * @return CompilationUnit
     */
    protected CompilationUnit parseJavaInterface(String javaCode) {
        try {
            return StaticJavaParser.parse(javaCode);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Java interface", e);
        }
    }
    
    /**
     * Create test parameters as Map (no need for business POJO)
     * 
     * Usage: createTestParams("name", "Alice", "age", 25)
     * 
     * @param keyValues alternating key-value pairs
     * @return parameter map
     */
    protected Map<String, Object> createTestParams(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must have even number of elements");
        }
        
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put((String) keyValues[i], keyValues[i + 1]);
        }
        return params;
    }
    
    /**
     * Register a custom type alias for testing
     * 
     * @param alias alias name
     * @param type Java class (use Object.class for non-existent classes)
     */
    protected void registerTypeAlias(String alias, Class<?> type) {
        configuration.getTypeAliasRegistry().registerAlias(alias, type);
    }
}

