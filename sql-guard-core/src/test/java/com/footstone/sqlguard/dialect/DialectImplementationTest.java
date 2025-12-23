package com.footstone.sqlguard.dialect;

import com.footstone.sqlguard.dialect.impl.DB2Dialect;
import com.footstone.sqlguard.dialect.impl.DmDialect;
import com.footstone.sqlguard.dialect.impl.GaussDBDialect;
import com.footstone.sqlguard.dialect.impl.InformixDialect;
import com.footstone.sqlguard.dialect.impl.KingbaseDialect;
import com.footstone.sqlguard.dialect.impl.MySQLDialect;
import com.footstone.sqlguard.dialect.impl.OpenGaussDialect;
import com.footstone.sqlguard.dialect.impl.OracleDialect;
import com.footstone.sqlguard.dialect.impl.OscarDialect;
import com.footstone.sqlguard.dialect.impl.PostgreSQLDialect;
import com.footstone.sqlguard.dialect.impl.SQLServerDialect;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqlGuardDialect implementations and DialectFactory.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>MySQLDialect LIMIT clause generation</li>
 *   <li>PostgreSQLDialect LIMIT clause generation</li>
 *   <li>OracleDialect ROWNUM wrapping</li>
 *   <li>SQLServerDialect TOP clause generation</li>
 *   <li>DialectFactory auto-detection and caching</li>
 * </ul>
 *
 * @since 1.1.0
 */
@DisplayName("Dialect Implementation Tests")
class DialectImplementationTest {

    @Nested
    @DisplayName("MySQLDialect Tests")
    class MySQLDialectTests {

        private MySQLDialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new MySQLDialect();
        }

        @Test
        @DisplayName("Should apply LIMIT clause to simple SELECT")
        void testApplyLimitToSimpleSelect() throws Exception {
            // Given
            String sql = "SELECT * FROM users";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 1000);

