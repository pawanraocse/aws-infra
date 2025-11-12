FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install AWS CLI for SSM parameter access
RUN apk add --no-cache aws-cli

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy pre-built jar
COPY target/*.jar app.jar

# Copy entrypoint script
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# Create logs directory
RUN mkdir -p /app/logs && chown -R spring:spring /app

USER spring

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/app/entrypoint.sh"]
