package com.footstone.sqlguard.spring.config.center.apollo;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * Configuration properties for Apollo config center integration.
 *
 * <p>These properties control Apollo config center adapter behavior and namespace monitoring.</p>
 *
 * <p><strong>Example Configuration:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   config-center:
 *     apollo:
 *       enabled: true
 *       namespaces:
 *         - application
 *         - sql-guard
 * }</pre>
 *
 * @see ApolloConfigCenterAdapter
 */
@ConfigurationProperties(prefix = "sql-guard.config-center.apollo")
public class ApolloConfigCenterProperties {

    /**
     * Enable Apollo config center integration.
     *
     * <p>When enabled, the adapter will monitor Apollo for configuration changes
     * and trigger SQL Guard configuration reload.</p>
     *
     * <p>Default: false</p>
     */
    private boolean enabled = false;

    /**
     * Apollo namespaces to monitor for configuration changes.
     *
     * <p>The adapter will listen for changes in all specified namespaces.
     * Changes to any sql-guard.* property in these namespaces will trigger a reload.</p>
     *
     * <p>Default: ["application"]</p>
     */
    private List<String> namespaces = Arrays.asList("application");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getNamespaces() {
        return namespaces;
    }

    public void setNamespaces(List<String> namespaces) {
        this.namespaces = namespaces;
    }

    @Override
    public String toString() {
        return "ApolloConfigCenterProperties{" +
                "enabled=" + enabled +
                ", namespaces=" + namespaces +
                '}';
    }
}




