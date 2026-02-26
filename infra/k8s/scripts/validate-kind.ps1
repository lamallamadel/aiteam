# PowerShell validation script for Kind on Windows
param(
    [string]$ClusterName = "atlasia-local"
)

Write-Host "=== Atlasia Kubernetes Validation for Kind (Windows) ===" -ForegroundColor Green
Write-Host ""

# Check prerequisites
Write-Host "Checking prerequisites..."
$commands = @("kubectl", "helm", "kind")
foreach ($cmd in $commands) {
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Write-Host "$cmd is required but not installed. Aborting." -ForegroundColor Red
        exit 1
    }
}

# Check if cluster exists
$existingClusters = kind get clusters
if ($existingClusters -notcontains $ClusterName) {
    Write-Host "Creating Kind cluster: $ClusterName..."
    
    $clusterConfig = @"
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
"@
    
    $clusterConfig | kind create cluster --name $ClusterName --config=-
} else {
    Write-Host "Kind cluster '$ClusterName' already exists"
    kubectl config use-context "kind-$ClusterName"
}

# Install NGINX Ingress
Write-Host "Installing NGINX Ingress Controller..."
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Wait for ingress controller
Write-Host "Waiting for ingress controller..."
kubectl wait --namespace ingress-nginx `
  --for=condition=ready pod `
  --selector=app.kubernetes.io/component=controller `
  --timeout=90s

# Create namespace
Write-Host "Creating namespace..."
kubectl create namespace atlasia --dry-run=client -o yaml | kubectl apply -f -

# Generate test secrets
Write-Host "Creating test secrets..."
kubectl create secret generic atlasia-secrets `
  --namespace atlasia `
  --from-literal=orchestrator-token='test-token-for-local-dev' `
  --from-literal=db-password='test-db-password' `
  --from-literal=jwt-secret='test-jwt-secret-min-256-bits-long-key-for-security' `
  --from-literal=encryption-key='test-encryption-key-32-bytes!!' `
  --from-literal=llm-api-key='test-openai-api-key' `
  --from-literal=vault-token='' `
  --from-literal=llm-fallback-api-key='' `
  --from-literal=github-webhook-secret='' `
  --from-literal=github-private-key-path='' `
  --from-literal=oauth2-github-client-id='' `
  --from-literal=oauth2-github-client-secret='' `
  --from-literal=oauth2-google-client-id='' `
  --from-literal=oauth2-google-client-secret='' `
  --from-literal=oauth2-gitlab-client-id='' `
  --from-literal=oauth2-gitlab-client-secret='' `
  --from-literal=mail-username='' `
  --from-literal=mail-password='' `
  --dry-run=client -o yaml | kubectl apply -f -

# Add Bitnami repo
Write-Host "Adding Helm repositories..."
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# Install or upgrade chart
Write-Host "Installing Atlasia Helm chart..."
$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
$helmPath = Join-Path (Split-Path -Parent $scriptPath) "helm\atlasia"
$examplesPath = Join-Path (Split-Path -Parent $scriptPath) "examples\local-values.yaml"

helm upgrade --install atlasia $helmPath `
  --namespace atlasia `
  --values "$helmPath\values.yaml" `
  --values $examplesPath `
  --wait `
  --timeout 10m

# Wait for deployments
Write-Host "Waiting for deployments to be ready..."
kubectl wait --for=condition=available --timeout=300s `
  deployment/atlasia-orchestrator `
  deployment/atlasia-frontend `
  -n atlasia

# Wait for PostgreSQL StatefulSet
kubectl wait --for=condition=ready --timeout=300s `
  pod -l app.kubernetes.io/name=postgresql `
  -n atlasia

# Display status
Write-Host ""
Write-Host "=== Deployment Status ===" -ForegroundColor Green
kubectl get pods -n atlasia
Write-Host ""
kubectl get svc -n atlasia
Write-Host ""
kubectl get ingress -n atlasia

# Health checks
Write-Host ""
Write-Host "=== Health Checks ===" -ForegroundColor Green
Write-Host "Waiting for services to be fully ready..."
Start-Sleep -Seconds 10

# Check orchestrator health
Write-Host "Checking orchestrator health..."
$orchestratorPod = kubectl get pod -n atlasia -l app.kubernetes.io/component=orchestrator -o jsonpath='{.items[0].metadata.name}'
try {
    $healthResult = kubectl exec -n atlasia $orchestratorPod -- wget -qO- http://localhost:8080/actuator/health 2>$null
    if ($healthResult -like "*UP*") {
        Write-Host "✓ Orchestrator health check: PASSED" -ForegroundColor Green
    } else {
        Write-Host "✗ Orchestrator health check: FAILED" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ Orchestrator health check: FAILED" -ForegroundColor Red
}

# Check frontend
Write-Host "Checking frontend..."
$frontendPod = kubectl get pod -n atlasia -l app.kubernetes.io/component=frontend -o jsonpath='{.items[0].metadata.name}'
try {
    $frontendResult = kubectl exec -n atlasia $frontendPod -- wget -qO- http://localhost:80/ 2>$null
    if ($frontendResult -like "*<!doctype html>*") {
        Write-Host "✓ Frontend health check: PASSED" -ForegroundColor Green
    } else {
        Write-Host "✗ Frontend health check: FAILED" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ Frontend health check: FAILED" -ForegroundColor Red
}

# Test ingress
Write-Host ""
Write-Host "Testing ingress..."
Start-Sleep -Seconds 5
try {
    $response = Invoke-WebRequest -Uri "http://localhost/" -UseBasicParsing -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Write-Host "✓ Ingress check: PASSED" -ForegroundColor Green
    } else {
        Write-Host "✗ Ingress check: FAILED (HTTP $($response.StatusCode))" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ Ingress check: FAILED" -ForegroundColor Red
}

# Display access information
Write-Host ""
Write-Host "=== Access Information ===" -ForegroundColor Green
Write-Host ""
Write-Host "Access via Ingress:"
Write-Host "  Frontend:     http://localhost/"
Write-Host "  API:          http://localhost/api"
Write-Host "  Health:       http://localhost/actuator/health"
Write-Host ""
Write-Host "Port forwarding commands:"
Write-Host "  Orchestrator: kubectl port-forward -n atlasia svc/atlasia-orchestrator 8080:8080"
Write-Host "  Frontend:     kubectl port-forward -n atlasia svc/atlasia-frontend 8081:80"
Write-Host "  PostgreSQL:   kubectl port-forward -n atlasia svc/atlasia-postgresql 5432:5432"
Write-Host ""
Write-Host "View logs:"
Write-Host "  Orchestrator: kubectl logs -n atlasia -l app.kubernetes.io/component=orchestrator --tail=50 -f"
Write-Host "  Frontend:     kubectl logs -n atlasia -l app.kubernetes.io/component=frontend --tail=50 -f"
Write-Host ""
Write-Host "Cleanup:"
Write-Host "  kind delete cluster --name $ClusterName"
Write-Host ""
Write-Host "=== Validation Complete ===" -ForegroundColor Green
