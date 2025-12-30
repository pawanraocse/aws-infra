# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jre-alpine AS runtime

# Install AWS CLI for SSM parameter fetching
RUN apk add --no-cache aws-cli

WORKDIR /app

ARG JAR_FILE=target/platform-service-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 8083
ENTRYPOINT ["/app/entrypoint.sh"]
