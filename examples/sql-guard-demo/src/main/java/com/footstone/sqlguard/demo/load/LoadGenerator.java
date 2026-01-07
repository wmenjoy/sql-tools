package com.footstone.sqlguard.demo.load;

import com.footstone.sqlguard.demo.mapper.AuditScenarioMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Load generator for simulating real-world SQL workloads.
 *
 * <p>This component generates a mix of SQL queries to produce audit logs
 * for demonstration purposes. The distribution follows a realistic pattern:</p>
 *
 * <ul>
 *   <li>80% Fast queries (&lt;100ms) - Normal operations</li>
 *   <li>15% Slow queries (&gt;1s) - Performance issues</li>
 *   <li>5% Error queries - Syntax/runtime errors</li>
 * </ul>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * loadGenerator.run(); // Runs for 5 minutes
 * loadGenerator.runFor(Duration.ofMinutes(10)); // Custom duration
 * }</pre>
 *
 * <p><strong>Throughput:</strong></p>
 * <p>Target throughput is ~100 QPS, controlled by sleep intervals between queries.
 * This generates approximately 30,000 queries in 5 minutes.</p>
 *
 * @see AuditScenarioMapper
 */
@Component
public class LoadGenerator {

    private static final Logger log = LoggerFactory.getLogger(LoadGenerator.class);

    // Default run duration: 5 minutes in milliseconds
    private static final long DEFAULT_DURATION_MS = 5 * 60 * 1000;

    // Sleep interval between queries for ~100 QPS
    private static final long QUERY_INTERVAL_MS = 10;

    // Random number generator for query selection
    private final Random random = new Random();

    // Execution statistics
    private final AtomicLong fastQueryCount = new AtomicLong(0);
    private final AtomicLong slowQueryCount = new AtomicLong(0);
    private final AtomicLong errorQueryCount = new AtomicLong(0);
    private final AtomicLong totalExecutionTime = new AtomicLong(0);

    // Control flag for running state
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    private AuditScenarioMapper auditScenarioMapper;

    /**
     * Run load generator for default duration (5 minutes).
     *
     * <p>Generates queries with the following distribution:</p>
     * <ul>
     *   <li>80% Fast queries (selectById)</li>
     *   <li>15% Slow queries (using SLEEP function)</li>
     *   <li>5% Error queries (invalid SQL)</li>
     * </ul>
     *
     * @return execution statistics
     */
    public LoadStatistics run() {
        return runFor(DEFAULT_DURATION_MS);
    }

