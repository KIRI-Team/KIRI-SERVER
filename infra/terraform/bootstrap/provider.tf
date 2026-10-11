provider "aws" {
  region  = "ap-northeast-2"
  profile = "kiri"

  allowed_account_ids = ["100024219481"]

  default_tags {
    tags = {
      Project     = "kiri"
      Environment = "bootstrap"
      ManagedBy   = "terraform"
    }
  }
}
