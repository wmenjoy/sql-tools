package com.footstone.audit.service.web.service;

import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.audit.service.core.repository.AuditReportRepository;
import com.footstone.audit.service.web.exception.ResourceNotFoundException;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditReportQueryService {
    
    private final AuditReportRepository repository;
    
    public AuditReportQueryService(AuditReportRepository repository) {
        this.repository = repository;
    }
    
    public Page<AuditReport> getAudits(
            String sqlId, 
            RiskLevel riskLevel, 
            Instant startTime, 
            Instant endTime,
            Pageable pageable) {
        
        // Determine time range - default to last 24 hours if not specified
        Instant effectiveStart = startTime != null ? startTime : Instant.now().minusSeconds(86400);
        Instant effectiveEnd = endTime != null ? endTime : Instant.now();
        
        // Fetch all reports in time range
        List<AuditReport> allReports = repository.findByTimeRange(effectiveStart, effectiveEnd);
        
        // Apply filters
        List<AuditReport> filtered = allReports.stream()
            .filter(report -> sqlId == null || sqlId.equals(report.sqlId()))
            .filter(report -> riskLevel == null || 
                    (report.aggregatedRiskScore() != null && 
                     report.aggregatedRiskScore().getSeverity() == riskLevel))
            .collect(Collectors.toList());
        
        // Apply sorting
        if (pageable.getSort().isSorted()) {
            filtered = applySorting(filtered, pageable);
        } else {
            // Default sort by creation time descending
            filtered.sort(Comparator.comparing(AuditReport::createdAt).reversed());
        }
        
        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        
        List<AuditReport> pageContent = filtered.subList(start, end);
        
        return new PageImpl<>(pageContent, pageable, filtered.size());
    }
    
    private List<AuditReport> applySorting(List<AuditReport> reports, Pageable pageable) {
        Comparator<AuditReport> comparator = null;
        
        pageable.getSort().forEach(order -> {
            Comparator<AuditReport> currentComparator = switch (order.getProperty()) {
                case "createdAt" -> Comparator.comparing(AuditReport::createdAt);
                case "sqlId" -> Comparator.comparing(AuditReport::sqlId);
                case "riskLevel" -> Comparator.comparing(r -> 
                    r.aggregatedRiskScore() != null ? r.aggregatedRiskScore().getSeverity() : RiskLevel.SAFE);
                default -> Comparator.comparing(AuditReport::createdAt);
            };
            
            if (order.isDescending()) {
                currentComparator = currentComparator.reversed();
            }
        });
        
        if (comparator != null) {
            reports.sort(comparator);
        }
        
        return reports;
    }
    
    public AuditReport getAuditById(String reportId) {
        return repository.findById(reportId)
            .orElseThrow(() -> new ResourceNotFoundException("Audit report not found: " + reportId));
    }
    
    public Page<AuditReport> getAuditsBySqlId(String sqlId, Pageable pageable) {
        return getAudits(sqlId, null, null, null, pageable);
    }
    
    public Page<AuditReport> getAuditsByRiskLevel(RiskLevel riskLevel, Pageable pageable) {
        return getAudits(null, riskLevel, null, null, pageable);
    }
}









