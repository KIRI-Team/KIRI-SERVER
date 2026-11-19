variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "vpc_cidr" {
  description = "VPC CIDR"
  type        = string
}

variable "azs" {
  description = "사용할 가용 영역 목록"
  type        = list(string)
}

variable "app_port" {
  description = "EC2에서 앱 컨테이너가 노출하는 포트"
  type        = number
  default     = 8080
}

variable "app_ingress_cidrs" {
  description = "앱 포트로 직접 접근을 허용할 CIDR 목록"
  type        = list(string)
  default     = []
}
