# Production Runbook

## Overview

This runbook provides step-by-step procedures for diagnosing and resolving common production incidents in the Atlasia AI Orchestrator platform. Follow these procedures to minimize downtime and restore service quickly.

## Incident Response Process

### 1. Detection
- Monitor alerts from Prometheus/Grafana
- Health check failures (`/api/health/ready`)
- User reports
- Automated monitoring systems

### 2. Initial Response (Target: 5 minutes)
1. Acknowledge the incident
2. Assess severity (see Severity Levels)
3. Alert appropriate team members
4. Begin incident log

### 3. Investigation
1. Review symptoms and error messages
2. Check system health dashboard
3. Examine logs
4. Identify root cause

### 4. Mitigation
1. Apply appropriate fix from runbook
2. Monitor for improvement
3. Verify resolution

### 5. Recovery
1. Restore normal operations
2. Update status page
3. Notify stakeholders

### 6. Post-Mortem
1. Document incident
2. Identify improvements
3. Update runbook

## Severity Levels

| Level | Description | Response Time | Escalation |
|-------|-------------|---------------|------------|
| **SEV1** | Complete outage, data loss risk | Immediate | Page on-call + manager |
| **SEV2** | Major functionality impaired | 15 minutes | Page on-call team |
| **SEV3** | Minor functionality impaired | 1 hour | Alert team via Slack |
| **SEV4** | Cosmetic issues, no user impact | Next business day | Create ticket |

## Common Production Incidents

---

## Incident 1: Database Connection Exhaustion

### Symptoms
- Errors: `FATAL: sorry, too many clients already`
- Application unable to acquire database connections
- Slow API responses or timeouts
- Health check failures on database connectivity

### Root Causes
- Connection pool misconfiguration
- Connection leaks (not properly closed)
- Sudden traffic spike
- Long-running transactions blocking connections

### Diagnosis Steps

1. **Check current connection count**
   ```bash
   docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c \
     "SELECT count(*) as connections FROM pg_stat_activity WHERE datname = 'aiteam_prod';"
   ```

2. **Identify connection sources**
   ```bash
   docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c \
     "SELECT client_addr, state, count(*) 
      FROM pg_stat_activity 
      WHERE datname = 'aiteam_prod' 
      GROUP BY client_addr, state 
      ORDER BY count(*) DESC;"
   ```

3. **Find long-running queries**
   ```bash
   docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c \
     "SELECT pid, now() - pg_stat_activity.query_start AS duration, query, state
      FROM pg_stat_activity
      WHERE (now() - pg_stat_activity.query_start) > interval '5 minutes'
      AND state != 'idle'
      ORDER BY duration DESC;"
   ```

4. **Check idle connections**
   ```bash
   docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c \
     "SELECT count(*) as idle_connections 
      FROM pg_stat_activity 
      WHERE state = 'idle' AND datname = 'aiteam_prod';"
   ```

### Resolution Steps

#### Quick Fix (Immediate Relief)

1. **Terminate idle connections**
   ```bash
   docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c \
     "SELECT pg_terminate_backend(pid) 
      FROM pg_stat_activity 
      WHERE datname = 'aiteam_prod' 
      AND state = 'idle' 
      AND state_change < current_timestamp - INTERVAL '10 minutes';"
   ```

2. **Kill long-running queries (if safe)**
   ```bash
   # Review query first, then kill if necessary
   docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c \
     "SELECT pg_terminate_backend(<PID>);"
   ```

3. **Restart application containers** (releases all connections)
   ```bash
   docker restart aiteam_backend_prod
   ```

#### Long-term Fix

