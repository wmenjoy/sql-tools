package com.footstone.sqlguard.validator.rule.impl;

import com.footstone.sqlguard.validator.rule.CheckerConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration for DummyConditionChecker.
 *
 * <p>Defines patterns for detecting invalid/dummy WHERE conditions that make the WHERE clause
 * meaningless, resulting in full-table scans despite apparent WHERE clause presence.</p>
 *
 * <p><strong>Default Patterns:</strong></p>
 * <ul>
 *   <li>"1=1" - Common dummy condition</li>
 *   <li>"1 = 1" - With spaces</li>
 *   <li>"'1'='1'" - String constant equality</li>
 *   <li>"true" - Boolean literal</li>
 *   <li>"'a'='a'" - Another string constant pattern</li>
 * </ul>
 *
 * <p><strong>Custom Patterns:</strong></p>
 * <p>Organizations can define additional dummy condition patterns specific to their codebase
 * (e.g., "0=0", "null is null", etc.).</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * DummyConditionConfig config = new DummyConditionConfig();
 * config.setCustomPatterns(Arrays.asList("0=0", "2=2"));
 * DummyConditionChecker checker = new DummyConditionChecker(config);
 * }</pre>
 */
public class DummyConditionConfig extends CheckerConfig {

  /**
   * Default dummy condition patterns.
   */
  private List<String> patterns = new ArrayList<>(Arrays.asList(
      "1=1",
      "1 = 1",
      "'1'='1'",
      "true",
      "'a'='a'"
  ));

  /**
   * User-defined custom patterns for organization-specific dummy conditions.
   */
  private List<String> customPatterns = new ArrayList<>();

  /**
   * Default constructor with checker enabled.
   */
  public DummyConditionConfig() {
    super();
  }

  /**
   * Constructor with enabled flag.
   *
   * @param enabled whether the checker is enabled
   */
  public DummyConditionConfig(boolean enabled) {
    super(enabled);
  }

  /**
   * Gets the default dummy condition patterns.
   *
   * @return list of default patterns
   */
  public List<String> getPatterns() {
    return patterns;
  }

  /**
   * Sets the default dummy condition patterns.
   *
   * @param patterns list of patterns to use
   */
  public void setPatterns(List<String> patterns) {
    this.patterns = patterns != null ? patterns : new ArrayList<>();
  }

  /**
   * Gets the custom dummy condition patterns.
   *
   * @return list of custom patterns
   */
  public List<String> getCustomPatterns() {
    return customPatterns;
  }

  /**
   * Sets custom dummy condition patterns.
   *
   * @param customPatterns list of organization-specific patterns
   */
  public void setCustomPatterns(List<String> customPatterns) {
    this.customPatterns = customPatterns != null ? customPatterns : new ArrayList<>();
  }
}


















