package com.footstone.audit.service.core.processor;

import com.footstone.audit.service.core.model.AuditProcessingResult;
import com.footstone.sqlguard.audit.AuditEvent;

/**
 * 审计事件处理器接口 - SQL审计服务的核心处理接口
 * Audit Event Processor Interface - Core processing interface of SQL Audit Service
 *
 * <p>职责 (Responsibilities):</p>
 * <ul>
 *   <li>接收来自Consumer的SQL审计事件 (Receives SQL audit events from Consumer)</li>
 *   <li>执行所有已启用的审计检查器 (Executes all enabled audit checkers)</li>
 *   <li>聚合风险评分 (Aggregates risk scores)</li>
 *   <li>生成审计报告 (Generates audit report)</li>
 *   <li>持久化到存储层 (Persists to storage layer)</li>
 * </ul>
 *
 * <p>处理流程 (Processing Flow):</p>
 * <pre>
 * AuditEvent → process() → 并发执行Checkers → 聚合结果 → 生成报告 → 持久化 → 返回结果
 * AuditEvent → process() → Concurrent Checkers → Aggregate → Generate Report → Persist → Return Result
 * </pre>
 *
 * <p>实现类 (Implementation):</p>
 * <ul>
 *   <li>{@link com.footstone.audit.service.core.engine.DefaultAuditEngine} - 默认实现，使用虚拟线程并发执行检查器
 *       (Default implementation using virtual threads for concurrent checker execution)</li>
 * </ul>
 *
 * <p>调用者 (Callers):</p>
 * <ul>
 *   <li>{@code KafkaAuditEventConsumer} - Kafka消费者模块，接收来自Kafka的审计事件
 *       (Kafka consumer module, receives audit events from Kafka)</li>
 * </ul>
 *
 * @see com.footstone.audit.service.core.engine.DefaultAuditEngine 默认实现 (Default implementation)
 * @see AuditEvent 输入参数 - SQL审计事件 (Input parameter - SQL audit event)
 * @see AuditProcessingResult 返回值 - 审计处理结果 (Return value - Audit processing result)
 * @since 1.0.0
 */
public interface AuditEventProcessor {

    /**
     * 处理审计事件 - 核心方法
     * Process audit event - Core method
     *
     * <p>执行步骤 (Execution Steps):</p>
     * <ol>
     *   <li>转换AuditEvent为ExecutionResult (Convert AuditEvent to ExecutionResult)</li>
     *   <li>并发执行所有启用的检查器 (Concurrently execute all enabled checkers)</li>
     *   <li>聚合所有检查器的风险评分 (Aggregate risk scores from all checkers)</li>
     *   <li>生成审计报告 (Generate audit report)</li>
     *   <li>持久化报告到存储层 (Persist report to storage layer)</li>
     *   <li>返回处理结果 (Return processing result)</li>
     * </ol>
     *
     * <p>性能特性 (Performance Characteristics):</p>
     * <ul>
     *   <li>并发执行: 所有检查器在虚拟线程中并发执行，耗时≈最慢的检查器
     *       (Concurrent execution: All checkers run concurrently in virtual threads, time≈slowest checker)</li>
     *   <li>超时控制: 每个检查器有独立超时(默认5秒)，超时自动失败
     *       (Timeout control: Each checker has independent timeout (default 5s), auto-fails on timeout)</li>
     *   <li>异常隔离: 单个检查器失败不影响其他检查器
     *       (Exception isolation: Single checker failure doesn't affect others)</li>
     * </ul>
     *
     * @param event SQL审计事件，包含SQL语句、执行时间、影响行数等信息
     *              (SQL audit event containing SQL statement, execution time, affected rows, etc.)
     * @return 审计处理结果，包含成功标志、审计报告、错误信息
     *         (Audit processing result containing success flag, audit report, error message)
     */
    AuditProcessingResult process(AuditEvent event);
}
