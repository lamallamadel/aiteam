# Quick Start Guide - Kubernetes Deployment

This guide will help you deploy Atlasia AI Orchestrator to a local Kubernetes cluster in under 10 minutes.

## Prerequisites

Choose one of the following local Kubernetes environments:

### Option 1: Minikube (Recommended for beginners)

```bash
# macOS
brew install minikube

# Windows (using Chocolatey)
choco install minikube

# Linux
curl -LO https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64
sudo install minikube-linux-amd64 /usr/local/bin/minikube
```

### Option 2: Kind (Kubernetes in Docker)

```bash
# macOS
brew install kind

# Windows (using Chocolatey)
choco install kind

# Linux
curl -Lo ./kind https://kind.sigs.k8s.io/dl/v0.20.0/kind-linux-amd64
chmod +x ./kind
sudo mv ./kind /usr/local/bin/kind
```

### Common Requirements

```bash
# Install kubectl
# macOS
brew install kubectl

# Windows (using Chocolatey)
choco install kubernetes-cli

# Linux
curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"
sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl

# Install Helm
# macOS
brew install helm

# Windows (using Chocolatey)
choco install kubernetes-helm

# Linux
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
```

## Quick Deploy with Scripts

We provide automated validation scripts for quick deployment:

### Using Kind (Recommended)

**Linux/macOS:**
```bash
cd infra/k8s
chmod +x scripts/validate-kind.sh
./scripts/validate-kind.sh
```

**Windows PowerShell:**
```powershell
cd infra/k8s
.\scripts\validate-kind.ps1
```

### Using Minikube

```bash
cd infra/k8s
chmod +x scripts/validate-minikube.sh
./scripts/validate-minikube.sh
```

The scripts will:
1. Create a local Kubernetes cluster
2. Install NGINX Ingress Controller
3. Deploy PostgreSQL with persistent storage
4. Deploy the Atlasia orchestrator and frontend
5. Validate all services are healthy
6. Display access information

## Manual Deployment

If you prefer manual control:

### Step 1: Start Your Cluster

**Minikube:**
```bash
minikube start --cpus=4 --memory=8192 --disk-size=50g
minikube addons enable ingress
```

**Kind:**
```bash
cat <<EOF | kind create cluster --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
  extraPortMappings:
  - containerPort: 80
    hostPort: 80
  - containerPort: 443
    hostPort: 443
- role: worker
- role: worker
EOF

# Install ingress controller
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
```

### Step 2: Create Namespace

```bash
kubectl create namespace atlasia
```

### Step 3: Create Secrets

**Quick test secrets (NOT for production):**
```bash
kubectl create secret generic atlasia-secrets \
  --namespace atlasia \
  --from-literal=orchestrator-token='test-token-for-local-dev' \
  --from-literal=db-password='test-db-password' \
  --from-literal=jwt-secret='test-jwt-secret-min-256-bits-long-key-for-security' \
  --from-literal=encryption-key='test-encryption-key-32-bytes!!' \
  --from-literal=llm-api-key='your-openai-api-key-here'
```

**Production secrets:**
Use the template in `examples/secrets-template.yaml` to create properly secured secrets.

### Step 4: Add Helm Repository

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

### Step 5: Build and Load Images (for local development)

**For Kind:**
```bash
# Build images
cd ai-orchestrator
docker build -t atlasia/ai-orchestrator:latest .

cd ../frontend
docker build -t atlasia/frontend:latest .

# Load into Kind
kind load docker-image atlasia/ai-orchestrator:latest
kind load docker-image atlasia/frontend:latest
```

**For Minikube:**
```bash
# Point to Minikube's Docker daemon
eval $(minikube docker-env)

# Build images (they'll be available in Minikube)
cd ai-orchestrator
docker build -t atlasia/ai-orchestrator:latest .

cd ../frontend
docker build -t atlasia/frontend:latest .
```

### Step 6: Install Helm Chart

```bash
cd infra/k8s
helm install atlasia ./helm/atlasia \
  --namespace atlasia \
  --values ./helm/atlasia/values.yaml \
  --values ./examples/local-values.yaml \
  --wait
```

### Step 7: Verify Deployment

```bash
# Check pods
kubectl get pods -n atlasia

# Check services
kubectl get svc -n atlasia

# Check logs
kubectl logs -n atlasia -l app.kubernetes.io/component=orchestrator --tail=50
```

