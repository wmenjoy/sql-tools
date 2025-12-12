package com.footstone.sqlguard.parser;

/**
 * Custom runtime exception thrown when SQL parsing fails.
 * Wraps the original JSQLParserException and provides descriptive error messages.
 */
public class SqlParseException extends RuntimeException {

    /**
     * Constructs a new SqlParseException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the cause (which is saved for later retrieval by the getCause() method)
     */
    public SqlParseException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new SqlParseException with the specified detail message.
     *
     * @param message the detail message
     */
    public SqlParseException(String message) {
        super(message);
    }
}
