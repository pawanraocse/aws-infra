# requirements.md

> Purpose: provide a step-by-step, unambiguous requirements file for generating a Spring Boot microservice project with AWS deployment via EKS. Use this file with GitHub Copilot or any code generator to produce a working repo, infra, CI/CD, and deployment artifacts.

---

## 1. Scope

Build one Spring Boot service named `entry-service` that supports two APIs:
- `POST /api/v1/entries` — create an entry.
- `GET  /api/v1/entries/{id}` — retrieve an entry by id.

Deliver a production-ready pipeline and infra in AWS:
- Amazon Cognito for authn/authz.
- Amazon RDS (SQL) for persistence.
- Docker images pushed to Amazon ECR.
- Kubernetes on Amazon EKS for runtime.
- AWS ALB via AWS Load Balancer Controller as ingress and external LB.
- Helm chart for app deployment.
- GitHub Actions CI/CD.
- Secrets in AWS Secrets Manager (consumed by K8s via AWS Secrets CSI or external-secrets).

This file must be detailed enough for Copilot to generate project skeletons, infra templates, and CI workflows.

---

## 2. Non-functional requirements

- Java 17 or 21. Default: **Java 17 LTS**.
- Spring Boot 3.x.
- Use JPA + Hibernate. Use Flyway for DB migrations.
- Use UUID primary keys.
- All network traffic encrypted (HTTPS). ALB terminates TLS with ACM cert.
- Horizontal autoscaling via HPA. Default CPU target 50%.
- Cluster autoscaler enabled for EKS node groups.
- Logs written to stdout in structured JSON.
- Metrics exposed at `/actuator/prometheus`.
- Tracing via OpenTelemetry → AWS X-Ray (optional if X-Ray chosen).
- Images built with a multi-stage Dockerfile and use a slim or distroless runtime image.
- No secrets in source control.
- Least-privilege IAM roles for pod service accounts via IRSA.

---

## 3. High-level architecture

1. Clients (web/mobile) -> ALB (HTTPS) -> Kubernetes Ingress Controller (AWS LB Controller) -> `entry-service` pods.
2. `entry-service` validates Cognito JWTs and enforces roles.
3. `entry-service` persists to RDS (Postgres preferred). DB is in private subnets.
4. CI pipeline builds container, pushes to ECR, and deploys via Helm to EKS.

---

## 4. Functional requirements and API contract

### 4.1 Data model (Postgres)

**Table: entries**

```sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE entries (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(255) NOT NULL,
  content TEXT,
  created_by VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  metadata JSONB
);
```

Indexes:
- `CREATE INDEX idx_entries_created_at ON entries(created_at);`
- Full text search not required initially.

### 4.2 API endpoints

Base path: `/api/v1`

**POST /api/v1/entries**
- Auth: `Bearer <Cognito JWT>` (scope or group `entry:create` or role mapping).
- Request JSON schema:
```json
{ "title": "string(1..255)", "content": "string", "metadata": {"any": "json"} }
```
- Response: `201 Created`
```json
{ "id": "uuid", "title": "...", "content": "...", "createdAt": "ISO-8601", "createdBy": "email or sub" }
```
- Validation: title required, <= 255 chars.

**GET /api/v1/entries/{id}**
- Auth: `Bearer <Cognito JWT>` (scope or role `entry:read`).
- Response: `200 OK` + JSON body same as POST response.
- `404` if not found.

### 4.3 Authentication and Authorization

- Use Cognito User Pool as OIDC provider.
- Services must validate JWTs using the OIDC issuer: `https://cognito-idp.{AWS_REGION}.amazonaws.com/{USER_POOL_ID}`.
- Map Cognito groups or custom claims to application roles.
- Implement a `JwtAuthenticationConverter` that extracts roles from `cognito:groups` or a specific claim named `roles`.
- Enforce role-based access using `@PreAuthorize` or method security in controllers.

---

## 5. Application implementation details

### 5.1 Project layout (Maven/Gradle)

```
entry-service/
  ├─ src/main/java/com/yourorg/entryservice
  │    ├─ controller/EntryController.java
  │    ├─ service/EntryService.java
  │    ├─ repository/EntryRepository.java
  │    ├─ model/Entry.java
  │    ├─ dto/EntryRequest.java
  │    ├─ dto/EntryResponse.java
  │    └─ config/SecurityConfig.java
  ├─ src/main/resources/application.yml
  ├─ Dockerfile
  ├─ build.gradle (or pom.xml)
  └─ deploy/helm/entry-service/...
```

Follow SOLID. Keep controller thin. Business logic in service classes.

### 5.2 Spring configuration

- Use `application.yml` with profiles: `application-dev.yml`, `application-prod.yml`.
- Config values via environment variables.
- Required env vars:
  - `SPRING_DATASOURCE_URL`
  - `SPRING_DATASOURCE_USERNAME`
  - `SPRING_DATASOURCE_PASSWORD`
  - `AWS_REGION`
  - `COGNITO_USER_POOL_ID`
  - `COGNITO_CLIENT_ID`