    /**
     * Run load generator for specified duration.
     *
     * @param durationMs duration in milliseconds
     * @return execution statistics
     */
    public LoadStatistics runFor(long durationMs) {
        if (running.getAndSet(true)) {
            log.warn("Load generator is already running");
            return getStatistics();
        }

        resetStatistics();
        long endTime = System.currentTimeMillis() + durationMs;
        long startTime = System.currentTimeMillis();

        log.info("Starting load generator for {} minutes ({} ms)", 
            durationMs / 60000, durationMs);

        try {
            while (System.currentTimeMillis() < endTime && running.get()) {
                executeRandomQuery();
                
                // Control QPS by sleeping between queries
                Thread.sleep(QUERY_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            log.warn("Load generator interrupted");
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
        }

        long actualDuration = System.currentTimeMillis() - startTime;
        LoadStatistics stats = getStatistics();
        stats.setActualDurationMs(actualDuration);

        log.info("Load generator completed: {}", stats);
        return stats;
    }

    /**
     * Stop the load generator if it's running.
     */
    public void stop() {
        if (running.getAndSet(false)) {
            log.info("Load generator stopping...");
        }
    }

    /**
     * Check if load generator is currently running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get current execution statistics.
     *
     * @return current statistics
     */
    public LoadStatistics getStatistics() {
        LoadStatistics stats = new LoadStatistics();
        stats.setFastQueryCount(fastQueryCount.get());
        stats.setSlowQueryCount(slowQueryCount.get());
        stats.setErrorQueryCount(errorQueryCount.get());
        stats.setTotalQueries(fastQueryCount.get() + slowQueryCount.get() + errorQueryCount.get());
        stats.setTotalExecutionTimeMs(totalExecutionTime.get());
        
        long total = stats.getTotalQueries();
        if (total > 0) {
            stats.setFastQueryPercent((double) fastQueryCount.get() / total * 100);
            stats.setSlowQueryPercent((double) slowQueryCount.get() / total * 100);
            stats.setErrorQueryPercent((double) errorQueryCount.get() / total * 100);
            stats.setAverageExecutionTimeMs((double) totalExecutionTime.get() / total);
        }
        
        return stats;
    }

    /**
     * Execute a random query based on the distribution.
     */
    private void executeRandomQuery() {
        int scenario = random.nextInt(100);
        long startTime = System.currentTimeMillis();

        try {
            if (scenario < 80) {
                // 80%: Fast query
                executeFastQuery();
                fastQueryCount.incrementAndGet();
            } else if (scenario < 95) {
                // 15%: Slow query (simulated, not actual SLEEP to avoid blocking)
                executeSlowQuerySimulated();
                slowQueryCount.incrementAndGet();
            } else {
                // 5%: Error query
                executeErrorQuery();
                errorQueryCount.incrementAndGet();
            }
        } catch (Exception e) {
            // Expected for error queries
            if (scenario >= 95) {
                errorQueryCount.incrementAndGet();
            }
        } finally {
            totalExecutionTime.addAndGet(System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Execute a fast query (selectById).
     */
    private void executeFastQuery() {
        long userId = random.nextInt(100) + 1;
        auditScenarioMapper.selectById(userId);
    }

    /**
     * Execute a slow query simulation.
     * 
     * <p>Instead of using actual SLEEP which would block, we execute a
     * query that triggers the slow query audit scenario.</p>
     */
    private void executeSlowQuerySimulated() {
        // Use deep pagination which is slow but not blocking
        auditScenarioMapper.deepPagination();
    }

    /**
     * Execute an error query.
     */
    private void executeErrorQuery() {
        try {
            auditScenarioMapper.invalidSql();
        } catch (Exception e) {
            // Expected - SQL error triggered
        }
    }

    /**
     * Reset all statistics counters.
     */
    private void resetStatistics() {
        fastQueryCount.set(0);
        slowQueryCount.set(0);
        errorQueryCount.set(0);
        totalExecutionTime.set(0);
    }

    /**
     * Statistics data class for load generation results.
     */
    public static class LoadStatistics {
        private long fastQueryCount;
        private long slowQueryCount;
        private long errorQueryCount;
        private long totalQueries;
        private long totalExecutionTimeMs;
        private long actualDurationMs;
        private double fastQueryPercent;
        private double slowQueryPercent;
        private double errorQueryPercent;
        private double averageExecutionTimeMs;

        // Getters and setters
        public long getFastQueryCount() { return fastQueryCount; }
        public void setFastQueryCount(long fastQueryCount) { this.fastQueryCount = fastQueryCount; }
        
        public long getSlowQueryCount() { return slowQueryCount; }
        public void setSlowQueryCount(long slowQueryCount) { this.slowQueryCount = slowQueryCount; }
        
        public long getErrorQueryCount() { return errorQueryCount; }
        public void setErrorQueryCount(long errorQueryCount) { this.errorQueryCount = errorQueryCount; }
        
        public long getTotalQueries() { return totalQueries; }
        public void setTotalQueries(long totalQueries) { this.totalQueries = totalQueries; }
        
        public long getTotalExecutionTimeMs() { return totalExecutionTimeMs; }
        public void setTotalExecutionTimeMs(long totalExecutionTimeMs) { this.totalExecutionTimeMs = totalExecutionTimeMs; }
        
        public long getActualDurationMs() { return actualDurationMs; }
        public void setActualDurationMs(long actualDurationMs) { this.actualDurationMs = actualDurationMs; }
        
        public double getFastQueryPercent() { return fastQueryPercent; }
        public void setFastQueryPercent(double fastQueryPercent) { this.fastQueryPercent = fastQueryPercent; }
        
        public double getSlowQueryPercent() { return slowQueryPercent; }
        public void setSlowQueryPercent(double slowQueryPercent) { this.slowQueryPercent = slowQueryPercent; }
        
        public double getErrorQueryPercent() { return errorQueryPercent; }
        public void setErrorQueryPercent(double errorQueryPercent) { this.errorQueryPercent = errorQueryPercent; }
        
        public double getAverageExecutionTimeMs() { return averageExecutionTimeMs; }
        public void setAverageExecutionTimeMs(double averageExecutionTimeMs) { this.averageExecutionTimeMs = averageExecutionTimeMs; }

        @Override
        public String toString() {
            return String.format(
                "LoadStatistics{total=%d, fast=%d(%.1f%%), slow=%d(%.1f%%), error=%d(%.1f%%), " +
                "duration=%dms, avgTime=%.2fms, qps=%.1f}",
                totalQueries, fastQueryCount, fastQueryPercent, 
                slowQueryCount, slowQueryPercent,
                errorQueryCount, errorQueryPercent,
                actualDurationMs, averageExecutionTimeMs,
                actualDurationMs > 0 ? (double) totalQueries * 1000 / actualDurationMs : 0
            );
        }
    }
}






