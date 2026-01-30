package com.footstone.sqlguard.spring.config.center.apollo;

import com.footstone.sqlguard.spring.config.SqlGuardProperties;
import com.footstone.sqlguard.spring.config.center.ConfigCenterAdapter;
import com.footstone.sqlguard.spring.config.center.ConfigChangeEvent;
import com.footstone.sqlguard.spring.config.center.ConfigReloadListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Apollo config center adapter for SQL Guard hot-reload.
 *
 * <p>This adapter integrates with Ctrip Apollo config center to enable runtime configuration
 * updates without application restart. When Apollo detects configuration changes, this adapter
 * rebinds Spring properties and notifies registered listeners.</p>
 *
 * <p><strong>Activation Conditions:</strong></p>
 * <ul>
 *   <li>Apollo client library on classpath (com.ctrip.framework.apollo.Config)</li>
 *   <li>Property sql-guard.config-center.apollo.enabled=true</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This adapter is thread-safe. Configuration reload operations
 * are synchronized to prevent concurrent modification issues.</p>
 *
 * <p><strong>Example Configuration:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   config-center:
 *     apollo:
 *       enabled: true
 *       namespaces:
 *         - application
 * }</pre>
 *
 * @see ConfigCenterAdapter
 * @see ApolloConfigCenterProperties
 */
@Configuration
@ConditionalOnClass(name = "com.ctrip.framework.apollo.Config")
@ConditionalOnProperty(prefix = "sql-guard.config-center.apollo", name = "enabled", havingValue = "true")
public class ApolloConfigCenterAdapter implements ConfigCenterAdapter {

    private static final Logger log = LoggerFactory.getLogger(ApolloConfigCenterAdapter.class);

    private final Object apolloConfig;
    private final SqlGuardProperties properties;
    private final ConfigurableEnvironment environment;
    private final List<ConfigReloadListener> listeners;
    private final ApolloConfigCenterProperties apolloProperties;

    /**
     * Create Apollo config center adapter.
     *
     * @param apolloConfig Apollo Config instance (injected via @ApolloConfig)
     * @param properties SQL Guard properties to rebind on config change
     * @param environment Spring environment for property rebinding
     * @param apolloProperties Apollo-specific configuration properties
     * @param listeners optional list of config reload listeners
     */
    public ApolloConfigCenterAdapter(
            Object apolloConfig,
            SqlGuardProperties properties,
            ConfigurableEnvironment environment,
            ApolloConfigCenterProperties apolloProperties,
            List<ConfigReloadListener> listeners) {
        this.apolloConfig = apolloConfig;
        this.properties = properties;
        this.environment = environment;
        this.apolloProperties = apolloProperties;
        this.listeners = listeners != null ? listeners : Collections.emptyList();
        
        log.info("Apollo config center adapter initialized for namespaces: {}", 
                apolloProperties.getNamespaces());
    }

    /**
     * Handle Apollo configuration change events.
     *
     * <p>This method is invoked by Apollo's @ApolloConfigChangeListener when configuration
     * changes are detected. It filters for sql-guard.* properties and triggers reload.</p>
     *
     * @param changeEvent Apollo's native ConfigChangeEvent
     */
    public void onChange(Object changeEvent) {
        try {
            // Use reflection to handle Apollo's ConfigChangeEvent
            Class<?> eventClass = changeEvent.getClass();
            Object changedKeysSet = eventClass.getMethod("changedKeys").invoke(changeEvent);
            String namespace = (String) eventClass.getMethod("getNamespace").invoke(changeEvent);

            if (!isNamespaceMonitored(namespace)) {
                log.debug("Apollo config change ignored for namespace '{}'", namespace);
                return;
            }
            
            @SuppressWarnings("unchecked")
            java.util.Set<String> keys = (java.util.Set<String>) changedKeysSet;
            
            log.info("Apollo config changed in namespace '{}': {} keys changed", namespace, keys.size());

            Map<String, String> sqlGuardChanges = new HashMap<>();
            for (String key : keys) {
                if (key.startsWith("sql-guard.")) {
                    Object change = eventClass.getMethod("getChange", String.class).invoke(changeEvent, key);
                    String oldValue = (String) change.getClass().getMethod("getOldValue").invoke(change);
                    String newValue = (String) change.getClass().getMethod("getNewValue").invoke(change);
                    
                    sqlGuardChanges.put(key, newValue);
                    log.info("  {}: {} -> {}", key, oldValue, newValue);
                }
            }

            if (!sqlGuardChanges.isEmpty()) {
                ConfigChangeEvent event = new ConfigChangeEvent(sqlGuardChanges, namespace);
                onConfigChange(event);
            } else {
                log.debug("No sql-guard.* properties changed, skipping reload");
            }
        } catch (Exception e) {
            log.error("Failed to process Apollo config change event", e);
        }
    }

    private boolean isNamespaceMonitored(String namespace) {
        if (namespace == null) {
            return false;
        }
        List<String> namespaces = apolloProperties != null ? apolloProperties.getNamespaces() : null;
        if (namespaces == null || namespaces.isEmpty()) {
            return true;
        }
        return namespaces.contains(namespace);
    }

    @Override
    public void onConfigChange(ConfigChangeEvent event) {
        log.info("SQL Guard config change detected: {} keys changed", event.getChangedKeys().size());
        reloadConfig();
    }

    @Override
    public void reloadConfig() {
        synchronized (this) {
            try {
                log.info("Reloading SQL Guard configuration from Apollo...");

                // Rebind properties from Apollo config
                rebindProperties();

                // Notify listeners
                notifyListeners();

                log.info("SQL Guard configuration reloaded successfully from Apollo");
            } catch (Exception e) {
                log.error("Failed to reload configuration from Apollo", e);
                throw new RuntimeException("Config reload failed", e);
            }
        }
    }

    /**
     * Rebind SqlGuardProperties from current environment.
     *
     * <p>Uses Spring Boot's Binder to rebind properties from the environment,
     * which includes Apollo's updated values.</p>
     */
    private void rebindProperties() {
        try {
            Binder binder = Binder.get(environment);
            BindResult<?> result = binder.bind("sql-guard", 
                    org.springframework.boot.context.properties.bind.Bindable.ofInstance(properties));

            if (!result.isBound()) {
                log.warn("Failed to rebind SqlGuardProperties from Apollo config");
            } else {
                log.debug("SqlGuardProperties successfully rebound from Apollo");
            }
        } catch (Exception e) {
            log.error("Error rebinding properties from Apollo", e);
            throw e;
        }
    }

    /**
     * Notify all registered config reload listeners.
     *
     * <p>Listeners are notified sequentially. If a listener throws an exception,
     * it is logged but does not prevent other listeners from being notified.</p>
     */
    private void notifyListeners() {
        if (listeners.isEmpty()) {
            log.debug("No config reload listeners registered");
            return;
        }

        log.debug("Notifying {} config reload listeners", listeners.size());
        for (ConfigReloadListener listener : listeners) {
            try {
                listener.onConfigReloaded(null, null);
            } catch (Exception e) {
                log.error("Error notifying config reload listener: {}", listener.getClass().getName(), e);
            }
        }
    }

    @Override
    public String getName() {
        return "ApolloConfigCenterAdapter";
    }
}















