variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "subnet_ids" {
  description = "DB Subnet Group에 넣을 private subnet 목록"
  type        = list(string)
}

variable "security_group_id" {
  description = "RDS에 붙일 보안 그룹"
  type        = string
}

variable "availability_zone" {
  description = "RDS를 배치할 AZ"
  type        = string
}

variable "instance_class" {
  description = "DB 인스턴스 타입"
  type        = string
}

variable "engine_version" {
  description = "PostgreSQL 버전"
  type        = string
}

variable "allocated_storage" {
  description = "디스크 크기 (GB)"
  type        = number
  default     = 20
}

variable "db_name" {
  description = "처음 생성할 데이터베이스 이름"
  type        = string
}

variable "db_username" {
  description = "마스터 사용자 이름"
  type        = string
}

variable "backup_retention_days" {
  description = "자동 백업 보관 기간 (일)"
  type        = number
}

variable "deletion_protection" {
  description = "삭제 보호"
  type        = bool
}

variable "skip_final_snapshot" {
  description = "삭제할 때 마지막 스냅샷을 건너뛸지 여부"
  type        = bool
}

variable "apply_immediately" {
  description = "설정 변경을 즉시 적용할지 여부"
  type        = bool
  default     = false
}

variable "parameter_path" {
  description = "접속 정보를 저장할 Parameter Store 경로"
  type        = string
}
