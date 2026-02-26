# Atlasia Kubernetes Deployment - File Index

Quick reference guide to all files in the Kubernetes deployment structure.

## 📚 Documentation Files

| File | Purpose | Audience |
|------|---------|----------|
| [README.md](README.md) | Complete deployment guide with cluster setup, configuration, and troubleshooting | DevOps, SRE |
| [QUICKSTART.md](QUICKSTART.md) | 10-minute local deployment guide | Developers |
| [DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md) | Production deployment checklist | DevOps, Release Managers |
| [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md) | Technical overview of implementation | Architects, Tech Leads |
| [INDEX.md](INDEX.md) | This file - quick navigation | Everyone |

## 🎛️ Helm Chart Files

### Core Chart Files
| File | Purpose |
|------|---------|
| [helm/atlasia/Chart.yaml](helm/atlasia/Chart.yaml) | Chart metadata and PostgreSQL dependency |
| [helm/atlasia/values.yaml](helm/atlasia/values.yaml) | Default configuration values (production) |
| [helm/atlasia/values.schema.json](helm/atlasia/values.schema.json) | JSON schema for values validation |
| [helm/atlasia/.helmignore](helm/atlasia/.helmignore) | Files to exclude from chart package |

### Template Files
| File | Description |
|------|-------------|
| [templates/_helpers.tpl](helm/atlasia/templates/_helpers.tpl) | Reusable template helper functions |
| [templates/configmap.yaml](helm/atlasia/templates/configmap.yaml) | Non-sensitive application configuration |
| [templates/secret.yaml](helm/atlasia/templates/secret.yaml) | Sensitive credentials and keys |
| [templates/serviceaccount.yaml](helm/atlasia/templates/serviceaccount.yaml) | Service account for pod authentication |
| [templates/orchestrator-deployment.yaml](helm/atlasia/templates/orchestrator-deployment.yaml) | Backend Spring Boot deployment |
| [templates/orchestrator-service.yaml](helm/atlasia/templates/orchestrator-service.yaml) | Backend ClusterIP service |
| [templates/orchestrator-hpa.yaml](helm/atlasia/templates/orchestrator-hpa.yaml) | Horizontal Pod Autoscaler (CPU/memory/custom) |
| [templates/orchestrator-pdb.yaml](helm/atlasia/templates/orchestrator-pdb.yaml) | Pod Disruption Budget for availability |
| [templates/frontend-deployment.yaml](helm/atlasia/templates/frontend-deployment.yaml) | Frontend Angular/Nginx deployment |
| [templates/frontend-service.yaml](helm/atlasia/templates/frontend-service.yaml) | Frontend ClusterIP service |
| [templates/frontend-pdb.yaml](helm/atlasia/templates/frontend-pdb.yaml) | Frontend Pod Disruption Budget |
| [templates/ingress.yaml](helm/atlasia/templates/ingress.yaml) | Ingress for external access with TLS |
| [templates/networkpolicy.yaml](helm/atlasia/templates/networkpolicy.yaml) | Network segmentation policies |
| [templates/servicemonitor.yaml](helm/atlasia/templates/servicemonitor.yaml) | Prometheus ServiceMonitor CRD |
| [templates/NOTES.txt](helm/atlasia/templates/NOTES.txt) | Post-installation instructions |

## 🌍 Environment Configurations

| File | Environment | Description |
|------|-------------|-------------|
| [helm/atlasia/values.yaml](helm/atlasia/values.yaml) | Production | Default production configuration |
| [environments/staging/values.yaml](environments/staging/values.yaml) | Staging | Staging environment overrides |
| [examples/local-values.yaml](examples/local-values.yaml) | Local | Local development (Minikube/Kind) |

## 📋 Example Files

| File | Purpose |
|------|---------|
| [examples/local-values.yaml](examples/local-values.yaml) | Configuration for local Kubernetes |
| [examples/secrets-template.yaml](examples/secrets-template.yaml) | Template for creating Kubernetes secrets |

## 🔧 Automation Scripts

| File | Platform | Purpose |
|------|----------|---------|
| [scripts/validate-minikube.sh](scripts/validate-minikube.sh) | Bash | Deploy and validate on Minikube |
| [scripts/validate-kind.sh](scripts/validate-kind.sh) | Bash | Deploy and validate on Kind (Linux/macOS) |
| [scripts/validate-kind.ps1](scripts/validate-kind.ps1) | PowerShell | Deploy and validate on Kind (Windows) |
| [Makefile](Makefile) | Make | 30+ common deployment tasks |

## 🚀 Quick Start Commands

### Documentation
```bash
# Read comprehensive guide
cat infra/k8s/README.md

# Quick start guide
cat infra/k8s/QUICKSTART.md

# Deployment checklist
cat infra/k8s/DEPLOYMENT_CHECKLIST.md
```

### Local Deployment
```bash
# Linux/macOS with Kind
cd infra/k8s
./scripts/validate-kind.sh

# Windows with Kind
cd infra/k8s
.\scripts\validate-kind.ps1

# Minikube
cd infra/k8s
./scripts/validate-minikube.sh

# Using Makefile
cd infra/k8s
make validate-kind
make validate-minikube
```

### Production Deployment
```bash
cd infra/k8s

# Add Helm repos
make deps

# Create namespace
make create-namespace

# Create and apply secrets
make create-secrets-template
# Edit secrets.yaml
make apply-secrets

# Install chart
make install

# Or install staging
make install-staging
```

### Common Operations
```bash
# View status
make status
make get-pods

# View logs
make logs-orchestrator
make logs-frontend

# Port forwarding
make port-forward-orchestrator  # http://localhost:8080
make port-forward-frontend      # http://localhost:8081

# Health check
make health-orchestrator

# Upgrade
make upgrade

# Uninstall
make uninstall
```

