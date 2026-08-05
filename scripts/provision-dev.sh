#!/usr/bin/env bash
# Provision the FabOps non-production (dev) Kubernetes platform baseline.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEV_DIR="${ROOT_DIR}/infrastructure/environments/dev"
ARTIFACTS_DIR="${ROOT_DIR}/artifacts"
PLAN_FILE="${ARTIFACTS_DIR}/dev.tfplan"
APPLY_LOG="${ARTIFACTS_DIR}/dev-apply.log"
OUTPUTS_FILE="${ARTIFACTS_DIR}/dev-outputs.json"

mkdir -p "${ARTIFACTS_DIR}"

echo "==> [1/4] terraform init (dev workspace)"
cd "${DEV_DIR}"

if [[ -f backend.hcl ]]; then
  terraform init -input=false -reconfigure -backend-config=backend.hcl
else
  echo "    No backend.hcl found — using local state. Copy backend.hcl.example for remote S3 state."
  terraform init -input=false
fi

echo "==> [2/4] terraform plan"
terraform plan -input=false -out="${PLAN_FILE}" | tee "${ARTIFACTS_DIR}/dev-plan.txt"

echo "==> [3/4] terraform apply -auto-approve"
terraform apply -input=false -auto-approve "${PLAN_FILE}" | tee "${APPLY_LOG}"

echo "==> Capturing outputs"
terraform output -json > "${OUTPUTS_FILE}"

echo "==> [4/4] Export kubeconfig"
"${ROOT_DIR}/scripts/export-kubeconfig.sh" dev

echo ""
echo "Dev cluster provisioned."
echo "  Plan:     ${PLAN_FILE}"
echo "  Apply log:${APPLY_LOG}"
echo "  Outputs:  ${OUTPUTS_FILE}"
echo "  Kubeconfig: ${ARTIFACTS_DIR}/kubeconfig-dev"
echo ""
echo "Store ChartMuseum URL in CI (from outputs):"
jq -r '.chartmuseum_repository_url.value // empty' "${OUTPUTS_FILE}" || true
