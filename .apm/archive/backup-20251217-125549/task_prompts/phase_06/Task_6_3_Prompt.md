---
agent: Agent_Spring_Integration
task_ref: Task 6.3
depends_on:
  - Task 2.13 (DefaultSqlSafetyValidator Assembly)
  - Task 6.2 (Configuration Properties Binding)
ad_hoc_delegation: false
---

# Task 6.3 - Config Center Extension Points

## Objective
Implement extension point architecture for config center integration enabling hot-reload from Apollo and Nacos configuration centers, providing ConfigCenterAdapter SPI, implementing adapters with @ConditionalOnClass guards, integrating with validator for thread-safe runtime config updates, and documenting extension pattern for custom config center implementations.

## Context

**Config Center Integration**: Config centers (Apollo, Nacos) enable runtime configuration updates without application restart - critical for production tuning (adjust risk levels, enable/disable rules, change strategies).

**Extension Point Pattern**: ConfigCenterAdapter provides uniform interface abstracting Apollo/Nacos differences. @ConditionalOnClass ensures adapters only created when Apollo/Nacos client libraries present - prevents startup failures in environments without config centers.

**Thread-Safe Reload**: Config changes occur asynchronously and must be applied atomically. Use AtomicReference or volatile for config holder, synchronize validator state updates to prevent race conditions during concurrent validation and reload.

**Cache Invalidation**: On config change, deduplication cache and JSqlParser cache must be cleared to prevent stale validation results from cached config.

## Dependencies

### Input from Phase 2:
- Task 2.13: DefaultSqlSafetyValidator (validator with reload support) ✅

### Input from Task 6.2:
- SqlGuardProperties (configuration properties) ⏳ Task 6.2 executing in parallel
- Task 6.3 must wait for Task 6.2 completion before starting

### Sequential Dependency:
- Task 6.3 CANNOT start until Task 6.2 completes
- Requires SqlGuardProperties class from Task 6.2

## Implementation Steps

### Step 1: Extension Point Interface TDD
**Goal**: Define ConfigCenterAdapter interface and ConfigChangeEvent class

**Tasks**:
1. Create `ConfigCenterAdapter` interface:
   ```java
   /**
    * Extension point for config center integration.
    * Implement this interface to support custom configuration centers.
    */
   public interface ConfigCenterAdapter {

       /**
        * Called when configuration changes.
        *
        * @param event configuration change event
        */
       void onConfigChange(ConfigChangeEvent event);

       /**
        * Trigger full configuration reload.
        */
       void reloadConfig();

       /**
        * Get adapter name for logging.
        *
        * @return adapter name
        */
       default String getName() {
           return this.getClass().getSimpleName();
       }
   }
   ```

2. Create `ConfigChangeEvent` class:
   ```java
   /**
    * Event representing configuration changes.
    */
   public class ConfigChangeEvent {

       private final Map<String, String> changedKeys;
       private final String namespace;
       private final long timestamp;

       public ConfigChangeEvent(Map<String, String> changedKeys, String namespace) {
           this.changedKeys = Collections.unmodifiableMap(new HashMap<>(changedKeys));
           this.namespace = namespace;
           this.timestamp = System.currentTimeMillis();
       }

       /**
        * Check if specific key changed.
        *
        * @param key property key
        * @return true if key changed
        */
       public boolean hasChanged(String key) {
           return changedKeys.containsKey(key);
       }

       /**
        * Get new value for changed key.
        *
        * @param key property key
        * @return new value or null if not changed
        */
       public String getNewValue(String key) {
           return changedKeys.get(key);
       }

       /**
        * Get all changed keys.
        *
        * @return unmodifiable map of changed keys
        */
       public Map<String, String> getChangedKeys() {
           return changedKeys;
       }

       // Getters
   }
   ```

3. Create `ConfigReloadListener` interface:
   ```java
   /**
    * Listener for configuration reload events.
    */
   @FunctionalInterface
   public interface ConfigReloadListener {

       /**
        * Called after configuration reloaded.
        *
        * @param oldConfig previous configuration
        * @param newConfig new configuration
        */
       void onConfigReloaded(SqlGuardConfig oldConfig, SqlGuardConfig newConfig);
   }
   ```

