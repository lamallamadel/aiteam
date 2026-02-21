-- Pipeline mutation support: pruned steps + pending grafts
ALTER TABLE ai_run
    ADD COLUMN pruned_steps TEXT,
    ADD COLUMN pending_grafts JSONB;
