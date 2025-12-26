package com.footstone.sqlguard.core.model;

import com.footstone.sqlguard.core.model.ExecutionLayer;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SqlContext class.
 * Tests builder pattern, immutability, and validation logic.
 */
@DisplayName("SqlContext Tests")
class SqlContextTest {

    @Test
    @DisplayName("Builder creates SqlContext with all fields populated")
    void testBuilderWithAllFields() {
        // Arrange
        String sql = "SELECT * FROM users WHERE id = ?";
        Statement parsedSql = new Select(); // Mock statement
        SqlCommandType type = SqlCommandType.SELECT;
        String mapperId = "com.example.UserMapper.selectById";
        Map<String, Object> params = new HashMap<>();
        params.put("id", 123);
        String datasource = "primary";
        Object rowBounds = new Object(); // Placeholder for RowBounds

        // Act
        SqlContext context = SqlContext.builder()
                .sql(sql)
                .statement(parsedSql)
                .type(type)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId(mapperId)
                .params(params)
                .datasource(datasource)
                .rowBounds(rowBounds)
                .build();

        // Assert
        assertNotNull(context);
        assertEquals(sql, context.getSql());
        assertEquals(parsedSql, context.getStatement());
        assertEquals(type, context.getType());
        assertEquals(mapperId, context.getStatementId());
        assertEquals(params, context.getParams());
        assertEquals(datasource, context.getDatasource());
        assertEquals(rowBounds, context.getRowBounds());
    }

    @Test
    @DisplayName("Builder creates SqlContext with minimal required fields only")
    void testBuilderWithMinimalFields() {
        // Arrange
        String sql = "SELECT * FROM users";
        SqlCommandType type = SqlCommandType.SELECT;
        String mapperId = "com.example.UserMapper.selectAll";

        // Act
        SqlContext context = SqlContext.builder()
                .sql(sql)
                .type(type)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId(mapperId)
                .build();

        // Assert
        assertNotNull(context);
        assertEquals(sql, context.getSql());
        assertNull(context.getStatement());
        assertEquals(type, context.getType());
        assertEquals(mapperId, context.getStatementId());
        assertNull(context.getParams());
        assertNull(context.getDatasource());
        assertNull(context.getRowBounds());
    }

    @Test
    @DisplayName("Immutability: parsedSql should be set once and not modifiable")
    void testImmutabilityOfParsedSql() {
        // Arrange
        String sql = "SELECT * FROM users";
        Statement parsedSql = new Select();
        SqlCommandType type = SqlCommandType.SELECT;
        String mapperId = "com.example.UserMapper.selectAll";

        // Act
        SqlContext context = SqlContext.builder()
                .sql(sql)
                .statement(parsedSql)
                .type(type)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId(mapperId)
                .build();

        // Assert - parsedSql should be immutable (final field)
        assertNotNull(context.getStatement());
        assertSame(parsedSql, context.getStatement());
    }

    @Test
    @DisplayName("Null handling: optional fields can be null")
    void testNullHandlingForOptionalFields() {
        // Arrange
        String sql = "INSERT INTO users VALUES (?)";
        SqlCommandType type = SqlCommandType.INSERT;
        String mapperId = "com.example.UserMapper.insert";

        // Act
        SqlContext context = SqlContext.builder()
                .sql(sql)
                .type(type)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId(mapperId)
                .params(null)
                .datasource(null)
                .rowBounds(null)
                .build();

        // Assert
        assertNotNull(context);
        assertNull(context.getParams());
        assertNull(context.getDatasource());
        assertNull(context.getRowBounds());
    }

    @Test
    @DisplayName("Builder throws IllegalArgumentException when sql is null")
    void testBuilderThrowsExceptionWhenSqlIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SqlContext.builder()
                        .type(SqlCommandType.SELECT)
                        .executionLayer(ExecutionLayer.MYBATIS)
                        .statementId("com.example.UserMapper.selectAll")
                        .build()
        );
        assertTrue(exception.getMessage().contains("sql"));
    }

    @Test
    @DisplayName("Builder throws IllegalArgumentException when type is null")
    void testBuilderThrowsExceptionWhenTypeIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SqlContext.builder()
                        .sql("SELECT * FROM users")
                        .executionLayer(ExecutionLayer.MYBATIS)
                        .statementId("com.example.UserMapper.selectAll")
                        .build()
        );
        assertTrue(exception.getMessage().contains("type"));
    }

    @Test
    @DisplayName("Builder allows null statementId for JDBC scenarios")
    void testBuilderAllowsNullStatementIdForJdbc() {
        // Act & Assert - null statementId should work for JDBC
        SqlContext context = assertDoesNotThrow(() -> SqlContext.builder()
                .sql("SELECT * FROM users")
                .type(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.JDBC)
                .build()
        );
        assertNull(context.getStatementId(), "statementId should be null for JDBC");
    }

    @Test
    @DisplayName("Builder allows null statementId for MYBATIS with warning")
    void testBuilderAllowsNullStatementIdForMybatis() {
        // Act & Assert - null statementId should still work (optional)
        SqlContext context = assertDoesNotThrow(() -> SqlContext.builder()
                .sql("SELECT * FROM users")
                .type(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .build()
        );
        assertNull(context.getStatementId(), "statementId can be null even for MYBATIS");
    }

    @Test
    @DisplayName("Builder accepts valid statementId format (namespace.methodId)")
    void testBuilderAcceptsValidStatementIdFormat() {
        // Valid format should work
        SqlContext context = assertDoesNotThrow(() -> SqlContext.builder()
                .sql("SELECT * FROM users")
                .type(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.UserMapper.selectAll")
                .build()
        );
        assertEquals("com.example.UserMapper.selectAll", context.getStatementId());
    }

    @Test
    @DisplayName("Builder accepts various statementId formats for flexibility")
    void testBuilderAcceptsVariousStatementIdFormats() {
        // Simple format (common for JDBC)
        SqlContext simple = assertDoesNotThrow(() -> SqlContext.builder()
                .sql("SELECT * FROM users")
                .type(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.JDBC)
                .statementId("selectUsers")
                .build()
        );
        assertEquals("selectUsers", simple.getStatementId());

        // Full qualified format (common for MyBatis)
        SqlContext qualified = assertDoesNotThrow(() -> SqlContext.builder()
                .sql("SELECT * FROM users")
                .type(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.UserMapper.selectAll")
                .build()
        );
        assertEquals("com.example.UserMapper.selectAll", qualified.getStatementId());
    }
}
