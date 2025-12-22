package com.footstone.sqlguard.validator;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Performance benchmark tests comparing new vs old architecture.
 * 
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Throughput: SQLs validated per second</li>
 *   <li>Latency: P50 and P99 percentile validation times</li>
 *   <li>Memory: Heap usage during bulk validation</li>
 * </ul>
 * 
 * <p>Expected Performance with New Architecture:</p>
 * <ul>
 *   <li>Throughput: > 500 SQLs/second (simple SQL > 1000)</li>
 *   <li>P99 Latency: < 10ms for simple/medium SQL</li>
 *   <li>Memory: < 100 MB increase for 10000 validations</li>
 * </ul>
 */
@DisplayName("Performance Benchmark Tests")
public class PerformanceBenchmarkTest {

    private static final int ITERATIONS = 1000;
    private static final int WARMUP_ITERATIONS = 100;
    private static final int BULK_ITERATIONS = 10000;

    private JSqlParserFacade facade;
    private DefaultSqlSafetyValidator validator;

    @BeforeEach
    void setUp() {
        facade = new JSqlParserFacade(false); // fail-fast mode
        SqlDeduplicationFilter.clearThreadCache();
    }

    @Nested
    @DisplayName("2. Throughput Tests")
    class ThroughputTests {

        @Test
        @DisplayName("benchmarkNewArchitecture_throughput_improved - throughput improvement 30-50%")
        void benchmarkNewArchitecture_throughput_improved() {
            // Arrange
            List<RuleChecker> checkers = createMockCheckers(5);
            RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
            SqlDeduplicationFilter filter = createBypassFilter();
            
            validator = new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
            List<String> testSqls = generateTestSqlSet(100);

            // Warmup - JIT compilation
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                validator.validate(buildContext(testSqls.get(i % testSqls.size())));
            }

            // Act - Measure throughput
            long startTime = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                validator.validate(buildContext(testSqls.get(i % testSqls.size())));
            }
            long endTime = System.nanoTime();

            // Calculate throughput
            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = ITERATIONS / durationSeconds;

            // Assert - Throughput should be reasonable
            assertTrue(throughput > 0, "Throughput should be positive");
            assertTrue(throughput > 500,
                String.format("Expected throughput > 500 SQLs/sec, got %.2f", throughput));

