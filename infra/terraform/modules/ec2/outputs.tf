output "instance_id" {
  value = aws_instance.this.id
}

output "public_ip" {
  value = aws_eip.this.public_ip
}

output "log_group_name" {
  value = aws_cloudwatch_log_group.app.name
}