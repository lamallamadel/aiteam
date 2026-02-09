# Atlasia AI Orchestrator (scaffold)

Ce module est un **socle**: endpoint `/runs` sécurisé par token et persistance Postgres.
Il sert à déclencher un "run" depuis GitHub Actions (label `ai:run`), et à tracer l'état.

## Run (local)
- Démarrer Postgres via `infra/docker-compose.ai.yml`
- Exporter `ORCHESTRATOR_TOKEN`
- Lancer l'app

## Fonctionnalités Clés
- **Workflow Engine**: Enchaînement automatique (PM -> Architect -> Developer -> Tester -> Writer).
- **GitHub Integration**: Gestion des issues, création de branches, commits, et Pull Requests.
- **Resilience Layer**: 
  - **Auto-Retries**: Stratégie de retry exponentiel (Reactor) sur les services LLM et GitHub pour l'auto-guérison des erreurs réseau.
  - **Connection Pooling**: Tuning de Netty `HttpClient` pour évincer les connexions inactives et éviter les "Connection reset".
  - **Empty Repo Support**: Détection et initialisation automatique des dépôts vides (main branch initialization).
- **Chat Mode (Gems)**: API pour le dialogue direct avec les personas AI (`/api/chat/{personaName}`).

## API
- `POST /api/runs`: Déclencher un orchestrateur complet.
- `GET /api/runs`: Liste des runs.
- `GET /api/personas`: Liste des "Gems" disponibles.
- `POST /api/chat/{personaName}`: Chat direct avec un Gem.
