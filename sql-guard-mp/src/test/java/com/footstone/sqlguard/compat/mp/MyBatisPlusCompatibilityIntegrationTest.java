package com.footstone.sqlguard.compat.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for MyBatis-Plus compatibility layer.
 *
 * @since 1.1.0
 */
@DisplayName("MyBatis-Plus Compatibility Integration Tests")
class MyBatisPlusCompatibilityIntegrationTest {

    static class User {
        private Long id;
        private String name;
        private Integer age;
        private String status;

        public Long getId() { return id; }
        public String getName() { return name; }
        public Integer getAge() { return age; }
        public String getStatus() { return status; }
    }

    @Nested
    @DisplayName("Combined Detection")
    class CombinedDetectionTests {

        @Test
        @DisplayName("Should detect both IPage and Wrapper from param map")
        void testCombinedDetection() {
            IPage<User> page = new Page<>(1, 10);
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            wrapper.eq("status", "active");
            
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("page", page);
            paramMap.put("ew", wrapper);
            
            // Both should be detected
            assertTrue(IPageDetector.hasPagination(paramMap));
            assertTrue(QueryWrapperInspector.hasConditions(paramMap));
            assertTrue(WrapperTypeDetector.isQueryWrapper(paramMap));
            
            // Extract details
            assertEquals(1, IPageDetector.getCurrent(paramMap));
            assertEquals(10, IPageDetector.getSize(paramMap));
            assertNotNull(QueryWrapperInspector.extractConditions(paramMap));
        }
    }

    @Nested
    @DisplayName("Safety Validation")
    class SafetyValidationTests {

        @Test
        @DisplayName("Empty wrapper detection is critical for safety")
        void testEmptyWrapperDetection() {
            // Dangerous: empty wrapper = no WHERE clause
            QueryWrapper<User> emptyWrapper = new QueryWrapper<>();
            assertTrue(QueryWrapperInspector.isEmpty(emptyWrapper),
                "CRITICAL: Empty wrapper MUST be detected for safety");
            
            // Safe: wrapper with conditions
            QueryWrapper<User> safeWrapper = new QueryWrapper<>();
            safeWrapper.eq("id", 1);
            assertFalse(QueryWrapperInspector.isEmpty(safeWrapper));
        }
    }

    @Nested
    @DisplayName("Behavior Consistency")
    class BehaviorConsistencyTests {

        @Test
        @DisplayName("All components should work together consistently")
        void testConsistentBehavior() {
            // Version detection
            String version = MyBatisPlusVersionDetector.getDetectedVersion();
            assertNotNull(version);
            
            // IPage detection
            IPage<User> page = new Page<>(1, 10);
            assertTrue(IPageDetector.hasPagination(page));
            assertEquals(1, IPageDetector.getCurrent(page));
            assertEquals(10, IPageDetector.getSize(page));
            
            // Wrapper inspection
            QueryWrapper<User> wrapper = new QueryWrapper<>();
            assertTrue(QueryWrapperInspector.isEmpty(wrapper));
            
            wrapper.eq("id", 1);
            assertFalse(QueryWrapperInspector.isEmpty(wrapper));
            
            // Type detection
            assertTrue(WrapperTypeDetector.isQueryWrapper(wrapper));
            assertFalse(WrapperTypeDetector.isLambdaWrapper(wrapper));
        }

        @Test
        @DisplayName("Null and empty handling should be consistent")
        void testNullAndEmptyHandling() {
            // Null handling
            assertNull(IPageDetector.detect(null));
            assertFalse(IPageDetector.hasPagination(null));
            assertEquals(-1, IPageDetector.getCurrent(null));
            
            assertTrue(QueryWrapperInspector.isEmpty(null));
            assertNull(QueryWrapperInspector.extractConditions(null));
            
            assertFalse(WrapperTypeDetector.isWrapper(null));
            assertEquals("null", WrapperTypeDetector.getTypeName(null));
            
            // Empty map handling
            Map<String, Object> emptyMap = new HashMap<>();
            assertNull(IPageDetector.detect(emptyMap));
            assertTrue(QueryWrapperInspector.isEmpty(emptyMap));
            assertFalse(WrapperTypeDetector.isWrapper(emptyMap));
        }
    }

    @Nested
    @DisplayName("Lambda Wrapper Support")
    class LambdaWrapperTests {

        @Test
        @DisplayName("Lambda wrapper type detection works")
        void testLambdaWrapperTypeDetection() {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            
            assertTrue(WrapperTypeDetector.isQueryWrapper(wrapper));
            assertTrue(WrapperTypeDetector.isLambdaWrapper(wrapper));
            assertEquals("LambdaQueryWrapper", WrapperTypeDetector.getTypeName(wrapper));
            
            // Empty lambda wrapper should be detected
            assertTrue(QueryWrapperInspector.isEmpty(wrapper));
        }
    }
}






