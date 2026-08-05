# FabOps Kubernetes Platform Baseline

Version-controlled infrastructure-as-code for repeatable **dev** and **prod** Kubernetes environments on AWS EKS.

## Architecture

| Layer | Module / Path | Purpose |
|-------|---------------|---------|
| Network | `infrastructure/modules/vpc` | VPC, public/private/database subnets, route tables, NAT, security groups, flow logs |
| Compute | `infrastructure/modules/eks` | EKS control plane, system + app node groups, OIDC/IRSA |
| Platform | `infrastructure/modules/platform` | Namespaces, NetworkPolicies, ECR, Secrets Manager hooks, ChartMuseum |
| Charts | `charts/chartmuseum` | Helm chart repository with publisher ServiceAccount + RBAC |
| Envs | `infrastructure/environments/{dev,prod}` | Root modules wiring the above |

### Node sizing (5,000+ concurrent users)

- **Prod apps node group**: `m5.2xlarge`, desired 6 (min 3 / max 20)
- **Prod system node group**: `m5.xlarge`, desired 3, tainted `CriticalAddonsOnly` + `workload.fabops.io/dedicated=system:NoSchedule`
- **Dev**: scaled-down (`m5.xlarge` × 3 apps, `m5.large` × 2 system)

Expected Ready node counts: **dev = 5**, **prod = 9**.

## Provisioning (dev)

```bash
# Optional: configure remote state
cp infrastructure/environments/dev/backend.hcl.example \
   infrastructure/environments/dev/backend.hcl
# Edit bucket/table names, then:

./scripts/provision-dev.sh
./scripts/export-kubeconfig.sh dev
./scripts/validate-cluster.sh dev
```

Artifacts land in `artifacts/`:

- `dev.tfplan` / `dev-plan.txt` / `dev-apply.log`
- `dev-outputs.json`
- `kubeconfig-dev`
- `ci-variables.env` (Helm repo URL for CI)
- `cluster-validation-report.txt`

## ChartMuseum + CI variables

ChartMuseum is deployed to the **`dev`** namespace with:

- ServiceAccount `chartmuseum` (runtime)
- ServiceAccount `chart-publisher` + Role/RoleBinding for push jobs
- In-cluster URL: `http://chartmuseum.dev.svc.cluster.local:8080`

Store in GitHub Actions variables (see `.github/ci-variables.env.example`):

- `HELM_REPO_URL`
- `HELM_REPO_NAME`
- `CHARTMUSEUM_NAMESPACE`
- `CHART_PUBLISHER_SA`

## Network policy validation

Baseline policies per namespace:

1. `default-deny-all`
2. `allow-dns` (kube-dns only)
3. `allow-same-namespace`

`scripts/validate-cluster.sh` launches a client in `apps` that attempts HTTP to a probe in `dev` and asserts **denial**.

## Secret-management hooks

AWS Secrets Manager secrets are created as placeholders under:

`{name_prefix}/{environment}/{database|oidc|kafka}`

IRSA roles for `external-secrets` (platform) and `chart-publisher` (dev) are wired for CSI/External Secrets consumption. **Never commit plaintext secret values.**

## Container registry

ECR repositories (immutable tags, scan-on-push):

- `fabops/portal`
- `fabops/equipment-monitor`
- `fabops/workflow-engine`
