ALTER TABLE ai_run
  ADD COLUMN environment_lifecycle VARCHAR(20) DEFAULT 'ACTIVE',
  ADD COLUMN environment_checkpoint JSONB;
