aws_region   = "us-east-1"
name_prefix  = "fabops-prod"
cluster_name = "fabops-prod"
vpc_cidr     = "10.30.0.0/16"
azs          = ["us-east-1a", "us-east-1b", "us-east-1c"]

public_subnet_cidrs   = ["10.30.0.0/20", "10.30.16.0/20", "10.30.32.0/20"]
private_subnet_cidrs  = ["10.30.64.0/20", "10.30.80.0/20", "10.30.96.0/20"]
database_subnet_cidrs = ["10.30.128.0/24", "10.30.129.0/24", "10.30.130.0/24"]

cluster_version            = "1.30"
system_node_instance_types = ["m5.xlarge"]
system_node_desired_size   = 3
app_node_instance_types    = ["m5.2xlarge"]
app_node_desired_size      = 6
enable_platform_bootstrap  = true
