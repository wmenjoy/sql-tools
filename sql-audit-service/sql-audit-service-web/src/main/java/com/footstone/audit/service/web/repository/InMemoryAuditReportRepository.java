package com.footstone.audit.service.web.repository;

import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.audit.service.core.repository.AuditReportRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of AuditReportRepository for testing and development.
 * This will be replaced by JPA implementation in production.
 */
@Repository
public class InMemoryAuditReportRepository implements AuditReportRepository {
    
    private final ConcurrentHashMap<String, AuditReport> reports = new ConcurrentHashMap<>();
    
    @Override
    public void save(AuditReport report) {
        reports.put(report.reportId(), report);
    }
    
    @Override
    public Optional<AuditReport> findById(String reportId) {
        return Optional.ofNullable(reports.get(reportId));
    }
    
    @Override
    public List<AuditReport> findByTimeRange(Instant start, Instant end) {
        return reports.values().stream()
            .filter(report -> !report.createdAt().isBefore(start) && !report.createdAt().isAfter(end))
            .collect(Collectors.toList());
    }
}
