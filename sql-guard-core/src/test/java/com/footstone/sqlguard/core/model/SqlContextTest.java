package com.footstone.sqlguard.core.model;

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
                .parsedSql(parsedSql)
                .type(type)
                .mapperId(mapperId)
                .params(params)
                .datasource(datasource)
                .rowBounds(rowBounds)
                .build();

        // Assert
        assertNotNull(context);
        assertEquals(sql, context.getSql());
        assertEquals(parsedSql, context.getParsedSql());
        assertEquals(type, context.getType());
        assertEquals(mapperId, context.getMapperId());
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
                .mapperId(mapperId)
                .build();

        // Assert
        assertNotNull(context);
        assertEquals(sql, context.getSql());
        assertNull(context.getParsedSql());
        assertEquals(type, context.getType());
        assertEquals(mapperId, context.getMapperId());
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
                .parsedSql(parsedSql)
                .type(type)
                .mapperId(mapperId)
                .build();

        // Assert - parsedSql should be immutable (final field)
        assertNotNull(context.getParsedSql());
        assertSame(parsedSql, context.getParsedSql());
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
                .mapperId(mapperId)
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
                        .mapperId("com.example.UserMapper.selectAll")
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
                        .mapperId("com.example.UserMapper.selectAll")
                        .build()
        );
        assertTrue(exception.getMessage().contains("type"));
    }

    @Test
    @DisplayName("Builder throws IllegalArgumentException when mapperId is null")
    void testBuilderThrowsExceptionWhenMapperIdIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SqlContext.builder()
                        .sql("SELECT * FROM users")
                        .type(SqlCommandType.SELECT)
                        .build()
        );
        assertTrue(exception.getMessage().contains("mapperId"));
    }

    @Test
    @DisplayName("Builder validates mapperId format (namespace.methodId)")
    void testBuilderValidatesMapperIdFormat() {
        // Valid format should work
        assertDoesNotThrow(() -> SqlContext.builder()
                .sql("SELECT * FROM users")
                .type(SqlCommandType.SELECT)
                .mapperId("com.example.UserMapper.selectAll")
                .build()
        );

        // Invalid format (no dot separator) should throw
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> SqlContext.builder()
                        .sql("SELECT * FROM users")
                        .type(SqlCommandType.SELECT)
                        .mapperId("invalidFormat")
                        .build()
        );
        assertTrue(exception.getMessage().contains("mapperId"));
        assertTrue(exception.getMessage().contains("format"));
    }
}
