# Playbook — Issue Intake (AI)

## Objectif
Transformer une issue en plan exécutable (AC + tâches + tests + commandes).

## Étapes
1. Lire l'issue et identifier le périmètre.
2. Extraire / écrire 3+ critères d'acceptation testables.
3. Lister les cas limites et risques.
4. Produire `ticket_plan.json` conforme au schéma.
5. Produire `work_plan.json` (branchName + tasks + commandes standard Atlasia).

## Commandes standard Atlasia
- Backend: `cd backend && ./mvnw -B clean verify`
- Frontend: `cd frontend && npm ci && npm run lint && npm test -- --watch=false`
- E2E: `cd frontend && npm run e2e:fast`
