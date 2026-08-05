variable "aws_region" {
  description = "AWS region for the dev platform"
  type        = string
  default     = "us-east-1"
}

variable "name_prefix" {
  description = "Resource name prefix"
  type        = string
  default     = "fabops-dev"
}

variable "vpc_cidr" {
  description = "VPC CIDR block"
  type        = string
  default     = "10.20.0.0/16"
}

variable "azs" {
  description = "Availability zones"
  type        = list(string)
  default     = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.0.0/20", "10.20.16.0/20", "10.20.32.0/20"]
}

variable "private_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.64.0/20", "10.20.80.0/20", "10.20.96.0/20"]
}

variable "database_subnet_cidrs" {
  type    = list(string)
  default = ["10.20.128.0/24", "10.20.129.0/24", "10.20.130.0/24"]
}

variable "cluster_name" {
  type    = string
  default = "fabops-dev"
}

variable "cluster_version" {
  type    = string
  default = "1.30"
}

variable "system_node_instance_types" {
  type    = list(string)
  default = ["m5.large"]
}

variable "system_node_desired_size" {
  type    = number
  default = 2
}

variable "app_node_instance_types" {
  type    = list(string)
  default = ["m5.xlarge"]
}

variable "app_node_desired_size" {
  description = "Desired app nodes for non-prod (scaled down from prod 5k-user baseline)"
  type        = number
  default     = 3
}

variable "enable_platform_bootstrap" {
  description = "After EKS is up, bootstrap namespaces, ChartMuseum, network policies, ECR, and secret hooks"
  type        = bool
  default     = true
}

variable "kubeconfig_path" {
  description = "Local path where kubeconfig for the dev cluster is written"
  type        = string
  default     = "../../../artifacts/kubeconfig-dev"
}
