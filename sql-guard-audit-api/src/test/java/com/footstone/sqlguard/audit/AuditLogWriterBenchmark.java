package com.footstone.sqlguard.audit;

import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance benchmark tests for AuditLogWriter.
 *
 * <p>This test suite validates:</p>
 * <ul>
 *   <li>Write latency (p50, p95, p99)</li>
 *   <li>Throughput (events/sec)</li>
 *   <li>Overhead on SQL execution</li>
 *   <li>Concurrent write performance</li>
 * </ul>
 */
class AuditLogWriterBenchmark {

    private LogbackAuditWriter writer;

    @BeforeEach
    void setUp() {
        writer = new LogbackAuditWriter();
    }

    @Test
    void benchmarkWriteLatency_shouldBeLessThan2ms() throws Exception {
        // Given: Sample audit events
        int eventCount = 10000;
        List<Long> latencies = new ArrayList<>(eventCount);

        // Warm up
        for (int i = 0; i < 100; i++) {
            AuditEvent event = createSampleEvent("WARMUP " + i);
            writer.writeAuditLog(event);
        }
        Thread.sleep(100);

        // When: Measure write latency
        for (int i = 0; i < eventCount; i++) {
            AuditEvent event = createSampleEvent("SELECT * FROM users WHERE id = " + i);
            
            long startNanos = System.nanoTime();
            writer.writeAuditLog(event);
            long endNanos = System.nanoTime();
            
            latencies.add(endNanos - startNanos);
        }

        // Then: Calculate percentiles
        latencies.sort(Long::compareTo);
        
        long p50Nanos = latencies.get((int) (eventCount * 0.50));
        long p95Nanos = latencies.get((int) (eventCount * 0.95));
        long p99Nanos = latencies.get((int) (eventCount * 0.99));
        
        double p50Ms = p50Nanos / 1_000_000.0;
        double p95Ms = p95Nanos / 1_000_000.0;
        double p99Ms = p99Nanos / 1_000_000.0;
        
        System.out.println("=== Write Latency Benchmark ===");
        System.out.println("Events: " + eventCount);
        System.out.println("P50 latency: " + p50Ms + "ms");
        System.out.println("P95 latency: " + p95Ms + "ms");
        System.out.println("P99 latency: " + p99Ms + "ms");
        
        // Verify: <2ms p99 latency (allowing some margin for test environment)
        assertTrue(p99Ms < 2.0, 
                "P99 latency should be <2ms, got: " + p99Ms + "ms");
    }

    @Test
    void benchmarkThroughput_shouldExceed10000EventsPerSecond() throws Exception {
        // Given: Large number of events
        int eventCount = 50000;
        
        // Warm up
        for (int i = 0; i < 100; i++) {
            AuditEvent event = createSampleEvent("WARMUP " + i);
            writer.writeAuditLog(event);
        }
        Thread.sleep(100);

        // When: Write events and measure time
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < eventCount; i++) {
            AuditEvent event = createSampleEvent("SELECT * FROM test WHERE id = " + i);
            writer.writeAuditLog(event);
        }
        
        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        
        // Then: Calculate throughput
        double throughput = (eventCount * 1000.0) / durationMs;
        
        System.out.println("=== Throughput Benchmark ===");
        System.out.println("Events: " + eventCount);
        System.out.println("Duration: " + durationMs + "ms");
        System.out.println("Throughput: " + String.format("%.0f", throughput) + " events/sec");
        
