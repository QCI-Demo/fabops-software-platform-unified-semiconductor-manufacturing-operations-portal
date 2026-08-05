# Provisioning artifacts

This directory receives outputs from `scripts/provision-dev.sh`, `export-kubeconfig.sh`, and `validate-cluster.sh`.

| File | Source |
|------|--------|
| `dev-init.log` | `terraform init` |
| `dev-plan.txt` / `dev.tfplan` | `terraform plan` |
| `dev-apply.log` | `terraform apply -auto-approve` |
| `dev-outputs.json` | `terraform output -json` |
| `kubeconfig-dev` | exported via AWS EKS |
| `ci-variables.env` | ChartMuseum repository URL for CI |
| `cluster-validation-report.txt` | node/taint/netpol checks |

Kubeconfig and state files are gitignored. Keep remote state in the encrypted S3 backend (`backend.hcl`).
