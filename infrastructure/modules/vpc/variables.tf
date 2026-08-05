variable "name" {
  description = "Name prefix applied to VPC resources"
  type        = string
}

variable "cidr_block" {
  description = "IPv4 CIDR block for the VPC"
  type        = string

  validation {
    condition     = can(cidrnetmask(var.cidr_block))
    error_message = "cidr_block must be a valid IPv4 CIDR."
  }
}

variable "azs" {
  description = "Availability zones used for subnet placement"
  type        = list(string)

  validation {
    condition     = length(var.azs) >= 2
    error_message = "Provide at least two availability zones."
  }
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (one per AZ)"
  type        = list(string)
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets hosting EKS nodes (one per AZ)"
  type        = list(string)
}

variable "database_subnet_cidrs" {
  description = "Optional CIDR blocks for isolated database subnets"
  type        = list(string)
  default     = []
}

variable "enable_nat_gateway" {
  description = "Create NAT gateways for private subnet egress"
  type        = bool
  default     = true
}

variable "single_nat_gateway" {
  description = "Use a single shared NAT gateway (cost-optimized for non-prod)"
  type        = bool
  default     = false
}

variable "enable_flow_logs" {
  description = "Enable VPC flow logs to CloudWatch"
  type        = bool
  default     = true
}

variable "tags" {
  description = "Additional tags applied to all resources"
  type        = map(string)
  default     = {}
}
