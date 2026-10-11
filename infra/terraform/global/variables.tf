variable "alert_emails" {
  description = "비용 알람을 받을 이메일 목록"
  type        = list(string)
}

variable "monthly_budget_usd" {
  description = "월 예산 (USD)"
  type        = number
  default     = 20
}
