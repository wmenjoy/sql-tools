package com.footstone.sqlguard.scanner.cli;

/**
 * Exception thrown when command-line input validation fails.
 *
 * <p>ValidationException provides clear, actionable error messages to help
 * users correct their input. Messages should include:</p>
 * <ul>
 *   <li>What validation failed</li>
 *   <li>Why it failed</li>
 *   <li>How to fix it (suggestions)</li>
 * </ul>
 *
 * <p><strong>Example Usage:</strong></p>
 * <pre>{@code
 * throw new ValidationException(
 *     "Project path does not exist: /invalid/path\n" +
 *     "Please provide a valid project root directory."
 * );
 * }</pre>
 */
public class ValidationException extends Exception {

  /**
   * Constructs a new ValidationException with the specified message.
   *
   * @param message the detailed error message with suggestions
   */
  public ValidationException(String message) {
    super(message);
  }

  /**
   * Constructs a new ValidationException with message and cause.
   *
   * @param message the detailed error message
   * @param cause the underlying cause
   */
  public ValidationException(String message, Throwable cause) {
    super(message, cause);
  }
}






