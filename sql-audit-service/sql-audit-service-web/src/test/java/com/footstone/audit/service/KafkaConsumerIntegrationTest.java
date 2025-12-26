package com.footstone.audit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.core.model.ExecutionLayer;
import com.footstone.sqlguard.core.model.SqlCommandType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer集成测试
 * Kafka Consumer Integration Test
 *
 * 用于验证Consumer能否正确接收和处理Kafka消息
 * Tests if Consumer can correctly receive and process Kafka messages
 */
@SpringBootTest
@ActiveProfiles("test")
public class KafkaConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试发送审计事件到Kafka
     * Test sending audit event to Kafka
     */
    @Test
    public void testSendAuditEvent() throws Exception {
        // 创建测试审计事件
        AuditEvent event = createTestAuditEvent();

        // 转换为JSON
        String eventJson = objectMapper.writeValueAsString(event);

        // 发送到Kafka
        System.out.println("=".repeat(80));
        System.out.println("发送测试消息到Kafka Topic: sql-audit-events");
        System.out.println("Sending test message to Kafka Topic: sql-audit-events");
        System.out.println("-".repeat(80));
        System.out.println("Event JSON:");
        System.out.println(eventJson);
        System.out.println("=".repeat(80));

        kafkaTemplate.send("sql-audit-events", event.getSqlId(), eventJson);

        System.out.println("消息已发送! 等待Consumer处理...");
        System.out.println("Message sent! Waiting for Consumer to process...");

        // 等待Consumer处理
        Thread.sleep(5000);

        System.out.println("测试完成! 请检查Consumer日志验证消息是否被处理");
        System.out.println("Test completed! Please check Consumer logs to verify message processing");
        System.out.println("=".repeat(80));
    }

    /**
     * 测试发送危险SQL（缺少WHERE子句）
     * Test sending dangerous SQL (missing WHERE clause)
     */
    @Test
    public void testSendDangerousSqlWithoutWhere() throws Exception {
        AuditEvent event = AuditEvent.builder()
                .sql("DELETE FROM users")  // 危险！没有WHERE子句
                .sqlType(SqlCommandType.DELETE)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.UserMapper.deleteAll")
                .timestamp(Instant.now())
                .executionTimeMs(10L)
                .rowsAffected(1000)
                .build();

        String eventJson = objectMapper.writeValueAsString(event);

        System.out.println("=".repeat(80));
        System.out.println("发送危险SQL测试消息 (无WHERE子句)");
        System.out.println("Sending dangerous SQL test message (no WHERE clause)");
        System.out.println("-".repeat(80));
        System.out.println(eventJson);
        System.out.println("=".repeat(80));

        kafkaTemplate.send("sql-audit-events", event.getSqlId(), eventJson);

        Thread.sleep(5000);

        System.out.println("危险SQL已发送! 应该触发NoWhereClauseChecker报警");
        System.out.println("Dangerous SQL sent! Should trigger NoWhereClauseChecker alert");
        System.out.println("=".repeat(80));
    }

    /**
     * 测试发送分页查询（无分页限制）
     * Test sending pagination query (no pagination limit)
     */
    @Test
    public void testSendQueryWithoutPagination() throws Exception {
        AuditEvent event = AuditEvent.builder()
                .sql("SELECT * FROM orders WHERE status = 'PENDING'")  // 无LIMIT
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.OrderMapper.selectPending")
                .timestamp(Instant.now())
                .executionTimeMs(200L)
                .rowsAffected(50000)  // 大量结果
                .build();

        String eventJson = objectMapper.writeValueAsString(event);

        System.out.println("=".repeat(80));
        System.out.println("发送无分页查询测试消息");
        System.out.println("Sending query without pagination test message");
        System.out.println("-".repeat(80));
        System.out.println(eventJson);
        System.out.println("=".repeat(80));

        kafkaTemplate.send("sql-audit-events", event.getSqlId(), eventJson);

        Thread.sleep(5000);

        System.out.println("无分页查询已发送! 应该触发NoPaginationChecker报警");
        System.out.println("Query without pagination sent! Should trigger NoPaginationChecker alert");
        System.out.println("=".repeat(80));
    }

    /**
     * 批量发送测试消息
     * Batch send test messages
     */
    @Test
    public void testBatchSendMessages() throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("批量发送10条测试消息");
        System.out.println("Batch sending 10 test messages");
        System.out.println("=".repeat(80));

        for (int i = 1; i <= 10; i++) {
            AuditEvent event = AuditEvent.builder()
                    .sql("SELECT * FROM users WHERE id = " + i)
                    .sqlType(SqlCommandType.SELECT)
                    .executionLayer(ExecutionLayer.MYBATIS)
                    .statementId("com.example.UserMapper.selectById")
                    .timestamp(Instant.now())
                    .executionTimeMs((long) (Math.random() * 100))
                    .rowsAffected(1)
                    .build();

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("sql-audit-events", event.getSqlId(), eventJson);

            System.out.println("已发送消息 " + i + "/10: " + event.getSqlId());

            // 间隔100ms
            Thread.sleep(100);
        }

        System.out.println("-".repeat(80));
        System.out.println("等待Consumer处理所有消息...");
        System.out.println("Waiting for Consumer to process all messages...");
        Thread.sleep(5000);

        System.out.println("批量测试完成!");
        System.out.println("Batch test completed!");
        System.out.println("=".repeat(80));
    }

    /**
     * 创建测试审计事件
     * Create test audit event
     */
    private AuditEvent createTestAuditEvent() {
        return AuditEvent.builder()
                .sql("SELECT id, username, email FROM users WHERE id = 1")
                .sqlType(SqlCommandType.SELECT)
                .executionLayer(ExecutionLayer.MYBATIS)
                .statementId("com.example.UserMapper.selectById")
                .datasource("primary")
                .timestamp(Instant.now())
                .executionTimeMs(50L)
                .rowsAffected(1)
                .build();
    }
}
