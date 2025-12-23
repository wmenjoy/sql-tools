package org.gradle.api.tasks;

import org.gradle.api.Action;
import org.gradle.api.Task;

/**
 * Stub interface for Gradle TaskContainer API.
 */
public interface TaskContainer {
    <T extends Task> void register(String name, Class<T> type, Action<? super T> configuration);
    Task findByName(String name);
}














