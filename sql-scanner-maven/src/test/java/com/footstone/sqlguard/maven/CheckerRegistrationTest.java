package com.footstone.sqlguard.maven;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.footstone.sqlguard.config.SqlGuardConfig;
import com.footstone.sqlguard.config.SqlGuardConfigDefaults;
import com.footstone.sqlguard.validator.rule.RuleChecker;

/**
 * Tests to verify all 15 security checkers are registered in the Maven plugin.
 *
 * <p>This ensures feature parity with the CLI module.
 *
 * @author SQL Safety Guard Team
 */
class CheckerRegistrationTest {

  @TempDir
  File tempDir;

  private SqlGuardScanMojo mojo;

  @BeforeEach
  void setUp() {
    mojo = new SqlGuardScanMojo();
  }

  @Test
  void testAllCheckersRegistered_shouldHave15Checkers() throws Exception {
    // Given: default configuration
    SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();

    // When: create all checkers using reflection
    Method method = SqlGuardScanMojo.class.getDeclaredMethod("createAllCheckers", SqlGuardConfig.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<RuleChecker> checkers = (List<RuleChecker>) method.invoke(mojo, config);

    // Then: should have 15 checkers
    assertEquals(15, checkers.size(), 
        "Should have 15 security checkers registered to match CLI module");
  }

  @Test
  void testBasicSecurityCheckers_shouldBeRegistered() throws Exception {
    // Given: default configuration
    SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();

    // When: create all checkers
    Method method = SqlGuardScanMojo.class.getDeclaredMethod("createAllCheckers", SqlGuardConfig.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<RuleChecker> checkers = (List<RuleChecker>) method.invoke(mojo, config);

    // Then: basic checkers should be present
    assertCheckerTypePresent(checkers, "NoWhereClauseChecker");
    assertCheckerTypePresent(checkers, "DummyConditionChecker");
    assertCheckerTypePresent(checkers, "BlacklistFieldChecker");
    assertCheckerTypePresent(checkers, "WhitelistFieldChecker");
  }

  @Test
  void testSqlInjectionCheckers_shouldBeRegistered() throws Exception {
    // Given: default configuration
    SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();

    // When: create all checkers
    Method method = SqlGuardScanMojo.class.getDeclaredMethod("createAllCheckers", SqlGuardConfig.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<RuleChecker> checkers = (List<RuleChecker>) method.invoke(mojo, config);

    // Then: SQL injection checkers should be present
    assertCheckerTypePresent(checkers, "MultiStatementChecker");
    assertCheckerTypePresent(checkers, "SetOperationChecker");
    assertCheckerTypePresent(checkers, "SqlCommentChecker");
    assertCheckerTypePresent(checkers, "IntoOutfileChecker");
  }

  @Test
  void testDangerousOperationCheckers_shouldBeRegistered() throws Exception {
    // Given: default configuration
    SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();

    // When: create all checkers
    Method method = SqlGuardScanMojo.class.getDeclaredMethod("createAllCheckers", SqlGuardConfig.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<RuleChecker> checkers = (List<RuleChecker>) method.invoke(mojo, config);

    // Then: dangerous operation checkers should be present
    assertCheckerTypePresent(checkers, "DdlOperationChecker");
    assertCheckerTypePresent(checkers, "DangerousFunctionChecker");
    assertCheckerTypePresent(checkers, "CallStatementChecker");
  }

  @Test
  void testAccessControlCheckers_shouldBeRegistered() throws Exception {
    // Given: default configuration
    SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();

    // When: create all checkers
    Method method = SqlGuardScanMojo.class.getDeclaredMethod("createAllCheckers", SqlGuardConfig.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<RuleChecker> checkers = (List<RuleChecker>) method.invoke(mojo, config);

    // Then: access control checkers should be present
    assertCheckerTypePresent(checkers, "MetadataStatementChecker");
    assertCheckerTypePresent(checkers, "SetStatementChecker");
    assertCheckerTypePresent(checkers, "DeniedTableChecker");
    assertCheckerTypePresent(checkers, "ReadOnlyTableChecker");
  }

  @Test
  void testCheckerOrder_shouldMatchCliOrder() throws Exception {
    // Given: default configuration
    SqlGuardConfig config = SqlGuardConfigDefaults.getDefault();

    // When: create all checkers
    Method method = SqlGuardScanMojo.class.getDeclaredMethod("createAllCheckers", SqlGuardConfig.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<RuleChecker> checkers = (List<RuleChecker>) method.invoke(mojo, config);

    // Then: checkers should be in the correct order (matching CLI)
    String[] expectedOrder = {
        "NoWhereClauseChecker",
        "DummyConditionChecker",
        "BlacklistFieldChecker",
        "WhitelistFieldChecker",
        "MultiStatementChecker",
        "SetOperationChecker",
        "SqlCommentChecker",
        "IntoOutfileChecker",
        "DdlOperationChecker",
        "DangerousFunctionChecker",
        "CallStatementChecker",
        "MetadataStatementChecker",
        "SetStatementChecker",
        "DeniedTableChecker",
        "ReadOnlyTableChecker"
    };

    assertEquals(expectedOrder.length, checkers.size(), "Checker count should match");

    for (int i = 0; i < expectedOrder.length; i++) {
      String actualName = checkers.get(i).getClass().getSimpleName();
      assertEquals(expectedOrder[i], actualName, 
          "Checker at index " + i + " should be " + expectedOrder[i]);
    }
  }

  private void assertCheckerTypePresent(List<RuleChecker> checkers, String checkerName) {
    boolean found = checkers.stream()
        .anyMatch(c -> c.getClass().getSimpleName().equals(checkerName));
    assertTrue(found, "Should contain " + checkerName);
  }
}
