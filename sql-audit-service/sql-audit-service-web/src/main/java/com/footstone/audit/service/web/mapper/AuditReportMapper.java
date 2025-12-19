package com.footstone.audit.service.web.mapper;

import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.audit.service.core.model.CheckerResult;
import com.footstone.audit.service.web.dto.AuditReportDto;
import com.footstone.audit.service.web.dto.CheckerResultDto;
import com.footstone.sqlguard.core.model.RiskLevel;

import java.util.List;
import java.util.stream.Collectors;

public class AuditReportMapper {
    
    public static AuditReportDto toDto(AuditReport report) {
        List<CheckerResultDto> checkerDtos = report.checkerResults().stream()
            .map(AuditReportMapper::toDto)
            .collect(Collectors.toList());
        
        RiskLevel riskLevel = report.aggregatedRiskScore() != null 
            ? report.aggregatedRiskScore().getSeverity() 
            : RiskLevel.SAFE;
        
        return new AuditReportDto(
            report.reportId(),
            report.sqlId(),
            report.originalEvent().getSql(),
            report.aggregatedRiskScore(),
            riskLevel,
            checkerDtos,
            report.createdAt(),
            report.originalEvent().getExecutionTimeMs(),
            report.originalEvent().getRowsAffected()
        );
    }
    
    public static CheckerResultDto toDto(CheckerResult result) {
        return new CheckerResultDto(
            result.checkerId(),
            result.riskScore(),
            result.errorMessage(),
            result.isSuccess()
        );
    }
}
