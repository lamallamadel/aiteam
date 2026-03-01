#!/usr/bin/env bash
set -euo pipefail

# Safe, idempotent helper to activate Temurin 21 for the current shell session.
# Does not edit any shell profile files.

if java -version 2>&1 | grep -q "21"; then
  echo "Java 21 already active in this shell"
  exit 0
fi

if command -v jabba >/dev/null 2>&1; then
  if jabba ls 2>/dev/null | grep -q 'temurin@1.21'; then
    echo "Using jabba temurin@1.21..."
    # prefer exact version, fall back to any 1.21 entry
    if jabba use temurin@1.21.0-10 2>/dev/null; then
      echo "Activated temurin@1.21.0-10 via jabba"
      java -version
      exit 0
    fi
    jabba use temurin@1.21 2>/dev/null || true
    java -version || true
    exit 0
  fi
fi

# Try common install locations (WSL / Linux / custom)
POSSIBLE=(
  "$HOME/.jabba/jdk/temurin@1.21.0-10"
  "/usr/lib/jvm/temurin-21-jdk"
  "/usr/lib/jvm/java-21-temurin"
  "/opt/java/temurin-21"
)
for p in "${POSSIBLE[@]}"; do
  if [ -d "$p" ]; then
    export JAVA_HOME="$p"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Set JAVA_HOME to $JAVA_HOME"
    java -version || true
    exit 0
  fi
done

cat <<'EOF'
Could not locate Temurin 21 automatically.
Options:
 - Install Temurin 21 (Adoptium/Temurin) or use your distro/package manager.
 - Install and use `jabba` to manage JDKs, then run `jabba install temurin@1.21 && jabba use temurin@1.21`.
 - Manually set `JAVA_HOME` for this session:
     export JAVA_HOME=/path/to/temurin-21
     export PATH="$JAVA_HOME/bin:$PATH"
This script does not modify your shell profiles.
EOF

exit 1
