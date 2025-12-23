package com.footstone.sqlguard.compat.mybatis;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MyBatis version compatibility layer.
 *
 * <p>These tests verify that the compatibility layer works correctly
 * with SqlGuardInterceptor integration scenarios.
 *
 * @since 1.1.0
 */
@DisplayName("MyBatis Compatibility Integration Tests")
class MyBatisCompatibilityIntegrationTest {

    private Configuration configuration;
    private MappedStatement mappedStatement;
    private String testSql;

    @BeforeEach
    void setUp() {
        configuration = new Configuration();
        testSql = "SELECT * FROM users WHERE id = ?";
        
        // Create a real SqlSource
        SqlSource sqlSource = new SqlSource() {
            @Override
            public BoundSql getBoundSql(Object parameterObject) {
                return new BoundSql(configuration, testSql, Collections.emptyList(), parameterObject);
            }
        };
        
        // Build a real MappedStatement
        mappedStatement = new MappedStatement.Builder(
            configuration, 
            "com.example.UserMapper.selectById",
            sqlSource,
            SqlCommandType.SELECT
        ).build();
    }

    /**
     * Helper method to create BoundSql with custom SQL.
     */
    private BoundSql createBoundSql(String sql) {
        return new BoundSql(configuration, sql, Collections.emptyList(), null);
    }

    @Nested
    @DisplayName("Interceptor Integration Tests")
    class InterceptorIntegrationTests {

        /**
         * Tests that interceptor can use SqlExtractor correctly.
         *
         * <p>Simulates the pattern used in SqlGuardInterceptor.
         */
        @Test
        @DisplayName("SqlGuardInterceptor integration pattern should work")
        void testMyBatis_interceptorWorks() {
            // Given: Simulated interceptor context
            String originalSql = "SELECT * FROM users WHERE id = ?";
            BoundSql boundSql = createBoundSql(originalSql);

            // When: Using SqlExtractor in interceptor pattern
            SqlExtractor extractor = SqlExtractorFactory.create();
            String extractedSql = extractor.extractSql(mappedStatement, 1, boundSql);

            // Then: SQL should be correctly extracted
            assertEquals(originalSql, extractedSql);

            // Verify extractor version matches detected version
            String expectedVersion = MyBatisVersionDetector.is35OrAbove() ? "3.5.x" : "3.4.x";
            assertEquals(expectedVersion, extractor.getTargetVersion());
        }

        /**
         * Tests multiple SQL extractions in a single request (interceptor chain).
         */
        @Test
        @DisplayName("Should handle multiple extractions in interceptor chain")
        void testMyBatis_multipleExtractionsInChain() {
            // Given: Multiple SQLs in interceptor chain
            SqlExtractor extractor = SqlExtractorFactory.create();
            String[] sqls = {
                "SELECT * FROM users WHERE id = ?",
                "SELECT COUNT(*) FROM users",
                "SELECT u.*, r.* FROM users u JOIN roles r ON u.role_id = r.id"
            };

            // When: Extracting each SQL
            for (String sql : sqls) {
                BoundSql boundSql = createBoundSql(sql);
                String extracted = extractor.extractSql(mappedStatement, null, boundSql);

                // Then: Each SQL should be correctly extracted
                assertEquals(sql, extracted);
            }
        }
    }

    @Nested
    @DisplayName("Version Consistency Tests")
    class VersionConsistencyTests {

        /**
         * Tests that all components report consistent version.
         */
        @Test
        @DisplayName("All components should report consistent version")
        void testAllVersions_behaviorConsistent() {
            // Given: All version-related components

            // When: Getting version info from all sources
            boolean detectorResult = MyBatisVersionDetector.is35OrAbove();
            String detectorVersion = MyBatisVersionDetector.getDetectedVersion();
            String factoryVersion = SqlExtractorFactory.getDetectedVersion();
            SqlExtractor extractor = SqlExtractorFactory.create();
            String extractorVersion = extractor.getTargetVersion();

            // Then: All should be consistent
            assertEquals(detectorVersion, factoryVersion,
                "Detector and Factory should report same version");

            if (detectorResult) {
                assertEquals("3.5.x", detectorVersion);
                assertEquals("3.5.x", extractorVersion);
                assertInstanceOf(ModernSqlExtractor.class, extractor);
            } else {
                assertEquals("3.4.x", detectorVersion);
                assertEquals("3.4.x", extractorVersion);
                assertInstanceOf(LegacySqlExtractor.class, extractor);
            }
        }

