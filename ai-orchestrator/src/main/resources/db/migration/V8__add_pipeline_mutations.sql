-- Pipeline mutation support: pruned steps
ALTER TABLE ai_run ADD COLUMN pruned_steps TEXT;
ALTER TABLE ai_run ADD COLUMN pending_grafts JSONB;
