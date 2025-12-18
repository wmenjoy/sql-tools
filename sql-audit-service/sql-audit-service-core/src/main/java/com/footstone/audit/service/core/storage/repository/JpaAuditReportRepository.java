package com.footstone.audit.service.core.storage.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.footstone.audit.service.core.model.AuditReport;
import com.footstone.audit.service.core.model.CheckerResult;
import com.footstone.audit.service.core.repository.AuditReportRepository;
import com.footstone.audit.service.core.storage.entity.AuditReportEntity;
import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.model.RiskScore;
import com.footstone.sqlguard.core.model.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
@Slf4j
public class JpaAuditReportRepository implements AuditReportRepository {

    private final AuditReportJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void save(AuditReport report) {
        AuditReportEntity entity = toEntity(report);
        jpaRepository.save(entity);
    }

    @Override
    public Optional<AuditReport> findById(String reportId) {
        return jpaRepository.findById(reportId)
                .map(this::toDomain);
    }

    @Override
    public List<AuditReport> findByTimeRange(Instant start, Instant end) {
        return jpaRepository.findByCreatedAtBetween(start, end).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private AuditReportEntity toEntity(AuditReport report) {
        try {
            return AuditReportEntity.builder()
                    .reportId(report.reportId())
                    .sqlId(report.sqlId())
                    .originalEventJson(objectMapper.writeValueAsString(report.originalEvent()))
                    .checkerResultsJson(objectMapper.writeValueAsString(report.checkerResults()))
                    .riskLevel(report.aggregatedRiskScore().getSeverity().name())
                    .riskScore(report.aggregatedRiskScore().getConfidence())
                    .createdAt(report.createdAt())
                    .build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize audit report data", e);
        }
    }

    private AuditReport toDomain(AuditReportEntity entity) {
        try {
            AuditEvent originalEvent = objectMapper.readValue(entity.getOriginalEventJson(), AuditEvent.class);
            List<CheckerResult> checkerResults = objectMapper.readValue(entity.getCheckerResultsJson(), new TypeReference<List<CheckerResult>>() {});
            
            RiskScore aggregatedRiskScore = RiskScore.builder()
                    .severity(RiskLevel.valueOf(entity.getRiskLevel()))
                    .confidence(entity.getRiskScore())
                    .justification("Restored from DB")
                    .build();

            return new AuditReport(
                    entity.getReportId(),
                    entity.getSqlId(),
                    originalEvent,
                    checkerResults,
                    aggregatedRiskScore,
                    entity.getCreatedAt()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize audit report data", e);
        }
    }
}
