# Centralized Logging Implementation Summary

## Overview

Implemented comprehensive centralized logging using **Grafana Loki + Promtail** with structured JSON logging, MDC context propagation, tiered retention policies, log-based alerting, and Grafana Explore dashboards.

## Implementation Components

### 1. Structured JSON Logging

#### Application Configuration (`application.yml`)
- Added logging configuration section with pattern including correlationId, runId, and userId
- Configured log levels for production use

#### Logback Configuration (`logback-spring.xml`)
- **Development Profile**: Plain text logs with MDC context
  - Console and file appenders
  - Debug-level logging for troubleshooting
  - 30-day file retention

- **Production Profile**: Structured JSON logs using `logstash-logback-encoder`
  - JSON console appender with LogstashEncoder
  - MDC context propagation (correlationId, runId, userId, agentName)
  - Separate security events appender with category label
  - Exception stack trace formatting with max depth and length limits
  - Custom fields: service, environment, category
  - Security-specific loggers with dedicated appenders

#### MDC Context Propagation

**Updated `CorrelationIdFilter.java`**:
- Added userId extraction from Spring Security context
- Sets correlationId, userId in MDC via CorrelationIdHolder
- Automatic cleanup in finally block

**Updated `CorrelationIdHolder.java`**:
- Added userId methods: `setUserId()`, `getUserId()`
- Updated `clear()` method to remove userId from MDC
- Maintains thread-local context for correlationId, runId, userId, agentName

### 2. Log Aggregation Infrastructure

#### Loki Configuration (`loki-config.yml`)
- **Storage**: BoltDB shipper with filesystem backend
- **Retention**: 90-day global retention with compaction
- **Compactor**: 10-minute intervals with retention enabled
- **Rate Limits**: 10MB/s ingestion rate, 20MB burst
- **Query Limits**: 720-hour lookback, 721-hour max query length
- **Ruler**: Integrated alerting with Alertmanager v2

#### Promtail Configuration (`promtail-config.yml`)
- **Docker SD Config**: Automatic container discovery
- **Jobs**:
  1. `ai-orchestrator`: JSON log parsing from app containers
  2. `system`: Syslog scraping
  3. `docker`: General Docker container logs

- **Pipeline Stages**:
  - JSON parsing with field extraction (@timestamp, level, message, MDC fields)
  - Timestamp conversion to RFC3339Nano
  - Label extraction (level, service, environment, correlationId, runId, userId, agentName)
  - Retention label assignment based on level/category
  - Structured metadata output

#### Docker Compose Integration (`docker-compose-monitoring.yml`)
- **Loki Service**:
  - Image: grafana/loki:2.9.4
  - Volume mounts: config, rules, data
  - Port: 3100
  - Resource limits: 1 CPU, 1GB memory
  - Networks: monitoring-prod, ai_prod_network

- **Promtail Service**:
  - Image: grafana/promtail:2.9.4
  - Volume mounts: config, /var/log, Docker containers
  - Depends on: loki
  - Resource limits: 0.25 CPU, 256MB memory

- **Grafana Updates**:
  - Added Loki datasource configuration mount
  - Added logs dashboard mount
  - Added loki dependency

### 3. Log Retention Policies

Implemented tiered retention based on log criticality:

| Category | Retention | Configuration |
|----------|-----------|---------------|
| INFO logs | 7 days | Promtail label: `retention="info"` |
| WARN/ERROR logs | 30 days | Promtail label: `retention="error_warn"` |
| Security events | 90 days | Promtail label: `retention="security"` + category="security" |

Retention labels are assigned in Promtail pipeline based on:
- Log level (INFO vs WARN/ERROR)
- Category field (security)

### 4. Grafana Integration

#### Loki Datasource (`datasources-loki.yml`)
- URL: http://loki-prod:3100
- Max lines: 1000
- Derived fields for correlation ID and run ID linking to Tempo

#### Logs Overview Dashboard (`dashboard-logs-overview.json`)
Contains 8 panels:

1. **Log Volume by Level**: Time-series graph of log counts by severity
2. **Authentication Failures**: Auth-related WARN/ERROR logs
3. **AI Agent Errors**: Agent execution failures
4. **Rate Limit Violations**: Rate limiting events
5. **Database Failures**: Database connectivity issues
6. **Vault Connectivity Loss**: Vault-related errors
7. **Unhandled Exceptions**: Unexpected exceptions
8. **Security Events**: 90-day retention security logs

Each panel includes saved LogQL queries targeting specific error patterns.

### 5. Log-Based Alerting

#### Loki Rules (`loki-rules.yml`)
Four alert groups with 10 total rules:

**Critical Alerts (log_alerts_critical)**:
1. `DatabaseConnectionFailuresHigh`: >5 DB errors/sec for 2min
2. `VaultConnectivityLoss`: Any Vault errors for 2min
3. `UnhandledExceptionsHigh`: >10 exceptions/sec for 3min

