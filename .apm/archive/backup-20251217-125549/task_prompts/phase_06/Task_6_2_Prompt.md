---
agent: Agent_Spring_Integration
task_ref: Task 6.2
depends_on:
  - Task 1.3 (Configuration Model with YAML Support)
ad_hoc_delegation: false
---

# Task 6.2 - Configuration Properties Binding

## Objective
Implement type-safe Spring Boot configuration properties with @ConfigurationProperties binding, JSR-303 validation, nested property support, IDE autocomplete via metadata generation, sensible defaults, and profile-specific configuration enabling declarative YAML-based SQL guard configuration.

## Context

**Spring Boot Configuration Properties**: @ConfigurationProperties provides type-safe binding from application.yml to Java objects with validation and IDE support. Prefix "sql-guard" maps all sql-guard.* properties to SqlGuardProperties fields.

**Nested Configuration**: Nested static classes mirror YAML structure (sql-guard.rules.noWhereClause.* → NoWhereClauseProperties). @NestedConfigurationProperty tells Spring Boot to recursively bind nested objects.

**Metadata Generation**: spring-configuration-metadata.json generated from Javadoc provides IDE autocomplete showing available properties with descriptions. This enables developers to discover configuration options without reading documentation.

**Validation**: JSR-303 validation (@Validated + annotations) enforces constraints at startup preventing invalid configurations. Fail-fast validation catches configuration errors before runtime.

## Dependencies

### Input from Phase 1:
- Task 1.3: SqlGuardConfigDefaults (default configuration values) ✅

### Independence:
- Task 6.2 can execute in parallel with Task 6.1
- Both depend only on completed Phases 1-4
- Task 6.1 references SqlGuardProperties but can use placeholder during parallel execution

## Implementation Steps

### Step 1: Properties Class TDD
**Goal**: Create SqlGuardProperties class with @ConfigurationProperties binding and basic structure

