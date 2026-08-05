locals {
  az_count = length(var.azs)

  common_tags = merge(var.tags, {
    Module = "vpc"
    Name   = var.name
  })

  nat_gateway_count = var.enable_nat_gateway ? (var.single_nat_gateway ? 1 : local.az_count) : 0
}

resource "aws_vpc" "this" {
  cidr_block           = var.cidr_block
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(local.common_tags, {
    Name = "${var.name}-vpc"
  })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${var.name}-igw"
  })
}

resource "aws_subnet" "public" {
  count = local.az_count

  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.azs[count.index]
  map_public_ip_on_launch = true

  tags = merge(local.common_tags, {
    Name                     = "${var.name}-public-${var.azs[count.index]}"
    Tier                     = "public"
    "kubernetes.io/role/elb" = "1"
  })
}

resource "aws_subnet" "private" {
  count = local.az_count

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = var.azs[count.index]

  tags = merge(local.common_tags, {
    Name                              = "${var.name}-private-${var.azs[count.index]}"
    Tier                              = "private"
    "kubernetes.io/role/internal-elb" = "1"
  })
}

resource "aws_subnet" "database" {
  count = length(var.database_subnet_cidrs)

  vpc_id            = aws_vpc.this.id
  cidr_block        = var.database_subnet_cidrs[count.index]
  availability_zone = var.azs[count.index % local.az_count]

  tags = merge(local.common_tags, {
    Name = "${var.name}-database-${var.azs[count.index % local.az_count]}"
    Tier = "database"
  })
}

resource "aws_eip" "nat" {
  count  = local.nat_gateway_count
  domain = "vpc"

  tags = merge(local.common_tags, {
    Name = "${var.name}-nat-eip-${count.index}"
  })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_nat_gateway" "this" {
  count = local.nat_gateway_count

  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = merge(local.common_tags, {
    Name = "${var.name}-nat-${count.index}"
  })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${var.name}-public-rt"
    Tier = "public"
  })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  count = local.az_count

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_route_table" "private" {
  count = local.az_count

  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${var.name}-private-rt-${var.azs[count.index]}"
    Tier = "private"
  })
}

resource "aws_route" "private_nat" {
  count = var.enable_nat_gateway ? local.az_count : 0

  route_table_id         = aws_route_table.private[count.index].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id         = aws_nat_gateway.this[var.single_nat_gateway ? 0 : count.index].id
}

resource "aws_route_table_association" "private" {
  count = local.az_count

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[count.index].id
}

resource "aws_route_table" "database" {
  count = length(var.database_subnet_cidrs) > 0 ? 1 : 0

  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${var.name}-database-rt"
    Tier = "database"
  })
}

resource "aws_route_table_association" "database" {
  count = length(var.database_subnet_cidrs)

  subnet_id      = aws_subnet.database[count.index].id
  route_table_id = aws_route_table.database[0].id
}

# Cluster control-plane security group (additional rules attached by EKS module)
resource "aws_security_group" "cluster" {
  name_prefix = "${var.name}-cluster-"
  description = "Control plane security group for EKS"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${var.name}-cluster-sg"
  })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group" "nodes" {
  name_prefix = "${var.name}-nodes-"
  description = "Worker node security group for EKS"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${var.name}-nodes-sg"
  })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_security_group_rule" "cluster_ingress_from_nodes" {
  type                     = "ingress"
  description              = "Allow worker nodes to communicate with control plane"
  from_port                = 443
  to_port                  = 443
  protocol                 = "tcp"
  security_group_id        = aws_security_group.cluster.id
  source_security_group_id = aws_security_group.nodes.id
}

resource "aws_security_group_rule" "cluster_egress_to_nodes" {
  type                     = "egress"
  description              = "Allow control plane to reach kubelets"
  from_port                = 1025
  to_port                  = 65535
  protocol                 = "tcp"
  security_group_id        = aws_security_group.cluster.id
  source_security_group_id = aws_security_group.nodes.id
}

resource "aws_security_group_rule" "nodes_ingress_self" {
  type              = "ingress"
  description       = "Allow node-to-node communication"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  security_group_id = aws_security_group.nodes.id
  self              = true
}

resource "aws_security_group_rule" "nodes_ingress_from_cluster" {
  type                     = "ingress"
  description              = "Allow control plane to reach nodes"
  from_port                = 1025
  to_port                  = 65535
  protocol                 = "tcp"
  security_group_id        = aws_security_group.nodes.id
  source_security_group_id = aws_security_group.cluster.id
}

resource "aws_security_group_rule" "nodes_egress_all" {
  type              = "egress"
  description       = "Allow all egress from nodes"
  from_port         = 0
  to_port           = 0
  protocol          = "-1"
  cidr_blocks       = ["0.0.0.0/0"]
  security_group_id = aws_security_group.nodes.id
}

resource "aws_cloudwatch_log_group" "flow_logs" {
  count = var.enable_flow_logs ? 1 : 0

  name              = "/fabops/${var.name}/vpc-flow-logs"
  retention_in_days = 30

  tags = local.common_tags
}

resource "aws_iam_role" "flow_logs" {
  count = var.enable_flow_logs ? 1 : 0

  name = "${var.name}-vpc-flow-logs"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Service = "vpc-flow-logs.amazonaws.com"
      }
      Action = "sts:AssumeRole"
    }]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy" "flow_logs" {
  count = var.enable_flow_logs ? 1 : 0

  name = "${var.name}-vpc-flow-logs"
  role = aws_iam_role.flow_logs[0].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogGroups",
        "logs:DescribeLogStreams"
      ]
      Resource = "*"
    }]
  })
}

resource "aws_flow_log" "this" {
  count = var.enable_flow_logs ? 1 : 0

  vpc_id                   = aws_vpc.this.id
  traffic_type             = "ALL"
  log_destination_type     = "cloud-watch-logs"
  log_destination          = aws_cloudwatch_log_group.flow_logs[0].arn
  iam_role_arn             = aws_iam_role.flow_logs[0].arn
  max_aggregation_interval = 60

  tags = merge(local.common_tags, {
    Name = "${var.name}-flow-logs"
  })
}
