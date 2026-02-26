# Backup & Rollback Scripts

Comprehensive backup and rollback scripts for Atlasia with retention policies and blue-green deployment support.

## 📁 Files

- **`backup.sh`** - PostgreSQL backup script with retention policy
- **`rollback.sh`** - Blue-green deployment rollback script
- **`backup-cron.sh`** - Scheduled backup wrapper for cron (in `../deployments/prod/`)
- **`setup-permissions.sh`** - Makes all scripts executable
- **`.env.backup.example`** - Example environment configuration

## 🚀 Quick Start

### 1. Setup Permissions

```bash
# Make scripts executable
chmod +x infra/ci-cd/scripts/setup-permissions.sh
./infra/ci-cd/scripts/setup-permissions.sh
```

### 2. Run Manual Backup

```bash
# Production backup
./infra/ci-cd/scripts/backup.sh prod

# Development backup
./infra/ci-cd/scripts/backup.sh dev
```

### 3. Setup Scheduled Backups

```bash
# On production server, add to crontab
crontab -e

# Daily backup at 2:00 AM
0 2 * * * /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1
```

### 4. Rollback if Needed

```bash
# Rollback to previous version
./infra/ci-cd/scripts/rollback.sh prod

# Rollback to specific version
ROLLBACK_TAG=v1.2.3 ./infra/ci-cd/scripts/rollback.sh prod
```

## 📖 Detailed Documentation

### backup.sh

PostgreSQL backup script with automated retention policy management.

**Features:**
- Daily, weekly, and monthly backups with different retention periods
- Backup integrity verification (gzip test)
- Optional S3 upload for remote storage
- Detailed backup reports with MD5 checksums
- Schema-only backups for documentation
- Automatic cleanup of old backups

**Retention Policy:**
- Daily backups: 7 days
- Weekly backups: 4 weeks (created on Sundays)
- Monthly backups: Indefinite (created on 1st of month)

**Usage:**
```bash
# Basic usage
./backup.sh [dev|prod]

# With environment variables
BACKUP_DIR=/custom/path ./backup.sh prod
S3_BUCKET=my-bucket ./backup.sh prod
DB_CONTAINER=custom_db ./backup.sh prod
```

**Environment Variables:**
- `BACKUP_DIR` - Base backup directory (default: ./backups)
- `DB_CONTAINER` - Database container name (auto-detected)
- `DB_NAME` - Database name (auto-detected)
- `DB_USER` - Database user (auto-detected)
- `S3_BUCKET` - S3 bucket for remote backup (optional)

**Output Structure:**
```
backups/
├── daily/
│   ├── backup_20260226_020000.sql.gz
│   ├── backup_20260226_020000.report.txt
│   └── schema_20260226_020000.sql
├── weekly/
│   └── backup_20260223_020000.sql.gz
└── monthly/
    └── backup_20260201_020000.sql.gz
```

**Backup Report Example:**
```
════════════════════════════════════════════════════════════════
ATLASIA DATABASE BACKUP REPORT
════════════════════════════════════════════════════════════════

Backup Information:
  - Timestamp: 2026-02-26 02:00:00 UTC
  - Environment: prod
  - Database: aiteam_prod
  - Container: aiteam_db_prod
  
Backup File:
  - Path: /opt/atlasia/backups/daily/backup_20260226_020000.sql.gz
  - Size: 15M
  - MD5: abc123def456...

Database Statistics:
  [Table statistics...]

Retention Status:
  - Daily backups: 7 files
  - Weekly backups: 4 files
  - Monthly backups: 12 files
```

**Exit Codes:**
- `0` - Success
- `1` - Database container not running, backup failed, or integrity check failed

---

### rollback.sh

Blue-green deployment rollback script with automatic backup and health verification.

**Features:**
- Detects current deployment and determines rollback target
- Creates pre-rollback database backup
- Pulls previous Docker images from GHCR
- Tracks deployment history for automatic rollback
- Graceful service shutdown (30s timeout)
- Health check verification after rollback
- Support for manual tag specification

**Usage:**
```bash
# Basic rollback to previous version
./rollback.sh [dev|prod]

# Rollback to specific tag
ROLLBACK_TAG=v1.2.3 ./rollback.sh prod

# Emergency rollback without backup
./rollback.sh prod --skip-backup

# With custom GitHub repo
GITHUB_REPO=myorg/myrepo ./rollback.sh prod
```

**Environment Variables:**
- `ROLLBACK_TAG` - Specific Docker image tag (default: uses history)
- `BACKUP_DIR` - Backup directory (default: ./backups)
- `GITHUB_REPO` - GitHub repository for GHCR images

**Rollback Process:**
1. Detect current deployment (Docker image tags)
2. Create pre-rollback database backup
3. Determine rollback target from deployment history or ROLLBACK_TAG
4. Pull rollback Docker images from GHCR
5. Stop current services gracefully (30s timeout)
6. Start services with previous image tags
7. Wait for health checks to pass
8. Verify all services are running with correct tags

**Deployment History:**

