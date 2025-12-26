package com.footstone.sqlguard.parser;

import net.sf.jsqlparser.statement.Statement;

import java.util.HashMap;
import java.util.Map;

/**
 * ThreadLocal-based cache for sharing parsed SQL Statement instances across
 * the InnerInterceptor chain within a single request.
 *
 * <h2>Purpose</h2>
 * <p>Avoids redundant SQL parsing by caching the Statement instance parsed once
 * by {@code SqlGuardInterceptor} and reusing it in all downstream InnerInterceptors
 * (e.g., {@code SqlGuardCheckInnerInterceptor}, {@code SelectLimitInnerInterceptor}).
 *
 * <h2>Thread Safety</h2>
 * <p>Uses {@link ThreadLocal} to ensure thread isolation. Each thread has its own
 * independent cache, preventing cross-request interference.
 *
 * <h2>Memory Leak Prevention</h2>
 * <p><b>CRITICAL:</b> Must call {@link #clear()} in a {@code finally} block after
 * request processing completes. Failure to clear ThreadLocal can cause memory leaks
 * in thread pool environments (e.g., Tomcat, Jetty).
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // In SqlGuardInterceptor (main interceptor)
 * try {
 *     Statement stmt = JSqlParserFacade.parse(sql);
 *     StatementContext.cache(sql, stmt);  // Cache for downstream interceptors
 *
 *     // Invoke InnerInterceptor chain...
 *     for (InnerInterceptor interceptor : interceptors) {
 *         interceptor.willDoQuery(...);
 *     }
 * } finally {
 *     StatementContext.clear();  // CRITICAL: Cleanup to prevent memory leak
 * }
 *
 * // In downstream InnerInterceptor (e.g., SqlGuardCheckInnerInterceptor)
 * public boolean willDoQuery(...) {
 *     Statement stmt = StatementContext.get(sql);  // Reuse cached Statement
 *     if (stmt == null) {
 *         // Cache miss - parse and cache
 *         stmt = JSqlParserFacade.parse(sql);
 *         StatementContext.cache(sql, stmt);
 *     }
 *     // Use stmt...
 * }
 * }</pre>
 *
 * @since 1.1.0
 */
public final class StatementContext {

    /**
     * ThreadLocal cache storing SQL → Statement mappings for the current thread.
     *
     * <p>Each thread has its own independent HashMap, ensuring thread safety and
     * preventing cross-request interference.
     */
    private static final ThreadLocal<Map<String, Statement>> CACHE =
        ThreadLocal.withInitial(HashMap::new);

    /**
     * Private constructor to prevent instantiation.
     * This is a utility class with only static methods.
     */
    private StatementContext() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Caches a parsed Statement for the given SQL string.
     *
     * <p>Stores the Statement in the current thread's ThreadLocal cache,
     * keyed by the SQL string. Downstream InnerInterceptors can retrieve
     * this Statement using {@link #get(String)} to avoid re-parsing.
     *
     * @param sql       SQL string (used as cache key)
     * @param statement Parsed Statement instance
     * @throws NullPointerException if sql or statement is null
     */
    public static void cache(String sql, Statement statement) {
        if (sql == null) {
            throw new NullPointerException("SQL cannot be null");
        }
        if (statement == null) {
            throw new NullPointerException("Statement cannot be null");
        }
        CACHE.get().put(sql, statement);
    }

    /**
     * Retrieves a cached Statement for the given SQL string.
     *
     * <p>Looks up the Statement in the current thread's ThreadLocal cache.
     * Returns {@code null} if no Statement is cached for this SQL.
     *
     * @param sql SQL string (cache key)
     * @return Cached Statement instance, or {@code null} if not found
     * @throws NullPointerException if sql is null
     */
    public static Statement get(String sql) {
        if (sql == null) {
            throw new NullPointerException("SQL cannot be null");
        }
        return CACHE.get().get(sql);
    }

    /**
     * Clears the ThreadLocal cache for the current thread.
     *
     * <p><b>CRITICAL:</b> Must be called in a {@code finally} block after request
     * processing completes to prevent memory leaks. Failure to clear ThreadLocal
     * in thread pool environments will cause memory to accumulate indefinitely.
     *
     * <p>Removes the entire ThreadLocal value, releasing all cached Statements
     * and allowing garbage collection.
     */
    public static void clear() {
        CACHE.remove();
    }

    /**
     * Returns the number of cached Statements for the current thread.
     *
     * <p>This method is primarily for testing and debugging purposes.
     *
     * @return Number of cached SQL → Statement mappings
     */
    static int size() {
        return CACHE.get().size();
    }
}







