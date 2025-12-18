# Tutorial: Adding Custom Config Center Adapter

This tutorial demonstrates how to integrate a custom configuration center with SQL Safety Guard for hot-reload of validation rules. We'll implement an etcd config center adapter following the same pattern as the built-in Apollo adapter.

## Problem Statement

Many enterprises use etcd for distributed configuration management, but SQL Safety Guard only has built-in support for Apollo Config Center. Applications using etcd need hot-reload capability for SQL validation rules without redeployment.

**Goal**: Implement `EtcdConfigCenterAdapter` that watches etcd for configuration changes and triggers SQL Guard configuration reload.

## Prerequisites

- Java 11 (for development)
- Maven 3.6+
- etcd server running locally (for testing)
- Familiarity with etcd client API
- Understanding of Spring Boot configuration

## Step 1: Research etcd Client API

etcd provides Java client library `jetcd` for watching configuration changes:

```java
// Create etcd client
Client client = Client.builder()
    .endpoints("http://localhost:2379")
    .build();

// Watch for key changes
Watch.Watcher watcher = client.getWatchClient().watch(key);
for (WatchResponse response : watcher) {
  for (WatchEvent event : response.getEvents()) {
    // Handle configuration change
  }
}
```

**Key Operations**:
- `get(key)` - Fetch configuration value
- `watch(key)` - Watch for key changes
- `put(key, value)` - Update configuration (for testing)

## Step 2: Write Test Class First (TDD)

Create `EtcdConfigCenterAdapterTest.java` in `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/center/etcd/`:

