locals {
  azs = ["ap-northeast-2a", "ap-northeast-2c"]
}

module "network" {
  source      = "../../modules/network"
  name_prefix = local.name_prefix
  vpc_cidr    = "10.0.0.0/16"
  azs         = local.azs
}