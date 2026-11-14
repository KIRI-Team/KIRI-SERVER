locals {
  # 환경마다 같은 자리가 필요한 앱 환경변수
  external_parameters = [
    "JWT_SECRET",
    "APPLE_CLIENT_ID",
    "GOOGLE_CLIENT_ID",
    "KAKAO_NATIVE_APP_KEY",
    "SENTRY_DSN",
    "GEMINI_API_KEY",
    "FIREBASE_SERVICE_ACCOUNT_B64",
    "CLOUDFRONT_PRIVATE_KEY"
  ]
}

resource "aws_ssm_parameter" "external" {
  for_each = toset(local.external_parameters)

  name  = "${var.parameter_path}/${each.key}"
  type  = "SecureString"
  value = "CHANGE_ME"

  lifecycle {
    ignore_changes = [value]
  }
}

resource "aws_ssm_parameter" "mission_autopublish" {
  name  = "${var.parameter_path}/MISSION_AUTOPUBLISH"
  type  = "String"
  value = "false"

  lifecycle {
    ignore_changes = [value]
  }
}