**Tasks**:
1. Add spring-boot-configuration-processor dependency to `sql-guard-spring-boot-starter/pom.xml`:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-configuration-processor</artifactId>
       <version>2.7.18</version>
       <optional>true</optional>
   </dependency>

   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-validation</artifactId>
       <version>2.7.18</version>
   </dependency>
   ```

2. Create `SqlGuardProperties` class:
   ```java
   @ConfigurationProperties(prefix = "sql-guard")
   @Validated
   public class SqlGuardProperties {

       /**
        * Enable SQL Safety Guard.
        */
       private boolean enabled = true;

       /**
        * Active violation strategy: LOG, WARN, or BLOCK.
        */
       @NotNull
       private String activeStrategy = "LOG";

       /**
        * Interceptor configuration.
        */
       @NestedConfigurationProperty
       private InterceptorsConfig interceptors = new InterceptorsConfig();

       /**
        * Deduplication configuration.
        */
       @NestedConfigurationProperty
       private DeduplicationConfig deduplication = new DeduplicationConfig();

       /**
        * Rule configuration.
        */
       @NestedConfigurationProperty
       private RulesConfig rules = new RulesConfig();

       /**
        * Parser configuration.
        */
       @NestedConfigurationProperty
       private ParserConfig parser = new ParserConfig();

       // Getters and setters
   }
   ```

3. Create test YAML configuration:
   ```yaml
   # src/test/resources/application-binding-test.yml
   sql-guard:
     enabled: true
     active-strategy: BLOCK
     interceptors:
       mybatis:
         enabled: true
       mybatis-plus:
         enabled: true
       jdbc:
         enabled: true
     deduplication:
       enabled: true
       cache-size: 2000
       ttl-ms: 200
     rules:
       no-where-clause:
         enabled: true
         risk-level: CRITICAL
   ```

**Test Requirements**:
- `SqlGuardPropertiesTest.java` (10 tests):
  - testYamlBinding_shouldBindAllProperties()
  - testDefaults_shouldMatchSqlGuardConfigDefaults()
  - testEnabled_shouldBindCorrectly()
  - testActiveStrategy_shouldBindCorrectly()
  - testNestedInterceptors_shouldBindCorrectly()
  - testNestedDeduplication_shouldBindCorrectly()
  - testNestedRules_shouldBindCorrectly()
  - testParserConfig_shouldBindCorrectly()
  - testGettersSetters_shouldWork()
  - testToString_shouldIncludeAllFields()

**Files to Create**:
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/SqlGuardProperties.java`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/SqlGuardPropertiesTest.java`
- `sql-guard-spring-boot-starter/src/test/resources/application-binding-test.yml`

---

### Step 2: Nested Configuration Classes
**Goal**: Implement nested static classes for all configuration sections

**Tasks**:
1. Implement InterceptorsConfig:
   ```java
   public static class InterceptorsConfig {
       /**
        * MyBatis interceptor configuration.
        */
       @NestedConfigurationProperty
       private MyBatisConfig mybatis = new MyBatisConfig();

       /**
        * MyBatis-Plus interceptor configuration.
        */
       @NestedConfigurationProperty
       private MyBatisPlusConfig mybatisPlus = new MyBatisPlusConfig();

       /**
        * JDBC interceptor configuration.
        */
       @NestedConfigurationProperty
       private JdbcConfig jdbc = new JdbcConfig();

       // Getters and setters
   }

   public static class MyBatisConfig {
       private boolean enabled = true;
       // Getters and setters
   }

   public static class MyBatisPlusConfig {
       private boolean enabled = true;
       // Getters and setters
   }

   public static class JdbcConfig {
       private boolean enabled = true;
       // Getters and setters
   }
   ```

2. Implement DeduplicationConfig:
   ```java
   public static class DeduplicationConfig {
       /**
        * Enable SQL deduplication.
        */
       private boolean enabled = true;

       /**
        * Cache size for deduplication (number of SQL statements).
        */
       @Min(1)
       @Max(100000)
       private int cacheSize = 1000;

       /**
        * Cache TTL in milliseconds.
        */
       @Min(1)
       @Max(60000)
       private long ttlMs = 100;

       // Getters and setters
   }
   ```

3. Implement RulesConfig and all rule property classes:
   ```java
   public static class RulesConfig {
       @NestedConfigurationProperty
       private NoWhereClauseProperties noWhereClause = new NoWhereClauseProperties();

       @NestedConfigurationProperty
       private DummyConditionProperties dummyCondition = new DummyConditionProperties();

       @NestedConfigurationProperty
       private BlacklistFieldProperties blacklistField = new BlacklistFieldProperties();

       @NestedConfigurationProperty
       private WhitelistFieldProperties whitelistField = new WhitelistFieldProperties();

       @NestedConfigurationProperty
       private LogicalPaginationProperties logicalPagination = new LogicalPaginationProperties();

       @NestedConfigurationProperty
       private NoConditionPaginationProperties noConditionPagination = new NoConditionPaginationProperties();

       @NestedConfigurationProperty
       private DeepPaginationProperties deepPagination = new DeepPaginationProperties();

       @NestedConfigurationProperty
       private LargePageSizeProperties largePageSize = new LargePageSizeProperties();

       @NestedConfigurationProperty
       private MissingOrderByProperties missingOrderBy = new MissingOrderByProperties();

       @NestedConfigurationProperty
       private NoPaginationProperties noPagination = new NoPaginationProperties();

       // Getters and setters
   }

   public static class NoWhereClauseProperties {
       private boolean enabled = true;
       private RiskLevel riskLevel = RiskLevel.CRITICAL;
       // Getters and setters
   }

   public static class DummyConditionProperties {
       private boolean enabled = true;
       private RiskLevel riskLevel = RiskLevel.HIGH;
       private List<String> patterns = Arrays.asList("1=1", "true", "'a'='a'");
       // Getters and setters
   }

   public static class BlacklistFieldProperties {
       private boolean enabled = true;
       private RiskLevel riskLevel = RiskLevel.HIGH;
       private List<String> blacklistFields = Arrays.asList("deleted", "status", "enabled");
       // Getters and setters
   }

   public static class WhitelistFieldProperties {
       private boolean enabled = true;
       private RiskLevel riskLevel = RiskLevel.HIGH;
       private Map<String, List<String>> whitelistFields = new HashMap<>();
       // Getters and setters
   }

   public static class LogicalPaginationProperties {
       private boolean enabled = true;
       private RiskLevel riskLevel = RiskLevel.CRITICAL;
       // Getters and setters
   }

   public static class NoConditionPaginationProperties {
       private boolean enabled = true;
       private RiskLevel riskLevel = RiskLevel.CRITICAL;
       // Getters and setters
   }

   public static class DeepPaginationProperties {
       private boolean enabled = true;
       private RiskLevel riskLevel = RiskLevel.MEDIUM;
       @Min(1)
       private int maxOffset = 10000;
       // Getters and setters
   }

   public static class LargePageSizeProperties {
       private boolean enabled = true;
       private RiskLevel riskLevel = RiskLevel.MEDIUM;
       @Min(1)
       private int maxPageSize = 1000;
       // Getters and setters
   }

   public static class MissingOrderByProperties {
       private boolean enabled = true;
       private RiskLevel riskLevel = RiskLevel.LOW;
       // Getters and setters
   }

   public static class NoPaginationProperties {
       private boolean enabled = true;
       private RiskLevel riskLevel = RiskLevel.MEDIUM;
       @Min(1)
       private long estimatedRowsThreshold = 10000;
       // Getters and setters
   }
   ```

4. Implement ParserConfig:
   ```java
   public static class ParserConfig {
       /**
        * Enable lenient parsing mode for SQL with syntax extensions.
        */
       private boolean lenientMode = false;

       // Getters and setters
   }
   ```

**Test Requirements**:
- `NestedPropertiesTest.java` (12 tests):
  - testInterceptorsConfig_shouldBindNested()
  - testMyBatisConfig_shouldBindCorrectly()
  - testMyBatisPlusConfig_shouldBindCorrectly()
  - testJdbcConfig_shouldBindCorrectly()
  - testDeduplicationConfig_shouldBindNested()
  - testRulesConfig_shouldBindNested()
  - testAllRuleProperties_shouldBindCorrectly()
  - testNoWhereClauseProperties_shouldHaveCorrectDefaults()
  - testDeepPaginationProperties_maxOffset_shouldBind()
  - testLargePageSizeProperties_maxPageSize_shouldBind()
  - testWhitelistFieldProperties_map_shouldBind()
  - testParserConfig_shouldBindCorrectly()

**Files to Modify**:
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/SqlGuardProperties.java`

