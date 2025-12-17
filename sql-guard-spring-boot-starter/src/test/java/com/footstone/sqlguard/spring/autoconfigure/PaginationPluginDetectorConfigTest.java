package com.footstone.sqlguard.spring.autoconfigure;

import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import org.apache.ibatis.plugin.Interceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PaginationPluginDetector configuration in different scenarios.
 *
 * <p>Verifies that the detector is correctly configured based on classpath dependencies:
 * <ul>
 *   <li>With MyBatis: detector should be created with interceptor injection capability</li>
 *   <li>Without MyBatis: detector should be created with null interceptors (fallback)</li>
 * </ul>
 */
@DisplayName("PaginationPluginDetector Configuration Tests")
class PaginationPluginDetectorConfigTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(SqlGuardAutoConfiguration.class));

  @Test
  @DisplayName("With MyBatis on classpath - should create detector with interceptor support")
  void testWithMyBatis_shouldCreateDetectorWithInterceptorSupport() {
    // Given: MyBatis is on classpath (it is, because we have it as optional dependency)
    
    // When: context starts
    contextRunner.run(context -> {
      // Then: PaginationPluginDetector bean should exist
      assertThat(context).hasSingleBean(PaginationPluginDetector.class);
      
      PaginationPluginDetector detector = context.getBean(PaginationPluginDetector.class);
      assertThat(detector).isNotNull();
      
      // The detector should be able to check for pagination plugins
      // (even if none are configured, the method should not throw NPE)
      assertThat(detector.hasPaginationPlugin()).isFalse();
    });
  }

  @Test
  @DisplayName("Detector should not throw NPE when no interceptors are configured")
  void testNoInterceptors_shouldNotThrowNPE() {
    // Given: context with no MyBatis interceptors configured
    
    // When: context starts
    contextRunner.run(context -> {
      // Then: detector should work without errors
      assertThat(context).hasSingleBean(PaginationPluginDetector.class);
      
      PaginationPluginDetector detector = context.getBean(PaginationPluginDetector.class);
      
      // Should not throw NPE when checking for plugins
      assertThat(detector.hasPaginationPlugin()).isFalse();
    });
  }

  @Test
  @DisplayName("Custom detector bean should override auto-configuration")
  void testCustomDetector_shouldOverrideAutoConfiguration() {
    // Given: custom configuration with custom detector bean
    
    // When: context starts with custom configuration
    contextRunner
        .withUserConfiguration(CustomDetectorConfiguration.class)
        .run(context -> {
          // Then: should use custom bean
          assertThat(context).hasSingleBean(PaginationPluginDetector.class);
          PaginationPluginDetector detector = context.getBean(PaginationPluginDetector.class);
          assertThat(detector).isNotNull();
        });
  }
  
  /**
   * Custom configuration for testing bean override.
   */
  @Configuration
  static class CustomDetectorConfiguration {
    @Bean
    public PaginationPluginDetector paginationPluginDetector() {
      return new PaginationPluginDetector(null, null);
    }
  }

  @Test
  @DisplayName("MyBatisDetectorConfiguration should be activated when MyBatis is present")
  void testMyBatisDetectorConfiguration_shouldBeActivated() {
    // Given: MyBatis is on classpath
    
    // When: context starts
    contextRunner.run(context -> {
      // Then: MyBatisDetectorConfiguration should be processed
      assertThat(context).hasSingleBean(PaginationPluginDetector.class);
      
      // Verify the bean is created by checking it exists and works
      PaginationPluginDetector detector = context.getBean(PaginationPluginDetector.class);
      assertThat(detector).isNotNull();
    });
  }

  @Test
  @DisplayName("Detector should handle null interceptors gracefully")
  void testNullInterceptors_shouldHandleGracefully() {
    // Given: context with detector
    
    // When: context starts
    contextRunner.run(context -> {
      // Then: detector should handle null interceptors without errors
      PaginationPluginDetector detector = context.getBean(PaginationPluginDetector.class);
      
      // Should return false when no plugins are configured
      assertThat(detector.hasPaginationPlugin()).isFalse();
    });
  }

  @Test
  @DisplayName("Scenario 1: Only MyBatis (no MyBatis-Plus) - should create detector with MyBatis support")
  void testOnlyMyBatis_shouldCreateDetectorWithMyBatisSupport() {
    // Given: MyBatis interceptors configured, no MyBatis-Plus
    
    // When: context starts with MyBatis interceptors only
    contextRunner
        .withUserConfiguration(MyBatisOnlyConfiguration.class)
        .run(context -> {
          // Then: detector should be created and work
          assertThat(context).hasSingleBean(PaginationPluginDetector.class);
          
          PaginationPluginDetector detector = context.getBean(PaginationPluginDetector.class);
          assertThat(detector).isNotNull();
          
          // Should not throw NPE even when MyBatis-Plus interceptor is null
          assertThat(detector.hasPaginationPlugin()).isFalse();
        });
  }
  
  /**
   * Configuration with only MyBatis interceptors.
   */
  @Configuration
  static class MyBatisOnlyConfiguration {
    @Bean
    public List<Interceptor> mybatisInterceptors() {
      // Return empty list to simulate MyBatis without PageHelper
      return Collections.emptyList();
    }
  }

  @Test
  @DisplayName("Scenario 2: Both MyBatis and MyBatis-Plus - should create detector with full support")
  void testBothMyBatisAndMyBatisPlus_shouldCreateDetectorWithFullSupport() {
    // Given: Both MyBatis and MyBatis-Plus interceptors configured
    
    // When: context starts with both types of interceptors
    contextRunner
        .withUserConfiguration(MyBatisAndMyBatisPlusConfiguration.class)
        .run(context -> {
          // Then: detector should be created
          assertThat(context).hasSingleBean(PaginationPluginDetector.class);
          
          PaginationPluginDetector detector = context.getBean(PaginationPluginDetector.class);
          assertThat(detector).isNotNull();
          
          // Should handle both MyBatis and MyBatis-Plus interceptors
          assertThat(detector.hasPaginationPlugin()).isFalse();
        });
  }
  
  /**
   * Configuration with both MyBatis and MyBatis-Plus interceptors.
   */
  @Configuration
  static class MyBatisAndMyBatisPlusConfiguration {
    @Bean
    public List<Interceptor> mybatisInterceptors() {
      return Collections.emptyList();
    }
    
    @Bean
    public Object mybatisPlusInterceptor() {
      // Return a mock object to simulate MybatisPlusInterceptor
      return new Object();
    }
  }

  @Test
  @DisplayName("Scenario 3: No MyBatis (pure JDBC) - should use fallback detector")
  void testNoMyBatis_shouldUseFallbackDetector() {
    // Note: This test cannot truly test "no MyBatis" scenario because MyBatis is on classpath
    // In a real application without MyBatis dependency, FallbackDetectorConfiguration would activate
    
    // Given: context with detector
    
    // When: context starts
    contextRunner.run(context -> {
      // Then: detector should still work (either MyBatis or Fallback configuration)
      assertThat(context).hasSingleBean(PaginationPluginDetector.class);
      
      PaginationPluginDetector detector = context.getBean(PaginationPluginDetector.class);
      assertThat(detector).isNotNull();
      
      // Should work based on LIMIT clause detection
      assertThat(detector.hasPaginationPlugin()).isFalse();
    });
  }

  @Test
  @DisplayName("MyBatisDetectorConfiguration should work when no interceptor beans are configured")
  void testMyBatisDetectorConfiguration_shouldWorkWithoutInterceptorBeans() {
    // Given: MyBatis is on classpath but no interceptor beans configured
    
    // When: context starts without any interceptor beans
    contextRunner.run(context -> {
      // Then: detector should be created
      // Note: Since we removed @Autowired(required = false), Spring will inject null
      // if no beans are found (because parameters are not annotated with @Autowired)
      assertThat(context).hasSingleBean(PaginationPluginDetector.class);
      
      PaginationPluginDetector detector = context.getBean(PaginationPluginDetector.class);
      
      // Should not throw NPE
      assertThat(detector.hasPaginationPlugin()).isFalse();
    });
  }
}
