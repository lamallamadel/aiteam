# Atlasia AI Orchestrator (scaffold)

Ce module est un **socle**: endpoint `/runs` sécurisé par token et persistance Postgres.
Il sert à déclencher un "run" depuis GitHub Actions (label `ai:run`), et à tracer l'état.

## Run (local)
- Démarrer Postgres via `infra/docker-compose.ai.yml`
- Exporter `ORCHESTRATOR_TOKEN`
- Lancer l'app

## API
- POST `/runs` (Authorization: Bearer <token>)
  Payload:
  ```json
  {"repo":"owner/repo","issueNumber":123,"mode":"FULL"}
  ```
  Réponse: 202 Accepted avec id du run.

## À implémenter ensuite
- GitHub App auth + APIs (issues/PR/actions)
- Workflow engine (Qualifier->Architect->Dev->Tester->Writer->PM)
- Artifact store et schémas JSON
- Boucles de fix CI/E2E contrôlées