**Files to Create**:
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/NestedPropertiesTest.java`

---

### Step 3: Validation and Metadata
**Goal**: Add JSR-303 validation and generate configuration metadata for IDE autocomplete

**Tasks**:
1. Add comprehensive validation annotations:
   ```java
   @ConfigurationProperties(prefix = "sql-guard")
   @Validated
   public class SqlGuardProperties {

       private boolean enabled = true;

       @NotNull
       @Pattern(regexp = "LOG|WARN|BLOCK", message = "activeStrategy must be LOG, WARN, or BLOCK")
       private String activeStrategy = "LOG";

       // ... rest of properties with validation
   }

   public static class DeduplicationConfig {
       private boolean enabled = true;

       @Min(value = 1, message = "cacheSize must be at least 1")
       @Max(value = 100000, message = "cacheSize must not exceed 100000")
       private int cacheSize = 1000;

       @Min(value = 1, message = "ttlMs must be at least 1ms")
       @Max(value = 60000, message = "ttlMs must not exceed 60000ms")
       private long ttlMs = 100;
   }
   ```

2. Verify metadata generation:
   ```bash
   # Build project and check metadata
   mvn clean compile
   # Verify target/classes/META-INF/spring-configuration-metadata.json exists
   ```

3. Create additional-spring-configuration-metadata.json for custom metadata:
   ```json
   {
     "properties": [
       {
         "name": "sql-guard.enabled",
         "type": "java.lang.Boolean",
         "description": "Enable SQL Safety Guard auto-configuration.",
         "defaultValue": true
       },
       {
         "name": "sql-guard.active-strategy",
         "type": "java.lang.String",
         "description": "Active violation strategy. Valid values: LOG (log only), WARN (log and continue), BLOCK (throw exception).",
         "defaultValue": "LOG"
       }
     ],
     "hints": [
       {
         "name": "sql-guard.active-strategy",
         "values": [
           {"value": "LOG", "description": "Log violations without blocking execution"},
           {"value": "WARN", "description": "Log violations with warning level"},
           {"value": "BLOCK", "description": "Block execution and throw SQLException"}
         ]
       }
     ]
   }
   ```

**Test Requirements**:
- `ValidationTest.java` (10 tests):
  - testValidation_withValidConfig_shouldPass()
  - testValidation_withInvalidActiveStrategy_shouldFail()
  - testValidation_withNegativeCacheSize_shouldFail()
  - testValidation_withZeroCacheSize_shouldFail()
  - testValidation_withExcessiveCacheSize_shouldFail()
  - testValidation_withNegativeTtl_shouldFail()
  - testValidation_withExcessiveTtl_shouldFail()
  - testValidation_withNegativeMaxOffset_shouldFail()
  - testValidation_withNegativeMaxPageSize_shouldFail()
  - testMetadataGeneration_shouldCreateMetadataFile()

**Files to Create**:
- `sql-guard-spring-boot-starter/src/main/resources/META-INF/additional-spring-configuration-metadata.json`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/ValidationTest.java`
- `sql-guard-spring-boot-starter/src/test/resources/application-invalid.yml` (for testing validation failures)

