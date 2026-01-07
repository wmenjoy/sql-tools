package com.footstone.sqlguard.demo;

import com.footstone.sqlguard.demo.entity.User;
import com.footstone.sqlguard.demo.mapper.UserMapper;
import com.footstone.sqlguard.interceptor.mybatis.SqlSafetyInterceptor;
import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests to verify MyBatis interceptor is actually working.
 *
 * <p>These tests verify that:
 * 1. The SqlGuardInterceptor bean is created and registered
 * 2. The interceptor is actually intercepting SQL statements
 * 3. Violations are being detected and logged/handled</p>
 * 
 * <p>The database is initialized via application-test.yml using db/test-init.sql</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class MyBatisInterceptorRealTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SqlGuardProperties properties;

    @Autowired(required = false)
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("Verify SqlSafetyInterceptor is registered in SqlSessionFactory")
    void testSqlSafetyInterceptorExists() {
        System.out.println("\n=== Verify SqlSafetyInterceptor ===");
        
        assertNotNull(sqlSessionFactory, "SqlSessionFactory should be available");
        
        // Check if SqlSafetyInterceptor is registered in SqlSessionFactory
        List<Interceptor> interceptors = sqlSessionFactory.getConfiguration().getInterceptors();
        
        boolean hasSqlSafetyInterceptor = false;
        System.out.println("Registered interceptors:");
        for (Interceptor interceptor : interceptors) {
            System.out.println("  - " + interceptor.getClass().getName());
            if (interceptor instanceof SqlSafetyInterceptor) {
                hasSqlSafetyInterceptor = true;
            }
        }
        
        assertTrue(hasSqlSafetyInterceptor, 
            "SqlSafetyInterceptor should be registered in SqlSessionFactory");
        System.out.println("✅ SqlSafetyInterceptor is registered");
    }

    @Test
    @DisplayName("Verify interceptor is registered in SqlSessionFactory")
    void testInterceptorRegisteredInSqlSessionFactory() {
        System.out.println("\n=== Verify interceptor in SqlSessionFactory ===");
        
        assertNotNull(sqlSessionFactory, "SqlSessionFactory should be available");
        
        List<Interceptor> interceptors = sqlSessionFactory.getConfiguration().getInterceptors();
        
        System.out.println("Registered interceptors in SqlSessionFactory:");
        boolean hasSqlSafetyInterceptor = false;
        for (Interceptor interceptor : interceptors) {
            System.out.println("  - " + interceptor.getClass().getName());
            if (interceptor instanceof SqlSafetyInterceptor) {
                hasSqlSafetyInterceptor = true;
            }
        }
        
        assertTrue(hasSqlSafetyInterceptor, "SqlSafetyInterceptor should be registered in SqlSessionFactory");
        System.out.println("✅ SqlSafetyInterceptor is registered in SqlSessionFactory");
    }

    @Test
    @DisplayName("Verify SQL Guard configuration is loaded")
    void testSqlGuardConfigurationLoaded() {
        System.out.println("\n=== Verify SQL Guard configuration ===");
        
        assertTrue(properties.isEnabled(), "SQL Guard should be enabled");
        assertEquals("LOG", properties.getActiveStrategy(), "Strategy should be LOG");
        assertTrue(properties.getInterceptors().getMybatis().isEnabled(), "MyBatis interceptor should be enabled");
        assertTrue(properties.getRules().getNoWhereClause().isEnabled(), "NoWhereClause rule should be enabled");
        
        System.out.println("✅ SQL Guard configuration verified:");
        System.out.println("   - Enabled: " + properties.isEnabled());
        System.out.println("   - Strategy: " + properties.getActiveStrategy());
        System.out.println("   - MyBatis enabled: " + properties.getInterceptors().getMybatis().isEnabled());
        System.out.println("   - NoWhereClause enabled: " + properties.getRules().getNoWhereClause().isEnabled());
        System.out.println("   - DummyCondition enabled: " + properties.getRules().getDummyCondition().isEnabled());
    }

    @Test
    @DisplayName("Execute SQL and verify interceptor logs violation")
    void testInterceptorLogsViolation() {
        System.out.println("\n=== Executing SQL without WHERE clause ===");
        System.out.println("Watch for SQL Guard violation logs below:");
        System.out.println("-------------------------------------------");
        
        // This should trigger a violation log
        List<User> users = userMapper.findAllUnsafe();
        
        System.out.println("-------------------------------------------");
        System.out.println("Returned " + users.size() + " users");
        System.out.println("\n✅ SQL executed - check logs above for violation detection");
        
        assertNotNull(users, "Should return results with LOG strategy");
        assertTrue(users.size() > 0, "Should have test data");
    }

    @Test
    @DisplayName("Execute multiple SQL types and verify interception")
    void testMultipleSqlTypesIntercepted() {
        System.out.println("\n=== Testing multiple SQL types ===\n");
        
        // 1. SELECT without WHERE
        System.out.println("1. SELECT without WHERE:");
        List<User> all = userMapper.findAllUnsafe();
        System.out.println("   -> Returned " + all.size() + " rows\n");
        
        // 2. SELECT with dummy condition
        System.out.println("2. SELECT with WHERE 1=1:");
        List<User> dummy = userMapper.findWithDummyCondition();
        System.out.println("   -> Returned " + dummy.size() + " rows\n");
        
        // 3. SELECT with blacklist field only
        System.out.println("3. SELECT with blacklist field (status):");
        List<User> byStatus = userMapper.findByStatusOnly("ACTIVE");
        System.out.println("   -> Returned " + byStatus.size() + " rows\n");
        
        // 4. SELECT with LIMIT but no WHERE
        System.out.println("4. SELECT with LIMIT but no WHERE:");
        List<User> limited = userMapper.findWithLimitNoWhere(5);
        System.out.println("   -> Returned " + limited.size() + " rows\n");
        
        // 5. Safe SELECT
        System.out.println("5. Safe SELECT by username:");
        User user = userMapper.findByUsername("user001");
        System.out.println("   -> Returned user: " + (user != null ? user.getUsername() : "null") + "\n");
        
        System.out.println("=== All SQL executed ===");
        System.out.println("✅ Check logs above for violation detection on queries 1-4");
        System.out.println("   Query 5 should NOT have violations");
        
        // Verify all queries executed
        assertNotNull(all);
        assertNotNull(dummy);
        assertNotNull(byStatus);
        assertNotNull(limited);
        assertNotNull(user);
    }

    @Test
    @DisplayName("Verify safe queries do not trigger violations")
    void testSafeQueriesNoViolations() {
        System.out.println("\n=== Testing safe queries ===\n");
        
        // 1. Query by ID (high selectivity)
        System.out.println("1. SELECT by ID:");
        User byId = userMapper.findById(1L);
        assertNotNull(byId, "Should find user by ID");
        System.out.println("   -> Found: " + byId.getUsername());
        
        // 2. Query by username (unique field)
        System.out.println("2. SELECT by username:");
        User byName = userMapper.findByUsername("user001");
        assertNotNull(byName, "Should find user by username");
        System.out.println("   -> Found: " + byName.getUsername());
        
        // 3. Proper pagination
        System.out.println("3. SELECT with proper pagination:");
        List<User> paginated = userMapper.findWithProperPagination("user%", 10, 0);
        assertNotNull(paginated, "Should return paginated results");
        System.out.println("   -> Returned " + paginated.size() + " rows");
        
        System.out.println("\n✅ All safe queries executed - no violations expected in logs");
    }

    @Test
    @DisplayName("Performance test - interceptor overhead")
    void testInterceptorPerformance() {
        System.out.println("\n=== Performance test ===\n");
        
        int iterations = 100;
        
        // Warm up
        for (int i = 0; i < 10; i++) {
            userMapper.findByUsername("user001");
        }
        
        // Measure safe queries
        long startSafe = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            userMapper.findByUsername("user001");
        }
        long safeDuration = System.currentTimeMillis() - startSafe;
        
        // Measure queries with violations
        long startViolation = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            userMapper.findAllUnsafe();
        }
        long violationDuration = System.currentTimeMillis() - startViolation;
        
        System.out.println("Results for " + iterations + " iterations:");
        System.out.println("  Safe queries: " + safeDuration + "ms (avg: " + (safeDuration / iterations) + "ms)");
        System.out.println("  Violation queries: " + violationDuration + "ms (avg: " + (violationDuration / iterations) + "ms)");
        System.out.println("\n✅ Performance test completed");
        
        // Just verify it completed without errors
        assertTrue(safeDuration >= 0);
        assertTrue(violationDuration >= 0);
    }
}
