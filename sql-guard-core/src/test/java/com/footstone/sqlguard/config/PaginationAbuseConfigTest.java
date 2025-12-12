package com.footstone.sqlguard.config;

import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for PaginationAbuseConfig.
 */
public class PaginationAbuseConfigTest {

    @Test
    public void testDefaultConfiguration() {
        PaginationAbuseConfig config = new PaginationAbuseConfig();
        
        assertTrue(config.isEnabled(), "Should be enabled by default");
        assertEquals(RiskLevel.HIGH, config.getRiskLevel(), "Default risk level should be HIGH");
        assertNotNull(config.getLogicalPagination(), "LogicalPagination should not be null");
        assertNotNull(config.getPhysicalNoCondition(), "PhysicalNoCondition should not be null");
        assertNotNull(config.getPhysicalDeepPagination(), "PhysicalDeepPagination should not be null");
        assertNotNull(config.getLargePageSize(), "LargePageSize should not be null");
        assertNotNull(config.getNoOrderBy(), "NoOrderBy should not be null");
    }

    @Test
    public void testLogicalPaginationConfig() {
        PaginationAbuseConfig config = new PaginationAbuseConfig();
        PaginationAbuseConfig.LogicalPaginationConfig logical = config.getLogicalPagination();
        
        assertTrue(logical.isEnabled(), "LogicalPagination should be enabled by default");
        
        logical.setEnabled(false);
        assertFalse(logical.isEnabled(), "LogicalPagination should be disabled after setting");
    }

    @Test
    public void testPhysicalNoConditionConfig() {
        PaginationAbuseConfig config = new PaginationAbuseConfig();
        PaginationAbuseConfig.PhysicalNoConditionConfig physical = config.getPhysicalNoCondition();
        
        assertTrue(physical.isEnabled(), "PhysicalNoCondition should be enabled by default");
        
        physical.setEnabled(false);
        assertFalse(physical.isEnabled(), "PhysicalNoCondition should be disabled after setting");
    }

    @Test
    public void testPhysicalDeepPaginationConfig() {
        PaginationAbuseConfig config = new PaginationAbuseConfig();
        PaginationAbuseConfig.PhysicalDeepPaginationConfig deep = config.getPhysicalDeepPagination();
        
        assertTrue(deep.isEnabled(), "PhysicalDeepPagination should be enabled by default");
        assertEquals(10000, deep.getMaxOffset(), "Default maxOffset should be 10000");
        assertEquals(1000, deep.getMaxPageNum(), "Default maxPageNum should be 1000");
        
        deep.setMaxOffset(5000);
        assertEquals(5000, deep.getMaxOffset(), "MaxOffset should be 5000 after setting");
        
        deep.setMaxPageNum(500);
        assertEquals(500, deep.getMaxPageNum(), "MaxPageNum should be 500 after setting");
    }

    @Test
    public void testLargePageSizeConfig() {
        PaginationAbuseConfig config = new PaginationAbuseConfig();
        PaginationAbuseConfig.LargePageSizeConfig large = config.getLargePageSize();
        
        assertTrue(large.isEnabled(), "LargePageSize should be enabled by default");
        assertEquals(1000, large.getMaxPageSize(), "Default maxPageSize should be 1000");
        
        large.setMaxPageSize(500);
        assertEquals(500, large.getMaxPageSize(), "MaxPageSize should be 500 after setting");
    }

    @Test
    public void testNoOrderByConfig() {
        PaginationAbuseConfig config = new PaginationAbuseConfig();
        PaginationAbuseConfig.NoOrderByConfig noOrder = config.getNoOrderBy();
        
        assertTrue(noOrder.isEnabled(), "NoOrderBy should be enabled by default");
        
        noOrder.setEnabled(false);
        assertFalse(noOrder.isEnabled(), "NoOrderBy should be disabled after setting");
    }
}
