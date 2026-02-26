# Disaster Recovery Plan

## Overview

This document outlines the disaster recovery (DR) procedures for the Atlasia AI Orchestrator platform. The DR plan is designed to ensure business continuity and data integrity in the event of system failures, data corruption, or other catastrophic events.

## Recovery Objectives

### RTO (Recovery Time Objective)
**Target: 4 hours**

The maximum acceptable time to restore the system to operational status following a disaster.

### RPO (Recovery Point Objective)
**Target: 24 hours**

The maximum acceptable amount of data loss measured in time. With daily backups, we can restore to within 24 hours of the most recent backup.

## Backup Strategy

### Backup Schedule

| Type | Frequency | Retention | Storage Location |
|------|-----------|-----------|------------------|
| Daily | Every day at 02:00 UTC | 7 days | Local + S3 |
| Weekly | Every Sunday at 02:00 UTC | 4 weeks | Local + S3 |
| Monthly | 1st of month at 02:00 UTC | Indefinite | S3 |

### Backup Components

1. **PostgreSQL Database**
   - Full database dumps (compressed with gzip)
   - Schema-only backups for documentation
   - Includes all tables, indexes, constraints, and data

2. **HashiCorp Vault** (Optional)
   - Raft storage snapshots
   - Contains encrypted secrets and credentials

3. **Application Configuration**
   - Environment files (`.env.prod`, `.env`)
   - Docker Compose configurations
   - Application properties

4. **File System Data**
   - Uploaded files (if using local storage)
   - Plugin artifacts
   - Log files (last 7 days)

### Backup Verification

- Automated integrity checks (gzip test for compressed backups)
- MD5 checksums generated for all backups
- Weekly restore tests to staging environment
- Monthly full DR drills

## Recovery Procedures

### Quick Reference

```bash
# Development environment restore
./infra/ci-cd/scripts/dr-restore.sh dev /path/to/backup.sql.gz

# Production environment restore
./infra/ci-cd/scripts/dr-restore.sh prod /path/to/backup.sql.gz

# Restore from S3
./infra/ci-cd/scripts/dr-restore.sh prod s3://bucket/backup.sql.gz

# Restore with Vault backup
VAULT_BACKUP=/vault/backup.snap ./infra/ci-cd/scripts/dr-restore.sh prod backup.sql.gz
```

### Detailed Recovery Process

#### Phase 1: Assessment (Target: 15 minutes)

1. **Identify the Failure**
   - Determine scope and severity
   - Identify affected components (database, application, infrastructure)
   - Document failure symptoms and error messages

2. **Alert Team**
   - Notify incident commander
   - Assemble DR team (see Roles section)
   - Establish communication channel (Slack, MS Teams, or phone bridge)

3. **Determine Recovery Strategy**
   - Assess if partial recovery is sufficient
   - Identify most recent valid backup
   - Decide between full restoration or targeted fixes

#### Phase 2: Preparation (Target: 30 minutes)

1. **Verify Backup Availability**
   ```bash
   # List available backups
   ls -lh ./backups/daily/
   ls -lh ./backups/weekly/
   
   # Verify backup integrity
   gunzip -t ./backups/daily/backup_latest.sql.gz
   ```

2. **Download Backup from S3 (if needed)**
   ```bash
   aws s3 ls s3://atlasia-backups/
   aws s3 cp s3://atlasia-backups/backup_YYYYMMDD.sql.gz ./backups/
   ```

3. **Prepare Infrastructure**
   - Ensure Docker is running
   - Verify network connectivity
   - Check available disk space (minimum 10GB free)

4. **Create Communication Plan**
   - Notify stakeholders of expected downtime
   - Prepare status page updates
   - Document timeline and milestones

#### Phase 3: Restoration (Target: 2 hours)

1. **Execute DR Restore Script**
   ```bash
   # Production restoration with confirmation
   ./infra/ci-cd/scripts/dr-restore.sh prod ./backups/daily/backup_20240115.sql.gz
   ```

   The script performs:
   - Pre-restore safety backup
   - Service shutdown
   - Database drop and recreation
   - Data restoration from backup
   - Vault restoration (if provided)
   - Service startup
   - Health checks and verification

2. **Monitor Progress**
   - Watch restoration logs in real-time
   - Track elapsed time against RTO
   - Document any errors or warnings

3. **Handle Failures**
   - If restoration fails, analyze logs
   - Try alternative backup if corruption suspected
   - Escalate to senior team if needed

#### Phase 4: Verification (Target: 1 hour)

1. **Automated Smoke Tests**
   The DR script automatically runs:
   - Database connectivity test
   - Health endpoint verification
   - Container status check

2. **Manual Verification**
   ```bash
   # Check service status
   docker-compose -f infra/deployments/prod/docker-compose.yml ps
   
   # Verify database contents
   docker exec aiteam_db_prod psql -U aiteam_prod_user -d aiteam_prod -c "\dt+"
   
   # Test API endpoints
   curl -k https://api.atlasia.ai/api/health/ready
   
   # Verify Vault access
   docker exec aiteam_vault_prod vault status
   ```

