package com.footstone.audit.service.core.model;

public record AuditProcessingResult(
    boolean success,
    AuditReport report,
    String error
) {}
