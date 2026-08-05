variable "name_prefix" {
  description = "Prefix for AWS resource names"
  type        = string
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
}

variable "cluster_name" {
  description = "EKS cluster name (used for tagging and IRSA annotations)"
  type        = string
}

variable "chartmuseum_namespace" {
  description = "Namespace where ChartMuseum is deployed (story target: dev)"
  type        = string
  default     = "dev"
}

variable "namespaces" {
  description = "Kubernetes namespaces to create with labels"
  type = map(object({
    labels = optional(map(string), {})
  }))
  default = {
    platform = {
      labels = { "fabops.io/tier" = "platform" }
    }
    apps = {
      labels = { "fabops.io/tier" = "application" }
    }
    monitoring = {
      labels = { "fabops.io/tier" = "observability" }
    }
    dev = {
      labels = { "fabops.io/tier" = "platform", "fabops.io/component" = "helm-repo", "fabops.io/env-alias" = "dev" }
    }
  }
}

variable "ecr_repositories" {
  description = "ECR repository names to create for container images"
  type        = list(string)
  default     = ["fabops/portal", "fabops/equipment-monitor", "fabops/workflow-engine"]
}

variable "secret_names" {
  description = "Secrets Manager secret name suffixes to provision as hooks for CSI driver"
  type        = list(string)
  default     = ["database", "oidc", "kafka"]
}

variable "chartmuseum_irsa_role_arn" {
  description = "Optional IRSA role ARN annotated onto the ChartMuseum publisher ServiceAccount"
  type        = string
  default     = null
}

variable "deploy_chartmuseum" {
  description = "Deploy ChartMuseum via Helm into the chartmuseum namespace"
  type        = bool
  default     = true
}

variable "chartmuseum_chart_path" {
  description = "Filesystem path to the local ChartMuseum Helm chart"
  type        = string
  default     = "../../../charts/chartmuseum"
}

variable "chartmuseum_values" {
  description = "Additional Helm values for ChartMuseum"
  type        = map(any)
  default     = {}
}

variable "enable_network_policies" {
  description = "Apply baseline NetworkPolicy resources"
  type        = bool
  default     = true
}

variable "tags" {
  description = "Additional tags"
  type        = map(string)
  default     = {}
}
