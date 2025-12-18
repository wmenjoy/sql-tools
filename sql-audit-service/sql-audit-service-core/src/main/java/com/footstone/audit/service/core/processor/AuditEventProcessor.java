package com.footstone.audit.service.core.processor;

import com.footstone.audit.service.core.model.AuditProcessingResult;
import com.footstone.sqlguard.audit.AuditEvent;

public interface AuditEventProcessor {
    AuditProcessingResult process(AuditEvent event);
}
