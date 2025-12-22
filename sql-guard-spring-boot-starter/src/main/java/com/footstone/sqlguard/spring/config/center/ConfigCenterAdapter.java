package com.footstone.sqlguard.spring.config.center;

/**
 * Extension point for config center integration.
 *
 * <p>Implement this interface to support custom configuration centers (Apollo, Nacos, Consul, etc.).
 * The adapter receives configuration change notifications and triggers SQL Guard configuration reload.</p>
 *
 * <p><strong>Thread Safety:</strong> Implementations must be thread-safe as configuration changes
 * may occur concurrently with SQL validation operations.</p>
 *
 * <p><strong>Example Implementation:</strong></p>
 * <pre>{@code
 * @Configuration
 * @ConditionalOnClass(name = "com.example.configcenter.Client")
 * public class MyConfigCenterAdapter implements ConfigCenterAdapter {
 *
 *     @Override
 *     public void onConfigChange(ConfigChangeEvent event) {
 *         // Handle config change notification
 *         reloadConfig();
 *     }
 *
 *     @Override
 *     public void reloadConfig() {
 *         // Trigger full configuration reload
 *     }
 * }
 * }</pre>
 *
 * @see ConfigChangeEvent
 * @see ConfigReloadListener
 */
public interface ConfigCenterAdapter {

    /**
     * Called when configuration changes in the config center.
     *
     * <p>This method is invoked by the config center client when it detects configuration changes.
     * Implementations should validate the changes and trigger a reload if necessary.</p>
     *
     * @param event configuration change event containing changed keys and values
     * @throws IllegalArgumentException if event is null
     */
    void onConfigChange(ConfigChangeEvent event);

    /**
     * Trigger full configuration reload from the config center.
     *
     * <p>This method forces a complete reload of all SQL Guard configuration from the config center,
     * regardless of whether specific changes were detected. Useful for manual refresh or
     * initialization scenarios.</p>
     *
     * <p><strong>Implementation Note:</strong> This method should be idempotent and thread-safe.</p>
     */
    void reloadConfig();

    /**
     * Get adapter name for logging and diagnostics.
     *
     * <p>Default implementation returns the simple class name. Override to provide a more
     * descriptive name.</p>
     *
     * @return adapter name (e.g., "ApolloConfigCenterAdapter", "NacosConfigCenterAdapter")
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }
}









