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

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD tests for SqlExtractor implementations.
 *
 * <p>Tests both LegacySqlExtractor (3.4.x) and ModernSqlExtractor (3.5.x)
 * implementations, as well as the SqlExtractorFactory.
 *
 * <p>Note: MappedStatement is a final class and cannot be mocked with standard Mockito.
 * These tests use real MyBatis objects created via Configuration.
 *
 * @since 1.1.0
 */
@DisplayName("SqlExtractor Implementation Tests")
class SqlExtractorImplementationTest {

    private Configuration configuration;
    private MappedStatement mappedStatement;
    private BoundSql boundSql;
    private String testSql;

    @BeforeEach
    void setUp() {
        configuration = new Configuration();
        testSql = "SELECT id, name FROM users WHERE id = ?";
        
        // Create a real SqlSource that returns our test SQL
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
        
        // Get BoundSql from the SqlSource
        boundSql = sqlSource.getBoundSql(null);
    }

    @Nested
    @DisplayName("LegacySqlExtractor Tests")
    class LegacySqlExtractorTests {

        private LegacySqlExtractor extractor;

        @BeforeEach
        void setUp() {
            extractor = new LegacySqlExtractor();
        }

        /**
         * Tests SQL extraction from MyBatis 3.4.x BoundSql.
         */
        @Test
        @DisplayName("Should extract SQL from BoundSql")
        void testLegacySqlExtractor_MyBatis34_extractsSql() {
            // Given: A BoundSql with SQL (already set up in parent setUp)

            // When: Extracting SQL
            String actualSql = extractor.extractSql(mappedStatement, null, boundSql);

            // Then: Should return the SQL from BoundSql
            assertEquals(testSql, actualSql);
        }

        /**
         * Tests that extractor returns correct target version.
         */
        @Test
        @DisplayName("Should return target version 3.4.x")
        void testLegacySqlExtractor_returnsCorrectVersion() {
            // Given: LegacySqlExtractor

            // When: Getting target version
            String version = extractor.getTargetVersion();

            // Then: Should return 3.4.x
            assertEquals("3.4.x", version);
        }

        /**
         * Tests null MappedStatement handling.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException for null MappedStatement")
        void testLegacySqlExtractor_nullMappedStatement_throws() {
            // Given: null MappedStatement

            // When/Then: Should throw
            assertThrows(IllegalArgumentException.class,
                () -> extractor.extractSql(null, null, boundSql),
                "Should throw for null MappedStatement");
        }

        /**
         * Tests null BoundSql handling.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException for null BoundSql")
        void testLegacySqlExtractor_nullBoundSql_throws() {
            // Given: null BoundSql

            // When/Then: Should throw
            assertThrows(IllegalArgumentException.class,
                () -> extractor.extractSql(mappedStatement, null, null),
                "Should throw for null BoundSql");
        }

        /**
         * Tests parameter handling (parameter can be null).
         */
        @Test
        @DisplayName("Should handle null parameter")
        void testLegacySqlExtractor_handlesNullParameter() {
            // Given: BoundSql with SQL, null parameter

            // When: Extracting SQL with null parameter
            String actualSql = extractor.extractSql(mappedStatement, null, boundSql);

            // Then: Should extract SQL successfully
            assertEquals(testSql, actualSql);
        }
    }

    @Nested
    @DisplayName("ModernSqlExtractor Tests")
    class ModernSqlExtractorTests {

        private ModernSqlExtractor extractor;

        @BeforeEach
        void setUp() {
            extractor = new ModernSqlExtractor();
        }

        /**
         * Tests SQL extraction from MyBatis 3.5.x BoundSql.
         */
        @Test
        @DisplayName("Should extract SQL from BoundSql")
        void testModernSqlExtractor_MyBatis35_extractsSql() {
            // Given: A BoundSql with SQL

            // When: Extracting SQL
            String actualSql = extractor.extractSql(mappedStatement, null, boundSql);

            // Then: Should return the SQL from BoundSql
            assertEquals(testSql, actualSql);
        }