        /**
         * Tests that behavior is consistent across multiple requests.
         */
        @Test
        @DisplayName("Behavior should be consistent across requests")
        void testBehavior_consistentAcrossRequests() {
            // Given: Multiple simulated requests
            String sql = "SELECT * FROM users";
            BoundSql boundSql = createBoundSql(sql);

            // When: Processing multiple "requests"
            for (int request = 0; request < 100; request++) {
                SqlExtractor extractor = SqlExtractorFactory.create();
                String extracted = extractor.extractSql(mappedStatement, null, boundSql);

                // Then: Each request should get consistent results
                assertEquals(sql, extracted);
                assertSame(SqlExtractorFactory.create(), extractor,
                    "Should always return same cached instance");
            }
        }
    }

    @Nested
    @DisplayName("Concurrent Access Tests")
    class ConcurrentAccessTests {

        /**
         * Tests thread-safe concurrent access to SqlExtractor.
         */
        @Test
        @DisplayName("Should handle concurrent access safely")
        void testConcurrentAccess_threadSafe() throws InterruptedException {
            // Given: Multiple threads accessing SqlExtractor
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            String sql = "SELECT * FROM concurrent_test";

            // When: All threads try to extract SQL concurrently
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready

                        // Create BoundSql for this thread
                        BoundSql threadBoundSql = createBoundSql(sql);

                        SqlExtractor extractor = SqlExtractorFactory.create();
                        String extracted = extractor.extractSql(mappedStatement, null, threadBoundSql);

                        if (sql.equals(extracted)) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // Wait for completion
            assertTrue(endLatch.await(10, TimeUnit.SECONDS), "Should complete within timeout");

            executor.shutdown();

            // Then: All threads should succeed
            assertEquals(threadCount, successCount.get(), "All threads should succeed");
            assertEquals(0, errorCount.get(), "No errors should occur");
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        /**
         * Tests handling of empty SQL.
         */
        @Test
        @DisplayName("Should handle empty SQL")
        void testEdgeCase_emptySql() {
            // Given: Empty SQL
            BoundSql boundSql = createBoundSql("");

            // When: Extracting SQL
            SqlExtractor extractor = SqlExtractorFactory.create();
            String extracted = extractor.extractSql(mappedStatement, null, boundSql);

            // Then: Should return empty string
            assertEquals("", extracted);
        }

        /**
         * Tests handling of very long SQL.
         */
        @Test
        @DisplayName("Should handle very long SQL")
        void testEdgeCase_veryLongSql() {
            // Given: Very long SQL (10KB)
            StringBuilder sb = new StringBuilder("SELECT ");
            for (int i = 0; i < 500; i++) {
                if (i > 0) sb.append(", ");
                sb.append("column").append(i);
            }
            sb.append(" FROM large_table WHERE id = ?");
            String longSql = sb.toString();
            BoundSql boundSql = createBoundSql(longSql);

            // When: Extracting SQL
            SqlExtractor extractor = SqlExtractorFactory.create();
            String extracted = extractor.extractSql(mappedStatement, null, boundSql);

            // Then: Should handle long SQL correctly
            assertEquals(longSql, extracted);
        }

        /**
         * Tests handling of SQL with special characters.
         */
        @Test
        @DisplayName("Should handle SQL with special characters")
        void testEdgeCase_specialCharacters() {
            // Given: SQL with special characters
            String specialSql = "SELECT * FROM users WHERE name LIKE '%test\\'s%' AND data = '{\"key\": \"value\"}'";
            BoundSql boundSql = createBoundSql(specialSql);

            // When: Extracting SQL
            SqlExtractor extractor = SqlExtractorFactory.create();
            String extracted = extractor.extractSql(mappedStatement, null, boundSql);

            // Then: Should preserve special characters
            assertEquals(specialSql, extracted);
        }

        /**
         * Tests handling of SQL with Unicode characters.
         */
        @Test
        @DisplayName("Should handle SQL with Unicode characters")
        void testEdgeCase_unicodeCharacters() {
            // Given: SQL with Unicode
            String unicodeSql = "SELECT * FROM users WHERE name = '中文名称' AND city = '東京'";
            BoundSql boundSql = createBoundSql(unicodeSql);

            // When: Extracting SQL
            SqlExtractor extractor = SqlExtractorFactory.create();
            String extracted = extractor.extractSql(mappedStatement, null, boundSql);

            // Then: Should preserve Unicode
            assertEquals(unicodeSql, extracted);
        }
    }
}
