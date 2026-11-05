locals {
  alloy_parameters = [
    "GRAFANA_PROM_URL",
    "GRAFANA_PROM_USER",
    "GRAFANA_CLOUD_TOKEN",
    "GRAFANA_LOKI_URL",
    "GRAFANA_LOKI_USER",
  ]
}

resource "aws_ssm_parameter" "alloy" {
  for_each = toset(local.alloy_parameters)

  name  = "${local.parameter_path}/alloy/${each.key}"
  type  = "SecureString"
  value = "CHANGE_ME"

  lifecycle {
    ignore_changes = [value]
  }
}