        /**
         * Tests that extractor returns correct target version.
         */
        @Test
        @DisplayName("Should return target version 3.5.x")
        void testModernSqlExtractor_returnsCorrectVersion() {
            // Given: ModernSqlExtractor

            // When: Getting target version
            String version = extractor.getTargetVersion();

            // Then: Should return 3.5.x
            assertEquals("3.5.x", version);
        }

        /**
         * Tests null MappedStatement handling.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException for null MappedStatement")
        void testModernSqlExtractor_nullMappedStatement_throws() {
            // Given: null MappedStatement

            // When/Then: Should throw
            assertThrows(IllegalArgumentException.class,
                () -> extractor.extractSql(null, null, boundSql),
                "Should throw for null MappedStatement");
        }

        /**
         * Tests null BoundSql handling.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException for null BoundSql")
        void testModernSqlExtractor_nullBoundSql_throws() {
            // Given: null BoundSql

            // When/Then: Should throw
            assertThrows(IllegalArgumentException.class,
                () -> extractor.extractSql(mappedStatement, null, null),
                "Should throw for null BoundSql");
        }

        /**
         * Tests complex SQL with parameters.
         */
        @Test
        @DisplayName("Should handle complex SQL with parameters")
        void testModernSqlExtractor_handlesComplexSql() {
            // Given: Complex SQL with multiple parameters
            String complexSql = "SELECT u.id, u.name, u.email, r.role_name " +
                "FROM users u " +
                "LEFT JOIN user_roles ur ON u.id = ur.user_id " +
                "LEFT JOIN roles r ON ur.role_id = r.id " +
                "WHERE u.status = ? AND u.created_at > ? " +
                "ORDER BY u.id DESC " +
                "LIMIT ? OFFSET ?";
            
            BoundSql complexBoundSql = new BoundSql(configuration, complexSql, Collections.emptyList(), null);

            // When: Extracting SQL
            String actualSql = extractor.extractSql(mappedStatement, new Object(), complexBoundSql);

            // Then: Should extract complete SQL
            assertEquals(complexSql, actualSql);
        }
    }

    @Nested
    @DisplayName("SqlExtractorFactory Tests")
    class SqlExtractorFactoryTests {

        /**
         * Tests that factory selects correct implementation based on version.
         */
        @Test
        @DisplayName("Should select correct implementation based on MyBatis version")
        void testSqlExtractorFactory_selectsCorrectImplementation() {
            // Given: SqlExtractorFactory

            // When: Creating extractor
            SqlExtractor extractor = SqlExtractorFactory.create();

            // Then: Should return appropriate implementation
            assertNotNull(extractor, "Extractor should not be null");

            if (MyBatisVersionDetector.is35OrAbove()) {
                assertInstanceOf(ModernSqlExtractor.class, extractor,
                    "Should return ModernSqlExtractor for MyBatis 3.5.x");
                assertTrue(SqlExtractorFactory.isUsingModernExtractor());
                assertFalse(SqlExtractorFactory.isUsingLegacyExtractor());
            } else {
                assertInstanceOf(LegacySqlExtractor.class, extractor,
                    "Should return LegacySqlExtractor for MyBatis 3.4.x");
                assertTrue(SqlExtractorFactory.isUsingLegacyExtractor());
                assertFalse(SqlExtractorFactory.isUsingModernExtractor());
            }
        }

        /**
         * Tests that factory returns cached instance.
         */
        @Test
        @DisplayName("Should return cached instance")
        void testSqlExtractorFactory_returnsCachedInstance() {
            // Given: Multiple calls to factory

            // When: Getting extractor multiple times
            SqlExtractor extractor1 = SqlExtractorFactory.create();
            SqlExtractor extractor2 = SqlExtractorFactory.create();
            SqlExtractor extractor3 = SqlExtractorFactory.getInstance();

            // Then: All should be the same instance
            assertSame(extractor1, extractor2, "Should return same cached instance");
            assertSame(extractor2, extractor3, "getInstance() should return same instance");
        }

