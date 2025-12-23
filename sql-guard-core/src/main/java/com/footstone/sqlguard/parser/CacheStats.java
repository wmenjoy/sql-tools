package com.footstone.sqlguard.parser;

/**
 * Statistics for the LRU cache in JSqlParserFacade.
 */
public class CacheStats {
    private final long hitCount;
    private final long missCount;
    private final int size;

    /**
     * Constructs cache statistics.
     *
     * @param hitCount  number of cache hits
     * @param missCount number of cache misses
     * @param size      current cache size
     */
    public CacheStats(long hitCount, long missCount, int size) {
        this.hitCount = hitCount;
        this.missCount = missCount;
        this.size = size;
    }

    /**
     * Gets the number of cache hits.
     *
     * @return hit count
     */
    public long getHitCount() {
        return hitCount;
    }

    /**
     * Gets the number of cache misses.
     *
     * @return miss count
     */
    public long getMissCount() {
        return missCount;
    }

    /**
     * Gets the current cache size.
     *
     * @return cache size
     */
    public int getSize() {
        return size;
    }

    /**
     * Calculates the cache hit rate.
     *
     * @return hit rate as a double between 0.0 and 1.0, or 0.0 if no requests
     */
    public double getHitRate() {
        long total = hitCount + missCount;
        if (total == 0) {
            return 0.0;
        }
        return (double) hitCount / total;
    }

    @Override
    public String toString() {
        return String.format("CacheStats{hits=%d, misses=%d, size=%d, hitRate=%.2f%%}",
                hitCount, missCount, size, getHitRate() * 100);
    }
}


















