variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "github_repo" {
  description = "배포 Role을 사용할 GitHub 레포 (owner/repo)"
  type        = string
}

variable "github_environment" {
  description = "배포 Role을 사용할 GitHub Environment 이름 (브랜치 제한은 Environment 설정에서 관리)"
  type        = string
}

variable "ecr_repository_name" {
  description = "이미지를 push할 ECR 저장소 이름"
  type        = string
}

variable "instance_id" {
  description = "배포 명령을 보낼 EC2 인스턴스 ID"
  type        = string
}