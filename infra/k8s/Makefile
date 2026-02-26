# Atlasia Kubernetes Deployment Makefile

.PHONY: help
help: ## Display this help message
	@echo "Atlasia Kubernetes Deployment"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'

.PHONY: deps
deps: ## Add required Helm repositories
	helm repo add bitnami https://charts.bitnami.com/bitnami
	helm repo update

.PHONY: lint
lint: ## Lint Helm chart
	helm lint ./helm/atlasia

.PHONY: template
template: ## Generate Kubernetes manifests from Helm chart
	helm template atlasia ./helm/atlasia --values ./helm/atlasia/values.yaml

.PHONY: template-staging
template-staging: ## Generate manifests for staging environment
	helm template atlasia ./helm/atlasia \
		--values ./helm/atlasia/values.yaml \
		--values ./environments/staging/values.yaml

.PHONY: template-local
template-local: ## Generate manifests for local environment
	helm template atlasia ./helm/atlasia \
		--values ./helm/atlasia/values.yaml \
		--values ./examples/local-values.yaml

.PHONY: create-namespace
create-namespace: ## Create atlasia namespace
	kubectl create namespace atlasia --dry-run=client -o yaml | kubectl apply -f -

.PHONY: create-secrets-template
create-secrets-template: ## Copy secrets template for manual editing
	@if [ -f secrets.yaml ]; then \
		echo "secrets.yaml already exists. Remove it first to recreate."; \
	else \
		cp examples/secrets-template.yaml secrets.yaml; \
		echo "Created secrets.yaml from template. Edit it and apply with 'make apply-secrets'"; \
	fi

.PHONY: apply-secrets
apply-secrets: ## Apply secrets from secrets.yaml
	@if [ ! -f secrets.yaml ]; then \
		echo "secrets.yaml not found. Run 'make create-secrets-template' first."; \
		exit 1; \
	fi
	kubectl apply -f secrets.yaml

.PHONY: install
install: deps create-namespace ## Install Helm chart (production)
	helm install atlasia ./helm/atlasia \
		--namespace atlasia \
		--values ./helm/atlasia/values.yaml

.PHONY: install-staging
install-staging: deps create-namespace ## Install Helm chart (staging)
	helm install atlasia ./helm/atlasia \
		--namespace atlasia \
		--values ./helm/atlasia/values.yaml \
		--values ./environments/staging/values.yaml

.PHONY: install-local
install-local: deps create-namespace ## Install Helm chart (local development)
	helm install atlasia ./helm/atlasia \
		--namespace atlasia \
		--values ./helm/atlasia/values.yaml \
		--values ./examples/local-values.yaml

.PHONY: upgrade
upgrade: ## Upgrade Helm release (production)
	helm upgrade atlasia ./helm/atlasia \
		--namespace atlasia \
		--values ./helm/atlasia/values.yaml

.PHONY: upgrade-staging
upgrade-staging: ## Upgrade Helm release (staging)
	helm upgrade atlasia ./helm/atlasia \
		--namespace atlasia \
		--values ./helm/atlasia/values.yaml \
		--values ./environments/staging/values.yaml

.PHONY: uninstall
uninstall: ## Uninstall Helm release
	helm uninstall atlasia --namespace atlasia

.PHONY: status
status: ## Show Helm release status
	helm status atlasia --namespace atlasia

.PHONY: get-pods
get-pods: ## Get all pods in atlasia namespace
	kubectl get pods -n atlasia

.PHONY: get-all
get-all: ## Get all resources in atlasia namespace
	kubectl get all -n atlasia

.PHONY: logs-orchestrator
logs-orchestrator: ## Tail orchestrator logs
	kubectl logs -n atlasia -l app.kubernetes.io/component=orchestrator --tail=100 -f

.PHONY: logs-frontend
logs-frontend: ## Tail frontend logs
	kubectl logs -n atlasia -l app.kubernetes.io/component=frontend --tail=100 -f

