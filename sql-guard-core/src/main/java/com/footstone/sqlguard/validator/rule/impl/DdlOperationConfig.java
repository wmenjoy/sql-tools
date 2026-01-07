package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration class for DdlOperationChecker.
 *
 * <p>DdlOperationConfig extends CheckerConfig to provide configuration for the DDL operation
 * validation rule. It controls which DDL operations (CREATE, ALTER, DROP, TRUNCATE) are
 * allowed to be executed at the application layer.</p>
 *
 * <h2>Default Behavior (Secure by Default)</h2>
 * <ul>
 *   <li>enabled = true (checker is active)</li>
 *   <li>riskLevel = CRITICAL (DDL at application layer is serious violation)</li>
 *   <li>allowedOperations = [] (empty list = block all DDL operations)</li>
 * </ul>
 *
 * <h2>Why Block Application-Layer DDL?</h2>
 * <ul>
 *   <li>Schema changes should occur via controlled migration scripts (Flyway, Liquibase)</li>
 *   <li>Runtime DDL indicates poor deployment practices</li>
 *   <li>DDL operations can cause downtime and data loss</li>
 *   <li>Production environments should have strict schema change controls</li>
 * </ul>
 *
 * <h2>Supported Operation Names</h2>
 * <ul>
 *   <li>CREATE - CREATE TABLE, CREATE INDEX, CREATE VIEW, etc.</li>
 *   <li>ALTER - ALTER TABLE, ALTER INDEX, etc.</li>
 *   <li>DROP - DROP TABLE, DROP INDEX, DROP VIEW, etc.</li>
 *   <li>TRUNCATE - TRUNCATE TABLE</li>
 * </ul>
 *
 * <h2>Configuration Examples</h2>
 * <pre>{@code
 * // Default: Block all DDL operations
 * DdlOperationConfig config = new DdlOperationConfig();
 *
 * // Allow specific DDL operations (e.g., for migration scripts)
 * DdlOperationConfig config = new DdlOperationConfig();
 * config.setAllowedOperations(Arrays.asList("CREATE", "ALTER"));
 *
 * // Disable checker entirely (not recommended for production)
 * DdlOperationConfig config = new DdlOperationConfig(false);
 * }</pre>
 *
 * @see CheckerConfig
 * @see DdlOperationChecker
 * @since 1.0.0
 */
public class DdlOperationConfig extends CheckerConfig {

    /**
     * Valid DDL operation names that can be configured.
     */
    private static final Set<String> VALID_OPERATIONS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("CREATE", "ALTER", "DROP", "TRUNCATE"))
    );

    /**
     * List of allowed DDL operation types.
     * Empty list means all DDL operations are blocked (default secure behavior).
     */
    private List<String> allowedOperations;

    /**
     * Creates a DdlOperationConfig with default settings:
     * enabled=true, riskLevel=CRITICAL, allowedOperations=[] (block all).
     */
    public DdlOperationConfig() {
        super();
        setRiskLevel(RiskLevel.CRITICAL);
        this.allowedOperations = new ArrayList<>();
    }

    /**
     * Creates a DdlOperationConfig with specified enabled state.
     * Uses CRITICAL risk level and empty allowedOperations (block all).
     *
     * @param enabled whether the checker should be enabled
     */
    public DdlOperationConfig(boolean enabled) {
        super(enabled);
        setRiskLevel(RiskLevel.CRITICAL);
        this.allowedOperations = new ArrayList<>();
    }

    /**
     * Creates a DdlOperationConfig with specified enabled state and allowed operations.
     *
     * @param enabled whether the checker should be enabled
     * @param allowedOperations list of allowed DDL operation types (CREATE, ALTER, DROP, TRUNCATE)
     * @throws IllegalArgumentException if any operation name is invalid
     */
    public DdlOperationConfig(boolean enabled, List<String> allowedOperations) {
        super(enabled);
        setRiskLevel(RiskLevel.CRITICAL);
        setAllowedOperations(allowedOperations);
    }

    /**
     * Returns the list of allowed DDL operation types.
     *
     * @return unmodifiable list of allowed operations (empty = block all)
     */
    public List<String> getAllowedOperations() {
        return Collections.unmodifiableList(allowedOperations);
    }

    /**
     * Sets the list of allowed DDL operation types.
     *
     * <p>Operation names are normalized to uppercase. Invalid operation names
     * will cause an IllegalArgumentException.</p>
     *
     * @param allowedOperations list of allowed operations (CREATE, ALTER, DROP, TRUNCATE)
     * @throws IllegalArgumentException if any operation name is invalid
     */
    public void setAllowedOperations(List<String> allowedOperations) {
        if (allowedOperations == null) {
            this.allowedOperations = new ArrayList<>();
            return;
        }

        List<String> normalized = new ArrayList<>();
        for (String op : allowedOperations) {
            if (op == null || op.trim().isEmpty()) {
                continue; // Skip null/empty entries
            }
            String normalizedOp = op.trim().toUpperCase();
            if (!VALID_OPERATIONS.contains(normalizedOp)) {
                throw new IllegalArgumentException(
                        "Invalid DDL operation name: '" + op + "'. " +
                        "Valid operations are: " + VALID_OPERATIONS);
            }
            normalized.add(normalizedOp);
        }
        this.allowedOperations = normalized;
    }

    /**
     * Checks if a specific DDL operation type is allowed.
     *
     * @param operationType the operation type to check (CREATE, ALTER, DROP, TRUNCATE)
     * @return true if the operation is allowed, false otherwise
     */
    public boolean isOperationAllowed(String operationType) {
        if (operationType == null || operationType.trim().isEmpty()) {
            return false;
        }
        return allowedOperations.contains(operationType.trim().toUpperCase());
    }

    /**
     * Returns the set of valid DDL operation names.
     *
     * @return unmodifiable set of valid operation names
     */
    public static Set<String> getValidOperations() {
        return VALID_OPERATIONS;
    }
}
