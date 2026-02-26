# Kubernetes Deployment Checklist

Use this checklist when deploying Atlasia AI Orchestrator to production Kubernetes clusters.

## Pre-Deployment

### Infrastructure Setup

- [ ] Kubernetes cluster provisioned (version 1.24+)
- [ ] Cluster has sufficient resources (see [Resource Sizing](README.md#resource-sizing))
- [ ] Multiple availability zones configured (for high availability)
- [ ] Network policies enabled on cluster
- [ ] Storage class configured for persistent volumes
- [ ] LoadBalancer or Ingress controller available

### Tools Installation

- [ ] `kubectl` installed and configured
- [ ] `helm` 3.8+ installed
- [ ] Access to container registry (Docker Hub, GCR, ECR, ACR)
- [ ] `cert-manager` installed (for TLS certificates)
- [ ] Prometheus Operator installed (optional, for monitoring)

### Security Prerequisites

- [ ] Secrets management solution configured (Vault, Sealed Secrets, External Secrets)
- [ ] TLS certificates prepared or cert-manager configured
- [ ] RBAC policies reviewed
- [ ] Network policies reviewed
- [ ] Pod Security Standards configured
- [ ] Image scanning enabled (Trivy, Snyk, etc.)

### Required Secrets

Ensure all required secrets are prepared:

- [ ] `orchestrator-token` - Bearer token for API authentication
- [ ] `db-password` - PostgreSQL password
- [ ] `jwt-secret` - JWT signing key (minimum 256 bits)
- [ ] `encryption-key` - Encryption key (32 bytes)
- [ ] `llm-api-key` - OpenAI/LLM API key
- [ ] `vault-token` - HashiCorp Vault token (if using Vault)

Optional secrets (if features enabled):

- [ ] `llm-fallback-api-key` - Fallback LLM API key
- [ ] `github-webhook-secret` - GitHub webhook secret
- [ ] `github-private-key-path` - GitHub App private key
- [ ] OAuth2 credentials (GitHub, Google, GitLab)
- [ ] Mail server credentials

## Deployment

### Step 1: Namespace and RBAC

- [ ] Create namespace: `kubectl create namespace atlasia`
- [ ] Apply custom RBAC policies (if any)
- [ ] Create service account with proper permissions

### Step 2: Secrets Management

Choose one method:

**Option A: Direct Kubernetes Secrets**
- [ ] Create secrets using `kubectl create secret`
- [ ] Verify secrets created: `kubectl get secrets -n atlasia`

**Option B: Sealed Secrets**
- [ ] Install Sealed Secrets controller
- [ ] Create and seal secrets
- [ ] Apply sealed secrets manifest

**Option C: External Secrets Operator**
- [ ] Configure External Secrets Operator
- [ ] Create SecretStore resource
- [ ] Create ExternalSecret resources
- [ ] Verify secrets synced

**Option D: HashiCorp Vault**
- [ ] Vault server accessible from cluster
- [ ] Vault policies configured
- [ ] Vault role created for Atlasia
- [ ] Secrets stored in Vault at `secret/data/atlasia/*`

### Step 3: Helm Chart Configuration

- [ ] Add Bitnami repository: `helm repo add bitnami https://charts.bitnami.com/bitnami`
- [ ] Update repositories: `helm repo update`
- [ ] Create custom `values.yaml` for environment
- [ ] Review and update:
  - [ ] Image repositories and tags
  - [ ] Replica counts
  - [ ] Resource requests and limits
  - [ ] Ingress hostnames
  - [ ] TLS configuration
  - [ ] CORS allowed origins
  - [ ] Database configuration
  - [ ] Monitoring settings

### Step 4: Container Images

- [ ] Build or pull orchestrator image
- [ ] Build or pull frontend image
- [ ] Tag images with version
- [ ] Push images to registry
- [ ] Verify image pull secrets (if using private registry)
- [ ] Scan images for vulnerabilities

### Step 5: Deploy Chart

```bash
helm install atlasia ./helm/atlasia \
  --namespace atlasia \
  --values ./helm/atlasia/values.yaml \
  --values ./path/to/environment/values.yaml \
  --wait \
  --timeout 10m
```

- [ ] Helm install command executed successfully
- [ ] Wait for all pods to be ready
- [ ] Check deployment status: `kubectl get pods -n atlasia`

### Step 6: Verify Deployment

#### Pod Status
- [ ] All orchestrator pods running
- [ ] All frontend pods running
- [ ] PostgreSQL StatefulSet running
- [ ] No CrashLoopBackOff or ImagePullBackOff errors

#### Services
- [ ] Orchestrator service created
- [ ] Frontend service created
- [ ] PostgreSQL service created
- [ ] Services have ClusterIP assigned

#### Ingress
- [ ] Ingress resource created
- [ ] Ingress has IP/hostname assigned
- [ ] DNS records updated (if applicable)
- [ ] TLS certificate provisioned

#### Health Checks
- [ ] Orchestrator liveness probe passing
- [ ] Orchestrator readiness probe passing
- [ ] Frontend responding to requests
- [ ] Database accepting connections

## Post-Deployment

### Functional Testing

- [ ] Access frontend via ingress URL
- [ ] Login functionality works
- [ ] API endpoints responding
- [ ] WebSocket connections working
- [ ] Database queries executing
- [ ] File uploads working (if applicable)

### Performance Testing

- [ ] Check pod resource usage: `kubectl top pods -n atlasia`
- [ ] Verify HPA is configured: `kubectl get hpa -n atlasia`
- [ ] Load test API endpoints
- [ ] Monitor response times
- [ ] Check database query performance

### Security Validation

- [ ] Verify pods run as non-root user
- [ ] Check security contexts applied
- [ ] Verify network policies active
- [ ] Test CORS restrictions
- [ ] Verify TLS/SSL certificates
- [ ] Scan deployed images for vulnerabilities
- [ ] Review exposed ports and services

### Monitoring Setup

- [ ] Prometheus scraping metrics
- [ ] Grafana dashboards configured
- [ ] Alerts configured for:
  - [ ] Pod crashes
  - [ ] High CPU/memory usage
  - [ ] Database connection issues
  - [ ] API error rates
  - [ ] Certificate expiration
- [ ] Log aggregation working (ELK, Loki, etc.)
- [ ] Distributed tracing configured (Jaeger, Zipkin)

### Backup Configuration

- [ ] Database backup CronJob created
- [ ] Backup storage configured (S3, GCS, Azure Blob)
- [ ] Test backup and restore procedure
- [ ] Document backup retention policy
- [ ] Verify backup monitoring/alerts

### Documentation

- [ ] Update deployment documentation
- [ ] Document custom values and overrides
- [ ] Record deployment date and version
- [ ] Update runbook with cluster-specific details
- [ ] Share access credentials (securely)

## Rollout Strategy

Choose and document rollout strategy:

### Rolling Update (Default)
- [ ] Configure maxSurge and maxUnavailable
- [ ] Test rolling update procedure
- [ ] Verify zero-downtime deployment

### Blue-Green Deployment
- [ ] Deploy new version alongside current
- [ ] Smoke test new version
- [ ] Switch traffic via Ingress
- [ ] Monitor for issues
- [ ] Keep old version running for quick rollback

### Canary Deployment
- [ ] Deploy canary version with limited traffic
- [ ] Monitor metrics and error rates
- [ ] Gradually increase canary traffic
- [ ] Promote to production or rollback

## Rollback Plan

- [ ] Document rollback procedure
- [ ] Test rollback: `helm rollback atlasia -n atlasia`
- [ ] Identify rollback triggers (error rates, latency, etc.)
- [ ] Define rollback decision makers
- [ ] Test database migration rollback (if applicable)

## Scaling Strategy

### Horizontal Pod Autoscaling
- [ ] HPA configured for orchestrator
- [ ] Metrics server installed
- [ ] Custom metrics configured (queue depth)
- [ ] Test scale-up behavior
- [ ] Test scale-down behavior

### Vertical Scaling
- [ ] VPA considered (optional)
- [ ] Resource limits allow for growth
- [ ] Node capacity sufficient for scaling

### Database Scaling
- [ ] Read replicas configured (if needed)
- [ ] Connection pooling configured
- [ ] Database performance tuned
- [ ] Backup strategy scales with data growth

## Disaster Recovery

- [ ] Multi-region deployment considered
- [ ] Database replication configured (if multi-region)
- [ ] Disaster recovery plan documented
- [ ] RTO (Recovery Time Objective) defined
- [ ] RPO (Recovery Point Objective) defined
- [ ] DR drills scheduled

## Maintenance

### Regular Tasks
- [ ] Schedule regular Helm upgrades
- [ ] Plan Kubernetes version upgrades
- [ ] Monitor certificate expiration
- [ ] Review and rotate secrets quarterly
- [ ] Review resource usage and adjust limits
- [ ] Update container images for security patches

### Incident Response
- [ ] On-call rotation established
- [ ] Incident response procedures documented
- [ ] Escalation paths defined
- [ ] Post-mortem template prepared

## Compliance

- [ ] Audit logging enabled
- [ ] Compliance requirements met (SOC2, HIPAA, etc.)
- [ ] Data retention policies configured
- [ ] Privacy controls in place
- [ ] Security review completed
- [ ] Compliance documentation updated

## Sign-off

Deployment completed by: _____________________________ Date: _________

Reviewed by: _____________________________ Date: _________

Approved by: _____________________________ Date: _________

## Post-Deployment Support

**24-Hour Monitoring**
- [ ] Monitor error rates for 24 hours
- [ ] Check performance metrics
- [ ] Review logs for anomalies
- [ ] Address any issues immediately

**One Week Follow-up**
- [ ] Review resource utilization trends
- [ ] Adjust HPA settings if needed
- [ ] Optimize database queries
- [ ] Review security alerts

**One Month Review**
- [ ] Capacity planning review
- [ ] Cost optimization review
- [ ] Security posture assessment
- [ ] User feedback collection

## Additional Notes

```
Add environment-specific notes, known issues, or special configurations here:




```

---

**Version**: 1.0  
**Last Updated**: [Date]  
**Environment**: [Production/Staging/Development]  
**Cluster**: [Cluster Name/Region]
