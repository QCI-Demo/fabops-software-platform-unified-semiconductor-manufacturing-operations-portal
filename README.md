# FabOps Software Platform

Unified Semiconductor Manufacturing Operations Portal — cloud-native platform foundation.

## Kubernetes Platform Baseline

This repository provisions a version-controlled Kubernetes platform baseline with:

- **Terraform VPC module** — CIDR/AZ parameterized VPC, subnets, route tables, security groups
- **Terraform EKS module** — node pools sized for 5,000+ concurrent users, IAM OIDC / IRSA
- **ChartMuseum Helm chart** — in-cluster Helm repository with publisher RBAC (deployed to `dev` namespace)
- **Dev/Prod environments** — repeatable root modules with remote-state hooks
- **Network policies** — default-deny + DNS + same-namespace, with validation scripts

### Quick start (dev)

```bash
./scripts/provision-dev.sh          # terraform init → plan → apply
./scripts/export-kubeconfig.sh dev  # write artifacts/kubeconfig-dev
./scripts/validate-cluster.sh dev   # nodes, taints, netpol denial test
```

See [docs/platform-baseline.md](docs/platform-baseline.md) for architecture, sizing, and CI variable setup.

### Layout

```
infrastructure/
  modules/{vpc,eks,platform}/
  environments/{dev,prod}/
charts/chartmuseum/
kubernetes/{namespaces,network-policies}/
scripts/
artifacts/          # plan/apply outputs, kubeconfig (gitignored secrets)
```
