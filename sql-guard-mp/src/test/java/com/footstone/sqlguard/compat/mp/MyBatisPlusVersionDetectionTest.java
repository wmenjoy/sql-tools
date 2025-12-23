package com.footstone.sqlguard.compat.mp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MyBatisPlusVersionDetector.
 *
 * @since 1.1.0
 */
@DisplayName("MyBatis-Plus Version Detection Tests")
class MyBatisPlusVersionDetectionTest {

    @Test
    @DisplayName("Version detector should return consistent version")
    void testVersionDetector_returnsConsistentVersion() {
        boolean firstCall = MyBatisPlusVersionDetector.is35OrAbove();
        boolean secondCall = MyBatisPlusVersionDetector.is35OrAbove();
        boolean thirdCall = MyBatisPlusVersionDetector.is35OrAbove();
        
        assertEquals(firstCall, secondCall, "Version detection should be consistent");
        assertEquals(secondCall, thirdCall, "Version detection should be consistent");
    }

    @Test
    @DisplayName("Version detector caching works correctly")
    void testVersionDetector_caching_works() {
        String version1 = MyBatisPlusVersionDetector.getDetectedVersion();
        String version2 = MyBatisPlusVersionDetector.getDetectedVersion();
        
        assertSame(version1, version2, "Detected version string should be cached");
        assertNotNull(version1, "Detected version should not be null");
    }

    @Test
    @DisplayName("is34x() should be opposite of is35OrAbove()")
    void testVersionDetector_is34x_oppositeOf_is35OrAbove() {
        boolean is35 = MyBatisPlusVersionDetector.is35OrAbove();
        boolean is34 = MyBatisPlusVersionDetector.is34x();
        
        assertNotEquals(is35, is34, "is34x() should be opposite of is35OrAbove()");
    }

    @Test
    @DisplayName("Detected version should be either 3.4.x or 3.5.x")
    void testVersionDetector_detectedVersion_validFormat() {
        String version = MyBatisPlusVersionDetector.getDetectedVersion();
        
        assertTrue(
            "3.4.x".equals(version) || "3.5.x".equals(version),
            "Detected version should be '3.4.x' or '3.5.x', but was: " + version
        );
    }

    @Test
    @DisplayName("Version detection should not throw exceptions")
    void testVersionDetector_handlesGracefully() {
        assertDoesNotThrow(() -> {
            MyBatisPlusVersionDetector.is35OrAbove();
            MyBatisPlusVersionDetector.is34x();
            MyBatisPlusVersionDetector.getDetectedVersion();
        }, "Version detection should not throw exceptions");
    }
}






