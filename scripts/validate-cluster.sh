#!/usr/bin/env bash
# Validate EKS node count, taints, and network policy enforcement.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ARTIFACTS_DIR="${ROOT_DIR}/artifacts"
REPORT="${ARTIFACTS_DIR}/cluster-validation-report.txt"
ENV_NAME="${1:-dev}"
KUBECONFIG_PATH="${KUBECONFIG:-${ARTIFACTS_DIR}/kubeconfig-${ENV_NAME}}"
EXPECTED_NODES="${EXPECTED_NODE_COUNT:-}"

mkdir -p "${ARTIFACTS_DIR}"
export KUBECONFIG="${KUBECONFIG_PATH}"

if [[ ! -f "${KUBECONFIG}" ]]; then
  echo "Kubeconfig not found at ${KUBECONFIG}. Run scripts/export-kubeconfig.sh ${ENV_NAME} first." >&2
  exit 1
fi

if [[ -z "${EXPECTED_NODES}" && -f "${ARTIFACTS_DIR}/${ENV_NAME}-outputs.json" ]]; then
  EXPECTED_NODES="$(jq -r '.expected_node_count.value // empty' "${ARTIFACTS_DIR}/${ENV_NAME}-outputs.json")"
fi
EXPECTED_NODES="${EXPECTED_NODES:-5}"

{
  echo "FabOps Cluster Validation Report"
  echo "Environment: ${ENV_NAME}"
  echo "Timestamp:   $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "Kubeconfig:  ${KUBECONFIG}"
  echo "Expected nodes: ${EXPECTED_NODES}"
  echo "============================================================"
  echo ""
  echo "==> [1/3] kubectl get nodes"
  kubectl get nodes -o wide
  echo ""

  READY_COUNT="$(kubectl get nodes --no-headers 2>/dev/null | awk '$2 ~ /Ready/ {c++} END {print c+0}')"
  echo "Ready nodes: ${READY_COUNT} (expected ${EXPECTED_NODES})"
  if [[ "${READY_COUNT}" -lt "${EXPECTED_NODES}" ]]; then
    echo "RESULT: FAIL — node count below expected"
    NODE_OK=0
  else
    echo "RESULT: PASS — node count meets or exceeds expected"
    NODE_OK=1
  fi

  echo ""
  echo "==> [2/3] Check taints for workload segregation"
  kubectl get nodes -o custom-columns='NAME:.metadata.name,TAINTS:.spec.taints[*].key' --no-headers || true
  SYSTEM_TAINTED="$(kubectl get nodes -o json | jq '[.items[] | select(.spec.taints != null) | select(any(.spec.taints[]; .key=="CriticalAddonsOnly" or .key=="workload.fabops.io/dedicated"))] | length')"
  echo "System-tainted nodes: ${SYSTEM_TAINTED}"
  if [[ "${SYSTEM_TAINTED}" -ge 1 ]]; then
    echo "RESULT: PASS — workload segregation taints present"
    TAINT_OK=1
  else
    echo "RESULT: FAIL — expected CriticalAddonsOnly / workload.fabops.io/dedicated taints on system nodes"
    TAINT_OK=0
  fi

  echo ""
  echo "==> [3/3] Network policy denial test (apps → dev/ChartMuseum)"
  kubectl apply -f "${ROOT_DIR}/kubernetes/network-policies/network-policy-test.yaml"
  kubectl wait --for=condition=Ready pod/netpol-probe-client -n apps --timeout=120s || true
  kubectl wait --for=condition=Ready pod/netpol-probe-server -n dev --timeout=120s || true

  set +e
  DENY_OUTPUT="$(kubectl exec -n apps netpol-probe-client -- wget -qO- --timeout=5 http://netpol-probe-server.dev.svc.cluster.local 2>&1)"
  DENY_RC=$?
  set -e

  echo "wget exit code: ${DENY_RC}"
  echo "wget output: ${DENY_OUTPUT}"
  if [[ "${DENY_RC}" -ne 0 ]]; then
    echo "RESULT: PASS — prohibited cross-namespace traffic denied"
    NETPOL_OK=1
  else
    echo "RESULT: FAIL — prohibited traffic unexpectedly succeeded"
    NETPOL_OK=0
  fi

  echo ""
  echo "Cleaning up probe resources"
  kubectl delete -f "${ROOT_DIR}/kubernetes/network-policies/network-policy-test.yaml" --ignore-not-found

  echo ""
  echo "============================================================"
  echo "Summary: nodes=${NODE_OK} taints=${TAINT_OK} netpol=${NETPOL_OK}"
  if [[ "${NODE_OK}" -eq 1 && "${TAINT_OK}" -eq 1 && "${NETPOL_OK}" -eq 1 ]]; then
    echo "OVERALL: PASS"
    exit 0
  else
    echo "OVERALL: FAIL"
    exit 1
  fi
} | tee "${REPORT}"
