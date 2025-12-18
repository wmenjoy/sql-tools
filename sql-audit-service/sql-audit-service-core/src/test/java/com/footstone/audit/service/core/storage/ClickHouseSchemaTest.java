package com.footstone.audit.service.core.storage;

import com.clickhouse.jdbc.ClickHouseConnection;
import com.clickhouse.jdbc.ClickHouseDataSource;
import com.footstone.audit.service.core.storage.clickhouse.ClickHouseExecutionLogger;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClickHouseSchemaTest {

    @Mock
    private ClickHouseDataSource dataSource;

    @Mock
    private ClickHouseConnection connection;

    @Mock
    private PreparedStatement preparedStatement;
    
    @Mock
    private ResultSet resultSet;

    private ClickHouseExecutionLogger logger;

    @BeforeEach
    void setUp() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        logger = new ClickHouseExecutionLogger(dataSource);
    }

    @Test
    void testInsert_singleRow_shouldSucceed() throws Exception {
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT 1")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("test")
                .timestamp(Instant.now())
                .build();

        logger.log(event);

        verify(connection).prepareStatement(contains("INSERT INTO sql_executions"));
        verify(preparedStatement).executeUpdate();
        
        // Verify parameters set (spot check)
        verify(preparedStatement).setString(eq(2), eq(event.getSqlId()));
        verify(preparedStatement).setString(eq(3), eq("SELECT 1"));
    }

    @Test
    void testInsert_batchRows_shouldSucceed() throws Exception {
        AuditEvent e1 = AuditEvent.builder()
                .sql("SELECT batch1")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("test")
                .timestamp(Instant.now())
                .build();
        
        AuditEvent e2 = AuditEvent.builder()
                .sql("SELECT batch2")
                .sqlType(SqlCommandType.SELECT)
                .mapperId("test")
                .timestamp(Instant.now())
                .build();

        logger.logBatch(List.of(e1, e2));

        verify(connection).prepareStatement(contains("INSERT INTO sql_executions"));
        verify(preparedStatement, times(2)).addBatch();
        verify(preparedStatement).executeBatch();
    }
}
