CREATE TABLE audit_reports (
    report_id VARCHAR(255) NOT NULL,
    sql_id VARCHAR(255),
    original_event_json TEXT,
    checker_results_json TEXT,
    risk_level VARCHAR(50),
    risk_score INTEGER NOT NULL,
    created_at TIMESTAMP(6),
    PRIMARY KEY (report_id)
);

CREATE INDEX idx_audit_reports_created_at ON audit_reports (created_at);
CREATE INDEX idx_audit_reports_sql_id ON audit_reports (sql_id);