## Accessing the Application

### Via Ingress (Kind)

If using Kind with port mapping:
```bash
# Access frontend
open http://localhost

# Access API
curl http://localhost/actuator/health
```

### Via Port Forwarding

For any cluster type:
```bash
# Forward orchestrator
kubectl port-forward -n atlasia svc/atlasia-orchestrator 8080:8080

# Forward frontend (in another terminal)
kubectl port-forward -n atlasia svc/atlasia-frontend 8081:80

# Access in browser
open http://localhost:8081
```

### Via Minikube Service

```bash
# Get service URLs
minikube service list -n atlasia

# Open frontend
minikube service atlasia-frontend -n atlasia

# Open orchestrator
minikube service atlasia-orchestrator -n atlasia
```

## Testing the Deployment

### Health Checks

```bash
# Orchestrator health
kubectl port-forward -n atlasia svc/atlasia-orchestrator 8080:8080 &
curl http://localhost:8080/actuator/health

# Expected output:
# {"status":"UP"}
```

### Database Connection

```bash
# Connect to PostgreSQL
kubectl port-forward -n atlasia svc/atlasia-postgresql 5432:5432 &
psql -h localhost -U ai -d ai
# Password: test-db-password
```

### View Logs

```bash
# Orchestrator logs
kubectl logs -n atlasia -l app.kubernetes.io/component=orchestrator --tail=100 -f

# Frontend logs
kubectl logs -n atlasia -l app.kubernetes.io/component=frontend --tail=100 -f

# PostgreSQL logs
kubectl logs -n atlasia statefulset/atlasia-postgresql -f
```

## Making Changes

### Update Configuration

```bash
# Edit values
nano helm/atlasia/values.yaml

# Upgrade deployment
helm upgrade atlasia ./helm/atlasia \
  --namespace atlasia \
  --values ./helm/atlasia/values.yaml \
  --values ./examples/local-values.yaml
```

### Update Images

After rebuilding images:

**Kind:**
```bash
docker build -t atlasia/ai-orchestrator:latest ai-orchestrator/
kind load docker-image atlasia/ai-orchestrator:latest
kubectl rollout restart deployment/atlasia-orchestrator -n atlasia
```

**Minikube:**
```bash
eval $(minikube docker-env)
docker build -t atlasia/ai-orchestrator:latest ai-orchestrator/
kubectl rollout restart deployment/atlasia-orchestrator -n atlasia
```

## Troubleshooting

### Pods Not Starting

```bash
# Describe pod to see events
kubectl describe pod <pod-name> -n atlasia

# Check logs
kubectl logs <pod-name> -n atlasia

# Common issues:
# 1. Image pull errors - ensure images are built and loaded
# 2. Secret not found - verify secrets are created
# 3. Resource constraints - check cluster resources
```

### Connection Issues

```bash
# Check if PostgreSQL is ready
kubectl get pods -n atlasia -l app.kubernetes.io/name=postgresql

# Test database connectivity
kubectl run -it --rm debug --image=postgres:16 --restart=Never -n atlasia -- \
  psql -h atlasia-postgresql -U ai -d ai
```

### Ingress Not Working

```bash
# Check ingress controller
kubectl get pods -n ingress-nginx

# Check ingress resource
kubectl describe ingress atlasia -n atlasia

# For Kind, ensure ports are mapped correctly
```

## Cleanup

### Remove Deployment

```bash
# Uninstall Helm release
helm uninstall atlasia -n atlasia

# Delete namespace
kubectl delete namespace atlasia
```

### Delete Cluster

**Minikube:**
```bash
minikube delete
```

**Kind:**
```bash
kind delete cluster --name atlasia-local
```

## Next Steps

- Read the [full README](README.md) for production deployment
- Configure [monitoring](README.md#monitoring-and-observability)
- Set up [TLS certificates](README.md#tlsssl)
- Review [security best practices](README.md#security)
- Deploy to cloud providers ([GKE](README.md#google-kubernetes-engine-gke), [EKS](README.md#amazon-elastic-kubernetes-service-eks), [AKS](README.md#azure-kubernetes-service-aks))

## Getting Help

- Check the [troubleshooting section](README.md#troubleshooting)
- Review [Kubernetes documentation](https://kubernetes.io/docs/)
- File an issue on GitHub
