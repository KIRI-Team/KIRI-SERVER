terraform {
  required_version = ">=1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~>6.0"
    }
  }

  backend "s3" {
    bucket       = "kiri-tfstate-100024219481"
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    profile      = "kiri"
    encrypt      = true
    use_lockfile = true
  }
}
