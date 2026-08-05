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

echo "==> [1/5] terraform init (dev workspace)"
cd "${DEV_DIR}"

if [[ -f backend.hcl ]]; then
  terraform init -input=false -reconfigure -backend-config=backend.hcl
else
  echo "    No backend.hcl found — using local state. Copy backend.hcl.example for remote S3 state."
  terraform init -input=false
fi

# Two-stage apply: create VPC+EKS first so kubernetes/helm providers can authenticate,
# then bootstrap namespaces / ChartMuseum / network policies / ECR / secrets.
echo "==> [2/5] terraform plan (stage 1: VPC + EKS, platform bootstrap off)"
terraform plan -input=false \
  -var='enable_platform_bootstrap=false' \
  -out="${ARTIFACTS_DIR}/dev-stage1.tfplan" | tee "${ARTIFACTS_DIR}/dev-plan-stage1.txt"

echo "==> [3/5] terraform apply -auto-approve (stage 1)"
terraform apply -input=false -auto-approve "${ARTIFACTS_DIR}/dev-stage1.tfplan" | tee "${APPLY_LOG}"

echo "==> [4/5] terraform plan + apply (stage 2: platform bootstrap)"
terraform plan -input=false \
  -var='enable_platform_bootstrap=true' \
  -out="${PLAN_FILE}" | tee "${ARTIFACTS_DIR}/dev-plan.txt"
terraform apply -input=false -auto-approve "${PLAN_FILE}" | tee -a "${APPLY_LOG}"

echo "==> Capturing outputs"
terraform output -json > "${OUTPUTS_FILE}"

echo "==> [5/5] Export kubeconfig"
"${ROOT_DIR}/scripts/export-kubeconfig.sh" dev

# Persist ChartMuseum CI variables from Terraform outputs when available
REPO_URL="$(jq -r '.chartmuseum_repository_url.value // empty' "${OUTPUTS_FILE}")"
if [[ -n "${REPO_URL}" ]]; then
  cat > "${ARTIFACTS_DIR}/ci-variables.env" <<EOF
# Generated $(date -u +%Y-%m-%dT%H:%M:%SZ) — store in GitHub Actions Variables
HELM_REPO_URL=${REPO_URL}
HELM_REPO_NAME=fabops-dev
CHARTMUSEUM_NAMESPACE=dev
CHART_PUBLISHER_SA=chart-publisher
EOF
  echo "CI variables written to ${ARTIFACTS_DIR}/ci-variables.env"
fi

echo ""
echo "Dev cluster provisioned."
echo "  Plan:      ${PLAN_FILE}"
echo "  Apply log: ${APPLY_LOG}"
echo "  Outputs:   ${OUTPUTS_FILE}"
echo "  Kubeconfig:${ARTIFACTS_DIR}/kubeconfig-dev"
echo ""
echo "Next: ./scripts/validate-cluster.sh dev"
