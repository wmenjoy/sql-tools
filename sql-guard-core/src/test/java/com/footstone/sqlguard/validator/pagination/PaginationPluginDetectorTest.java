package com.footstone.sqlguard.validator.pagination;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.plugin.Interceptor;
import org.junit.jupiter.api.Test;

/**
 * Test class for PaginationPluginDetector.
 * Tests plugin detection for MyBatis PageHelper and MyBatis-Plus PaginationInnerInterceptor.
 */
public class PaginationPluginDetectorTest {

  /**
   * Test that detector returns false when no pagination plugins are configured.
   */
  @Test
  public void testNoPaginationPlugin_shouldReturnFalse() {
    // No plugins configured
    PaginationPluginDetector detector = new PaginationPluginDetector(null, null);
    
    assertFalse(detector.hasPaginationPlugin(), 
        "Should return false when no pagination plugins are configured");
  }

  /**
   * Test that detector returns true when MyBatis PageHelper is configured.
   */
  @Test
  public void testPageHelper_shouldReturnTrue() {
    // Create a custom interceptor class that simulates PageInterceptor
    Interceptor mockPageInterceptor = new Interceptor() {
      @Override
      public Object intercept(org.apache.ibatis.plugin.Invocation invocation) {
        return null;
      }
      
      @Override
      public Object plugin(Object target) {
        return null;
      }
      
      @Override
      public void setProperties(java.util.Properties properties) {
      }
      
      @Override
      public String toString() {
        return "PageInterceptor";
      }
    };
    
    // Override getClass().getName() by creating anonymous class
    Interceptor pageInterceptor = new Interceptor() {
      @Override
      public Object intercept(org.apache.ibatis.plugin.Invocation invocation) {
        return null;
      }
      
      @Override
      public Object plugin(Object target) {
        return null;
      }
      
      @Override
      public void setProperties(java.util.Properties properties) {
      }
      
      // This class name will contain "PageInterceptor"
    };
    
    // Create class with PageInterceptor in package name
    Interceptor actualInterceptor = createPageInterceptor();
    
    List<Interceptor> interceptors = Arrays.asList(actualInterceptor);
    PaginationPluginDetector detector = new PaginationPluginDetector(interceptors, null);
    
    assertTrue(detector.hasPaginationPlugin(), 
        "Should return true when PageHelper PageInterceptor is configured");
  }
  
  /**
   * Helper method to create an interceptor with "PageInterceptor" in class name.
   */
  private Interceptor createPageInterceptor() {
    // Create anonymous class whose name will be checked
    return new com.github.pagehelper.PageInterceptor();
  }
  
  /**
   * Mock PageInterceptor class for testing.
   */
  static class com {
    static class github {
      static class pagehelper {
        static class PageInterceptor implements Interceptor {
          @Override
          public Object intercept(org.apache.ibatis.plugin.Invocation invocation) {
            return null;
          }
          
          @Override
          public Object plugin(Object target) {
            return null;
          }
          
          @Override
          public void setProperties(java.util.Properties properties) {
          }
        }
      }
    }
  }

  /**
   * Test that detector returns true when MyBatis-Plus PaginationInnerInterceptor is configured.
   * Uses a mock object that simulates MybatisPlusInterceptor with PaginationInnerInterceptor.
   */
  @Test
  public void testMpPaginationInnerInterceptor_shouldReturnTrue() throws Exception {
    // Create a mock object that simulates MybatisPlusInterceptor
    Object mybatisPlusInterceptor = createMockMybatisPlusInterceptor(true);
    
    PaginationPluginDetector detector = 
        new PaginationPluginDetector(null, mybatisPlusInterceptor);
    
    assertTrue(detector.hasPaginationPlugin(), 
        "Should return true when MyBatis-Plus PaginationInnerInterceptor is configured");
  }