**Test Requirements**:
- `ConfigCenterAdapterTest.java` (8 tests):
  - testConfigChangeEvent_shouldContainChangedKeys()
  - testConfigChangeEvent_hasChanged_shouldReturnCorrectly()
  - testConfigChangeEvent_getNewValue_shouldReturnValue()
  - testConfigChangeEvent_immutable_shouldNotModify()
  - testConfigCenterAdapter_getName_shouldReturnClassName()
  - testConfigReloadListener_shouldReceiveEvents()
  - testConfigChangeEvent_namespace_shouldStore()
  - testConfigChangeEvent_timestamp_shouldBeSet()

**Files to Create**:
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/ConfigCenterAdapter.java`
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/ConfigChangeEvent.java`
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/ConfigReloadListener.java`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/center/ConfigCenterAdapterTest.java`

---

### Step 2: Apollo Adapter Implementation
**Goal**: Implement Apollo config center adapter with @ApolloConfigChangeListener

**Tasks**:
1. Add Apollo client dependency (provided scope):
   ```xml
   <dependency>
       <groupId>com.ctrip.framework.apollo</groupId>
       <artifactId>apollo-client</artifactId>
       <version>2.0.1</version>
       <scope>provided</scope>
   </dependency>
   ```

2. Create `ApolloConfigCenterAdapter`:
   ```java
   @Configuration
   @ConditionalOnClass(name = "com.ctrip.framework.apollo.Config")
   @ConditionalOnProperty(prefix = "sql-guard.config-center.apollo", name = "enabled", havingValue = "true")
   public class ApolloConfigCenterAdapter implements ConfigCenterAdapter {

       private static final Logger log = LoggerFactory.getLogger(ApolloConfigCenterAdapter.class);

       private final Config apolloConfig;
       private final SqlGuardProperties properties;
       private final ConfigurableEnvironment environment;
       private final List<ConfigReloadListener> listeners;

       @Autowired
       public ApolloConfigCenterAdapter(
               @ApolloConfig Config apolloConfig,
               SqlGuardProperties properties,
               ConfigurableEnvironment environment,
               @Autowired(required = false) List<ConfigReloadListener> listeners) {
           this.apolloConfig = apolloConfig;
           this.properties = properties;
           this.environment = environment;
           this.listeners = listeners != null ? listeners : Collections.emptyList();
       }

       @ApolloConfigChangeListener
       private void onChange(ConfigChangeEvent changeEvent) {
           log.info("Apollo config changed: {} keys changed", changeEvent.changedKeys().size());

           Map<String, String> changedKeys = new HashMap<>();
           for (String key : changeEvent.changedKeys()) {
               ConfigChange change = changeEvent.getChange(key);
               if (key.startsWith("sql-guard.")) {
                   changedKeys.put(key, change.getNewValue());
                   log.info("  {}: {} -> {}", key, change.getOldValue(), change.getNewValue());
               }
           }

           if (!changedKeys.isEmpty()) {
               onConfigChange(new com.footstone.sqlguard.spring.config.center.ConfigChangeEvent(
                   changedKeys, changeEvent.getNamespace()));
           }
       }

       @Override
       public void onConfigChange(com.footstone.sqlguard.spring.config.center.ConfigChangeEvent event) {
           log.info("Reloading SQL Guard configuration from Apollo...");
           reloadConfig();
       }

       @Override
       public void reloadConfig() {
           try {
               // Rebind properties from Apollo config
               rebindProperties();

               // Notify listeners
               notifyListeners();

               log.info("SQL Guard configuration reloaded successfully from Apollo");
           } catch (Exception e) {
               log.error("Failed to reload configuration from Apollo", e);
           }
       }

       private void rebindProperties() {
           // Use Binder to rebind properties from environment
           Binder binder = Binder.get(environment);
           BindResult<SqlGuardProperties> result = binder.bind(
               "sql-guard",
               Bindable.ofInstance(properties));

           if (!result.isBound()) {
               log.warn("Failed to rebind SqlGuardProperties from Apollo config");
           }
       }

       private void notifyListeners() {
           for (ConfigReloadListener listener : listeners) {
               try {
                   listener.onConfigReloaded(null, null); // Old/new config comparison can be added
               } catch (Exception e) {
                   log.error("Error notifying config reload listener: {}", listener.getClass(), e);
               }
           }
       }
   }
   ```

