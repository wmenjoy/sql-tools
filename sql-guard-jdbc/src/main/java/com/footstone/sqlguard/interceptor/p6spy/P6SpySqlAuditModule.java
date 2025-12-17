package com.footstone.sqlguard.interceptor.p6spy;

import com.footstone.sqlguard.audit.AuditLogWriter;
import com.footstone.sqlguard.audit.LogbackAuditWriter;
import com.p6spy.engine.event.JdbcEventListener;
import com.p6spy.engine.spy.P6Factory;
import com.p6spy.engine.spy.P6LoadableOptions;
import com.p6spy.engine.spy.option.P6OptionsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * P6Spy module for audit listener registration.
 *
 * <p>This module registers P6SpySqlAuditListener with P6Spy for automatic
 * discovery via SPI (Service Provider Interface). It implements P6Factory to
 * integrate with P6Spy's module loading mechanism.</p>
 *
 * <p><strong>SPI Registration:</strong></p>
 * <p>Create file: META-INF/services/com.p6spy.engine.spy.P6Factory</p>
 * <p>Content: com.footstone.sqlguard.interceptor.p6spy.P6SpySqlAuditModule</p>
 *
 * <p><strong>Configuration:</strong></p>
 * <pre>
 * # spy.properties
 * modulelist=com.footstone.sqlguard.interceptor.p6spy.P6SpySqlSafetyModule,\
 *            com.footstone.sqlguard.interceptor.p6spy.P6SpySqlAuditModule
 * appender=com.p6spy.engine.spy.appender.Slf4JLogger
 * </pre>
 *
 * <p><strong>Lifecycle:</strong></p>
 * <ol>
 *   <li>P6Spy discovers module via ServiceLoader</li>
 *   <li>Calls getJdbcEventListener()</li>
 *   <li>Returns singleton listener instance</li>
 *   <li>P6Spy registers listener for all JDBC events</li>
 * </ol>
 *
 * @see P6SpySqlAuditListener
 * @see AuditLogWriter
 * @see P6Factory
 */
public class P6SpySqlAuditModule implements P6Factory {

    private static final Logger logger = LoggerFactory.getLogger(P6SpySqlAuditModule.class);

    /**
     * Singleton audit log writer instance.
     * Can be set via setAuditLogWriter() before P6Spy initialization.
     */
    private static AuditLogWriter auditLogWriter;

    /**
     * Singleton audit listener instance.
     */
    private static P6SpySqlAuditListener auditListener;

    /**
     * Static initialization block to create listener.
     */
    static {
        try {
            logger.info("Initializing P6Spy SQL Audit Module...");

            // Initialize audit log writer if not already set
            if (auditLogWriter == null) {
                // Default to Logback implementation
                auditLogWriter = new LogbackAuditWriter();
                logger.info("Using default LogbackAuditWriter");
            }

            // Create audit listener
            auditListener = new P6SpySqlAuditListener(auditLogWriter);

            logger.info("P6Spy SQL Audit Module initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize P6Spy SQL Audit Module", e);
            throw new IllegalStateException("P6Spy SQL Audit Module initialization failed", e);
        }
    }

    /**
     * Sets the AuditLogWriter for this module.
     * Must be called before P6Spy initialization.
     *
     * <p><strong>Usage Example:</strong></p>
     * <pre>{@code
     * // Before P6Spy initialization
     * AuditLogWriter customWriter = new CustomAuditWriter();
     * P6SpySqlAuditModule.setAuditLogWriter(customWriter);
     * }</pre>
     *
     * @param writer the audit log writer (must not be null)
     * @throws IllegalArgumentException if writer is null
     */
    public static void setAuditLogWriter(AuditLogWriter writer) {
        if (writer == null) {
            throw new IllegalArgumentException("AuditLogWriter cannot be null");
        }
        auditLogWriter = writer;
        logger.info("Custom AuditLogWriter configured: {}", writer.getClass().getName());
    }

    /**
     * Returns the loadable options for this module.
     *
     * <p>This module doesn't require custom options, so returns null.</p>
     *
     * @param optionsRepository the P6Spy options repository
     * @return null (no custom options)
     */
    public P6LoadableOptions getOptions(P6OptionsRepository optionsRepository) {
        return null;
    }

    /**
     * Returns the JDBC event listener for audit logging.
     *
     * <p>This method is called by P6Spy during module initialization.
     * It returns the singleton P6SpySqlAuditListener instance.</p>
     *
     * @return the audit listener, or null if initialization failed
     */
    public JdbcEventListener getJdbcEventListener() {
        return auditListener;
    }

    /**
     * Gets the singleton audit listener instance for testing purposes.
     *
     * @return the audit listener, or null if initialization failed
     */
    static P6SpySqlAuditListener getAuditListener() {
        return auditListener;
    }

    /**
     * Gets the singleton audit log writer instance for testing purposes.
     *
     * @return the audit log writer, or null if not initialized
     */
    static AuditLogWriter getAuditLogWriter() {
        return auditLogWriter;
    }
}
