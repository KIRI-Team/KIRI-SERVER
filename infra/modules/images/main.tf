data "aws_caller_identity" "current" {}

# S3
resource "aws_s3_bucket" "this" {
  bucket        = "${var.name_prefix}-images-${data.aws_caller_identity.current.account_id}"
  force_destroy = var.force_destroy
}

# 퍼블릭 접근 전체 차단
resource "aws_s3_bucket_public_access_block" "this" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ACL 비활성화
resource "aws_s3_bucket_ownership_controls" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# SSE-S3 암호화
resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# Lifecycle
resource "aws_s3_bucket_lifecycle_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  # 미완료 멀티파트 업로드 정리
  rule {
    id     = "abort-incomplete-multipart"
    status = "Enabled"

    filter {}

    abort_incomplete_multipart_upload {
      days_after_initiation = var.multipart_abort_days
    }
  }

  # finalized되지 않은 업로드 정리
  rule {
    id     = "expire-pending-uploads"
    status = "Enabled"
    filter {
      prefix = "pending/"
    }
    expiration {
      days = var.pending_expiration_days
    }
  }
}

# 버킷 정책
data "aws_iam_policy_document" "bucket" {
  statement {
    sid     = "AllowCloudFrontRead"
    actions = ["s3:GetObject"]
    resources = [
      "${aws_s3_bucket.this.arn}/public/*",
      "${aws_s3_bucket.this.arn}/user/*"
    ]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.this.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "this" {
  bucket = aws_s3_bucket.this.id
  policy = data.aws_iam_policy_document.bucket.json

  depends_on = [aws_s3_bucket_public_access_block.this]
}

# CloudFront
# OAC
resource "aws_cloudfront_origin_access_control" "this" {
  name                              = "${var.name_prefix}-images"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# 캐시 정책
resource "aws_cloudfront_cache_policy" "immutable" {
  name        = "${var.name_prefix}-images-immutable"
  min_ttl     = 0
  default_ttl = var.cache_ttl_seconds
  max_ttl     = var.cache_ttl_seconds

  parameters_in_cache_key_and_forwarded_to_origin {
    enable_accept_encoding_gzip   = false
    enable_accept_encoding_brotli = false

    cookies_config {
      cookie_behavior = "none"
    }

    headers_config {
      header_behavior = "none"
    }

    query_strings_config {
      query_string_behavior = "none"
    }
  }
}

# Signed URL 키
resource "aws_cloudfront_public_key" "this" {
  name        = "${var.name_prefix}-images-signer"
  encoded_key = var.signing_public_key
}

resource "aws_cloudfront_key_group" "this" {
  name  = "${var.name_prefix}-images-signers"
  items = [aws_cloudfront_public_key.this.id]
}

resource "aws_cloudfront_distribution" "this" {
  enabled         = true
  is_ipv6_enabled = true
  http_version    = "http2and3"
  price_class     = var.price_class
  comment         = "${var.name_prefix} images"

  origin {
    origin_id                = "s3-images"
    domain_name              = aws_s3_bucket.this.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.this.id
  }

  default_cache_behavior {
    target_origin_id       = "s3-images"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = false
    viewer_protocol_policy = "redirect-to-https"
    cache_policy_id        = aws_cloudfront_cache_policy.immutable.id
    trusted_key_groups     = [aws_cloudfront_key_group.this.id]
  }

  ordered_cache_behavior {
    path_pattern           = "public/*"
    target_origin_id       = "s3-images"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = false
    viewer_protocol_policy = "redirect-to-https"
    cache_policy_id        = aws_cloudfront_cache_policy.immutable.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