---

### Step 4: Integration with Auto-Configuration
**Goal**: Integrate SqlGuardProperties into SqlGuardAutoConfiguration for bean configuration

**Tasks**:
1. Update SqlGuardAutoConfiguration to use properties:
   ```java
   @Configuration
   @ConditionalOnClass(SqlSafetyValidator.class)
   @ConditionalOnProperty(prefix = "sql-guard", name = "enabled", havingValue = "true", matchIfMissing = true)
   @AutoConfigureAfter(DataSourceAutoConfiguration.class)
   @EnableConfigurationProperties(SqlGuardProperties.class)
   public class SqlGuardAutoConfiguration {

       private final SqlGuardProperties properties;

       public SqlGuardAutoConfiguration(SqlGuardProperties properties) {
           this.properties = properties;
       }

       @Bean
       @ConditionalOnMissingBean
       public JSqlParserFacade sqlParserFacade() {
           boolean lenientMode = properties.getParser().isLenientMode();
           return new JSqlParserFacade(lenientMode);
       }

       @Bean
       @ConditionalOnMissingBean
       @ConditionalOnProperty(prefix = "sql-guard.rules.no-where-clause", name = "enabled", havingValue = "true", matchIfMissing = true)
       public NoWhereClauseChecker noWhereClauseChecker() {
           NoWhereClauseConfig config = new NoWhereClauseConfig();
           config.setEnabled(properties.getRules().getNoWhereClause().isEnabled());
           config.setRiskLevel(properties.getRules().getNoWhereClause().getRiskLevel());
           return new NoWhereClauseChecker(config);
       }

       // Similar for all other rule checkers...

       @Bean
       @ConditionalOnMissingBean
       public SqlDeduplicationFilter sqlDeduplicationFilter() {
           if (!properties.getDeduplication().isEnabled()) {
               return SqlDeduplicationFilter.NOOP; // No-op filter
           }
           int cacheSize = properties.getDeduplication().getCacheSize();
           long ttlMs = properties.getDeduplication().getTtlMs();
           return new SqlDeduplicationFilter(cacheSize, ttlMs);
       }

       private ViolationStrategy getStrategyFromProperties() {
           String activeStrategy = properties.getActiveStrategy();
           switch (activeStrategy.toUpperCase()) {
               case "BLOCK":
                   return ViolationStrategy.BLOCK;
               case "WARN":
                   return ViolationStrategy.WARN;
               case "LOG":
               default:
                   return ViolationStrategy.LOG;
           }
       }
   }
   ```

2. Implement @RefreshScope support (optional, requires Spring Cloud):
   ```java
   @Configuration
   @ConditionalOnClass(name = "org.springframework.cloud.context.config.annotation.RefreshScope")
   public class RefreshScopeConfiguration {

       @Bean
       @RefreshScope
       @ConditionalOnMissingBean
       public DefaultSqlSafetyValidator refreshableValidator(
               JSqlParserFacade facade,
               List<RuleChecker> checkers,
               RuleCheckerOrchestrator orchestrator,
               SqlDeduplicationFilter filter) {
           return new DefaultSqlSafetyValidator(facade, checkers, orchestrator, filter);
       }
   }
   ```

**Test Requirements**:
- `PropertiesIntegrationTest.java` (10 tests):
  - testPropertiesInjection_shouldConfigureBeans()
  - testLenientMode_shouldConfigureParser()
  - testRuleEnabled_false_shouldNotCreateChecker()
  - testRuleEnabled_true_shouldCreateChecker()
  - testDeduplicationDisabled_shouldCreateNoopFilter()
  - testDeduplicationEnabled_shouldConfigureFilter()
  - testActiveStrategy_LOG_shouldSetLogStrategy()
  - testActiveStrategy_WARN_shouldSetWarnStrategy()
  - testActiveStrategy_BLOCK_shouldSetBlockStrategy()
  - testRefreshScope_withSpringCloud_shouldEnableRefresh()

