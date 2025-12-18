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
import com.footstone.sqlguard.core.model.RiskLevel;
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

@Service
public class DefaultAuditEngine implements AuditEventProcessor {

    private final List<AbstractAuditChecker> checkers;
    private final AuditEngineConfig config;
    private final ExecutorService executor;
    private final ResultAggregator aggregator;

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultAuditEngine(List<AbstractAuditChecker> checkers, AuditEngineConfig config, ExecutorService executor, ResultAggregator aggregator) {
        this.checkers = checkers;
        this.config = config;
        this.executor = executor;
        this.aggregator = aggregator;
    }

    public DefaultAuditEngine(List<AbstractAuditChecker> checkers, AuditEngineConfig config, ExecutorService executor) {
        this(checkers, config, executor, new ResultAggregator());
    }

    public DefaultAuditEngine(List<AbstractAuditChecker> checkers, AuditEngineConfig config) {
        this(checkers, config, Executors.newVirtualThreadPerTaskExecutor(), new ResultAggregator());
    }

    @Override
    public AuditProcessingResult process(AuditEvent event) {
        ExecutionResult executionResult = toExecutionResult(event);
        List<CheckerResult> results = executeCheckers(event.getSql(), executionResult);
        return aggregateResults(event, results);
    }

    private ExecutionResult toExecutionResult(AuditEvent event) {
        // Basic mapping, needs to be improved based on fields
        return ExecutionResult.builder()
                .rowsAffected(event.getRowsAffected())
                .executionTimeMs(event.getExecutionTimeMs())
                .executionTimestamp(event.getTimestamp())
                .errorMessage(event.getErrorMessage())
                .build();
    }

    private List<CheckerResult> executeCheckers(String sql, ExecutionResult executionResult) {
        List<CompletableFuture<CheckerResult>> futures = checkers.stream()
                .filter(checker -> config.isCheckerEnabled(checker.getCheckerId()))
                .map(checker -> CompletableFuture.supplyAsync(
                        () -> executeChecker(checker, sql, executionResult),
                        executor
                ).orTimeout(config.getCheckerTimeoutMs(), TimeUnit.MILLISECONDS)
                 .exceptionally(ex -> CheckerResult.failed(checker.getCheckerId(), ex)))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

    private CheckerResult executeChecker(AbstractAuditChecker checker, String sql, ExecutionResult executionResult) {
        try {
            AuditResult result = checker.check(sql, executionResult);
            RiskScore maxRisk = result.getRisks().stream()
                    .max(Comparator.comparing(RiskScore::getSeverity))
                    .orElse(null);
            
            return CheckerResult.success(checker.getCheckerId(), maxRisk);
        } catch (Exception e) {
            return CheckerResult.failed(checker.getCheckerId(), e);
        }
    }

    private AuditProcessingResult aggregateResults(AuditEvent event, List<CheckerResult> results) {
        RiskScore aggregatedRisk = aggregator.aggregate(results);
        
        AuditReport report = new AuditReport(
                UUID.randomUUID().toString(),
                event.getSqlId(),
                event,
                results,
                aggregatedRisk,
                Instant.now()
        );
        
        return new AuditProcessingResult(true, report, null);
    }
}
