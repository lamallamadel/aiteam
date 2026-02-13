# AI Product Canvas — Atlasia AI Orchestrator

## Problem Statement

Software development workflows suffer from fragmentation and latency caused by manual handoffs between product managers, architects, developers, and testers. Critical decisions are bottlenecked by human availability, and documentation consistently drifts from the actual codebase.

## Target Users

- **Development teams** using GitHub for issue tracking and CI/CD
- **Engineering managers** overseeing multi-project delivery pipelines
- **Platform teams** seeking to standardize SDLC automation across the organization

## Value Proposition

Atlasia transforms multi-week development cycles into high-velocity "bolts" measured in hours. It embeds specialized AI agents directly into the SDLC, replacing manual handoffs with an automated, observable pipeline — while preserving human oversight at critical decision points.

## Dual-Mode Capabilities

### Code Mode (Autonomous Pipeline)
| Agent | Capability | Output |
|-------|-----------|--------|
| PM | Issue analysis, requirement decomposition, risk identification | `ticket_plan.json` |
| Qualifier | Edge case extraction, test data planning, work decomposition | `work_plan.json` |
| Architect | Architecture validation, ADR generation, API/DB alignment | `architecture_notes.md` |
| Developer | Multi-file code generation, Git operations, PR creation | PR with code changes |
| Review | Multi-persona security/quality/infra/UX code review | `persona_review_report` |
| Tester | CI/E2E execution, failure diagnosis, automated fix loops | `test_report.json` |
| Writer | Documentation updates, changelog, README maintenance | `docs_update` |

### Chat Mode (Interactive Dialogue)
| Gem | Specialization |
|-----|---------------|
| Aabo | Security: MIME validation, secret detection, XSS/SQLi prevention |
| Aksil | Code Quality: error handling, SOLID principles, test coverage |
| Imad | Infrastructure: Docker, Kubernetes, caching, scalability |
| Tiziri | Frontend/UX: accessibility, responsive design, performance |

## Constraints

- **LLM token limits**: Context windows constrain per-step reasoning depth
- **GitHub API rate limits**: 5,000 requests/hour per installation
- **No direct main commits**: All changes go through Pull Requests
- **Protected paths**: `.github/workflows/` requires human escalation
- **Fix loop caps**: 3 CI iterations, 2 E2E iterations before escalation
- **Allowlist enforcement**: Only designated paths are modifiable

## Governance

- Agent contracts defined in `ai/agents/*.yaml`
- Artifact schemas enforced via `ai/schemas/*.schema.json`
- Quality gates documented in `docs/QUALITY_GATES.md`
- Operational playbooks in `ai/playbooks/`
- Persona review checklists with severity-based findings

## Metrics of Success

- **Bolt completion rate**: Percentage of issues resolved without human escalation
- **Time-to-PR**: Duration from issue trigger to PR creation
- **Test coverage maintenance**: Sustaining >= 70% backend / >= 60% frontend
- **Escalation rate**: Lower is better — indicates agent self-sufficiency
- **Fix loop efficiency**: Average iterations needed to achieve green CI
