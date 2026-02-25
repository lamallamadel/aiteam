-- Webhook events table for audit trail
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    processed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_webhook_events_event_type ON webhook_events(event_type);
CREATE INDEX idx_webhook_events_processed_at ON webhook_events(processed_at);
CREATE INDEX idx_webhook_events_signature_valid ON webhook_events(signature_valid);
