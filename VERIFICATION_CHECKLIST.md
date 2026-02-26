# ✅ Critical Issues Fix Verification Checklist

Use this checklist to verify all fixes are in place before deploying to production.

---

## 1️⃣ Health Checks: `wget` → `curl`

### Verification Steps:

- [ ] **Backend Dockerfile** (`ai-orchestrator/Dockerfile`)
  ```bash
  grep -A 2 "HEALTHCHECK" ai-orchestrator/Dockerfile | grep "curl"
  # Expected: CMD curl -sf http://localhost:8080/actuator/health || exit 1
  ```

- [ ] **Frontend Dockerfile** (`frontend/Dockerfile`)
  ```bash
  grep -A 2 "HEALTHCHECK" frontend/Dockerfile | grep "curl"
  # Expected: CMD curl -sf http://localhost:80/index.html || exit 1
  ```

- [ ] **Frontend Dev Dockerfile** (`frontend/Dockerfile.dev`)
  ```bash
  grep -A 2 "HEALTHCHECK" frontend/Dockerfile.dev | grep "curl"
  # Expected: CMD curl -sf http://localhost:4200/ || exit 1
  ```

- [ ] **Curl is installed before apk-tools removal**
  ```bash
  grep -B 1 "apk del --purge apk-tools" ai-orchestrator/Dockerfile
  # Expected: Line above should have "apk add --no-cache curl"
  ```

- [ ] **Production Compose** (`infra/deployments/prod/docker-compose.yml`)
  ```bash
  grep -A 2 'test:.*curl' infra/deployments/prod/docker-compose.yml
  # Expected: Should show curl commands for both services
  ```

**Status:** ☐ ALL PASSED ☐ FAILED - Action needed:

---

## 2️⃣ PostgreSQL Port: Exposed → Localhost Only

### Verification Steps:

- [ ] **Production Compose Port Binding** (`infra/deployments/prod/docker-compose.yml`)
  ```bash
  grep -A 1 "ports:" infra/deployments/prod/docker-compose.yml | grep -A 1 "ai-db:" | tail -n 1
  # Expected: "127.0.0.1:${POSTGRES_PORT:-5432}:5432" or similar with 127.0.0.1
  # NOT: "${POSTGRES_PORT:-5432}:5432" or "5432:5432"
  ```

- [ ] **Comment explains security**
  ```bash
  grep -B 2 "Restricted to localhost" infra/deployments/prod/docker-compose.yml
  # Expected: Should have comment about security
  ```

- [ ] **Test that port is NOT accessible** (after deployment)
  ```bash
  # On production server:
  nmap -p 5432 your-server-ip
  # Expected: 5432/tcp filtered or closed (NOT open)
  
  # Local access should work:
  psql -h 127.0.0.1 -U user -d dbname
  # Expected: Connection successful
  ```

**Status:** ☐ ALL PASSED ☐ FAILED - Action needed:

---

## 3️⃣ Duplicate Files: Marked Deprecated

### Verification Steps:

- [ ] **Root docker-compose.yml** contains deprecation notice
  ```bash
  head -n 5 docker-compose.yml
  # Expected: Contains "DEPRECATED" warning
  ```

- [ ] **Root docker-compose.dev.yml** contains deprecation notice
  ```bash
  head -n 5 docker-compose.dev.yml
  # Expected: Contains "DEPRECATED" warning
  ```

- [ ] **Both files point to new locations**
  ```bash
  grep "Use:" docker-compose.yml docker-compose.dev.yml
  # Expected: Should reference infra/deployments/ paths
  ```

**Status:** ☐ ALL PASSED ☐ FAILED - Action needed:

---

## 4️⃣ Container Security: Added `cap_drop` and `security_opt`

### Verification Steps:

- [ ] **Production Compose: ai-db service**
  ```bash
  docker-compose -f infra/deployments/prod/docker-compose.yml config | \
    sed -n '/ai-db:/,/ai-orchestrator:/p' | grep -A 10 "cap_drop"
  # Expected: cap_drop: ["ALL"] followed by cap_add with DB capabilities
  ```

- [ ] **Production Compose: ai-orchestrator service**
  ```bash
  docker-compose -f infra/deployments/prod/docker-compose.yml config | \
    sed -n '/ai-orchestrator:/,/ai-dashboard:/p' | grep -A 5 "cap_drop"
  # Expected: cap_drop: ["ALL"] and cap_add: ["NET_BIND_SERVICE"]
  ```

- [ ] **Production Compose: ai-dashboard service**
  ```bash
  docker-compose -f infra/deployments/prod/docker-compose.yml config | \
    sed -n '/ai-dashboard:/,/networks:/p' | grep "cap_drop" -A 5
  # Expected: cap_drop and cap_add present
  ```

- [ ] **Dev Compose: All services have security options**
  ```bash
  docker-compose -f infra/deployments/dev/docker-compose.yml config | \
    grep -c "no-new-privileges"
  # Expected: 3 (one for each service)
  ```

