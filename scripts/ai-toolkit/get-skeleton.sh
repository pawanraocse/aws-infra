#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────
# get-skeleton.sh — Extract class/function signatures from source files
# Usage: ./scripts/ai-toolkit/get-skeleton.sh <file-or-directory>
#
# Purpose: Token-optimized codebase exploration.
# Instead of reading a 500-line Java file, the agent reads
# only the class name, method signatures, and annotations.
# ─────────────────────────────────────────────────────────

set -euo pipefail

TARGET="${1:-.}"

if [ ! -e "$TARGET" ]; then
  echo "❌ Error: '$TARGET' does not exist."
  exit 1
fi

# --- Java files: extract class declarations, method signatures, annotations ---
extract_java() {
  local file="$1"
  echo "### 📄 $file"
  grep -nE '^\s*(@\w+|public\s|private\s|protected\s|static\s|class\s|interface\s|enum\s|record\s|extends\s|implements\s)' "$file" 2>/dev/null \
    | grep -vE '^\s*//' \
    | sed 's/{.*//g' \
    | head -60
  echo ""
}

# --- TypeScript files: extract class, function, interface, type declarations ---
extract_ts() {
  local file="$1"
  echo "### 📄 $file"
  grep -nE '^\s*(export\s|@\w+|class\s|interface\s|type\s|enum\s|function\s|const\s\w+\s*=\s*(async\s*)?\()' "$file" 2>/dev/null \
    | grep -vE '^\s*//' \
    | sed 's/{.*//g' \
    | head -60
  echo ""
}

# --- Python files: extract class and function definitions ---
extract_py() {
  local file="$1"
  echo "### 📄 $file"
  grep -nE '^\s*(class\s|def\s|async\s+def\s|@\w+)' "$file" 2>/dev/null \
    | head -60
  echo ""
}

# --- Process a single file ---
process_file() {
  local file="$1"
  case "$file" in
    *.java) extract_java "$file" ;;
    *.ts)   extract_ts "$file" ;;
    *.py)   extract_py "$file" ;;
    *)      echo "⚠️  Skipping unsupported file: $file" ;;
  esac
}

# --- Main ---
if [ -f "$TARGET" ]; then
  process_file "$TARGET"
elif [ -d "$TARGET" ]; then
  echo "# Skeleton View: $TARGET"
  echo "# Generated: $(date -Iseconds)"
  echo ""
  find "$TARGET" \( -name "*.java" -o -name "*.ts" -o -name "*.py" \) \
    -not -path "*/node_modules/*" \
    -not -path "*/.git/*" \
    -not -path "*/target/*" \
    -not -path "*/dist/*" \
    -not -path "*/build/*" \
    -not -name "*.spec.ts" \
    -not -name "*.test.ts" \
    -not -name "*Test.java" \
    | sort \
    | while read -r file; do
        process_file "$file"
      done
else
  echo "❌ Error: '$TARGET' is neither a file nor a directory."
  exit 1
fi
