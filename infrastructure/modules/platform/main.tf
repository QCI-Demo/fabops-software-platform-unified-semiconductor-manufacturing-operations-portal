locals {
  common_tags = merge(var.tags, {
    Module      = "platform"
    Environment = var.environment
  })

  secret_full_names = {
    for name in var.secret_names : name => "${var.name_prefix}/${var.environment}/${name}"
  }
}

# ---------------------------------------------------------------------------
# Container registry (ECR)
# ---------------------------------------------------------------------------

resource "aws_ecr_repository" "this" {
  for_each = toset(var.ecr_repositories)

  name                 = each.value
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = merge(local.common_tags, {
    Name = each.value
  })
}

resource "aws_ecr_lifecycle_policy" "this" {
  for_each = aws_ecr_repository.this

  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Retain last 30 images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 30
      }
      action = {
        type = "expire"
      }
    }]
  })
}

# ---------------------------------------------------------------------------
# Secret-management hooks (AWS Secrets Manager placeholders)
# ---------------------------------------------------------------------------

resource "aws_secretsmanager_secret" "hooks" {
  for_each = local.secret_full_names

  name                    = each.value
  description             = "FabOps ${var.environment} secret hook for ${each.key}"
  recovery_window_in_days = var.environment == "prod" ? 30 : 7

  tags = merge(local.common_tags, {
    Name       = each.value
    SecretHook = each.key
  })
}

# Placeholder versions — real values injected out-of-band; never commit secrets.
resource "aws_secretsmanager_secret_version" "hooks" {
  for_each = aws_secretsmanager_secret.hooks

  secret_id = each.value.id
  secret_string = jsonencode({
    placeholder = true
    secret_name = each.key
    environment = var.environment
    note        = "Replace via approved secret rotation process; do not store plaintext in Git"
  })

  lifecycle {
    ignore_changes = [secret_string]
  }
}

# ---------------------------------------------------------------------------
# Kubernetes namespaces
# ---------------------------------------------------------------------------

resource "kubernetes_namespace_v1" "this" {
  for_each = var.namespaces

  metadata {
    name = each.key
    labels = merge(
      {
        "app.kubernetes.io/part-of" = "fabops"
        "fabops.io/environment"     = var.environment
      },
      each.value.labels
    )
  }
}

# ---------------------------------------------------------------------------
# Baseline network policies (default-deny + DNS + same-namespace)
# ---------------------------------------------------------------------------

resource "kubernetes_network_policy_v1" "default_deny" {
  for_each = var.enable_network_policies ? var.namespaces : {}

  metadata {
    name      = "default-deny-all"
    namespace = kubernetes_namespace_v1.this[each.key].metadata[0].name
  }

  spec {
    pod_selector {}
    policy_types = ["Ingress", "Egress"]
  }
}

resource "kubernetes_network_policy_v1" "allow_dns" {
  for_each = var.enable_network_policies ? var.namespaces : {}

  metadata {
    name      = "allow-dns"
    namespace = kubernetes_namespace_v1.this[each.key].metadata[0].name
  }

  spec {
    pod_selector {}
    policy_types = ["Egress"]

    egress {
      to {
        namespace_selector {
          match_labels = {
            "kubernetes.io/metadata.name" = "kube-system"
          }
        }
        pod_selector {
          match_labels = {
            "k8s-app" = "kube-dns"
          }
        }
      }
      ports {
        protocol = "UDP"
        port     = 53
      }
      ports {
        protocol = "TCP"
        port     = 53
      }
    }
  }
}

resource "kubernetes_network_policy_v1" "allow_same_namespace" {
  for_each = var.enable_network_policies ? var.namespaces : {}

  metadata {
    name      = "allow-same-namespace"
    namespace = kubernetes_namespace_v1.this[each.key].metadata[0].name
  }

  spec {
    pod_selector {}
    policy_types = ["Ingress", "Egress"]

    ingress {
      from {
        pod_selector {}
      }
    }

    egress {
      to {
        pod_selector {}
      }
    }
  }
}

# Explicitly deny cross-namespace traffic from apps -> chartmuseum except publisher SA path
# (enforced by absence of allow rules + default-deny)
