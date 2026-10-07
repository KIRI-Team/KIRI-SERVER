variable "name_prefix" {
  description = "리소스 이름 접두사"
  type        = string
}

variable "force_destroy" {
  description = "버킷에 객체가 있어도 삭제 허용 (dev만 true)"
  type        = bool
  default     = false
}

variable "multipart_abort_days" {
  description = "미완료 멀티파트 업로드 삭제 기간 (일)"
  type        = number
  default     = 7
}

variable "pending_expiration_days" {
  description = "finalize되지 않은 pending/ 업로드 삭제 기간 (일)"
  type        = number
  default     = 1
}

variable "signing_public_key" {
  description = "CloudFront Signed URL 검증용 RSA 2048 공개키"
  type        = string
}

variable "price_class" {
  description = "CloudFront 가격 등급"
  type        = string
  default     = "PriceClass_200"
}

variable "cache_ttl_seconds" {
  description = "엣지 캐시 TTL (초)"
  type        = number
  default     = 31536000
}
