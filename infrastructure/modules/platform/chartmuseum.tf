resource "helm_release" "chartmuseum" {
  count = var.deploy_chartmuseum ? 1 : 0

  name             = "chartmuseum"
  chart            = var.chartmuseum_chart_path
  namespace        = kubernetes_namespace_v1.this[var.chartmuseum_namespace].metadata[0].name
  create_namespace = false

  values = [
    yamlencode(merge({
      fullnameOverride = "chartmuseum"
      env = {
        environment = var.environment
      }
      serviceAccount = {
        create = true
        name   = "chartmuseum"
        annotations = var.chartmuseum_irsa_role_arn != null ? {
          "eks.amazonaws.com/role-arn" = var.chartmuseum_irsa_role_arn
        } : {}
      }
      publisher = {
        create = true
        serviceAccount = {
          name = "chart-publisher"
          annotations = var.chartmuseum_irsa_role_arn != null ? {
            "eks.amazonaws.com/role-arn" = var.chartmuseum_irsa_role_arn
          } : {}
        }
      }
      service = {
        type = "ClusterIP"
        port = 8080
      }
      persistence = {
        enabled = true
        size    = "10Gi"
      }
      networkPolicy = {
        enabled = true
      }
    }, var.chartmuseum_values))
  ]

  depends_on = [
    kubernetes_namespace_v1.this,
    kubernetes_network_policy_v1.default_deny,
    kubernetes_network_policy_v1.allow_dns,
    kubernetes_network_policy_v1.allow_same_namespace,
  ]
}

locals {
  chartmuseum_repo_url = var.deploy_chartmuseum ? "http://chartmuseum.${var.chartmuseum_namespace}.svc.cluster.local:8080" : null
}
