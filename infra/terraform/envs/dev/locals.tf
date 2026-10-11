locals {
  project     = "kiri"
  environment = "dev"

  name_prefix    = "${local.project}-${local.environment}"
  parameter_path = "/${local.project}/${local.environment}"

  github_repo = "KIRI-Team@331671213/KIRI-SERVER@1392618225"
}