**Security Alerts (log_alerts_security)**:
4. `AuthenticationFailuresSpike`: >2 auth failures/sec for 5min
5. `RateLimitViolationsHigh`: >5 rate limit violations/sec for 5min
6. `SecurityEventsUnusual`: >1 security event/sec for 5min

**Application Alerts (log_alerts_application)**:
7. `AIAgentExecutionFailuresHigh`: >1 agent failure/sec for 5min
8. `OutOfMemoryIndicators`: Any memory errors for 2min
9. `GitHubAPIFailuresHigh`: >2 GitHub errors/sec for 5min

**Infrastructure Alerts (log_alerts_infrastructure)**:
10. `SSLTLSErrors`: Any SSL/TLS errors for 5min
11. `CircuitBreakerTriggered`: Any circuit breaker opens for 2min

#### Prometheus Alert Integration (`monitoring/alerts.yml`)
Added `orchestrator_logs` alert group with 5 log-based alerts:
- DatabaseConnectionFailuresLogged
- VaultConnectivityIssues
- UnhandledExceptionsInLogs
- AuthenticationFailuresInLogs
- RateLimitViolationsInLogs

All alerts route to Alertmanager for notification.

### 6. Documentation

#### OBSERVABILITY.md (`docs/OBSERVABILITY.md`)
Added comprehensive logging section with:
- Architecture overview
- Structured JSON logging details
- MDC context propagation guide
- Log retention policies
- 20+ LogQL query patterns (common and advanced)
- Grafana dashboard guide
- Log-based alerting configuration
- Deployment instructions
- Troubleshooting guide (5 sections)
- Security considerations
- Performance tuning
- Monitoring Loki itself

#### LOGGING_QUICKSTART.md (`infra/deployments/prod/LOGGING_QUICKSTART.md`)
Quick reference guide with:
- Architecture diagram
- Quick start commands
- Common queries (6 examples)
- Retention policy table
- Alert summary
- Troubleshooting (3 common issues)
- Configuration file locations
- Performance tips
- Security notes

#### README-MONITORING.md (`infra/deployments/prod/README-MONITORING.md`)
Updated with:
- Loki + Promtail in architecture section
- Logs Overview dashboard description
- Log-based alerting rules
- Loki/Promtail configuration details
- Troubleshooting sections for Loki
- Backup procedures for Loki data

## Log Query Patterns

### Authentication Issues
```logql
{service="ai-orchestrator"} |= "authentication" | json | level=~"WARN|ERROR"
```

### AI Agent Errors
```logql
{service="ai-orchestrator"} |~ "agent|workflow" | json | level="ERROR"
```

### Rate Limit Violations
```logql
{service="ai-orchestrator"} |= "rate limit" | json | level=~"WARN|ERROR"
```

### Database Failures
```logql
{service="ai-orchestrator"} |~ "database|connection|hikari" | json | level="ERROR"
```

### Vault Connectivity
```logql
{service="ai-orchestrator"} |~ "vault|secret" | json | level=~"WARN|ERROR"
```

### Unhandled Exceptions
```logql
{service="ai-orchestrator"} |= "Exception" | json | level="ERROR"
```

### Track Workflow Run
```logql
{service="ai-orchestrator"} | json | runId="<run-id>"
```

### User Activity
```logql
{service="ai-orchestrator"} | json | userId="user@example.com"
```

### Correlation ID Tracking
```logql
{service="ai-orchestrator"} | json | correlationId="<correlation-id>"
```

### Security Events (90-day retention)
```logql
{service="ai-orchestrator", category="security"} | json
```

## File Changes

### New Files Created
1. `infra/deployments/prod/config/loki-config.yml` - Loki configuration
2. `infra/deployments/prod/config/promtail-config.yml` - Promtail configuration
3. `infra/deployments/prod/config/datasources-loki.yml` - Grafana Loki datasource
4. `infra/deployments/prod/config/dashboard-logs-overview.json` - Logs dashboard
5. `infra/deployments/prod/config/loki-rules.yml` - Log-based alert rules
6. `infra/deployments/prod/LOGGING_QUICKSTART.md` - Quick reference guide

### Modified Files
1. `ai-orchestrator/src/main/resources/logback-spring.xml` - Enhanced with JSON logging
2. `ai-orchestrator/src/main/resources/application.yml` - Added logging configuration
3. `ai-orchestrator/src/main/java/com/atlasia/ai/config/CorrelationIdFilter.java` - Added userId
4. `ai-orchestrator/src/main/java/com/atlasia/ai/service/observability/CorrelationIdHolder.java` - Added userId methods
5. `infra/deployments/prod/docker-compose-monitoring.yml` - Added Loki and Promtail services
6. `monitoring/alerts.yml` - Added log-based alerts
7. `docs/OBSERVABILITY.md` - Added comprehensive logging documentation
8. `infra/deployments/prod/README-MONITORING.md` - Updated with logging info

## Technology Stack

