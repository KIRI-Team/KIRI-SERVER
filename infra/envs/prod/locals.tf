locals {
  project     = "kiri"
  environment = "prod"

  name_prefix = "${local.project}-${local.environment}"
}