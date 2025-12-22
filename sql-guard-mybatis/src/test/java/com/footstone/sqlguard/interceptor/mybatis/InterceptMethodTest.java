package com.footstone.sqlguard.interceptor.mybatis;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.SqlCommandType;
import com.footstone.sqlguard.core.model.SqlContext;
import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.SqlSafetyValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

/**
 * Tests for intercept method implementation details.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>MappedStatement extraction</li>
 *   <li>Parameter extraction (single, map, POJO)</li>
 *   <li>RowBounds extraction (query vs update)</li>
 *   <li>BoundSql resolution with dynamic tags</li>
 *   <li>SqlContext building with all fields</li>
 *   <li>SqlCommandType conversion</li>
 *   <li>MapperId extraction</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InterceptMethodTest {

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
  void testExtractMappedStatement_shouldGet() throws Throwable {
    // Arrange
    String sql = "SELECT * FROM users";
    String mapperId = "com.example.UserMapper.selectAll";

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, null, RowBounds.DEFAULT, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify MappedStatement was extracted and used
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(contextCaptor.capture());
    SqlContext context = contextCaptor.getValue();
    assertNotNull(context);
    assertEquals(mapperId, context.getMapperId());
  }

  @Test
  void testExtractParameter_shouldHandleSingle() throws Throwable {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = ?";
    String mapperId = "com.example.UserMapper.selectById";
    Long singleParam = 123L;

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, singleParam, RowBounds.DEFAULT, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify single parameter extracted
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(contextCaptor.capture());
    SqlContext context = contextCaptor.getValue();
    assertNotNull(context.getParams());
    assertTrue(context.getParams().containsKey("param"));
    assertEquals(singleParam, context.getParams().get("param"));
  }

  @Test
  void testExtractParameter_shouldHandleMap() throws Throwable {
    // Arrange
    String sql = "SELECT * FROM users WHERE name = ? AND age = ?";
    String mapperId = "com.example.UserMapper.selectByNameAndAge";

    // Simulate @Param annotated parameters
    Map<String, Object> paramMap = new HashMap<>();
    paramMap.put("name", "John");
    paramMap.put("age", 30);

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, paramMap, RowBounds.DEFAULT, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify map parameters extracted
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(contextCaptor.capture());
    SqlContext context = contextCaptor.getValue();
    assertNotNull(context.getParams());
    assertEquals("John", context.getParams().get("name"));
    assertEquals(30, context.getParams().get("age"));
  }

  @Test
  void testExtractParameter_shouldHandlePojo() throws Throwable {
    // Arrange
    String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
    String mapperId = "com.example.UserMapper.insert";

    // Simulate POJO parameter
    UserPojo user = new UserPojo("John", "john@example.com");

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.INSERT);

    Object[] args = {mappedStatement, user};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(1);

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify POJO parameter extracted
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(contextCaptor.capture());
    SqlContext context = contextCaptor.getValue();
    assertNotNull(context.getParams());
    assertTrue(context.getParams().containsKey("param"));
    assertEquals(user, context.getParams().get("param"));
  }

  @Test
  void testExtractRowBounds_query_shouldGet() throws Throwable {
    // Arrange
    String sql = "SELECT * FROM users";
    String mapperId = "com.example.UserMapper.selectAll";
    RowBounds rowBounds = new RowBounds(10, 20);

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, null, rowBounds, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify RowBounds extracted from query
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(contextCaptor.capture());
    SqlContext context = contextCaptor.getValue();
    assertNotNull(context.getRowBounds());
    assertEquals(rowBounds, context.getRowBounds());
  }

  @Test
  void testExtractRowBounds_update_shouldBeNull() throws Throwable {
    // Arrange
    String sql = "UPDATE users SET name = ?";
    String mapperId = "com.example.UserMapper.updateName";

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.UPDATE);

    Object[] args = {mappedStatement, "NewName"};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(1);

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify RowBounds is null for update
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(contextCaptor.capture());
    SqlContext context = contextCaptor.getValue();
    assertNull(context.getRowBounds());
  }

  @Test
  void testGetBoundSql_shouldResolveDynamicTags() throws Throwable {
    // Arrange - simulate dynamic SQL with <if> tag resolved
    String dynamicSql = "SELECT * FROM users WHERE status = 'ACTIVE'";
    String mapperId = "com.example.UserMapper.selectActive";

    MappedStatement mappedStatement = createMappedStatement(dynamicSql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, null, RowBounds.DEFAULT, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify BoundSql contains resolved SQL
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(contextCaptor.capture());
    SqlContext context = contextCaptor.getValue();
    assertEquals(dynamicSql, context.getSql());
  }

  @Test
  void testBuildSqlContext_shouldPopulateAllFields() throws Throwable {
    // Arrange
    String sql = "SELECT * FROM users WHERE id = ?";
    String mapperId = "com.example.UserMapper.selectById";
    Long parameter = 123L;
    RowBounds rowBounds = new RowBounds(0, 10);

    MappedStatement mappedStatement = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, parameter, rowBounds, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify all SqlContext fields populated
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(contextCaptor.capture());
    SqlContext context = contextCaptor.getValue();

    assertNotNull(context.getSql());
    assertEquals(sql, context.getSql());

    assertNotNull(context.getType());
    assertEquals(SqlCommandType.SELECT, context.getType());

    assertNotNull(context.getMapperId());
    assertEquals(mapperId, context.getMapperId());

    assertNotNull(context.getRowBounds());
    assertEquals(rowBounds, context.getRowBounds());

    assertNotNull(context.getParams());
    assertTrue(context.getParams().containsKey("param"));
  }

  @Test
  void testSqlCommandType_shouldExtract() throws Throwable {
    // Test SELECT command type
    String sql = "SELECT * FROM users";
    String mapperId = "test.Mapper.select";
    MappedStatement selectMs = createMappedStatement(sql, mapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);
    Object[] selectArgs = {selectMs, null, RowBounds.DEFAULT, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());
    Invocation selectInvocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), selectArgs);
    interceptor.intercept(selectInvocation);
    
    ArgumentCaptor<SqlContext> captor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(captor.capture());
    assertEquals(SqlCommandType.SELECT, captor.getValue().getType());

    // Test UPDATE command type
    reset(validator, executor);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
    MappedStatement updateMs = createMappedStatement("UPDATE users SET name = ?", "test.Mapper.update",
        org.apache.ibatis.mapping.SqlCommandType.UPDATE);
    Object[] updateArgs = {updateMs, null};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(0);
    Invocation updateInvocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), updateArgs);
    interceptor.intercept(updateInvocation);
    verify(validator).validate(captor.capture());
    assertEquals(SqlCommandType.UPDATE, captor.getValue().getType());

    // Test DELETE command type
    reset(validator, executor);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
    MappedStatement deleteMs = createMappedStatement("DELETE FROM users", "test.Mapper.delete",
        org.apache.ibatis.mapping.SqlCommandType.DELETE);
    Object[] deleteArgs = {deleteMs, null};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(0);
    Invocation deleteInvocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), deleteArgs);
    interceptor.intercept(deleteInvocation);
    verify(validator).validate(captor.capture());
    assertEquals(SqlCommandType.DELETE, captor.getValue().getType());

    // Test INSERT command type
    reset(validator, executor);
    when(validator.validate(any(SqlContext.class))).thenReturn(ValidationResult.pass());
    MappedStatement insertMs = createMappedStatement("INSERT INTO users VALUES (?)", "test.Mapper.insert",
        org.apache.ibatis.mapping.SqlCommandType.INSERT);
    Object[] insertArgs = {insertMs, null};
    when(executor.update(any(MappedStatement.class), any())).thenReturn(0);
    Invocation insertInvocation = new Invocation(executor,
        Executor.class.getMethod("update", MappedStatement.class, Object.class), insertArgs);
    interceptor.intercept(insertInvocation);
    verify(validator).validate(captor.capture());
    assertEquals(SqlCommandType.INSERT, captor.getValue().getType());
  }

  @Test
  void testMapperId_shouldExtract() throws Throwable {
    // Arrange
    String sql = "SELECT * FROM users";
    String expectedMapperId = "com.example.mapper.UserMapper.selectAll";

    MappedStatement mappedStatement = createMappedStatement(sql, expectedMapperId,
        org.apache.ibatis.mapping.SqlCommandType.SELECT);

    Object[] args = {mappedStatement, null, RowBounds.DEFAULT, resultHandler};
    when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class),
        any(ResultHandler.class))).thenReturn(new ArrayList<>());

    Invocation invocation = new Invocation(executor,
        Executor.class.getMethod("query", MappedStatement.class, Object.class,
            RowBounds.class, ResultHandler.class), args);

    // Act
    interceptor.intercept(invocation);

    // Assert - verify MapperId extracted correctly
    ArgumentCaptor<SqlContext> contextCaptor = ArgumentCaptor.forClass(SqlContext.class);
    verify(validator).validate(contextCaptor.capture());
    SqlContext context = contextCaptor.getValue();
    assertEquals(expectedMapperId, context.getMapperId());
  }

  /**
   * Helper method to create MappedStatement.
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

  /**
   * Simple POJO for testing.
   */
  static class UserPojo {
    private String name;
    private String email;

    public UserPojo(String name, String email) {
      this.name = name;
      this.email = email;
    }

    public String getName() {
      return name;
    }

    public String getEmail() {
      return email;
    }
  }
}









