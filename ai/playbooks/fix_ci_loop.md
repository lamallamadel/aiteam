# Playbook — Fix CI Loop

## Règles
- Max 3 itérations CI
- Max 2 itérations E2E
- Si bloqué: produire `escalation.json` conforme au schéma

## Process
1. Lire le job en échec + logs
2. Identifier cause probable
3. Patch minimal
4. Rerun CI
5. Répéter dans les limites
