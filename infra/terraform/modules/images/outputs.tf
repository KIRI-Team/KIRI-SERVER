output "bucket_name" {
  value = aws_s3_bucket.this.bucket
}

output "bucket_arn" {
  value = aws_s3_bucket.this.arn
}

output "cdn_domain" {
  value = aws_cloudfront_distribution.this.domain_name
}

output "signing_key_pair_id" {
  value = aws_cloudfront_public_key.this.id
}