The script maintains a deployment history file for automatic rollback:
```
backups/rollback/deployment_tags.log

Format: timestamp,backend_tag,frontend_tag
20260226_143022,latest,latest
20260226_150000,v1.2.3,v1.2.3
20260226_163045,v1.2.4,v1.2.4
```

**Confirmation:**

For production rollbacks, you must type `ROLLBACK` to confirm:
```
═══════════════════════════════════════════════════════
PRODUCTION ROLLBACK - THIS ACTION IS CRITICAL
═══════════════════════════════════════════════════════

This will:
  1. Create a pre-rollback database backup
  2. Stop current services
  3. Rollback to previous Docker image tags
  4. Restore database from backup (if available)
  5. Start services with rolled back configuration

Type 'ROLLBACK' to confirm:
```

**Exit Codes:**
- `0` - Rollback successful
- `1` - Rollback completed with warnings (some services unhealthy)

---

### backup-cron.sh

Wrapper script for scheduled backups via cron with monitoring integration.

**Location:** `infra/deployments/prod/backup-cron.sh`

**Features:**
- Calls the main backup.sh script
- Pre-flight checks (script exists, is executable, disk space)
- Optional healthcheck.io integration
- Optional email notifications
- Disk space verification (1GB minimum)
- Detailed logging

**Cron Installation:**
```bash
# Edit crontab
crontab -e

# Add one of these lines:

# Daily backup at 2:00 AM
0 2 * * * /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1

# Every 6 hours
0 */6 * * * /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1

# Weekly on Sunday at 3:00 AM
0 3 * * 0 /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1
```

**Environment Variables:**
- `DEPLOY_DIR` - Deployment directory (default: /opt/atlasia)
- `BACKUP_DIR` - Backup directory (default: /opt/atlasia/backups)
- `S3_BUCKET` - S3 bucket for remote backup (optional)
- `HEALTHCHECK_URL` - Healthcheck.io URL to ping on success (optional)
- `ADMIN_EMAIL` - Email for notifications (optional, requires mail command)
- `LOG_FILE` - Log file path (default: /var/log/atlasia-backup.log)

**Healthcheck.io Integration:**
```bash
# Sign up at https://healthchecks.io/
# Get your unique ping URL
export HEALTHCHECK_URL=https://hc-ping.com/your-unique-id

# Add to crontab with environment variable
crontab -e

# Add before cron job:
HEALTHCHECK_URL=https://hc-ping.com/your-unique-id
0 2 * * * /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1
```

**Email Notifications:**
```bash
# Requires 'mail' command installed
sudo apt-get install mailutils  # Debian/Ubuntu
sudo yum install mailx          # RHEL/CentOS

# Set admin email
export ADMIN_EMAIL=admin@example.com

# Notifications sent on:
# - Backup success
# - Backup failure
# - Disk space issues
# - Script not found
```

---

## 🔧 Configuration

### Using .env.backup

Create a configuration file for your environment:

```bash
# 1. Copy example file
cp infra/ci-cd/scripts/.env.backup.example infra/ci-cd/scripts/.env.backup

# 2. Edit with your values
nano infra/ci-cd/scripts/.env.backup

# 3. Source before running scripts
source infra/ci-cd/scripts/.env.backup
./infra/ci-cd/scripts/backup.sh prod
```

### Example Configuration

```bash
# Backup configuration
BACKUP_DIR=/opt/atlasia/backups
DB_CONTAINER=aiteam_db_prod
DB_NAME=aiteam_prod
DB_USER=aiteam_prod_user
S3_BUCKET=my-backup-bucket

# Rollback configuration
GITHUB_REPO=myorg/atlasia
ROLLBACK_TAG=

# Cron configuration
DEPLOY_DIR=/opt/atlasia
HEALTHCHECK_URL=https://hc-ping.com/your-unique-id
ADMIN_EMAIL=admin@example.com
LOG_FILE=/var/log/atlasia-backup.log
```

---

## 📊 Monitoring & Alerts

### View Backup Status

```bash
# List all backups
ls -lh /opt/atlasia/backups/daily/
ls -lh /opt/atlasia/backups/weekly/
ls -lh /opt/atlasia/backups/monthly/

# View latest backup report
cat $(ls -t /opt/atlasia/backups/daily/*.report.txt | head -1)

# Check backup sizes
du -sh /opt/atlasia/backups/*

# Count backups by type
echo "Daily: $(ls /opt/atlasia/backups/daily/*.sql.gz 2>/dev/null | wc -l)"
echo "Weekly: $(ls /opt/atlasia/backups/weekly/*.sql.gz 2>/dev/null | wc -l)"
echo "Monthly: $(ls /opt/atlasia/backups/monthly/*.sql.gz 2>/dev/null | wc -l)"
```

### View Cron Logs

```bash
# View recent backup logs
tail -f /var/log/atlasia-backup.log

# View last 100 lines
tail -n 100 /var/log/atlasia-backup.log

# Search for errors
grep -i error /var/log/atlasia-backup.log

# Check last backup time
grep "BACKUP COMPLETED" /var/log/atlasia-backup.log | tail -1
```

### Verify Cron Job

