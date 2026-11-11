locals {
  azs = ["ap-northeast-2a", "ap-northeast-2c"]
}

module "network" {
  source      = "../../modules/network"
  name_prefix = local.name_prefix
  vpc_cidr    = "10.1.0.0/16"
  azs         = local.azs
}

module "ec2" {
  source = "../../modules/ec2"

  name_prefix   = local.name_prefix
  instance_type = "t4g.small"

  subnet_id         = module.network.public_subnet_ids[0]
  security_group_id = module.network.ec2_sg_id

  parameter_path     = local.parameter_path
  log_retention_days = 30

  s3_bucket_arns = [module.images.bucket_arn]
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
  deletion_protection   = true
  skip_final_snapshot   = false

  parameter_path = local.parameter_path
}

module "images" {
  source = "../../modules/images"

  name_prefix = local.name_prefix

  signing_public_key = file("${path.module}/keys/cloudfront-public.pem")

  parameter_path = local.parameter_path
}

module "app_parameters" {
  source         = "../../modules/app_parameters"
  parameter_path = local.parameter_path
}
