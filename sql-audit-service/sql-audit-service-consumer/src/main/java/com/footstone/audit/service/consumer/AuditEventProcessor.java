package com.footstone.audit.service.consumer;

import com.footstone.sqlguard.audit.AuditEvent;

public interface AuditEventProcessor {
    void process(AuditEvent event);
}
