package com.footstone.sqlguard.compat.mp;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IPageDetector.
 *
 * @since 1.1.0
 */
@DisplayName("IPageDetector Tests")
class IPageDetectorTest {

    private IPage<Object> testPage;

    @BeforeEach
    void setUp() {
        testPage = new Page<>(1, 10);
    }

    @Nested
    @DisplayName("Direct Parameter Detection")
    class DirectParameterTests {

        @Test
        @DisplayName("Should detect IPage from direct parameter")
        void testDetect_directParameter() {
            IPage<?> detected = IPageDetector.detect(testPage);
            
            assertNotNull(detected, "Should detect IPage from direct parameter");
            assertSame(testPage, detected, "Should return the same IPage instance");
        }

        @Test
        @DisplayName("Should return null for non-IPage parameter")
        void testDetect_nonIPage() {
            IPage<?> detected = IPageDetector.detect("not an IPage");
            assertNull(detected, "Should return null for non-IPage parameter");
        }

        @Test
        @DisplayName("Should return null for null parameter")
        void testDetect_null() {
            IPage<?> detected = IPageDetector.detect(null);
            assertNull(detected, "Should return null for null parameter");
        }
    }

    @Nested
    @DisplayName("Map-Wrapped Detection")
    class MapWrappedTests {

        @Test
        @DisplayName("Should detect IPage from param map with 'page' key")
        void testDetect_fromMap_pageKey() {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("page", testPage);
            
            IPage<?> detected = IPageDetector.detect(paramMap);
            
            assertNotNull(detected, "Should detect IPage from param map");
            assertSame(testPage, detected);
        }

        @Test
        @DisplayName("Should detect IPage from param map with 'Page' key")
        void testDetect_fromMap_PageKey() {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("Page", testPage);
            
            IPage<?> detected = IPageDetector.detect(paramMap);
            assertNotNull(detected, "Should detect IPage with 'Page' key");
        }

        @Test
        @DisplayName("Should detect IPage from map values")
        void testDetect_fromMap_anyValue() {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("customKey", testPage);
            
            IPage<?> detected = IPageDetector.detect(paramMap);
            assertNotNull(detected, "Should detect IPage from any map value");
        }

        @Test
        @DisplayName("Should return null for empty map")
        void testDetect_emptyMap() {
            Map<String, Object> paramMap = new HashMap<>();
            IPage<?> detected = IPageDetector.detect(paramMap);
            assertNull(detected, "Should return null for empty map");
        }
    }

    @Nested
    @DisplayName("Pagination Info Extraction")
    class PaginationInfoTests {

        @Test
        @DisplayName("hasPagination should return true for IPage")
        void testHasPagination_true() {
            assertTrue(IPageDetector.hasPagination(testPage));
        }

        @Test
        @DisplayName("hasPagination should return false for non-IPage")
        void testHasPagination_false() {
            assertFalse(IPageDetector.hasPagination("not an IPage"));
        }

        @Test
        @DisplayName("Should extract current page")
        void testGetCurrent() {
            Page<Object> page = new Page<>(5, 20);
            assertEquals(5, IPageDetector.getCurrent(page));
        }

        @Test
        @DisplayName("Should extract page size")
        void testGetSize() {
            Page<Object> page = new Page<>(1, 50);
            assertEquals(50, IPageDetector.getSize(page));
        }

        @Test
        @DisplayName("Should extract pagination info as formatted string")
        void testExtractPaginationInfo() {
            Page<Object> page = new Page<>(3, 25);
            String info = IPageDetector.extractPaginationInfo(page);
            
            assertEquals("IPage(current=3, size=25)", info);
        }

        @Test
        @DisplayName("Should return null pagination info for non-IPage")
        void testExtractPaginationInfo_null() {
            String info = IPageDetector.extractPaginationInfo("not an IPage");
            assertNull(info);
        }

        @Test
        @DisplayName("Should return -1 for getCurrent on non-IPage")
        void testGetCurrent_nonIPage() {
            assertEquals(-1, IPageDetector.getCurrent("not an IPage"));
        }

        @Test
        @DisplayName("Should return -1 for getSize on non-IPage")
        void testGetSize_nonIPage() {
            assertEquals(-1, IPageDetector.getSize("not an IPage"));
        }
    }

    @Nested
    @DisplayName("Valid Pagination")
    class ValidPaginationTests {

        @Test
        @DisplayName("hasValidPagination should return true for valid IPage")
        void testHasValidPagination_true() {
            Page<Object> page = new Page<>(1, 10);
            assertTrue(IPageDetector.hasValidPagination(page));
        }

        @Test
        @DisplayName("hasValidPagination should return false for zero size")
        void testHasValidPagination_zeroSize() {
            Page<Object> page = new Page<>(1, 0);
            assertFalse(IPageDetector.hasValidPagination(page));
        }
    }
}








