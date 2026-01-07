package com.footstone.sqlguard.compat.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WrapperTypeDetector.
 *
 * @since 1.1.0
 */
@DisplayName("WrapperTypeDetector Tests")
class WrapperTypeDetectorTest {

    static class TestEntity {
        private Long id;
        public Long getId() { return id; }
    }

    @Nested
    @DisplayName("Type Identification")
    class TypeIdentificationTests {

        @Test
        @DisplayName("Should identify QueryWrapper")
        void testIsQueryWrapper() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            
            assertTrue(WrapperTypeDetector.isQueryWrapper(wrapper));
            assertFalse(WrapperTypeDetector.isUpdateWrapper(wrapper));
            assertFalse(WrapperTypeDetector.isLambdaWrapper(wrapper));
        }

        @Test
        @DisplayName("Should identify LambdaQueryWrapper")
        void testIsLambdaQueryWrapper() {
            LambdaQueryWrapper<TestEntity> wrapper = new LambdaQueryWrapper<>();
            
            assertTrue(WrapperTypeDetector.isQueryWrapper(wrapper));
            assertTrue(WrapperTypeDetector.isLambdaWrapper(wrapper));
            assertFalse(WrapperTypeDetector.isUpdateWrapper(wrapper));
        }

        @Test
        @DisplayName("Should identify UpdateWrapper")
        void testIsUpdateWrapper() {
            UpdateWrapper<TestEntity> wrapper = new UpdateWrapper<>();
            
            assertTrue(WrapperTypeDetector.isUpdateWrapper(wrapper));
            assertFalse(WrapperTypeDetector.isQueryWrapper(wrapper));
            assertFalse(WrapperTypeDetector.isLambdaWrapper(wrapper));
        }

        @Test
        @DisplayName("Should identify LambdaUpdateWrapper")
        void testIsLambdaUpdateWrapper() {
            LambdaUpdateWrapper<TestEntity> wrapper = new LambdaUpdateWrapper<>();
            
            assertTrue(WrapperTypeDetector.isUpdateWrapper(wrapper));
            assertTrue(WrapperTypeDetector.isLambdaWrapper(wrapper));
            assertFalse(WrapperTypeDetector.isQueryWrapper(wrapper));
        }

        @Test
        @DisplayName("isStandardWrapper should identify non-lambda wrappers")
        void testIsStandardWrapper() {
            assertTrue(WrapperTypeDetector.isStandardWrapper(new QueryWrapper<TestEntity>()));
            assertTrue(WrapperTypeDetector.isStandardWrapper(new UpdateWrapper<TestEntity>()));
            assertFalse(WrapperTypeDetector.isStandardWrapper(new LambdaQueryWrapper<TestEntity>()));
            assertFalse(WrapperTypeDetector.isStandardWrapper(new LambdaUpdateWrapper<TestEntity>()));
        }

        @Test
        @DisplayName("isWrapper should detect any wrapper")
        void testIsWrapper() {
            assertTrue(WrapperTypeDetector.isWrapper(new QueryWrapper<TestEntity>()));
            assertTrue(WrapperTypeDetector.isWrapper(new LambdaQueryWrapper<TestEntity>()));
            assertTrue(WrapperTypeDetector.isWrapper(new UpdateWrapper<TestEntity>()));
            assertTrue(WrapperTypeDetector.isWrapper(new LambdaUpdateWrapper<TestEntity>()));
            assertFalse(WrapperTypeDetector.isWrapper("not a wrapper"));
            assertFalse(WrapperTypeDetector.isWrapper(null));
        }
    }

    @Nested
    @DisplayName("Type Name and Enum")
    class TypeNameTests {

        @Test
        @DisplayName("Should get correct type name")
        void testGetTypeName() {
            assertEquals("QueryWrapper", WrapperTypeDetector.getTypeName(new QueryWrapper<TestEntity>()));
            assertEquals("LambdaQueryWrapper", WrapperTypeDetector.getTypeName(new LambdaQueryWrapper<TestEntity>()));
            assertEquals("UpdateWrapper", WrapperTypeDetector.getTypeName(new UpdateWrapper<TestEntity>()));
            assertEquals("LambdaUpdateWrapper", WrapperTypeDetector.getTypeName(new LambdaUpdateWrapper<TestEntity>()));
            assertEquals("null", WrapperTypeDetector.getTypeName(null));
        }

        @Test
        @DisplayName("Should get correct WrapperType enum")
        void testGetType() {
            assertEquals(WrapperTypeDetector.WrapperType.QUERY_WRAPPER, 
                WrapperTypeDetector.getType(new QueryWrapper<TestEntity>()));
            assertEquals(WrapperTypeDetector.WrapperType.LAMBDA_QUERY_WRAPPER, 
                WrapperTypeDetector.getType(new LambdaQueryWrapper<TestEntity>()));
            assertEquals(WrapperTypeDetector.WrapperType.UPDATE_WRAPPER, 
                WrapperTypeDetector.getType(new UpdateWrapper<TestEntity>()));
            assertEquals(WrapperTypeDetector.WrapperType.LAMBDA_UPDATE_WRAPPER, 
                WrapperTypeDetector.getType(new LambdaUpdateWrapper<TestEntity>()));
            assertEquals(WrapperTypeDetector.WrapperType.NONE, 
                WrapperTypeDetector.getType(null));
        }

        @Test
        @DisplayName("WrapperType enum methods should work correctly")
        void testWrapperTypeEnumMethods() {
            // Query types
            assertTrue(WrapperTypeDetector.WrapperType.QUERY_WRAPPER.isQuery());
            assertFalse(WrapperTypeDetector.WrapperType.QUERY_WRAPPER.isUpdate());
            assertFalse(WrapperTypeDetector.WrapperType.QUERY_WRAPPER.isLambda());
            
            // Lambda query types
            assertTrue(WrapperTypeDetector.WrapperType.LAMBDA_QUERY_WRAPPER.isQuery());
            assertTrue(WrapperTypeDetector.WrapperType.LAMBDA_QUERY_WRAPPER.isLambda());
            
            // Update types
            assertTrue(WrapperTypeDetector.WrapperType.UPDATE_WRAPPER.isUpdate());
            assertFalse(WrapperTypeDetector.WrapperType.UPDATE_WRAPPER.isLambda());
            
            // Lambda update types
            assertTrue(WrapperTypeDetector.WrapperType.LAMBDA_UPDATE_WRAPPER.isUpdate());
            assertTrue(WrapperTypeDetector.WrapperType.LAMBDA_UPDATE_WRAPPER.isLambda());
        }
    }

    @Nested
    @DisplayName("Map Parameter Detection")
    class MapParameterTests {

        @Test
        @DisplayName("Should detect wrapper from Map parameter")
        void testDetectFromMap() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("ew", wrapper);
            
            assertTrue(WrapperTypeDetector.isQueryWrapper(paramMap));
            assertTrue(WrapperTypeDetector.isWrapper(paramMap));
            assertEquals("QueryWrapper", WrapperTypeDetector.getTypeName(paramMap));
        }

        @Test
        @DisplayName("Should return false for empty map")
        void testEmptyMap() {
            Map<String, Object> paramMap = new HashMap<>();
            
            assertFalse(WrapperTypeDetector.isWrapper(paramMap));
            assertEquals("null", WrapperTypeDetector.getTypeName(paramMap));
        }
    }
}