3. Create Apollo configuration properties:
   ```java
   @ConfigurationProperties(prefix = "sql-guard.config-center.apollo")
   public class ApolloConfigCenterProperties {
       /**
        * Enable Apollo config center integration.
        */
       private boolean enabled = false;

       /**
        * Apollo namespaces to monitor.
        */
       private List<String> namespaces = Arrays.asList("application");

       // Getters and setters
   }
   ```

**Test Requirements**:
- `ApolloConfigCenterAdapterTest.java` (10 tests):
  - testApolloAdapter_withApollo_shouldCreate()
  - testApolloAdapter_withoutApollo_shouldNotCreate()
  - testApolloAdapter_disabled_shouldNotCreate()
  - testOnChange_withSqlGuardKeys_shouldTriggerReload()
  - testOnChange_withoutSqlGuardKeys_shouldIgnore()
  - testReloadConfig_shouldRebindProperties()
  - testReloadConfig_shouldNotifyListeners()
  - testReloadConfig_withException_shouldLogError()
  - testRebindProperties_shouldUpdateValues()
  - testMultipleNamespaces_shouldMonitorAll()

**Files to Create**:
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/apollo/ApolloConfigCenterAdapter.java`
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/apollo/ApolloConfigCenterProperties.java`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/center/apollo/ApolloConfigCenterAdapterTest.java`

---

### Step 3: Nacos Adapter Implementation
**Goal**: Implement Nacos config center adapter with @NacosConfigListener

**Tasks**:
1. Add Nacos client dependency (provided scope):
   ```xml
   <dependency>
       <groupId>com.alibaba.nacos</groupId>
       <artifactId>nacos-spring-context</artifactId>
       <version>1.1.1</version>
       <scope>provided</scope>
   </dependency>
   ```

2. Create `NacosConfigCenterAdapter`:
   ```java
   @Configuration
   @ConditionalOnClass(name = "com.alibaba.nacos.api.config.ConfigService")
   @ConditionalOnProperty(prefix = "sql-guard.config-center.nacos", name = "enabled", havingValue = "true")
   public class NacosConfigCenterAdapter implements ConfigCenterAdapter {

       private static final Logger log = LoggerFactory.getLogger(NacosConfigCenterAdapter.class);

       private final SqlGuardProperties properties;
       private final ConfigurableEnvironment environment;
       private final List<ConfigReloadListener> listeners;
       private final NacosConfigCenterProperties nacosProperties;

       @Autowired
       public NacosConfigCenterAdapter(
               SqlGuardProperties properties,
               ConfigurableEnvironment environment,
               NacosConfigCenterProperties nacosProperties,
               @Autowired(required = false) List<ConfigReloadListener> listeners) {
           this.properties = properties;
           this.environment = environment;
           this.nacosProperties = nacosProperties;
           this.listeners = listeners != null ? listeners : Collections.emptyList();
       }

       @NacosConfigListener(dataId = "${sql-guard.config-center.nacos.data-id:sql-guard}",
                            groupId = "${sql-guard.config-center.nacos.group-id:DEFAULT_GROUP}")
       public void onConfigChange(String configInfo) {
           log.info("Nacos config changed, new content length: {}", configInfo.length());

           try {
               // Parse and apply configuration
               parseAndRebindProperties(configInfo);

               // Notify listeners
               notifyListeners();

               log.info("SQL Guard configuration reloaded successfully from Nacos");
           } catch (Exception e) {
               log.error("Failed to reload configuration from Nacos", e);
           }
       }

       @Override
       public void onConfigChange(ConfigChangeEvent event) {
           log.info("Config change event received: {} keys changed", event.getChangedKeys().size());
           reloadConfig();
       }

       @Override
       public void reloadConfig() {
           // Trigger via Nacos listener
           log.info("Manual config reload triggered for Nacos");
       }

       private void parseAndRebindProperties(String configInfo) throws IOException {
           // Detect format (YAML or properties)
           boolean isYaml = configInfo.trim().startsWith("sql-guard:");

           if (isYaml) {
               parseYamlConfig(configInfo);
           } else {
               parsePropertiesConfig(configInfo);
           }
       }

       private void parseYamlConfig(String yamlContent) throws IOException {
           Yaml yaml = new Yaml();
           Map<String, Object> config = yaml.load(yamlContent);

           // Add to environment
           MapPropertySource propertySource = new MapPropertySource(
               "nacos-sql-guard",
               flattenMap(config));

           MutablePropertySources propertySources = environment.getPropertySources();
           propertySources.addFirst(propertySource);

           // Rebind
           Binder binder = Binder.get(environment);
           binder.bind("sql-guard", Bindable.ofInstance(properties));
       }

       private void parsePropertiesConfig(String propertiesContent) throws IOException {
           Properties props = new Properties();
           props.load(new StringReader(propertiesContent));

           PropertiesPropertySource propertySource = new PropertiesPropertySource(
               "nacos-sql-guard", props);

           MutablePropertySources propertySources = environment.getPropertySources();
           propertySources.addFirst(propertySource);

           // Rebind
           Binder binder = Binder.get(environment);
           binder.bind("sql-guard", Bindable.ofInstance(properties));
       }

       private Map<String, Object> flattenMap(Map<String, Object> source) {
           Map<String, Object> result = new HashMap<>();
           flattenMap("", source, result);
           return result;
       }

       private void flattenMap(String prefix, Map<String, Object> source, Map<String, Object> result) {
           for (Map.Entry<String, Object> entry : source.entrySet()) {
               String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
               Object value = entry.getValue();

               if (value instanceof Map) {
                   flattenMap(key, (Map<String, Object>) value, result);
               } else {
                   result.put(key, value);
               }
           }
       }

       private void notifyListeners() {
           for (ConfigReloadListener listener : listeners) {
               try {
                   listener.onConfigReloaded(null, null);
               } catch (Exception e) {
                   log.error("Error notifying config reload listener: {}", listener.getClass(), e);
               }
           }
       }
   }
   ```

3. Create Nacos configuration properties:
   ```java
   @ConfigurationProperties(prefix = "sql-guard.config-center.nacos")
   public class NacosConfigCenterProperties {
       /**
        * Enable Nacos config center integration.
        */
       private boolean enabled = false;

       /**
        * Nacos data ID.
        */
       private String dataId = "sql-guard";

       /**
        * Nacos group ID.
        */
       private String groupId = "DEFAULT_GROUP";

       // Getters and setters
   }
   ```

**Test Requirements**:
- `NacosConfigCenterAdapterTest.java` (12 tests):
  - testNacosAdapter_withNacos_shouldCreate()
  - testNacosAdapter_withoutNacos_shouldNotCreate()
  - testNacosAdapter_disabled_shouldNotCreate()
  - testOnConfigChange_withYaml_shouldParse()
  - testOnConfigChange_withProperties_shouldParse()
  - testParseYamlConfig_shouldRebindProperties()
  - testParsePropertiesConfig_shouldRebindProperties()
  - testFlattenMap_shouldFlattenNestedStructure()
  - testReloadConfig_shouldNotifyListeners()
  - testConfigFormat_autoDetection_shouldWork()
  - testDataIdAndGroupId_shouldBeConfigurable()
  - testConfigChange_withException_shouldLogError()

**Files to Create**:
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/nacos/NacosConfigCenterAdapter.java`
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/nacos/NacosConfigCenterProperties.java`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/center/nacos/NacosConfigCenterAdapterTest.java`

