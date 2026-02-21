CREATE TABLE IF NOT EXISTS ai_global_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    value_json TEXT NOT NULL DEFAULT '{}'
);

INSERT INTO ai_global_settings (setting_key, value_json)
VALUES ('oversight_config', '{"autonomyLevel":"supervised","autoApproveMedianTier":true,"maxConcurrentRuns":5,"interruptRules":[]}')
ON CONFLICT DO NOTHING;
