package com.footstone.audit.service.web.controller;

import com.footstone.audit.service.web.dto.*;
import com.footstone.audit.service.web.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@RestController
@RequestMapping("/api/v1/statistics")
@Tag(name = "Statistics", description = "API for querying audit statistics and trends")
public class StatisticsController {
    
    private final StatisticsService statisticsService;
    
    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }
    
    @GetMapping("/overview")
    @Operation(summary = "Get statistics overview for a time range")
    public StatisticsOverviewDto getOverview(
            @Parameter(description = "Start time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant startTime,
            
            @Parameter(description = "End time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant endTime) {
        return statisticsService.getOverview(startTime, endTime);
    }
    
    @GetMapping("/top-risky-sql")
    @Operation(summary = "Get top risky SQL statements")
    public List<RiskySqlDto> getTopRiskySql(
            @Parameter(description = "Number of results to return")
            @RequestParam(defaultValue = "10") int limit) {
        return statisticsService.getTopRiskySql(limit);
    }
    
    @GetMapping("/trends/slow-queries")
    @Operation(summary = "Get slow query trends over time")
    public List<TrendDataPoint> getSlowQueryTrends(
            @Parameter(description = "Start time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant startTime,
            
            @Parameter(description = "End time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant endTime,
            
            @Parameter(description = "Time granularity for aggregation")
            @RequestParam(defaultValue = "HOURLY") Granularity granularity) {
        return statisticsService.getSlowQueryTrends(startTime, endTime, granularity);
    }
    
    @GetMapping("/error-rates")
    @Operation(summary = "Get error rate percentages")
    public Map<String, Double> getErrorRates(
            @Parameter(description = "Start time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant startTime,
            
            @Parameter(description = "End time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant endTime) {
        return statisticsService.getErrorRates(startTime, endTime);
    }
    
    @GetMapping("/checker-stats")
    @Operation(summary = "Get per-checker statistics")
    public List<CheckerStatsDto> getCheckerStats(
            @Parameter(description = "Start time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant startTime,
            
            @Parameter(description = "End time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant endTime) {
        return statisticsService.getCheckerStats(startTime, endTime);
    }
    
    @GetMapping("/daily")
    @Operation(summary = "Get daily statistics breakdown")
    public List<DailyStatsDto> getDailyStats(
            @Parameter(description = "Start time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant startTime,
            
            @Parameter(description = "End time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant endTime) {
        return statisticsService.getDailyStats(startTime, endTime);
    }
    
    @GetMapping("/hourly")
    @Operation(summary = "Get hourly statistics breakdown")
    public List<TrendDataPoint> getHourlyStats(
            @Parameter(description = "Start time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant startTime,
            
            @Parameter(description = "End time (ISO-8601 format)")
            @RequestParam @DateTimeFormat(iso = ISO.DATE_TIME) Instant endTime) {
        return statisticsService.getHourlyStats(startTime, endTime);
    }
}









