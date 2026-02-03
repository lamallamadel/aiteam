# Playbook — E2E Policy (fast)

## Pré-conditions
- Backend en H2 avec jwtmock activé sur `http://127.0.0.1:8080`
- Frontend sur `http://127.0.0.1:4200`
- Proxy Angular `frontend/proxy.conf.json` route `/api/**` vers 8080

## Commande
- `cd frontend && npm run e2e:fast`

## CI
- Chromium only en CI (stabilité)
- Upload du rapport Playwright en artifact