```java
package com.footstone.sqlguard.spring.config.center.etcd;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.footstone.sqlguard.spring.config.center.ConfigChangeEvent;
import com.footstone.sqlguard.spring.config.center.ConfigReloadListener;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Test class for EtcdConfigCenterAdapter.
 */
class EtcdConfigCenterAdapterTest {

  @Mock
  private Client etcdClient;

  @Mock
  private KV kvClient;

  @Mock
  private Watch watchClient;

  @Mock
  private ConfigReloadListener reloadListener;

  private EtcdConfigCenterAdapter adapter;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(etcdClient.getKVClient()).thenReturn(kvClient);
    when(etcdClient.getWatchClient()).thenReturn(watchClient);
  }

  @Test
  void testOnConfigChange_shouldTriggerReload() {
    // Arrange
    adapter = new EtcdConfigCenterAdapter(
        etcdClient,
        "/sqlguard/config",
        reloadListener
    );
    ConfigChangeEvent event = new ConfigChangeEvent(
        Collections.singleton("/sqlguard/config")
    );

    // Mock etcd get response
    String configYaml = "sql-guard:\n  enabled: true\n";
    KeyValue kv = mock(KeyValue.class);
    when(kv.getValue()).thenReturn(
        ByteSequence.from(configYaml, StandardCharsets.UTF_8)
    );
    GetResponse getResponse = mock(GetResponse.class);
    when(getResponse.getKvs()).thenReturn(Collections.singletonList(kv));
    when(kvClient.get(any(ByteSequence.class)))
        .thenReturn(CompletableFuture.completedFuture(getResponse));

    // Act
    adapter.onConfigChange(event);

    // Assert
    verify(reloadListener, timeout(1000)).onReload(configYaml);
  }

  @Test
  void testReloadConfig_shouldFetchAndNotify() throws Exception {
    // Arrange
    adapter = new EtcdConfigCenterAdapter(
        etcdClient,
        "/sqlguard/config",
        reloadListener
    );

    String configYaml = "sql-guard:\n  enabled: true\n";
    KeyValue kv = mock(KeyValue.class);
    when(kv.getValue()).thenReturn(
        ByteSequence.from(configYaml, StandardCharsets.UTF_8)
    );
    GetResponse getResponse = mock(GetResponse.class);
    when(getResponse.getKvs()).thenReturn(Collections.singletonList(kv));
    when(kvClient.get(any(ByteSequence.class)))
        .thenReturn(CompletableFuture.completedFuture(getResponse));

    // Act
    adapter.reloadConfig();

    // Assert
    verify(reloadListener, timeout(1000)).onReload(configYaml);
  }

  @Test
  void testReloadConfig_emptyResponse_shouldSkip() throws Exception {
    // Arrange
    adapter = new EtcdConfigCenterAdapter(
        etcdClient,
        "/sqlguard/config",
        reloadListener
    );

    GetResponse getResponse = mock(GetResponse.class);
    when(getResponse.getKvs()).thenReturn(Collections.emptyList());
    when(kvClient.get(any(ByteSequence.class)))
        .thenReturn(CompletableFuture.completedFuture(getResponse));

    // Act
    adapter.reloadConfig();

    // Assert
    verify(reloadListener, never()).onReload(any());
  }

  @Test
  void testReloadConfig_exception_shouldLogAndNotThrow() throws Exception {
    // Arrange
    adapter = new EtcdConfigCenterAdapter(
        etcdClient,
        "/sqlguard/config",
        reloadListener
    );

    when(kvClient.get(any(ByteSequence.class)))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("etcd error")));

    // Act & Assert
    assertDoesNotThrow(() -> adapter.reloadConfig());
    verify(reloadListener, never()).onReload(any());
  }

  @Test
  void testGetName_shouldReturnAdapterName() {
    // Arrange
    adapter = new EtcdConfigCenterAdapter(
        etcdClient,
        "/sqlguard/config",
        reloadListener
    );

    // Act
    String name = adapter.getName();

    // Assert
    assertEquals("EtcdConfigCenterAdapter", name);
  }

  @Test
  void testThreadSafety_concurrentReloads() throws Exception {
    // Arrange
    adapter = new EtcdConfigCenterAdapter(
        etcdClient,
        "/sqlguard/config",
        reloadListener
    );

    String configYaml = "sql-guard:\n  enabled: true\n";
    KeyValue kv = mock(KeyValue.class);
    when(kv.getValue()).thenReturn(
        ByteSequence.from(configYaml, StandardCharsets.UTF_8)
    );
    GetResponse getResponse = mock(GetResponse.class);
    when(getResponse.getKvs()).thenReturn(Collections.singletonList(kv));
    when(kvClient.get(any(ByteSequence.class)))
        .thenReturn(CompletableFuture.completedFuture(getResponse));

    // Act: Trigger 10 concurrent reloads
    CountDownLatch latch = new CountDownLatch(10);
    for (int i = 0; i < 10; i++) {
      new Thread(() -> {
        adapter.reloadConfig();
        latch.countDown();
      }).start();
    }

    // Assert: All reloads complete without exception
    assertTrue(latch.await(5, TimeUnit.SECONDS));
    verify(reloadListener, atLeast(1)).onReload(configYaml);
  }
}
```

## Step 3: Add etcd Client Dependency

Update `sql-guard-spring-boot-starter/pom.xml`:

```xml
<dependencies>
  <!-- Existing dependencies -->
  
  <!-- etcd client (optional) -->
  <dependency>
    <groupId>io.etcd</groupId>
    <artifactId>jetcd-core</artifactId>
    <version>0.7.5</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

## Step 4: Implement EtcdConfigCenterAdapter

Create `EtcdConfigCenterAdapter.java` in `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/etcd/`:

```java
package com.footstone.sqlguard.spring.config.center.etcd;

