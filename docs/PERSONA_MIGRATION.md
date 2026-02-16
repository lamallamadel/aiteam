# Persona-to-Role Migration Guide

This document maps the legacy human-named personas to their new role-based identifiers and details what was retained, enhanced, and added during the migration.

## Migration Summary

| Legacy Persona | New Role Identifier | Role Title | File |
|---|---|---|---|
| Aabo | `security-engineer` | Security Engineer | `ai/agents/personas/security-engineer.yaml` |
| Aksil | `code-quality-engineer` | Code Quality Engineer | `ai/agents/personas/code-quality-engineer.yaml` |
| Imad | `sre-engineer` | Site Reliability Engineer | `ai/agents/personas/sre-engineer.yaml` |
| Tiziri | `frontend-ux-engineer` | Frontend UX Engineer | `ai/agents/personas/frontend-ux-engineer.yaml` |

Each role YAML includes a `legacy_alias` field preserving the original persona name for backward-compatible lookups in the Java service layer.

---

## Detailed Mapping: Security Engineer (formerly Aabo)

### Retained from Aabo
- OWASP vulnerability identification (SQL injection, XSS, CSRF)
- Input validation and sanitization focus
- Secret detection and protection
- Authentication and authorization review
- Dependency vulnerability scanning
- Severity taxonomy (critical/high/medium/low) with same baseline categories
- Constructive review tone with concrete code fix suggestions

### Enhanced
- **Mission**: Expanded from "identify vulnerabilities" to include "enforce secure coding practices, validate supply chain integrity, and ensure defense-in-depth across all layers"
- **MIME validation**: Enhanced from basic type/size to include content inspection (magic byte verification)
- **Secret management**: Expanded from "not hardcoded" to include rotation policies and vault integration
- **Encryption**: Specified standards (TLS 1.3, AES-256) instead of generic "industry-standard"
- **Dependencies**: Elevated from checklist item to full focus area with CVE scanning
- **Review workflow**: Added STRIDE threat modeling step for architectural changes

### New Additions
- **Required skills**: 8 explicit competencies (OWASP/SANS, STRIDE/DREAD, SAST/DAST, supply chain, container scanning, cryptographic analysis, Zero Trust)
- **Supply chain security**: SBOM validation, dependency provenance, lock file integrity
- **Container security**: Image scanning (Trivy, Grype) before deployment
- **Security headers**: CSP, HSTS, X-Frame-Options, Referrer-Policy enforcement
- **Threat modeling**: STRIDE methodology as formal step in review workflow
- **Quality standards**: 5 explicit standards (mandatory security review, zero-tolerance secrets, CVE assessment, container scanning, threat models)
- **Collaboration patterns**: Explicit coordination points with Code Quality, SRE, and Frontend UX roles

---

## Detailed Mapping: Code Quality Engineer (formerly Aksil)

### Retained from Aksil
- Error handling and recovery assessment
- Test coverage threshold enforcement (>=70% backend, >=60% frontend)
- Naming convention review
- Code duplication detection
- Cyclomatic complexity limits (<15 per method)
- SOLID principles enforcement
- Design pattern appropriateness
- Technical debt tracking
- Hexagonal Architecture / DDD awareness
- Reactive Programming correctness (RxJS, Project Reactor)

### Enhanced
- **Mission**: Expanded from "ensure maintainability" to include "enforce architectural boundaries, quantify technical debt, and promote sustainable engineering practices"
- **Test coverage**: Enhanced from "above threshold" to include test quality assessment (mutation testing, assertion density, behavior vs implementation testing)
- **Complexity**: Added cognitive complexity alongside cyclomatic complexity
- **Technical debt**: Elevated from "tracked" to "quantified with issue references"
- **Architecture**: Added fitness functions for automated boundary enforcement
- **Review workflow**: Added explicit test quality check step ("are tests meaningful?")

### New Additions
- **Required skills**: 8 explicit competencies (static analysis tooling, complexity metrics, mutation testing, performance profiling, refactoring patterns, API design)
- **Architecture fitness functions**: Automated boundary checks, dependency direction enforcement
- **Performance patterns**: N+1 query detection, efficient collections, lazy loading
- **Quality standards**: 6 explicit standards (coverage thresholds, complexity limits, function size, boundary respect, API docs, debt tracking)
- **Collaboration patterns**: Explicit coordination points with Security, SRE, and Frontend UX roles
- **New severity**: Architecture boundary violations elevated to critical

---

## Detailed Mapping: SRE Engineer (formerly Imad)

### Retained from Imad
- Docker resource limits enforcement
- Environment configuration externalization
- Scalability assessment (horizontal/vertical)
- Database design and indexing review
- Caching strategy evaluation
- Monitoring and observability checks
- Deployment automation verification
- Infrastructure as Code validation
- Backup and disaster recovery assessment
- Idempotency enforcement for scripts and migrations
- 5 Whys root cause analysis technique

