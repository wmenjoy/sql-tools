package com.footstone.sqlguard.scanner.model;

import com.footstone.sqlguard.config.SqlGuardConfig;

import java.nio.file.Path;

/**
 * Immutable context holder for SQL scanning operations.
 *
 * <p>ScanContext encapsulates the project path and configuration settings
 * required for static SQL analysis. Once constructed, its fields cannot be modified.</p>
 *
 * <p><strong>Immutability:</strong> All fields are final and cannot be changed
 * after construction, ensuring thread-safety and predictable behavior.</p>
 *
 * @see SqlGuardConfig
 */
public class ScanContext {
  private final Path projectPath;
  private final SqlGuardConfig config;

  /**
   * Constructs a new immutable ScanContext.
   *
   * @param projectPath the root path of the project to scan (required)
   * @param config the SQL Guard configuration (required)
   * @throws IllegalArgumentException if any parameter is null
   */
  public ScanContext(Path projectPath, SqlGuardConfig config) {
    if (projectPath == null) {
      throw new IllegalArgumentException("projectPath cannot be null");
    }
    if (config == null) {
      throw new IllegalArgumentException("config cannot be null");
    }

    this.projectPath = projectPath;
    this.config = config;
  }

  /**
   * Gets the project root path.
   *
   * @return the project path (immutable)
   */
  public Path getProjectPath() {
    return projectPath;
  }

  /**
   * Gets the SQL Guard configuration.
   *
   * @return the configuration (immutable)
   */
  public SqlGuardConfig getConfig() {
    return config;
  }

  @Override
  public String toString() {
    return "ScanContext{" +
        "projectPath=" + projectPath +
        ", config=" + config +
        '}';
  }
}

