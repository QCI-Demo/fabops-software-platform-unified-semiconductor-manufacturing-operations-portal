variable "cluster_name" {
  description = "Name of the EKS cluster"
  type        = string
}

variable "cluster_version" {
  description = "Kubernetes version for the EKS control plane"
  type        = string
  default     = "1.30"
}

variable "vpc_id" {
  description = "VPC ID hosting the cluster"
  type        = string
}

variable "subnet_ids" {
  description = "Subnet IDs for the EKS control plane and node groups (private preferred)"
  type        = list(string)
}

variable "cluster_security_group_id" {
  description = "Additional security group attached to the control plane"
  type        = string
}

variable "node_security_group_id" {
  description = "Security group attached to managed node groups"
  type        = string
}

variable "endpoint_private_access" {
  description = "Enable private API server endpoint"
  type        = bool
  default     = true
}

variable "endpoint_public_access" {
  description = "Enable public API server endpoint"
  type        = bool
  default     = true
}

variable "public_access_cidrs" {
  description = "CIDR blocks allowed to reach the public API endpoint"
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "enabled_cluster_log_types" {
  description = "Control plane log types to enable"
  type        = list(string)
  default     = ["api", "audit", "authenticator", "controllerManager", "scheduler"]
}

variable "system_node_instance_types" {
  description = "Instance types for the system (tainted) node group"
  type        = list(string)
  default     = ["m5.large"]
}

variable "system_node_desired_size" {
  description = "Desired node count for the system node group"
  type        = number
  default     = 2
}

variable "system_node_min_size" {
  description = "Minimum node count for the system node group"
  type        = number
  default     = 2
}

variable "system_node_max_size" {
  description = "Maximum node count for the system node group"
  type        = number
  default     = 4
}

variable "app_node_instance_types" {
  description = "Instance types for the application node group sized for 5,000+ concurrent users"
  type        = list(string)
  default     = ["m5.2xlarge"]
}

variable "app_node_desired_size" {
  description = "Desired node count for the application node group"
  type        = number
  default     = 6
}

variable "app_node_min_size" {
  description = "Minimum node count for the application node group"
  type        = number
  default     = 3
}

variable "app_node_max_size" {
  description = "Maximum node count for the application node group"
  type        = number
  default     = 20
}

variable "node_disk_size" {
  description = "Root volume size (GiB) for managed nodes"
  type        = number
  default     = 100
}

variable "enable_irsa" {
  description = "Create IAM OIDC provider for IRSA (IAM Roles for Service Accounts)"
  type        = bool
  default     = true
}

variable "service_account_roles" {
  description = "Map of IRSA role definitions keyed by logical name"
  type = map(object({
    namespace            = string
    service_account_name = string
    policy_arns          = list(string)
    inline_policy_json   = optional(string)
  }))
  default = {}
}

variable "tags" {
  description = "Additional tags applied to all resources"
  type        = map(string)
  default     = {}
}