3. **Functional Testing**
   - Login to application
   - Create test workflow run
   - Verify workflow execution
   - Check historical data accessibility
   - Test file upload/download
   - Verify webhook delivery

4. **Data Integrity Checks**
   ```sql
   -- Check record counts in key tables
   SELECT 'workflow_runs' as table_name, COUNT(*) as count FROM workflow_runs
   UNION ALL
   SELECT 'users', COUNT(*) FROM users
   UNION ALL
   SELECT 'personas', COUNT(*) FROM personas;
   
   -- Verify latest data timestamps
   SELECT MAX(created_at) as latest_run FROM workflow_runs;
   ```

#### Phase 5: Post-Recovery (Target: 30 minutes)

1. **Update Status**
   - Notify stakeholders of recovery completion
   - Update status page
   - Send recovery summary email

2. **Enable Monitoring**
   - Verify Prometheus metrics collection
   - Check Grafana dashboards
   - Enable alerting rules

3. **Resume Operations**
   - Re-enable cron jobs
   - Resume scheduled workflows
   - Restart any paused integrations

4. **Document Recovery**
   - Record actual RTO achieved
   - Document any issues encountered
   - Update runbook with lessons learned

## Recovery Scenarios

### Scenario 1: Database Corruption

**Symptoms:**
- Database connection errors
- Data inconsistency errors
- PostgreSQL crash loops

**Recovery Steps:**
1. Stop application services
2. Assess database state with `pg_dump` test
3. If corruption confirmed, execute full DR restore
4. Verify data integrity after restoration

**Expected Duration:** 2-3 hours

### Scenario 2: Complete Infrastructure Loss

**Symptoms:**
- All services down
- Infrastructure unavailable (EC2, VM, etc.)

**Recovery Steps:**
1. Provision new infrastructure
2. Install Docker and dependencies
3. Clone repository
4. Retrieve backups from S3
5. Execute DR restore script
6. Reconfigure DNS/Load balancer

**Expected Duration:** 3-4 hours

### Scenario 3: Vault Seal/Corruption

**Symptoms:**
- Vault sealed unexpectedly
- Secrets inaccessible
- Authentication failures

**Recovery Steps:**
1. Attempt to unseal Vault with unseal keys
2. If unseal fails, restore from Vault snapshot
3. Re-initialize if snapshot unavailable (requires key rotation)
4. Update application with new secrets

**Expected Duration:** 1-2 hours

### Scenario 4: Accidental Data Deletion

**Symptoms:**
- User reports missing data
- Tables/records deleted accidentally

**Recovery Steps:**
1. Identify time window of deletion
2. Create current database snapshot (safety)
3. Restore to temporary database from backup
4. Extract missing data via SQL queries
5. Import missing data to production database

**Expected Duration:** 1-2 hours

### Scenario 5: Ransomware/Security Incident

**Symptoms:**
- Encrypted files
- Unauthorized access detected
- Data exfiltration

**Recovery Steps:**
1. **Immediate Actions:**
   - Isolate affected systems
   - Disable external access
   - Preserve evidence
   
2. **Recovery:**
   - Build new clean infrastructure
   - Restore from verified clean backup
   - Rotate all secrets and credentials
   - Reset user passwords
   
3. **Post-Recovery:**
   - Security audit
   - Incident report
   - Strengthen security measures

**Expected Duration:** 4-8 hours

## Roles and Responsibilities

### Incident Commander
- Overall responsibility for DR process
- Decision-making authority
- Communication with stakeholders
- Resource allocation

### Database Administrator
- Database restoration
- Data integrity verification
- Performance optimization post-recovery

### DevOps Engineer
- Infrastructure provisioning
- Service deployment
- Container orchestration
- Network configuration

### Security Engineer
- Access control restoration
- Secret management
- Security verification
- Incident analysis (if security-related)

### QA Engineer
- Functional testing
- Data validation
- Smoke test execution
- User acceptance verification

## Communication Plan

### Internal Communication

1. **Slack Channels:**
   - `#incidents` - Primary incident coordination
   - `#engineering` - Technical discussion
   - `#leadership` - Executive updates

2. **Update Frequency:**
   - Every 30 minutes during active recovery
   - Every hour during verification phase
   - Final summary upon completion

### External Communication

1. **Status Page:**
   - Update at start of incident
   - Hourly updates during recovery
   - Final update upon resolution

2. **Customer Notifications:**
   - Initial notification within 15 minutes
   - Updates every 2 hours
   - Post-mortem within 24 hours

## Testing and Validation

### Monthly DR Drills

1. **Partial Restore Test (Monthly)**
   - Restore backup to staging environment
   - Verify data integrity
   - Test application functionality
   - Document issues and improvements

2. **Full DR Simulation (Quarterly)**
   - Complete infrastructure rebuild
   - Full restoration from backups
   - End-to-end functional testing
   - Measure actual RTO/RPO

### Test Checklist

- [ ] Backup integrity verification
- [ ] Database restoration
- [ ] Vault restoration (if applicable)
- [ ] Application startup
- [ ] Health checks passing
- [ ] User authentication
- [ ] Workflow execution
- [ ] API endpoint testing
- [ ] File upload/download
- [ ] Webhook delivery
- [ ] Monitoring/alerting