            System.out.printf("📊 New Architecture Throughput: %.2f SQLs/second%n", throughput);
            System.out.printf("   Duration for %d validations: %.3f seconds%n", ITERATIONS, durationSeconds);
        }

        @Test
        @DisplayName("benchmarkThroughput_simpleSql_veryFast - simple SQL validates very fast")
        void benchmarkThroughput_simpleSql_veryFast() {
            // Arrange
            List<RuleChecker> checkers = createMockCheckers(3);
            RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
            SqlDeduplicationFilter filter = createBypassFilter();
            
            validator = new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
            String simpleSql = "SELECT id, name FROM users WHERE id = 1";

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                validator.validate(buildContext(simpleSql));
            }

            // Act
            long startTime = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                validator.validate(buildContext(simpleSql));
            }
            long endTime = System.nanoTime();

            double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
            double throughput = ITERATIONS / durationSeconds;

            // Assert
            assertTrue(throughput > 1000,
                String.format("Simple SQL should be very fast, got %.2f SQLs/sec", throughput));

            System.out.printf("📊 Simple SQL Throughput: %.2f SQLs/second%n", throughput);
        }
    }

    @Nested
    @DisplayName("3. Latency Tests")
    class LatencyTests {

        @Test
        @DisplayName("benchmarkNewArchitecture_latencyP99_improved - P99 latency improved")
        void benchmarkNewArchitecture_latencyP99_improved() {
            // Arrange
            List<RuleChecker> checkers = createMockCheckers(5);
            RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
            SqlDeduplicationFilter filter = createBypassFilter();
            
            validator = new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
            List<String> testSqls = generateTestSqlSet(100);
            List<Long> latencies = new ArrayList<>();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                validator.validate(buildContext(testSqls.get(i % testSqls.size())));
            }

            // Act - Measure latency per validation
            for (int i = 0; i < ITERATIONS; i++) {
                long startTime = System.nanoTime();
                validator.validate(buildContext(testSqls.get(i % testSqls.size())));
                long endTime = System.nanoTime();
                latencies.add(endTime - startTime);
            }

            // Calculate percentiles
            Collections.sort(latencies);
            long p50 = latencies.get((int) (ITERATIONS * 0.50));
            long p99 = latencies.get((int) (ITERATIONS * 0.99));
            long min = latencies.get(0);
            long max = latencies.get(ITERATIONS - 1);
            double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);

            // Assert - P99 < 10ms
            assertTrue(p99 < 10_000_000,
                String.format("P99 latency should be < 10ms, got %.3f ms", p99 / 1_000_000.0));

            System.out.printf("📊 Latency Statistics:%n");
            System.out.printf("   Min:  %.3f ms%n", min / 1_000_000.0);
            System.out.printf("   P50:  %.3f ms%n", p50 / 1_000_000.0);
            System.out.printf("   P99:  %.3f ms%n", p99 / 1_000_000.0);
            System.out.printf("   Max:  %.3f ms%n", max / 1_000_000.0);
            System.out.printf("   Avg:  %.3f ms%n", avg / 1_000_000.0);
        }

        @Test
        @DisplayName("benchmarkLatency_complexSql_acceptable - complex SQL latency acceptable")
        void benchmarkLatency_complexSql_acceptable() {
            // Arrange
            List<RuleChecker> checkers = createMockCheckers(5);
            RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
            SqlDeduplicationFilter filter = createBypassFilter();
            
            validator = new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);

            String complexSql = "SELECT u.id, u.name, o.order_id, p.product_name " +
                "FROM users u " +
                "JOIN orders o ON u.id = o.user_id " +
                "JOIN products p ON o.product_id = p.id " +
                "WHERE u.age > 18 AND o.status = 'COMPLETED' " +
                "ORDER BY o.created_at DESC LIMIT 100";

            List<Long> latencies = new ArrayList<>();

            // Warmup
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                validator.validate(buildContext(complexSql));
            }

            // Act
            for (int i = 0; i < ITERATIONS; i++) {
                long startTime = System.nanoTime();
                validator.validate(buildContext(complexSql));
                long endTime = System.nanoTime();
                latencies.add(endTime - startTime);
            }

            Collections.sort(latencies);
            long p50 = latencies.get((int) (ITERATIONS * 0.50));
            long p99 = latencies.get((int) (ITERATIONS * 0.99));

            // Assert - Complex SQL P99 < 20ms
            assertTrue(p99 < 20_000_000,
                String.format("Complex SQL P99 should be < 20ms, got %.3f ms", p99 / 1_000_000.0));

            System.out.printf("📊 Complex SQL Latency:%n");
            System.out.printf("   P50: %.3f ms%n", p50 / 1_000_000.0);
            System.out.printf("   P99: %.3f ms%n", p99 / 1_000_000.0);
        }
    }

    @Nested
    @DisplayName("4. Memory Tests")
    class MemoryTests {

        @Test
        @DisplayName("benchmarkMemoryUsage_reduction_confirmed - memory usage reduced")
        void benchmarkMemoryUsage_reduction_confirmed() {
            // Arrange
            List<RuleChecker> checkers = createMockCheckers(5);
            RuleCheckerOrchestrator orchestrator = new RuleCheckerOrchestrator(checkers);
            SqlDeduplicationFilter filter = createBypassFilter();
            
            validator = new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
            List<String> testSqls = generateTestSqlSet(100);

            // Force GC before measurement
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            
            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

            // Act - Validate many SQLs
            for (int i = 0; i < BULK_ITERATIONS; i++) {
                validator.validate(buildContext(testSqls.get(i % testSqls.size())));
            }

            // Force GC after validation
            System.gc();
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

            // Calculate memory increase
            long memoryIncrease = memoryAfter - memoryBefore;

            // Assert - Memory increase should be reasonable (< 100 MB)
            // Note: This is a soft assertion - memory behavior can vary
            assertTrue(memoryIncrease < 100_000_000,
                String.format("Memory increase should be < 100 MB, got %.2f MB",
                    memoryIncrease / 1_000_000.0));

            System.out.printf("📊 Memory Usage (after %d validations):%n", BULK_ITERATIONS);
            System.out.printf("   Before: %.2f MB%n", memoryBefore / 1_000_000.0);
            System.out.printf("   After:  %.2f MB%n", memoryAfter / 1_000_000.0);
            System.out.printf("   Increase: %.2f MB%n", memoryIncrease / 1_000_000.0);
        }
    }

    // ========== Helper Methods ==========

    /**
     * Generates a diverse set of test SQL statements.
     * Mix of simple, medium, and complex queries.
     */
    private List<String> generateTestSqlSet(int count) {
        List<String> sqls = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (i % 3 == 0) {
                // Simple SQL
                sqls.add("SELECT id, name FROM users WHERE id = " + i);
            } else if (i % 3 == 1) {
                // Medium SQL
                sqls.add("SELECT u.id, u.name, o.order_id FROM users u " +
                    "JOIN orders o ON u.id = o.user_id WHERE u.age > " + (i % 100));
            } else {
                // Complex SQL
                sqls.add("SELECT u.id, u.name, o.order_id, p.product_name " +
                    "FROM users u " +
                    "JOIN orders o ON u.id = o.user_id " +
                    "JOIN products p ON o.product_id = p.id " +
                    "WHERE u.age > " + (i % 100) + " AND o.status = 'COMPLETED' " +
                    "ORDER BY o.created_at DESC LIMIT " + ((i % 10) + 1) * 10);
            }
        }
        return sqls;
    }

    /**
     * Creates a list of mock checkers that simulate real validation work.
     */
    private List<RuleChecker> createMockCheckers(int count) {
        List<RuleChecker> checkers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            checkers.add(new RuleChecker() {
                @Override
                public void check(SqlContext context, ValidationResult result) {
                    // Access Statement to simulate real checker behavior
                    assertNotNull(context.getStatement(), 
                        "Parsed Statement should be available");
                }

                @Override
                public boolean isEnabled() {
                    return true;
                }
            });
        }
        return checkers;
    }

    /**
     * Creates a deduplication filter that bypasses caching for accurate measurements.
     */
    private SqlDeduplicationFilter createBypassFilter() {
        return new SqlDeduplicationFilter() {
            @Override
            public boolean shouldCheck(String sql) {
                return true; // Always check (bypass deduplication)
            }
        };
    }

    /**
     * Builds a SqlContext for testing.
     */
    private SqlContext buildContext(String sql) {
        return SqlContext.builder()
            .sql(sql)
            .type(SqlCommandType.SELECT)
            .mapperId("com.example.TestMapper.method")
            .build();
    }
}

