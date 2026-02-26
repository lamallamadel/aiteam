# Backup & Rollback Quick Reference Card

## 🚀 Quick Start (New Installation)

```bash
# 1. Setup permissions
./infra/ci-cd/scripts/setup-permissions.sh

# 2. Verify setup
./infra/ci-cd/scripts/verify-backup-setup.sh prod

# 3. Test backup
./infra/ci-cd/scripts/backup.sh prod

# 4. Setup cron (on production server)
crontab -e
# Add: 0 2 * * * /opt/atlasia/infra/deployments/prod/backup-cron.sh >> /var/log/atlasia-backup.log 2>&1
```

## 📋 Common Commands

### Backups

```bash
# Manual backup
./infra/ci-cd/scripts/backup.sh prod              # Production
./infra/ci-cd/scripts/backup.sh dev               # Development

# Backup with S3 upload
S3_BUCKET=my-bucket ./infra/ci-cd/scripts/backup.sh prod

# Check backup status
ls -lh backups/daily/                              # Daily backups
ls -lh backups/weekly/                             # Weekly backups
ls -lh backups/monthly/                            # Monthly backups

# Verify backup integrity
gunzip -t backups/daily/backup_20260226_020000.sql.gz

# View backup report
cat backups/daily/backup_20260226_020000.report.txt
```

### Restore

```bash
# Restore from backup
zcat backups/daily/backup_20260226_020000.sql.gz | \
  docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod

# Restore with connection reset (if database in use)
docker exec -i aiteam_db_prod psql -U postgres -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='aiteam_prod';"
zcat backups/daily/backup_20260226_020000.sql.gz | \
  docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod
```

### Rollback

```bash
# Rollback to previous deployment
./infra/ci-cd/scripts/rollback.sh prod

# Rollback to specific version
ROLLBACK_TAG=v1.2.3 ./infra/ci-cd/scripts/rollback.sh prod

# Emergency rollback (skip backup for speed)
./infra/ci-cd/scripts/rollback.sh prod --skip-backup

# View deployment history
cat backups/rollback/deployment_tags.log

# Verify current image tags
docker inspect aiteam_backend_prod --format='{{.Config.Image}}'
docker inspect aiteam_frontend_prod --format='{{.Config.Image}}'
```

## 🔍 Troubleshooting

### Backup Failed

```bash
# Check disk space
df -h

# Check database is running
docker ps | grep db

# Check database connectivity
docker exec aiteam_db_prod pg_isready -U aiteam_prod_user

# Check script permissions
ls -la infra/ci-cd/scripts/backup.sh
chmod +x infra/ci-cd/scripts/backup.sh

# Manual backup (fallback)
docker exec aiteam_db_prod pg_dump -U aiteam_prod_user aiteam_prod | \
  gzip > manual_backup_$(date +%Y%m%d_%H%M%S).sql.gz
```

### Rollback Failed

```bash
# Check Docker images available
docker images | grep -E "(ai-orchestrator|ai-dashboard)"

# Check deployment history
cat backups/rollback/deployment_tags.log

# Check service status
docker-compose -f infra/deployments/prod/docker-compose.yml ps

# View service logs
docker logs aiteam_backend_prod --tail=100
docker logs aiteam_frontend_prod --tail=100

# Restart services
docker-compose -f infra/deployments/prod/docker-compose.yml restart
```

### Cron Not Running

```bash
# Check cron status
sudo systemctl status cron

# View cron logs
grep CRON /var/log/syslog

# Test cron script manually
/opt/atlasia/infra/deployments/prod/backup-cron.sh

# Check crontab
crontab -l

# Check log file permissions
ls -la /var/log/atlasia-backup.log
```

## 📊 Monitoring

### Check Backup Health

```bash
# Last backup age
find backups/daily -name "backup_*.sql.gz" -type f -mtime -1

# Backup counts
echo "Daily: $(ls backups/daily/*.sql.gz 2>/dev/null | wc -l)"
echo "Weekly: $(ls backups/weekly/*.sql.gz 2>/dev/null | wc -l)"
echo "Monthly: $(ls backups/monthly/*.sql.gz 2>/dev/null | wc -l)"

# Total backup size
du -sh backups/

# Recent backup log
tail -50 /var/log/atlasia-backup.log
```

