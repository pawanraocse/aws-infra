
# Quick Start Guide

**Version:** 7.2 (Extracted from HLD)
**Last Updated:** 2026-01-17

This guide gets you up and running with the SaaS Foundation template. It covers initial deployment, local development, and how to add your own services.

---

## 🚀 Quick Start (Local & Budget)

### Prerequisites
- Java 21+, Maven 3.9+
- Docker & Docker Compose
- Node.js 18+ (for frontend)
- AWS CLI configured with credentials
- Terraform 1.9+

### Step 1: Deploy AWS Infrastructure (Cognito)
Even for local development, we use a real AWS Cognito User Pool.

```bash
cd terraform
terraform init
terraform apply -auto-approve
```
This creates: Cognito User Pool, Lambda triggers, SSM parameters.

### Step 2: Start Backend Services
```bash
# From project root
docker-compose up -d
```
Services available at:
- Gateway: http://localhost:8080
- Frontend: http://localhost:4200
- Eureka Dashboard: http://localhost:8761

### Step 3: Create System Admin
```bash
./scripts/bootstrap-system-admin.sh your-admin@email.com "YourPassword123!"
```

### Step 4: Test the Flow
1. Navigate to http://localhost:4200
2. **Personal Signup (B2C):** Create account → Verify email → Login
3. **Organization Signup (B2B):** Create org → Invite users → Login
4. Access dashboard with your tenant's isolated data

---

## 🔧 Adding Your Own Service

The `backend-service` is a mimic/placeholder. Here's how to create your real domain service (e.g., `inventory-service` or `crm-service`).

### Step 1: Copy Backend-Service
```bash
cp -r backend-service/ my-new-service/
# Update pom.xml: artifactId, name
# Update application.yml: server.port (e.g., 8084)
```

### Step 2: Add Dependencies
Ensure `pom.xml` includes the shared infrastructure:
```xml
<dependency>
    <groupId>com.learning</groupId>
    <artifactId>common-infra</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 3: Configure Tenant Database Routing
Create `DataSourceConfig.java` to enable `TenantDataSourceRouter`. This ensures every request connects to the correct tenant database.

```java
@Configuration
public class DataSourceConfig {
    @Bean
    public TenantRegistryService tenantRegistryService(WebClient platformWebClient) {
        return new PlatformServiceTenantRegistry(platformWebClient, new TenantLocalCache());
    }
    
    @Bean
    public DataSource dataSource(TenantRegistryService tenantRegistry, DataSource defaultDataSource) {
        return new TenantDataSourceRouter(tenantRegistry, defaultDataSource);
    }
}
```

### Step 4: Add WebClient for Platform-Service
Required for fetching tenant database credentials.
```java
@Bean
public WebClient platformWebClient(WebClient.Builder builder) {
    return builder.baseUrl("http://platform-service/platform").build();
}
```

### Step 5: Register with Eureka
In `application.yml`:
```yaml
spring.application.name: my-new-service
eureka.client.service-url.defaultZone: http://eureka-server:8761/eureka
```

### Step 6: Add to Docker Compose
```yaml
my-new-service:
  build: ./my-new-service
  ports: ["8084:8084"]
  depends_on:
    eureka-server: {condition: service_healthy}
    postgres: {condition: service_healthy}
```

### Step 7: Add Gateway Route
In `gateway-service/application.yml`:
```yaml
- id: my-new-service
  uri: lb://my-new-service
  predicates:
    - Path=/my-new/**
```

---

## 🧪 How to Build Your Service

### 1. Replace Domain Entities
Delete the example `Entry` entity and create your own.

```java
@Entity
@Table(name = "orders")  // Your domain table
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    private String customerId;
    private BigDecimal totalAmount;
    // Your domain fields...
}
```

### 2. Add Permissions
Protect your endpoints using `@RequirePermission`.

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @GetMapping
    @RequirePermission(resource = "order", action = "read")
    public List<Order> getOrders() { ... }
    
    @PostMapping
    @RequirePermission(resource = "order", action = "create")
    public Order createOrder(@RequestBody OrderDto dto) { ... }
}
```

### 3. Register Permissions
Add SQL migration in `auth-service/src/main/resources/db/migration/tenant/`:

```sql
-- V100__add_order_permissions.sql
INSERT INTO permissions (id, resource, action, description) VALUES
    (gen_random_uuid(), 'order', 'read', 'View orders'),
    (gen_random_uuid(), 'order', 'create', 'Create orders');

-- Assign to admin role
INSERT INTO role_permissions (role_id, permission_id)
SELECT 'admin', id FROM permissions WHERE resource = 'order';
```

---

## 🛠️ Deployment & Configuration Flow

The project includes automated scripts for infrastructure provisioning.

### Automated Deployment Script
**Location:** `scripts/terraform/deploy.sh`

**What it does:**
1. **Deploys AWS Infrastructure**: VPC, RDS, Cognito, ALB, ECS.
2. **Stores Config in SSM**: Pushes all sensitive URLs and IDs to AWS Systems Manager Parameter Store.
3. **Auto-Updates Frontend**: Writes `environment.ts` with the new Cognito User Pool ID.

**Usage:**
```bash
./scripts/terraform/deploy.sh
```

### Backend Configuration (SSM)
Backend services read configuration from SSM at runtime. No hardcoded secrets!
Prefix: `/cloud-infra/dev/cognito/*`

### Frontend Configuration
Frontend environment files are auto-generated by the deploy script.
Example `environment.ts`:
```typescript
export const environment = {
  production: false,
  cognito: {
    userPoolId: 'us-east-1_jjRFRnxGA',
    clientId: '...',
    region: 'us-east-1'
  }
};
```
