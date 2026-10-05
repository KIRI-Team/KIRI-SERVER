resource "random_password" "db" {
  length  = 32
  special = false
}

# Subnet Group
resource "aws_db_subnet_group" "this" {
  name       = "${var.name_prefix}-db-subnet"
  subnet_ids = var.subnet_ids
  tags       = { Name = "${var.name_prefix}-db-subnet" }
}

# DB 인스턴스
resource "aws_db_instance" "this" {
  identifier     = "${var.name_prefix}-db"
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  # 스토리지
  allocated_storage = var.allocated_storage
  storage_type      = "gp3"
  storage_encrypted = true

  # 초기 DB와 계정
  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  # 네트워크
  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.security_group_id]
  availability_zone      = var.availability_zone
  publicly_accessible    = false
  multi_az               = false

  # 백업
  backup_retention_period    = var.backup_retention_days
  apply_immediately          = var.apply_immediately
  auto_minor_version_upgrade = true

  # 삭제
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : "${var.name_prefix}-db-final"

  tags = { Name = "${var.name_prefix}-db" }
}

# Parameter Store
resource "aws_ssm_parameter" "db_url" {
  name  = "${var.parameter_path}/DB_URL"
  type  = "SecureString"
  value = "jdbc:postgresql://${aws_db_instance.this.endpoint}/${var.db_name}"
}

resource "aws_ssm_parameter" "db_user" {
  name  = "${var.parameter_path}/DB_USER"
  type  = "SecureString"
  value = var.db_username
}

resource "aws_ssm_parameter" "db_pw" {
  name  = "${var.parameter_path}/DB_PW"
  type  = "SecureString"
  value = random_password.db.result
}