            // Then
            String result = select.toString();
            assertTrue(result.toLowerCase().contains("limit 1000"),
                    "Expected LIMIT 1000, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("MySQL", dialect.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("PostgreSQLDialect Tests")
    class PostgreSQLDialectTests {

        private PostgreSQLDialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new PostgreSQLDialect();
        }

        @Test
        @DisplayName("Should apply LIMIT clause to simple SELECT")
        void testApplyLimitToSimpleSelect() throws Exception {
            // Given
            String sql = "SELECT id, name FROM products WHERE active = true";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 500);

            // Then
            String result = select.toString();
            assertTrue(result.toLowerCase().contains("limit 500"),
                    "Expected LIMIT 500, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("PostgreSQL", dialect.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("OracleDialect Tests")
    class OracleDialectTests {

        private OracleDialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new OracleDialect();
        }

        @Test
        @DisplayName("Should wrap SELECT with ROWNUM condition")
        void testApplyLimitWithRownum() throws Exception {
            // Given
            String sql = "SELECT * FROM employees";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 100);

            // Then
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("rownum"),
                    "Expected ROWNUM condition, got: " + result);
            assertTrue(result.contains("100"),
                    "Expected limit value 100, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("Oracle", dialect.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("SQLServerDialect Tests")
    class SQLServerDialectTests {

        private SQLServerDialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new SQLServerDialect();
        }

        @Test
        @DisplayName("Should apply TOP clause to SELECT")
        void testApplyLimitWithTop() throws Exception {
            // Given
            String sql = "SELECT * FROM orders";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 200);

            // Then
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("top"),
                    "Expected TOP clause, got: " + result);
            assertTrue(result.contains("200"),
                    "Expected limit value 200, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("SQL Server", dialect.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("DialectFactory Tests")
    class DialectFactoryTests {

        @BeforeEach
        void setUp() {
            DialectFactory.clearCache();
        }

        @Test
        @DisplayName("Should detect MySQL dialect from DataSource")
        void testDetectMySQLDialect() throws SQLException {
            // Given
            DataSource dataSource = createMockDataSource("MySQL");

            // When
            SqlGuardDialect dialect = DialectFactory.getDialect(dataSource);

            // Then
            assertInstanceOf(MySQLDialect.class, dialect);
            assertEquals("MySQL", dialect.getDatabaseType());
        }

        @Test
        @DisplayName("Should detect PostgreSQL dialect from DataSource")
        void testDetectPostgreSQLDialect() throws SQLException {
            // Given
            DataSource dataSource = createMockDataSource("PostgreSQL");

            // When
            SqlGuardDialect dialect = DialectFactory.getDialect(dataSource);

            // Then
            assertInstanceOf(PostgreSQLDialect.class, dialect);
        }

        @Test
        @DisplayName("Should detect Oracle dialect from DataSource")
        void testDetectOracleDialect() throws SQLException {
            // Given
            DataSource dataSource = createMockDataSource("Oracle");

            // When
            SqlGuardDialect dialect = DialectFactory.getDialect(dataSource);

            // Then
            assertInstanceOf(OracleDialect.class, dialect);
        }

        @Test
        @DisplayName("Should detect SQL Server dialect from DataSource")
        void testDetectSQLServerDialect() throws SQLException {
            // Given
            DataSource dataSource = createMockDataSource("Microsoft SQL Server");

            // When
            SqlGuardDialect dialect = DialectFactory.getDialect(dataSource);

            // Then
            assertInstanceOf(SQLServerDialect.class, dialect);
        }

        @Test
        @DisplayName("Should cache dialect for same DataSource")
        void testDialectCaching() throws SQLException {
            // Given
            DataSource dataSource = createMockDataSource("MySQL");

            // When
            SqlGuardDialect dialect1 = DialectFactory.getDialect(dataSource);
            SqlGuardDialect dialect2 = DialectFactory.getDialect(dataSource);

            // Then
            assertSame(dialect1, dialect2, "Should return same cached instance");
        }

        @Test
        @DisplayName("Should default to MySQL for unknown database")
        void testDefaultToMySQLForUnknownDatabase() throws SQLException {
            // Given
            DataSource dataSource = createMockDataSource("UnknownDB");

            // When
            SqlGuardDialect dialect = DialectFactory.getDialect(dataSource);

            // Then
            assertInstanceOf(MySQLDialect.class, dialect);
        }

        @Test
        @DisplayName("Should default to MySQL for null DataSource")
        void testDefaultToMySQLForNullDataSource() {
            // When
            SqlGuardDialect dialect = DialectFactory.getDialect(null);

            // Then
            assertInstanceOf(MySQLDialect.class, dialect);
        }

        @Test
        @DisplayName("Should create dialect by product name")
        void testCreateDialectByProductName() {
            // Test various product names
            assertInstanceOf(MySQLDialect.class, DialectFactory.createDialect("MySQL"));
            assertInstanceOf(MySQLDialect.class, DialectFactory.createDialect("MariaDB"));
            assertInstanceOf(PostgreSQLDialect.class, DialectFactory.createDialect("PostgreSQL"));
            assertInstanceOf(OracleDialect.class, DialectFactory.createDialect("Oracle Database"));
            assertInstanceOf(SQLServerDialect.class, DialectFactory.createDialect("Microsoft SQL Server"));
        }

        @Test
        @DisplayName("Should create dialect for DB2")
        void testCreateDB2Dialect() {
            assertInstanceOf(DB2Dialect.class, DialectFactory.createDialect("DB2"));
            assertInstanceOf(DB2Dialect.class, DialectFactory.createDialect("IBM DB2"));
        }

        @Test
        @DisplayName("Should create dialect for Informix")
        void testCreateInformixDialect() {
            assertInstanceOf(InformixDialect.class, DialectFactory.createDialect("Informix"));
            assertInstanceOf(InformixDialect.class, DialectFactory.createDialect("IBM Informix"));
        }

        @Test
        @DisplayName("Should create dialect for 达梦 (DM)")
        void testCreateDmDialect() {
            assertInstanceOf(DmDialect.class, DialectFactory.createDialect("DM DBMS"));
            assertInstanceOf(DmDialect.class, DialectFactory.createDialect("Dameng"));
        }

        @Test
        @DisplayName("Should create dialect for 金仓 (KingbaseES)")
        void testCreateKingbaseDialect() {
            assertInstanceOf(KingbaseDialect.class, DialectFactory.createDialect("KingbaseES"));
            assertInstanceOf(KingbaseDialect.class, DialectFactory.createDialect("Kingbase"));
        }

        @Test
        @DisplayName("Should create dialect for 神通 (Oscar)")
        void testCreateOscarDialect() {
            assertInstanceOf(OscarDialect.class, DialectFactory.createDialect("Oscar"));
        }

        @Test
        @DisplayName("Should create dialect for 华为 GaussDB")
        void testCreateGaussDBDialect() {
            assertInstanceOf(GaussDBDialect.class, DialectFactory.createDialect("GaussDB"));
            assertInstanceOf(GaussDBDialect.class, DialectFactory.createDialect("Huawei GaussDB"));
        }

        @Test
        @DisplayName("Should create dialect for openGauss")
        void testCreateOpenGaussDialect() {
            assertInstanceOf(OpenGaussDialect.class, DialectFactory.createDialect("openGauss"));
        }

        /**
         * Creates a mock DataSource that returns the specified database product name.
         */
        private DataSource createMockDataSource(String productName) throws SQLException {
            DataSource dataSource = Mockito.mock(DataSource.class);
            Connection connection = Mockito.mock(Connection.class);
            DatabaseMetaData metaData = Mockito.mock(DatabaseMetaData.class);

            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metaData);
            when(metaData.getDatabaseProductName()).thenReturn(productName);

            return dataSource;
        }
    }

    @Nested
    @DisplayName("DB2Dialect Tests")
    class DB2DialectTests {

        private DB2Dialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new DB2Dialect();
        }

        @Test
        @DisplayName("Should apply FETCH FIRST clause to SELECT")
        void testApplyLimitWithFetch() throws Exception {
            // Given
            String sql = "SELECT * FROM customers";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 500);

            // Then
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("fetch"),
                    "Expected FETCH clause, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("DB2", dialect.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("InformixDialect Tests")
    class InformixDialectTests {

        private InformixDialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new InformixDialect();
        }

        @Test
        @DisplayName("Should apply FIRST clause to SELECT")
        void testApplyLimitWithFirst() throws Exception {
            // Given
            String sql = "SELECT * FROM inventory";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 300);

            // Then
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("first"),
                    "Expected FIRST clause, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("Informix", dialect.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("DmDialect Tests (达梦)")
    class DmDialectTests {

        private DmDialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new DmDialect();
        }

        @Test
        @DisplayName("Should apply LIMIT clause to SELECT")
        void testApplyLimitClause() throws Exception {
            // Given
            String sql = "SELECT * FROM accounts";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 1000);

            // Then
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 1000"),
                    "Expected LIMIT 1000, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("DM", dialect.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("KingbaseDialect Tests (金仓)")
    class KingbaseDialectTests {

        private KingbaseDialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new KingbaseDialect();
        }

        @Test
        @DisplayName("Should apply LIMIT clause to SELECT")
        void testApplyLimitClause() throws Exception {
            // Given
            String sql = "SELECT * FROM transactions";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 800);

            // Then
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 800"),
                    "Expected LIMIT 800, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("KingbaseES", dialect.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("OscarDialect Tests (神通)")
    class OscarDialectTests {

        private OscarDialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new OscarDialect();
        }

        @Test
        @DisplayName("Should apply LIMIT clause to SELECT")
        void testApplyLimitClause() throws Exception {
            // Given
            String sql = "SELECT * FROM logs";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 600);

            // Then
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 600"),
                    "Expected LIMIT 600, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("Oscar", dialect.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("GaussDBDialect Tests (华为)")
    class GaussDBDialectTests {

        private GaussDBDialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new GaussDBDialect();
        }

        @Test
        @DisplayName("Should apply LIMIT clause to SELECT")
        void testApplyLimitClause() throws Exception {
            // Given
            String sql = "SELECT * FROM metrics";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 2000);

            // Then
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 2000"),
                    "Expected LIMIT 2000, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("GaussDB", dialect.getDatabaseType());
        }
    }

    @Nested
    @DisplayName("OpenGaussDialect Tests")
    class OpenGaussDialectTests {

        private OpenGaussDialect dialect;

        @BeforeEach
        void setUp() {
            dialect = new OpenGaussDialect();
        }

        @Test
        @DisplayName("Should apply LIMIT clause to SELECT")
        void testApplyLimitClause() throws Exception {
            // Given
            String sql = "SELECT * FROM events";
            Select select = (Select) CCJSqlParserUtil.parse(sql);

            // When
            dialect.applyLimit(select, 1500);

            // Then
            String result = select.toString().toLowerCase();
            assertTrue(result.contains("limit 1500"),
                    "Expected LIMIT 1500, got: " + result);
        }

        @Test
        @DisplayName("Should return correct database type")
        void testGetDatabaseType() {
            assertEquals("openGauss", dialect.getDatabaseType());
        }
    }
}