## Backup Management

### Creating Manual Backups

```bash
# Development backup
./infra/ci-cd/scripts/backup.sh dev

# Production backup
./infra/ci-cd/scripts/backup.sh prod

# Backup with S3 upload
S3_BUCKET=atlasia-backups ./infra/ci-cd/scripts/backup.sh prod
```

### Backup Locations

**Local Storage:**
- `./backups/daily/` - Daily backups (7 days retention)
- `./backups/weekly/` - Weekly backups (4 weeks retention)
- `./backups/monthly/` - Monthly backups (indefinite retention)

**Remote Storage (S3):**
- `s3://atlasia-backups/atlasia-backups/backup_YYYYMMDD_HHMMSS.sql.gz`
- Storage class: STANDARD_IA (Infrequent Access)
- Versioning: Enabled
- Encryption: AES-256

### Backup Verification

```bash
# Verify backup integrity
gunzip -t ./backups/daily/backup_20240115.sql.gz

# Check backup size
du -h ./backups/daily/backup_20240115.sql.gz

# Verify backup contents (without restoring)
zcat ./backups/daily/backup_20240115.sql.gz | head -n 100

# List tables in backup
zcat ./backups/daily/backup_20240115.sql.gz | grep "CREATE TABLE"
```

## Monitoring and Alerting

### Critical Alerts

1. **Backup Failure**
   - Alert: Slack + PagerDuty
   - Severity: High
   - Response: Within 1 hour

2. **Database Unavailable**
   - Alert: Slack + PagerDuty + SMS
   - Severity: Critical
   - Response: Immediate

3. **Disk Space Low (<10%)**
   - Alert: Slack + Email
   - Severity: Warning
   - Response: Within 4 hours

### Health Monitoring

```bash
# Check system health
curl -f https://api.atlasia.ai/api/health/ready

# Monitor backup age
find ./backups/daily/ -name "*.sql.gz" -mtime +1

# Check disk space
df -h | grep -E '(Filesystem|/var/lib/docker)'
```

## Dependencies and Prerequisites

### Required Tools

- Docker (version 20.10+)
- Docker Compose (version 2.0+)
- AWS CLI (for S3 backups)
- curl (for health checks)
- PostgreSQL client (for verification)

### Access Requirements

- Docker host access (SSH or console)
- AWS credentials (for S3 access)
- Vault unseal keys (for Vault restoration)
- Database credentials
- GitHub access (for code deployment)

### Infrastructure Requirements

- Minimum 10GB free disk space
- Network connectivity to Docker Hub/GHCR
- Network connectivity to S3 (if using remote backups)
- Network connectivity to Vault (if applicable)

## Continuous Improvement

### Post-Incident Review

After each disaster recovery event:

1. **Metrics Collection:**
   - Actual RTO achieved
   - Actual RPO achieved
   - Issues encountered
   - Resolution time per phase

2. **Lessons Learned:**
   - What went well
   - What could be improved
   - Process gaps identified
   - Tool/script enhancements needed

3. **Action Items:**
   - Update documentation
   - Enhance automation
   - Schedule training
   - Improve monitoring

### Quarterly Reviews

- Review and update recovery procedures
- Test new scenarios
- Update contact information
- Review backup retention policies
- Assess RTO/RPO targets

## Appendix

### A. Emergency Contacts

| Role | Name | Phone | Email |
|------|------|-------|-------|
| Incident Commander | TBD | TBD | TBD |
| Database Admin | TBD | TBD | TBD |
| DevOps Lead | TBD | TBD | TBD |
| Security Lead | TBD | TBD | TBD |

### B. Key Commands Reference

```bash
# Check backup status
ls -lh ./backups/daily/ | tail -n 5

# Download latest backup from S3
aws s3 cp s3://atlasia-backups/$(aws s3 ls s3://atlasia-backups/ | sort | tail -n 1 | awk '{print $4}') ./backups/

# Quick database restore (development)
zcat backup.sql.gz | docker exec -i ai-db psql -U ai ai

# Check service health
docker-compose ps
curl http://localhost:8080/api/health/ready

# View recent logs
docker-compose logs --tail=100 -f
```

### C. Backup File Naming Convention

```
backup_<DATE>_<TIMESTAMP>.sql.gz
schema_<DATE>_<TIMESTAMP>.sql
prerollback_<TIMESTAMP>.sql.gz
prerestore_<TIMESTAMP>.sql.gz
```

Example: `backup_20240115_143052.sql.gz`

### D. Related Documentation

- [RUNBOOK.md](./RUNBOOK.md) - Production incident procedures
- [SECURITY.md](./SECURITY.md) - Security incident response
- [CONTAINER_SECURITY.md](./CONTAINER_SECURITY.md) - Container hardening
- [MULTI_REGION_DEPLOYMENT.md](./MULTI_REGION_DEPLOYMENT.md) - High availability setup

---

**Document Version:** 1.0  
**Last Updated:** 2024-01-15  
**Next Review Date:** 2024-04-15  
**Owner:** DevOps Team
