package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuration class for DangerousFunctionChecker.
 *
 * <p>DangerousFunctionConfig extends CheckerConfig to provide configuration for the
 * dangerous function validation rule that detects and blocks dangerous database functions
 * (load_file, sys_exec, sleep, etc.) that enable file operations, OS command execution,
 * and DoS attacks.</p>
 *
 * <p><strong>Default Configuration (Secure by Default):</strong></p>
 * <ul>
 *   <li>enabled: true (active by default for security)</li>
 *   <li>riskLevel: CRITICAL (highest severity for dangerous functions)</li>
 *   <li>deniedFunctions: load_file, into_outfile, into_dumpfile, sys_exec, sys_eval, sleep, benchmark</li>
 * </ul>
 *
 * <p><strong>Security Rationale:</strong></p>
 * <p>Dangerous database functions can be exploited for various attacks:</p>
 * <ul>
 *   <li><strong>load_file:</strong> Arbitrary file read from server filesystem</li>
 *   <li><strong>into_outfile/into_dumpfile:</strong> File write operations</li>
 *   <li><strong>sys_exec/sys_eval:</strong> OS command execution (MySQL UDF)</li>
 *   <li><strong>sleep/benchmark:</strong> DoS attacks via query delay</li>
 * </ul>
 *
 * <p><strong>Attack Examples:</strong></p>
 * <pre>{@code
 * -- File read attack
 * SELECT load_file('/etc/passwd')
 *
 * -- OS command execution (MySQL UDF)
 * SELECT sys_exec('whoami')
 *
 * -- DoS attack via sleep
 * SELECT * FROM users WHERE sleep(10) = 0
 *
 * -- Time-based blind SQL injection
 * SELECT * FROM users WHERE IF(1=1, sleep(5), 0)
 * }</pre>
 *
 * <p><strong>YAML Configuration Example:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     dangerous-function:
 *       enabled: true
 *       risk-level: CRITICAL
 *       denied-functions:
 *         - load_file
 *         - sys_exec
 *         - sys_eval
 *         - sleep
 *         - benchmark
 *         - into_outfile
 *         - into_dumpfile
 * }</pre>
 *
 * @see CheckerConfig
 * @see DangerousFunctionChecker
 * @since 1.0.0
 */
public class DangerousFunctionConfig extends CheckerConfig {

  /**
   * Default list of dangerous functions to deny.
   * All function names are stored in lowercase for case-insensitive matching.
   */
  private static final List<String> DEFAULT_DENIED_FUNCTIONS = Arrays.asList(
      "load_file",       // MySQL: Arbitrary file read
      "into_outfile",    // MySQL: File write (handled separately, but included for completeness)
      "into_dumpfile",   // MySQL: Binary file write
      "sys_exec",        // MySQL UDF: OS command execution
      "sys_eval",        // MySQL UDF: OS command execution with output
      "sleep",           // MySQL/PostgreSQL: DoS via delay
      "benchmark",       // MySQL: DoS via CPU-intensive operation
      "pg_sleep",        // PostgreSQL: DoS via delay
      "waitfor",         // SQL Server: DoS via delay (WAITFOR DELAY)
      "xp_cmdshell",     // SQL Server: OS command execution
      "dbms_pipe"        // Oracle: Inter-session communication (potential data exfiltration)
  );

  /**
   * List of denied function names (stored in lowercase for case-insensitive matching).
   */
  private List<String> deniedFunctions;

  /**
   * Creates a DangerousFunctionConfig with default settings:
   * enabled=true, riskLevel=CRITICAL, default deniedFunctions list.
   */
  public DangerousFunctionConfig() {
    super();
    setRiskLevel(RiskLevel.CRITICAL);
    this.deniedFunctions = new ArrayList<>(DEFAULT_DENIED_FUNCTIONS);
  }

  /**
   * Creates a DangerousFunctionConfig with specified enabled state
   * and CRITICAL risk level with default deniedFunctions list.
   *
   * @param enabled whether the checker should be enabled
   */
  public DangerousFunctionConfig(boolean enabled) {
    super(enabled);
    setRiskLevel(RiskLevel.CRITICAL);
    this.deniedFunctions = new ArrayList<>(DEFAULT_DENIED_FUNCTIONS);
  }

  /**
   * Creates a DangerousFunctionConfig with specified enabled state
   * and custom deniedFunctions list.
   *
   * @param enabled         whether the checker should be enabled
   * @param deniedFunctions list of function names to deny (case-insensitive)
   */
  public DangerousFunctionConfig(boolean enabled, List<String> deniedFunctions) {
    super(enabled);
    setRiskLevel(RiskLevel.CRITICAL);
    setDeniedFunctions(deniedFunctions);
  }

  /**
   * Returns the list of denied function names.
   * All names are stored in lowercase for case-insensitive matching.
   *
   * @return list of denied function names (lowercase)
   */
  public List<String> getDeniedFunctions() {
    return deniedFunctions;
  }

  /**
   * Sets the list of denied function names.
   * Function names are converted to lowercase for case-insensitive matching.
   *
   * @param deniedFunctions list of function names to deny
   */
  public void setDeniedFunctions(List<String> deniedFunctions) {
    if (deniedFunctions == null) {
      this.deniedFunctions = new ArrayList<>();
    } else {
      // Convert all function names to lowercase for case-insensitive matching
      this.deniedFunctions = deniedFunctions.stream()
          .map(String::toLowerCase)
          .collect(Collectors.toList());
    }
  }

  /**
   * Adds a function name to the denied list.
   * Function name is converted to lowercase for case-insensitive matching.
   *
   * @param functionName the function name to add
   */
  public void addDeniedFunction(String functionName) {
    if (functionName != null && !functionName.trim().isEmpty()) {
      String lowerName = functionName.toLowerCase();
      if (!deniedFunctions.contains(lowerName)) {
        deniedFunctions.add(lowerName);
      }
    }
  }

  /**
   * Removes a function name from the denied list.
   *
   * @param functionName the function name to remove
   */
  public void removeDeniedFunction(String functionName) {
    if (functionName != null) {
      deniedFunctions.remove(functionName.toLowerCase());
    }
  }

  /**
   * Checks if a function name is in the denied list.
   * Comparison is case-insensitive.
   *
   * @param functionName the function name to check
   * @return true if the function is denied, false otherwise
   */
  public boolean isDenied(String functionName) {
    if (functionName == null) {
      return false;
    }
    return deniedFunctions.contains(functionName.toLowerCase());
  }

  /**
   * Returns the default list of denied functions.
   *
   * @return unmodifiable list of default denied function names
   */
  public static List<String> getDefaultDeniedFunctions() {
    return new ArrayList<>(DEFAULT_DENIED_FUNCTIONS);
  }
}