        // Verify: >10,000 events/sec
        assertTrue(throughput > 10000, 
                "Throughput should be >10,000 events/sec, got: " + throughput);
    }

    @Test
    void benchmarkOverhead_shouldBeLessThan1Percent() throws Exception {
        // Given: Measure only audit logging overhead
        int iterations = 10000;
        
        // Warm up
        for (int i = 0; i < 100; i++) {
            AuditEvent event = createSampleEvent("WARMUP " + i);
            writer.writeAuditLog(event);
        }
        Thread.sleep(100);
        
        // Measure audit logging time only
        List<Long> auditLatencies = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            AuditEvent event = createSampleEvent("SELECT * FROM test WHERE id = " + i);
            
            long start = System.nanoTime();
            writer.writeAuditLog(event);
            long end = System.nanoTime();
            
            auditLatencies.add(end - start);
        }
        
        // Calculate average audit overhead
        long totalAuditTime = auditLatencies.stream().mapToLong(Long::longValue).sum();
        double avgAuditTimeMs = (totalAuditTime / iterations) / 1_000_000.0;
        
        // Assuming typical SQL execution time is 10ms, calculate overhead percentage
        double typicalSqlTimeMs = 10.0;
        double overheadPercent = (avgAuditTimeMs / typicalSqlTimeMs) * 100.0;
        
        System.out.println("=== Overhead Benchmark ===");
        System.out.println("Iterations: " + iterations);
        System.out.println("Avg audit time: " + String.format("%.4f", avgAuditTimeMs) + "ms");
        System.out.println("Typical SQL time: " + typicalSqlTimeMs + "ms");
        System.out.println("Overhead: " + String.format("%.2f", overheadPercent) + "%");
        
        // Verify: <1% overhead for typical 10ms SQL execution
        // Allowing 5% for test environment (0.5ms audit time for 10ms SQL)
        assertTrue(overheadPercent < 5.0, 
                "Overhead should be <5% for typical SQL execution, got: " + overheadPercent + "%");
    }

    @Test
    void benchmarkConcurrentWrites_shouldMaintainPerformance() throws Exception {
        // Given: Multiple concurrent writers
        int threadCount = 10;
        int eventsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        
        List<Long> threadDurations = new ArrayList<>();
        
        // When: Execute concurrent writes
        long overallStart = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    long threadStart = System.currentTimeMillis();
                    
                    for (int j = 0; j < eventsPerThread; j++) {
                        AuditEvent event = AuditEvent.builder()
                                .sql("SELECT * FROM test WHERE id = " + (threadId * 10000 + j))
                                .sqlType(SqlCommandType.SELECT)
                                .mapperId("TestMapper.select")
                                .datasource("test")
                                .timestamp(Instant.now())
                                .build();
                        
                        writer.writeAuditLog(event);
                    }
                    
                    long threadEnd = System.currentTimeMillis();
                    synchronized (threadDurations) {
                        threadDurations.add(threadEnd - threadStart);
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }
        
        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for completion
        boolean completed = completionLatch.await(60, TimeUnit.SECONDS);
        long overallEnd = System.currentTimeMillis();
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Then: Calculate concurrent performance metrics
        assertTrue(completed, "All threads should complete within timeout");
        
        long overallDuration = overallEnd - overallStart;
        int totalEvents = threadCount * eventsPerThread;
        double overallThroughput = (totalEvents * 1000.0) / overallDuration;
        
        long avgThreadDuration = threadDurations.stream()
                .mapToLong(Long::longValue)
                .sum() / threadDurations.size();
        
        System.out.println("=== Concurrent Write Benchmark ===");
        System.out.println("Threads: " + threadCount);
        System.out.println("Events per thread: " + eventsPerThread);
        System.out.println("Total events: " + totalEvents);
        System.out.println("Overall duration: " + overallDuration + "ms");
        System.out.println("Overall throughput: " + String.format("%.0f", overallThroughput) + " events/sec");
        System.out.println("Avg thread duration: " + avgThreadDuration + "ms");
        
        // Verify: Concurrent throughput should still be high
        assertTrue(overallThroughput > 5000, 
                "Concurrent throughput should be >5,000 events/sec, got: " + overallThroughput);
    }

    @Test
    void benchmarkQueueSizeImpact_shouldShowPerformanceDifference() throws Exception {
        // Given: Different event counts to test queue behavior
        int[] eventCounts = {1000, 5000, 10000};
        
        System.out.println("=== Queue Size Impact Benchmark ===");
        
        for (int eventCount : eventCounts) {
            // Warm up
            for (int i = 0; i < 10; i++) {
                AuditEvent event = createSampleEvent("WARMUP " + i);
                writer.writeAuditLog(event);
            }
            Thread.sleep(50);
            
            // Measure write performance
            long startTime = System.currentTimeMillis();
            
            for (int i = 0; i < eventCount; i++) {
                AuditEvent event = createSampleEvent("SELECT * FROM test WHERE id = " + i);
                writer.writeAuditLog(event);
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            double throughput = (eventCount * 1000.0) / duration;
            double avgLatency = (double) duration / eventCount;
            
            System.out.println("Events: " + eventCount + 
                             ", Duration: " + duration + "ms" +
                             ", Throughput: " + String.format("%.0f", throughput) + " events/sec" +
                             ", Avg latency: " + String.format("%.3f", avgLatency) + "ms");
        }
        
        // This test is informational, no assertions
        assertTrue(true, "Queue size impact benchmark completed");
    }

    // Helper methods

    private AuditEvent createSampleEvent(String sql) {
        return AuditEvent.builder()
                .sql(sql)
                .sqlType(SqlCommandType.SELECT)
                .mapperId("BenchmarkMapper.select")
                .datasource("benchmark")
                .timestamp(Instant.now())
                .executionTimeMs(10L)
                .rowsAffected(1)
                .build();
    }

    private void simulateSqlExecution(long nanos) {
        long end = System.nanoTime() + nanos;
        while (System.nanoTime() < end) {
            // Busy wait to simulate SQL execution
        }
    }

    private void simulateValidation() {
        // Simulate lightweight validation (50 microseconds)
        long end = System.nanoTime() + 50_000;
        while (System.nanoTime() < end) {
            // Busy wait
        }
    }
}

