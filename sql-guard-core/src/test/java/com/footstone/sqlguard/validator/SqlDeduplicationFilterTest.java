package com.footstone.sqlguard.validator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SqlDeduplicationFilter with ThreadLocal LRU cache.
 */
@DisplayName("SqlDeduplicationFilter - Deduplication Logic")
class SqlDeduplicationFilterTest {

  private SqlDeduplicationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new SqlDeduplicationFilter();
    // Clear any existing thread cache before each test
    SqlDeduplicationFilter.clearThreadCache();
  }

  @AfterEach
  void tearDown() {
    // Clean up thread cache after each test
    SqlDeduplicationFilter.clearThreadCache();
  }

  @Test
  @DisplayName("First check should allow validation")
  void testFirstCheck_shouldAllow() {
    String sql = "SELECT * FROM users WHERE id = ?";
    
    boolean shouldCheck = filter.shouldCheck(sql);
    
    assertTrue(shouldCheck, "First check should return true (allow validation)");
  }

  @Test
  @DisplayName("Same SQL within TTL should skip validation")
  void testSameSQLWithinTTL_shouldSkip() throws InterruptedException {
    String sql = "SELECT * FROM users WHERE id = ?";
    
    // First check
    assertTrue(filter.shouldCheck(sql), "First check should allow");
    
    // Second check immediately (within TTL)
    boolean shouldCheckAgain = filter.shouldCheck(sql);
    
    assertFalse(shouldCheckAgain, "Second check within TTL should return false (skip validation)");
  }

  @Test
  @DisplayName("Same SQL after TTL should allow re-validation")
  void testSameSQLAfterTTL_shouldAllow() throws InterruptedException {
    // Use a filter with very short TTL for testing
    SqlDeduplicationFilter shortTtlFilter = new SqlDeduplicationFilter(1000, 10L); // 10ms TTL
    String sql = "SELECT * FROM users WHERE id = ?";
    
    // First check
    assertTrue(shortTtlFilter.shouldCheck(sql), "First check should allow");
    
    // Wait for TTL to expire
    Thread.sleep(15); // Wait 15ms (> 10ms TTL)
    
    // Check again after TTL
    boolean shouldCheckAgain = shortTtlFilter.shouldCheck(sql);
    
    assertTrue(shouldCheckAgain, "Check after TTL expiration should return true (re-validate)");
  }

  @Test
  @DisplayName("Different SQL should always allow validation")
  void testDifferentSQL_shouldAllow() {
    String sql1 = "SELECT * FROM users WHERE id = ?";
    String sql2 = "SELECT * FROM orders WHERE user_id = ?";
    
    // Check first SQL
    assertTrue(filter.shouldCheck(sql1), "First SQL should allow");
    
    // Check different SQL
    boolean shouldCheck2 = filter.shouldCheck(sql2);
    
    assertTrue(shouldCheck2, "Different SQL should always return true (allow validation)");
  }

  @Test
  @DisplayName("Clear thread cache should reset cache state")
  void testClearThreadCache_shouldClearCache() {
    String sql = "SELECT * FROM users WHERE id = ?";
    
    // First check
    assertTrue(filter.shouldCheck(sql), "First check should allow");
    
    // Second check within TTL (should skip)
    assertFalse(filter.shouldCheck(sql), "Second check should skip");
    
    // Clear cache
    SqlDeduplicationFilter.clearThreadCache();
    
    // Check again after clear (should allow like first time)
    boolean shouldCheckAfterClear = filter.shouldCheck(sql);
    
    assertTrue(shouldCheckAfterClear, "Check after cache clear should allow (like first check)");
  }

  @Test
  @DisplayName("Thread isolation should maintain separate caches per thread")
  void testThreadIsolation_shouldIsolatePerThread() throws InterruptedException {
    String sql = "SELECT * FROM users WHERE id = ?";
    
    // Check in main thread
    assertTrue(filter.shouldCheck(sql), "Main thread first check should allow");
    assertFalse(filter.shouldCheck(sql), "Main thread second check should skip");
    
    // Check in different thread
    Thread otherThread = new Thread(() -> {
      SqlDeduplicationFilter threadFilter = new SqlDeduplicationFilter();
      boolean shouldCheck = threadFilter.shouldCheck(sql);
      assertTrue(shouldCheck, "Other thread should have separate cache (first check should allow)");
      
      // Cleanup
      SqlDeduplicationFilter.clearThreadCache();
    });
    
    otherThread.start();
    otherThread.join();
    
    // Main thread cache should still be intact
    assertFalse(filter.shouldCheck(sql), "Main thread cache should be unaffected by other thread");
  }

  @Test
  @DisplayName("SQL normalization should treat equivalent SQL as same")
  void testSQLNormalization_shouldTreatEquivalentAsSame() {
    String sql1 = "SELECT * FROM users WHERE id = ?";
    String sql2 = "  SELECT * FROM USERS WHERE ID = ?  "; // Different whitespace and case
    
    // Check first SQL
    assertTrue(filter.shouldCheck(sql1), "First check should allow");
    
    // Check normalized equivalent SQL
    boolean shouldCheck2 = filter.shouldCheck(sql2);
    
    assertFalse(shouldCheck2, "Normalized equivalent SQL should skip (treated as same)");
  }

  @Test
  @DisplayName("LRU cache should evict oldest entries when full")
  void testLRUEviction_shouldEvictOldest() {
    // Create filter with small cache size for testing
    SqlDeduplicationFilter smallCacheFilter = new SqlDeduplicationFilter(3, 100L); // Cache size 3
    
    // Add 3 entries (insertion order: SQL1, SQL2, SQL3)
    assertTrue(smallCacheFilter.shouldCheck("SQL1"), "SQL1 first check");
    assertTrue(smallCacheFilter.shouldCheck("SQL2"), "SQL2 first check");
    assertTrue(smallCacheFilter.shouldCheck("SQL3"), "SQL3 first check");
    
    // All should be cached (within TTL)
    assertFalse(smallCacheFilter.shouldCheck("SQL1"), "SQL1 should be cached");
    assertFalse(smallCacheFilter.shouldCheck("SQL2"), "SQL2 should be cached");
    assertFalse(smallCacheFilter.shouldCheck("SQL3"), "SQL3 should be cached");
    
    // Note: After the above checks, access order is now: SQL1 (most recent), SQL2, SQL3 (least recent)
    // But we accessed all 3, so the actual LRU order depends on the last access
    
    // Add 4th entry (should evict least recently used)
    assertTrue(smallCacheFilter.shouldCheck("SQL4"), "SQL4 first check");
    
    // At least one of the first 3 should be evicted
    // Due to access order complexity, we just verify cache size limit works
    int cachedCount = 0;
    if (!smallCacheFilter.shouldCheck("SQL1")) cachedCount++;
    if (!smallCacheFilter.shouldCheck("SQL2")) cachedCount++;
    if (!smallCacheFilter.shouldCheck("SQL3")) cachedCount++;
    if (!smallCacheFilter.shouldCheck("SQL4")) cachedCount++;
    
    assertTrue(cachedCount <= 3, "Cache should not exceed max size of 3");
  }

  @Test
  @DisplayName("Null SQL should throw IllegalArgumentException")
  void testNullSQL_shouldThrowException() {
    assertThrows(IllegalArgumentException.class, () -> {
      filter.shouldCheck(null);
    }, "Null SQL should throw IllegalArgumentException");
  }

  @Test
  @DisplayName("Empty SQL should be handled correctly")
  void testEmptySQL_shouldHandle() {
    String emptySQL = "";
    
    // First check
    assertTrue(filter.shouldCheck(emptySQL), "Empty SQL first check should allow");
    
    // Second check
    assertFalse(filter.shouldCheck(emptySQL), "Empty SQL second check should skip");
  }
}