import com.footstone.sqlguard.spring.config.center.ConfigCenterAdapter;
import com.footstone.sqlguard.spring.config.center.ConfigChangeEvent;
import com.footstone.sqlguard.spring.config.center.ConfigReloadListener;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * etcd config center adapter for SQL Guard hot-reload.
 *
 * <p>This adapter watches etcd for configuration changes and triggers SQL Guard
 * configuration reload when changes are detected.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Watches etcd key for configuration changes</li>
 *   <li>Fetches latest configuration on change events</li>
 *   <li>Thread-safe reload operations with synchronized access</li>
 *   <li>Exception isolation prevents reload failures from breaking application</li>
 *   <li>Automatic reconnection on watch failure</li>
 * </ul>
 *
 * <p><strong>Configuration Example:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   config-center:
 *     etcd:
 *       enabled: true
 *       endpoints: http://localhost:2379
 *       config-key: /sqlguard/config
 * }</pre>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * // Configuration is automatically loaded from etcd
 * // Changes to /sqlguard/config in etcd trigger hot-reload
 * 
 * // Update configuration in etcd:
 * etcdctl put /sqlguard/config "$(cat config.yml)"
 * 
 * // SQL Guard automatically reloads without restart
 * }</pre>
 *
 * @see ConfigCenterAdapter
 * @see ConfigReloadListener
 */
@Configuration
@ConditionalOnClass(name = "io.etcd.jetcd.Client")
@ConditionalOnProperty(
    prefix = "sql-guard.config-center.etcd",
    name = "enabled",
    havingValue = "true"
)
@EnableConfigurationProperties(EtcdConfigCenterProperties.class)
public class EtcdConfigCenterAdapter implements ConfigCenterAdapter {

  private static final Logger log = LoggerFactory.getLogger(EtcdConfigCenterAdapter.class);

  private final Client etcdClient;
  private final String configKey;
  private final ConfigReloadListener reloadListener;
  private Watch.Watcher watcher;

  /**
   * Constructs adapter with etcd client and configuration.
   *
   * @param etcdClient the etcd client
   * @param configKey the configuration key in etcd
   * @param reloadListener the reload listener
   */
  public EtcdConfigCenterAdapter(
      Client etcdClient,
      String configKey,
      ConfigReloadListener reloadListener) {
    this.etcdClient = etcdClient;
    this.configKey = configKey;
    this.reloadListener = reloadListener;

    // Start watching for configuration changes
    watchConfigChanges();

    log.info("EtcdConfigCenterAdapter initialized, watching key: {}", configKey);
  }

  /**
   * Alternative constructor using properties.
   *
   * @param properties the etcd properties
   * @param reloadListener the reload listener
   */
  public EtcdConfigCenterAdapter(
      EtcdConfigCenterProperties properties,
      ConfigReloadListener reloadListener) {
    this(
        Client.builder()
            .endpoints(properties.getEndpoints().split(","))
            .build(),
        properties.getConfigKey(),
        reloadListener
    );
  }

  @Override
  public void onConfigChange(ConfigChangeEvent event) {
    if (event.getChangedKeys().contains(configKey)) {
      log.info("Configuration change detected for key: {}", configKey);
      reloadConfig();
    }
  }

  @Override
  public synchronized void reloadConfig() {
    try {
      // Fetch latest configuration from etcd
      ByteSequence key = ByteSequence.from(configKey, StandardCharsets.UTF_8);
      CompletableFuture<GetResponse> future = etcdClient.getKVClient().get(key);
      GetResponse response = future.get();

      if (response.getKvs().isEmpty()) {
        log.warn("Configuration key not found in etcd: {}", configKey);
        return;
      }

      String configYaml = response.getKvs().get(0).getValue()
          .toString(StandardCharsets.UTF_8);

      // Notify reload listener
      reloadListener.onReload(configYaml);

      log.info("Configuration reloaded successfully from etcd");

    } catch (Exception e) {
      log.error("Failed to reload configuration from etcd", e);
      // Don't rethrow - exception isolation prevents reload failures from breaking app
    }
  }

