# Config Center Extension Guide

## Overview

SQL Guard supports hot-reload from configuration centers via the `ConfigCenterAdapter` SPI (Service Provider Interface). This allows runtime configuration updates without application restart, which is critical for production tuning such as adjusting risk levels, enabling/disabling rules, and changing validation strategies.

## Architecture

### Extension Point Pattern

The config center integration uses a clean extension point architecture:

```
ConfigCenterAdapter (interface)
    ├── onConfigChange(ConfigChangeEvent)
    ├── reloadConfig()
    └── getName()

ConfigChangeEvent (immutable)
    ├── getChangedKeys()
    ├── hasChanged(key)
    └── getNewValue(key)

ConfigReloadListener (interface)
    └── onConfigReloaded(oldConfig, newConfig)
```

### Thread Safety

- **ConfigChangeEvent**: Immutable and thread-safe
- **Adapters**: Synchronized reload operations prevent concurrent modification
- **Listeners**: Isolated exception handling - one failing listener doesn't affect others

## Built-in Adapters

### Apollo (Ctrip Apollo)

**Status**: ✅ Fully Implemented and Tested

**Activation Conditions**:
- Apollo client library on classpath (`com.ctrip.framework.apollo.Config`)
- Property `sql-guard.config-center.apollo.enabled=true`

**Configuration Example**:
```yaml
sql-guard:
  config-center:
    apollo:
      enabled: true
      namespaces:
        - application
        - sql-guard
```

**Features**:
- Automatic detection of sql-guard.* property changes
- Support for multiple Apollo namespaces
- Reflection-based event handling (no compile-time dependency)
- Thread-safe property rebinding
- Listener notification with exception isolation

**Maven Dependency** (provided scope):
```xml
<dependency>
    <groupId>com.ctrip.framework.apollo</groupId>
    <artifactId>apollo-client</artifactId>
    <version>2.0.1</version>
    <scope>provided</scope>
</dependency>
```

## Creating Custom Adapter

### Step 1: Implement ConfigCenterAdapter

```java
package com.example.config;

import com.footstone.sqlguard.spring.config.center.ConfigCenterAdapter;
import com.footstone.sqlguard.spring.config.center.ConfigChangeEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(name = "com.example.configcenter.Client")
@ConditionalOnProperty(prefix = "sql-guard.config-center.my-center", 
                       name = "enabled", havingValue = "true")
public class MyConfigCenterAdapter implements ConfigCenterAdapter {

    @Override
    public void onConfigChange(ConfigChangeEvent event) {
        // Handle config change notification
        log.info("Config changed: {} keys", event.getChangedKeys().size());
        reloadConfig();
    }

    @Override
    public void reloadConfig() {
        synchronized (this) {
            // Rebind properties from your config center
            // Notify listeners
        }
    }

    @Override
    public String getName() {
        return "MyConfigCenterAdapter";
    }
}
```

### Step 2: Create Configuration Properties

```java
@ConfigurationProperties(prefix = "sql-guard.config-center.my-center")
public class MyConfigCenterProperties {
    private boolean enabled = false;
    private String serverAddress;
    private String dataId;
    
    // Getters and setters
}
```

### Step 3: Register as Spring Bean

Auto-configuration will discover your adapter automatically through component scanning.

### Step 4: Test Your Adapter

```java
@SpringBootTest
public class MyConfigCenterAdapterTest {
    
    @Autowired(required = false)
    private MyConfigCenterAdapter adapter;

    @Test
    public void testAdapter_shouldLoad() {
        assertNotNull(adapter);
        assertEquals("MyConfigCenterAdapter", adapter.getName());
    }

    @Test
    public void testConfigChange_shouldTriggerReload() {
        Map<String, String> changes = new HashMap<>();
        changes.put("sql-guard.active-strategy", "BLOCK");
        
        ConfigChangeEvent event = new ConfigChangeEvent(changes, "test");
        adapter.onConfigChange(event);
        
        // Verify reload occurred
    }
}
```

## Best Practices

### 1. Use @ConditionalOnClass

Always use `@ConditionalOnClass` to prevent startup failures when the config center client library is not present:

```java
@ConditionalOnClass(name = "com.example.configcenter.Client")
```

### 2. Thread-Safe Reload

Synchronize reload operations to prevent race conditions:

```java
@Override
public void reloadConfig() {
    synchronized (this) {
        // Reload logic here
    }
}
```

### 3. Exception Isolation

Catch and log exceptions when notifying listeners:

```java
for (ConfigReloadListener listener : listeners) {
    try {
        listener.onConfigReloaded(oldConfig, newConfig);
    } catch (Exception e) {
        log.error("Listener failed: {}", listener.getClass(), e);
    }
}
```

### 4. Filter Relevant Changes

Only trigger reload for sql-guard.* properties:

```java
if (key.startsWith("sql-guard.")) {
    // Handle change
}
```

## Supported Config Centers

