module "vpc" {
  source = "../../modules/vpc"

  name                  = var.name_prefix
  cidr_block            = var.vpc_cidr
  azs                   = var.azs
  public_subnet_cidrs   = var.public_subnet_cidrs
  private_subnet_cidrs  = var.private_subnet_cidrs
  database_subnet_cidrs = var.database_subnet_cidrs
  enable_nat_gateway    = true
  single_nat_gateway    = false
  enable_flow_logs      = true

  tags = {
    Environment = "prod"
  }
}

module "eks" {
  source = "../../modules/eks"

  cluster_name              = var.cluster_name
  cluster_version           = var.cluster_version
  vpc_id                    = module.vpc.vpc_id
  subnet_ids                = module.vpc.private_subnet_ids
  cluster_security_group_id = module.vpc.cluster_security_group_id
  node_security_group_id    = module.vpc.node_security_group_id

  endpoint_private_access = true
  endpoint_public_access  = true
  public_access_cidrs     = var.public_access_cidrs

  system_node_instance_types = var.system_node_instance_types
  system_node_desired_size   = var.system_node_desired_size
  system_node_min_size       = 3
  system_node_max_size       = 6

  app_node_instance_types = var.app_node_instance_types
  app_node_desired_size   = var.app_node_desired_size
  app_node_min_size       = 3
  app_node_max_size       = 20

  enable_irsa = true

  service_account_roles = {
    chart_publisher = {
      namespace            = "dev"
      service_account_name = "chart-publisher"
      policy_arns          = ["arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"]
      inline_policy_json = jsonencode({
        Version = "2012-10-17"
        Statement = [{
          Effect = "Allow"
          Action = [
            "secretsmanager:GetSecretValue",
            "secretsmanager:DescribeSecret"
          ]
          Resource = "arn:aws:secretsmanager:${var.aws_region}:*:secret:${var.name_prefix}/prod/*"
        }]
      })
    }
    external_secrets = {
      namespace            = "platform"
      service_account_name = "external-secrets"
      policy_arns          = []
      inline_policy_json = jsonencode({
        Version = "2012-10-17"
        Statement = [{
          Effect = "Allow"
          Action = [
            "secretsmanager:GetSecretValue",
            "secretsmanager:DescribeSecret",
            "secretsmanager:ListSecrets"
          ]
          Resource = "arn:aws:secretsmanager:${var.aws_region}:*:secret:${var.name_prefix}/prod/*"
        }]
      })
    }
  }

  tags = {
    Environment = "prod"
  }
}

module "platform" {
  count  = var.enable_platform_bootstrap ? 1 : 0
  source = "../../modules/platform"

  name_prefix               = var.name_prefix
  environment               = "prod"
  cluster_name              = module.eks.cluster_name
  chartmuseum_namespace     = "dev"
  chartmuseum_irsa_role_arn = try(module.eks.irsa_role_arns["chart_publisher"], null)
  chartmuseum_chart_path    = "${path.module}/../../../charts/chartmuseum"
  deploy_chartmuseum        = true
  enable_network_policies   = true

  tags = {
    Environment = "prod"
  }

  depends_on = [module.eks]
}

resource "local_file" "kubeconfig" {
  filename = var.kubeconfig_path
  content = yamlencode({
    apiVersion = "v1"
    kind       = "Config"
    clusters = [{
      name = module.eks.cluster_name
      cluster = {
        server                     = module.eks.cluster_endpoint
        certificate-authority-data = module.eks.cluster_certificate_authority_data
      }
    }]
    contexts = [{
      name = module.eks.cluster_name
      context = {
        cluster = module.eks.cluster_name
        user    = module.eks.cluster_name
      }
    }]
    current-context = module.eks.cluster_name
    users = [{
      name = module.eks.cluster_name
      user = {
        exec = {
          apiVersion = "client.authentication.k8s.io/v1beta1"
          command    = "aws"
          args = [
            "eks",
            "get-token",
            "--cluster-name",
            module.eks.cluster_name,
            "--region",
            var.aws_region,
          ]
        }
      }
    }]
  })

  file_permission = "0600"
}
