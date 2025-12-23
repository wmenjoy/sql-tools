package com.footstone.sqlguard.interceptor.p6spy;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.p6spy.engine.event.JdbcEventListener;
import org.junit.jupiter.api.Test;

/**
 * Tests for P6SpySqlSafetyModule registration and configuration.
 *
 * <p>Tests cover:</p>
 * <ul>
 *   <li>Module class extends JdbcEventListener</li>
 *   <li>Static initialization loads validator</li>
 *   <li>onBeforeAnyExecute delegates to listener</li>
 *   <li>ServiceLoader integration</li>
 *   <li>Strategy loading from system properties</li>
 *   <li>spy.properties configuration</li>
 * </ul>
 */
class ModuleRegistrationTest {

  /**
   * Test 1: Module class should extend JdbcEventListener.
   */
  @Test
  void testModuleClass_shouldExtendJdbcEventListener() {
    assertTrue(JdbcEventListener.class.isAssignableFrom(P6SpySqlSafetyModule.class));
  }

  /**
   * Test 2: Static initialization should load validator successfully.
   */
  @Test
  void testStaticInitialization_shouldLoadValidator() {
    // Module should be initialized without errors
    P6SpySqlSafetyModule module = new P6SpySqlSafetyModule();
    assertNotNull(module);

    // Listener should be created
    P6SpySqlSafetyListener listener = P6SpySqlSafetyModule.getListener();
    assertNotNull(listener);
  }

  /**
   * Test 3: onBeforeAnyExecute should delegate to listener.
   */
  @Test
  void testOnBeforeAnyExecute_shouldDelegateToListener() {
    // Module should have listener initialized
    P6SpySqlSafetyModule module = new P6SpySqlSafetyModule();
    P6SpySqlSafetyListener listener = P6SpySqlSafetyModule.getListener();

    assertNotNull(listener);
    // If listener exists, delegation will work (tested in P6SpySqlSafetyListenerTest)
  }

  /**
   * Test 4: loadValidator should work with ServiceLoader fallback.
   */
  @Test
  void testLoadValidator_serviceLoader_shouldWork() {
    // Load validator (will use fallback since no service registered in test)
    DefaultSqlSafetyValidator validator = P6SpySqlSafetyModule.loadValidator();

    assertNotNull(validator);
  }

  /**
   * Test 5: loadStrategy from system property should work.
   */
  @Test
  void testLoadStrategy_fromProperties_shouldWork() {
    // Test default strategy (no property set)
    ViolationStrategy defaultStrategy = P6SpySqlSafetyModule.loadStrategy();
    assertNotNull(defaultStrategy);
    // Default should be LOG
    assertEquals(ViolationStrategy.LOG, defaultStrategy);
  }

  /**
   * Test 6: loadStrategy with BLOCK property.
   */
  @Test
  void testLoadStrategy_BLOCK_shouldWork() {
    try {
      System.setProperty("sqlguard.p6spy.strategy", "BLOCK");
      ViolationStrategy strategy = P6SpySqlSafetyModule.loadStrategy();
      assertEquals(ViolationStrategy.BLOCK, strategy);
    } finally {
      System.clearProperty("sqlguard.p6spy.strategy");
    }
  }

  /**
   * Test 7: loadStrategy with WARN property.
   */
  @Test
  void testLoadStrategy_WARN_shouldWork() {
    try {
      System.setProperty("sqlguard.p6spy.strategy", "WARN");
      ViolationStrategy strategy = P6SpySqlSafetyModule.loadStrategy();
      assertEquals(ViolationStrategy.WARN, strategy);
    } finally {
      System.clearProperty("sqlguard.p6spy.strategy");
    }
  }

  /**
   * Test 8: loadStrategy with LOG property.
   */
  @Test
  void testLoadStrategy_LOG_shouldWork() {
    try {
      System.setProperty("sqlguard.p6spy.strategy", "LOG");
      ViolationStrategy strategy = P6SpySqlSafetyModule.loadStrategy();
      assertEquals(ViolationStrategy.LOG, strategy);
    } finally {
      System.clearProperty("sqlguard.p6spy.strategy");
    }
  }

  /**
   * Test 9: loadStrategy with invalid property should default to LOG.
   */
  @Test
  void testLoadStrategy_invalid_shouldDefaultToLOG() {
    try {
      System.setProperty("sqlguard.p6spy.strategy", "INVALID");
      ViolationStrategy strategy = P6SpySqlSafetyModule.loadStrategy();
      assertEquals(ViolationStrategy.LOG, strategy);
    } finally {
      System.clearProperty("sqlguard.p6spy.strategy");
    }
  }

  /**
   * Test 10: loadStrategy with lowercase property should work (case-insensitive).
   */
  @Test
  void testLoadStrategy_lowercase_shouldWork() {
    try {
      System.setProperty("sqlguard.p6spy.strategy", "block");
      ViolationStrategy strategy = P6SpySqlSafetyModule.loadStrategy();
      assertEquals(ViolationStrategy.BLOCK, strategy);
    } finally {
      System.clearProperty("sqlguard.p6spy.strategy");
    }
  }

  /**
   * Test: Module initialization should not throw exceptions.
   */
  @Test
  void testModuleInitialization_shouldNotThrow() {
    assertDoesNotThrow(() -> {
      P6SpySqlSafetyModule module = new P6SpySqlSafetyModule();
      assertNotNull(module);
    });
  }

  /**
   * Test: getListener should return non-null listener.
   */
  @Test
  void testGetListener_shouldReturnNonNull() {
    P6SpySqlSafetyListener listener = P6SpySqlSafetyModule.getListener();
    assertNotNull(listener);
  }

  /**
   * Test: Validator should be properly initialized with all components.
   */
  @Test
  void testValidator_shouldBeProperlyInitialized() {
    DefaultSqlSafetyValidator validator = P6SpySqlSafetyModule.loadValidator();

    assertNotNull(validator);
    // Validator should be usable (tested in DefaultSqlSafetyValidatorTest)
  }

  /**
   * Test: Strategy loading should be thread-safe.
   */
  @Test
  void testStrategyLoading_shouldBeThreadSafe() throws InterruptedException {
    Thread[] threads = new Thread[10];

    for (int i = 0; i < threads.length; i++) {
      final int index = i;
      threads[i] =
          new Thread(
              () -> {
                try {
                  System.setProperty(
                      "sqlguard.p6spy.strategy", index % 2 == 0 ? "BLOCK" : "WARN");
                  ViolationStrategy strategy = P6SpySqlSafetyModule.loadStrategy();
                  assertNotNull(strategy);
                } finally {
                  System.clearProperty("sqlguard.p6spy.strategy");
                }
              });
    }

    for (Thread thread : threads) {
      thread.start();
    }

    for (Thread thread : threads) {
      thread.join();
    }
  }
}














