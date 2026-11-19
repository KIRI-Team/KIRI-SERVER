data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

locals {
  sentry_alert_name       = "kiri-sentry-alert"
  sentry_alert_param_path = "/kiri/shared/sentry-alert"

  sentry_alert_parameters = [
    "DISCORD_WEBHOOK_URL",
    "SENTRY_CLIENT_SECRET",
  ]
}

data "archive_file" "sentry_alert" {
  type        = "zip"
  source_dir  = "${path.module}/lambda/sentry_alert"
  output_path = "${path.module}/.build/sentry_alert.zip"
  excludes    = ["__pycache__"]
}

data "aws_iam_policy_document" "sentry_alert_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "sentry_alert" {
  name               = local.sentry_alert_name
  assume_role_policy = data.aws_iam_policy_document.sentry_alert_assume.json
}

data "aws_iam_policy_document" "sentry_alert" {
  statement {
    sid = "WriteLogs"
    actions = [
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["${aws_cloudwatch_log_group.sentry_alert.arn}:*"]
  }

  statement {
    sid     = "ReadSecrets"
    actions = ["ssm:GetParameter"]
    resources = [
      "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter${local.sentry_alert_param_path}/*",
    ]
  }
}

resource "aws_iam_role_policy" "sentry_alert" {
  name   = local.sentry_alert_name
  role   = aws_iam_role.sentry_alert.id
  policy = data.aws_iam_policy_document.sentry_alert.json
}

resource "aws_cloudwatch_log_group" "sentry_alert" {
  name              = "/aws/lambda/${local.sentry_alert_name}"
  retention_in_days = 14
}

resource "aws_lambda_function" "sentry_alert" {
  function_name = local.sentry_alert_name
  role          = aws_iam_role.sentry_alert.arn

  runtime       = "python3.13"
  architectures = ["arm64"]
  handler       = "handler.lambda_handler"
  memory_size   = 128
  timeout       = 10

  filename         = data.archive_file.sentry_alert.output_path
  source_code_hash = data.archive_file.sentry_alert.output_base64sha256

  environment {
    variables = {
      DISCORD_WEBHOOK_PARAM      = "${local.sentry_alert_param_path}/DISCORD_WEBHOOK_URL"
      SENTRY_CLIENT_SECRET_PARAM = "${local.sentry_alert_param_path}/SENTRY_CLIENT_SECRET"
    }
  }

  logging_config {
    log_format = "Text"
    log_group  = aws_cloudwatch_log_group.sentry_alert.name
  }
}

resource "aws_ssm_parameter" "sentry_alert" {
  for_each = toset(local.sentry_alert_parameters)

  name  = "${local.sentry_alert_param_path}/${each.key}"
  type  = "SecureString"
  value = "CHANGE_ME"

  lifecycle {
    ignore_changes = [value]
  }
}

# 인증 없는 공개 URL
resource "aws_lambda_function_url" "sentry_alert" {
  function_name      = aws_lambda_function.sentry_alert.function_name
  authorization_type = "NONE"
}

resource "aws_lambda_permission" "sentry_alert_url" {
  statement_id           = "AllowPublicFunctionUrl"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.sentry_alert.function_name
  principal              = "*"
  function_url_auth_type = "NONE"
}

resource "aws_lambda_permission" "sentry_alert_invoke" {
  statement_id             = "AllowInvokeViaFunctionUrl"
  action                   = "lambda:InvokeFunction"
  function_name            = aws_lambda_function.sentry_alert.function_name
  principal                = "*"
  invoked_via_function_url = true
}
