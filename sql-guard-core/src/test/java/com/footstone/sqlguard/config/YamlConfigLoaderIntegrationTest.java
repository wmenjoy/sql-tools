package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for YamlConfigLoader with comprehensive YAML file scenarios.
 * Tests all aspects of configuration loading, validation, and merging.
 */
public class YamlConfigLoaderIntegrationTest {

    private final YamlConfigLoader loader = new YamlConfigLoader();

    @Test
    public void testValidCompleteYaml() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("valid-complete.yml");
        
        // Verify successful load
        assertNotNull(config, "Config should load successfully");
        
        // Verify all sections present
        assertNotNull(config.getInterceptors());
        assertNotNull(config.getDeduplication());
        assertNotNull(config.getRules());
        
        // Verify specific values
        assertTrue(config.isEnabled());
        assertEquals("prod", config.getActiveStrategy());
        assertEquals(1000, config.getDeduplication().getCacheSize());
        assertEquals(RiskLevel.CRITICAL, config.getRules().getNoWhereClause().getRiskLevel());
        
        // Verify nested structures
        assertEquals(2, config.getRules().getWhitelistFields().getByTable().size());
        assertEquals(3, config.getRules().getWhitelistFields().getByTable().get("users").size());
        
        // Verify validation passes
        assertDoesNotThrow(() -> config.validate(), "Valid complete config should pass validation");
    }

    @Test
    public void testValidPartialYaml() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("valid-partial.yml");
        
        // Verify successful load
        assertNotNull(config, "Config should load successfully");
        
        // Verify user overrides
        assertFalse(config.isEnabled());
        assertEquals("dev", config.getActiveStrategy());
        assertFalse(config.getRules().getNoWhereClause().isEnabled());
        assertEquals(RiskLevel.MEDIUM, config.getRules().getDummyCondition().getRiskLevel());
        
        // Verify defaults inherited
        assertNotNull(config.getDeduplication());
        assertTrue(config.getDeduplication().isEnabled());
        
        // Verify validation passes
        assertDoesNotThrow(() -> config.validate(), "Valid partial config should pass validation");
    }

    @Test
    public void testInvalidSyntaxYaml() {
        ConfigLoadException exception = assertThrows(ConfigLoadException.class, () -> {
            loader.loadFromClasspath("invalid-syntax.yml");
        });
        
        assertNotNull(exception, "Should throw ConfigLoadException");
        assertTrue(exception.getMessage().contains("Failed to parse YAML"), 
                   "Exception should mention YAML parsing failure");
        assertNotNull(exception.getCause(), "Exception should have underlying cause");
    }

    @Test
    public void testInvalidValuesYaml() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("invalid-values.yml");
        
        // Config loads successfully
        assertNotNull(config, "Config should load");
        
        // But validation fails
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            config.validate();
        });
        
        assertTrue(exception.getMessage().contains("maxOffset"), 
                   "Validation should catch invalid maxOffset");
        assertTrue(exception.getMessage().contains("> 0"), 
                   "Validation should indicate constraint violation");
    }

    @Test
    public void testInvalidTypesYaml() {
        ConfigLoadException exception = assertThrows(ConfigLoadException.class, () -> {
            loader.loadFromClasspath("invalid-types.yml");
        });
        
        assertNotNull(exception, "Should throw ConfigLoadException for type mismatch");
        // Exception message should indicate the problem
        String message = exception.getMessage();
        assertTrue(message.contains("Type mismatch") || message.contains("parse") || 
                   message.contains("Cannot create"), 
                   "Exception should indicate type or parsing issue");
    }

    @Test
    public void testMissingRequiredYaml() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("missing-required.yml");
        
        // Config loads with defaults
        assertNotNull(config, "Config should load with defaults");
        assertTrue(config.isEnabled());
        
        // Missing sections should have defaults
        assertNotNull(config.getInterceptors());
        assertNotNull(config.getDeduplication());
        assertNotNull(config.getRules());
        
        // Verify validation passes with defaults
        assertDoesNotThrow(() -> config.validate(), 
                           "Config with defaults should pass validation");
    }

    @Test
    public void testEmptyYaml() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("empty.yml");
        
        // Empty YAML should result in complete default configuration
        assertNotNull(config, "Config should not be null");
        assertTrue(config.isEnabled(), "Should have default enabled=true");
        assertEquals("prod", config.getActiveStrategy(), "Should have default strategy");
        
        // All sections should have defaults
        assertNotNull(config.getInterceptors());
        assertNotNull(config.getDeduplication());
        assertNotNull(config.getRules());
        
        // Verify validation passes
        assertDoesNotThrow(() -> config.validate(), 
                           "Empty config with defaults should pass validation");
    }

    @Test
    public void testFailFastValidation() throws Exception {
        // Load config with invalid values
        SqlGuardConfig config = loader.loadFromClasspath("invalid-values.yml");
        
        // Validation should fail before any SQL validation occurs
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            config.validate();
        });
        
        assertNotNull(exception.getMessage(), "Exception should have descriptive message");
        assertTrue(exception.getMessage().length() > 10, 
                   "Exception message should be descriptive");
    }

    @Test
    public void testCorrectMergingWithDefaults() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("valid-partial.yml", true);
        
        // User values should override
        assertFalse(config.isEnabled(), "User value should override default");
        assertEquals("dev", config.getActiveStrategy(), "User value should override default");
        
        // Unspecified values should use defaults
        assertEquals(1000, config.getDeduplication().getCacheSize(), 
                     "Should inherit default cache size");
        assertEquals(100L, config.getDeduplication().getTtlMs(), 
                     "Should inherit default TTL");
    }

    @Test
    public void testAllConfigScenariosPass() throws Exception {
        // Test that all valid configurations pass validation
        String[] validConfigs = {"valid-complete.yml", "valid-partial.yml", "empty.yml", "missing-required.yml"};
        
        for (String configFile : validConfigs) {
            SqlGuardConfig config = loader.loadFromClasspath(configFile);
            assertDoesNotThrow(() -> config.validate(), 
                               "Config " + configFile + " should pass validation");
        }
    }

    @Test
    public void testAllInvalidConfigsFail() {
        // Test that all invalid configurations are properly detected
        String[] invalidConfigs = {"invalid-syntax.yml", "invalid-types.yml"};
        
        for (String configFile : invalidConfigs) {
            assertThrows(ConfigLoadException.class, () -> {
                loader.loadFromClasspath(configFile);
            }, "Config " + configFile + " should fail to load");
        }
    }

    @Test
    public void testConstraintViolationsDetected() throws Exception {
        // Test that constraint violations are detected during validation
        String[] constraintViolations = {"invalid-values.yml", "invalid-strategy.yml", "invalid-dedup-cache.yml"};
        
        for (String configFile : constraintViolations) {
            SqlGuardConfig config = loader.loadFromClasspath(configFile);
            assertThrows(IllegalArgumentException.class, () -> {
                config.validate();
            }, "Config " + configFile + " should fail validation");
        }
    }
}
