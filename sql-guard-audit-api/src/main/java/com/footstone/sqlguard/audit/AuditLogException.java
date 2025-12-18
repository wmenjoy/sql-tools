package com.footstone.sqlguard.audit;

/**
 * Exception thrown when an error occurs during audit log writing.
 *
 * <p>This exception indicates a failure in the audit logging mechanism itself,
 * such as I/O errors, serialization failures, or backend service unavailability.
 * It does not indicate validation errors (which throw {@link IllegalArgumentException}).</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * try {
 *     writer.writeAuditLog(event);
 * } catch (AuditLogException e) {
 *     logger.error("Failed to write audit log", e);
 *     // Handle audit logging failure
 * }
 * }</pre>
 */
public class AuditLogException extends Exception {

    /**
     * Constructs a new AuditLogException with the specified detail message.
     *
     * @param message the detail message
     */
    public AuditLogException(String message) {
        super(message);
    }

    /**
     * Constructs a new AuditLogException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public AuditLogException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new AuditLogException with the specified cause.
     *
     * @param cause the cause of this exception
     */
    public AuditLogException(Throwable cause) {
        super(cause);
    }
}

