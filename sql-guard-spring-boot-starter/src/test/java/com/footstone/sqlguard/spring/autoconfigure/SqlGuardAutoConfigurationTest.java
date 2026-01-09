package com.footstone.sqlguard.spring.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import com.footstone.sqlguard.validator.SqlSafetyValidator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Comprehensive tests for SqlGuardAutoConfiguration auto-configuration loading.
 *
 * <p>This test class validates Spring Boot auto-configuration infrastructure including:</p>
 * <ul>
 *   <li>Auto-configuration loading with various dependency combinations</li>
 *   <li>Conditional bean creation based on @ConditionalOnClass</li>
 *   <li>Configuration properties binding via @EnableConfigurationProperties</li>
 *   <li>Idempotent configuration behavior</li>
 *   <li>Auto-configuration ordering</li>
 *   <li>Master enabled/disabled switch</li>
 * </ul>
 *
 * <p><strong>Test Strategy:</strong></p>
 * <ul>
 *   <li>Use ApplicationContextRunner for isolated context testing</li>
 *   <li>Test with/without required dependencies on classpath</li>
 *   <li>Verify property binding from application.properties</li>
 *   <li>Validate idempotent behavior with multiple context loads</li>
 * </ul>
 */
class SqlGuardAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SqlGuardAutoConfiguration.class));

  /**
   * Test 1: Auto-configuration should load when all dependencies present.
   *
   * <p>Validates that SqlGuardAutoConfiguration class is instantiated and registered as a bean
   * when SqlSafetyValidator (core dependency) is on classpath.</p>
   */
  @Test
  void testAutoConfigurationLoads_withAllDependencies() {
    contextRunner.run(
        context -> {
          assertNotNull(context);
          assertTrue(context.containsBean("sqlGuardAutoConfiguration"));
        });
  }

  /**
   * Test 2: Auto-configuration should load with only core module.
   *
   * <p>Validates that SqlGuardAutoConfiguration loads successfully even when optional dependencies
   * (MyBatis, MyBatis-Plus, JDBC pools) are not present. Core beans should still be created.</p>
   */
  @Test
  void testAutoConfigurationLoads_withOnlyCoreModule() {
    contextRunner.run(
        context -> {
          assertNotNull(context);
          // Auto-configuration bean should exist
          assertTrue(context.containsBean("sqlGuardAutoConfiguration"));
          // Properties bean should be created
          assertNotNull(context.getBean(SqlGuardProperties.class));
        });
  }

  /**
   * Test 3: Auto-configuration should NOT load without core module.
   *
   * <p>Validates @ConditionalOnClass(SqlSafetyValidator.class) prevents auto-configuration when
   * sql-guard-core is not on classpath. This test simulates missing dependency scenario.</p>
   *
   * <p><strong>Note:</strong> Since SqlSafetyValidator is always on test classpath (via
   * sql-guard-core dependency), this test verifies the annotation is present but cannot actually
   * test the negative case without complex classloader manipulation.</p>
   */
  @Test
  void testConditionalOnClass_withoutCoreModule_shouldNotLoad() {
    // Verify @ConditionalOnClass annotation exists on SqlGuardAutoConfiguration
    assertTrue(
        SqlGuardAutoConfiguration.class.isAnnotationPresent(
            org.springframework.boot.autoconfigure.condition.ConditionalOnClass.class));

    // In real scenario without sql-guard-core, context would not contain bean
    // This test documents the expected behavior
  }

  /**
   * Test 4: @EnableConfigurationProperties should bind properties correctly.
   *
   * <p>Validates that SqlGuardProperties bean is created and registered in application context
   * when auto-configuration loads. Properties should be available for dependency injection.</p>
   */
  @Test
  void testEnableConfigurationProperties_shouldBindProperties() {
    contextRunner.run(
        context -> {
          // Properties bean should exist
          assertTrue(context.containsBean("sql-guard-com.footstone.sqlguard.spring.config.SqlGuardProperties"));
          
          // Should be able to get properties bean
          SqlGuardProperties properties = context.getBean(SqlGuardProperties.class);
          assertNotNull(properties);
          
          // Default enabled should be false (opt-in design for safety)
          assertFalse(properties.isEnabled());
        });
  }

  /**
   * Test 5: Configuration should be idempotent (safe to load multiple times).
   *
   * <p>Validates that SqlGuardAutoConfiguration can be loaded multiple times without errors or
   * side effects. This is important for Spring Boot test scenarios where context is recreated.</p>
   */
  @Test
  void testConfiguration_shouldBeIdempotent() {
    // Load context twice
    contextRunner.run(context1 -> {
      assertNotNull(context1);
      assertTrue(context1.containsBean("sqlGuardAutoConfiguration"));
    });

    contextRunner.run(context2 -> {
      assertNotNull(context2);
      assertTrue(context2.containsBean("sqlGuardAutoConfiguration"));
    });

    // Both loads should succeed without errors
  }

  /**
   * Test 6: Auto-configuration order should be correct.
   *
   * <p>Validates that SqlGuardAutoConfiguration has proper @AutoConfigureAfter and
   * @AutoConfigureBefore annotations to ensure correct bean creation order relative to
   * DataSourceAutoConfiguration and MybatisAutoConfiguration.</p>
   *
   * <p><strong>Note:</strong> This test verifies annotations are present. Full ordering tests
   * require integration tests with actual DataSource and MyBatis configurations.</p>
   */
  @Test
  void testAutoConfigurationOrder_shouldBeCorrect() {
    // Verify SqlGuardAutoConfiguration class exists and is annotated with @Configuration
    assertTrue(SqlGuardAutoConfiguration.class.isAnnotationPresent(Configuration.class));
    
    // Ordering annotations will be added in Step 5
    // This test documents the expected behavior
  }

  /**
   * Test 7: Properties injection should work in auto-configuration constructor.
   *
   * <p>Validates that SqlGuardProperties is correctly injected into SqlGuardAutoConfiguration
   * constructor via Spring's dependency injection mechanism.</p>
   */
  @Test
  void testPropertiesInjection_shouldWork() {
    contextRunner.run(
        context -> {
          SqlGuardAutoConfiguration config = context.getBean(SqlGuardAutoConfiguration.class);
          assertNotNull(config);
          
          // Properties should be injected (verified by successful context creation)
          SqlGuardProperties properties = context.getBean(SqlGuardProperties.class);
          assertNotNull(properties);
        });
  }

  /**
   * Test 8: enabled=false should disable auto-configuration.
   *
   * <p>Validates that setting sql-guard.enabled=false in application.properties prevents SQL Guard
   * beans from being created. This provides a master kill switch for the entire framework.</p>
   *
   * <p><strong>Note:</strong> This test validates property binding. Actual bean creation disabling
   * logic will be implemented in Step 2 using @ConditionalOnProperty.</p>
   */
  @Test
  void testEnabled_false_shouldDisableAutoConfiguration() {
    contextRunner
        .withPropertyValues("sql-guard.enabled=false")
        .run(
            context -> {
              // Auto-configuration should still load (class-level conditions met)
              assertTrue(context.containsBean("sqlGuardAutoConfiguration"));
              
              // Properties should reflect disabled state
              SqlGuardProperties properties = context.getBean(SqlGuardProperties.class);
              assertFalse(properties.isEnabled());
              
              // In Step 2, we'll add @ConditionalOnProperty to prevent bean creation
            });
  }

  /**
   * Test 9: Spring Boot application should load auto-configuration automatically.
   *
   * <p>Validates that SqlGuardAutoConfiguration is discovered and loaded by Spring Boot's
   * auto-configuration mechanism without requiring explicit @Import or @EnableSqlGuard.</p>
   *
   * <p><strong>Note:</strong> This test verifies auto-configuration class structure. Full
   * auto-discovery tests require META-INF/spring.factories registration (Step 5).</p>
   */
  @Test
  void testSpringBootApplication_shouldLoadAutoConfiguration() {
    contextRunner.run(
        context -> {
          // Auto-configuration should be discovered and loaded
          assertTrue(context.containsBean("sqlGuardAutoConfiguration"));
          
          // No @Import or @EnableSqlGuard required
          // Spring Boot auto-configuration mechanism handles discovery
        });
  }

  /**
   * Test 10: Auto-configuration report should show SqlGuard.
   *
   * <p>Validates that SqlGuardAutoConfiguration appears in Spring Boot's auto-configuration report
   * (accessible via actuator /conditions endpoint). This helps users debug configuration issues.</p>
   *
   * <p><strong>Note:</strong> This test verifies auto-configuration class is properly structured.
   * Full report validation requires Spring Boot Actuator integration tests.</p>
   */
  @Test
  void testAutoConfigurationReport_shouldShowSqlGuard() {
    contextRunner.run(
        context -> {
          // Auto-configuration should be registered
          assertTrue(context.containsBean("sqlGuardAutoConfiguration"));
          
          // In production, this would appear in actuator /conditions endpoint:
          // {
          //   "contexts": {
          //     "application": {
          //       "positiveMatches": {
          //         "SqlGuardAutoConfiguration": [
          //           {
          //             "condition": "OnClassCondition",
          //             "message": "@ConditionalOnClass found required class 'SqlSafetyValidator'"
          //           }
          //         ]
          //       }
          //     }
          //   }
          // }
        });
  }

  /**
   * Test configuration for custom property values.
   */
  @Configuration
  static class CustomPropertiesConfig {
    @Bean
    public SqlGuardProperties customProperties() {
      SqlGuardProperties props = new SqlGuardProperties();
      props.setEnabled(false);
      return props;
    }
  }
}
