output "cluster_name" {
  description = "Name of the EKS cluster"
  value       = aws_eks_cluster.this.name
}

output "cluster_arn" {
  description = "ARN of the EKS cluster"
  value       = aws_eks_cluster.this.arn
}

output "cluster_endpoint" {
  description = "API server endpoint"
  value       = aws_eks_cluster.this.endpoint
}

output "cluster_certificate_authority_data" {
  description = "Base64 encoded certificate data required to communicate with the cluster"
  value       = aws_eks_cluster.this.certificate_authority[0].data
  sensitive   = true
}

output "cluster_security_group_id" {
  description = "Security group created by EKS for the cluster"
  value       = aws_eks_cluster.this.vpc_config[0].cluster_security_group_id
}

output "oidc_provider_arn" {
  description = "ARN of the IAM OIDC provider used for IRSA"
  value       = local.oidc_provider_arn
}

output "oidc_provider_url" {
  description = "Issuer URL of the OIDC provider (without https://)"
  value       = local.oidc_provider_url
}

output "node_role_arn" {
  description = "IAM role ARN used by managed node groups"
  value       = aws_iam_role.node.arn
}

output "cluster_role_arn" {
  description = "IAM role ARN used by the EKS control plane"
  value       = aws_iam_role.cluster.arn
}

output "system_node_group_name" {
  description = "Name of the system (tainted) node group"
  value       = aws_eks_node_group.system.node_group_name
}

output "app_node_group_name" {
  description = "Name of the application node group"
  value       = aws_eks_node_group.apps.node_group_name
}

output "expected_node_count" {
  description = "Sum of desired sizes across managed node groups"
  value       = local.expected_node_count
}

output "irsa_role_arns" {
  description = "Map of IRSA role ARNs keyed by logical name"
  value       = { for k, r in aws_iam_role.irsa : k => r.arn }
}

output "kubeconfig_command" {
  description = "AWS CLI command to update kubeconfig for this cluster"
  value       = "aws eks update-kubeconfig --name ${aws_eks_cluster.this.name} --region $${AWS_REGION}"
}
