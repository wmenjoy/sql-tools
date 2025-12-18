package com.footstone.audit.service.core.storage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "audit_reports", indexes = {
    @Index(name = "idx_audit_reports_created_at", columnList = "createdAt"),
    @Index(name = "idx_audit_reports_sql_id", columnList = "sqlId")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditReportEntity {
    @Id
    private String reportId;
    
    private String sqlId;
    
    @Column(columnDefinition = "TEXT")
    private String originalEventJson;
    
    @Column(columnDefinition = "TEXT")
    private String checkerResultsJson;
    
    private String riskLevel;
    
    private int riskScore;
    
    private Instant createdAt;
}