---

### Step 4: Validator Reload Integration
**Goal**: Implement config reload support in DefaultSqlSafetyValidator with thread-safe updates

**Tasks**:
1. Add config reload support to DefaultSqlSafetyValidator:
   ```java
   public class DefaultSqlSafetyValidator implements SqlSafetyValidator {

       private static final Logger log = LoggerFactory.getLogger(DefaultSqlSafetyValidator.class);

       private final JSqlParserFacade parserFacade;
       private final List<RuleChecker> checkers;
       private final RuleCheckerOrchestrator orchestrator;
       private final SqlDeduplicationFilter deduplicationFilter;

       // Thread-safe config holder
       private final AtomicReference<SqlGuardConfig> configRef;

       public DefaultSqlSafetyValidator(
               JSqlParserFacade parserFacade,
               List<RuleChecker> checkers,
               RuleCheckerOrchestrator orchestrator,
               SqlDeduplicationFilter deduplicationFilter) {
           this.parserFacade = parserFacade;
           this.checkers = checkers;
           this.orchestrator = orchestrator;
           this.deduplicationFilter = deduplicationFilter;
           this.configRef = new AtomicReference<>(SqlGuardConfig.getDefault());
       }

       /**
        * Reload configuration at runtime.
        * Thread-safe operation supporting hot-reload from config centers.
        *
        * @param newConfig new configuration
        */
       public void reloadConfig(SqlGuardConfig newConfig) {
           Objects.requireNonNull(newConfig, "newConfig cannot be null");

           log.info("Reloading SQL Guard configuration...");

           // Validate new config
           try {
               newConfig.validate();
           } catch (Exception e) {
               log.error("Invalid configuration, reload aborted", e);
               throw new IllegalArgumentException("Invalid configuration", e);
           }

           // Get old config for comparison
           SqlGuardConfig oldConfig = configRef.get();

           // Atomically swap config
           configRef.set(newConfig);

           // Invalidate caches
           invalidateCaches();

           // Log changes
           logConfigChanges(oldConfig, newConfig);

           log.info("SQL Guard configuration reloaded successfully");
       }

       /**
        * Get current configuration (thread-safe).
        *
        * @return current configuration
        */
       public SqlGuardConfig getCurrentConfig() {
           return configRef.get();
       }

       private void invalidateCaches() {
           // Clear deduplication cache
           if (deduplicationFilter != null) {
               deduplicationFilter.clearAll();
               log.info("Deduplication cache cleared");
           }

           // Clear JSqlParser cache
           if (parserFacade != null) {
               parserFacade.clearCache();
               log.info("JSqlParser cache cleared");
           }
       }

       private void logConfigChanges(SqlGuardConfig oldConfig, SqlGuardConfig newConfig) {
           // Compare and log significant changes
           // This helps operators understand what changed

           if (oldConfig == null) {
               log.info("Initial configuration loaded");
               return;
           }

           // Log strategy changes
           // Log enabled/disabled rules
           // Log threshold changes
           // etc.
       }

       @Override
       public ValidationResult validate(SqlContext context) {
           // Use current config from atomic reference
           SqlGuardConfig config = configRef.get();

           // Existing validation logic using config
           // ...

           return orchestrator.validate(context);
       }
   }
   ```