**Files to Modify**:
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/autoconfigure/SqlGuardAutoConfiguration.java`

**Files to Create**:
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/autoconfigure/RefreshScopeConfiguration.java`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/PropertiesIntegrationTest.java`

---

### Step 5: Property Binding Tests
**Goal**: Comprehensive testing of property binding with various YAML configurations

**Tasks**:
1. Create comprehensive test YAML configurations:
   ```yaml
   # application-test-full.yml - Complete configuration
   sql-guard:
     enabled: true
     active-strategy: BLOCK
     interceptors:
       mybatis:
         enabled: true
       mybatis-plus:
         enabled: true
       jdbc:
         enabled: true
     deduplication:
       enabled: true
       cache-size: 5000
       ttl-ms: 500
     parser:
       lenient-mode: true
     rules:
       no-where-clause:
         enabled: true
         risk-level: CRITICAL
       dummy-condition:
         enabled: true
         risk-level: HIGH
         patterns:
           - "1=1"
           - "true"
           - "'a'='a'"
       blacklist-field:
         enabled: true
         risk-level: HIGH
         blacklist-fields:
           - deleted
           - status
           - enabled
       whitelist-field:
         enabled: true
         risk-level: HIGH
         whitelist-fields:
           user:
             - id
             - user_id
           order:
             - id
             - order_id
       deep-pagination:
         enabled: true
         risk-level: MEDIUM
         max-offset: 50000
       large-page-size:
         enabled: true
         risk-level: MEDIUM
         max-page-size: 2000
   ```

   ```yaml
   # application-test-minimal.yml - Minimal configuration (all defaults)
   sql-guard:
     enabled: true
   ```

   ```yaml
   # application-dev.yml - Development profile
   sql-guard:
     active-strategy: LOG
     deduplication:
       cache-size: 1000
   ```

   ```yaml
   # application-prod.yml - Production profile
   sql-guard:
     active-strategy: BLOCK
     deduplication:
       cache-size: 10000
       ttl-ms: 200
   ```

2. Create comprehensive binding test:
   ```java
   @SpringBootTest
   @TestPropertySource(locations = "classpath:application-test-full.yml")
   public class FullConfigurationBindingTest {

       @Autowired
       private SqlGuardProperties properties;

       @Test
       public void testFullConfiguration_allProperties_shouldBind() {
           assertTrue(properties.isEnabled());
           assertEquals("BLOCK", properties.getActiveStrategy());

           // Interceptors
           assertTrue(properties.getInterceptors().getMybatis().isEnabled());
           assertTrue(properties.getInterceptors().getMybatisPlus().isEnabled());
           assertTrue(properties.getInterceptors().getJdbc().isEnabled());

           // Deduplication
           assertTrue(properties.getDeduplication().isEnabled());
           assertEquals(5000, properties.getDeduplication().getCacheSize());
           assertEquals(500, properties.getDeduplication().getTtlMs());

           // Parser
           assertTrue(properties.getParser().isLenientMode());

           // Rules
           assertTrue(properties.getRules().getNoWhereClause().isEnabled());
           assertEquals(RiskLevel.CRITICAL, properties.getRules().getNoWhereClause().getRiskLevel());

           assertEquals(3, properties.getRules().getDummyCondition().getPatterns().size());
           assertEquals(3, properties.getRules().getBlacklistField().getBlacklistFields().size());

           Map<String, List<String>> whitelist = properties.getRules().getWhitelistField().getWhitelistFields();
           assertEquals(2, whitelist.size());
           assertTrue(whitelist.containsKey("user"));
           assertEquals(2, whitelist.get("user").size());

           assertEquals(50000, properties.getRules().getDeepPagination().getMaxOffset());
           assertEquals(2000, properties.getRules().getLargePageSize().getMaxPageSize());
       }
   }
   ```

3. Test profile-specific configurations:
   ```java
   @SpringBootTest
   @ActiveProfiles("dev")
   public class DevProfileTest {
       @Autowired
       private SqlGuardProperties properties;

       @Test
       public void testDevProfile_shouldUseLogStrategy() {
           assertEquals("LOG", properties.getActiveStrategy());
           assertEquals(1000, properties.getDeduplication().getCacheSize());
       }
   }

   @SpringBootTest
   @ActiveProfiles("prod")
   public class ProdProfileTest {
       @Autowired
       private SqlGuardProperties properties;

       @Test
       public void testProdProfile_shouldUseBlockStrategy() {
           assertEquals("BLOCK", properties.getActiveStrategy());
           assertEquals(10000, properties.getDeduplication().getCacheSize());
           assertEquals(200, properties.getDeduplication().getTtlMs());
       }
   }
   ```

**Test Requirements**:
- `PropertyBindingTest.java` (15 tests):
  - testFullConfiguration_shouldBindAllProperties()
  - testMinimalConfiguration_shouldUseDefaults()
  - testNestedInterceptors_shouldBindCorrectly()
  - testNestedDeduplication_shouldBindCorrectly()
  - testNestedRules_shouldBindCorrectly()
  - testListProperties_shouldBindCorrectly()
  - testMapProperties_shouldBindCorrectly()
  - testNumericProperties_shouldBindCorrectly()
  - testEnumProperties_shouldBindCorrectly()
  - testDevProfile_shouldOverrideDefaults()
  - testProdProfile_shouldOverrideDefaults()
  - testKebabCase_shouldBindToJavaCase()
  - testSnakeCase_shouldBindToJavaCase()
  - testEnvironmentVariables_shouldOverrideYaml()
  - testSystemProperties_shouldOverrideAll()

**Files to Create**:
- `sql-guard-spring-boot-starter/src/test/resources/application-test-full.yml`
- `sql-guard-spring-boot-starter/src/test/resources/application-test-minimal.yml`
- `sql-guard-spring-boot-starter/src/test/resources/application-dev.yml`
- `sql-guard-spring-boot-starter/src/test/resources/application-prod.yml`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/PropertyBindingTest.java`

