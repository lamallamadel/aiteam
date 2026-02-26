#!/bin/bash
set -e

echo "=== Atlasia Kubernetes Validation for Kind ==="
echo ""

# Check prerequisites
echo "Checking prerequisites..."
command -v kubectl >/dev/null 2>&1 || { echo "kubectl is required but not installed. Aborting." >&2; exit 1; }
command -v helm >/dev/null 2>&1 || { echo "helm is required but not installed. Aborting." >&2; exit 1; }
command -v kind >/dev/null 2>&1 || { echo "kind is required but not installed. Aborting." >&2; exit 1; }

CLUSTER_NAME="${CLUSTER_NAME:-atlasia-local}"

# Check if cluster exists
if ! kind get clusters | grep -q "^${CLUSTER_NAME}$"; then
    echo "Creating Kind cluster: $CLUSTER_NAME..."
    cat <<EOF | kind create cluster --name $CLUSTER_NAME --config=-
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
else
    echo "Kind cluster '$CLUSTER_NAME' already exists"
    kubectl config use-context kind-$CLUSTER_NAME
fi

# Install NGINX Ingress
echo "Installing NGINX Ingress Controller..."
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

# Wait for ingress controller
echo "Waiting for ingress controller..."
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s

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
ORCHESTRATOR_POD=$(kubectl get pod -n atlasia -l app.kubernetes.io/component=orchestrator -o jsonpath='{.items[0].metadata.name}')
HEALTH_STATUS=$(kubectl exec -n atlasia $ORCHESTRATOR_POD -- wget -qO- http://localhost:8080/actuator/health 2>/dev/null || echo "FAILED")

if echo "$HEALTH_STATUS" | grep -q "UP"; then
    echo "✓ Orchestrator health check: PASSED"
else
    echo "✗ Orchestrator health check: FAILED"
fi

# Check frontend
echo "Checking frontend..."
FRONTEND_POD=$(kubectl get pod -n atlasia -l app.kubernetes.io/component=frontend -o jsonpath='{.items[0].metadata.name}')
FRONTEND_STATUS=$(kubectl exec -n atlasia $FRONTEND_POD -- wget -qO- http://localhost:80/ 2>/dev/null || echo "FAILED")

if echo "$FRONTEND_STATUS" | grep -q "<!doctype html>"; then
    echo "✓ Frontend health check: PASSED"
else
    echo "✗ Frontend health check: FAILED"
fi

# Test ingress (if available)
echo ""
echo "Testing ingress..."
sleep 5
INGRESS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/ 2>/dev/null || echo "000")

if [ "$INGRESS_STATUS" = "200" ]; then
    echo "✓ Ingress check: PASSED"
else
    echo "✗ Ingress check: FAILED (HTTP $INGRESS_STATUS)"
fi

# Display access information
echo ""
echo "=== Access Information ==="
echo ""
echo "Access via Ingress:"
echo "  Frontend:     http://localhost/"
echo "  API:          http://localhost/api"
echo "  Health:       http://localhost/actuator/health"
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
echo "Cleanup:"
echo "  kind delete cluster --name $CLUSTER_NAME"
echo ""
echo "=== Validation Complete ==="
