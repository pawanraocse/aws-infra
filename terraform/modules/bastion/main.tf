# Bastion Module
# Secure EC2 instance for database administration access

locals {
  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
    Module      = "bastion"
  }

  bastion_name = "${var.project_name}-${var.environment}-bastion"
}

# =============================================================================
# Security Group
# =============================================================================

resource "aws_security_group" "bastion" {
  name        = "${local.bastion_name}-sg"
  description = "Security group for bastion host"
  vpc_id      = var.vpc_id

  # SSH access from allowed IPs only
  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.allowed_ssh_cidr_blocks
  }

  # Application ports (Gateway, Eureka, services)
  ingress {
    description = "Gateway API"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "Eureka Dashboard"
    from_port   = 8761
    to_port     = 8761
    protocol    = "tcp"
    cidr_blocks = var.allowed_ssh_cidr_blocks
  }

  # Internal services - accessible only from Lambda (when Lambda SG is provided)
  # Auth Service (8081) - used by PreTokenGeneration Lambda
  dynamic "ingress" {
    for_each = var.lambda_security_group_id != null ? [1] : []
    content {
      description     = "Auth Service from Lambda"
      from_port       = 8081
      to_port         = 8081
      protocol        = "tcp"
      security_groups = [var.lambda_security_group_id]
    }
  }

  # Platform Service (8083) - used by PreTokenGeneration Lambda
  dynamic "ingress" {
    for_each = var.lambda_security_group_id != null ? [1] : []
    content {
      description     = "Platform Service from Lambda"
      from_port       = 8083
      to_port         = 8083
      protocol        = "tcp"
      security_groups = [var.lambda_security_group_id]
    }
  }

  egress {
    description = "All outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${local.bastion_name}-sg"
  })
}

# =============================================================================
# IAM Role (for SSM Session Manager)
# =============================================================================

resource "aws_iam_role" "bastion" {
  name = "${local.bastion_name}-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = local.common_tags
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.bastion.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

# Policy for reading SSM parameters and Secrets Manager secrets
resource "aws_iam_role_policy" "bastion_app_access" {
  name = "${local.bastion_name}-app-access"
  role = aws_iam_role.bastion.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = concat(
      [
        {
          Effect = "Allow"
          Action = [
            "ssm:GetParameter",
            "ssm:GetParameters",
            "ssm:GetParametersByPath"
          ]
          Resource = "arn:aws:ssm:*:*:parameter/${var.project_name}/*"
        },
        {
          Effect = "Allow"
          Action = [
            "secretsmanager:GetSecretValue"
          ]
          Resource = "arn:aws:secretsmanager:*:*:secret:${var.project_name}/*"
        },
        {
          Effect = "Allow"
          Action = [
            "ecr:GetAuthorizationToken",
            "ecr:BatchCheckLayerAvailability",
            "ecr:GetDownloadUrlForLayer",
            "ecr:BatchGetImage"
          ]
          Resource = "*"
        }
      ],
      # Cognito Admin operations (for auth-service) - only if user pool ARN provided
      var.cognito_user_pool_arn != null ? [
        {
          Effect = "Allow"
          Action = [
            # Core: User existence check for signup
            "cognito-idp:AdminGetUser",
            # SSO: Identity Provider management
            "cognito-idp:DescribeIdentityProvider",
            "cognito-idp:CreateIdentityProvider",
            "cognito-idp:UpdateIdentityProvider",
            "cognito-idp:DeleteIdentityProvider",
            # SSO: User Pool Client management
            "cognito-idp:DescribeUserPoolClient",
            "cognito-idp:UpdateUserPoolClient"
          ]
          Resource = var.cognito_user_pool_arn
        }
      ] : []
    )
  })
}


resource "aws_iam_instance_profile" "bastion" {
  name = "${local.bastion_name}-profile"
  role = aws_iam_role.bastion.name

  tags = local.common_tags
}

# =============================================================================
# Key Pair (optional - for SSH access)
# =============================================================================

resource "aws_key_pair" "bastion" {
  count = var.ssh_public_key != null ? 1 : 0

  key_name   = local.bastion_name
  public_key = var.ssh_public_key

  tags = local.common_tags
}

# =============================================================================
# EC2 Instance
# =============================================================================

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_instance" "bastion" {
  ami                    = var.ami_id != null ? var.ami_id : data.aws_ami.amazon_linux.id
  instance_type          = var.instance_type
  subnet_id              = var.subnet_id
  vpc_security_group_ids = [aws_security_group.bastion.id]
  iam_instance_profile   = aws_iam_instance_profile.bastion.name
  key_name               = var.ssh_public_key != null ? aws_key_pair.bastion[0].key_name : null

  # Associate public IP for SSH access
  associate_public_ip_address = true

  # Enable detailed monitoring for Free Tier
  monitoring = false

  # User data for initial setup
  user_data = <<-EOF
    #!/bin/bash
    # Disable SSH DNS lookup to prevent connection delays
    echo "UseDNS no" >> /etc/ssh/sshd_config
    systemctl restart sshd
    
    # Add 2GB Swap to prevent OOM
    dd if=/dev/zero of=/swapfile bs=128M count=16
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo "/swapfile swap swap defaults 0 0" >> /etc/fstab

    yum update -y
    yum install -y postgresql15 redis6 htop tmux rsync git docker jq
    
    # Start and enable Docker
    systemctl start docker
    systemctl enable docker
    usermod -aG docker ec2-user
    
    # Install docker-compose
    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
    
    echo "Bastion setup complete. Use: psql -h <RDS_ENDPOINT> -U postgres -d <DB_NAME>"
  EOF

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    encrypted             = true
    delete_on_termination = true
  }

  tags = merge(local.common_tags, {
    Name = local.bastion_name
  })

  lifecycle {
    ignore_changes = [ami]
  }
}

# =============================================================================
# Elastic IP (optional - for consistent IP)
# =============================================================================

resource "aws_eip" "bastion" {
  count = var.create_eip ? 1 : 0

  instance = aws_instance.bastion.id
  domain   = "vpc"

  tags = merge(local.common_tags, {
    Name = local.bastion_name
  })
}
