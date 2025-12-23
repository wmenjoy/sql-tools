package org.gradle.api;

/**
 * Stub interface for Gradle Plugin API.
 * This is a minimal implementation to allow compilation without full Gradle dependencies.
 */
public interface Plugin<T> {
    void apply(T target);
}