- Enable Actuator endpoints: `/actuator/health`, `/actuator/prometheus`.

### 5.3 Database migration

- Use Flyway migrations under `src/main/resources/db/migration`.
- On startup, Flyway validates and migrates.
- Migrations include create table DDL above.

### 5.4 Logging and metrics

- Use Logback config for JSON output. Example: log pattern includes timestamp, level, logger, traceId.
- Expose Prometheus metrics via Micrometer and `management.metrics.export.prometheus.enabled=true`.

### 5.5 Tests

- Unit tests for controllers and services (Mockito / JUnit 5).
- Integration tests using Testcontainers for Postgres.
- Contract tests for REST API (e.g., Pact or simpler contract tests).
- E2E tests in CI using a deployed dev environment.

---

## 6. Docker requirements

- Multi-stage build. Build stage uses JDK. Final stage uses JRE or distroless.
- Use non-root user in final image.
- Expose port `8080`.
- Build args allowed for Java version.

Minimal sample (generator-friendly):

```dockerfile
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew bootJar -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
EXPOSE 8080
```

---

## 7. Kubernetes & Helm requirements

Create a Helm chart named `entry-service` with the following templates and values:

- `deployment.yaml` (Deployment)
  - Use `spec.template.spec.serviceAccountName` annotated with IRSA role arn placeholder.
  - Resource requests and limits mandatory. Defaults: requests.cpu=250m, memory=512Mi. limits.cpu=500m, memory=1Gi.
  - Liveness probe: `GET /actuator/health/liveness`.
  - Readiness probe: `GET /actuator/health/readiness`.
  - Environment variables loaded from a Kubernetes Secret and ConfigMap.
  - Sidecar optional for log shipping.

- `service.yaml` (ClusterIP)

- `ingress.yaml` (Ingress) annotated for AWS Load Balancer Controller.
  - Annotations:
    - `alb.ingress.kubernetes.io/scheme: internet-facing` or internal as required.
    - `alb.ingress.kubernetes.io/listen-ports: '[{"HTTP":80,"HTTPS":443}]'`.
    - `alb.ingress.kubernetes.io/certificate-arn: "<ACM_CERT_ARN>"`
    - `alb.ingress.kubernetes.io/ssl-redirect: '443'`

- `hpa.yaml` (HorizontalPodAutoscaler)
  - Target CPU utilization default 50%.
  - Min replicas 2 for prod, default 1 for dev.

- `values.yaml` must contain keys for image repository, tag, resources, replica count, cognito settings, DB connection details (only references to k8s secrets), and service account annotations.

---

## 8. AWS infra and IaC requirements

Prefer Terraform modules. Provide example module boundaries and outputs.

### Resources required
- VPC with 3 AZs (public and private subnets).
- ECR repo for `entry-service`.
- EKS cluster with managed node groups (or Fargate profile) and OIDC provider enabled.
- RDS Postgres in private subnets. Multi-AZ for prod.
- Secrets Manager secret for DB credentials.
- IAM roles and policies for:
  - CI/CD to push to ECR and deploy to EKS.
  - EKS nodes.
  - IRSA roles for service account to read secrets.
- ACM certificate in same region for domain.
- Route53 Hosted Zone record pointing DNS to ALB.

### Terraform module outline
- `networking/` -> VPC, subnets, route tables.
- `eks/` -> EKS cluster, node groups, OIDC provider.
- `rds/` -> RDS instance, subnets, parameter group.
- `ecr/` -> ECR repository.
- `iam/` -> roles and policies.
- `dns/` -> route53 records.

Each module must export IDs and ARNs needed by other modules.

---

## 9. CI/CD: GitHub Actions (required)

Create two workflows:

1. `ci.yml` run on PRs and pushes to `develop`:
   - Checkout.
   - Build and run unit tests.
   - Build Docker image (tag ephemeral with commit sha).
   - Run integration tests with Testcontainers if possible.
   - Upload artifact (jar) for further pipeline.

2. `cd.yml` run on push to `main` or release tags:
   - Checkout.
   - Authenticate to AWS (use OIDC & short-lived creds or `aws-actions/configure-aws-credentials`).
   - Build Docker image and push to ECR with semantic tag (e.g., `v{semver}` and `latest` for main).
   - Run Flyway migrations against RDS (use an ephemeral migration job or run as helm pre-deploy job). Prefer migration as separate job executed from CI with DB credentials stored in Secrets Manager and passed securely.
   - Deploy Helm chart to EKS: `helm upgrade --install` using `kubectl` context set by `aws eks update-kubeconfig`.
   - Run smoke tests against deployed endpoint.

Security:
- Use GitHub OIDC to assume an IAM role for push/publish to ECR and deploy to EKS.
- Store sensitive secrets in GitHub Secrets only for pipeline (minimize scope).

---

## 10. Secrets management and IRSA