  /**
   * Starts watching etcd for configuration changes.
   *
   * <p>Runs in background thread, automatically reconnects on failure.</p>
   */
  private void watchConfigChanges() {
    ByteSequence key = ByteSequence.from(configKey, StandardCharsets.UTF_8);
    watcher = etcdClient.getWatchClient().watch(key);

    // Listen for watch events in background thread
    CompletableFuture.runAsync(() -> {
      try {
        for (WatchResponse response : watcher) {
          for (WatchEvent event : response.getEvents()) {
            if (event.getEventType() == WatchEvent.EventType.PUT) {
              log.debug("etcd PUT event detected for key: {}", configKey);
              onConfigChange(new ConfigChangeEvent(Collections.singleton(configKey)));
            }
          }
        }
      } catch (Exception e) {
        log.error("Error watching etcd configuration changes", e);
        // Attempt to restart watch
        watchConfigChanges();
      }
    });
  }

  /**
   * Closes etcd client and watcher.
   */
  public void close() {
    if (watcher != null) {
      watcher.close();
    }
    if (etcdClient != null) {
      etcdClient.close();
    }
    log.info("EtcdConfigCenterAdapter closed");
  }

  @Override
  public String getName() {
    return "EtcdConfigCenterAdapter";
  }
}
```

## Step 5: Implement EtcdConfigCenterProperties

Create `EtcdConfigCenterProperties.java` in same package:

```java
package com.footstone.sqlguard.spring.config.center.etcd;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for etcd config center.
 *
 * <p><strong>Configuration Example:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   config-center:
 *     etcd:
 *       enabled: true
 *       endpoints: http://localhost:2379,http://localhost:2380
 *       config-key: /sqlguard/config
 * }</pre>
 */
@ConfigurationProperties(prefix = "sql-guard.config-center.etcd")
public class EtcdConfigCenterProperties {

  /**
   * Whether etcd config center is enabled.
   */
  private boolean enabled = false;

  /**
   * etcd endpoints (comma-separated).
   * Example: "http://localhost:2379,http://localhost:2380"
   */
  private String endpoints = "http://localhost:2379";

  /**
   * Configuration key in etcd.
   * Default: /sqlguard/config
   */
  private String configKey = "/sqlguard/config";

  // Getters and setters

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getEndpoints() {
    return endpoints;
  }

  public void setEndpoints(String endpoints) {
    this.endpoints = endpoints;
  }

  public String getConfigKey() {
    return configKey;
  }

  public void setConfigKey(String configKey) {
    this.configKey = configKey;
  }
}
```

## Step 6: Run Tests

```bash
cd sql-guard-spring-boot-starter
mvn test -Dtest=EtcdConfigCenterAdapterTest
```

**Expected Output**: All tests pass.

## Step 7: Add Spring Boot Auto-Configuration

Update `SqlGuardAutoConfiguration.java` to include etcd adapter:

```java
@Configuration
@ConditionalOnClass(name = "io.etcd.jetcd.Client")
@ConditionalOnProperty(
    prefix = "sql-guard.config-center.etcd",
    name = "enabled",
    havingValue = "true"
)
public static class EtcdConfigCenterConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public EtcdConfigCenterAdapter etcdConfigCenterAdapter(
      EtcdConfigCenterProperties properties,
      ConfigReloadListener reloadListener) {
    return new EtcdConfigCenterAdapter(properties, reloadListener);
  }
}
```

## Step 8: Create Documentation

Update `config-center-extension.md` in `sql-guard-spring-boot-starter/docs/`:

```markdown
# Config Center Extension

## etcd Config Center

### Maven Dependency

\`\`\`xml
<dependency>
    <groupId>io.etcd</groupId>
    <artifactId>jetcd-core</artifactId>
    <version>0.7.5</version>
</dependency>
\`\`\`

### Configuration

\`\`\`yaml
sql-guard:
  config-center:
    etcd:
      enabled: true
      endpoints: http://localhost:2379
      config-key: /sqlguard/config
\`\`\`

### Usage

1. Store SQL Guard configuration in etcd:

\`\`\`bash
etcdctl put /sqlguard/config "$(cat config.yml)"
\`\`\`

2. Configuration is automatically loaded on startup

3. Update configuration in etcd:

\`\`\`bash
etcdctl put /sqlguard/config "$(cat updated-config.yml)"
\`\`\`

4. SQL Guard automatically reloads without restart

### Example Configuration

\`\`\`yaml
sql-guard:
  enabled: true
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
    dummy-condition:
      enabled: true
      risk-level: HIGH
\`\`\`
```

