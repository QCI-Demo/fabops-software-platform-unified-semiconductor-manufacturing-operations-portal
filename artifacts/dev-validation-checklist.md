# Dev cluster validation checklist

Expected after `./scripts/provision-dev.sh`:

1. **Node count** — `kubectl get nodes` shows **5** Ready nodes (2 system + 3 apps).
2. **Taints** — system node group has:
   - `CriticalAddonsOnly=true:NoSchedule`
   - `workload.fabops.io/dedicated=system:NoSchedule`
3. **Network policy** — probe from `apps` → `dev` is **denied** (`scripts/validate-cluster.sh`).
4. **ChartMuseum** — Service/Deployment healthy in `dev`; SA `chart-publisher` bound to publisher Role.
5. **CI variables** — `HELM_REPO_URL=http://chartmuseum.dev.svc.cluster.local:8080` stored in Actions vars.

Offline checks completed in this agent run:

- [x] `terraform fmt`
- [x] `terraform init` (dev + prod)
- [x] `terraform validate` (dev + prod)
- [x] `helm lint charts/chartmuseum`
- [ ] `terraform plan/apply` — **blocked**: AWS credentials not available (requested via environment setup)
- [ ] `kubectl get nodes` / netpol test — **blocked**: requires live EKS cluster after apply
