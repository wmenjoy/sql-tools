package com.footstone.sqlguard.interceptor.mybatis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.SqlSafetyValidator;
import java.util.ArrayList;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Tests for MyBatis version compatibility.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>MyBatis 3.4.x compatibility</li>
 *   <li>MyBatis 3.5.x compatibility</li>
 *   <li>BoundSql extraction across versions</li>
 *   <li>Parameter extraction across versions</li>
 *   <li>Dynamic SQL resolution</li>
 * </ul>
 *
 * <p>Note: These tests verify the interceptor works with both MyBatis versions.
 * The actual version testing is done via Maven profiles (mybatis-3.4 and mybatis-3.5).</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MyBatisVersionCompatibilityTest {

  @Mock
  private SqlSafetyValidator validator;

  @Mock
  private Executor executor;

  @Mock
  private ResultHandler resultHandler;

  private SqlSafetyInterceptor interceptor;
  private Configuration configuration;

  @BeforeEach
  void setUp() {
    interceptor = new SqlSafetyInterceptor(validator, ViolationStrategy.WARN);
    configuration = new Configuration();
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
  }

  @Test
  void testMyBatis34_interceptor_shouldWork() throws Throwable {
    // This test verifies the interceptor works with MyBatis 3.4.x API
    // When run with -Pmybatis-3.4 profile, it uses MyBatis 3.4.6

    // Arrange
    String sql = "SELECT * FROM users WHERE id = ?";
    String mapperId = "com.example.UserMapper.selectById";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {ms, 123L, RowBounds.DEFAULT, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    Object result = interceptor.intercept(invocation);

    // Assert
    assertNotNull(result);
    verify(validator, times(1)).validate(any(SqlContext.class));
    verify(executor, times(1)).query(any(MappedStatement.class), any(),
        any(RowBounds.class), any(ResultHandler.class));
  }

  @Test
  void testMyBatis34_boundSql_shouldExtract() throws Throwable {
    // Verify BoundSql extraction works in MyBatis 3.4.x

    // Arrange
    String expectedSql = "SELECT * FROM users WHERE status = 'ACTIVE'";
    String mapperId = "com.example.UserMapper.selectActive";

    MappedStatement ms = createMappedStatement(expectedSql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {ms, null, RowBounds.DEFAULT, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify SQL was extracted from BoundSql
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals(expectedSql, captor.getValue().getSql());
  }

  @Test
  void testMyBatis34_parameters_shouldExtract() throws Throwable {
    // Verify parameter extraction works in MyBatis 3.4.x

    // Arrange
    String sql = "UPDATE users SET name = ? WHERE id = ?";
    String mapperId = "com.example.UserMapper.updateName";
    Long userId = 456L;

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.UPDATE);

    Object[] args = {ms, userId};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(1);

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify parameters were extracted
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertNotNull(captor.getValue().getParams());
    assertTrue(captor.getValue().getParams().containsKey("param"));
    assertEquals(userId, captor.getValue().getParams().get("param"));
  }

  @Test
  void testMyBatis35_interceptor_shouldWork() throws Throwable {
    // This test verifies the interceptor works with MyBatis 3.5.x API
    // When run with -Pmybatis-3.5 profile (default), it uses MyBatis 3.5.13

    // Arrange
    String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
    String mapperId = "com.example.UserMapper.insert";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.INSERT);

    Object[] args = {ms, new Object()};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(1);

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    // Act
    Object result = interceptor.intercept(invocation);

    // Assert
    assertNotNull(result);
    assertEquals(1, result);
    verify(validator, times(1)).validate(any(SqlContext.class));
    verify(executor, times(1)).update(any(MappedStatement.class), any());
  }

  @Test
  void testMyBatis35_boundSql_shouldExtract() throws Throwable {
    // Verify BoundSql extraction works in MyBatis 3.5.x (enhanced API)

    // Arrange
    String expectedSql = "DELETE FROM sessions WHERE expired_at < NOW()";
    String mapperId = "com.example.SessionMapper.deleteExpired";

    MappedStatement ms = createMappedStatement(expectedSql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.DELETE);

    Object[] args = {ms, null};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(10);

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify SQL was extracted from BoundSql
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals(expectedSql, captor.getValue().getSql());
  }

  @Test
  void testMyBatis35_parameters_shouldExtract() throws Throwable {
    // Verify parameter extraction works in MyBatis 3.5.x

    // Arrange
    String sql = "SELECT * FROM products WHERE category = ?";
    String mapperId = "com.example.ProductMapper.selectByCategory";
    String category = "Electronics";

    MappedStatement ms = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {ms, category, RowBounds.DEFAULT, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify parameters were extracted
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertNotNull(captor.getValue().getParams());
    assertTrue(captor.getValue().getParams().containsKey("param"));
    assertEquals(category, captor.getValue().getParams().get("param"));
  }

  @Test
  void testVersionDetection_shouldIdentifyCorrectly() {
    // This test verifies we can detect MyBatis version if needed
    // Currently, our implementation doesn't need version-specific code,
    // but this test documents version compatibility

    // Arrange & Act
    String mybatisVersion = org.apache.ibatis.session.Configuration.class.getPackage()
        .getImplementationVersion();

    // Assert - version should be either 3.4.x or 3.5.x depending on profile
    // Note: Implementation version might be null in some environments
    if (mybatisVersion != null) {
      assertTrue(mybatisVersion.startsWith("3.4") || mybatisVersion.startsWith("3.5"),
          "MyBatis version should be 3.4.x or 3.5.x, got: " + mybatisVersion);
    } else {
      // If version is null, verify Configuration class exists (basic compatibility check)
      assertNotNull(configuration);
    }
    
    // Verify validator was not called (this test doesn't use interceptor)
    verify(validator, never()).validate(any(SqlContext.class));
  }

  @Test
  void testDynamicSql_bothVersions_shouldResolve() throws Throwable {
    // Verify dynamic SQL resolution works in both MyBatis versions
    // This simulates <if>, <where>, <foreach> tags being resolved

    // Arrange - simulate resolved dynamic SQL
    String resolvedSql = "SELECT * FROM users WHERE name = ? AND age > ?";
    String mapperId = "com.example.UserMapper.selectByNameAndAge";

    MappedStatement ms = createMappedStatement(resolvedSql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {ms, new Object(), RowBounds.DEFAULT, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify resolved SQL was captured
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals(resolvedSql, captor.getValue().getSql());

    // Verify SQL doesn't contain MyBatis dynamic tags
    assertFalse(resolvedSql.contains("<if"));
    assertFalse(resolvedSql.contains("<where"));
    assertFalse(resolvedSql.contains("<foreach"));
  }

  /**
   * Helper method to create MappedStatement.
   * This works with both MyBatis 3.4.x and 3.5.x.
   */
  private MappedStatement createMappedStatement(String sql, String mapperId,
                                                 org.apache.ibatis.mapping.SqlCommandType commandType) {
    SqlSource sqlSource = new SqlSource() {
      @Override
      public BoundSql getBoundSql(Object parameterObject) {
        return new BoundSql(configuration, sql, new ArrayList<>(), parameterObject);
      }
    };

    MappedStatement.Builder builder = new MappedStatement.Builder(
        configuration,
        mapperId,
        sqlSource,
        commandType
    );

    return builder.build();
  }
}








