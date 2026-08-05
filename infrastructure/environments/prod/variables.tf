variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "name_prefix" {
  type    = string
  default = "fabops-prod"
}

variable "vpc_cidr" {
  type    = string
  default = "10.30.0.0/16"
}

variable "azs" {
  type    = list(string)
  default = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.30.0.0/20", "10.30.16.0/20", "10.30.32.0/20"]
}

variable "private_subnet_cidrs" {
  type    = list(string)
  default = ["10.30.64.0/20", "10.30.80.0/20", "10.30.96.0/20"]
}

variable "database_subnet_cidrs" {
  type    = list(string)
  default = ["10.30.128.0/24", "10.30.129.0/24", "10.30.130.0/24"]
}

variable "cluster_name" {
  type    = string
  default = "fabops-prod"
}

variable "cluster_version" {
  type    = string
  default = "1.30"
}

variable "system_node_instance_types" {
  type    = list(string)
  default = ["m5.xlarge"]
}

variable "system_node_desired_size" {
  type    = number
  default = 3
}

variable "app_node_instance_types" {
  description = "Sized for 5,000+ concurrent users"
  type        = list(string)
  default     = ["m5.2xlarge"]
}

variable "app_node_desired_size" {
  type    = number
  default = 6
}

variable "enable_platform_bootstrap" {
  type    = bool
  default = true
}

variable "kubeconfig_path" {
  type    = string
  default = "../../../artifacts/kubeconfig-prod"
}

variable "public_access_cidrs" {
  description = "Restrict public API access in production"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}
