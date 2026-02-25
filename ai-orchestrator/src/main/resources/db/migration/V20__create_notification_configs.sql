-- Create notification_configs table for Slack/Discord webhook integrations
CREATE TABLE notification_configs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL CHECK (provider IN ('slack', 'discord')),
    webhook_url TEXT NOT NULL,
    enabled_events JSONB NOT NULL DEFAULT '[]'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_configs_user_id ON notification_configs(user_id);
CREATE INDEX idx_notification_configs_provider ON notification_configs(provider);
CREATE INDEX idx_notification_configs_enabled ON notification_configs(enabled);

-- Create notification_delivery_log table to track webhook deliveries
CREATE TABLE notification_delivery_log (
    id UUID PRIMARY KEY,
    notification_config_id UUID REFERENCES notification_configs(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,
    webhook_url TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    http_status_code INTEGER,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_delivery_config_id ON notification_delivery_log(notification_config_id);
CREATE INDEX idx_notification_delivery_created_at ON notification_delivery_log(created_at);
CREATE INDEX idx_notification_delivery_status ON notification_delivery_log(status);