## 📊 Resource Summary

### Deployments
- **Orchestrator**: Spring Boot backend (3 replicas, HPA enabled)
- **Frontend**: Angular/Nginx frontend (2 replicas)
- **PostgreSQL**: StatefulSet via Bitnami chart (1 replica with PVC)

### Services
- **atlasia-orchestrator**: ClusterIP on port 8080
- **atlasia-frontend**: ClusterIP on port 80
- **atlasia-postgresql**: ClusterIP on port 5432

### Ingress Paths
- `/api` → Orchestrator
- `/actuator` → Orchestrator
- `/ws` → Orchestrator (WebSocket)
- `/` → Frontend

### ConfigMaps
- **atlasia-config**: Application configuration (non-sensitive)

### Secrets
- **atlasia-secrets**: Application credentials and API keys
- **atlasia-postgresql-secret**: Database credentials

### HPA Configuration
- **Target CPU**: 70% utilization
- **Target Memory**: 80% utilization
- **Custom Metric**: ai_tasks_queue_depth (target: 50)
- **Scale Range**: 3-10 replicas (production)

### Pod Disruption Budgets
- **Orchestrator**: Minimum 2 pods available
- **Frontend**: Minimum 1 pod available
- **PostgreSQL**: Minimum 1 pod available

## 🔍 Finding Specific Information

### Cluster Requirements
- See [README.md](README.md#cluster-requirements)
- GKE, EKS, AKS specific instructions included

### Resource Sizing
- See [README.md](README.md#resource-sizing)
- Tables for dev, staging, production

### Security Configuration
- See [README.md](README.md#security)
- Network policies, RBAC, TLS, secrets management

### Monitoring Setup
- See [README.md](README.md#monitoring-and-observability)
- Prometheus, Grafana, Jaeger integration

### Troubleshooting
- See [README.md](README.md#troubleshooting)
- Common issues and solutions

### Local Development
- See [README.md](README.md#local-development)
- Minikube and Kind setup

## 📈 Deployment Workflow

```
1. Prerequisites → README.md#prerequisites
2. Cluster Setup → README.md#cluster-requirements
3. Build Images → README.md#build-and-push-docker-images
4. Create Secrets → examples/secrets-template.yaml
5. Install Chart → README.md#installation
6. Verify → DEPLOYMENT_CHECKLIST.md
7. Monitor → README.md#monitoring-and-observability
```

## 🎯 Use Cases

| Task | Reference |
|------|-----------|
| First-time deployment | [QUICKSTART.md](QUICKSTART.md) |
| Production deployment | [DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md) |
| Troubleshooting | [README.md](README.md#troubleshooting) |
| Upgrade existing | [README.md](README.md#rolling-update) |
| Rollback | [README.md](README.md#rollback) |
| Scale up/down | [README.md](README.md#scaling) |
| Add monitoring | [README.md](README.md#monitoring-and-observability) |
| Configure TLS | [README.md](README.md#tlsssl) |
| Local testing | [QUICKSTART.md](QUICKSTART.md) |
| Automation | [Makefile](Makefile) |

## 🛠️ Customization Guide

### Change Replica Count
Edit `values.yaml` or override:
```yaml
replicaCount:
  orchestrator: 5  # Your desired count
  frontend: 3
```

### Adjust Resources
Edit `values.yaml` or override:
```yaml
orchestrator:
  resources:
    requests:
      memory: "1Gi"
      cpu: "1000m"
    limits:
      memory: "4Gi"
      cpu: "4000m"
```

### Configure Ingress
Edit `values.yaml` or override:
```yaml
ingress:
  enabled: true
  hosts:
    - host: your-domain.com
  tls:
    - secretName: your-tls-secret
      hosts:
        - your-domain.com
```

### Add Environment Variables
Edit `orchestrator.env` in `values.yaml`:
```yaml
orchestrator:
  env:
    - name: MY_CUSTOM_VAR
      value: "my-value"
```

## 📦 File Statistics

- **Total Files**: 30
- **Documentation**: 5 markdown files
- **Helm Templates**: 14 YAML templates
- **Configuration Files**: 4 values files
- **Scripts**: 3 validation scripts
- **Automation**: 1 Makefile
- **Schema**: 1 JSON schema

## 🔗 External Dependencies

- **PostgreSQL Chart**: Bitnami PostgreSQL 12.x
- **Ingress Controller**: NGINX Ingress (recommended)
- **Certificate Manager**: cert-manager (optional)
- **Monitoring**: Prometheus Operator (optional)
- **Image Registry**: Any (Docker Hub, GCR, ECR, ACR)

## 📞 Getting Help

1. Check [QUICKSTART.md](QUICKSTART.md) for common scenarios
2. Review [TROUBLESHOOTING](README.md#troubleshooting) section
3. Consult [DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md)
4. Review Makefile for available commands: `make help`
5. Check template comments in YAML files
6. File an issue on GitHub

## ✅ Pre-Flight Checklist

Before deployment, ensure you have:

- [ ] Kubernetes cluster (1.24+)
- [ ] kubectl configured
- [ ] Helm 3.8+ installed
- [ ] Docker images built and pushed
- [ ] Secrets prepared
- [ ] DNS configured (for production)
- [ ] TLS certificates (for production)
- [ ] Monitoring stack (optional)

## 🎓 Learning Path

1. **Beginner**: Start with [QUICKSTART.md](QUICKSTART.md)
2. **Intermediate**: Read [README.md](README.md) sections 1-6
3. **Advanced**: Study [DEPLOYMENT_CHECKLIST.md](DEPLOYMENT_CHECKLIST.md)
4. **Expert**: Review [IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)

---

**Last Updated**: 2024  
**Version**: 1.0  
**Maintainer**: Atlasia Team
