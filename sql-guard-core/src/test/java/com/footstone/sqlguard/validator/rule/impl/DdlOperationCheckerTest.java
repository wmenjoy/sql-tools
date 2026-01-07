package com.footstone.sqlguard.validator.rule.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.parser.JSqlParserFacade;
import net.sf.jsqlparser.statement.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Test class for DdlOperationChecker.
 *
 * <p>Comprehensive tests covering:</p>
 * <ul>
 *   <li>PASS tests (≥5): DML operations (SELECT/INSERT/UPDATE/DELETE), allowed DDL types</li>
 *   <li>FAIL tests (≥10): Various DDL statement types (CREATE, ALTER, DROP, TRUNCATE)</li>
 *   <li>边界 tests (≥3): Temporary tables, empty/populated allowedOperations</li>
 * </ul>
 *
 * <p>Multi-dialect coverage: MySQL, Oracle, PostgreSQL DDL syntax variants.</p>
 */
@DisplayName("DdlOperationChecker Tests")
class DdlOperationCheckerTest {

    private JSqlParserFacade parser;
    private DdlOperationChecker checker;
    private DdlOperationConfig config;

    @BeforeEach
    void setUp() {
        parser = new JSqlParserFacade();
        config = new DdlOperationConfig();
        checker = new DdlOperationChecker(config);
    }

    /**
     * Helper method to create SqlContext from SQL string.
     */
    private SqlContext createContext(String sql) throws Exception {
        Statement stmt = parser.parse(sql);
        return SqlContext.builder()
                .sql(sql)
                .statement(stmt)
                .type(SqlCommandType.UNKNOWN)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.TestMapper.testMethod")
                .build();
    }

    // ==================== PASS TESTS (DML Operations - Should Pass) ====================

    @Nested
    @DisplayName("PASS Tests - DML Operations Should Pass")
    class PassTests {