2. Create ValidatorConfigReloadListener:
   ```java
   @Component
   @ConditionalOnBean(DefaultSqlSafetyValidator.class)
   public class ValidatorConfigReloadListener implements ConfigReloadListener {

       private static final Logger log = LoggerFactory.getLogger(ValidatorConfigReloadListener.class);

       private final DefaultSqlSafetyValidator validator;
       private final SqlGuardProperties properties;

       @Autowired
       public ValidatorConfigReloadListener(
               DefaultSqlSafetyValidator validator,
               SqlGuardProperties properties) {
           this.validator = validator;
           this.properties = properties;
       }

       @Override
       public void onConfigReloaded(SqlGuardConfig oldConfig, SqlGuardConfig newConfig) {
           log.info("Config reload event received, updating validator...");

           try {
               // Convert SqlGuardProperties to SqlGuardConfig
               SqlGuardConfig config = convertToConfig(properties);

               // Reload validator with new config
               validator.reloadConfig(config);

           } catch (Exception e) {
               log.error("Failed to reload validator configuration", e);
           }
       }

       private SqlGuardConfig convertToConfig(SqlGuardProperties properties) {
           // Convert properties to SqlGuardConfig
           // This bridges Spring Boot properties to core config model

           SqlGuardConfig config = new SqlGuardConfig();

           // Map all properties
           // ...

           return config;
       }
   }
   ```

**Test Requirements**:
- `ValidatorReloadIntegrationTest.java` (15 tests):
  - testReloadConfig_shouldUpdateConfigAtomically()
  - testReloadConfig_withInvalidConfig_shouldReject()
  - testReloadConfig_shouldClearDeduplicationCache()
  - testReloadConfig_shouldClearParserCache()
  - testGetCurrentConfig_shouldReturnLatest()
  - testConcurrentValidation_duringReload_shouldBeThreadSafe()
  - testConfigReloadListener_shouldUpdateValidator()
  - testConfigChange_strategy_shouldApplyImmediately()
  - testConfigChange_ruleEnabled_shouldTakeEffect()
  - testConfigChange_threshold_shouldUpdate()
  - testReloadConfig_nullConfig_shouldThrow()
  - testReloadConfig_invalidConfig_shouldNotApply()
  - testCacheInvalidation_shouldPreventStaleResults()
  - testMultipleReloads_shouldHandleCorrectly()
  - testThreadSafety_100ConcurrentOperations()

**Files to Modify**:
- `sql-guard-core/src/main/java/com/footstone/sqlguard/validator/DefaultSqlSafetyValidator.java`

