package com.footstone.sqlguard.parser;

import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for LRU cache functionality.
 */
class CacheTest {

    private JSqlParserFacade facade;

    @BeforeEach
    void setUp() {
        facade = new JSqlParserFacade(false, 10); // Small cache for testing
    }

    @Test
    void testCacheHitReturnsSameInstance() {
        String sql = "SELECT * FROM users WHERE id = 1";
        
        Statement first = facade.parseCached(sql);
        Statement second = facade.parseCached(sql);
        
        assertNotNull(first, "First parse should not be null");
        assertNotNull(second, "Second parse should not be null");
        assertSame(first, second, "Cache hit should return same Statement instance");
    }

    @Test
    void testCacheMissCallsParseAndCaches() {
        String sql1 = "SELECT * FROM users";
        String sql2 = "SELECT * FROM orders";
        
        Statement stmt1 = facade.parseCached(sql1);
        Statement stmt2 = facade.parseCached(sql2);
        
        assertNotNull(stmt1, "First statement should not be null");
        assertNotNull(stmt2, "Second statement should not be null");
        
        CacheStats stats = facade.getCacheStatistics();
        assertEquals(2, stats.getMissCount(), "Should have 2 cache misses");
        assertEquals(0, stats.getHitCount(), "Should have 0 cache hits");
        assertEquals(2, stats.getSize(), "Cache should contain 2 entries");
    }

    @Test
    void testCacheSizeLimitEnforced() {
        // Fill cache beyond limit (cache size is 10)
        for (int i = 0; i < 15; i++) {
            String sql = "SELECT * FROM table" + i;
            facade.parseCached(sql);
        }
        
        CacheStats stats = facade.getCacheStatistics();
        assertTrue(stats.getSize() <= 10, "Cache size should not exceed limit");
        assertEquals(15, stats.getMissCount(), "Should have 15 misses");
    }

    @Test
    void testCacheStatisticsAccurate() {
        String sql = "SELECT * FROM users";
        
        // First call - cache miss
        facade.parseCached(sql);
        CacheStats stats1 = facade.getCacheStatistics();
        assertEquals(1, stats1.getMissCount(), "Should have 1 miss");
        assertEquals(0, stats1.getHitCount(), "Should have 0 hits");
        
        // Second call - cache hit
        facade.parseCached(sql);
        CacheStats stats2 = facade.getCacheStatistics();
        assertEquals(1, stats2.getMissCount(), "Should still have 1 miss");
        assertEquals(1, stats2.getHitCount(), "Should have 1 hit");
        
        // Third call - another cache hit
        facade.parseCached(sql);
        CacheStats stats3 = facade.getCacheStatistics();
        assertEquals(1, stats3.getMissCount(), "Should still have 1 miss");
        assertEquals(2, stats3.getHitCount(), "Should have 2 hits");
    }

    @Test
    void testCacheHitRate() {
        String sql = "SELECT * FROM users";
        
        // 1 miss
        facade.parseCached(sql);
        CacheStats stats1 = facade.getCacheStatistics();
        assertEquals(0.0, stats1.getHitRate(), 0.001, "Hit rate should be 0%");
        
        // 1 miss, 1 hit
        facade.parseCached(sql);
        CacheStats stats2 = facade.getCacheStatistics();
        assertEquals(0.5, stats2.getHitRate(), 0.001, "Hit rate should be 50%");
        
        // 1 miss, 2 hits
        facade.parseCached(sql);
        CacheStats stats3 = facade.getCacheStatistics();
        assertEquals(0.666, stats3.getHitRate(), 0.01, "Hit rate should be ~66.7%");
    }

    @Test
    void testClearCache() {
        String sql = "SELECT * FROM users";
        
        facade.parseCached(sql);
        facade.parseCached(sql);
        
        CacheStats statsBefore = facade.getCacheStatistics();
        assertTrue(statsBefore.getSize() > 0, "Cache should have entries");
        assertTrue(statsBefore.getHitCount() > 0, "Should have hits");
        
        facade.clearCache();
        
        CacheStats statsAfter = facade.getCacheStatistics();
        assertEquals(0, statsAfter.getSize(), "Cache should be empty");
        assertEquals(0, statsAfter.getHitCount(), "Hit count should be reset");
        assertEquals(0, statsAfter.getMissCount(), "Miss count should be reset");
    }

    @Test
    void testCacheNormalizationTrimAndLowercase() {
        String sql1 = "SELECT * FROM users";
        String sql2 = "  SELECT * FROM users  "; // Extra whitespace
        String sql3 = "SELECT * FROM USERS"; // Different case
        
        Statement stmt1 = facade.parseCached(sql1);
        Statement stmt2 = facade.parseCached(sql2);
        Statement stmt3 = facade.parseCached(sql3);
        
        // All should hit the same cache entry
        assertSame(stmt1, stmt2, "Trimmed SQL should hit same cache entry");
        assertSame(stmt1, stmt3, "Case-insensitive SQL should hit same cache entry");
        
        CacheStats stats = facade.getCacheStatistics();
        assertEquals(1, stats.getMissCount(), "Should have only 1 miss");
        assertEquals(2, stats.getHitCount(), "Should have 2 hits");
    }

    @Test
    void testLRUEvictionOrder() {
        JSqlParserFacade smallCache = new JSqlParserFacade(false, 3);
        
        // Add 3 entries
        smallCache.parseCached("SELECT * FROM table1");
        smallCache.parseCached("SELECT * FROM table2");
        smallCache.parseCached("SELECT * FROM table3");
        
        // Access table1 to make it recently used
        smallCache.parseCached("SELECT * FROM table1");
        
        // Add new entry - should evict table2 (least recently used)
        smallCache.parseCached("SELECT * FROM table4");
        
        CacheStats stats = facade.getCacheStatistics();
        assertEquals(3, smallCache.getCacheStatistics().getSize(), 
                "Cache should maintain size limit");
        
        // table1 should still be in cache (was accessed recently)
        smallCache.clearCache();
        smallCache.parseCached("SELECT * FROM table1");
        smallCache.parseCached("SELECT * FROM table2");
        smallCache.parseCached("SELECT * FROM table3");
        smallCache.parseCached("SELECT * FROM table1"); // Access table1
        smallCache.parseCached("SELECT * FROM table4"); // Evicts table2
        
        // table1 should hit (recently accessed)
        long hitsBefore = smallCache.getCacheStatistics().getHitCount();
        smallCache.parseCached("SELECT * FROM table1");
        long hitsAfter = smallCache.getCacheStatistics().getHitCount();
        assertEquals(hitsBefore + 1, hitsAfter, "table1 should still be cached");
    }
}