        /**
         * Tests getDetectedVersion() method.
         */
        @Test
        @DisplayName("getDetectedVersion() should return valid version")
        void testSqlExtractorFactory_getDetectedVersion() {
            // Given: Factory

            // When: Getting detected version
            String version = SqlExtractorFactory.getDetectedVersion();

            // Then: Should return valid version string
            assertNotNull(version);
            assertTrue(version.equals("3.4.x") || version.equals("3.5.x"),
                "Version should be '3.4.x' or '3.5.x'");
        }

        /**
         * Tests that factory cannot be instantiated.
         */
        @Test
        @DisplayName("Should not allow instantiation")
        void testSqlExtractorFactory_cannotBeInstantiated() {
            // Given: SqlExtractorFactory class

            // When/Then: Attempting to instantiate should fail
            try {
                java.lang.reflect.Constructor<?> constructor = 
                    SqlExtractorFactory.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                assertThrows(Exception.class, constructor::newInstance,
                    "Constructor should throw exception");
            } catch (NoSuchMethodException e) {
                fail("Constructor should exist but be private");
            }
        }
    }

    @Nested
    @DisplayName("Parameter Handling Tests")
    class ParameterHandlingTests {

        /**
         * Tests that extractors handle various parameter types.
         */
        @Test
        @DisplayName("Should handle various parameter types")
        void testSqlExtractor_handlesParameters() {
            // Given: Extractor and various parameters
            SqlExtractor extractor = SqlExtractorFactory.create();

            // When/Then: Should handle null parameter
            assertEquals(testSql, extractor.extractSql(mappedStatement, null, boundSql));

            // When/Then: Should handle primitive wrapper
            assertEquals(testSql, extractor.extractSql(mappedStatement, 123, boundSql));

            // When/Then: Should handle String
            assertEquals(testSql, extractor.extractSql(mappedStatement, "test", boundSql));

            // When/Then: Should handle Object
            assertEquals(testSql, extractor.extractSql(mappedStatement, new Object(), boundSql));
        }
    }

    @Nested
    @DisplayName("Dynamic SQL Handling Tests")
    class DynamicSqlHandlingTests {

        /**
         * Tests that extractors handle dynamic SQL correctly.
         *
         * <p>Dynamic SQL fragments (IF, CHOOSE, FOREACH) are already resolved
         * in BoundSql, so the extractor just needs to return the final SQL.
         */
        @Test
        @DisplayName("Should handle dynamic SQL (already resolved in BoundSql)")
        void testSqlExtractor_handlesDynamicSql() {
            // Given: BoundSql with resolved dynamic SQL
            SqlExtractor extractor = SqlExtractorFactory.create();

            // Simulating resolved dynamic SQL (IF condition was true)
            String resolvedSql = "SELECT * FROM users WHERE status = ? AND name LIKE ?";
            BoundSql dynamicBoundSql = new BoundSql(configuration, resolvedSql, Collections.emptyList(), null);

            // When: Extracting SQL
            String actualSql = extractor.extractSql(mappedStatement, null, dynamicBoundSql);

            // Then: Should return the resolved SQL
            assertEquals(resolvedSql, actualSql);
        }

        /**
         * Tests FOREACH-generated SQL handling.
         */
        @Test
        @DisplayName("Should handle FOREACH-generated SQL")
        void testSqlExtractor_handlesForeachSql() {
            // Given: BoundSql with FOREACH-generated IN clause
            SqlExtractor extractor = SqlExtractorFactory.create();

            String foreachSql = "SELECT * FROM users WHERE id IN (?, ?, ?, ?)";
            BoundSql foreachBoundSql = new BoundSql(configuration, foreachSql, Collections.emptyList(), null);

            // When: Extracting SQL
            String actualSql = extractor.extractSql(mappedStatement, null, foreachBoundSql);

            // Then: Should return the SQL with expanded IN clause
            assertEquals(foreachSql, actualSql);
        }
    }
}
