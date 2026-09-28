#!/bin/bash
# deploy-connectors.sh
# Deploys Kafka Connect connectors using the Connect REST API
# Usage: ./deploy-connectors.sh [CONNECT_URL] [--dry-run]

set -euo pipefail

CONNECT_URL="${1:-http://localhost:8083}"
DRY_RUN="${2:-}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTORS_DIR="${SCRIPT_DIR}"

echo "=============================================="
echo "Kafka Connect Connector Deployment"
echo "Connect URL: ${CONNECT_URL}"
[[ "$DRY_RUN" == "--dry-run" ]] && echo "Mode: DRY RUN"
echo "=============================================="

# Check if Connect is available
echo -n "Checking Kafka Connect availability... "
if ! curl -sf "${CONNECT_URL}/" > /dev/null 2>&1; then
    echo "FAILED"
    echo "Error: Cannot reach Kafka Connect at ${CONNECT_URL}"
    exit 1
fi
echo "OK"

# Get existing connectors
echo ""
echo "Existing connectors:"
curl -sf "${CONNECT_URL}/connectors" | jq -r '.[] | "  - \(.)"' 2>/dev/null || echo "  (none)"

echo ""
echo "=============================================="
echo "Deploying connectors..."
echo "=============================================="

DEPLOY_COUNT=0
DEPLOY_ERRORS=0

for config_file in "${CONNECTORS_DIR}"/*.json; do
    if [[ ! -f "$config_file" ]]; then
        continue
    fi
    
    connector_name=$(jq -r '.name' "$config_file" 2>/dev/null)
    
    if [[ -z "$connector_name" || "$connector_name" == "null" ]]; then
        echo "Skipping ${config_file}: no connector name found"
        continue
    fi
    
    echo ""
    echo "Deploying: ${connector_name}"
    
    if [[ "$DRY_RUN" == "--dry-run" ]]; then
        echo "  (dry run - skipping actual deployment)"
        ((DEPLOY_COUNT++))
        continue
    fi
    
    # Check if connector already exists
    if curl -sf "${CONNECT_URL}/connectors/${connector_name}" > /dev/null 2>&1; then
        echo "  Connector exists - updating configuration..."
        config_only=$(jq '.config' "$config_file")
        response=$(curl -sf -X PUT \
            -H "Content-Type: application/json" \
            -d "$config_only" \
            "${CONNECT_URL}/connectors/${connector_name}/config" 2>&1) || {
            echo "  Status: FAILED to update"
            echo "  Response: ${response}"
            ((DEPLOY_ERRORS++))
            continue
        }
    else
        echo "  Creating new connector..."
        response=$(curl -sf -X POST \
            -H "Content-Type: application/json" \
            -d @"$config_file" \
            "${CONNECT_URL}/connectors" 2>&1) || {
            echo "  Status: FAILED to create"
            echo "  Response: ${response}"
            ((DEPLOY_ERRORS++))
            continue
        }
    fi
    
    echo "  Status: SUCCESS"
    ((DEPLOY_COUNT++))
done

echo ""
echo "=============================================="
if [[ "$DEPLOY_ERRORS" -gt 0 ]]; then
    echo "Deployment completed: ${DEPLOY_COUNT} succeeded, ${DEPLOY_ERRORS} failed"
    exit 1
else
    echo "Deployment completed: ${DEPLOY_COUNT} connector(s) deployed"
    exit 0
fi
