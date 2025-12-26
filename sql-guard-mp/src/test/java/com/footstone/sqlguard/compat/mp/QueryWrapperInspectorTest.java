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
 * Tests for QueryWrapperInspector.
 *
 * @since 1.1.0
 */
@DisplayName("QueryWrapperInspector Tests")
class QueryWrapperInspectorTest {

    static class TestEntity {
        private Long id;
        private String name;
        private Integer age;

        public Long getId() { return id; }
        public String getName() { return name; }
        public Integer getAge() { return age; }
    }

    @Nested
    @DisplayName("Condition Extraction")
    class ConditionExtractionTests {

        @Test
        @DisplayName("Should extract conditions from QueryWrapper")
        void testExtractConditions_queryWrapper() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            wrapper.eq("id", 1);
            
            String conditions = QueryWrapperInspector.extractConditions(wrapper);
            
            assertNotNull(conditions, "Conditions should be extracted");
            assertTrue(conditions.contains("id"), "Conditions should contain field name");
        }

        @Test
        @DisplayName("Should extract complex conditions")
        void testExtractConditions_complex() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            wrapper.eq("id", 1)
                   .like("name", "test")
                   .gt("age", 18);
            
            String conditions = QueryWrapperInspector.extractConditions(wrapper);
            
            assertNotNull(conditions, "Complex conditions should be extracted");
            assertFalse(conditions.isEmpty());
        }

        @Test
        @DisplayName("Should extract conditions from Map-wrapped QueryWrapper")
        void testExtractConditions_fromMap() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            wrapper.eq("status", "active");
            
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("ew", wrapper);
            
            String conditions = QueryWrapperInspector.extractConditions(paramMap);
            assertNotNull(conditions, "Should extract from map-wrapped wrapper");
        }

        @Test
        @DisplayName("Should extract custom SQL segment with WHERE keyword")
        void testExtractCustomSqlSegment() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            wrapper.eq("id", 1);
            
            String customSegment = QueryWrapperInspector.extractCustomSqlSegment(wrapper);
            
            assertNotNull(customSegment);
            assertTrue(customSegment.toUpperCase().contains("WHERE"));
        }
    }

    @Nested
    @DisplayName("Empty Wrapper Detection (Critical for Safety)")
    class EmptyWrapperTests {

        @Test
        @DisplayName("Should detect empty QueryWrapper")
        void testIsEmpty_emptyQueryWrapper() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            assertTrue(QueryWrapperInspector.isEmpty(wrapper), 
                "Empty wrapper should be detected as empty");
        }

        @Test
        @DisplayName("Should detect non-empty QueryWrapper")
        void testIsEmpty_nonEmptyQueryWrapper() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            wrapper.eq("id", 1);
            
            assertFalse(QueryWrapperInspector.isEmpty(wrapper), 
                "Non-empty wrapper should not be detected as empty");
        }

        @Test
        @DisplayName("Should detect empty LambdaQueryWrapper")
        void testIsEmpty_emptyLambdaWrapper() {
            LambdaQueryWrapper<TestEntity> wrapper = new LambdaQueryWrapper<>();
            assertTrue(QueryWrapperInspector.isEmpty(wrapper));
        }

        @Test
        @DisplayName("Should return true for null parameter")
        void testIsEmpty_null() {
            assertTrue(QueryWrapperInspector.isEmpty(null));
        }

        @Test
        @DisplayName("hasConditions should be opposite of isEmpty")
        void testHasConditions() {
            QueryWrapper<TestEntity> emptyWrapper = new QueryWrapper<>();
            QueryWrapper<TestEntity> nonEmptyWrapper = new QueryWrapper<>();
            nonEmptyWrapper.eq("id", 1);
            
            assertFalse(QueryWrapperInspector.hasConditions(emptyWrapper));
            assertTrue(QueryWrapperInspector.hasConditions(nonEmptyWrapper));
        }

        @Test
        @DisplayName("Empty wrapper detection works with all wrapper types")
        void testIsEmpty_allTypes() {
            assertTrue(QueryWrapperInspector.isEmpty(new QueryWrapper<TestEntity>()));
            assertTrue(QueryWrapperInspector.isEmpty(new LambdaQueryWrapper<TestEntity>()));
            assertTrue(QueryWrapperInspector.isEmpty(new UpdateWrapper<TestEntity>()));
            assertTrue(QueryWrapperInspector.isEmpty(new LambdaUpdateWrapper<TestEntity>()));
        }
    }

    @Nested
    @DisplayName("Wrapper Detection")
    class WrapperDetectionTests {

        @Test
        @DisplayName("Should detect wrapper from direct parameter")
        void testDetectWrapper_direct() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            
            assertNotNull(QueryWrapperInspector.detectWrapper(wrapper));
            assertSame(wrapper, QueryWrapperInspector.detectWrapper(wrapper));
        }

        @Test
        @DisplayName("Should detect wrapper from Map with 'ew' key")
        void testDetectWrapper_fromMap() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("ew", wrapper);
            
            assertNotNull(QueryWrapperInspector.detectWrapper(paramMap));
            assertSame(wrapper, QueryWrapperInspector.detectWrapper(paramMap));
        }

        @Test
        @DisplayName("Should return null for non-wrapper parameter")
        void testDetectWrapper_nonWrapper() {
            assertNull(QueryWrapperInspector.detectWrapper("not a wrapper"));
        }

        @Test
        @DisplayName("Should get wrapper type name")
        void testGetWrapperTypeName() {
            QueryWrapper<TestEntity> wrapper = new QueryWrapper<>();
            assertEquals("QueryWrapper", QueryWrapperInspector.getWrapperTypeName(wrapper));
            assertEquals("null", QueryWrapperInspector.getWrapperTypeName(null));
        }
    }
}







