package com.footstone.sqlguard.validator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SQL deduplication filter using ThreadLocal LRU cache to prevent redundant validation.
 *
 * <p>This filter tracks recently validated SQL statements per thread and skips re-validation
 * if the same SQL is checked within a configured TTL window. This optimization is particularly
 * useful for repeated SQL executions in hot code paths.</p>
 *
 * <p><strong>Thread Safety:</strong> Uses ThreadLocal to ensure thread isolation without
 * synchronization overhead. Each thread maintains its own LRU cache.</p>
 *
 * <p><strong>Memory Management:</strong> LRU eviction prevents unbounded memory growth.
 * Cache cleanup via clearThreadCache() is important in thread pool scenarios.</p>
 *
 * <p><strong>Configuration:</strong></p>
 * <ul>
 *   <li>cacheSize: Maximum entries per thread (default 1000)</li>
 *   <li>ttlMs: Time-to-live in milliseconds (default 100ms)</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * SqlDeduplicationFilter filter = new SqlDeduplicationFilter();
 * if (filter.shouldCheck("SELECT * FROM users WHERE id = ?")) {
 *   // Perform validation
 * } else {
 *   // Skip validation (recently checked)
 * }
 * }</pre>
 *
 * @see DefaultSqlSafetyValidator
 */
public class SqlDeduplicationFilter {

  /**
   * Default cache size per thread.
   */
  private static final int DEFAULT_CACHE_SIZE = 1000;

  /**
   * Default TTL in milliseconds (100ms).
   */
  private static final long DEFAULT_TTL_MS = 100L;

  /**
   * ThreadLocal LRU cache mapping normalized SQL → last check timestamp.
   */
  private static final ThreadLocal<LRUCache<String, Long>> CACHE_HOLDER = new ThreadLocal<>();

  /**
   * Cache size configuration.
   */
  private final int cacheSize;

  /**
   * TTL in milliseconds.
   */
  private final long ttlMs;

  /**
   * Constructs a SqlDeduplicationFilter with default configuration.
   * Cache size: 1000, TTL: 100ms.
   */
  public SqlDeduplicationFilter() {
    this(DEFAULT_CACHE_SIZE, DEFAULT_TTL_MS);
  }

  /**
   * Constructs a SqlDeduplicationFilter with custom configuration.
   *
   * @param cacheSize maximum entries per thread
   * @param ttlMs time-to-live in milliseconds
   */
  public SqlDeduplicationFilter(int cacheSize, long ttlMs) {
    if (cacheSize <= 0) {
      throw new IllegalArgumentException("cacheSize must be positive");
    }
    if (ttlMs < 0) {
      throw new IllegalArgumentException("ttlMs cannot be negative");
    }
    this.cacheSize = cacheSize;
    this.ttlMs = ttlMs;
  }

  /**
   * Checks whether the given SQL should be validated.
   *
   * <p><strong>Logic:</strong></p>
   * <ul>
   *   <li>First check for this SQL → returns true (should validate)</li>
   *   <li>Checked within TTL window → returns false (skip validation)</li>
   *   <li>Checked but TTL expired → returns true (re-validate)</li>
   * </ul>
   *
   * @param sql the SQL string to check
   * @return true if validation should proceed, false if recently validated
   * @throws IllegalArgumentException if sql is null
   */
  public boolean shouldCheck(String sql) {
    if (sql == null) {
      throw new IllegalArgumentException("sql cannot be null");
    }

    // Normalize SQL: trim and lowercase for cache key
    String key = sql.trim().toLowerCase();

    // Get or create cache for current thread
    LRUCache<String, Long> cache = CACHE_HOLDER.get();
    if (cache == null) {
      cache = new LRUCache<>(cacheSize);
      CACHE_HOLDER.set(cache);
    }

    // Check cache
    Long lastCheckTime = cache.get(key);
    long currentTime = System.currentTimeMillis();

    if (lastCheckTime == null) {
      // First check - cache and allow validation
      cache.put(key, currentTime);
      return true;
    }

    // Check TTL
    long timeSinceLastCheck = currentTime - lastCheckTime;
    if (timeSinceLastCheck < ttlMs) {
      // Within TTL - skip validation
      return false;
    } else {
      // TTL expired - update timestamp and allow re-validation
      cache.put(key, currentTime);
      return true;
    }
  }

  /**
   * Clears the ThreadLocal cache for the current thread.
   *
   * <p>This method should be called in thread pool scenarios to prevent memory leaks.
   * Best practice: invoke in finally blocks or thread cleanup hooks.</p>
   */
  public static void clearThreadCache() {
    CACHE_HOLDER.remove();
  }

  /**
   * LRU cache implementation using LinkedHashMap with access order.
   *
   * @param <K> key type
   * @param <V> value type
   */
  static class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    /**
     * Constructs an LRU cache with specified maximum size.
     *
     * @param maxSize maximum number of entries
     */
    public LRUCache(int maxSize) {
      super(16, 0.75f, true); // accessOrder=true for LRU behavior
      this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
      return size() > maxSize;
    }
  }
}












