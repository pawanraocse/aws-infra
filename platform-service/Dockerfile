# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

ARG JAR_FILE=target/platform-service-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]

