package com.footstone.audit.service.core.repository;

import com.footstone.audit.service.core.model.AuditReport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuditReportRepository {
    void save(AuditReport report);
    Optional<AuditReport> findById(String reportId);
    List<AuditReport> findByTimeRange(Instant start, Instant end);
}
