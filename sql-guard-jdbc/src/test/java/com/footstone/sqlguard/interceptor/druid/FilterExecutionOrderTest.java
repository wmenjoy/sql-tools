package com.footstone.sqlguard.interceptor.druid;

import com.alibaba.druid.filter.FilterAdapter;
import com.alibaba.druid.filter.FilterChain;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.proxy.jdbc.StatementProxy;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the execution order of filter finally blocks.
 * This is CRITICAL for understanding ThreadLocal cleanup timing.
 */
class FilterExecutionOrderTest {

    private static final List<String> executionLog = new CopyOnWriteArrayList<>();
    private static final ThreadLocal<String> testThreadLocal = new ThreadLocal<>();

    static class FirstFilter extends FilterAdapter {
        @Override
        public com.alibaba.druid.proxy.jdbc.ResultSetProxy statement_executeQuery(
                FilterChain chain, StatementProxy statement, String sql) throws SQLException {
            executionLog.add("FirstFilter: try start");
            testThreadLocal.set("DATA_FROM_FIRST_FILTER");
            try {
                executionLog.add("FirstFilter: calling super");
                return super.statement_executeQuery(chain, statement, sql);
            } finally {
                executionLog.add("FirstFilter: finally start");
                testThreadLocal.remove();
                executionLog.add("FirstFilter: finally end (ThreadLocal cleared)");
            }
        }
    }

    static class SecondFilter extends FilterAdapter {
        @Override
        public com.alibaba.druid.proxy.jdbc.ResultSetProxy statement_executeQuery(
                FilterChain chain, StatementProxy statement, String sql) throws SQLException {
            executionLog.add("SecondFilter: try start");
            try {
                executionLog.add("SecondFilter: calling super");
                return super.statement_executeQuery(chain, statement, sql);
            } finally {
                executionLog.add("SecondFilter: finally start");
                String data = testThreadLocal.get();
                executionLog.add("SecondFilter: ThreadLocal value = " + data);
                executionLog.add("SecondFilter: finally end");
            }
        }
    }

    @Test
    void testFilterExecutionOrder() throws Exception {
        // Setup
        executionLog.clear();
        testThreadLocal.remove();

        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setUrl("jdbc:h2:mem:ordertest_" + System.nanoTime());
        dataSource.setDriverClassName("org.h2.Driver");

        // Register filters in order: First, Second
        List<com.alibaba.druid.filter.Filter> filters = new ArrayList<>();
        filters.add(new FirstFilter());
        filters.add(new SecondFilter());
        dataSource.setProxyFilters(filters);

        dataSource.init();

        // Create test table
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE test (id INT)");
        }

        executionLog.clear();

        // Execute query
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT * FROM test");
        }

        // Print execution log
        System.out.println("\n=== Filter Execution Order ===");
        for (int i = 0; i < executionLog.size(); i++) {
            System.out.println(i + ": " + executionLog.get(i));
        }
        System.out.println("==============================\n");

        // Verify order
        assertTrue(executionLog.size() >= 8, "Should have all log entries");

        // Critical assertion: SecondFilter's finally should execute BEFORE FirstFilter's finally
        int secondFinally = -1;
        int firstFinally = -1;
        int threadLocalRead = -1;

        for (int i = 0; i < executionLog.size(); i++) {
            if (executionLog.get(i).contains("SecondFilter: ThreadLocal value")) {
                threadLocalRead = i;
            }
            if (executionLog.get(i).contains("SecondFilter: finally end")) {
                secondFinally = i;
            }
            if (executionLog.get(i).contains("FirstFilter: finally end")) {
                firstFinally = i;
            }
        }

        // Verify SecondFilter reads ThreadLocal before FirstFilter clears it
        assertTrue(threadLocalRead > 0, "SecondFilter should read ThreadLocal");
        assertTrue(threadLocalRead < firstFinally, 
            "SecondFilter should read ThreadLocal BEFORE FirstFilter clears it");
        assertTrue(secondFinally < firstFinally,
            "SecondFilter finally should execute BEFORE FirstFilter finally");

        // Verify the ThreadLocal value was successfully read
        String logWithValue = executionLog.stream()
            .filter(log -> log.contains("ThreadLocal value"))
            .findFirst()
            .orElse("");
        assertTrue(logWithValue.contains("DATA_FROM_FIRST_FILTER"),
            "SecondFilter should successfully read the ThreadLocal value");

        dataSource.close();
    }
}
