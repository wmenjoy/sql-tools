package com.footstone.sqlguard.interceptor.hikari;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.core.model.ValidationResult;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Unit tests for HikariSqlSafetyConfiguration.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>DataSource wrapping</li>
 *   <li>Safe DataSource creation from config</li>
 *   <li>Spring BeanPostProcessor integration</li>
 *   <li>Leak detection compatibility</li>
 * </ul>
 */
class HikariConfigurationTest {

  @Mock
  private DefaultSqlSafetyValidator validator;

  @Mock
  private HikariDataSource mockHikariDataSource;

  @Mock
  private Connection mockConnection;

  @BeforeEach
  void setUp() throws SQLException {
    MockitoAnnotations.openMocks(this);

    // Setup default mock behaviors
    when(validator.validate(any())).thenReturn(ValidationResult.pass());
    when(mockHikariDataSource.getConnection()).thenReturn(mockConnection);
    when(mockHikariDataSource.getPoolName()).thenReturn("test-pool");
  }

  @Test
  void testWrapDataSource_nullDataSource_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> HikariSqlSafetyConfiguration.wrapDataSource(null, validator, ViolationStrategy.BLOCK)
    );
    assertEquals("hikariDataSource cannot be null", exception.getMessage());
  }

  @Test
  void testWrapDataSource_nullValidator_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> HikariSqlSafetyConfiguration.wrapDataSource(
            mockHikariDataSource, null, ViolationStrategy.BLOCK)
    );
    assertEquals("validator cannot be null", exception.getMessage());
  }

  @Test
  void testWrapDataSource_nullStrategy_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> HikariSqlSafetyConfiguration.wrapDataSource(
            mockHikariDataSource, validator, null)
    );
    assertEquals("strategy cannot be null", exception.getMessage());
  }

  @Test
  void testWrapDataSource_shouldReturnProxy() {
    // When
    DataSource wrappedDs = HikariSqlSafetyConfiguration.wrapDataSource(
        mockHikariDataSource, validator, ViolationStrategy.BLOCK);

    // Then
    assertNotNull(wrappedDs);
    assertTrue(Proxy.isProxyClass(wrappedDs.getClass()));
  }

  @Test
  void testGetConnection_shouldReturnProxy() throws SQLException {
    // When
    DataSource wrappedDs = HikariSqlSafetyConfiguration.wrapDataSource(
        mockHikariDataSource, validator, ViolationStrategy.BLOCK);
    Connection conn = wrappedDs.getConnection();

    // Then
    assertNotNull(conn);
    assertTrue(Proxy.isProxyClass(conn.getClass()));
  }

  @Test
  void testCreateSafeDataSource_nullConfig_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> HikariSqlSafetyConfiguration.createSafeDataSource(
            null, validator, ViolationStrategy.BLOCK)
    );
    assertEquals("config cannot be null", exception.getMessage());
  }

  @Test
  void testCreateSafeDataSource_shouldCreateWrappedDataSource() {
    // Given
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:h2:mem:test");
    config.setDriverClassName("org.h2.Driver");

    // When
    DataSource wrappedDs = HikariSqlSafetyConfiguration.createSafeDataSource(
        config, validator, ViolationStrategy.BLOCK);

    // Then
    assertNotNull(wrappedDs);
    assertTrue(Proxy.isProxyClass(wrappedDs.getClass()));
  }

  @Test
  void testCreateBeanPostProcessor_nullValidator_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> HikariSqlSafetyConfiguration.createBeanPostProcessor(
            null, ViolationStrategy.BLOCK)
    );
    assertEquals("validator cannot be null", exception.getMessage());
  }

  @Test
  void testCreateBeanPostProcessor_nullStrategy_shouldThrowException() {
    IllegalArgumentException exception = assertThrows(
        IllegalArgumentException.class,
        () -> HikariSqlSafetyConfiguration.createBeanPostProcessor(validator, null)
    );
    assertEquals("strategy cannot be null", exception.getMessage());
  }

  @Test
  void testBeanPostProcessor_shouldWrapHikariDataSource() {
    // Given
    BeanPostProcessor postProcessor = HikariSqlSafetyConfiguration.createBeanPostProcessor(
        validator, ViolationStrategy.WARN);

    // When
    Object result = postProcessor.postProcessAfterInitialization(
        mockHikariDataSource, "testDataSource");

    // Then
    assertNotNull(result);
    assertTrue(result instanceof DataSource);
    assertTrue(Proxy.isProxyClass(result.getClass()));
  }

  @Test
  void testBeanPostProcessor_nonHikariBean_shouldNotWrap() {
    // Given
    BeanPostProcessor postProcessor = HikariSqlSafetyConfiguration.createBeanPostProcessor(
        validator, ViolationStrategy.WARN);
    String nonDataSourceBean = "some string bean";

    // When
    Object result = postProcessor.postProcessAfterInitialization(
        nonDataSourceBean, "testBean");

    // Then
    assertSame(nonDataSourceBean, result);
  }

  @Test
  void testIsLeakDetectionCompatible_nullDataSource_shouldReturnFalse() {
    // When
    boolean compatible = HikariSqlSafetyConfiguration.isLeakDetectionCompatible(null);

    // Then
    assertFalse(compatible);
  }

  @Test
  void testIsLeakDetectionCompatible_wrappedDataSource_shouldReturnTrue() throws SQLException {
    // Given
    DataSource wrappedDs = HikariSqlSafetyConfiguration.wrapDataSource(
        mockHikariDataSource, validator, ViolationStrategy.BLOCK);

    // When
    boolean compatible = HikariSqlSafetyConfiguration.isLeakDetectionCompatible(wrappedDs);

    // Then
    assertTrue(compatible);
  }
}

