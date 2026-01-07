package com.footstone.sqlguard.spring.config.center;

import com.footstone.sqlguard.config.SqlGuardConfig;

/**
 * Listener for configuration reload events.
 *
 * <p>Implement this interface to receive notifications when SQL Guard configuration is reloaded
 * from a config center. This allows components to react to configuration changes, such as
 * updating validator state or invalidating caches.</p>
 *
 * <p><strong>Thread Safety:</strong> Implementations must be thread-safe as reload events
 * may occur concurrently with normal operations.</p>
 *
 * <p><strong>Example Implementation:</strong></p>
 * <pre>{@code
 * @Component
 * public class ValidatorReloadListener implements ConfigReloadListener {
 *
 *     private final DefaultSqlSafetyValidator validator;
 *
 *     @Override
 *     public void onConfigReloaded(SqlGuardConfig oldConfig, SqlGuardConfig newConfig) {
 *         validator.reloadConfig(newConfig);
 *     }
 * }
 * }</pre>
 *
 * @see ConfigCenterAdapter
 * @see SqlGuardConfig
 */
@FunctionalInterface
public interface ConfigReloadListener {

    /**
     * Called after SQL Guard configuration has been reloaded.
     *
     * <p>This method is invoked after the config center adapter successfully reloads configuration.
     * Implementations should update their internal state to reflect the new configuration.</p>
     *
     * <p><strong>Error Handling:</strong> Implementations should catch and log exceptions to prevent
     * one failing listener from affecting others.</p>
     *
     * @param oldConfig previous configuration (may be null for initial load)
     * @param newConfig new configuration (may be null if reload failed)
     */
    void onConfigReloaded(SqlGuardConfig oldConfig, SqlGuardConfig newConfig);
}
