- [ ] **Test in running container** (after deployment)
  ```bash
  # On production server:
  docker inspect $(docker ps -qf "name=aiteam_backend_prod") | \
    jq '.[0].HostConfig.CapDrop'
  # Expected: ["ALL"]
  
  docker inspect $(docker ps -qf "name=aiteam_backend_prod") | \
    jq '.[0].HostConfig.SecurityOpt'
  # Expected: ["no-new-privileges:true"]
  ```

**Status:** ☐ ALL PASSED ☐ FAILED - Action needed:

---

## 5️⃣ Git Branch: Hardcoded `main` → Dynamic Selection

### Verification Steps:

- [ ] **Manual deploy workflow has git_branch step**
  ```bash
  grep -A 10 "Set Git branch based on environment" .github/workflows/manual-deploy.yml
  # Expected: Should set branch based on environment input
  ```

- [ ] **Branch is used in git commands**
  ```bash
  grep "steps.git_branch.outputs.branch" .github/workflows/manual-deploy.yml
  # Expected: Should appear in fetch, checkout, reset commands
  ```

- [ ] **Production environment uses main branch**
  ```bash
  grep -A 3 'environment.*production' .github/workflows/manual-deploy.yml | \
    grep "main" || echo "Check logic in workflow"
  # Expected: Should use main for production
  ```

- [ ] **Staging environment uses develop branch**
  ```bash
  grep -B 5 'branch=develop' .github/workflows/manual-deploy.yml
  # Expected: Should have condition checking for staging/non-production
  ```

- [ ] **Slack notification includes branch**
  ```bash
  grep 'Branch:' .github/workflows/manual-deploy.yml | grep git_branch
  # Expected: Should reference git_branch output
  ```

- [ ] **Test the workflow** (dry run)
  ```bash
  # Go to Actions → Manual Deploy to Production
  # Trigger with environment=production
  # Verify in logs it says "Branch: main"
  
  # Trigger with environment=staging
  # Verify in logs it says "Branch: develop"
  ```

**Status:** ☐ ALL PASSED ☐ FAILED - Action needed:

---

## 🧪 Integration Testing

Run these after deploying to a test environment:

### Local Testing (Development):
```bash
# Test dev stack starts correctly
docker-compose -f infra/deployments/dev/docker-compose.yml --profile full up -d

# Verify all containers are healthy
docker-compose -f infra/deployments/dev/docker-compose.yml ps
# Expected: All showing "Up" and "healthy" status

# Check security options are applied
docker inspect $(docker ps -qf "name=aiteam_backend_dev") | jq '.[0].HostConfig | {CapDrop, CapAdd, SecurityOpt}'

# Test health checks pass
curl http://localhost:8080/actuator/health
curl http://localhost:4200/

# Stop stack
docker-compose -f infra/deployments/dev/docker-compose.yml down
```

### Staging Testing (After Deploy):
```bash
# SSH to staging server
ssh deploy@staging-server

# Check database is not exposed
nmap -p 5432 localhost
# Expected: 5432/tcp filtered (NOT open)

# Check health of services
docker-compose -f infra/deployments/prod/docker-compose.yml ps
# Expected: All healthy

# Verify branches deployed
git -C /opt/aiteam branch -vv
# Expected: Should show "develop" for staging

# Test endpoints
curl https://staging.yourdomain.com/health
```

### Production Testing (After Deploy):
```bash
# SSH to production server
ssh deploy@prod-server

# Verify correct branch
git -C /opt/aiteam branch -vv
# Expected: Should show "main"

# Check database security
nmap -p 5432 localhost
# Expected: 5432/tcp filtered (NOT open to internet)

# Verify external access blocked
nmap -p 5432 your-public-ip
# Expected: filtered or closed

# Test endpoints
curl https://api.yourdomain.com/health
curl https://api.yourdomain.com/api/actuator/health
```

**Status:** ☐ ALL TESTS PASSED ☐ FAILURES DETECTED - Document below:

---

## 📝 Testing Notes

**Date Tested:** _______________

**Tested By:** _______________

**Issues Found:**
```
(List any issues discovered during testing)
```

**Resolutions:**
```
(Document how each issue was resolved)
```

---

## 🎯 Final Sign-Off

- [ ] All 5 critical fixes verified
- [ ] Local testing completed successfully
- [ ] Staging deployment tested
- [ ] Production readiness confirmed
- [ ] Team notified of changes
- [ ] Documentation updated

**Signed Off By:** _______________  
**Date:** _______________  
**Ready for Production Deployment:** ☐ YES ☐ NO

---

## 🔄 Rollback Procedure (If Issues Occur)

If any issues are detected in production:

```bash
# 1. Revert to previous commit
git revert --no-edit <commit-hash>

# 2. Redeploy
./infra/ci-cd/scripts/deploy-prod.sh

# 3. Verify services
docker-compose -f infra/deployments/prod/docker-compose.yml ps

# 4. Check logs for errors
docker-compose -f infra/deployments/prod/docker-compose.yml logs -f

# 5. Restore from backup if needed
./scripts/rollback.sh prod
```

---

**Document Status:** Ready for verification ✅  
**Last Updated:** 2026-02-26
