package org.gradle.api;

/**
 * Stub interface for Gradle Task API.
 */
public interface Task {
    String getGroup();
    void setGroup(String group);
    String getDescription();
    void setDescription(String description);
    Task dependsOn(Object... paths);
}



