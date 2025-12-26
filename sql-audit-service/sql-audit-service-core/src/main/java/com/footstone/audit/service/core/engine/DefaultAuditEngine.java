package com.footstone.audit.service.core.engine;

import com.footstone.audit.service.core.config.AuditEngineConfig;
import com.footstone.audit.service.core.model.AuditProcessingResult;
import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.audit.service.core.model.CheckerResult;
import com.footstone.audit.service.core.processor.AuditEventProcessor;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.checker.AbstractAuditChecker;
import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 默认审计引擎 - SQL审计服务的核心执行引擎
 * Default Audit Engine - Core execution engine of SQL Audit Service
 *
 * <p>架构职责 (Architectural Responsibilities):</p>
 * <ul>
 *   <li>实现AuditEventProcessor接口，提供完整的审计处理能力
 *       (Implements AuditEventProcessor interface, provides complete audit processing)</li>
 *   <li>管理所有审计检查器的生命周期和执行
 *       (Manages lifecycle and execution of all audit checkers)</li>
 *   <li>使用虚拟线程池并发执行检查器，最大化吞吐量
 *       (Uses virtual thread pool for concurrent checker execution, maximizing throughput)</li>
 *   <li>聚合风险评分，生成统一的审计报告
 *       (Aggregates risk scores, generates unified audit report)</li>
 *   <li>集成超时控制和异常隔离机制
 *       (Integrates timeout control and exception isolation)</li>
 * </ul>
 *
 * <p>执行流程 (Execution Flow):</p>
 * <pre>
 * 1. process(AuditEvent)
 *    ↓
 * 2. toExecutionResult() - 转换事件为执行结果
 *    ↓
 * 3. executeCheckers() - 并发执行所有检查器
 *    ├─ NoWhereClauseChecker     [Virtual Thread 1]
 *    ├─ BlacklistFieldChecker    [Virtual Thread 2]
 *    ├─ NoPaginationChecker      [Virtual Thread 3]
 *    └─ ...                      [Virtual Thread N]
 *    ↓
 * 4. aggregateResults() - 聚合风险评分
 *    ↓
 * 5. 生成AuditReport并返回
 * </pre>
 *
 * <p>虚拟线程优势 (Virtual Thread Benefits):</p>
 * <ul>
 *   <li>轻量级: 每个检查器独立虚拟线程，无平台线程消耗
 *       (Lightweight: Each checker in independent virtual thread, no platform thread consumption)</li>
 *   <li>高并发: 支持数千检查器并发执行，吞吐量不受线程池限制
 *       (High concurrency: Supports thousands of concurrent checkers, throughput not limited by thread pool)</li>
 *   <li>简化编程: 同步风格代码，无需复杂的异步编程模型
 *       (Simplified programming: Synchronous style code, no complex async programming model)</li>
 * </ul>
 *
 * <p>超时和异常处理 (Timeout and Exception Handling):</p>
 * <ul>
 *   <li>每个检查器有独立超时(默认5秒)，超时自动标记为失败
 *       (Each checker has independent timeout (default 5s), auto-marked as failed on timeout)</li>
 *   <li>单个检查器异常不影响其他检查器执行
 *       (Single checker exception doesn't affect other checkers)</li>
 *   <li>所有检查器执行完毕后统一聚合结果
 *       (Results aggregated after all checkers complete)</li>
 * </ul>
 *
 * <p>配置项 (Configuration):</p>
 * <ul>
 *   <li>audit.engine.checker-timeout-ms: 检查器超时时间(默认5000ms)
 *       (Checker timeout in milliseconds, default 5000ms)</li>
 *   <li>audit.engine.enabled-checkers: 启用的检查器ID列表
 *       (List of enabled checker IDs)</li>
 * </ul>
 *
 * @see AuditEventProcessor 实现的接口 (Implemented interface)
 * @see AbstractAuditChecker 审计检查器基类 (Audit checker base class)
 * @see AuditEngineConfig 引擎配置 (Engine configuration)
 * @see ResultAggregator 结果聚合器 (Result aggregator)
 * @since 1.0.0
 */
@Service
public class DefaultAuditEngine implements AuditEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultAuditEngine.class);

    /**
     * 所有可用的审计检查器列表 - Spring自动注入
     * All available audit checkers - Auto-injected by Spring
     *
     * 通过类型自动扫描并注入所有AbstractAuditChecker的子类实例。
     * 包括: NoWhereClauseChecker, BlacklistFieldChecker, NoPaginationChecker等。
     * Auto-scans and injects all AbstractAuditChecker subclass instances.
     * Includes: NoWhereClauseChecker, BlacklistFieldChecker, NoPaginationChecker, etc.
     */
    private final List<AbstractAuditChecker> checkers;

    /**
     * 审计引擎配置 - 控制检查器启用状态、超时时间等
     * Audit engine configuration - Controls checker enablement, timeout, etc.
     */
    private final AuditEngineConfig config;

    /**
     * 虚拟线程执行器 - 用于并发执行检查器
     * Virtual thread executor - For concurrent checker execution
     *
     * 使用JDK 21+的虚拟线程特性，每个任务创建独立虚拟线程。
     * Uses JDK 21+ virtual thread feature, creates independent virtual thread for each task.
     */
    private final ExecutorService executor;

    /**
     * 结果聚合器 - 聚合所有检查器的风险评分
     * Result aggregator - Aggregates risk scores from all checkers
     */
    private final ResultAggregator aggregator;

    /**
     * 主构造函数 - Spring自动注入所有依赖
     * Primary constructor - Spring auto-injects all dependencies
     *
     * @param checkers 所有审计检查器 (All audit checkers)
     * @param config 引擎配置 (Engine configuration)
     * @param executor 线程执行器 (Thread executor)
     * @param aggregator 结果聚合器 (Result aggregator)
     */
    @org.springframework.beans.factory.annotation.Autowired
    public DefaultAuditEngine(List<AbstractAuditChecker> checkers, AuditEngineConfig config, ExecutorService executor, ResultAggregator aggregator) {
        this.checkers = checkers;
        this.config = config;
        this.executor = executor;
        this.aggregator = aggregator;
    }

    /**
     * 构造函数 - 使用默认ResultAggregator
     * Constructor - Uses default ResultAggregator
     */
    public DefaultAuditEngine(List<AbstractAuditChecker> checkers, AuditEngineConfig config, ExecutorService executor) {
        this(checkers, config, executor, new ResultAggregator());
    }

    /**
     * 构造函数 - 使用默认虚拟线程执行器
     * Constructor - Uses default virtual thread executor
     */
    public DefaultAuditEngine(List<AbstractAuditChecker> checkers, AuditEngineConfig config) {
        this(checkers, config, Executors.newVirtualThreadPerTaskExecutor(), new ResultAggregator());
    }

    /**
     * 处理审计事件 - 核心方法
     * Process audit event - Core method
     *
     * 实现AuditEventProcessor接口，完整执行审计流程。
     * Implements AuditEventProcessor interface, fully executes audit process.
     *
     * @param event SQL审计事件 (SQL audit event)
     * @return 审计处理结果 (Audit processing result)
     */
    @Override
    public AuditProcessingResult process(AuditEvent event) {
        // Null safety check for AuditEvent
        if (event == null) {
            logger.error("AuditEvent is null, cannot process");
            return new AuditProcessingResult(false, null, "AuditEvent is null");
        }

        // 转换审计事件为执行结果 (Convert audit event to execution result)
        ExecutionResult executionResult = toExecutionResult(event);

        // Null safety check for ExecutionResult
        if (executionResult == null) {
            logger.warn("ExecutionResult is null, using default values");
            executionResult = ExecutionResult.builder()
                .rowsAffected(-1)
                .executionTimeMs(0L)
                .executionTimestamp(Instant.now())
                .errorMessage("Failed to build ExecutionResult")
                .build();
        }

        // 并发执行所有检查器 (Concurrently execute all checkers)
        List<CheckerResult> results = executeCheckers(event.getSql(), executionResult);

        // 聚合结果并生成报告 (Aggregate results and generate report)
        return aggregateResults(event, results);
    }

    /**
     * 转换AuditEvent为ExecutionResult
     * Convert AuditEvent to ExecutionResult
     *
     * 提取AuditEvent中的执行相关信息，构造ExecutionResult供检查器使用。
     * Extracts execution-related info from AuditEvent, constructs ExecutionResult for checkers.
     *
     * @param event 审计事件 (Audit event)
     * @return 执行结果 (Execution result), never null
     */
    private ExecutionResult toExecutionResult(AuditEvent event) {
        try {
            // Extract values - primitives cannot be null
            int rowsAffected = event.getRowsAffected();  // int primitive, default -1 in AuditEvent
            long executionTimeMs = event.getExecutionTimeMs();  // long primitive, default 0

            Instant timestamp = (event.getTimestamp() != null)
                ? event.getTimestamp()
                : Instant.now();  // fallback to current time if null

            return ExecutionResult.builder()
                    .rowsAffected(rowsAffected)
                    .executionTimeMs(executionTimeMs)
                    .executionTimestamp(timestamp)
                    .errorMessage(event.getErrorMessage())  // errorMessage can be null
                    .build();
        } catch (Exception e) {
            logger.warn("Failed to convert AuditEvent to ExecutionResult: {}", e.getMessage());
            // Return safe default values
            return ExecutionResult.builder()
                    .rowsAffected(-1)
                    .executionTimeMs(0L)
                    .executionTimestamp(Instant.now())
                    .errorMessage("Failed to extract execution result")
                    .build();
        }
    }

    /**
     * 并发执行所有启用的检查器
     * Concurrently execute all enabled checkers
     *
     * <p>执行机制 (Execution Mechanism):</p>
     * <ol>
     *   <li>过滤出所有启用的检查器 (Filter enabled checkers)</li>
     *   <li>为每个检查器创建CompletableFuture任务 (Create CompletableFuture for each)</li>
     *   <li>在虚拟线程池中并发执行 (Execute concurrently in virtual thread pool)</li>
     *   <li>设置超时控制(默认5秒) (Set timeout control, default 5s)</li>
     *   <li>等待所有任务完成 (Wait for all tasks to complete)</li>
     *   <li>收集所有结果(包括成功和失败) (Collect all results including success and failure)</li>
     * </ol>
     *
     * @param sql SQL语句 (SQL statement)
     * @param executionResult 执行结果 (Execution result)
     * @return 所有检查器的结果列表 (Results from all checkers)
     */
    private List<CheckerResult> executeCheckers(String sql, ExecutionResult executionResult) {
        // 创建所有检查器的异步任务 (Create async tasks for all checkers)
        List<CompletableFuture<CheckerResult>> futures = checkers.stream()
                .filter(checker -> config.isCheckerEnabled(checker.getCheckerId()))
                .map(checker -> CompletableFuture.supplyAsync(
                        () -> executeChecker(checker, sql, executionResult),
                        executor
                ).orTimeout(config.getCheckerTimeoutMs(), TimeUnit.MILLISECONDS)
                 .exceptionally(ex -> CheckerResult.failed(checker.getCheckerId(), ex)))
                .toList();

        // 等待所有任务完成 (Wait for all tasks to complete)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 收集所有结果 (Collect all results)
        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    /**
     * 执行单个检查器
     * Execute single checker
     *
     * 捕获所有异常，确保单个检查器失败不影响整体流程。
     * Catches all exceptions, ensures single checker failure doesn't affect overall process.
     *
     * @param checker 审计检查器 (Audit checker)
     * @param sql SQL语句 (SQL statement)
     * @param executionResult 执行结果 (Execution result)
     * @return 检查器结果(成功或失败) (Checker result - success or failure)
     */
    private CheckerResult executeChecker(AbstractAuditChecker checker, String sql, ExecutionResult executionResult) {
        try {
            // 执行检查器 (Execute checker)
            AuditResult result = checker.check(sql, executionResult);

            // 提取最高风险评分 (Extract highest risk score)
            RiskScore maxRisk = result.getRisks().stream()
                    .max(Comparator.comparing(RiskScore::getSeverity))
                    .orElse(null);

            return CheckerResult.success(checker.getCheckerId(), maxRisk);
        } catch (Exception e) {
            // 检查器执行失败，返回失败结果 (Checker execution failed, return failure result)
            return CheckerResult.failed(checker.getCheckerId(), e);
        }
    }

    /**
     * 聚合所有检查器结果，生成审计报告
     * Aggregate all checker results, generate audit report
     *
     * <p>聚合策略 (Aggregation Strategy):</p>
     * <ul>
     *   <li>使用ResultAggregator聚合所有风险评分 (Use ResultAggregator to aggregate all risk scores)</li>
     *   <li>生成唯一的报告ID (Generate unique report ID)</li>
     *   <li>记录所有检查器结果(包括失败的) (Record all checker results including failures)</li>
     *   <li>记录生成时间戳 (Record generation timestamp)</li>
     * </ul>
     *
     * @param event 原始审计事件 (Original audit event)
     * @param results 所有检查器结果 (All checker results)
     * @return 审计处理结果 (Audit processing result)
     */
    private AuditProcessingResult aggregateResults(AuditEvent event, List<CheckerResult> results) {
        // 聚合风险评分 (Aggregate risk scores)
        RiskScore aggregatedRisk = aggregator.aggregate(results);

        // 生成审计报告 (Generate audit report)
        AuditReport report = new AuditReport(
                UUID.randomUUID().toString(),  // 唯一报告ID (Unique report ID)
                event.getSqlId(),              // SQL标识 (SQL identifier)
                event,                          // 原始事件 (Original event)
                results,                        // 所有检查器结果 (All checker results)
                aggregatedRisk,                 // 聚合风险 (Aggregated risk)
                Instant.now()                   // 生成时间 (Generation time)
        );

        return new AuditProcessingResult(true, report, null);
    }
}