1. **Adjust connection pool settings in `application.yml`**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20
         minimum-idle: 5
         connection-timeout: 30000
         idle-timeout: 600000
         max-lifetime: 1800000
   ```

2. **Increase PostgreSQL max_connections**
   ```bash
   # Edit postgresql.conf
   docker exec aiteam_db_prod bash -c \
     "echo 'max_connections = 200' >> /var/lib/postgresql/data/postgresql.conf"
   
   # Restart database
   docker restart aiteam_db_prod
   ```

3. **Review code for connection leaks**
   - Ensure all JDBC operations use try-with-resources
   - Check for unclosed ResultSets and Statements
   - Use Spring's JdbcTemplate which handles cleanup automatically

### Prevention
- Monitor connection pool metrics in Grafana
- Set up alerts for > 80% connection pool utilization
- Implement connection timeout policies
- Regular code reviews for connection handling
- Load testing to identify connection requirements

### Related Metrics
- `hikari_connections_active`
- `hikari_connections_idle`
- `hikari_connections_pending`
- PostgreSQL `pg_stat_activity` table

---

## Incident 2: Out of Memory (OOM) Errors

### Symptoms
- Container restarts unexpectedly
- `java.lang.OutOfMemoryError` in logs
- JVM heap space errors
- Application becomes unresponsive before crash
- Docker container shows "OOMKilled" status

### Root Causes
- Insufficient JVM heap size
- Memory leaks in application code
- Large dataset processing without streaming
- Unoptimized database queries loading too much data
- Excessive caching
- Metaspace exhaustion

### Diagnosis Steps

1. **Check if container was OOM killed**
   ```bash
   docker inspect aiteam_backend_prod | grep -A 5 OOMKilled
   ```

2. **Review container memory limits**
   ```bash
   docker stats aiteam_backend_prod --no-stream
   ```

3. **Check JVM heap settings**
   ```bash
   docker exec aiteam_backend_prod java -XX:+PrintFlagsFinal -version | grep -E 'HeapSize|MaxHeapSize'
   ```

4. **Analyze heap dump (if available)**
   ```bash
   # Trigger heap dump
   docker exec aiteam_backend_prod jcmd 1 GC.heap_dump /tmp/heap_dump.hprof
   
   # Copy heap dump for analysis
   docker cp aiteam_backend_prod:/tmp/heap_dump.hprof ./heap_dump.hprof
   ```

5. **Review memory metrics in Grafana**
   - JVM heap usage
   - JVM non-heap usage
   - Container memory usage
   - GC frequency and duration

6. **Check logs for memory warnings**
   ```bash
   docker logs aiteam_backend_prod --tail=500 | grep -i "memory\|heap\|oom"
   ```

### Resolution Steps

#### Immediate Fix

1. **Increase container memory limit** (docker-compose.yml)
   ```yaml
   services:
     backend:
       deploy:
         resources:
           limits:
             memory: 4G
           reservations:
             memory: 2G
   ```

2. **Increase JVM heap size** (Dockerfile or docker-compose.yml)
   ```yaml
   environment:
     - JAVA_OPTS=-Xmx2g -Xms1g -XX:MaxMetaspaceSize=512m
   ```

3. **Restart the service**
   ```bash
   docker-compose -f infra/deployments/prod/docker-compose.yml restart backend
   ```

#### Memory Leak Investigation

1. **Enable verbose GC logging**
   ```
   JAVA_OPTS=-Xlog:gc*:file=/logs/gc.log -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/logs/heap_dump.hprof
   ```

2. **Monitor memory over time**
   - Watch for continuously increasing heap usage
   - Check if GC is able to reclaim memory
   - Look for objects that are not being garbage collected

3. **Analyze heap dump with Eclipse MAT**
   - Load heap dump in Eclipse Memory Analyzer
   - Find dominator tree
   - Identify objects consuming most memory
   - Look for memory leak suspects report

#### Long-term Fix

1. **Optimize database queries**
   - Use pagination for large result sets
   - Implement streaming for large data processing
   - Add proper indexes
   - Use projections to fetch only needed fields

2. **Review caching strategy**
   ```java
   // Configure Caffeine cache with size limits
   @Bean
   public CacheManager cacheManager() {
       CaffeineCacheManager cacheManager = new CaffeineCacheManager();
       cacheManager.setCaffeine(Caffeine.newBuilder()
           .maximumSize(1000)
           .expireAfterWrite(10, TimeUnit.MINUTES));
       return cacheManager;
   }
   ```

3. **Implement proper resource cleanup**
   - Use try-with-resources for streams
   - Close database connections properly
   - Clear collections after use
   - Avoid storing large objects in session

4. **Configure garbage collection**
   ```
   JAVA_OPTS=-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:ParallelGCThreads=4
   ```

### Prevention
- Set up memory alerts (>80% usage)
- Regular heap dump analysis
- Load testing with realistic data volumes
- Code reviews for memory-intensive operations
- Monitor GC metrics continuously

### Related Metrics
- `jvm_memory_used_bytes`
- `jvm_memory_max_bytes`
- `jvm_gc_pause_seconds`
- `container_memory_usage_bytes`

---

## Incident 3: LLM API Rate Limits

### Symptoms
- Errors: `429 Too Many Requests`
- Workflows failing at LLM call steps
- Increased API response times
- Logs showing "rate limit" errors
- Workflows stuck in "pending" state

### Root Causes
- Exceeded API rate limit (requests per minute/day)
- Token quota exhausted
- Concurrent request limit reached
- API key throttling
- Traffic spike without rate limiting

### Diagnosis Steps

1. **Check LLM error metrics**
   ```bash
   # View recent LLM errors in logs
   docker logs aiteam_backend_prod --tail=200 | grep -i "llm.*rate.*limit\|429"
   ```

2. **Review Prometheus metrics**
   ```promql
   # Rate limit errors in last hour
   sum(increase(llm_api_errors_total{error_type="RATE_LIMIT"}[1h]))
   
   # LLM requests per minute
   rate(llm_api_calls_total[1m])
   
   # LLM token usage
   sum(increase(llm_tokens_total[1h]))
   ```

3. **Check API provider dashboard**
   - Login to OpenAI/Anthropic/etc. dashboard
   - Review rate limit status
   - Check token usage and quotas
   - Verify billing status

4. **Identify high-volume workflows**
   ```bash
   docker exec aiteam_backend_prod curl -s http://localhost:8080/api/metrics/prometheus | \
     grep llm_calls_by_agent
   ```

### Resolution Steps

#### Immediate Fix

1. **Use fallback LLM endpoint** (if configured)
   ```yaml
   # Verify fallback is configured in application.yml
   atlasia:
     orchestrator:
       llm:
         fallback-endpoint: https://api.deepseek.com/v1
         fallback-api-key: ${LLM_FALLBACK_API_KEY}
   ```

2. **Pause non-critical workflows**
   ```bash
   # Disable scheduled workflows temporarily
   docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c \
     "UPDATE workflow_runs SET status = 'PAUSED' WHERE status = 'PENDING' AND priority < 5;"
   ```

3. **Implement temporary rate limiting**
   ```yaml
   # Update application.yml and restart
   resilience4j:
     ratelimiter:
       instances:
         llm:
           limitForPeriod: 10
           limitRefreshPeriod: 1m
           timeoutDuration: 5s
   ```

4. **Request rate limit increase** (if available)
   - Contact API provider support
   - Request temporary or permanent increase
   - Upgrade API tier if necessary

#### Workload Management

1. **Prioritize critical workflows**
   ```java
   // Implement priority queue for LLM requests
   @Configuration
   public class LlmRateLimitConfig {
       @Bean
       public RateLimiter llmRateLimiter() {
           return RateLimiter.of("llm", RateLimiterConfig.custom()
               .limitForPeriod(50)
               .limitRefreshPeriod(Duration.ofMinutes(1))
               .timeoutDuration(Duration.ofSeconds(30))
               .build());
       }
   }
   ```

2. **Batch similar requests**
   - Combine multiple small LLM calls
   - Use structured output to get multiple responses
   - Cache common LLM responses

3. **Implement exponential backoff**
   ```java
   // Already implemented in LlmService with Retry
   .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
       .filter(this::isTransientError))
   ```

#### Long-term Fix

1. **Optimize prompt sizes**
   - Reduce system prompt length
   - Trim context to essential information
   - Use prompt templates efficiently
   - Remove unnecessary examples

2. **Implement request caching**
   ```java
   @Cacheable(value = "llmResponses", key = "#userPrompt.hashCode()")
   public String generateCompletion(String systemPrompt, String userPrompt) {
       // LLM call
   }
   ```

3. **Use cheaper models for simple tasks**
   ```yaml
   # Configure per-agent model selection
   atlasia:
     orchestrator:
       agents:
         reviewer:
           llm-model: gpt-4o-mini  # Cheaper for reviews
         developer:
           llm-model: gpt-4o       # More powerful for code generation
   ```

4. **Implement queue-based processing**
   - Add LLM requests to queue
   - Process at controlled rate
   - Retry failed requests automatically

5. **Monitor and alert**
   ```yaml
   # Prometheus alert rule
   - alert: LLMRateLimitApproaching
     expr: rate(llm_api_calls_total[5m]) > 40
     for: 5m
     labels:
       severity: warning
     annotations:
       summary: "LLM API rate limit approaching (>40 req/min)"
   ```

### Prevention
- Set up rate limit alerts (>80% of limit)
- Regular review of LLM usage patterns
- Implement request prioritization
- Use multiple API keys for load distribution
- Consider self-hosted LLM for high-volume scenarios

### Related Metrics
- `llm_api_calls_total`
- `llm_api_errors_total{error_type="RATE_LIMIT"}`
- `llm_tokens_total`
- `llm_api_duration_seconds`
- `llm_cost_per_bolt`

---

## Incident 4: Vault Sealed

### Symptoms
- Application cannot start
- Authentication failures
- Error: "Vault is sealed"
- Health check failures on Vault
- Database password unavailable

### Diagnosis Steps

1. **Check Vault status**
   ```bash
   docker exec aiteam_vault_prod vault status
   ```

2. **Check Vault logs**
   ```bash
   docker logs aiteam_vault_prod --tail=100
   ```

### Resolution Steps

1. **Unseal Vault using unseal keys**
   ```bash
   # Requires 3 of 5 unseal keys (threshold depends on init config)
   docker exec aiteam_vault_prod vault operator unseal <KEY_1>
   docker exec aiteam_vault_prod vault operator unseal <KEY_2>
   docker exec aiteam_vault_prod vault operator unseal <KEY_3>
   ```

2. **Verify Vault is unsealed**
   ```bash
   docker exec aiteam_vault_prod vault status
   ```

3. **Restart application services**
   ```bash
   docker restart aiteam_backend_prod
   ```

### Prevention
- Document unseal key locations securely
- Set up auto-unseal with cloud KMS
- Monitor Vault seal status
- Implement Vault HA for resilience

---

## Incident 5: High API Latency

### Symptoms
- API response times > 2 seconds
- User complaints about slowness
- Timeouts in frontend
- Increased queue depths

### Diagnosis Steps

1. **Check API response time metrics**
   ```bash
   docker exec aiteam_backend_prod curl -s http://localhost:8080/api/metrics/prometheus | \
     grep http_server_requests_seconds
   ```

2. **Review slow database queries**
   ```bash
   docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c \
     "SELECT query, mean_exec_time, calls 
      FROM pg_stat_statements 
      ORDER BY mean_exec_time DESC LIMIT 10;"
   ```

3. **Check system resources**
   ```bash
   docker stats --no-stream
   ```

### Resolution Steps

1. **Identify slow endpoints** from logs
2. **Add database indexes** for slow queries
3. **Enable query caching** for frequently accessed data
4. **Optimize N+1 queries** with JOIN or batch loading
5. **Increase connection pool** if exhausted
6. **Scale horizontally** if single instance saturated

---

## Incident 6: Disk Space Full

### Symptoms
- Cannot write to disk
- Database cannot create new files
- Container crashes with I/O errors
- Log rotation fails

### Diagnosis Steps

1. **Check disk usage**
   ```bash
   df -h
   docker system df
   ```

2. **Find large files**
   ```bash
   du -ah /var/lib/docker | sort -rh | head -n 20
   ```

### Resolution Steps

1. **Clean Docker system**
   ```bash
   docker system prune -a -f --volumes
   ```

2. **Remove old logs**
   ```bash
   find /var/log -type f -name "*.log" -mtime +7 -delete
   ```

3. **Remove old backups**
   ```bash
   find ./backups/daily -name "*.sql.gz" -mtime +7 -delete
   ```

4. **Increase disk size** (if cloud VM)

### Prevention
- Set up disk space alerts (<20% free)
- Implement log rotation
- Automate backup cleanup
- Monitor Docker volume usage

---

## Incident 7: WebSocket Connection Failures

### Symptoms
- Real-time collaboration not working
- WebSocket connection errors in frontend
- Stale UI data
- "Connection lost" messages

### Diagnosis Steps

1. **Check WebSocket health**
   ```bash
   docker logs aiteam_backend_prod | grep -i websocket
   ```

2. **Test WebSocket connection**
   ```bash
   curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" \
     http://localhost:8080/ws/runs/test
   ```

### Resolution Steps

1. **Restart backend service**
   ```bash
   docker restart aiteam_backend_prod
   ```

2. **Check reverse proxy WebSocket configuration**
   - Ensure WebSocket headers are forwarded
   - Check connection timeout settings
   - Verify SockJS fallback is enabled

3. **Review connection limits**

### Prevention
- Monitor WebSocket connection count
- Implement connection heartbeat
- Use SockJS for fallback to polling

---

## General Troubleshooting Commands

### Service Status
```bash
# Check all containers
docker-compose -f infra/deployments/prod/docker-compose.yml ps

