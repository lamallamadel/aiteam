ALTER TABLE ai_run_artifact ADD COLUMN original_filename VARCHAR(500);
ALTER TABLE ai_run_artifact ADD COLUMN content_type VARCHAR(255);
ALTER TABLE ai_run_artifact ADD COLUMN size_bytes BIGINT;
ALTER TABLE ai_run_artifact ADD COLUMN uploaded_by UUID;
ALTER TABLE ai_run_artifact ADD COLUMN uploaded_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE ai_run_artifact ADD COLUMN file_path VARCHAR(1000);
ALTER TABLE ai_run_artifact ALTER COLUMN payload DROP NOT NULL;

CREATE INDEX idx_ai_run_artifact_uploaded_by ON ai_run_artifact(uploaded_by);
CREATE INDEX idx_ai_run_artifact_uploaded_at ON ai_run_artifact(uploaded_at);

COMMENT ON COLUMN ai_run_artifact.original_filename IS 'Original filename of uploaded file';
COMMENT ON COLUMN ai_run_artifact.content_type IS 'MIME type of uploaded file';
COMMENT ON COLUMN ai_run_artifact.size_bytes IS 'Size of uploaded file in bytes';
COMMENT ON COLUMN ai_run_artifact.uploaded_by IS 'User who uploaded the file';
COMMENT ON COLUMN ai_run_artifact.uploaded_at IS 'Timestamp when file was uploaded';
COMMENT ON COLUMN ai_run_artifact.file_path IS 'File system path to stored file';
