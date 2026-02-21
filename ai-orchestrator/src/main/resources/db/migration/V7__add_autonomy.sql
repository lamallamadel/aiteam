ALTER TABLE ai_run
  ADD COLUMN autonomy VARCHAR(20) DEFAULT 'autonomous',
  ADD COLUMN autonomy_dev_gate_passed BOOLEAN DEFAULT FALSE;
