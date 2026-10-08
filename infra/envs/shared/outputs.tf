output "ecr_repository_urls" {
  value = { for env, repo in aws_ecr_repository.app : env => repo.repository_url }
}

output "github_oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.github.arn
}
