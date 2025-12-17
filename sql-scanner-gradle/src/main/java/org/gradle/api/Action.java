package org.gradle.api;

/**
 * Stub interface for Gradle Action API.
 */
@FunctionalInterface
public interface Action<T> {
    void execute(T t);
}