---

## Expected Outcomes

### Functional Outcomes:
1. ✅ Type-safe configuration properties with @ConfigurationProperties
2. ✅ Nested property binding matching YAML structure
3. ✅ JSR-303 validation preventing invalid configurations
4. ✅ IDE autocomplete via spring-configuration-metadata.json
5. ✅ Sensible defaults from SqlGuardConfigDefaults
6. ✅ Profile-specific configuration support (dev/prod)
7. ✅ @RefreshScope support for runtime config updates

### Test Outcomes:
- Total new tests: **67 tests** (10 + 12 + 10 + 10 + 15 + 10)
- Test categories:
  - Basic properties: 10 tests
  - Nested properties: 12 tests
  - Validation: 10 tests
  - Integration: 10 tests
  - Binding scenarios: 15 tests
  - Profile-specific: 10 tests
- 100% pass rate required

### Architecture Outcomes:
- ✅ Type-safe configuration without string literals
- ✅ Fail-fast validation at startup
- ✅ IDE autocomplete for developer experience
- ✅ Profile-based configuration for environments
- ✅ Runtime config reload support (with Spring Cloud)

## Validation Criteria

### Must Pass Before Completion:
1. All 67 tests passing (100% pass rate)
2. YAML properties bind to Java fields correctly
3. Nested properties bind recursively
4. Default values match SqlGuardConfigDefaults
5. JSR-303 validation catches invalid configs at startup
6. spring-configuration-metadata.json generated
7. IDE autocomplete works in IntelliJ/Eclipse/VSCode
8. Profile-specific configs override defaults
9. Environment variables override YAML
10. Integration with auto-configuration works

### Code Quality:
1. Google Java Style compliance
2. Comprehensive Javadoc on all properties
3. Clear descriptions in metadata
4. Complete documentation

## Success Metrics

- ✅ 67 tests passing (100%)
- ✅ Type-safe property binding works
- ✅ Validation prevents invalid configs
- ✅ IDE autocomplete functional
- ✅ Profile support verified

## Timeline Estimate
- Step 1: 2 hours (Properties class + 10 tests)
- Step 2: 3 hours (Nested classes + 12 tests)
- Step 3: 2 hours (Validation + metadata + 10 tests)
- Step 4: 2 hours (Auto-configuration integration + 10 tests)
- Step 5: 3 hours (Comprehensive binding tests + 25 tests)

**Total**: ~12 hours

## Definition of Done

- [ ] All 67 tests passing
- [ ] SqlGuardProperties created with all nested classes
- [ ] YAML binding works for all properties
- [ ] JSR-303 validation enforces constraints
- [ ] spring-configuration-metadata.json generated
- [ ] IDE autocomplete verified
- [ ] Profile-specific configs work (dev/prod)
- [ ] Integration with auto-configuration complete
- [ ] Memory Log created
- [ ] Compatible with Spring Boot 2.x and 3.x

---

**End of Task Assignment**

Type-safe configuration properties enable declarative YAML-based SQL guard configuration with IDE autocomplete, validation, and profile support for environment-specific behavior.
