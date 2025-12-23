package org.gradle.api;

import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;

import java.io.File;

/**
 * Stub interface for Gradle Project API.
 * This is a minimal implementation to allow compilation without full Gradle dependencies.
 */
public interface Project {
    ExtensionContainer getExtensions();
    TaskContainer getTasks();
    File getProjectDir();
    File getBuildDir();
}














