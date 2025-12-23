package com.footstone.sqlguard.parser;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for StatementContext ThreadLocal cache.
 */
@DisplayName("StatementContext Tests")
public class StatementContextTest {

    @AfterEach
    void cleanup() {
        // Always clear ThreadLocal after each test
        StatementContext.clear();
    }

    @Nested
    @DisplayName("1. Basic Cache Operations")
    class BasicCacheOperationsTests {

        @Test
        @DisplayName("testCache_storesStatement_keyedBySql - cache stores Statement")
        public void testCache_storesStatement_keyedBySql() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);

            // Act
            StatementContext.cache(sql, stmt);

            // Assert
            assertEquals(1, StatementContext.size(), "Cache should contain 1 entry");
        }

        @Test
        @DisplayName("testGet_retrievesCachedStatement - get retrieves cached Statement")
        public void testGet_retrievesCachedStatement() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            // Act
            Statement retrieved = StatementContext.get(sql);

            // Assert
            assertNotNull(retrieved, "Retrieved Statement should not be null");
            assertSame(stmt, retrieved, "Should retrieve the same Statement instance");
        }

        @Test
        @DisplayName("testGet_returnsNull_ifNotCached - get returns null for uncached SQL")
        public void testGet_returnsNull_ifNotCached() {
            // Arrange
            String sql = "SELECT * FROM products";

            // Act
            Statement retrieved = StatementContext.get(sql);

            // Assert
            assertNull(retrieved, "Should return null for uncached SQL");
        }

        @Test
        @DisplayName("testClear_removesThreadLocalValue - clear removes all cached Statements")
        public void testClear_removesThreadLocalValue() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);
            assertEquals(1, StatementContext.size());

            // Act
            StatementContext.clear();

            // Assert
            assertEquals(0, StatementContext.size(), "Cache should be empty after clear");
            assertNull(StatementContext.get(sql), "Should return null after clear");
        }
    }

    @Nested
    @DisplayName("2. Thread Isolation Tests")
    class ThreadIsolationTests {

        @Test
        @DisplayName("testThreadLocalIsolation_differentThreads_independentCaches - threads have independent caches")
        public void testThreadLocalIsolation_differentThreads_independentCaches() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";
            Statement stmt1 = CCJSqlParserUtil.parse(sql);
            Statement stmt2 = CCJSqlParserUtil.parse(sql);

            CountDownLatch latch = new CountDownLatch(2);
            AtomicReference<Statement> thread1Result = new AtomicReference<>();
            AtomicReference<Statement> thread2Result = new AtomicReference<>();

            // Act - Thread 1 caches stmt1
            Thread t1 = new Thread(() -> {
                try {
                    StatementContext.cache(sql, stmt1);
                    thread1Result.set(StatementContext.get(sql));
                } finally {
                    StatementContext.clear();
                    latch.countDown();
                }
            });

            // Act - Thread 2 caches stmt2
            Thread t2 = new Thread(() -> {
                try {
                    StatementContext.cache(sql, stmt2);
                    thread2Result.set(StatementContext.get(sql));
                } finally {
                    StatementContext.clear();
                    latch.countDown();
                }
            });

            t1.start();
            t2.start();
            latch.await();

            // Assert - Each thread sees its own cached Statement
            assertSame(stmt1, thread1Result.get(), "Thread 1 should see stmt1");
            assertSame(stmt2, thread2Result.get(), "Thread 2 should see stmt2");
        }

        @Test
        @DisplayName("testConcurrentAccess_threadSafe - concurrent access is thread-safe")
        public void testConcurrentAccess_threadSafe() throws Exception {
            // Arrange
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);

            // Act - Multiple threads concurrently cache different Statements
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                new Thread(() -> {
                    try {
                        String sql = "SELECT * FROM table" + threadId;
                        Statement stmt = CCJSqlParserUtil.parse(sql);
                        StatementContext.cache(sql, stmt);

                        // Verify this thread sees its own cache
                        Statement retrieved = StatementContext.get(sql);
                        assertSame(stmt, retrieved, "Thread should see its own Statement");
                    } catch (Exception e) {
                        fail("Thread " + threadId + " threw exception: " + e);
                    } finally {
                        StatementContext.clear();
                        latch.countDown();
                    }
                }).start();
            }

            latch.await();

            // Assert - Main thread cache is empty (independent from worker threads)
            assertEquals(0, StatementContext.size(), "Main thread cache should be empty");
        }
    }

    @Nested
    @DisplayName("3. Memory Leak Prevention Tests")
    class MemoryLeakPreventionTests {

        @Test
        @DisplayName("testMemoryLeakPrevention_clear_releasesMemory - clear releases memory")
        public void testMemoryLeakPrevention_clear_releasesMemory() throws Exception {
            // Arrange - Cache many Statements
            for (int i = 0; i < 1000; i++) {
                String sql = "SELECT * FROM table" + i;
                Statement stmt = CCJSqlParserUtil.parse(sql);
                StatementContext.cache(sql, stmt);
            }

            assertEquals(1000, StatementContext.size());

            // Act - Clear cache
            StatementContext.clear();

            // Assert - All Statements released
            assertEquals(0, StatementContext.size(), "All cached Statements should be released");

            // Verify cache is truly empty (get returns null)
            assertNull(StatementContext.get("SELECT * FROM table0"));
        }
    }

    @Nested
    @DisplayName("4. Multiple SQL Tests")
    class MultipleSqlTests {

        @Test
        @DisplayName("testMultipleSql_cacheIndependently - multiple SQLs cache independently")
        public void testMultipleSql_cacheIndependently() throws Exception {
            // Arrange
            String sql1 = "SELECT * FROM users";
            String sql2 = "SELECT * FROM products";
            String sql3 = "SELECT * FROM orders";

            Statement stmt1 = CCJSqlParserUtil.parse(sql1);
            Statement stmt2 = CCJSqlParserUtil.parse(sql2);
            Statement stmt3 = CCJSqlParserUtil.parse(sql3);

            // Act
            StatementContext.cache(sql1, stmt1);
            StatementContext.cache(sql2, stmt2);
            StatementContext.cache(sql3, stmt3);

            // Assert
            assertEquals(3, StatementContext.size(), "Should cache 3 different SQLs");
            assertSame(stmt1, StatementContext.get(sql1));
            assertSame(stmt2, StatementContext.get(sql2));
            assertSame(stmt3, StatementContext.get(sql3));
        }
    }

    @Nested
    @DisplayName("5. Usage Pattern Tests")
    class UsagePatternTests {

        @Test
        @DisplayName("testCacheMiss_parseAndCache_pattern - cache miss pattern works")
        public void testCacheMiss_parseAndCache_pattern() throws Exception {
            // Arrange
            String sql = "SELECT * FROM users";

            // Act - Simulate InnerInterceptor cache-miss pattern
            Statement stmt = StatementContext.get(sql);
            if (stmt == null) {
                // Cache miss - parse and cache
                stmt = CCJSqlParserUtil.parse(sql);
                StatementContext.cache(sql, stmt);
            }

            // Assert - Subsequent calls hit cache
            Statement cached = StatementContext.get(sql);
            assertSame(stmt, cached, "Subsequent get should return cached Statement");
        }

        @Test
        @DisplayName("testUsagePattern_interceptorChain_works - interceptor chain pattern works")
        public void testUsagePattern_interceptorChain_works() throws Exception {
            // Simulate SqlGuardInterceptor (main interceptor)
            String sql = "SELECT * FROM users";
            Statement stmt = CCJSqlParserUtil.parse(sql);
            StatementContext.cache(sql, stmt);

            // Simulate SqlGuardCheckInnerInterceptor (priority 10)
            Statement stmt1 = StatementContext.get(sql);
            assertNotNull(stmt1, "CheckInterceptor should get cached Statement");
            assertSame(stmt, stmt1, "Should be same instance");

            // Simulate SelectLimitInnerInterceptor (priority 100)
            Statement stmt2 = StatementContext.get(sql);
            assertNotNull(stmt2, "LimitInterceptor should get cached Statement");
            assertSame(stmt, stmt2, "Should be same instance");

            // Cleanup (main interceptor's finally block)
            StatementContext.clear();

            // Verify cleaned up
            assertEquals(0, StatementContext.size());
        }
    }

    @Nested
    @DisplayName("6. Null Safety Tests")
    class NullSafetyTests {

        @Test
        @DisplayName("testCache_nullSql_throwsException - cache throws on null SQL")
        public void testCache_nullSql_throwsException() throws Exception {
            // Arrange
            Statement stmt = CCJSqlParserUtil.parse("SELECT 1");

            // Act & Assert
            assertThrows(NullPointerException.class,
                () -> StatementContext.cache(null, stmt),
                "Should throw NPE for null SQL");
        }

        @Test
        @DisplayName("testCache_nullStatement_throwsException - cache throws on null Statement")
        public void testCache_nullStatement_throwsException() {
            // Act & Assert
            assertThrows(NullPointerException.class,
                () -> StatementContext.cache("SELECT 1", null),
                "Should throw NPE for null Statement");
        }

        @Test
        @DisplayName("testGet_nullSql_throwsException - get throws on null SQL")
        public void testGet_nullSql_throwsException() {
            // Act & Assert
            assertThrows(NullPointerException.class,
                () -> StatementContext.get(null),
                "Should throw NPE for null SQL");
        }
    }
}

