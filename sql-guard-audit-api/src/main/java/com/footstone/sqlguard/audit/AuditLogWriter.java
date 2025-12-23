package com.footstone.sqlguard.audit;

/**
 * Interface for writing audit log events.
 *
 * <p>AuditLogWriter defines the contract for audit log output implementations.
 * All implementations must validate required fields before writing and handle
 * errors appropriately.</p>
 *
 * <p><strong>Required Fields Validation:</strong></p>
 * <ul>
 *   <li>{@code sql} - The SQL statement being audited (must not be null)</li>
 *   <li>{@code sqlType} - The SQL command type (must not be null)</li>
 *   <li>{@code mapperId} - The mapper identifier (must not be null)</li>
 *   <li>{@code timestamp} - The event timestamp (must not be null)</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong></p>
 * <p>Implementations should be thread-safe as they may be called concurrently
 * from multiple threads in a multi-threaded application environment. Consider
 * using thread-local buffers, synchronized blocks, or concurrent data structures
 * as appropriate for the specific implementation.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * AuditLogWriter writer = new LogbackAuditLogWriter();
 * AuditEvent event = AuditEvent.builder()
 *     .sql("SELECT * FROM users WHERE id = ?")
 *     .sqlType(SqlCommandType.SELECT)
 *     .mapperId("UserMapper.selectById")
 *     .timestamp(Instant.now())
 *     .build();
 * writer.writeAuditLog(event);
 * }</pre>
 *
 * @see AuditEvent
 * @see AuditLogException
 * @since 2.0.0
 */
public interface AuditLogWriter {

    /**
     * Writes an audit log event.
     *
     * <p>This method validates that all required fields are present in the event
     * before writing. If any required field is null, an {@link IllegalArgumentException}
     * is thrown.</p>
     *
     * @param event the audit event to write (must not be null)
     * @throws AuditLogException if an error occurs while writing the audit log
     * @throws IllegalArgumentException if event is null or required fields are missing
     */
    void writeAuditLog(AuditEvent event) throws AuditLogException;
}
