package com.footstone.audit.service.core.storage.repository;

import com.footstone.audit.service.core.storage.entity.AuditReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditReportJpaRepository extends JpaRepository<AuditReportEntity, String> {
    List<AuditReportEntity> findByCreatedAtBetween(Instant start, Instant end);
    List<AuditReportEntity> findBySqlId(String sqlId);
    List<AuditReportEntity> findByRiskLevel(String riskLevel);
}
