# GitHub Webhooks Integration

The Atlasia AI Orchestrator supports event-driven workflow execution via GitHub webhooks, replacing the need for polling-based GitHub Actions workflows.

## Overview

When a GitHub issue is labeled with `ai:run`, the orchestrator automatically starts a workflow run. This provides:

- **Event-driven architecture**: Instant response to issue labeling
- **Security**: HMAC-SHA256 signature verification
- **Audit trail**: All webhook events stored in database
- **No polling**: Eliminates the need for scheduled GitHub Actions

## Setup

### 1. Configure Webhook Secret

Set the webhook secret in your environment or Vault:

```bash
# Environment variable
export GITHUB_WEBHOOK_SECRET="your-secret-here"

# Or in Vault
vault kv put secret/atlasia github-webhook-secret="your-secret-here"
```

### 2. Configure GitHub Webhook

1. Go to your GitHub repository settings
2. Navigate to **Settings** → **Webhooks** → **Add webhook**
3. Configure the webhook:
   - **Payload URL**: `https://your-domain.com/api/webhooks/github`
   - **Content type**: `application/json`
   - **Secret**: The same secret you configured above
   - **Events**: Select "Let me select individual events" and choose:
     - ✅ Issues
   - **Active**: ✅ Enabled

### 3. Test the Webhook

1. Create or open an issue in your repository
2. Add the `ai:run` label to the issue
3. Check the webhook delivery in GitHub's webhook settings
4. Verify the run was created by checking `/api/runs`

## Webhook Flow

```
GitHub Issue Labeled (ai:run)
    ↓
GitHub sends POST /api/webhooks/github
    ↓
Signature verification (HMAC-SHA256)
    ↓
Event stored in webhook_events table
    ↓
Filter: event_type == "issues" && action == "labeled" && label == "ai:run"
    ↓
Extract: repo, issue number, title, body
    ↓
Create RunEntity with mode="code", status=RECEIVED
    ↓
WorkflowEngine.executeWorkflowAsync(runId, githubToken)
    ↓
Autonomous workflow execution begins
```

## Security

### Signature Verification

All webhook requests are verified using HMAC-SHA256:

1. GitHub calculates HMAC-SHA256 hash of the payload using the shared secret
2. GitHub sends the hash in the `X-Hub-Signature-256` header as `sha256=<hash>`
3. Orchestrator calculates the expected hash using the same secret
4. Orchestrator uses constant-time comparison to prevent timing attacks
5. Request is rejected if signatures don't match

### Database Audit Trail

Every webhook event is logged in the `webhook_events` table:

```sql
SELECT * FROM webhook_events ORDER BY processed_at DESC LIMIT 10;
```

Fields:
- `id`: Unique event ID
- `event_type`: GitHub event type (e.g., "issues")
- `payload`: Full JSON payload from GitHub
- `signature_valid`: Whether the signature was valid
- `processed_at`: Timestamp of processing

## Event Filtering

The webhook endpoint only processes events that match ALL criteria:

1. **Event Type**: `X-GitHub-Event` header == `"issues"`
2. **Action**: Payload `action` field == `"labeled"`
3. **Label**: Payload `label.name` field == `"ai:run"`

All other events receive a `200 OK` response with `{"status":"ignored"}` to acknowledge receipt but skip processing.

## Payload Extraction

From the GitHub webhook payload, the following metadata is extracted:

- `repository.full_name` → `RunEntity.repo`
- `issue.number` → `RunEntity.issueNumber`
- `issue.title` → Used for logging
- `issue.body` → Available in workflow context

## Troubleshooting

### Webhook Returns 401 Unauthorized

- Verify the webhook secret matches in both GitHub and orchestrator config
- Check the `webhook_events` table: `SELECT * FROM webhook_events WHERE signature_valid = false;`
- Review orchestrator logs for signature verification details

### Webhook Returns 200 but No Run Created

- Check the event type: only `issues` events with `labeled` action are processed
- Verify the label name is exactly `ai:run` (case-sensitive)
- Review orchestrator logs for filtering decisions

### Run Fails Immediately

- Verify the repository name format is `owner/repo`
- Check that the issue number is valid
- Review the run artifacts: `GET /api/runs/{runId}/artifacts`

## API Endpoint

**POST** `/api/webhooks/github`

**Headers:**
- `X-Hub-Signature-256`: HMAC-SHA256 signature (required)
- `X-GitHub-Event`: Event type (required)
- `Content-Type`: application/json

**Response Codes:**
- `200`: Event processed or ignored
- `401`: Invalid signature
- `500`: Internal processing error

**Response Body Examples:**

```json
// Success
{
  "status": "accepted",
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "repo": "owner/repo",
  "issueNumber": 42
}

// Ignored (wrong event type)
{
  "status": "ignored",
  "reason": "not an issues event"
}

// Ignored (wrong label)
{
  "status": "ignored",
  "reason": "label is not ai:run"
}
```

## Migration from GitHub Actions

If you're currently using GitHub Actions to poll for labeled issues, you can:

1. Set up the webhook as described above
2. Test that webhook-triggered runs work correctly
3. Disable or remove the polling GitHub Actions workflow
4. Keep the Actions workflow as a backup for manual triggers if desired

## Database Schema

```sql
CREATE TABLE webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    signature_valid BOOLEAN NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhook_events_event_type ON webhook_events(event_type);
CREATE INDEX idx_webhook_events_processed_at ON webhook_events(processed_at);
CREATE INDEX idx_webhook_events_signature_valid ON webhook_events(signature_valid);
```
