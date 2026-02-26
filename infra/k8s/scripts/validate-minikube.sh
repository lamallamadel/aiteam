#!/bin/bash
set -e

echo "=== Atlasia Kubernetes Validation for Minikube ==="
echo ""

# Check prerequisites
echo "Checking prerequisites..."
command -v kubectl >/dev/null 2>&1 || { echo "kubectl is required but not installed. Aborting." >&2; exit 1; }
command -v helm >/dev/null 2>&1 || { echo "helm is required but not installed. Aborting." >&2; exit 1; }
command -v minikube >/dev/null 2>&1 || { echo "minikube is required but not installed. Aborting." >&2; exit 1; }

# Check if minikube is running
if ! minikube status >/dev/null 2>&1; then
    echo "Starting Minikube..."
    minikube start --cpus=4 --memory=8192 --disk-size=50g
else
    echo "Minikube is already running"
fi

# Enable ingress addon
echo "Enabling ingress addon..."
minikube addons enable ingress

# Create namespace
echo "Creating namespace..."
kubectl create namespace atlasia --dry-run=client -o yaml | kubectl apply -f -

# Generate test secrets
echo "Creating test secrets..."
kubectl create secret generic atlasia-secrets \
  --namespace atlasia \
  --from-literal=orchestrator-token='test-token-for-local-dev' \
  --from-literal=db-password='test-db-password' \
  --from-literal=jwt-secret='test-jwt-secret-min-256-bits-long-key-for-security' \
  --from-literal=encryption-key='test-encryption-key-32-bytes!!' \
  --from-literal=llm-api-key='test-openai-api-key' \
  --from-literal=vault-token='' \
  --from-literal=llm-fallback-api-key='' \
  --from-literal=github-webhook-secret='' \
  --from-literal=github-private-key-path='' \
  --from-literal=oauth2-github-client-id='' \
  --from-literal=oauth2-github-client-secret='' \
  --from-literal=oauth2-google-client-id='' \
  --from-literal=oauth2-google-client-secret='' \
  --from-literal=oauth2-gitlab-client-id='' \
  --from-literal=oauth2-gitlab-client-secret='' \
  --from-literal=mail-username='' \
  --from-literal=mail-password='' \
  --dry-run=client -o yaml | kubectl apply -f -

# Add Bitnami repo
echo "Adding Helm repositories..."
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# Install or upgrade chart
echo "Installing Atlasia Helm chart..."
helm upgrade --install atlasia ./helm/atlasia \
  --namespace atlasia \
  --values ./helm/atlasia/values.yaml \
  --values ./examples/local-values.yaml \
  --wait \
  --timeout 10m

# Wait for deployments
echo "Waiting for deployments to be ready..."
kubectl wait --for=condition=available --timeout=300s \
  deployment/atlasia-orchestrator \
  deployment/atlasia-frontend \
  -n atlasia

# Wait for PostgreSQL StatefulSet
kubectl wait --for=condition=ready --timeout=300s \
  pod -l app.kubernetes.io/name=postgresql \
  -n atlasia

# Display status
echo ""
echo "=== Deployment Status ==="
kubectl get pods -n atlasia
echo ""
kubectl get svc -n atlasia
echo ""
kubectl get ingress -n atlasia

# Health checks
echo ""
echo "=== Health Checks ==="
echo "Waiting for services to be fully ready..."
sleep 10

# Check orchestrator health
echo "Checking orchestrator health..."
kubectl port-forward -n atlasia svc/atlasia-orchestrator 18080:8080 >/dev/null 2>&1 &
PF_PID=$!
sleep 3
HEALTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/actuator/health || echo "000")
kill $PF_PID 2>/dev/null || true

if [ "$HEALTH_STATUS" = "200" ]; then
    echo "✓ Orchestrator health check: PASSED"
else
    echo "✗ Orchestrator health check: FAILED (HTTP $HEALTH_STATUS)"
fi

# Check frontend
echo "Checking frontend..."
kubectl port-forward -n atlasia svc/atlasia-frontend 18081:80 >/dev/null 2>&1 &
PF_PID=$!
sleep 3
FRONTEND_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:18081/ || echo "000")
kill $PF_PID 2>/dev/null || true

if [ "$FRONTEND_STATUS" = "200" ]; then
    echo "✓ Frontend health check: PASSED"
else
    echo "✗ Frontend health check: FAILED (HTTP $FRONTEND_STATUS)"
fi

# Display access information
echo ""
echo "=== Access Information ==="
echo "Minikube IP: $(minikube ip)"
echo ""
echo "Access services:"
echo "  Orchestrator: minikube service atlasia-orchestrator -n atlasia"
echo "  Frontend:     minikube service atlasia-frontend -n atlasia"
echo ""
echo "Port forwarding commands:"
echo "  Orchestrator: kubectl port-forward -n atlasia svc/atlasia-orchestrator 8080:8080"
echo "  Frontend:     kubectl port-forward -n atlasia svc/atlasia-frontend 8081:80"
echo "  PostgreSQL:   kubectl port-forward -n atlasia svc/atlasia-postgresql 5432:5432"
echo ""
echo "View logs:"
echo "  Orchestrator: kubectl logs -n atlasia -l app.kubernetes.io/component=orchestrator --tail=50 -f"
echo "  Frontend:     kubectl logs -n atlasia -l app.kubernetes.io/component=frontend --tail=50 -f"
echo ""
echo "=== Validation Complete ==="
