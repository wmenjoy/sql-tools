package com.footstone.audit.service.web.controller;

import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.audit.service.web.dto.AuditReportDto;
import com.footstone.audit.service.web.mapper.AuditReportMapper;
import com.footstone.audit.service.web.service.AuditReportQueryService;
import com.footstone.sqlguard.core.model.RiskLevel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

import static org.springframework.format.annotation.DateTimeFormat.ISO;

@RestController
@RequestMapping("/api/v1/audits")
@Tag(name = "Audit Reports", description = "API for querying SQL audit reports")
public class AuditReportController {
    
    private final AuditReportQueryService queryService;
    
    public AuditReportController(AuditReportQueryService queryService) {
        this.queryService = queryService;
    }
    
    @GetMapping
    @Operation(summary = "Get audit reports with optional filtering and pagination")
    public Page<AuditReportDto> getAudits(
            @Parameter(description = "Filter by SQL identifier")
            @RequestParam(required = false) String sqlId,
            
            @Parameter(description = "Filter by risk level")
            @RequestParam(required = false) RiskLevel riskLevel,
            
            @Parameter(description = "Start time for time range filter (ISO-8601 format)")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = ISO.DATE_TIME) Instant startTime,
            
            @Parameter(description = "End time for time range filter (ISO-8601 format)")
            @RequestParam(required = false) 
            @DateTimeFormat(iso = ISO.DATE_TIME) Instant endTime,
            
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        
        Page<AuditReport> reports = queryService.getAudits(
            sqlId, riskLevel, startTime, endTime, pageable);
        
        return reports.map(AuditReportMapper::toDto);
    }
    
    @GetMapping("/{reportId}")
    @Operation(summary = "Get a specific audit report by ID")
    public ResponseEntity<AuditReportDto> getAuditById(
            @Parameter(description = "Report identifier")
            @PathVariable String reportId) {
        
        AuditReport report = queryService.getAuditById(reportId);
        return ResponseEntity.ok(AuditReportMapper.toDto(report));
    }
}










