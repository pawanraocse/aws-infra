# AWS Infrastructure - Multi-Tenant Microservices

Multi-tenant microservices platform with AWS Cognito authentication and Lambda-based tenant isolation.

## 📚 Documentation

- **[HLD.md](HLD.md)** - High-Level Design, Architecture, and Requirements
- **[copilot-index.md](copilot-index.md)** - Technical Index, Setup Guide, and Current Status
- **[next_task.md](next_task.md)** - Roadmap and Active Tasks
- **[scripts/README.md](scripts/README.md)** - All utility scripts documentation
- **[terraform/README.md](terraform/README.md)** - Terraform infrastructure documentation

## 🛠️ Common Commands

```bash
# Build all services
./scripts/build/build-all.sh

# Deploy infrastructure
./scripts/terraform/deploy.sh

# Load environment variables
source ./scripts/env/export-envs.sh
```

## 🚀 Quick Start

### 1. Deploy Infrastructure

```bash
./scripts/terraform/deploy.sh
```

### 2. Build Services

```bash
./scripts/build/build-all.sh
```

### 3. Start Services

```bash
docker-compose up -d
```

### 4. Run Tests

```bash
# Normal build (skips system tests)
mvn clean package

# Run E2E tests
export TEST_USER_EMAIL="test@example.com"
export TEST_USER_PASSWORD="Test123!"
mvn verify -Psystem-tests
```

## 📁 Project Structure

```
AWS-Infra/
├── scripts/           # All utility scripts (build, terraform, env)
├── terraform/         # AWS infrastructure (Cognito + Lambda)
├── auth-service/      # Authentication microservice
├── backend-service/   # Main business logic service
├── gateway-service/   # API Gateway
├── eureka-server/     # Service discovery
├── platform-service/  # Platform management
├── system-tests/      # E2E integration tests
└── docker-compose.yml # Local orchestration
```

## 🔐 AWS Resources

- **User Pool:** `cloud-infra-dev-user-pool` (us-east-1_6RGxkqTmA)
- **Lambda:** `cloud-infra-dev-pre-token-generation` (injects tenantId into JWT)
- **Region:** us-east-1
- **Profile:** personal

See [terraform/README.md](terraform/README.md) for details.

---

**Version:** 1.0.0

