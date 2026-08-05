output "aws_region" {
  value = var.aws_region
}

output "vpc_id" {
  value = module.vpc.vpc_id
}

output "public_subnet_ids" {
  value = module.vpc.public_subnet_ids
}

output "private_subnet_ids" {
  value = module.vpc.private_subnet_ids
}

output "cluster_name" {
  value = module.eks.cluster_name
}

output "cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "oidc_provider_arn" {
  value = module.eks.oidc_provider_arn
}

output "expected_node_count" {
  description = "Expected Ready node count after apply (system + apps desired sizes)"
  value       = module.eks.expected_node_count
}

output "system_node_group_name" {
  value = module.eks.system_node_group_name
}

output "app_node_group_name" {
  value = module.eks.app_node_group_name
}

output "irsa_role_arns" {
  value = module.eks.irsa_role_arns
}

output "ecr_repository_urls" {
  value = try(module.platform[0].ecr_repository_urls, {})
}

output "secret_arns" {
  value = try(module.platform[0].secret_arns, {})
}

output "chartmuseum_repository_url" {
  description = "Helm repository URL to store in CI variables"
  value       = try(module.platform[0].chartmuseum_repository_url, null)
}

output "namespace_names" {
  value = try(module.platform[0].namespace_names, [])
}

output "kubeconfig_path" {
  value = abspath(local_file.kubeconfig.filename)
}

output "kubeconfig_command" {
  value = module.eks.kubeconfig_command
}