### Enhanced
- **Mission**: Expanded from "validate infrastructure decisions" to include "enforce SLO compliance, optimize resource efficiency, and reduce operational toil through automation"
- **Resource management**: Docker limits expanded to include requests alongside limits
- **Monitoring**: Upgraded from generic "monitoring and logging" to OpenTelemetry, structured logging, and correlation IDs
- **Deployment**: Added reversibility requirement ("rollback within 5 minutes")
- **Database**: Added backward-compatibility requirement for zero-downtime deployments
- **IaC**: Expanded from "versioned and tracked" to explicit Terraform/Ansible/Helm

### New Additions
- **Required skills**: 9 explicit competencies (SLO/SLI/SLA, IaC, container orchestration, observability stack, incident response, chaos engineering, GitOps, cost optimization, DB admin)
- **SLO/SLI/SLA**: Formal service level definitions and error budget management
- **Chaos engineering**: Failure injection readiness and game day practices
- **GitOps workflows**: ArgoCD/Flux validation
- **Deployment strategies**: Canary, Blue-Green, Rolling assessment
- **Cost optimization**: Resource right-sizing and cost impact assessment
- **Toil reduction**: Identification and automation of repetitive manual operations
- **Quality standards**: 7 explicit standards (SLO definitions, resource limits, IaC-only, backward-compatible migrations, 5-minute rollback, structured logs, alert taxonomy)
- **Collaboration patterns**: Explicit coordination points with Security, Code Quality, and Frontend UX roles
- **New severity levels**: Non-idempotent migrations (critical), no rollback strategy (high), missing SLO definitions (medium)

---

## Detailed Mapping: Frontend UX Engineer (formerly Tiziri)

### Retained from Tiziri
- UX pattern consistency review
- Progress feedback and loading state verification
- Accessibility (WCAG 2.1 AA) enforcement
- Responsive design assessment
- Error messaging quality
- Form validation review
- Keyboard navigation and screen reader support
- Navigation consistency
- Internationalization (i18n) support
- Framework-specific performance review (Angular OnPush)

### Enhanced
- **Mission**: Expanded from "ensure excellent UX" to include "enforce accessibility standards, validate performance budgets, maintain design system compliance"
- **Loading states**: Threshold specified (>300ms instead of >2s)
- **Accessibility**: Added ARIA attributes and full keyboard navigation specifics
- **Responsive design**: Added mobile-first and container queries
- **Performance**: Elevated from generic "performance" to Core Web Vitals with specific budgets
- **Error handling**: Added accessibility requirement (ARIA live regions)

### New Additions
- **Required skills**: 9 explicit competencies (WCAG auditing, Core Web Vitals, Angular performance tuning, responsive patterns, design system compliance, assistive technology testing, visual regression testing, i18n/RTL, component documentation)
- **Core Web Vitals**: LCP < 2.5s, INP < 200ms, CLS < 0.1 performance budgets
- **Design system compliance**: Token validation, visual consistency, no hardcoded values
- **Visual regression testing**: Playwright screenshots, Chromatic integration
- **RTL layout**: Right-to-left language support
- **Component documentation**: Storybook stories and living style guides
- **PWA capabilities**: Progressive Web App validation
- **Quality standards**: 7 explicit standards (WCAG AA, Core Web Vitals budgets, 300ms loading states, actionable errors, externalized strings, visual regression, OnPush default)
- **Collaboration patterns**: Explicit coordination points with Security, Code Quality, and SRE roles
- **New severity levels**: Core Web Vitals budget exceeded (critical), design system token violations (high), missing OnPush (medium)

---

## Cross-Cutting Enhancements (All Roles)

These sections were added to every role definition during the migration:

| Section | Purpose |
|---|---|
| `required_skills` | 8-9 explicit technical competencies per role |
| `collaboration_patterns` | How this role coordinates with the other three reviewers |
| `quality_standards` | 5-7 measurable standards that define "good enough" |
| `legacy_alias` | Backward-compatible lookup key (e.g., `legacy_alias: aabo`) |
| `mcp.permissions_ref` | Updated to reference new role-based keys in permissions.yaml |

## Files Modified During Migration

| Category | Files |
|---|---|
| **Role definitions** | `ai/agents/personas/{security-engineer,code-quality-engineer,sre-engineer,frontend-ux-engineer}.yaml` |
| **Resource copies** | `ai-orchestrator/src/main/resources/ai/agents/personas/{security-engineer,code-quality-engineer,sre-engineer,frontend-ux-engineer}.yaml` |
| **Config references** | `ai/agents/orchestrator.yaml`, `ai/agents/review.yaml`, `ai/mcp/permissions.yaml`, `ai/orchestration/architecture.yaml` |
| **Schemas** | `ai/schemas/persona_review.schema.json`, `ai-orchestrator/src/main/resources/ai/schemas/persona_review.schema.json` |
| **Documentation** | `docs/DOMAIN_GLOSSARY.md`, `README.md`, `AGENTS.md`, `IMPLEMENTATION_SUMMARY.md`, `docs/AI_PRODUCT_CANVAS.md`, `docs/GLOBAL_REVIEW.md` |
| **Java source** | `PersonaReviewService.java`, `PersonaReviewServiceTest.java` |
| **TypeScript** | `frontend/src/app/components/chat-interface.ts` |
