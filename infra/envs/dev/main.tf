locals {
  azs = ["ap-northeast-2a", "ap-northeast-2c"]
}

module "network" {
  source      = "../../modules/network"
  name_prefix = local.name_prefix
  vpc_cidr    = "10.0.0.0/16"
  azs         = local.azs
}

module "ec2" {
  source = "../../modules/ec2"

  name_prefix   = local.name_prefix
  instance_type = "t4g.small"

  subnet_id         = module.network.public_subnet_ids[0]
  security_group_id = module.network.ec2_sg_id

  parameter_path     = local.parameter_path
  log_retention_days = 7
}