**Files to Create**:
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/ValidatorConfigReloadListener.java`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/center/ValidatorReloadIntegrationTest.java`

---

### Step 5: Integration and Extension Documentation
**Goal**: Integration tests and extension documentation for custom adapters

**Tasks**:
1. Create Apollo integration test:
   ```java
   @SpringBootTest
   @ActiveProfiles("apollo-test")
   @ConditionalOnClass(name = "com.ctrip.framework.apollo.Config")
   public class ApolloIntegrationTest {

       @Autowired(required = false)
       private ApolloConfigCenterAdapter adapter;

       @Autowired
       private DefaultSqlSafetyValidator validator;

       @Autowired
       private SqlGuardProperties properties;

       @MockBean
       private Config apolloConfig;

       @Test
       public void testApolloIntegration_shouldLoadAdapter() {
           assertNotNull(adapter);
       }

       @Test
       public void testConfigChange_shouldUpdateValidator() {
           // Simulate Apollo config change
           ConfigChangeEvent event = mock(ConfigChangeEvent.class);
           when(event.changedKeys()).thenReturn(
               Sets.newHashSet("sql-guard.active-strategy"));

           ConfigChange change = new ConfigChange(
               "sql-guard", "sql-guard.active-strategy", "LOG", "BLOCK");
           when(event.getChange("sql-guard.active-strategy")).thenReturn(change);

           // Trigger change
           adapter.onChange(event);

           // Verify properties updated
           assertEquals("BLOCK", properties.getActiveStrategy());
       }
   }
   ```

2. Create Nacos integration test:
   ```java
   @SpringBootTest
   @ActiveProfiles("nacos-test")
   @ConditionalOnClass(name = "com.alibaba.nacos.api.config.ConfigService")
   public class NacosIntegrationTest {

       @Autowired(required = false)
       private NacosConfigCenterAdapter adapter;

       @Autowired
       private SqlGuardProperties properties;

       @Test
       public void testNacosIntegration_shouldLoadAdapter() {
           assertNotNull(adapter);
       }

       @Test
       public void testYamlConfigChange_shouldUpdate() {
           String yamlConfig =
               "sql-guard:\n" +
               "  active-strategy: BLOCK\n" +
               "  deduplication:\n" +
               "    cache-size: 5000\n";

           adapter.onConfigChange(yamlConfig);

           assertEquals("BLOCK", properties.getActiveStrategy());
           assertEquals(5000, properties.getDeduplication().getCacheSize());
       }

       @Test
       public void testPropertiesConfigChange_shouldUpdate() {
           String propsConfig =
               "sql-guard.active-strategy=WARN\n" +
               "sql-guard.deduplication.ttl-ms=500\n";

           adapter.onConfigChange(propsConfig);

           assertEquals("WARN", properties.getActiveStrategy());
           assertEquals(500, properties.getDeduplication().getTtlMs());
       }
   }
   ```

3. Create extension documentation:
   ```markdown
   # Config Center Extension Guide

   ## Overview
   SQL Guard supports hot-reload from configuration centers via the ConfigCenterAdapter SPI.

   ## Built-in Adapters
   - Apollo (Ctrip Apollo)
   - Nacos (Alibaba Nacos)

   ## Creating Custom Adapter

   ### Step 1: Implement ConfigCenterAdapter
   \\```java
   @Configuration
   @ConditionalOnClass(name = "com.example.configcenter.Client")
   public class MyConfigCenterAdapter implements ConfigCenterAdapter {

       @Override
       public void onConfigChange(ConfigChangeEvent event) {
           // Handle config change
       }

       @Override
       public void reloadConfig() {
           // Trigger reload
       }
   }
   \\```

   ### Step 2: Register as Spring Bean
   Auto-configuration will discover your adapter automatically.

   ### Step 3: Test Your Adapter
   \\```java
   @SpringBootTest
   public class MyConfigCenterAdapterTest {
       @Autowired(required = false)
       private MyConfigCenterAdapter adapter;

       @Test
       public void testAdapter_shouldLoad() {
           assertNotNull(adapter);
       }
   }
   \\```

   ## Supported Config Centers
   | Config Center | Support | Adapter Class |
   |---------------|---------|---------------|
   | Apollo | ✅ | ApolloConfigCenterAdapter |
   | Nacos | ✅ | NacosConfigCenterAdapter |
   | Consul | 📝 | Community contribution |
   | Etcd | 📝 | Community contribution |
   | Zookeeper | 📝 | Community contribution |

   ## Troubleshooting
   - Ensure config center client library on classpath
   - Enable adapter via `sql-guard.config-center.<name>.enabled=true`
   - Check logs for config change events
   ```