```bash
# List cron jobs
crontab -l

# Check cron service status
systemctl status cron      # Debian/Ubuntu
systemctl status crond     # RHEL/CentOS

# View cron logs
grep CRON /var/log/syslog  # Debian/Ubuntu
grep CRON /var/log/cron    # RHEL/CentOS
```

---

## 🆘 Troubleshooting

### Backup Script Issues

**Problem: "Database container not running"**
```bash
# Check if container is running
docker ps | grep db

# Start container
docker-compose -f infra/deployments/prod/docker-compose.yml up -d ai-db
```

**Problem: "Permission denied"**
```bash
# Make script executable
chmod +x infra/ci-cd/scripts/backup.sh

# Or use setup script
./infra/ci-cd/scripts/setup-permissions.sh
```

**Problem: "Backup file is corrupted"**
```bash
# This indicates gzip compression failed
# Check disk space
df -h

# Check file system errors
sudo dmesg | grep -i error
```

**Problem: "S3 upload failed"**
```bash
# Check AWS CLI configuration
aws configure list

# Test S3 access
aws s3 ls s3://my-backup-bucket/

# Check credentials
cat ~/.aws/credentials
```

### Rollback Script Issues

**Problem: "Failed to pull backend image"**
```bash
# Check GITHUB_REPO is set
echo $GITHUB_REPO

# Check GHCR access
docker login ghcr.io

# Verify image exists
docker pull ghcr.io/${GITHUB_REPO}/ai-orchestrator:v1.2.3
```

**Problem: "No deployment history found"**
```bash
# Specify tag manually
ROLLBACK_TAG=v1.2.3 ./rollback.sh prod

# Or check history file exists
cat backups/rollback/deployment_tags.log
```

**Problem: "Health check timeout"**
```bash
# Check service logs
docker-compose -f infra/deployments/prod/docker-compose.yml logs ai-orchestrator

# Check if port is blocked
sudo netstat -tulpn | grep 8080

# Increase health check timeout (edit script)
local max_attempts=60  # Default is 30
```

### Cron Job Issues

**Problem: "Cron job not running"**
```bash
# Check cron service
systemctl status cron

# Check crontab syntax
crontab -l

# Test script manually
/opt/atlasia/infra/deployments/prod/backup-cron.sh
```

**Problem: "No logs in /var/log/atlasia-backup.log"**
```bash
# Check file permissions
ls -l /var/log/atlasia-backup.log

# Create log file if missing
sudo touch /var/log/atlasia-backup.log
sudo chown $USER:$USER /var/log/atlasia-backup.log

# Check cron is writing to file
grep backup-cron /var/log/syslog
```

**Problem: "Email notifications not working"**
```bash
# Check mail command exists
which mail

# Install if missing
sudo apt-get install mailutils

# Test mail command
echo "Test" | mail -s "Test" admin@example.com

# Check mail logs
sudo tail -f /var/log/mail.log
```

---

## 🔐 Security Considerations

### Backup Security

1. **Encrypt backups at rest:**
   ```bash
   # Encrypt backup with GPG
   gpg --encrypt --recipient admin@example.com backup.sql.gz
   
   # Decrypt
   gpg --decrypt backup.sql.gz.gpg > backup.sql.gz
   ```

2. **Restrict backup directory permissions:**
   ```bash
   chmod 700 /opt/atlasia/backups
   chown -R deploy:deploy /opt/atlasia/backups
   ```

3. **Use S3 encryption:**
   ```bash
   # Enable S3 server-side encryption
   aws s3 cp backup.sql.gz s3://bucket/ --sse AES256
   ```

### Database Security

1. **Use read-only user for backups:**
   ```sql
   CREATE ROLE backup_user WITH LOGIN PASSWORD 'secure_password';
   GRANT CONNECT ON DATABASE aiteam_prod TO backup_user;
   GRANT SELECT ON ALL TABLES IN SCHEMA public TO backup_user;
   ```

2. **Restrict pg_dump access:**
   ```bash
   # Use .pgpass file for secure password storage
   echo "localhost:5432:aiteam_prod:backup_user:password" > ~/.pgpass
   chmod 600 ~/.pgpass
   ```

### Rollback Security

1. **Require confirmation for production:**
   - The script always asks for `ROLLBACK` confirmation in production

2. **Audit rollback actions:**
   ```bash
   # Log all rollback attempts
   echo "$(date): Rollback initiated by $USER" >> /var/log/atlasia-rollback-audit.log
   ```

3. **Test rollback in staging:**
   ```bash
   # Always test rollback in dev/staging first
   ./rollback.sh dev
   ```

---

## 📚 Related Documentation

- [CI/CD README](../README.md) - Full CI/CD documentation
- [Container Security](../../docs/CONTAINER_SECURITY.md) - Security hardening
- [Vault Setup](../../docs/VAULT_SETUP.md) - Secrets management
- [Multi-Repo Orchestration](../../docs/MULTI_REPO_ORCHESTRATION.md) - Cross-repo workflows

---

**For questions or issues, check the main [CI/CD README](../README.md) or contact the team.**
