# LLM routing, budget, and providers

This document describes how **Code Mode** (pipeline) and **Chat Mode** reach language models, how **dual legs** and **budget soft caps** behave, and which environment variables apply.

## Code Mode: tiers and dual legs

Pipeline steps call `LlmService` with a `TaskComplexity` resolved from `atlasia.model-tiers.agent-complexity` in `application.yml` (via `LlmComplexityResolver`). Each complexity maps to a **tier** (`very-high` … `trivial`). Each tier defines a **dual pool**:

- **Primary** leg: tried first when `availability` is `prefer-primary`.
- **Secondary** leg: used on eligible failures (e.g. transient HTTP errors, 429, circuit breaker open).
- **`sticky-until-failure`**: remembers the last successful leg per `(MDC runId, tier, complete|structured)` and tries that leg first on the next call, so a healthy secondary is not abandoned every request after primary recovers slowly.

If **both** legs fail for a tier, the executor **downgrades one `TaskComplexity` step** (same order as budget downgrade) and retries that tier’s dual pool; repeats until success or `TRIVIAL` is exhausted, then falls back to the legacy `LlmService` path.

Implementation: `TieredLlmExecutor` + `LlmProviderCircuitBreakerFactory` (Resilience4j circuit breaker per `provider-id`). Failover within a tier increments **`orchestrator.llm.dual.failover.total`** (Micrometer), tagged with `tier`, `from_leg`, `to_leg`.

### Structured JSON output

Tiered routing for **structured** (`generateStructuredOutput`) uses OpenAI-compatible chat completions on legs whose provider `type` is **OPENAI** or **LITELLM**. If the selected leg is **ANTHROPIC** (or another non–OpenAI-compat type), the call falls back to the legacy single-endpoint path (`atlasia.orchestrator.llm`) for that request.

## Chat Mode: persona → provider

`ChatService` uses **`AiProviderRouter`**, not `LlmService`. Routing uses `persona.ai.default-provider`, optional `persona.ai.persona-provider-map`, and named entries under `persona.ai.providers` (same IDs referenced by tier legs).

## Budget tracker

`BudgetTracker` maintains:

- Estimated spend **per run** (UUID from MDC `runId`) and **per UTC day**.
- Soft caps from `atlasia.model-tiers.budget`: `max-per-run-usd`, `max-daily-usd`, `downgrade-threshold` (default 0.80 of cap).

When pressure exceeds threshold, `adjustForBudget` downgrades the requested `TaskComplexity` one step at a time toward `TRIVIAL` before the LLM call. Costs use per-leg `cost-per-1k-input` / `cost-per-1k-output` from the tier configuration.

Prometheus: `orchestrator.cost.attribution` is updated on usage (repo/user tags from correlation MDC where set).

## REST: current budget snapshot

`GET /api/budget` returns `BudgetTracker.BudgetSnapshot`: spend vs caps for the **UTC day**, optional **per-run** spend, `downgradeThreshold`, and the configured `agent-complexity` map.

- **`?runId=<uuid>`** (recommended for UIs): scopes **run** spend to that workflow run without relying on MDC.
- Without `runId`, the snapshot uses MDC `runId` when the orchestration filter set it (rare for ad hoc HTTP calls).

## Provider table (summary)

| Provider id (example) | Typical `type` | Notes |
|----------------------|----------------|--------|
| `openai` | `OPENAI` | `LLM_ENDPOINT`, `LLM_API_KEY`, `LLM_MODEL` |
| `groq` | `OPENAI` | OpenAI-compatible base URL; `GROQ_API_KEY`, `GROQ_ENDPOINT`, `GROQ_MODEL` |
| `anthropic` | `ANTHROPIC` | Native Messages API; `ANTHROPIC_API_KEY`, `ANTHROPIC_BASE_URL`, `ANTHROPIC_MODEL` |
| Vertex / Gemini (optional) | Often `OPENAI` | Use Vertex **OpenAI-compatible** base URL + GCP auth if available; native Gemini would need a dedicated adapter |

Align **tier leg `provider-id`** values with keys under `persona.ai.providers`. Mismatched IDs cause resolution failures at runtime.

## Environment variables (quick reference)

| Variable | Role |
|----------|------|
| `LLM_ENDPOINT`, `LLM_API_KEY`, `LLM_MODEL` | Default OpenAI-compat provider |
| `LLM_FALLBACK_*` | Legacy pipeline fallback (DeepSeek, etc.) |
| `GROQ_API_KEY`, `GROQ_ENDPOINT`, `GROQ_MODEL` | Groq OpenAI-compat provider |
| `ANTHROPIC_API_KEY`, `ANTHROPIC_BASE_URL`, `ANTHROPIC_MODEL` | Anthropic provider |
| `LLM_BUDGET_MAX_PER_RUN_USD`, `LLM_BUDGET_MAX_DAILY_USD`, `LLM_BUDGET_DOWNGRADE_THRESHOLD` | Override budget section |

Vault paths in `application.yml` can supersede plain env vars for secrets (e.g. `vault.secret.data.atlasia.groq-api-key`).

## Pitfalls

- **ID alignment**: Tier `provider-id` must exist in `persona.ai.providers`.
- **Structured + Anthropic**: A tier whose primary/secondary is only Anthropic may still hit legacy LLM for structured calls; configure at least one OpenAI-compat leg for JSON-schema style steps if you want full tier routing.
- **Budget snapshot**: pass **`?runId=`** from the dashboard (see run detail) if MDC `runId` is not set on the request.

See also: `application.yml` sections `atlasia.model-tiers` and `persona.ai`.
