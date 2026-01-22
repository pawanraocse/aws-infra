# Makefile for Terraform operations
# ==================================
# Automatically passes common.auto.tfvars for consistent settings

COMMON_VARS := -var-file=../../common.auto.tfvars

# Budget Environment
.PHONY: budget-init budget-plan budget-apply budget-destroy

budget-init:
	cd envs/budget && terraform init

budget-plan:
	cd envs/budget && terraform plan $(COMMON_VARS)

budget-apply:
	cd envs/budget && terraform apply $(COMMON_VARS)

budget-destroy:
	cd envs/budget && terraform destroy $(COMMON_VARS)

# Production Environment
.PHONY: prod-init prod-plan prod-apply prod-destroy

prod-init:
	cd envs/production && terraform init

prod-plan:
	cd envs/production && terraform plan $(COMMON_VARS)

prod-apply:
	cd envs/production && terraform apply $(COMMON_VARS)

prod-destroy:
	cd envs/production && terraform destroy $(COMMON_VARS)

# Root (dev) Environment
.PHONY: dev-init dev-plan dev-apply dev-destroy

dev-init:
	terraform init

dev-plan:
	terraform plan -var-file=common.auto.tfvars

dev-apply:
	terraform apply -var-file=common.auto.tfvars

dev-destroy:
	terraform destroy -var-file=common.auto.tfvars

# Utility
.PHONY: fmt validate

fmt:
	terraform fmt -recursive

validate:
	terraform validate
	cd envs/budget && terraform validate
	cd envs/production && terraform validate

# Help
.PHONY: help
help:
	@echo "Terraform Makefile"
	@echo ""
	@echo "Usage: make <target>"
	@echo ""
	@echo "Targets:"
	@echo "  budget-init     Initialize budget environment"
	@echo "  budget-plan     Plan budget environment"
	@echo "  budget-apply    Apply budget environment"
	@echo "  budget-destroy  Destroy budget environment"
	@echo ""
	@echo "  prod-init       Initialize production environment"
	@echo "  prod-plan       Plan production environment"
	@echo "  prod-apply      Apply production environment"
	@echo "  prod-destroy    Destroy production environment"
	@echo ""
	@echo "  fmt             Format all Terraform files"
	@echo "  validate        Validate all configurations"
