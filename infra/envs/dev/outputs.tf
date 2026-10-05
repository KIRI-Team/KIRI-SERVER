output "ec2_instance_id" {
  value = module.ec2.instance_id
}

output "ec2_public_ip" {
  value = module.ec2.public_ip
}

output "log_group_name" {
  value = module.ec2.log_group_name
}

output "rds_address" {
  value = module.rds.address
}
