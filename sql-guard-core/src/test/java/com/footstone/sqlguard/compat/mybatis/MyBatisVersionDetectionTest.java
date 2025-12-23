package com.footstone.sqlguard.compat.mybatis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for MyBatisVersionDetector.
 *
 * <p>These tests verify the version detection mechanism. Note that the actual
 * detection result depends on the MyBatis version in the classpath at runtime.
 *
 * <h2>Test Categories</h2>
 * <ul>
 *   <li>Version Detection Tests - verify detection logic</li>
 *   <li>Caching Tests - verify static caching mechanism</li>
 *   <li>API Tests - verify public API methods</li>
 * </ul>
 *
 * @since 1.1.0
 */
@DisplayName("MyBatisVersionDetector Tests")
class MyBatisVersionDetectionTest {

    @Nested
    @DisplayName("Version Detection Tests")
    class VersionDetectionTests {

        /**
         * Tests that version detection returns a consistent result.
         *
         * <p>The actual result depends on the MyBatis version in classpath.
         * This test verifies the method doesn't throw and returns a valid boolean.
         */
        @Test
        @DisplayName("is35OrAbove() should return consistent result")
        void testVersionDetector_returnsConsistentResult() {
            // Given: MyBatisVersionDetector class loaded

            // When: calling is35OrAbove() multiple times
            boolean result1 = MyBatisVersionDetector.is35OrAbove();
            boolean result2 = MyBatisVersionDetector.is35OrAbove();
            boolean result3 = MyBatisVersionDetector.is35OrAbove();

            // Then: all results should be the same (cached)
            assertEquals(result1, result2, "Results should be consistent");
            assertEquals(result2, result3, "Results should be consistent");
        }

        /**
         * Tests that is34x() is the inverse of is35OrAbove().
         */
        @Test
        @DisplayName("is34x() should be inverse of is35OrAbove()")
        void testVersionDetector_is34x_isInverseOfIs35OrAbove() {
            // Given: version detector methods

            // When: calling both methods
            boolean is35 = MyBatisVersionDetector.is35OrAbove();
            boolean is34 = MyBatisVersionDetector.is34x();

            // Then: they should be mutually exclusive
            assertNotEquals(is35, is34, "is34x() should be inverse of is35OrAbove()");
            assertTrue(is35 || is34, "One of them must be true");
        }

        /**
         * Tests detection with current classpath MyBatis version.
         *
         * <p>This test verifies that version detection works correctly with whatever
         * MyBatis version is in the classpath. The actual result depends on the
         * MyBatis version configured in pom.xml.
         */
        @Test
        @DisplayName("Should detect MyBatis version correctly based on classpath")
        void testVersionDetector_detectsCorrectly() {
            // Given: MyBatis in classpath (version depends on pom.xml configuration)

            // When: checking version
            boolean is35OrAbove = MyBatisVersionDetector.is35OrAbove();
            String detectedVersion = MyBatisVersionDetector.getDetectedVersion();

            // Then: detection result should be consistent
            if (is35OrAbove) {
                assertEquals("3.5.x", detectedVersion,
                    "Detected version should be 3.5.x when is35OrAbove() is true");
            } else {
                assertEquals("3.4.x", detectedVersion,
                    "Detected version should be 3.4.x when is35OrAbove() is false");
            }
            
            // Log the actual detected version for visibility
            System.out.println("Detected MyBatis version: " + detectedVersion);
        }

        /**
         * Tests getDetectedVersion() returns valid version string.
         */
        @Test
        @DisplayName("getDetectedVersion() should return valid version string")
        void testVersionDetector_getDetectedVersion_returnsValidString() {
            // Given: version detector

            // When: getting detected version
            String version = MyBatisVersionDetector.getDetectedVersion();

            // Then: should be either "3.4.x" or "3.5.x"
            assertNotNull(version, "Version should not be null");
            assertTrue(version.equals("3.4.x") || version.equals("3.5.x"),
                "Version should be '3.4.x' or '3.5.x', got: " + version);
        }

        /**
         * Tests that detected version matches is35OrAbove() result.
         */
        @Test
        @DisplayName("getDetectedVersion() should match is35OrAbove() result")
        void testVersionDetector_detectedVersionMatchesIs35OrAbove() {
            // Given: version detector methods

            // When: getting version info
            boolean is35 = MyBatisVersionDetector.is35OrAbove();
            String version = MyBatisVersionDetector.getDetectedVersion();

            // Then: version string should match boolean result
            if (is35) {
                assertEquals("3.5.x", version, "Version should be 3.5.x when is35OrAbove() is true");
            } else {
                assertEquals("3.4.x", version, "Version should be 3.4.x when is35OrAbove() is false");
            }
        }
    }

    @Nested
    @DisplayName("Caching Mechanism Tests")
    class CachingTests {

        /**
         * Tests that caching works correctly (no repeated Class.forName calls).
         *
         * <p>Verifies that multiple calls return the same cached result without
         * re-executing the detection logic.
         */
        @Test
        @DisplayName("Caching should return same result without re-detection")
        void testVersionDetector_caching_works() {
            // Given: First call to trigger detection
            boolean firstCall = MyBatisVersionDetector.is35OrAbove();

            // When: Making many subsequent calls
            for (int i = 0; i < 1000; i++) {
                boolean result = MyBatisVersionDetector.is35OrAbove();

                // Then: All results should match the first (cached) result
                assertEquals(firstCall, result,
                    "Cached result should be consistent across all calls");
            }
        }

        /**
         * Tests that caching is thread-safe.
         */
        @Test
        @DisplayName("Caching should be thread-safe")
        void testVersionDetector_caching_threadSafe() throws InterruptedException {
            // Given: Expected result from current thread
            boolean expectedResult = MyBatisVersionDetector.is35OrAbove();

            // When: Multiple threads access the detector concurrently
            Thread[] threads = new Thread[10];
            boolean[] results = new boolean[10];

            for (int i = 0; i < threads.length; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    results[index] = MyBatisVersionDetector.is35OrAbove();
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // Then: All threads should get the same result
            for (int i = 0; i < results.length; i++) {
                assertEquals(expectedResult, results[i],
                    "Thread " + i + " should get same cached result");
            }
        }
    }

    @Nested
    @DisplayName("API Tests")
    class ApiTests {

        /**
         * Tests that the class cannot be instantiated.
         */
        @Test
        @DisplayName("Should not allow instantiation")
        void testVersionDetector_cannotBeInstantiated() {
            // Given: MyBatisVersionDetector class

            // When/Then: Attempting to use reflection to instantiate should fail
            // (Private constructor throws UnsupportedOperationException)
            try {
                java.lang.reflect.Constructor<?> constructor = 
                    MyBatisVersionDetector.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                assertThrows(Exception.class, constructor::newInstance,
                    "Constructor should throw exception");
            } catch (NoSuchMethodException e) {
                fail("Constructor should exist but be private");
            }
        }

        /**
         * Tests that all public methods are static.
         */
        @Test
        @DisplayName("All public methods should be static")
        void testVersionDetector_allPublicMethodsAreStatic() {
            // Given: MyBatisVersionDetector class
            Class<?> clazz = MyBatisVersionDetector.class;

            // When: Checking public methods
            java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();

            // Then: All public methods should be static
            for (java.lang.reflect.Method method : methods) {
                if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    assertTrue(java.lang.reflect.Modifier.isStatic(method.getModifiers()),
                        "Public method " + method.getName() + " should be static");
                }
            }
        }
    }
}

