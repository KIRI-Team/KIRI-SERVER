locals {
  azs = ["ap-northeast-2a", "ap-northeast-2c"]
}

module "network" {
  source      = "../../modules/network"
  name_prefix = local.name_prefix
  vpc_cidr    = "10.0.0.0/16"
  azs         = local.azs

  # 도메인 확보 전까지 앱 포트 임시 개방
  app_ingress_cidrs = ["0.0.0.0/0"]
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

module "rds" {
  source = "../../modules/rds"

  name_prefix = local.name_prefix

  subnet_ids        = module.network.private_subnet_ids
  security_group_id = module.network.rds_sg_id
  availability_zone = local.azs[0]

  instance_class = "db.t4g.micro"
  engine_version = "18"

  db_name     = "kiri"
  db_username = "kiri"

  backup_retention_days = 1
  deletion_protection   = false
  skip_final_snapshot   = true
  apply_immediately     = true

  parameter_path = local.parameter_path
}

module "github_deploy" {
  source = "../../modules/github_deploy"

  name_prefix = local.name_prefix

  github_repo        = local.github_repo
  github_environment = "dev"

  ecr_repository_name = "kiri-server"
  instance_id         = module.ec2.instance_id
}
