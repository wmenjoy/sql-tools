package com.footstone.audit.service.web.service;

import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.audit.service.core.model.CheckerResult;
import com.footstone.audit.service.core.repository.AuditReportRepository;
import com.footstone.audit.service.web.dto.*;
import com.footstone.sqlguard.core.model.RiskLevel;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsService {
    
    private final AuditReportRepository repository;
    
    public StatisticsService(AuditReportRepository repository) {
        this.repository = repository;
    }
    
    @Cacheable(value = "statisticsOverview", key = "#startTime.toString() + '-' + #endTime.toString()")
    public StatisticsOverviewDto getOverview(Instant startTime, Instant endTime) {
        List<AuditReport> reports = repository.findByTimeRange(startTime, endTime);
        
        // Count by risk level
        Map<RiskLevel, Long> byRiskLevel = reports.stream()
            .filter(r -> r.aggregatedRiskScore() != null)
            .collect(Collectors.groupingBy(
                r -> r.aggregatedRiskScore().getSeverity(),
                Collectors.counting()
            ));
        
        // Top triggered checkers
        Map<String, Long> checkerCounts = reports.stream()
            .flatMap(r -> r.checkerResults().stream())
            .filter(CheckerResult::isSuccess)
            .filter(cr -> cr.riskScore() != null && cr.riskScore().getSeverity() != RiskLevel.SAFE)
            .collect(Collectors.groupingBy(
                CheckerResult::checkerId,
                Collectors.counting()
            ));
        
        Map<String, Long> topCheckers = checkerCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(10)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
        
        // Average execution time
        double avgExecTime = reports.stream()
            .mapToLong(r -> r.originalEvent().getExecutionTimeMs())
            .average()
            .orElse(0.0);
        
        // Error count
        long errorCount = reports.stream()
            .filter(r -> r.originalEvent().getErrorMessage() != null)
            .count();
        
        return new StatisticsOverviewDto(
            reports.size(),
            byRiskLevel,
            topCheckers,
            avgExecTime,
            errorCount
        );
    }
    
    public List<RiskySqlDto> getTopRiskySql(int limit) {
        Instant startTime = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant endTime = Instant.now();
        List<AuditReport> reports = repository.findByTimeRange(startTime, endTime);
        
        // Group by sqlId and count
        Map<String, List<AuditReport>> bySqlId = reports.stream()
            .collect(Collectors.groupingBy(AuditReport::sqlId));
        
        return bySqlId.entrySet().stream()
            .map(entry -> {
                List<AuditReport> sqlReports = entry.getValue();
                AuditReport first = sqlReports.get(0);
                
                RiskLevel maxRisk = sqlReports.stream()
                    .filter(r -> r.aggregatedRiskScore() != null)
                    .map(r -> r.aggregatedRiskScore().getSeverity())
                    .max(Comparator.naturalOrder())
                    .orElse(RiskLevel.SAFE);
                
                return new RiskySqlDto(
                    entry.getKey(),
                    first.originalEvent().getSql(),
                    sqlReports.size(),
                    maxRisk,
                    first.originalEvent().getMapperId()
                );
            })
            .filter(dto -> dto.maxRiskLevel() != RiskLevel.SAFE)
            .sorted(Comparator.comparingLong(RiskySqlDto::flagCount).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    public List<TrendDataPoint> getSlowQueryTrends(Instant startTime, Instant endTime, Granularity granularity) {
        List<AuditReport> reports = repository.findByTimeRange(startTime, endTime);
        
        // Filter slow queries (> 1000ms)
        List<AuditReport> slowQueries = reports.stream()
            .filter(r -> r.originalEvent().getExecutionTimeMs() > 1000)
            .collect(Collectors.toList());
        
        // Group by time bucket
        return groupByTimeBucket(slowQueries, granularity);
    }
    
    private List<TrendDataPoint> groupByTimeBucket(List<AuditReport> reports, Granularity granularity) {
        Map<Instant, List<AuditReport>> grouped = reports.stream()
            .collect(Collectors.groupingBy(r -> truncateToGranularity(r.createdAt(), granularity)));
        
        return grouped.entrySet().stream()
            .map(entry -> {
                double avgTime = entry.getValue().stream()
                    .mapToLong(r -> r.originalEvent().getExecutionTimeMs())
                    .average()
                    .orElse(0.0);
                return new TrendDataPoint(entry.getKey(), entry.getValue().size(), avgTime);
            })
            .sorted(Comparator.comparing(TrendDataPoint::timestamp))
            .collect(Collectors.toList());
    }
    
    private Instant truncateToGranularity(Instant instant, Granularity granularity) {
        return switch (granularity) {
            case HOURLY -> instant.truncatedTo(ChronoUnit.HOURS);
            case DAILY -> instant.truncatedTo(ChronoUnit.DAYS);
            case WEEKLY -> instant.truncatedTo(ChronoUnit.DAYS).minus(
                LocalDate.ofInstant(instant, ZoneId.systemDefault()).getDayOfWeek().getValue() - 1,
                ChronoUnit.DAYS
            );
            case MONTHLY -> LocalDate.ofInstant(instant, ZoneId.systemDefault())
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
        };
    }
    
    public Map<String, Double> getErrorRates(Instant startTime, Instant endTime) {
        List<AuditReport> reports = repository.findByTimeRange(startTime, endTime);
        
        long total = reports.size();
        if (total == 0) {
            return Map.of("errorRate", 0.0, "successRate", 100.0);
        }
        
        long errors = reports.stream()
            .filter(r -> r.originalEvent().getErrorMessage() != null)
            .count();
        
        double errorRate = (double) errors / total * 100;
        return Map.of(
            "errorRate", errorRate,
            "successRate", 100 - errorRate
        );
    }
    
    public List<CheckerStatsDto> getCheckerStats(Instant startTime, Instant endTime) {
        List<AuditReport> reports = repository.findByTimeRange(startTime, endTime);
        
        // Group all checker results by checkerId
        Map<String, List<CheckerResult>> byChecker = reports.stream()
            .flatMap(r -> r.checkerResults().stream())
            .collect(Collectors.groupingBy(CheckerResult::checkerId));
        
        return byChecker.entrySet().stream()
            .map(entry -> {
                List<CheckerResult> results = entry.getValue();
                long total = results.size();
                long successes = results.stream().filter(CheckerResult::isSuccess).count();
                
                return new CheckerStatsDto(
                    entry.getKey(),
                    total,
                    total > 0 ? (double) successes / total * 100 : 0.0,
                    0.0 // Processing time not tracked in current model
                );
            })
            .sorted(Comparator.comparingLong(CheckerStatsDto::triggerCount).reversed())
            .collect(Collectors.toList());
    }
    
    public List<DailyStatsDto> getDailyStats(Instant startTime, Instant endTime) {
        List<AuditReport> reports = repository.findByTimeRange(startTime, endTime);
        
        Map<LocalDate, List<AuditReport>> byDate = reports.stream()
            .collect(Collectors.groupingBy(r -> 
                LocalDate.ofInstant(r.createdAt(), ZoneId.systemDefault())
            ));
        
        return byDate.entrySet().stream()
            .map(entry -> {
                List<AuditReport> dayReports = entry.getValue();
                
                Map<RiskLevel, Long> byRisk = dayReports.stream()
                    .filter(r -> r.aggregatedRiskScore() != null)
                    .collect(Collectors.groupingBy(
                        r -> r.aggregatedRiskScore().getSeverity(),
                        Collectors.counting()
                    ));
                
                double avgExec = dayReports.stream()
                    .mapToLong(r -> r.originalEvent().getExecutionTimeMs())
                    .average()
                    .orElse(0.0);
                
                return new DailyStatsDto(
                    entry.getKey(),
                    dayReports.size(),
                    byRisk,
                    avgExec
                );
            })
            .sorted(Comparator.comparing(DailyStatsDto::date))
            .collect(Collectors.toList());
    }
    
    public List<TrendDataPoint> getHourlyStats(Instant startTime, Instant endTime) {
        List<AuditReport> reports = repository.findByTimeRange(startTime, endTime);
        return groupByTimeBucket(reports, Granularity.HOURLY);
    }
}








