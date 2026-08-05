output "ecr_repository_urls" {
  description = "Map of ECR repository URLs"
  value       = { for k, r in aws_ecr_repository.this : k => r.repository_url }
}

output "ecr_repository_arns" {
  description = "Map of ECR repository ARNs"
  value       = { for k, r in aws_ecr_repository.this : k => r.arn }
}

output "secret_arns" {
  description = "Map of Secrets Manager secret ARNs (hooks for CSI driver)"
  value       = { for k, s in aws_secretsmanager_secret.hooks : k => s.arn }
}

output "secret_names" {
  description = "Map of Secrets Manager secret names"
  value       = { for k, s in aws_secretsmanager_secret.hooks : k => s.name }
}

output "namespace_names" {
  description = "List of provisioned Kubernetes namespaces"
  value       = [for n in kubernetes_namespace_v1.this : n.metadata[0].name]
}

output "chartmuseum_repository_url" {
  description = "In-cluster Helm repository URL for ChartMuseum"
  value       = local.chartmuseum_repo_url
}

output "chartmuseum_release_name" {
  description = "Helm release name for ChartMuseum"
  value       = try(helm_release.chartmuseum[0].name, null)
}
