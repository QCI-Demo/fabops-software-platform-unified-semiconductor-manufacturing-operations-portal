#!/usr/bin/env bash
# Lint / validate Avro schema (.avsc) files using Apache Avro Tools.
# Usage: scripts/avro-lint.sh [schemas_dir]
set -eu

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SCHEMAS_DIR="${1:-$ROOT_DIR/schemas}"
AVRO_TOOLS_JAR="${AVRO_TOOLS_JAR:-$ROOT_DIR/tools/avro-tools.jar}"
AVRO_TOOLS_VERSION="${AVRO_TOOLS_VERSION:-1.11.4}"
AVRO_TOOLS_URL="https://repo1.maven.org/maven2/org/apache/avro/avro-tools/${AVRO_TOOLS_VERSION}/avro-tools-${AVRO_TOOLS_VERSION}.jar"

if [ ! -f "$AVRO_TOOLS_JAR" ]; then
  echo "Downloading avro-tools ${AVRO_TOOLS_VERSION} to ${AVRO_TOOLS_JAR}..."
  mkdir -p "$(dirname "$AVRO_TOOLS_JAR")"
  curl -fsSL -o "$AVRO_TOOLS_JAR" "$AVRO_TOOLS_URL"
fi

if [ ! -d "$SCHEMAS_DIR" ]; then
  echo "ERROR: schemas directory not found: $SCHEMAS_DIR" >&2
  exit 1
fi

tmpdir=$(mktemp -d)
failed=0
count=0

echo "Linting Avro schemas in $SCHEMAS_DIR with avro-tools..."

for schema in "$SCHEMAS_DIR"/*.avsc; do
  if [ ! -f "$schema" ]; then
    echo "ERROR: no .avsc files found in $SCHEMAS_DIR" >&2
    rm -rf "$tmpdir"
    exit 1
  fi
  count=$((count + 1))
  name=$(basename "$schema")
  out="$tmpdir/${name}.canonical"
  echo "--- avro-tools lint: $name"
  # Apache Avro Tools has no dedicated lint subcommand. Parsing via
  # canonical and fingerprint fails non-zero on invalid schemas.
  if java -jar "$AVRO_TOOLS_JAR" canonical "$schema" "$out" >/dev/null 2>&1 \
    && java -jar "$AVRO_TOOLS_JAR" fingerprint "$schema" >/dev/null 2>&1; then
    echo "OK:   $name"
  else
    echo "FAIL: $name" >&2
    # Re-run without silencing so CI logs show the parser error.
    java -jar "$AVRO_TOOLS_JAR" canonical "$schema" "$out" || true
    failed=1
  fi
done

rm -rf "$tmpdir"

if [ "$count" -eq 0 ]; then
  echo "ERROR: no .avsc files found in $SCHEMAS_DIR" >&2
  exit 1
fi

if [ "$failed" -ne 0 ]; then
  echo "avro-tools lint failed." >&2
  exit 1
fi

echo "All $count schemas passed avro-tools lint."
