package com.footstone.sqlguard.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for SqlGuardConfig validation.
 */
public class SqlGuardConfigValidationTest {

    private final YamlConfigLoader loader = new YamlConfigLoader();

    @Test
    public void testValidConfigPassesValidation() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("valid-complete.yml");
        assertDoesNotThrow(() -> config.validate(), "Valid config should pass validation");
    }

    @Test
    public void testInvalidActiveStrategy() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("invalid-strategy.yml");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            config.validate();
        });
        
        assertTrue(exception.getMessage().contains("activeStrategy"), 
                   "Exception should mention activeStrategy");
        assertTrue(exception.getMessage().contains("dev, test, prod"), 
                   "Exception should list valid strategies");
    }

    @Test
    public void testInvalidCacheSize() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("invalid-dedup-cache.yml");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            config.validate();
        });
        
        assertTrue(exception.getMessage().contains("cacheSize"), 
                   "Exception should mention cacheSize");
        assertTrue(exception.getMessage().contains("> 0"), 
                   "Exception should indicate value must be > 0");
    }

    @Test
    public void testInvalidMaxOffset() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("invalid-values.yml");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            config.validate();
        });
        
        assertTrue(exception.getMessage().contains("maxOffset"), 
                   "Exception should mention maxOffset");
        assertTrue(exception.getMessage().contains("> 0"), 
                   "Exception should indicate value must be > 0");
    }

    @Test
    public void testInvalidMaxPageSize() {
        SqlGuardConfig config = new SqlGuardConfig();
        config.getRules().getPaginationAbuse().getLargePageSize().setMaxPageSize(-500);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            config.validate();
        });
        
        assertTrue(exception.getMessage().contains("maxPageSize"), 
                   "Exception should mention maxPageSize");
    }

    @Test
    public void testInvalidTtlMs() {
        SqlGuardConfig config = new SqlGuardConfig();
        config.getDeduplication().setTtlMs(-10L);
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            config.validate();
        });
        
        assertTrue(exception.getMessage().contains("ttlMs"), 
                   "Exception should mention ttlMs");
    }

    @Test
    public void testEmptyPatternsWhenEnabled() {
        SqlGuardConfig config = new SqlGuardConfig();
        config.getRules().getDummyCondition().setEnabled(true);
        config.getRules().getDummyCondition().getPatterns().clear();
        config.getRules().getDummyCondition().getCustomPatterns().clear();
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            config.validate();
        });
        
        assertTrue(exception.getMessage().contains("patterns"), 
                   "Exception should mention patterns");
    }

    @Test
    public void testEmptyPatternsWhenDisabled() {
        SqlGuardConfig config = new SqlGuardConfig();
        config.getRules().getDummyCondition().setEnabled(false);
        config.getRules().getDummyCondition().getPatterns().clear();
        config.getRules().getDummyCondition().getCustomPatterns().clear();
        
        // Should not throw exception when rule is disabled
        assertDoesNotThrow(() -> config.validate(), 
                           "Empty patterns should be allowed when rule is disabled");
    }

    @Test
    public void testDefaultConfigIsValid() {
        SqlGuardConfig config = new SqlGuardConfig();
        assertDoesNotThrow(() -> config.validate(), "Default config should be valid");
    }
}












