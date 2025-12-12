package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for YamlConfigLoader.
 */
public class YamlConfigLoaderTest {

    private final YamlConfigLoader loader = new YamlConfigLoader();

    @Test
    public void testLoadValidCompleteConfig() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("valid-complete.yml");
        
        assertNotNull(config, "Config should not be null");
        assertTrue(config.isEnabled(), "Should be enabled");
        assertEquals("prod", config.getActiveStrategy(), "Strategy should be prod");
        
        // Verify interceptors
        assertTrue(config.getInterceptors().getMybatis().isEnabled());
        assertFalse(config.getInterceptors().getMybatisPlus().isEnabled());
        assertTrue(config.getInterceptors().getJdbc().isEnabled());
        assertEquals("auto", config.getInterceptors().getJdbc().getType());
        
        // Verify deduplication
        assertTrue(config.getDeduplication().isEnabled());
        assertEquals(1000, config.getDeduplication().getCacheSize());
        assertEquals(100L, config.getDeduplication().getTtlMs());
        
        // Verify rules
        assertTrue(config.getRules().getNoWhereClause().isEnabled());
        assertEquals(RiskLevel.CRITICAL, config.getRules().getNoWhereClause().getRiskLevel());
        
        assertTrue(config.getRules().getDummyCondition().isEnabled());
        assertEquals(RiskLevel.HIGH, config.getRules().getDummyCondition().getRiskLevel());
        assertEquals(2, config.getRules().getDummyCondition().getPatterns().size());
        assertEquals(1, config.getRules().getDummyCondition().getCustomPatterns().size());
        
        assertTrue(config.getRules().getBlacklistFields().isEnabled());
        assertEquals(3, config.getRules().getBlacklistFields().getFields().size());
        
        assertTrue(config.getRules().getWhitelistFields().isEnabled());
        assertEquals(2, config.getRules().getWhitelistFields().getFields().size());
        assertEquals(2, config.getRules().getWhitelistFields().getByTable().size());
        assertEquals(3, config.getRules().getWhitelistFields().getByTable().get("users").size());
        
        assertTrue(config.getRules().getPaginationAbuse().isEnabled());
        assertEquals(10000, config.getRules().getPaginationAbuse().getPhysicalDeepPagination().getMaxOffset());
        assertEquals(1000, config.getRules().getPaginationAbuse().getLargePageSize().getMaxPageSize());
        
        assertTrue(config.getRules().getNoPagination().isEnabled());
        assertEquals(1, config.getRules().getNoPagination().getWhitelistMapperIds().size());
        assertEquals(2, config.getRules().getNoPagination().getWhitelistTables().size());
        
        assertTrue(config.getRules().getEstimatedRows().isEnabled());
    }

    @Test
    public void testLoadValidPartialConfig() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("valid-partial.yml");
        
        assertNotNull(config, "Config should not be null");
        assertFalse(config.isEnabled(), "Should be disabled");
        assertEquals("dev", config.getActiveStrategy(), "Strategy should be dev");
        
        // Verify partial rules loaded
        assertFalse(config.getRules().getNoWhereClause().isEnabled());
        assertEquals(RiskLevel.MEDIUM, config.getRules().getDummyCondition().getRiskLevel());
        
        // Verify defaults for unspecified sections
        assertNotNull(config.getInterceptors());
        assertNotNull(config.getDeduplication());
    }

    @Test
    public void testLoadEmptyConfig() throws Exception {
        SqlGuardConfig config = loader.loadFromClasspath("empty.yml");
        
        assertNotNull(config, "Config should not be null even for empty file");
        assertTrue(config.isEnabled(), "Should use default enabled=true");
        assertEquals("prod", config.getActiveStrategy(), "Should use default strategy");
    }

    @Test
    public void testLoadInvalidSyntax() {
        ConfigLoadException exception = assertThrows(ConfigLoadException.class, () -> {
            loader.loadFromClasspath("invalid-syntax.yml");
        });
        
        assertTrue(exception.getMessage().contains("Failed to parse YAML"), 
                   "Exception should mention YAML parsing failure");
    }

    @Test
    public void testLoadInvalidTypes() {
        ConfigLoadException exception = assertThrows(ConfigLoadException.class, () -> {
            loader.loadFromClasspath("invalid-types.yml");
        });
        
        assertNotNull(exception, "Should throw ConfigLoadException for type mismatch");
    }

    @Test
    public void testLoadNonExistentClasspathResource() {
        ConfigLoadException exception = assertThrows(ConfigLoadException.class, () -> {
            loader.loadFromClasspath("non-existent.yml");
        });
        
        assertTrue(exception.getMessage().contains("not found in classpath"), 
                   "Exception should mention resource not found");
    }

    @Test
    public void testLoadNonExistentFile() {
        IOException exception = assertThrows(IOException.class, () -> {
            loader.loadFromFile(Paths.get("/non/existent/path/config.yml"));
        });
        
        assertTrue(exception.getMessage().contains("not found"), 
                   "Exception should mention file not found");
    }
}
