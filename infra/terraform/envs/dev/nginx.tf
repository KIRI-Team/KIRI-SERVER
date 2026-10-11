resource "random_password" "nginx_basic_auth" {
  length  = 24
  special = false
}

resource "aws_ssm_parameter" "nginx_basic_auth_user" {
  name  = "${local.parameter_path}/nginx/BASIC_AUTH_USER"
  type  = "SecureString"
  value = "kiri"
}

resource "aws_ssm_parameter" "nginx_basic_auth_password" {
  name  = "${local.parameter_path}/nginx/BASIC_AUTH_PASSWORD"
  type  = "SecureString"
  value = random_password.nginx_basic_auth.result
}