# Check specific service logs
docker logs aiteam_backend_prod --tail=100 -f

# Check resource usage
docker stats --no-stream

# Test health endpoints
curl -f http://localhost:8080/api/health/ready
```

### Database Operations
```bash
# Connect to database
docker exec -it aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod

# Check database size
docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c \
  "SELECT pg_size_pretty(pg_database_size('aiteam_prod'));"

# Check table sizes
docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c \
  "SELECT schemaname, tablename, pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size 
   FROM pg_tables WHERE schemaname = 'public' ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;"
```

### Application Operations
```bash
# Restart services gracefully
docker-compose -f infra/deployments/prod/docker-compose.yml restart backend

# Force recreate containers
docker-compose -f infra/deployments/prod/docker-compose.yml up -d --force-recreate

# View environment variables
docker exec aiteam_backend_prod env | sort

# Execute Java diagnostic commands
docker exec aiteam_backend_prod jcmd 1 VM.version
docker exec aiteam_backend_prod jcmd 1 GC.heap_info
```

## Escalation Procedures

### When to Escalate

| Condition | Action |
|-----------|--------|
| SEV1 incident not resolved in 30 minutes | Escalate to engineering manager |
| Data loss or corruption | Immediately escalate to CTO |
| Security incident | Immediately escalate to security team |
| Cannot resolve with runbook | Escalate to on-call senior engineer |
| Multiple components failing | Escalate to infrastructure team |

### Escalation Contacts

See [DISASTER_RECOVERY.md](./DISASTER_RECOVERY.md) Appendix A for contact information.

## Useful Links

- [Disaster Recovery Plan](./DISASTER_RECOVERY.md)
- [Security Incident Response](./SECURITY.md)
- [Monitoring Dashboards](./PROMETHEUS_METRICS.md)
- [Container Security](./CONTAINER_SECURITY.md)
- [Multi-Region Deployment](./MULTI_REGION_DEPLOYMENT.md)

## Maintenance Windows

Scheduled maintenance should be performed during these windows:

- **Production**: Saturdays 02:00-06:00 UTC
- **Staging**: Wednesdays 22:00-00:00 UTC
- **Development**: Anytime

## On-Call Rotation

- Review on-call schedule: [PagerDuty/Opsgenie]
- Handoff checklist: Review open incidents, check backup status, review metrics
- Response time: Acknowledge within 5 minutes, respond within 15 minutes

---

**Document Version:** 1.0  
**Last Updated:** 2024-01-15  
**Next Review Date:** 2024-04-15  
**Owner:** Engineering Team
