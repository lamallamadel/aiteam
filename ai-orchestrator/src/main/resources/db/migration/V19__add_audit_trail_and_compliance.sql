-- Add tamper-proof audit trail with hash chain to collaboration_events
ALTER TABLE collaboration_events ADD COLUMN retention_days INTEGER DEFAULT 2555;
ALTER TABLE collaboration_events ADD COLUMN archived_at TIMESTAMP;
ALTER TABLE collaboration_events ADD COLUMN previous_event_hash VARCHAR(64);
ALTER TABLE collaboration_events ADD COLUMN event_hash VARCHAR(64);

CREATE INDEX idx_collaboration_events_event_hash ON collaboration_events(event_hash);
CREATE INDEX idx_collaboration_events_archived_at ON collaboration_events(archived_at);

-- Create audit log table for authentication events
CREATE TABLE audit_authentication_events (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    username VARCHAR(255) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN NOT NULL,
    failure_reason TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    retention_days INTEGER DEFAULT 2555,
    archived_at TIMESTAMP,
    previous_event_hash VARCHAR(64),
    event_hash VARCHAR(64) NOT NULL
);

CREATE INDEX idx_audit_auth_user_id ON audit_authentication_events(user_id);
CREATE INDEX idx_audit_auth_timestamp ON audit_authentication_events(timestamp);
CREATE INDEX idx_audit_auth_event_type ON audit_authentication_events(event_type);
CREATE INDEX idx_audit_auth_event_hash ON audit_authentication_events(event_hash);

-- Create audit log table for access logs
CREATE TABLE audit_access_logs (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    username VARCHAR(255) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255),
    action VARCHAR(50) NOT NULL,
    http_method VARCHAR(10),
    endpoint VARCHAR(500),
    ip_address VARCHAR(45),
    user_agent TEXT,
    status_code INTEGER,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    retention_days INTEGER DEFAULT 2555,
    archived_at TIMESTAMP,
    previous_event_hash VARCHAR(64),
    event_hash VARCHAR(64) NOT NULL
);

CREATE INDEX idx_audit_access_user_id ON audit_access_logs(user_id);
CREATE INDEX idx_audit_access_timestamp ON audit_access_logs(timestamp);
CREATE INDEX idx_audit_access_resource ON audit_access_logs(resource_type, resource_id);
CREATE INDEX idx_audit_access_event_hash ON audit_access_logs(event_hash);

-- Create audit log table for data mutations
CREATE TABLE audit_data_mutations (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    username VARCHAR(255) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255) NOT NULL,
    operation VARCHAR(20) NOT NULL,
    field_name VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    retention_days INTEGER DEFAULT 2555,
    archived_at TIMESTAMP,
    previous_event_hash VARCHAR(64),
    event_hash VARCHAR(64) NOT NULL
);

CREATE INDEX idx_audit_mutation_user_id ON audit_data_mutations(user_id);
CREATE INDEX idx_audit_mutation_timestamp ON audit_data_mutations(timestamp);
CREATE INDEX idx_audit_mutation_entity ON audit_data_mutations(entity_type, entity_id);
CREATE INDEX idx_audit_mutation_event_hash ON audit_data_mutations(event_hash);

-- Create audit log table for admin actions
CREATE TABLE audit_admin_actions (
    id UUID PRIMARY KEY,
    admin_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    admin_username VARCHAR(255) NOT NULL,
    action_type VARCHAR(100) NOT NULL,
    target_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    target_username VARCHAR(255),
    action_details TEXT,
    ip_address VARCHAR(45),
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    retention_days INTEGER DEFAULT 2555,
    archived_at TIMESTAMP,
    previous_event_hash VARCHAR(64),
    event_hash VARCHAR(64) NOT NULL
);

CREATE INDEX idx_audit_admin_admin_user_id ON audit_admin_actions(admin_user_id);
CREATE INDEX idx_audit_admin_target_user_id ON audit_admin_actions(target_user_id);
CREATE INDEX idx_audit_admin_timestamp ON audit_admin_actions(timestamp);
CREATE INDEX idx_audit_admin_action_type ON audit_admin_actions(action_type);
CREATE INDEX idx_audit_admin_event_hash ON audit_admin_actions(event_hash);

-- Create compliance reports tracking table
CREATE TABLE compliance_reports (
    id UUID PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    report_period_start TIMESTAMP NOT NULL,
    report_period_end TIMESTAMP NOT NULL,
    generated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    generated_by VARCHAR(255),
    file_path TEXT,
    record_count INTEGER,
    status VARCHAR(20) NOT NULL
);

CREATE INDEX idx_compliance_reports_type ON compliance_reports(report_type);
CREATE INDEX idx_compliance_reports_generated_at ON compliance_reports(generated_at);
