#!/usr/bin/env bash
# Deploy ChartMuseum into the chartmuseum (dev) namespace and emit CI variables.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CHART_DIR="${ROOT_DIR}/charts/chartmuseum"
ARTIFACTS_DIR="${ROOT_DIR}/artifacts"
NAMESPACE="${CHARTMUSEUM_NAMESPACE:-dev}"
RELEASE_NAME="${CHARTMUSEUM_RELEASE:-chartmuseum}"
KUBECONFIG_PATH="${KUBECONFIG:-${ARTIFACTS_DIR}/kubeconfig-dev}"

export KUBECONFIG="${KUBECONFIG_PATH}"
mkdir -p "${ARTIFACTS_DIR}"

echo "==> Ensuring namespace layout (includes ${NAMESPACE})"
kubectl apply -f "${ROOT_DIR}/kubernetes/namespaces/dev.yaml"

echo "==> Linting Helm chart"
helm lint "${CHART_DIR}" -f "${CHART_DIR}/values-dev.yaml"

echo "==> Deploying ChartMuseum to ${NAMESPACE}"
helm upgrade --install "${RELEASE_NAME}" "${CHART_DIR}" \
  --namespace "${NAMESPACE}" \
  --create-namespace \
  -f "${CHART_DIR}/values-dev.yaml" \
  --wait --timeout 5m

REPO_URL="http://${RELEASE_NAME}.${NAMESPACE}.svc.cluster.local:8080"
CI_VARS_FILE="${ARTIFACTS_DIR}/ci-variables.env"

cat > "${CI_VARS_FILE}" <<EOF
# Generated $(date -u +%Y-%m-%dT%H:%M:%SZ) — store these in GitHub Actions / CI secrets+vars
HELM_REPO_URL=${REPO_URL}
HELM_REPO_NAME=fabops-dev
CHARTMUSEUM_NAMESPACE=${NAMESPACE}
CHART_PUBLISHER_SA=chart-publisher
EOF

# Mirror into GitHub Actions variable template for operators
cp "${CI_VARS_FILE}" "${ROOT_DIR}/.github/ci-variables.env.generated" 2>/dev/null || true

echo "==> Verifying ServiceAccount + RBAC"
kubectl get sa chart-publisher -n "${NAMESPACE}"
kubectl get role,rolebinding -n "${NAMESPACE}" | grep -E 'publisher|NAME' || true

echo "==> ChartMuseum repository URL stored in ${CI_VARS_FILE}"
cat "${CI_VARS_FILE}"
