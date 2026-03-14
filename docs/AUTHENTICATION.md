# Authentification Atlasia AI Orchestrator

## Vue d'ensemble

L'orchestrateur reconnaît trois types d'authentification :

1. **JWT local** — Émis par le serveur après login/password ou OAuth2 (GitHub, Google, GitLab). Le `JwtAuthenticationFilter` valide le token et remplit le `SecurityContext`.
2. **Token admin** — Token statique configuré via `atlasia.orchestrator.token`. Utilisé par les agents CI/CD et pour les opérations administratives.
3. **Token GitHub** — Token personnel ou d'application validé via l'API GitHub. Utilisé pour les workflows CI et les opérations sur les dépôts.

## Architecture

### ApiAuthService

Service central qui décide si une requête est autorisée. Utilisé par `RunController`, `GraftController`, `MultiRepoController`, et `A2AController`.

- **`isAuthorized(authorizationHeader)`** — Retourne `true` si :
  - le Bearer token est le token admin ;
  - le Bearer token est un token GitHub valide ;
  - ou le `SecurityContext` contient déjà une authentification (utilisateur JWT).
- **`getApiTokenForWorkflow(authorizationHeader)`** — Retourne le token uniquement pour admin ou GitHub (pour les appels CI/workflow). Pour un utilisateur JWT, retourne vide.
- **`isAdminToken(authorizationHeader)`** — Retourne `true` uniquement quand le Bearer token est le token admin.

### BearerTokenAuthenticationResolver

Abstraction pour la résolution d'un Bearer token en `Authentication`. Le filtre JWT s'appuie sur cette interface.

- **Implémentation actuelle** : `LocalJwtBearerTokenResolver` — validation JWT symétrique + chargement des `UserDetails` depuis la base.
- **Token E2E** : Le token `test-github-token` est accepté uniquement lorsque `atlasia.auth.e2e-test-token-enabled=true` (profil E2E).

## Configuration

| Propriété | Description | Défaut |
|-----------|-------------|--------|
| `atlasia.auth.mode` | Mode d'authentification : `local` (JWT émis par le serveur) ou `keycloak` (prévu) | `local` |
| `atlasia.auth.e2e-test-token-enabled` | Active le token E2E `test-github-token` pour les tests | `false` |

## Préparation Keycloak

Le mode `keycloak` est prévu pour une migration future. Une fois activé :

- Le `BearerTokenAuthenticationResolver` pourra être branché sur un `JwtDecoder` configuré via `spring.security.oauth2.resourceserver.jwt.issuer-uri`.
- Les contrôleurs s'appuient déjà sur `SecurityContext` + `ApiAuthService`, ils resteront compatibles avec des JWT émis par Keycloak.
- Les claims (ex. `sub`, `realm_access.roles`) seront mappés vers un principal compatible avec `CurrentUserService` et `AuthorizationService`.

## Flux d'authentification

```
Requête avec Authorization: Bearer <token>
    ↓
JwtAuthenticationFilter
    ↓
BearerTokenAuthenticationResolver.resolve(token)
    ↓
SecurityContext.setAuthentication(...)
    ↓
Contrôleurs API → ApiAuthService.isAuthorized()
    ↓
ApiAuthService consulte : token admin ? token GitHub ? SecurityContext authentifié ?
```

## Exécution Docker

En Docker, l’orchestrateur a besoin des variables d’environnement suivantes pour l’authentification :

| Variable | Description | Exemple (dev) |
|----------|-------------|-------------------------------|
| `JWT_SECRET_KEY` | Clé secrète pour signer/vérifier les JWT (min. 256 bits). En prod, préférer Vault ou une valeur injectée. | Défaut dev dans `infra/docker-compose.ai.yml` |
| `AUTH_MODE` | `local` (JWT émis par le serveur) ou `keycloak` (prévu) | `local` |
| `ORCHESTRATOR_TOKEN` | Token admin pour les appels API/CI (Bearer) | Défini dans le compose ou `.env` |

Composes officiels :

- **Dev (backend + frontend + DB)** : `infra/deployments/dev/docker-compose.yml`
- **Infra seule (DB + Vault + orchestrator)** : `infra/docker-compose.ai.yml`
- **Prod** : `infra/deployments/prod/docker-compose.yml`

Exemple (dev) :

```bash
docker compose -f infra/deployments/dev/docker-compose.yml --profile full up -d
```

Sans Vault (`SPRING_CLOUD_VAULT_ENABLED=false`), définir `JWT_SECRET_KEY` (ou utiliser le défaut dev du compose). Le frontend doit pointer vers l’URL de l’API (ex. `http://localhost:8088` si mappé) et les CORS sont réglés via `CORS_ALLOWED_ORIGINS` (défaut : `http://localhost:4200,http://localhost:8080`).

## Références

- [JWT_AUTHENTICATION.md](JWT_AUTHENTICATION.md) — Détails sur les tokens JWT
- [SECURITY.md](SECURITY.md) — Checklist de sécurité
