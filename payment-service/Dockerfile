# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jre-alpine AS runtime

# Install AWS CLI if needed (kept for consistency, though unused for now)
RUN apk add --no-cache aws-cli

WORKDIR /app

ARG JAR_FILE=target/payment-service-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8088
ENTRYPOINT ["/app/entrypoint.sh"]