### Setup Monitoring

```bash
# Add to crontab environment
crontab -e
# Add at top:
HEALTHCHECK_URL=https://hc-ping.com/your-unique-id
ADMIN_EMAIL=admin@example.com
S3_BUCKET=my-backup-bucket

# Create monitoring alert
# Alert if no backup in 25 hours:
0 3 * * * [ $(find /opt/atlasia/backups/daily -name "backup_*.sql.gz" -mtime -1 | wc -l) -eq 0 ] && \
  echo "No backup in 24h" | mail -s "Backup Alert" admin@example.com
```

## 🔐 Security

### Encrypt Backups (Optional)

```bash
# Encrypt backup with GPG
docker exec aiteam_db_prod pg_dump -U aiteam_prod_user aiteam_prod | \
  gzip | \
  gpg --encrypt --recipient admin@example.com \
  > backup_$(date +%Y%m%d_%H%M%S).sql.gz.gpg

# Decrypt backup
gpg --decrypt backup_20260226_020000.sql.gz.gpg | \
  gunzip | \
  docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod
```

### Restrict Access

```bash
# Set restrictive permissions on backups
chmod 700 backups/
chmod 600 backups/**/*.sql.gz

# Set restrictive permissions on scripts
chmod 750 infra/ci-cd/scripts/*.sh
chmod 750 infra/deployments/prod/*.sh
```

## 📅 Retention Policy

| Type | Frequency | Retention | When Created |
|------|-----------|-----------|--------------|
| Daily | Every day | 7 days | Every day |
| Weekly | Weekly | 4 weeks | Sunday (day 7) |
| Monthly | Monthly | Indefinite | 1st of month |
| Pre-deployment | Before deploy | Indefinite | During deployment |
| Pre-rollback | Before rollback | Indefinite | During rollback |

## 🌐 Environment Variables

### Backup Script
- `BACKUP_DIR` - Base backup directory (default: ./backups)
- `DB_CONTAINER` - Database container name (auto-detected)
- `DB_NAME` - Database name (auto-detected)
- `DB_USER` - Database user (auto-detected)
- `S3_BUCKET` - S3 bucket for remote backup (optional)

### Rollback Script
- `ROLLBACK_TAG` - Docker image tag (default: previous from history)
- `BACKUP_DIR` - Backup directory (default: ./backups)
- `GITHUB_REPO` - GitHub repo for GHCR images (e.g., myorg/atlasia)

### Cron Script
- `DEPLOY_DIR` - Deployment directory (default: /opt/atlasia)
- `BACKUP_DIR` - Backup directory (default: /opt/atlasia/backups)
- `S3_BUCKET` - S3 bucket for remote backup (optional)
- `HEALTHCHECK_URL` - Healthcheck.io URL (optional)
- `ADMIN_EMAIL` - Email for notifications (optional)

## 📞 Emergency Contacts

| Scenario | Action | Command |
|----------|--------|---------|
| **Database Corruption** | Restore from latest backup | `zcat backups/daily/backup_*.sql.gz \| docker exec -i aiteam_db_prod psql -U aiteam_prod_user aiteam_prod` |
| **Bad Deployment** | Rollback immediately | `./infra/ci-cd/scripts/rollback.sh prod --skip-backup` |
| **Disk Full** | Clean old backups | `find backups/daily -mtime +7 -delete` |
| **Service Down** | Check logs and restart | `docker-compose -f infra/deployments/prod/docker-compose.yml logs -f && docker-compose restart` |
| **Backup Failed** | Manual backup | `docker exec aiteam_db_prod pg_dump -U aiteam_prod_user aiteam_prod \| gzip > manual_backup.sql.gz` |

## 📚 Additional Resources

- Full Documentation: `infra/ci-cd/README.md`
- Backup Script: `infra/ci-cd/scripts/backup.sh`
- Rollback Script: `infra/ci-cd/scripts/rollback.sh`
- Verification: `infra/ci-cd/scripts/verify-backup-setup.sh`
- Setup: `infra/ci-cd/scripts/setup-permissions.sh`

---

**Keep this card handy during deployments and maintenance windows!**