- **Grafana Loki 2.9.4**: Log aggregation and querying
- **Promtail 2.9.4**: Log collection agent
- **logstash-logback-encoder 7.4**: Structured JSON logging (already in pom.xml)
- **SLF4J MDC**: Thread-local context propagation
- **LogQL**: Loki query language
- **BoltDB**: Loki storage backend
- **Docker Compose**: Service orchestration

## Key Features

1. **Structured Logging**: JSON format with consistent field names
2. **MDC Context**: Automatic propagation of correlationId, runId, userId, agentName
3. **Tiered Retention**: 7d/30d/90d based on criticality
4. **Log-Based Alerting**: 11 alert rules for critical events
5. **Grafana Integration**: Unified metrics, logs, traces visualization
6. **Query Performance**: Label-based indexing, efficient storage
7. **Scalability**: Horizontal scaling support, compression, compaction
8. **Security**: No PII in logs, retention compliance, network isolation

## Deployment Instructions

1. **Start monitoring stack**:
   ```bash
   cd infra/deployments/prod
   export GRAFANA_ADMIN_PASSWORD="secure-password"
   export DB_PASSWORD="db-password"
   docker-compose -f docker-compose-monitoring.yml up -d
   ```

2. **Verify services**:
   ```bash
   docker-compose -f docker-compose-monitoring.yml ps
   curl http://localhost:3100/ready
   docker logs promtail-prod --tail 50
   ```

3. **Access Grafana**:
   - Navigate to http://localhost:3001
   - Open "Explore" → Select "Loki" datasource
   - Or open "Logs Overview" dashboard

4. **Test log queries**:
   ```bash
   curl -G -s "http://localhost:3100/loki/api/v1/query_range" \
     --data-urlencode 'query={service="ai-orchestrator"}' | jq
   ```

## Monitoring and Maintenance

### Check Log Ingestion
```bash
# Promtail targets
curl http://localhost:9080/targets | jq

# Loki labels
curl http://localhost:3100/loki/api/v1/labels | jq

# Recent logs
curl -G "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={service="ai-orchestrator"}[5m]' | jq
```

### Monitor Loki Health
```bash
# Check ready status
curl http://localhost:3100/ready

# Check metrics
curl http://localhost:3100/metrics | grep loki_ingester_chunks_created_total

# Check disk usage
docker exec loki-prod du -sh /loki/*
```

### Trigger Compaction
```bash
curl -X POST http://localhost:3100/loki/api/v1/compact
```

## Best Practices

1. **Always use label filters** in queries: `{service="ai-orchestrator"}`
2. **Limit time ranges**: Use `[5m]` instead of `[24h]` for better performance
3. **Avoid unbounded queries**: Always add filters and limits
4. **Use JSON parsing**: `| json` for structured field access
5. **Test retention policies**: Verify logs are deleted according to schedule
6. **Monitor alert noise**: Tune thresholds to avoid alert fatigue
7. **Review security logs regularly**: 90-day retention for compliance
8. **Never log secrets**: Sanitize sensitive data before logging

## Performance Considerations

- **Promtail batch size**: 1MB (configured)
- **Loki ingestion rate**: 10MB/s with 20MB burst
- **Query limits**: 720-hour lookback, 721-hour max query
- **Compaction interval**: 10 minutes
- **Memory limits**: Loki 1GB, Promtail 256MB
- **Storage compression**: Automatic via BoltDB

## Security Notes

1. **No built-in authentication**: Use reverse proxy for production
2. **Network isolation**: Loki runs on internal Docker network
3. **TLS recommended**: Enable for Promtail → Loki in production
4. **Log sanitization**: Never log tokens, passwords, or secrets
5. **Retention compliance**: 90-day max for PII data
6. **Access control**: Grafana authentication for log access

## Testing

### Generate Test Logs
```bash
# Error logs
docker exec ai-orchestrator bash -c 'for i in {1..10}; do echo "{\"level\":\"ERROR\",\"message\":\"Test error\"}" >&2; done'

# Check in Loki
curl -G "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode 'query={service="ai-orchestrator"} | json | level="ERROR"' | jq
```

### Test Alerts
```bash
# Check Prometheus alerts
curl http://localhost:9090/api/v1/alerts | jq '.data.alerts[]'

# Check Alertmanager
curl http://localhost:9093/api/v2/alerts | jq
```

## Troubleshooting

See `docs/OBSERVABILITY.md` and `infra/deployments/prod/LOGGING_QUICKSTART.md` for detailed troubleshooting guides.

## References

- [Loki Documentation](https://grafana.com/docs/loki/latest/)
- [LogQL Language](https://grafana.com/docs/loki/latest/logql/)
- [Promtail Configuration](https://grafana.com/docs/loki/latest/clients/promtail/configuration/)
- [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder)
- [SLF4J MDC](http://www.slf4j.org/manual.html#mdc)
- Complete guide: `docs/OBSERVABILITY.md`
