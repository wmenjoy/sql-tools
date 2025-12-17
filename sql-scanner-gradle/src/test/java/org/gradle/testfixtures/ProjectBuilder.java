package org.gradle.testfixtures;

import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.Action;
import org.gradle.api.Task;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Test fixture for creating Gradle Project instances in tests.
 */
public class ProjectBuilder {
    private File projectDir;

    private ProjectBuilder() {
        this.projectDir = new File(System.getProperty("java.io.tmpdir"), "test-project");
    }

    public static ProjectBuilder builder() {
        return new ProjectBuilder();
    }

    public ProjectBuilder withProjectDir(File projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    public Project build() {
        return new TestProject(projectDir);
    }

    private static class TestProject implements Project {
        private final File projectDir;
        private final File buildDir;
        private final TestExtensionContainer extensions;
        private final TestTaskContainer tasks;

        public TestProject(File projectDir) {
            this.projectDir = projectDir;
            this.buildDir = new File(projectDir, "build");
            this.extensions = new TestExtensionContainer();
            this.tasks = new TestTaskContainer(this);
        }

        @Override
        public ExtensionContainer getExtensions() {
            return extensions;
        }

        @Override
        public TaskContainer getTasks() {
            return tasks;
        }

        @Override
        public File getProjectDir() {
            return projectDir;
        }

        @Override
        public File getBuildDir() {
            return buildDir;
        }
    }

    private static class TestExtensionContainer implements ExtensionContainer {
        private final Map<String, Object> extensions = new HashMap<>();

        @Override
        public <T> T create(String name, Class<T> type, Object... constructionArguments) {
            try {
                T instance;
                if (constructionArguments.length > 0) {
                    // Try to find constructor with Project parameter
                    instance = type.getConstructor(Project.class).newInstance(constructionArguments);
                } else {
                    instance = type.getDeclaredConstructor().newInstance();
                }
                extensions.put(name, instance);
                return instance;
            } catch (Exception e) {
                throw new RuntimeException("Failed to create extension: " + name, e);
            }
        }

        @Override
        public Object findByName(String name) {
            return extensions.get(name);
        }
    }

    private static class TestTaskContainer implements TaskContainer {
        private final Map<String, Task> tasks = new HashMap<>();
        private final Project project;

        public TestTaskContainer(Project project) {
            this.project = project;
        }

        @Override
        public <T extends Task> void register(String name, Class<T> type, Action<? super T> configuration) {
            try {
                T task = type.getDeclaredConstructor().newInstance();
                if (task instanceof org.gradle.api.DefaultTask) {
                    ((org.gradle.api.DefaultTask) task).setProject(project);
                }
                configuration.execute(task);
                tasks.put(name, task);
            } catch (Exception e) {
                throw new RuntimeException("Failed to register task: " + name, e);
            }
        }

        @Override
        public Task findByName(String name) {
            return tasks.get(name);
        }
    }
}