- Store DB credentials and any third-party secrets in AWS Secrets Manager.
- Create an IRSA IAM role that allows `secretsmanager:GetSecretValue` for the DB secret ARN.
- Annotate the Kubernetes service account to use that IAM role.
- Option A: use `kubernetes-external-secrets` or `aws-secrets-manager-csi-driver` to sync secrets into Kubernetes.
- Avoid embedding secrets in Helm `values.yaml` for public repos.

---

## 11. Observability and monitoring

- Logs: Fluent Bit or CloudWatch agent configured as DaemonSet to forward container stdout to CloudWatch Logs.
- Metrics: Prometheus operator or Prometheus server scraping `/actuator/prometheus`.
- Dashboards: Grafana with dashboards for JVM metrics, request latency, error rates.
- Tracing: OpenTelemetry with exporter to X-Ray.
- Alerts: Prometheus Alertmanager + CloudWatch Alarms for node and RDS health.

SLO examples:
- Availability 99.9% per month for critical endpoints.
- P95 latency < 500ms for GET, < 1s for POST.

---

## 12. Security checklist

- ALB enforces TLS.`
- Cognito validates tokens and enforces roles.
- RDS is not publicly accessible.
- Security group rules are minimal.
- Image scanning enabled on ECR.
- Dependabot or similar for dependency updates.
- IAM roles use least privilege.
- K8s RBAC limited to namespaces; do not use cluster-admin for app service accounts.

---

## 13. Local development

- Provide `docker-compose.yml` to run Postgres and the app locally.
- Provide a script `scripts/run-local.sh` to build and run the jar and set env vars.
- Use Testcontainers in integration tests.
- Optionally use `localstack` to emulate AWS services for offline development.

---

## 14. Acceptance criteria and tests

- All CI checks pass on PR.
- `POST /api/v1/entries` stores a record in Postgres and returns 201.
- `GET /api/v1/entries/{id}` returns correct record with 200.
- Requests without valid Cognito JWT return 401.
- Role-protected endpoints return 403 for insufficient roles.
- Helm deploy script installs and passes readiness checks.
- HPA scales pods when CPU load rises.
- Logs and metrics are available in configured backends.

---

## 15. Repository deliverables

The generated repo must include:
- `entry-service/` full source.
- `deploy/helm/entry-service/` complete Helm chart.
- `iac/terraform/` modules skeleton to create VPC, EKS, RDS, ECR, IAM, ACM.
- `.github/workflows/ci.yml` and `cd.yml`.
- `Dockerfile` and `Makefile` with common commands.
- `README.md` with quick start for dev and deploy steps.
- `postman/entry-service.postman_collection.json` or a `curl` script for manual testing.

---

## 16. Example commands (to include in README)

- Build locally:
```bash
./gradlew clean build
```
- Build image and push:
```bash
./scripts/build-and-push.sh <account-id> <region> <repo> <tag>
```
- Deploy:
```bash
helm upgrade --install entry-service ./deploy/helm/entry-service -f ./deploy/helm/entry-service/values-dev.yaml
```
- Run integration tests:
```bash
./gradlew test integrationTest
```

---

## 17. File tree example

```
├── entry-service/
│   ├── src/
│   ├── build.gradle
│   ├── Dockerfile
│   └── deploy/helm/entry-service/
├── iac/
│   ├── terraform/
│   └── eksctl/
├── .github/workflows/ci.yml
└── .github/workflows/cd.yml
```

---

## 18. Deliverables expected from Copilot

- Java Spring Boot project with controllers, service, repository, DTOs.
- Flyway migrations.
- Dockerfile.
- Helm chart with templates and `values.yaml`.
- Terraform skeleton for core infra modules.
- GitHub Actions workflows.
- `README.md` and `scripts/` to build and deploy.

---

## 19. Questions (answer these to finalize generation)

1. Preferred database: `postgres` or `mysql`? (default: `postgres`)
2. AWS region and account id to embed in CI examples. (default region suggestion: `ap-south-1`)
3. CI/CD preference: GitHub Actions (default) or AWS CodePipeline?
4. Domain name and Route53 zone available? Provide hosted zone id if yes.
5. TLS: will you provide ACM certificate ARN or want IaC to request one?
6. Node groups: prefer managed node groups or Fargate for pods? Preferred instance types?
7. Use RDS Proxy? (yes/no)
8. Use `aws-secrets-manager-csi-driver` or `kubernetes-external-secrets`?
9. Do you require corporate compliance (PCI, HIPAA, etc.)?
10. JVM flavor preference for images (Temurin, Graal native image)?
11. Want example frontend or only backend APIs?
12. Budget constraints for infra (to decide instance sizes and RDS class)?

Answer the questions or provide defaults. Copilot can generate most artifacts with defaults.

---

## 20. Next steps

- Answer section 19.
- Choose first artifact for generation: (a) Spring Boot skeleton, (b) Helm chart, (c) Terraform skeleton, (d) GitHub Actions workflows.

End of requirements.md

