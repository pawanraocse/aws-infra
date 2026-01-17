# Bastion Module - Outputs

output "instance_id" {
  description = "Bastion EC2 instance ID"
  value       = aws_instance.bastion.id
}

output "public_ip" {
  description = "Bastion public IP (Elastic IP if created, otherwise instance IP)"
  value       = var.create_eip ? aws_eip.bastion[0].public_ip : aws_instance.bastion.public_ip
}

output "public_dns" {
  description = "Bastion public DNS"
  value       = var.create_eip ? aws_eip.bastion[0].public_dns : aws_instance.bastion.public_dns
}

output "private_ip" {
  description = "Bastion private IP"
  value       = aws_instance.bastion.private_ip
}

output "security_group_id" {
  description = "Bastion security group ID"
  value       = aws_security_group.bastion.id
}

output "ssh_command" {
  description = "SSH command to connect"
  value       = "ssh -i <key.pem> ec2-user@${var.create_eip ? aws_eip.bastion[0].public_ip : aws_instance.bastion.public_ip}"
}

output "ssm_command" {
  description = "SSM Session Manager command"
  value       = "aws ssm start-session --target ${aws_instance.bastion.id}"
}
