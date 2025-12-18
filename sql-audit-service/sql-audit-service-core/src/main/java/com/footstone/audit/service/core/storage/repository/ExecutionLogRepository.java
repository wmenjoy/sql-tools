package com.footstone.audit.service.core.storage.repository;

import com.footstone.sqlguard.audit.AuditEvent;
import java.time.Instant;
import java.util.List;

public interface ExecutionLogRepository {
    void log(AuditEvent event);
    void logBatch(List<AuditEvent> events);
    
    List<AuditEvent> findByTimeRange(Instant startTime, Instant endTime);
    long countByTimeRange(Instant startTime, Instant endTime);
    void deleteOlderThan(Instant timestamp);
}
