package com.footstone.audit.service.core.job;

import com.footstone.audit.service.core.storage.repository.ExecutionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "audit.storage.retention.enabled", havingValue = "true", matchIfMissing = true)
public class RetentionJob {

    private final ExecutionLogRepository repository;

    @Value("${audit.storage.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "${audit.storage.retention.cron:0 0 2 * * *}") // Run at 2 AM daily
    public void cleanupOldData() {
        log.info("Starting data retention cleanup job. Retention days: {}", retentionDays);
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            repository.deleteOlderThan(cutoff);
            log.info("Data retention cleanup completed successfully. Deleted data older than {}", cutoff);
        } catch (Exception e) {
            log.error("Failed to execute data retention cleanup", e);
        }
    }
}
