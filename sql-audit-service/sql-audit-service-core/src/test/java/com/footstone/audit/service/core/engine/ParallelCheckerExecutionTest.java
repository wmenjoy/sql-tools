package com.footstone.audit.service.core.engine;

import com.footstone.audit.service.core.config.AuditEngineConfig;
import com.footstone.audit.service.core.model.AuditProcessingResult;
import com.footstone.audit.service.core.model.CheckerResult;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.checker.AbstractAuditChecker;
import com.footstone.sqlguard.audit.model.AuditResult;
import com.footstone.sqlguard.audit.model.ExecutionResult;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ParallelCheckerExecutionTest {

    private DefaultAuditEngine engine;
    private ExecutorService executor;
    
    @Mock
    private AuditEngineConfig config;
    
    @BeforeEach
    void setUp() {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        when(config.getCheckerTimeoutMs()).thenReturn(200L);
        when(config.isCheckerEnabled(anyString())).thenReturn(true);
    }
    
    static class LatchAuditChecker extends AbstractAuditChecker {
        private final String id;
        private final CountDownLatch startLatch;
        private final CountDownLatch doneLatch;
        
        public LatchAuditChecker(String id, CountDownLatch startLatch, CountDownLatch doneLatch) {
            this.id = id;
            this.startLatch = startLatch;
            this.doneLatch = doneLatch;
        }
        
        @Override
        protected RiskScore performAudit(String sql, ExecutionResult result) {
            try {
                startLatch.await();
                doneLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }

        @Override
        public String getCheckerId() {
            return id;
        }
    }
    
    @Test
    void testParallelExecution_allCheckers_shouldRunConcurrently() throws InterruptedException {
        // Given
        int checkerCount = 5;
        CountDownLatch doneLatch = new CountDownLatch(checkerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<AbstractAuditChecker> checkers = new ArrayList<>();
        
        for (int i = 0; i < checkerCount; i++) {
            checkers.add(new LatchAuditChecker("c" + i, startLatch, doneLatch));
        }
        
        engine = new DefaultAuditEngine(checkers, config, executor);
        AuditEvent event = createEvent();
        
        // When
        new Thread(() -> {
            try {
                Thread.sleep(50); 
                startLatch.countDown();
            } catch (InterruptedException e) {}
        }).start();
        
        AuditProcessingResult result = engine.process(event);
        
        // Then
        assertTrue(result.success());
        assertEquals(checkerCount, result.report().checkerResults().size());
        assertEquals(0, doneLatch.getCount());
    }

    static class SlowAuditChecker extends AbstractAuditChecker {
        private final String id;
        private final long sleepMs;
        
        public SlowAuditChecker(String id, long sleepMs) {
            this.id = id;
            this.sleepMs = sleepMs;
        }
        
        @Override
        protected RiskScore performAudit(String sql, ExecutionResult result) {
            if (sleepMs > 0) {
                try {
                    System.out.println("Checker " + id + " sleeping for " + sleepMs);
                    Thread.sleep(sleepMs);
                    System.out.println("Checker " + id + " woke up");
                } catch (InterruptedException e) {
                    System.out.println("Checker " + id + " interrupted");
                    Thread.currentThread().interrupt();
                }
            }
            return null;
        }

        @Override
        public String getCheckerId() {
            return id;
        }
    }

    @Test
    void testParallelExecution_oneSlowChecker_shouldNotBlockOthers() {
        // Given
        AbstractAuditChecker fastChecker = new SlowAuditChecker("fast", 0);
        AbstractAuditChecker slowChecker = new SlowAuditChecker("slow", 500);

        engine = new DefaultAuditEngine(List.of(fastChecker, slowChecker), config, executor);
        AuditEvent event = createEvent();

        // When
        long start = System.currentTimeMillis();
        AuditProcessingResult result = engine.process(event);
        long duration = System.currentTimeMillis() - start;

        // Then
        List<CheckerResult> results = result.report().checkerResults();
        assertEquals(2, results.size());
        
        CheckerResult fastResult = results.stream().filter(r -> r.checkerId().equals("fast")).findFirst().orElseThrow();
        assertTrue(fastResult.isSuccess());
        
        CheckerResult slowResult = results.stream().filter(r -> r.checkerId().equals("slow")).findFirst().orElseThrow();
        assertFalse(slowResult.isSuccess());
        
        assertTrue(duration < 450, "Should timeout around 200ms, actual: " + duration);
    }

    private AuditEvent createEvent() {
        return AuditEvent.builder()
                .sql("SELECT 1")
                .sqlType(com.footstone.sqlguard.core.model.SqlCommandType.SELECT)
                .mapperId("test")
                .timestamp(Instant.now())
                .build();
    }
}
