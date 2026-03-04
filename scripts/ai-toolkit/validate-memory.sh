#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────
# validate-memory.sh — Pre-commit hook for .memory/ files
# Ensures no secrets, tokens, or PII are accidentally committed.
# Install: cp scripts/ai-toolkit/validate-memory.sh .git/hooks/pre-commit
# ─────────────────────────────────────────────────────────

set -euo pipefail

MEMORY_DIR=".memory"
VIOLATIONS=0

# Patterns that should NEVER appear in memory files
FORBIDDEN_PATTERNS=(
  'AKIA[0-9A-Z]{16}'        # AWS Access Key
  'sk-[a-zA-Z0-9]{20,}'     # OpenAI / Stripe secret key
  'ghp_[a-zA-Z0-9]{36}'     # GitHub PAT
  'password\s*[:=]\s*\S+'   # Plaintext password assignments
  'secret\s*[:=]\s*\S+'     # Plaintext secret assignments
  'token\s*[:=]\s*\S+'      # Plaintext token assignments
  'Bearer\s+[a-zA-Z0-9._-]+' # Bearer tokens
)

if [ ! -d "$MEMORY_DIR" ]; then
  exit 0
fi

echo "🔒 Validating .memory/ files for secrets..."

for pattern in "${FORBIDDEN_PATTERNS[@]}"; do
  MATCHES=$(grep -rnE "$pattern" "$MEMORY_DIR" 2>/dev/null || true)
  if [ -n "$MATCHES" ]; then
    echo "❌ FORBIDDEN PATTERN DETECTED: $pattern"
    echo "$MATCHES"
    VIOLATIONS=$((VIOLATIONS + 1))
  fi
done

if [ "$VIOLATIONS" -gt 0 ]; then
  echo ""
  echo "🚫 COMMIT BLOCKED — $VIOLATIONS secret pattern(s) found in .memory/ files."
  echo "   Remove secrets before committing."
  exit 1
fi

echo "✅ .memory/ files are clean."
exit 0
