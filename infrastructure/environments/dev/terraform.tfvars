aws_region   = "us-east-1"
name_prefix  = "fabops-dev"
cluster_name = "fabops-dev"
vpc_cidr     = "10.20.0.0/16"
azs          = ["us-east-1a", "us-east-1b", "us-east-1c"]

public_subnet_cidrs   = ["10.20.0.0/20", "10.20.16.0/20", "10.20.32.0/20"]
private_subnet_cidrs  = ["10.20.64.0/20", "10.20.80.0/20", "10.20.96.0/20"]
database_subnet_cidrs = ["10.20.128.0/24", "10.20.129.0/24", "10.20.130.0/24"]

cluster_version            = "1.30"
system_node_instance_types = ["m5.large"]
system_node_desired_size   = 2
app_node_instance_types    = ["m5.xlarge"]
app_node_desired_size      = 3
enable_platform_bootstrap  = true
kubeconfig_path            = "../../../artifacts/kubeconfig-dev"
