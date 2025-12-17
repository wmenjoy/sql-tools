package com.footstone.sqlguard.spring.config.center;

import com.footstone.sqlguard.config.SqlGuardConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfigCenterAdapter extension point interfaces.
 *
 * <p>Tests cover ConfigChangeEvent, ConfigCenterAdapter, and ConfigReloadListener
 * to ensure proper behavior of the extension point architecture.</p>
 */
class ConfigCenterAdapterTest {

    /**
     * Test that ConfigChangeEvent correctly stores and retrieves changed keys.
     */
    @Test
    void testConfigChangeEvent_shouldContainChangedKeys() {
        // Given
        Map<String, String> changes = new HashMap<>();
        changes.put("sql-guard.active-strategy", "BLOCK");
        changes.put("sql-guard.rules.no-where-clause.enabled", "false");

        // When
        ConfigChangeEvent event = new ConfigChangeEvent(changes, "application");

        // Then
        assertNotNull(event);
        assertEquals(2, event.getChangedKeys().size());
        assertEquals("BLOCK", event.getChangedKeys().get("sql-guard.active-strategy"));
        assertEquals("false", event.getChangedKeys().get("sql-guard.rules.no-where-clause.enabled"));
    }

    /**
     * Test that hasChanged() correctly identifies changed keys.
     */
    @Test
    void testConfigChangeEvent_hasChanged_shouldReturnCorrectly() {
        // Given
        Map<String, String> changes = new HashMap<>();
        changes.put("sql-guard.active-strategy", "BLOCK");

        ConfigChangeEvent event = new ConfigChangeEvent(changes, "application");

        // When & Then
        assertTrue(event.hasChanged("sql-guard.active-strategy"));
        assertFalse(event.hasChanged("sql-guard.enabled"));
        assertFalse(event.hasChanged("non-existent-key"));
    }

    /**
     * Test that getNewValue() returns correct values for changed keys.
     */
    @Test
    void testConfigChangeEvent_getNewValue_shouldReturnValue() {
        // Given
        Map<String, String> changes = new HashMap<>();
        changes.put("sql-guard.active-strategy", "WARN");
        changes.put("sql-guard.deduplication.cache-size", "5000");

        ConfigChangeEvent event = new ConfigChangeEvent(changes, "application");

        // When & Then
        assertEquals("WARN", event.getNewValue("sql-guard.active-strategy"));
        assertEquals("5000", event.getNewValue("sql-guard.deduplication.cache-size"));
        assertNull(event.getNewValue("non-existent-key"));
    }

    /**
     * Test that ConfigChangeEvent is immutable - modifications to source map don't affect event.
     */
    @Test
    void testConfigChangeEvent_immutable_shouldNotModify() {
        // Given
        Map<String, String> changes = new HashMap<>();
        changes.put("sql-guard.active-strategy", "BLOCK");

        ConfigChangeEvent event = new ConfigChangeEvent(changes, "application");

        // When - modify source map
        changes.put("sql-guard.enabled", "false");
        changes.put("sql-guard.active-strategy", "LOG");

        // Then - event should not be affected
        assertEquals(1, event.getChangedKeys().size());
        assertEquals("BLOCK", event.getNewValue("sql-guard.active-strategy"));
        assertFalse(event.hasChanged("sql-guard.enabled"));

        // Verify returned map is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> {
            event.getChangedKeys().put("new-key", "new-value");
        });
    }

    /**
     * Test that ConfigCenterAdapter getName() returns class name by default.
     */
    @Test
    void testConfigCenterAdapter_getName_shouldReturnClassName() {
        // Given
        ConfigCenterAdapter adapter = new TestConfigCenterAdapter();

        // When
        String name = adapter.getName();

        // Then
        assertEquals("TestConfigCenterAdapter", name);
    }

    /**
     * Test that ConfigReloadListener receives and processes events correctly.
     */
    @Test
    void testConfigReloadListener_shouldReceiveEvents() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicBoolean receivedOldConfig = new AtomicBoolean(false);
        AtomicBoolean receivedNewConfig = new AtomicBoolean(false);

        ConfigReloadListener listener = (oldConfig, newConfig) -> {
            callCount.incrementAndGet();
            if (oldConfig != null) receivedOldConfig.set(true);
            if (newConfig != null) receivedNewConfig.set(true);
        };

        SqlGuardConfig oldConfig = new SqlGuardConfig();
        SqlGuardConfig newConfig = new SqlGuardConfig();

        // When
        listener.onConfigReloaded(oldConfig, newConfig);

        // Then
        assertEquals(1, callCount.get());
        assertTrue(receivedOldConfig.get());
        assertTrue(receivedNewConfig.get());
    }

    /**
     * Test that ConfigChangeEvent stores namespace correctly.
     */
    @Test
    void testConfigChangeEvent_namespace_shouldStore() {
        // Given
        Map<String, String> changes = new HashMap<>();
        changes.put("sql-guard.active-strategy", "BLOCK");

        // When
        ConfigChangeEvent event1 = new ConfigChangeEvent(changes, "application");
        ConfigChangeEvent event2 = new ConfigChangeEvent(changes, "sql-guard-namespace");
        ConfigChangeEvent event3 = new ConfigChangeEvent(changes, null);

        // Then
        assertEquals("application", event1.getNamespace());
        assertEquals("sql-guard-namespace", event2.getNamespace());
        assertNull(event3.getNamespace());
    }

    /**
     * Test that ConfigChangeEvent timestamp is set correctly.
     */
    @Test
    void testConfigChangeEvent_timestamp_shouldBeSet() {
        // Given
        Map<String, String> changes = new HashMap<>();
        changes.put("sql-guard.active-strategy", "BLOCK");

        long beforeTimestamp = System.currentTimeMillis();

        // When
        ConfigChangeEvent event = new ConfigChangeEvent(changes, "application");

        long afterTimestamp = System.currentTimeMillis();

        // Then
        assertTrue(event.getTimestamp() >= beforeTimestamp);
        assertTrue(event.getTimestamp() <= afterTimestamp);
    }

    /**
     * Test implementation of ConfigCenterAdapter for testing purposes.
     */
    private static class TestConfigCenterAdapter implements ConfigCenterAdapter {

        private int reloadCount = 0;
        private ConfigChangeEvent lastEvent;

        @Override
        public void onConfigChange(ConfigChangeEvent event) {
            this.lastEvent = event;
            reloadConfig();
        }

        @Override
        public void reloadConfig() {
            reloadCount++;
        }

        public int getReloadCount() {
            return reloadCount;
        }

        public ConfigChangeEvent getLastEvent() {
            return lastEvent;
        }
    }
}
