# Platform Service

Platform service centralizes tenant lifecycle, provisioning, policy, and internal token issuance.

## Run Locally

```bash
# Build
./mvnw -f platform-service/pom.xml clean package -DskipTests

# Run
java -jar platform-service/target/platform-service-0.0.1-SNAPSHOT.jar

# Health
curl http://localhost:8083/actuator/health

# Swagger UI
open http://localhost:8083/swagger-ui/index.html
```

## Test (Integration with Testcontainers)

```bash
./mvnw -f platform-service/pom.xml test
```

## Environment
- Port: 8083
- Registers with Eureka via configuration
- PostgreSQL: jdbc:postgresql://postgres:5432/awsinfra

## Next
- Implement DB/schema provisioning per tenant
- Integrate Cognito AdminCreateUser for admin account creation
- Policy storage & decision API
- Internal token issuance (JWK endpoint)

