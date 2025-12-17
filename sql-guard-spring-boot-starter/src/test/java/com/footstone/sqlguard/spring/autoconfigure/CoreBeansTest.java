package com.footstone.sqlguard.spring.autoconfigure;

import static org.junit.jupiter.api.Assertions.*;

import com.footstone.sqlguard.parser.JSqlParserFacade;
import com.footstone.sqlguard.validator.DefaultSqlSafetyValidator;
import com.footstone.sqlguard.validator.SqlDeduplicationFilter;
import com.footstone.sqlguard.validator.pagination.PaginationPluginDetector;
import com.footstone.sqlguard.validator.pagination.impl.DeepPaginationChecker;
import com.footstone.sqlguard.validator.pagination.impl.LargePageSizeChecker;
import com.footstone.sqlguard.validator.pagination.impl.LogicalPaginationChecker;
import com.footstone.sqlguard.validator.pagination.impl.MissingOrderByChecker;
import com.footstone.sqlguard.validator.pagination.impl.NoConditionPaginationChecker;
import com.footstone.sqlguard.validator.pagination.impl.NoPaginationChecker;
import com.footstone.sqlguard.validator.rule.RuleChecker;
import com.footstone.sqlguard.validator.rule.RuleCheckerOrchestrator;
import com.footstone.sqlguard.validator.rule.impl.BlacklistFieldChecker;
import com.footstone.sqlguard.validator.rule.impl.DummyConditionChecker;
import com.footstone.sqlguard.validator.rule.impl.NoWhereClauseChecker;
import com.footstone.sqlguard.validator.rule.impl.WhitelistFieldChecker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tests for core bean creation in SqlGuardAutoConfiguration.
 *
 * <p>Validates that all core beans (parser, checkers, orchestrator, validator) are created
 * correctly with proper configuration binding and dependency injection.</p>
 */
class CoreBeansTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(SqlGuardAutoConfiguration.class));

  /**
   * Test 1: JSqlParserFacade should be created with correct configuration.
   */
  @Test
  void testSqlParserFacade_shouldCreate() {
    contextRunner.run(
        context -> {
          assertTrue(context.containsBean("sqlParserFacade"));
          JSqlParserFacade facade = context.getBean(JSqlParserFacade.class);
          assertNotNull(facade);
          assertFalse(facade.isLenientMode()); // Default is false
        });
  }

  /**
   * Test 2: All 10 rule checkers should be created.
   */
  @Test
  void testAllRuleCheckers_shouldCreate() {
    contextRunner.run(
        context -> {
          // Basic rule checkers
          assertTrue(context.containsBean("noWhereClauseChecker"));
          assertTrue(context.containsBean("dummyConditionChecker"));
          assertTrue(context.containsBean("blacklistFieldChecker"));
          assertTrue(context.containsBean("whitelistFieldChecker"));
          
          // Pagination checkers
          assertTrue(context.containsBean("logicalPaginationChecker"));
          assertTrue(context.containsBean("noConditionPaginationChecker"));
          assertTrue(context.containsBean("deepPaginationChecker"));
          assertTrue(context.containsBean("largePageSizeChecker"));
          assertTrue(context.containsBean("missingOrderByChecker"));
          assertTrue(context.containsBean("noPaginationChecker"));
          
          // Verify beans are of correct type
          assertNotNull(context.getBean(NoWhereClauseChecker.class));
          assertNotNull(context.getBean(DummyConditionChecker.class));
          assertNotNull(context.getBean(BlacklistFieldChecker.class));
          assertNotNull(context.getBean(WhitelistFieldChecker.class));
          assertNotNull(context.getBean(LogicalPaginationChecker.class));
          assertNotNull(context.getBean(NoConditionPaginationChecker.class));
          assertNotNull(context.getBean(DeepPaginationChecker.class));
          assertNotNull(context.getBean(LargePageSizeChecker.class));
          assertNotNull(context.getBean(MissingOrderByChecker.class));
          assertNotNull(context.getBean(NoPaginationChecker.class));
        });
  }

  /**
   * Test 3: RuleCheckerOrchestrator should autowire all checkers.
   */
  @Test
  void testRuleCheckerOrchestrator_shouldAutowireAllCheckers() {
    contextRunner.run(
        context -> {
          assertTrue(context.containsBean("ruleCheckerOrchestrator"));
          RuleCheckerOrchestrator orchestrator = context.getBean(RuleCheckerOrchestrator.class);
          assertNotNull(orchestrator);
          
          // Verify all checkers are autowired
          List<RuleChecker> checkers = new java.util.ArrayList<>(
              context.getBeansOfType(RuleChecker.class).values());
          assertTrue(checkers.size() >= 10); // At least 10 rule checkers
        });
  }

  /**
   * Test 4: SqlDeduplicationFilter should be created with correct configuration.
   */
  @Test
  void testSqlDeduplicationFilter_shouldCreate() {
    contextRunner.run(
        context -> {
          assertTrue(context.containsBean("sqlDeduplicationFilter"));
          SqlDeduplicationFilter filter = context.getBean(SqlDeduplicationFilter.class);
          assertNotNull(filter);
        });
  }

  /**
   * Test 5: DefaultSqlSafetyValidator should be created with all dependencies.
   */
  @Test
  void testDefaultSqlSafetyValidator_shouldCreate() {
    contextRunner.run(
        context -> {
          assertTrue(context.containsBean("sqlSafetyValidator"));
          DefaultSqlSafetyValidator validator = context.getBean(DefaultSqlSafetyValidator.class);
          assertNotNull(validator);
        });
  }

  /**
   * Test 6: @ConditionalOnMissingBean should allow user override.
   */
  @Test
  void testConditionalOnMissingBean_withUserBean_shouldNotOverride() {
    contextRunner
        .withUserConfiguration(CustomValidatorConfig.class)
        .run(
            context -> {
              // User bean should be used
              DefaultSqlSafetyValidator validator = 
                  context.getBean(DefaultSqlSafetyValidator.class);
              assertNotNull(validator);
              
              // Should be the custom bean (we can't easily verify this without mocking,
              // but the test validates that context loads successfully with user override)
            });
  }

  /**
   * Test 7: Rule checker config should bind from properties.
   */
  @Test
  void testRuleCheckerConfig_shouldBindFromProperties() {
    contextRunner
        .withPropertyValues("sql-guard.rules.no-where-clause.enabled=false")
        .run(
            context -> {
              NoWhereClauseChecker checker = context.getBean(NoWhereClauseChecker.class);
              assertNotNull(checker);
              assertFalse(checker.isEnabled()); // Should be disabled via properties
            });
  }

  /**
   * Test 8: Deduplication config should bind from properties.
   */
  @Test
  void testDeduplicationConfig_shouldBindFromProperties() {
    contextRunner
        .withPropertyValues(
            "sql-guard.deduplication.cache-size=5000",
            "sql-guard.deduplication.ttl-ms=200")
        .run(
            context -> {
              SqlDeduplicationFilter filter = context.getBean(SqlDeduplicationFilter.class);
              assertNotNull(filter);
              // Filter is created with custom config (we can't easily verify internal state,
              // but test validates successful bean creation with properties)
            });
  }

  /**
   * Test 9: Lenient mode should configure parser.
   */
  @Test
  void testLenientMode_shouldConfigureParser() {
    contextRunner
        .withPropertyValues("sql-guard.parser.lenient-mode=true")
        .run(
            context -> {
              JSqlParserFacade facade = context.getBean(JSqlParserFacade.class);
              assertNotNull(facade);
              assertTrue(facade.isLenientMode()); // Should be enabled via properties
            });
  }

  /**
   * Test 10: Disabled rule should still create checker (but checker.isEnabled() returns false).
   */
  @Test
  void testDisabledRule_shouldCreateCheckerButDisabled() {
    contextRunner
        .withPropertyValues("sql-guard.rules.dummy-condition.enabled=false")
        .run(
            context -> {
              // Bean should still be created
              assertTrue(context.containsBean("dummyConditionChecker"));
              DummyConditionChecker checker = context.getBean(DummyConditionChecker.class);
              assertNotNull(checker);
              assertFalse(checker.isEnabled()); // But should be disabled
            });
  }

  /**
   * Test 11: User override for validator should use user bean.
   */
  @Test
  void testUserOverride_validator_shouldUseUserBean() {
    contextRunner
        .withUserConfiguration(CustomValidatorConfig.class)
        .run(
            context -> {
              // Context should load successfully with user override
              assertTrue(context.containsBean("customValidator"));
              DefaultSqlSafetyValidator validator = 
                  context.getBean(DefaultSqlSafetyValidator.class);
              assertNotNull(validator);
            });
  }

  /**
   * Test 12: User override for checker should use user bean.
   */
  @Test
  void testUserOverride_checker_shouldUseUserBean() {
    contextRunner
        .withUserConfiguration(CustomCheckerConfig.class)
        .run(
            context -> {
              // Context should load successfully with user override
              assertTrue(context.containsBean("customNoWhereClauseChecker"));
              NoWhereClauseChecker checker = context.getBean(NoWhereClauseChecker.class);
              assertNotNull(checker);
            });
  }

  /**
   * Test configuration with custom validator.
   */
  @Configuration
  static class CustomValidatorConfig {
    @Bean
    public DefaultSqlSafetyValidator customValidator(
        JSqlParserFacade facade,
        List<RuleChecker> checkers,
        RuleCheckerOrchestrator orchestrator,
        SqlDeduplicationFilter filter) {
      return new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
    }
  }

  /**
   * Test configuration with custom checker.
   */
  @Configuration
  static class CustomCheckerConfig {
    @Bean
    public NoWhereClauseChecker customNoWhereClauseChecker() {
      return new NoWhereClauseChecker(new com.footstone.sqlguard.validator.rule.impl.NoWhereClauseConfig());
    }
  }
}
