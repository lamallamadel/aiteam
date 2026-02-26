# Atlasia AI Orchestrator - Kubernetes Deployment

This directory contains Helm charts and configurations for deploying Atlasia AI Orchestrator to Kubernetes.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Architecture](#architecture)
- [Cluster Requirements](#cluster-requirements)
- [Resource Sizing](#resource-sizing)
- [Installation](#installation)
- [Configuration](#configuration)
- [Deployment Procedures](#deployment-procedures)
- [Monitoring and Observability](#monitoring-and-observability)
- [Security](#security)
- [Troubleshooting](#troubleshooting)
- [Local Development](#local-development)

## Prerequisites

- Kubernetes cluster 1.24+ (GKE, EKS, AKS, or local Minikube/Kind)
- `kubectl` CLI configured to access your cluster
- `helm` 3.8+
- `docker` for building images
- For production: cert-manager for TLS certificate management
- For monitoring: Prometheus Operator (optional)

## Architecture

The Helm chart deploys the following components:

```
┌─────────────────────────────────────────────────────────────┐
│                         Ingress (TLS)                        │
│                    atlasia.example.com                       │
└────────────────────┬────────────────────┬───────────────────┘
                     │                    │
              ┌──────▼──────┐      ┌─────▼──────┐
              │  Frontend   │      │ Orchestrator│
              │  (Angular)  │      │ (Spring Boot)│
              │  Nginx      │      │   3 Pods    │
              │  2 Pods     │      │   HPA       │
              └─────────────┘      └──────┬──────┘
                                          │
                                   ┌──────▼──────┐
                                   │ PostgreSQL  │
                                   │ StatefulSet │
                                   │ Persistent  │
                                   └─────────────┘
```

### Components

1. **AI Orchestrator (Backend)**: Spring Boot application
   - 3 replicas (production), scales 3-10 based on CPU/memory/queue depth
   - Exposes REST API, WebSocket, and actuator endpoints
   - Health probes: `/actuator/health/liveness` and `/actuator/health/readiness`

2. **Frontend**: Angular SPA served by Nginx
   - 2 replicas (production)
   - Static asset serving with caching

3. **PostgreSQL**: Database (StatefulSet)
   - 1 primary instance with persistent storage
   - Automatic backups (optional)
   - Extensions: uuid-ossp, pg_trgm

4. **Ingress**: NGINX Ingress Controller
   - TLS termination with Let's Encrypt
   - Path-based routing
   - Rate limiting and security headers

## Cluster Requirements

### Minimum Requirements (Staging)

- **Nodes**: 3 worker nodes
- **Total vCPU**: 4 cores
- **Total Memory**: 8 GB
- **Storage**: 50 GB persistent volume support (ReadWriteOnce)

### Production Requirements

- **Nodes**: 5+ worker nodes across multiple availability zones
- **Total vCPU**: 12 cores
- **Total Memory**: 24 GB
- **Storage**: 100 GB+ persistent volume support with backup strategy
- **Network**: Load balancer support
- **Security**: Network policies enabled

### Cloud Provider Specifics

#### Google Kubernetes Engine (GKE)

```bash
# Create GKE cluster
gcloud container clusters create atlasia-prod \
  --region us-central1 \
  --num-nodes 2 \
  --machine-type n2-standard-4 \
  --disk-size 100 \
  --enable-autoscaling \
  --min-nodes 2 \
  --max-nodes 10 \
  --enable-autorepair \
  --enable-autoupgrade \
  --enable-network-policy \
  --addons HttpLoadBalancing,HorizontalPodAutoscaling \
  --workload-pool=PROJECT_ID.svc.id.goog

# Get credentials
gcloud container clusters get-credentials atlasia-prod --region us-central1
```

**Storage Class**: `standard-rwo` (default) or `premium-rwo` for production

#### Amazon Elastic Kubernetes Service (EKS)

```bash
# Create EKS cluster using eksctl
eksctl create cluster \
  --name atlasia-prod \
  --region us-east-1 \
  --nodegroup-name standard-workers \
  --node-type t3.xlarge \
  --nodes 3 \
  --nodes-min 3 \
  --nodes-max 10 \
  --managed

# Update kubeconfig
aws eks update-kubeconfig --region us-east-1 --name atlasia-prod
```

**Storage Class**: `gp3` (recommended) or `gp2`

#### Azure Kubernetes Service (AKS)

```bash
# Create resource group
az group create --name atlasia-prod-rg --location eastus

# Create AKS cluster
az aks create \
  --resource-group atlasia-prod-rg \
  --name atlasia-prod \
  --node-count 3 \
  --node-vm-size Standard_D4s_v3 \
  --enable-cluster-autoscaler \
  --min-count 3 \
  --max-count 10 \
  --network-policy azure \
  --enable-addons monitoring

# Get credentials
az aks get-credentials --resource-group atlasia-prod-rg --name atlasia-prod
```

**Storage Class**: `managed-premium` (recommended)

## Resource Sizing

### Orchestrator (Backend)

| Environment | Replicas | CPU Request | CPU Limit | Memory Request | Memory Limit | HPA Min/Max |
|-------------|----------|-------------|-----------|----------------|--------------|-------------|
| Development | 1        | 250m        | 500m      | 256Mi          | 512Mi        | N/A         |
| Staging     | 2        | 250m        | 1000m     | 256Mi          | 1Gi          | 2-5         |
| Production  | 3        | 500m        | 2000m     | 512Mi          | 2Gi          | 3-10        |

### Frontend

| Environment | Replicas | CPU Request | CPU Limit | Memory Request | Memory Limit |
|-------------|----------|-------------|-----------|----------------|--------------|
| Development | 1        | 50m         | 100m      | 64Mi           | 128Mi        |
| Staging     | 1        | 50m         | 100m      | 64Mi           | 128Mi        |
| Production  | 2        | 100m        | 200m      | 128Mi          | 256Mi        |

### PostgreSQL

| Environment | CPU Request | CPU Limit | Memory Request | Memory Limit | Storage |
|-------------|-------------|-----------|----------------|--------------|---------|
| Development | 125m        | 500m      | 128Mi          | 512Mi        | 10Gi    |
| Staging     | 125m        | 500m      | 128Mi          | 512Mi        | 20Gi    |
| Production  | 250m        | 1000m     | 256Mi          | 2Gi          | 50Gi+   |

### HPA Metrics

The orchestrator supports auto-scaling based on:

1. **CPU Utilization**: Target 70% (production), 80% (staging)
2. **Memory Utilization**: Target 80% (production), 85% (staging)
3. **Custom Metrics**: `ai_tasks_queue_depth` (target: 50 tasks per pod)

For custom metrics, install [Prometheus Adapter](https://github.com/kubernetes-sigs/prometheus-adapter):

```bash
helm install prometheus-adapter prometheus-community/prometheus-adapter \
  --namespace monitoring \
  --set prometheus.url=http://prometheus-server.monitoring.svc
```

## Installation

### 1. Add Dependencies

```bash
# Add Bitnami repository for PostgreSQL
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

### 2. Create Namespace

```bash
kubectl create namespace atlasia
```

### 3. Create Secrets

**Option A: Manual Secret Creation**

```bash
# Create secrets manually
kubectl create secret generic atlasia-secrets \
  --namespace atlasia \
  --from-literal=orchestrator-token='your-secure-token' \
  --from-literal=db-password='your-db-password' \
  --from-literal=jwt-secret='your-jwt-secret-min-256-bits' \
  --from-literal=encryption-key='your-encryption-key-32-bytes' \
  --from-literal=llm-api-key='your-openai-api-key' \
  --from-literal=vault-token='your-vault-token' \
  --dry-run=client -o yaml | kubectl apply -f -
```

**Option B: Using Sealed Secrets (Recommended for GitOps)**

```bash
# Install Sealed Secrets controller
helm install sealed-secrets sealed-secrets/sealed-secrets \
  --namespace kube-system

# Create and seal secrets
kubectl create secret generic atlasia-secrets \
  --namespace atlasia \
  --from-literal=orchestrator-token='your-secure-token' \
  --from-literal=db-password='your-db-password' \
  --from-literal=jwt-secret='your-jwt-secret' \
  --from-literal=encryption-key='your-encryption-key' \
  --from-literal=llm-api-key='your-openai-api-key' \
  --dry-run=client -o yaml | \
  kubeseal --controller-name=sealed-secrets -o yaml > sealed-secrets.yaml

kubectl apply -f sealed-secrets.yaml
```

**Option C: Using External Secrets Operator (Best for Production)**

See [External Secrets documentation](https://external-secrets.io/) for integration with HashiCorp Vault, AWS Secrets Manager, etc.

### 4. Build and Push Docker Images

```bash
# Build orchestrator
cd ai-orchestrator
docker build -t atlasia/ai-orchestrator:0.1.0 .
docker push atlasia/ai-orchestrator:0.1.0

# Build frontend
cd ../frontend
docker build -t atlasia/frontend:0.1.0 .
docker push atlasia/frontend:0.1.0
```

### 5. Install Helm Chart

**Production Deployment:**

```bash
helm install atlasia ./infra/k8s/helm/atlasia \
  --namespace atlasia \
  --values ./infra/k8s/helm/atlasia/values.yaml \
  --set secrets.orchestratorToken='your-token' \
  --set secrets.dbPassword='your-password' \
  --set secrets.jwtSecret='your-jwt-secret' \
  --set secrets.encryptionKey='your-encryption-key' \
  --set secrets.llmApiKey='your-openai-key' \
  --set ingress.hosts[0].host='atlasia.example.com' \
  --set config.cors.allowedOrigins='https://atlasia.example.com'
```

**Staging Deployment:**

```bash
helm install atlasia ./infra/k8s/helm/atlasia \
  --namespace atlasia \
  --values ./infra/k8s/helm/atlasia/values.yaml \
  --values ./infra/k8s/environments/staging/values.yaml \
  --set secrets.orchestratorToken='your-token' \
  --set secrets.dbPassword='your-password' \
  --set secrets.jwtSecret='your-jwt-secret' \
  --set secrets.encryptionKey='your-encryption-key' \
  --set secrets.llmApiKey='your-openai-key'
```

### 6. Verify Deployment

```bash
# Check pod status
kubectl get pods -n atlasia

# Check services
kubectl get svc -n atlasia

# Check ingress
kubectl get ingress -n atlasia

# Watch deployment progress
kubectl rollout status deployment/atlasia-orchestrator -n atlasia
kubectl rollout status deployment/atlasia-frontend -n atlasia
```

## Configuration

### Custom Values Files

Create environment-specific values files:

```yaml
# values-prod.yaml
global:
  environment: production

replicaCount:
  orchestrator: 5
  frontend: 3

orchestrator:
  autoscaling:
    minReplicas: 5
    maxReplicas: 20

config:
  region: us-east-1
  cors:
    allowedOrigins: "https://atlasia.example.com"

ingress:
  hosts:
    - host: atlasia.example.com
```

### Environment Variables

Key configuration via ConfigMap and Secrets:

- **ConfigMap** (`atlasia-config`): Non-sensitive configuration
- **Secret** (`atlasia-secrets`): Credentials and API keys

### Database Migration

Flyway migrations run automatically on startup. To manually run migrations:

```bash
kubectl exec -it deployment/atlasia-orchestrator -n atlasia -- \
  java -jar app.jar --spring.flyway.baseline-on-migrate=true
```

## Deployment Procedures

### Rolling Update

```bash
# Update image version
helm upgrade atlasia ./infra/k8s/helm/atlasia \
  --namespace atlasia \
  --reuse-values \
  --set image.orchestrator.tag=0.2.0 \
  --set image.frontend.tag=0.2.0

# Monitor rollout
kubectl rollout status deployment/atlasia-orchestrator -n atlasia
```

### Rollback

```bash
# View release history
helm history atlasia -n atlasia

# Rollback to previous version
helm rollback atlasia -n atlasia

# Or rollback to specific revision
helm rollback atlasia 2 -n atlasia
```

### Blue-Green Deployment

```bash
# Install new version with different release name
helm install atlasia-green ./infra/k8s/helm/atlasia \
  --namespace atlasia \
  --values values-green.yaml

# Switch traffic (update Ingress)
kubectl patch ingress atlasia -n atlasia --type merge -p '{"spec":{"rules":[...]}}'

# Delete old version
helm uninstall atlasia-blue -n atlasia
```

### Scaling

**Manual Scaling:**

```bash
# Scale orchestrator
kubectl scale deployment/atlasia-orchestrator -n atlasia --replicas=5

# Scale frontend
kubectl scale deployment/atlasia-frontend -n atlasia --replicas=3
```

**Auto-scaling** is configured via HPA for the orchestrator.

### Database Backup

**Manual Backup:**

```bash
# Create backup
kubectl exec -it statefulset/atlasia-postgresql -n atlasia -- \
  pg_dump -U ai -d ai > backup-$(date +%Y%m%d-%H%M%S).sql

# Restore backup
kubectl exec -i statefulset/atlasia-postgresql -n atlasia -- \
  psql -U ai -d ai < backup-20240101-120000.sql
```

**Automated Backups** (using CronJob):

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: postgres-backup
spec:
  schedule: "0 2 * * *"
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - name: backup
            image: postgres:16
            command:
            - /bin/sh
            - -c
            - pg_dump -h atlasia-postgresql -U ai -d ai | gzip > /backup/backup-$(date +\%Y\%m\%d-\%H\%M\%S).sql.gz
            volumeMounts:
            - name: backup
              mountPath: /backup
          volumes:
          - name: backup
            persistentVolumeClaim:
              claimName: postgres-backup-pvc
          restartPolicy: OnFailure
```

## Monitoring and Observability

### Health Checks

```bash
# Check orchestrator health
kubectl port-forward svc/atlasia-orchestrator -n atlasia 8080:8080
curl http://localhost:8080/actuator/health

# Check readiness
curl http://localhost:8080/actuator/health/readiness
```

### Logs

```bash
# View orchestrator logs
kubectl logs -n atlasia -l app.kubernetes.io/component=orchestrator --tail=100 -f

# View frontend logs
kubectl logs -n atlasia -l app.kubernetes.io/component=frontend --tail=100 -f

# View PostgreSQL logs
kubectl logs -n atlasia statefulset/atlasia-postgresql -f
```

### Metrics

The orchestrator exposes Prometheus metrics at `/actuator/prometheus`.

**Install Prometheus and Grafana:**

```bash
# Add Prometheus community charts
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts

# Install Prometheus
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --create-namespace

# Access Grafana
kubectl port-forward -n monitoring svc/prometheus-grafana 3000:80
# Default credentials: admin/prom-operator
```

### Tracing

Configure OpenTelemetry for distributed tracing:

```bash
# Install Jaeger
helm repo add jaegertracing https://jaegertracing.github.io/helm-charts
helm install jaeger jaegertracing/jaeger \
  --namespace monitoring

# Update values to enable tracing
helm upgrade atlasia ./infra/k8s/helm/atlasia \
  --namespace atlasia \
  --reuse-values \
  --set config.tracing.otlpEndpoint=http://jaeger-collector.monitoring:4317
```

## Security

### Network Policies

Network policies are enabled by default to restrict pod-to-pod communication:

- Orchestrator can only communicate with PostgreSQL
- Frontend can only communicate with Orchestrator
- All pods can access DNS

### Pod Security Standards

Pods run with restricted security context:

```yaml
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  allowPrivilegeEscalation: false
  capabilities:
    drop:
      - ALL
  readOnlyRootFilesystem: true
```

### TLS/SSL

**Install cert-manager:**

```bash
helm repo add jetstack https://charts.jetstack.io
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager \
  --create-namespace \
  --set installCRDs=true
```

**Create ClusterIssuer:**

```yaml
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@example.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
    - http01:
        ingress:
          class: nginx
```

### RBAC

Create service account with minimal permissions:

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: atlasia-role
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: atlasia-rolebinding
subjects:
- kind: ServiceAccount
  name: atlasia
roleRef:
  kind: Role
  name: atlasia-role
  apiGroup: rbac.authorization.k8s.io
```

## Troubleshooting

### Pod CrashLoopBackOff

```bash
# Check pod status
kubectl describe pod <pod-name> -n atlasia

# Check logs
kubectl logs <pod-name> -n atlasia --previous

# Common issues:
# 1. Database connection failure - verify DB_URL and credentials
# 2. Missing secrets - ensure all required secrets are created
# 3. Resource limits - check if OOMKilled
```

### Database Connection Issues

```bash
# Test database connectivity
kubectl run -it --rm debug --image=postgres:16 --restart=Never -n atlasia -- \
  psql -h atlasia-postgresql -U ai -d ai

# Check PostgreSQL logs
kubectl logs statefulset/atlasia-postgresql -n atlasia
```

### Ingress Not Working

```bash
# Check ingress status
kubectl describe ingress atlasia -n atlasia

# Check NGINX ingress controller
kubectl logs -n ingress-nginx deployment/ingress-nginx-controller

# Verify DNS resolution
nslookup atlasia.example.com
```

### Performance Issues

```bash
# Check resource usage
kubectl top pods -n atlasia
kubectl top nodes

# Check HPA status
kubectl get hpa -n atlasia
kubectl describe hpa atlasia-orchestrator -n atlasia

# Review metrics
kubectl port-forward svc/atlasia-orchestrator -n atlasia 8080:8080
curl http://localhost:8080/actuator/metrics
```

## Local Development

### Minikube

```bash
# Start Minikube
minikube start --cpus=4 --memory=8192 --disk-size=50g

# Enable ingress
minikube addons enable ingress

# Deploy Atlasia
helm install atlasia ./infra/k8s/helm/atlasia \
  --namespace atlasia \
  --create-namespace \
  --values ./infra/k8s/helm/atlasia/values.yaml \
  --set ingress.enabled=false \
  --set postgresql.primary.persistence.size=10Gi

# Access services
minikube service atlasia-orchestrator -n atlasia
minikube service atlasia-frontend -n atlasia
```

### Kind (Kubernetes in Docker)

```bash
# Create Kind cluster
cat <<EOF | kind create cluster --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  kubeadmConfigPatches:
  - |
    kind: InitConfiguration
    nodeRegistration:
      kubeletExtraArgs:
        node-labels: "ingress-ready=true"
  extraPortMappings:
  - containerPort: 80
    hostPort: 80
    protocol: TCP
  - containerPort: 443
    hostPort: 443
    protocol: TCP
- role: worker
- role: worker
EOF

# Install NGINX Ingress
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Deploy Atlasia
helm install atlasia ./infra/k8s/helm/atlasia \
  --namespace atlasia \
  --create-namespace \
  --values ./infra/k8s/helm/atlasia/values.yaml \
  --set ingress.hosts[0].host=localhost

# Access via localhost
curl http://localhost
```

### Validation Steps

After deploying to local cluster:

1. **Check all pods are running:**
   ```bash
   kubectl get pods -n atlasia
   ```

2. **Verify services:**
   ```bash
   kubectl get svc -n atlasia
   ```

3. **Test orchestrator health:**
   ```bash
   kubectl port-forward svc/atlasia-orchestrator -n atlasia 8080:8080
   curl http://localhost:8080/actuator/health
   ```

4. **Test frontend:**
   ```bash
   kubectl port-forward svc/atlasia-frontend -n atlasia 8081:80
   curl http://localhost:8081
   ```

5. **Test database connectivity:**
   ```bash
   kubectl port-forward svc/atlasia-postgresql -n atlasia 5432:5432
   psql -h localhost -U ai -d ai
   ```

6. **Verify ingress (if enabled):**
   ```bash
   kubectl get ingress -n atlasia
   curl -H "Host: localhost" http://localhost
   ```

## Additional Resources

- [Helm Documentation](https://helm.sh/docs/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [NGINX Ingress Controller](https://kubernetes.github.io/ingress-nginx/)
- [cert-manager](https://cert-manager.io/)
- [Prometheus Operator](https://prometheus-operator.dev/)
- [External Secrets Operator](https://external-secrets.io/)

## Support

For issues and questions:
- GitHub Issues: https://github.com/atlasia/ai-orchestrator/issues
- Documentation: https://github.com/atlasia/ai-orchestrator/docs
