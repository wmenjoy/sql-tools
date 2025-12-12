package com.footstone.sqlguard.config;

/**
 * Exception thrown when configuration loading fails.
 * Wraps underlying YAML parsing errors with descriptive messages.
 */
public class ConfigLoadException extends Exception {

    public ConfigLoadException(String message) {
        super(message);
    }

    public ConfigLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