| Config Center | Support Status | Adapter Class | Notes |
|---------------|----------------|---------------|-------|
| Apollo | ✅ Implemented | `ApolloConfigCenterAdapter` | Fully tested with 10 unit tests |
| Nacos | 📝 Community | - | Removed due to dependency conflicts |
| Consul | 📝 Community | - | Community contribution welcome |
| Etcd | 📝 Community | - | Community contribution welcome |
| Zookeeper | 📝 Community | - | Community contribution welcome |
| Spring Cloud Config | 📝 Community | - | Community contribution welcome |

## Troubleshooting

### Adapter Not Loading

**Problem**: Config center adapter not created at startup

**Solutions**:
1. Verify config center client library is on classpath
2. Check `sql-guard.config-center.<name>.enabled=true` is set
3. Enable debug logging: `logging.level.com.footstone.sqlguard.spring.config.center=DEBUG`

### Config Changes Not Applied

**Problem**: Configuration changes detected but not applied

**Solutions**:
1. Check property binding in logs
2. Verify property names match exactly (case-sensitive)
3. Ensure properties are mutable (not final)
4. Check for validation errors in logs

### Thread Safety Issues

**Problem**: Concurrent modification exceptions during reload

**Solutions**:
1. Ensure reload method is synchronized
2. Use AtomicReference for config holder
3. Clear caches before applying new config

## Performance Considerations

### Reload Frequency

- **Recommended**: Debounce rapid config changes (e.g., 1-second window)
- **Avoid**: Reloading on every single property change
- **Best**: Batch multiple changes into single reload

### Cache Invalidation

Config reload should invalidate:
- Deduplication cache (prevents stale validation results)
- JSqlParser cache (ensures new SQL patterns are parsed)
- Rule checker caches (if any)

### Listener Performance

- Keep listener logic lightweight
- Offload heavy operations to async threads
- Use timeout protection for slow listeners

## Extension Examples

### Example 1: Consul Integration

```java
@Configuration
@ConditionalOnClass(name = "com.ecwid.consul.v1.ConsulClient")
@ConditionalOnProperty(prefix = "sql-guard.config-center.consul", 
                       name = "enabled", havingValue = "true")
public class ConsulConfigCenterAdapter implements ConfigCenterAdapter {
    
    private final ConsulClient consulClient;
    private final String keyPrefix = "sql-guard/";
    
    @PostConstruct
    public void startWatching() {
        // Watch Consul KV changes
        consulClient.watchKVValues(keyPrefix, this::onConsulChange);
    }
    
    private void onConsulChange(List<KeyValue> changes) {
        Map<String, String> changedKeys = new HashMap<>();
        for (KeyValue kv : changes) {
            changedKeys.put(kv.getKey(), kv.getValue());
        }
        onConfigChange(new ConfigChangeEvent(changedKeys, keyPrefix));
    }
    
    @Override
    public void onConfigChange(ConfigChangeEvent event) {
        reloadConfig();
    }
    
    @Override
    public void reloadConfig() {
        synchronized (this) {
            // Reload from Consul
        }
    }
}
```

### Example 2: Spring Cloud Config Integration

```java
@Configuration
@ConditionalOnClass(name = "org.springframework.cloud.config.client.ConfigClientProperties")
@ConditionalOnProperty(prefix = "sql-guard.config-center.spring-cloud", 
                       name = "enabled", havingValue = "true")
public class SpringCloudConfigAdapter implements ConfigCenterAdapter {
    
    @Autowired
    private ConfigurableEnvironment environment;
    
    @EventListener(EnvironmentChangeEvent.class)
    public void onEnvironmentChange(EnvironmentChangeEvent event) {
        Set<String> changedKeys = event.getKeys();
        Map<String, String> sqlGuardChanges = new HashMap<>();
        
        for (String key : changedKeys) {
            if (key.startsWith("sql-guard.")) {
                sqlGuardChanges.put(key, environment.getProperty(key));
            }
        }
        
        if (!sqlGuardChanges.isEmpty()) {
            onConfigChange(new ConfigChangeEvent(sqlGuardChanges, "spring-cloud"));
        }
    }
    
    @Override
    public void onConfigChange(ConfigChangeEvent event) {
        reloadConfig();
    }
    
    @Override
    public void reloadConfig() {
        synchronized (this) {
            // Properties already updated by Spring Cloud Config
            // Just notify listeners
        }
    }
}
```

## Contributing

We welcome community contributions for additional config center adapters!

**Contribution Guidelines**:
1. Follow the extension point pattern
2. Include comprehensive unit tests (minimum 8 tests)
3. Add documentation with configuration examples
4. Use `@ConditionalOnClass` for optional dependencies
5. Implement thread-safe reload logic
6. Add troubleshooting section

**Submission Process**:
1. Fork the repository
2. Create feature branch: `feature/config-center-<name>`
3. Implement adapter with tests
4. Update this documentation
5. Submit pull request with description

---

**Last Updated**: 2025-12-17  
**Version**: 1.0.0  
**Maintainer**: SQL Guard Team