  /**
   * Test that detector returns false when MybatisPlusInterceptor exists but has no 
   * PaginationInnerInterceptor.
   */
  @Test
  public void testMpInterceptorWithoutPagination_shouldReturnFalse() throws Exception {
    // Create a mock object that simulates MybatisPlusInterceptor without PaginationInnerInterceptor
    Object mybatisPlusInterceptor = createMockMybatisPlusInterceptor(false);
    
    PaginationPluginDetector detector = 
        new PaginationPluginDetector(null, mybatisPlusInterceptor);
    
    assertFalse(detector.hasPaginationPlugin(), 
        "Should return false when MybatisPlusInterceptor has no PaginationInnerInterceptor");
  }

  /**
   * Test that detector returns false when MyBatis interceptor list contains non-pagination 
   * interceptors.
   */
  @Test
  public void testNonPaginationInterceptors_shouldReturnFalse() {
    // Create a custom interceptor that doesn't have PageInterceptor in name
    Interceptor customInterceptor = new Interceptor() {
      @Override
      public Object intercept(org.apache.ibatis.plugin.Invocation invocation) {
        return null;
      }
      
      @Override
      public Object plugin(Object target) {
        return null;
      }
      
      @Override
      public void setProperties(java.util.Properties properties) {
      }
    };
    
    List<Interceptor> interceptors = Arrays.asList(customInterceptor);
    PaginationPluginDetector detector = new PaginationPluginDetector(interceptors, null);
    
    assertFalse(detector.hasPaginationPlugin(), 
        "Should return false when interceptor list contains no PageInterceptor");
  }

  /**
   * Test that detector returns true when both MyBatis and MyBatis-Plus pagination plugins 
   * are configured.
   */
  @Test
  public void testBothPlugins_shouldReturnTrue() throws Exception {
    // MyBatis PageHelper
    Interceptor pageInterceptor = createPageInterceptor();
    List<Interceptor> interceptors = Arrays.asList(pageInterceptor);
    
    // MyBatis-Plus PaginationInnerInterceptor
    Object mybatisPlusInterceptor = createMockMybatisPlusInterceptor(true);
    
    PaginationPluginDetector detector = 
        new PaginationPluginDetector(interceptors, mybatisPlusInterceptor);
    
    assertTrue(detector.hasPaginationPlugin(), 
        "Should return true when both pagination plugins are configured");
  }

  /**
   * Test that detector handles empty interceptor list correctly.
   */
  @Test
  public void testEmptyInterceptorList_shouldReturnFalse() {
    List<Interceptor> emptyList = new ArrayList<>();
    PaginationPluginDetector detector = new PaginationPluginDetector(emptyList, null);
    
    assertFalse(detector.hasPaginationPlugin(), 
        "Should return false when interceptor list is empty");
  }

  /**
   * Helper method to create a mock object that simulates MybatisPlusInterceptor.
   * Uses dynamic proxy to simulate getInterceptors() method.
   *
   * @param hasPaginationInterceptor whether to include PaginationInnerInterceptor
   * @return mock object simulating MybatisPlusInterceptor
   */
  private Object createMockMybatisPlusInterceptor(boolean hasPaginationInterceptor) throws Exception {
    // Create a list of inner interceptors
    final List<Object> innerInterceptors = new ArrayList<>();
    
    if (hasPaginationInterceptor) {
      // Add an object with PaginationInnerInterceptor class name
      innerInterceptors.add(new MockPaginationInnerInterceptor());
    } else {
      // Add an object with different class name
      innerInterceptors.add(new MockOtherInterceptor());
    }
    
    // Create an object with getInterceptors() method
    return new Object() {
      @SuppressWarnings("unused")
      public List<Object> getInterceptors() {
        return innerInterceptors;
      }
    };
  }
  
  /**
   * Mock class simulating PaginationInnerInterceptor.
   */
  static class MockPaginationInnerInterceptor {
    // Class name contains "PaginationInnerInterceptor"
  }
  
  /**
   * Mock class simulating other interceptor.
   */
  static class MockOtherInterceptor {
    // Class name doesn't contain "PaginationInnerInterceptor"
  }
}
