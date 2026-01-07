package org.gradle.api;

import org.gradle.api.logging.Logger;

/**
 * Stub class for Gradle DefaultTask API.
 * This is a minimal implementation to allow compilation without full Gradle dependencies.
 */
public abstract class DefaultTask implements Task {
    private String group;
    private String description;
    private Project project;

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Logger getLogger() {
        return new Logger() {
            @Override
            public void info(String message) {
                System.out.println("[INFO] " + message);
            }

            @Override
            public void warn(String message) {
                System.out.println("[WARN] " + message);
            }

            @Override
            public void error(String message) {
                System.err.println("[ERROR] " + message);
            }
        };
    }

    public Task dependsOn(Object... paths) {
        return this;
    }
}
















