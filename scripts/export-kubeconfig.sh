#!/usr/bin/env bash
# Export kubeconfig for a FabOps EKS environment (dev|prod).
set -euo pipefail

ENV_NAME="${1:-dev}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_DIR="${ROOT_DIR}/infrastructure/environments/${ENV_NAME}"
ARTIFACTS_DIR="${ROOT_DIR}/artifacts"
KUBECONFIG_OUT="${ARTIFACTS_DIR}/kubeconfig-${ENV_NAME}"

if [[ ! -d "${ENV_DIR}" ]]; then
  echo "Unknown environment: ${ENV_NAME}" >&2
  exit 1
fi

mkdir -p "${ARTIFACTS_DIR}"
cd "${ENV_DIR}"

REGION="$(terraform output -raw aws_region 2>/dev/null || true)"
if [[ -z "${REGION}" ]]; then
  REGION="${AWS_REGION:-us-east-1}"
fi

CLUSTER_NAME="$(terraform output -raw cluster_name)"
OUT_PATH="$(terraform output -raw kubeconfig_path 2>/dev/null || echo "${KUBECONFIG_OUT}")"

echo "Updating kubeconfig for cluster ${CLUSTER_NAME} (${REGION}) → ${KUBECONFIG_OUT}"
aws eks update-kubeconfig \
  --name "${CLUSTER_NAME}" \
  --region "${REGION}" \
  --kubeconfig "${KUBECONFIG_OUT}"

# Also refresh terraform-managed artifact if present
if [[ -f "${OUT_PATH}" && "${OUT_PATH}" != "${KUBECONFIG_OUT}" ]]; then
  cp -f "${KUBECONFIG_OUT}" "${OUT_PATH}"
fi

chmod 600 "${KUBECONFIG_OUT}"
export KUBECONFIG="${KUBECONFIG_OUT}"
echo "KUBECONFIG=${KUBECONFIG}"
kubectl cluster-info || true
kubectl get nodes -o wide || true
