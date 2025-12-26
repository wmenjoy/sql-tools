package com.footstone.sqlguard.spring.config.center;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Event representing configuration changes from a config center.
 *
 * <p>This immutable class captures all changed configuration keys and their new values,
 * along with metadata about the change source (namespace) and timing.</p>
 *
 * <p><strong>Thread Safety:</strong> This class is immutable and thread-safe.</p>
 *
 * <p><strong>Example Usage:</strong></p>
 * <pre>{@code
 * Map<String, String> changes = new HashMap<>();
 * changes.put("sql-guard.active-strategy", "BLOCK");
 * changes.put("sql-guard.rules.no-where-clause.enabled", "false");
 *
 * ConfigChangeEvent event = new ConfigChangeEvent(changes, "application");
 *
 * if (event.hasChanged("sql-guard.active-strategy")) {
 *     String newValue = event.getNewValue("sql-guard.active-strategy");
 *     // Apply change...
 * }
 * }</pre>
 */
public class ConfigChangeEvent {

    private final Map<String, String> changedKeys;
    private final String namespace;
    private final long timestamp;

    /**
     * Create a new configuration change event.
     *
     * @param changedKeys map of changed property keys to their new values (must not be null)
     * @param namespace   configuration namespace or data ID (may be null)
     * @throws NullPointerException if changedKeys is null
     */
    public ConfigChangeEvent(Map<String, String> changedKeys, String namespace) {
        Objects.requireNonNull(changedKeys, "changedKeys cannot be null");
        this.changedKeys = Collections.unmodifiableMap(new HashMap<>(changedKeys));
        this.namespace = namespace;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Check if a specific property key changed.
     *
     * @param key property key to check (e.g., "sql-guard.active-strategy")
     * @return true if the key changed, false otherwise
     */
    public boolean hasChanged(String key) {
        return changedKeys.containsKey(key);
    }

    /**
     * Get the new value for a changed property key.
     *
     * @param key property key (e.g., "sql-guard.active-strategy")
     * @return new value for the key, or null if the key did not change
     */
    public String getNewValue(String key) {
        return changedKeys.get(key);
    }

    /**
     * Get all changed property keys and their new values.
     *
     * <p>The returned map is unmodifiable.</p>
     *
     * @return unmodifiable map of changed keys to new values
     */
    public Map<String, String> getChangedKeys() {
        return changedKeys;
    }

    /**
     * Get the configuration namespace or data ID.
     *
     * <p>For Apollo, this is the namespace (e.g., "application").
     * For Nacos, this is the data ID (e.g., "sql-guard").</p>
     *
     * @return namespace or data ID, may be null
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Get the timestamp when this event was created.
     *
     * @return timestamp in milliseconds since epoch
     */
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "ConfigChangeEvent{" +
                "changedKeys=" + changedKeys.size() + " keys" +
                ", namespace='" + namespace + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigChangeEvent that = (ConfigChangeEvent) o;
        return timestamp == that.timestamp &&
                Objects.equals(changedKeys, that.changedKeys) &&
                Objects.equals(namespace, that.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(changedKeys, namespace, timestamp);
    }
}















