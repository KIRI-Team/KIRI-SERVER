locals {
  project     = "kiri"
  environment = "dev"

  name_prefix    = "${local.project}-${local.environment}"
  parameter_path = "/${local.project}/${local.environment}"
}
