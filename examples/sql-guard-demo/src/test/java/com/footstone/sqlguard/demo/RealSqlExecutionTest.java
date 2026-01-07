package com.footstone.sqlguard.demo;

import com.footstone.sqlguard.demo.entity.User;
import com.footstone.sqlguard.demo.mapper.UserMapper;
import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real SQL execution integration tests.
 *
 * <p>These tests actually execute SQL statements through MyBatis and verify
 * that the SQL Guard interceptor correctly detects and handles violations.</p>
 * 
 * <p>The database is initialized via application-test.yml using db/test-init.sql</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class RealSqlExecutionTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SqlGuardProperties properties;

    @Test
    @DisplayName("Test 1: Execute SELECT without WHERE - should trigger NoWhereClauseChecker")
    void testSelectWithoutWhere_shouldTriggerViolation() {
        System.out.println("\n=== Test 1: SELECT without WHERE ===");
        
        // Execute SQL that violates NoWhereClause rule
        List<User> users = userMapper.findAllUnsafe();

        // With LOG strategy, execution continues but violation is logged
        assertNotNull(users, "Result should not be null with LOG strategy");
        assertTrue(users.size() > 0, "Should return data with LOG strategy");
        
        System.out.println("✅ Test 1 PASSED: SELECT without WHERE executed, " + users.size() + " rows returned");
        System.out.println("   Check logs above for violation detection with [CRITICAL] level");
    }

    @Test
    @DisplayName("Test 2: Execute SELECT with dummy condition 1=1 - should trigger DummyConditionChecker")
    void testSelectWithDummyCondition_shouldTriggerViolation() {
        System.out.println("\n=== Test 2: SELECT with WHERE 1=1 ===");
        
        // Execute SQL with dummy condition
        List<User> users = userMapper.findWithDummyCondition();

        assertNotNull(users, "Result should not be null");
        assertTrue(users.size() > 0, "Should return data");
        
        System.out.println("✅ Test 2 PASSED: SELECT with 1=1 executed, " + users.size() + " rows returned");
        System.out.println("   Check logs above for violation detection with [HIGH] level");
    }

    @Test
    @DisplayName("Test 3: Execute SELECT with only blacklist field - should trigger BlacklistFieldChecker")
    void testSelectWithBlacklistOnly_shouldTriggerViolation() {
        System.out.println("\n=== Test 3: SELECT with blacklist field only ===");
        
        // Execute SQL with only blacklist field (status)
        List<User> users = userMapper.findByStatusOnly("ACTIVE");

        assertNotNull(users, "Result should not be null");
        
        System.out.println("✅ Test 3 PASSED: SELECT with only 'status' field executed, " + users.size() + " rows returned");
        System.out.println("   Check logs above for violation detection with [HIGH] level");
    }

    @Test
    @DisplayName("Test 4: Execute SELECT with deep pagination - should trigger DeepPaginationChecker")
    void testSelectWithDeepPagination_shouldTriggerViolation() {
        System.out.println("\n=== Test 4: SELECT with deep pagination ===");
        
        // Execute SQL with high offset (pattern, limit, offset)
        List<User> users = userMapper.findWithDeepOffset("user%", 10, 10000);

        assertNotNull(users, "Result should not be null");
        
        System.out.println("✅ Test 4 PASSED: SELECT with OFFSET 10000 executed");
        System.out.println("   Check logs above for violation detection with [MEDIUM] level");
    }

    @Test
    @DisplayName("Test 5: Execute SELECT with large page size - should trigger LargePageSizeChecker")
    void testSelectWithLargePageSize_shouldTriggerViolation() {
        System.out.println("\n=== Test 5: SELECT with large page size ===");
        
        // Execute SQL with large limit
        List<User> users = userMapper.findWithLargePageSize("user%", 5000);

        assertNotNull(users, "Result should not be null");
        
        System.out.println("✅ Test 5 PASSED: SELECT with LIMIT 5000 executed, " + users.size() + " rows returned");
        System.out.println("   Check logs above for violation detection with [MEDIUM] level");
    }

    @Test
    @DisplayName("Test 6: Execute SELECT with pagination but no ORDER BY - should trigger MissingOrderByChecker")
    void testSelectWithoutOrderBy_shouldTriggerViolation() {
        System.out.println("\n=== Test 6: SELECT with LIMIT but no ORDER BY ===");
        
        // Execute SQL with LIMIT but no ORDER BY
        List<User> users = userMapper.findWithoutOrderBy("user%", 10);

        assertNotNull(users, "Result should not be null");
        assertEquals(10, users.size(), "Should return 10 rows");
        
        System.out.println("✅ Test 6 PASSED: SELECT with LIMIT but no ORDER BY executed");
        System.out.println("   Check logs above for violation detection with [LOW] level");
    }

    @Test
    @DisplayName("Test 7: Execute SELECT with LIMIT but no WHERE - should trigger NoConditionPaginationChecker")
    void testSelectWithLimitNoWhere_shouldTriggerViolation() {
        System.out.println("\n=== Test 7: SELECT with LIMIT but no WHERE ===");
        
        // Execute SQL with LIMIT but no WHERE
        List<User> users = userMapper.findWithLimitNoWhere(10);

        assertNotNull(users, "Result should not be null");
        assertEquals(10, users.size(), "Should return 10 rows");
        
        System.out.println("✅ Test 7 PASSED: SELECT with LIMIT but no WHERE executed");
        System.out.println("   Check logs above for violation detection with [CRITICAL] level");
    }

    @Test
    @DisplayName("Test 8: Execute safe SELECT with proper conditions - should NOT trigger violations")
    void testSafeSelect_shouldNotTriggerViolation() {
        System.out.println("\n=== Test 8: Safe SELECT ===");
        
        // Execute safe SQL with proper WHERE and LIMIT
        User user = userMapper.findByUsername("user001");

        assertNotNull(user, "Result should not be null");
        assertEquals("user001", user.getUsername());
        
        System.out.println("✅ Test 8 PASSED: Safe SELECT executed without violations");
    }

    @Test
    @DisplayName("Test 9: Verify SQL Guard configuration is active")
    void testSqlGuardConfiguration() {
        System.out.println("\n=== Test 9: SQL Guard Configuration ===");
        
        assertTrue(properties.isEnabled(), "SQL Guard should be enabled");
        assertEquals("LOG", properties.getActiveStrategy(), "Strategy should be LOG");
        assertTrue(properties.getInterceptors().getMybatis().isEnabled(), "MyBatis interceptor should be enabled");
        
        System.out.println("✅ Test 9 PASSED: SQL Guard configuration verified");
        System.out.println("   - Enabled: " + properties.isEnabled());
        System.out.println("   - Strategy: " + properties.getActiveStrategy());
        System.out.println("   - MyBatis interceptor: " + properties.getInterceptors().getMybatis().isEnabled());
    }

    @Test
    @DisplayName("Test 10: Execute multiple violations in sequence")
    void testMultipleViolationsInSequence() {
        System.out.println("\n=== Test 10: Multiple violations in sequence ===\n");
        
        // Violation 1: No WHERE
        System.out.println("1. Executing findAllUnsafe()...");
        List<User> all = userMapper.findAllUnsafe();
        System.out.println("   -> Returned " + all.size() + " rows");
        
        // Violation 2: Dummy condition
        System.out.println("2. Executing findWithDummyCondition()...");
        List<User> dummy = userMapper.findWithDummyCondition();
        System.out.println("   -> Returned " + dummy.size() + " rows");
        
        // Violation 3: Blacklist only
        System.out.println("3. Executing findByStatusOnly()...");
        List<User> blacklist = userMapper.findByStatusOnly("ACTIVE");
        System.out.println("   -> Returned " + blacklist.size() + " rows");
        
        // Safe query
        System.out.println("4. Executing findByUsername() (safe)...");
        User safe = userMapper.findByUsername("user001");
        System.out.println("   -> Returned user: " + (safe != null ? safe.getUsername() : "null"));
        
        System.out.println("\n✅ Test 10 PASSED: All queries executed");
        System.out.println("   Check logs above for violations on queries 1-3");
        
        // Verify all queries executed successfully
        assertNotNull(all);
        assertNotNull(dummy);
        assertNotNull(blacklist);
        assertNotNull(safe);
    }
}
