# Bastion Module

Secure EC2 instance for database administration access.

## Features

- ✅ **SSM Session Manager** - No SSH key needed
- ✅ **SSH access** - Optional key pair
- ✅ **Free Tier** - t2.micro eligible
- ✅ **Pre-installed tools** - psql, redis-cli

## Usage

```hcl
module "bastion" {
  source = "../../modules/bastion"

  project_name = "saas-factory"
  environment  = "production"

  vpc_id    = module.vpc.vpc_id
  subnet_id = module.vpc.public_subnet_ids[0]

  # Restrict SSH access
  allowed_ssh_cidr_blocks = ["1.2.3.4/32"]  # Your IP
  
  # Optional: SSH key
  ssh_public_key = file("~/.ssh/id_rsa.pub")
}
```

## Access Methods

### SSM Session Manager (Recommended)
```bash
aws ssm start-session --target <instance-id>
```

### SSH
```bash
ssh -i key.pem ec2-user@<bastion-ip>
```

### Connect to RDS
```bash
# From bastion
psql -h <rds-endpoint> -U postgres -d saas_db
```

## Cost

| Resource | Monthly |
|----------|---------|
| t2.micro | **FREE** (12 months) |
| EBS 8GB | ~$0.64 |
