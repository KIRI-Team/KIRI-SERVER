locals {
  ecr_repositories = {
    dev  = 5
    prod = 10
  }
}

resource "aws_ecr_repository" "app" {
  for_each = local.ecr_repositories

  name = "kiri-server-${each.key}"

  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "app" {
  for_each = aws_ecr_repository.app

  repository = each.value.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "untagged 이미지 1일 후 삭제"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        rulePriority = 2
        description  = "최신 ${local.ecr_repositories[each.key]}개 유지"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = local.ecr_repositories[each.key]
        }
        action = { type = "expire" }
      }
    ]
  })
}
