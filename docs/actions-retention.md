# GitHub Actions Artifact Retention

## Workflow Artifact Retention

All `actions/upload-artifact` steps in this repository are configured with `retention-days: 1`.
This is the smallest supported value and ensures uploaded artifacts (test reports, security scan
results, SBOM, logs) are automatically deleted after one day, preventing storage accumulation.

## Repository-Level Retention

In addition to per-workflow settings, GitHub allows a global default retention period for all
workflow artifacts and logs:

1. Go to the repository on GitHub.
2. Navigate to **Settings → Actions → General**.
3. Under **Artifact and log retention**, set the desired default (minimum: 1 day).

The per-workflow `retention-days: 1` setting takes precedence over the repository default.
