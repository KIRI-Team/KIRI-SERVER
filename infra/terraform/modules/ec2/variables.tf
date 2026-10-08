variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "instance_type" {
  description = "EC2 인스턴스 타입"
  type        = string
}

variable "subnet_id" {
  description = "EC2를 배치할 서브넷 (public subnet)"
  type        = string
}

variable "security_group_id" {
  description = "EC2에 붙일 보안 그룹"
  type        = string
}

variable "root_volume_size" {
  description = "루트 볼륨 크기 (GB)"
  type        = number
  default     = 20
}

variable "cpu_credits" {
  description = "CPU 크레딧 모드 (standard / ultimate)"
  type        = string
  default     = "standard"
}

variable "parameter_path" {
  description = "앱이 읽을 Parameter Store 경로"
  type        = string
}

variable "log_retention_days" {
  description = "CloudWatch 로그 보관 기간 (일)"
  type        = number
}

variable "s3_bucket_arns" {
  description = "EC2가 접근할 S3 버킷 ARN 목록"
  type        = list(string)
  default     = []
}
