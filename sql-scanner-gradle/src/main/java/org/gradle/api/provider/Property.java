package org.gradle.api.provider;

/**
 * Stub interface for Gradle Property API.
 */
public interface Property<T> {
    T get();
    T getOrNull();
    T getOrElse(T defaultValue);
    void set(T value);
    void convention(T value);
}
















