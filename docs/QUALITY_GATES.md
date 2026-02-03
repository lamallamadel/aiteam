# Quality Gates — Atlasia

Ce document est la **source de vérité** des critères d'acceptation techniques. Toute PR doit respecter ces règles.

## PR Gates (obligatoires)
- Backend: `cd backend && ./mvnw -B clean verify` **SUCCESS**
- Frontend: `cd frontend && npm ci && npm run lint && npm test -- --watch=false` **SUCCESS**
- E2E (fast): `cd frontend && npm run e2e:fast` **SUCCESS** sur PR impactant UI / parcours
- Aucun secret / token / clé committé
- Aucune modification de `.github/workflows/**` sans escalade humaine

## Coverage (seuils initiaux)
- Backend (JaCoCo): LINE >= **70%** (seuil initial, à augmenter)
- Frontend (unit): LINES >= **60%** (seuil initial, à augmenter)
- Toute baisse > 2 points = justification obligatoire dans la PR

## Definition of Done (feature)
- Critères d'acceptation respectés
- Tests unitaires ajoutés/adaptés (backend + frontend si concerné)
- Tests d'intégration ajoutés/adaptés si endpoint / persistance concernés
- Migration DB versionnée si changement de schéma
- Erreurs/ProblemDetails conformes aux conventions Atlasia (si applicable)
- Docs mises à jour (README / RUNBOOK / endpoints) si nécessaire

## Escalade vers humain (uniquement si)
- Décision produit/architecture nouvelle (ADR requis)
- Flaky test persistant (>= 2 runs) sans cause claire
- Conflit entre ADR / règles métier / nomenclature
- Gate impossible à satisfaire sans arbitrage

## Boucles d'auto-correction (limites)
- CI fix loops max: **3**
- E2E fix loops max: **2**