## Step 9: Integration Testing

Create integration test with real etcd server:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "sql-guard.config-center.etcd.enabled=true",
    "sql-guard.config-center.etcd.endpoints=http://localhost:2379",
    "sql-guard.config-center.etcd.config-key=/sqlguard/test-config"
})
class EtcdConfigCenterIntegrationTest {

  @Autowired
  private EtcdConfigCenterAdapter adapter;

  @Test
  void testRealEtcdIntegration() throws Exception {
    // This test requires etcd server running on localhost:2379
    // Skip if etcd not available
    assumeTrue(isEtcdAvailable());

    // Test configuration reload
    adapter.reloadConfig();

    // Verify no exceptions thrown
  }

  private boolean isEtcdAvailable() {
    try {
      Client client = Client.builder()
          .endpoints("http://localhost:2379")
          .build();
      client.close();
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
```

## Step 10: Update CHANGELOG.md

```markdown
## [Unreleased]

### Added
- etcd config center adapter support (EtcdConfigCenterAdapter)
- Hot-reload of SQL Guard configuration from etcd
- Documentation: etcd config center setup guide
```

## Verification Testing

Test the adapter with real etcd:

```bash
# Start etcd server
docker run -d --name etcd \
  -p 2379:2379 \
  -p 2380:2380 \
  quay.io/coreos/etcd:latest \
  /usr/local/bin/etcd \
  --advertise-client-urls http://0.0.0.0:2379 \
  --listen-client-urls http://0.0.0.0:2379

# Store configuration
etcdctl put /sqlguard/config "$(cat <<EOF
sql-guard:
  enabled: true
  rules:
    no-where-clause:
      enabled: true
      risk-level: CRITICAL
EOF
)"

# Start Spring Boot application
# Configuration automatically loaded from etcd

# Update configuration
etcdctl put /sqlguard/config "$(cat <<EOF
sql-guard:
  enabled: true
  rules:
    no-where-clause:
      enabled: false
      risk-level: CRITICAL
EOF
)"

# Application automatically reloads configuration
```

## Troubleshooting

### Issue: Connection refused to etcd

**Solution**: Verify etcd server is running:

```bash
etcdctl endpoint health
```

### Issue: Configuration not reloading

**Solution**: Check watch is active:

```bash
# In one terminal, watch for changes
etcdctl watch /sqlguard/config

# In another terminal, update config
etcdctl put /sqlguard/config "new config"
```

### Issue: Thread safety violations

**Solution**: Ensure `reloadConfig()` is synchronized:

```java
@Override
public synchronized void reloadConfig() {
  // Synchronized to prevent concurrent reloads
}
```

## Summary

You've successfully added etcd config center support to SQL Safety Guard! Key takeaways:

1. **Implement ConfigCenterAdapter Interface**: Follow SPI pattern
2. **Watch for Changes**: Use etcd watch API for real-time updates
3. **Thread Safety**: Synchronize reload operations
4. **Exception Isolation**: Don't let reload failures break application
5. **Spring Boot Integration**: Use @ConditionalOnClass and @ConditionalOnProperty

## Next Steps

- Add support for etcd authentication (username/password, TLS)
- Add support for etcd namespace prefixes
- Add metrics for reload success/failure rates
- Contribute your adapter back to the project via Pull Request!

## Complete Source Code

All source code from this tutorial is available in:

- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/etcd/EtcdConfigCenterAdapter.java`
- `sql-guard-spring-boot-starter/src/main/java/com/footstone/sqlguard/spring/config/center/etcd/EtcdConfigCenterProperties.java`
- `sql-guard-spring-boot-starter/src/test/java/com/footstone/sqlguard/spring/config/center/etcd/EtcdConfigCenterAdapterTest.java`

Copy-paste the code examples above to get started!



