package com.footstone.sqlguard.spring.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import java.io.File;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for JSR-303 validation on SqlGuardProperties.
 */
@SpringBootTest(
    classes = ValidationTest.TestConfig.class,
    properties = {
        "sql-guard.enabled=true",
        "sql-guard.active-strategy=LOG"
    }
)
public class ValidationTest {

    @Autowired
    private SqlGuardProperties properties;

    @Autowired
    private Validator validator;

    @Test
    public void testValidation_withValidConfig_shouldPass() {
        Set<ConstraintViolation<SqlGuardProperties>> violations = validator.validate(properties);
        assertTrue(violations.isEmpty(), "Valid configuration should have no violations");
    }

    @Test
    public void testValidation_withInvalidActiveStrategy_shouldFail() {
        SqlGuardProperties testProps = new SqlGuardProperties();
        testProps.setActiveStrategy("INVALID");
        
        Set<ConstraintViolation<SqlGuardProperties>> violations = validator.validate(testProps);
        assertFalse(violations.isEmpty(), "Invalid activeStrategy should produce violations");
        
        boolean foundActiveStrategyViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().equals("activeStrategy"));
        assertTrue(foundActiveStrategyViolation, "Should have violation for activeStrategy");
    }

    @Test
    public void testValidation_withNegativeCacheSize_shouldFail() {
        SqlGuardProperties testProps = new SqlGuardProperties();
        testProps.getDeduplication().setCacheSize(-1);
        
        Set<ConstraintViolation<SqlGuardProperties>> violations = validator.validate(testProps);
        assertFalse(violations.isEmpty(), "Negative cacheSize should produce violations");
        
        boolean foundCacheSizeViolation = violations.stream()
            .anyMatch(v -> v.getPropertyPath().toString().contains("cacheSize"));
        assertTrue(foundCacheSizeViolation, "Should have violation for cacheSize");
    }

    @Test
    public void testValidation_withZeroCacheSize_shouldFail() {
        SqlGuardProperties testProps = new SqlGuardProperties();
        testProps.getDeduplication().setCacheSize(0);
        
        Set<ConstraintViolation<SqlGuardProperties>> violations = validator.validate(testProps);
        assertFalse(violations.isEmpty(), "Zero cacheSize should produce violations");
    }

    @Test
    public void testValidation_withExcessiveCacheSize_shouldFail() {
        SqlGuardProperties testProps = new SqlGuardProperties();
        testProps.getDeduplication().setCacheSize(100001);
        
        Set<ConstraintViolation<SqlGuardProperties>> violations = validator.validate(testProps);
        assertFalse(violations.isEmpty(), "Excessive cacheSize should produce violations");
    }

    @Test
    public void testValidation_withNegativeTtl_shouldFail() {
        SqlGuardProperties testProps = new SqlGuardProperties();
        testProps.getDeduplication().setTtlMs(-1);
        
        Set<ConstraintViolation<SqlGuardProperties>> violations = validator.validate(testProps);
        assertFalse(violations.isEmpty(), "Negative ttlMs should produce violations");
    }

    @Test
    public void testValidation_withExcessiveTtl_shouldFail() {
        SqlGuardProperties testProps = new SqlGuardProperties();
        testProps.getDeduplication().setTtlMs(60001);
        
        Set<ConstraintViolation<SqlGuardProperties>> violations = validator.validate(testProps);
        assertFalse(violations.isEmpty(), "Excessive ttlMs should produce violations");
    }

    @Test
    public void testValidation_withNegativeMaxOffset_shouldFail() {
        SqlGuardProperties testProps = new SqlGuardProperties();
        testProps.getRules().getDeepPagination().setMaxOffset(-1);
        
        Set<ConstraintViolation<SqlGuardProperties>> violations = validator.validate(testProps);
        assertFalse(violations.isEmpty(), "Negative maxOffset should produce violations");
    }

    @Test
    public void testValidation_withNegativeMaxPageSize_shouldFail() {
        SqlGuardProperties testProps = new SqlGuardProperties();
        testProps.getRules().getLargePageSize().setMaxPageSize(-1);
        
        Set<ConstraintViolation<SqlGuardProperties>> violations = validator.validate(testProps);
        assertFalse(violations.isEmpty(), "Negative maxPageSize should produce violations");
    }

    @Test
    public void testMetadataGeneration_shouldCreateMetadataFile() {
        // Check that spring-configuration-metadata.json exists in target/classes/META-INF
        File metadataFile = new File("target/classes/META-INF/spring-configuration-metadata.json");
        assertTrue(metadataFile.exists(), "Metadata file should be generated");
        assertTrue(metadataFile.length() > 0, "Metadata file should not be empty");
        
        // Check that additional metadata file exists
        File additionalMetadataFile = new File("target/classes/META-INF/additional-spring-configuration-metadata.json");
        assertTrue(additionalMetadataFile.exists(), "Additional metadata file should exist");
        assertTrue(additionalMetadataFile.length() > 0, "Additional metadata file should not be empty");
    }

    /**
     * Test configuration to enable ConfigurationProperties and Validator.
     */
    @org.springframework.context.annotation.Configuration
    @EnableConfigurationProperties(SqlGuardProperties.class)
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }
    }
}














