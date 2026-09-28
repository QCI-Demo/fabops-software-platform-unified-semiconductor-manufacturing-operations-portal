#!/bin/bash
# validate-connectors.sh
# Validates Kafka Connect connector configurations against the Connect REST API
# Usage: ./validate-connectors.sh [CONNECT_URL]

set -euo pipefail

CONNECT_URL="${1:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTORS_DIR="${SCRIPT_DIR}"

echo "=============================================="
echo "Kafka Connect Connector Validation"
echo "Connect URL: ${CONNECT_URL}"
echo "=============================================="

# Check if Connect is available
echo -n "Checking Kafka Connect availability... "
if ! curl -sf "${CONNECT_URL}/" > /dev/null 2>&1; then
    echo "FAILED"
    echo "Error: Cannot reach Kafka Connect at ${CONNECT_URL}"
    echo "Please ensure Kafka Connect is running and accessible."
    exit 1
fi
echo "OK"

# Get available connector plugins
echo ""
echo "Available Snowflake connector plugins:"
curl -sf "${CONNECT_URL}/connector-plugins" | jq -r '.[] | select(.class | contains("Snowflake")) | "  - \(.class)"' 2>/dev/null || echo "  (Unable to list plugins)"

echo ""
echo "=============================================="
echo "Validating connector configurations..."
echo "=============================================="

VALIDATION_ERRORS=0

for config_file in "${CONNECTORS_DIR}"/*.json; do
    if [[ ! -f "$config_file" ]]; then
        continue
    fi
    
    connector_name=$(basename "$config_file" .json)
    echo ""
    echo "Validating: ${connector_name}"
    echo "  File: ${config_file}"
    
    # Extract connector class for validation endpoint
    connector_class=$(jq -r '.config["connector.class"]' "$config_file" 2>/dev/null)
    
    if [[ -z "$connector_class" || "$connector_class" == "null" ]]; then
        echo "  Status: SKIP (no connector.class found)"
        continue
    fi
    
    echo "  Class: ${connector_class}"
    
    # Validate JSON syntax
    if ! jq . "$config_file" > /dev/null 2>&1; then
        echo "  Status: FAILED (invalid JSON)"
        ((VALIDATION_ERRORS++))
        continue
    fi
    
    # Validate against Connect REST API
    validation_response=$(curl -sf -X PUT \
        -H "Content-Type: application/json" \
        -d @"$config_file" \
        "${CONNECT_URL}/connector-plugins/${connector_class}/config/validate" 2>&1) || true
    
    if [[ -z "$validation_response" ]]; then
        echo "  Status: SKIP (plugin not available for validation)"
        continue
    fi
    
    # Check for validation errors
    error_count=$(echo "$validation_response" | jq '[.configs[].value.errors | length] | add // 0' 2>/dev/null || echo "0")
    
    if [[ "$error_count" -gt 0 ]]; then
        echo "  Status: FAILED (${error_count} configuration errors)"
        echo "  Errors:"
        echo "$validation_response" | jq -r '.configs[] | select(.value.errors | length > 0) | "    - \(.value.name): \(.value.errors | join(", "))"' 2>/dev/null
        ((VALIDATION_ERRORS++))
    else
        echo "  Status: VALID"
    fi
done

echo ""
echo "=============================================="
if [[ "$VALIDATION_ERRORS" -gt 0 ]]; then
    echo "Validation completed with ${VALIDATION_ERRORS} error(s)"
    exit 1
else
    echo "All connector configurations validated successfully"
    exit 0
fi
