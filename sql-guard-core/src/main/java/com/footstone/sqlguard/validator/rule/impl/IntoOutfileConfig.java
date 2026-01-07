package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.core.model.RiskLevel;
import com.footstone.sqlguard.validator.rule.CheckerConfig;

/**
 * Configuration class for IntoOutfileChecker.
 *
 * <p>IntoOutfileConfig extends CheckerConfig to provide configuration for the IntoOutfile
 * validation rule that detects MySQL file write operations (SELECT INTO OUTFILE/DUMPFILE)
 * which can be used for arbitrary file writes and data exfiltration attacks.</p>
 *
 * <p><strong>Default Configuration (Secure by Default):</strong></p>
 * <ul>
 *   <li>enabled: true (active by default for security)</li>
 *   <li>riskLevel: CRITICAL (highest severity for file write attacks)</li>
 * </ul>
 *
 * <p><strong>Security Rationale:</strong></p>
 * <p>MySQL's SELECT INTO OUTFILE and INTO DUMPFILE allow writing query results to
 * the file system. This capability can be exploited for:</p>
 * <ul>
 *   <li>Arbitrary file writes to system directories</li>
 *   <li>Data exfiltration to attacker-controlled paths</li>
 *   <li>Web shell creation via PHP/JSP file writes</li>
 *   <li>Configuration file manipulation</li>
 * </ul>
 *
 * <p><strong>Attack Examples:</strong></p>
 * <pre>{@code
 * -- Web shell creation
 * SELECT '<?php system($_GET["cmd"]); ?>' INTO OUTFILE '/var/www/html/shell.php'
 *
 * -- Data exfiltration
 * SELECT * INTO OUTFILE '/tmp/credentials.txt' FROM users
 *
 * -- Binary file creation
 * SELECT UNHEX('4D5A...') INTO DUMPFILE '/tmp/malware.exe'
 * }</pre>
 *
 * <p><strong>Oracle Syntax Differentiation:</strong></p>
 * <p>This checker correctly differentiates between:</p>
 * <ul>
 *   <li>MySQL file operations (BLOCK): {@code SELECT * INTO OUTFILE '/path'}</li>
 *   <li>Oracle variable assignment (ALLOW): {@code SELECT id INTO v_id FROM users}</li>
 * </ul>
 *
 * <p><strong>YAML Configuration Example:</strong></p>
 * <pre>{@code
 * sql-guard:
 *   rules:
 *     into-outfile:
 *       enabled: true
 *       risk-level: CRITICAL
 * }</pre>
 *
 * @see CheckerConfig
 * @see IntoOutfileChecker
 * @since 1.0.0
 */
public class IntoOutfileConfig extends CheckerConfig {

  /**
   * Creates an IntoOutfileConfig with default settings:
   * enabled=true, riskLevel=CRITICAL.
   */
  public IntoOutfileConfig() {
    super();
    setRiskLevel(RiskLevel.CRITICAL);
  }

  /**
   * Creates an IntoOutfileConfig with specified enabled state
   * and CRITICAL risk level.
   *
   * @param enabled whether the checker should be enabled
   */
  public IntoOutfileConfig(boolean enabled) {
    super(enabled);
    setRiskLevel(RiskLevel.CRITICAL);
  }
}