.PHONY: logs-postgresql
logs-postgresql: ## Tail PostgreSQL logs
	kubectl logs -n atlasia statefulset/atlasia-postgresql -f

.PHONY: port-forward-orchestrator
port-forward-orchestrator: ## Port forward to orchestrator
	kubectl port-forward -n atlasia svc/atlasia-orchestrator 8080:8080

.PHONY: port-forward-frontend
port-forward-frontend: ## Port forward to frontend
	kubectl port-forward -n atlasia svc/atlasia-frontend 8081:80

.PHONY: port-forward-db
port-forward-db: ## Port forward to PostgreSQL
	kubectl port-forward -n atlasia svc/atlasia-postgresql 5432:5432

.PHONY: health-orchestrator
health-orchestrator: ## Check orchestrator health
	@kubectl port-forward -n atlasia svc/atlasia-orchestrator 18080:8080 >/dev/null 2>&1 & \
	PID=$$!; \
	sleep 2; \
	curl -s http://localhost:18080/actuator/health | jq .; \
	kill $$PID 2>/dev/null || true

.PHONY: describe-orchestrator
describe-orchestrator: ## Describe orchestrator deployment
	kubectl describe deployment atlasia-orchestrator -n atlasia

.PHONY: describe-frontend
describe-frontend: ## Describe frontend deployment
	kubectl describe deployment atlasia-frontend -n atlasia

.PHONY: describe-postgresql
describe-postgresql: ## Describe PostgreSQL statefulset
	kubectl describe statefulset atlasia-postgresql -n atlasia

.PHONY: exec-orchestrator
exec-orchestrator: ## Execute shell in orchestrator pod
	kubectl exec -it -n atlasia deployment/atlasia-orchestrator -- /bin/sh

.PHONY: exec-db
exec-db: ## Connect to PostgreSQL database
	kubectl exec -it -n atlasia statefulset/atlasia-postgresql -- psql -U ai -d ai

.PHONY: restart-orchestrator
restart-orchestrator: ## Restart orchestrator deployment
	kubectl rollout restart deployment/atlasia-orchestrator -n atlasia

.PHONY: restart-frontend
restart-frontend: ## Restart frontend deployment
	kubectl rollout restart deployment/atlasia-frontend -n atlasia

.PHONY: scale-orchestrator
scale-orchestrator: ## Scale orchestrator (usage: make scale-orchestrator REPLICAS=5)
	kubectl scale deployment/atlasia-orchestrator -n atlasia --replicas=$(REPLICAS)

.PHONY: validate-minikube
validate-minikube: ## Run Minikube validation script
	@chmod +x scripts/validate-minikube.sh
	@./scripts/validate-minikube.sh

.PHONY: validate-kind
validate-kind: ## Run Kind validation script
	@chmod +x scripts/validate-kind.sh
	@./scripts/validate-kind.sh

.PHONY: build-images
build-images: ## Build Docker images
	cd ../../ai-orchestrator && docker build -t atlasia/ai-orchestrator:latest .
	cd ../../frontend && docker build -t atlasia/frontend:latest .

.PHONY: load-images-kind
load-images-kind: build-images ## Build and load images into Kind cluster
	kind load docker-image atlasia/ai-orchestrator:latest
	kind load docker-image atlasia/frontend:latest

.PHONY: package
package: ## Package Helm chart
	helm package ./helm/atlasia

.PHONY: clean
clean: ## Clean up generated files
	rm -f *.tgz
	rm -f secrets.yaml
	rm -f sealed-secrets.yaml

.PHONY: dry-run
dry-run: ## Dry run Helm install
	helm install atlasia ./helm/atlasia \
		--namespace atlasia \
		--values ./helm/atlasia/values.yaml \
		--dry-run --debug

.PHONY: diff
diff: ## Show diff between current and new release
	helm diff upgrade atlasia ./helm/atlasia \
		--namespace atlasia \
		--values ./helm/atlasia/values.yaml || echo "Install helm-diff plugin: helm plugin install https://github.com/databus23/helm-diff"

.DEFAULT_GOAL := help
