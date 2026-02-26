# Notification Integration Guide

## Overview

The Notification Integration service provides Slack and Discord webhook notifications for workflow lifecycle events, review findings, CI check failures, MFA setup reminders, and analytics summaries.

## Features

- **Configurable Webhooks**: Store multiple webhook configurations per user with provider-specific formatting
- **Event-Based Notifications**: Subscribe to specific events (run started/completed/escalated, CI failures, etc.)
- **Template Engine**: Rich markdown-formatted messages with code snippets and severity icons
- **Webhook Delivery**: Asynchronous delivery via WebClient with retry logic and exponential backoff
- **Analytics Summaries**: Scheduled daily and weekly analytics reports
- **Admin UI**: Full webhook configuration interface in Settings Dashboard

## Supported Events

| Event Type | Description | Payload Includes |
|------------|-------------|------------------|
| `run_started` | Workflow run begins | Repository, issue #, mode, status |
| `run_completed` | Workflow run completes | Status, fix counts, duration, review findings |
| `run_escalated` | Workflow escalated for manual review | Fix attempts, escalation reason, review findings |
| `ci_check_failure` | CI checks fail | Fix attempt count, error logs (truncated) |
| `mfa_setup_reminder` | Reminder to enable MFA | Username |
| `daily_analytics` | Daily workflow summary | Run stats, status breakdown |
| `weekly_analytics` | Weekly workflow summary | Run stats, agent performance, review findings |

## Database Schema

### notification_configs

```sql
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
```

### notification_delivery_log

```sql
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
```

## Backend Integration

### Sending Notifications from Services

```java
@Service
public class MyWorkflowService {
    
    private final NotificationService notificationService;
    
    public void completeWorkflow(UUID userId, RunEntity run) {
        // Workflow logic...
        
        // Send notification
        notificationService.sendRunCompletedNotification(userId, run);
    }
    
    public void escalateRun(UUID userId, RunEntity run) {
        // Escalation logic...
        
        // Send notification
        notificationService.sendRunEscalatedNotification(userId, run);
    }
}
```

### Example Notification Payloads

#### Run Completed (Slack format)
```json
{
  "text": "Workflow Run Completed",
  "blocks": [
    {
      "type": "section",
      "text": {
        "type": "mrkdwn",
        "text": "### ✅ Workflow Run Completed\n\n**Repository:** my-org/my-repo\n**Issue:** #123\n**Status:** DONE\n**CI Fixes:** 2\n**E2E Fixes:** 1\n**Duration:** 5m 30s\n\n**Review Findings:**\n🔴 **CRITICAL**: SQL injection vulnerability detected\n  Location: `src/main/java/UserController.java:45`"
      }
    }
  ]
}
```

#### Daily Analytics (Discord format)
```json
{
  "content": "Daily Analytics Summary",
  "embeds": [
    {
      "title": "📊 Daily Analytics Summary",
      "description": "**Period:** Last 24 hours\n\n**Total Runs:** 42\n**Success Rate:** 85.7%\n**Failure Rate:** 9.5%\n**Escalation Rate:** 4.8%\n\n**Status Breakdown:**\n- DONE: 36\n- FAILED: 4\n- ESCALATED: 2",
      "color": 3447003
    }
  ]
}
```

## Frontend Integration

### Component Usage

The notification configuration UI is integrated into the Settings Dashboard under the "Integrations" tab:

```typescript
// Load notification configs
this.notificationService.getConfigs().subscribe({
  next: (configs) => {
    this.notificationConfigs.set(configs);
  }
});

// Create new webhook
const config: CreateNotificationConfig = {
  provider: 'slack',
  webhookUrl: 'https://hooks.slack.com/services/...',
  enabledEvents: ['run_completed', 'run_escalated', 'ci_check_failure']
};

this.notificationService.createConfig(config).subscribe({
  next: (created) => {
    this.toastService.show('Webhook created successfully', 'success');
  }
});
```

## Scheduled Tasks

### Daily Summary (9 AM daily)
```java
@Scheduled(cron = "0 0 9 * * *")
public void sendDailyAnalyticsSummary()
```

### Weekly Summary (9 AM every Monday)
```java
@Scheduled(cron = "0 0 9 * * MON")
public void sendWeeklyAnalyticsSummary()
```

### Retry Failed Notifications (every 5 minutes)
```java
@Scheduled(fixedDelay = 300000)
public void retryFailedNotifications()
```

## Webhook Delivery

- **Retry Logic**: Up to 3 retry attempts with exponential backoff (1s, 2s, 5s)
- **Timeout**: 10 second timeout per request
- **Error Handling**: Failed deliveries are logged and queued for retry
- **Status Tracking**: All deliveries tracked in `notification_delivery_log` table

## API Endpoints

### Get Notification Configs
```
GET /api/notifications/configs
Authorization: Bearer <token>
```

### Create Notification Config
```
POST /api/notifications/configs
Authorization: Bearer <token>
Content-Type: application/json

{
  "provider": "slack",
  "webhookUrl": "https://hooks.slack.com/services/...",
  "enabledEvents": ["run_completed", "run_escalated"]
}
```

### Update Notification Config
```
PUT /api/notifications/configs/{configId}
Authorization: Bearer <token>
Content-Type: application/json

{
  "webhookUrl": "https://hooks.slack.com/services/...",
  "enabledEvents": ["run_completed", "ci_check_failure"],
  "enabled": true
}
```

### Delete Notification Config
```
DELETE /api/notifications/configs/{configId}
Authorization: Bearer <token>
```

## Configuration Examples

### Slack Incoming Webhook Setup

1. Go to your Slack workspace settings
2. Navigate to "Apps" → "Incoming Webhooks"
3. Click "Add to Slack"
4. Select a channel and copy the webhook URL
5. Add the webhook URL to Atlasia settings

### Discord Webhook Setup

1. Open Discord server settings
2. Navigate to "Integrations" → "Webhooks"
3. Click "New Webhook"
4. Select a channel and copy the webhook URL
5. Add the webhook URL to Atlasia settings

## Security Considerations

- Webhook URLs are stored in database and should be treated as sensitive
- All webhook deliveries are logged for audit purposes
- Failed deliveries are automatically retried with backoff
- Users can only manage their own notification configurations
- Admin users can view delivery logs for troubleshooting

## Monitoring and Troubleshooting

### Check Delivery Status
Query the `notification_delivery_log` table to see delivery history:

```sql
SELECT event_type, status, http_status_code, error_message, created_at
FROM notification_delivery_log
WHERE notification_config_id = '<config-id>'
ORDER BY created_at DESC
LIMIT 50;
```

### Common Issues

**Webhook not receiving notifications:**
- Verify the webhook URL is correct
- Check that the event is enabled in the configuration
- Review the delivery log for error messages
- Ensure the webhook endpoint is accessible from the Atlasia server

**Notifications delayed:**
- Check the retry queue for failed deliveries
- Verify network connectivity to the webhook endpoint
- Review server logs for WebClient errors

## Performance Considerations

- Webhook delivery is asynchronous and non-blocking
- Failed deliveries are retried in background job
- Delivery logs are automatically cleaned up based on retention policy
- Large payloads (>500 chars) are truncated for logs
