output "aws_region" {
  value = var.aws_region
}

output "vpc_id" {
  value = module.vpc.vpc_id
}

output "cluster_name" {
  value = module.eks.cluster_name
}

output "cluster_endpoint" {
  value = module.eks.cluster_endpoint
}

output "expected_node_count" {
  value = module.eks.expected_node_count
}

output "chartmuseum_repository_url" {
  value = try(module.platform[0].chartmuseum_repository_url, null)
}

output "ecr_repository_urls" {
  value = try(module.platform[0].ecr_repository_urls, {})
}

output "kubeconfig_path" {
  value = abspath(local_file.kubeconfig.filename)
}

output "oidc_provider_arn" {
  value = module.eks.oidc_provider_arn
}
