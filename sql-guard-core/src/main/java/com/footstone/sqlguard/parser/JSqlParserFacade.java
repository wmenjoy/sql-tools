package com.footstone.sqlguard.parser;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.update.Update;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Facade for JSqlParser 4.x providing unified SQL parsing interface.
 * Supports fail-fast and lenient parsing modes with descriptive error handling.
 * Includes LRU cache for parsed statements to optimize repeated SQL validation.
 */
public class JSqlParserFacade {

    private static final Logger logger = LoggerFactory.getLogger(JSqlParserFacade.class);
    private static final int SQL_SNIPPET_MAX_LENGTH = 100;
    private static final int DEFAULT_CACHE_SIZE = 1000;

    private final boolean lenientMode;
    private final Map<String, Statement> cache;
    private final int cacheSize;
    private long hitCount = 0;
    private long missCount = 0;

    /**
     * Constructs a JSqlParserFacade with specified lenient mode and cache size.
     *
     * @param lenientMode if true, parsing errors are logged and null is returned;
     *                    if false (default), parsing errors throw SqlParseException
     * @param cacheSize   maximum number of parsed statements to cache (LRU eviction)
     */
    public JSqlParserFacade(boolean lenientMode, int cacheSize) {
        this.lenientMode = lenientMode;
        this.cacheSize = cacheSize;
        this.cache = new LinkedHashMap<String, Statement>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Statement> eldest) {
                return size() > JSqlParserFacade.this.cacheSize;
            }
        };
    }

    /**
     * Constructs a JSqlParserFacade with specified lenient mode and default cache size.
     *
     * @param lenientMode if true, parsing errors are logged and null is returned;
     *                    if false (default), parsing errors throw SqlParseException
     */
    public JSqlParserFacade(boolean lenientMode) {
        this(lenientMode, DEFAULT_CACHE_SIZE);
    }

    /**
     * Constructs a JSqlParserFacade in fail-fast mode with default cache size.
     */
    public JSqlParserFacade() {
        this(false, DEFAULT_CACHE_SIZE);
    }

    /**
     * Parses the given SQL string into a Statement object.
     *
     * @param sql the SQL string to parse
     * @return the parsed Statement object, or null in lenient mode if parsing fails
     * @throws IllegalArgumentException if sql is null
     * @throws SqlParseException        if sql is invalid and lenientMode is false
     */
    public Statement parse(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL cannot be null");
        }

        String trimmedSql = sql.trim();
        if (trimmedSql.isEmpty()) {
            if (lenientMode) {
                logger.warn("Empty or whitespace-only SQL provided");
                return null;
            } else {
                throw new SqlParseException("SQL cannot be empty or whitespace-only");
            }
        }

        try {
            return CCJSqlParserUtil.parse(sql);
        } catch (JSQLParserException e) {
            String sqlSnippet = getSqlSnippet(sql);
            String errorMessage = String.format(
                    "Failed to parse SQL: %s - Reason: %s",
                    sqlSnippet,
                    e.getMessage()
            );

            if (lenientMode) {
                logger.warn(errorMessage, e);
                return null;
            } else {
                throw new SqlParseException(errorMessage, e);
            }
        }
    }

    /**
     * Returns whether this facade is in lenient mode.
     *
     * @return true if lenient mode is enabled, false otherwise
     */
    public boolean isLenientMode() {
        return lenientMode;
    }

    /**
     * Extracts the WHERE clause from a SQL statement.
     *
     * @param statement the parsed SQL statement
     * @return the WHERE expression, or null if no WHERE clause or unsupported statement type
     */
    public Expression extractWhere(Statement statement) {
        if (statement == null) {
            return null;
        }

        if (statement instanceof Select) {
            Select select = (Select) statement;
            if (select.getSelectBody() instanceof PlainSelect) {
                PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
                return plainSelect.getWhere();
            }
        } else if (statement instanceof Update) {
            Update update = (Update) statement;
            return update.getWhere();
        } else if (statement instanceof Delete) {
            Delete delete = (Delete) statement;
            return delete.getWhere();
        }

        return null;
    }

    /**
     * Extracts the primary table name from a SQL statement.
     * For JOIN queries, returns the first (primary) table.
     * Removes database-specific delimiters (backticks, square brackets).
     *
     * @param statement the parsed SQL statement
     * @return the table name, or null if no table found or unsupported statement type
     */
    public String extractTableName(Statement statement) {
        if (statement == null) {
            return null;
        }

        try {
            String tableName = null;
            
            if (statement instanceof Select) {
                Select select = (Select) statement;
                if (select.getSelectBody() instanceof PlainSelect) {
                    PlainSelect plainSelect = (PlainSelect) select.getSelectBody();
                    if (plainSelect.getFromItem() instanceof Table) {
                        Table table = (Table) plainSelect.getFromItem();
                        tableName = table.getName();
                    }
                }
            } else if (statement instanceof Update) {
                Update update = (Update) statement;
                Table table = update.getTable();
                tableName = table != null ? table.getName() : null;
            } else if (statement instanceof Delete) {
                Delete delete = (Delete) statement;
                Table table = delete.getTable();
                tableName = table != null ? table.getName() : null;
            }
            
            // Remove database-specific delimiters
            if (tableName != null) {
                tableName = removeDelimiters(tableName);
            }
            
            return tableName;
        } catch (Exception e) {
            logger.warn("Failed to extract table name from statement", e);
        }

        return null;
    }

    /**
     * Removes database-specific delimiters from identifiers.
     * Handles backticks (MySQL), square brackets (SQL Server), and double quotes.
     *
     * @param identifier the identifier to clean
     * @return identifier without delimiters
     */
    private String removeDelimiters(String identifier) {
        if (identifier == null) {
            return null;
        }
        
        // Remove backticks (MySQL)
        if (identifier.startsWith("`") && identifier.endsWith("`")) {
            return identifier.substring(1, identifier.length() - 1);
        }
        
        // Remove square brackets (SQL Server)
        if (identifier.startsWith("[") && identifier.endsWith("]")) {
            return identifier.substring(1, identifier.length() - 1);
        }
        
        // Remove double quotes (standard SQL)
        if (identifier.startsWith("\"") && identifier.endsWith("\"")) {
            return identifier.substring(1, identifier.length() - 1);
        }
        
        return identifier;
    }

    /**
     * Extracts all field (column) names from a WHERE expression.
     * Uses FieldExtractorVisitor to traverse the expression tree.
     *
     * @param expression the WHERE expression to analyze
     * @return set of field names (without table prefixes), empty set if expression is null
     */
    public Set<String> extractFields(Expression expression) {
        if (expression == null) {
            return Collections.emptySet();
        }

        try {
            FieldExtractorVisitor visitor = new FieldExtractorVisitor();
            expression.accept(visitor);
            return visitor.getFieldNames();
        } catch (Exception e) {
            logger.warn("Failed to extract fields from expression", e);
            return new HashSet<>();
        }
    }

    /**
     * Parses SQL with caching support. Normalizes SQL (trim + lowercase) for cache key.
     * Cache hits return the cached Statement; misses call parse() and cache the result.
     *
     * @param sql the SQL string to parse
     * @return the parsed Statement object, or null in lenient mode if parsing fails
     * @throws IllegalArgumentException if sql is null
     * @throws SqlParseException        if sql is invalid and lenientMode is false
     */
    public Statement parseCached(String sql) {
        if (sql == null) {
            throw new IllegalArgumentException("SQL cannot be null");
        }

        // Normalize SQL for cache key (trim and lowercase)
        String normalizedSql = sql.trim().toLowerCase();
        
        synchronized (cache) {
            Statement cached = cache.get(normalizedSql);
            if (cached != null) {
                hitCount++;
                return cached;
            }

            // Cache miss - parse and cache
            missCount++;
            Statement statement = parse(sql);
            if (statement != null) {
                cache.put(normalizedSql, statement);
            }
            return statement;
        }
    }

    /**
     * Gets cache statistics including hit count, miss count, size, and hit rate.
     *
     * @return CacheStats object with current statistics
     */
    public CacheStats getCacheStatistics() {
        synchronized (cache) {
            return new CacheStats(hitCount, missCount, cache.size());
        }
    }

    /**
     * Clears the cache and resets statistics.
     */
    public void clearCache() {
        synchronized (cache) {
            cache.clear();
            hitCount = 0;
            missCount = 0;
        }
    }

    /**
     * Extracts a snippet of SQL for error messages (first 100 characters).
     *
     * @param sql the full SQL string
     * @return a truncated SQL snippet with ellipsis if needed
     */
    private String getSqlSnippet(String sql) {
        if (sql.length() <= SQL_SNIPPET_MAX_LENGTH) {
            return sql;
        }
        return sql.substring(0, SQL_SNIPPET_MAX_LENGTH) + "...";
    }
}
