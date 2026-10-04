locals {
  project     = "kiri"
  environment = "dev"

  name_prefix = "${local.project}-${local.environment}"
}