        @Test
        @DisplayName("SELECT statement should pass (DML, not DDL)")
        void testSelect_shouldPass() throws Exception {
            String sql = "SELECT * FROM users WHERE id = 1";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "SELECT should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("INSERT statement should pass (DML, not DDL)")
        void testInsert_shouldPass() throws Exception {
            String sql = "INSERT INTO users (name, email) VALUES ('John', 'john@example.com')";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "INSERT should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("UPDATE statement should pass (DML, not DDL)")
        void testUpdate_shouldPass() throws Exception {
            String sql = "UPDATE users SET status = 'active' WHERE id = 1";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "UPDATE should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("DELETE statement should pass (DML, not DDL)")
        void testDelete_shouldPass() throws Exception {
            String sql = "DELETE FROM users WHERE id = 1";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "DELETE should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Complex SELECT with JOIN should pass (DML, not DDL)")
        void testComplexSelect_shouldPass() throws Exception {
            String sql = "SELECT u.name, o.order_id FROM users u " +
                    "INNER JOIN orders o ON u.id = o.user_id WHERE u.status = 'active'";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "Complex SELECT should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Allowed CREATE operation should pass when configured")
        void testAllowedCreate_shouldPass() throws Exception {
            // Configure to allow CREATE operations
            config.setAllowedOperations(Arrays.asList("CREATE"));
            checker = new DdlOperationChecker(config);

            String sql = "CREATE TABLE temp_data (id INT)";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "Allowed CREATE should pass");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Allowed ALTER operation should pass when configured")
        void testAllowedAlter_shouldPass() throws Exception {
            // Configure to allow ALTER operations
            config.setAllowedOperations(Arrays.asList("ALTER"));
            checker = new DdlOperationChecker(config);

            String sql = "ALTER TABLE users ADD COLUMN age INT";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "Allowed ALTER should pass");
            assertEquals(0, result.getViolations().size());
        }
    }

    // ==================== FAIL TESTS (DDL Operations - Should Fail) ====================

    @Nested
    @DisplayName("FAIL Tests - DDL Operations Should Fail")
    class FailTests {

        @Test
        @DisplayName("CREATE TABLE should add CRITICAL violation")
        void testCreateTable_shouldFail() throws Exception {
            String sql = "CREATE TABLE users (id INT PRIMARY KEY, name VARCHAR(100))";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "CREATE TABLE should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertEquals(1, result.getViolations().size());
            assertTrue(result.getViolations().get(0).getMessage().contains("CREATE"));
            assertTrue(result.getViolations().get(0).getMessage().contains("DDL"));
        }

        @Test
        @DisplayName("ALTER TABLE should add CRITICAL violation")
        void testAlterTable_shouldFail() throws Exception {
            String sql = "ALTER TABLE users ADD COLUMN email VARCHAR(255)";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "ALTER TABLE should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertEquals(1, result.getViolations().size());
            assertTrue(result.getViolations().get(0).getMessage().contains("ALTER"));
        }

        @Test
        @DisplayName("DROP TABLE should add CRITICAL violation")
        void testDropTable_shouldFail() throws Exception {
            String sql = "DROP TABLE users";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "DROP TABLE should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertEquals(1, result.getViolations().size());
            assertTrue(result.getViolations().get(0).getMessage().contains("DROP"));
        }

        @Test
        @DisplayName("TRUNCATE TABLE should add CRITICAL violation")
        void testTruncateTable_shouldFail() throws Exception {
            String sql = "TRUNCATE TABLE users";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "TRUNCATE TABLE should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertEquals(1, result.getViolations().size());
            assertTrue(result.getViolations().get(0).getMessage().contains("TRUNCATE"));
        }

        @Test
        @DisplayName("CREATE INDEX should add CRITICAL violation")
        void testCreateIndex_shouldFail() throws Exception {
            String sql = "CREATE INDEX idx_users_email ON users (email)";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "CREATE INDEX should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("CREATE"));
        }

        @Test
        @DisplayName("DROP INDEX should add CRITICAL violation")
        void testDropIndex_shouldFail() throws Exception {
            String sql = "DROP INDEX idx_users_email";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "DROP INDEX should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("DROP"));
        }

        @Test
        @DisplayName("CREATE VIEW should add CRITICAL violation")
        void testCreateView_shouldFail() throws Exception {
            String sql = "CREATE VIEW active_users AS SELECT * FROM users WHERE status = 'active'";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "CREATE VIEW should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("CREATE"));
        }

        @Test
        @DisplayName("DROP VIEW should add CRITICAL violation")
        void testDropView_shouldFail() throws Exception {
            String sql = "DROP VIEW active_users";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "DROP VIEW should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("DROP"));
        }

        @Test
        @DisplayName("MySQL CREATE TABLE with AUTO_INCREMENT should fail")
        void testMySqlCreateTable_shouldFail() throws Exception {
            String sql = "CREATE TABLE orders (id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "user_id INT NOT NULL, amount DECIMAL(10,2))";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "MySQL CREATE TABLE should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("PostgreSQL CREATE TABLE with SERIAL should fail")
        void testPostgreSqlCreateTable_shouldFail() throws Exception {
            String sql = "CREATE TABLE products (id SERIAL PRIMARY KEY, name VARCHAR(100))";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "PostgreSQL CREATE TABLE should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("ALTER TABLE ADD CONSTRAINT should fail")
        void testAlterTableAddConstraint_shouldFail() throws Exception {
            String sql = "ALTER TABLE orders ADD CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id)";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "ALTER TABLE ADD CONSTRAINT should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("ALTER TABLE MODIFY COLUMN should fail")
        void testAlterTableModifyColumn_shouldFail() throws Exception {
            String sql = "ALTER TABLE users MODIFY COLUMN name VARCHAR(200)";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "ALTER TABLE MODIFY COLUMN should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }
    }

    // ==================== EDGE CASE TESTS ====================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("CREATE TEMPORARY TABLE should fail (still DDL)")
        void testCreateTemporaryTable_shouldFail() throws Exception {
            String sql = "CREATE TEMPORARY TABLE temp_data (id INT, value VARCHAR(100))";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "CREATE TEMPORARY TABLE should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
            assertTrue(result.getViolations().get(0).getMessage().contains("CREATE"));
        }

        @Test
        @DisplayName("Empty allowedOperations should block all DDL")
        void testEmptyAllowedOperations_shouldBlockAllDdl() throws Exception {
            // Default config has empty allowedOperations
            DdlOperationConfig emptyConfig = new DdlOperationConfig();
            assertTrue(emptyConfig.getAllowedOperations().isEmpty());
            DdlOperationChecker emptyChecker = new DdlOperationChecker(emptyConfig);

            // Test CREATE
            String createSql = "CREATE TABLE test (id INT)";
            SqlContext createContext = createContext(createSql);
            ValidationResult createResult = ValidationResult.pass();
            emptyChecker.check(createContext, createResult);
            assertFalse(createResult.isPassed(), "CREATE should be blocked");

            // Test ALTER
            String alterSql = "ALTER TABLE test ADD COLUMN name VARCHAR(100)";
            SqlContext alterContext = createContext(alterSql);
            ValidationResult alterResult = ValidationResult.pass();
            emptyChecker.check(alterContext, alterResult);
            assertFalse(alterResult.isPassed(), "ALTER should be blocked");

            // Test DROP
            String dropSql = "DROP TABLE test";
            SqlContext dropContext = createContext(dropSql);
            ValidationResult dropResult = ValidationResult.pass();
            emptyChecker.check(dropContext, dropResult);
            assertFalse(dropResult.isPassed(), "DROP should be blocked");

            // Test TRUNCATE
            String truncateSql = "TRUNCATE TABLE test";
            SqlContext truncateContext = createContext(truncateSql);
            ValidationResult truncateResult = ValidationResult.pass();
            emptyChecker.check(truncateContext, truncateResult);
            assertFalse(truncateResult.isPassed(), "TRUNCATE should be blocked");
        }

        @Test
        @DisplayName("Populated allowedOperations should allow only specific types")
        void testPopulatedAllowedOperations_shouldAllowSpecificOnly() throws Exception {
            // Configure to allow only CREATE and ALTER
            DdlOperationConfig selectiveConfig = new DdlOperationConfig(true, Arrays.asList("CREATE", "ALTER"));
            DdlOperationChecker selectiveChecker = new DdlOperationChecker(selectiveConfig);

            // CREATE should pass
            String createSql = "CREATE TABLE test (id INT)";
            SqlContext createContext = createContext(createSql);
            ValidationResult createResult = ValidationResult.pass();
            selectiveChecker.check(createContext, createResult);
            assertTrue(createResult.isPassed(), "CREATE should be allowed");

            // ALTER should pass
            String alterSql = "ALTER TABLE test ADD COLUMN name VARCHAR(100)";
            SqlContext alterContext = createContext(alterSql);
            ValidationResult alterResult = ValidationResult.pass();
            selectiveChecker.check(alterContext, alterResult);
            assertTrue(alterResult.isPassed(), "ALTER should be allowed");

            // DROP should fail
            String dropSql = "DROP TABLE test";
            SqlContext dropContext = createContext(dropSql);
            ValidationResult dropResult = ValidationResult.pass();
            selectiveChecker.check(dropContext, dropResult);
            assertFalse(dropResult.isPassed(), "DROP should be blocked");

            // TRUNCATE should fail
            String truncateSql = "TRUNCATE TABLE test";
            SqlContext truncateContext = createContext(truncateSql);
            ValidationResult truncateResult = ValidationResult.pass();
            selectiveChecker.check(truncateContext, truncateResult);
            assertFalse(truncateResult.isPassed(), "TRUNCATE should be blocked");
        }

        @Test
        @DisplayName("Disabled checker should skip validation")
        void testDisabledChecker_shouldSkip() throws Exception {
            DdlOperationConfig disabledConfig = new DdlOperationConfig(false);
            DdlOperationChecker disabledChecker = new DdlOperationChecker(disabledConfig);

            String sql = "DROP TABLE users";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            disabledChecker.check(context, result);

            assertTrue(result.isPassed(), "Disabled checker should skip validation");
            assertEquals(0, result.getViolations().size());
        }

        @Test
        @DisplayName("Null statement should be handled gracefully")
        void testNullStatement_shouldBeHandled() {
            SqlContext context = SqlContext.builder()
                    .sql("invalid sql")
                    .statement(null)
                    .type(SqlCommandType.UNKNOWN)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.TestMapper.testMethod")
                    .build();
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertTrue(result.isPassed(), "Null statement should be handled gracefully");
            assertEquals(0, result.getViolations().size());
        }
    }

    // ==================== CONFIG TESTS ====================

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigTests {

        @Test
        @DisplayName("Default config should have CRITICAL risk level")
        void testDefaultConfig_shouldHaveCriticalRiskLevel() {
            DdlOperationConfig defaultConfig = new DdlOperationConfig();
            assertEquals(RiskLevel.CRITICAL, defaultConfig.getRiskLevel());
        }

        @Test
        @DisplayName("Default config should have empty allowedOperations")
        void testDefaultConfig_shouldHaveEmptyAllowedOperations() {
            DdlOperationConfig defaultConfig = new DdlOperationConfig();
            assertTrue(defaultConfig.getAllowedOperations().isEmpty());
        }

        @Test
        @DisplayName("Default config should be enabled")
        void testDefaultConfig_shouldBeEnabled() {
            DdlOperationConfig defaultConfig = new DdlOperationConfig();
            assertTrue(defaultConfig.isEnabled());
        }

        @Test
        @DisplayName("Invalid operation name should throw exception")
        void testInvalidOperationName_shouldThrowException() {
            DdlOperationConfig testConfig = new DdlOperationConfig();
            assertThrows(IllegalArgumentException.class, () -> {
                testConfig.setAllowedOperations(Arrays.asList("INVALID_OP"));
            });
        }

        @Test
        @DisplayName("Operation names should be case-insensitive")
        void testOperationNames_shouldBeCaseInsensitive() {
            DdlOperationConfig testConfig = new DdlOperationConfig();
            testConfig.setAllowedOperations(Arrays.asList("create", "ALTER", "Drop"));

            assertTrue(testConfig.isOperationAllowed("CREATE"));
            assertTrue(testConfig.isOperationAllowed("create"));
            assertTrue(testConfig.isOperationAllowed("ALTER"));
            assertTrue(testConfig.isOperationAllowed("alter"));
            assertTrue(testConfig.isOperationAllowed("DROP"));
            assertTrue(testConfig.isOperationAllowed("drop"));
            assertFalse(testConfig.isOperationAllowed("TRUNCATE"));
        }

        @Test
        @DisplayName("Null allowedOperations should be treated as empty list")
        void testNullAllowedOperations_shouldBeTreatedAsEmpty() {
            DdlOperationConfig testConfig = new DdlOperationConfig();
            testConfig.setAllowedOperations(null);

            assertTrue(testConfig.getAllowedOperations().isEmpty());
            assertFalse(testConfig.isOperationAllowed("CREATE"));
        }

        @Test
        @DisplayName("Empty strings in allowedOperations should be ignored")
        void testEmptyStringsInAllowedOperations_shouldBeIgnored() {
            DdlOperationConfig testConfig = new DdlOperationConfig();
            testConfig.setAllowedOperations(Arrays.asList("CREATE", "", "  ", null, "ALTER"));

            assertEquals(2, testConfig.getAllowedOperations().size());
            assertTrue(testConfig.isOperationAllowed("CREATE"));
            assertTrue(testConfig.isOperationAllowed("ALTER"));
        }

        @Test
        @DisplayName("getAllowedOperations should return unmodifiable list")
        void testGetAllowedOperations_shouldReturnUnmodifiableList() {
            DdlOperationConfig testConfig = new DdlOperationConfig();
            testConfig.setAllowedOperations(Arrays.asList("CREATE"));

            assertThrows(UnsupportedOperationException.class, () -> {
                testConfig.getAllowedOperations().add("DROP");
            });
        }
    }

    // ==================== MULTI-DIALECT TESTS ====================

    @Nested
    @DisplayName("Multi-Dialect Tests")
    class MultiDialectTests {

        @Test
        @DisplayName("MySQL CREATE TABLE IF NOT EXISTS should fail")
        void testMySqlCreateTableIfNotExists_shouldFail() throws Exception {
            String sql = "CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY)";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "MySQL CREATE TABLE IF NOT EXISTS should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("Oracle CREATE TABLE with TABLESPACE should fail")
        void testOracleCreateTableWithTablespace_shouldFail() throws Exception {
            String sql = "CREATE TABLE employees (id NUMBER PRIMARY KEY, name VARCHAR2(100))";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "Oracle CREATE TABLE should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("PostgreSQL ALTER TABLE ADD COLUMN should fail")
        void testPostgreSqlAlterTable_shouldFail() throws Exception {
            String sql = "ALTER TABLE users ADD COLUMN created_at TIMESTAMP DEFAULT NOW()";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "PostgreSQL ALTER TABLE should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("DROP TABLE IF EXISTS should fail")
        void testDropTableIfExists_shouldFail() throws Exception {
            String sql = "DROP TABLE IF EXISTS temp_data";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "DROP TABLE IF EXISTS should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }

        @Test
        @DisplayName("CREATE UNIQUE INDEX should fail")
        void testCreateUniqueIndex_shouldFail() throws Exception {
            String sql = "CREATE UNIQUE INDEX idx_users_email ON users (email)";
            SqlContext context = createContext(sql);
            ValidationResult result = ValidationResult.pass();

            checker.check(context, result);

            assertFalse(result.isPassed(), "CREATE UNIQUE INDEX should fail");
            assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        }
    }
}
