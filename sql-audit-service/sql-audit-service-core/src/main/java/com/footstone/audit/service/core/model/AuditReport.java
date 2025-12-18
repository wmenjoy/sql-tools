package com.footstone.audit.service.core.model;

import com.footstone.sqlguard.audit.AuditEvent;
import com.footstone.sqlguard.audit.model.RiskScore;
import java.time.Instant;
import java.util.List;

public record AuditReport(
    String reportId,
    String sqlId,
    AuditEvent originalEvent,
    List<CheckerResult> checkerResults,
    RiskScore aggregatedRiskScore,
    Instant createdAt
) {}
