---
name: devops-cicd
description: >
  Configure and optimize DevOps pipelines, Docker containers, Kubernetes deployments, and CI/CD
  workflows for Java/Spring Boot and Angular projects. Supports Jenkins, GitHub Actions, GitLab CI,
  CircleCI, Docker, Kubernetes, and SonarQube. Use this skill when the user asks to:
  (1) Create or modify CI/CD pipeline configurations,
  (2) Write or optimize Dockerfiles and docker-compose files,
  (3) Create Kubernetes manifests (deployments, services, ingress, configmaps),
  (4) Set up SonarQube quality gates and analysis,
  (5) Configure environments, secrets, or deployment strategies,
  (6) Troubleshoot pipeline failures or deployment issues.
  Triggers: "Dockerfile", "docker-compose", "Kubernetes", "k8s", "CI/CD", "pipeline",
  "Jenkins", "Jenkinsfile", "GitHub Actions", "GitLab CI", "CircleCI", "SonarQube",
  "deploy", "build pipeline", "container", "helm", "ingress", "quality gate".
---

# DevOps CI/CD Skill

## Workflow

1. **Identify task**: Pipeline config, Docker, Kubernetes, or SonarQube.
2. **Detect CI platform**: Jenkins, GitHub Actions, GitLab CI, or CircleCI.
3. **Detect project type**: Spring Boot (Maven/Gradle) or Angular (npm).
4. **Generate config**: Follow patterns in `references/pipeline-templates.md`.
5. **Output**: Complete config file(s) + explanation of stages and key decisions.

## Docker Rules

### Spring Boot Dockerfile
- Use multi-stage builds: build stage (Maven/Gradle) + runtime stage (JRE only)
- Base image: `eclipse-temurin:21-jre-alpine` (or appropriate JDK version) for runtime
- Run as non-root user
- Use `.dockerignore` to exclude unnecessary files
- Set `JAVA_OPTS` via environment variable for tuning
- Use `ENTRYPOINT ["java", ...]` not `CMD`
- Health check: `HEALTHCHECK CMD curl -f http://localhost:8080/actuator/health || exit 1`

### Angular Dockerfile
- Multi-stage: Node build stage + Nginx runtime stage
- Build with `--configuration production`
- Nginx config for SPA routing (fallback to `index.html`)
- Gzip enabled in nginx config

### docker-compose
- Define services, networks, and volumes
- Use environment variables for config (not hardcoded)
- Health checks and `depends_on` with conditions
- Named volumes for data persistence

## Kubernetes Rules

- Always define: Deployment, Service, ConfigMap, and optionally Ingress
- Set resource `requests` and `limits` on every container
- Liveness and readiness probes on every pod
- Use `ConfigMap` for non-sensitive config, `Secret` for sensitive data
- Rolling update strategy with `maxSurge` and `maxUnavailable`
- Namespace per environment (dev, staging, prod)
- Use labels consistently: `app`, `version`, `environment`
- HPA (Horizontal Pod Autoscaler) for production workloads

## CI/CD Pipeline Standard Stages

Every pipeline should include these stages in order:

1. **Build**: Compile code, resolve dependencies
2. **Test**: Unit tests + integration tests
3. **Code Quality**: SonarQube analysis, lint
4. **Security Scan**: Dependency check (OWASP), container scan
5. **Build Image**: Docker build + push to registry
6. **Deploy Staging**: Deploy to staging environment
7. **E2E Tests**: Run Cypress/Playwright against staging
8. **Deploy Production**: Manual approval + deploy to prod

## SonarQube Rules

- Configure `sonar-project.properties` or Maven/Gradle plugin
- Quality gate: coverage >= 80%, duplicated lines < 3%, no critical/blocker issues
- Exclude generated code and test files from analysis
- Set up branch analysis for PRs

## General Rules

- Always generate **complete config files** — never fragments.
- Include comments explaining non-obvious configurations.
- Use secrets management (K8s Secrets, CI/CD env vars) — never hardcode credentials.
- Provide separate configs per environment when needed (dev, staging, prod).
