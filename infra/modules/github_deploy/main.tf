data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

data "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"
}

data "aws_ecr_repository" "app" {
  name = var.ecr_repository_name
}

# Trust 정책
# GitHub 토큰의 aud, sub 값을 검사하여 KIRI-SERVER 레포의 develop 브랜치만 허용
data "aws_iam_policy_document" "github_deploy_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      identifiers = [data.aws_iam_openid_connect_provider.github.arn]
      type        = "Federated"
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:environment:${var.github_environment}"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "${var.name_prefix}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_deploy_assume.json
}

# 권한 정책
# assume한 뒤 무엇을 할 수 있는가
data "aws_iam_policy_document" "github_deploy" {
  # ECR 로그인 토큰 발급
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # kiri-server 저장소에만 이미지 push
  statement {
    sid = "EcrPush"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [data.aws_ecr_repository.app.arn]
  }

  # Name 태그로 배포 대상 인스턴스 ID 조회
  statement {
    sid       = "FindDeployTarget"
    actions   = ["ec2:DescribeInstances"]
    resources = ["*"]
  }

  # dev EC2에서 AWS-RunShellScript로만 명령 실행
  # 두 리소스를 모두 허용해야 SendCommand가 동작
  statement {
    sid     = "RunDeployCommand"
    actions = ["ssm:SendCommand"]
    resources = [
      "arn:aws:ssm:${data.aws_region.current.region}::document/AWS-RunShellScript",
      "arn:aws:ec2:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:instance/${var.instance_id}",
    ]
  }

  # SendCommand는 비동기이기 때문에 실행 결과를 polling해서 조회
  statement {
    sid = "ReadCommandResult"
    actions = [
      "ssm:GetCommandInvocation",
      "ssm:ListCommandInvocations",
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "${var.name_prefix}-github-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