**Test Requirements**:
- `ConfigCenterIntegrationTest.java` (10 tests):
  - testWithoutConfigCenter_shouldNotCreateAdapters()
  - testWithApollo_shouldCreateApolloAdapter()
  - testWithNacos_shouldCreateNacosAdapter()
  - testApolloConfigChange_shouldReloadValidator()
  - testNacosConfigChange_shouldReloadValidator()
  - testMultipleAdapters_shouldNotConflict()
  - testConfigReload_shouldInvalidateCaches()
  - testExtensionPoint_customAdapter_shouldWork()
  - testThreadSafety_concurrentReload_shouldHandle()
  - testValidatorReload_shouldApplyNewConfig()

**Files to Create**:
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/center/ApolloIntegrationTest.java`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/center/NacosIntegrationTest.java`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/center/ConfigCenterIntegrationTest.java`
- `sql-guard-spring-boot-starter/docs/config-center-extension.md`

---

## Expected Outcomes

### Functional Outcomes:
1. ✅ ConfigCenterAdapter SPI for extensibility
2. ✅ Apollo adapter with @ApolloConfigChangeListener
3. ✅ Nacos adapter with @NacosConfigListener (YAML + properties)
4. ✅ Thread-safe config reload in DefaultSqlSafetyValidator
5. ✅ Cache invalidation on config change
6. ✅ @ConditionalOnClass prevents startup failures
7. ✅ Extension documentation for custom adapters

### Test Outcomes:
- Total new tests: **65 tests** (8 + 10 + 12 + 15 + 10 + 10)
- Test categories:
  - Extension point interface: 8 tests
  - Apollo adapter: 10 tests
  - Nacos adapter: 12 tests
  - Validator reload: 15 tests
  - Integration tests: 10 tests
  - End-to-end: 10 tests
- 100% pass rate required

### Architecture Outcomes:
- ✅ Uniform extension point for all config centers
- ✅ Thread-safe runtime config updates
- ✅ Cache invalidation prevents stale validation
- ✅ Conditional activation prevents failures
- ✅ Documentation enables custom implementations

## Validation Criteria

### Must Pass Before Completion:
1. All 65 tests passing (100% pass rate)
2. ConfigCenterAdapter interface defined
3. Apollo adapter created with @ConditionalOnClass
4. Nacos adapter created with @ConditionalOnClass
5. Validator reload support implemented (thread-safe)
6. Cache invalidation works (deduplication + parser)
7. Apollo integration test passes
8. Nacos integration test passes (YAML + properties)
9. Thread-safety verified (concurrent validation + reload)
10. Extension documentation complete

### Code Quality:
1. Google Java Style compliance
2. Comprehensive Javadoc
3. SLF4J logging
4. Complete documentation

## Success Metrics

- ✅ 65 tests passing (100%)
- ✅ Hot-reload from Apollo works
- ✅ Hot-reload from Nacos works
- ✅ Thread-safe config updates verified
- ✅ Extension pattern documented

## Timeline Estimate
- Step 1: 2 hours (Extension interface + 8 tests)
- Step 2: 3 hours (Apollo adapter + 10 tests)
- Step 3: 3 hours (Nacos adapter + 12 tests)
- Step 4: 4 hours (Validator reload + 15 tests)
- Step 5: 3 hours (Integration + docs + 20 tests)

**Total**: ~15 hours

## Definition of Done

- [ ] All 65 tests passing
- [ ] ConfigCenterAdapter interface created
- [ ] Apollo adapter implemented with hot-reload
- [ ] Nacos adapter implemented (YAML + properties)
- [ ] Validator reload support added (thread-safe)
- [ ] Cache invalidation working
- [ ] Integration tests passing
- [ ] Extension documentation complete
- [ ] Memory Log created
- [ ] Compatible with Spring Boot 2.x and 3.x

---

**End of Task Assignment**

Config center extension points enable runtime configuration hot-reload from Apollo and Nacos with thread-safe validator updates, cache invalidation, and extensibility pattern for custom config center implementations.
