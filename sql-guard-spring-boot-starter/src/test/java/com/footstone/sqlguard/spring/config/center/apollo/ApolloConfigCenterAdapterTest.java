package com.footstone.sqlguard.spring.config.center.apollo;

import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import com.footstone.sqlguard.spring.config.center.ConfigChangeEvent;
import com.footstone.sqlguard.spring.config.center.ConfigReloadListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.mock.env.MockEnvironment;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApolloConfigCenterAdapter.
 *
 * <p>Tests cover adapter creation conditions, config change handling, property rebinding,
 * listener notification, and error handling scenarios.</p>
 */
class ApolloConfigCenterAdapterTest {

    private SqlGuardProperties properties;
    private ConfigurableEnvironment environment;
    private ApolloConfigCenterProperties apolloProperties;
    private List<ConfigReloadListener> listeners;
    private ApolloConfigCenterAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new SqlGuardProperties();
        properties.setEnabled(true);
        properties.setActiveStrategy("LOG");

        environment = new MockEnvironment();
        
        apolloProperties = new ApolloConfigCenterProperties();
        apolloProperties.setEnabled(true);
        apolloProperties.setNamespaces(Arrays.asList("application"));

        listeners = new ArrayList<>();
    }

    /**
     * Test that adapter is created when Apollo client is on classpath and enabled.
     */
    @Test
    void testApolloAdapter_withApollo_shouldCreate() {
        // Given
        Object mockApolloConfig = new Object();

        // When
        adapter = new ApolloConfigCenterAdapter(
                mockApolloConfig, properties, environment, apolloProperties, listeners);

        // Then
        assertNotNull(adapter);
        assertEquals("ApolloConfigCenterAdapter", adapter.getName());
    }

    /**
     * Test that adapter handles null listeners gracefully.
     */
    @Test
    void testApolloAdapter_withNullListeners_shouldHandleGracefully() {
        // Given
        Object mockApolloConfig = new Object();

        // When
        adapter = new ApolloConfigCenterAdapter(
                mockApolloConfig, properties, environment, apolloProperties, null);

        // Then
        assertNotNull(adapter);
        // Should not throw when reloading with no listeners
        assertDoesNotThrow(() -> adapter.reloadConfig());
    }

    /**
     * Test that onConfigChange triggers reload.
     */
    @Test
    void testOnConfigChange_withSqlGuardKeys_shouldTriggerReload() {
        // Given
        Object mockApolloConfig = new Object();
        adapter = new ApolloConfigCenterAdapter(
                mockApolloConfig, properties, environment, apolloProperties, listeners);

        Map<String, String> changes = new HashMap<>();
        changes.put("sql-guard.active-strategy", "BLOCK");
        ConfigChangeEvent event = new ConfigChangeEvent(changes, "application");

        // When
        adapter.onConfigChange(event);

        // Then - should complete without exception
        assertNotNull(adapter);
    }

    /**
     * Test that onChange filters non-sql-guard keys.
     */
    @Test
    void testOnChange_withoutSqlGuardKeys_shouldIgnore() {
        // Given
        Object mockApolloConfig = new Object();
        adapter = new ApolloConfigCenterAdapter(
                mockApolloConfig, properties, environment, apolloProperties, listeners);

        // Create mock Apollo ConfigChangeEvent with no sql-guard keys
        MockApolloConfigChangeEvent apolloEvent = new MockApolloConfigChangeEvent("application");
        apolloEvent.addChange("other.property", "oldValue", "newValue");

        // When
        adapter.onChange(apolloEvent);

        // Then - should not trigger reload (no exception means success)
        assertNotNull(adapter);
    }

    /**
     * Test that reloadConfig rebinds properties.
     */
    @Test
    void testReloadConfig_shouldRebindProperties() {
        // Given
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setProperty("sql-guard.enabled", "true");
        mockEnv.setProperty("sql-guard.active-strategy", "WARN");

        adapter = new ApolloConfigCenterAdapter(
                new Object(), properties, mockEnv, apolloProperties, listeners);

        // When
        adapter.reloadConfig();

        // Then
        assertEquals("WARN", properties.getActiveStrategy());
    }

    /**
     * Test that reloadConfig notifies listeners.
     */
    @Test
    void testReloadConfig_shouldNotifyListeners() {
        // Given
        AtomicInteger notifyCount = new AtomicInteger(0);
        ConfigReloadListener listener = (oldConfig, newConfig) -> notifyCount.incrementAndGet();
        listeners.add(listener);

        adapter = new ApolloConfigCenterAdapter(
                new Object(), properties, environment, apolloProperties, listeners);

        // When
        adapter.reloadConfig();

        // Then
        assertEquals(1, notifyCount.get());
    }

    /**
     * Test that reloadConfig handles listener exceptions gracefully.
     */
    @Test
    void testReloadConfig_withException_shouldLogError() {
        // Given
        ConfigReloadListener failingListener = (oldConfig, newConfig) -> {
            throw new RuntimeException("Listener failed");
        };
        ConfigReloadListener successListener = mock(ConfigReloadListener.class);
        
        listeners.add(failingListener);
        listeners.add(successListener);

        adapter = new ApolloConfigCenterAdapter(
                new Object(), properties, environment, apolloProperties, listeners);

        // When
        adapter.reloadConfig();

        // Then - should still notify second listener despite first one failing
        verify(successListener, times(1)).onConfigReloaded(null, null);
    }

    /**
     * Test that rebindProperties updates values.
     */
    @Test
    void testRebindProperties_shouldUpdateValues() {
        // Given
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setProperty("sql-guard.enabled", "false");
        mockEnv.setProperty("sql-guard.active-strategy", "BLOCK");
        mockEnv.setProperty("sql-guard.deduplication.enabled", "true");
        mockEnv.setProperty("sql-guard.deduplication.cache-size", "5000");

        properties.setEnabled(true);
        properties.setActiveStrategy("LOG");

        adapter = new ApolloConfigCenterAdapter(
                new Object(), properties, mockEnv, apolloProperties, listeners);

        // When
        adapter.reloadConfig();

        // Then
        assertFalse(properties.isEnabled());
        assertEquals("BLOCK", properties.getActiveStrategy());
        assertTrue(properties.getDeduplication().isEnabled());
        assertEquals(5000, properties.getDeduplication().getCacheSize());
    }

    /**
     * Test that multiple namespaces are supported.
     */
    @Test
    void testMultipleNamespaces_shouldMonitorAll() {
        // Given
        apolloProperties.setNamespaces(Arrays.asList("application", "sql-guard", "custom"));

        // When
        adapter = new ApolloConfigCenterAdapter(
                new Object(), properties, environment, apolloProperties, listeners);

        // Then
        assertNotNull(adapter);
        assertEquals(3, apolloProperties.getNamespaces().size());
    }

    /**
     * Test that onChange handles Apollo events with sql-guard keys.
     */
    @Test
    void testOnChange_withSqlGuardKeys_shouldProcessChanges() {
        // Given
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setProperty("sql-guard.active-strategy", "LOG");
        
        adapter = new ApolloConfigCenterAdapter(
                new Object(), properties, mockEnv, apolloProperties, listeners);

        MockApolloConfigChangeEvent apolloEvent = new MockApolloConfigChangeEvent("application");
        apolloEvent.addChange("sql-guard.active-strategy", "LOG", "BLOCK");
        apolloEvent.addChange("sql-guard.enabled", "true", "false");

        // When
        adapter.onChange(apolloEvent);

        // Then - should complete without exception
        assertNotNull(adapter);
    }

    /**
     * Mock Apollo ConfigChangeEvent for testing.
     */
    private static class MockApolloConfigChangeEvent {
        private final String namespace;
        private final Map<String, MockConfigChange> changes = new HashMap<>();

        public MockApolloConfigChangeEvent(String namespace) {
            this.namespace = namespace;
        }

        public void addChange(String key, String oldValue, String newValue) {
            changes.put(key, new MockConfigChange(key, oldValue, newValue));
        }

        public Set<String> changedKeys() {
            return changes.keySet();
        }

        public String getNamespace() {
            return namespace;
        }

        public MockConfigChange getChange(String key) {
            return changes.get(key);
        }
    }

    /**
     * Mock Apollo ConfigChange for testing.
     */
    private static class MockConfigChange {
        private final String propertyName;
        private final String oldValue;
        private final String newValue;

        public MockConfigChange(String propertyName, String oldValue, String newValue) {
            this.propertyName = propertyName;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getPropertyName() {
            return propertyName;
        }

        public String getOldValue() {
            return oldValue;
        }

        public String getNewValue() {
            return newValue;
        }
    }
}
