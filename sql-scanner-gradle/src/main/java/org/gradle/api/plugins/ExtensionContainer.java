package org.gradle.api.plugins;

/**
 * Stub interface for Gradle ExtensionContainer API.
 */
public interface ExtensionContainer {
    <T> T create(String name, Class<T> type, Object... constructionArguments);
    Object findByName(String name);
}








