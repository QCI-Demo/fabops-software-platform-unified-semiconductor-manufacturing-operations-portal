data "tls_certificate" "oidc" {
  count = var.enable_irsa ? 1 : 0
  url   = aws_eks_cluster.this.identity[0].oidc[0].issuer
}

resource "aws_iam_openid_connect_provider" "this" {
  count = var.enable_irsa ? 1 : 0

  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = [data.tls_certificate.oidc[0].certificates[0].sha1_fingerprint]
  url             = aws_eks_cluster.this.identity[0].oidc[0].issuer

  tags = merge(local.common_tags, {
    Name = "${var.cluster_name}-oidc"
  })
}

locals {
  oidc_provider_arn = try(aws_iam_openid_connect_provider.this[0].arn, null)
  oidc_provider_url = try(replace(aws_iam_openid_connect_provider.this[0].url, "https://", ""), null)
}

data "aws_iam_policy_document" "irsa_assume_role" {
  for_each = var.enable_irsa ? var.service_account_roles : {}

  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_provider_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_url}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.oidc_provider_url}:sub"
      values   = ["system:serviceaccount:${each.value.namespace}:${each.value.service_account_name}"]
    }
  }
}

resource "aws_iam_role" "irsa" {
  for_each = var.enable_irsa ? var.service_account_roles : {}

  name               = "${var.cluster_name}-irsa-${each.key}"
  assume_role_policy = data.aws_iam_policy_document.irsa_assume_role[each.key].json
  tags               = local.common_tags
}

resource "aws_iam_role_policy_attachment" "irsa" {
  for_each = {
    for item in flatten([
      for role_key, role in var.service_account_roles : [
        for idx, arn in role.policy_arns : {
          key        = "${role_key}-${idx}"
          role_key   = role_key
          policy_arn = arn
        }
      ]
    ]) : item.key => item
    if var.enable_irsa
  }

  role       = aws_iam_role.irsa[each.value.role_key].name
  policy_arn = each.value.policy_arn
}

resource "aws_iam_role_policy" "irsa_inline" {
  for_each = {
    for k, v in var.service_account_roles : k => v
    if var.enable_irsa && try(v.inline_policy_json, null) != null
  }

  name   = "${var.cluster_name}-irsa-${each.key}-inline"
  role   = aws_iam_role.irsa[each.key].id
  policy = each.value.inline_policy_json
}
